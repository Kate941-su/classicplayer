"""Seesaa風の模擬HTMLをキャッシュに置き、--offline で通しテストする。"""
import hashlib, json, sys, tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import build_catalog as bc

B = bc.BASE
SIDEBAR = f"""
<div class="side"><h3>最近の記事</h3>
<a href="{B}article/474143449.html">ベートーヴェン：悲愴 第2楽章</a>
<a href="{B}article/500000003.html">お知らせ</a>
<h3>カテゴリ</h3>
<a href="{B}category/111-1.html">器楽曲・Piano</a>
<a href="{B}category/222-1.html">交響曲</a>
<a href="{B}category/999-1.html">ラストシンフォニー(J-POP, 洋楽)</a>
</div>"""

def page(title, body):
    return f"""<html><head><title>{title} : CMSL クラシック名曲サウンドライブラリー</title>
<meta property="og:title" content="{title}"></head><body>{body}{SIDEBAR}</body></html>"""

def article(date, title, mp3s, cat_id, cat, extra=""):
    links = "".join(f'<a href="https://classical-sound.up.seesaa.net/image/{m}">🎵こちら</a>' for m in mp3s)
    return page(title, f"""<h2 class="date">{date}</h2><h3>{title}</h3>
<div class="text">解説… {links}{extra}</div>
<div class="footer">posted by cmsl at 18:18| <a href="{B}category/{cat_id}-1.html">{cat}</a> |</div>""")

PAGES = {
    B: page("CMSL", f'<a href="{B}article/474143449.html">a</a><a href="{B}index-2.html">次</a>'),
    B + "index-2.html": page("CMSL", f'<a href="{B}article/500000001.html">b</a><a href="{B}article/500000002.html">c</a><a href="{B}article/500000004.html">d</a>'),
    B + "category/111-1.html": page("器楽曲・Piano", f'<a href="{B}article/474143449.html">x</a><a href="{B}category/111-2.html">次</a>'),
    B + "category/111-2.html": page("器楽曲・Piano", ""),
    B + "category/222-1.html": page("交響曲", f'<a href="{B}article/500000001.html">x</a>'),
    B + "category/999-1.html": page("J-POP", f'<a href="{B}article/500000002.html">x</a>'),
    B + "article/474143449.html": article(
        "2020年03月20日",
        "ベートーヴェン：ピアノソナタ 第8番 ハ短調 Op.13 「悲愴」 第2楽章 [2020][AR] / L.V.Beethoven:Piano Sonata No.8 C minor, Op.13 &quot;Grande Sonate pathetique&quot;:II. Adagio cantabile",
        ["Beethoven-PianoSonata-No8-2nd-2020-AR.mp3"], 111, "器楽曲・Piano",
        extra='<audio src="https://classical-sound.up.seesaa.net/image/stream-only.mp3"></audio>'),
    B + "article/500000001.html": article(
        "2021年1月5日", "ドヴォルザーク：交響曲 第9番 ホ短調 「新世界より」 第4楽章 / A.Dvorak:Symphony No.9:IV.",
        ["Dvorak-Sym9-4th.mp3"], 222, "交響曲"),
    B + "article/500000002.html": article(
        "2021年2月1日", "ポップス曲 / Pop song", ["pop.mp3"], 999, "ラストシンフォニー(J-POP, 洋楽)"),
    B + "article/500000004.html": page("ストリーミングのみ", '<p>posted by cmsl at 10:00</p><audio src="https://classical-sound.up.seesaa.net/image/only.mp3"></audio>'),
}

def test_end_to_end():
    with tempfile.TemporaryDirectory() as d:
        cache = Path(d) / "cache"; cache.mkdir()
        for url, html in PAGES.items():
            (cache / (hashlib.sha1(url.encode()).hexdigest() + ".html")).write_text(html, encoding="utf-8")
        out = Path(d) / "catalog.json"
        bc.main(["--offline", "--cache", str(cache), "-o", str(out), "-q"])
        cat = json.loads(out.read_text(encoding="utf-8"))
        rep = json.loads(out.with_suffix(".report.json").read_text(encoding="utf-8"))

    tracks = {t["id"]: t for t in cat["tracks"]}
    assert set(tracks) == {"474143449", "500000001"}, tracks.keys()   # J-POP除外・ストリーミングのみ除外
    t = tracks["474143449"]
    assert t["audioUrl"].endswith("Beethoven-PianoSonata-No8-2nd-2020-AR.mp3")
    assert t["movement"] == "第2楽章"
    assert t["tags"] == ["2020", "AR"]
    assert t["publishedAt"] == "2020-03-20T18:18:00+09:00"
    assert '"Grande Sonate pathetique"' in t["titleEn"]
    genres = {g["name"]: g for g in cat["genres"]}
    assert set(genres) == {"ピアノ", "交響曲"}, genres   # genre_map.json で表示ジャンルにまとめる
    assert genres["ピアノ"]["nameEn"] == "Piano" and genres["交響曲"]["order"] == 10
    assert [g["order"] for g in cat["genres"]] == sorted(g["order"] for g in cat["genres"])
    comps = {c["id"]: c for c in cat["composers"]}
    assert comps[t["composerId"]]["name"] == "ベートーヴェン"
    assert comps[t["composerId"]]["nameEn"] == "L.V.Beethoven"
    work = next(w for w in cat["works"] if w["id"] == t["workId"])
    assert work["title"] == "ピアノソナタ 第8番 ハ短調 Op.13 「悲愴」", work
    assert tracks["500000001"]["genreId"] != t["genreId"]
    assert rep["excluded"] == 1 and len(rep["streamOnly"]) == 1
    assert not any("stream-only" in x["audioUrl"] for x in cat["tracks"])
    print("OK", json.dumps(t, ensure_ascii=False, indent=1))

if __name__ == "__main__":
    test_end_to_end()
