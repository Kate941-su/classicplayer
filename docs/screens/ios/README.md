# iOS のスクリーンショット

UI テスト（`ios/CMSLClassicalUITests/MainFlowUITests.swift`）が主要フローをたどりながら撮る（フィクスチャのカタログ、iPhone 17 シミュレーター）。

```sh
cd ios && TEST_RUNNER_SCREENSHOTS_DIR=$PWD/../docs/screens/ios xcodebuild -scheme CMSLClassical \
  -destination 'platform=iOS Simulator,name=iPhone 17' CMSL_SEED_CATALOG=$PWD/../fixtures/catalog.sample.json \
  -only-testing:CMSLClassicalUITests test
```

| ファイル | 内容 |
|---|---|
| `{ja,en}-light-*.png` / `ja-dark-*.png` | ホーム・ジャンル・作品（再生開始後）・フルプレイヤー（一時停止後） |

フルプレイヤーの画面は音源を実際に読み込むので、ネットワークが必要。
