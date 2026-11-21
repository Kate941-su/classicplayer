"""composers[].portrait（SPEC §3.6、PLAN 0.11）: build_catalog.py で埋め、スキーマと validate_catalog.py で検査する。"""
import copy
import hashlib
import json
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))
import build_catalog as bc  # noqa: E402
import test_build_catalog as tb  # noqa: E402
import validate_catalog as vc  # noqa: E402

SAMPLE = json.loads((ROOT / "fixtures/catalog.sample.json").read_text(encoding="utf-8"))
JPG = b"\xff\xd8\xff fake jpeg \xff\xd9"


def portrait(cid, data=JPG, **kw):
    return {"url": f"portraits/{cid}.jpg", "width": 512, "height": 512, "sha256": hashlib.sha256(data).hexdigest(),
            "title": "Portrait", "artist": "Painter", "date": None, "license": "Public domain", "licenseCode": "pd",
            "licenseUrl": None, "sourceUrl": f"https://commons.wikimedia.org/wiki/File:{cid}.jpg",
            "attributionRequired": False, "wikidata": "Q1", **kw}


def with_portrait(cid=None, **kw):
    cat = copy.deepcopy(SAMPLE)
    c = cat["composers"][0] if cid is None else next(c for c in cat["composers"] if c["id"] == cid)
    c["portrait"] = portrait(c["id"], **kw)
    return cat, c["id"]


def build_offline(tmp_path, portraits=None):
    cache = tmp_path / "cache"
    cache.mkdir(exist_ok=True)
    for url, html in tb.PAGES.items():
        (cache / (hashlib.sha1(url.encode()).hexdigest() + ".html")).write_text(html, encoding="utf-8")
    out = tmp_path / "catalog.json"
    args = ["--offline", "--cache", str(cache), "-o", str(out), "-q", "--portraits", str(tmp_path / "portraits.json")]
    if portraits is not None:
        (tmp_path / "portraits.json").write_text(json.dumps(portraits), encoding="utf-8")
    bc.main(args)
    return (json.loads(out.read_text(encoding="utf-8")),
            json.loads(out.with_suffix(".report.json").read_text(encoding="utf-8")))


# ---------------------------------------------------------------- build_catalog.py
def test_without_portraits_json_every_composer_has_null(tmp_path):
    cat, rep = build_offline(tmp_path)
    assert cat["composers"] and all("portrait" in c and c["portrait"] is None for c in cat["composers"])
    assert rep["portraitsUnmatched"] == [] and rep["composersWithoutPortrait"] == len(cat["composers"])
    assert vc.validate(cat, max_ratio=1.0)[0] == []


def test_portraits_json_fills_matching_composers(tmp_path):
    cat, _ = build_offline(tmp_path)
    beethoven = next(c["id"] for c in cat["composers"] if c["name"] == "ベートーヴェン")
    cat, rep = build_offline(tmp_path, {beethoven: portrait(beethoven), "renamed-composer": portrait("renamed-composer")})
    comps = {c["id"]: c for c in cat["composers"]}
    assert comps[beethoven]["portrait"] == portrait(beethoven)
    assert all(c["portrait"] is None for cid, c in comps.items() if cid != beethoven)
    assert rep["portraitsUnmatched"] == ["renamed-composer"]   # ID が変わった作曲家はレポートで知らせる
    assert list(comps[beethoven]) == ["id", "name", "nameEn", "trackCount", "portrait"]
    assert vc.validate(cat, max_ratio=1.0)[0] == []


# ---------------------------------------------------------------- スキーマ
def test_catalog_without_portrait_key_is_still_valid():
    cat = copy.deepcopy(SAMPLE)
    for c in cat["composers"]:
        c.pop("portrait", None)
    assert vc.schema_errors(cat) == []   # 公開中の古いカタログ（キーなし）も通る


def test_portrait_object_is_valid():
    cat, _ = with_portrait()
    assert vc.schema_errors(cat) == []


@pytest.mark.parametrize("change", [
    {"url": "https://example.com/a.jpg"},
    {"url": "portraits/a.png"},
    {"sha256": "abc"},
    {"licenseCode": "cc-by-nc-4.0"},
    {"sourceUrl": "https://example.com/File:a.jpg"},
    {"wikidata": "1234"},
    {"attributionRequired": "yes"},
    {"license": ""},
    {"extra": 1},
])
def test_invalid_portrait_is_rejected_by_schema(change):
    cat, _ = with_portrait(**change)
    assert vc.schema_errors(cat)


def test_portrait_missing_key_is_rejected_by_schema():
    cat, _ = with_portrait()
    del cat["composers"][0]["portrait"]["sourceUrl"]
    assert vc.schema_errors(cat)


# ---------------------------------------------------------------- validate_catalog.py
def test_url_must_match_composer_id():
    cat, cid = with_portrait()
    cat["composers"][0]["portrait"]["url"] = "portraits/someone-else.jpg"
    errors, _ = vc.validate(cat)
    assert any(cid in e and "portrait.url" in e for e in errors)


def test_assets_dir_checks_file_and_sha(tmp_path):
    cat, cid = with_portrait()
    errors, _ = vc.validate(cat, assets_dir=tmp_path)
    assert any("ファイルがない" in e for e in errors)

    (tmp_path / "portraits").mkdir()
    (tmp_path / "portraits" / f"{cid}.jpg").write_bytes(b"other bytes")
    errors, _ = vc.validate(cat, assets_dir=tmp_path)
    assert any("sha256" in e for e in errors)

    (tmp_path / "portraits" / f"{cid}.jpg").write_bytes(JPG)
    assert vc.validate(cat, assets_dir=tmp_path)[0] == []
    assert vc.validate(cat)[0] == []   # assets_dir なしではファイルを見ない


def test_unused_image_is_a_warning(tmp_path):
    cat, cid = with_portrait()
    (tmp_path / "portraits").mkdir()
    (tmp_path / "portraits" / f"{cid}.jpg").write_bytes(JPG)
    (tmp_path / "portraits" / "gone.jpg").write_bytes(JPG)
    errors, warnings = vc.validate(cat, assets_dir=tmp_path)
    assert errors == [] and any("gone.jpg" in w for w in warnings)


def test_cli_assets_dir(tmp_path, capsys):
    cat, cid = with_portrait()
    path = tmp_path / "catalog.json"
    path.write_text(json.dumps(cat, ensure_ascii=False), encoding="utf-8")
    assert vc.main([str(path), "--assets-dir", str(tmp_path)]) == 1
    (tmp_path / "portraits").mkdir()
    (tmp_path / "portraits" / f"{cid}.jpg").write_bytes(JPG)
    assert vc.main([str(path), "--assets-dir", str(tmp_path)]) == 0


def test_sample_fixture_outputs_portrait_key():
    # 生成側はスキーマにあるキーをすべて出力する（SPEC §3.2）。サンプルも同じ形にしておく
    assert all("portrait" in c for c in SAMPLE["composers"])
