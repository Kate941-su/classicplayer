import SwiftUI
import UIKit

/// 作曲家の肖像画（SPEC §4.10）。今のカタログの `portrait` を見て `PortraitStore` から取り、
/// デコードしたものをメモリにためておく（画面と Now Playing で共有する）。肖像画がない・取れないときは nil。
@MainActor
final class Portraits {
    private let store: PortraitStore
    private let portraitOf: @MainActor (String) -> Portrait?
    private let images = NSCache<NSString, UIImage>()

    init(store: PortraitStore, portraitOf: @escaping @MainActor (String) -> Portrait?) {
        self.store = store
        self.portraitOf = portraitOf
        images.countLimit = 48
    }

    func portrait(of composerId: String) -> Portrait? { portraitOf(composerId) }

    private func key(_ composerId: String, _ portrait: Portrait) -> NSString {
        "\(composerId)\u{1F}\(portrait.sha256)" as NSString
    }

    /// メモリにあればすぐ返す（最初の描画で生成アートワークがちらつかないように）。
    func cachedImage(_ composerId: String) -> UIImage? {
        guard let portrait = portraitOf(composerId) else { return nil }
        return images.object(forKey: key(composerId, portrait))
    }

    func image(_ composerId: String) async -> UIImage? {
        guard let portrait = portraitOf(composerId) else { return nil }
        let key = key(composerId, portrait)
        if let image = images.object(forKey: key) { return image }
        guard let data = await store.load(composerId: composerId, portrait: portrait),
              let image = UIImage(data: data) else { return nil }
        images.setObject(image, forKey: key)
        return image
    }
}

private struct PortraitsKey: EnvironmentKey {
    static let defaultValue: Portraits? = nil
}

extension EnvironmentValues {
    /// nil なら肖像画を使わない（プレビューなど）。
    var portraits: Portraits? {
        get { self[PortraitsKey.self] }
        set { self[PortraitsKey.self] = newValue }
    }
}
