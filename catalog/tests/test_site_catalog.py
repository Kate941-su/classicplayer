"""公開するカタログ（site/v1/catalog.json と manifest.json）の検証（PLAN 0.7、SPEC §3.1・§3.5）。

site/v1/ はオーナーが手元で tool/update-catalog.sh を実行して作り、PR でコミットする。CI（catalog-test・pages）で
この検証が通らなければ公開しない。site/v1/ がまだなければ飛ばす。
"""
import hashlib
import json
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
import build_catalog as bc  # noqa: E402
import make_manifest as mm  # noqa: E402
import validate_catalog as vc  # noqa: E402

SITE = ROOT / "site" / "v1"
CATALOG = SITE / "catalog.json"
MANIFEST = SITE / "manifest.json"

pytestmark = pytest.mark.skipif(not CATALOG.exists(), reason="site/v1/catalog.json がまだない")


def test_published_catalog_is_valid():
    # 肖像画（site/v1/portraits/）のファイルと sha256 も確かめる（SPEC §3.6）
    errors, _ = vc.validate(json.loads(CATALOG.read_text(encoding="utf-8")), assets_dir=SITE)
    assert errors == []


def test_manifest_matches_catalog():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    assert manifest["sha256"] == hashlib.sha256(CATALOG.read_bytes()).hexdigest(), \
        "manifest.json が catalog.json と合わない（tool/update-catalog.sh で作り直す）"
    assert manifest["trackCount"] == len(catalog["tracks"])
    assert manifest["generatedAt"] == catalog["generatedAt"]
    assert manifest["catalogUrl"] == "catalog.json"
    assert manifest["schemaVersion"] == catalog["schemaVersion"] == bc.SCHEMA_VERSION == mm.SCHEMA_VERSION
    assert manifest["minAppSchema"] == mm.MIN_APP_SCHEMA
