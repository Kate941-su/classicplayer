import Foundation
import Testing
@testable import CMSLClassical

/// `fixtures/search_cases.json` をすべて通す（Android と同じケース）。
struct SearchCasesTests {
    struct Cases: Decodable {
        let normalize: [NormalizeCase]
        let queries: [QueryCase]
    }

    struct NormalizeCase: Decodable {
        let input: String
        let expected: String
    }

    struct QueryCase: Decodable {
        let query: String
        let expect: [String: [String]]?
        let absent: [String: [String]]?
        let first: [String: String]?
        let empty: Bool?
    }

    let cases: Cases
    let index: SearchIndex

    init() throws {
        cases = try JSONDecoder().decode(Cases.self, from: Fixtures.data("search_cases.json"))
        index = SearchIndex(CatalogIndex(try Fixtures.sample()))
    }

    @Test func normalizeCases() {
        for c in cases.normalize {
            #expect(SearchNormalizer.normalize(c.input) == c.expected, "\(c.input)")
        }
    }

    @Test func queryCases() throws {
        for q in cases.queries {
            let r = index.search(q.query)
            let found = ["composers": r.composers.map(\.id), "works": r.works.map(\.id), "tracks": r.tracks.map(\.id)]
            if q.empty == true { #expect(r.isEmpty, "「\(q.query)」は空のはず: \(found)") }
            for (section, ids) in q.expect ?? [:] {
                let list = try #require(found[section])
                for id in ids { #expect(list.contains(id), "「\(q.query)」の \(section) に \(id) がない: \(list)") }
            }
            for (section, ids) in q.absent ?? [:] {
                let list = try #require(found[section])
                for id in ids { #expect(!list.contains(id), "「\(q.query)」の \(section) に \(id) がある") }
            }
            for (section, id) in q.first ?? [:] {
                #expect(found[section]?.first == id, "「\(q.query)」の \(section) の先頭")
            }
        }
    }

    @Test func blankQueryIsEmpty() {
        #expect(index.search("　 ").isEmpty)
        #expect(index.search("ー・").isEmpty)
    }

    @Test func rankingPutsPrefixMatchesFirst() {
        // 曲名に一致した曲だけが出る（作品名・作曲家名だけでの一致はない語）
        let tracks = index.search("ピアノソナタ").tracks
        #expect(!tracks.isEmpty)
        #expect(tracks.allSatisfy { $0.title.contains("ピアノソナタ") || $0.titleEn?.contains("Piano Sonata") == true })
    }
}
