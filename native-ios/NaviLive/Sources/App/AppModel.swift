import Combine
import CoreLocation
import Foundation
import Network

@MainActor
final class AppModel: ObservableObject {
  enum LaunchState {
    case bootstrapping
    case onboarding
    case permissions
    case ready
  }

  @Published var launchState: LaunchState = .bootstrapping
  @Published var path: [AppRoute] = []
  @Published var searchQuery = ""
  @Published var searchResults: [Place] = []
  @Published var hasPerformedSearch = false
  @Published var currentLocationDescription = ""
  @Published var currentPositionStatusMessage = ""
  @Published var currentPositionStatusIsWarning = false
  @Published var statusMessage = ""
  @Published var settings: AppSettings
  @Published var favorites: [Place]
  @Published var lastRoutePlaceID: String?
  @Published var selectedRouteSummary: RouteSummary?
  @Published var headingState: HeadingState
  @Published var activeNavigationState: ActiveNavigationState
  @Published var isSearching = false
  @Published var isRouting = false
  @Published var hasCompletedOnboarding: Bool
  @Published var isLiveTracking = false
  @Published var nearbyPOICacheState = NearbyPOICacheState()

  let locationService: LocationService

  private let settingsStore: SettingsStore
  private let navigationAPI: NavigationAPIClient
  private let announcer: VoiceOverAnnouncer
  private let liveNavigationEngine = LiveNavigationEngine()
  private let routeIssueLogger = RouteIssueDiagnosticLogger()

  private var knownPlaces: [String: Place] = [:]
  private var cancellables: Set<AnyCancellable> = []
  private let pathMonitor = NWPathMonitor()
  private let pathMonitorQueue = DispatchQueue(label: "NaviLive.NearbyPOICache.Network")
  private var currentNetworkPath: NWPath?
  private var nearbyPOICacheRefreshTask: Task<Void, Never>?
  private var delayedAnnouncementTask: Task<Void, Never>?
  private var lastNearbyPOICacheAttemptAt: Date?
  private var isNavigationLive = false
  private var lastCountdownAnnouncementStepIndex = -1
  private var lastCountdownMilestoneMeters: Int?
  private var lastCountdownCadenceMode: AnnouncementCadenceMode?
  private var lastImmediateAnnouncementStepIndex = -1
  private var headingIndex = 0
  private let headingSequence = [
    HeadingState(
      instruction: L10n.text("heading.instruction.rotate_right", table: .navigation),
      isAligned: false,
      arrowRotationDegrees: 22
    ),
    HeadingState(
      instruction: L10n.text("heading.instruction.almost_aligned", table: .navigation),
      isAligned: false,
      arrowRotationDegrees: 7
    ),
    HeadingState(
      instruction: L10n.text("heading.instruction.aligned", table: .navigation),
      isAligned: true,
      arrowRotationDegrees: 0
    )
  ]
  private static let nearbyPOICacheFreshInterval: TimeInterval = 24 * 60 * 60
  private static let nearbyPOICacheMoveThresholdMeters: Double = 800
  private static let nearbyPOICacheAttemptThrottle: TimeInterval = 2 * 60
  private static let speechAfterSoundDelay: TimeInterval = 0.5

  convenience init() {
    self.init(
      settingsStore: SettingsStore(),
      locationService: LocationService(),
      navigationAPI: NavigationAPIClient(),
      announcer: VoiceOverAnnouncer()
    )
  }

  init(
    settingsStore: SettingsStore,
    locationService: LocationService,
    navigationAPI: NavigationAPIClient,
    announcer: VoiceOverAnnouncer
  ) {
    self.settingsStore = settingsStore
    self.locationService = locationService
    self.navigationAPI = navigationAPI
    self.announcer = announcer

    let snapshot = settingsStore.snapshot
    settings = snapshot.settings
    favorites = snapshot.favorites
    lastRoutePlaceID = snapshot.lastRoutePlaceID
    headingState = headingSequence.first ?? HeadingState()
    activeNavigationState = ActiveNavigationState()
    hasCompletedOnboarding = snapshot.hasCompletedOnboarding
    favorites.forEach { knownPlaces[$0.id] = $0 }

    startNetworkMonitor()
    bindLocation()
    Task { await refreshNearbyPOICacheState() }
  }

  var appVersionLabel: String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0"
  }

  var appBuildLabel: String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
  }

  var hasLocationPermission: Bool {
    locationService.hasPermission
  }

  func bootstrap() async {
    refreshLaunchState()
    if hasLocationPermission {
      locationService.startUpdates()
      await loadCurrentAddress()
    } else {
      currentLocationDescription = L10n.text("home.location.unavailable", table: .home)
    }
    if statusMessage.isEmpty {
      statusMessage = L10n.text("home.status.ready", table: .home)
    }
  }

  func refreshLaunchState() {
    if !hasCompletedOnboarding && settings.showTutorialOnLaunch {
      launchState = .onboarding
    } else if !hasLocationPermission {
      launchState = .permissions
    } else {
      launchState = .ready
    }
  }

  func completeOnboarding() {
    hasCompletedOnboarding = true
    settingsStore.setOnboardingCompleted(true)
    refreshLaunchState()
  }

  func requestLocationPermission() {
    locationService.requestPermission()
  }

  func toggleLiveTracking() {
    guard hasLocationPermission else {
      requestLocationPermission()
      return
    }

    if locationService.isUpdating {
      locationService.stopUpdates()
      isLiveTracking = false
      playLiveTrackingToggleSound(starting: false)
      let stoppedMessage = L10n.text("home.location.tracking_stopped", table: .home)
      currentLocationDescription = stoppedMessage
      statusMessage = L10n.text("home.status.live_tracking_stopped", table: .home)
    } else {
      locationService.startUpdates()
      isLiveTracking = true
      playLiveTrackingToggleSound(starting: true)
      currentLocationDescription = L10n.text("home.location.waiting", table: .home)
      statusMessage = L10n.text("home.status.live_tracking_started", table: .home)
      Task { await loadCurrentAddress() }
    }
  }

  func continueWithoutPermission() {
    launchState = .ready
    statusMessage = L10n.text("home.status.location_later", table: .home)
  }

  func place(for id: String) -> Place? {
    knownPlaces[id]
  }

  func isFavorite(_ place: Place) -> Bool {
    favorites.contains(where: { $0.id == place.id })
  }

  func toggleFavorite(_ place: Place) {
    if let index = favorites.firstIndex(where: { $0.id == place.id }) {
      favorites.remove(at: index)
      statusMessage = L10n.text("favorites.status.removed", table: .home)
    } else {
      favorites.append(place)
      statusMessage = L10n.text("favorites.status.saved", table: .home)
    }
    knownPlaces[place.id] = place
    settingsStore.setFavorites(favorites)
  }

  func saveCurrentLocationAsFavorite(named customName: String) async {
    let savedName = customName.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !savedName.isEmpty else {
      let message = L10n.text("current.position.name_required", table: .home)
      statusMessage = message
      currentPositionStatusMessage = message
      currentPositionStatusIsWarning = true
      announceWarning(message: message)
      return
    }
    guard let fix = locationService.latestFix else {
      let message = L10n.text("current.position.save_unavailable", table: .home)
      statusMessage = message
      currentPositionStatusMessage = message
      currentPositionStatusIsWarning = true
      announceWarning(message: message)
      return
    }
    let address = await currentAddressForFavorite(fix: fix)
    if let existingIndex = favorites.firstIndex(where: { isSameSavedCurrentPosition($0, savedName: savedName, address: address, point: fix.point) }) {
      favorites[existingIndex].address = address
      if favorites[existingIndex].point == nil {
        favorites[existingIndex].point = fix.point
      }
      favorites[existingIndex].savedAt = Date()
      favorites[existingIndex].savedAccuracyMeters = fix.accuracyMeters
      knownPlaces[favorites[existingIndex].id] = favorites[existingIndex]
      settingsStore.setFavorites(favorites)
      let message = L10n.text("current.position.already_saved_named", table: .home, savedName)
      statusMessage = message
      currentPositionStatusMessage = message
      currentPositionStatusIsWarning = false
      announceSuccess(message: message)
      return
    }
    let place = Place(
      id: "current-\(Int(Date().timeIntervalSince1970 * 1000))",
      name: savedName,
      address: address,
      walkDistanceMeters: 0,
      walkEtaMinutes: 0,
      point: fix.point,
      savedAt: Date(),
      savedAccuracyMeters: fix.accuracyMeters
    )
    favorites.append(place)
    knownPlaces[place.id] = place
    settingsStore.setFavorites(favorites)
    let message = L10n.text("current.position.saved_named", table: .home, savedName)
    statusMessage = message
    currentPositionStatusMessage = message
    currentPositionStatusIsWarning = false
    announceSuccess(message: message)
  }

  private func isSameSavedCurrentPosition(
    _ favorite: Place,
    savedName: String,
    address: String,
    point: GeoPoint
  ) -> Bool {
    guard favorite.name == savedName else { return false }
    if let favoritePoint = favorite.point, favoritePoint.distance(to: point) <= 25 {
      return true
    }
    return !address.isEmpty && favorite.address == address
  }

  private func currentAddressForFavorite(fix: LocationFix) async -> String {
    do {
      let address = try await navigationAPI.reverseGeocode(point: fix.point).trimmingCharacters(in: .whitespacesAndNewlines)
      if !address.isEmpty {
        return address
      }
    } catch {
      // Fall back to the last visible address when live reverse geocoding is unavailable.
    }
    let visibleAddress = currentLocationDescription.trimmingCharacters(in: .whitespacesAndNewlines)
    if !visibleAddress.isEmpty,
       visibleAddress != L10n.text("home.location.waiting", table: .home),
       visibleAddress != L10n.text("home.location.fallback", table: .home) {
      return visibleAddress
    }
    return L10n.text("current.position.unknown", table: .home)
  }

  func loadCurrentAddress() async {
    guard locationService.isUpdating else {
      currentLocationDescription = L10n.text("home.location.tracking_stopped", table: .home)
      return
    }

    guard let fix = locationService.latestFix else {
      currentLocationDescription = L10n.text("home.location.waiting", table: .home)
      return
    }

    do {
      let address = try await navigationAPI.reverseGeocode(point: fix.point)
      guard locationService.isUpdating else {
        currentLocationDescription = L10n.text("home.location.tracking_stopped", table: .home)
        return
      }
      currentLocationDescription = address
    } catch {
      guard locationService.isUpdating else {
        currentLocationDescription = L10n.text("home.location.tracking_stopped", table: .home)
        return
      }
      currentLocationDescription = L10n.text("home.location.fallback", table: .home)
    }
  }

  func performSearch() async {
    let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !query.isEmpty else {
      searchResults = []
      hasPerformedSearch = false
      return
    }

    hasPerformedSearch = true
    isSearching = true
    defer { isSearching = false }

    do {
      let results = try await navigationAPI.searchPlaces(
        query: query,
        near: locationService.latestFix?.point,
        searchRadiusKilometers: settings.searchRadiusKilometers,
        resultLimit: settings.searchResultLimit
      )
      searchResults = results
      results.forEach { knownPlaces[$0.id] = $0 }
      statusMessage = results.isEmpty
        ? L10n.text("search.status.no_results", table: .home)
        : L10n.text("search.status.results", table: .home, results.count)
    } catch {
      searchResults = []
      statusMessage = L10n.text("search.status.error", table: .home)
    }
  }

  func prepareRoute(for placeID: String) async {
    guard let place = place(for: placeID), let start = locationService.latestFix?.point else {
      statusMessage = L10n.text("route.status.location_required", table: .navigation)
      return
    }

    isRouting = true
    defer { isRouting = false }

    do {
      let summary = try await navigationAPI.buildWalkingRoute(
        from: start,
        to: place,
        includePedestrianCrossings: settings.pedestrianCrossingAlerts
      )
      selectedRouteSummary = summary
      lastRoutePlaceID = place.id
      settingsStore.setLastRoutePlaceID(place.id)
      headingIndex = 0
      headingState = headingSequence[headingIndex]
      activeNavigationState = liveNavigationEngine.loadRoute(
        destination: place,
        summary: summary,
        fix: locationService.latestFix
      )
      isNavigationLive = false
      resetCountdownAnnouncementState()
      lastImmediateAnnouncementStepIndex = -1
      statusMessage = L10n.text("route.status.ready", table: .navigation)
      announceSuccess(message: L10n.text("route.status.ready", table: .navigation))
    } catch {
      selectedRouteSummary = nil
      activeNavigationState = ActiveNavigationState()
      statusMessage = L10n.text("route.status.error", table: .navigation)
      announceWarning(message: L10n.text("route.status.error", table: .navigation))
    }
  }

  func cycleHeadingInstruction() {
    headingIndex = (headingIndex + 1) % headingSequence.count
    headingState = headingSequence[headingIndex]
  }

  func markHeadingAligned() {
    headingIndex = headingSequence.count - 1
    headingState = headingSequence[headingIndex]
    statusMessage = L10n.text("heading.status.aligned", table: .navigation)
    announceSuccess(message: L10n.text("heading.instruction.aligned", table: .navigation))
  }

  func beginActiveNavigation() {
    guard liveNavigationEngine.currentDestination != nil else { return }
    locationService.prepareForActiveNavigation()
    isNavigationLive = true
    resetCountdownAnnouncementState()
    lastImmediateAnnouncementStepIndex = -1
    activeNavigationState.isPaused = false
    statusMessage = L10n.text("active.status.started", table: .navigation)
    announceNavigationPrompt(
      activeNavigationState.currentInstruction.isEmpty
        ? L10n.text("route.follow_default", table: .navigation)
        : activeNavigationState.currentInstruction,
      cue: .success
    )
    if let latestFix = locationService.latestFix {
      syncActiveNavigationWithLocation(latestFix)
    }
  }

  func repeatCurrentInstruction() {
    let message = activeNavigationState.currentInstruction.isEmpty
      ? L10n.text("route.follow_default", table: .navigation)
      : activeNavigationState.currentInstruction
    statusMessage = L10n.text("active.status.repeating", table: .navigation)
    announceNavigationPrompt(message)
  }

  func reportRouteProblem() {
    let steps = selectedRouteSummary?.steps ?? []
    let currentStepIndex = activeNavigationState.currentStepIndex
    let destination = liveNavigationEngine.currentDestination
    let fix = locationService.latestFix
    let snapshot = RouteIssueDiagnosticSnapshot(
      createdAt: Date(),
      appVersion: appVersionLabel,
      appBuild: appBuildLabel,
      destinationID: destination?.id,
      destinationName: destination?.name,
      currentStepIndex: currentStepIndex,
      stepCount: steps.count,
      currentInstruction: activeNavigationState.currentInstruction,
      nextInstruction: activeNavigationState.nextInstruction,
      distanceToNextMeters: activeNavigationState.distanceToNextMeters,
      remainingDistanceMeters: activeNavigationState.remainingDistanceMeters,
      isPaused: activeNavigationState.isPaused,
      isOffRoute: activeNavigationState.isOffRoute,
      isRecalculating: activeNavigationState.isRecalculating,
      offRouteDistanceMeters: activeNavigationState.offRouteDistanceMeters,
      accuracyMeters: fix?.accuracyMeters,
      currentStep: diagnosticStepSnapshot(steps.indices.contains(currentStepIndex) ? steps[currentStepIndex] : nil),
      nextStep: diagnosticStepSnapshot(steps.indices.contains(currentStepIndex + 1) ? steps[currentStepIndex + 1] : nil)
    )

    Task {
      do {
        try await routeIssueLogger.append(snapshot)
        let message = L10n.text("active.status.problem_report_saved", table: .navigation)
        statusMessage = message
        announcer.announce(message, settings: settings)
      } catch {
        let message = L10n.text("active.status.problem_report_failed", table: .navigation)
        statusMessage = message
        announcer.announce(message, settings: settings)
      }
    }
  }

  func announcePreviousInstruction() {
    announceRouteInstruction(
      offset: -1,
      spokenKey: "active.spoken.previous_instruction",
      unavailableKey: "active.status.no_previous_instruction"
    )
  }

  func announceNextInstruction() {
    announceRouteInstruction(
      offset: 1,
      spokenKey: "active.spoken.next_instruction",
      unavailableKey: "active.status.no_next_instruction"
    )
  }

  func togglePauseNavigation() {
    activeNavigationState.isPaused.toggle()
    if activeNavigationState.isPaused {
      statusMessage = L10n.text("active.status.paused", table: .navigation)
      announceNavigationPrompt(L10n.text("active.status.paused", table: .navigation), warning: true)
    } else {
      statusMessage = L10n.text("active.status.resumed", table: .navigation)
      announceNavigationPrompt(L10n.text("active.status.resumed", table: .navigation), cue: .success)
      if let latestFix = locationService.latestFix {
        syncActiveNavigationWithLocation(latestFix)
      }
    }
  }

  func recalculateRoute() {
    Task {
      await recalculateRoute(autoTriggered: false)
    }
  }

  private func diagnosticStepSnapshot(_ step: RouteStep?) -> RouteIssueStepSnapshot? {
    guard let step else { return nil }
    return RouteIssueStepSnapshot(
      instruction: step.instruction,
      distanceMeters: step.distanceMeters,
      kind: step.kind.rawValue,
      maneuverType: step.maneuverType,
      maneuverModifier: step.maneuverModifier,
      roadName: step.roadName
    )
  }

  private func announceRouteInstruction(offset: Int, spokenKey: String, unavailableKey: String) {
    let steps = selectedRouteSummary?.steps ?? []
    let targetIndex = activeNavigationState.currentStepIndex + offset
    guard steps.indices.contains(targetIndex) else {
      let message = L10n.text(unavailableKey, table: .navigation)
      statusMessage = message
      announceNavigationPrompt(message, warning: true)
      return
    }

    let instruction = steps[targetIndex].instruction.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !instruction.isEmpty else { return }
    let message = L10n.text(spokenKey, table: .navigation, instruction)
    statusMessage = message
    announceNavigationPrompt(message)
  }

  func stopNavigation() {
    isNavigationLive = false
    resetCountdownAnnouncementState()
    lastImmediateAnnouncementStepIndex = -1
    locationService.finishActiveNavigation()
    delayedAnnouncementTask?.cancel()
    delayedAnnouncementTask = nil
    announcer.stopSpeech()
    liveNavigationEngine.reset()
    headingIndex = 0
    headingState = headingSequence[headingIndex]
    activeNavigationState = ActiveNavigationState()
    selectedRouteSummary = nil
    statusMessage = L10n.text("active.status.stopped", table: .navigation)
  }

  func markArrived() {
    guard let destination = liveNavigationEngine.currentDestination else { return }
    isNavigationLive = false
    resetCountdownAnnouncementState()
    lastImmediateAnnouncementStepIndex = -1
    locationService.finishActiveNavigation()
    activeNavigationState.isPaused = false
    activeNavigationState.isOffRoute = false
    activeNavigationState.isRecalculating = false
    statusMessage = L10n.text("active.status.arrived", table: .navigation)
    announceNavigationPrompt(L10n.text("active.spoken.arrived", table: .navigation), cue: .arrival)
    if path.last != .arrival(placeID: destination.id) {
      path.append(.arrival(placeID: destination.id))
    }
  }

  func openSearch() {
    path.append(.search)
  }

  func openPlaceDetails(_ placeID: String) {
    path.append(.placeDetails(placeID: placeID))
  }

  func openRouteSummary(_ placeID: String) {
    path.append(.routeSummary(placeID: placeID))
  }

  func openHeadingAlign(_ placeID: String) {
    path.append(.headingAlign(placeID: placeID))
  }

  func openActiveNavigation(_ placeID: String) {
    path.append(.activeNavigation(placeID: placeID))
  }

  func openCurrentPosition() {
    currentPositionStatusMessage = ""
    currentPositionStatusIsWarning = false
    path.append(.currentPosition)
  }

  func openFavorites() {
    path.append(.favorites)
  }

  func openSettings() {
    path.append(.settings)
  }

  func openHelpPrivacy() {
    path.append(.helpPrivacy)
  }

  func updateShowTutorialOnLaunch(_ enabled: Bool) {
    settings.showTutorialOnLaunch = enabled
    settingsStore.updateSettings { $0.showTutorialOnLaunch = enabled }
  }

  func updateVibrationEnabled(_ enabled: Bool) {
    settings.vibrationEnabled = enabled
    settingsStore.updateSettings { $0.vibrationEnabled = enabled }
  }

  func updateShakeGestureEnabled(_ enabled: Bool) {
    settings.shakeGestureEnabled = enabled
    settingsStore.updateSettings { $0.shakeGestureEnabled = enabled }
  }

  func updateShakeStrength(_ strength: ShakeStrength) {
    settings.shakeStrength = strength
    settingsStore.updateSettings { $0.shakeStrength = strength }
  }

  func onShakeGestureDetected() {
    guard settings.shakeGestureEnabled else { return }
    guard !activeNavigationState.currentInstruction.isEmpty else { return }
    repeatCurrentInstruction()
  }

  func updateSoundCuesEnabled(_ enabled: Bool) {
    settings.soundCuesEnabled = enabled
    settingsStore.updateSettings { $0.soundCuesEnabled = enabled }
  }

  func updateSoundCueVolume(_ value: Double) {
    let normalized = min(max(value, 0.0), 1.0)
    settings.soundCueVolume = normalized
    settingsStore.updateSettings { $0.soundCueVolume = normalized }
  }

  func updateSoundCueTheme(_ theme: SoundCueTheme) {
    settings.soundCueTheme = theme
    settingsStore.updateSettings { $0.soundCueTheme = theme }
  }

  func previewSoundCue(_ cue: NavigationSoundCue) {
    announcer.playSoundCue(cue, volume: settings.soundCueVolume, theme: settings.soundCueTheme)
  }

  func previewSpeechSettings() {
    announcer.previewSynthesizer(
      L10n.text("settings.speech.preview.sample", table: .settings),
      settings: settings
    )
  }

  func updateAutoRecalculate(_ enabled: Bool) {
    settings.autoRecalculate = enabled
    settingsStore.updateSettings { $0.autoRecalculate = enabled }
  }

  func updateJunctionAlerts(_ enabled: Bool) {
    settings.junctionAlerts = enabled
    settingsStore.updateSettings { $0.junctionAlerts = enabled }
  }

  func updatePedestrianCrossingAlerts(_ enabled: Bool) {
    settings.pedestrianCrossingAlerts = enabled
    settingsStore.updateSettings { $0.pedestrianCrossingAlerts = enabled }
  }

  func updateTurnByTurnAnnouncements(_ enabled: Bool) {
    settings.turnByTurnAnnouncements = enabled
    settingsStore.updateSettings { $0.turnByTurnAnnouncements = enabled }
  }

  func updateAnnouncementCadenceMode(_ mode: AnnouncementCadenceMode) {
    settings.announcementCadenceMode = mode
    settingsStore.updateSettings { $0.announcementCadenceMode = mode }
    resetCountdownAnnouncementState()
  }

  func updateSearchRadiusKilometers(_ value: Int) {
    let normalized = min(
      max(value, SharedProductRules.Search.minimumRadiusKm),
      SharedProductRules.Search.maximumRadiusKm
    )
    settings.searchRadiusKilometers = normalized
    settingsStore.updateSettings { $0.searchRadiusKilometers = normalized }
  }

  func updateSearchResultLimit(_ value: Int) {
    let normalized = min(
      max(value, SharedProductRules.Search.minimumResultLimit),
      SharedProductRules.Search.maximumResultLimit
    )
    settings.searchResultLimit = normalized
    settingsStore.updateSettings { $0.searchResultLimit = normalized }
  }

  func updateNearbyPOICacheMode(_ mode: NearbyPOICacheMode) {
    settings.nearbyPOICacheMode = mode
    settingsStore.updateSettings { $0.nearbyPOICacheMode = mode }
    if mode != .disabled {
      refreshNearbyPOICacheNow()
    }
  }

  func updateNearbyPOICacheRadiusKilometers(_ value: Int) {
    let normalized = min(max(value, SharedProductRules.Search.minimumRadiusKm), 5)
    settings.nearbyPOICacheRadiusKilometers = normalized
    settingsStore.updateSettings { $0.nearbyPOICacheRadiusKilometers = normalized }
    refreshNearbyPOICacheNow()
  }

  func refreshNearbyPOICacheNow() {
    guard let fix = locationService.latestFix else {
      statusMessage = L10n.text("settings.local_search.status.waiting_location", table: .settings)
      return
    }
    maybeRefreshNearbyPOICache(fix: fix, force: true)
  }

  func clearNearbyPOICache() {
    Task {
      nearbyPOICacheState = await navigationAPI.clearNearbyPOICache()
      statusMessage = L10n.text("settings.local_search.status.cleared", table: .settings)
    }
  }

  func updateSpeechMode(_ mode: GuidanceSpeechMode) {
    let normalizedMode: GuidanceSpeechMode = mode == .automatic ? .voiceOver : mode
    settings.speechMode = normalizedMode
    settingsStore.updateSettings { $0.speechMode = normalizedMode }
    announcer.announce(L10n.text("settings.speech_mode.updated", table: .settings), settings: settings)
  }

  func updateSpeechVoiceIdentifier(_ identifier: String?) {
    let normalized = identifier?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true ? nil : identifier
    settings.selectedSpeechVoiceIdentifier = normalized
    settingsStore.updateSettings { $0.selectedSpeechVoiceIdentifier = normalized }
  }

  func updateSpeechRate(_ value: Double) {
    settings.speechRate = value
    settingsStore.updateSettings { $0.speechRate = value }
  }

  func updateSpeechVolume(_ value: Double) {
    settings.speechVolume = value
    settingsStore.updateSettings { $0.speechVolume = value }
  }

  private func bindLocation() {
    locationService.$authorizationStatus
      .sink { [weak self] _ in
        guard let self else { return }
        self.refreshLaunchState()
        if self.hasLocationPermission {
          self.locationService.startUpdates()
        }
      }
      .store(in: &cancellables)

    locationService.$latestFix
      .compactMap { $0 }
      .sink { [weak self] fix in
        guard let self else { return }
        if self.locationService.isUpdating {
          Task { await self.loadCurrentAddress() }
          self.maybeRefreshNearbyPOICache(fix: fix)
        }
        self.syncActiveNavigationWithLocation(fix)
      }
      .store(in: &cancellables)

    locationService.$isUpdating
      .removeDuplicates()
      .sink { [weak self] isUpdating in
        guard let self else { return }
        self.isLiveTracking = isUpdating
        if !isUpdating {
          self.currentLocationDescription = L10n.text("home.location.tracking_stopped", table: .home)
        }
      }
      .store(in: &cancellables)
  }

  private func startNetworkMonitor() {
    pathMonitor.pathUpdateHandler = { [weak self] path in
      Task { @MainActor in
        self?.currentNetworkPath = path
      }
    }
    pathMonitor.start(queue: pathMonitorQueue)
  }

  private func refreshNearbyPOICacheState() async {
    nearbyPOICacheState = await navigationAPI.nearbyPOICacheState()
  }

  private func maybeRefreshNearbyPOICache(fix: LocationFix, force: Bool = false) {
    guard canRefreshNearbyPOICache(mode: settings.nearbyPOICacheMode) else {
      if force && settings.nearbyPOICacheMode == .wifiOnly {
        statusMessage = L10n.text("settings.local_search.status.waiting_wifi", table: .settings)
      }
      return
    }
    if nearbyPOICacheRefreshTask != nil { return }
    let now = Date()
    if !force,
       let lastAttempt = lastNearbyPOICacheAttemptAt,
       now.timeIntervalSince(lastAttempt) < Self.nearbyPOICacheAttemptThrottle {
      return
    }
    if !force && !shouldRefreshNearbyPOICache(fix: fix, now: now) {
      return
    }
    lastNearbyPOICacheAttemptAt = now
    nearbyPOICacheState.isRefreshing = true
    statusMessage = L10n.text("settings.local_search.status.refreshing", table: .settings)

    nearbyPOICacheRefreshTask = Task { [weak self] in
      guard let self else { return }
      do {
        let refreshed = try await navigationAPI.refreshNearbyPOICache(
          near: fix.point,
          radiusKilometers: settings.nearbyPOICacheRadiusKilometers
        )
        nearbyPOICacheState = NearbyPOICacheState(
          cachedPlaceCount: refreshed.cachedPlaceCount,
          lastUpdatedAt: refreshed.lastUpdatedAt,
          lastCenter: refreshed.lastCenter
        )
        statusMessage = L10n.text("settings.local_search.status.updated", table: .settings, refreshed.cachedPlaceCount)
      } catch {
        nearbyPOICacheState.isRefreshing = false
        if force {
          statusMessage = L10n.text("settings.local_search.status.failed", table: .settings)
        }
      }
      nearbyPOICacheRefreshTask = nil
    }
  }

  private func shouldRefreshNearbyPOICache(fix: LocationFix, now: Date) -> Bool {
    guard let lastUpdatedAt = nearbyPOICacheState.lastUpdatedAt else { return true }
    if now.timeIntervalSince(lastUpdatedAt) > Self.nearbyPOICacheFreshInterval { return true }
    guard let lastCenter = nearbyPOICacheState.lastCenter else { return true }
    return fix.point.distance(to: lastCenter) >= Self.nearbyPOICacheMoveThresholdMeters
  }

  private func canRefreshNearbyPOICache(mode: NearbyPOICacheMode) -> Bool {
    switch mode {
    case .enabled:
      return true
    case .disabled:
      return false
    case .wifiOnly:
      guard let currentNetworkPath else { return false }
      return currentNetworkPath.status == .satisfied && !currentNetworkPath.isExpensive
    }
  }

  private func syncActiveNavigationWithLocation(_ fix: LocationFix) {
    guard isNavigationLive, !activeNavigationState.isPaused else { return }
    guard let update = liveNavigationEngine.update(
      fix: fix,
      previous: activeNavigationState,
      autoRecalculateEnabled: settings.autoRecalculate
    ) else {
      return
    }

    activeNavigationState = update.state
    if update.stepChanged && settings.turnByTurnAnnouncements {
      if update.currentStepIndex != lastImmediateAnnouncementStepIndex {
        announceNavigationPrompt(
          L10n.text("active.spoken.now", table: .navigation, update.state.currentInstruction),
          cue: soundCue(for: update.currentStepKind, defaultCue: .turnNow)
        )
        lastImmediateAnnouncementStepIndex = update.currentStepIndex
      }
    } else if settings.turnByTurnAnnouncements {
      let announcedCountdown = maybeAnnounceCountdownInstruction(update: update)
      if !announcedCountdown {
        _ = maybeAnnounceImmediateInstruction(update: update, fix: fix)
      }
    }
    if update.offRouteTriggered {
      statusMessage = L10n.text("active.status.off_route", table: .navigation)
      announceNavigationPrompt(L10n.text("active.spoken.off_route", table: .navigation), warning: true)
    } else if update.state.isOffRoute {
      statusMessage = L10n.text("active.status.off_route", table: .navigation)
    } else if update.state.isRecalculating {
      statusMessage = L10n.text("active.status.recalculating", table: .navigation)
    } else if update.stepChanged {
      statusMessage = update.state.currentInstruction
    }

    if update.shouldAutoRecalculate {
      Task { await recalculateRoute(autoTriggered: true) }
    } else if update.hasArrived {
      markArrived()
    }
  }

  private func recalculateRoute(autoTriggered: Bool) async {
    guard let placeID = lastRoutePlaceID,
          let place = place(for: placeID),
          let start = locationService.latestFix?.point else {
      statusMessage = L10n.text("route.status.cannot_recalculate", table: .navigation)
      return
    }

    isRouting = true
    activeNavigationState.isRecalculating = true
    statusMessage = L10n.text("active.status.recalculating", table: .navigation)
    defer {
      isRouting = false
    }

    do {
      let summary = try await navigationAPI.buildWalkingRoute(
        from: start,
        to: place,
        includePedestrianCrossings: settings.pedestrianCrossingAlerts
      )
      selectedRouteSummary = summary
      activeNavigationState = liveNavigationEngine.loadRoute(
        destination: place,
        summary: summary,
        fix: locationService.latestFix
      )
      resetCountdownAnnouncementState()
      lastImmediateAnnouncementStepIndex = -1
      activeNavigationState.isRecalculating = false
      if autoTriggered {
        statusMessage = L10n.text("active.status.auto_recalculated", table: .navigation)
      } else {
        statusMessage = L10n.text("active.status.recalculated", table: .navigation)
      }
      announceNavigationPrompt(L10n.text("active.spoken.recalculated", table: .navigation), cue: .success)
    } catch {
      activeNavigationState.isRecalculating = false
      statusMessage = L10n.text("route.status.error", table: .navigation)
      announceWarning(message: L10n.text("route.status.error", table: .navigation))
    }
  }

  private func maybeAnnounceCountdownInstruction(update: LiveNavigationUpdate) -> Bool {
    guard !update.stepChanged else { return false }
    guard !update.state.isOffRoute, !update.state.isRecalculating, !update.hasArrived else { return false }
    let cadenceMode = settings.announcementCadenceMode
    let upcomingStepIndex = update.currentStepIndex + 1
    guard let upcomingInstruction = update.upcomingInstruction?.trimmingCharacters(in: .whitespacesAndNewlines),
          !upcomingInstruction.isEmpty else {
      return false
    }
    let milestoneValue: Int?
    switch cadenceMode {
    case .distance:
      milestoneValue = NavigationScenarioCore.countdownMilestoneMeters(
        distanceToNext: update.state.distanceToNextMeters
      )
    case .time:
      milestoneValue = NavigationScenarioCore.countdownMilestoneSeconds(
        secondsToNext: NavigationScenarioCore.estimatedSecondsToManeuver(
          distanceToNextMeters: update.state.distanceToNextMeters
        )
      )
    }
    guard let milestoneValue else {
      return false
    }
    if update.upcomingStepKind == .pedestrianCrossing {
      guard cadenceMode == .distance, milestoneValue <= 20 else { return false }
    }

    if upcomingStepIndex != lastCountdownAnnouncementStepIndex || cadenceMode != lastCountdownCadenceMode {
      lastCountdownAnnouncementStepIndex = upcomingStepIndex
      lastCountdownMilestoneMeters = nil
      lastCountdownCadenceMode = cadenceMode
    }
    if let lastMilestone = lastCountdownMilestoneMeters, milestoneValue >= lastMilestone {
      return false
    }

    lastCountdownMilestoneMeters = milestoneValue
    let message: String
    switch cadenceMode {
    case .distance:
      message = L10n.text(
        "active.spoken.upcoming.distance",
        table: .navigation,
        milestoneValue,
        upcomingInstruction
      )
    case .time:
      message = L10n.text(
        "active.spoken.upcoming.time",
        table: .navigation,
        milestoneValue,
        upcomingInstruction
      )
    }
    announceNavigationPrompt(
      message,
      cue: soundCue(for: update.upcomingStepKind, defaultCue: .countdown)
    )
    return true
  }

  private func maybeAnnounceImmediateInstruction(update: LiveNavigationUpdate, fix: LocationFix) -> Bool {
    guard !update.stepChanged else { return false }
    guard !update.state.isOffRoute, !update.state.isRecalculating, !update.hasArrived else { return false }
    let upcomingStepIndex = update.currentStepIndex + 1
    guard upcomingStepIndex != lastImmediateAnnouncementStepIndex else { return false }
    guard let upcomingInstruction = update.upcomingInstruction?.trimmingCharacters(in: .whitespacesAndNewlines),
          !upcomingInstruction.isEmpty else {
      return false
    }
    let threshold = NavigationScenarioCore.immediateAnnouncementThresholdMeters(
      accuracyMeters: fix.accuracyMeters
    )
    guard update.state.distanceToNextMeters > 0, update.state.distanceToNextMeters <= threshold else {
      return false
    }

    lastImmediateAnnouncementStepIndex = upcomingStepIndex
    announceNavigationPrompt(
      L10n.text("active.spoken.now", table: .navigation, upcomingInstruction),
      cue: soundCue(for: update.upcomingStepKind, defaultCue: .turnNow)
    )
    return true
  }

  private func announceNavigationPrompt(
    _ message: String,
    cue: NavigationSoundCue? = nil,
    warning: Bool = false
  ) {
    let effectiveCue = cue ?? (warning ? .warning : nil)
    let speechDelay = effectiveCue.map { playSoundCueIfEnabled($0) } ?? 0.0
    announceNavigationSpeech(message, delayAfterSound: speechDelay)
    if settings.vibrationEnabled {
      if warning {
        announcer.hapticWarning()
      } else {
        announcer.hapticSuccess()
      }
    }
  }

  private func announceSuccess(message: String) {
    let speechDelay = playSoundCueIfEnabled(.success)
    announceSpeech(message, delayAfterSound: speechDelay)
    hapticSuccessIfEnabled()
  }

  private func announceWarning(message: String) {
    let speechDelay = playSoundCueIfEnabled(.warning)
    announceSpeech(message, delayAfterSound: speechDelay)
    if settings.vibrationEnabled {
      announcer.hapticWarning()
    }
  }

  private func announceNavigationSpeech(_ message: String, delayAfterSound: TimeInterval) {
    scheduleSpeech(delayAfterSound: delayAfterSound) { [weak self] in
      guard let self else { return }
      self.announcer.announceNavigation(message, settings: self.settings)
    }
  }

  private func announceSpeech(_ message: String, delayAfterSound: TimeInterval) {
    scheduleSpeech(delayAfterSound: delayAfterSound) { [weak self] in
      guard let self else { return }
      self.announcer.announce(message, settings: self.settings)
    }
  }

  private func scheduleSpeech(delayAfterSound: TimeInterval, action: @escaping @MainActor () -> Void) {
    delayedAnnouncementTask?.cancel()
    delayedAnnouncementTask = nil
    guard delayAfterSound > 0 else {
      action()
      return
    }

    delayedAnnouncementTask = Task { [weak self] in
      do {
        try await Task.sleep(nanoseconds: UInt64(delayAfterSound * 1_000_000_000))
      } catch {
        return
      }
      guard !Task.isCancelled else { return }
      await MainActor.run {
        guard self != nil else { return }
        action()
      }
    }
  }

  private func hapticSuccessIfEnabled() {
    if settings.vibrationEnabled {
      announcer.hapticSuccess()
    }
  }

  private func playLiveTrackingToggleSound(starting: Bool) {
    _ = playSoundCueIfEnabled(starting ? .success : .warning)
  }

  private func playSoundCueIfEnabled(_ cue: NavigationSoundCue) -> TimeInterval {
    guard settings.soundCuesEnabled else { return 0.0 }
    let queuedStartDelay = announcer.playSoundCue(cue, volume: settings.soundCueVolume, theme: settings.soundCueTheme)
    return queuedStartDelay + Self.speechAfterSoundDelay
  }

  private func soundCue(for stepKind: RouteStepKind?, defaultCue: NavigationSoundCue) -> NavigationSoundCue {
    switch stepKind {
    case .pedestrianCrossing:
      return .pedestrianCrossing
    case .instruction, .none:
      return defaultCue
    }
  }

  private func resetCountdownAnnouncementState() {
    lastCountdownAnnouncementStepIndex = -1
    lastCountdownMilestoneMeters = nil
    lastCountdownCadenceMode = nil
  }
}
