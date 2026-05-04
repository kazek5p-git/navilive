import SwiftUI

struct RouteSummaryView: View {
  @ObservedObject var model: AppModel
  let placeID: String
  @State private var didRequestRoute = false

  var body: some View {
    Group {
      if let place = model.place(for: placeID) {
        List {
          Section {
            VStack(alignment: .leading, spacing: 8) {
              Text(place.name)
                .font(.headline)
              Text(place.address)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
          } header: {
            Text(L10n.text("route.section.destination", table: .navigation))
          }

          if let summary = model.selectedRouteSummary {
            Section {
              LabeledContent(
                L10n.text("route.label.distance", table: .navigation),
                value: AppFormatters.distance(summary.distanceMeters)
              )
              LabeledContent(
                L10n.text("route.label.eta", table: .navigation),
                value: AppFormatters.eta(minutes: summary.etaMinutes)
              )
              LabeledContent(
                L10n.text("route.label.mode", table: .navigation),
                value: summary.modeLabel
              )
            } header: {
              Text(L10n.text("route.section.summary", table: .navigation))
            }

            Section {
              Text(summary.currentInstruction)
                .font(.body)
              if !summary.nextInstruction.isEmpty {
                Text(summary.nextInstruction)
                  .font(.body)
                  .foregroundStyle(.secondary)
              }
            } header: {
              Text(L10n.text("route.section.guidance", table: .navigation))
            }

            if !summary.steps.isEmpty {
              Section {
                ForEach(summary.steps) { step in
                  VStack(alignment: .leading, spacing: 4) {
                    Text(step.instruction)
                    Text(AppFormatters.distance(step.distanceMeters))
                      .font(.footnote)
                      .foregroundStyle(.secondary)
                  }
                  .accessibilityElement(children: .combine)
                }
              } header: {
                Text(L10n.text("route.section.steps", table: .navigation))
              }
            }

            Section {
              PrimaryActionButton(
                title: L10n.text("route.action.start_guidance", table: .navigation),
                systemImage: "location.fill"
              ) {
                model.beginActiveNavigation()
                model.openActiveNavigation(placeID)
              }

              SecondaryActionButton(
                title: L10n.text("route.action.refresh", table: .navigation),
                systemImage: "arrow.clockwise"
              ) {
                Task { await model.prepareRoute(for: placeID) }
              }
            }
          } else if model.isRouting {
            Section {
              HStack(spacing: 12) {
                ProgressView()
                Text(L10n.text("route.status.loading", table: .navigation))
              }
            }
          } else {
            Section {
              StatusCard(
                title: L10n.text("route.status.missing_title", table: .navigation),
                message: L10n.text("route.status.missing_message", table: .navigation),
                tone: .warning
              )
              PrimaryActionButton(
                title: L10n.text("route.action.prepare", table: .navigation),
                systemImage: "arrow.triangle.turn.up.right.circle.fill"
              ) {
                Task { await model.prepareRoute(for: placeID) }
              }
            }
          }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L10n.text("route.title", table: .navigation))
        .navigationBarTitleDisplayMode(.inline)
        .task {
          guard !didRequestRoute, model.selectedRouteSummary == nil else { return }
          didRequestRoute = true
          await model.prepareRoute(for: placeID)
        }
      } else {
        UnavailableStateView(
          title: L10n.text("route.unavailable.title", table: .navigation),
          systemImage: "point.topleft.down.curvedto.point.bottomright.up",
          message: L10n.text("route.unavailable.message", table: .navigation)
        )
      }
    }
  }
}

#Preview {
  NavigationStack {
    RouteSummaryView(model: AppModel(), placeID: "preview")
  }
}
