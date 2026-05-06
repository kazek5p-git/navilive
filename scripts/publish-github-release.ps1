param(
    [string]$Tag,
    [string]$Title,
    [string]$NotesFile,
    [switch]$SkipBuild,
    [switch]$NoVersionBump,
    [switch]$SkipGitPush
)

$ErrorActionPreference = "Stop"

Write-Host '[etap] Sprawdzam srodowisko Android i GitHub.'

function Get-RequiredValue {
    param(
        [string]$Value,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "Missing $Label."
    }

    return $Value.Trim()
}

function Invoke-Tool {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Command,
        [string]$WorkingDirectory
    )

    if ($WorkingDirectory) {
        Push-Location $WorkingDirectory
    }

    try {
        $null = & $Command[0] $Command[1..($Command.Length - 1)]
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $($Command -join ' ')"
        }
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }
}

function Invoke-Capture {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Command,
        [string]$WorkingDirectory
    )

    if ($WorkingDirectory) {
        Push-Location $WorkingDirectory
    }

    try {
        $output = & $Command[0] $Command[1..($Command.Length - 1)] 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw ($output | Out-String)
        }
        return ($output | Out-String).Trim()
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }
}

function Get-ReleaseNotes {
    param(
        [string]$Path,
        [string]$VersionLabel,
        [bool]$ReleaseExists,
        [string]$ExistingNotes
    )

    if (-not [string]::IsNullOrWhiteSpace($Path)) {
        $resolvedPath = (Resolve-Path -Path $Path).Path
        return [System.IO.File]::ReadAllText($resolvedPath, [System.Text.Encoding]::UTF8)
    }

    if ($ReleaseExists -and -not [string]::IsNullOrWhiteSpace($ExistingNotes)) {
        return $ExistingNotes
    }

return @"
Navi Live $VersionLabel release.

- Android build published as navi-live.apk.
"@
}

function Get-AndroidVersionInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$GradleFile
    )

    $gradleText = Get-Content -Path $GradleFile -Raw -Encoding UTF8
    $versionCodeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*([0-9]+)')
    $versionNameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
    $versionCodeValue = Get-RequiredValue -Value $versionCodeMatch.Groups[1].Value -Label "versionCode"
    $versionNameValue = Get-RequiredValue -Value $versionNameMatch.Groups[1].Value -Label "versionName"

    return [pscustomobject]@{
        VersionCode = [int]$versionCodeValue
        VersionName = $versionNameValue
    }
}

function Get-NextPatchVersionName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$VersionName
    )

    $normalized = $VersionName.Trim().TrimStart('v')
    if ($normalized -notmatch '^[0-9]+(?:\.[0-9]+)+$') {
        throw "Nie umiem automatycznie podbic versionName '$VersionName'. Uzyj prostego formatu, np. 1.0.12."
    }

    $parts = @($normalized.Split('.') | ForEach-Object { [int]$_ })
    $parts[$parts.Count - 1] = $parts[$parts.Count - 1] + 1
    return ($parts -join '.')
}

function Set-AndroidVersionInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$GradleFile,
        [Parameter(Mandatory = $true)]
        [int]$VersionCode,
        [Parameter(Mandatory = $true)]
        [string]$VersionName
    )

    $gradleText = Get-Content -Path $GradleFile -Raw -Encoding UTF8
    $codeRegex = [regex]::new('versionCode\s*=\s*[0-9]+')
    $nameRegex = [regex]::new('versionName\s*=\s*"[^"]+"')
    $updated = $codeRegex.Replace($gradleText, "versionCode = $VersionCode", 1)
    $updated = $nameRegex.Replace($updated, "versionName = `"$VersionName`"", 1)
    [System.IO.File]::WriteAllText($GradleFile, $updated, [System.Text.UTF8Encoding]::new($false))
}

function Assert-GitWorktreeClean {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $statusLines = @(& git -C $RepoRoot status --porcelain=v1 --untracked-files=all)
    if ($LASTEXITCODE -ne 0) {
        throw "Nie moge odczytac statusu git przed podbiciem wersji Androida."
    }
    if ($statusLines.Count -gt 0) {
        $preview = ($statusLines | Select-Object -First 20) -join "`n"
        throw "Nie podbijam Androida automatycznie, bo repo ma niezatwierdzone zmiany. Zacommituj je i ponow publikacje. Pliki:`n$preview"
    }
}

function Assert-GitBranchCanBePushed {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$Branch
    )

    Invoke-Tool -Command @("git", "-C", $RepoRoot, "fetch", "--quiet", "origin", $Branch)
    & git -C $RepoRoot merge-base --is-ancestor "origin/$Branch" HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Nie podbijam Androida, bo lokalna galaz nie zawiera aktualnego origin/$Branch. Zrob pull/rebase i ponow publikacje."
    }
}

function Test-GitHubReleaseExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoSlug,
        [Parameter(Mandatory = $true)]
        [string]$ReleaseTag
    )

    $null = & gh api "repos/$RepoSlug/releases/tags/$ReleaseTag" 2>$null
    return $LASTEXITCODE -eq 0
}

function Test-RemoteTagExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$ReleaseTag
    )

    $null = & git -C $RepoRoot ls-remote --exit-code --tags origin "refs/tags/$ReleaseTag" 2>$null
    return $LASTEXITCODE -eq 0
}

function Get-NextAvailableAndroidVersionName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CurrentVersionName,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$RepoSlug
    )

    $candidate = Get-NextPatchVersionName -VersionName $CurrentVersionName
    while ($true) {
        $candidateTag = "v$candidate"
        if (-not (Test-RemoteTagExists -RepoRoot $RepoRoot -ReleaseTag $candidateTag) -and
            -not (Test-GitHubReleaseExists -RepoSlug $RepoSlug -ReleaseTag $candidateTag)) {
            return $candidate
        }
        $candidate = Get-NextPatchVersionName -VersionName $candidate
    }
}

function New-AndroidVersionBumpCommit {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$GradleFile,
        [Parameter(Mandatory = $true)]
        [string]$RepoSlug,
        [Parameter(Mandatory = $true)]
        [string]$TargetBranch,
        [switch]$SkipPush
    )

    Write-Host '[etap] Podbijam wersje Androida.'
    Assert-GitWorktreeClean -RepoRoot $RepoRoot
    Assert-GitBranchCanBePushed -RepoRoot $RepoRoot -Branch $TargetBranch

    $current = Get-AndroidVersionInfo -GradleFile $GradleFile
    $nextVersionName = Get-NextAvailableAndroidVersionName `
        -CurrentVersionName $current.VersionName `
        -RepoRoot $RepoRoot `
        -RepoSlug $RepoSlug
    $nextVersionCode = $current.VersionCode + 1

    Set-AndroidVersionInfo -GradleFile $GradleFile -VersionCode $nextVersionCode -VersionName $nextVersionName
    Invoke-Tool -Command @("git", "-C", $RepoRoot, "add", "android/app/build.gradle.kts")
    Invoke-Tool -Command @("git", "-C", $RepoRoot, "commit", "-m", "Bump Android version to $nextVersionName")

    if ($SkipPush) {
        Write-Host '[etap] Pomijam git push na zadanie uzytkownika.'
    } else {
        Write-Host '[etap] Wypycham commit z nowa wersja Androida.'
        Invoke-Tool -Command @("git", "-C", $RepoRoot, "push", "origin", $TargetBranch)
    }

    Write-Host "Android versionName: $($current.VersionName) -> $nextVersionName"
    Write-Host "Android versionCode: $($current.VersionCode) -> $nextVersionCode"

    return [pscustomobject]@{
        VersionCode = $nextVersionCode
        VersionName = $nextVersionName
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$androidDir = Join-Path $repoRoot "android"
$gradleFile = Join-Path $androidDir "app\build.gradle.kts"
$assetPath = Join-Path $androidDir "app\build\release-asset\navi-live.apk"
$tempPayloadPath = Join-Path $repoRoot ".release-payload.json"

$remoteUrl = Invoke-Capture -Command @("git", "-C", $repoRoot, "remote", "get-url", "origin")
$repoMatch = [regex]::Match($remoteUrl, 'github\.com[:/](.+?)(?:\.git)?$')
$repoSlug = Get-RequiredValue -Value $repoMatch.Groups[1].Value -Label "GitHub repo slug"
$targetBranch = Invoke-Capture -Command @("git", "-C", $repoRoot, "branch", "--show-current")
$targetBranch = Get-RequiredValue -Value $targetBranch -Label "current git branch"

$autoBumpVersion = -not $NoVersionBump -and -not $SkipBuild -and [string]::IsNullOrWhiteSpace($Tag)
if ($autoBumpVersion) {
    $versionInfo = New-AndroidVersionBumpCommit `
        -RepoRoot $repoRoot `
        -GradleFile $gradleFile `
        -RepoSlug $repoSlug `
        -TargetBranch $targetBranch `
        -SkipPush:$SkipGitPush
} else {
    if ($NoVersionBump) {
        Write-Host '[etap] Pomijam automatyczne podbijanie wersji Androida.'
    } elseif ($SkipBuild) {
        Write-Host '[etap] SkipBuild: nie podbijam wersji Androida.'
    } elseif (-not [string]::IsNullOrWhiteSpace($Tag)) {
        Write-Host '[etap] Podano wlasny tag: nie podbijam wersji Androida automatycznie.'
    }
    $versionInfo = Get-AndroidVersionInfo -GradleFile $gradleFile
}

$versionName = $versionInfo.VersionName
if ([string]::IsNullOrWhiteSpace($Tag)) {
    $Tag = "v$versionName"
}
$userProvidedTitle = -not [string]::IsNullOrWhiteSpace($Title)

if (-not $SkipBuild) {
    Write-Host '[etap] Buduje Android release lokalnie.'
    Invoke-Tool -Command @(".\gradlew.bat", ":app:stageDebugReleaseAsset") -WorkingDirectory $androidDir
}

if (-not (Test-Path -Path $assetPath)) {
    throw "Release asset not found at $assetPath"
}

$releaseExists = $false
$release = $null
try {
    $releaseJson = Invoke-Capture -Command @("gh", "api", "repos/$repoSlug/releases/tags/$Tag")
    $release = $releaseJson | ConvertFrom-Json
    $releaseExists = $true
} catch {
    $releaseExists = $false
}

$notes = Get-ReleaseNotes -Path $NotesFile -VersionLabel $versionName -ReleaseExists $releaseExists -ExistingNotes $release.body
$effectiveTitle = if ($releaseExists -and -not $userProvidedTitle) {
    $release.name
} else {
    if ($userProvidedTitle) { $Title } else { $Tag }
}

$payload = $(
    if ($releaseExists) {
        @{
            name = $effectiveTitle
            body = $notes
        }
    } else {
        @{
            tag_name = $Tag
            target_commitish = $targetBranch
            name = $effectiveTitle
            body = $notes
        }
    }
) | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText(
    $tempPayloadPath,
    $payload,
    [System.Text.UTF8Encoding]::new($false)
)

try {
    Write-Host '[etap] Publikuje Android release na GitHub.'
    if ($releaseExists) {
        Invoke-Tool -Command @("gh", "api", "repos/$repoSlug/releases/$($release.id)", "--method", "PATCH", "--input", $tempPayloadPath)
    } else {
        Invoke-Tool -Command @("gh", "api", "repos/$repoSlug/releases", "--method", "POST", "--input", $tempPayloadPath)
        $releaseJson = Invoke-Capture -Command @("gh", "api", "repos/$repoSlug/releases/tags/$Tag")
        $release = $releaseJson | ConvertFrom-Json
    }

    $assetsJson = Invoke-Capture -Command @("gh", "api", "repos/$repoSlug/releases/$($release.id)/assets")
    $assets = $assetsJson | ConvertFrom-Json
    foreach ($asset in $assets) {
        if ($asset.name -like "*.apk") {
            Invoke-Tool -Command @("gh", "api", "repos/$repoSlug/releases/assets/$($asset.id)", "--method", "DELETE")
        }
    }

    $uploadUrl = "https://uploads.github.com/repos/$repoSlug/releases/$($release.id)/assets?name=navi-live.apk"
    Invoke-Tool -Command @(
        "gh",
        "api",
        $uploadUrl,
        "--method",
        "POST",
        "--input",
        $assetPath,
        "-H",
        "Content-Type: application/vnd.android.package-archive"
    )

    $releaseJson = Invoke-Capture -Command @("gh", "api", "repos/$repoSlug/releases/tags/$Tag")
    $release = $releaseJson | ConvertFrom-Json
    $downloadUrl = ($release.assets | Where-Object { $_.name -eq "navi-live.apk" } | Select-Object -First 1).browser_download_url

    Write-Host '[etap] Android GitHub release zakonczony.'
    Write-Host "Release ready: $($release.html_url)"
    Write-Host "APK: $downloadUrl"
    Write-Host "Latest link: https://github.com/$repoSlug/releases/latest/download/navi-live.apk"
} finally {
    if (Test-Path -Path $tempPayloadPath) {
        Remove-Item -Path $tempPayloadPath -Force
    }
}
