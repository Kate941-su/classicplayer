# 生成アートワーク（SPEC §4.10）

CMSL の音源には画像がないので、アプリ内でアートワークを描く（色のグラデーション + ジャンルの模様 + ジャンルのイラスト）。作曲家に肖像画（`composers[].portrait`、SPEC §3.6）があれば、作曲家の一覧・作曲家詳細・プレイヤー・ロック画面と通知では肖像画を表示し（SPEC §4.10）、このアートワークは肖像画のない作曲家と、肖像画を取得できないときのフォールバックに使う。iOS と Android は同じ規則で描く（描画の細部まで一致させる必要はないが、**色相・パターン・variant は一致させる**）。

## 入力と値

| 値 | 決め方 |
|---|---|
| `h` | 作曲家 ID（`composer.id`）の UTF-8 バイト列の **FNV-1a 32 ビット**ハッシュ（offset `0x811c9dc5`、prime `0x01000193`） |
| 色相 `hue` | `h mod 360`（度） |
| `variant` | `(h >> 16) & 0xff`。線の本数や中心の位置など、細部を決める |
| パターン | ジャンル ID で決める（下表）。未知のジャンルは「斜めの縞」 |
| イラスト | ジャンル ID で決める（下表）。未知のジャンル（`baroque` を含む）は `other`（音符） |

既知の値（テストで確かめる）: `fnv1a32("") = 0x811c9dc5`、`fnv1a32("a") = 0xe40c292c`、`fnv1a32("foobar") = 0xbf9cf968`

## パターン

| ジャンル ID | パターン | イラスト |
|---|---|---|
| `symphony` | 同心円 | 譜面台と指揮棒 |
| `orchestral` | 波線 | トランペット |
| `concerto` | 角から広がる弧 | ヴァイオリンと弓 |
| `piano` | 鍵盤の縞（白鍵の線と黒鍵） | グランドピアノ |
| `chamber` | 点の格子 | ギター |
| `vocal` | アーチの列 | 劇場の幕と歌手 |
| `baroque` | ひし形の格子 | 音符 |
| その他 | 斜めの縞 | 音符 |

## イラスト
- 正本は `design/genre-art/{イラスト}.svg`（viewBox `0 0 100 100`）。使えるのは path（絶対座標の M L H V C Q Z）・circle・ellipse・rect・line・g と、transform の rotate / translate。色は `#fff`（アクセント色）・`#000`（濃い色）・`none` だけ
- `python3 design/genre-art/generate.py` で、M・L・C・Z だけの命令列（0=M x y、1=L x y、2=C x1 y1 x2 y2 x y、3=Z）にした `GenreIllustrations.kt`（Android `core/artwork`）と `GenreIllustrations.swift`（iOS `UI/`）を作る。生成したファイルは手で直さない
- 配置: 枠の短い辺の正方形に収め、中央に置く（iOS のジャンルカードは左下に文字があるので右に寄せる）。模様は長い辺の正方形を枠の中央で切り抜いた形で敷く（横長の枠でもイラストが切れない）

## 色
- 背景: 左上 `hsl(hue, 38%, 34%)` → 右下 `hsl(hue + 28, 38%, 18%)` の線形グラデーション
- 模様: `hsl(hue + 14, 38%, 78%)`、不透明度 約 27%（70/255。イラストの後ろなので薄くする）
- イラスト: アクセント色 `hsl(hue + 14, 38%, 86%)`、濃い色 `hsl(hue + 28, 38%, 20%)`（どちらも不透明）。線は端と角を丸める
- 肖像画（`site/v1/portraits/` のもの）以外の外部画像は使わない

実装: Android `android/core/artwork/`、iOS `ios/CMSLClassical/UI/ArtworkCanvas.swift`
