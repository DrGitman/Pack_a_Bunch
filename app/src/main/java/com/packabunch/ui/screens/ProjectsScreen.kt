package com.packabunch.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.data.Project
import com.packabunch.ui.components.BrandMark
import com.packabunch.ui.components.DimensionChip
import com.packabunch.ui.components.NavDestination
import com.packabunch.ui.components.NavPill
import com.packabunch.ui.components.NavPillClearance
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.render.MiniCratePreview
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Projects — `design/artboards/Projects.dc.html` and `ProjectsEmpty.dc.html`.
 *
 * The subtitle says "on this device" and means it: nothing here is synced. When accounts
 * land that line has to change, and it should not change before they do.
 *
 * Metric chips reuse the plan's own numbers, so a card cannot show a fill percentage that
 * the result screen would disagree with.
 */
@Composable
fun ProjectsScreen(
    projects: List<Project>,
    unit: LengthUnit,
    onOpen: (String) -> Unit,
    onNewPack: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            PackAppBar(title = "Projects", onBack = onBack)

            Text(
                text = if (projects.size == 1) "1 pack saved on this device"
                else "${projects.size} packs saved on this device",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 4.dp),
            )

            if (projects.isEmpty()) {
                EmptyState(onNewPack = onNewPack, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.md,
                        bottom = NavPillClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(projects, key = { _, p -> p.id }) { index, project ->
                        val entrance = rememberStaggeredEntrance(index)
                        ProjectCard(
                            project = project,
                            unit = unit,
                            onClick = { onOpen(project.id) },
                            modifier = Modifier.entrance(entrance),
                        )
                    }
                }
            }
        }

        NavPill(
            current = NavDestination.Projects,
            onNavigate = {},
            onNewPack = onNewPack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    unit: LengthUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val plan = project.currentPlan

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.985f)
            .warmShadow(8.dp, shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MiniCratePreview(
            space = project.space,
            placements = plan?.placements.orEmpty(),
            modifier = Modifier.size(76.dp),
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = project.name.ifEmpty { "Untitled pack" },
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = "${project.pieceCount} " +
                    if (project.pieceCount == 1) "piece" else "pieces",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DimensionChip(text = formatDimensions(project.space.dimensions, unit))
            }

            if (plan != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DimensionChip(
                        text = "${plan.metrics.modelledFillPercent}% modelled fill",
                        background = SuccessTint,
                        contentColor = Color(0xFF2F5C45),
                    )
                    if (plan.metrics.unplacedInstanceCount > 0) {
                        DimensionChip(
                            text = "${plan.metrics.unplacedInstanceCount} not placed",
                            background = Color(0xFFFBE4DF),
                            contentColor = Color(0xFF8E3322),
                        )
                    }
                }
            } else if (project.isPlanStale) {
                // The plan no longer matches the items, so it is not shown at all rather
                // than shown against numbers it was never solved from.
                Spacer(Modifier.height(6.dp))
                DimensionChip(
                    text = "needs planning again",
                    background = Color(0xFFFAEEDA),
                    contentColor = Color(0xFF6E4708),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onNewPack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.base, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 64.dp)
        Text(
            text = "No packs yet",
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
        )
        Text(
            text = "Measure a space, add what's going in it, and we'll work out an order.",
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        PrimaryButton(text = "Plan a pack", onClick = onNewPack)
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 412, heightDp = 916)
@Composable
private fun ProjectsPreview() {
    PackABunchTheme {
        ProjectsScreen(
            projects = listOf(com.packabunch.data.ProjectRepository.sampleProject()),
            unit = LengthUnit.CENTIMETRES,
            onOpen = {},
            onNewPack = {},
            onBack = {},
        )
    }
}
