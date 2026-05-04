package com.navilive.android.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val walkDistanceMeters: Int,
    val walkEtaMinutes: Int,
    val point: GeoPoint? = null,
    val phone: String? = null,
    val website: String? = null,
)

data class RouteSummary(
    val distanceMeters: Int,
    val etaMinutes: Int,
    val modeLabel: String = "",
    val currentInstruction: String = "",
    val nextInstruction: String = "",
    val steps: List<RouteStep> = emptyList(),
    val pathPoints: List<GeoPoint> = emptyList(),
)

enum class RouteStepKind {
    Instruction,
    PedestrianCrossing,
}

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val maneuverPoint: GeoPoint? = null,
    val kind: RouteStepKind = RouteStepKind.Instruction,
    val maneuverType: String? = null,
    val maneuverModifier: String? = null,
    val roadName: String? = null,
)

data class HeadingState(
    val instruction: String = "",
    val isAligned: Boolean = false,
    val arrowRotationDeg: Float = 18f,
)

data class ActiveNavigationState(
    val currentInstruction: String = "",
    val nextInstruction: String = "",
    val distanceToNextMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val progressLabel: String = "",
    val isPaused: Boolean = false,
    val isOffRoute: Boolean = false,
    val isRecalculating: Boolean = false,
    val offRouteDistanceMeters: Int? = null,
)

data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val timestampMs: Long,
)

data class LocationUiState(
    val hasPermission: Boolean = false,
    val isForegroundTracking: Boolean = false,
    val latestFix: LocationFix? = null,
)

enum class SpeechOutputMode(val storageValue: String) {
    System("system"),
    ScreenReader("screen_reader"),
    ;

    companion object {
        fun fromStorageValue(value: String?): SpeechOutputMode {
            return entries.firstOrNull { it.storageValue == value } ?: ScreenReader
        }
    }
}

enum class AnnouncementCadenceMode(val storageValue: String) {
    Distance("distance"),
    Time("time"),
    ;

    companion object {
        fun fromStorageValue(value: String?): AnnouncementCadenceMode {
            return entries.firstOrNull { it.storageValue == value } ?: Distance
        }
    }
}

enum class ShakeStrength(val storageValue: String) {
    Light("light"),
    Medium("medium"),
    Strong("strong"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ShakeStrength {
            return entries.firstOrNull { it.storageValue == value } ?: Medium
        }
    }
}

enum class SoundCueTheme(val storageValue: String) {
    Standard("standard"),
    Tetris("tetris"),
    Cosmic("cosmic"),
    ;

    companion object {
        fun fromStorageValue(value: String?): SoundCueTheme {
            return entries.firstOrNull { it.storageValue == value } ?: Standard
        }
    }
}

enum class NearbyPoiCacheMode(val storageValue: String) {
    Enabled("enabled"),
    WifiOnly("wifi_only"),
    Disabled("disabled"),
    ;

    companion object {
        fun fromStorageValue(value: String?): NearbyPoiCacheMode {
            return entries.firstOrNull { it.storageValue == value } ?: Enabled
        }
    }
}

enum class UpdateChannel(val storageValue: String) {
    Stable("stable"),
    Beta("beta"),
    ;

    companion object {
        fun fromStorageValue(value: String?): UpdateChannel {
            if (value == "test") {
                return Beta
            }
            return entries.firstOrNull { it.storageValue == value } ?: Stable
        }
    }
}

data class SystemTtsEngineOption(
    val packageName: String?,
    val displayName: String,
    val isDefaultChoice: Boolean = false,
)

data class SettingsState(
    val language: String = "",
    val showTutorialOnStartup: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val shakeGestureEnabled: Boolean = true,
    val shakeStrength: ShakeStrength = ShakeStrength.Medium,
    val soundCuesEnabled: Boolean = true,
    val soundCueVolumePercent: Int = 85,
    val soundCueTheme: SoundCueTheme = SoundCueTheme.Standard,
    val autoRecalculate: Boolean = true,
    val junctionAlerts: Boolean = true,
    val pedestrianCrossingAlerts: Boolean = true,
    val turnByTurnAnnouncements: Boolean = true,
    val announcementCadenceMode: AnnouncementCadenceMode = AnnouncementCadenceMode.Distance,
    val searchRadiusKm: Int = SharedProductRules.Search.defaultRadiusKm,
    val searchResultLimit: Int = SharedProductRules.Search.resultLimit,
    val nearbyPoiCacheMode: NearbyPoiCacheMode = NearbyPoiCacheMode.Enabled,
    val nearbyPoiCacheRadiusKm: Int = SharedProductRules.Search.defaultRadiusKm,
    val updateChannel: UpdateChannel = UpdateChannel.Stable,
    val speechOutputMode: SpeechOutputMode = SpeechOutputMode.ScreenReader,
    val selectedSystemTtsEnginePackage: String? = null,
    val speechRatePercent: Int = 100,
    val speechVolumePercent: Int = 100,
    val isScreenReaderActive: Boolean = false,
    val activeScreenReaderName: String? = null,
    val availableSystemTtsEngines: List<SystemTtsEngineOption> = emptyList(),
    val defaultSystemTtsEngineLabel: String? = null,
    val activeSystemTtsEngineLabel: String? = null,
    val isSelectedSystemTtsEngineAvailable: Boolean = true,
)

data class NearbyPoiCacheState(
    val isRefreshing: Boolean = false,
    val cachedPlaceCount: Int = 0,
    val lastUpdatedAtMs: Long? = null,
    val lastCenter: GeoPoint? = null,
)

data class DiagnosticsState(
    val eventCount: Int = 0,
    val activeSessionLabel: String = "",
    val lastEventLabel: String = "",
    val lastExportPath: String? = null,
)

enum class AppUpdatePhase {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    ReadyToInstall,
    Error,
}

data class AppUpdateState(
    val currentVersionLabel: String = "",
    val currentBuildLabel: String = "",
    val latestVersionLabel: String? = null,
    val latestReleaseName: String? = null,
    val latestAssetName: String? = null,
    val latestAssetDownloadUrl: String? = null,
    val releaseNotes: String = "",
    val releasePageUrl: String? = null,
    val statusMessage: String = "",
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val downloadProgressPercent: Int? = null,
    val downloadedApkPath: String? = null,
    val downloadedVersionLabel: String? = null,
    val canRequestPackageInstalls: Boolean = true,
    val isAutoInstallRequested: Boolean = false,
)

data class NaviLiveUiState(
    val currentLocationLabel: String = "",
    val places: List<Place> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val lastRoutePlaceId: String? = null,
    val headingState: HeadingState = HeadingState(),
    val activeNavigationState: ActiveNavigationState = ActiveNavigationState(),
    val settingsState: SettingsState = SettingsState(),
    val diagnosticsState: DiagnosticsState = DiagnosticsState(),
    val appUpdateState: AppUpdateState = AppUpdateState(),
    val nearbyPoiCacheState: NearbyPoiCacheState = NearbyPoiCacheState(),
    val statusMessage: String = "",
    val isLoadingSearch: Boolean = false,
    val isLoadingRoute: Boolean = false,
    val locationState: LocationUiState = LocationUiState(),
    val hasCompletedOnboarding: Boolean = false,
    val isPreferencesLoaded: Boolean = false,
)
