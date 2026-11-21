import SwiftUI

/// アートワークの描画（`docs/artwork.md`）。背景は色相のグラデーション。図形はジャンルごとのパターン（PLAN 2.8）。
struct ArtworkCanvas: View {
    let spec: ArtworkSpec

    var body: some View {
        Canvas { context, size in
            ArtworkPainter(spec: spec).paint(in: &context, size: size)
        }
    }
}

struct ArtworkPainter {
    let spec: ArtworkSpec

    func paint(in context: inout GraphicsContext, size: CGSize) {
        let rect = CGRect(origin: .zero, size: size)
        context.fill(
            Path(rect),
            with: .linearGradient(
                Gradient(colors: [hsl(spec.hue, 0.38, 0.34), hsl(spec.hue + 28, 0.38, 0.18)]),
                startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)
            )
        )
    }

    /// HSL → Color（s・l は 0〜1）。
    func hsl(_ hue: Double, _ s: Double, _ l: Double, opacity: Double = 1) -> Color {
        let h = (hue.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 360
        let v = l + s * min(l, 1 - l)
        let sv = v == 0 ? 0 : 2 * (1 - l / v)
        return Color(hue: h, saturation: sv, brightness: v, opacity: opacity)
    }
}
