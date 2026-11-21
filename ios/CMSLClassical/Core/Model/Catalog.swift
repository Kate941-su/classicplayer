import Foundation

/// アプリが読めるカタログの schemaVersion。
let supportedSchemaVersion = 2

/// カタログ（schemaVersion 2）。正本は `schema/catalog.schema.json`。未知のキーは無視する（SPEC §3.2）。
struct Catalog: Codable, Equatable, Sendable {
    var schemaVersion: Int
    var generatedAt: String
    var source: String
    var license: License
    var genres: [Genre]
    var composers: [Composer]
    var works: [Work]
    var tracks: [Track]
    /// 新しい録音に置き換えて外した曲 ID → 同じ楽章の新しい録音の曲 ID（SPEC §3.2「作品と録音」）。
    var replacedTracks: [String: String]

    init(
        schemaVersion: Int, generatedAt: String, source: String, license: License,
        genres: [Genre], composers: [Composer], works: [Work], tracks: [Track],
        replacedTracks: [String: String] = [:]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAt = generatedAt
        self.source = source
        self.license = license
        self.genres = genres
        self.composers = composers
        self.works = works
        self.tracks = tracks
        self.replacedTracks = replacedTracks
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try c.decode(Int.self, forKey: .schemaVersion)
        generatedAt = try c.decode(String.self, forKey: .generatedAt)
        source = try c.decode(String.self, forKey: .source)
        license = try c.decode(License.self, forKey: .license)
        genres = try c.decode([Genre].self, forKey: .genres)
        composers = try c.decode([Composer].self, forKey: .composers)
        works = try c.decode([Work].self, forKey: .works)
        tracks = try c.decode([Track].self, forKey: .tracks)
        replacedTracks = try c.decodeIfPresent([String: String].self, forKey: .replacedTracks) ?? [:]
    }
}

struct License: Codable, Equatable, Sendable {
    var name: String
    var credit: String
    var url: String
    var notes: String?
}

struct Genre: Codable, Equatable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String
    var nameEn: String?
    var order: Int
    var trackCount: Int
}

/// `id` は表記揺れの修正で変わることがあるので永続化しない。
struct Composer: Codable, Equatable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String
    var nameEn: String?
    var trackCount: Int
}

/// `id` は表記揺れの修正で変わることがあるので永続化しない。
struct Work: Codable, Equatable, Hashable, Sendable, Identifiable {
    var id: String
    var title: String
    var composerId: String
    var genreId: String
}

/// `id` だけが永続化してよい ID（お気に入り・履歴・キュー）。
struct Track: Codable, Equatable, Hashable, Sendable, Identifiable {
    var id: String
    var title: String
    var titleEn: String?
    var composerId: String
    var workId: String
    var genreId: String
    var movement: String?
    var tags: [String]
    var audioUrl: String
    var fileName: String
    var articleUrl: String
    var publishedAt: String?
    var durationSec: Double?

    init(
        id: String, title: String, titleEn: String? = nil, composerId: String, workId: String, genreId: String,
        movement: String? = nil, tags: [String] = [], audioUrl: String, fileName: String, articleUrl: String,
        publishedAt: String? = nil, durationSec: Double? = nil
    ) {
        self.id = id
        self.title = title
        self.titleEn = titleEn
        self.composerId = composerId
        self.workId = workId
        self.genreId = genreId
        self.movement = movement
        self.tags = tags
        self.audioUrl = audioUrl
        self.fileName = fileName
        self.articleUrl = articleUrl
        self.publishedAt = publishedAt
        self.durationSec = durationSec
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        titleEn = try c.decodeIfPresent(String.self, forKey: .titleEn)
        composerId = try c.decode(String.self, forKey: .composerId)
        workId = try c.decode(String.self, forKey: .workId)
        genreId = try c.decode(String.self, forKey: .genreId)
        movement = try c.decodeIfPresent(String.self, forKey: .movement)
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        audioUrl = try c.decode(String.self, forKey: .audioUrl)
        fileName = try c.decode(String.self, forKey: .fileName)
        articleUrl = try c.decode(String.self, forKey: .articleUrl)
        publishedAt = try c.decodeIfPresent(String.self, forKey: .publishedAt)
        durationSec = try c.decodeIfPresent(Double.self, forKey: .durationSec)
    }
}

/// SPEC §3.5。
struct Manifest: Codable, Equatable, Sendable {
    var schemaVersion: Int
    var catalogVersion: String
    var catalogUrl: String
    var sha256: String
    var trackCount: Int
    var generatedAt: String
    var minAppSchema: Int

    var isSupported: Bool { schemaVersion == supportedSchemaVersion && minAppSchema <= supportedSchemaVersion }
}

enum CatalogJSON {
    static func decodeCatalog(_ data: Data) throws -> Catalog {
        try JSONDecoder().decode(Catalog.self, from: data)
    }

    static func decodeManifest(_ data: Data) throws -> Manifest {
        try JSONDecoder().decode(Manifest.self, from: data)
    }
}
