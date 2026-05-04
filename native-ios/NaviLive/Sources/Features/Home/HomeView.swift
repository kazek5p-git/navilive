import SwiftUI

struct HomeView: View {
  @ObservedObject var model: AppModel

  var body: some View {
    List {
      Section {
        StatusCard(
          title: L10n.text("home.section.location", table: .home),
          message: currentLocationMessage,
          tone: model.hasLocationPermission ? .success : .warning
        )
        .accessibilityHint(L10n.text("home.location.hint", table: .home))
      }

      Section {
        SecondaryActionButton(
          title: liveTrackingActionTitle,
          systemImage: liveTrackingSystemImage
        ) {
          model.toggleLiveTracking()
        }
      }

      Section {
        PrimaryActionButton(
          title: L10n.text("home.action.search", table: .home),
          systemImage: "magnifyingglass"
        ) {
          model.openSearch()
        }

        SecondaryActionButton(
          title: L10n.text("home.action.current_position", table: .home),
          systemImage: "location.viewfinder"
        ) {
          model.openCurrentPosition()
        }

        SecondaryActionButton(
          title: L10n.text("home.action.favorites", table: .home),
          systemImage: "star"
        ) {
          model.openFavorites()
        }
      }

      if let lastRoutePlaceID = model.lastRoutePlaceID,
         let lastPlace = model.place(for: lastRoutePlaceID) {
        Section {
          Button {
            model.openPlaceDetails(lastPlace.id)
          } label: {
            VStack(alignment: .leading, spacing: 4) {
              Text(lastPlace.name)
                .font(.headline)
              Text(lastPlace.address)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
          }
          .accessibilityHint(L10n.text("home.resume_last_route.hint", table: .home))
        } header: {
          Text(L10n.text("home.section.last_route", table: .home))
        }
      }
    }
    .listStyle(.insetGrouped)
    .navigationTitle(L10n.text("home.title", table: .home))
    .navigationBarTitleDisplayMode(.large)
    .refreshable {
      await model.loadCurrentAddress()
    }
  }

  private var currentLocationMessage: String {
    guard !model.currentLocationDescription.isEmpty else {
      return L10n.text("home.location.waiting", table: .home)
    }

    guard let accuracyMeters = model.locationService.latestFix?.accuracyMeters else {
      return model.currentLocationDescription
    }

    let accuracyLabel = L10n.text(
      "home.location.accuracy",
      table: .home,
      Int(accuracyMeters.rounded())
    )
    return "\(model.currentLocationDescription)\n\(accuracyLabel)"
  }

  private var liveTrackingActionTitle: String {
    guard model.hasLocationPermission else {
      return L10n.text("home.action.grant_location_access", table: .home)
    }

    return model.isLiveTracking
      ? L10n.text("home.action.stop_live_tracking", table: .home)
      : L10n.text("home.action.start_live_tracking", table: .home)
  }

  private var liveTrackingSystemImage: String {
    guard model.hasLocationPermission else { return "location.circle" }
    return model.isLiveTracking ? "stop.circle" : "location.viewfinder"
  }
}

#Preview {
  NavigationStack {
    HomeView(model: AppModel())
  }
}
