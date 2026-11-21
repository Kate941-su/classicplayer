import SwiftUI

/// ライブラリの「ダウンロード」（SPEC §4.7・§5.1）: 取得中の進み具合、設定、一覧（作曲家順）、合計サイズ、削除。
struct DownloadsSection: View {
    @Environment(AppModel.self) private var model
    let index: CatalogIndex
    @State private var confirmRemoveAll = false

    var body: some View {
        let downloads = model.downloads
        @Bindable var bindable = downloads
        Toggle("downloads.wifiOnly", isOn: $bindable.wifiOnly)
        if let active = downloads.active {
            VStack(alignment: .leading, spacing: 6) {
                Text("downloads.inProgress").font(.footnote).foregroundStyle(.secondary)
                Text(index.track(active)?.displayTitle(english: model.english) ?? active).lineLimit(2)
                ProgressView(value: downloads.progress)
                if !downloads.queued.isEmpty {
                    Text("downloads.waiting \(downloads.queued.count)").font(.footnote).foregroundStyle(.secondary)
                }
            }
            .accessibilityElement(children: .combine)
        }
        if downloads.lastFailure != nil {
            Text("downloads.failed").font(.footnote).foregroundStyle(.red)
        }
        let tracks = downloadedTracks
        if tracks.isEmpty {
            Text("library.downloadsEmpty").foregroundStyle(.secondary)
        } else {
            HStack {
                Text("downloads.total \(ByteCountFormatter.string(fromByteCount: downloads.totalBytes, countStyle: .file))")
                    .foregroundStyle(.secondary)
                Spacer()
                Button("action.removeAllDownloads", role: .destructive) { confirmRemoveAll = true }
                    .buttonStyle(.borderless)
            }
            .confirmationDialog("downloads.removeAllConfirm", isPresented: $confirmRemoveAll, titleVisibility: .visible) {
                Button("action.removeAllDownloads", role: .destructive) { downloads.removeAll() }
            }
            ForEach(Array(tracks.enumerated()), id: \.element.id) { offset, track in
                TrackRow(
                    track: track, title: track.displayTitle(english: model.english),
                    subtitle: index.composer(track.composerId)?.displayName(english: model.english), showArtwork: true
                ) {
                    Task { await model.playback.play(QueueBuilder.fromList(tracks, startIndex: offset)) }
                }
            }
            .onDelete { offsets in
                for offset in offsets { downloads.remove(tracks[offset].id) }
            }
        }
    }

    /// 作曲家順 → 作品順 → 楽章順。カタログにない曲は出さない（カタログの更新で消える）。
    private var downloadedTracks: [Track] {
        model.downloads.downloaded.keys.compactMap { index.track($0) }.sorted { a, b in
            let ca = index.composer(a.composerId)?.name ?? "", cb = index.composer(b.composerId)?.name ?? ""
            if ca != cb { return ca < cb }
            let wa = index.work(a.workId)?.title ?? "", wb = index.work(b.workId)?.title ?? ""
            if wa != wb { return wa < wb }
            return CatalogIndex.trackInWorkOrder(a, b)
        }
    }
}
