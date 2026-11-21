#!/usr/bin/env python3
"""
CMSL クラシック名曲サウンドライブラリー カタログ作成スクリプト

https://classical-sound.seesaa.net/ を巡回し、ダウンロード可能な mp3 を
ジャンル（ブログのカテゴリ）・作曲家・作品ごとに整理した catalog.json を作ります。

使い方:
    python3 build_catalog.py                 # 全体を巡回して catalog.json を出力
    python3 build_catalog.py --max-pages 50  # お試し（50ページで打ち切り）
    python3 build_catalog.py --offline       # キャッシュ済みHTMLだけで作り直す
    python3 build_catalog.py --refresh-index # 差分巡回（一覧ページだけ取り直し、新しい記事だけ取得）

依存ライブラリなし（Python 3.9+ 標準ライブラリのみ）。
取得したHTMLは cache/ に保存されるので、2回目以降はサーバーにアクセスしません。

音源の利用条件（サイト記載）: フリー素材・商用利用可・許諾不要、
著作権は制作者に帰属、Creative Commons 表示 2.1 日本。
アプリ内には必ずクレジット「CMSL クラシック名曲サウンドライブラリー」を表示してください。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from collections import Counter, defaultdict, deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from html import unescape
from html.parser import HTMLParser
from pathlib import Path

BASE = "https://classical-sound.seesaa.net/"
HOST = "classical-sound.seesaa.net"
MP3_RE = re.compile(r"^https?://classical-sound\.up\.seesaa\.net/.+\.mp3$", re.I)
USER_AGENT = "CMSL-Catalog-Builder/1.0 (personal classical music app; polite crawler)"

# 巡回対象にするURL（Seesaa標準の構成）
ARTICLE_RE = re.compile(r"^/article/(\d+)\.html$")
CATEGORY_RE = re.compile(r"^/category/(\d+)-(\d+)\.html$")
FOLLOW_RES = [
    ARTICLE_RE,
    CATEGORY_RE,
    re.compile(r"^/archives/\d{6}-\d+\.html$"),
    re.compile(r"^/index-?\d*\.html$"),
    re.compile(r"^/$"),
]

# クラシック以外のカテゴリ（部分一致で除外）。--exclude-category で追加可能。
DEFAULT_EXCLUDE = ["J-POP", "洋楽", "お知らせ", "ラストシンフォニー"]


# ---------------------------------------------------------------- HTML解析
class PageParser(HTMLParser):
    """リンク・テキスト・タイトルを順番通りに集める軽量パーサー。"""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.events: list[tuple] = []  # ("text", str) / ("link", href, text) / ("media", src)
        self.title = ""
        self.og_title = ""
        self._in_title = False
        self._a_stack: list[list] = []
        self._skip = 0  # script/style

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("script", "style"):
            self._skip += 1
        elif tag == "title":
            self._in_title = True
        elif tag == "meta" and a.get("property") == "og:title":
            self.og_title = a.get("content", "") or ""
        elif tag == "a" and a.get("href"):
            self._a_stack.append([a["href"], []])
        elif tag in ("audio", "source", "embed") and a.get("src"):
            self.events.append(("media", a["src"]))

    def handle_endtag(self, tag):
        if tag in ("script", "style") and self._skip:
            self._skip -= 1
        elif tag == "title":
            self._in_title = False
        elif tag == "a" and self._a_stack:
            href, parts = self._a_stack.pop()
            self.events.append(("link", href, " ".join("".join(parts).split())))

    def handle_data(self, data):
        if self._skip:
            return
        if self._in_title:
            self.title += data
        if self._a_stack:
            self._a_stack[-1][1].append(data)
        elif data.strip():
            self.events.append(("text", data))


def parse(html: str) -> PageParser:
    p = PageParser()
    p.feed(html)
    return p


def normalize(url: str, base: str) -> str | None:
    u = urllib.parse.urljoin(base, url)
    s = urllib.parse.urlsplit(u)
    if s.scheme not in ("http", "https"):
        return None
    return urllib.parse.urlunsplit(("https", s.netloc.lower(), s.path or "/", "", ""))


# ---------------------------------------------------------------- 取得
MIN_DELAY = 1.0  # 巡回の間隔の下限（秒）。CLAUDE.md の原則


def is_listing(url: str) -> bool:
    """記事以外（トップ・一覧・カテゴリ・アーカイブ）のページか。"""
    return not ARTICLE_RE.match(urllib.parse.urlsplit(url).path)


class Fetcher:
    """HTML を取得してキャッシュする。

    - キャッシュにあるページは通信しない
    - refresh_index=True のときは、一覧ページ（記事以外）だけキャッシュを無視して取り直す。
      記事ページはキャッシュを使うので、2 回目以降の巡回は「一覧ページ + 新しい記事」だけになる
    - transport / sleep / robots はテスト用に差し替えられる
    """

    def __init__(self, cache_dir: Path, delay: float, offline: bool, refresh_index: bool = False,
                 transport=None, sleep=time.sleep, robots="auto"):
        if delay < MIN_DELAY:
            raise ValueError(f"--delay は {MIN_DELAY} 秒以上にしてください（指定: {delay}）")
        self.cache_dir = cache_dir
        self.delay = delay
        self.offline = offline
        self.refresh_index = refresh_index
        self.transport = transport or self._urlopen
        self.sleep = sleep
        self.fetched: list[str] = []  # 実際に通信した URL（テストと集計用）
        self._last = 0.0
        cache_dir.mkdir(parents=True, exist_ok=True)
        self.robots = None
        if robots == "auto" and not offline:
            try:
                rp = urllib.robotparser.RobotFileParser()
                rp.set_url(urllib.parse.urljoin(BASE, "/robots.txt"))
                rp.read()
                self.robots = rp
            except Exception:
                self.robots = None  # 読めない場合は制限なしとして扱う
        elif robots not in ("auto", None):
            self.robots = robots

    def _path(self, url: str) -> Path:
        return self.cache_dir / (hashlib.sha1(url.encode()).hexdigest() + ".html")

    @staticmethod
    def _urlopen(url: str) -> tuple[int, bytes | None, str]:
        """(HTTP ステータス, 本文, 文字コード)。通信エラーは例外。"""
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, r.read(), r.headers.get_content_charset() or "utf-8"
        except urllib.error.HTTPError as e:
            return e.code, None, "utf-8"

    def get(self, url: str) -> str | None:
        p = self._path(url)
        use_cache = p.exists() and not (self.refresh_index and is_listing(url) and not self.offline)
        if use_cache:
            return p.read_text(encoding="utf-8", errors="replace")
        gone = p.with_suffix(".404")
        if gone.exists() and not is_listing(url):
            return None  # 前回 404 だった記事は取り直さない
        if self.offline:
            return None
        if self.robots and not self.robots.can_fetch(USER_AGENT, url):
            print(f"  robots.txt により除外: {url}", file=sys.stderr)
            return None
        raw, charset = None, "utf-8"
        for attempt in range(3):
            wait = self.delay - (time.time() - self._last)
            if wait > 0:
                self.sleep(wait)
            try:
                status, body, charset = self.transport(url)
            except Exception:
                status, body = None, None
            self._last = time.time()
            if status == 404:
                self.fetched.append(url)
                if not is_listing(url):
                    gone.touch()
                return None
            if status == 200 and body is not None:
                raw = body
                break
            self.sleep(5 * (attempt + 1))
        self.fetched.append(url)
        if raw is None:
            print(f"  取得失敗: {url}", file=sys.stderr)
            # 取り直しに失敗した一覧ページは、古いキャッシュがあればそれを使う
            return p.read_text(encoding="utf-8", errors="replace") if p.exists() else None
        html = raw.decode(charset, errors="replace")
        p.write_text(html, encoding="utf-8")
        return html


# ---------------------------------------------------------------- 記事の解釈
@dataclass
class Article:
    id: str
    url: str
    title: str
    category: str | None
    published: str | None
    mp3s: list[tuple[str, str]] = field(default_factory=list)  # (url, link text)
    stream_only: list[str] = field(default_factory=list)


DATE_RE = re.compile(r"(\d{4})年(\d{1,2})月(\d{1,2})日")
TIME_RE = re.compile(r"\bat\s+(\d{1,2}):(\d{2})")


def extract_article(art_id: str, url: str, page: PageParser, blog_name_hint: str) -> Article:
    # タイトル: og:title → <title> から末尾のブログ名（「 : CMSL …」「: CMSL …」「 - CMSL …」など）を除去
    title = (page.og_title or page.title).strip()
    if blog_name_hint:
        title = re.sub(rf"\s*(?:[:：|]|\s-)\s*{re.escape(blog_name_hint)}\b.*$", "", title)
    title = unescape(" ".join(title.split()))

    # カテゴリ: 「posted by」の直後に現れる /category/ リンク
    category = None
    seen_posted = False
    date = None
    hhmm = None
    for ev in page.events:
        if ev[0] == "text":
            t = ev[1]
            if date is None and (m := DATE_RE.search(t)):
                date = m.groups()
            if "posted by" in t.lower():
                seen_posted = True
                if (m := TIME_RE.search(t)):
                    hhmm = m.groups()
            elif seen_posted and hhmm is None and (m := TIME_RE.search(t)):
                hhmm = m.groups()
        elif ev[0] == "link":
            path = urllib.parse.urlsplit(urllib.parse.urljoin(url, ev[1])).path
            if seen_posted and category is None and CATEGORY_RE.match(path) and ev[2]:
                category = ev[2]
            if seen_posted and (m := TIME_RE.search(ev[2] or "")) and hhmm is None:
                hhmm = m.groups()

    published = None
    if date:
        y, mo, d = (int(x) for x in date)
        h, mi = (int(x) for x in hhmm) if hhmm else (0, 0)
        published = f"{y:04d}-{mo:02d}-{d:02d}T{h:02d}:{mi:02d}:00+09:00"

    # mp3: <a href="...mp3"> はダウンロード用（ライセンス対象）。
    # <audio>/<embed> のみの音源はストリーミング用で、ライセンス対象外なので除外。
    linked, seen = [], set()
    for ev in page.events:
        if ev[0] == "link":
            u = normalize(ev[1], url)
            if u and MP3_RE.match(u) and u not in seen:
                seen.add(u)
                linked.append((u, ev[2]))
    stream_only = []
    for ev in page.events:
        if ev[0] == "media":
            u = normalize(ev[1], url)
            if u and MP3_RE.match(u) and u not in seen and u not in stream_only:
                stream_only.append(u)

    return Article(art_id, url, title, category, published, linked, stream_only)


# ---------------------------------------------------------------- タイトルの解釈
# 例: ベートーヴェン：ピアノソナタ 第8番 ハ短調 Op.13 「悲愴」 第2楽章 [2020][AR]
#     / L.V.Beethoven:Piano Sonata No.8 C minor, Op.13 "Grande Sonate pathetique":II. Adagio cantabile
TAG_RE = re.compile(r"\[([^\]]+)\]")
_NUM = r"[0-9０-９一二三四五六七八九十]+"
# 「第1楽章」のほか、複数の楽章をまとめた「第1,2楽章」「第3-第4楽章」「第1楽章・第2楽章」「第3, 4楽章」
_MULTI = rf"第\s*{_NUM}(?:\s*楽章)?(?:\s*[,，、・\-－~〜]\s*(?:第\s*)?{_NUM}(?:\s*楽章)?)*"
MOVEMENT_RES = [
    re.compile(rf"\s*({_MULTI}\s*楽章.*)$"),
    re.compile(rf"\s*(第\s*{_NUM}\s*(?:曲|幕|場|部|変奏)\b.*)$"),
]


EN_SEPARATOR_RE = re.compile(r"\s+/\s+|\s*[/／]\s*(?=[A-Z\"“'])")


def slug(s: str) -> str:
    out = re.sub(r"[^\w]+", "-", s.lower(), flags=re.UNICODE).strip("-")
    return out or hashlib.sha1(s.encode()).hexdigest()[:8]  # 記号だけの名前は元の文字列のハッシュ


def parse_title(title: str) -> dict:
    ja, en = title, ""
    # 日本語 / 英語の区切り。「 / 」のほか、空白が欠けた「]/ 」「 /Antonio」もある。空白のない「/」は後ろが大文字で
    # 始まる語のときだけ区切る（「モルダウ/クラシック名曲集」のような日本語どうしの「/」は区切らない）
    if (m := EN_SEPARATOR_RE.search(title)):
        ja, en = title[: m.start()], title[m.end():]
        if not re.match(r"[A-Za-z\"“'.]", en):
            en = ""  # 「革命 / シューベルト：即興曲」のように、2 曲目の日本語タイトルが続く記事
    tags = TAG_RE.findall(ja)
    ja = TAG_RE.sub("", ja).strip()

    composer, rest = None, ja
    m = re.match(r"^\s*([^：:「」]{1,30}?)\s*[：:]\s*(.+)$", ja)
    if m:
        composer, rest = m.group(1).strip(), m.group(2).strip()

    composer_en = None
    if en:
        me = re.match(r"^\s*([^:：]{1,40}?)\s*[:：]", en)
        if me:
            composer_en = me.group(1).strip()

    work, movement = rest, None
    for r in MOVEMENT_RES:
        mm = r.search(rest)
        if mm and mm.start() > 0:
            movement = mm.group(1).strip()
            work = rest[: mm.start()].strip()
            break
    work = work.rstrip(" :：-")

    return {
        "composer": composer,
        "composerEn": composer_en,
        "work": work,
        "movement": movement,
        "titleEn": en.strip() or None,
        "tags": tags,
    }


# ---------------------------------------------------------------- 複数の作品をまとめた記事
# 例: 「オンライン特別プログラム - ブラームス 交響曲第4番 バーチャルコンサート」には、交響曲の 4 楽章のほかに
# アンコール（Faure-Pavane-2020-IR.mp3）も入っている。記事のタイトルでは曲ごとの作曲家・作品が分からないので、
# ファイル名「作曲家-作品-楽章-録音年-種別.mp3」から解釈し、作曲家と作品名は通常の記事の同じ曲に合わせる。
COMPILATION_RE = re.compile(r"^オンライン特別プログラム|バーチャルコンサート")
_ORDINAL_RE = re.compile(r"^(\d+)(?:st|nd|rd|th)$", re.I)
_YEAR_RE = re.compile(r"^(?:19|20)\d\d$")


def ascii_key(s: str) -> str:
    """ファイル名の語の比較用（アクセント記号を外して小文字に。Dvořák → dvorak）。"""
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c)).lower()


def parse_file_name(file_name: str) -> dict:
    """mp3 のファイル名を語に分ける。

    `064-Tchaikovsky-Symphony-No6-4th.mp3` → composer "tchaikovsky", work "symphony-no6", movements [4]
    `Beethoven-Symphony-No5-3rd-4th-2020-IR.mp3` → movements [3, 4], tags ["2020", "IR"]
    """
    stem = re.sub(r"\.mp3$", "", file_name, flags=re.I)
    stem = re.sub(r"^\d{3}-", "", stem)  # 通し番号（064-…）
    words = [w for w in re.split(r"[-_\s]+", stem.replace("SymNo", "Symphony-No").replace("No.", "No")) if w]
    tags: list[str] = []
    for i, w in enumerate(words):
        if _YEAR_RE.match(w):
            tags = words[i:]
            words = words[:i]
            break
    movements = []
    while words and (m := _ORDINAL_RE.match(words[-1])):
        movements.insert(0, int(m.group(1)))
        words.pop()
    return {
        "composer": ascii_key(words[0]) if words else None,
        "work": ascii_key("-".join(words[1:])),
        "workWords": words[1:],
        "movements": movements,
        "tags": tags,
    }


_VARIANT_RE = re.compile(r"(?<![A-Za-z])(AR|VR|IR)(?![A-Za-z])")


def recording_key(tags: list[str], file_name: str) -> str:
    """同じ作品の別録音を区別するキー。「年 + 種別（AR/VR/IR）」をタグ、なければファイル名から取る。

    `["2019", "AR"]` → "2019 AR"、`Beethoven-Symphony-No5-1st-2015.mp3` → "2015"、年も種別もなければ ""。
    """
    joined = " ".join(tags)
    pf = parse_file_name(file_name)
    year = re.search(r"(?:19|20)\d\d", joined) or re.search(r"(?:19|20)\d\d", " ".join(pf["tags"]))
    variant = _VARIANT_RE.search(joined) or _VARIANT_RE.search(" ".join(pf["tags"]))
    return " ".join(x.group(0) for x in (year, variant) if x)


def is_compilation(art: Article) -> bool:
    """曲ごとに作曲家・作品が違う記事。タイトル（オンライン特別プログラム）か、ファイル名の作曲家が 2 人以上いるもの。"""
    if COMPILATION_RE.search(art.title):
        return True
    composers = {parse_file_name(file_name_of(u))["composer"] for u, _ in art.mp3s}
    return len(composers) > 1


def movement_label(numbers: list[int]) -> str | None:
    if not numbers:
        return None
    if len(numbers) == 1:
        return f"第{numbers[0]}楽章"
    return f"第{numbers[0]}楽章-第{numbers[-1]}楽章"


# ---------------------------------------------------------------- 巡回
def crawl(fetcher: Fetcher, max_pages: int | None, verbose: bool):
    queue = deque([BASE])
    seen = {BASE}
    pages: dict[str, PageParser] = {}
    category_listing: dict[str, set[str]] = defaultdict(set)  # category name -> article ids
    category_names: dict[str, str] = {}  # category id -> name

    n = 0
    while queue:
        url = queue.popleft()
        if max_pages and n >= max_pages:
            break
        html = fetcher.get(url)
        n += 1
        if html is None:
            continue
        page = parse(html)
        pages[url] = page
        if verbose and n % 25 == 0:
            print(f"  {n} ページ取得 / キュー残り {len(queue)}", file=sys.stderr)

        path = urllib.parse.urlsplit(url).path
        cat_m = CATEGORY_RE.match(path)
        for ev in page.events:
            if ev[0] != "link":
                continue
            u = normalize(ev[1], url)
            if not u or urllib.parse.urlsplit(u).netloc != HOST:
                continue
            p = urllib.parse.urlsplit(u).path
            if (cm := CATEGORY_RE.match(p)) and ev[2] and cm.group(2) == "1":
                category_names.setdefault(cm.group(1), ev[2])
            if cat_m and (am := ARTICLE_RE.match(p)):
                category_listing[cat_m.group(1)].add(am.group(1))
            if u not in seen and any(r.match(p) for r in FOLLOW_RES):
                seen.add(u)
                queue.append(u)
    return pages, category_listing, category_names


# ---------------------------------------------------------------- 設定ファイル
HERE = Path(__file__).resolve().parent
OTHER_GENRE_ID = "other"
UNKNOWN_COMPOSER = "作曲者不明"


def load_json(path: Path | None, default):
    if path is None or not Path(path).exists():
        return default
    return json.loads(Path(path).read_text(encoding="utf-8"))


class GenreMap:
    """CMSL のカテゴリ名 → 表示ジャンル（genre_map.json。SPEC §3.3）。

    1. categories: カテゴリ名の完全一致
    2. rules: 上から順に、contains のいずれかを部分一致（大文字小文字を区別しない）
    3. どれにも当たらなければ default（「その他」）
    """

    def __init__(self, data: dict):
        self.genres = {g["id"]: g for g in data.get("genres", [])}
        self.categories = data.get("categories", {})
        self.rules = data.get("rules", [])
        self.default = data.get("default", OTHER_GENRE_ID)
        if self.default not in self.genres:
            self.genres[self.default] = {"id": self.default, "name": "その他", "nameEn": "Other", "order": 999}
        for gid in list(self.categories.values()) + [r["genre"] for r in self.rules]:
            if gid not in self.genres:
                raise ValueError(f"genre_map.json: 未定義のジャンル ID {gid!r}")

    def resolve(self, category: str | None) -> tuple[str, bool]:
        """(genre id, マップで解決できたか)"""
        if category is None:
            return self.default, False
        if category in self.categories:
            return self.categories[category], True
        low = category.lower()
        for r in self.rules:
            if any(c.lower() in low for c in r["contains"]):
                return r["genre"], True
        return self.default, False


class Overrides:
    """手動補正（overrides.json。SPEC §3.4）。"""

    def __init__(self, data: dict):
        self.exclude = set(map(str, data.get("exclude", [])))
        self.tracks = {str(k): v for k, v in data.get("tracks", {}).items()}
        self.aliases = data.get("composerAliases", {})
        self.composer_en = data.get("composerEn", {})

    def canonical_composer(self, name: str | None) -> str | None:
        if name is None:
            return None
        return self.aliases.get(name, name)


def file_name_of(mp3_url: str) -> str:
    return urllib.parse.unquote(mp3_url.rsplit("/", 1)[-1])


def track_id(art: Article, index: int) -> str:
    """記事 ID 由来で恒久（SPEC §3.2）。1 記事に複数の mp3 があれば「記事ID-連番」。"""
    return f"{art.id}-{index}" if len(art.mp3s) > 1 else art.id


def work_slug(title: str) -> str:
    """作品の ID。空白の有無（「交響曲 第5番」と「交響曲第5番」）や全角・半角の違いは同じ作品として扱う。"""
    return slug(re.sub(r"\s+", "", unicodedata.normalize("NFKC", title)))


def split_en(title: str) -> str:
    """タイトルの日本語の部分（英語タイトルの前まで）。"""
    m = EN_SEPARATOR_RE.search(title)
    return title[: m.start()] if m else title


def track_title(art: Article, fname: str, info: dict, composer: str | None) -> str:
    base = split_en(art.title)
    if is_compilation(art) and composer not in (None, UNKNOWN_COMPOSER):
        # まとめ記事の曲は、通常の記事と同じ「作曲家：作品 楽章 [年][種別]」の形にする
        parts = [f"{composer}：{info['work']}"] + ([info["movement"]] if info["movement"] else [])
        return " ".join(parts) + "".join(f"[{t}]" for t in info["tags"])
    return base if len(art.mp3s) == 1 else f"{base} ({fname})"


# ---------------------------------------------------------------- 組み立て
def build(pages, category_listing, category_names, exclude: list[str],
          genre_map: GenreMap | None = None, overrides: Overrides | None = None):
    genre_map = genre_map or GenreMap({})
    overrides = overrides or Overrides({})

    # 全ページに共通して出る記事リンク（サイドバーの「最近の記事」等）はカテゴリ判定に使わない
    link_freq = Counter()
    for page in pages.values():
        ids = {m.group(1) for ev in page.events if ev[0] == "link"
               and (m := ARTICLE_RE.match(urllib.parse.urlsplit(ev[1]).path))}
        link_freq.update(ids)
    sidebar = {a for a, c in link_freq.items() if c >= max(5, len(pages) * 0.5)}

    article_to_cats: dict[str, set[str]] = defaultdict(set)
    for cat_id, ids in category_listing.items():
        name = category_names.get(cat_id)
        if name:
            for a in ids - sidebar:
                article_to_cats[a].add(name)

    blog_hint = "CMSL"
    articles: list[Article] = []
    for url, page in pages.items():
        m = ARTICLE_RE.match(urllib.parse.urlsplit(url).path)
        if not m:
            continue
        art = extract_article(m.group(1), url, page, blog_hint)
        if art.category is None and len(article_to_cats.get(art.id, ())) == 1:
            art.category = next(iter(article_to_cats[art.id]))
        articles.append(art)

    def excluded(cat):
        return cat is not None and any(x.lower() in cat.lower() for x in exclude)

    report = {"noMp3": [], "streamOnly": [], "noComposer": [], "noCategory": [], "excluded": 0,
              "excludedByOverride": [], "duplicateAudio": [], "categories": {}, "unmappedCategories": {}}
    category_count: Counter = Counter()
    unmapped: Counter = Counter()

    # 1 周目: 記事ごとに解釈し、作曲家の英語表記を集める。複数の作品をまとめた記事は 2 周目で曲ごとに解釈する
    entries = []  # 曲ごと: (記事, 連番, mp3, ファイル名, 解釈, 作曲家, ジャンル)
    compilations = []
    en_votes: dict[str, Counter] = defaultdict(Counter)

    def resolve_genre(art, ov) -> str:
        if "genre" in ov:
            if ov["genre"] not in genre_map.genres:
                raise ValueError(f"overrides.json: 記事 {art.id} の未定義のジャンル ID {ov['genre']!r}")
            return ov["genre"]
        gid, mapped = genre_map.resolve(art.category)
        if art.category is not None and not mapped:
            unmapped[art.category] += len(art.mp3s)
        return gid

    def apply(info, ov):
        for key in ("composer", "composerEn", "work", "movement"):
            if key in ov:
                info[key] = ov[key]

    for art in sorted(articles, key=lambda a: (a.published or "", a.id)):
        if excluded(art.category):
            report["excluded"] += 1
            continue
        if art.id in overrides.exclude:
            report["excludedByOverride"].append(art.id)
            continue
        if not art.mp3s:
            (report["streamOnly"] if art.stream_only else report["noMp3"]).append(art.url)
            continue
        if art.category is None:
            report["noCategory"].append(art.url)
        else:
            category_count[art.category] += len(art.mp3s)
        ov = overrides.tracks.get(art.id, {})
        gid = resolve_genre(art, ov)
        if art.category is not None:
            report["categories"][art.category] = gid
        if is_compilation(art) and "composer" not in ov:
            compilations.append((art, gid))
            continue
        info = parse_title(art.title)
        apply(info, ov)
        composer = overrides.canonical_composer(info["composer"])
        if composer is not None and info["composerEn"]:
            en_votes[composer][info["composerEn"]] += 1
        for i, (mp3, _) in enumerate(art.mp3s):
            fname = file_name_of(mp3)
            track_info = info
            if len(art.mp3s) > 1 and "movement" not in ov and (numbers := parse_file_name(fname)["movements"]):
                # 「第1,2楽章」の記事に mp3 が 2 つ → 1st / 2nd をそれぞれの楽章にする
                track_info = {**info, "movement": movement_label(numbers)}
            entries.append([art, i, mp3, fname, track_info, composer, gid])

    # 2 周目: まとめ記事の曲を、ファイル名と通常の記事の対応（作曲家の語 → 作曲家、作品の語 → 作品名・ジャンル）で解釈する
    composer_by_word: dict[str, Counter] = defaultdict(Counter)
    work_by_words: dict[tuple, Counter] = defaultdict(Counter)
    for _, _, _, fname, info, composer, gid in entries:
        if composer is None:
            continue
        pf = parse_file_name(fname)
        composer_by_word[pf["composer"]][composer] += 1
        work_by_words[(composer, pf["work"])][(info["work"], gid)] += 1
    alias_by_word = {ascii_key(k): v for k, v in overrides.aliases.items()}
    for art, article_gid in compilations:
        for i, (mp3, _) in enumerate(art.mp3s):
            fname = file_name_of(mp3)
            pf = parse_file_name(fname)
            composer = alias_by_word.get(pf["composer"])
            if composer is None and composer_by_word[pf["composer"]]:
                composer = composer_by_word[pf["composer"]].most_common(1)[0][0]
            titled = parse_title(art.title)
            title_composer = overrides.canonical_composer(titled["composer"])
            if composer is None and title_composer is not None:
                # 通常の記事にない語でも、タイトルの作曲家の英語名の姓と一致すれば、その作曲家（Elgar-… → エルガー）
                names_en = [titled["composerEn"], overrides.composer_en.get(title_composer), *en_votes[title_composer]]
                if any(n and ascii_key(re.split(r"[\s.]+", n.strip())[-1]) == pf["composer"] for n in names_en):
                    composer = title_composer
            gid = article_gid
            info = {"composer": composer, "composerEn": None, "work": " ".join(pf["workWords"]) or art.title,
                    "movement": movement_label(pf["movements"]), "titleEn": None, "tags": pf["tags"]}
            if composer is not None and work_by_words[(composer, pf["work"])]:
                info["work"], gid = work_by_words[(composer, pf["work"])].most_common(1)[0][0]
            elif composer is not None and composer == title_composer:
                # 記事の主役の曲（タイトルの作曲家と同じ）は、タイトルの作品名・楽章・英語タイトルを使う
                info.update({k: titled[k] for k in ("work", "movement", "titleEn", "tags", "composerEn")})
            entries.append([art, i, mp3, fname, info, composer, gid])

    # 同じ mp3 を再掲した記事（紹介記事・再投稿）は、最初に公開された記事の曲だけ残す
    entries.sort(key=lambda e: (e[0].published or "", e[0].id, e[1]))  # 公開順、記事内は mp3 の順
    first_by_audio: dict[str, str] = {}
    kept = []
    for e in entries:
        tid = track_id(e[0], e[1])
        if e[2] in first_by_audio:
            report["duplicateAudio"].append({"id": tid, "sameAs": first_by_audio[e[2]]})
            continue
        first_by_audio[e[2]] = tid
        kept.append(e)
    entries = kept

    # 曲ごとの上書き（キーが曲 ID。まとめ記事の曲は「記事ID-連番」で 1 曲ずつ直せる）。記事単位の上書きは適用済みで、
    # 同じ内容をもう一度当てても変わらない
    for e in entries:
        art, i = e[0], e[1]
        tid = track_id(art, i)
        if tid in overrides.tracks:
            ov = overrides.tracks[tid]
            e[4] = info = dict(e[4])
            apply(info, ov)
            e[5] = overrides.canonical_composer(info["composer"])
            if "genre" in ov:
                e[6] = resolve_genre(art, ov)
        if e[5] is None:
            if art.url not in report["noComposer"]:
                report["noComposer"].append(art.url)
            e[5] = UNKNOWN_COMPOSER

    # 作曲家: 日本語の正規名でまとめる。英語表記は overrides → タイトルで最も多い表記
    composers: dict[str, dict] = {}
    composer_id: dict[str, str] = {}
    used_ids: set[str] = set()
    for e in entries:
        composer = e[5]
        if composer in composer_id:
            continue
        name_en = overrides.composer_en.get(composer)
        if name_en is None and en_votes[composer]:
            name_en = en_votes[composer].most_common(1)[0][0]
        cid = base = slug(name_en or composer)
        n = 2
        while cid in used_ids:  # 英語表記が同じ別人（または別表記）を区別する
            cid = f"{base}-{n}"
            n += 1
        used_ids.add(cid)
        composer_id[composer] = cid
        composers[cid] = {"id": cid, "name": composer, "nameEn": name_en, "trackCount": 0}

    # 作品: 作曲家 + 作品名 + 録音でまとめる（別録音が混ざると「作品を通して再生」で同じ楽章が続くため）。
    # 録音のキーは記事単位（1 記事の mp3 は同じ録音。カラオケ版などのタグの違いで分けない）
    def recording_of(e) -> str:
        art, _, mp3, fname, info = e[:5]
        if len(art.mp3s) > 1 and not is_compilation(art):
            return recording_key(parse_title(art.title)["tags"], file_name_of(art.mp3s[0][0]))
        return recording_key(info["tags"], fname)  # まとめ記事は曲ごと（過去の録音が載っていることがある）

    recordings: dict[tuple, set] = defaultdict(set)
    for e in entries:
        recordings[(e[5], work_slug(e[4]["work"]))].add(recording_of(e))

    genres: dict[str, dict] = {}
    works: dict[str, dict] = {}
    tracks = []
    for e in entries:
        art, i, mp3, fname, info, composer, gid = e
        cid = composer_id[composer]
        g = genre_map.genres[gid]
        genres.setdefault(gid, {"id": gid, "name": g["name"], "nameEn": g.get("nameEn"),
                                "order": g.get("order", 999), "trackCount": 0})
        rec = recording_of(e)
        wid = f"{cid}--{work_slug(info['work'])}" + (f"--{slug(rec)}" if rec else "")
        title = info["work"]
        if len(recordings[(composer, work_slug(info["work"]))]) > 1 and rec:
            title = f"{title}（{rec}）"  # 同じ作品名の録音が複数あるときだけ、録音を書き添える
        works.setdefault(wid, {"id": wid, "title": title, "composerId": cid, "genreId": gid})

        tracks.append({
            "id": track_id(art, i),
            "title": track_title(art, fname, info, composer),
            "titleEn": info["titleEn"],
            "composerId": cid,
            "workId": wid,
            "genreId": gid,
            "movement": info["movement"] or None,
            "tags": info["tags"],
            "audioUrl": mp3,
            "fileName": fname,
            "articleUrl": art.url,
            "publishedAt": art.published,
            "durationSec": None,  # 将来拡張（SPEC §3.2）。キーは常に出力する
        })
        genres[gid]["trackCount"] += 1
        composers[cid]["trackCount"] += 1

    total = len(tracks) or 1
    report["unmappedCategories"] = dict(unmapped.most_common())
    report["categoryTrackCounts"] = dict(category_count.most_common())
    report["ratios"] = {
        "otherGenre": round(genres.get(genre_map.default, {}).get("trackCount", 0) / total, 4),
        "unknownComposer": round(composers.get(composer_id.get(UNKNOWN_COMPOSER, ""), {}).get("trackCount", 0) / total, 4),
    }

    catalog = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": BASE,
        "license": {
            "name": "Creative Commons 表示 2.1 日本",
            "credit": "CMSL クラシック名曲サウンドライブラリー",
            "url": BASE,
            "notes": "フリー素材・商用利用可・許諾不要（著作権は制作者に帰属）。ストリーミング専用音源は対象外のため除外済み。",
        },
        "genres": sorted(genres.values(), key=lambda g: (g["order"], g["id"])),
        "composers": sorted(composers.values(), key=lambda c: (-c["trackCount"], c["id"])),
        "works": list(works.values()),
        "tracks": tracks,
    }
    return catalog, report


def main(argv=None):
    ap = argparse.ArgumentParser(description="CMSL カタログ(catalog.json)を作成")
    ap.add_argument("-o", "--output", default="catalog.json")
    ap.add_argument("--cache", default="cache", help="HTMLキャッシュの保存先")
    ap.add_argument("--delay", type=float, default=1.5, help="リクエスト間隔(秒)。サーバー負荷軽減のため1秒以上推奨")
    ap.add_argument("--max-pages", type=int, default=None, help="取得ページ数の上限（お試し用）")
    ap.add_argument("--offline", action="store_true", help="キャッシュのみで作成（通信しない）")
    ap.add_argument("--refresh-index", action="store_true",
                    help="一覧ページ（トップ・カテゴリ・アーカイブ）だけ取り直す。記事はキャッシュを使う（差分巡回）")
    ap.add_argument("--exclude-category", action="append", default=[], help="除外するカテゴリ名（部分一致）")
    ap.add_argument("--genre-map", default=str(HERE / "genre_map.json"), help="カテゴリ → 表示ジャンルの対応表")
    ap.add_argument("--overrides", default=str(HERE / "overrides.json"), help="手動補正ファイル")
    ap.add_argument("-q", "--quiet", action="store_true")
    args = ap.parse_args(argv)

    if args.delay < MIN_DELAY:
        ap.error(f"--delay は {MIN_DELAY} 秒以上にしてください")
    fetcher = Fetcher(Path(args.cache), args.delay, args.offline, args.refresh_index)
    print("巡回を開始します…", file=sys.stderr)
    pages, listing, names = crawl(fetcher, args.max_pages, not args.quiet)
    genre_map = GenreMap(load_json(Path(args.genre_map), {}))
    overrides = Overrides(load_json(Path(args.overrides), {}))
    catalog, report = build(pages, listing, names, DEFAULT_EXCLUDE + args.exclude_category, genre_map, overrides)

    Path(args.output).write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    Path(args.output).with_suffix(".report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"\n完了: {args.output}", file=sys.stderr)
    print(f"  取得ページ   {len(pages)}（うち通信 {len(fetcher.fetched)}）", file=sys.stderr)
    print(f"  曲           {len(catalog['tracks'])}", file=sys.stderr)
    print(f"  ジャンル     {len(catalog['genres'])}", file=sys.stderr)
    print(f"  作曲家       {len(catalog['composers'])}", file=sys.stderr)
    print(f"  作品         {len(catalog['works'])}", file=sys.stderr)
    print(f"  除外(クラシック以外) {report['excluded']} / mp3なし {len(report['noMp3'])}"
          f" / ストリーミングのみ {len(report['streamOnly'])}"
          f" / 作曲家不明 {len(report['noComposer'])} / カテゴリ不明 {len(report['noCategory'])}",
          file=sys.stderr)
    print(f"  比率: その他 {report['ratios']['otherGenre']:.1%} / 作曲者不明 {report['ratios']['unknownComposer']:.1%}"
          f" / マップにないカテゴリ {len(report['unmappedCategories'])}", file=sys.stderr)
    print("  ジャンル内訳:", ", ".join(f"{g['name']}({g['trackCount']})" for g in catalog["genres"]), file=sys.stderr)


if __name__ == "__main__":
    main()
