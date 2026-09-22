package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.CountPill
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ProvenanceBadge
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/** A measured thing, kept across packs. */
data class LibraryItem(
    val spec: ItemSpec,
    val usedInPackCount: Int,
)

/**
 * Things I've measured — `ItemLibrary.dc.html`, and its locked twin `LockedLibrary.dc.html`.
 *
 * One screen with two states, because they show the same list. The locked state is not a
 * teaser with fake rows: it says how many things are already saved and that they are
 * waiting. Measuring is free, and this screen has to keep saying so — what Plus sells is
 * reuse across packs, not the act of measuring.
 */
@Composable
fun ItemLibraryScreen(
    items: List<LibraryItem>,
    unlocked: Boolean,
    unit: LengthUnit,
    price: String?,
    onUse: (ItemSpec) -> Unit,
    onMeasureNew: () -> Unit,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(
            title = "Things I've measured",
            onBack = onBack,
            actions = {
                if (unlocked) {
                    CountPill(
                        text = "${items.size}",
                        contentColor = Primary,
                        background = BrandTint,
                    )
                }
            },
        )

        if (!unlocked) {
            LockedLibrary(
                measuredCount = items.size,
                price = price,
                onUpgrade = onUpgrade,
                onBack = onBack,
            )
            return@ScreenScaffold
        }

        Text(
            text = "Measure something once and drop it into any pack. Sizes stay exactly as " +
                "you saved them.",
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 13.5f.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.sm,
                bottom = Spacing.base,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.spec.id }) { index, entry ->
                val enter = rememberStaggeredEntrance(index)
                LibraryRow(
                    index = index,
                    entry = entry,
                    unit = unit,
                    onClick = { onUse(entry.spec) },
                    modifier = Modifier.entrance(enter),
                )
            }
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Measure a new one",
                onClick = onMeasureNew,
                icon = PackIcons.Plus,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun LibraryRow(
    index: Int,
    entry: LibraryItem,
    unit: LengthUnit,
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
        ItemNumberTile(
            number = index + 1,
            color = itemColor(index),
            size = 38.dp,
            cornerRadius = 13.dp,
            fontSize = 15,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.spec.name,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = formatDimensions(entry.spec.dimensions, unit),
                style = NumeralChip,
                color = TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The provenance travels with the item, not with the pack it was measured
                // in. A camera estimate reused in a new pack is still a camera estimate.
                ProvenanceBadge(source = entry.spec.measurementSource)
                Text(
                    text = if (entry.usedInPackCount == 1) "In 1 pack"
                    else "In ${entry.usedInPackCount} packs",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.sp,
                )
            }
        }
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            tint = Color(0xFFC3B0A0),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LockedLibrary(
    measuredCount: Int,
    price: String?,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
) {
    Spacer(Modifier.height(Spacing.lg))

    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        IconTile(
            icon = PackIcons.Library,
            tint = Primary,
            background = BrandTint,
            size = 56.dp,
            iconSize = 26.dp,
        )
        Spacer(Modifier.height(Spacing.base))
        Text(
            text = "Measure once, use it everywhere",
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 25.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Pack a Bunch Pro keeps every item you measure, so the toolbox you sized in " +
                "March drops straight into the pack you're planning now.",
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )

        if (measuredCount > 0) {
            Spacer(Modifier.height(Spacing.base))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(BrandTint, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                // Not a teaser. These are really saved and really theirs — the screen would
                // be dishonest if it implied the measurements had been thrown away.
                Text(
                    text = "Your $measuredCount measured things are already saved " +
                        "they're here waiting.",
                    color = Color(0xFF7C4223),
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5f.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }

    Spacer(Modifier.weight(1f))

    Column(
        Modifier.padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryButton(
            text = price?.let { "Get Pack a Bunch Pro, $it" } ?: "See Pack a Bunch Pro",
            onClick = onUpgrade,
        )
        SecondaryButton(text = "Not now", onClick = onBack)
        Text(
            text = "Measuring, planning and the packing guide stay free.",
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.5f.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(Spacing.sm))
}

/** Convenience for callers that only have the tier to hand. */
fun libraryUnlocked(limits: TierLimits): Boolean = limits.itemLibrary
