import SwiftUI

struct SearchView: View {
  @ObservedObject var model: AppModel
  @Environment(\.dismiss) private var dismiss
  @FocusState private var queryFocused: Bool

  var body: some View {
    List {
      Section {
        TextField(L10n.text("search.query.placeholder", table: .home), text: $model.searchQuery)
          .textInputAutocapitalization(.words)
          .disableAutocorrection(true)
          .focused($queryFocused)
          .submitLabel(.search)
          .onSubmit {
            Task { await model.performSearch() }
          }

        PrimaryActionButton(
          title: L10n.text("search.action.search", table: .home),
          systemImage: "magnifyingglass"
        ) {
          Task { await model.performSearch() }
        }
      }

      if model.isSearching {
        Section {
          HStack(spacing: 12) {
            ProgressView()
            Text(L10n.text("search.status.loading", table: .home))
          }
        }
      }

      if !model.isSearching && (model.hasPerformedSearch || !model.searchResults.isEmpty) {
        Section {
          if model.searchResults.isEmpty {
            Text(model.statusMessage)
              .foregroundStyle(.secondary)
          } else {
            ForEach(model.searchResults) { place in
              Button {
                model.openPlaceDetails(place.id)
              } label: {
                VStack(alignment: .leading, spacing: 4) {
                  Text(place.name)
                    .font(.headline)
                  Text(place.address)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                  Text(
                    L10n.text(
                      "search.result.meta",
                      table: .home,
                      AppFormatters.distance(place.walkDistanceMeters),
                      AppFormatters.eta(minutes: place.walkEtaMinutes)
                    )
                  )
                  .font(.footnote)
                  .foregroundStyle(.tertiary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
              }
              .accessibilityLabel(
                L10n.text(
                  "search.result.accessibility",
                  table: .home,
                  place.name,
                  place.address,
                  AppFormatters.distance(place.walkDistanceMeters),
                  AppFormatters.eta(minutes: place.walkEtaMinutes)
                )
              )
              .accessibilityAction(named: Text(L10n.text("place.action.route", table: .home))) {
                Task {
                  await model.prepareRoute(for: place.id)
                  if model.selectedRouteSummary != nil {
                    model.openRouteSummary(place.id)
                  }
                }
              }
              .accessibilityAction(
                named: Text(
                  model.isFavorite(place)
                    ? L10n.text("place.action.favorite.remove", table: .home)
                    : L10n.text("place.action.favorite.add", table: .home)
                )
              ) {
                model.toggleFavorite(place)
              }
            }
          }
        } header: {
          Text(L10n.text("search.section.results", table: .home))
        }
      }
    }
    .listStyle(.insetGrouped)
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar {
      ToolbarItem(placement: .navigationBarLeading) {
        Button {
          dismiss()
        } label: {
          Label(L10n.text("common.back"), systemImage: "chevron.backward")
            .labelStyle(.iconOnly)
        }
        .accessibilityLabel(L10n.text("common.back"))
      }
    }
    .task {
      queryFocused = true
    }
  }
}

#Preview {
  NavigationStack {
    SearchView(model: AppModel())
  }
}
