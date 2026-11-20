"""site/ の静的ページ（PLAN 0.8）: 内部リンク切れがなく、日英がそろい、クレジットがあること。"""
import re
from pathlib import Path

import pytest

SITE = Path(__file__).resolve().parents[2] / "site"
PAGES = sorted(SITE.rglob("*.html"))


@pytest.mark.parametrize("page", PAGES, ids=lambda p: str(p.relative_to(SITE)))
def test_internal_links_resolve(page):
    html = page.read_text(encoding="utf-8")
    for href in re.findall(r'(?:href|src)="([^"#]+)"', html):
        if re.match(r"^[a-z]+:", href):
            continue
        target = (page.parent / href).resolve()
        if href.endswith("/"):
            target = target / "index.html"
        assert target.exists(), f"{page.name}: {href} が見つからない"


@pytest.mark.parametrize("page", PAGES, ids=lambda p: str(p.relative_to(SITE)))
def test_page_basics(page):
    html = page.read_text(encoding="utf-8")
    assert re.search(r'<html lang="(ja|en)">', html)
    assert '<meta name="viewport"' in html and "<title>" in html
    assert "https://classical-sound.seesaa.net/" in html, "CMSL のクレジット（リンク）がない"
    if page.parent.name in ("privacy", "support"):
        assert "https://github.com/Kate941-su/classicplayer/issues" in html, "問い合わせ先がない"


def test_every_japanese_page_has_english_version():
    for ja in SITE.rglob("index.html"):
        en = ja.parent / "en.html"
        assert en.exists(), f"{en} がない"


def test_no_external_donation_links():
    # CLAUDE.md: 外部の寄付リンクは置かない
    for page in PAGES:
        html = page.read_text(encoding="utf-8").lower()
        for bad in ("ko-fi", "paypal", "buymeacoffee", "patreon", "github.com/sponsors"):
            assert bad not in html, f"{page}: {bad}"
