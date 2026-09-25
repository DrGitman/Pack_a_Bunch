package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.data.Project
import com.packabunch.ui.components.BrandMark
import com.packabunch.ui.components.DimensionChip
import com.packabunch.ui.components.NavDestination
import com.packabunch.ui.components.NavPillClearance
import com.packabunch.ui.components.popIn
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
    greet: Boolean = false,
    /** A pack just deleted from its own page, so the undo window appears where the list is. */
    deleted: Project? = null,
) {
    // A pack deleted on its own page lands back here with a window to take it back.
    var justDeleted by remember(deleted?.id) { mutableStateOf(deleted) }
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

    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Projects", Modifier.weight(1f), color = TextPrimary, fontFamily = UiFamily,
                    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp)
                if (projects.isNotEmpty()) com.packabunch.ui.components.LottieTapIcon(
                    animation = com.packabunch.R.raw.icon_search,
                    contentDescription = "Search packs",
                    onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
                    size = 44.dp,
                )
                Box {
                    com.packabunch.ui.components.LottieTapIcon(
                        animation = com.packabunch.R.raw.icon_filter,
                        contentDescription = "Sort packs",
                        onClick = { sortOpen = true },
                        size = 44.dp,
                    )
                    androidx.compose.material3.DropdownMenu(sortOpen, { sortOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Recently edited") }, onClick = { alphabetical = false; sortOpen = false })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Name A, Z") }, onClick = { alphabetical = true; sortOpen = false })
                    }
                }
            }

            if (loading) Text(
                text = "Loading saved packs…",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 4.dp),
            ) else if (projects.isNotEmpty()) com.packabunch.ui.components.ProjectsSubtitle(
                packCount = projects.size,
                greet = greet,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 4.dp),
            )

            // A plain white pill with the words inside it: no floating label, and light enough
            // against the cream ground to be obvious.
            if (searchOpen) androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary, fontFamily = UiFamily, fontSize = 15.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(com.packabunch.ui.theme.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, com.packabunch.ui.theme.Outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search packs", color = TextTertiary, fontFamily = UiFamily, fontSize = 15.sp)
                    }
                    inner()
                },
            )
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
                    // Grouped the way the artboard groups them: what you touched today, what
                    // came before, and the made-up pack we ship, kept apart from real work.
                    // One running index across headings and cards, so the pop-in reads as a
                    // single ripple down the page rather than restarting at each section.
                    var row = 0
                    groupedProjects(visibleProjects).forEach { (heading, group) ->
                        val headingIndex = row++
                        item(key = "heading-$heading") {
                            Text(
                                text = heading,
                                color = TextTertiary,
                                fontFamily = UiFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).animateItem()
                                    .popIn(headingIndex),
                            )
                        }
                        group.forEachIndexed { indexInGroup, project ->
                            val cardIndex = row++
                            item(key = project.id) {
                                ProjectCard(
                                    project = project,
                                    unit = unit,
                                    onClick = { onOpen(project.id) },
                                    modifier = Modifier.animateItem()
                                        .popIn(cardIndex),
                                )
                            }
                        }
                    }
                }
                }
            }
        }

com.packabunch.ui.components.PackBar(
            here = com.packabunch.ui.components.NavSlots.Projects,
            onProjects = {},
            onSettings = onSettings,
            onNewPack = onNewPack,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
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

    }
}

/**
 * The closed greige box that stands for any sample: the same drawing every time, with nothing
 * in it, so a made-up pack never looks like something somebody measured.
 */
@Composable
private fun SampleCrateGlyph(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun face(points: List<Pair<Float, Float>>, colour: Color) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points[0].first * w, points[0].second * h)
                points.drop(1).forEach { lineTo(it.first * w, it.second * h) }
                close()
            }
            drawPath(path, colour)
        }
        // Lid, then the two visible walls, lightest to darkest.
        face(listOf(0.06f to 0.42f, 0.5f to 0.20f, 0.94f to 0.42f, 0.5f to 0.64f), Color(0xFFE7DED2))
        face(listOf(0.06f to 0.42f, 0.5f to 0.64f, 0.5f to 0.84f, 0.06f to 0.62f), Color(0xFFD3C6B4))
        face(listOf(0.94f to 0.42f, 0.5f to 0.64f, 0.5f to 0.84f, 0.94f to 0.62f), Color(0xFFC4B5A1))
    }
}

/** The sample pack's id, fixed so every screen can recognise it. */
const val SAMPLE_ID = "sample"

/** The artboard's three groups, in its order. Empty groups are left out entirely. */
private fun groupedProjects(projects: List<Project>): List<Pair<String, List<Project>>> {
    val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    val samples = projects.filter { it.id == SAMPLE_ID }
    val real = projects.filter { it.id != SAMPLE_ID }
    return listOf(
        "TODAY" to real.filter { it.updatedAtMillis >= dayAgo },
        "EARLIER" to real.filter { it.updatedAtMillis < dayAgo },
        "SAMPLES" to samples,
    ).filter { (_, group) -> group.isNotEmpty() }
}

/** "2 h ago", the way the artboard writes it. Anything older than a week is just the day count. */
private fun timeAgo(millis: Long): String? {
    if (millis <= 0L) return null
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
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
    val sample = project.id == SAMPLE_ID

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f)
            .warmShadow(8.dp, shape)
            .background(Color.White, shape)
            .clip(shape).clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(58.dp)
                .background(if (sample) Color(0xFFEFE8DF) else Color(0xFFF7E4D3), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // A sample shows a plain closed box. Only a real pack draws what is actually in it.
            if (sample) SampleCrateGlyph(Modifier.size(40.dp))
            else MiniCratePreview(
                items = project.items,
                specOrder = project.items.map { it.id },
                space = project.space,
                placements = plan?.placements.orEmpty(),
                modifier = Modifier.size(42.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = project.name.ifEmpty { "Untitled pack" },
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                if (sample) {
                    Text(
                        text = "SAMPLE",
                        color = Color(0xFF9B8877),
                        fontFamily = UiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier
                            .background(Color(0xFFF0E9E1), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                // The sample explains itself; a real pack says what is in it and when you touched it.
                text = if (sample) "Try the whole flow with made-up items" else listOfNotNull(
                    project.space.name.takeIf { it.isNotBlank() && !it.equals(project.name, ignoreCase = true) },
                    "${project.pieceCount} " + if (project.pieceCount == 1) "piece" else "pieces",
                    timeAgo(project.updatedAtMillis),
                ).joinToString(" · "),
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (!sample) Spacer(Modifier.height(8.dp))

            // One wrapping row: a long "1 not placed" used to be squeezed into a vertical column.
            if (!sample) androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                DimensionChip(text = com.packabunch.ui.format.formatDimensionsCompact(project.space.dimensions, unit), compact = true)
                if (plan != null) {
                    DimensionChip(
                        text = "${plan.metrics.modelledFillPercent}% fill",
                        background = SuccessTint,
                        contentColor = Color(0xFF2F5C45),
                        compact = true,
                    )
                    if (plan.metrics.unplacedInstanceCount > 0) {
                        DimensionChip(
                            text = "${plan.metrics.unplacedInstanceCount} not placed",
                            background = Color(0xFFFBE4DF),
                            contentColor = Color(0xFF8E3322),
                            compact = true,
                        )
                    }
                } else if (project.isPlanStale) {
                    // The plan no longer matches the items, so it is not shown at all rather
                    // than shown against numbers it was never solved from.
                    DimensionChip(
                        text = "needs planning again",
                        background = Color(0xFFFAEEDA),
                        contentColor = Color(0xFF6E4708),
                        compact = true,
                    )
                }
            }
        }

        // Opening the pack is the only thing this card does now; renaming, duplicating and
        // deleting live on the pack's own page, where there is room to explain them.
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterVertically).size(20.dp),
            tint = Color(0xFFB9A491),
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
            text = "Start with a space you can measure, a crate, a storage box, a drawer, a car boot.",
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
    val crate by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.packabunch.R.raw.empty_crate),
    )
    val animation = com.airbnb.lottie.compose.rememberLottieAnimatable()
    androidx.compose.runtime.LaunchedEffect(crate) {
        val loaded = crate ?: return@LaunchedEffect
        animation.animate(loaded, clipSpec = com.airbnb.lottie.compose.LottieClipSpec.Marker("intro"))
        animation.animate(loaded, clipSpec = com.airbnb.lottie.compose.LottieClipSpec.Marker("loop"),
            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever)
    }
    Box(Modifier.size(180.dp, 150.dp).background(com.packabunch.ui.theme.SurfaceSunken, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center) {
        com.airbnb.lottie.compose.LottieAnimation(
            composition = crate,
            progress = { animation.progress },
            modifier = Modifier.size(138.dp, 116.dp),
            renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
        )
    }
}

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

