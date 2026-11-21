import Foundation
import Testing
@testable import CMSLClassical

/// PLAN 2.1: テストバンドルに共有フィクスチャが参照で入っていることを確かめる。
struct ProjectSetupTests {
    @Test func sharedFixturesAreBundled() throws {
        let bundle = Bundle(for: BundleToken.self)
        for name in ["catalog.sample", "manifest.sample", "search_cases"] {
            let url = try #require(bundle.url(forResource: name, withExtension: "json", subdirectory: "fixtures"))
            let data = try Data(contentsOf: url)
            #expect(try JSONSerialization.jsonObject(with: data) is [String: Any])
        }
    }

    @Test func backgroundAudioAndATSExceptionsAreDeclared() throws {
        let info = try #require(Bundle.main.infoDictionary)
        #expect((info["UIBackgroundModes"] as? [String])?.contains("audio") == true)
        let ats = try #require(info["NSAppTransportSecurity"] as? [String: Any])
        #expect(ats["NSAllowsArbitraryLoads"] == nil)
        let domains = try #require(ats["NSExceptionDomains"] as? [String: Any])
        #expect(Set(domains.keys) == ["classical-music.aki.gs", "andotowa.sakura.ne.jp", "classical-music.sakura.ne.jp"])
    }
}

private final class BundleToken {}
