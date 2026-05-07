import Foundation

struct LiveNavigationUpdate {
  let state: ActiveNavigationState
  let currentStepIndex: Int
  let upcomingInstruction: String?
  let currentStepKind: RouteStepKind
  let upcomingStepKind: RouteStepKind?
  let stepChanged: Bool
  let offRouteTriggered: Bool
  let shouldAutoRecalculate: Bool
  let hasArrived: Bool
}

private struct RouteProgressProjection {
  let distanceAlongRouteMeters: Double
  let remainingRouteMeters: Double
  let lateralDistanceMeters: Double
}

private struct SegmentProjection {
  let ratio: Double
  let lengthMeters: Double
  let lateralDistanceMeters: Double
}

final class LiveNavigationEngine {
  private struct RouteSession {
    let destination: Place
    let steps: [RouteStep]
    let pathPoints: [GeoPoint]
    let stepDistancesAlongRoute: [Double]
    var currentStepIndex: Int
  }

  private static let approachManeuverType = "approach"
  private static let routeProjectionBacktrackToleranceMeters = 25.0
  private static let routeProjectionLookAheadToleranceMeters = 45.0

  private var session: RouteSession?
  private var lastAutoRecalculateAt: Date = .distantPast

  var currentDestination: Place? {
    session?.destination
  }

  func loadRoute(destination: Place, summary: RouteSummary, fix: LocationFix?) -> ActiveNavigationState {
    let normalizedSteps = summary.steps.isEmpty
      ? [
          RouteStep(
            instruction: summary.currentInstruction.isEmpty
              ? L10n.text("route.follow_default", table: .navigation)
              : summary.currentInstruction,
            distanceMeters: max(summary.distanceMeters, 1),
            maneuverPoint: destination.point,
            roadName: destination.name
          )
        ]
      : summary.steps

    let routeLength = routeLengthMeters(summary.pathPoints)
    let stepDistances = stepDistancesAlongRoute(
      steps: normalizedSteps,
      pathPoints: summary.pathPoints,
      routeLengthMeters: routeLength
    )
    session = RouteSession(
      destination: destination,
      steps: normalizedSteps,
      pathPoints: summary.pathPoints,
      stepDistancesAlongRoute: stepDistances,
      currentStepIndex: 0
    )

    return buildState(
      currentStepIndex: 0,
      fix: fix,
      previous: ActiveNavigationState(),
      isOffRoute: false,
      isRecalculating: false,
      offRouteDistanceMeters: nil
    )
  }

  func rebuildCurrentState(fix: LocationFix?, previous: ActiveNavigationState) -> ActiveNavigationState? {
    guard let session else { return nil }
    return buildState(
      currentStepIndex: session.currentStepIndex,
      fix: fix,
      previous: previous,
      isOffRoute: false,
      isRecalculating: false,
      offRouteDistanceMeters: nil
    )
  }

  func update(
    fix: LocationFix,
    previous: ActiveNavigationState,
    autoRecalculateEnabled: Bool
  ) -> LiveNavigationUpdate? {
    guard var session else { return nil }

    let deviation = routeDeviationMeters(pathPoints: session.pathPoints, point: fix.point)
    let isOffRoute = NavigationScenarioCore.shouldTriggerOffRoute(
      deviationMeters: deviation,
      accuracyMeters: fix.accuracyMeters
    )
    let distanceToDestination = session.destination.point.map { Int(fix.point.distance(to: $0).rounded()) } ?? 0
    let arrivedThreshold = max(12, Int(fix.accuracyMeters.rounded()))

    if distanceToDestination > 0 && distanceToDestination <= arrivedThreshold {
      let state = buildState(
        currentStepIndex: session.currentStepIndex,
        fix: fix,
        previous: previous,
        isOffRoute: false,
        isRecalculating: false,
        offRouteDistanceMeters: nil
      )
      return LiveNavigationUpdate(
        state: state,
        currentStepIndex: session.currentStepIndex,
        upcomingInstruction: session.steps[safe: session.currentStepIndex + 1]?.instruction,
        currentStepKind: session.steps[safe: session.currentStepIndex]?.kind ?? .instruction,
        upcomingStepKind: session.steps[safe: session.currentStepIndex + 1]?.kind,
        stepChanged: false,
        offRouteTriggered: false,
        shouldAutoRecalculate: false,
        hasArrived: true
      )
    }

    let isApproachingRouteStart = session.steps[safe: session.currentStepIndex]?.maneuverType == Self.approachManeuverType
    if !isApproachingRouteStart, isOffRoute, let deviation {
      let state = buildState(
        currentStepIndex: session.currentStepIndex,
        fix: fix,
        previous: previous,
        isOffRoute: true,
        isRecalculating: previous.isRecalculating,
        offRouteDistanceMeters: deviation
      )
      let autoRecalculate = autoRecalculateEnabled && shouldAutoRecalculate(now: fix.timestamp)
      if autoRecalculate {
        lastAutoRecalculateAt = fix.timestamp
      }
      return LiveNavigationUpdate(
        state: state,
        currentStepIndex: session.currentStepIndex,
        upcomingInstruction: session.steps[safe: session.currentStepIndex + 1]?.instruction,
        currentStepKind: session.steps[safe: session.currentStepIndex]?.kind ?? .instruction,
        upcomingStepKind: session.steps[safe: session.currentStepIndex + 1]?.kind,
        stepChanged: false,
        offRouteTriggered: !previous.isOffRoute,
        shouldAutoRecalculate: autoRecalculate,
        hasArrived: false
      )
    }

    let nextStepIndex = resolveStepIndex(session: session, fix: fix)
    let stepChanged = nextStepIndex != session.currentStepIndex
    session.currentStepIndex = nextStepIndex
    self.session = session

    let state = buildState(
      currentStepIndex: nextStepIndex,
      fix: fix,
      previous: previous,
      isOffRoute: false,
      isRecalculating: false,
      offRouteDistanceMeters: nil
    )

    return LiveNavigationUpdate(
      state: state,
      currentStepIndex: nextStepIndex,
      upcomingInstruction: session.steps[safe: nextStepIndex + 1]?.instruction,
      currentStepKind: session.steps[safe: nextStepIndex]?.kind ?? .instruction,
      upcomingStepKind: session.steps[safe: nextStepIndex + 1]?.kind,
      stepChanged: stepChanged,
      offRouteTriggered: false,
      shouldAutoRecalculate: false,
      hasArrived: false
    )
  }

  func reset() {
    session = nil
    lastAutoRecalculateAt = .distantPast
  }

  private func shouldAutoRecalculate(now: Date) -> Bool {
    NavigationScenarioCore.shouldAllowAutoRecalculate(
      isRouteRecalculating: false,
      elapsedSinceLastRecalculateMs: Int((now.timeIntervalSince(lastAutoRecalculateAt) * 1000.0).rounded())
    )
  }

  private func resolveStepIndex(session: RouteSession, fix: LocationFix) -> Int {
    var index = session.currentStepIndex
    while index < session.steps.count - 1 {
      let currentStep = session.steps[safe: index]
      if currentStep?.maneuverType == Self.approachManeuverType {
        guard let approachTarget = currentStep?.maneuverPoint else { break }
        if NavigationScenarioCore.shouldAdvanceStep(
          distanceToManeuverMeters: fix.point.distance(to: approachTarget),
          accuracyMeters: fix.accuracyMeters
        ) {
          index += 1
          continue
        }
        break
      }
      guard let nextManeuver = session.steps[safe: index + 1]?.maneuverPoint else { break }
      let projectedProgress = routeProgressProjection(session: session, point: fix.point, currentStepIndex: index)
      let nextDistanceAlongRoute = session.stepDistancesAlongRoute[safe: index + 1] ?? .greatestFiniteMagnitude
      let passThreshold = maneuverPassThresholdMeters(accuracyMeters: fix.accuracyMeters)
      let hasPassedManeuver = projectedProgress != nil &&
        projectedProgress!.distanceAlongRouteMeters >= nextDistanceAlongRoute + passThreshold
      let fallbackPassedManeuver = projectedProgress == nil &&
        fix.point.distance(to: nextManeuver) <= passThreshold
      if hasPassedManeuver || fallbackPassedManeuver {
        index += 1
      } else {
        break
      }
    }
    return index
  }

  private func buildState(
    currentStepIndex: Int,
    fix: LocationFix?,
    previous: ActiveNavigationState,
    isOffRoute: Bool,
    isRecalculating: Bool,
    offRouteDistanceMeters: Int?
  ) -> ActiveNavigationState {
    guard let session else {
      return ActiveNavigationState()
    }

    let safeIndex = min(max(currentStepIndex, 0), session.steps.count - 1)
    let currentStep = session.steps[safeIndex]
    let nextStep = safeIndex < session.steps.count - 1 ? session.steps[safeIndex + 1] : nil
    let routeProgress = fix.flatMap {
      routeProgressProjection(session: session, point: $0.point, currentStepIndex: safeIndex) ??
        routeProgressProjection(pathPoints: session.pathPoints, point: $0.point)
    }

    let distanceToNext: Int = {
      if currentStep.maneuverType == Self.approachManeuverType,
         let maneuverPoint = currentStep.maneuverPoint,
         let fix {
        return max(Int(fix.point.distance(to: maneuverPoint).rounded()), 1)
      }
      if nextStep != nil, let routeProgress {
        let nextDistance = session.stepDistancesAlongRoute[safe: safeIndex + 1] ?? routeProgress.distanceAlongRouteMeters
        return max(Int((nextDistance - routeProgress.distanceAlongRouteMeters).rounded()), 0)
      }
      if let maneuverPoint = nextStep?.maneuverPoint, let fix {
        return max(Int(fix.point.distance(to: maneuverPoint).rounded()), 1)
      }
      if let nextStep, nextStep.distanceMeters > 0 {
        return nextStep.distanceMeters
      }
      if let destinationPoint = session.destination.point, let fix {
        return max(Int(fix.point.distance(to: destinationPoint).rounded()), 0)
      }
      return max(currentStep.distanceMeters, 1)
    }()

    let remainingFromSteps = session.steps.dropFirst(safeIndex).reduce(0) { $0 + $1.distanceMeters }
    let remainingFromDestination = {
      if let destinationPoint = session.destination.point, let fix {
        return Int(fix.point.distance(to: destinationPoint).rounded())
      }
      return 0
    }()

    return ActiveNavigationState(
      currentInstruction: currentStep.instruction,
      nextInstruction: nextStep?.instruction ?? L10n.text("active.destination_ahead", table: .navigation),
      currentStepIndex: safeIndex,
      distanceToNextMeters: distanceToNext,
      remainingDistanceMeters: routeProgress.map { max(Int($0.remainingRouteMeters.rounded()), 0) } ?? max(remainingFromSteps, remainingFromDestination),
      progressLabel: L10n.text("active.progress", table: .navigation, safeIndex + 1, session.steps.count),
      isPaused: previous.isPaused,
      isOffRoute: isOffRoute,
      isRecalculating: isRecalculating,
      offRouteDistanceMeters: offRouteDistanceMeters
    )
  }

  private func routeDeviationMeters(pathPoints: [GeoPoint], point: GeoPoint) -> Int? {
    guard pathPoints.count >= 3 else { return nil }
    return routeProgressProjection(pathPoints: pathPoints, point: point)
      .map { Int($0.lateralDistanceMeters.rounded()) }
  }

  private func routeLengthMeters(_ pathPoints: [GeoPoint]) -> Double {
    guard pathPoints.count >= 2 else { return 0 }
    var length = 0.0
    for index in 0..<(pathPoints.count - 1) {
      length += pathPoints[index].distance(to: pathPoints[index + 1])
    }
    return length
  }

  private func stepDistancesAlongRoute(
    steps: [RouteStep],
    pathPoints: [GeoPoint],
    routeLengthMeters: Double
  ) -> [Double] {
    var distances: [Double] = []
    var previous = 0.0
    for (index, step) in steps.enumerated() {
      let raw = step.maneuverPoint
        .flatMap { routeProgressProjection(pathPoints: pathPoints, point: $0)?.distanceAlongRouteMeters } ??
        (index == 0 ? 0 : routeLengthMeters)
      let normalized = min(max(raw, previous), routeLengthMeters)
      distances.append(normalized)
      previous = normalized
    }
    return distances
  }

  private func maneuverPassThresholdMeters(accuracyMeters: Double) -> Double {
    min(max(accuracyMeters, 5), 12)
  }

  private func routeProgressProjection(
    session: RouteSession,
    point: GeoPoint,
    currentStepIndex: Int
  ) -> RouteProgressProjection? {
    let routeLength = routeLengthMeters(session.pathPoints)
    let currentAlong = session.stepDistancesAlongRoute[safe: currentStepIndex] ?? 0
    let nextAlong = session.stepDistancesAlongRoute[safe: currentStepIndex + 1] ?? routeLength
    return routeProgressProjection(
      pathPoints: session.pathPoints,
      point: point,
      minimumDistanceAlongRouteMeters: max(currentAlong - Self.routeProjectionBacktrackToleranceMeters, 0),
      maximumDistanceAlongRouteMeters: min(nextAlong + Self.routeProjectionLookAheadToleranceMeters, routeLength)
    )
  }

  private func routeProgressProjection(
    pathPoints: [GeoPoint],
    point: GeoPoint,
    minimumDistanceAlongRouteMeters: Double = 0,
    maximumDistanceAlongRouteMeters: Double = .greatestFiniteMagnitude
  ) -> RouteProgressProjection? {
    guard pathPoints.count >= 2 else { return nil }
    var bestProjection: RouteProgressProjection?
    var distanceBeforeSegment = 0.0
    let totalLength = routeLengthMeters(pathPoints)
    for index in 0..<(pathPoints.count - 1) {
      let segmentProjection = projectOntoSegment(
        point: point,
        start: pathPoints[index],
        end: pathPoints[index + 1]
      )
      let distanceAlongRoute = distanceBeforeSegment + segmentProjection.lengthMeters * segmentProjection.ratio
      if distanceAlongRoute < minimumDistanceAlongRouteMeters ||
        distanceAlongRoute > maximumDistanceAlongRouteMeters {
        distanceBeforeSegment += segmentProjection.lengthMeters
        continue
      }
      let projection = RouteProgressProjection(
        distanceAlongRouteMeters: distanceAlongRoute,
        remainingRouteMeters: max(totalLength - distanceAlongRoute, 0),
        lateralDistanceMeters: segmentProjection.lateralDistanceMeters
      )
      if bestProjection == nil || projection.lateralDistanceMeters < bestProjection!.lateralDistanceMeters {
        bestProjection = projection
      }
      distanceBeforeSegment += segmentProjection.lengthMeters
    }
    return bestProjection
  }

  private func projectOntoSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint) -> SegmentProjection {
    let latitudeReference = ((point.latitude + start.latitude + end.latitude) / 3.0) * .pi / 180.0
    let earthRadius = 6_371_000.0

    func project(_ geoPoint: GeoPoint) -> (x: Double, y: Double) {
      let x = geoPoint.longitude * .pi / 180.0 * earthRadius * cos(latitudeReference)
      let y = geoPoint.latitude * .pi / 180.0 * earthRadius
      return (x, y)
    }

    let pointProjection = project(point)
    let startProjection = project(start)
    let endProjection = project(end)
    let dx = endProjection.x - startProjection.x
    let dy = endProjection.y - startProjection.y
    let lengthSquared = (dx * dx) + (dy * dy)

    guard lengthSquared > 0 else {
      return SegmentProjection(
        ratio: 0,
        lengthMeters: 0,
        lateralDistanceMeters: hypot(pointProjection.x - startProjection.x, pointProjection.y - startProjection.y)
      )
    }

    let ratio = min(
      max((((pointProjection.x - startProjection.x) * dx) + ((pointProjection.y - startProjection.y) * dy)) / lengthSquared, 0),
      1
    )
    let nearestX = startProjection.x + (ratio * dx)
    let nearestY = startProjection.y + (ratio * dy)
    return SegmentProjection(
      ratio: ratio,
      lengthMeters: sqrt(lengthSquared),
      lateralDistanceMeters: hypot(pointProjection.x - nearestX, pointProjection.y - nearestY)
    )
  }
}

private extension Array {
  subscript(safe index: Int) -> Element? {
    indices.contains(index) ? self[index] : nil
  }
}
