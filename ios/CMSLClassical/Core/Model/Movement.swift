/// 楽章名から並び順の番号を取り出す（SPEC §4.5）。「第2楽章」「第２楽章」「第二楽章」→ 2。取れなければ nil。
enum Movement {
    private static let kanji: [Character: Int] = [
        "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9,
    ]

    static func number(_ movement: String?) -> Int? {
        guard let movement, !movement.allSatisfy(\.isWhitespace) else { return nil }
        if let digits = firstDigitRun(movement) { return digits }
        return kanjiNumber(movement)
    }

    private static func digitValue(_ c: Character) -> Int? {
        guard let scalar = c.unicodeScalars.first, c.unicodeScalars.count == 1 else { return nil }
        switch scalar.value {
        case 0x30...0x39: return Int(scalar.value - 0x30)
        case 0xFF10...0xFF19: return Int(scalar.value - 0xFF10)
        default: return nil
        }
    }

    private static func firstDigitRun(_ text: String) -> Int? {
        var value: Int?
        for c in text {
            if let d = digitValue(c) {
                value = (value ?? 0) * 10 + d
            } else if value != nil {
                break
            }
        }
        return value
    }

    /// 「第十二楽章」まで（十の位は「十」「二十」…）。
    private static func kanjiNumber(_ text: String) -> Int? {
        guard let start = text.firstIndex(where: { kanji[$0] != nil || $0 == "十" }) else { return nil }
        var value = 0
        var current = 0
        for c in text[start...] {
            if c == "十" {
                value += (current == 0 ? 1 : current) * 10
                current = 0
            } else if let d = kanji[c] {
                current = d
            } else {
                break
            }
        }
        let result = value + current
        return result > 0 ? result : nil
    }
}
