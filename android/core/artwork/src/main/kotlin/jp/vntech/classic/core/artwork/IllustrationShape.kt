package jp.vntech.classic.core.artwork

import android.graphics.Path

/** イラストの色。アクセント色（明るい）と、細部を描く濃い色。 */
internal enum class Ink { Accent, Detail }

/**
 * ジャンルのイラストの図形 1 つ（`docs/artwork.md`）。座標は 0〜100。
 * [strokeWidth] が 0 なら塗り、そうでなければその太さの線。
 * [ops] は命令列: 0=M x y、1=L x y、2=C x1 y1 x2 y2 x y、3=Z。
 */
internal class IllustrationShape(val ink: Ink, val strokeWidth: Float, val ops: FloatArray) {
    /** 0〜100 の座標を [scale] 倍し、([dx], [dy]) だけずらしたパス。 */
    @Suppress("MagicNumber")
    fun path(scale: Float, dx: Float, dy: Float): Path {
        val path = Path()
        var i = 0
        fun v(k: Int) = ops[i + k] * scale + if (k % 2 == 1) dx else dy
        while (i < ops.size) {
            when (ops[i].toInt()) {
                OP_MOVE -> path.moveTo(v(1), v(2)).also { i += 3 }
                OP_LINE -> path.lineTo(v(1), v(2)).also { i += 3 }
                OP_CUBIC -> path.cubicTo(v(1), v(2), v(3), v(4), v(5), v(6)).also { i += 7 }
                OP_CLOSE -> path.close().also { i += 1 }
                else -> error("イラストの命令が不正: ${ops[i]}")
            }
        }
        return path
    }

    companion object {
        const val OP_MOVE = 0
        const val OP_LINE = 1
        const val OP_CUBIC = 2
        const val OP_CLOSE = 3

        /** [ArtworkSpec.illustration] の図形。 */
        fun of(illustration: String): List<IllustrationShape> = GenreIllustrations.shapes[illustration]
            ?: GenreIllustrations.shapes.getValue(ArtworkSpec.ILLUSTRATION_OTHER)
    }
}
