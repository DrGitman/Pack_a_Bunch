package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Dimensions
import com.packabunch.packing.MeasurementSource
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.DimensionField
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SelectableChip
import com.packabunch.ui.components.StepLabel
import com.packabunch.ui.components.StepProgress
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatEditableLength as formatLength
import com.packabunch.ui.format.parseLengthToMm
import com.packabunch.ui.render.CrateDiagram
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.FieldLabel
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.packabunch.ui.theme.Primary

/**
 * Create space — `design/artboards/CreateSpace.dc.html`.
 *
 * The rule this screen exists to enforce: **a preset names the space and never fills in
 * measurements.** "Car boot" tells us what to call it and nothing whatsoever about how big
 * this particular car boot is. Prefilling from a preset would be inventing a measurement
 * and then showing it with a provenance badge, which is worse than not having one.
 *
 * The camera route sits directly under the typed fields at equal weight, per the copy rules.
 */
private val PRESETS = listOf("Crate", "Storage box", "Drawer", "Car boot", "Wardrobe", "Shelf")

@Composable
fun CreateSpaceScreen(
    state: PackEditorState,
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    onNameChange: (String) -> Unit,
    onDimensionsChange: (Dimensions, MeasurementSource) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onMeasureWithCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val existing = state.space?.dimensions
    var width by remember { mutableStateOf(existing?.widthMm?.takeIf { it > 0 }?.let { formatLength(it, unit) } ?: "") }
    var depth by remember { mutableStateOf(existing?.depthMm?.takeIf { it > 0 }?.let { formatLength(it, unit) } ?: "") }
    var height by remember { mutableStateOf(existing?.heightMm?.takeIf { it > 0 }?.let { formatLength(it, unit) } ?: "") }
    var focused by remember { mutableStateOf("") }

    val widthMm = parseLengthToMm(width, unit)
    val depthMm = parseLengthToMm(depth, unit)
    val heightMm = parseLengthToMm(height, unit)
    val complete = widthMm != null && depthMm != null && heightMm != null

    fun push() {
        val w = parseLengthToMm(width, unit)
        val d = parseLengthToMm(depth, unit)
        val h = parseLengthToMm(height, unit)
        if (w != null && d != null && h != null) {
            onDimensionsChange(
                Dimensions(w, d, h),
                if (Dimensions(w, d, h) == state.space?.dimensions)
                    state.space.measurementSource else MeasurementSource.TYPED_IN,
            )
        }
    }

    ArtboardPage(modifier) {
        PackAppBar(title = "New pack", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 14.dp)) {
            StepProgress(step = 1, totalSteps = 3)
            Spacer(Modifier.height(10.dp))
            StepLabel(step = 1, totalSteps = 3, name = "The space")
            Spacer(Modifier.height(Spacing.sm))
            ScreenHeading("What are you packing into?")
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(contentPadding = 0.dp) {
                Column(Modifier.padding(horizontal = Spacing.base, vertical = 14.dp)) {
                    Text("NAME", style = FieldLabel, color = TextTertiary)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        textStyle = TextStyle(
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = TextPrimary,
                        ),
                        cursorBrush = SolidColor(Primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.name.isEmpty()) {
                                Text(
                                    "Name this space",
                                    color = TextTertiary,
                                    fontFamily = UiFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.base))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = Spacing.gutter, end = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESETS.forEach { preset ->
                SelectableChip(
                    text = preset,
                    selected = state.name == preset,
                    onClick = { onNameChange(preset) },
                )
            }
        }

        Text(
            text = "A preset only names the space. It never fills in measurements for you.",
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.5f.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 9.dp),
        )

        Spacer(Modifier.height(Spacing.md))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(elevation = 8.dp, contentPadding = 0.dp) {
                Column(Modifier.padding(horizontal = Spacing.base, vertical = 18.dp)) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 118.dp, height = 104.dp)
                                .background(BrandTint, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CrateDiagram(Modifier.size(104.dp, 92.dp))
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DimensionField(
                                label = "Width",
                                value = width,
                                onValueChange = { width = it; push() },
                                unit = unit,
                                focused = focused == "w",
                                error = width.isNotEmpty() && widthMm == null,
                            )
                            DimensionField(
                                label = "Depth",
                                value = depth,
                                onValueChange = { depth = it; push() },
                                unit = unit,
                                focused = focused == "d",
                                error = depth.isNotEmpty() && depthMm == null,
                            )
                            DimensionField(
                                label = "Height",
                                value = height,
                                onValueChange = { height = it; push() },
                                unit = unit,
                                focused = focused == "h",
                                error = height.isNotEmpty() && heightMm == null,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Note(
                        text = "Measure the space inside the crate, and keep the opening clear.",
                        icon = PackIcons.Info,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.base))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SecondaryButton(
                text = "Measure with the camera",
                onClick = onMeasureWithCamera,
                icon = PackIcons.Camera,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Works on some phones. Typing always works.",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                )
            }
        }

        PushDown()

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Next: add items",
                onClick = { push(); onNext() },
                enabled = complete,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Preview(widthDp = 412, heightDp = 916)
@Composable
private fun CreateSpacePreview() {
    PackABunchTheme {
        CreateSpaceScreen(
            state = PackEditorState(projectId = "p", name = "Moving crate"),
            unit = LengthUnit.CENTIMETRES,
            onUnitChange = {},
            onNameChange = {},
            onDimensionsChange = { _, _ -> },
            onNext = {},
            onBack = {},
        )
    }
}
