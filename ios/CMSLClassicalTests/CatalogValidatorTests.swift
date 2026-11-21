import Testing
@testable import CMSLClassical

struct CatalogValidatorTests {
    @Test func sampleIsValid() throws {
        #expect(CatalogValidator.validate(try Fixtures.sample()) == [])
    }

    @Test(arguments: [
        "duplicate-track-id.json", "dangling-work-ref.json", "foreign-audio-domain.json",
        "schema-version-3.json", "trackcount-mismatch.json",
    ])
    func rejectsInvalidFixtures(name: String) throws {
        let catalog = try CatalogJSON.decodeCatalog(Fixtures.data("invalid/\(name)"))
        #expect(!CatalogValidator.validate(catalog).isEmpty)
    }

    @Test func oldServersOnlyOverHTTPAndReplacementsMustExist() throws {
        let sample = try Fixtures.sample()
        #expect(sample.tracks.contains { $0.audioUrl.hasPrefix("http://") })
        func withAudio(_ url: String) -> Catalog {
            var c = sample
            c.tracks[0].audioUrl = url
            return c
        }
        #expect(!CatalogValidator.validate(withAudio("https://classical-music.aki.gs/a.mp3")).isEmpty)
        #expect(!CatalogValidator.validate(withAudio("http://classical-sound.up.seesaa.net/a.mp3")).isEmpty)
        #expect(!CatalogValidator.validate(withAudio("https://classical-sound.up.seesaa.net/a.mp3.html")).isEmpty)
        #expect(CatalogValidator.validate(withAudio("http://andotowa.sakura.ne.jp/x/a.mp3")).isEmpty)
        var dangling = sample
        dangling.replacedTracks = ["1": "2"]
        #expect(CatalogValidator.validate(dangling) == ["replacedTracks 1: 置き換え先 2 がない"])
    }

    @Test func portraitIsOptionalAndPathHashSourceAreChecked() throws {
        let sample = try Fixtures.sample()
        let index = try #require(sample.composers.firstIndex { $0.id == "l-v-beethoven" })
        let portrait = try #require(sample.composers[index].portrait)
        #expect(portrait.url == "portraits/l-v-beethoven.jpg")
        func with(_ p: Portrait?) -> Catalog {
            var c = sample
            c.composers[index].portrait = p
            return c
        }
        #expect(CatalogValidator.validate(with(nil)).isEmpty)
        var other = portrait
        other.url = "portraits/j-s-bach.jpg"
        #expect(!CatalogValidator.validate(with(other)).isEmpty)
        other = portrait
        other.url = "../catalog.json"
        #expect(!CatalogValidator.validate(with(other)).isEmpty)
        other = portrait
        other.sha256 = "ABC"
        #expect(!CatalogValidator.validate(with(other)).isEmpty)
        other = portrait
        other.sourceUrl = "https://example.com/a"
        #expect(!CatalogValidator.validate(with(other)).isEmpty)
    }
}
