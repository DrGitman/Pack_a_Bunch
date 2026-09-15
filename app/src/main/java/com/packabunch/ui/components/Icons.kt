package com.packabunch.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icon set, lifted straight from the artboards.
 *
 * Every icon in the mockups is inline SVG on a 24 grid at stroke 1.9 (2.3 where it needs
 * emphasis), so rather than redraw them by eye the path data is pasted in verbatim and
 * parsed. Same geometry as the design, no transcription drift.
 *
 * Add an icon by copying its `d` attribute out of the relevant `.dc.html` — do not
 * substitute a Material icon that is roughly similar, because the stroke weight and the
 * optical size will not match anything else on the screen.
 */

/** A circle as path data, so circle-based icons can use the same pipeline as the rest. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

private fun strokeIcon(
    name: String,
    vararg pathData: String,
    strokeWidth: Float = 1.9f,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    pathData.forEach { data ->
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black), // tinted at the call site
            strokeLineWidth = strokeWidth,
            strokeLineCap = cap,
            strokeLineJoin = join,
        )
    }
}.build()

private fun filledIcon(name: String, vararg pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    pathData.forEach { data ->
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }
}.build()

object PackIcons {

    // -- navigation ----------------------------------------------------------------------

    val Back: ImageVector = strokeIcon("Back", "M15 19l-7-7 7-7", strokeWidth = 2f)
    val Forward: ImageVector = strokeIcon("Forward", "M9 6l6 6-6 6", strokeWidth = 2f)
    val Close: ImageVector = strokeIcon("Close", "M18 6L6 18M6 6l12 12", strokeWidth = 2.1f)
    val ChevronDown: ImageVector = strokeIcon("ChevronDown", "M6 9l6 6 6-6", strokeWidth = 2f)

    val MoreVertical: ImageVector = strokeIcon(
        "MoreVertical",
        circle(12f, 5f, 0.6f),
        circle(12f, 12f, 0.6f),
        circle(12f, 19f, 0.6f),
        strokeWidth = 2.4f,
    )

    // -- the bottom nav pill --------------------------------------------------------------

    /** The packed-crate glyph that marks Projects. */
    val Projects: ImageVector = strokeIcon(
        "Projects",
        "M4 6h7v7H4zM13 6h7v4h-7zM13 13h7v5h-7zM4 15h7v3H4z",
    )

    val Plus: ImageVector = strokeIcon("Plus", "M12 5v14M5 12h14", strokeWidth = 2.3f)
    val Search: ImageVector = strokeIcon("Search", circle(11f, 11f, 7f), "M20 20l-3.6-3.6")

    val Settings: ImageVector = strokeIcon(
        "Settings",
        "M4 7h7M16 7h4M4 17h4M13 17h7",
        circle(13.5f, 7f, 2.3f),
        circle(10.5f, 17f, 2.3f),
    )

    // -- measuring --------------------------------------------------------------------------

    val Camera: ImageVector = strokeIcon(
        "Camera",
        "M4 8h3l1.4-2h7.2L17 8h3a1 1 0 011 1v9a1 1 0 01-1 1H4a1 1 0 01-1-1V9a1 1 0 011-1z",
        circle(12f, 13.4f, 3.2f),
    )

    val Ruler: ImageVector = strokeIcon(
        "Ruler",
        "M3 14.5L14.5 3l6.5 6.5L9.5 21z",
        "M7 12l2 2M10 9l2 2M13 6l2 2",
    )

    val Torch: ImageVector = strokeIcon(
        "Torch",
        "M9 3h6v3l-1.5 2v10a1.5 1.5 0 01-3 0V8L9 6z",
        "M9 6h6",
    )

    val Undo: ImageVector = strokeIcon("Undo", "M20 12a8 8 0 11-2.3-5.6", "M20 4v5h-5", strokeWidth = 2f)

    val Rotate: ImageVector = strokeIcon("Rotate", "M20 12a8 8 0 11-2.3-5.6", "M20 4v5h-5", strokeWidth = 2f)

    val Keyboard: ImageVector = strokeIcon(
        "Keyboard",
        "M3 7h18v10H3z",
        "M7 11h.01M11 11h.01M15 11h.01M8 14h8",
    )

    // -- items ---------------------------------------------------------------------------------

    val Cube: ImageVector = strokeIcon(
        "Cube",
        "M12 3l8 4.2v9.6L12 21l-8-4.2V7.2z",
        "M4 7.2l8 4.2 8-4.2",
        "M12 11.4V21",
    )

    val Copy: ImageVector = strokeIcon(
        "Copy",
        "M8 8h12v12H8z",
        "M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2",
    )

    val Trash: ImageVector = strokeIcon(
        "Trash",
        "M4 7h16",
        "M9 7V5h6v2",
        "M6 7l1 13h10l1-13",
    )

    val Pencil: ImageVector = strokeIcon("Pencil", "M4 20h4l10-10-4-4L4 16z", "M14 6l4 4")

    val Sparkle: ImageVector = strokeIcon(
        "Sparkle",
        "M12 3v3M12 18v3M3 12h3M18 12h3M6 6l2 2M16 16l2 2M18 6l-2 2M8 16l-2 2",
        strokeWidth = 2f,
    )

    val Library: ImageVector = strokeIcon(
        "Library",
        "M5 4h4v16H5zM11 4h4v16h-4z",
        "M17.5 4.6l3 15.2",
    )

    // -- state and status --------------------------------------------------------------------------

    val Check: ImageVector = strokeIcon("Check", "M20 6L9 17l-5-5", strokeWidth = 2.4f)

    val Info: ImageVector = strokeIcon(
        "Info",
        circle(12f, 12f, 9f),
        "M12 11v5",
        circle(12f, 7.8f, 0.9f),
    )

    val Warning: ImageVector = strokeIcon(
        "Warning",
        "M12 3.5L21.5 20h-19z",
        "M12 10v4.5",
        circle(12f, 17.4f, 0.8f),
    )

    val Lock: ImageVector = strokeIcon(
        "Lock",
        "M6 11h12v9H6z",
        "M8.5 11V7.5a3.5 3.5 0 017 0V11",
    )

    val Clock: ImageVector = strokeIcon("Clock", circle(12f, 12f, 8.5f), "M12 7v5.4l3.4 2")

    val Offline: ImageVector = strokeIcon(
        "Offline",
        "M3 3l18 18",
        "M5 12.5a10 10 0 014.2-2.4M19 12.5a10 10 0 00-4.6-2.5",
        "M8.5 16a5.5 5.5 0 013.5-1.6",
        circle(12f, 19.5f, 0.9f),
    )

    val Bell: ImageVector = strokeIcon(
        "Bell",
        "M6 9a6 6 0 1112 0v5l1.5 3h-15L6 14z",
        "M10 20a2 2 0 004 0",
    )

    val Person: ImageVector = strokeIcon("Person", circle(12f, 8f, 4f), "M4.5 20a7.5 7.5 0 0115 0")

    val Mail: ImageVector = strokeIcon("Mail", "M3 6h18v12H3z", "M3 7l9 6 9-6")

    val Google: ImageVector = filledIcon(
        "Google",
        "M21.35 11.1h-9.17v2.98h5.27c-.23 1.37-1.6 4.02-5.27 4.02a5.9 5.9 0 010-11.8c1.68 0 " +
            "2.81.72 3.46 1.33l2.36-2.27C16.46 3.9 14.49 3 12.18 3a9 9 0 100 18c5.2 0 " +
            "8.64-3.65 8.64-8.8 0-.59-.06-1.04-.15-1.5z",
    )
}
