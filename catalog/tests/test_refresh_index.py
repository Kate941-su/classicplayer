"""差分巡回 --refresh-index（PLAN 0.4）。通信は偽の transport で置き換え、CMSL には接続しない。"""
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE.parent), str(HERE)]
import build_catalog as bc  # noqa: E402
from test_build_catalog import B, PAGES, article, page  # noqa: E402


class FakeSite:
    def __init__(self, pages):
        self.pages = dict(pages)
        self.calls: list[str] = []

    def __call__(self, url):
        self.calls.append(url)
        if url in self.pages:
            return 200, self.pages[url].encode("utf-8"), "utf-8"
        return 404, None, "utf-8"


def crawl(cache, site, refresh_index):
    sleeps = []
    f = bc.Fetcher(cache, 1.0, offline=False, refresh_index=refresh_index,
                   transport=site, sleep=sleeps.append, robots=None)
    pages, _, _ = bc.crawl(f, None, verbose=False)
    return f, pages, sleeps


LISTING = {u for u in PAGES if bc.is_listing(u)}
MISSING = {B + "article/500000003.html"}  # サイドバーにリンクがあるが 404 の記事


def test_is_listing():
    assert bc.is_listing(B) and bc.is_listing(B + "index-2.html") and bc.is_listing(B + "category/1-1.html")
    assert not bc.is_listing(B + "article/123.html")


def test_first_run_fetches_everything(tmp_path):
    site = FakeSite(PAGES)
    f, pages, _ = crawl(tmp_path, site, refresh_index=True)
    assert set(site.calls) == set(PAGES) | MISSING
    assert set(pages) == set(PAGES)


def test_second_run_fetches_only_listing_pages(tmp_path):
    crawl(tmp_path, FakeSite(PAGES), refresh_index=True)
    site = FakeSite(PAGES)
    f, pages, _ = crawl(tmp_path, site, refresh_index=True)
    assert set(site.calls) == LISTING        # 記事はキャッシュから
    assert set(f.fetched) == LISTING
    assert set(pages) == set(PAGES)          # 記事も解析対象に入っている


def test_second_run_without_refresh_fetches_nothing(tmp_path):
    crawl(tmp_path, FakeSite(PAGES), refresh_index=False)
    site = FakeSite(PAGES)
    crawl(tmp_path, site, refresh_index=False)
    assert site.calls == []


def test_new_article_is_found_via_refreshed_index(tmp_path):
    crawl(tmp_path, FakeSite(PAGES), refresh_index=True)
    new_url = B + "article/600000001.html"
    updated = dict(PAGES)
    updated[B] = page("CMSL", f'<a href="{B}article/474143449.html">a</a><a href="{new_url}">新</a>'
                              f'<a href="{B}index-2.html">次</a>')
    updated[new_url] = article("2026年11月1日", "モーツァルト：トルコ行進曲 / W.A.Mozart:Turkish March",
                               ["Mozart-Turkish.mp3"], 111, "器楽曲・Piano")
    site = FakeSite(updated)
    _, pages, _ = crawl(tmp_path, site, refresh_index=True)
    assert set(site.calls) == LISTING | {new_url}
    assert new_url in pages


def test_failed_refresh_falls_back_to_cached_listing(tmp_path):
    crawl(tmp_path, FakeSite(PAGES), refresh_index=True)

    def broken(url):
        raise OSError("network down")

    f = bc.Fetcher(tmp_path, 1.0, offline=False, refresh_index=True, transport=broken,
                   sleep=lambda s: None, robots=None)
    assert f.get(B) == PAGES[B]


def test_waits_at_least_delay_between_requests(tmp_path):
    _, _, sleeps = crawl(tmp_path, FakeSite(PAGES), refresh_index=True)
    # 1 回目以外は、直前の取得から delay（1.0 秒）未満なら待っている
    assert len(sleeps) >= len(PAGES) - 1
    assert all(0 < s <= 1.0 for s in sleeps)


def test_delay_below_one_second_is_rejected(tmp_path):
    with pytest.raises(ValueError):
        bc.Fetcher(tmp_path, 0.5, offline=True)
    with pytest.raises(SystemExit):
        bc.main(["--offline", "--cache", str(tmp_path), "--delay", "0.9", "-q"])
