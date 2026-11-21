import SwiftUI

/// ライブラリ（SPEC §4.7）: お気に入り（追加日順・作曲家順）と最近再生した曲。
/// カタログから消えた曲は「この曲は現在利用できません」としてグレー表示する（SPEC §6）。
struct LibraryView: View {
    @Environment(AppModel.self) private var model
    @State private var tab = 0
    @State private var byComposer = false

    var body: some View {
        List {
            Picker("tab.library", selection: $tab) {
                Text("library.favorites").tag(0)
                Text("library.history").tag(1)
                Text("library.downloads").tag(2)
            }
            .pickerStyle(.segmented)
            .listRowSeparator(.hidden)
            if let index = model.index {
                switch tab {
                case 0: favorites(index)
                case 1: history(index)
                default: DownloadsSection(index: index)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(Text("tab.library"))
        .toolbar {
            if tab == 0 {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Picker("action.sort", selection: $byComposer) {
                            Text("sort.byAdded").tag(false)
                            Text("sort.byComposer").tag(true)
                        }
                    } label: {
                        Label("action.sort", systemImage: "arrow.up.arrow.down")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func favorites(_ index: CatalogIndex) -> some View {
        let entries = sortedFavorites(index)
        if entries.isEmpty {
            Text("library.favoritesEmpty").foregroundStyle(.secondary)
        }
        let playable = entries.compactMap(\.track)
        ForEach(entries, id: \.trackId) { entry in
            if let track = entry.track {
                TrackRow(
                    track: track, title: track.displayTitle(english: model.english),
                    subtitle: index.composer(track.composerId)?.displayName(english: model.english), showArtwork: true
                ) {
                    let start = playable.firstIndex { $0.id == track.id } ?? 0
                    Task { await model.playback.play(QueueBuilder.fromList(playable, startIndex: start)) }
                }
            } else {
                HStack {
                    VStack(alignment: .leading) {
                        Text("track.unavailable")
                        Text(entry.trackId).font(.caption)
                    }
                    .foregroundStyle(.secondary)
                    Spacer()
                    FavoriteButton(trackId: entry.trackId)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .onDelete { offsets in
            for offset in offsets { model.library.removeFavorite(entries[offset].trackId) }
        }
    }

    private struct FavoriteEntry {
        let trackId: String
        let track: Track?
    }

    private func sortedFavorites(_ index: CatalogIndex) -> [FavoriteEntry] {
        let entries = model.library.favorites.map { FavoriteEntry(trackId: $0.trackId, track: index.track($0.trackId)) }
        guard byComposer else { return entries }
        func key(_ e: FavoriteEntry) -> (String, String)? {
            e.track.map { (index.composer($0.composerId)?.name ?? "", index.work($0.workId)?.title ?? "") }
        }
        return entries.sorted { a, b in
            switch (key(a), key(b)) {
            case let (x?, y?): x < y
            case (.some, nil): true
            default: false
            }
        }
    }

    @ViewBuilder
    private func history(_ index: CatalogIndex) -> some View {
        let tracks = model.library.history.compactMap { index.track($0.trackId) }
        if tracks.isEmpty {
            Text("library.historyEmpty").foregroundStyle(.secondary)
        }
        ForEach(Array(tracks.enumerated()), id: \.element.id) { offset, track in
            TrackRow(
                track: track, title: track.displayTitle(english: model.english),
                subtitle: index.composer(track.composerId)?.displayName(english: model.english), showArtwork: true
            ) {
                Task { await model.playback.play(QueueBuilder.fromList(tracks, startIndex: offset)) }
            }
        }
    }
}
