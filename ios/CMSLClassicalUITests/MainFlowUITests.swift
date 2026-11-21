import XCTest

/// 主要フロー: ホーム → ジャンル → 作曲家 → 作品 → 作品を通して再生 → ミニプレイヤー → フルプレイヤー（PLAN 2.7）。
/// 環境変数 `SCREENSHOTS_DIR`（xcodebuild では `TEST_RUNNER_SCREENSHOTS_DIR`）があれば、各画面を PNG で保存する。
@MainActor
final class MainFlowUITests: XCTestCase {
    override func setUp() async throws {
        continueAfterFailure = false
    }

    func testBrowseAndPlayJapaneseLight() throws {
        try runFlow(language: "ja", dark: false)
    }

    func testBrowseAndPlayEnglishLight() throws {
        try runFlow(language: "en", dark: false)
    }

    func testBrowseAndPlayJapaneseDark() throws {
        try runFlow(language: "ja", dark: true)
    }

    private func runFlow(language: String, dark: Bool) throws {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTesting", "-AppleLanguages", "(\(language))", "-AppleLocale", language == "ja" ? "ja_JP" : "en_US"]
        if dark { app.launchArguments += ["-uiDarkMode"] }
        app.launch()
        let prefix = "\(language)-\(dark ? "dark" : "light")"

        let genre = app.buttons["genre-symphony"]
        XCTAssertTrue(genre.waitForExistence(timeout: 20))
        save("\(prefix)-home")
        genre.tap()

        let composer = app.buttons["composer-l-v-beethoven"]
        XCTAssertTrue(composer.waitForExistence(timeout: 5))
        save("\(prefix)-genre")
        composer.tap()

        // 作曲家詳細: 肖像画とクレジット（SPEC §4.4）
        let credit = app.links.matching(NSPredicate(format: "label CONTAINS %@", "Joseph Karl Stieler")).firstMatch
        XCTAssertTrue(credit.waitForExistence(timeout: 5))
        save("\(prefix)-composer")

        let work = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'work-'")).firstMatch
        XCTAssertTrue(work.waitForExistence(timeout: 5))
        work.tap()

        let playWork = app.buttons["playWork"]
        XCTAssertTrue(playWork.waitForExistence(timeout: 5))
        playWork.tap()
        let mini = app.buttons["miniPlayer"]
        XCTAssertTrue(mini.waitForExistence(timeout: 5))
        save("\(prefix)-work")

        mini.tap()
        let playPause = app.buttons["playerPlayPause"]
        XCTAssertTrue(playPause.waitForExistence(timeout: 5))
        // シートが開き終わるのを待ってから押す（アニメーション中は別のボタンに当たる）
        XCTAssertTrue(playPause.waitUntilHittable(timeout: 5))
        sleep(1)
        // 音源の読み込みを待たずに止める（ネットワークに依存しない）
        playPause.tap()

        // スリープタイマーを 15 分に（SPEC §5.2）
        app.buttons["sleepTimer"].tap()
        let fifteen = app.buttons[language == "ja" ? "15 分" : "15 minutes"]
        XCTAssertTrue(fifteen.waitForExistence(timeout: 5))
        fifteen.tap()
        let remaining = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", language == "ja" ? "残り 1" : " left")).firstMatch
        XCTAssertTrue(remaining.waitForExistence(timeout: 5))
        sleep(1)
        save("\(prefix)-player")

        // 情報タブ → 画像のクレジット（SPEC §4.9）
        app.buttons["closePlayer"].tap()
        app.tabBars.buttons.element(boundBy: 3).tap()
        let creditsLink = app.buttons["imageCreditsLink"]
        for _ in 0..<5 where !creditsLink.isHittable { app.swipeUp() }
        creditsLink.tap()
        XCTAssertTrue(app.staticTexts[language == "ja" ? "ショパン" : "Frederic Francois Chopin"].waitForExistence(timeout: 5))
        save("\(prefix)-image-credits")
    }

    private func save(_ name: String) {
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        guard let dir = ProcessInfo.processInfo.environment["SCREENSHOTS_DIR"], !dir.isEmpty else { return }
        try? shot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
    }
}

@MainActor
private extension XCUIElement {
    func waitUntilHittable(timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if exists, isHittable { return true }
            Thread.sleep(forTimeInterval: 0.1)
        }
        return false
    }
}
