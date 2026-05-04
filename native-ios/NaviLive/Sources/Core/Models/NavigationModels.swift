import CoreLocation
import Foundation

struct GeoPoint: Codable, Hashable, Sendable {
  var latitude: Double
  var longitude: Double

  var clCoordinate: CLLocationCoordinate2D {
    CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }

  func distance(to other: GeoPoint) -> CLLocationDistance {
    CLLocation(latitude: latitude, longitude: longitude)
      .distance(from: CLLocation(latitude: other.latitude, longitude: other.longitude))
  }

  static func from(_ coordinate: CLLocationCoordinate2D) -> GeoPoint {
    GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude)
  }
}

struct Place: Identifiable, Codable, Hashable, Sendable {
  var id: String
  var name: String
  var address: String
  var walkDistanceMeters: Int
  var walkEtaMinutes: Int
  var point: GeoPoint?
  var phone: String?
  var website: String?

  var hasContactDetails: Bool {
    phone?.isEmpty == false || website?.isEmpty == false
  }
}

enum RouteStepKind: String, Codable, Hashable, Sendable {
  case instruction
  case pedestrianCrossing
}

struct RouteStep: Identifiable, Codable, Hashable, Sendable {
  var id: UUID = UUID()
  var instruction: String
  var distanceMeters: Int
  var maneuverPoint: GeoPoint?
  var kind: RouteStepKind = .instruction
  var maneuverType: String?
  var maneuverModifier: String?
  var roadName: String?

  init(
    id: UUID = UUID(),
    instruction: String,
    distanceMeters: Int,
    maneuverPoint: GeoPoint? = nil,
    kind: RouteStepKind = .instruction,
    maneuverType: String? = nil,
    maneuverModifier: String? = nil,
    roadName: String? = nil
  ) {
    self.id = id
    self.instruction = instruction
    self.distanceMeters = distanceMeters
    self.maneuverPoint = maneuverPoint
    self.kind = kind
    self.maneuverType = maneuverType
    self.maneuverModifier = maneuverModifier
    self.roadName = roadName
  }

  private enum CodingKeys: String, CodingKey {
    case id
    case instruction
    case distanceMeters
    case maneuverPoint
    case kind
    case maneuverType
    case maneuverModifier
    case roadName
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
    instruction = try container.decode(String.self, forKey: .instruction)
    distanceMeters = try container.decode(Int.self, forKey: .distanceMeters)
    maneuverPoint = try container.decodeIfPresent(GeoPoint.self, forKey: .maneuverPoint)
    kind = try container.decodeIfPresent(RouteStepKind.self, forKey: .kind) ?? .instruction
    maneuverType = try container.decodeIfPresent(String.self, forKey: .maneuverType)
    maneuverModifier = try container.decodeIfPresent(String.self, forKey: .maneuverModifier)
    roadName = try container.decodeIfPresent(String.self, forKey: .roadName)
  }
}

struct RouteSummary: Codable, Hashable, Sendable {
  var distanceMeters: Int
  var etaMinutes: Int
  var modeLabel: String
  var currentInstruction: String
  var nextInstruction: String
  var steps: [RouteStep]
  var pathPoints: [GeoPoint]
}

struct HeadingState: Codable, Hashable, Sendable {
  var instruction: String = ""
  var isAligned: Bool = false
  var arrowRotationDegrees: Double = 0
}

struct ActiveNavigationState: Codable, Hashable, Sendable {
  var currentInstruction: String = ""
  var nextInstruction: String = ""
  var currentStepIndex: Int = 0
  var distanceToNextMeters: Int = 0
  var remainingDistanceMeters: Int = 0
  var progressLabel: String = ""
  var isPaused: Bool = false
  var isOffRoute: Bool = false
  var isRecalculating: Bool = false
  var offRouteDistanceMeters: Int?
}

struct LocationFix: Codable, Hashable, Sendable {
  var point: GeoPoint
  var accuracyMeters: Double
  var timestamp: Date
  var courseDegrees: Double?
}

enum GuidanceSpeechMode: String, CaseIterable, Codable, Sendable {
  case automatic
  case voiceOver
  case speechSynthesizer
}

enum AnnouncementCadenceMode: String, CaseIterable, Codable, Sendable {
  case distance
  case time
}

enum ShakeStrength: String, CaseIterable, Codable, Sendable {
  case light
  case medium
  case strong
}

enum SoundCueTheme: String, CaseIterable, Codable, Sendable {
  case standard
  case tetris
  case cosmic
}

enum NearbyPOICacheMode: String, CaseIterable, Codable, Sendable {
  case enabled
  case wifiOnly
  case disabled
}

struct NearbyPOICacheState: Codable, Hashable, Sendable {
  var isRefreshing: Bool = false
  var cachedPlaceCount: Int = 0
  var lastUpdatedAt: Date?
  var lastCenter: GeoPoint?
}

struct AppSettings: Codable, Hashable, Sendable {
  var showTutorialOnLaunch: Bool = true
  var vibrationEnabled: Bool = true
  var shakeGestureEnabled: Bool = true
  var shakeStrength: ShakeStrength = .medium
  var soundCuesEnabled: Bool = true
  var soundCueVolume: Double = 0.85
  var soundCueTheme: SoundCueTheme = .standard
  var autoRecalculate: Bool = true
  var junctionAlerts: Bool = true
  var pedestrianCrossingAlerts: Bool = true
  var turnByTurnAnnouncements: Bool = true
  var announcementCadenceMode: AnnouncementCadenceMode = .distance
  var searchRadiusKilometers: Int = SharedProductRules.Search.defaultRadiusKm
  var searchResultLimit: Int = SharedProductRules.Search.resultLimit
  var nearbyPOICacheMode: NearbyPOICacheMode = .enabled
  var nearbyPOICacheRadiusKilometers: Int = SharedProductRules.Search.defaultRadiusKm
  var speechMode: GuidanceSpeechMode = .voiceOver
  var selectedSpeechVoiceIdentifier: String?
  var speechRate: Double = 1.0
  var speechVolume: Double = 1.0

  enum CodingKeys: String, CodingKey {
    case showTutorialOnLaunch
    case vibrationEnabled
    case shakeGestureEnabled
    case shakeStrength
    case soundCuesEnabled
    case soundCueVolume
    case soundCueTheme
    case autoRecalculate
    case junctionAlerts
    case pedestrianCrossingAlerts
    case turnByTurnAnnouncements
    case announcementCadenceMode
    case searchRadiusKilometers
    case searchResultLimit
    case nearbyPOICacheMode
    case nearbyPOICacheRadiusKilometers
    case speechMode
    case selectedSpeechVoiceIdentifier
    case speechRate
    case speechVolume
  }

  init() {}

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    showTutorialOnLaunch = try container.decodeIfPresent(Bool.self, forKey: .showTutorialOnLaunch) ?? true
    vibrationEnabled = try container.decodeIfPresent(Bool.self, forKey: .vibrationEnabled) ?? true
    shakeGestureEnabled = try container.decodeIfPresent(Bool.self, forKey: .shakeGestureEnabled) ?? true
    shakeStrength = try container.decodeIfPresent(ShakeStrength.self, forKey: .shakeStrength) ?? .medium
    soundCuesEnabled = try container.decodeIfPresent(Bool.self, forKey: .soundCuesEnabled) ?? true
    soundCueVolume = min(max(try container.decodeIfPresent(Double.self, forKey: .soundCueVolume) ?? 0.85, 0.0), 1.0)
    soundCueTheme = try container.decodeIfPresent(SoundCueTheme.self, forKey: .soundCueTheme) ?? .standard
    autoRecalculate = try container.decodeIfPresent(Bool.self, forKey: .autoRecalculate) ?? true
    junctionAlerts = try container.decodeIfPresent(Bool.self, forKey: .junctionAlerts) ?? true
    pedestrianCrossingAlerts = try container.decodeIfPresent(Bool.self, forKey: .pedestrianCrossingAlerts) ?? true
    turnByTurnAnnouncements = try container.decodeIfPresent(Bool.self, forKey: .turnByTurnAnnouncements) ?? true
    announcementCadenceMode = try container.decodeIfPresent(AnnouncementCadenceMode.self, forKey: .announcementCadenceMode) ?? .distance
    let decodedSearchRadius = try container.decodeIfPresent(Int.self, forKey: .searchRadiusKilometers)
      ?? SharedProductRules.Search.defaultRadiusKm
    searchRadiusKilometers = min(
      max(decodedSearchRadius, SharedProductRules.Search.minimumRadiusKm),
      SharedProductRules.Search.maximumRadiusKm
    )
    let decodedSearchResultLimit = try container.decodeIfPresent(Int.self, forKey: .searchResultLimit)
      ?? SharedProductRules.Search.resultLimit
    searchResultLimit = min(
      max(decodedSearchResultLimit, SharedProductRules.Search.minimumResultLimit),
      SharedProductRules.Search.maximumResultLimit
    )
    nearbyPOICacheMode = try container.decodeIfPresent(NearbyPOICacheMode.self, forKey: .nearbyPOICacheMode) ?? .enabled
    let decodedNearbyPOIRadius = try container.decodeIfPresent(Int.self, forKey: .nearbyPOICacheRadiusKilometers)
      ?? SharedProductRules.Search.defaultRadiusKm
    nearbyPOICacheRadiusKilometers = min(
      max(decodedNearbyPOIRadius, SharedProductRules.Search.minimumRadiusKm),
      5
    )
    let decodedSpeechMode = try container.decodeIfPresent(GuidanceSpeechMode.self, forKey: .speechMode) ?? .voiceOver
    speechMode = decodedSpeechMode == .automatic ? .voiceOver : decodedSpeechMode
    selectedSpeechVoiceIdentifier = try container.decodeIfPresent(String.self, forKey: .selectedSpeechVoiceIdentifier)
    speechRate = try container.decodeIfPresent(Double.self, forKey: .speechRate) ?? 1.0
    speechVolume = try container.decodeIfPresent(Double.self, forKey: .speechVolume) ?? 1.0
  }
}

struct PersistedSnapshot: Codable, Sendable {
  var favorites: [Place] = []
  var lastRoutePlaceID: String?
  var settings: AppSettings = .init()
  var hasCompletedOnboarding: Bool = false

  enum CodingKeys: String, CodingKey {
    case favorites
    case lastRoutePlaceID
    case settings
    case hasCompletedOnboarding
  }

  init() {}

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    favorites = try container.decodeIfPresent([Place].self, forKey: .favorites) ?? []
    lastRoutePlaceID = try container.decodeIfPresent(String.self, forKey: .lastRoutePlaceID)
    settings = try container.decodeIfPresent(AppSettings.self, forKey: .settings) ?? .init()
    hasCompletedOnboarding = try container.decodeIfPresent(Bool.self, forKey: .hasCompletedOnboarding) ?? false
  }
}

enum AppRoute: Hashable {
  case onboarding
  case permissions
  case search
  case placeDetails(placeID: String)
  case routeSummary(placeID: String)
  case headingAlign(placeID: String)
  case activeNavigation(placeID: String)
  case arrival(placeID: String)
  case currentPosition
  case favorites
  case settings
  case helpPrivacy
}
