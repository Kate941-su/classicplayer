import Testing
@testable import CMSLClassical

struct CatalogIndexTests {
    static let fate = "l-v-beethoven--交響曲第5番ハ短調op-67-運命"
    static let seventh = "l-v-beethoven--交響曲第7番イ長調op-92"

    let index: CatalogIndex

    init() throws { index = CatalogIndex(try Fixtures.sample()) }

    private func track(_ id: String) throws -> Track { try #require(index.track(id)) }

    @Test func genresAreInOrder() {
        #expect(index.genres.map(\.id) == ["symphony", "concerto", "piano", "chamber", "vocal", "other"])
    }

    @Test func tracksOfWorkAreInMovementOrder() {
        #expect(index.tracks(ofWork: Self.fate).map(\.id) == ["500111088-0", "500111088-1", "500150417"])
        let seventh = index.tracks(ofWork: Self.seventh)
        #expect(seventh.map(\.id) == ["499616036-0", "493005331", "222058571", "499616036-1"])
        #expect(seventh.map(\.movement) == ["第1楽章", "第2楽章", "第3楽章", "第4楽章"])
    }

    @Test func replacedTrackIdsResolveToNewRecording() {
        #expect(index.currentTrackId("427916377") == "500111088-0")
        #expect(index.currentTrackId("500111088-0") == "500111088-0")
        #expect(index.currentTrackId("1") == "1")
        #expect(index.replacements(for: ["427916377", "500111088-0", "1"]) == ["427916377": "500111088-0"])
    }

    @Test func tracksOfComposerAreInWorkAndMovementOrder() {
        let tracks = index.tracks(of: "l-v-beethoven")
        #expect(tracks.count == 9)
        #expect(tracks.first?.workId == "l-v-beethoven--ピアノソナタ第14番嬰ハ短調作品27-2-月光")
        #expect(tracks.last?.workId == Self.fate)
    }

    @Test func composersOfGenreWithCounts() {
        let counts = Dictionary(uniqueKeysWithValues: index.composers(ofGenre: "piano").map { ($0.composer.id, $0.trackCount) })
        #expect(counts == ["l-v-beethoven": 2, "frederic-francois-chopin": 2])
    }

    @Test func newestIsByPublishedAtDescending() {
        let newest = index.newest(limit: 10)
        let keys = newest.map { instantKey($0.publishedAt!) }
        #expect(keys == keys.sorted(by: >))
        #expect(newest.count == 10)
        #expect(newest.first?.id == "502781377-0")
        #expect(index.newest(limit: 100).count == index.catalog.tracks.filter { $0.publishedAt != nil }.count)
    }

    @Test func instantKeyNormalizesTimeZones() {
        #expect(instantKey("2020-03-20T18:18:00+09:00") == instantKey("2020-03-20T09:18:00Z"))
        #expect(instantKey("2020-03-20T09:18:00.5Z") > instantKey("2020-03-20T09:18:00Z"))
        #expect(instantKey("not a date") == "not a date")
    }

    @Test func movementNumbers() {
        #expect(Movement.number("第2楽章") == 2)
        #expect(Movement.number("第１２楽章") == 12)
        #expect(Movement.number("第三楽章") == 3)
        #expect(Movement.number("第十四楽章") == 14)
        #expect(Movement.number("第二十楽章") == 20)
        #expect(Movement.number("序奏") == nil)
        #expect(Movement.number(nil) == nil)
    }

    @Test func englishNamesFallBackToJapanese() throws {
        let beethoven = try #require(index.composer("l-v-beethoven"))
        #expect(beethoven.displayName(english: true) == "L.V.Beethoven")
        #expect(beethoven.displayName(english: false) == "ベートーヴェン")
        #expect(Composer(id: "x", name: "作曲者不明", nameEn: nil, trackCount: 1).displayName(english: true) == "作曲者不明")
    }

    @Test func englishShortTitleStartsAtMovementMark() throws {
        #expect(try track("474143449").shortTitle(english: true) == "II. Adagio cantabile")
        #expect(try track("474143449").shortTitle(english: false) == "第2楽章")
        #expect(try track("499179250").shortTitle(english: true) == "I. Allegro non troppo")
        #expect(try track("500150417").shortTitle(english: true) == "3. Allegro - 4. Allegro-Presto")
        var roman = try track("499179250")
        roman.titleEn = "Antonín Dvořák：Symphony No.9 \"From the New World\" I: Adagio"
        #expect(roman.shortTitle(english: true) == "I: Adagio")
        #expect(try track("474475833").shortTitle(english: true) == "Recuerdos de la Alhambra")
        #expect(try track("476156249-1").shortTitle(english: true) == "第2楽章")
        var ascii = try track("474143449")
        ascii.titleEn = "L.V.Beethoven:Piano Sonata No.8, Op.13:I. Grave - Allegro"
        #expect(ascii.shortTitle(english: true) == "I. Grave - Allegro")
    }

    @Test func workTitleIsEnglishTitleBeforeMovement() throws {
        func title(_ id: String, _ english: Bool) throws -> String {
            let t = try track(id)
            return t.workTitle(english: english, work: index.work(t.workId))
        }
        #expect(try title("501651167", true) == "Piano Sonata No.14 in C sharp minor, Op.27-2 \"Moonlight\"")
        #expect(try title("500150417", true) == "Symphony No.5 in C minor, Op.67")
        #expect(try title("474475833", true) == "Recuerdos de la Alhambra")
        #expect(try title("476156249-1", true) == "交響曲 第4番 ホ短調 Op.98")
        #expect(try title("501651167", false) == "ピアノソナタ 第14番 嬰ハ短調 作品27-2 「月光」")
    }
}
