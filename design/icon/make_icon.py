"""アプリアイコン（iOS と Android）を元のイラストから作る。

  uv run --with Pillow python design/icon/make_icon.py [元画像.png]

- iOS: 1024×1024、不透明（AppIcon.appiconset/AppIcon.png）
- Android: アダプティブアイコンの前景（mipmap-*dpi/ic_launcher_foreground.png、108dp）。
  背景は色（values/colors.xml の ic_launcher_background）。ランチャーのマスクで見える中央 72dp に
  iOS と同じ配置で胸像を置き、マスクの外（視差効果で見えることがある）は胸像の下端を下へ伸ばして埋める
- Android のテーマアイコン（mipmap-*dpi/ic_launcher_monochrome.png）: 前景の明るさを不透明度にした単色版
  （ランチャーが色を付ける。暗い上着や線が抜けて顔立ちが分かるようにする）

元画像（既定 design/icon/source.png）は素材の再配布にならないよう、リポジトリに入れない（.gitignore）。
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "design/icon/source.png"
IOS_OUT = ROOT / "ios/CMSLClassical/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
ANDROID_RES = ROOT / "android/app/src/main/res"
BACKGROUND = (243, 231, 216)  # 落ち着いたクリーム色（Android は colors.xml の ic_launcher_background と同じ値にする）
BUST_RATIO = 940 / 1024  # アイコンの高さに対する胸像の高さ（下端はアイコンの下端にそろえる）

# Android の密度ごとの 108dp のピクセル数
ANDROID_DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

src = Image.open(SRC).convert("RGBA")


def bust(height: int) -> Image.Image:
    return src.resize((round(src.width * height / src.height), height), Image.LANCZOS)


def make_ios(size: int = 1024) -> None:
    fg = bust(round(size * BUST_RATIO))
    canvas = Image.new("RGBA", (size, size), BACKGROUND + (255,))
    canvas.alpha_composite(fg, ((size - fg.width) // 2, size - fg.height))
    canvas.convert("RGB").save(IOS_OUT, "PNG", optimize=True)
    print(IOS_OUT)


def make_android_foreground(size: int) -> Image.Image:
    visible = size * 72 // 108  # マスクで見える範囲（中央 72dp）
    inset = (size - visible) // 2
    fg = bust(round(visible * BUST_RATIO))
    bottom = size - inset  # 見える範囲の下端
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    x = (size - fg.width) // 2
    canvas.alpha_composite(fg, (x, bottom - fg.height))
    # 胸像の不透明な最後の行を下へ伸ばして、見える範囲の外まで胸像を続ける
    # （元画像の下端は透明な余白と筆のかすれなので、その上の行を使う）
    edge_y = last_solid_row(fg)
    top = bottom - fg.height + edge_y
    edge = fg.crop((0, edge_y, fg.width, edge_y + 1)).resize((fg.width, size - top))
    canvas.alpha_composite(edge, (x, top))
    return canvas


def last_solid_row(img: Image.Image) -> int:
    """不透明な画素の数が最大の 95% 以上ある、いちばん下の行。"""
    solid = img.getchannel("A").point(lambda v: 255 if v > 200 else 0)
    counts = [solid.crop((0, y, img.width, y + 1)).histogram()[255] for y in range(img.height)]
    threshold = max(counts) * 0.95
    return max(y for y, c in enumerate(counts) if c >= threshold)


def monochrome(fg: Image.Image) -> Image.Image:
    lightness = fg.convert("L").point(lambda v: min(255, max(0, (v - 40) * 2)))
    alpha = ImageChops.multiply(fg.getchannel("A"), lightness)
    out = Image.new("RGBA", fg.size, (255, 255, 255, 255))
    out.putalpha(alpha)
    return out


def make_android() -> None:
    # 大きく作ってから縮める（各密度で同じ見た目にする）
    foreground = make_android_foreground(ANDROID_DENSITIES["xxxhdpi"] * 2)
    layers = {"ic_launcher_foreground": foreground, "ic_launcher_monochrome": monochrome(foreground)}
    for density, px in ANDROID_DENSITIES.items():
        for name, master in layers.items():
            out = ANDROID_RES / f"mipmap-{density}" / f"{name}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            master.resize((px, px), Image.LANCZOS).save(out, "PNG", optimize=True)
            print(out)


make_ios()
make_android()
