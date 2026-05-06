import SwiftUI

struct FavoritesView: View {
  @ObservedObject var model: AppModel

  var body: some View {
    List {
      if model.favorites.isEmpty {
        Section {
          Text(L10n.text("favorites.empty", table: .home))
            .foregroundStyle(.secondary)
        }
      } else {
        Section {
          ForEach(model.favorites) { place in
            Button {
              model.openPlaceDetails(place.id)
            } label: {
              VStack(alignment: .leading, spacing: 4) {
                Text(place.name)
                  .font(.headline)
                Text(place.address)
                  .font(.subheadline)
                  .foregroundStyle(.secondary)
              }
              .frame(maxWidth: .infinity, alignment: .leading)
            }
            .accessibilityLabel(favoriteAccessibilityLabel(for: place))
            .accessibilityHint(L10n.text("favorites.open_hint", table: .home))
            .accessibilityAction(named: Text(L10n.text("place.action.route", table: .home))) {
              Task {
                await model.prepareRoute(for: place.id)
                if model.selectedRouteSummary != nil {
                  model.openRouteSummary(place.id)
                }
              }
            }
            .accessibilityAction(named: Text(L10n.text("place.action.favorite.remove", table: .home))) {
              model.toggleFavorite(place)
            }
          }
          .onDelete { offsets in
            for index in offsets.sorted(by: >) {
              model.toggleFavorite(model.favorites[index])
            }
          }
        } footer: {
          Text(L10n.text("favorites.delete_hint", table: .home))
        }
      }
    }
    .listStyle(.insetGrouped)
    .navigationTitle(L10n.text("favorites.title", table: .home))
    .navigationBarTitleDisplayMode(.inline)
  }

  private func favoriteAccessibilityLabel(for place: Place) -> String {
    [place.name, place.address]
      .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }
      .joined(separator: ". ")
  }
}

#Preview {
  NavigationStack {
    FavoritesView(model: AppModel())
  }
}
