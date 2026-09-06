package com.packabunch.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.packabunch.ui.AppViewModel
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.screens.CreateSpaceScreen
import com.packabunch.ui.screens.ItemsScreen
import com.packabunch.ui.screens.PackingGuideScreen
import com.packabunch.ui.screens.PlanResultScreen
import com.packabunch.ui.screens.ProjectsScreen
import com.packabunch.ui.screens.WelcomeScreen

/**
 * Where every screen lives.
 *
 * Route names match the artboard file they were built from, so the mockup for anything on
 * screen is one grep away.
 */
object Routes {
    const val WELCOME = "welcome"          // Main.dc.html
    const val PROJECTS = "projects"        // Projects.dc.html
    const val CREATE_SPACE = "createSpace" // CreateSpace.dc.html
    const val ITEMS = "items"              // Items.dc.html
    const val PLAN_RESULT = "planResult"   // PlanResult.dc.html
    const val PACKING_GUIDE = "packingGuide" // PackingGuide.dc.html
}

/**
 * Screen motion.
 *
 * Forward travel slides in from the right and settles; going back reverses it. That is the
 * Android convention rather than an invention, and it is doing a job here: this app is a
 * sequence — space, then items, then the plan, then the pack — and the direction of travel
 * is what tells you where you are in it.
 *
 * Durations come from the tokens: 250 ms in, 150 ms out. Nothing longer, because the whole
 * app is meant to feel like a tool rather than a presentation.
 */
private const val SLIDE_FRACTION = 8

private fun AnimatedContentTransitionScope<*>.enterForward(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.Enter),
        initialOffset = { it / SLIDE_FRACTION },
    ) + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.Enter))

private fun AnimatedContentTransitionScope<*>.exitForward(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.Exit),
        targetOffset = { it / (SLIDE_FRACTION * 2) },
    ) + fadeOut(tween(Motion.SHORT_MS, easing = Motion.Exit))

private fun AnimatedContentTransitionScope<*>.enterBack(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.Enter),
        initialOffset = { it / (SLIDE_FRACTION * 2) },
    ) + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.Enter))

private fun AnimatedContentTransitionScope<*>.exitBack(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.Exit),
        targetOffset = { it / SLIDE_FRACTION },
    ) + fadeOut(tween(Motion.SHORT_MS, easing = Motion.Exit))

/** The result arrives rather than slides — it is an answer, not the next page of a form. */
private fun resultEnter(): EnterTransition =
    fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.Enter)) +
        scaleIn(tween(Motion.MEDIUM_MS, easing = Motion.Enter), initialScale = 0.96f)

private fun resultExit(): ExitTransition =
    fadeOut(tween(Motion.SHORT_MS, easing = Motion.Exit)) +
        scaleOut(tween(Motion.SHORT_MS, easing = Motion.Exit), targetScale = 0.98f)

@Composable
fun PackNavHost(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME,
        modifier = modifier,
        enterTransition = { enterForward() },
        exitTransition = { exitForward() },
        popEnterTransition = { enterBack() },
        popExitTransition = { exitBack() },
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onPlanAPack = {
                    viewModel.startNewPack()
                    navController.navigate(Routes.CREATE_SPACE)
                },
                onTrySample = {
                    viewModel.openPack("sample")
                    navController.navigate(Routes.ITEMS)
                },
                onSeeProjects = { navController.navigate(Routes.PROJECTS) },
            )
        }

        composable(Routes.PROJECTS) {
            ProjectsScreen(
                projects = projects,
                unit = settings.unit,
                onOpen = { id ->
                    viewModel.openPack(id)
                    navController.navigate(Routes.ITEMS)
                },
                onNewPack = {
                    viewModel.startNewPack()
                    navController.navigate(Routes.CREATE_SPACE)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_SPACE) {
            CreateSpaceScreen(
                state = editor,
                unit = settings.unit,
                onUnitChange = viewModel::setUnit,
                onNameChange = viewModel::setSpaceName,
                onDimensionsChange = viewModel::setSpaceDimensions,
                onNext = { navController.navigate(Routes.ITEMS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ITEMS) {
            ItemsScreen(
                state = editor,
                unit = settings.unit,
                limits = viewModel.limits,
                onUpsertItem = viewModel::upsertItem,
                onRemoveItem = viewModel::removeItem,
                onDuplicateItem = viewModel::duplicateItem,
                onPlan = {
                    viewModel.solve()
                    navController.navigate(Routes.PLAN_RESULT)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.PLAN_RESULT,
            enterTransition = { resultEnter() },
            popExitTransition = { resultExit() },
        ) {
            PlanResultScreen(
                state = editor,
                unit = settings.unit,
                onStartPacking = {
                    viewModel.setGuideStep(0)
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                onEditItems = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PACKING_GUIDE) {
            PackingGuideScreen(
                state = editor,
                unit = settings.unit,
                onStepChange = viewModel::setGuideStep,
                onMarkPacked = viewModel::markPacked,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Sheets rise from the bottom edge they are attached to. */
val sheetEnter: EnterTransition = slideInVertically(
    animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.Enter),
    initialOffsetY = { it },
) + fadeIn(tween(Motion.SHORT_MS))

val sheetExit: ExitTransition = slideOutVertically(
    animationSpec = tween(Motion.SHORT_MS, easing = Motion.Exit),
    targetOffsetY = { it },
) + fadeOut(tween(Motion.SHORT_MS))
