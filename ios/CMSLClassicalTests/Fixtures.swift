import Foundation
@testable import CMSLClassical

/// リポジトリ直下の fixtures/（テストバンドルに参照で入れてある）。
enum Fixtures {
    private final class Token {}

    static func url(_ name: String) -> URL {
        let bundle = Bundle(for: Token.self)
        guard let base = bundle.resourceURL?.appendingPathComponent("fixtures") else { fatalError("fixtures がない") }
        return base.appendingPathComponent(name)
    }

    static func data(_ name: String) throws -> Data { try Data(contentsOf: url(name)) }

    static func text(_ name: String) throws -> String {
        guard let text = String(bytes: try data(name), encoding: .utf8) else { throw CocoaError(.fileReadInapplicableStringEncoding) }
        return text
    }

    static func sample() throws -> Catalog { try CatalogJSON.decodeCatalog(data("catalog.sample.json")) }
}
