#!/usr/bin/env python3
"""
作曲家の肖像画を Wikidata / Wikimedia Commons から取得し、アプリ用のサムネイルを作る。

流れ:
  1. catalog.json の作曲家ごとに Wikidata の項目（QID）を対応づける
     - composer_wikidata.json にある対応を優先する（人が確認したもの = verified: true）
     - ないものは Wikidata を検索して「作曲家である人物」の候補を提案し、verified: false で追記する
  2. QID の画像プロパティ（P18）から Commons のファイルを取得する
  3. Commons のライセンス情報を確認し、PD / CC0 / CC BY / CC BY-SA だけを採用する
  4. 正方形に切り抜き、512px の JPEG（メタデータなし）として site/v1/portraits/ に保存する
  5. portraits.json（クレジット情報）と portraits.report.json を出力する。
     catalog.json の composers[].portrait は build_catalog.py が portraits.json から埋める（SPEC §3.6）

使い方（catalog/ で実行する。手順は docs/catalog-pipeline.md）:
  uv run --python 3.12 --with-requirements requirements-dev.txt python fetch_portraits.py --catalog ../site/v1/catalog.json --suggest-only
  # → composer_wikidata.json を確認して、正しい候補を verified: true にする（人が確認する）
  uv run --python 3.12 --with-requirements requirements-dev.txt python fetch_portraits.py --catalog ../site/v1/catalog.json \
      --contact https://github.com/kate941-su/classicplayer
  # → tool/update-catalog.sh --offline でカタログに反映する

依存: Pillow
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from html import unescape
from pathlib import Path
from typing import Callable

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
# Wikimedia の User-Agent ポリシーに従い、連絡先を含める（--contact で上書きする）
DEFAULT_CONTACT = "set-your-contact@example.com"
Q_HUMAN = "Q5"
Q_COMPOSER = "Q36834"
COMPOSER_OCCUPATIONS = {Q_COMPOSER}

# 採用するライセンス（Commons の extmetadata.License の値）
ALLOWED_LICENSE_RE = re.compile(r"^(pd|cc0|cc-by-[0-9.]+|cc-by-sa-[0-9.]+)$", re.I)
ATTRIBUTION_FREE_RE = re.compile(r"^(pd|cc0)$", re.I)

SIZE = 512
DEFAULT_FOCUS = (0.5, 0.35)  # 肖像画は顔が上寄りのことが多い

HttpGet = Callable[[str], bytes]


# ---------------------------------------------------------------- HTTP
class Http:
    """1リクエスト/秒、応答をディスクにキャッシュする。"""

    def __init__(self, cache_dir: Path, contact: str, delay: float = 1.0):
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.ua = f"CMSLClassicalPlayer-PortraitFetcher/1.0 ({contact})"
        self.delay = delay
        self._last = 0.0

    def __call__(self, url: str) -> bytes:
        p = self.cache_dir / hashlib.sha1(url.encode()).hexdigest()
        if p.exists():
            return p.read_bytes()
        wait = self.delay - (time.time() - self._last)
        if wait > 0:
            time.sleep(wait)
        req = urllib.request.Request(url, headers={"User-Agent": self.ua})
        last_err = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = r.read()
                break
            except Exception as e:  # noqa: BLE001
                last_err = e
                time.sleep(3 * (attempt + 1))
        else:
            self._last = time.time()
            raise RuntimeError(f"取得失敗: {url}: {last_err}")
        self._last = time.time()
        p.write_bytes(data)
        return data


def api_json(get: HttpGet, base: str, **params) -> dict:
    params.setdefault("format", "json")
    url = base + "?" + urllib.parse.urlencode(params)
    return json.loads(get(url).decode("utf-8"))


# ---------------------------------------------------------------- Wikidata
def claim_ids(entity: dict, prop: str) -> list[str]:
    out = []
    for c in entity.get("claims", {}).get(prop, []):
        v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
        if isinstance(v, dict) and "id" in v:
            out.append(v["id"])
    return out


def claim_strings(entity: dict, prop: str) -> list[str]:
    """P18 などの文字列値。preferred ランクを先頭にする。"""
    claims = entity.get("claims", {}).get(prop, [])
    claims = sorted(claims, key=lambda c: {"preferred": 0, "normal": 1}.get(c.get("rank"), 2))
    out = []
    for c in claims:
        if c.get("rank") == "deprecated":
            continue
        v = c.get("mainsnak", {}).get("datavalue", {}).get("value")
        if isinstance(v, str):
            out.append(v)
    return out


def get_entities(get: HttpGet, qids: list[str]) -> dict[str, dict]:
    out = {}
    for i in range(0, len(qids), 50):
        r = api_json(get, WIKIDATA_API, action="wbgetentities", ids="|".join(qids[i:i + 50]),
                     props="claims|labels", languages="ja|en")
        out.update(r.get("entities", {}))
    return out


def is_composer(entity: dict) -> bool:
    return Q_HUMAN in claim_ids(entity, "P31") and bool(COMPOSER_OCCUPATIONS & set(claim_ids(entity, "P106")))


def search_candidates(get: HttpGet, composer: dict, limit: int = 5) -> list[dict]:
    """英語名 → 日本語名の順に検索し、作曲家である人物だけを返す。"""
    queries = []
    if composer.get("nameEn"):
        queries.append((composer["nameEn"], "en"))
    queries.append((composer["name"], "ja"))
    seen, ids = set(), []
    for q, lang in queries:
        r = api_json(get, WIKIDATA_API, action="wbsearchentities", search=q, language=lang,
                     uselang=lang, type="item", limit=limit)
        for hit in r.get("search", []):
            if hit["id"] not in seen:
                seen.add(hit["id"])
                ids.append(hit["id"])
    if not ids:
        return []
    ents = get_entities(get, ids)
    out = []
    for qid in ids:
        e = ents.get(qid)
        if e and is_composer(e):
            labels = e.get("labels", {})
            out.append({
                "qid": qid,
                "labelJa": labels.get("ja", {}).get("value"),
                "labelEn": labels.get("en", {}).get("value"),
                "hasImage": bool(claim_strings(e, "P18")),
            })
    return out


# ---------------------------------------------------------------- Commons
def strip_html(s: str | None) -> str | None:
    if not s:
        return None
    s = re.sub(r"<[^>]+>", "", s)
    s = " ".join(unescape(s).split())
    return s or None


def commons_info(get: HttpGet, filename: str, width: int = 1024) -> dict | None:
    r = api_json(get, COMMONS_API, action="query", titles=f"File:{filename}", prop="imageinfo",
                 iiprop="url|size|extmetadata", iiurlwidth=str(width))
    pages = r.get("query", {}).get("pages", {})
    for page in pages.values():
        infos = page.get("imageinfo")
        if not infos:
            return None
        ii = infos[0]
        md = {k: (v or {}).get("value") for k, v in (ii.get("extmetadata") or {}).items()}
        return {
            "title": page.get("title"),
            "thumbUrl": ii.get("thumburl") or ii.get("url"),
            "descriptionUrl": ii.get("descriptionurl"),
            "license": (md.get("License") or "").strip().lower() or None,
            "licenseShortName": strip_html(md.get("LicenseShortName")),
            "licenseUrl": md.get("LicenseUrl"),
            "artist": strip_html(md.get("Artist")),
            "credit": strip_html(md.get("Credit")),
            "objectName": strip_html(md.get("ObjectName")),
            "date": strip_html(md.get("DateTimeOriginal")),
            "attributionRequired": str(md.get("AttributionRequired", "")).lower() == "true",
            "nonFree": str(md.get("NonFree", "")).lower() == "true",
        }
    return None


def license_ok(info: dict) -> tuple[bool, str]:
    lic = info.get("license") or ""
    if info.get("nonFree"):
        return False, "非フリー（NonFree）"
    if not ALLOWED_LICENSE_RE.match(lic):
        return False, f"対象外のライセンス: {lic or '不明'} ({info.get('licenseShortName')})"
    return True, lic


# ---------------------------------------------------------------- 画像処理
def make_square(data: bytes, focus: tuple[float, float] = DEFAULT_FOCUS, size: int = SIZE) -> bytes:
    from PIL import Image, ImageOps

    im = Image.open(io.BytesIO(data))
    im = ImageOps.exif_transpose(im).convert("RGB")
    w, h = im.size
    side = min(w, h)
    fx, fy = focus
    left = int(round(min(max(fx * w - side / 2, 0), w - side)))
    top = int(round(min(max(fy * h - side / 2, 0), h - side)))
    im = im.crop((left, top, left + side, top + side)).resize((size, size), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=85, optimize=True, progressive=True)  # EXIF は書き込まない
    return buf.getvalue()


# ---------------------------------------------------------------- メイン処理
def run(catalog: dict, mapping: dict, get: HttpGet, out_dir: Path | None, *,
        suggest_only: bool = False, accept_unverified: bool = False) -> tuple[dict, dict, dict]:
    """戻り値: (portraits, 更新後の mapping, report)"""
    report = {"unmapped": [], "unverified": [], "noImage": [], "rejected": [], "errors": [], "ok": []}
    portraits: dict[str, dict] = {}

    # 1. 対応づけ
    for comp in catalog["composers"]:
        cid = comp["id"]
        m = mapping.get(cid)
        if m is None:
            cands = search_candidates(get, comp)
            mapping[cid] = {
                "name": comp["name"],
                "qid": cands[0]["qid"] if len(cands) == 1 else None,
                "verified": False,
                "candidates": cands,
            }
        if mapping[cid].get("exclude"):
            continue
        if not mapping[cid].get("qid"):
            report["unmapped"].append({"composerId": cid, "name": comp["name"],
                                       "candidates": mapping[cid].get("candidates", [])})
        elif not mapping[cid].get("verified"):
            report["unverified"].append({"composerId": cid, "name": comp["name"], "qid": mapping[cid]["qid"]})

    if suggest_only:
        return portraits, mapping, report

    # 2〜4. 画像の取得
    targets = {cid: m for cid, m in mapping.items()
               if m.get("qid") and not m.get("exclude") and (m.get("verified") or accept_unverified)
               and any(c["id"] == cid for c in catalog["composers"])}
    entities = get_entities(get, sorted({m["qid"] for m in targets.values()})) if targets else {}

    for cid, m in sorted(targets.items()):
        try:
            files = [m["file"]] if m.get("file") else claim_strings(entities.get(m["qid"], {}), "P18")
            if not files:
                report["noImage"].append({"composerId": cid, "qid": m["qid"]})
                continue
            info = None
            for f in files:
                cand = commons_info(get, f)
                if not cand:
                    continue
                ok, why = license_ok(cand)
                if ok:
                    info = cand
                    break
                report["rejected"].append({"composerId": cid, "file": f, "reason": why})
            if not info:
                continue
            focus = tuple(m.get("focus") or DEFAULT_FOCUS)
            jpg = make_square(get(info["thumbUrl"]), focus)
            rel = f"portraits/{cid}.jpg"
            if out_dir:
                (out_dir / "portraits").mkdir(parents=True, exist_ok=True)
                (out_dir / rel).write_bytes(jpg)
            needs_attr = info["attributionRequired"] or not ATTRIBUTION_FREE_RE.match(info["license"])
            portraits[cid] = {
                "url": rel,
                "width": SIZE,
                "height": SIZE,
                "sha256": hashlib.sha256(jpg).hexdigest(),
                "title": info["objectName"] or (info["title"] or "").removeprefix("File:"),
                "artist": info["artist"],
                "date": info["date"],
                "license": info["licenseShortName"] or info["license"],
                "licenseCode": info["license"],
                "licenseUrl": info["licenseUrl"],
                "sourceUrl": info["descriptionUrl"],
                "attributionRequired": needs_attr,
                "wikidata": m["qid"],
            }
            report["ok"].append(cid)
        except Exception as e:  # noqa: BLE001
            report["errors"].append({"composerId": cid, "error": str(e)})
    return portraits, mapping, report


def main(argv=None):
    ap = argparse.ArgumentParser(description="作曲家の肖像画サムネイルを作成")
    ap.add_argument("--catalog", default="catalog.json")
    ap.add_argument("--mapping", default="composer_wikidata.json")
    ap.add_argument("--out", default="../site/v1", help="portraits/ を書き出す公開ディレクトリ")
    ap.add_argument("--cache", default="cache/wikimedia")
    ap.add_argument("--contact", default=DEFAULT_CONTACT, help="User-Agent に入れる連絡先（メールまたはURL）")
    ap.add_argument("--suggest-only", action="store_true", help="QID の候補作りだけ行う")
    ap.add_argument("--accept-unverified", action="store_true", help="未確認の対応も使う（非推奨）")
    args = ap.parse_args(argv)

    if args.contact == DEFAULT_CONTACT and not args.suggest_only:
        print("注意: --contact に連絡先を指定してください（Wikimedia の User-Agent ポリシー）", file=sys.stderr)

    cat_path, map_path = Path(args.catalog), Path(args.mapping)
    catalog = json.loads(cat_path.read_text(encoding="utf-8"))
    mapping = json.loads(map_path.read_text(encoding="utf-8")) if map_path.exists() else {}
    http = Http(Path(args.cache), args.contact)

    portraits, mapping, report = run(catalog, mapping, http, Path(args.out),
                                     suggest_only=args.suggest_only,
                                     accept_unverified=args.accept_unverified)
    map_path.write_text(json.dumps(mapping, ensure_ascii=False, indent=1), encoding="utf-8")
    Path("portraits.report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    if not args.suggest_only:
        Path("portraits.json").write_text(json.dumps(portraits, ensure_ascii=False, indent=1, sort_keys=True),
                                          encoding="utf-8")

    n = len(catalog["composers"])
    print(f"作曲家 {n} 人: 取得 {len(report['ok'])} / 未対応 {len(report['unmapped'])} / "
          f"未確認 {len(report['unverified'])} / 画像なし {len(report['noImage'])} / "
          f"ライセンス除外 {len(report['rejected'])} / エラー {len(report['errors'])}", file=sys.stderr)
    print(f"→ {map_path} を確認し、正しい対応を \"verified\": true にしてください", file=sys.stderr)


if __name__ == "__main__":
    main()
