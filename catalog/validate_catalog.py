#!/usr/bin/env python3
"""catalog.json を検証する（SPEC §3.1、PLAN 0.5）。

    python3 validate_catalog.py catalog.json
    python3 validate_catalog.py catalog.json --previous-manifest old-manifest.json   # 曲数の減少も検査
    python3 validate_catalog.py catalog.json --assets-dir ../site/v1                  # 肖像画のファイルも検査

検査: スキーマ / ID の重複 / 参照切れ / trackCount の整合 / URL（記事 ID と track.id の一致）/
「その他」と「作曲者不明」の比率 / 前回からの曲数の減少 /
肖像画（url の形式。--assets-dir があればファイルの有無と sha256、使われていない画像）。
エラーがあれば終了コード 1。依存: jsonschema（requirements-dev.txt）。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_PATH = ROOT / "schema" / "catalog.schema.json"
OTHER_GENRE_ID = "other"
UNKNOWN_COMPOSER = "作曲者不明"
DEFAULT_MAX_RATIO = 0.05   # SPEC §12: 「その他」と「作曲者不明」は 5% 以下
DEFAULT_MAX_DROP = 0.10    # SPEC §3.1: 前回より 10% 以上減ったら止める


def schema_errors(catalog: dict) -> list[str]:
    try:
        from jsonschema import Draft202012Validator, FormatChecker
    except ImportError:
        sys.exit("jsonschema が必要です: pip install -r catalog/requirements-dev.txt")
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    v = Draft202012Validator(schema, format_checker=FormatChecker())
    return [f"スキーマ: /{'/'.join(map(str, e.absolute_path))}: {e.message}"
            for e in sorted(v.iter_errors(catalog), key=lambda e: list(map(str, e.absolute_path)))]


def portrait_problems(catalog: dict, assets_dir: Path | None) -> tuple[list[str], list[str]]:
    """composers[].portrait の検査（SPEC §3.6）。assets_dir（site/v1/）があれば画像ファイルも確かめる。"""
    errors: list[str] = []
    warnings: list[str] = []
    used: set[str] = set()
    for c in catalog["composers"]:
        p = c.get("portrait")
        if p is None:
            continue
        expected = f"portraits/{c['id']}.jpg"
        if p["url"] != expected:
            errors.append(f"不整合: composer {c['id']} の portrait.url {p['url']}（{expected} にする）")
        used.add(p["url"])
        if assets_dir is None:
            continue
        f = assets_dir / p["url"]
        if not f.is_file():
            errors.append(f"肖像画のファイルがない: {f}（composer {c['id']}）")
        elif hashlib.sha256(f.read_bytes()).hexdigest() != p["sha256"]:
            errors.append(f"肖像画の sha256 が合わない: {f}（fetch_portraits.py とカタログを作り直す）")
    if assets_dir is not None and (assets_dir / "portraits").is_dir():
        for f in sorted((assets_dir / "portraits").glob("*.jpg")):
            if f"portraits/{f.name}" not in used:
                warnings.append(f"どの作曲家にも使われていない肖像画: {f.name}")
    return errors, warnings


def validate(catalog: dict, max_ratio: float = DEFAULT_MAX_RATIO,
             previous_count: int | None = None, max_drop: float = DEFAULT_MAX_DROP,
             assets_dir: Path | None = None) -> tuple[list[str], list[str]]:
    """(errors, warnings) を返す。"""
    errors = schema_errors(catalog)
    if errors:
        return errors, []   # 形が壊れていると以降の検査が誤検知するので止める
    warnings: list[str] = []

    for kind in ("genres", "composers", "works", "tracks"):
        dup = [k for k, n in Counter(x["id"] for x in catalog[kind]).items() if n > 1]
        if dup:
            errors.append(f"ID の重複（{kind}）: {', '.join(dup[:10])}")

    genres = {g["id"]: g for g in catalog["genres"]}
    composers = {c["id"]: c for c in catalog["composers"]}
    works = {w["id"]: w for w in catalog["works"]}
    tracks = catalog["tracks"]

    for w in catalog["works"]:
        if w["composerId"] not in composers:
            errors.append(f"参照切れ: work {w['id']} の composerId {w['composerId']}")
        if w["genreId"] not in genres:
            errors.append(f"参照切れ: work {w['id']} の genreId {w['genreId']}")
    for t in tracks:
        for key, table in (("composerId", composers), ("workId", works), ("genreId", genres)):
            if t[key] not in table:
                errors.append(f"参照切れ: track {t['id']} の {key} {t[key]}")
        w = works.get(t["workId"])
        if w and w["composerId"] != t["composerId"]:
            errors.append(f"不整合: track {t['id']} の composerId が作品 {w['id']} の作曲家と違う")
        article_id = t["id"].split("-")[0]
        if not t["articleUrl"].endswith(f"/article/{article_id}.html"):
            errors.append(f"不整合: track {t['id']} の articleUrl {t['articleUrl']}")
        if not t["audioUrl"].endswith("/" + t["fileName"]) and t["fileName"] not in t["audioUrl"]:
            warnings.append(f"track {t['id']} の fileName が audioUrl と一致しない")

    for kind, key, table in (("genres", "genreId", genres), ("composers", "composerId", composers)):
        actual = Counter(t[key] for t in tracks)
        for id_, item in table.items():
            if item["trackCount"] != actual.get(id_, 0):
                errors.append(f"trackCount の不一致（{kind} {id_}）: {item['trackCount']} ≠ 実数 {actual.get(id_, 0)}")
            if actual.get(id_, 0) == 0:
                warnings.append(f"曲のない {kind}: {id_}")
    used_works = {t["workId"] for t in tracks}
    for wid in works.keys() - used_works:
        warnings.append(f"曲のない作品: {wid}")

    track_ids = {t["id"] for t in tracks}
    for old_id, new_id in catalog["replacedTracks"].items():
        if old_id in track_ids:
            errors.append(f"不整合: replacedTracks の {old_id} が tracks に残っている")
        if new_id not in track_ids:
            errors.append(f"参照切れ: replacedTracks の {old_id} → {new_id}")

    p_errors, p_warnings = portrait_problems(catalog, assets_dir)
    errors += p_errors
    warnings += p_warnings

    dup_audio = [u for u, n in Counter(t["audioUrl"] for t in tracks).items() if n > 1]
    if dup_audio:
        warnings.append(f"同じ mp3 を指す曲が複数ある: {len(dup_audio)} 件（例 {dup_audio[0]}）")

    total = len(tracks)
    other = sum(1 for t in tracks if t["genreId"] == OTHER_GENRE_ID)
    unknown_ids = {c["id"] for c in catalog["composers"] if c["name"] == UNKNOWN_COMPOSER}
    unknown = sum(1 for t in tracks if t["composerId"] in unknown_ids)
    for label, n in (("その他（ジャンル）", other), ("作曲者不明", unknown)):
        if total and n / total > max_ratio:
            errors.append(f"比率: {label} が {n}/{total}（{n / total:.1%}）で上限 {max_ratio:.0%} を超えている")

    if previous_count:
        if total < previous_count * (1 - max_drop):
            errors.append(f"曲数の減少: 前回 {previous_count} → 今回 {total}"
                          f"（{1 - total / previous_count:.1%} 減。上限 {max_drop:.0%}）")
    return errors, warnings


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="catalog.json を検証する")
    ap.add_argument("catalog")
    ap.add_argument("--max-ratio", type=float, default=DEFAULT_MAX_RATIO,
                    help="「その他」と「作曲者不明」の比率の上限（既定 0.05）")
    ap.add_argument("--previous-manifest", help="前回公開した manifest.json（曲数の減少を検査する）")
    ap.add_argument("--previous-count", type=int, help="前回の曲数（--previous-manifest の代わり）")
    ap.add_argument("--max-drop", type=float, default=DEFAULT_MAX_DROP)
    ap.add_argument("--assets-dir", help="公開ディレクトリ（site/v1/）。肖像画のファイルの有無と sha256 を検査する")
    args = ap.parse_args(argv)

    catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8"))
    prev = args.previous_count
    if args.previous_manifest and Path(args.previous_manifest).exists():
        prev = json.loads(Path(args.previous_manifest).read_text(encoding="utf-8")).get("trackCount")
    assets = Path(args.assets_dir) if args.assets_dir else None
    errors, warnings = validate(catalog, args.max_ratio, prev, args.max_drop, assets)
    for w in warnings:
        print(f"警告: {w}", file=sys.stderr)
    for e in errors:
        print(f"エラー: {e}", file=sys.stderr)
    print(f"{'NG' if errors else 'OK'}: 曲 {len(catalog.get('tracks', []))} / エラー {len(errors)} / 警告 {len(warnings)}",
          file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
