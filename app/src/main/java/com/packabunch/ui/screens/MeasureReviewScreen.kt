package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Dimensions
import com.packabunch.packing.MeasurementSource
import com.packabunch.ui.components.DimensionField
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ProvenanceBadge
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.Stepper
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLength
import com.packabunch.ui.format.parseLengthToMm
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Review measurements — `design/artboards/MeasureReview.dc.html`.
 *
 * The screen the AR path is not allowed to skip. Three things it guarantees:
 *
 *  - **Every value is editable.** A camera estimate that cannot be corrected is a camera
 *    estimate the user is forced to trust, and they should not have to.
 *  - **Every value carries where it came from**, and editing one flips its badge to "typed
 *    in" the moment it changes — because it is then a typed value, whatever produced the
 *    first draft.
 *  - **No confidence score.** ARCore's tracking state is not calibrated accuracy, so there
 *    is no percentage here and no colour-coding pretending to be one.
 */
@Composable
fun MeasureReviewScreen(
    dimensions: Dimensions,
    sources: Map<Axis3, MeasurementSource>,
    edgeGapMm: Int,
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    onConfirm: (Dimensions, Map<Axis3, MeasurementSource>, Int) -> Unit,
    onMeasureAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableStateOf(formatLength(dimensions.widthMm, unit)) }
    var depth by remember { mutableStateOf(formatLength(dimensions.depthMm, unit)) }
    var height by remember { mutableStateOf(formatLength(dimensions.heightMm, unit)) }
    var gap by remember { mutableIntStateOf(edgeGapMm) }

    // Provenance follows the value. Touch a number and it becomes yours, not the camera's.
    var widthSource by remember { mutableStateOf(sources[Axis3.WIDTH] ?: MeasurementSource.TYPED_IN) }
    var depthSource by remember { mutableStateOf(sources[Axis3.DEPTH] ?: MeasurementSource.TYPED_IN) }
    var heightSource by remember { mutableStateOf(sources[Axis3.HEIGHT] ?: MeasurementSource.TYPED_IN) }

    val widthMm = parseLengthToMm(width, unit)
    val depthMm = parseLengthToMm(depth, unit)
    val heightMm = parseLengthToMm(height, unit)
    val valid = widthMm != null && depthMm != null && heightMm != null

    ScreenScaffold(modifier) {
        PackAppBar(title = "Measure", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 12.dp)) {
            ScreenHeading("Check the measurements")
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(elevation = 8.dp, contentPadding = 0.dp) {
                Column(Modifier.padding(Spacing.base)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Inside measurements",
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5f.sp,
                        )
                    UnitToggle(unit = unit, onUnitChange = { next ->
                        width = parseLengthToMm(width, unit)?.let { formatLength(it, next) } ?: width
                        depth = parseLengthToMm(depth, unit)?.let { formatLength(it, next) } ?: depth
                        height = parseLengthToMm(height, unit)?.let { formatLength(it, next) } ?: height
                        onUnitChange(next)
                    }, compact = true)
                    }

                    Spacer(Modifier.height(14.dp))

                    ReviewRow(
                        label = "Width",
                        value = width,
                        onValueChange = { width = it; widthSource = MeasurementSource.TYPED_IN },
                        unit = unit,
                        source = widthSource,
                        error = width.isNotEmpty() && widthMm == null,
                    )
                    Spacer(Modifier.height(10.dp))
                    ReviewRow(
                        label = "Depth",
                        value = depth,
                        onValueChange = { depth = it; depthSource = MeasurementSource.TYPED_IN },
                        unit = unit,
                        source = depthSource,
                        error = depth.isNotEmpty() && depthMm == null,
                    )
                    Spacer(Modifier.height(10.dp))
                    ReviewRow(
                        label = "Height",
                        value = height,
                        onValueChange = { height = it; heightSource = MeasurementSource.TYPED_IN },
                        unit = unit,
                        source = heightSource,
                        error = height.isNotEmpty() && heightMm == null,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.base))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Note(
                title = "Camera numbers are estimates",
                text = "If the fit will be tight, check it with a tape measure and type the " +
                    "value in. You can edit any of these.",
                icon = PackIcons.Info,
            )
        }

        Spacer(Modifier.height(Spacing.base))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceField, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Leave a gap around the edges",
                        color = TextPrimary,
                        fontFamily = UiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5f.sp,
                    )
                    Text(
                        "Currently $gap mm on every side",
                        color = TextTertiary,
                        fontFamily = UiFamily,
                        fontSize = 12.5f.sp,
                    )
                }
                Stepper(value = gap, onValueChange = { gap = it }, range = 0..50)
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = "Save this space",
                enabled = valid,
                onClick = {
                    onConfirm(
                        Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0),
                        mapOf(
                            Axis3.WIDTH to widthSource,
                            Axis3.DEPTH to depthSource,
                            Axis3.HEIGHT to heightSource,
                        ),
                        gap,
                    )
                },
            )
            SecondaryButton(
                text = "Measure again",
                onClick = onMeasureAgain,
                icon = PackIcons.Camera,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

/** Which edge a provenance badge belongs to. Each is recorded separately. */
enum class Axis3 { WIDTH, DEPTH, HEIGHT }

@Composable
private fun ReviewRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: LengthUnit,
    source: MeasurementSource,
    error: Boolean,
) {
    Column {
        DimensionField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            unit = unit,
            error = error,
        )
        Box(Modifier.padding(start = 12.dp, top = 6.dp)) {
            ProvenanceBadge(source = source)
        }
    }
}
