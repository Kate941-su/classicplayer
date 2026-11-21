import Foundation

/// 取得したカタログを差し替える前の検査（SPEC §7）。`catalog/validate_catalog.py` のうち、
/// アプリが壊れないために必要なもの（版、ID の重複、参照切れ、URL のドメイン、件数の整合、置き換え先）だけを見る。
enum CatalogValidator {
    /// CMSL（https）と、制作者の旧サーバー（http のみ。Info.plist の ATS 例外と同じ 3 つ）。SPEC §1.2
    static let audioHosts: [(scheme: String, host: String)] = [
        ("https", "classical-sound.up.seesaa.net"),
        ("http", "classical-music.aki.gs"),
        ("http", "andotowa.sakura.ne.jp"),
        ("http", "classical-music.sakura.ne.jp"),
    ]

    static func validate(_ catalog: Catalog) -> [String] {
        guard catalog.schemaVersion == supportedSchemaVersion else {
            return ["schemaVersion \(catalog.schemaVersion) は対応範囲外"]
        }
        var errors: [String] = []
        if catalog.tracks.isEmpty { errors.append("曲が 0 件") }
        errors += duplicates("genre", catalog.genres.map(\.id))
        errors += duplicates("composer", catalog.composers.map(\.id))
        errors += duplicates("work", catalog.works.map(\.id))
        errors += duplicates("track", catalog.tracks.map(\.id))
        errors += referenceErrors(catalog)
        errors += portraitErrors(catalog)
        errors += countErrors(catalog)
        let trackIds = Set(catalog.tracks.map(\.id))
        for (old, new) in catalog.replacedTracks.sorted(by: { $0.key < $1.key }) where !trackIds.contains(new) {
            errors.append("replacedTracks \(old): 置き換え先 \(new) がない")
        }
        return errors
    }

    /// 肖像画のパス（SPEC §3.6）。ほかのパスやホストの画像は読まない。
    static func portraitPath(_ composerId: String) -> String { "portraits/\(composerId).jpg" }

    private static func portraitErrors(_ catalog: Catalog) -> [String] {
        var errors: [String] = []
        for composer in catalog.composers {
            guard let p = composer.portrait else { continue }
            if p.url != portraitPath(composer.id) {
                errors.append("composer \(composer.id): portrait.url が \(portraitPath(composer.id)) ではない")
            }
            if p.sha256.count != 64 || !p.sha256.allSatisfy({ $0.isHexDigit && !$0.isUppercase }) {
                errors.append("composer \(composer.id): portrait.sha256 の形式が違う")
            }
            if !p.sourceUrl.hasPrefix("https://commons.wikimedia.org/") || p.sourceUrl.contains(where: \.isWhitespace) {
                errors.append("composer \(composer.id): portrait.sourceUrl が Commons ではない")
            }
        }
        return errors
    }

    static func isAllowedAudioURL(_ text: String) -> Bool {
        for (scheme, host) in audioHosts {
            let prefix = "\(scheme)://\(host)/"
            guard text.hasPrefix(prefix) else { continue }
            let path = text.dropFirst(prefix.count)
            return path.count > ".mp3".count && path.hasSuffix(".mp3") && !path.contains(where: \.isWhitespace)
        }
        return false
    }

    static func isValidArticleURL(_ text: String) -> Bool {
        let prefix = "https://classical-sound.seesaa.net/article/", suffix = ".html"
        guard text.hasPrefix(prefix), text.hasSuffix(suffix) else { return false }
        let id = text.dropFirst(prefix.count).dropLast(suffix.count)
        return !id.isEmpty && id.allSatisfy { $0.isASCII && $0.isNumber }
    }

    private static func referenceErrors(_ catalog: Catalog) -> [String] {
        let genreIds = Set(catalog.genres.map(\.id))
        let composerIds = Set(catalog.composers.map(\.id))
        let workIds = Set(catalog.works.map(\.id))
        var errors: [String] = []
        for w in catalog.works {
            if !composerIds.contains(w.composerId) { errors.append("work \(w.id): composerId \(w.composerId) がない") }
            if !genreIds.contains(w.genreId) { errors.append("work \(w.id): genreId \(w.genreId) がない") }
        }
        for t in catalog.tracks {
            if !composerIds.contains(t.composerId) { errors.append("track \(t.id): composerId \(t.composerId) がない") }
            if !workIds.contains(t.workId) { errors.append("track \(t.id): workId \(t.workId) がない") }
            if !genreIds.contains(t.genreId) { errors.append("track \(t.id): genreId \(t.genreId) がない") }
            if !isAllowedAudioURL(t.audioUrl) { errors.append("track \(t.id): audioUrl のドメインが CMSL（と旧サーバー）ではない") }
            if !isValidArticleURL(t.articleUrl) { errors.append("track \(t.id): articleUrl の形式が違う") }
        }
        return errors
    }

    private static func countErrors(_ catalog: Catalog) -> [String] {
        var byGenre: [String: Int] = [:], byComposer: [String: Int] = [:]
        for t in catalog.tracks {
            byGenre[t.genreId, default: 0] += 1
            byComposer[t.composerId, default: 0] += 1
        }
        var errors: [String] = []
        for g in catalog.genres where g.trackCount != byGenre[g.id, default: 0] {
            errors.append("genre \(g.id): trackCount \(g.trackCount) が実数 \(byGenre[g.id, default: 0]) と違う")
        }
        for c in catalog.composers where c.trackCount != byComposer[c.id, default: 0] {
            errors.append("composer \(c.id): trackCount \(c.trackCount) が実数 \(byComposer[c.id, default: 0]) と違う")
        }
        return errors
    }

    private static func duplicates(_ kind: String, _ ids: [String]) -> [String] {
        var seen: [String: Int] = [:]
        for id in ids { seen[id, default: 0] += 1 }
        return seen.filter { $0.value > 1 }.keys.sorted().map { "\(kind) の id が重複: \($0)" }
    }
}
