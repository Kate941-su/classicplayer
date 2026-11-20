"""schema/catalog.schema.json の検証（PLAN 0.2）。"""
import copy
import hashlib
import json
import sys
import tempfile
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "catalog"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

SCHEMA = json.loads((ROOT / "schema/catalog.schema.json").read_text(encoding="utf-8"))
SAMPLE = json.loads((ROOT / "fixtures/catalog.sample.json").read_text(encoding="utf-8"))
VALIDATOR = Draft202012Validator(SCHEMA, format_checker=FormatChecker())


def errors(doc):
    return sorted(e.message for e in VALIDATOR.iter_errors(doc))


def test_schema_is_valid_draft_2020_12():
    Draft202012Validator.check_schema(SCHEMA)


def test_sample_fixture_passes():
    assert errors(SAMPLE) == []


def test_sample_fixture_covers_plan_requirements():
    # PLAN 0.9 の条件の一部（ジャンル 3 以上、複数楽章の作品、複数 mp3 の記事、null の値）
    assert len(SAMPLE["genres"]) >= 3
    per_work = {}
    for t in SAMPLE["tracks"]:
        per_work[t["workId"]] = per_work.get(t["workId"], 0) + 1
    assert max(per_work.values()) >= 2
    assert any("-" in t["id"] for t in SAMPLE["tracks"])
    assert any(t["publishedAt"] is None for t in SAMPLE["tracks"])


def mutate(fn):
    doc = copy.deepcopy(SAMPLE)
    fn(doc)
    return doc


BAD = {
    "schemaVersion が 2": lambda d: d.update(schemaVersion=2),
    "未知のトップレベルキー": lambda d: d.update(extra=1),
    "tracks が空": lambda d: d.update(tracks=[]),
    "track.id が slug": lambda d: d["tracks"][0].update(id="beethoven-1"),
    "track.id の連番が 2 段": lambda d: d["tracks"][0].update(id="1-2-3"),
    "audioUrl が別ドメイン": lambda d: d["tracks"][0].update(audioUrl="https://example.com/a.mp3"),
    "audioUrl が http": lambda d: d["tracks"][0].update(
        audioUrl="http://classical-sound.up.seesaa.net/image/a.mp3"),
    "audioUrl に クエリ": lambda d: d["tracks"][0].update(
        audioUrl="https://classical-sound.up.seesaa.net/image/a.mp3?x=1.mp3"),
    "articleUrl が記事以外": lambda d: d["tracks"][0].update(
        articleUrl="https://classical-sound.seesaa.net/category/1-1.html"),
    "track のキー欠落（durationSec）": lambda d: d["tracks"][0].pop("durationSec"),
    "track の未知キー": lambda d: d["tracks"][0].update(duration=1),
    "publishedAt の書式": lambda d: d["tracks"][0].update(publishedAt="2020/03/20 18:18"),
    "publishedAt のタイムゾーンなし": lambda d: d["tracks"][0].update(publishedAt="2020-03-20T18:18:00"),
    "movement が空文字": lambda d: d["tracks"][0].update(movement=""),
    "tags に数値": lambda d: d["tracks"][0].update(tags=[2020]),
    "genre に order なし": lambda d: d["genres"][0].pop("order"),
    "genre.order が文字列": lambda d: d["genres"][0].update(order="10"),
    "genre.trackCount が負": lambda d: d["genres"][0].update(trackCount=-1),
    "composer に nameEn なし": lambda d: d["composers"][0].pop("nameEn"),
    "work の id に空白": lambda d: d["works"][0].update(id="a b"),
    "license に credit なし": lambda d: d["license"].pop("credit"),
    "source が別サイト": lambda d: d.update(source="https://example.com/"),
}


@pytest.mark.parametrize("name", BAD)
def test_invalid_documents_fail(name):
    assert errors(mutate(BAD[name])), f"{name} が検証を通ってしまう"


def test_build_catalog_output_matches_schema():
    import build_catalog as bc
    from test_build_catalog import PAGES

    with tempfile.TemporaryDirectory() as d:
        cache = Path(d) / "cache"
        cache.mkdir()
        for url, html in PAGES.items():
            (cache / (hashlib.sha1(url.encode()).hexdigest() + ".html")).write_text(html, encoding="utf-8")
        out = Path(d) / "catalog.json"
        bc.main(["--offline", "--cache", str(cache), "-o", str(out), "-q"])
        cat = json.loads(out.read_text(encoding="utf-8"))
    assert errors(cat) == []
