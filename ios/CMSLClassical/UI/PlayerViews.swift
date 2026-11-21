import SwiftUI

/// ミニプレイヤー（再生中のみ。タップでフルプレイヤー）。
struct MiniPlayerView: View {
    @Environment(AppModel.self) private var model
    @Binding var showPlayer: Bool

    var body: some View {
        let playback = model.playback
        if let id = playback.currentTrackId, let index = model.index, let track = index.track(id) {
            VStack(spacing: 0) {
                ProgressView(value: playback.duration > 0 ? min(playback.position / playback.duration, 1) : 0)
                    .progressViewStyle(.linear)
                    .accessibilityHidden(true)
                HStack(spacing: 12) {
                    Button {
                        showPlayer = true
                    } label: {
                        HStack(spacing: 12) {
                            ArtworkView(composerId: track.composerId, genreId: track.genreId, cornerRadius: 6)
                                .frame(width: 44, height: 44)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(track.workTitle(english: model.english, work: index.work(track.workId)))
                                    .font(.subheadline).lineLimit(1)
                                Text(subtitle(track, index)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                    .accessibilityHint(Text("action.openPlayer"))
                    .accessibilityIdentifier("miniPlayer")
                    PlayPauseButton(size: 22)
                    Button {
                        playback.skipNext()
                    } label: {
                        Image(systemName: "forward.fill").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text("action.next"))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
            }
            .background(.bar)
        }
    }

    private func subtitle(_ track: Track, _ index: CatalogIndex) -> String {
        let composer = index.composer(track.composerId)?.displayName(english: model.english) ?? ""
        let movement = track.movement == nil ? nil : track.shortTitle(english: model.english)
        return [composer, movement].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

struct PlayPauseButton: View {
    @Environment(AppModel.self) private var model
    var size: CGFloat = 28
    var identifier = "playPause"

    var body: some View {
        let playing = model.playback.isPlaying
        Button {
            model.playback.togglePlayPause()
        } label: {
            Image(systemName: playing ? "pause.fill" : "play.fill")
                .font(.system(size: size))
                .frame(width: max(44, size * 2), height: max(44, size * 2))
        }
        .accessibilityLabel(Text(playing ? "action.pause" : "action.play"))
        .accessibilityIdentifier(identifier)
    }
}

/// フルプレイヤー（SPEC §4.8）。
struct PlayerView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var showQueue = false
    @State private var showAbout = false
    @State private var scrubbing: Double?

    var body: some View {
        NavigationStack {
            Group {
                if let id = model.playback.currentTrackId, let index = model.index, let track = index.track(id) {
                    content(track, index)
                } else {
                    Text("player.empty").foregroundStyle(.secondary)
                }
            }
            .navigationTitle(Text("player.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image(systemName: "chevron.down") }
                        .accessibilityLabel(Text("action.closePlayer"))
                }
            }
            .sheet(isPresented: $showQueue) { QueueView() }
        }
    }

    private func content(_ track: Track, _ index: CatalogIndex) -> some View {
        let playback = model.playback
        return ScrollView {
            VStack(spacing: 20) {
                ArtworkView(composerId: track.composerId, genreId: track.genreId, cornerRadius: 16)
                    .aspectRatio(1, contentMode: .fit)
                    .frame(maxWidth: 360)
                    .shadow(radius: 8, y: 4)
                VStack(spacing: 4) {
                    Text(track.workTitle(english: model.english, work: index.work(track.workId)))
                        .font(.title3.bold())
                    if track.movement != nil {
                        Text(track.shortTitle(english: model.english)).font(.body)
                    }
                    Text([
                        index.composer(track.composerId)?.displayName(english: model.english),
                        index.genre(track.genreId)?.displayName(english: model.english),
                    ].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
                seekBar
                HStack(spacing: 32) {
                    Button { playback.skipPrevious() } label: {
                        Image(systemName: "backward.fill").font(.title).frame(width: 56, height: 56)
                    }
                    .accessibilityLabel(Text("action.previous"))
                    PlayPauseButton(size: 40, identifier: "playerPlayPause")
                    Button { playback.skipNext() } label: {
                        Image(systemName: "forward.fill").font(.title).frame(width: 56, height: 56)
                    }
                    .accessibilityLabel(Text("action.next"))
                }
                secondaryControls(track)
                sleepTimerRow
            }
            .padding()
        }
        .sheet(isPresented: $showAbout) { AboutTrackView(track: track) }
    }

    private var seekBar: some View {
        let playback = model.playback
        let duration = max(playback.duration, 0)
        let shown = scrubbing ?? min(playback.position, duration > 0 ? duration : playback.position)
        return VStack(spacing: 4) {
            Slider(
                value: Binding(get: { shown }, set: { scrubbing = $0 }),
                in: 0...max(duration, 1)
            ) { editing in
                if !editing, let value = scrubbing {
                    playback.seek(to: value)
                    scrubbing = nil
                }
            }
            .disabled(duration <= 0)
            .accessibilityLabel(Text("player.seek"))
            .accessibilityValue(Text("player.seekState \(formatTime(shown)) \(formatTime(duration))"))
            HStack {
                Text(formatTime(shown))
                Spacer()
                Text("-" + formatTime(max(duration - shown, 0)))
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)
            .accessibilityHidden(true)
        }
    }

    private func secondaryControls(_ track: Track) -> some View {
        let playback = model.playback
        let repeatMode = playback.queue.repeatMode
        return HStack(spacing: 8) {
            Button { playback.setShuffle(!playback.queue.shuffle) } label: {
                Image(systemName: "shuffle")
                    .foregroundStyle(playback.queue.shuffle ? Color.accentColor : .secondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("action.shuffle"))
            .accessibilityValue(Text(playback.queue.shuffle ? "toggle.on" : "toggle.off"))
            Button { playback.cycleRepeatMode() } label: {
                Image(systemName: repeatMode == .one ? "repeat.1" : "repeat")
                    .foregroundStyle(repeatMode == .off ? .secondary : Color.accentColor)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("action.repeat"))
            .accessibilityValue(Text(repeatLabel(repeatMode)))
            FavoriteButton(trackId: track.id)
            Button { showQueue = true } label: {
                Image(systemName: "list.bullet").frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("action.queue"))
            if let url = URL(string: track.articleUrl) {
                ShareLink(item: url) {
                    Image(systemName: "square.and.arrow.up").frame(width: 44, height: 44)
                }
                .accessibilityLabel(Text("action.share"))
            }
            Button { showAbout = true } label: {
                Image(systemName: "info.circle").frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("action.aboutTrack"))
        }
    }

    private var sleepTimerRow: some View {
        SleepTimerButton()
        .foregroundStyle(.secondary)
    }

    private func repeatLabel(_ mode: RepeatMode) -> LocalizedStringKey {
        switch mode {
        case .off: "repeat.off"
        case .all: "repeat.all"
        case .one: "repeat.one"
        }
    }
}

/// キュー（並べ替えと削除ができる。SPEC §5）。
struct QueueView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let playback = model.playback
        NavigationStack {
            List {
                if playback.queue.isEmpty {
                    Text("player.queueEmpty").foregroundStyle(.secondary)
                }
                ForEach(Array(playback.queue.items.enumerated()), id: \.element.key) { offset, item in
                    let track = model.index?.track(item.trackId)
                    let current = item.key == playback.queue.currentKey
                    Button {
                        playback.skipToQueueItem(at: offset)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(track?.displayTitle(english: model.english) ?? item.trackId)
                                    .foregroundStyle(current ? Color.accentColor : .primary)
                                if let track, let composer = model.index?.composer(track.composerId) {
                                    Text(composer.displayName(english: model.english)).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            if current {
                                Image(systemName: "speaker.wave.2.fill").foregroundStyle(Color.accentColor)
                                    .accessibilityLabel(Text("a11y.nowPlaying"))
                            }
                        }
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
                .onMove { from, to in
                    guard let source = from.first else { return }
                    playback.moveQueueItem(from: source, to: to > source ? to - 1 : to)
                }
                .onDelete { offsets in
                    for offset in offsets.sorted(by: >) { playback.removeQueueItem(at: offset) }
                }
            }
            .navigationTitle(Text("action.queue"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("action.close") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { EditButton() }
            }
        }
    }
}

/// スリープタイマー（SPEC §5.2）。設定中は残り時間を出す。
struct SleepTimerButton: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let timer = model.sleepTimer
        Menu {
            // set: にメソッド参照（apply）を直接渡すと Xcode 26.6（Swift 6.3.3）のコンパイラが IRGen で落ちるため、クロージャで包む。
            Picker("sleep.title", selection: Binding(get: { selection }, set: { apply($0) })) {
                Text("sleep.off").tag(0)
                ForEach(SleepTimer.choices, id: \.self) { minutes in
                    Text("sleep.minutes \(minutes)").tag(minutes)
                }
                Text("sleep.endOfTrack").tag(-1)
            }
        } label: {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                HStack(spacing: 6) {
                    Image(systemName: timer.mode == .off ? "moon" : "moon.fill")
                    Text(label(at: context.date)).font(.footnote.monospacedDigit())
                }
                .foregroundStyle(timer.mode == .off ? Color.secondary : Color.accentColor)
                .frame(minHeight: 44)
                .padding(.horizontal, 12)
            }
        }
        .accessibilityLabel(Text("sleep.title"))
        .accessibilityIdentifier("sleepTimer")
    }

    /// メニューで選んでいる項目（0: オフ、-1: この曲の終わりまで）。時間はメニューの選択肢と同じとは限らないので、時間で止めるときは選択なし。
    private var selection: Int {
        switch model.sleepTimer.mode {
        case .off: 0
        case .endOfTrack: -1
        case .until: -2
        }
    }

    private func apply(_ value: Int) {
        let timer = model.sleepTimer
        switch value {
        case 0: timer.cancel()
        case -1: timer.stopAtEndOfTrack()
        default: timer.start(minutes: value)
        }
    }

    private func label(at date: Date) -> String {
        switch model.sleepTimer.mode {
        case .off: String(localized: "sleep.title")
        case .endOfTrack: String(localized: "sleep.atEndOfTrack")
        case .until: String(localized: "sleep.remaining \(formatTime(model.sleepTimer.remaining(at: date) ?? 0))")
        }
    }
}
