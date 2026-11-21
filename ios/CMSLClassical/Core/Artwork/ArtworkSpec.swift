/// 生成アートワークの値（`docs/artwork.md`。Android の `ArtworkSpec` と同じ規則）。
struct ArtworkSpec: Equatable, Sendable {
    enum Pattern: String, Sendable, CaseIterable {
        case concentricCircles, waves, cornerArcs, keyboard, dotGrid, arches, diamonds, diagonalStripes
    }

    let hue: Double
    let variant: Int
    let pattern: Pattern

    init(composerId: String, genreId: String) {
        let h = Self.fnv1a32(composerId)
        hue = Double(h % 360)
        variant = Int((h >> 16) & 0xff)
        pattern = Self.pattern(genreId: genreId)
    }

    static func pattern(genreId: String) -> Pattern {
        switch genreId {
        case "symphony": .concentricCircles
        case "orchestral": .waves
        case "concerto": .cornerArcs
        case "piano": .keyboard
        case "chamber": .dotGrid
        case "vocal": .arches
        case "baroque": .diamonds
        default: .diagonalStripes
        }
    }

    /// FNV-1a 32 ビット（UTF-8 バイト列）。
    static func fnv1a32(_ text: String) -> UInt32 {
        var hash: UInt32 = 0x811c_9dc5
        for byte in text.utf8 {
            hash ^= UInt32(byte)
            hash = hash &* 0x0100_0193
        }
        return hash
    }
}
