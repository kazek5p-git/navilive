package com.navilive.android.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navilive.android.R
import com.navilive.android.data.location.LocationForegroundService
import com.navilive.android.model.AppUpdatePhase
import com.navilive.android.model.ShakeStrength
import com.navilive.android.ui.NaviLiveViewModel
import com.navilive.android.ui.screens.ActiveNavigationScreen
import com.navilive.android.ui.screens.ArrivalScreen
import com.navilive.android.ui.screens.BootstrapScreen
import com.navilive.android.ui.screens.CurrentPositionScreen
import com.navilive.android.ui.screens.FavoritesScreen
import com.navilive.android.ui.screens.HelpPrivacyScreen
import com.navilive.android.ui.screens.LocalOpenSettings
import com.navilive.android.ui.screens.LocalOpenVisualAssistance
import com.navilive.android.ui.screens.HeadingAlignScreen
import com.navilive.android.ui.screens.NotFoundScreen
import com.navilive.android.ui.screens.OnboardingScreen
import com.navilive.android.ui.screens.PermissionsScreen
import com.navilive.android.ui.screens.PlaceDetailsScreen
import com.navilive.android.ui.screens.RouteSummaryScreen
import com.navilive.android.ui.screens.SearchScreen
import com.navilive.android.ui.screens.SettingsScreen
import com.navilive.android.ui.screens.StartScreen
import com.navilive.android.ui.screens.TutorialScreen
import java.io.File
import kotlin.math.sqrt

private const val ProjectRepositoryUrl = "https://github.com/kazek5p-git/navi-live"
private const val BeMyEyesPackageName = "com.bemyeyes.bemyeyes"
private const val BeMyEyesPlayStoreUrl = "https://play.google.com/store/apps/details?id=com.bemyeyes.bemyeyes"

@Composable
fun NaviLiveNavHost(viewModel: NaviLiveViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = checkLocationPermission(context)
        viewModel.onLocationPermissionChanged(hasLocationPermission)
        if (hasLocationPermission) {
            ContextCompat.startForegroundService(context, LocationForegroundService.startIntent(context))
            if (navController.currentBackStackEntry?.destination?.route == Routes.Permissions) {
                navController.navigate(Routes.Start) {
                    popUpTo(Routes.Bootstrap) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(hasLocationPermission) {
        viewModel.onLocationPermissionChanged(hasLocationPermission)
        if (hasLocationPermission) {
            ContextCompat.startForegroundService(context, LocationForegroundService.startIntent(context))
        } else {
            context.startService(LocationForegroundService.stopIntent(context))
        }
    }

    LaunchedEffect(uiState.value.isPreferencesLoaded) {
        if (uiState.value.isPreferencesLoaded) {
            viewModel.performStartupUpdateCheckIfNeeded()
        }
    }

    val shakeSettings = uiState.value.settingsState
    val shakeHasInstruction = uiState.value.activeNavigationState.currentInstruction.isNotBlank()
    DisposableEffect(context, shakeSettings.shakeGestureEnabled, shakeSettings.shakeStrength, shakeHasInstruction) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!shakeSettings.shakeGestureEnabled || !shakeHasInstruction || sensorManager == null || accelerometer == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private var lastShakeAtMs = 0L

                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values.getOrNull(0)?.div(SensorManager.GRAVITY_EARTH) ?: return
                    val y = event.values.getOrNull(1)?.div(SensorManager.GRAVITY_EARTH) ?: return
                    val z = event.values.getOrNull(2)?.div(SensorManager.GRAVITY_EARTH) ?: return
                    val force = sqrt(x * x + y * y + z * z)
                    val now = SystemClock.elapsedRealtime()
                    if (force >= shakeSettings.shakeStrength.thresholdG && now - lastShakeAtMs >= ShakeDebounceMs) {
                        lastShakeAtMs = now
                        viewModel.onShakeGestureDetected()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val runPrimaryUpdateAction = {
        val updateState = uiState.value.appUpdateState
        when {
            updateState.phase == AppUpdatePhase.Available -> {
                viewModel.downloadAndInstallAvailableUpdate()
            }
            updateState.phase == AppUpdatePhase.ReadyToInstall &&
                updateState.downloadedApkPath != null &&
                updateState.canRequestPackageInstalls -> {
                if (installDownloadedApk(context, updateState.downloadedApkPath)) {
                    viewModel.markUpdateInstallStarted()
                } else {
                    viewModel.clearAutoInstallRequest()
                }
            }
            updateState.phase == AppUpdatePhase.ReadyToInstall -> {
                viewModel.requestAutoInstallForDownloadedUpdate()
                openUnknownAppSourcesSettings(context)
            }
            else -> {
                viewModel.checkForAppUpdates()
            }
        }
    }

    LaunchedEffect(
        uiState.value.appUpdateState.phase,
        uiState.value.appUpdateState.downloadedApkPath,
        uiState.value.appUpdateState.canRequestPackageInstalls,
        uiState.value.appUpdateState.isAutoInstallRequested,
    ) {
        val updateState = uiState.value.appUpdateState
        if (
            updateState.phase == AppUpdatePhase.ReadyToInstall &&
            updateState.downloadedApkPath != null &&
            updateState.isAutoInstallRequested
        ) {
            if (updateState.canRequestPackageInstalls) {
                if (installDownloadedApk(context, updateState.downloadedApkPath)) {
                    viewModel.markUpdateInstallStarted()
                } else {
                    viewModel.clearAutoInstallRequest()
                }
            } else {
                openUnknownAppSourcesSettings(context)
            }
        }
    }

    val openSettings: () -> Unit = {
        navController.navigate(Routes.Settings) {
            launchSingleTop = true
        }
    }
    val openVisualAssistance: () -> Unit = {
        openVisualAssistance(context)
    }

    CompositionLocalProvider(
        LocalOpenSettings provides openSettings,
        LocalOpenVisualAssistance provides openVisualAssistance,
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Bootstrap,
        ) {
        composable(Routes.Bootstrap) {
            LaunchedEffect(
                uiState.value.isPreferencesLoaded,
                uiState.value.hasCompletedOnboarding,
                hasLocationPermission,
            ) {
                if (!uiState.value.isPreferencesLoaded) return@LaunchedEffect
                val destination = when {
                    !uiState.value.hasCompletedOnboarding -> Routes.Onboarding
                    uiState.value.settingsState.showTutorialOnStartup ->
                        Routes.tutorial(Routes.TutorialEntryStartup)
                    !hasLocationPermission -> Routes.Permissions
                    else -> Routes.Start
                }
                navController.navigate(destination) {
                    popUpTo(Routes.Bootstrap) { inclusive = true }
                    launchSingleTop = true
                }
            }
            BootstrapScreen()
        }

        composable(Routes.Onboarding) {
            OnboardingScreen(
                onContinue = {
                    viewModel.completeOnboarding()
                    val destination = if (hasLocationPermission) Routes.Start else Routes.Permissions
                    navController.navigate(destination) {
                        popUpTo(Routes.Bootstrap) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Routes.TutorialPattern,
            arguments = listOf(navArgument("entryMode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryMode = backStackEntry.arguments?.getString("entryMode")
                ?: Routes.TutorialEntrySettings
            val opensFromStartup = entryMode == Routes.TutorialEntryStartup
            TutorialScreen(
                showOnStartup = uiState.value.settingsState.showTutorialOnStartup,
                onShowOnStartupChange = viewModel::setShowTutorialOnStartup,
                onDone = {
                    if (opensFromStartup) {
                        val destination = if (hasLocationPermission) Routes.Start else Routes.Permissions
                        navController.navigate(destination) {
                            popUpTo(Routes.Bootstrap) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = {
                    if (opensFromStartup) {
                        closeApp(context)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(Routes.Permissions) {
            PermissionsScreen(
                hasLocationPermission = hasLocationPermission,
                onGrantPermission = {
                    if (hasLocationPermission) {
                        navController.navigate(Routes.Start) {
                            popUpTo(Routes.Bootstrap) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        permissionLauncher.launch(locationPermissionsForRequest())
                    }
                },
                onContinueWithoutPermission = {
                    navController.navigate(Routes.Start) {
                        popUpTo(Routes.Bootstrap) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.Start) {
            val quickFavorites = viewModel.getFavorites()
            val resumeLastRoutePlaceId = uiState.value.lastRoutePlaceId?.takeIf { placeId ->
                viewModel.getPlace(placeId) != null
            }
            StartScreen(
                currentLocation = uiState.value.currentLocationLabel,
                statusMessage = uiState.value.statusMessage,
                lastRoutePlaceId = resumeLastRoutePlaceId,
                quickFavorites = quickFavorites,
                accuracyMeters = uiState.value.locationState.latestFix?.accuracyMeters,
                hasLocationPermission = uiState.value.locationState.hasPermission,
                isForegroundTracking = uiState.value.locationState.isForegroundTracking,
                onSearch = { navController.navigate(Routes.Search) },
                onCurrentPosition = { navController.navigate(Routes.CurrentPosition) },
                onFavorites = { navController.navigate(Routes.Favorites) },
                onResumeLastRoute = { placeId ->
                    navController.navigate(Routes.routeSummary(placeId))
                },
                onOpenQuickFavorite = { placeId ->
                    navController.navigate(Routes.routeSummary(placeId))
                },
                onGrantLocationPermission = {
                    permissionLauncher.launch(locationPermissionsForRequest())
                },
                onToggleTracking = {
                    if (!uiState.value.locationState.hasPermission) {
                        permissionLauncher.launch(locationPermissionsForRequest())
                    } else if (uiState.value.locationState.isForegroundTracking) {
                        viewModel.playLiveTrackingToggleSound(starting = false)
                        context.startService(LocationForegroundService.stopIntent(context))
                    } else {
                        viewModel.playLiveTrackingToggleSound(starting = true)
                        ContextCompat.startForegroundService(
                            context,
                            LocationForegroundService.startIntent(context),
                        )
                    }
                },
            )
        }

        composable(Routes.Search) {
            SearchScreen(
                query = uiState.value.searchQuery,
                results = uiState.value.searchResults,
                isLoading = uiState.value.isLoadingSearch,
                hasSubmittedSearch = uiState.value.hasSubmittedSearch,
                favoriteIds = uiState.value.favoriteIds,
                onQueryChange = viewModel::updateSearchQuery,
                onSubmitSearch = viewModel::submitSearchQuery,
                onSelectPlace = { placeId ->
                    navController.navigate(Routes.placeDetails(placeId))
                },
                onShowRoute = { placeId ->
                    navController.navigate(Routes.routeSummary(placeId))
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PlaceDetailsPattern,
            arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            val place = viewModel.getPlace(placeId)
            if (place == null) {
                NotFoundScreen(onBack = { navController.popBackStack() })
            } else {
                PlaceDetailsScreen(
                    place = place,
                    isFavorite = place.id in uiState.value.favoriteIds,
                    onShowRoute = { navController.navigate(Routes.routeSummary(place.id)) },
                    onToggleFavorite = { viewModel.toggleFavorite(place.id) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Routes.RouteSummaryPattern,
            arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            val place = viewModel.getPlace(placeId)
            if (place == null) {
                NotFoundScreen(onBack = { navController.popBackStack() })
            } else {
                LaunchedEffect(place.id) {
                    if (!viewModel.hasPreparedRoute(place.id)) {
                        viewModel.startRoute(place.id)
                    }
                }
                RouteSummaryScreen(
                    place = place,
                    summary = viewModel.routeSummaryFor(place.id),
                    isFavorite = place.id in uiState.value.favoriteIds,
                    isLoadingRoute = uiState.value.isLoadingRoute,
                    onSaveFavorite = { viewModel.toggleFavorite(place.id) },
                    onStartRoute = {
                        if (viewModel.beginPreparedRoute(place.id)) {
                            navController.navigate(Routes.activeNavigation(place.id))
                        } else {
                            viewModel.startRoute(
                                placeId = place.id,
                                autoStartNavigation = true,
                            ) {
                                navController.navigate(Routes.activeNavigation(place.id))
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Routes.HeadingAlignPattern,
            arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            val place = viewModel.getPlace(placeId)
            if (place == null) {
                NotFoundScreen(onBack = { navController.popBackStack() })
            } else {
                HeadingAlignScreen(
                    place = place,
                    headingState = uiState.value.headingState,
                    onCheckHeading = viewModel::cycleHeadingInstruction,
                    onProceed = {
                        viewModel.markHeadingAligned()
                        viewModel.beginActiveNavigation()
                        navController.navigate(Routes.activeNavigation(place.id))
                    },
                    onSkip = {
                        viewModel.beginActiveNavigation()
                        navController.navigate(Routes.activeNavigation(place.id))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Routes.ActiveNavigationPattern,
            arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            val place = viewModel.getPlace(placeId)
            if (place == null) {
                NotFoundScreen(onBack = { navController.popBackStack() })
            } else {
                ActiveNavigationScreen(
                    place = place,
                    state = uiState.value.activeNavigationState,
                    hasLocationPermission = uiState.value.locationState.hasPermission,
                    accuracyMeters = uiState.value.locationState.latestFix?.accuracyMeters,
                    onPauseResume = viewModel::togglePauseNavigation,
                    onRepeatInstruction = viewModel::repeatCurrentInstruction,
                    onRecalculate = viewModel::recalculateRoute,
                    onReportProblem = viewModel::reportRouteProblem,
                    onArrived = {
                        viewModel.markArrived()
                        navController.navigate(Routes.arrival(place.id))
                    },
                    onStop = {
                        viewModel.stopNavigation()
                        navController.popBackStack(Routes.Start, false)
                    },
                )
            }
        }

        composable(
            route = Routes.ArrivalPattern,
            arguments = listOf(navArgument("placeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getString("placeId")
            val place = viewModel.getPlace(placeId)
            if (place == null) {
                NotFoundScreen(onBack = { navController.popBackStack() })
            } else {
                ArrivalScreen(
                    place = place,
                    onFinish = { navController.popBackStack(Routes.Start, false) },
                    onReverseRoute = { navController.navigate(Routes.routeSummary(place.id)) },
                    onSaveFavorite = { viewModel.toggleFavorite(place.id) },
                )
            }
        }

        composable(Routes.CurrentPosition) {
            CurrentPositionScreen(
                currentLocation = uiState.value.currentLocationLabel,
                accuracyMeters = uiState.value.locationState.latestFix?.accuracyMeters,
                hasLocationPermission = uiState.value.locationState.hasPermission,
                quickFavorites = viewModel.getFavorites(),
                onReadLocation = viewModel::announceCurrentLocation,
                onSaveCurrentLocationAsFavorite = viewModel::saveCurrentLocationAsFavorite,
                onSearch = { navController.navigate(Routes.Search) },
                onPickFavorite = { placeId ->
                    navController.navigate(Routes.routeSummary(placeId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Favorites) {
            FavoritesScreen(
                favorites = viewModel.getFavorites(),
                onOpenFavoriteDetails = { placeId ->
                    navController.navigate(Routes.placeDetails(placeId))
                },
                onSelectFavorite = { placeId ->
                    navController.navigate(Routes.routeSummary(placeId))
                },
                onRemoveFavorite = viewModel::toggleFavorite,
                onAddFavorite = { navController.navigate(Routes.Search) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Settings) {
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshSpeechRuntimeState()
                        viewModel.refreshUpdateRuntimeState()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            LaunchedEffect(Unit) {
                viewModel.refreshSpeechRuntimeState()
                viewModel.refreshUpdateRuntimeState()
            }
            SettingsScreen(
                state = uiState.value.settingsState,
                updateState = uiState.value.appUpdateState,
                diagnosticsState = uiState.value.diagnosticsState,
                nearbyPoiCacheState = uiState.value.nearbyPoiCacheState,
                onOpenHelpPrivacy = { navController.navigate(Routes.HelpPrivacy) },
                onVibrationChange = viewModel::setVibration,
                onShakeGestureEnabledChange = viewModel::setShakeGestureEnabled,
                onShakeStrengthChange = viewModel::setShakeStrength,
                onSoundCuesChange = viewModel::setSoundCues,
                onSoundCueVolumeChange = viewModel::setSoundCueVolumePercent,
                onSoundCueThemeChange = viewModel::setSoundCueTheme,
                onPreviewSoundCue = viewModel::previewSoundCue,
                onAutoRecalculateChange = viewModel::setAutoRecalculate,
                onJunctionAlertChange = viewModel::setJunctionAlerts,
                onTurnByTurnChange = viewModel::setTurnByTurnAnnouncements,
                onAnnouncementCadenceModeChange = viewModel::setAnnouncementCadenceMode,
                onSearchRadiusChange = viewModel::setSearchRadiusKm,
                onSearchResultLimitChange = viewModel::setSearchResultLimit,
                onNearbyPoiCacheModeChange = viewModel::setNearbyPoiCacheMode,
                onNearbyPoiCacheRadiusChange = viewModel::setNearbyPoiCacheRadiusKm,
                onRefreshNearbyPoiCache = viewModel::refreshNearbyPoiCacheNow,
                onClearNearbyPoiCache = viewModel::clearNearbyPoiCache,
                onPedestrianCrossingAlertsChange = viewModel::setPedestrianCrossingAlerts,
                onUpdateChannelChange = viewModel::setUpdateChannel,
                onSpeechOutputModeChange = viewModel::setSpeechOutputMode,
                onSystemTtsEngineChange = viewModel::setSystemTtsEnginePackage,
                onOpenSystemTtsSettings = {
                    openSystemTtsSettings(context)
                },
                onSpeechRateChange = viewModel::setSpeechRatePercent,
                onSpeechVolumeChange = viewModel::setSpeechVolumePercent,
                onPreviewSpeech = viewModel::previewSpeechOutput,
                onPrimaryUpdateAction = runPrimaryUpdateAction,
                onOpenReleasePage = { releaseUrl ->
                    openExternalUrl(context, releaseUrl)
                },
                onOpenProjectRepository = {
                    openExternalUrl(context, ProjectRepositoryUrl)
                },
                onExportDiagnostics = viewModel::exportDiagnostics,
                onClearDiagnostics = viewModel::clearDiagnostics,
                onShareDiagnostics = uiState.value.diagnosticsState.lastExportPath?.let { exportPath ->
                    {
                        shareDiagnosticsFile(context, exportPath)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HelpPrivacy) {
            HelpPrivacyScreen(
                showTutorialOnStartup = uiState.value.settingsState.showTutorialOnStartup,
                onShowTutorialOnStartupChange = viewModel::setShowTutorialOnStartup,
                onOpenTutorial = {
                    navController.navigate(Routes.tutorial(Routes.TutorialEntrySettings))
                },
                onOpenSupportUrl = { supportUrl ->
                    openExternalUrl(context, supportUrl)
                },
                onOpenVisualAssistance = openVisualAssistance,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
}

private fun closeApp(context: Context) {
    context.findActivity()?.let { activity ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.finishAndRemoveTask()
        } else {
            @Suppress("DEPRECATION")
            activity.finishAffinity()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun checkLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun locationPermissionsForRequest(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

private fun shareDiagnosticsFile(context: Context, exportPath: String) {
    val exportFile = File(exportPath)
    if (!exportFile.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        exportFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_telemetry_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_telemetry_chooser)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun openSystemTtsSettings(context: Context) {
    val intents = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    val packageManager = context.packageManager
    val launchIntent = intents.firstOrNull { intent ->
        intent.resolveActivity(packageManager) != null
    }?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } ?: return
    context.startActivity(launchIntent)
}

private fun openUnknownAppSourcesSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openVisualAssistance(context: Context) {
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(BeMyEyesPackageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (launchIntent != null) {
        context.startActivity(launchIntent)
        return
    }

    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$BeMyEyesPackageName")).apply {
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (marketIntent.resolveActivity(packageManager) != null) {
        context.startActivity(marketIntent)
        return
    }

    openExternalUrl(context, BeMyEyesPlayStoreUrl)
}
private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

private val ShakeStrength.thresholdG: Float
    get() = when (this) {
        ShakeStrength.Light -> 2.2f
        ShakeStrength.Medium -> 2.8f
        ShakeStrength.Strong -> 3.4f
    }

private const val ShakeDebounceMs = 1_400L

private fun installDownloadedApk(context: Context, apkPath: String): Boolean {
    val apkFile = File(apkPath)
    if (!apkFile.exists()) return false
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
    )
    val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        putExtra(Intent.EXTRA_RETURN_RESULT, false)
    }
    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val launchIntent = listOf(installIntent, fallbackIntent).firstOrNull { intent ->
        intent.resolveActivity(context.packageManager) != null
    } ?: return false
    context.startActivity(launchIntent)
    return true
}
