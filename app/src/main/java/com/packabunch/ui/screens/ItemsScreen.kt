package com.packabunch.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.TierLimits
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.CountPill
import com.packabunch.ui.components.DashedPlaceholder
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.Stepper
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.nav.sheetEnter
import com.packabunch.ui.nav.sheetExit
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/**
 * Items — `design/artboards/Items.dc.html`.
 *
 * Each row carries a number, a name and a colour, in that order of importance. The colour
 * is never the only thing distinguishing two items, which matters both for colour vision
 * and for the packing guide, where "the blue one" is useless and "item 3, the board game"
 * is not.
 *
 * The rotation and nothing-on-top flags are shown on the row rather than hidden in the
 * editor, because they change what the solver is allowed to do and a surprising
 * arrangement usually traces back to one of them.
 */
@Composable
fun ItemsScreen(
    state: PackEditorState,
    unit: LengthUnit,
    limits: TierLimits,
    onUpsertItem: (ItemSpec) -> Unit,
    onRemoveItem: (String) -> Unit,
    onDuplicateItem: (String) -> Unit,
    onPlan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialEditItemId: String? = null,
    onEditConsumed: () -> Unit = {},
    onLibrary: () -> Unit = {},
    onScan: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<ItemSpec?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(initialEditItemId) {
        if (initialEditItemId != null) {
            editing = state.items.firstOrNull { it.id == initialEditItemId }
            editorOpen = editing != null
            onEditConsumed()
        }
    }

    val pieces = state.pieceCount
    val atLimit = !limits.allowsPieces(pieces + 1)

    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            PackAppBar(
                title = "Items",
                onBack = onBack,
                actions = {
                    CountPill(
                        text = limits.maxPiecesPerPack?.let { "$pieces / $it" } ?: "$pieces",
                    )
                },
            )

            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = "Scan items together", onClick = onScan)
            com.packabunch.ui.components.PackTextButton(text = "Item library", onClick = onLibrary)

            if (state.space != null) {
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    PackCard(contentPadding = 0.dp, elevation = 4.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = state.name.ifEmpty { "This space" },
                                    color = TextPrimary,
                                    fontFamily = UiFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5f.sp,
                                )
                                Text(
                                    text = formatDimensions(state.space.dimensions, unit),
                                    style = NumeralChip,
                                    color = TextTertiary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    bottom = Spacing.base,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    ItemRow(
                        index = index,
                        item = item,
                        unit = unit,
                        modifier = Modifier.animateItem(),
                        onQuantityChange = { onUpsertItem(item.copy(quantity = it)) },
                        onClick = { editing = item; editorOpen = true },
                    )
                }

                item {
                    Box(
                        Modifier
                            .pressScale(pressedScale = 0.98f, enabled = !atLimit)
                            .clickable(enabled = !atLimit) { editing = null; editorOpen = true },
                    ) {
                        DashedPlaceholder(text = "Add an item")
                    }
                }

                if (atLimit) {
                    item {
                        Note(
                            title = "Twenty pieces on the free plan",
                            text = "The planner searches well past twenty, twenty is where " +
                                "the free plan stops. Split this into two packs, or see Pack a Bunch Pro.",
                            tone = NoteTone.Caution,
                            icon = PackIcons.Cube,
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (pieces == 1) "1 piece" else "$pieces pieces",
                        style = NumeralChip.copy(fontSize = 13.sp),
                        color = TextTertiary,
                    )
                }
                PrimaryButton(
                    text = "Plan the pack",
                    onClick = onPlan,
                    enabled = state.items.isNotEmpty() && state.hasUsableSpace,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }

        AnimatedVisibility(visible = editorOpen, enter = sheetEnter, exit = sheetExit) {
            ItemEditorSheet(
                existing = editing,
                unit = unit,
                nextIndex = state.items.size,
                onDismiss = { editorOpen = false },
                onSave = { spec ->
                    onUpsertItem(spec)
                    editorOpen = false
                },
                onDelete = editing?.let { spec ->
                    { onRemoveItem(spec.id); editorOpen = false }
                },
                onDuplicate = editing?.let { spec ->
                    { onDuplicateItem(spec.id); editorOpen = false }
                },
            )
        }
    }
}

@Composable
private fun ItemRow(
    index: Int,
    item: ItemSpec,
    unit: LengthUnit,
    onQuantityChange: (Int) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f)
            .warmShadow(6.dp, shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ItemNumberTile(number = index + 1, color = itemColor(index), size = 38.dp, cornerRadius = 13.dp, fontSize = 15)

        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = formatDimensions(item.dimensions, unit),
                style = NumeralChip,
                color = TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = item.constraintLabel(),
                color = Color(0xFF8A7565),
                fontFamily = UiFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .background(SurfaceMuted, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }

        Stepper(
            value = item.quantity,
            onValueChange = onQuantityChange,
            background = SurfaceMuted,
            buttonBackground = Color.White,
        )
    }
}

/** The one-line summary of what the solver may and may not do with this item. */
private fun ItemSpec.constraintLabel(): String = when {
    keepUpright && !maySupportItems -> "Keep upright · nothing on top"
    keepUpright -> "Keep upright"
    !maySupportItems -> "Nothing on top"
    else -> "Can turn any way"
}

@Preview(widthDp = 412, heightDp = 916)
@Composable
private fun ItemsPreview() {
    PackABunchTheme {
        ItemsScreen(
            state = PackEditorState(
                projectId = "p",
                name = "Moving crate",
                space = com.packabunch.data.ProjectRepository.sampleProject().space,
                items = com.packabunch.data.ProjectRepository.sampleProject().items,
            ),
            unit = LengthUnit.CENTIMETRES,
            limits = TierLimits.FREE,
            onUpsertItem = {},
            onRemoveItem = {},
            onDuplicateItem = {},
            onPlan = {},
            onBack = {},
        )
    }
}

