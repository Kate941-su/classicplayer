# Android のスクリーンショット

`android/app/src/test/.../ScreenshotsTest.kt` が Robolectric のネイティブ描画で撮る（フィクスチャのカタログ、411×891dp）。

```sh
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScreenshotsTest' -Pcmsl.recordScreenshots
```

| ファイル | 内容 |
|---|---|
| `{ja,en}-light-*.png` / `ja-dark-*.png` | ホーム・ジャンル・作品（再生開始後）・フルプレイヤー |

実機・エミュレーターでの見た目は `tool/run.sh` で確認する。
