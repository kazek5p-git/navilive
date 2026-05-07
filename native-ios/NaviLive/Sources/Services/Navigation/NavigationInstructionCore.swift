import Foundation

struct NavigationInstructionDescriptor {
  enum Strategy: String {
    case departNamed = "DepartNamed"
    case arrive = "Arrive"
    case turnNamed = "TurnNamed"
    case turnGenericNamed = "TurnGenericNamed"
    case turnBareModifier = "TurnBareModifier"
    case continueNamed = "ContinueNamed"
    case proceedTowardNamed = "ProceedTowardNamed"
  }

  let strategy: Strategy
  let roadName: String?
  let normalizedModifier: String?

  func paritySignature() -> String {
    "\(strategy.rawValue)|\(roadName ?? "-")|\(normalizedModifier ?? "-")"
  }
}

enum NavigationInstructionCore {
  static func describe(
    maneuverType: String,
    modifier: String?,
    roadName: String?
  ) -> NavigationInstructionDescriptor {
    let normalizedRoad = roadName?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .nilIfBlank
    let normalizedModifier = modifier
      .map { SharedProductRules.Instructions.normalizeModifier($0) }
      .flatMap { SharedProductRules.Instructions.supportedModifiers.contains($0) ? $0 : nil }

    switch maneuverType {
    case "depart":
      return NavigationInstructionDescriptor(strategy: .departNamed, roadName: normalizedRoad, normalizedModifier: nil)
    case "arrive":
      return NavigationInstructionDescriptor(strategy: .arrive, roadName: nil, normalizedModifier: nil)
    case "turn":
      return turnDescriptor(normalizedRoad: normalizedRoad, normalizedModifier: normalizedModifier)
    case "new name", "continue":
      return NavigationInstructionDescriptor(
        strategy: .continueNamed,
        roadName: normalizedRoad,
        normalizedModifier: nil
      )
    default:
      if let normalizedModifier {
        return turnDescriptor(normalizedRoad: normalizedRoad, normalizedModifier: normalizedModifier)
      }
      return NavigationInstructionDescriptor(
        strategy: .proceedTowardNamed,
        roadName: normalizedRoad,
        normalizedModifier: nil
      )
    }
  }

  private static func turnDescriptor(
    normalizedRoad: String?,
    normalizedModifier: String?
  ) -> NavigationInstructionDescriptor {
    if let normalizedRoad, let normalizedModifier {
      return NavigationInstructionDescriptor(
        strategy: .turnNamed,
        roadName: normalizedRoad,
        normalizedModifier: normalizedModifier
      )
    }
    if let normalizedRoad {
      return NavigationInstructionDescriptor(
        strategy: .turnGenericNamed,
        roadName: normalizedRoad,
        normalizedModifier: nil
      )
    }
    if let normalizedModifier {
      return NavigationInstructionDescriptor(
        strategy: .turnBareModifier,
        roadName: nil,
        normalizedModifier: normalizedModifier
      )
    }
    return NavigationInstructionDescriptor(
      strategy: .turnGenericNamed,
      roadName: nil,
      normalizedModifier: nil
    )
  }
}


enum RouteStepSimplificationCore {
  private static let shortConnectorStepMaxMeters = 45

  static func shouldSuppressRouteStep(
    _ step: RouteStep,
    previous: RouteStep?,
    index: Int,
    lastIndex: Int
  ) -> Bool {
    guard let previous else { return false }
    guard index > 0, index < lastIndex else { return false }
    guard step.kind == .instruction, previous.kind == .instruction else { return false }
    if step.maneuverType?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "arrive" {
      return false
    }
    let currentRoad = normalizedRouteRoadName(step.roadName)
    let previousRoad = normalizedRouteRoadName(previous.roadName)
    let isSameRoad = currentRoad != nil && currentRoad == previousRoad
    let isShortConnector = step.distanceMeters <= shortConnectorStepMaxMeters
    if isTurnLikeManeuver(step) {
      return isShortConnector && (currentRoad == nil || isSameRoad)
    }
    guard let currentRoad else { return isShortConnector }
    guard currentRoad == previousRoad else { return false }
    return step.distanceMeters <= 35
  }

  static func isTurnLikeManeuver(_ step: RouteStep) -> Bool {
    let type = step.maneuverType?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .lowercased() ?? ""
    let modifier = step.maneuverModifier
      .map { SharedProductRules.Instructions.normalizeModifier($0) } ?? ""
    if modifier == "straight" { return false }
    if SharedProductRules.Instructions.supportedModifiers.contains(modifier) { return true }
    return [
      "turn",
      "end of road",
      "fork",
      "merge",
      "on ramp",
      "off ramp",
      "roundabout turn",
      "exit roundabout",
      "rotary",
      "roundabout"
    ].contains(type)
  }

  private static func normalizedRouteRoadName(_ value: String?) -> String? {
    let normalized = value?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .lowercased()
      .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    return normalized?.isEmpty == false ? normalized : nil
  }
}
private extension String {
  var nilIfBlank: String? {
    trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
  }
}
