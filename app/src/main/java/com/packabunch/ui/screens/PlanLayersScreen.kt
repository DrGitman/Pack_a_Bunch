package com.packabunch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLength
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.render.LayerView
import com.packabunch.ui.render.layersOf
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.ChromeAlt
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/**
 * Layer view — `design/artboards/PlanLayers.dc.html`.
 *
 * The overview shows you the shape of the pack; this shows you what to actually do, one
 * pass at a time, seen from where you will be standing — directly above it.
 */
@Composable
fun PlanLayersScreen(
    state: PackEditorState,
    unit: LengthUnit,
    onShowOverview: () -> Unit,
    onStartPacking: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    val space = state.space
    val layers = remember(plan) { layersOf(plan?.placements.orEmpty()) }
    var selected by remember { mutableIntStateOf(0) }
    val layer = layers.getOrNull(selected.coerceIn(0, (layers.size - 1).coerceAtLeast(0)))

    ArtboardPage(modifier) {
        PackAppBar(
            title = state.name.ifEmpty { "Your pack" },
            subtitle = "Looking straight down",
            onBack = onBack,
        )

        Spacer(Modifier.height(Spacing.md))

        Row(
            Modifier.padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ViewToggle(text = "Overview", selected = false, onClick = onShowOverview)
            ViewToggle(text = "Layers", selected = true, onClick = {})
        }

        Spacer(Modifier.height(Spacing.md))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(shape = RoundedCornerShape(28.dp), elevation = 10.dp, contentPadding = 12.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(BrandTint, RoundedCornerShape(22.dp)),
                ) {
                    if (space != null && layer != null) {
                        LayerView(
                            items = state.items,
                            space = space,
                            layer = layer,
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            colorFor = { placement ->
                                itemColor(state.items.indexOfFirst { it.id == placement.specId })
                            },
                        )
                    }
                }
            }
        }

        if (layer != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Text(
                    text = "Layer ${layer.index + 1} of ${layers.size}",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "${formatLength(layer.baseMm, unit)} " +
                        "${formatLength(layer.topMm, unit)} ${unit.shortLabel} from the floor",
                    style = NumeralChip,
                    color = TextTertiary,
                )
            }

            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                layer.placements.forEach { placement ->
                    val index = state.items.indexOfFirst { it.id == placement.specId }
                    val spec = state.items.getOrNull(index)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemNumberTile(
                            number = index + 1,
                            color = itemColor(index.coerceAtLeast(0)),
                            size = 26.dp,
                            cornerRadius = 9.dp,
                            fontSize = 12,
                        )
                        Spacer(Modifier.size(9.dp))
                        Text(
                            text = spec?.name ?: placement.specId,
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        PushDown()

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            layers.forEach { candidate ->
                LayerChip(
                    title = "Layer ${candidate.index + 1}",
                    detail = "${candidate.placements.size} " +
                        if (candidate.placements.size == 1) "piece" else "pieces",
                    selected = candidate.index == selected,
                    onClick = { selected = candidate.index },
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(text = "Start packing", onClick = onStartPacking)
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ViewToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) ChromeAlt else Color.White,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "viewToggle",
    )
    Box(
        Modifier
            .pressScale(pressedScale = 0.96f)
            .background(background, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Ground else Color(0xFF8A7565),
            fontFamily = UiFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5f.sp,
        )
    }
}

@Composable
private fun LayerChip(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) BrandTint else Color.White,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "layerChip",
    )
    Column(
        Modifier
            .pressScale(pressedScale = 0.96f)
            .background(background, RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Outline, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            text = detail,
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 11.5f.sp,
        )
    }
}
