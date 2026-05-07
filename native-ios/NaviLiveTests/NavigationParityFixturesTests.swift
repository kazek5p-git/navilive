import Foundation
import XCTest
@testable import NaviLive

final class NavigationParityFixturesTests: XCTestCase {
  func testAddressFormattingMatchesSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.addressCases {
      let actual = AddressFormattingCore.formatAddress(entry.address, fallback: entry.fallback)
      XCTAssertEqual(actual, entry.expected, entry.name)
    }
  }

  func testInstructionDescriptorsMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.instructionCases {
      let descriptor = NavigationInstructionCore.describe(
        maneuverType: entry.maneuverType,
        modifier: entry.modifier,
        roadName: entry.roadName
      )
      XCTAssertEqual(descriptor.paritySignature(), entry.expectedParity, entry.name)
    }
  }


  func testRouteStepSimplificationKeepsSameRoadTurnManeuvers() {
    let previous = RouteStep(
      instruction: "Skręć w lewo w Warmińską",
      distanceMeters: 90,
      maneuverType: "turn",
      maneuverModifier: "left",
      roadName: "Warmińska"
    )
    let nextTurn = RouteStep(
      instruction: "Skręć w prawo w Warmińską",
      distanceMeters: 120,
      maneuverType: "turn",
      maneuverModifier: "right",
      roadName: "Warmińska"
    )

    XCTAssertFalse(
      RouteStepSimplificationCore.shouldSuppressRouteStep(
        nextTurn,
        previous: previous,
        index: 2,
        lastIndex: 4
      )
    )
  }

  func testRouteStepSimplificationSuppressesShortUnnamedTurnConnectors() {
    let previous = RouteStep(
      instruction: "Continue Main Street",
      distanceMeters: 80,
      maneuverType: "turn",
      maneuverModifier: "left",
      roadName: "Main Street"
    )
    let shortConnector = RouteStep(
      instruction: "Turn right",
      distanceMeters: 12,
      maneuverType: "turn",
      maneuverModifier: "right",
      roadName: nil
    )

    XCTAssertTrue(
      RouteStepSimplificationCore.shouldSuppressRouteStep(
        shortConnector,
        previous: previous,
        index: 2,
        lastIndex: 4
      )
    )
  }

  func testRouteStepSimplificationSuppressesShortSameRoadTurnConnectors() {
    let previous = RouteStep(
      instruction: "Continue Main Street",
      distanceMeters: 80,
      maneuverType: "turn",
      maneuverModifier: "left",
      roadName: "Main Street"
    )
    let shortConnector = RouteStep(
      instruction: "Turn right in Main Street",
      distanceMeters: 20,
      maneuverType: "turn",
      maneuverModifier: "right",
      roadName: "Main Street"
    )

    XCTAssertTrue(
      RouteStepSimplificationCore.shouldSuppressRouteStep(
        shortConnector,
        previous: previous,
        index: 2,
        lastIndex: 4
      )
    )
  }
  func testRouteStepSimplificationStillMergesShortSameRoadContinuations() {
    let previous = RouteStep(
      instruction: "Idź Warmińską",
      distanceMeters: 90,
      maneuverType: "turn",
      maneuverModifier: "left",
      roadName: "Warmińska"
    )
    let shortContinuation = RouteStep(
      instruction: "Kontynuuj Warmińską",
      distanceMeters: 20,
      maneuverType: "continue",
      maneuverModifier: "straight",
      roadName: "Warmińska"
    )

    XCTAssertTrue(
      RouteStepSimplificationCore.shouldSuppressRouteStep(
        shortContinuation,
        previous: previous,
        index: 2,
        lastIndex: 4
      )
    )
  }
  func testNavigationThresholdsMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.thresholds {
      XCTAssertEqual(
        NavigationScenarioCore.maneuverAdvanceThresholdMeters(accuracyMeters: entry.accuracyMeters),
        entry.expectedManeuverAdvance,
        accuracy: 0.0001,
        entry.name
      )
      XCTAssertEqual(
        NavigationScenarioCore.offRouteThresholdMeters(accuracyMeters: entry.accuracyMeters),
        entry.expectedOffRoute,
        entry.name
      )
      XCTAssertEqual(
        NavigationScenarioCore.immediateAnnouncementThresholdMeters(accuracyMeters: entry.accuracyMeters),
        entry.expectedImmediate,
        entry.name
      )
    }
  }

  func testNavigationCountdownMilestonesMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.countdowns {
      XCTAssertEqual(
        NavigationScenarioCore.countdownMilestoneMeters(distanceToNext: entry.distanceToNextMeters),
        entry.expectedMilestone,
        entry.name
      )
    }
  }

  func testNavigationTimeCountdownMilestonesMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.timeCountdowns {
      XCTAssertEqual(
        NavigationScenarioCore.countdownMilestoneSeconds(secondsToNext: entry.secondsToNext),
        entry.expectedMilestone,
        entry.name
      )
    }
  }

  func testRouteEtaMatchesSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.etaCases {
      XCTAssertEqual(
        NavigationScenarioCore.routeEtaMinutes(
          distanceMeters: entry.distanceMeters,
          providerDurationSeconds: entry.providerDurationSeconds
        ),
        entry.expectedEtaMinutes,
        entry.name
      )
    }
  }

  func testNavigationAdvanceDecisionsMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.advanceDecisions {
      XCTAssertEqual(
        NavigationScenarioCore.shouldAdvanceStep(
          distanceToManeuverMeters: entry.distanceToManeuverMeters,
          accuracyMeters: entry.accuracyMeters
        ),
        entry.expectedAdvance,
        entry.name
      )
    }
  }

  func testNavigationOffRouteDecisionsMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.offRouteDecisions {
      XCTAssertEqual(
        NavigationScenarioCore.shouldTriggerOffRoute(
          deviationMeters: entry.deviationMeters,
          accuracyMeters: entry.accuracyMeters
        ),
        entry.expectedOffRoute,
        entry.name
      )
    }
  }

  func testNavigationAutoRecalculateDecisionsMatchSharedFixtures() throws {
    let fixtures = try SharedParityFixtureLoader.load()
    for entry in fixtures.scenarioCases.autoRecalculate {
      XCTAssertEqual(
        NavigationScenarioCore.shouldAllowAutoRecalculate(
          isRouteRecalculating: entry.isRouteRecalculating,
          elapsedSinceLastRecalculateMs: entry.elapsedMs
        ),
        entry.expectedAllowed,
        entry.name
      )
    }
  }
}

private enum SharedParityFixtureLoader {
  struct Fixtures: Decodable {
    let addressCases: [AddressCase]
    let instructionCases: [InstructionCase]
    let scenarioCases: ScenarioCases
  }

  struct AddressCase: Decodable {
    let name: String
    let address: [String: String]?
    let fallback: String
    let expected: String
  }

  struct InstructionCase: Decodable {
    let name: String
    let maneuverType: String
    let modifier: String?
    let roadName: String?
    let expectedParity: String
  }

  struct ScenarioCases: Decodable {
    let thresholds: [ThresholdCase]
    let countdowns: [CountdownCase]
    let timeCountdowns: [TimeCountdownCase]
    let etaCases: [EtaCase]
    let advanceDecisions: [AdvanceDecisionCase]
    let offRouteDecisions: [OffRouteDecisionCase]
    let autoRecalculate: [AutoRecalculateCase]
  }

  struct ThresholdCase: Decodable {
    let name: String
    let accuracyMeters: Double
    let expectedManeuverAdvance: Double
    let expectedOffRoute: Int
    let expectedImmediate: Int
  }

  struct CountdownCase: Decodable {
    let name: String
    let distanceToNextMeters: Int
    let expectedMilestone: Int?
  }

  struct TimeCountdownCase: Decodable {
    let name: String
    let secondsToNext: Int
    let expectedMilestone: Int?
  }

  struct EtaCase: Decodable {
    let name: String
    let distanceMeters: Int
    let providerDurationSeconds: Double
    let expectedEtaMinutes: Int
  }

  struct AdvanceDecisionCase: Decodable {
    let name: String
    let distanceToManeuverMeters: Double
    let accuracyMeters: Double
    let expectedAdvance: Bool
  }

  struct OffRouteDecisionCase: Decodable {
    let name: String
    let deviationMeters: Int?
    let accuracyMeters: Double
    let expectedOffRoute: Bool
  }

  struct AutoRecalculateCase: Decodable {
    let name: String
    let elapsedMs: Int
    let isRouteRecalculating: Bool
    let expectedAllowed: Bool
  }

  static func load() throws -> Fixtures {
    let url = try locateFixtureURL()
    let data = try Data(contentsOf: url)
    return try JSONDecoder().decode(Fixtures.self, from: data)
  }

  private static func locateFixtureURL() throws -> URL {
    var current = URL(fileURLWithPath: #filePath)
      .deletingLastPathComponent()
    while true {
      let candidate = current
        .appendingPathComponent("shared", isDirectory: true)
        .appendingPathComponent("test-fixtures", isDirectory: true)
        .appendingPathComponent("navigation-parity-fixtures.json", isDirectory: false)
      if FileManager.default.fileExists(atPath: candidate.path) {
        return candidate
      }
      let parent = current.deletingLastPathComponent()
      if parent.path == current.path {
        throw NSError(domain: "NavigationParityFixturesTests", code: 1, userInfo: [
          NSLocalizedDescriptionKey: "Could not locate shared parity fixtures."
        ])
      }
      current = parent
    }
  }
}
