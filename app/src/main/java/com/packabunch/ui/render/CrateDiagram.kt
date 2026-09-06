package com.packabunch.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.packabunch.ui.theme.NumericFamily

/**
 * The small annotated crate beside the three dimension fields.
 *
 * It is here to answer one question — which number goes in which box — so the labels are
 * the point and the drawing is the supporting act. Geometry is lifted from the artboard's
 * SVG (a 104 × 92 viewport) and scaled, so it matches the mockup exactly at any size.
 *
 * Note it draws an *open* crate: the top face is the opening, not a lid. The whole model
 * assumes loading through an open top, and the diagram should not suggest otherwise.
 */
@Composable
fun CrateDiagram(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val sx = size.width / 104f
        val sy = size.height / 92f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)

        fun face(points: List<Offset>, color: Color) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, color)
        }

        // Opening, then the two visible walls, lightest to darkest.
        face(
            listOf(p(18f, 34f), p(52f, 18f), p(86f, 34f), p(52f, 50f)),
            Color(0xFFE7CFB6),
        )
        face(
            listOf(p(18f, 34f), p(52f, 50f), p(52f, 74f), p(18f, 58f)),
            Color(0xFFC98A5C),
        )
        face(
            listOf(p(52f, 50f), p(86f, 34f), p(86f, 58f), p(52f, 74f)),
            Color(0xFFA96B3D),
        )

        val rim = Path().apply {
            moveTo(p(18f, 34f).x, p(18f, 34f).y)
            lineTo(p(52f, 18f).x, p(52f, 18f).y)
            lineTo(p(86f, 34f).x, p(86f, 34f).y)
            lineTo(p(52f, 50f).x, p(52f, 50f).y)
            close()
        }
        drawPath(rim, Color(0xFF8E4E28), style = Stroke(width = 1.6f * sx))

        val ink = Color(0xFF3F2718)
        drawLine(ink, p(14f, 62f), p(14f, 38f), strokeWidth = 1.4f * sx)
        drawLine(ink, p(22f, 66f), p(50f, 80f), strokeWidth = 1.4f * sx)
        drawLine(ink, p(58f, 80f), p(84f, 66f), strokeWidth = 1.4f * sx)

        drawAxisLabel(measurer, "H", p(4f, 46f), ink, sx)
        drawAxisLabel(measurer, "W", p(30f, 73f), ink, sx)
        drawAxisLabel(measurer, "D", p(68f, 75f), ink, sx)
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
    scale: Float,
) {
    val laid = measurer.measure(
        text = text,
        style = TextStyle(fontFamily = NumericFamily, fontSize = (9f * scale).sp, color = color),
    )
    drawText(laid, topLeft = at)
}
