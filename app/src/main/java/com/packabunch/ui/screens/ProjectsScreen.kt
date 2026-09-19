package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.data.Project
import com.packabunch.ui.components.BrandMark
import com.packabunch.ui.components.DimensionChip
import com.packabunch.ui.components.NavDestination
import com.packabunch.ui.components.NavPill
import com.packabunch.ui.components.NavPillClearance
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
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
 * Metric chips reuse the plan's own numbers, so a card cannot show a fill percentage that
 * the result screen would disagree with.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ProjectsScreen(
    projects: List<Project>,
    unit: LengthUnit,
    onOpen: (String) -> Unit,
    onNewPack: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onDelete: (Project) -> Unit = {},
    onRestore: (Project) -> Unit = {},
    onSettings: () -> Unit = {},
    onRename: (Project, String) -> Unit = { _, _ -> },
    onDuplicate: (Project) -> Unit = {},
    onRemeasure: (Project) -> Unit = {},
    onShare: (Project) -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    // The three data states live here together because they are one flow: open the menu,
    // confirm the delete, then get a window to take it back.
    var menuFor by remember { mutableStateOf<Project?>(null) }
    var confirmFor by remember { mutableStateOf<Project?>(null) }
    var justDeleted by remember { mutableStateOf<Project?>(null) }
    var renameFor by remember { mutableStateOf<Project?>(null) }
    var newName by remember { mutableStateOf("") }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
    var alphabetical by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val visibleProjects = projects.filter { project ->
        val plan = project.currentPlan
        val packed = plan != null && plan.placements.isNotEmpty() &&
            plan.metrics.unplacedInstanceCount == 0 && plan.placements.all { it.instanceId in project.packedInstanceIds }
        project.name.contains(query, ignoreCase = true) &&
            (filter == "All" || (filter == "Packed" && packed) || (filter == "In progress" && !packed))
    }.let { if (alphabetical) it.sortedBy { p -> p.name.lowercase() } else it.sortedByDescending { p -> p.updatedAtMillis } }

    renameFor?.let { project ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename pack") },
            text = { androidx.compose.material3.OutlinedTextField(value = newName,
                onValueChange = { newName = it }, label = { Text("Pack name") }, singleLine = true) },
            confirmButton = { androidx.compose.material3.TextButton(enabled = newName.isNotBlank(),
                onClick = { onRename(project, newName.trim()); renameFor = null }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renameFor = null }) { Text("Cancel") } },
        )
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Projects", Modifier.weight(1f), color = TextPrimary, fontFamily = UiFamily,
                    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp)
                if (projects.isNotEmpty()) PackIconButton(PackIcons.Search, "Search packs", { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                    modifier = Modifier.background(Color.White, RoundedCornerShape(22.dp)))
                Box {
                    PackIconButton(PackIcons.Settings, "Sort packs", { sortOpen = true },
                        modifier = Modifier.background(Color.White, RoundedCornerShape(22.dp)))
                    androidx.compose.material3.DropdownMenu(sortOpen, { sortOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Recently edited") }, onClick = { alphabetical = false; sortOpen = false })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Name A–Z") }, onClick = { alphabetical = true; sortOpen = false })
                    }
                }
            }

            if (loading || projects.isNotEmpty()) Text(
                text = if (loading) "Loading saved packs…" else if (projects.size == 1) "1 pack saved"
                else "${projects.size} packs saved",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 4.dp),
            )

            if (searchOpen) androidx.compose.material3.OutlinedTextField(query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Search packs") }, singleLine = true)
            if (projects.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "In progress", "Packed").forEach { candidate ->
                    androidx.compose.material3.FilterChip(selected = filter == candidate,
                        onClick = { filter = candidate }, label = { Text(candidate, fontFamily = UiFamily, fontSize = 13.sp) },
                        shape = RoundedCornerShape(99.dp), colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = Color.White, selectedContainerColor = com.packabunch.ui.theme.ChromeAlt,
                            selectedLabelColor = com.packabunch.ui.theme.Ground))
                }
            }

            if (loading) {
                com.packabunch.ui.components.PackListSkeleton(Modifier.weight(1f))
            } else if (projects.isEmpty()) {
                EmptyState(onNewPack = onNewPack, onSample = { onOpen("sample") }, modifier = Modifier.weight(1f))
            } else if (visibleProjects.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No packs match this search or filter.", color = TextSecondary, fontFamily = UiFamily)
                }
            } else {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f),
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.md,
                        bottom = NavPillClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(visibleProjects, key = { _, p -> p.id }) { index, project ->
                        ProjectCard(
                            project = project,
                            unit = unit,
                            onClick = { onOpen(project.id) },
                            onMenu = { menuFor = project },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                }
            }
        }

        NavPill(
            current = NavDestination.Projects,
            onNavigate = { destination ->
                if (destination == NavDestination.Settings) onSettings()
            },
            onNewPack = onNewPack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )

        DeletedProjectBar(
            packName = justDeleted?.name.orEmpty(),
            itemCount = justDeleted?.items?.size ?: 0,
            visible = justDeleted != null,
            onUndo = {
                justDeleted?.let(onRestore)
                justDeleted = null
            },
            onExpired = { justDeleted = null },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )

        menuFor?.let { project ->
            ProjectMenuSheet(
                project = project,
                unit = unit,
                onRename = { newName = project.name; renameFor = project; menuFor = null },
                onDuplicate = { onDuplicate(project); menuFor = null },
                onRemeasure = { onRemeasure(project); menuFor = null },
                onShare = { onShare(project); menuFor = null },
                onDelete = {
                    confirmFor = project
                    menuFor = null
                },
                onDismiss = { menuFor = null },
            )
        }

        confirmFor?.let { project ->
            DeleteConfirmDialog(
                packName = project.name,
                itemCount = project.items.size,
                photoCount = 0,
                onConfirm = {
                    onDelete(project)
                    // Held so the undo bar has something to put back.
                    justDeleted = project
                    confirmFor = null
                },
                onCancel = { confirmFor = null },
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    unit: LengthUnit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val plan = project.currentPlan

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f)
            .warmShadow(8.dp, shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MiniCratePreview(
            items = project.items,
            specOrder = project.items.map { it.id },
            space = project.space,
            placements = plan?.placements.orEmpty(),
            modifier = Modifier.size(68.dp),
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

        PackIconButton(
            icon = PackIcons.MoreVertical,
            contentDescription = "More",
            onClick = onMenu,
            tint = Color(0xFF8A7565),
            size = 40.dp,
            iconSize = 19.dp,
        )
    }
}

@Composable
private fun EmptyState(onNewPack: () -> Unit, onSample: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = NavPillClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyCrateIllustration()
        Spacer(Modifier.height(26.dp))
        Text(
            text = "Nothing packed yet",
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            letterSpacing = (-.4).sp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = "Start with a space you can measure — a crate, a storage box, a drawer, a car boot.",
            modifier = Modifier.widthIn(max = 290.dp),
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        PrimaryButton(text = "New pack", onClick = onNewPack)
        Spacer(Modifier.height(10.dp))
        com.packabunch.ui.components.SecondaryButton(text = "Open the sample pack", onClick = onSample,
            backgroundColor = com.packabunch.ui.theme.Ground)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().background(com.packabunch.ui.theme.Surface, RoundedCornerShape(22.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(com.packabunch.ui.theme.BrandTint, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(PackIcons.Info, null, Modifier.size(19.dp), tint = com.packabunch.ui.theme.Primary)
            }
            Column(Modifier.weight(1f)) {
                Text("Measure the inside", color = TextPrimary, fontFamily = UiFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("Always measure the space inside the box, not the outside, and keep the opening clear.",
                    color = TextSecondary, fontFamily = UiFamily, fontSize = 13.5.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun EmptyCrateIllustration() {
    Box(Modifier.size(180.dp, 150.dp).background(com.packabunch.ui.theme.SurfaceSunken, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(132.dp, 110.dp)) {
            val scale = size.width / 132f
            fun polygon(vararg xy: Float) = androidx.compose.ui.graphics.Path().apply {
                moveTo(xy[0] * scale, xy[1] * scale)
                for (i in 2 until xy.size step 2) lineTo(xy[i] * scale, xy[i + 1] * scale)
                close()
            }
            val top = polygon(14f,46f,66f,20f,118f,46f,66f,72f)
            drawPath(top, com.packabunch.ui.theme.EmptyCrateTop)
            drawPath(polygon(14f,46f,66f,72f,66f,94f,14f,68f), com.packabunch.ui.theme.EmptyCrateLeft, alpha = .85f)
            drawPath(polygon(66f,72f,118f,46f,118f,68f,66f,94f), com.packabunch.ui.theme.EmptyCrateRight, alpha = .85f)
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2f * scale,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f * scale, 6f * scale)))
            drawPath(top, com.packabunch.ui.theme.EmptyCrateStroke, style = stroke)
            for ((x, y) in listOf(14f to 46f, 66f to 72f, 118f to 46f)) {
                drawLine(com.packabunch.ui.theme.EmptyCrateStroke,
                    androidx.compose.ui.geometry.Offset(x * scale, y * scale),
                    androidx.compose.ui.geometry.Offset(x * scale, (y + 22f) * scale),
                    strokeWidth = 2f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, pathEffect = stroke.pathEffect)
            }
        }
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

