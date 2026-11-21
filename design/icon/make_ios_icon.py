"""アプリアイコン（iOS 1024×1024、不透明）を元のイラストから作る。

  uv run --with Pillow python design/icon/make_ios_icon.py [元画像.png]

元画像（既定 design/icon/source.png）は素材の再配布にならないよう、リポジトリに入れない（.gitignore）。
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "design/icon/source.png"
OUT = ROOT / "ios/CMSLClassical/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
SIZE = 1024
BACKGROUND = (243, 231, 216)  # 落ち着いたクリーム色
HEIGHT = 940  # 胸像の高さ（下端はアイコンの下端にそろえる）

src = Image.open(SRC).convert("RGBA")
scale = HEIGHT / src.height
fg = src.resize((round(src.width * scale), HEIGHT), Image.LANCZOS)
canvas = Image.new("RGBA", (SIZE, SIZE), BACKGROUND + (255,))
canvas.alpha_composite(fg, ((SIZE - fg.width) // 2, SIZE - fg.height))
canvas.convert("RGB").save(OUT, "PNG", optimize=True)
print(OUT)
