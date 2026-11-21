import Foundation

// 英語 UI では `nameEn` / `titleEn` を優先し、なければ日本語にフォールバックする（SPEC §4.11）。

extension Genre {
    func displayName(english: Bool) -> String { english ? nameEn ?? name : name }
}

extension Composer {
    func displayName(english: Bool) -> String { english ? nameEn ?? name : name }
}

extension Track {
    func displayTitle(english: Bool) -> String { english ? titleEn ?? title : title }

    /// 作品画面の行に出す短い名前（SPEC §4.11）。日本語は楽章名。英語は `titleEn` の作曲家の区切りより後ろから、
    /// 最初の楽章の印（`II. Adagio`・`I: Adagio`・`3. Allegro`）以降。印がなく楽章もない単曲は、区切りより後ろの英語の曲名。
    /// どれもなければタイトル全体。
    func shortTitle(english: Bool) -> String {
        if english, let en = titleEnAfterComposer {
            if let mark = MovementMark.find(in: en) {
                return String(en[mark...]).trimmingCharacters(in: .whitespaces)
            }
            if movement == nil { return en }
        }
        return movement ?? displayTitle(english: english)
    }

    /// 曲が属する作品の名前（ホームの「新着」など、楽章名だけでは何の曲か分からない所に出す）。
    /// 英語は `titleEn` の作曲家の区切りより後ろから楽章の印の前まで。なければ作品の `title`、作品がなければ曲のタイトル。
    func workTitle(english: Bool, work: Work?) -> String {
        if english, let en = titleEnAfterComposer {
            var name = MovementMark.find(in: en).map { String(en[..<$0]) } ?? en
            name = name.trimmingCharacters(in: .whitespaces)
            while let last = name.last, last == ":" || last == "：" { name.removeLast() }
            name = name.trimmingCharacters(in: .whitespaces)
            if !name.isEmpty { return name }
        }
        return work?.title ?? displayTitle(english: english)
    }

    /// `titleEn` の作曲家の区切り（最初の `:` か `：`）より後ろ。
    private var titleEnAfterComposer: String? {
        guard let en = titleEn else { return nil }
        let rest = en.firstIndex(where: { $0 == ":" || $0 == "：" }).map { en[en.index(after: $0)...] } ?? en[...]
        let trimmed = rest.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// 楽章の印。ローマ数字か数字の直後に「.」か「:」と空白。前は行頭・空白・コロン（「No.9 in」「Op.27-2」は当たらない）。
private enum MovementMark {
    /// 印の始まり（ローマ数字・数字の先頭）の位置。
    static func find(in text: String) -> String.Index? {
        let chars = Array(text)
        var offsets: [String.Index] = []
        offsets.reserveCapacity(chars.count + 1)
        var idx = text.startIndex
        for _ in chars { offsets.append(idx); idx = text.index(after: idx) }
        for start in chars.indices {
            if start > 0 {
                let prev = chars[start - 1]
                guard prev.isWhitespace || prev == ":" || prev == "：" else { continue }
            }
            var end = start
            if isRoman(chars[end]) {
                while end < chars.count, isRoman(chars[end]) { end += 1 }
            } else if chars[end].isASCII, chars[end].isNumber {
                while end < chars.count, chars[end].isASCII, chars[end].isNumber { end += 1 }
            } else {
                continue
            }
            guard end + 1 < chars.count, chars[end] == "." || chars[end] == ":", chars[end + 1].isWhitespace else {
                continue
            }
            return offsets[start]
        }
        return nil
    }

    private static func isRoman(_ c: Character) -> Bool { c == "I" || c == "V" || c == "X" }
}
