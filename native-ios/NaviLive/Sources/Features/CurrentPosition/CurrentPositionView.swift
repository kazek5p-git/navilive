import SwiftUI

struct CurrentPositionView: View {
  @ObservedObject var model: AppModel
  @State private var isSaveDialogPresented = false
  @State private var favoriteName = ""

  private var trimmedFavoriteName: String {
    favoriteName.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  var body: some View {
    List {
      Section {
        StatusCard(
          title: L10n.text("current.title", table: .home),
          message: model.currentLocationDescription.isEmpty
            ? L10n.text("home.location.waiting", table: .home)
            : model.currentLocationDescription,
          tone: model.hasLocationPermission ? .success : .warning
        )
      }

      Section {
        PrimaryActionButton(
          title: L10n.text("current.action.save_favorite", table: .home),
          systemImage: "star"
        ) {
          favoriteName = ""
          isSaveDialogPresented = true
        }
        .accessibilitySortPriority(2)

        SecondaryActionButton(
          title: L10n.text("current.action.refresh", table: .home),
          systemImage: "arrow.clockwise"
        ) {
          Task { await model.loadCurrentAddress() }
        }
        .accessibilitySortPriority(1)
      }

      Section {
        LabeledContent(
          L10n.text("current.label.accuracy", table: .home),
          value: AppFormatters.accuracy(model.locationService.latestFix?.accuracyMeters)
        )
      } header: {
        Text(L10n.text("current.section.details", table: .home))
      }

      if !model.currentPositionStatusMessage.isEmpty {
        Section {
          StatusCard(
            title: L10n.text("home.section.status", table: .home),
            message: model.currentPositionStatusMessage,
            tone: model.currentPositionStatusIsWarning ? .warning : .success
          )
        }
      }
    }
    .listStyle(.insetGrouped)
    .navigationTitle(L10n.text("current.title", table: .home))
    .navigationBarTitleDisplayMode(.inline)
    .alert(L10n.text("current.save.dialog.title", table: .home), isPresented: $isSaveDialogPresented) {
      TextField(L10n.text("current.save.dialog.name", table: .home), text: $favoriteName)
      Button(L10n.text("common.cancel", table: .general), role: .cancel) {
        favoriteName = ""
      }
      Button(L10n.text("common.save", table: .general)) {
        let name = trimmedFavoriteName
        favoriteName = ""
        Task { await model.saveCurrentLocationAsFavorite(named: name) }
      }
      .disabled(trimmedFavoriteName.isEmpty)
    }
  }
}

#Preview {
  NavigationStack {
    CurrentPositionView(model: AppModel())
  }
}
