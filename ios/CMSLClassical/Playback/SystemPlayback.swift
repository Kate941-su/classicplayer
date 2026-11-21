import AVFoundation
import MediaPlayer
import UIKit

/// OS との接点（SPEC §5）: オーディオセッション、ロック画面とコントロールセンターの表示（Now Playing）と操作
/// （Remote Command）、割り込み（電話など）、経路の変更（イヤホンを外したとき）。
@MainActor
final class SystemPlayback {
    private let controller: PlaybackController
    private var observers: [any NSObjectProtocol] = []
    /// 割り込みの前に再生していたか（終わったときに再開するため）。
    private var wasPlayingBeforeInterruption = false

    init(controller: PlaybackController) {
        self.controller = controller
    }

    func start() {
        configureSession()
        registerRemoteCommands()
        observeSession()
        controller.onChange = { [weak self] in self?.updateNowPlaying() }
        updateNowPlaying()
    }

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, policy: .longFormAudio)
    }

    private func registerRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            self?.activateAndResume()
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            self?.controller.pause()
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            if controller.isPlaying { controller.pause() } else { activateAndResume() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.controller.skipNext()
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.controller.skipPrevious()
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self, let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            controller.seek(to: event.positionTime)
            return .success
        }
        // 早送り・巻き戻し・評価などは使わない
        center.skipForwardCommand.isEnabled = false
        center.skipBackwardCommand.isEnabled = false
    }

    private func activateAndResume() { controller.resume() }

    private func observeSession() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            let type = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt)
                .flatMap(AVAudioSession.InterruptionType.init(rawValue:))
            let options = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt)
                .map(AVAudioSession.InterruptionOptions.init(rawValue:)) ?? []
            MainActor.assumeIsolated { self?.handleInterruption(type, options: options) }
        })
        observers.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main
        ) { [weak self] note in
            let reason = (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt)
                .flatMap(AVAudioSession.RouteChangeReason.init(rawValue:))
            MainActor.assumeIsolated { self?.handleRouteChange(reason) }
        })
        observers.append(center.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.configureSession() }
        })
    }

    /// 電話やほかのアプリの再生で一時停止し、終わったら（OS が再開を勧めるときだけ）再開する。
    func handleInterruption(_ type: AVAudioSession.InterruptionType?, options: AVAudioSession.InterruptionOptions) {
        switch type {
        case .began:
            wasPlayingBeforeInterruption = controller.isPlaying
            controller.pause()
        case .ended:
            if wasPlayingBeforeInterruption, options.contains(.shouldResume) { activateAndResume() }
            wasPlayingBeforeInterruption = false
        default:
            break
        }
    }

    /// イヤホンを外したとき（今の出力先がなくなったとき）は一時停止する。
    func handleRouteChange(_ reason: AVAudioSession.RouteChangeReason?) {
        if reason == .oldDeviceUnavailable { controller.pause() }
    }

    private func updateNowPlaying() {
        let center = MPNowPlayingInfoCenter.default()
        guard let track = controller.currentTrack else {
            center.nowPlayingInfo = nil
            center.playbackState = .stopped
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.composer,
            MPMediaItemPropertyAlbumTitle: track.workTitle,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: controller.position,
            MPNowPlayingInfoPropertyPlaybackRate: controller.isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
        ]
        if controller.duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = controller.duration }
        if let png = track.artworkPNG, let image = UIImage(data: png) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }
        center.nowPlayingInfo = info
        center.playbackState = controller.isPlaying ? .playing : .paused
    }
}
