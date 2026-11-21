import SwiftUI
import UIKit

/// アートワークの描画（`docs/artwork.md`。Android の `ArtworkRenderer` と同じ図形）。
/// 模様の座標は辺の長さを 1 とした割合、イラストは 0〜100 で考える。
struct ArtworkCanvas: View {
    let spec: ArtworkSpec
    /// 横長の枠でのイラストの位置（ジャンルのカードは左下に文字があるので右に寄せる）。
    var illustrationAlignment: HorizontalAlignment = .center

    var body: some View {
        Canvas { context, size in
            ArtworkPainter(spec: spec, illustrationAlignment: illustrationAlignment).paint(in: &context, size: size)
        }
    }
}

struct ArtworkPainter {
    private static let saturation = 0.38
    private static let lightTop = 0.34
    private static let lightBottom = 0.18
    private static let hueShift = 28.0
    private static let accentLight = 0.78
    /// 模様はイラストの後ろなので薄くする。
    private static let patternOpacity = 70.0 / 255
    private static let illustrationLight = 0.86
    private static let detailLight = 0.2
    private static let strokeRatio = 0.018
    private static let viewBox = 100.0

    let spec: ArtworkSpec
    var illustrationAlignment: HorizontalAlignment = .center

    /// 背景のグラデーションと模様は枠いっぱいに敷き（模様は長い辺の正方形を中央で切り抜いた形）、
    /// ジャンルのイラストは短い辺の正方形に収める。
    func paint(in context: inout GraphicsContext, size: CGSize) {
        context.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .linearGradient(
                Gradient(colors: [
                    Self.hsl(spec.hue, Self.saturation, Self.lightTop),
                    Self.hsl(spec.hue + Self.hueShift, Self.saturation, Self.lightBottom),
                ]),
                startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)
            )
        )
        let accent = Self.hsl(spec.hue + Self.hueShift / 2, Self.saturation, Self.accentLight, opacity: Self.patternOpacity)
        let s = max(size.width, size.height)
        var patterns = Patterns(s: s, variant: spec.variant)
        patterns.draw(spec.pattern)
        let center = CGAffineTransform(translationX: (size.width - s) / 2, y: (size.height - s) / 2)
        context.stroke(patterns.strokes.applying(center), with: .color(accent), lineWidth: s * Self.strokeRatio)
        context.fill(patterns.fills.applying(center), with: .color(accent))
        paintIllustration(in: &context, size: size)
    }

    private func paintIllustration(in context: inout GraphicsContext, size: CGSize) {
        let side = min(size.width, size.height)
        let scale = side / Self.viewBox
        let dx: CGFloat = switch illustrationAlignment {
        case .leading: 0
        case .trailing: size.width - side
        default: (size.width - side) / 2
        }
        let transform = CGAffineTransform(a: scale, b: 0, c: 0, d: scale, tx: dx, ty: (size.height - side) / 2)
        let accent = Self.hsl(spec.hue + Self.hueShift / 2, Self.saturation, Self.illustrationLight)
        let detail = Self.hsl(spec.hue + Self.hueShift, Self.saturation, Self.detailLight)
        for shape in IllustrationShape.of(spec.illustration) {
            let path = shape.path.applying(transform)
            let color = shape.ink == .accent ? accent : detail
            if shape.strokeWidth == 0 {
                context.fill(path, with: .color(color))
            } else {
                context.stroke(
                    path, with: .color(color),
                    style: StrokeStyle(lineWidth: shape.strokeWidth * scale, lineCap: .round, lineJoin: .round)
                )
            }
        }
    }

    /// HSL（色相は度、s・l は 0〜1）→ Color。
    static func hsl(_ hue: Double, _ saturation: Double, _ lightness: Double, opacity: Double = 1) -> Color {
        let h = (hue.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        let c = (1 - abs(2 * lightness - 1)) * saturation
        let x = c * (1 - abs((h / 60).truncatingRemainder(dividingBy: 2) - 1))
        let m = lightness - c / 2
        // swiftlint:disable:next large_tuple
        let (r, g, b): (Double, Double, Double) = switch Int(h / 60) {
        case 0: (c, x, 0)
        case 1: (x, c, 0)
        case 2: (0, c, x)
        case 3: (0, x, c)
        case 4: (x, 0, c)
        default: (c, 0, x)
        }
        return Color(.sRGB, red: r + m, green: g + m, blue: b + m, opacity: opacity)
    }

    /// ロック画面（Now Playing）用の PNG。
    @MainActor
    static func png(spec: ArtworkSpec, size: CGFloat = 512) -> Data? {
        let renderer = ImageRenderer(content: ArtworkCanvas(spec: spec).frame(width: size, height: size))
        renderer.scale = 1
        return renderer.uiImage?.pngData()
    }
}

private struct Patterns {
    let s: CGFloat
    let variant: Int
    var strokes = Path()
    var fills = Path()

    init(s: CGFloat, variant: Int) {
        self.s = s
        self.variant = variant
    }

    /// variant から 0〜n-1 の値を取り出す（ビットの位置を変えて独立にする）。
    func pick(_ n: Int, shift: Int = 0) -> Int { ((variant >> shift) & 0xff) % n }

    mutating func draw(_ pattern: ArtworkSpec.Pattern) {
        switch pattern {
        case .concentricCircles: rings()
        case .waves: waves()
        case .cornerArcs: arcs()
        case .keyboard: keys()
        case .dotGrid: dots()
        case .arches: arches()
        case .diamonds: lattice()
        case .diagonalStripes: stripes()
        }
    }

    private func circle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> CGRect {
        CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
    }

    private mutating func line(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) {
        strokes.move(to: CGPoint(x: x0, y: y0))
        strokes.addLine(to: CGPoint(x: x1, y: y1))
    }

    mutating func rings() {
        let cx = s * (0.35 + CGFloat(pick(4)) * 0.1)
        let cy = s * (0.4 + CGFloat(pick(3, shift: 2)) * 0.1)
        for i in 1...(4 + pick(3, shift: 4)) { strokes.addEllipse(in: circle(cx, cy, s * 0.11 * CGFloat(i))) }
    }

    mutating func waves() {
        let lines = 5 + pick(3)
        let freq = 1.5 + Double(pick(3, shift: 2)) * 0.5
        for i in 0..<lines {
            let y0 = s * CGFloat(i + 1) / CGFloat(lines + 1)
            strokes.move(to: CGPoint(x: 0, y: y0))
            var x: CGFloat = 0
            while x <= s {
                strokes.addLine(to: CGPoint(x: x, y: y0 + CGFloat(sin(Double(x / s) * freq * 2 * .pi + Double(i))) * s * 0.04))
                x += s / 64
            }
        }
    }

    mutating func arcs() {
        let count = 5 + pick(3)
        let cx = pick(2, shift: 3) == 1 ? s : 0
        for i in 1...count {
            let r = s * 0.2 * CGFloat(i)
            var arc = Path()
            arc.addArc(center: CGPoint(x: cx, y: s), radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            strokes.addPath(arc)
        }
    }

    mutating func keys() {
        let white = 7 + pick(3)
        let w = s / CGFloat(white)
        let top = s * (0.25 + CGFloat(pick(3, shift: 2)) * 0.08)
        for i in 1..<white { line(w * CGFloat(i), top, w * CGFloat(i), s) }
        // 黒鍵: 2 つ・3 つの並び（ミとシの後ろには置かない）
        for i in 0..<(white - 1) where i % 7 != 2 && i % 7 != 6 {
            fills.addRect(CGRect(x: w * (CGFloat(i) + 0.68), y: top, width: w * 0.64, height: (s - top) * 0.6))
        }
    }

    mutating func dots() {
        let n = 5 + pick(3)
        let step = s / CGFloat(n + 1)
        let r = s * (0.018 + CGFloat(pick(3, shift: 2)) * 0.008)
        for i in 1...n {
            for j in 1...n { fills.addEllipse(in: circle(step * CGFloat(i), step * CGFloat(j), r)) }
        }
    }

    mutating func arches() {
        let n = 3 + pick(3)
        let w = s / CGFloat(n)
        let base = s * (0.7 + CGFloat(pick(2, shift: 2)) * 0.1)
        for i in 0..<n {
            let left = w * CGFloat(i) + w * 0.15
            let right = w * CGFloat(i + 1) - w * 0.15
            let r = (right - left) / 2
            strokes.move(to: CGPoint(x: left, y: s))
            strokes.addLine(to: CGPoint(x: left, y: base - r))
            strokes.addArc(
                center: CGPoint(x: left + r, y: base - r), radius: r,
                startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false
            )
            strokes.addLine(to: CGPoint(x: right, y: s))
        }
    }

    mutating func lattice() {
        let n = 4 + pick(3)
        let step = s / CGFloat(n)
        for i in -n...(n * 2) {
            line(step * CGFloat(i), 0, step * CGFloat(i) + s, s)
            line(step * CGFloat(i), 0, step * CGFloat(i) - s, s)
        }
    }

    mutating func stripes() {
        let n = 6 + pick(4)
        let step = s / CGFloat(n)
        for i in -n...(n * 2) { line(step * CGFloat(i), s, step * CGFloat(i) + s, 0) }
    }
}
