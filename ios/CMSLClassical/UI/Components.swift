import SwiftUI
import UIKit

/// 画面の行き先（NavigationStack）。
enum Route: Hashable {
    case genre(String)
    case composer(String, genreId: String?)
    case work(String)
    case licenses
    case imageCredits
}

struct RouteView: View {
    let route: Route

    var body: some View {
        switch route {
        case let .genre(id): GenreView(genreId: id)
        case let .composer(id, genreId): ComposerView(composerId: id, genreId: genreId)
        case let .work(id): WorkView(workId: id)
        case .licenses: LicensesView()
        case .imageCredits: ImageCreditsView()
        }
    }
}

/// 曲・作曲家のアートワーク（SPEC §4.10）: 作曲家の肖像画があればそれ、なければ（取得中・失敗も）生成アートワーク。
/// `usePortrait: false` は生成アートワークだけ（ジャンルのカードなど、作曲家の絵でないところ）。装飾なので読み上げない。
struct ArtworkView: View {
    @Environment(\.portraits) private var portraits
    let composerId: String
    let genreId: String
    var cornerRadius: CGFloat = 8
    var usePortrait = true

    @State private var loaded: (composerId: String, image: UIImage)?

    var body: some View {
        let image = usePortrait
            ? (loaded?.composerId == composerId ? loaded?.image : nil) ?? portraits?.cachedImage(composerId)
            : nil
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                ArtworkCanvas(spec: ArtworkSpec(composerId: composerId, genreId: genreId))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .accessibilityHidden(true)
        .task(id: composerId) {
            guard usePortrait, let portraits, let image = await portraits.image(composerId) else { return }
            loaded = (composerId, image)
        }
    }
}

/// 曲の行: タップで再生、☆でお気に入り、「…」でメニュー（SPEC §4.5）。
struct TrackRow: View {
    @Environment(AppModel.self) private var model
    let track: Track
    let title: String
    var subtitle: String?
    var showArtwork = false
    let onPlay: () -> Void

    @State private var showAbout = false

    var body: some View {
        let isCurrent = model.playback.currentTrackId == track.id
        HStack(spacing: 12) {
            Button(action: onPlay) {
                HStack(spacing: 12) {
                    if showArtwork {
                        ArtworkView(composerId: track.composerId, genreId: track.genreId, cornerRadius: 6)
                            .frame(width: 44, height: 44)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.body)
                            .foregroundStyle(isCurrent ? Color.accentColor : .primary)
                        if let subtitle, !subtitle.isEmpty {
                            Text(subtitle).font(.footnote).foregroundStyle(.secondary)
                        }
                        if !track.tags.isEmpty {
                            Text(track.tags.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 0)
                    DownloadBadge(trackId: track.id)
                    if isCurrent {
                        Image(systemName: "speaker.wave.2.fill")
                            .foregroundStyle(Color.accentColor)
                            .accessibilityLabel(Text("a11y.nowPlaying"))
                    }
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text("action.playThis"))
            FavoriteButton(trackId: track.id)
            TrackMenu(track: track, showAbout: $showAbout)
        }
        .sheet(isPresented: $showAbout) { AboutTrackView(track: track) }
    }
}

struct FavoriteButton: View {
    @Environment(AppModel.self) private var model
    let trackId: String

    var body: some View {
        let on = model.library.isFavorite(trackId)
        Button {
            model.library.toggleFavorite(trackId)
        } label: {
            Image(systemName: on ? "star.fill" : "star")
                .foregroundStyle(on ? Color.accentColor : .secondary)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(on ? "action.unfavorite" : "action.favorite"))
    }
}

struct TrackMenu: View {
    @Environment(AppModel.self) private var model
    let track: Track
    @Binding var showAbout: Bool

    var body: some View {
        Menu {
            Button {
                model.library.toggleFavorite(track.id)
            } label: {
                model.library.isFavorite(track.id)
                    ? Label("action.unfavorite", systemImage: "star.slash")
                    : Label("action.favorite", systemImage: "star")
            }
            Button {
                Task { await model.playback.playNext(track.id) }
            } label: { Label("action.playNext", systemImage: "text.insert") }
            Button {
                Task { await model.playback.addToQueue(track.id) }
            } label: { Label("action.addToQueue", systemImage: "text.append") }
            DownloadMenuItem(track: track)
            Button {
                showAbout = true
            } label: { Label("action.aboutTrack", systemImage: "info.circle") }
        } label: {
            Image(systemName: "ellipsis")
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .foregroundStyle(.secondary)
        .accessibilityLabel(Text("action.more"))
    }
}

/// ダウンロード済み・取得中の印（SPEC §5.1）。
struct DownloadBadge: View {
    @Environment(AppModel.self) private var model
    let trackId: String

    var body: some View {
        let downloads = model.downloads
        if downloads.isDownloaded(trackId) {
            Image(systemName: "arrow.down.circle.fill")
                .foregroundStyle(.secondary)
                .accessibilityLabel(Text("a11y.downloaded"))
        } else if downloads.active == trackId {
            ProgressView(value: downloads.progress)
                .progressViewStyle(.circular)
                .controlSize(.small)
                .accessibilityLabel(Text("a11y.downloading"))
        } else if downloads.isPending(trackId) {
            Image(systemName: "clock")
                .foregroundStyle(.secondary)
                .accessibilityLabel(Text("a11y.downloading"))
        }
    }
}

/// 曲の「…」メニューのダウンロードの項目。
struct DownloadMenuItem: View {
    @Environment(AppModel.self) private var model
    let track: Track

    var body: some View {
        let downloads = model.downloads
        if downloads.isDownloaded(track.id) {
            Button(role: .destructive) {
                downloads.remove(track.id)
            } label: { Label("action.removeDownload", systemImage: "trash") }
        } else if downloads.isPending(track.id) {
            Button {
                downloads.remove(track.id)
            } label: { Label("action.cancelDownload", systemImage: "xmark.circle") }
        } else {
            Button {
                downloads.enqueue([track])
            } label: { Label("action.download", systemImage: "arrow.down.circle") }
        }
    }
}

/// 「この曲について」: 原典記事へのリンクとクレジット（SPEC §1.2-5・§4.5）。
struct AboutTrackView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let track: Track

    var body: some View {
        NavigationStack {
            List {
                if let index = model.index {
                    Section {
                        Text(track.displayTitle(english: model.english)).font(.headline)
                        if let composer = index.composer(track.composerId) {
                            Text(composer.displayName(english: model.english))
                        }
                        if let work = index.work(track.workId) { Text(work.title).foregroundStyle(.secondary) }
                        if !track.tags.isEmpty { Text(track.tags.joined(separator: " · ")).foregroundStyle(.secondary) }
                    }
                    Section {
                        if let url = URL(string: track.articleUrl) {
                            Link(destination: url) { Label("action.openArticle", systemImage: "safari") }
                        }
                    }
                    CreditSection(license: index.catalog.license)
                    // 肖像画を出している曲は、そのクレジットと「画像のクレジット」画面への導線（SPEC §4.8）
                    if let portrait = index.composer(track.composerId)?.portrait {
                        Section("info.imageCredits") {
                            PortraitCreditLink(portrait: portrait)
                            NavigationLink { ImageCreditsView() } label: { Text("info.imageCredits") }
                        }
                    }
                }
            }
            .navigationTitle(Text("action.aboutTrack"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("action.close") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

struct CreditSection: View {
    let license: License

    var body: some View {
        Section("info.credit") {
            Text("credit.source \(license.credit)")
            Text("credit.license \(license.name)")
            if let url = URL(string: license.url) {
                Link(destination: url) { Label("info.cmslSite", systemImage: "arrow.up.right.square") }
            }
            Text("credit.note").font(.footnote).foregroundStyle(.secondary)
        }
    }
}

/// 行の見出しと件数。
struct CountLabel: View {
    let title: String
    let count: Int
    var movements = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(movements ? "count.movements \(count)" : "count.tracks \(count)")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

extension View {
    /// ミニプレイヤーをタブバーの上に出す（SPEC §4.1）。
    func miniPlayerInset(showPlayer: Binding<Bool>) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerView(showPlayer: showPlayer) }
    }
}

func formatTime(_ seconds: TimeInterval) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "0:00" }
    let total = Int(seconds.rounded(.down))
    return total >= 3600
        ? String(format: "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
        : String(format: "%d:%02d", total / 60, total % 60)
}
