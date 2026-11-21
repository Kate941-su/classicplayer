package app.cmsl.classical.core.artwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.sin

/** [ArtworkSpec] をビットマップに描く。模様の座標は辺の長さを 1 とした割合、イラストは 0〜100 で考える。 */
object ArtworkRenderer {
    private const val SATURATION = 0.38f
    private const val LIGHT_TOP = 0.34f
    private const val LIGHT_BOTTOM = 0.18f
    private const val HUE_SHIFT = 28f
    private const val ACCENT_LIGHT = 0.78f

    /** 模様はイラストの後ろなので薄くする。 */
    private const val PATTERN_ALPHA = 70
    private const val ILLUSTRATION_LIGHT = 0.86f
    private const val DETAIL_LIGHT = 0.2f
    private const val VIEWBOX = 100f
    private const val STROKE = 0.018f

    /**
     * 幅 [widthPx]・高さ [heightPx] に描く（省略すると正方形）。背景のグラデーションと模様は枠いっぱいに敷き
     * （模様は長い辺の正方形を中央で切り抜いた形）、ジャンルのイラストは短い辺の正方形に収めて中央に置く。
     */
    fun render(spec: ArtworkSpec, widthPx: Int, heightPx: Int = widthPx): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val bg = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                w,
                h,
                hsl(spec.hue.toFloat(), SATURATION, LIGHT_TOP),
                hsl(spec.hue + HUE_SHIFT, SATURATION, LIGHT_BOTTOM),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bg)
        val accent = hsl(spec.hue + HUE_SHIFT / 2, SATURATION, ACCENT_LIGHT)
        val pattern = maxOf(w, h)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            alpha = PATTERN_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = pattern * STROKE
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            alpha = PATTERN_ALPHA
            style = Paint.Style.FILL
        }
        canvas.withTranslation((w - pattern) / 2, (h - pattern) / 2) {
            Patterns(this, pattern, spec.variant, stroke, fill).draw(spec.pattern)
        }
        drawIllustration(canvas, spec, w, h)
        return bitmap
    }

    private fun drawIllustration(canvas: Canvas, spec: ArtworkSpec, w: Float, h: Float) {
        val side = minOf(w, h)
        val scale = side / VIEWBOX
        val dx = (w - side) / 2
        val dy = (h - side) / 2
        val colors = mapOf(
            Ink.Accent to hsl(spec.hue + HUE_SHIFT / 2, SATURATION, ILLUSTRATION_LIGHT),
            Ink.Detail to hsl(spec.hue + HUE_SHIFT, SATURATION, DETAIL_LIGHT),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (shape in IllustrationShape.of(spec.illustration)) {
            paint.color = colors.getValue(shape.ink)
            if (shape.strokeWidth == 0f) {
                paint.style = Paint.Style.FILL
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = shape.strokeWidth * scale
            }
            canvas.drawPath(shape.path(scale, dx, dy), paint)
        }
    }

    /** HSL（色相は度）→ ARGB。 */
    @Suppress("MagicNumber")
    internal fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    @Suppress("MagicNumber")
    private class Patterns(
        val canvas: Canvas,
        val s: Float,
        val variant: Int,
        val stroke: Paint,
        val fill: Paint,
    ) {
        /** variant から 0〜n-1 の値を取り出す（ビットの位置を変えて独立にする）。 */
        fun pick(n: Int, shift: Int = 0): Int = ((variant shr shift) and 0xff) % n

        fun draw(pattern: ArtworkPattern) = when (pattern) {
            ArtworkPattern.Rings -> rings()
            ArtworkPattern.Waves -> waves()
            ArtworkPattern.Arcs -> arcs()
            ArtworkPattern.Keys -> keys()
            ArtworkPattern.Dots -> dots()
            ArtworkPattern.Arches -> arches()
            ArtworkPattern.Lattice -> lattice()
            ArtworkPattern.Stripes -> stripes()
        }

        fun rings() {
            val cx = s * (0.35f + pick(4) * 0.1f)
            val cy = s * (0.4f + pick(3, 2) * 0.1f)
            val count = 4 + pick(3, 4)
            for (i in 1..count) canvas.drawCircle(cx, cy, s * 0.11f * i, stroke)
        }

        fun waves() {
            val lines = 5 + pick(3)
            val freq = 1.5f + pick(3, 2) * 0.5f
            for (i in 0 until lines) {
                val y0 = s * (i + 1) / (lines + 1)
                val path = Path().apply { moveTo(0f, y0) }
                var x = 0f
                while (x <= s) {
                    path.lineTo(x, y0 + sin(x / s * freq * 2 * PI + i).toFloat() * s * 0.04f)
                    x += s / 64
                }
                canvas.drawPath(path, stroke)
            }
        }

        fun arcs() {
            val count = 5 + pick(3)
            val fromRight = pick(2, 3) == 1
            val cx = if (fromRight) s else 0f
            for (i in 1..count) {
                val r = s * 0.2f * i
                canvas.drawArc(RectF(cx - r, s - r, cx + r, s + r), 180f, 180f, false, stroke)
            }
        }

        fun keys() {
            val white = 7 + pick(3)
            val w = s / white
            val top = s * (0.25f + pick(3, 2) * 0.08f)
            for (i in 1 until white) canvas.drawLine(w * i, top, w * i, s, stroke)
            // 黒鍵: 2 つ・3 つの並び（ミとシの後ろには置かない）
            for (i in 0 until white - 1) {
                if ((i % 7) == 2 || (i % 7) == 6) continue
                canvas.drawRect(w * (i + 0.68f), top, w * (i + 1.32f), top + (s - top) * 0.6f, fill)
            }
        }

        fun dots() {
            val n = 5 + pick(3)
            val step = s / (n + 1)
            val r = s * (0.018f + pick(3, 2) * 0.008f)
            for (i in 1..n) for (j in 1..n) canvas.drawCircle(step * i, step * j, r, fill)
        }

        fun arches() {
            val n = 3 + pick(3)
            val w = s / n
            val base = s * (0.7f + pick(2, 2) * 0.1f)
            for (i in 0 until n) {
                val left = w * i + w * 0.15f
                val right = w * (i + 1) - w * 0.15f
                val r = (right - left) / 2
                val path = Path().apply {
                    moveTo(left, s)
                    lineTo(left, base - r)
                    arcTo(RectF(left, base - 2 * r, right, base), 180f, 180f)
                    lineTo(right, s)
                }
                canvas.drawPath(path, stroke)
            }
        }

        fun lattice() {
            val n = 4 + pick(3)
            val step = s / n
            for (i in -n..n * 2) {
                canvas.drawLine(step * i, 0f, step * i + s, s, stroke)
                canvas.drawLine(step * i, 0f, step * i - s, s, stroke)
            }
        }

        fun stripes() {
            val n = 6 + pick(4)
            val step = s / n
            for (i in -n..n * 2) canvas.drawLine(step * i, s, step * i + s, 0f, stroke)
        }
    }
}
