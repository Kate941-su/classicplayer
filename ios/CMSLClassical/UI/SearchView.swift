import SwiftUI

/// 検索（SPEC §4.6）: 端末内のカタログを 150ms のデバウンスで検索し、作曲家・作品・曲に分けて表示する。
/// 未入力のときは最近の検索とジャンルのチップを出す。
struct SearchView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var results = SearchResults.empty

    var body: some View {
        List {
            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                idleContent
            } else if results.isEmpty {
                Text("search.noResults").foregroundStyle(.secondary)
            } else {
                resultSections
            }
        }
        .navigationTitle(Text("tab.search"))
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text("search.hint"))
        .onSubmit(of: .search) { model.recentSearches.add(query) }
        .task(id: SearchKey(query: query, ready: model.searchIndex != nil)) {
            try? await Task.sleep(for: .milliseconds(150))
            guard !Task.isCancelled, let index = model.searchIndex else { return }
            results = index.search(query)
        }
    }

    private struct SearchKey: Equatable {
        let query: String
        let ready: Bool
    }

    @ViewBuilder private var idleContent: some View {
        if !model.recentSearches.recent.isEmpty {
            Section {
                ForEach(model.recentSearches.recent, id: \.self) { recent in
                    HStack {
                        Button(recent) { query = recent }
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        Button {
                            model.recentSearches.remove(recent)
                        } label: {
                            Image(systemName: "xmark").frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(Text("action.removeRecent \(recent)"))
                    }
                }
            } header: {
                HStack {
                    Text("search.recent")
                    Spacer()
                    Button("action.clear") { model.recentSearches.clear() }.font(.footnote)
                }
            }
        }
        if let index = model.index {
            Section("home.genres") {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], alignment: .leading, spacing: 8) {
                    ForEach(index.genres) { genre in
                        NavigationLink(value: Route.genre(genre.id)) {
                            Text(genre.displayName(english: model.english))
                                .font(.subheadline)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(Color.accentColor.opacity(0.12), in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    @ViewBuilder private var resultSections: some View {
        if let index = model.index {
            if !results.composers.isEmpty {
                Section("search.section.composers") {
                    ForEach(results.composers) { composer in
                        NavigationLink(value: Route.composer(composer.id, genreId: nil)) {
                            CountLabel(title: composer.displayName(english: model.english), count: composer.trackCount)
                        }
                        .simultaneousGesture(TapGesture().onEnded { model.recentSearches.add(query) })
                    }
                }
            }
            if !results.works.isEmpty {
                Section("search.section.works") {
                    ForEach(results.works) { work in
                        NavigationLink(value: Route.work(work.id)) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(work.title)
                                if let composer = index.composer(work.composerId) {
                                    Text(composer.displayName(english: model.english)).font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .simultaneousGesture(TapGesture().onEnded { model.recentSearches.add(query) })
                    }
                }
            }
            if !results.tracks.isEmpty {
                Section("search.section.tracks") {
                    ForEach(results.tracks) { track in
                        TrackRow(
                            track: track,
                            title: track.displayTitle(english: model.english),
                            subtitle: index.composer(track.composerId)?.displayName(english: model.english)
                        ) {
                            model.recentSearches.add(query)
                            Task {
                                await model.playback.play(QueueBuilder.fromWork(index.tracks(ofWork: track.workId), startTrackId: track.id))
                            }
                        }
                    }
                }
            }
        }
    }
}
