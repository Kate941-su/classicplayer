"""PLAN 0.6: 実カタログ（2026-11-21 の全件巡回）で見つかった書式への対応。"""
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE.parent), str(HERE)]
import build_catalog as bc  # noqa: E402
from test_build_catalog import B  # noqa: E402
from test_genre_overrides import run  # noqa: E402


def by_id(cat):
    return {t["id"]: t for t in cat["tracks"]}


def work_of(cat, track):
    return next(w for w in cat["works"] if w["id"] == track["workId"])


def composer_of(cat, track):
    return next(c for c in cat["composers"] if c["id"] == track["composerId"])


# ---------------------------------------------------------------- タイトル
def test_blog_name_suffix_without_space_before_colon_is_removed():
    # 実際の <title> は「…: CMSL クラシック名曲…」（コロンの前に空白がない）。og:title はない
    html = ("<html><head><title>ブラームス：交響曲第1番 ハ短調 Op.68 第1楽章: CMSL クラシック名曲サウンドライブラリー"
            "【ライセンスフリー素材音源 700曲（ラストシンフォニー）】</title></head><body></body></html>")
    art = bc.extract_article("1", f"{B}article/1.html", bc.parse(html), "CMSL")
    assert art.title == "ブラームス：交響曲第1番 ハ短調 Op.68 第1楽章"


@pytest.mark.parametrize("title, movement, work", [
    ("ブラームス：交響曲 第1番 ハ短調 Op.68 第1,2楽章 [2023]", "第1,2楽章", "交響曲 第1番 ハ短調 Op.68"),
    ("ベートーヴェン：交響曲 第5番 「運命」 第3-第4楽章", "第3-第4楽章", "交響曲 第5番 「運命」"),
    ("ベートーヴェン：交響曲第9番 作品125 第1楽章・第2楽章", "第1楽章・第2楽章", "交響曲第9番 作品125"),
    ("ベートーヴェン：交響曲 第5番 「運命」 第3, 4楽章", "第3, 4楽章", "交響曲 第5番 「運命」"),
    ("エルガー：《エニグマ変奏曲》 Op.36 第9変奏 「ニムロッド」", "第9変奏 「ニムロッド」", "《エニグマ変奏曲》 Op.36"),
    ("ドヴォルザーク：交響曲 第9番 「新世界より」", None, "交響曲 第9番 「新世界より」"),
])
def test_movement_formats(title, movement, work):
    info = bc.parse_title(title)
    assert (info["movement"], info["work"]) == (movement, work)


@pytest.mark.parametrize("title, ja_movement, en", [
    ("ベートーヴェン：交響曲 第3番 第1楽章 [2024]/ L.V.Beethoven：Symphony No.3 1. Allegro", "第1楽章",
     "L.V.Beethoven：Symphony No.3 1. Allegro"),
    ("ヴィヴァルディ：協奏曲「冬」 第1楽章 [2023] /Antonio Vivaldi：Winter", "第1楽章", "Antonio Vivaldi：Winter"),
    ("ショパン：練習曲 「革命」 / シューベルト：4つの即興曲 第3曲", None, None),  # 日本語の 2 曲目は英語ではない
    ("オンライン特別プログラム - モルダウ/クラシック名曲集 Vol.1", None, None),     # 日本語どうしの「/」
])
def test_english_title_separator(title, ja_movement, en):
    info = bc.parse_title(title)
    assert info["movement"] == ja_movement
    assert info["titleEn"] == en
    assert "/" not in (info["movement"] or "")


# ---------------------------------------------------------------- ファイル名
@pytest.mark.parametrize("name, composer, work, movements, tags", [
    ("064-Tchaikovsky-Symphony-No6-4th.mp3", "tchaikovsky", "symphony-no6", [4], []),
    ("Beethoven-Symphony-No5-3rd-4th-2020-IR.mp3", "beethoven", "symphony-no5", [3, 4], ["2020", "IR"]),
    ("Dvorak-SymNo9-1.mp3", "dvorak", "symphony-no9-1", [], []),
    ("Dvořák-Symphony-No.9-1st-2019-AR.mp3", "dvorak", "symphony-no9", [1], ["2019", "AR"]),
    ("Faure-Pavane-2020-IR.mp3", "faure", "pavane", [], ["2020", "IR"]),
])
def test_parse_file_name(name, composer, work, movements, tags):
    pf = bc.parse_file_name(name)
    assert (pf["composer"], pf["work"], pf["movements"], pf["tags"]) == (composer, work, movements, tags)


@pytest.mark.parametrize("tags, name, key", [
    (["2019", "AR"], "x.mp3", "2019 AR"),
    (["2019新録音AR"], "x.mp3", "2019 AR"),
    ([], "Beethoven-Symphony-No5-1st-2015.mp3", "2015"),
    ([], "Beethoven-Symphony-No6-1st-2020-AR-VR.mp3", "2020 AR"),
    ([], "Brahms-Symphony-No1-1st.mp3", ""),
])
def test_recording_key(tags, name, key):
    assert bc.recording_key(tags, name) == key


# ---------------------------------------------------------------- まとめ記事・録音・重複
REGULAR = [
    ("300", "2015年1月1日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第1楽章 / J.Brahms:Symphony No.4 I.",
     ["Brahms-Symphony-No4-1st-2015.mp3"], "交響曲 (Symphony)"),
    ("301", "2015年1月2日", "フォーレ：パヴァーヌ Op.50 / G.Faure:Pavane", ["Faure-Pavane-2015.mp3"], "管弦楽曲 (Orchestral)"),
]
VIRTUAL = ("302", "2020年5月1日", "オンライン特別プログラム - ブラームス 交響曲第4番 バーチャルコンサート / Online Virtual Concert",
           ["Brahms-Symphony-No4-1st-2020-IR.mp3", "Brahms-Symphony-No4-2nd-2020-IR.mp3", "Faure-Pavane-2020-IR.mp3",
            "Lange-Blumenlied-2020-IR.mp3"], "交響曲 (Symphony)")


def test_compilation_tracks_borrow_composer_work_and_genre_from_regular_articles():
    cat, rep = run(REGULAR + [VIRTUAL], overrides={"composerAliases": {}})  # 実ファイルの「Lange」の別名を外す
    t = by_id(cat)
    assert composer_of(cat, t["302-1"])["name"] == "ブラームス"
    assert work_of(cat, t["302-1"])["title"] == "交響曲 第4番 ホ短調 Op.98（2020 IR）"
    assert t["302-1"]["movement"] == "第2楽章" and t["302-1"]["tags"] == ["2020", "IR"]
    assert t["302-1"]["title"] == "ブラームス：交響曲 第4番 ホ短調 Op.98 第2楽章[2020][IR]"
    pavane = t["302-2"]
    assert composer_of(cat, pavane)["name"] == "フォーレ" and pavane["genreId"] == "orchestral"  # アンコールは記事のジャンルにしない
    # 通常の記事にない作曲家は不明のまま（overrides で直す）
    assert composer_of(cat, t["302-3"])["name"] == "作曲者不明"
    assert rep["noComposer"] == [f"{B}article/302.html"]


def test_compilation_track_override_by_track_id_and_alias_for_file_word():
    cat, rep = run(REGULAR + [VIRTUAL], overrides={
        "composerAliases": {"Lange": "ランゲ"},
        "composerEn": {"ランゲ": "Gustav Lange"},
        "tracks": {"302-3": {"work": "花の歌 Op.39", "genre": "piano"}},
    })
    t = by_id(cat)["302-3"]
    assert composer_of(cat, t)["nameEn"] == "Gustav Lange" and t["genreId"] == "piano"
    assert work_of(cat, t)["title"] == "花の歌 Op.39"
    assert rep["noComposer"] == [] and rep["ratios"]["unknownComposer"] == 0.0


def test_article_with_several_composers_in_file_names_is_a_compilation():
    arts = REGULAR + [("303", "2020年6月1日", "エルガー：《エニグマ変奏曲》 Op.36 第9変奏 「ニムロッド」 [2020][IR] / Edward Elgar：Enigma Variations",
                       ["Brahms-Symphony-No4-1st.mp3", "Elgar-Enigma-Variations-9-Nimrod-2020.mp3"], "管弦楽曲 (Orchestral)")]
    cat, _ = run(arts)
    t = by_id(cat)
    assert composer_of(cat, t["303-0"])["name"] == "ブラームス"
    assert work_of(cat, t["303-0"])["title"].startswith("交響曲 第4番 ホ短調 Op.98")
    assert t["303-0"]["movement"] == "第1楽章"
    # 通常の記事にない「Elgar」は、タイトルの英語名の姓で作曲家を決め、タイトルの作品名と楽章を使う
    assert composer_of(cat, t["303-1"])["name"] == "エルガー"
    assert work_of(cat, t["303-1"])["title"] == "《エニグマ変奏曲》 Op.36"
    assert t["303-1"]["movement"] == "第9変奏 「ニムロッド」"


def test_works_are_split_by_recording_and_labeled_only_when_needed():
    cat, _ = run(REGULAR + [
        ("310", "2023年1月1日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第1楽章 [2023]", ["Brahms-Symphony-No4-1st-2023.mp3"],
         "交響曲 (Symphony)"),
        ("311", "2023年1月2日", "ブラームス：交響曲第4番 ホ短調 Op.98 第2楽章 [2023]", ["Brahms-Symphony-No4-2nd-2023.mp3"],
         "交響曲 (Symphony)"),
    ])
    t = by_id(cat)
    assert t["310"]["workId"] == t["311"]["workId"] != t["300"]["workId"]  # 空白の有無は同じ作品、録音が違えば別の作品
    assert work_of(cat, t["310"])["title"] == "交響曲 第4番 ホ短調 Op.98（2023）"
    assert work_of(cat, t["300"])["title"] == "交響曲 第4番 ホ短調 Op.98（2015）"
    assert work_of(cat, t["301"])["title"] == "パヴァーヌ Op.50"  # 録音が 1 つだけなら書き添えない


def test_movements_of_multi_file_article_come_from_file_names_and_share_one_work():
    cat, _ = run([("320", "2023年1月1日", "ブラームス：交響曲 第1番 ハ短調 Op.68 第1,2楽章 [2023]",
                   ["Brahms-Symphony-No1-1st-2023.mp3", "Brahms-Symphony-No1-2nd-2023.mp3",
                    "Brahms-Symphony-No1-2nd-2023-KARAOKE.mp3"], "交響曲 (Symphony)")])
    t = by_id(cat)
    assert [t[f"320-{i}"]["movement"] for i in range(3)] == ["第1楽章", "第2楽章", "第2楽章"]
    assert len({t[f"320-{i}"]["workId"] for i in range(3)}) == 1  # カラオケ版も同じ録音（記事単位）


def test_duplicate_audio_keeps_first_published_track():
    same = "Beethoven-Symphony-No7-2nd-2022.mp3"
    cat, rep = run([
        ("330", "2022年10月29日", "ベートーヴェン：交響曲 第7番 イ長調 Op92 第2楽章 [2022]", [same], "交響曲 (Symphony)"),
        ("331", "2023年12月10日", "ベートーヴェン：交響曲 第7番 イ長調 Op92 第2楽章 [2022]", [same], "交響曲 (Symphony)"),
    ])
    assert [x["id"] for x in cat["tracks"]] == ["330"]
    assert rep["duplicateAudio"] == [{"id": "331", "sameAs": "330"}]


def test_unknown_composer_gets_no_english_name_from_title():
    # 日本語タイトルに作曲家がなく、英語タイトルだけにある記事。英語名の票を「作曲者不明」に入れない
    cat, _ = run([("340", "2020年1月1日", "いつくしみ深き -What A Friend- / Charles Crozat Converse：What A Friend",
                   ["What-A-Friend.mp3"], "国歌・賛美歌・民謡 (National Anthem/Hymn/Folk Song)")])
    unknown = cat["composers"][0]
    assert unknown["name"] == "作曲者不明" and unknown["nameEn"] is None


def test_track_ids_are_unchanged_by_compilation_and_recording_rules():
    cat, _ = run(REGULAR + [VIRTUAL])
    assert [x["id"] for x in cat["tracks"]] == ["300", "301", "302-0", "302-1", "302-2", "302-3"]
