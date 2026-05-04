import CoreMotion
import SwiftUI

struct RootView: View {
  @StateObject private var model = AppModel()
  @StateObject private var shakeGestureMonitor = ShakeGestureMonitor()
  @State private var didBootstrap = false

  var body: some View {
    Group {
      switch model.launchState {
      case .bootstrapping:
        BootstrappingView()
      case .onboarding:
        OnboardingView(model: model)
      case .permissions:
        PermissionsView(model: model)
      case .ready:
        RootNavigationView(model: model)
      }
    }
    .task {
      guard !didBootstrap else { return }
      didBootstrap = true
      await model.bootstrap()
    }
    .onAppear {
      configureShakeGestureMonitor()
    }
    .onReceive(model.$settings) { _ in
      configureShakeGestureMonitor()
    }
    .onReceive(model.$activeNavigationState) { _ in
      configureShakeGestureMonitor()
    }
    .onDisappear {
      shakeGestureMonitor.stop()
    }
  }

  private func configureShakeGestureMonitor() {
    let settings = model.settings
    shakeGestureMonitor.configure(
      isEnabled: settings.shakeGestureEnabled && !model.activeNavigationState.currentInstruction.isEmpty,
      strength: settings.shakeStrength
    ) {
      model.onShakeGestureDetected()
    }
  }
}

private struct RootNavigationView: View {
  @ObservedObject var model: AppModel

  var body: some View {
    NavigationStack(path: $model.path) {
      HomeView(model: model)
        .navigationDestination(for: AppRoute.self) { route in
          switch route {
          case .onboarding:
            OnboardingView(model: model)
          case .permissions:
            PermissionsView(model: model)
          case .search:
            SearchView(model: model)
          case .placeDetails(let placeID):
            PlaceDetailsView(model: model, placeID: placeID)
          case .routeSummary(let placeID):
            RouteSummaryView(model: model, placeID: placeID)
          case .headingAlign(let placeID):
            HeadingAlignView(model: model, placeID: placeID)
          case .activeNavigation(let placeID):
            ActiveNavigationView(model: model, placeID: placeID)
          case .arrival(let placeID):
            ArrivalView(model: model, placeID: placeID)
          case .currentPosition:
            CurrentPositionView(model: model)
          case .favorites:
            FavoritesView(model: model)
          case .settings:
            SettingsView(model: model)
          case .helpPrivacy:
            HelpPrivacyView()
          }
        }
    }
    .safeAreaInset(edge: .bottom, alignment: .trailing) {
      if model.path.last != .settings {
        HStack {
          Spacer()
          SecondaryActionButton(
            title: L10n.text("home.action.settings", table: .home),
            systemImage: "gearshape"
          ) {
            model.openSettings()
          }
          .frame(maxWidth: 260)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 6)
        .background(.regularMaterial)
      }
    }
  }
}

private struct BootstrappingView: View {
  var body: some View {
    VStack(spacing: 20) {
      ProgressView()
        .controlSize(.large)
      Text(L10n.text("root.bootstrapping", table: .root))
        .font(.body)
        .foregroundStyle(.secondary)
    }
    .padding(24)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color(.systemGroupedBackground))
    .accessibilityElement(children: .combine)
  }
}

@MainActor
private final class ShakeGestureMonitor: ObservableObject {
  private let motionManager = CMMotionManager()
  private var lastShakeAt = Date.distantPast
  private var currentStrength: ShakeStrength = .medium
  private var onShake: (() -> Void)?

  func configure(isEnabled: Bool, strength: ShakeStrength, onShake: @escaping () -> Void) {
    self.onShake = onShake
    currentStrength = strength

    guard isEnabled, motionManager.isAccelerometerAvailable else {
      stop()
      return
    }

    if !motionManager.isAccelerometerActive {
      motionManager.accelerometerUpdateInterval = 0.05
      motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
        guard let data else { return }
        Task { @MainActor in
          self?.handle(acceleration: data.acceleration)
        }
      }
    }
  }

  func stop() {
    motionManager.stopAccelerometerUpdates()
  }

  private func handle(acceleration: CMAcceleration) {
    let force = sqrt(
      acceleration.x * acceleration.x +
      acceleration.y * acceleration.y +
      acceleration.z * acceleration.z
    )
    guard force >= currentStrength.thresholdG else { return }

    let now = Date()
    guard now.timeIntervalSince(lastShakeAt) >= Self.debounceInterval else { return }
    lastShakeAt = now
    onShake?()
  }

  private static let debounceInterval: TimeInterval = 1.4
}

private extension ShakeStrength {
  var thresholdG: Double {
    switch self {
    case .light:
      return 2.2
    case .medium:
      return 2.8
    case .strong:
      return 3.4
    }
  }
}

#Preview {
  RootView()
}
