package com.navilive.android.ui.screens

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Stop
import com.navilive.android.guidance.NavigationSoundCue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.navilive.android.R
import com.navilive.android.model.ActiveNavigationState
import com.navilive.android.model.AnnouncementCadenceMode
import com.navilive.android.model.AppUpdatePhase
import com.navilive.android.model.AppUpdateState
import com.navilive.android.model.DiagnosticsState
import com.navilive.android.model.GeoPoint
import com.navilive.android.model.HeadingState
import com.navilive.android.model.NearbyPoiCacheMode
import com.navilive.android.model.NearbyPoiCacheState
import com.navilive.android.model.Place
import com.navilive.android.model.RouteSummary
import com.navilive.android.model.SettingsState
import com.navilive.android.model.ShakeStrength
import com.navilive.android.model.SharedProductRules
import com.navilive.android.model.SoundCueTheme
import com.navilive.android.model.SpeechOutputMode
import com.navilive.android.model.UpdateChannel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class BannerTone {
    Info,
    Success,
    Warning,
    Critical,
}

private enum class SettingsDestination {
    Root,
    Guidance,
    LocalSearch,
    Sounds,
    Speech,
    App,
    Diagnostics,
}

private data class StatusPresentation(
    val title: String,
    val message: String,
    val tone: BannerTone,
)

private val WarningContainer = Color(0xFFFFEDC2)
private val OnWarningContainer = Color(0xFF3B2F04)
private val SuccessContainer = Color(0xFFDDF4E0)
private val OnSuccessContainer = Color(0xFF14311A)
private const val SupportBaseUrl = "https://paypal.me/KazimierzParzych"
private val SupportQuickAmounts = listOf(5, 10, 20, 50)

val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalOpenVisualAssistance = staticCompositionLocalOf<(() -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    title: String,
    showBack: Boolean,
    showTitle: Boolean = true,
    showSettingsAction: Boolean = true,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val openSettings = LocalOpenSettings.current
    val openVisualAssistance = LocalOpenVisualAssistance.current
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { isTraversalGroup = true },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { traversalIndex = -1f },
                title = {
                    if (showTitle) {
                        Text(
                            text = title,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                },
                navigationIcon = {
                    if (showBack && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = { actions?.invoke() },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding(),
            ) {
                content(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = if (showSettingsAction && (openSettings != null || openVisualAssistance != null)) 96.dp else 12.dp,
                        ),
                )
                if (showSettingsAction && (openSettings != null || openVisualAssistance != null)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        openVisualAssistance?.let { action ->
                            FloatingActionButton(
                                onClick = action,
                                modifier = Modifier.semantics { traversalIndex = 0.9f },
                            ) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.action_visual_assistance),
                                )
                            }
                        }
                        openSettings?.let { action ->
                            FloatingActionButton(
                                onClick = action,
                                modifier = Modifier.semantics { traversalIndex = 1f },
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_settings),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartScreen(
    currentLocation: String,
    statusMessage: String,
    lastRoutePlaceId: String?,
    quickFavorites: List<Place>,
    accuracyMeters: Float?,
    hasLocationPermission: Boolean,
    isForegroundTracking: Boolean,
    onSearch: () -> Unit,
    onCurrentPosition: () -> Unit,
    onFavorites: () -> Unit,
    onResumeLastRoute: (String) -> Unit,
    onOpenQuickFavorite: (String) -> Unit,
    onGrantLocationPermission: () -> Unit,
    onToggleTracking: () -> Unit,
) {
    val locationStatus = locationStatus(
        hasLocationPermission = hasLocationPermission,
        accuracyMeters = accuracyMeters,
        isForegroundTracking = isForegroundTracking,
        currentLocation = currentLocation,
    )

    ScreenScaffold(
        title = stringResource(R.string.app_name),
        showBack = false,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                title = locationStatus.title,
                message = locationStatus.message,
                tone = locationStatus.tone,
            )

            FilledTonalButton(
                onClick = if (hasLocationPermission) onToggleTracking else onGrantLocationPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    !hasLocationPermission -> stringResource(R.string.start_button_grant_location_access)
                    isForegroundTracking -> stringResource(R.string.start_button_stop_live_tracking)
                    else -> stringResource(R.string.start_button_start_live_tracking)
                }
                Text(label)
            }

            PrimaryActionButton(
                label = stringResource(R.string.start_primary_search),
                icon = Icons.Filled.Search,
                onClick = onSearch,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryActionButton(
                    label = stringResource(R.string.start_current_position),
                    icon = Icons.Filled.Map,
                    onClick = onCurrentPosition,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    label = stringResource(R.string.start_favorites),
                    icon = Icons.Filled.Favorite,
                    onClick = onFavorites,
                    modifier = Modifier.weight(1f),
                )
            }

            if (lastRoutePlaceId != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionHeading(stringResource(R.string.start_last_route_title))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrimaryActionButton(
                            label = stringResource(R.string.start_resume_last_route),
                            icon = Icons.Filled.Navigation,
                            onClick = { onResumeLastRoute(lastRoutePlaceId) },
                        )
                    }
                }
            }

            if (quickFavorites.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SectionHeading(stringResource(R.string.start_quick_favorites_title))
                        quickFavorites.take(3).forEach { place ->
                            OutlinedButton(
                                onClick = { onOpenQuickFavorite(place.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = if (place.id == "home") Icons.Filled.Home else Icons.Filled.Favorite,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(place.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = place.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun BootstrapScreen() {
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        showBack = false,
        showSettingsAction = false,
    ) { modifier ->
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                title = stringResource(R.string.bootstrap_loading_title),
                message = stringResource(R.string.bootstrap_loading_message),
                tone = BannerTone.Info,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.onboarding_title), showBack = false) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(
                title = stringResource(R.string.tutorial_welcome_status_title),
                message = stringResource(R.string.tutorial_welcome_status_message),
                tone = BannerTone.Success,
            )

            TutorialOverviewCards()

            Spacer(modifier = Modifier.weight(1f, fill = false))

            PrimaryActionButton(
                label = stringResource(R.string.onboarding_continue),
                icon = Icons.Filled.Navigation,
                onClick = onContinue,
            )
        }
    }
}

@Composable
fun TutorialScreen(
    showOnStartup: Boolean,
    onShowOnStartupChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    ScreenScaffold(
        title = stringResource(R.string.tutorial_navigation_title),
        showBack = true,
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(
                title = stringResource(R.string.tutorial_revisit_status_title),
                message = stringResource(R.string.tutorial_revisit_status_message),
                tone = BannerTone.Info,
            )

            TutorialOverviewCards()
            TutorialStartupCard(
                showOnStartup = showOnStartup,
                onShowOnStartupChange = onShowOnStartupChange,
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            PrimaryActionButton(
                label = stringResource(R.string.tutorial_done),
                icon = Icons.Filled.Navigation,
                onClick = onDone,
            )
        }
    }
}

@Composable
fun HelpPrivacyScreen(
    showTutorialOnStartup: Boolean,
    onShowTutorialOnStartupChange: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenSupportUrl: (String) -> Unit,
    onOpenVisualAssistance: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.settings_help_privacy_title),
        showBack = true,
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TutorialSettingsCard(
                showOnStartup = showTutorialOnStartup,
                onShowOnStartupChange = onShowTutorialOnStartupChange,
                onOpenTutorial = onOpenTutorial,
            )
            VisualAssistanceCard(
                onOpenVisualAssistance = onOpenVisualAssistance,
            )
            SupportDevelopmentCard(
                onOpenSupportUrl = onOpenSupportUrl,
            )
            PrivacyInfoCard()
        }
    }
}

@Composable
fun PermissionsScreen(
    hasLocationPermission: Boolean,
    onGrantPermission: () -> Unit,
    onContinueWithoutPermission: () -> Unit,
) {
    val status = if (hasLocationPermission) {
        StatusPresentation(
            title = stringResource(R.string.permissions_ready_title),
            message = stringResource(R.string.permissions_ready_message),
            tone = BannerTone.Success,
        )
    } else {
        StatusPresentation(
            title = stringResource(R.string.permissions_request_title),
            message = stringResource(R.string.permissions_request_message),
            tone = BannerTone.Warning,
        )
    }

    ScreenScaffold(title = stringResource(R.string.permissions_title), showBack = false) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(
                title = status.title,
                message = status.message,
                tone = status.tone,
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeading(stringResource(R.string.permissions_why_it_matters_title))
                    LabelValue(stringResource(R.string.permissions_current_position_label), stringResource(R.string.permissions_current_position_message))
                    LabelValue(stringResource(R.string.permissions_live_tracking_label), stringResource(R.string.permissions_live_tracking_message))
                    LabelValue(stringResource(R.string.permissions_recalculation_label), stringResource(R.string.permissions_recalculation_message))
                }
            }

            PrimaryActionButton(
                label = if (hasLocationPermission) {
                    stringResource(R.string.permissions_open_start_screen)
                } else {
                    stringResource(R.string.permissions_grant_location_access)
                },
                icon = Icons.Filled.LocationSearching,
                onClick = onGrantPermission,
            )

            SecondaryActionButton(
                label = stringResource(R.string.permissions_continue_for_now),
                icon = Icons.Filled.Navigation,
                onClick = onContinueWithoutPermission,
            )
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    results: List<Place>,
    isLoading: Boolean,
    hasSubmittedSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    favoriteIds: Set<String>,
    onSelectPlace: (String) -> Unit,
    onShowRoute: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val searchFieldLabel = stringResource(R.string.search_field_label)
    val submitSearch = {
        onSubmitSearch()
        focusManager.clearFocus()
    }

    ScreenScaffold(
        title = stringResource(R.string.search_title),
        showBack = true,
        showTitle = false,
        onBack = onBack,
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                AccessibleSearchTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = searchFieldLabel,
                    onSearch = submitSearch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PrimaryActionButton(
                    label = stringResource(R.string.search_title),
                    icon = Icons.Filled.Search,
                    onClick = submitSearch,
                )
            }
            if (isLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (results.isNotEmpty() || (query.isNotBlank() && hasSubmittedSearch)) {
                item {
                    SectionHeading(
                        if (query.isBlank()) {
                            stringResource(R.string.search_suggested_places)
                        } else {
                            stringResource(R.string.search_results)
                        },
                    )
                }
            }
            if (results.isEmpty()) {
                if (query.isNotBlank() && hasSubmittedSearch && !isLoading) {
                    item {
                        EmptyStateCard(
                            title = stringResource(R.string.search_empty_results_title),
                        )
                    }
                }
            } else {
                items(results, key = { it.id }) { place ->
                    val timingLabel = placeTimingLabel(place)
                    val placeAccessibilityLabel = remember(place.name, place.address, timingLabel) {
                        listOf(place.name, place.address, timingLabel)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .joinToString(separator = ". ")
                    }
                    val detailsActionLabel = stringResource(R.string.place_details_title)
                    val routeActionLabel = stringResource(R.string.place_details_show_route)
                    val favoriteActionLabel = if (place.id in favoriteIds) {
                        stringResource(R.string.common_remove_from_favorites)
                    } else {
                        stringResource(R.string.common_save_favorite)
                    }
                    ElevatedCard(
                        onClick = { onSelectPlace(place.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = placeAccessibilityLabel
                                onClick(label = detailsActionLabel) {
                                    onSelectPlace(place.id)
                                    true
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction(routeActionLabel) {
                                        onShowRoute(place.id)
                                        true
                                    },
                                    CustomAccessibilityAction(favoriteActionLabel) {
                                        onToggleFavorite(place.id)
                                        true
                                    },
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(place.name, fontWeight = FontWeight.SemiBold)
                            Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(timingLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDetailsScreen(
    place: Place,
    isFavorite: Boolean,
    onShowRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.place_details_title), showBack = true, onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    LabelValue(stringResource(R.string.label_address), place.address)
                    place.point?.let { point ->
                        LabelValue(stringResource(R.string.label_coordinates), point.coordinatesLabel())
                    }
                    if (place.walkDistanceMeters > 0 || place.walkEtaMinutes > 0) {
                        LabelValue(stringResource(R.string.label_walking_estimate), placeTimingLabel(place))
                    }
                    place.savedAccuracyMeters?.let { accuracy ->
                        LabelValue(
                            stringResource(R.string.label_saved_accuracy),
                            stringResource(R.string.current_position_accuracy, accuracy.roundToInt()),
                        )
                    }
                    place.savedAtMs?.let { savedAt ->
                        LabelValue(stringResource(R.string.label_saved_at), formatSavedTimestamp(savedAt))
                    }
                    place.phone?.let { LabelValue(stringResource(R.string.label_phone), it) }
                    place.website?.let { LabelValue(stringResource(R.string.label_website), it) }
                }
            }

            PrimaryActionButton(
                label = stringResource(R.string.place_details_show_route),
                icon = Icons.AutoMirrored.Filled.AssistantDirection,
                onClick = onShowRoute,
            )


            SecondaryActionButton(
                label = if (isFavorite) {
                    stringResource(R.string.common_remove_from_favorites)
                } else {
                    stringResource(R.string.common_save_favorite)
                },
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.BookmarkAdd,
                onClick = onToggleFavorite,
            )
        }
    }
}

@Composable
fun RouteSummaryScreen(
    place: Place,
    summary: RouteSummary,
    isFavorite: Boolean,
    isLoadingRoute: Boolean,
    onSaveFavorite: () -> Unit,
    onStartRoute: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.route_summary_title), showBack = true, onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    LabelValue(stringResource(R.string.label_travel_time), stringResource(R.string.format_eta_minutes, summary.etaMinutes))
                    LabelValue(stringResource(R.string.label_distance), stringResource(R.string.format_distance_meters, summary.distanceMeters))
                    LabelValue(stringResource(R.string.label_mode), summary.modeLabel)
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeading(stringResource(R.string.route_summary_guidance_preview))
                    summary.steps.forEachIndexed { index, step ->
                        Text(
                            text = stringResource(
                                R.string.format_step_preview_with_distance,
                                index + 1,
                                step.instruction,
                                stringResource(R.string.format_distance_meters, step.distanceMeters),
                            ),
                            color = if (index == 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            SecondaryActionButton(
                label = if (isFavorite) {
                    stringResource(R.string.route_summary_favorite_saved)
                } else {
                    stringResource(R.string.common_save_favorite)
                },
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.BookmarkAdd,
                onClick = onSaveFavorite,
            )

            PrimaryActionButton(
                label = if (isLoadingRoute) {
                    stringResource(R.string.route_summary_preparing_route)
                } else {
                    stringResource(R.string.route_summary_start_route)
                },
                icon = Icons.Filled.Navigation,
                enabled = !isLoadingRoute,
                onClick = onStartRoute,
            )
        }
    }
}

@Composable
fun HeadingAlignScreen(
    place: Place,
    headingState: HeadingState,
    onCheckHeading: () -> Unit,
    onProceed: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val status = if (headingState.isAligned) {
        StatusPresentation(
            title = stringResource(R.string.heading_confirmed_title),
            message = stringResource(R.string.heading_confirmed_message),
            tone = BannerTone.Success,
        )
    } else {
        StatusPresentation(
            title = stringResource(R.string.heading_set_title),
            message = headingState.instruction,
            tone = BannerTone.Warning,
        )
    }

    ScreenScaffold(title = stringResource(R.string.heading_title), showBack = true, onBack = onBack) { modifier ->
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(status.title, status.message, status.tone)

            Text(
                text = stringResource(R.string.heading_destination, place.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )

            Surface(
                modifier = Modifier.size(220.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(104.dp)
                            .rotate(headingState.arrowRotationDeg),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = headingState.instruction,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.weight(1f))

            SecondaryActionButton(
                label = stringResource(R.string.heading_check_alignment),
                icon = Icons.Filled.LocationSearching,
                onClick = onCheckHeading,
            )

            PrimaryActionButton(
                label = stringResource(R.string.heading_start_walking),
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                enabled = headingState.isAligned,
                onClick = onProceed,
            )

            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.heading_skip))
            }
        }
    }
}

@Composable
fun ActiveNavigationScreen(
    place: Place,
    state: ActiveNavigationState,
    hasLocationPermission: Boolean,
    accuracyMeters: Float?,
    onPauseResume: () -> Unit,
    onRepeatInstruction: () -> Unit,
    onRecalculate: () -> Unit,
    onReportProblem: () -> Unit,
    onArrived: () -> Unit,
    onStop: () -> Unit,
) {
    val status = navigationStatus(
        state = state,
        hasLocationPermission = hasLocationPermission,
        accuracyMeters = accuracyMeters,
    )

    ScreenScaffold(title = stringResource(R.string.active_navigation_title), showBack = false) { modifier ->
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(status.title, status.message, status.tone)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.active_navigation_to_destination, place.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = state.progressLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.currentInstruction,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(state.nextInstruction, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabelValue(
                        stringResource(R.string.active_navigation_distance_to_next),
                        stringResource(R.string.format_distance_meters, state.distanceToNextMeters),
                    )
                    LabelValue(
                        stringResource(R.string.active_navigation_remaining_distance),
                        stringResource(R.string.format_distance_meters, state.remainingDistanceMeters),
                    )
                    state.offRouteDistanceMeters?.let { deviation ->
                        LabelValue(
                            stringResource(R.string.active_navigation_distance_from_route),
                            stringResource(R.string.format_distance_meters, deviation),
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onRecalculate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRecalculating,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        state.isRecalculating -> stringResource(R.string.active_navigation_recalculating)
                        state.isOffRoute -> stringResource(R.string.active_navigation_recalculate)
                        else -> stringResource(R.string.active_navigation_need_new_route)
                    },
                )
            }

            OutlinedButton(
                onClick = onReportProblem,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.active_navigation_report_problem))
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryActionButton(
                    label = if (state.isPaused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause),
                    icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    onClick = onPauseResume,
                    modifier = Modifier.weight(1f),
                )
                SecondaryActionButton(
                    label = stringResource(R.string.common_repeat),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = onRepeatInstruction,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(
                onClick = onArrived,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.active_navigation_arrived))
            }

            PrimaryActionButton(
                label = stringResource(R.string.active_navigation_stop),
                icon = Icons.Filled.Stop,
                onClick = onStop,
            )
        }
    }
}

@Composable
fun CurrentPositionScreen(
    currentLocation: String,
    accuracyMeters: Float?,
    hasLocationPermission: Boolean,
    quickFavorites: List<Place>,
    onReadLocation: () -> Unit,
    onSaveCurrentLocationAsFavorite: (String) -> Unit,
    onSearch: () -> Unit,
    onPickFavorite: (String) -> Unit,
    onBack: () -> Unit,
) {
    val status = currentPositionStatus(hasLocationPermission, accuracyMeters)
    var showSaveDialog by remember { mutableStateOf(false) }
    var customFavoriteName by remember { mutableStateOf("") }
    val trimmedFavoriteName = customFavoriteName.trim()

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.current_position_save_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = customFavoriteName,
                    onValueChange = { customFavoriteName = it },
                    label = { Text(stringResource(R.string.current_position_save_dialog_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = trimmedFavoriteName.isNotBlank(),
                    onClick = {
                        onSaveCurrentLocationAsFavorite(trimmedFavoriteName)
                        customFavoriteName = ""
                        showSaveDialog = false
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customFavoriteName = ""
                        showSaveDialog = false
                    },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    ScreenScaffold(title = stringResource(R.string.current_position_title), showBack = true, onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(status.title, status.message, status.tone)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeading(stringResource(R.string.current_position_current_address))
                    Text(currentLocation)
                    accuracyMeters?.let { accuracy ->
                        Text(
                            text = stringResource(R.string.current_position_accuracy, accuracy.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            PrimaryActionButton(
                label = stringResource(R.string.current_position_read_aloud),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onReadLocation,
            )

            SecondaryActionButton(
                label = stringResource(R.string.current_position_save_current),
                icon = Icons.Filled.BookmarkAdd,
                onClick = { showSaveDialog = true },
            )

            SecondaryActionButton(
                label = stringResource(R.string.current_position_search_destination),
                icon = Icons.Filled.Search,
                onClick = onSearch,
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeading(stringResource(R.string.common_favorites))
                    if (quickFavorites.isEmpty()) {
                        EmptyStateCard(
                            title = stringResource(R.string.current_position_no_favorites_title),
                            message = stringResource(R.string.current_position_no_favorites_message),
                        )
                    } else {
                        quickFavorites.take(4).forEach { place ->
                            OutlinedButton(
                                onClick = { onPickFavorite(place.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = if (place.id == "home") Icons.Filled.Home else Icons.Filled.Favorite,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(place.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = place.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AccessibleSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val density = context.resources.displayMetrics.density
            val editText = TextInputEditText(context).apply {
                hint = null
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                minHeight = (56 * density).toInt()
                setText(value)
                setSelection(text?.length ?: 0)
                applyAccessibleSearchEditTextAnnouncement(this, label)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        currentOnValueChange(s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                setOnEditorActionListener { _, actionId, event ->
                    val isKeyboardEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_UP
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || isKeyboardEnter) {
                        currentOnSearch()
                        true
                    } else {
                        false
                    }
                }
            }

            TextInputLayout(context).apply {
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                isHintEnabled = true
                hint = label
                addView(
                    editText,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        },
        update = { inputLayout ->
            val editText = inputLayout.editText ?: return@AndroidView
            if (!editText.hasFocus() && editText.text?.toString().orEmpty() != value) {
                editText.setText(value)
                editText.setSelection(editText.text?.length ?: 0)
            }
            inputLayout.hint = label
            editText.setTextColor(textColor.toArgb())
            editText.setHintTextColor(hintColor.toArgb())
            applyAccessibleSearchEditTextAnnouncement(editText, label)
        },
    )
}

private fun applyAccessibleSearchEditTextAnnouncement(editText: EditText, label: String) {
    editText.contentDescription = null
    editText.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val currentText = editText.text?.toString().orEmpty().trim()
            info.contentDescription = null
            info.hintText = label
            info.text = if (currentText.isBlank()) label else "$label ($currentText)"
        }
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<Place>,
    onOpenFavoriteDetails: (String) -> Unit,
    onSelectFavorite: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onAddFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.favorites_title), showBack = true, onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {

            item {
                FilledTonalButton(
                    onClick = onAddFavorite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.favorites_add_from_search))
                }
            }
            if (favorites.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.favorites_empty_title),
                        message = stringResource(R.string.favorites_empty_message),
                    )
                }
            } else {
                items(favorites, key = { it.id }) { place ->
                    val timingLabel = placeTimingLabel(place)
                    val placeAccessibilityLabel = remember(place.name, place.address, timingLabel) {
                        listOf(place.name, place.address, timingLabel)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .joinToString(separator = ". ")
                    }
                    val detailsActionLabel = stringResource(R.string.favorites_open_details)
                    val routeActionLabel = stringResource(R.string.favorites_route)
                    val removeActionLabel = stringResource(R.string.favorites_remove)
                    ElevatedCard(
                        onClick = { onOpenFavoriteDetails(place.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = placeAccessibilityLabel
                                onClick(label = detailsActionLabel) {
                                    onOpenFavoriteDetails(place.id)
                                    true
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction(routeActionLabel) {
                                        onSelectFavorite(place.id)
                                        true
                                    },
                                    CustomAccessibilityAction(removeActionLabel) {
                                        onRemoveFavorite(place.id)
                                        true
                                    },
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(place.name, fontWeight = FontWeight.SemiBold)
                                Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(timingLabel)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics { },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SecondaryActionButton(
                                    label = stringResource(R.string.favorites_route),
                                    icon = Icons.Filled.Navigation,
                                    onClick = { onSelectFavorite(place.id) },
                                    modifier = Modifier.weight(1f),
                                )
                                SecondaryActionButton(
                                    label = stringResource(R.string.favorites_remove),
                                    icon = Icons.Filled.Stop,
                                    onClick = { onRemoveFavorite(place.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    updateState: AppUpdateState,
    diagnosticsState: DiagnosticsState,
    nearbyPoiCacheState: NearbyPoiCacheState,
    onOpenHelpPrivacy: () -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onShakeGestureEnabledChange: (Boolean) -> Unit,
    onShakeStrengthChange: (ShakeStrength) -> Unit,
    onSoundCuesChange: (Boolean) -> Unit,
    onSoundCueVolumeChange: (Int) -> Unit,
    onSoundCueThemeChange: (SoundCueTheme) -> Unit,
    onPreviewSoundCue: (NavigationSoundCue) -> Unit,
    onAutoRecalculateChange: (Boolean) -> Unit,
    onJunctionAlertChange: (Boolean) -> Unit,
    onTurnByTurnChange: (Boolean) -> Unit,
    onAnnouncementCadenceModeChange: (AnnouncementCadenceMode) -> Unit,
    onSearchRadiusChange: (Int) -> Unit,
    onSearchResultLimitChange: (Int) -> Unit,
    onNearbyPoiCacheModeChange: (NearbyPoiCacheMode) -> Unit,
    onNearbyPoiCacheRadiusChange: (Int) -> Unit,
    onRefreshNearbyPoiCache: () -> Unit,
    onClearNearbyPoiCache: () -> Unit,
    onPedestrianCrossingAlertsChange: (Boolean) -> Unit,
    onUpdateChannelChange: (UpdateChannel) -> Unit,
    onSpeechOutputModeChange: (SpeechOutputMode) -> Unit,
    onSystemTtsEngineChange: (String?) -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onSpeechRateChange: (Int) -> Unit,
    onSpeechVolumeChange: (Int) -> Unit,
    onPreviewSpeech: () -> Unit,
    onPrimaryUpdateAction: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onOpenProjectRepository: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onShareDiagnostics: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var destination by remember { mutableStateOf(SettingsDestination.Root) }
    BackHandler(enabled = destination != SettingsDestination.Root) {
        destination = SettingsDestination.Root
    }

    val title = when (destination) {
        SettingsDestination.Root -> stringResource(R.string.settings_title)
        SettingsDestination.Guidance -> stringResource(R.string.settings_group_guidance_title)
        SettingsDestination.LocalSearch -> stringResource(R.string.settings_group_local_search_title)
        SettingsDestination.Sounds -> stringResource(R.string.settings_group_sounds_title)
        SettingsDestination.Speech -> stringResource(R.string.settings_group_speech_title)
        SettingsDestination.App -> stringResource(R.string.settings_group_app_title)
        SettingsDestination.Diagnostics -> stringResource(R.string.settings_group_diagnostics_title)
    }

    ScreenScaffold(
        title = title,
        showBack = true,
        showSettingsAction = false,
        onBack = {
            if (destination == SettingsDestination.Root) {
                onBack()
            } else {
                destination = SettingsDestination.Root
            }
        },
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (destination) {
                SettingsDestination.Root -> {
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_guidance_title),
                        icon = Icons.AutoMirrored.Filled.AssistantDirection,
                        onClick = { destination = SettingsDestination.Guidance },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_local_search_title),
                        icon = Icons.Filled.Search,
                        onClick = { destination = SettingsDestination.LocalSearch },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_sounds_title),
                        icon = Icons.Filled.SurroundSound,
                        onClick = { destination = SettingsDestination.Sounds },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_speech_title),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = { destination = SettingsDestination.Speech },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_app_title),
                        icon = Icons.Filled.Settings,
                        onClick = { destination = SettingsDestination.App },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_diagnostics_title),
                        icon = Icons.Filled.Share,
                        onClick = { destination = SettingsDestination.Diagnostics },
                    )
                    SettingsNavigationCard(
                        title = stringResource(R.string.settings_group_help_title),
                        icon = Icons.Filled.Info,
                        onClick = onOpenHelpPrivacy,
                    )
                }
                SettingsDestination.Guidance -> {
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_voice_title),
                        checked = state.turnByTurnAnnouncements,
                        onCheckedChange = onTurnByTurnChange,
                    )
                    AnnouncementCadenceCard(
                        selectedMode = state.announcementCadenceMode,
                        enabled = state.turnByTurnAnnouncements,
                        onModeChange = onAnnouncementCadenceModeChange,
                    )
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_auto_recalculate_title),
                        checked = state.autoRecalculate,
                        onCheckedChange = onAutoRecalculateChange,
                    )
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_junction_alerts_title),
                        checked = state.junctionAlerts,
                        onCheckedChange = onJunctionAlertChange,
                    )
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_pedestrian_crossing_alerts_title),
                        checked = state.pedestrianCrossingAlerts,
                        onCheckedChange = onPedestrianCrossingAlertsChange,
                    )
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_vibration_title),
                        checked = state.vibrationEnabled,
                        onCheckedChange = onVibrationChange,
                    )
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_shake_gesture_title),
                        checked = state.shakeGestureEnabled,
                        onCheckedChange = onShakeGestureEnabledChange,
                    )
                    ShakeStrengthCard(
                        selectedStrength = state.shakeStrength,
                        enabled = state.shakeGestureEnabled,
                        onStrengthChange = onShakeStrengthChange,
                    )
                }
                SettingsDestination.LocalSearch -> {
                    SearchRadiusCard(
                        radiusKm = state.searchRadiusKm,
                        onCommit = onSearchRadiusChange,
                    )
                    SearchResultLimitCard(
                        resultLimit = state.searchResultLimit,
                        onCommit = onSearchResultLimitChange,
                    )
                    NearbyPoiCacheModeCard(
                        selectedMode = state.nearbyPoiCacheMode,
                        onModeChange = onNearbyPoiCacheModeChange,
                    )
                    NearbyPoiCacheRadiusCard(
                        radiusKm = state.nearbyPoiCacheRadiusKm,
                        enabled = state.nearbyPoiCacheMode != NearbyPoiCacheMode.Disabled,
                        onCommit = onNearbyPoiCacheRadiusChange,
                    )
                    NearbyPoiCacheStatusCard(
                        cacheState = nearbyPoiCacheState,
                        enabled = state.nearbyPoiCacheMode != NearbyPoiCacheMode.Disabled,
                        onRefresh = onRefreshNearbyPoiCache,
                        onClear = onClearNearbyPoiCache,
                    )
                }
                SettingsDestination.Sounds -> {
                    SettingsToggleCard(
                        title = stringResource(R.string.settings_sound_cues_title),
                        checked = state.soundCuesEnabled,
                        onCheckedChange = onSoundCuesChange,
                    )
                    SoundCueThemeMenuCard(
                        selectedTheme = state.soundCueTheme,
                        enabled = state.soundCuesEnabled,
                        onThemeChange = onSoundCueThemeChange,
                    )
                    VoiceSliderCard(
                        title = stringResource(R.string.settings_sound_cue_volume_title),
                        value = state.soundCueVolumePercent,
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = state.soundCuesEnabled,
                        disabledMessage = stringResource(R.string.settings_sound_cue_volume_disabled_message),
                        onCommit = onSoundCueVolumeChange,
                    )
                    SoundCueTutorialCard(onPreviewSoundCue = onPreviewSoundCue)
                }
                SettingsDestination.Speech -> {
                    VoiceOutputSettingsCard(
                        state = state,
                        onSpeechOutputModeChange = onSpeechOutputModeChange,
                        onSystemTtsEngineChange = onSystemTtsEngineChange,
                        onOpenSystemTtsSettings = onOpenSystemTtsSettings,
                    )
                    VoiceSliderCard(
                        title = stringResource(R.string.settings_speech_rate_title),
                        value = state.speechRatePercent,
                        valueRange = 50f..200f,
                        steps = 29,
                        enabled = true,
                        disabledMessage = stringResource(R.string.settings_speech_controls_disabled_message),
                        onCommit = onSpeechRateChange,
                    )
                    VoiceSliderCard(
                        title = stringResource(R.string.settings_speech_volume_title),
                        value = state.speechVolumePercent,
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = true,
                        disabledMessage = stringResource(R.string.settings_speech_controls_disabled_message),
                        onCommit = onSpeechVolumeChange,
                    )
                    val previewSpeechLabel = stringResource(R.string.settings_speech_preview_button)
                    FilledTonalButton(
                        onClick = onPreviewSpeech,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = previewSpeechLabel
                                role = Role.Button
                                onClick(label = previewSpeechLabel) {
                                    onPreviewSpeech()
                                    true
                                }
                            },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = previewSpeechLabel,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
                SettingsDestination.App -> {
                    LanguageSettingsCard(language = state.language)
                    AppUpdateCard(
                        settingsState = state,
                        updateState = updateState,
                        onUpdateChannelChange = onUpdateChannelChange,
                        onPrimaryUpdateAction = onPrimaryUpdateAction,
                        onOpenReleasePage = onOpenReleasePage,
                    )
                    AppInfoCard(
                        versionLabel = updateState.currentVersionLabel,
                        buildLabel = updateState.currentBuildLabel,
                        onOpenProjectRepository = onOpenProjectRepository,
                    )
                }
                SettingsDestination.Diagnostics -> {
                    DiagnosticsSettingsCard(
                        diagnosticsState = diagnosticsState,
                        onExportDiagnostics = onExportDiagnostics,
                        onClearDiagnostics = onClearDiagnostics,
                        onShareDiagnostics = onShareDiagnostics,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageSettingsCard(language: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_language_title))
            LabelValue(
                stringResource(R.string.settings_language_detected_label),
                value = language,
            )
        }
    }
}

@Composable
private fun AppInfoCard(
    versionLabel: String,
    buildLabel: String,
    onOpenProjectRepository: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_about_title))
            LabelValue(
                label = stringResource(R.string.settings_updates_current_version),
                value = versionLabel,
            )
            LabelValue(
                label = stringResource(R.string.settings_updates_current_build),
                value = buildLabel,
            )
            OutlinedButton(
                onClick = onOpenProjectRepository,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_app_repository))
            }
        }
    }
}

@Composable
private fun DiagnosticsSettingsCard(
    diagnosticsState: DiagnosticsState,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onShareDiagnostics: (() -> Unit)?,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_debug_telemetry_title))
            LabelValue(stringResource(R.string.settings_buffered_events), diagnosticsState.eventCount.toString())
            LabelValue(stringResource(R.string.settings_active_session), diagnosticsState.activeSessionLabel)
            LabelValue(stringResource(R.string.settings_last_event), diagnosticsState.lastEventLabel)
            diagnosticsState.lastExportPath?.let { exportPath ->
                LabelValue(stringResource(R.string.settings_last_export), exportPath)
            }
            FilledTonalButton(
                onClick = onExportDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_export_telemetry))
            }
            if (diagnosticsState.lastExportPath != null && onShareDiagnostics != null) {
                OutlinedButton(
                    onClick = onShareDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_share_last_export))
                }
            }
            OutlinedButton(
                onClick = onClearDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_clear_telemetry))
            }
        }
    }
}

@Composable
private fun AnnouncementCadenceCard(
    selectedMode: AnnouncementCadenceMode,
    enabled: Boolean,
    onModeChange: (AnnouncementCadenceMode) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_announcement_cadence_title))
            SelectableOptionRow(
                title = stringResource(R.string.settings_announcement_cadence_distance_title),
                selected = selectedMode == AnnouncementCadenceMode.Distance,
                onSelect = {
                    if (enabled) {
                        onModeChange(AnnouncementCadenceMode.Distance)
                    }
                },
            )
            SelectableOptionRow(
                title = stringResource(R.string.settings_announcement_cadence_time_title),
                selected = selectedMode == AnnouncementCadenceMode.Time,
                onSelect = {
                    if (enabled) {
                        onModeChange(AnnouncementCadenceMode.Time)
                    }
                },
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.settings_announcement_cadence_disabled_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShakeStrengthCard(
    selectedStrength: ShakeStrength,
    enabled: Boolean,
    onStrengthChange: (ShakeStrength) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_shake_strength_title))
            SelectableOptionRow(
                title = stringResource(R.string.settings_shake_strength_light_title),
                selected = selectedStrength == ShakeStrength.Light,
                onSelect = {
                    if (enabled) onStrengthChange(ShakeStrength.Light)
                },
            )
            SelectableOptionRow(
                title = stringResource(R.string.settings_shake_strength_medium_title),
                selected = selectedStrength == ShakeStrength.Medium,
                onSelect = {
                    if (enabled) onStrengthChange(ShakeStrength.Medium)
                },
            )
            SelectableOptionRow(
                title = stringResource(R.string.settings_shake_strength_strong_title),
                selected = selectedStrength == ShakeStrength.Strong,
                onSelect = {
                    if (enabled) onStrengthChange(ShakeStrength.Strong)
                },
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.settings_shake_strength_disabled_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchRadiusCard(
    radiusKm: Int,
    onCommit: (Int) -> Unit,
) {
    val minRadius = SharedProductRules.Search.minimumRadiusKm
    val maxRadius = SharedProductRules.Search.maximumRadiusKm
    var sliderValue by remember(radiusKm) {
        mutableFloatStateOf(radiusKm.coerceIn(minRadius, maxRadius).toFloat())
    }

    fun commitValue(incoming: Float) {
        val normalized = incoming.roundToInt().coerceIn(minRadius, maxRadius)
        sliderValue = normalized.toFloat()
        if (normalized != radiusKm) {
            onCommit(normalized)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val title = stringResource(R.string.settings_search_radius_title)
            val radiusLabel = stringResource(R.string.format_search_radius_value, sliderValue.roundToInt())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clearAndSetSemantics { },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = radiusLabel,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { incoming -> commitValue(incoming) },
                valueRange = minRadius.toFloat()..maxRadius.toFloat(),
                steps = (maxRadius - minRadius - 1).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title + String(charArrayOf(':', ' ')) + radiusLabel
                        stateDescription = radiusLabel
                    },
            )
        }
    }
}

@Composable
private fun SearchResultLimitCard(
    resultLimit: Int,
    onCommit: (Int) -> Unit,
) {
    val minLimit = SharedProductRules.Search.minimumResultLimit
    val maxLimit = SharedProductRules.Search.maximumResultLimit
    var sliderValue by remember(resultLimit) {
        mutableFloatStateOf(resultLimit.coerceIn(minLimit, maxLimit).toFloat())
    }

    fun commitValue(incoming: Float) {
        val normalized = incoming.roundToInt().coerceIn(minLimit, maxLimit)
        sliderValue = normalized.toFloat()
        if (normalized != resultLimit) {
            onCommit(normalized)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val title = stringResource(R.string.settings_search_result_limit_title)
            val limitLabel = stringResource(R.string.format_search_result_limit_value, sliderValue.roundToInt())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clearAndSetSemantics { },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = limitLabel,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { incoming -> commitValue(incoming) },
                valueRange = minLimit.toFloat()..maxLimit.toFloat(),
                steps = (maxLimit - minLimit - 1).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title + String(charArrayOf(':', ' ')) + limitLabel
                        stateDescription = limitLabel
                    },
            )
        }
    }
}

@Composable
private fun NearbyPoiCacheModeCard(
    selectedMode: NearbyPoiCacheMode,
    onModeChange: (NearbyPoiCacheMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_nearby_poi_cache_mode_title)
    val selectedLabel = nearbyPoiCacheModeLabel(selectedMode)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(16.dp)
                    .semantics {
                        contentDescription = title + String(charArrayOf(':', ' ')) + selectedLabel
                        role = Role.Button
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clearAndSetSemantics { },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedLabel,
                    modifier = Modifier.clearAndSetSemantics { },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                NearbyPoiCacheMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(nearbyPoiCacheModeLabel(mode)) },
                        leadingIcon = {
                            RadioButton(
                                selected = mode == selectedMode,
                                onClick = null,
                            )
                        },
                        onClick = {
                            expanded = false
                            onModeChange(mode)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyPoiCacheRadiusCard(
    radiusKm: Int,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    val minRadius = SharedProductRules.Search.minimumRadiusKm
    val maxRadius = 5
    var sliderValue by remember(radiusKm) {
        mutableFloatStateOf(radiusKm.coerceIn(minRadius, maxRadius).toFloat())
    }

    fun commitValue(incoming: Float) {
        val normalized = incoming.roundToInt().coerceIn(minRadius, maxRadius)
        sliderValue = normalized.toFloat()
        if (normalized != radiusKm) {
            onCommit(normalized)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val title = stringResource(R.string.settings_nearby_poi_cache_radius_title)
            val radiusLabel = stringResource(R.string.format_search_radius_value, sliderValue.roundToInt())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clearAndSetSemantics { },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = radiusLabel,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { incoming -> commitValue(incoming) },
                valueRange = minRadius.toFloat()..maxRadius.toFloat(),
                steps = (maxRadius - minRadius - 1).coerceAtLeast(0),
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title + String(charArrayOf(':', ' ')) + radiusLabel
                        stateDescription = radiusLabel
                    },
            )
        }
    }
}

@Composable
private fun NearbyPoiCacheStatusCard(
    cacheState: NearbyPoiCacheState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val status = when {
        cacheState.isRefreshing -> stringResource(R.string.nearby_poi_cache_status_refreshing)
        cacheState.cachedPlaceCount > 0 -> stringResource(
            R.string.format_nearby_poi_cache_status_saved,
            cacheState.cachedPlaceCount,
        )
        else -> stringResource(R.string.nearby_poi_cache_status_empty)
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = status,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = enabled && !cacheState.isRefreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_nearby_poi_cache_refresh_now))
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = cacheState.cachedPlaceCount > 0 && !cacheState.isRefreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_nearby_poi_cache_clear))
                }
            }
        }
    }
}

@Composable
private fun SoundCueThemeMenuCard(
    selectedTheme: SoundCueTheme,
    enabled: Boolean,
    onThemeChange: (SoundCueTheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_sound_theme_title)
    val selectedLabel = soundCueThemeLabel(selectedTheme)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(16.dp)
                    .semantics {
                        role = Role.Button
                        stateDescription = selectedLabel
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.SurroundSound,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                SoundCueTheme.entries.forEach { theme ->
                    val label = soundCueThemeLabel(theme)
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            if (theme == selectedTheme) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            }
                        },
                        onClick = {
                            onThemeChange(theme)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun nearbyPoiCacheModeLabel(mode: NearbyPoiCacheMode): String {
    return when (mode) {
        NearbyPoiCacheMode.Enabled -> stringResource(R.string.settings_nearby_poi_cache_mode_enabled)
        NearbyPoiCacheMode.WifiOnly -> stringResource(R.string.settings_nearby_poi_cache_mode_wifi_only)
        NearbyPoiCacheMode.Disabled -> stringResource(R.string.settings_nearby_poi_cache_mode_disabled)
    }
}

@Composable
private fun soundCueThemeLabel(theme: SoundCueTheme): String {
    return when (theme) {
        SoundCueTheme.Standard -> stringResource(R.string.settings_sound_theme_standard)
        SoundCueTheme.Tetris -> stringResource(R.string.settings_sound_theme_tetris)
        SoundCueTheme.Cosmic -> stringResource(R.string.settings_sound_theme_cosmic)
    }
}

@Composable
private fun SoundCueTutorialCard(
    onPreviewSoundCue: (NavigationSoundCue) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_countdown_title),
                cue = NavigationSoundCue.Countdown,
                onPreviewSoundCue = onPreviewSoundCue,
            )
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_turn_now_title),
                cue = NavigationSoundCue.TurnNow,
                onPreviewSoundCue = onPreviewSoundCue,
            )
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_pedestrian_crossing_title),
                cue = NavigationSoundCue.PedestrianCrossing,
                onPreviewSoundCue = onPreviewSoundCue,
            )
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_warning_title),
                cue = NavigationSoundCue.Warning,
                onPreviewSoundCue = onPreviewSoundCue,
            )
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_success_title),
                cue = NavigationSoundCue.Success,
                onPreviewSoundCue = onPreviewSoundCue,
            )
            SoundCuePreviewRow(
                title = stringResource(R.string.settings_sound_cue_arrival_title),
                cue = NavigationSoundCue.Arrival,
                onPreviewSoundCue = onPreviewSoundCue,
            )
        }
    }
}

@Composable
private fun SoundCuePreviewRow(
    title: String,
    cue: NavigationSoundCue,
    onPreviewSoundCue: (NavigationSoundCue) -> Unit,
) {
    val playLabel = stringResource(R.string.settings_sound_cue_play)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onPreviewSoundCue(cue) }
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (cue) {
                NavigationSoundCue.Warning -> Icons.Filled.Campaign
                else -> Icons.Filled.SurroundSound
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(playLabel)
        }
    }
}

@Composable
private fun TutorialOverviewCards() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.tutorial_flow_title))
            LabelValue(stringResource(R.string.tutorial_search_label), stringResource(R.string.tutorial_search_message))
            LabelValue(stringResource(R.string.tutorial_route_label), stringResource(R.string.tutorial_route_message))
            LabelValue(stringResource(R.string.tutorial_guidance_label), stringResource(R.string.tutorial_guidance_message))
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.tutorial_tools_title))
            LabelValue(stringResource(R.string.tutorial_actions_label), stringResource(R.string.tutorial_actions_message))
            LabelValue(stringResource(R.string.tutorial_safety_label), stringResource(R.string.tutorial_safety_message))
        }
    }
}

@Composable
private fun TutorialStartupCard(
    showOnStartup: Boolean,
    onShowOnStartupChange: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.tutorial_startup_section_title))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tutorial_show_on_start_title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = showOnStartup,
                    onCheckedChange = onShowOnStartupChange,
                )
            }
        }
    }
}

@Composable
private fun TutorialSettingsCard(
    showOnStartup: Boolean,
    onShowOnStartupChange: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.tutorial_navigation_title))
            FilledTonalButton(
                onClick = onOpenTutorial,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_help_privacy_tutorial_open_button))
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tutorial_show_on_start_title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = showOnStartup,
                    onCheckedChange = onShowOnStartupChange,
                )
            }
        }
    }
}

@Composable
private fun VisualAssistanceCard(
    onOpenVisualAssistance: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_visual_assistance_section_title))
            Text(
                text = stringResource(R.string.settings_visual_assistance_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryActionButton(
                label = stringResource(R.string.settings_visual_assistance_open_button),
                icon = Icons.Filled.Visibility,
                onClick = onOpenVisualAssistance,
            )
        }
    }
}
@Composable
private fun SupportDevelopmentCard(
    onOpenSupportUrl: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var customAmountInput by remember { mutableStateOf("") }
    var localStatusMessage by remember { mutableStateOf<String?>(null) }
    val copySuccessMessage = stringResource(R.string.settings_support_copy_success)
    val invalidAmountMessage = stringResource(R.string.settings_support_custom_invalid)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_support_section_title))
            Text(
                text = stringResource(R.string.settings_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_support_amounts_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SupportQuickAmounts.chunked(2).forEach { amountRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    amountRow.forEach { amount ->
                        Button(
                            onClick = {
                                localStatusMessage = null
                                onOpenSupportUrl(supportUrlForAmount(amount.toString()))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.format_settings_support_amount_button, amount))
                        }
                    }
                    if (amountRow.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_support_custom_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = customAmountInput,
                onValueChange = { customAmountInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.settings_support_custom_placeholder))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            FilledTonalButton(
                onClick = {
                    val normalizedAmount = normalizeSupportAmount(customAmountInput)
                    if (normalizedAmount == null) {
                        localStatusMessage = invalidAmountMessage
                    } else {
                        localStatusMessage = null
                        onOpenSupportUrl(supportUrlForAmount(normalizedAmount))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_support_custom_button))
            }
            OutlinedButton(
                onClick = {
                    localStatusMessage = null
                    onOpenSupportUrl(SupportBaseUrl)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_support_other_currency_button))
            }
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(SupportBaseUrl))
                    localStatusMessage = copySuccessMessage
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_support_copy_link))
            }
            localStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrivacyInfoCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_privacy_section_title))
            PrivacyInfoBlock(
                title = stringResource(R.string.settings_privacy_local_title),
                body = stringResource(R.string.settings_privacy_local_body),
            )
            HorizontalDivider()
            PrivacyInfoBlock(
                title = stringResource(R.string.settings_privacy_online_title),
                body = stringResource(R.string.settings_privacy_online_body),
            )
            HorizontalDivider()
            PrivacyInfoBlock(
                title = stringResource(R.string.settings_privacy_telemetry_title),
                body = stringResource(R.string.settings_privacy_telemetry_body),
            )
        }
    }
}

@Composable
private fun PrivacyInfoBlock(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun normalizeSupportAmount(input: String): String? {
    val normalized = input.trim().replace(',', '.')
    if (!normalized.matches(Regex("\\d+(\\.\\d{1,2})?"))) {
        return null
    }
    val amount = normalized.toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) {
        return null
    }
    return amount.stripTrailingZeros().toPlainString()
}

private fun supportUrlForAmount(amount: String): String = "$SupportBaseUrl/${amount}PLN"

@Composable
fun ArrivalScreen(
    place: Place,
    onFinish: () -> Unit,
    onReverseRoute: () -> Unit,
    onSaveFavorite: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.arrival_title), showBack = false) { modifier ->
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(132.dp),
                shape = CircleShape,
                color = SuccessContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = OnSuccessContainer,
                    )
                }
            }

            Text(
                text = stringResource(R.string.arrival_message),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.weight(1f))

            PrimaryActionButton(
                label = stringResource(R.string.arrival_finish),
                icon = Icons.Filled.CheckCircle,
                onClick = onFinish,
            )

            SecondaryActionButton(
                label = stringResource(R.string.arrival_reverse_route),
                icon = Icons.Filled.Navigation,
                onClick = onReverseRoute,
            )

            SecondaryActionButton(
                label = stringResource(R.string.common_save_favorite),
                icon = Icons.Filled.Favorite,
                onClick = onSaveFavorite,
            )
        }
    }
}

@Composable
fun NotFoundScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.not_found_title), showBack = true, onBack = onBack) { modifier ->
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = stringResource(R.string.not_found_message_title),
                message = stringResource(R.string.not_found_message_body),
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        enabled = enabled,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun StatusCard(
    title: String,
    message: String = "",
    tone: BannerTone,
    modifier: Modifier = Modifier,
) {
    val (container, content) = bannerColors(tone)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$label. $value"
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}


@Composable
private fun EmptyStateCard(
    title: String,
    message: String = "",
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = listOf(title, message)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(separator = ". ")
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VoiceOutputSettingsCard(
    state: SettingsState,
    onSpeechOutputModeChange: (SpeechOutputMode) -> Unit,
    onSystemTtsEngineChange: (String?) -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
) {
    val sourceMessage = when (state.speechOutputMode) {
        SpeechOutputMode.System -> stringResource(R.string.settings_speech_source_system_status)
        SpeechOutputMode.ScreenReader -> {
            val screenReaderName = state.activeScreenReaderName
            if (state.isScreenReaderActive && !screenReaderName.isNullOrBlank()) {
                stringResource(
                    R.string.format_settings_speech_source_screen_reader_active,
                    screenReaderName,
                )
            } else {
                stringResource(R.string.settings_speech_source_screen_reader_fallback)
            }
        }
    }

    val screenReaderTitle = state.activeScreenReaderName?.takeIf { it.isNotBlank() }?.let { name ->
        stringResource(R.string.format_settings_speech_source_screen_reader_title_with_name, name)
    } ?: stringResource(R.string.settings_speech_source_screen_reader_title)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_speech_source_title))
            SpeechSourceOptionRow(
                title = screenReaderTitle,
                selected = state.speechOutputMode == SpeechOutputMode.ScreenReader,
                onSelect = { onSpeechOutputModeChange(SpeechOutputMode.ScreenReader) },
            )
            SpeechSourceOptionRow(
                title = stringResource(R.string.settings_speech_source_system_title),
                selected = state.speechOutputMode == SpeechOutputMode.System,
                onSelect = { onSpeechOutputModeChange(SpeechOutputMode.System) },
            )
            LabelValue(stringResource(R.string.settings_speech_source_active_label), sourceMessage)
            if (state.speechOutputMode == SpeechOutputMode.System) {
                SystemTtsEnginePicker(
                    state = state,
                    onSystemTtsEngineChange = onSystemTtsEngineChange,
                    onOpenSystemTtsSettings = onOpenSystemTtsSettings,
                )
            }
        }
    }
}

@Composable
private fun SpeechSourceOptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SystemTtsEnginePicker(
    state: SettingsState,
    onSystemTtsEngineChange: (String?) -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
) {
    val defaultEngineTitle = state.defaultSystemTtsEngineLabel?.takeIf { it.isNotBlank() }?.let { label ->
        stringResource(R.string.format_settings_system_tts_default_with_name, label)
    } ?: stringResource(R.string.settings_system_tts_default_title)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_system_tts_title))
            SelectableOptionRow(
                title = defaultEngineTitle,
                selected = state.selectedSystemTtsEnginePackage == null,
                onSelect = { onSystemTtsEngineChange(null) },
            )
            if (state.availableSystemTtsEngines.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_system_tts_none_detected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.availableSystemTtsEngines.forEach { engine ->
                    SelectableOptionRow(
                        title = engine.displayName,
                        selected = state.selectedSystemTtsEnginePackage == engine.packageName,
                        onSelect = { onSystemTtsEngineChange(engine.packageName) },
                    )
                }
            }
            state.activeSystemTtsEngineLabel?.takeIf { it.isNotBlank() }?.let { activeLabel ->
                LabelValue(
                    stringResource(R.string.settings_system_tts_active_label),
                    activeLabel,
                )
            }
            if (!state.isSelectedSystemTtsEngineAvailable) {
                Text(
                    text = stringResource(R.string.settings_system_tts_unavailable_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = onOpenSystemTtsSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_system_tts_open_settings))
            }
        }
    }
}

@Composable
private fun SelectableOptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VoiceSliderCard(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    disabledMessage: String,
    onCommit: (Int) -> Unit,
) {
    var sliderValue by remember(value, valueRange) {
        mutableFloatStateOf(value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive))
    }

    fun commitValue(incoming: Float) {
        val normalized = normalizeSliderValue(incoming, valueRange)
        sliderValue = normalized
        val normalizedInt = normalized.roundToInt()
        if (normalizedInt != value) {
            onCommit(normalizedInt)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sliderPercentText = stringResource(
                R.string.format_percent_value,
                sliderValue.roundToInt(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clearAndSetSemantics { },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sliderPercentText,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { incoming ->
                    commitValue(incoming)
                },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title + String(charArrayOf(':', ' ')) + sliderPercentText
                        stateDescription = sliderPercentText
                    },
            )
            if (!enabled) {
                Text(
                    text = disabledMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun normalizeSliderValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): Float {
    val rounded = (value / 5f).roundToInt() * 5f
    return rounded.coerceIn(valueRange.start, valueRange.endInclusive)
}

@Composable
private fun SettingsToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    settingsState: SettingsState,
    updateState: AppUpdateState,
    onUpdateChannelChange: (UpdateChannel) -> Unit,
    onPrimaryUpdateAction: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
) {
    val latestVersionLabel = updateState.latestVersionLabel
        ?: updateState.downloadedVersionLabel
        ?: stringResource(R.string.settings_updates_not_checked)
    val action = updatePrimaryActionPresentation(updateState)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_updates_title))
            LabelValue(
                label = stringResource(R.string.settings_updates_current_version),
                value = updateState.currentVersionLabel,
            )
            LabelValue(
                label = stringResource(R.string.settings_updates_current_build),
                value = updateState.currentBuildLabel,
            )
            LabelValue(
                label = stringResource(R.string.settings_updates_latest_version),
                value = latestVersionLabel,
            )
            UpdateChannelCard(
                selectedChannel = settingsState.updateChannel,
                onUpdateChannelChange = onUpdateChannelChange,
            )
            updateState.latestAssetName?.let { assetName ->
                LabelValue(
                    label = stringResource(R.string.settings_updates_asset),
                    value = assetName,
                )
            }
            updateState.latestReleaseName?.takeIf { it.isNotBlank() }?.let { releaseName ->
                LabelValue(
                    label = stringResource(R.string.settings_updates_release_name),
                    value = releaseName,
                )
            }
            Text(
                text = updateState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (updateState.phase == AppUpdatePhase.Downloading) {
                val progress = (updateState.downloadProgressPercent ?: 0).coerceIn(0, 100)
                LabelValue(
                    label = stringResource(R.string.settings_updates_progress),
                    value = stringResource(R.string.format_percent_value, progress),
                )
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (updateState.latestVersionLabel != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_updates_release_notes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = updateState.releaseNotes.ifBlank {
                            stringResource(R.string.settings_updates_release_notes_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(
                onClick = onPrimaryUpdateAction,
                modifier = Modifier.fillMaxWidth(),
                enabled = action.enabled,
            ) {
                Icon(action.icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(action.label)
            }
            updateState.releasePageUrl?.let { releaseUrl ->
                OutlinedButton(
                    onClick = { onOpenReleasePage(releaseUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_updates_open_release))
                }
            }
        }
    }
}

@Composable
private fun UpdateChannelCard(
    selectedChannel: UpdateChannel,
    onUpdateChannelChange: (UpdateChannel) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardTitle(stringResource(R.string.settings_updates_channel_title))
            SelectableOptionRow(
                title = stringResource(R.string.settings_updates_channel_stable_title),
                selected = selectedChannel == UpdateChannel.Stable,
                onSelect = { onUpdateChannelChange(UpdateChannel.Stable) },
            )
            SelectableOptionRow(
                title = stringResource(R.string.settings_updates_channel_beta_title),
                selected = selectedChannel == UpdateChannel.Beta,
                onSelect = { onUpdateChannelChange(UpdateChannel.Beta) },
            )
        }
    }
}

private data class UpdatePrimaryActionPresentation(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
)

@Composable
private fun updatePrimaryActionPresentation(updateState: AppUpdateState): UpdatePrimaryActionPresentation {
    return when (updateState.phase) {
        AppUpdatePhase.Checking -> UpdatePrimaryActionPresentation(
            label = stringResource(R.string.settings_updates_checking_action),
            icon = Icons.Filled.Refresh,
            enabled = false,
        )
        AppUpdatePhase.Downloading -> UpdatePrimaryActionPresentation(
            label = stringResource(R.string.settings_updates_downloading_action),
            icon = Icons.Filled.FileDownload,
            enabled = false,
        )
        AppUpdatePhase.Available -> UpdatePrimaryActionPresentation(
            label = stringResource(R.string.settings_updates_download_install),
            icon = Icons.Filled.FileDownload,
            enabled = true,
        )
        AppUpdatePhase.ReadyToInstall -> UpdatePrimaryActionPresentation(
            label = stringResource(
                if (updateState.canRequestPackageInstalls) {
                    R.string.settings_updates_install
                } else {
                    R.string.settings_updates_allow_installs
                },
            ),
            icon = if (updateState.canRequestPackageInstalls) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.Settings
            },
            enabled = true,
        )
        else -> UpdatePrimaryActionPresentation(
            label = stringResource(R.string.settings_updates_check),
            icon = Icons.Filled.Refresh,
            enabled = true,
        )
    }
}

@Composable
private fun bannerColors(tone: BannerTone): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        BannerTone.Info -> colors.secondaryContainer to colors.onSecondaryContainer
        BannerTone.Success -> SuccessContainer to OnSuccessContainer
        BannerTone.Warning -> WarningContainer to OnWarningContainer
        BannerTone.Critical -> colors.errorContainer to colors.onErrorContainer
    }
}

@Composable
private fun locationStatus(
    hasLocationPermission: Boolean,
    accuracyMeters: Float?,
    isForegroundTracking: Boolean,
    currentLocation: String,
): StatusPresentation {
    return when {
        !hasLocationPermission -> StatusPresentation(
            title = stringResource(R.string.location_status_permission_needed_title),
            message = stringResource(R.string.location_status_permission_needed_message),
            tone = BannerTone.Warning,
        )
        !isForegroundTracking -> StatusPresentation(
            title = stringResource(R.string.location_status_tracking_off_title),
            message = stringResource(R.string.location_status_tracking_off_message),
            tone = BannerTone.Info,
        )
        accuracyMeters == null -> StatusPresentation(
            title = stringResource(R.string.location_status_waiting_gps_title),
            message = stringResource(R.string.location_status_waiting_gps_message),
            tone = BannerTone.Warning,
        )
        accuracyMeters > 45f -> StatusPresentation(
            title = stringResource(R.string.location_status_gps_weak_title),
            message = stringResource(R.string.location_status_gps_weak_message),
            tone = BannerTone.Warning,
        )
        else -> {
            val accuracyLabel = stringResource(R.string.current_position_accuracy, accuracyMeters.roundToInt())
            StatusPresentation(
                title = stringResource(R.string.location_status_ready_title),
                message = "$currentLocation\n$accuracyLabel",
                tone = BannerTone.Success,
            )
        }
    }
}

@Composable
private fun currentPositionStatus(
    hasLocationPermission: Boolean,
    accuracyMeters: Float?,
): StatusPresentation {
    return when {
        !hasLocationPermission -> StatusPresentation(
            title = stringResource(R.string.current_position_status_blocked_title),
            message = stringResource(R.string.current_position_status_blocked_message),
            tone = BannerTone.Warning,
        )
        accuracyMeters == null -> StatusPresentation(
            title = stringResource(R.string.current_position_status_waiting_title),
            message = stringResource(R.string.current_position_status_waiting_message),
            tone = BannerTone.Info,
        )
        accuracyMeters > 45f -> StatusPresentation(
            title = stringResource(R.string.current_position_status_approximate_title),
            message = stringResource(R.string.current_position_status_approximate_message),
            tone = BannerTone.Warning,
        )
        else -> StatusPresentation(
            title = stringResource(R.string.current_position_status_ready_title),
            message = stringResource(R.string.current_position_status_ready_message),
            tone = BannerTone.Success,
        )
    }
}

@Composable
private fun navigationStatus(
    state: ActiveNavigationState,
    hasLocationPermission: Boolean,
    accuracyMeters: Float?,
): StatusPresentation {
    return when {
        !hasLocationPermission -> StatusPresentation(
            title = stringResource(R.string.navigation_status_permission_lost_title),
            message = stringResource(R.string.navigation_status_permission_lost_message),
            tone = BannerTone.Critical,
        )
        state.isRecalculating -> StatusPresentation(
            title = stringResource(R.string.navigation_status_recalculating_title),
            message = stringResource(R.string.navigation_status_recalculating_message),
            tone = BannerTone.Warning,
        )
        state.isOffRoute -> StatusPresentation(
            title = stringResource(R.string.navigation_status_off_route_title),
            message = stringResource(R.string.navigation_status_off_route_message),
            tone = BannerTone.Critical,
        )
        accuracyMeters != null && accuracyMeters > 45f -> StatusPresentation(
            title = stringResource(R.string.navigation_status_gps_weak_title),
            message = stringResource(R.string.navigation_status_gps_weak_message),
            tone = BannerTone.Warning,
        )
        state.isPaused -> StatusPresentation(
            title = stringResource(R.string.navigation_status_paused_title),
            message = stringResource(R.string.navigation_status_paused_message),
            tone = BannerTone.Info,
        )
        else -> StatusPresentation(
            title = stringResource(R.string.navigation_status_active_title),
            message = "",
            tone = BannerTone.Success,
        )
    }
}

@Composable
private fun GeoPoint.coordinatesLabel(): String {
    return stringResource(
        R.string.format_coordinates_label,
        "%.5f".format(latitude),
        "%.5f".format(longitude),
    )
}

private fun formatSavedTimestamp(timestampMs: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMs))
}
@Composable
private fun placeTimingLabel(place: Place): String {
    return if (place.walkDistanceMeters > 0) {
        stringResource(R.string.format_walk_time_and_distance, place.walkEtaMinutes, place.walkDistanceMeters)
    } else {
        stringResource(R.string.format_walk_time_only, place.walkEtaMinutes)
    }
}
