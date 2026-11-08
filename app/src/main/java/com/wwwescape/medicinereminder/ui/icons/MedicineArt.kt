package com.wwwescape.medicinereminder.ui.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.wwwescape.medicinereminder.data.db.MedicineShape
import java.util.EnumMap
import kotlin.math.min

/**
 * Draws a medicine in any shape and color(s) on a plain [Canvas], so the same art serves Compose
 * (see MedicineIcon) and notification bitmaps.
 *
 * Every shape lives in a 100×64 box and is made of parts, each painted in color 1 or color 2.
 * The 3D look is layered on top: a darker copy of every part set slightly lower, a faint outline,
 * then the part itself, then white highlights and labels, then score lines. The darker copy is
 * derived from the chosen color, so any color works.
 */
object MedicineArt {

    const val WIDTH = 100f
    const val HEIGHT = 64f
    const val ASPECT = WIDTH / HEIGHT

    private enum class Tone { FIRST, SECOND }

    private class Art(
        val parts: List<Pair<Path, Tone>>,
        val highlights: List<Path> = emptyList(),
        val labels: List<Path> = emptyList(),
        val score: Path? = null,
        val needle: Path? = null,
        /** Polygons are softened by stroking them in their own color with round joins. */
        val rounded: Boolean = false,
    )

    private val cache = EnumMap<MedicineShape, Art>(MedicineShape::class.java)

    fun draw(canvas: Canvas, shape: MedicineShape, color1: Int, color2: Int, width: Float, height: Float) {
        val art = synchronized(cache) { cache.getOrPut(shape) { artFor(shape) } }
        val scale = min(width / WIDTH, height / HEIGHT)
        canvas.save()
        canvas.translate((width - WIDTH * scale) / 2f, (height - HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)

        val grow = if (art.rounded) 6f else 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        fun colorOf(tone: Tone) = if (tone == Tone.SECOND && shape.twoTone) color2 else color1
        fun fill(path: Path, color: Int, extra: Float) {
            paint.color = color
            paint.style = if (extra > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            paint.strokeWidth = extra
            canvas.drawPath(path, paint)
        }

        canvas.save()
        canvas.translate(0f, 3f)
        art.parts.forEach { (path, tone) -> fill(path, darken(colorOf(tone)), grow) }
        canvas.restore()
        art.parts.forEach { (path, _) -> fill(path, OUTLINE, grow + 1.2f) }
        art.parts.forEach { (path, tone) -> fill(path, colorOf(tone), grow) }
        art.labels.forEach { fill(it, LABEL, 0f) }
        art.highlights.forEach { fill(it, HIGHLIGHT, 0f) }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        art.score?.let {
            paint.color = darken(color1)
            canvas.drawPath(it, paint)
        }
        art.needle?.let {
            paint.color = NEEDLE
            canvas.drawPath(it, paint)
        }
        canvas.restore()
    }

    fun bitmap(shape: MedicineShape, color1: Int, color2: Int, widthPx: Int): Bitmap {
        val heightPx = (widthPx / ASPECT).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
            draw(Canvas(it), shape, color1, color2, widthPx.toFloat(), heightPx.toFloat())
        }
    }

    fun darken(color: Int): Int {
        val a = color ushr 24
        val r = ((color shr 16 and 0xFF) * 0.7f).toInt()
        val g = ((color shr 8 and 0xFF) * 0.7f).toInt()
        val b = ((color and 0xFF) * 0.7f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private const val OUTLINE = 0x2E000000
    private const val HIGHLIGHT = 0x73FFFFFF
    private const val LABEL = 0xB8FFFFFF.toInt()
    private const val NEEDLE = 0xFF9A9A9A.toInt()

    private fun p(data: String): Path = PathParser.createPathFromPathData(data)

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r},${y}H${x + w - r}A$r,$r 0 0 1 ${x + w},${y + r}V${y + h - r}A$r,$r 0 0 1 ${x + w - r},${y + h}" +
            "H${x + r}A$r,$r 0 0 1 $x,${y + h - r}V${y + r}A$r,$r 0 0 1 ${x + r},${y}Z"

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String =
        "M${cx - rx},${cy}A$rx,$ry 0 1 0 ${cx + rx},${cy}A$rx,$ry 0 1 0 ${cx - rx},${cy}Z"

    private fun one(data: String) = listOf(p(data) to Tone.FIRST)

    private fun artFor(shape: MedicineShape): Art = when (shape) {
        MedicineShape.ROUND -> Art(one(ellipse(50f, 30f, 22f, 22f)), listOf(p(ellipse(42f, 20f, 9f, 4f))))
        MedicineShape.ROUND_SCORED -> Art(
            one(ellipse(50f, 30f, 22f, 22f)), listOf(p(ellipse(42f, 20f, 9f, 4f))), score = p("M37,17L63,43"),
        )
        MedicineShape.ROUND_HALVES -> Art(
            listOf(p("M50,8A22,22 0 0 0 50,52Z") to Tone.FIRST, p("M50,8A22,22 0 0 1 50,52Z") to Tone.SECOND),
            listOf(p(ellipse(42f, 20f, 9f, 4f))),
        )
        MedicineShape.OVAL -> Art(one(ellipse(50f, 30f, 30f, 19f)), listOf(p(ellipse(40f, 19f, 12f, 4f))))
        MedicineShape.CAPLET -> Art(one(rect(14f, 16f, 72f, 28f, 14f)), listOf(p(rect(24f, 20f, 40f, 5f, 2.5f))))
        MedicineShape.CAPLET_SCORED -> Art(
            one(rect(14f, 16f, 72f, 28f, 14f)), listOf(p(rect(24f, 20f, 22f, 5f, 2.5f))), score = p("M50,17V43"),
        )
        MedicineShape.CAPSULE -> Art(
            listOf(
                p("M50,17H25A13,13 0 0 0 25,43H50Z") to Tone.FIRST,
                p("M50,17H75A13,13 0 0 1 75,43H50Z") to Tone.SECOND,
            ),
            listOf(p(rect(22f, 21f, 50f, 5f, 2.5f))),
        )
        MedicineShape.SOFTGEL -> Art(one(ellipse(50f, 30f, 27f, 16f)), listOf(p(ellipse(42f, 22f, 13f, 5f))))
        MedicineShape.TRIANGLE -> Art(one("M50,14L73,47L27,47Z"), listOf(p(ellipse(47f, 27f, 3f, 7f))), rounded = true)
        MedicineShape.SQUARE -> Art(one(rect(28f, 8f, 44f, 44f, 10f)), listOf(p(rect(34f, 13f, 22f, 5f, 2.5f))))
        MedicineShape.DIAMOND -> Art(one("M50,12L74,30L50,48L26,30Z"), listOf(p(ellipse(45f, 22f, 6f, 3f))), rounded = true)
        MedicineShape.HEXAGON -> Art(
            one("M50,11L68,21V39L50,49L32,39V21Z"), listOf(p(ellipse(44f, 19f, 7f, 3f))), rounded = true,
        )
        MedicineShape.HEART -> Art(
            one("M50,52C20,34 22,10 38,12C44,12 48,16 50,20C52,16 56,12 62,12C78,10 80,34 50,52Z"),
            listOf(p(ellipse(36f, 20f, 6f, 3f))),
        )
        MedicineShape.DROPS -> Art(
            listOf(p(rect(36f, 28f, 28f, 30f, 8f)) to Tone.FIRST, p("M44,28V18Q50,2 56,18V28Z") to Tone.SECOND),
            listOf(p(rect(40f, 33f, 5f, 18f, 2.5f))),
        )
        MedicineShape.SYRUP -> Art(
            listOf(p(rect(34f, 18f, 32f, 40f, 6f)) to Tone.FIRST, p(rect(38f, 4f, 24f, 12f, 2f)) to Tone.SECOND),
            labels = listOf(p(rect(38f, 30f, 24f, 16f, 2f))),
        )
        MedicineShape.NASAL_SPRAY -> Art(
            listOf(
                p(rect(34f, 30f, 32f, 28f, 6f)) to Tone.FIRST,
                p("M44,26V16Q50,2 56,16V26Z") to Tone.SECOND,
                p(rect(38f, 24f, 24f, 7f, 2f)) to Tone.SECOND,
            ),
            listOf(p(rect(38f, 35f, 5f, 16f, 2.5f))),
        )
        MedicineShape.INHALER -> Art(
            listOf(
                p(rect(42f, 2f, 18f, 24f, 4f)) to Tone.SECOND,
                p("M38,18H64Q66,18 66,20V54Q66,58 62,58H24Q20,58 20,54V46Q20,42 24,42H38Z") to Tone.FIRST,
            ),
            listOf(p(rect(42f, 22f, 5f, 26f, 2.5f))),
        )
        MedicineShape.NEBULIZER -> Art(
            listOf(
                p(rect(6f, 26f, 48f, 30f, 6f)) to Tone.FIRST,
                p(ellipse(44f, 41f, 5f, 5f)) to Tone.SECOND,
                p("M70,16Q88,12 92,26Q92,40 78,40Q66,38 70,16Z") to Tone.SECOND,
            ),
            listOf(p(rect(10f, 29f, 30f, 3f, 1.5f))),
            labels = listOf(p(rect(12f, 33f, 20f, 9f, 2f))),
            // The tube from the machine to the mask.
            needle = p("M54,44C66,44 62,28 72,28"),
        )
        MedicineShape.SYRINGE -> Art(
            listOf(
                p(rect(66f, 16f, 4f, 28f, 1f)) to Tone.SECOND,
                p("M68,27H84V33H68Z") to Tone.SECOND,
                p(rect(84f, 18f, 5f, 24f, 1f)) to Tone.SECOND,
                p(rect(22f, 21f, 46f, 18f, 3f)) to Tone.FIRST,
            ),
            listOf(p(rect(26f, 24f, 36f, 4f, 2f))),
            needle = p("M22,30H5"),
        )
        MedicineShape.PEN -> Art(
            listOf(p(rect(72f, 24f, 18f, 12f, 5f)) to Tone.SECOND, p(rect(8f, 21f, 66f, 18f, 9f)) to Tone.FIRST),
            labels = listOf(p(rect(30f, 26f, 18f, 8f, 2f))),
        )
        MedicineShape.VIAL -> Art(
            listOf(p(rect(36f, 20f, 28f, 38f, 4f)) to Tone.FIRST, p(rect(35f, 6f, 30f, 14f, 2f)) to Tone.SECOND),
            labels = listOf(p(rect(40f, 32f, 20f, 14f, 2f))),
        )
        MedicineShape.AMPOULE -> Art(
            listOf(
                p("M40,58H60Q64,58 64,54V32Q64,26 57,23V18Q61,14 57,10V6Q50,1 43,6V10Q39,14 43,18V23Q36,26 36,32V54Q36,58 40,58Z") to Tone.FIRST,
                p(rect(42f, 17f, 16f, 4f, 2f)) to Tone.SECOND,
            ),
            listOf(p(rect(40f, 32f, 5f, 18f, 2.5f))),
        )
        MedicineShape.TUBE -> Art(
            listOf(
                p(rect(82f, 22f, 12f, 16f, 2f)) to Tone.SECOND,
                p("M14,16H68L83,23V37L68,44H14Q10,44 10,40V20Q10,16 14,16Z") to Tone.FIRST,
            ),
            listOf(p(rect(18f, 20f, 44f, 4f, 2f))),
        )
        MedicineShape.PATCH -> Art(
            listOf(p(rect(16f, 10f, 68f, 40f, 10f)) to Tone.FIRST, p(rect(32f, 20f, 36f, 20f, 5f)) to Tone.SECOND),
        )
        MedicineShape.SACHET -> Art(
            listOf(p(rect(24f, 8f, 52f, 48f, 4f)) to Tone.FIRST, p(rect(24f, 8f, 52f, 9f, 3f)) to Tone.SECOND),
            labels = listOf(p(rect(32f, 26f, 36f, 18f, 3f))),
        )
        MedicineShape.SUPPOSITORY -> Art(
            one("M16,30Q30,14 56,16H78Q86,16 86,30Q86,44 78,44H56Q30,46 16,30Z"),
            listOf(p(rect(40f, 20f, 30f, 4f, 2f))),
        )
    }
}
