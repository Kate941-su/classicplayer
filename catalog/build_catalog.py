#!/usr/bin/env python3
"""
CMSL クラシック名曲サウンドライブラリー カタログ作成スクリプト

https://classical-sound.seesaa.net/ を巡回し、ダウンロード可能な mp3 を
ジャンル（ブログのカテゴリ）・作曲家・作品ごとに整理した catalog.json を作ります。

使い方:
    python3 build_catalog.py                 # 全体を巡回して catalog.json を出力
    python3 build_catalog.py --max-pages 50  # お試し（50ページで打ち切り）
    python3 build_catalog.py --offline       # キャッシュ済みHTMLだけで作り直す

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
class Fetcher:
    def __init__(self, cache_dir: Path, delay: float, offline: bool):
        self.cache_dir = cache_dir
        self.delay = delay
        self.offline = offline
        self._last = 0.0
        cache_dir.mkdir(parents=True, exist_ok=True)
        self.robots = urllib.robotparser.RobotFileParser()
        if not offline:
            try:
                self.robots.set_url(urllib.parse.urljoin(BASE, "/robots.txt"))
                self.robots.read()
            except Exception:
                self.robots = None  # 読めない場合は制限なしとして扱う

    def _path(self, url: str) -> Path:
        return self.cache_dir / (hashlib.sha1(url.encode()).hexdigest() + ".html")

    def get(self, url: str) -> str | None:
        p = self._path(url)
        if p.exists():
            return p.read_text(encoding="utf-8", errors="replace")
        if self.offline:
            return None
        if self.robots and not self.robots.can_fetch(USER_AGENT, url):
            print(f"  robots.txt により除外: {url}", file=sys.stderr)
            return None
        wait = self.delay - (time.time() - self._last)
        if wait > 0:
            time.sleep(wait)
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        raw = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=30) as r:
                    raw = r.read()
                    charset = r.headers.get_content_charset() or "utf-8"
                break
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    self._last = time.time()
                    return None
                time.sleep(5 * (attempt + 1))
            except Exception:
                time.sleep(5 * (attempt + 1))
        self._last = time.time()
        if raw is None:
            print(f"  取得失敗: {url}", file=sys.stderr)
            return None
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
    # タイトル: og:title → <title> から「 : ブログ名」等を除去
    title = (page.og_title or page.title).strip()
    for sep in (" : ", "：CMSL", ": CMSL", " - ", " | "):
        if blog_name_hint and sep in title and blog_name_hint in title.split(sep)[-1]:
            title = sep.join(title.split(sep)[:-1]).strip()
            break
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
MOVEMENT_RES = [
    re.compile(r"\s*(第\s*[0-9０-９一二三四五六七八九十]+\s*楽章.*)$"),
    re.compile(r"\s*(第\s*[0-9０-９一二三四五六七八九十]+\s*(?:曲|幕|場|部|変奏)\b.*)$"),
]


def slug(s: str) -> str:
    s = re.sub(r"[^\w]+", "-", s.lower(), flags=re.UNICODE).strip("-")
    return s or hashlib.sha1(s.encode()).hexdigest()[:8]


def parse_title(title: str) -> dict:
    ja, en = title, ""
    if " / " in title:
        ja, en = title.split(" / ", 1)
    elif "／" in title:
        ja, en = title.split("／", 1)
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

    return {
        "composer": composer,
        "composerEn": composer_en,
        "work": work,
        "movement": movement,
        "titleEn": en.strip() or None,
        "tags": tags,
    }


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


def build(pages, category_listing, category_names, exclude: list[str]):
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

    genres, composers, works, tracks = {}, {}, {}, []
    report = {"noMp3": [], "streamOnly": [], "noComposer": [], "noCategory": [], "excluded": 0}

    for art in sorted(articles, key=lambda a: (a.published or "", a.id)):
        if excluded(art.category):
            report["excluded"] += 1
            continue
        if not art.mp3s:
            (report["streamOnly"] if art.stream_only else report["noMp3"]).append(art.url)
            continue
        info = parse_title(art.title)
        genre_name = art.category or "その他"
        if art.category is None:
            report["noCategory"].append(art.url)
        gid = slug(genre_name)
        genres.setdefault(gid, {"id": gid, "name": genre_name, "trackCount": 0})

        comp_name = info["composer"] or "作曲者不明"
        if info["composer"] is None:
            report["noComposer"].append(art.url)
        cid = slug(info["composerEn"] or comp_name)
        composers.setdefault(cid, {"id": cid, "name": comp_name, "nameEn": info["composerEn"], "trackCount": 0})

        wid = f"{cid}--{slug(info['work'])}"
        works.setdefault(wid, {"id": wid, "title": info["work"], "composerId": cid, "genreId": gid})

        for i, (mp3, link_text) in enumerate(art.mp3s):
            fname = urllib.parse.unquote(mp3.rsplit("/", 1)[-1])
            tracks.append({
                "id": f"{art.id}-{i}" if len(art.mp3s) > 1 else art.id,
                "title": art.title.split(" / ")[0] if len(art.mp3s) == 1 else f"{art.title.split(' / ')[0]} ({fname})",
                "titleEn": info["titleEn"],
                "composerId": cid,
                "workId": wid,
                "genreId": gid,
                "movement": info["movement"],
                "tags": info["tags"],
                "audioUrl": mp3,
                "fileName": fname,
                "articleUrl": art.url,
                "publishedAt": art.published,
            })
            genres[gid]["trackCount"] += 1
            composers[cid]["trackCount"] += 1

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
        "genres": sorted(genres.values(), key=lambda g: -g["trackCount"]),
        "composers": sorted(composers.values(), key=lambda c: -c["trackCount"]),
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
    ap.add_argument("--exclude-category", action="append", default=[], help="除外するカテゴリ名（部分一致）")
    ap.add_argument("-q", "--quiet", action="store_true")
    args = ap.parse_args(argv)

    fetcher = Fetcher(Path(args.cache), args.delay, args.offline)
    print("巡回を開始します…", file=sys.stderr)
    pages, listing, names = crawl(fetcher, args.max_pages, not args.quiet)
    catalog, report = build(pages, listing, names, DEFAULT_EXCLUDE + args.exclude_category)

    Path(args.output).write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    Path(args.output).with_suffix(".report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"\n完了: {args.output}", file=sys.stderr)
    print(f"  取得ページ   {len(pages)}", file=sys.stderr)
    print(f"  曲           {len(catalog['tracks'])}", file=sys.stderr)
    print(f"  ジャンル     {len(catalog['genres'])}", file=sys.stderr)
    print(f"  作曲家       {len(catalog['composers'])}", file=sys.stderr)
    print(f"  作品         {len(catalog['works'])}", file=sys.stderr)
    print(f"  除外(クラシック以外) {report['excluded']} / mp3なし {len(report['noMp3'])}"
          f" / ストリーミングのみ {len(report['streamOnly'])}"
          f" / 作曲家不明 {len(report['noComposer'])} / カテゴリ不明 {len(report['noCategory'])}",
          file=sys.stderr)
    print("  ジャンル内訳:", ", ".join(f"{g['name']}({g['trackCount']})" for g in catalog["genres"]), file=sys.stderr)


if __name__ == "__main__":
    main()
