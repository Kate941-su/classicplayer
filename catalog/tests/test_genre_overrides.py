"""genre_map.json と overrides.json の適用（PLAN 0.3）。"""
import json
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE.parent), str(HERE)]
import build_catalog as bc  # noqa: E402
from test_build_catalog import B, article  # noqa: E402

REAL_MAP = bc.GenreMap(json.loads((HERE.parent / "genre_map.json").read_text(encoding="utf-8")))
REAL_OVERRIDES = json.loads((HERE.parent / "overrides.json").read_text(encoding="utf-8"))


def run(articles, genre_map=REAL_MAP, overrides=None):
    """articles: [(記事ID, 日付, タイトル, [mp3], カテゴリ名)]。overrides は実ファイルに上書きで足す"""
    pages = {}
    for i, (aid, date, title, mp3s, cat) in enumerate(articles):
        pages[f"{B}article/{aid}.html"] = bc.parse(article(date, title, mp3s, 100 + i, cat))
    return bc.build(pages, {}, {}, bc.DEFAULT_EXCLUDE, genre_map, bc.Overrides({**REAL_OVERRIDES, **(overrides or {})}))


# ---------------------------------------------------------------- GenreMap
@pytest.mark.parametrize("category, expected", [
    ("器楽曲・Piano", "piano"),            # 完全一致
    ("交響曲", "symphony"),
    ("ピアノ協奏曲", "concerto"),          # rules は上から順（ピアノより協奏曲が先）
    ("PIANO SOLO", "piano"),              # 大文字小文字を区別しない
    ("室内楽", "chamber"),
    ("歌劇・オペラ", "vocal"),
    ("序曲", "orchestral"),
    ("よく分からない", "other"),            # 当たらなければ default
    (None, "other"),
])
def test_real_genre_map_resolves(category, expected):
    assert REAL_MAP.resolve(category)[0] == expected


def test_real_genre_map_has_name_en_and_unique_order():
    orders = [g["order"] for g in REAL_MAP.genres.values()]
    assert len(orders) == len(set(orders))
    assert all(g["nameEn"] for g in REAL_MAP.genres.values())


def test_genre_map_rejects_unknown_genre_id():
    with pytest.raises(ValueError):
        bc.GenreMap({"genres": [], "categories": {"X": "nope"}})


def test_genre_map_without_default_genre_gets_other():
    gm = bc.GenreMap({})
    assert gm.resolve("何か") == ("other", False)
    assert gm.genres["other"]["nameEn"] == "Other"


# ---------------------------------------------------------------- build
BASIC = [
    ("100", "2020年1月1日", "ベートーヴェン：交響曲 第5番 第1楽章 / L.V.Beethoven:Symphony No.5:I.", ["a.mp3"], "交響曲"),
    ("101", "2020年1月2日", "ベートーベン：交響曲 第5番 第2楽章 / Beethoven:Symphony No.5:II.", ["b.mp3"], "交響曲"),
    ("102", "2020年1月3日", "ショパン：ノクターン 第2番 / F.Chopin:Nocturne No.2", ["c.mp3"], "器楽曲・Piano"),
    ("103", "2020年1月4日", "謎の曲", ["d.mp3"], "謎カテゴリ"),
]


def test_genres_have_display_name_name_en_and_order_sorted():
    cat, rep = run(BASIC)
    assert [(g["id"], g["name"], g["nameEn"], g["order"]) for g in cat["genres"]] == [
        ("symphony", "交響曲", "Symphonies", 10),
        ("piano", "ピアノ", "Piano", 40),
        ("other", "その他", "Other", 999),
    ]
    assert rep["unmappedCategories"] == {"謎カテゴリ": 1}
    assert rep["categories"]["器楽曲・Piano"] == "piano"


def test_composer_aliases_merge_and_majority_name_en():
    cat, _ = run(BASIC)
    beethoven = [c for c in cat["composers"] if c["name"] == "ベートーヴェン"]
    assert len(beethoven) == 1 and beethoven[0]["trackCount"] == 2
    # 「L.V.Beethoven」と「Beethoven」が 1 票ずつ → 先に出た方
    assert beethoven[0]["nameEn"] == "L.V.Beethoven"
    t100, t101 = (t for t in cat["tracks"] if t["id"] in ("100", "101"))
    assert t100["composerId"] == t101["composerId"] and t100["workId"] == t101["workId"]


def test_composer_en_override_sets_name_and_id():
    cat, _ = run(BASIC, overrides={"composerEn": {"ベートーヴェン": "Ludwig van Beethoven"}})
    c = next(c for c in cat["composers"] if c["name"] == "ベートーヴェン")
    assert c["nameEn"] == "Ludwig van Beethoven" and c["id"] == "ludwig-van-beethoven"


def test_exclude_by_article_id():
    cat, rep = run(BASIC, overrides={"exclude": ["103"]})
    assert "103" not in {t["id"] for t in cat["tracks"]}
    assert rep["excludedByOverride"] == ["103"]
    assert "other" not in {g["id"] for g in cat["genres"]}   # 曲のないジャンルは出さない


def test_track_override_fixes_parse_and_genre():
    ov = {"tracks": {"103": {"composer": "エルガー", "composerEn": "E.Elgar", "work": "愛の挨拶",
                             "movement": None, "genre": "chamber"}}}
    cat, rep = run(BASIC, overrides=ov)
    t = next(t for t in cat["tracks"] if t["id"] == "103")
    assert t["genreId"] == "chamber"
    assert next(c for c in cat["composers"] if c["id"] == t["composerId"])["name"] == "エルガー"
    assert next(w for w in cat["works"] if w["id"] == t["workId"])["title"] == "愛の挨拶"
    assert rep["unmappedCategories"] == {}   # 上書きしたものは警告に出さない
    assert rep["ratios"] == {"otherGenre": 0.0, "unknownComposer": 0.0}


def test_track_override_rejects_unknown_genre():
    with pytest.raises(ValueError):
        run(BASIC, overrides={"tracks": {"103": {"genre": "nope"}}})


def test_ratios_and_unknown_composer():
    cat, rep = run(BASIC)
    assert rep["ratios"] == {"otherGenre": 0.25, "unknownComposer": 0.25}
    assert any(c["name"] == "作曲者不明" for c in cat["composers"])


def test_track_ids_do_not_change_with_overrides():
    plain = [t["id"] for t in run(BASIC)[0]["tracks"]]
    tuned = [t["id"] for t in run(BASIC, overrides={
        "composerEn": {"ベートーヴェン": "X"}, "tracks": {"103": {"composer": "Y"}}})[0]["tracks"]]
    assert plain == tuned == ["100", "101", "102", "103"]


def test_same_name_en_for_different_composers_gets_distinct_ids():
    arts = [
        ("200", "2020年1月1日", "バッハ：曲A / J.S.Bach:A", ["a.mp3"], "器楽曲・Piano"),
        ("201", "2020年1月2日", "大バッハ：曲B / J.S.Bach:B", ["b.mp3"], "器楽曲・Piano"),
    ]
    cat, _ = run(arts)
    assert sorted(c["id"] for c in cat["composers"]) == ["j-s-bach", "j-s-bach-2"]


def test_slug_of_symbols_only_is_distinct():
    assert bc.slug("！？") != bc.slug("＃＃")
