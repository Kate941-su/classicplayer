import Foundation
import Testing
@testable import CMSLClassical

struct CatalogDecodingTests {
    @Test func decodesSampleFixture() throws {
        let catalog = try Fixtures.sample()
        #expect(catalog.schemaVersion == 2)
        #expect(!catalog.replacedTracks.isEmpty)
        #expect(catalog.tracks.count == catalog.genres.reduce(0) { $0 + $1.trackCount })
        #expect(!catalog.license.credit.isEmpty)
        #expect(catalog.tracks.allSatisfy { $0.audioUrl.hasSuffix(".mp3") })
    }

    @Test func handlesNullsAndEmptyArrays() throws {
        let catalog = try Fixtures.sample()
        #expect(catalog.tracks.contains { $0.titleEn == nil })
        #expect(catalog.tracks.contains { $0.movement == nil })
        #expect(catalog.tracks.contains { $0.tags.isEmpty })
        #expect(catalog.tracks[0].durationSec == nil)
        // 実カタログには publishedAt と nameEn が null のものがないので、書き換えて確かめる
        var text = try Fixtures.text("catalog.sample.json")
        text = text.replacing(#/"publishedAt": "[^"]+"/#, maxReplacements: 1) { _ in "\"publishedAt\": null" }
        text = text.replacing("\"nameEn\": \"L.V.Beethoven\"", with: "\"nameEn\": null", maxReplacements: 1)
        let nulls = try CatalogJSON.decodeCatalog(Data(text.utf8))
        #expect(nulls.tracks[0].publishedAt == nil)
        #expect(nulls.composers.first { $0.id == "l-v-beethoven" }?.nameEn == nil)
    }

    @Test func ignoresUnknownKeys() throws {
        var text = try Fixtures.text("catalog.sample.json")
        text = text.replacing(
            "\"schemaVersion\": 2,", with: "\"schemaVersion\": 2, \"futureField\": {\"a\": [1, 2]},", maxReplacements: 1
        )
        text = text.replacing("\"durationSec\": null", with: "\"durationSec\": null, \"bpm\": 120")
        #expect(try CatalogJSON.decodeCatalog(Data(text.utf8)) == Fixtures.sample())
    }

    @Test func missingOptionalKeysUseDefaults() throws {
        let json = """
        {"id": "1", "title": "t", "composerId": "c", "workId": "w", "genreId": "g",
         "audioUrl": "https://classical-sound.up.seesaa.net/a.mp3", "fileName": "a.mp3",
         "articleUrl": "https://classical-sound.seesaa.net/article/1.html"}
        """
        let track = try JSONDecoder().decode(Track.self, from: Data(json.utf8))
        #expect(track.tags.isEmpty)
        #expect(track.movement == nil)
    }

    @Test func decodesManifest() throws {
        let manifest = try CatalogJSON.decodeManifest(Fixtures.data("manifest.sample.json"))
        #expect(manifest.catalogUrl == "catalog.json")
        #expect(manifest.sha256.count == 64)
        #expect(manifest.trackCount == (try Fixtures.sample()).tracks.count)
    }
}
