"""validate_catalog.py と make_manifest.py（PLAN 0.5）。"""
import copy
import hashlib
import json
import sys
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, FormatChecker

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent))
import make_manifest as mm  # noqa: E402
import validate_catalog as vc  # noqa: E402

FIX = ROOT / "fixtures"
SAMPLE = json.loads((FIX / "catalog.sample.json").read_text(encoding="utf-8"))
LOOSE = 1.0  # 比率以外を見るテストでは、比率の検査を外す（サンプルは 20 曲中「その他」1 曲 = 5%）
MANIFEST_SCHEMA = json.loads((ROOT / "schema/manifest.schema.json").read_text(encoding="utf-8"))


def check(doc, **kw):
    kw.setdefault("max_ratio", LOOSE)
    return vc.validate(doc, **kw)


# ---------------------------------------------------------------- validate_catalog
def test_sample_is_valid():
    errors, warnings = check(SAMPLE)
    assert errors == [] and warnings == []


@pytest.mark.parametrize("path", sorted((FIX / "invalid").glob("*.json")), ids=lambda p: p.stem)
def test_invalid_fixtures_fail(path):
    doc = json.loads(path.read_text(encoding="utf-8"))
    errors, _ = check(doc)
    assert errors, f"{path.name} が検証を通ってしまう"
    assert vc.main([str(path), "--max-ratio", "1"]) == 1


def test_invalid_fixtures_fail_for_the_documented_reason():
    msg = {p.stem: " ".join(check(json.loads(p.read_text(encoding="utf-8")))[0])
           for p in (FIX / "invalid").glob("*.json")}
    assert "ID の重複" in msg["duplicate-track-id"]
    assert "参照切れ" in msg["dangling-work-ref"]
    assert "スキーマ" in msg["foreign-audio-domain"]
    assert "スキーマ" in msg["schema-version-3"]
    assert "trackCount の不一致" in msg["trackcount-mismatch"]


def mutate(fn):
    d = copy.deepcopy(SAMPLE)
    fn(d)
    return d


@pytest.mark.parametrize("name, fn, needle", [
    ("work の作曲家が存在しない", lambda d: d["works"][0].update(composerId="nobody"), "参照切れ"),
    ("work のジャンルが存在しない", lambda d: d["works"][0].update(genreId="nope"), "参照切れ"),
    ("track のジャンルが存在しない", lambda d: d["tracks"][0].update(genreId="nope"), "参照切れ"),
    ("作品と曲の作曲家が違う", lambda d: d["tracks"][0].update(composerId="john-newton"), "不整合"),
    ("articleUrl の記事 ID が違う", lambda d: d["tracks"][0].update(
        articleUrl="https://classical-sound.seesaa.net/article/1.html"), "articleUrl"),
    ("作曲家 ID の重複", lambda d: d["composers"].append(dict(d["composers"][0])), "ID の重複"),
    ("置き換え先の曲がない", lambda d: d["replacedTracks"].update({"1": "2"}), "参照切れ: replacedTracks"),
    ("置き換えた曲が残っている", lambda d: d["replacedTracks"].update({d["tracks"][1]["id"]: d["tracks"][0]["id"]}),
     "tracks に残っている"),
])
def test_integrity_errors(name, fn, needle):
    errors, _ = check(mutate(fn))
    assert any(needle in e for e in errors), errors


def test_ratio_limits():
    # 20 曲中 1 曲（5%）が「その他」、「作曲者不明」は 0 曲。上限ちょうどは通す
    errors, _ = vc.validate(SAMPLE)  # 既定 5%
    assert errors == []
    errors, _ = vc.validate(SAMPLE, max_ratio=0.04)
    assert sum("比率" in e for e in errors) == 1
    unknown = mutate(lambda d: [d["composers"][0].update(name="作曲者不明")])
    errors, _ = vc.validate(unknown, max_ratio=0.3)
    assert any("作曲者不明" in e for e in errors), errors  # 8 曲（40%）


@pytest.mark.parametrize("prev, ok", [(20, True), (22, True), (23, False), (100, False)])
def test_track_count_drop_threshold(prev, ok):
    # 20 曲。前回 prev 曲から 10% 以上減ったら NG（22 → 9.1% 減、23 → 13% 減）
    errors, _ = check(SAMPLE, previous_count=prev)
    assert (errors == []) is ok, errors


def test_warnings_for_unused_items():
    d = mutate(lambda d: d["works"].append(
        {"id": "x--unused", "title": "未使用", "composerId": "frederic-francois-chopin", "genreId": "piano"}))
    errors, warnings = check(d)
    assert errors == [] and any("曲のない作品" in w for w in warnings)


def test_cli_with_previous_manifest(tmp_path):
    prev = tmp_path / "prev.json"
    prev.write_text(json.dumps({"trackCount": 100}), encoding="utf-8")
    assert vc.main([str(FIX / "catalog.sample.json"), "--max-ratio", "1"]) == 0
    assert vc.main([str(FIX / "catalog.sample.json"), "--max-ratio", "1", "--previous-manifest", str(prev)]) == 1
    # 前回の manifest がまだない（初回公開）なら減少の検査はしない
    assert vc.main([str(FIX / "catalog.sample.json"), "--max-ratio", "1",
                    "--previous-manifest", str(tmp_path / "none.json")]) == 0


# ---------------------------------------------------------------- make_manifest
def test_manifest_sample_matches_catalog_sample():
    manifest = json.loads((FIX / "manifest.sample.json").read_text(encoding="utf-8"))
    assert manifest == mm.make_manifest(FIX / "catalog.sample.json")
    assert manifest["sha256"] == hashlib.sha256((FIX / "catalog.sample.json").read_bytes()).hexdigest()


def test_manifest_matches_schema():
    v = Draft202012Validator(MANIFEST_SCHEMA, format_checker=FormatChecker())
    Draft202012Validator.check_schema(MANIFEST_SCHEMA)
    assert list(v.iter_errors(mm.make_manifest(FIX / "catalog.sample.json"))) == []


SHA_A, SHA_B = "a" * 64, "b" * 64


@pytest.mark.parametrize("generated, sha, previous, expected", [
    ("2026-12-01T03:17:00+00:00", SHA_A, None, "2026.12.01"),
    ("2026-12-01T00:30:00+09:00", SHA_A, None, "2026.11.30"),                       # UTC の日付
    ("2027-01-01T03:17:00+00:00", SHA_A, {"catalogVersion": "2026.12.01", "sha256": SHA_A}, "2026.12.01"),  # 中身が同じなら据え置き
    ("2026-12-01T05:00:00+00:00", SHA_B, {"catalogVersion": "2026.12.01", "sha256": SHA_A}, "2026.12.01.2"),
    ("2026-12-01T06:00:00+00:00", SHA_A, {"catalogVersion": "2026.12.01.2", "sha256": SHA_B}, "2026.12.01.3"),
    ("2027-01-01T03:17:00+00:00", SHA_B, {"catalogVersion": "2026.12.01", "sha256": SHA_A}, "2027.01.01"),
])
def test_catalog_version(generated, sha, previous, expected):
    assert mm.catalog_version(generated, sha, previous) == expected


def test_make_manifest_cli(tmp_path):
    out = tmp_path / "manifest.json"
    assert mm.main([str(FIX / "catalog.sample.json"), "-o", str(out)]) == 0
    assert json.loads(out.read_text(encoding="utf-8"))["trackCount"] == 20
