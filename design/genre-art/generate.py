"""ジャンルのイラスト（design/genre-art/*.svg）から、両アプリの描画データを作る（docs/artwork.md）。

  python3 design/genre-art/generate.py            # Kotlin と Swift を書き出す
  uv run --with Pillow python design/genre-art/generate.py --preview out.png   # 確認用の一覧画像も作る

SVG は正本。手で描き、ここで読める範囲だけを使う:
- 要素: path（M L H V C Q Z。絶対座標だけ）、circle、ellipse、rect（角丸なし）、line、g
- transform: rotate(a cx cy)、translate(x y)（g と各要素）
- 色: fill / stroke が #fff（アクセント色）か #000（濃い色）か none。stroke-width（viewBox 0 0 100 100 の単位）
出力は M・L・C・Z だけの命令列（0=M x y、1=L x y、2=C x1 y1 x2 y2 x y、3=Z）。
"""
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
KOTLIN_OUT = ROOT / "android/core/artwork/src/main/kotlin/app/cmsl/classical/core/artwork/GenreIllustrations.kt"
SWIFT_OUT = ROOT / "ios/CMSLClassical/UI/GenreIllustrations.swift"
GENRES = ["symphony", "orchestral", "concerto", "piano", "chamber", "vocal", "other"]
NS = "{http://www.w3.org/2000/svg}"
KAPPA = 0.5522847498

M, L, C, Z = 0, 1, 2, 3


# --- 変換（2×3 の行列 a b c d e f: x' = a x + c y + e, y' = b x + d y + f） ---
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def multiply(p, q):
    """p の後に q をかける（q ∘ p ではなく、点に p → q の順で効く行列を返す）。"""
    a1, b1, c1, d1, e1, f1 = p
    a2, b2, c2, d2, e2, f2 = q
    return (
        a2 * a1 + c2 * b1,
        b2 * a1 + d2 * b1,
        a2 * c1 + c2 * d1,
        b2 * c1 + d2 * d1,
        a2 * e1 + c2 * f1 + e2,
        b2 * e1 + d2 * f1 + f2,
    )


def parse_transform(text):
    m = IDENTITY
    for name, args in re.findall(r"(\w+)\(([^)]*)\)", text or ""):
        v = [float(x) for x in re.split(r"[\s,]+", args.strip())]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "rotate":
            r = math.radians(v[0])
            cx, cy = (v[1], v[2]) if len(v) == 3 else (0, 0)
            cos, sin = math.cos(r), math.sin(r)
            t = (cos, sin, -sin, cos, cx - cos * cx + sin * cy, cy - sin * cx - cos * cy)
        else:
            raise ValueError(f"使えない transform: {name}")
        # SVG の transform は左から順に「外側」なので、点には右から効く
        m = multiply(t, m)
    return m


def apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


# --- 図形 → 命令列 ---
def parse_path(d):
    tokens = re.findall(r"[MLHVCQZmlhvcqzA-Za-z]|-?\d*\.?\d+(?:e-?\d+)?", d)
    ops, i, cmd = [], 0, None
    cur = start = (0.0, 0.0)

    def nums(n):
        nonlocal i
        v = [float(t) for t in tokens[i:i + n]]
        i += n
        return v

    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            if t not in "MLHVCQZ":
                raise ValueError(f"使えない path 命令: {t}（絶対座標の M L H V C Q Z だけ）")
            cmd = t
            i += 1
            if cmd == "Z":
                ops.append((Z,))
                cur = start
                continue
        if cmd == "M":
            cur = start = tuple(nums(2))
            ops.append((M, *cur))
            cmd = "L"
        elif cmd == "L":
            cur = tuple(nums(2))
            ops.append((L, *cur))
        elif cmd == "H":
            cur = (nums(1)[0], cur[1])
            ops.append((L, *cur))
        elif cmd == "V":
            cur = (cur[0], nums(1)[0])
            ops.append((L, *cur))
        elif cmd == "C":
            v = nums(6)
            ops.append((C, *v))
            cur = (v[4], v[5])
        elif cmd == "Q":
            qx, qy, x, y = nums(4)
            x0, y0 = cur
            ops.append((C, x0 + 2 / 3 * (qx - x0), y0 + 2 / 3 * (qy - y0), x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y), x, y))
            cur = (x, y)
        else:
            raise ValueError(f"命令の前に数値がある: {d}")
    return ops


def ellipse(cx, cy, rx, ry):
    kx, ky = rx * KAPPA, ry * KAPPA
    return [
        (M, cx + rx, cy),
        (C, cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
        (C, cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
        (C, cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
        (C, cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
        (Z,),
    ]


def element_ops(el):
    tag = el.tag.removeprefix(NS)
    f = lambda k: float(el.get(k, 0))  # noqa: E731
    if tag == "path":
        return parse_path(el.get("d"))
    if tag == "circle":
        return ellipse(f("cx"), f("cy"), f("r"), f("r"))
    if tag == "ellipse":
        return ellipse(f("cx"), f("cy"), f("rx"), f("ry"))
    if tag == "rect":
        x, y, w, h = f("x"), f("y"), f("width"), f("height")
        return [(M, x, y), (L, x + w, y), (L, x + w, y + h), (L, x, y + h), (Z,)]
    if tag == "line":
        return [(M, f("x1"), f("y1")), (L, f("x2"), f("y2"))]
    raise ValueError(f"使えない要素: {tag}")


def transform_ops(ops, m):
    out = []
    for op in ops:
        pts = [apply(m, op[k], op[k + 1]) for k in range(1, len(op), 2)]
        out.append((op[0], *[c for p in pts for c in p]))
    return out


INKS = {"#fff": "accent", "#ffffff": "accent", "white": "accent", "#000": "detail", "#000000": "detail", "black": "detail"}


def ink(value, what, name):
    if value in INKS:
        return INKS[value]
    raise ValueError(f"{name}: {what} は #fff か #000 か none（{value}）")


def shapes_of(svg):
    """[(ink, strokeWidth（0 なら塗り）, ops)] を描く順に返す。"""
    root = ET.parse(svg).getroot()
    if root.get("viewBox") != "0 0 100 100":
        raise ValueError(f"{svg.name}: viewBox は 0 0 100 100")
    out = []

    def walk(el, m):
        for child in el:
            tag = child.tag.removeprefix(NS)
            cm = multiply(parse_transform(child.get("transform")), m)
            if tag == "g":
                walk(child, cm)
                continue
            ops = transform_ops(element_ops(child), cm)
            fill = child.get("fill", "none" if tag == "line" else "#000")
            if fill != "none":
                out.append((ink(fill, "fill", svg.name), 0.0, ops))
            stroke = child.get("stroke", "none")
            if stroke != "none":
                out.append((ink(stroke, "stroke", svg.name), float(child.get("stroke-width", 1)), ops))

    walk(root, IDENTITY)
    return out


def num(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def flat(ops):
    return [num(v) if k else str(op[0]) for op in ops for k, v in enumerate(op)]


def kotlin(all_shapes):
    lines = [
        "// 生成したファイル。手で直さない（design/genre-art/generate.py で SVG から作る）",
        "package app.cmsl.classical.core.artwork",
        "",
        "/** ジャンルのイラスト（viewBox 0〜100）。キーはジャンル ID。描画は [IllustrationShape] を参照。 */",
        "@Suppress(\"LargeClass\", \"MagicNumber\", \"ktlint\")",
        "internal object GenreIllustrations {",
        "    val shapes: Map<String, List<IllustrationShape>> = mapOf(",
    ]
    for genre, shapes in all_shapes.items():
        lines.append(f'        "{genre}" to listOf(')
        for inkname, width, ops in shapes:
            values = ", ".join(f"{v}f" for v in flat(ops))
            lines.append(f"            IllustrationShape(Ink.{inkname.capitalize()}, {num(width)}f, floatArrayOf({values})),")
        lines.append("        ),")
    lines += ["    )", "}", ""]
    return "\n".join(lines)


def swift(all_shapes):
    lines = [
        "// 生成したファイル。手で直さない（design/genre-art/generate.py で SVG から作る）",
        "// swiftlint:disable all",
        "",
        "/// ジャンルのイラスト（viewBox 0〜100）。キーはジャンル ID。描画は `IllustrationShape` を参照。",
        "enum GenreIllustrations {",
        "    static let shapes: [String: [IllustrationShape]] = [",
    ]
    for genre, shapes in all_shapes.items():
        lines.append(f'        "{genre}": [')
        for inkname, width, ops in shapes:
            lines.append(f"            IllustrationShape(ink: .{inkname}, strokeWidth: {num(width)}, ops: [{', '.join(flat(ops))}]),")
        lines.append("        ],")
    lines += ["    ]", "}", ""]
    return "\n".join(lines)


def preview(all_shapes, out):
    """アプリと同じ色の考え方で並べた確認用の画像（大・小のサムネイル）。"""
    import colorsys

    from PIL import Image, ImageDraw

    def hsl(h, s, l, a=255):  # noqa: E741
        r, g, b = colorsys.hls_to_rgb((h % 360) / 360, l, s)
        return (round(r * 255), round(g * 255), round(b * 255), a)

    def points(ops, k):
        """命令列を折れ線（サブパスごと）にする。"""
        subs, cur = [], None
        for op in ops:
            if op[0] == M:
                cur = [(op[1] * k, op[2] * k)]
                subs.append(cur)
            elif op[0] == L:
                cur.append((op[1] * k, op[2] * k))
            elif op[0] == C:
                x0, y0 = cur[-1]
                for i in range(1, 25):
                    t = i / 24
                    u = 1 - t
                    cur.append((
                        u**3 * x0 + 3 * u * u * t * op[1] * k + 3 * u * t * t * op[3] * k + t**3 * op[5] * k,
                        u**3 * y0 + 3 * u * u * t * op[2] * k + 3 * u * t * t * op[4] * k + t**3 * op[6] * k,
                    ))
            elif op[0] == Z:
                cur.append(cur[0])
        return subs

    def tile(genre, shapes, hue, size):
        ss = 4
        n = size * ss
        img = Image.new("RGBA", (n, n))
        top, bottom = hsl(hue, 0.38, 0.34), hsl(hue + 28, 0.38, 0.18)
        grad = ImageDraw.Draw(img)
        for y in range(n):
            t = y / n
            grad.line([(0, y), (n, y)], fill=tuple(round(top[i] * (1 - t) + bottom[i] * t) for i in range(4)))
        layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        colors = {"accent": hsl(hue + 14, 0.38, 0.86), "detail": hsl(hue + 28, 0.38, 0.2)}
        k = n / 100
        for inkname, width, ops in shapes:
            for sub in points(ops, k):
                if width == 0:
                    d.polygon(sub, fill=colors[inkname])
                else:
                    w = max(1, round(width * k))
                    d.line(sub, fill=colors[inkname], width=w, joint="curve")
                    for p in (sub[0], sub[-1]):
                        d.ellipse((p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2), fill=colors[inkname])
        img.alpha_composite(layer)
        return img.resize((size, size), Image.LANCZOS)

    big, small, gap = 240, 48, 16
    sheet = Image.new("RGBA", (len(all_shapes) * (big + gap) + gap, big + small + gap * 3), (250, 250, 250, 255))
    for i, (genre, shapes) in enumerate(all_shapes.items()):
        hue = (i * 53 + 20) % 360
        x = gap + i * (big + gap)
        sheet.alpha_composite(tile(genre, shapes, hue, big), (x, gap))
        sheet.alpha_composite(tile(genre, shapes, hue, small), (x, big + gap * 2))
    sheet.save(out)
    print(out)


def main():
    all_shapes = {g: shapes_of(HERE / f"{g}.svg") for g in GENRES}
    for name, shapes in all_shapes.items():
        for _, _, ops in shapes:
            for op in ops:
                if any(not -1 <= v <= 101 for v in op[1:]):
                    raise ValueError(f"{name}: 座標が 0〜100 の外にある {op}")
    if "--preview" in sys.argv:
        preview(all_shapes, sys.argv[sys.argv.index("--preview") + 1])
        return
    KOTLIN_OUT.write_text(kotlin(all_shapes))
    SWIFT_OUT.write_text(swift(all_shapes))
    print(KOTLIN_OUT)
    print(SWIFT_OUT)


main()
