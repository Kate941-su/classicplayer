import Testing
import UIKit
@testable import CMSLClassical

/// Android の `ArtworkSpecTest` と同じケース（`docs/artwork.md`）。
struct ArtworkTests {
    @Test func sameComposerAlwaysLooksTheSame() {
        let a = ArtworkSpec(composerId: "l-v-beethoven", genreId: "piano")
        for _ in 0..<10 { #expect(ArtworkSpec(composerId: "l-v-beethoven", genreId: "piano") == a) }
    }

    @Test func genreChangesOnlyThePattern() {
        let piano = ArtworkSpec(composerId: "l-v-beethoven", genreId: "piano")
        let symphony = ArtworkSpec(composerId: "l-v-beethoven", genreId: "symphony")
        #expect(piano.hue == symphony.hue)
        #expect(piano.variant == symphony.variant)
        #expect(piano.pattern == .keyboard)
        #expect(symphony.pattern == .concentricCircles)
    }

    @Test func differentComposersDiffer() {
        #expect(ArtworkSpec(composerId: "l-v-beethoven", genreId: "piano").hue != ArtworkSpec(composerId: "f-chopin", genreId: "piano").hue)
    }

    @Test func unknownGenreUsesStripes() {
        #expect(ArtworkSpec(composerId: "x", genreId: "unknown-genre").pattern == .diagonalStripes)
        #expect(ArtworkSpec(composerId: "x", genreId: "other").pattern == .diagonalStripes)
    }

    @Test func fnvKnownValues() {
        #expect(ArtworkSpec.fnv1a32("") == 0x811c_9dc5)
        #expect(ArtworkSpec.fnv1a32("a") == 0xe40c_292c)
        #expect(ArtworkSpec.fnv1a32("foobar") == 0xbf9c_f968)
        let unicode = ArtworkSpec(composerId: "作曲者不明", genreId: "x")
        #expect((0..<360).contains(unicode.hue))
        #expect((0...255).contains(unicode.variant))
    }

    @Test func hslMatchesAndroid() {
        // hsl(0, 38%, 34%) = #782B2B 付近（Android の ArtworkRenderer.hsl と同じ式）
        let color = UIColor(ArtworkPainter.hsl(0, 0.38, 0.34))
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        #expect(abs(r * 255 - 119.6) < 1.5)
        #expect(abs(g * 255 - 53.7) < 1.5)
        #expect(abs(b * 255 - 53.7) < 1.5)
    }

    @MainActor
    @Test(arguments: ArtworkSpec.Pattern.allCases)
    func rendersEveryPattern(pattern: ArtworkSpec.Pattern) throws {
        let genre = ["symphony", "orchestral", "concerto", "piano", "chamber", "vocal", "baroque", "other"]
            .first { ArtworkSpec.pattern(genreId: $0) == pattern } ?? "other"
        let png = try #require(ArtworkPainter.png(spec: ArtworkSpec(composerId: "l-v-beethoven", genreId: genre), size: 64))
        #expect(UIImage(data: png)?.size == CGSize(width: 64, height: 64))
    }
}
