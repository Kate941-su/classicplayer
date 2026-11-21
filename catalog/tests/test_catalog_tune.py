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
    assert work_of(cat, t["302-1"])["title"] == "交響曲 第4番 ホ短調 Op.98"
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


BRAHMS4_2023 = [
    ("310", "2023年1月1日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第1楽章 [2023]", ["Brahms-Symphony-No4-1st-2023.mp3"],
     "交響曲 (Symphony)"),
    ("311", "2023年1月2日", "ブラームス：交響曲第4番 ホ短調 Op.98 第2楽章 [2023]", ["Brahms-Symphony-No4-2nd-2023.mp3"],
     "交響曲 (Symphony)"),
]


def test_newer_recording_replaces_the_same_movement_and_records_the_replacement():
    old_2nd = ("305", "2015年1月3日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第2楽章", ["Brahms-Symphony-No4-2nd.mp3"],
               "交響曲 (Symphony)")
    old_3rd = ("306", "2015年1月4日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第3楽章", ["Brahms-Symphony-No4-3rd.mp3"],
               "交響曲 (Symphony)")
    cat, rep = run(REGULAR + [old_2nd, old_3rd] + BRAHMS4_2023)
    t = by_id(cat)
    assert "300" not in t and "305" not in t  # 2015 年の第1・第2楽章は 2023 年の録音に置き換え
    assert cat["replacedTracks"] == {"300": "310", "305": "311"}
    assert rep["supersededRecordings"] == [{"id": "300", "replacedBy": "310"}, {"id": "305", "replacedBy": "311"}]
    # 新しい録音にない第3楽章は古い録音のまま、同じ作品に楽章順で入る（空白の有無は同じ作品）
    assert t["310"]["workId"] == t["311"]["workId"] == t["306"]["workId"]
    assert [x["id"] for x in cat["tracks"] if x["workId"] == t["310"]["workId"]] == ["310", "311", "306"]
    assert work_of(cat, t["310"])["title"] == "交響曲第4番 ホ短調 Op.98"  # 録音は書き添えない。作品名は最新の曲（311）の表記
    assert "301" in t  # 録音が 1 つだけの作品はそのまま


def test_recording_year_wins_over_publication_date_and_one_article_is_one_recording():
    cat, _ = run([
        # 2024 年に公開された記事でも、録音が 2015 年なら 2020 年の録音より古い
        ("350", "2020年1月1日", "フォーレ：パヴァーヌ Op.50 [2020]", ["Faure-Pavane-2020.mp3"], "管弦楽曲 (Orchestral)"),
        ("351", "2024年1月1日", "フォーレ：パヴァーヌ Op.50 [2015]", ["Faure-Pavane-2015.mp3"], "管弦楽曲 (Orchestral)"),
        # 同じ記事のピアノ版と管弦楽版は置き換えない
        ("352", "2023年1月1日", "ラヴェル：亡き王女のためのパヴァーヌ [2023]",
         ["Ravel-Pavane-2023-piano.mp3", "Ravel-Pavane-2023-Orchestral.mp3"], "管弦楽曲 (Orchestral)"),
    ])
    assert [x["id"] for x in cat["tracks"]] == ["350", "352-0", "352-1"]
    assert cat["replacedTracks"] == {"351": "350"}


def test_whole_work_file_is_replaced_by_newer_movements_and_points_to_the_first():
    cat, _ = run([
        ("360", "2012年1月1日", "ブラームス：交響曲 第4番 ホ短調 Op.98 第1楽章-第2楽章", ["Brahms-Symphony-No4-1st-2nd.mp3"],
         "交響曲 (Symphony)"),
    ] + BRAHMS4_2023)
    assert cat["replacedTracks"] == {"360": "310"}


def test_movement_numbers():
    assert bc.movement_numbers("第2楽章") == [2]
    assert bc.movement_numbers("第1,2楽章") == [1, 2]
    assert bc.movement_numbers("第十二楽章") == [12]
    assert bc.movement_numbers("第１楽章 (リスト編)") == [1]
    assert bc.movement_numbers("第9変奏 「ニムロッド」") == []
    assert bc.movement_numbers(None) == []


def test_movements_of_multi_file_article_come_from_file_names_and_share_one_work():
    cat, _ = run([("320", "2023年1月1日", "ブラームス：交響曲 第1番 ハ短調 Op.68 第1,2楽章 [2023] / J.Brahms：Symphony No.1 1. Allegro",
                   ["Brahms-Symphony-No1-1st-2023.mp3", "Brahms-Symphony-No1-2nd-2023.mp3",
                    "Brahms-Symphony-No1-2nd-2023-KARAOKE.mp3"], "交響曲 (Symphony)")])
    t = by_id(cat)
    assert [t[f"320-{i}"]["movement"] for i in range(3)] == ["第1楽章", "第2楽章", "第2楽章"]
    assert all(t[f"320-{i}"]["titleEn"] is None for i in range(3))  # 記事の英語タイトルは曲ごとに正しくないので外す
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
    # 300・301 は消えずに、新しい録音（302-0・302-2）への置き換えとして残る
    assert [x["id"] for x in cat["tracks"]] == ["302-0", "302-1", "302-2", "302-3"]
    assert cat["replacedTracks"] == {"300": "302-0", "301": "302-2"}


@pytest.mark.parametrize("a, b", [
    ("交響曲 第5番 ホ短調 作品64", "交響曲 第5番 ホ短調 Op.64"),
    ("交響曲 第7番 イ長調 Op92", "交響曲 第7番 イ長調 Op.92"),
    ("アルペジオーネ・ソナタ イ短調 D.821", "アルペジオーネ・ソナタ イ短調 D821"),
    ("4つの即興曲 作品90、D899", "4つの即興曲 Op90, D899"),
    ("交響曲 第9番 ホ短調 Op.95 《新世界より》", "交響曲第9番 ホ短調 作品95 「新世界より」"),
])
def test_work_key_ignores_opus_notation_brackets_and_punctuation(a, b):
    assert bc.work_key(a) == bc.work_key(b)


def test_title_without_nickname_joins_the_only_nicknamed_title():
    groups = bc.work_groups({
        ("b", "交響曲第9番 ニ短調 Op.125 「合唱」"), ("b", "交響曲 第9番 ニ短調 Op.125"), ("b", "交響曲第9番 ニ短調 作品125"),
        ("v", "協奏曲集 Op.8『四季』より「夏」"), ("v", "協奏曲集 Op.8 『四季』より「冬」"), ("v", "協奏曲集 Op.8"),
    })
    assert len({groups[("b", w)] for w in ("交響曲第9番 ニ短調 Op.125 「合唱」", "交響曲 第9番 ニ短調 Op.125",
                                            "交響曲第9番 ニ短調 作品125")}) == 1
    # 愛称が違えば別の作品。愛称つきが 2 つあると、愛称のない作品名はどちらにも寄せない
    assert len({groups[("v", w)] for w in ("協奏曲集 Op.8『四季』より「夏」", "協奏曲集 Op.8 『四季』より「冬」",
                                            "協奏曲集 Op.8")}) == 3


def test_work_title_variants_are_one_work_and_newer_recording_replaces_older():
    cat, _ = run([
        ("370", "2019年3月1日", "ドヴォルザーク：交響曲 第9番 ホ短調 Op.95 《新世界より》 第4楽章 [2019][AR]",
         ["Dvorak-Symphony-No9-4th-2019-AR.mp3"], "交響曲 (Symphony)"),
        ("371", "2023年6月3日", "ドヴォルザーク：交響曲第9番 ホ短調 作品95 「新世界より」 第4楽章 [2023]",
         ["Dvorak-Symphony-No9-4th-2023.mp3"], "交響曲 (Symphony)"),
    ])
    assert [t["id"] for t in cat["tracks"]] == ["371"] and cat["replacedTracks"] == {"370": "371"}
    assert [w["title"] for w in cat["works"]] == ["交響曲第9番 ホ短調 作品95 「新世界より」"]  # 最新の曲の表記
