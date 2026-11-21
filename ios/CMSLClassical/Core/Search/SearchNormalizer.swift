import Foundation

/// 検索の正規化。規則の正本は `docs/search-normalization.md`（Android と共通）。
enum SearchNormalizer {
    private static let vu: [(String, String)] = [
        ("ヴァ", "バ"), ("ヴィ", "ビ"), ("ヴェ", "ベ"), ("ヴォ", "ボ"), ("ヴュ", "ビュ"), ("ヴ", "ブ"),
    ]
    private static let removed: Set<Unicode.Scalar> = Set("ー・-‐–—_.,'\"/:;!?()[]{}「」『』【】、。〜~".unicodeScalars)

    static func normalize(_ text: String) -> String {
        // 1. NFKC、2. 小文字（Swift の lowercased はロケールに依存しない）
        var s = text.precomposedStringWithCompatibilityMapping.lowercased()
        // 3. ラテン文字の発音記号を外す
        s = stripLatinDiacritics(s)
        // 4. ひらがな → カタカナ
        var scalars = String.UnicodeScalarView()
        for scalar in s.unicodeScalars {
            if (0x3041...0x3096).contains(scalar.value), let kata = Unicode.Scalar(scalar.value + 0x60) {
                scalars.append(kata)
            } else {
                scalars.append(scalar)
            }
        }
        s = String(scalars)
        // 5. 「ヴ」の揺れ
        for (from, to) in vu { s = s.replacingOccurrences(of: from, with: to) }
        // 6. 空白類と記号を削除
        var out = String.UnicodeScalarView()
        for scalar in s.unicodeScalars where !(scalar.properties.isWhitespace || removed.contains(scalar)) {
            out.append(scalar)
        }
        return String(out)
    }

    /// 検索語を語に分けて正規化する。空になった語は捨てる。
    static func terms(_ query: String) -> [String] {
        query.split(whereSeparator: { $0.isWhitespace }).map { normalize(String($0)) }.filter { !$0.isEmpty }
    }

    private static func stripLatinDiacritics(_ s: String) -> String {
        let decomposed = s.decomposedStringWithCanonicalMapping
        let isCombining: (Unicode.Scalar) -> Bool = { (0x0300...0x036F).contains($0.value) }
        guard decomposed.unicodeScalars.contains(where: isCombining) else { return s }
        var kept = String.UnicodeScalarView()
        for scalar in decomposed.unicodeScalars where !isCombining(scalar) { kept.append(scalar) }
        return String(kept).precomposedStringWithCanonicalMapping
    }
}
