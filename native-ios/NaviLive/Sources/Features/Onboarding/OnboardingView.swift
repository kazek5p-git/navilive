import SwiftUI

struct OnboardingView: View {
  @ObservedObject var model: AppModel

  var body: some View {
    NavigationStack {
      List {
        Section {
          Text(L10n.text("onboarding.intro", table: .onboarding))
            .font(.body)
            .accessibilityAddTraits(.isStaticText)
        } header: {
          Text(L10n.text("onboarding.section.intro", table: .onboarding))
        }

        Section {
          Label(L10n.text("onboarding.feature.search", table: .onboarding), systemImage: "magnifyingglass")
          Label(L10n.text("onboarding.feature.route", table: .onboarding), systemImage: "map")
          Label(L10n.text("onboarding.feature.navigation", table: .onboarding), systemImage: "figure.walk")
        } header: {
          Text(L10n.text("onboarding.section.features", table: .onboarding))
        }

        Section {
          Label(L10n.text("onboarding.feature.actions", table: .onboarding), systemImage: "gearshape")
          Label(L10n.text("onboarding.feature.favorites", table: .onboarding), systemImage: "star")
          Label(L10n.text("onboarding.feature.safety", table: .onboarding), systemImage: "exclamationmark.triangle")
        } header: {
          Text(L10n.text("onboarding.section.on_route", table: .onboarding))
        }

        Section {
          PrimaryActionButton(
            title: L10n.text("onboarding.action.start", table: .onboarding),
            systemImage: "arrow.right.circle.fill"
          ) {
            model.completeOnboarding()
          }
        } footer: {
          Text(L10n.text("onboarding.footer", table: .onboarding))
        }
      }
      .navigationTitle(L10n.text("onboarding.title", table: .onboarding))
      .navigationBarTitleDisplayMode(.inline)
    }
  }
}

#Preview {
  OnboardingView(model: AppModel())
}
