param(
  [string]$RepoSlug = "kazek5p-git/navi-live",
  [string]$Ref = "main",
  [string]$BuildNumber,
  [switch]$NoUpload,
  [switch]$NoWait
)

$ErrorActionPreference = "Stop"

function Assert-Tooling {
  if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "gh is not available in PATH."
  }
}

function Invoke-GitHub {
  param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
  )

  & gh @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "gh command failed: gh $($Arguments -join ' ')"
  }
}

Assert-Tooling

$uploadValue = if ($NoUpload) { "false" } else { "true" }

Write-Host ""
Write-Host "==> Dispatching Navi Live iOS signed workflow"
Write-Host ("Repo: " + $RepoSlug)
Write-Host ("Ref: " + $Ref)
Write-Host ("Upload to TestFlight: " + $uploadValue)
if (-not [string]::IsNullOrWhiteSpace($BuildNumber)) {
  Write-Host ("Build number override: " + $BuildNumber)
}

$workflowArgs = @("workflow", "run", "ios-signed-testflight.yml", "--repo", $RepoSlug, "--ref", $Ref, "-f", "upload_to_testflight=$uploadValue")
if (-not [string]::IsNullOrWhiteSpace($BuildNumber)) {
  $workflowArgs += @("-f", "build_number_override=$BuildNumber")
}
Invoke-GitHub @workflowArgs

Start-Sleep -Seconds 8

$runJson = gh run list --repo $RepoSlug --workflow "ios-signed-testflight.yml" --branch $Ref --limit 1 --json "databaseId,status,conclusion,headSha"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($runJson)) {
  throw "Unable to resolve the latest workflow run."
}

$run = $runJson | ConvertFrom-Json | Select-Object -First 1
if (-not $run) {
  throw "No workflow run found after dispatch."
}
$runUrl = "https://github.com/$RepoSlug/actions/runs/$($run.databaseId)"

Write-Host ""
Write-Host "Latest run:"
Write-Host ("- Run ID: " + $run.databaseId)
Write-Host ("- URL: " + $runUrl)
Write-Host ("- Head SHA: " + $run.headSha)
Write-Host ("- Status: " + $run.status)

if (-not $NoWait) {
  Write-Host ""
  Write-Host "==> Waiting for workflow completion"
  & gh run watch $run.databaseId --repo $RepoSlug --exit-status
  if ($LASTEXITCODE -ne 0) {
    throw "Workflow run failed: $runUrl"
  }
}

Write-Host ""
Write-Host "Workflow completed successfully."
Write-Host ("URL: " + $runUrl)
