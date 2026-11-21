import SwiftUI

/// ホーム（SPEC §4.2）: おまかせ再生、新着、ジャンルのグリッド。
struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Group {
            if let index = model.index {
                content(index)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(Text("tab.home"))
    }

    private func content(_ index: CatalogIndex) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Button {
                    Task { await model.playback.play(QueueBuilder.shuffled(index.catalog.tracks)) }
                } label: {
                    Label("action.shuffleAll", systemImage: "shuffle")
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("shuffleAll")

                VStack(alignment: .leading, spacing: 12) {
                    Text("home.newest").font(.title3.bold()).accessibilityAddTraits(.isHeader)
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(alignment: .top, spacing: 12) {
                            ForEach(index.newest(limit: 10)) { track in
                                NewestCard(track: track, index: index)
                            }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("home.genres").font(.title3.bold()).accessibilityAddTraits(.isHeader)
                    // 文字が大きい設定（アクセシビリティサイズ）では 1 列にする
                    let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: typeSize.isAccessibilitySize ? 1 : 2)
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(index.genres) { genre in
                            NavigationLink(value: Route.genre(genre.id)) {
                                GenreCard(genre: genre)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("genre-\(genre.id)")
                        }
                    }
                }
            }
            .padding()
        }
    }
}

private struct NewestCard: View {
    @Environment(AppModel.self) private var model
    /// 文字サイズに合わせて広げる（Dynamic Type）
    @ScaledMetric(relativeTo: .subheadline) private var width: CGFloat = 140
    let track: Track
    let index: CatalogIndex

    var body: some View {
        let composer = index.composer(track.composerId)?.displayName(english: model.english) ?? ""
        let movement = track.movement.map { model.english ? track.shortTitle(english: true) : $0 }
        Button {
            Task { await model.playback.play(QueueBuilder.fromWork(index.tracks(ofWork: track.workId), startTrackId: track.id)) }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                ArtworkView(composerId: track.composerId, genreId: track.genreId)
                    .frame(width: cardWidth, height: cardWidth)
                Text(track.workTitle(english: model.english, work: index.work(track.workId)))
                    .font(.subheadline)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                Text([composer, movement].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
            .frame(width: cardWidth, alignment: .leading)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(Text("action.playThis"))
    }

    private var cardWidth: CGFloat { min(width, 280) }
}

private struct GenreCard: View {
    @Environment(AppModel.self) private var model
    let genre: Genre

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(genre.displayName(english: model.english))
                .font(.headline)
                .foregroundStyle(.white)
                .multilineTextAlignment(.leading)
            Text("count.tracks \(genre.trackCount)")
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, minHeight: 88, alignment: .bottomLeading)
        .padding(12)
        .background {
            ArtworkView(composerId: genre.id, genreId: genre.id, cornerRadius: 12)
                .overlay {
                    // 模様の上でも文字が読めるよう、下側を暗くする（WCAG AA のコントラスト）
                    LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .top, endPoint: .bottom)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
        }
        .accessibilityElement(children: .combine)
    }
}

/// ジャンル詳細（SPEC §4.3）: 作曲家の一覧（曲数順・名前順）とシャッフル再生。
struct GenreView: View {
    @Environment(AppModel.self) private var model
    let genreId: String
    @State private var byName = false

    var body: some View {
        if let index = model.index, let genre = index.genre(genreId) {
            let composers = index.composers(ofGenre: genreId).sorted { a, b in
                if byName { return a.composer.displayName(english: model.english) < b.composer.displayName(english: model.english) }
                return a.trackCount != b.trackCount ? a.trackCount > b.trackCount : a.composer.name < b.composer.name
            }
            List(composers, id: \.composer.id) { entry in
                NavigationLink(value: Route.composer(entry.composer.id, genreId: genreId)) {
                    CountLabel(title: entry.composer.displayName(english: model.english), count: entry.trackCount)
                }
                .accessibilityIdentifier("composer-\(entry.composer.id)")
            }
            .navigationTitle(genre.displayName(english: model.english))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Picker("action.sort", selection: $byName) {
                            Text("sort.byCount").tag(false)
                            Text("sort.byName").tag(true)
                        }
                    } label: {
                        Label("action.sort", systemImage: "arrow.up.arrow.down")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        Task { await model.playback.play(QueueBuilder.shuffled(index.tracks(ofGenre: genreId))) }
                    } label: {
                        Label("action.shuffleGenre", systemImage: "shuffle")
                    }
                }
            }
        }
    }
}

/// 作曲家詳細（SPEC §4.4）: 作品の一覧。曲が 1 つだけの作品は曲の行として直接出す。
struct ComposerView: View {
    @Environment(AppModel.self) private var model
    let composerId: String
    let genreId: String?

    var body: some View {
        if let index = model.index, let composer = index.composer(composerId) {
            let works = index.works(of: composerId, genreId: genreId)
            let all = index.tracks(of: composerId, genreId: genreId)
            List {
                Section {
                    Button {
                        Task { await model.playback.play(QueueBuilder.fromList(all)) }
                    } label: {
                        Label("action.playAll", systemImage: "play.fill").frame(minHeight: 44)
                    }
                    .accessibilityIdentifier("playAll")
                }
                Section {
                    ForEach(works) { work in
                        let tracks = index.tracks(ofWork: work.id)
                        if tracks.count == 1, let track = tracks.first {
                            TrackRow(track: track, title: track.workTitle(english: model.english, work: work)) {
                                let start = all.firstIndex { $0.id == track.id } ?? 0
                                Task { await model.playback.play(QueueBuilder.fromList(all, startIndex: start)) }
                            }
                        } else {
                            NavigationLink(value: Route.work(work.id)) {
                                CountLabel(title: work.title, count: tracks.count, movements: true)
                            }
                            .accessibilityIdentifier("work-\(work.id)")
                        }
                    }
                }
            }
            .navigationTitle(composer.displayName(english: model.english))
        }
    }
}

/// 作品詳細（SPEC §4.5）: 楽章の一覧。1 行をタップすると、その曲から作品の最後までを再生する。
struct WorkView: View {
    @Environment(AppModel.self) private var model
    let workId: String

    var body: some View {
        if let index = model.index, let work = index.work(workId) {
            let tracks = index.tracks(ofWork: workId)
            List {
                Section {
                    if let composer = index.composer(work.composerId) {
                        Text(composer.displayName(english: model.english)).foregroundStyle(.secondary)
                    }
                    Button {
                        Task { await model.playback.play(QueueBuilder.fromWork(tracks)) }
                    } label: {
                        Label("action.playWork", systemImage: "play.fill").frame(minHeight: 44)
                    }
                    .accessibilityIdentifier("playWork")
                }
                Section {
                    ForEach(tracks) { track in
                        TrackRow(track: track, title: track.shortTitle(english: model.english)) {
                            Task { await model.playback.play(QueueBuilder.fromWork(tracks, startTrackId: track.id)) }
                        }
                    }
                }
            }
            .navigationTitle(work.title)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
