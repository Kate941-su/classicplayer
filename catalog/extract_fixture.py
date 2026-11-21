#!/usr/bin/env python3
"""実カタログから曲を抜き出して、テスト用のサンプルカタログ（fixtures/catalog.sample.json）を作る（PLAN 0.9）。

    python3 extract_fixture.py <catalog.json>    # fixtures/catalog.sample.json と fixtures/invalid/*.json を書く

抜き出す曲は SAMPLE_TRACK_IDS（観点ごとに選んだ 20 曲）。ジャンル・作曲家・作品は、抜き出した曲が参照するものだけを残し、
trackCount を数え直す。作品名などはカタログのまま（「（2015）」のような録音の書き添えは、全体の録音数で決まったもの）。
"""
from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path

SAMPLE_TRACK_IDS = [
    # 複数楽章の作品。同じ作品名の録音が 2 つ（2015 と 2023）。2023 は 1 記事に mp3 が 2 つ（記事ID-連番）
    "427916377", "428637877", "431342999",
    "500111088-0", "500111088-1", "500150417",
    # ピアノ: 楽章ありの単曲と、楽章なしの単曲
    "474143449", "501651167", "211280853", "211455435",
    # まとめ記事（オンライン特別プログラム）: 英語タイトルが null、記事のジャンルと違う曲（フォーレ）を含む
    "476156249-0", "476156249-1", "476156249-2", "476156249-3", "476156249-4",
    # 英語名にアクセント記号（Antonín Dvořák）
    "467532689",
    # 残りのジャンル: 協奏曲・声楽・器楽・その他
    "228725107", "176681272", "474475833", "174220792",
]


def extract(catalog: dict, ids: list[str]) -> dict:
    by_id = {t["id"]: t for t in catalog["tracks"]}
    missing = [i for i in ids if i not in by_id]
    if missing:
        raise SystemExit(f"カタログにない曲 ID: {missing}")
    wanted = set(ids)
    tracks = [t for t in catalog["tracks"] if t["id"] in wanted]  # カタログの並び（公開順）を保つ

    def count(key, value):
        return sum(1 for t in tracks if t[key] == value)

    work_ids = {t["workId"] for t in tracks}
    composer_ids = {t["composerId"] for t in tracks}
    genre_ids = {t["genreId"] for t in tracks}
    return {
        **catalog,
        "genres": [{**g, "trackCount": count("genreId", g["id"])} for g in catalog["genres"] if g["id"] in genre_ids],
        "composers": sorted(
            ({**c, "trackCount": count("composerId", c["id"])} for c in catalog["composers"] if c["id"] in composer_ids),
            key=lambda c: (-c["trackCount"], c["id"])),
        "works": [w for w in catalog["works"] if w["id"] in work_ids],
        "tracks": tracks,
    }


def invalid_variants(sample: dict) -> dict[str, dict]:
    """異常系のフィクスチャ（fixtures/invalid/README.md）。サンプルを 1 か所だけ壊す。"""
    def broken(fn):
        doc = copy.deepcopy(sample)
        fn(doc)
        return doc

    def duplicate_id(d):
        first, second = d["tracks"][0], d["tracks"][1]
        second.update(id=first["id"], articleUrl=first["articleUrl"])

    return {
        "duplicate-track-id.json": broken(duplicate_id),
        "dangling-work-ref.json": broken(
            lambda d: d["tracks"][0].update(workId=f"{d['tracks'][0]['composerId']}--no-such-work")),
        "foreign-audio-domain.json": broken(lambda d: d["tracks"][0].update(audioUrl="https://example.com/image/a.mp3")),
        "schema-version-2.json": broken(lambda d: d.update(schemaVersion=2)),
        "trackcount-mismatch.json": broken(lambda d: d["genres"][0].update(trackCount=99)),
    }


def dumps(catalog: dict) -> str:
    """読みやすい形で出力する（genres・composers・works は 1 件 1 行、tracks は 1 項目 1 行）。"""
    def one(v):
        return json.dumps(v, ensure_ascii=False)

    def inline(obj):
        return "{ " + ", ".join(f"{one(k)}: {one(v)}" for k, v in obj.items()) + " }"

    def block(obj, indent):
        pad = " " * indent
        return "{\n" + ",\n".join(f"{pad}  {one(k)}: {one(v)}" for k, v in obj.items()) + f"\n{pad}}}"

    lines = []
    for key, value in catalog.items():
        if key in ("genres", "composers", "works"):
            body = ",\n".join(f"    {inline(x)}" for x in value)
            lines.append(f'  {one(key)}: [\n{body}\n  ]')
        elif key == "tracks":
            body = ",\n".join(f"    {block(x, 4)}" for x in value)
            lines.append(f'  {one(key)}: [\n{body}\n  ]')
        elif isinstance(value, dict):
            lines.append(f"  {one(key)}: {block(value, 2)}")
        else:
            lines.append(f"  {one(key)}: {one(value)}")
    return "{\n" + ",\n".join(lines) + "\n}\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description="実カタログからサンプルカタログを作る")
    ap.add_argument("catalog")
    ap.add_argument("--fixtures", default=str(Path(__file__).resolve().parent.parent / "fixtures"),
                    help="出力先（catalog.sample.json と invalid/）")
    args = ap.parse_args(argv)
    catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8"))
    sample = extract(catalog, SAMPLE_TRACK_IDS)
    out = Path(args.fixtures)
    (out / "catalog.sample.json").write_text(dumps(sample), encoding="utf-8")
    (out / "invalid").mkdir(parents=True, exist_ok=True)
    for name, doc in invalid_variants(sample).items():
        (out / "invalid" / name).write_text(dumps(doc), encoding="utf-8")
    print(f"{out / 'catalog.sample.json'}: 曲 {len(sample['tracks'])} / ジャンル {len(sample['genres'])} / 作曲家 {len(sample['composers'])}"
          f" / 作品 {len(sample['works'])}", file=sys.stderr)


if __name__ == "__main__":
    main()
