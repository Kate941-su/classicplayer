#!/usr/bin/env python3
"""catalog.json から manifest.json を作る（SPEC §3.5、PLAN 0.5）。標準ライブラリのみ。

    python3 make_manifest.py catalog.json -o manifest.json [--previous old-manifest.json]

catalogVersion は generatedAt（UTC）の日付 "YYYY.MM.DD"。
- 前回と sha256 が同じなら前回の catalogVersion を引き継ぐ（アプリは取り直さない）
- 同じ日に中身の違うカタログを出し直すときは "YYYY.MM.DD.2" のように連番を付ける
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
MIN_APP_SCHEMA = 1


def catalog_version(generated_at: str, sha256: str, previous: dict | None) -> str:
    day = datetime.fromisoformat(generated_at.replace("Z", "+00:00")).astimezone(timezone.utc).strftime("%Y.%m.%d")
    if not previous:
        return day
    prev_ver = previous.get("catalogVersion", "")
    if previous.get("sha256") == sha256:
        return prev_ver
    m = re.fullmatch(r"(\d{4}\.\d{2}\.\d{2})(?:\.(\d+))?", prev_ver)
    if m and m.group(1) == day:
        return f"{day}.{int(m.group(2) or 1) + 1}"
    return day


def make_manifest(catalog_path: Path, previous: dict | None = None, catalog_url: str = "catalog.json") -> dict:
    raw = catalog_path.read_bytes()
    catalog = json.loads(raw)
    sha = hashlib.sha256(raw).hexdigest()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "catalogVersion": catalog_version(catalog["generatedAt"], sha, previous),
        "catalogUrl": catalog_url,
        "sha256": sha,
        "trackCount": len(catalog["tracks"]),
        "generatedAt": catalog["generatedAt"],
        "minAppSchema": MIN_APP_SCHEMA,
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="manifest.json を作る")
    ap.add_argument("catalog")
    ap.add_argument("-o", "--output", default="manifest.json")
    ap.add_argument("--previous", help="前回公開した manifest.json（なければ省略）")
    args = ap.parse_args(argv)
    prev = None
    if args.previous and Path(args.previous).exists():
        prev = json.loads(Path(args.previous).read_text(encoding="utf-8"))
    manifest = make_manifest(Path(args.catalog), prev)
    Path(args.output).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"manifest: {manifest['catalogVersion']} / 曲 {manifest['trackCount']} / sha256 {manifest['sha256'][:12]}…",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
