"""fetch_portraits.py（SPEC §3.6、PLAN 0.11）。Wikidata / Commons には通信せず、HttpGet を偽物に差し替える。"""
import hashlib
import io
import json
import sys
import urllib.parse
from pathlib import Path

import pytest
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import fetch_portraits as fp  # noqa: E402

THUMB = "https://upload.wikimedia.org/thumb/"


def png(w=800, h=1200, split=600):
    """上が赤、下が青の画像（切り抜きの位置を確かめる）。"""
    im = Image.new("RGB", (w, h), (0, 0, 255))
    im.paste((255, 0, 0), (0, 0, w, split))
    buf = io.BytesIO()
    im.save(buf, "PNG")
    return buf.getvalue()


def entity(qid, *, human=True, composer=True, images=(), label="X", occupation="Q36834"):
    claims = {"P31": [{"mainsnak": {"datavalue": {"value": {"id": "Q5" if human else "Q4167410"}}}}],
              "P106": [{"mainsnak": {"datavalue": {"value": {"id": occupation if composer else "Q33999"}}}}],
              "P18": [{"rank": rank, "mainsnak": {"datavalue": {"value": f}}} for f, rank in images]}
    return {"id": qid, "claims": claims, "labels": {"ja": {"value": label}, "en": {"value": label}}}


def file_info(name, license_code, *, short=None, nonfree=False, attribution=None):
    md = {"License": {"value": license_code}, "LicenseShortName": {"value": short or license_code.upper()},
          "LicenseUrl": {"value": "https://creativecommons.org/licenses/by-sa/4.0"},
          "Artist": {"value": '<a href="//commons.wikimedia.org/wiki/User:A">Painter &amp; Co</a>'},
          "ObjectName": {"value": f"Portrait {name}"}, "DateTimeOriginal": {"value": "1820"},
          "NonFree": {"value": "true" if nonfree else "false"}}
    if attribution is not None:
        md["AttributionRequired"] = {"value": "true" if attribution else "false"}
    return {"title": f"File:{name}", "imageinfo": [{
        "thumburl": THUMB + urllib.parse.quote(name), "url": THUMB + urllib.parse.quote(name),
        "descriptionurl": f"https://commons.wikimedia.org/wiki/File:{urllib.parse.quote(name)}",
        "extmetadata": md}]}


class FakeWikimedia:
    """URL を見て応答を返す。呼ばれた URL を記録する。"""

    def __init__(self, search=None, entities=None, files=None, image=None):
        self.search = search or {}        # 検索語 → [QID]
        self.entities = entities or {}    # QID → entity
        self.files = files or {}          # ファイル名 → imageinfo
        self.image = image or png()
        self.calls = []

    def __call__(self, url):
        self.calls.append(url)
        if url.startswith(THUMB):
            return self.image
        q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
        if q.get("action") == "wbsearchentities":
            body = {"search": [{"id": i} for i in self.search.get(q["search"], [])]}
        elif q.get("action") == "wbgetentities":
            body = {"entities": {i: self.entities[i] for i in q["ids"].split("|") if i in self.entities}}
        elif q.get("action") == "query":
            name = q["titles"].removeprefix("File:")
            page = self.files.get(name, {"title": q["titles"], "missing": ""})
            body = {"query": {"pages": {"1": page}}}
        else:
            raise AssertionError(f"想定外の URL: {url}")
        return json.dumps(body).encode()


def catalog(*composers):
    return {"composers": [{"id": cid, "name": name, "nameEn": en, "trackCount": 1} for cid, name, en in composers]}


# ---------------------------------------------------------------- 部品
@pytest.mark.parametrize("code", ["pd", "cc0", "cc-by-4.0", "cc-by-sa-3.0", "CC-BY-SA-4.0"])
def test_allowed_licenses(code):
    assert fp.license_ok({"license": code.lower()})[0]


@pytest.mark.parametrize("code", ["", "cc-by-nc-4.0", "cc-by-nd-4.0", "gfdl", "fal", "cc-by-sa-4.0-nc"])
def test_rejected_licenses(code):
    assert not fp.license_ok({"license": code})[0]


def test_nonfree_is_rejected_even_with_free_code():
    ok, why = fp.license_ok({"license": "cc0", "nonFree": True})
    assert not ok and "NonFree" in why


def test_p18_preferred_first_and_deprecated_dropped():
    e = entity("Q1", images=[("a.jpg", "normal"), ("b.jpg", "deprecated"), ("c.jpg", "preferred")])
    assert fp.claim_strings(e, "P18") == ["c.jpg", "a.jpg"]


def test_is_composer_requires_human_and_composer():
    assert fp.is_composer(entity("Q1"))
    assert not fp.is_composer(entity("Q2", human=False))
    assert not fp.is_composer(entity("Q3", composer=False))
    assert fp.is_composer(entity("Q4", occupation="Q21680663"))   # classical composer（ヴィドールなど）
    assert not fp.is_composer(entity("Q5", occupation="Q765778"))  # organist だけなら作曲家ではない


def test_strip_html():
    assert fp.strip_html('<a href="x">Painter &amp;  Co</a>\n') == "Painter & Co"
    assert fp.strip_html("<br>") is None and fp.strip_html(None) is None


def test_make_square_size_format_and_no_exif():
    out = Image.open(io.BytesIO(fp.make_square(png())))
    assert out.format == "JPEG" and out.size == (512, 512)
    assert not out.info.get("exif") and not out.getexif()


def test_make_square_focus_moves_the_crop():
    # 800×1200、600 行目まで赤。既定（縦 0.35）は上寄り（20〜820 行）→ 下端は青、上端は赤
    top_heavy = Image.open(io.BytesIO(fp.make_square(png()))).convert("RGB")
    assert top_heavy.getpixel((256, 5))[0] > 200
    # 縦 1.0 は一番下（400〜1200 行）→ 上の 1/4 だけ赤
    bottom = Image.open(io.BytesIO(fp.make_square(png(), focus=(0.5, 1.0)))).convert("RGB")
    assert bottom.getpixel((256, 60))[0] > 200 and bottom.getpixel((256, 300))[2] > 200


# ---------------------------------------------------------------- run
def test_suggest_only_proposes_composers_without_fetching_images(tmp_path):
    fake = FakeWikimedia(
        search={"L.V.Beethoven": ["Q255", "Q999"], "ベートーヴェン": ["Q255"], "ショパン": ["Q1268"]},
        entities={"Q255": entity("Q255", images=[("B.jpg", "normal")], label="Beethoven"),
                  "Q999": entity("Q999", human=False), "Q1268": entity("Q1268")})
    cat = catalog(("l-v-beethoven", "ベートーヴェン", "L.V.Beethoven"), ("chopin", "ショパン", None),
                  ("nobody", "誰か", None))
    portraits, mapping, report = fp.run(cat, {}, fake, tmp_path, suggest_only=True)

    assert portraits == {}
    assert mapping["l-v-beethoven"]["qid"] == "Q255"          # 作曲家である人物の候補が 1 つだけ
    assert [c["qid"] for c in mapping["l-v-beethoven"]["candidates"]] == ["Q255"]   # Q999 は人物でない
    assert mapping["l-v-beethoven"]["candidates"][0]["hasImage"]
    assert all(m["verified"] is False for m in mapping.values())   # verified は人が付ける
    assert mapping["nobody"]["qid"] is None
    assert {x["composerId"] for x in report["unmapped"]} == {"nobody"}
    assert {x["composerId"] for x in report["unverified"]} == {"l-v-beethoven", "chopin"}
    assert not any("commons" in u or u.startswith(THUMB) for u in fake.calls)
    assert not (tmp_path / "portraits").exists()


def test_existing_mapping_is_kept_and_not_searched_again(tmp_path):
    fake = FakeWikimedia()
    mapping = {"chopin": {"name": "ショパン", "qid": "Q1268", "verified": True}}
    fp.run(catalog(("chopin", "ショパン", None)), mapping, fake, tmp_path, suggest_only=True)
    assert mapping["chopin"] == {"name": "ショパン", "qid": "Q1268", "verified": True}
    assert fake.calls == []


def test_only_verified_mappings_are_fetched(tmp_path):
    fake = FakeWikimedia(
        entities={"Q1": entity("Q1", images=[("A.jpg", "normal")]), "Q2": entity("Q2", images=[("B.jpg", "normal")])},
        files={"A.jpg": file_info("A.jpg", "pd", short="Public domain"),
               "B.jpg": file_info("B.jpg", "pd")})
    mapping = {"a": {"qid": "Q1", "verified": True}, "b": {"qid": "Q2", "verified": False}}
    portraits, _, report = fp.run(catalog(("a", "A", None), ("b", "B", None)), mapping, fake, tmp_path)
    assert set(portraits) == {"a"} and report["ok"] == ["a"]
    assert not any("B.jpg" in u for u in fake.calls)
    # --accept-unverified のときだけ未確認も使う
    portraits, _, _ = fp.run(catalog(("a", "A", None), ("b", "B", None)), mapping, fake, tmp_path,
                             accept_unverified=True)
    assert set(portraits) == {"a", "b"}


def test_portrait_entry_and_file(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1", images=[("Old.jpg", "normal"), ("New.jpg", "preferred")])},
                         files={"New.jpg": file_info("New.jpg", "cc-by-sa-4.0", short="CC BY-SA 4.0"),
                                "Old.jpg": file_info("Old.jpg", "pd")})
    mapping = {"bach": {"qid": "Q1", "verified": True, "focus": [0.5, 0.2]}}
    portraits, _, report = fp.run(catalog(("bach", "バッハ", "J.S.Bach")), mapping, fake, tmp_path)

    p = portraits["bach"]
    jpg = (tmp_path / "portraits" / "bach.jpg").read_bytes()
    assert p == {
        "url": "portraits/bach.jpg", "width": 512, "height": 512, "sha256": hashlib.sha256(jpg).hexdigest(),
        "title": "Portrait New.jpg", "artist": "Painter & Co", "date": "1820",
        "license": "CC BY-SA 4.0", "licenseCode": "cc-by-sa-4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0",
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:New.jpg",
        "attributionRequired": True, "wikidata": "Q1",
    }
    assert not any("Old.jpg" in u for u in fake.calls)   # preferred の画像が使えたので normal は見ない
    assert report["ok"] == ["bach"]


def test_attribution_not_required_for_pd_and_cc0(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1", images=[("A.jpg", "normal")]),
                                   "Q2": entity("Q2", images=[("B.jpg", "normal")]),
                                   "Q3": entity("Q3", images=[("C.jpg", "normal")])},
                         files={"A.jpg": file_info("A.jpg", "pd"), "B.jpg": file_info("B.jpg", "cc0"),
                                "C.jpg": file_info("C.jpg", "pd", attribution=True)})
    mapping = {k: {"qid": q, "verified": True} for k, q in (("a", "Q1"), ("b", "Q2"), ("c", "Q3"))}
    portraits, _, _ = fp.run(catalog(("a", "A", None), ("b", "B", None), ("c", "C", None)), mapping, fake, tmp_path)
    assert not portraits["a"]["attributionRequired"] and not portraits["b"]["attributionRequired"]
    assert portraits["c"]["attributionRequired"]   # Commons が必要としているとき


def test_rejected_license_falls_back_to_next_image(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1", images=[("NC.jpg", "preferred"), ("Free.jpg", "normal")])},
                         files={"NC.jpg": file_info("NC.jpg", "cc-by-nc-4.0"), "Free.jpg": file_info("Free.jpg", "cc0")})
    portraits, _, report = fp.run(catalog(("a", "A", None)), {"a": {"qid": "Q1", "verified": True}}, fake, tmp_path)
    assert portraits["a"]["licenseCode"] == "cc0"
    assert report["rejected"] == [{"composerId": "a", "file": "NC.jpg", "reason": report["rejected"][0]["reason"]}]


def test_no_usable_image(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1"), "Q2": entity("Q2", images=[("NC.jpg", "normal")])},
                         files={"NC.jpg": file_info("NC.jpg", "gfdl")})
    mapping = {"a": {"qid": "Q1", "verified": True}, "b": {"qid": "Q2", "verified": True},
               "c": {"qid": "Q3", "verified": True, "exclude": True}}
    portraits, _, report = fp.run(catalog(("a", "A", None), ("b", "B", None), ("c", "C", None)), mapping, fake, tmp_path)
    assert portraits == {}
    assert report["noImage"] == [{"composerId": "a", "qid": "Q1"}]
    assert [r["composerId"] for r in report["rejected"]] == ["b"]
    assert not any("Q3" in u for u in fake.calls)   # exclude は取得しない


def test_file_override_is_used_instead_of_p18(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1", images=[("P18.jpg", "normal")])},
                         files={"Mine.jpg": file_info("Mine.jpg", "pd")})
    portraits, _, _ = fp.run(catalog(("a", "A", None)), {"a": {"qid": "Q1", "verified": True, "file": "Mine.jpg"}},
                             fake, tmp_path)
    assert portraits["a"]["sourceUrl"].endswith("File:Mine.jpg")
    assert not any("P18.jpg" in u for u in fake.calls)


def test_mapping_for_composer_not_in_catalog_is_ignored(tmp_path):
    fake = FakeWikimedia(entities={"Q1": entity("Q1", images=[("A.jpg", "normal")])}, files={"A.jpg": file_info("A.jpg", "pd")})
    portraits, _, _ = fp.run(catalog(("a", "A", None)), {"a": {"qid": "Q1", "verified": True},
                                                         "gone": {"qid": "Q1", "verified": True}}, fake, tmp_path)
    assert set(portraits) == {"a"}


def test_error_is_reported_and_others_continue(tmp_path):
    class Broken(FakeWikimedia):
        def __call__(self, url):
            if "Bad.jpg" in url:
                raise RuntimeError("取得失敗")
            return super().__call__(url)

    fake = Broken(entities={"Q1": entity("Q1", images=[("Bad.jpg", "normal")]),
                            "Q2": entity("Q2", images=[("Good.jpg", "normal")])},
                  files={"Good.jpg": file_info("Good.jpg", "pd")})
    mapping = {"a": {"qid": "Q1", "verified": True}, "b": {"qid": "Q2", "verified": True}}
    portraits, _, report = fp.run(catalog(("a", "A", None), ("b", "B", None)), mapping, fake, tmp_path)
    assert set(portraits) == {"b"} and [e["composerId"] for e in report["errors"]] == ["a"]


def test_http_uses_cache_and_user_agent(tmp_path, monkeypatch):
    seen = []

    class Resp(io.BytesIO):
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    def fake_urlopen(req, timeout):
        seen.append(req.get_header("User-agent"))
        return Resp(b"{}")

    monkeypatch.setattr(fp.urllib.request, "urlopen", fake_urlopen)
    http = fp.Http(tmp_path, "https://github.com/kate941-su/classicplayer", delay=0)
    assert http("https://www.wikidata.org/w/api.php?x=1") == b"{}"
    assert http("https://www.wikidata.org/w/api.php?x=1") == b"{}"   # 2 回目はキャッシュ
    assert seen == ["CMSLClassicalPlayer-PortraitFetcher/1.0 (https://github.com/kate941-su/classicplayer)"]
