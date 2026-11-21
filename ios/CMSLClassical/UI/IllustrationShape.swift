import SwiftUI

/// ジャンルのイラストの図形 1 つ（`docs/artwork.md`。Android の `IllustrationShape` と同じ）。座標は 0〜100。
/// `strokeWidth` が 0 なら塗り、そうでなければその太さの線。
/// `ops` は命令列: 0=M x y、1=L x y、2=C x1 y1 x2 y2 x y、3=Z。
struct IllustrationShape: Sendable {
    enum Ink: Sendable { case accent, detail }

    let ink: Ink
    let strokeWidth: Double
    let ops: [Double]

    var path: Path {
        var path = Path()
        var i = 0
        func point(_ k: Int) -> CGPoint { CGPoint(x: ops[i + k], y: ops[i + k + 1]) }
        while i < ops.count {
            switch Int(ops[i]) {
            case 0:
                path.move(to: point(1))
                i += 3
            case 1:
                path.addLine(to: point(1))
                i += 3
            case 2:
                path.addCurve(to: point(5), control1: point(1), control2: point(3))
                i += 7
            default:
                path.closeSubpath()
                i += 1
            }
        }
        return path
    }

    /// `ArtworkSpec.illustration` の図形。
    static func of(_ illustration: String) -> [IllustrationShape] {
        GenreIllustrations.shapes[illustration] ?? GenreIllustrations.shapes[ArtworkSpec.illustrationOther] ?? []
    }
}
