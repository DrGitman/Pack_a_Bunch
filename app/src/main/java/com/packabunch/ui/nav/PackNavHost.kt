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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.packabunch.ui.screens.MeasureScreen
import com.packabunch.ui.screens.PackingGuideScreen
import com.packabunch.ui.components.NavDestination
import com.packabunch.ui.screens.PackingDoneScreen
import com.packabunch.ui.screens.SettingsScreen
import com.packabunch.ui.screens.UpgradeScreen
import com.packabunch.ui.screens.ItemLibraryScreen
import com.packabunch.ui.screens.LibraryItem
import com.packabunch.ui.screens.NotificationSettingsScreen
import com.packabunch.ui.screens.PlanLayersScreen
import com.packabunch.ui.screens.ScanIncompleteScreen
import com.packabunch.ui.screens.SpaceKind
import com.packabunch.ui.screens.SpaceObstructionsScreen
import com.packabunch.ui.screens.SpaceScanReviewScreen
import com.packabunch.ui.screens.SpaceScanScreen
import com.packabunch.ui.screens.SpaceTypeScreen
import com.packabunch.packing.ScanCompleteness
import com.packabunch.ui.screens.AccountDeleteScreen
import com.packabunch.ui.screens.CreateAccountScreen
import com.packabunch.ui.screens.ForgotPasswordScreen
import com.packabunch.ui.screens.LogInScreen
import com.packabunch.ui.screens.OnboardingScreen
import com.packabunch.ui.screens.PlanIrregularScreen
import com.packabunch.ui.screens.ProfileScreen
import com.packabunch.ui.screens.SignInScreen
import com.packabunch.ui.screens.DoesntFitScreen
import com.packabunch.ui.screens.NoArrangementScreen
import com.packabunch.ui.screens.NotificationsScreen
import com.packabunch.ui.screens.OnbSetupScreen
import com.packabunch.ui.screens.PlanComparisonScreen
import com.packabunch.ui.format.formatDimensions
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
    const val MEASURE = "measure"          // Measure.dc.html + its recovery states
    const val MEASURE_REVIEW = "measureReview"
    const val ITEMS = "items"              // Items.dc.html
    const val PLAN_RESULT = "planResult"   // PlanResult.dc.html
    const val PACKING_GUIDE = "packingGuide" // PackingGuide.dc.html
    const val PACKING_DONE = "packingDone"   // PackingDone.dc.html
    const val SETTINGS = "settings"          // Settings.dc.html
    const val UPGRADE = "upgrade"            // Upgrade.dc.html
    const val PLAN_LAYERS = "planLayers"     // PlanLayers.dc.html
    const val ITEM_LIBRARY = "itemLibrary"   // ItemLibrary.dc.html / LockedLibrary.dc.html
    const val NOTIFICATION_SETTINGS = "notificationSettings" // NotificationSettings.dc.html
    const val SPACE_TYPE = "spaceType"             // SpaceType.dc.html
    const val SPACE_SCAN = "spaceScan"             // SpaceScan.dc.html
    const val SPACE_SCAN_REVIEW = "spaceScanReview" // SpaceScanReview.dc.html
    const val SPACE_OBSTRUCTIONS = "spaceObstructions" // SpaceObstructions.dc.html
    const val SCAN_INCOMPLETE = "scanIncomplete"   // ScanIncomplete.dc.html
    const val ONBOARDING = "onboarding"            // OnbMeasure/OnbPlan/OnbPack/Onboarding
    const val SIGN_IN = "signIn"                   // SignIn.dc.html
    const val LOG_IN = "logIn"                     // LogIn.dc.html
    const val CREATE_ACCOUNT = "createAccount"     // CreateAccount.dc.html
    const val FORGOT_PASSWORD = "forgotPassword"   // ForgotPassword.dc.html
    const val PROFILE = "profile"                  // Profile.dc.html
    const val ACCOUNT_DELETE = "accountDelete"     // AccountDelete.dc.html
    const val PLAN_IRREGULAR = "planIrregular"     // PlanIrregular.dc.html
    const val NO_ARRANGEMENT = "noArrangement"     // NoArrangement.dc.html
    const val DOESNT_FIT = "doesntFit"             // DoesntFit.dc.html
    const val PLAN_COMPARISON = "planComparison"   // PlanComparison.dc.html
    const val NOTIFICATIONS = "notifications"      // Notifications.dc.html
    const val ONB_SETUP = "onbSetup"               // OnbSetup.dc.html
    const val ITEM_PHOTO = "itemPhoto"             // ItemPhoto.dc.html
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    notice?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notice = null },
            title = { androidx.compose.material3.Text("Local preview") },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { notice = null }) {
                androidx.compose.material3.Text("OK")
            } },
        )
    }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val startRoute = remember { if (viewModel.setupCompleteAtLaunch) Routes.PROJECTS else Routes.ONBOARDING }
    fun home() = navController.navigate(Routes.PROJECTS) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
    }
    fun items() = navController.navigate(Routes.ITEMS) {
        popUpTo(Routes.ITEMS) { inclusive = true }
        launchSingleTop = true
    }
    fun finishSetup() {
        viewModel.completeSetup()
        navController.navigate(Routes.WELCOME) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
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
                    navController.navigate(Routes.SPACE_TYPE)
                },
                onTrySample = {
                    viewModel.openPack("sample") { navController.navigate(Routes.ITEMS) }
                },
                onSeeProjects = { home() },
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = { navController.navigate(Routes.ONB_SETUP) })
        }

        composable(Routes.ONB_SETUP) {
            OnbSetupScreen(
                unit = settings.unit,
                habit = settings.packingHabit,
                onUnitChange = viewModel::setUnit,
                onHabitChange = viewModel::setPackingHabit,
                onContinue = { finishSetup() },
                onSkip = { finishSetup() },
            )
        }

        composable(Routes.PLAN_COMPARISON) {
            PlanComparisonScreen(
                // Null until billing is wired, which disables the button rather than
                // showing an invented price.
                price = null,
                onSubscribe = { navController.navigate(Routes.UPGRADE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                // Empty until something has actually happened. No seeded fake messages.
                notifications = emptyList(),
                onMarkAllRead = {},
                onOpen = {},
                onChooseWhatShows = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SIGN_IN) {
            SignInScreen(
                onContinueWithGoogle = {},
                onContinueWithEmail = { navController.navigate(Routes.CREATE_ACCOUNT) },
                onLogIn = { navController.navigate(Routes.LOG_IN) },
                // Skippable while the accounts question is unresolved. Nothing in the app
                // blocks on being signed in, so forcing it here would be a wall with
                // nothing behind it.
                onSkip = { navController.popBackStack(Routes.WELCOME, inclusive = false) },
            )
        }

        composable(Routes.LOG_IN) {
            LogInScreen(
                localPackCount = projects.size,
                onLogIn = { _, _ -> navController.popBackStack(Routes.WELCOME, inclusive = false) },
                onGoogle = {},
                onForgot = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onCreateAccount = { navController.navigate(Routes.CREATE_ACCOUNT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                onCreate = { _, _, _, _ ->
                    navController.popBackStack(Routes.WELCOME, inclusive = false)
                },
                onLogIn = { navController.navigate(Routes.LOG_IN) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onSend = {},
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                email = null,
                tier = settings.tier,
                savedPackCount = projects.size,
                itemsMeasured = viewModel.libraryItems(projects).size,
                // Says what is true today, not what the design assumed.
                backupEnabled = false,
                onChangeEmail = {},
                onChangePassword = {},
                onManageSubscription = {},
                onSignOut = { navController.popBackStack(Routes.WELCOME, inclusive = false) },
                onDeleteAccount = { navController.navigate(Routes.ACCOUNT_DELETE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ACCOUNT_DELETE) {
            AccountDeleteScreen(
                email = "you@example.com",
                hasActiveSubscription = settings.tier == com.packabunch.packing.Tier.PLUS,
                onConfirmDelete = {
                    viewModel.deleteAllLocalData()
                    navController.popBackStack(Routes.WELCOME, inclusive = false)
                },
                onManageSubscription = {},
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PLAN_IRREGULAR) {
            PlanIrregularScreen(
                state = editor,
                onStartLoading = {
                    viewModel.setGuideStep(0)
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                onFixOpening = {},
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROJECTS) {
            ProjectsScreen(
                projects = projects,
                unit = settings.unit,
                onOpen = { id ->
                    viewModel.openPack(id) { navController.navigate(Routes.ITEMS) }
                },
                onNewPack = {
                    viewModel.startNewPack()
                    navController.navigate(Routes.SPACE_TYPE)
                },
                onBack = { navController.popBackStack() },
                onDelete = { viewModel.deleteProject(it.id) },
                onRestore = { viewModel.restoreProject(it) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onRename = viewModel::renameProject,
                onDuplicate = viewModel::duplicateProject,
                onRemeasure = { project ->
                    viewModel.openPack(project.id) { navController.navigate(Routes.MEASURE_REVIEW) }
                },
                onShare = { project ->
                    val summary = buildString {
                        appendLine(project.name)
                        appendLine(formatDimensions(project.space.dimensions, settings.unit))
                        appendLine(if (project.space.measurementSource == com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE) "Camera estimate" else "Typed in")
                        project.items.forEachIndexed { index, item ->
                            appendLine("${index + 1}. ${item.name} × ${item.quantity}: ${formatDimensions(item.dimensions, settings.unit)} — " +
                                if (item.measurementSource == com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE) "Camera estimate" else "Typed in")
                        }
                    }
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, summary), "Share pack"))
                },
            )
        }

        composable(Routes.SPACE_TYPE) {
            // Asked here rather than at launch: it costs a short-lived ARCore session, and
            // this is the first moment the answer changes anything on screen.
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.setDepthCapable(
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.packabunch.ar.ArAvailability.supportsDepth(context)
                    },
                )
            }

            SpaceTypeScreen(
                selected = editor.spaceKind,
                // Not every ARCore phone can sense depth, and mapping needs it. Checked
                // here so the choice is honest before it is made, not after.
                depthCapable = viewModel.depthCapable,
                limits = viewModel.limits,
                onSelect = viewModel::setSpaceKind,
                onContinue = {
                    if (editor.spaceKind == SpaceKind.ANY_SHAPE) {
                        navController.navigate(Routes.SPACE_SCAN)
                    } else {
                        navController.navigate(Routes.CREATE_SPACE)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SPACE_SCAN) {
            SpaceScanScreen(
                onScanned = { scan ->
                    viewModel.setScannedSpace(scan)
                    val report = scan.report()
                    // Significant gaps get their own screen before anything is planned on it.
                    if (report.completeness == ScanCompleteness.SIGNIFICANT_GAPS) {
                        navController.navigate(Routes.SCAN_INCOMPLETE)
                    } else {
                        navController.navigate(Routes.SPACE_SCAN_REVIEW)
                    }
                },
                onTypeInstead = { navController.navigate(Routes.CREATE_SPACE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SCAN_INCOMPLETE) {
            val scan = editor.space?.scan
            if (scan != null) {
                ScanIncompleteScreen(
                    report = scan.report(),
                    onSweepAgain = { navController.popBackStack() },
                    onUseSmaller = { navController.navigate(Routes.SPACE_SCAN_REVIEW) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.SPACE_SCAN_REVIEW) {
            val scan = editor.space?.scan
            if (scan != null) {
                SpaceScanReviewScreen(
                    scan = scan,
                    report = scan.report(),
                    onContinue = { navController.navigate(Routes.SPACE_OBSTRUCTIONS) },
                    onScanAgain = { navController.popBackStack(Routes.SPACE_SCAN, false) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.SPACE_OBSTRUCTIONS) {
            val scan = editor.space?.scan
            if (scan != null) {
                SpaceObstructionsScreen(
                    scan = scan,
                    report = scan.report(),
                    onToggle = viewModel::setObstructionIncluded,
                    onContinue = { navController.navigate(Routes.ITEMS) },
                    onBack = { navController.popBackStack() },
                )
            }
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
                onMeasureWithCamera = { navController.navigate(Routes.MEASURE) },
            )
        }

        composable(Routes.MEASURE) {
            MeasureScreen(
                unit = settings.unit,
                onMeasured = { dimensions, source ->
                    viewModel.setSpaceDimensions(dimensions, source)
                    // Straight back to the space screen, where every value is editable and
                    // carries its provenance. A camera estimate is never stored unreviewed.
                    navController.navigate(Routes.MEASURE_REVIEW)
                },
                onTypeInstead = { navController.popBackStack() },
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
                    // A scanned space gets the irregular result screen: litres rather than
                    // three dimensions, and the opening called out.
                    navController.navigate(
                        if (editor.space?.isScanned == true) Routes.PLAN_IRREGULAR
                        else Routes.PLAN_RESULT,
                    )
                },
                onBack = { navController.popBackStack() },
                initialEditItemId = editItemId,
                onEditConsumed = { editItemId = null },
                onLibrary = { navController.navigate(Routes.ITEM_LIBRARY) },
            )
        }

        composable(Routes.MEASURE_REVIEW) {
            editor.space?.let { space ->
                com.packabunch.ui.screens.MeasureReviewScreen(
                    dimensions = space.dimensions,
                    sources = com.packabunch.ui.screens.Axis3.entries.associateWith { space.measurementSource },
                    edgeGapMm = space.edgeGapMm,
                    unit = settings.unit,
                    onUnitChange = viewModel::setUnit,
                    onConfirm = { dimensions, sources, gap ->
                        viewModel.setSpaceDimensions(dimensions,
                            if (sources.values.all { it == com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE })
                                com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE
                            else com.packabunch.packing.MeasurementSource.TYPED_IN)
                        viewModel.setEdgeGap(gap)
                        items()
                    },
                    onMeasureAgain = { navController.navigate(Routes.MEASURE) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.DOESNT_FIT) {
            val placements = editor.plan?.placements?.sortedBy { it.sequenceIndex }.orEmpty()
            val current = placements.getOrNull(editor.guideStep)
            val item = editor.items.firstOrNull { it.id == current?.specId }
            DoesntFitScreen(
                itemName = item?.name ?: "This item",
                itemSummary = item?.let { formatDimensions(it.dimensions, settings.unit) }.orEmpty(),
                stepNumber = editor.guideStep + 1,
                totalSteps = placements.size,
                piecesAlreadyIn = editor.packedInstanceIds.size,
                onItemBigger = { editItemId = item?.id; items() },
                onSpaceSmaller = { navController.navigate(Routes.MEASURE_REVIEW) },
                onObstruction = {
                    navController.navigate(if (editor.space?.scan != null) Routes.SPACE_OBSTRUCTIONS else Routes.MEASURE_REVIEW)
                },
                onReplan = { items() },
                onSkipAndCarryOn = {
                    if (editor.guideStep + 1 < placements.size) {
                        viewModel.setGuideStep(editor.guideStep + 1)
                        navController.popBackStack()
                    } else items()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.PLAN_RESULT,
            enterTransition = { resultEnter() },
            popExitTransition = { resultExit() },
        ) {
            // Nothing placed at all is not a result screen with zeros in it — it is a
            // different screen with a different job: explaining that this search failed,
            // not that the items cannot fit.
            if (editor.plan?.placements?.isEmpty() == true && editor.items.isNotEmpty()) {
                NoArrangementScreen(
                    spaceName = editor.name.ifEmpty { "Your space" },
                    spaceSummary = editor.space
                        ?.let { formatDimensions(it.dimensions, settings.unit) }
                        .orEmpty(),
                    uprightItemCount = editor.items.count { it.keepUpright },
                    edgeGapMm = editor.space?.edgeGapMm ?: 0,
                    awkwardItemName = editor.plan?.unplaced?.firstOrNull()?.name,
                    unit = settings.unit,
                    onAllowTurning = { navController.popBackStack() },
                    onShrinkGap = { navController.navigate(Routes.MEASURE_REVIEW) },
                    onRemoveAwkward = { navController.popBackStack() },
                    onBackToItems = { navController.popBackStack() },
                    onChangeSpace = { navController.navigate(Routes.MEASURE_REVIEW) },
                    onBack = { navController.popBackStack() },
                )
                return@composable
            }

            PlanResultScreen(
                state = editor,
                unit = settings.unit,
                onStartPacking = {
                    viewModel.setGuideStep(0)
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                onEditItems = { navController.popBackStack() },
                onShowLayers = { navController.navigate(Routes.PLAN_LAYERS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PLAN_LAYERS) {
            PlanLayersScreen(
                state = editor,
                unit = settings.unit,
                onShowOverview = { navController.popBackStack() },
                onStartPacking = {
                    viewModel.setGuideStep(0)
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ITEM_LIBRARY) {
            ItemLibraryScreen(
                items = viewModel.libraryItems(projects),
                unlocked = viewModel.limits.itemLibrary,
                unit = settings.unit,
                price = null,
                onUse = { item ->
                    viewModel.upsertItem(item.copy(id = java.util.UUID.randomUUID().toString()))
                    navController.popBackStack()
                },
                onMeasureNew = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Routes.UPGRADE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NOTIFICATION_SETTINGS) {
            NotificationSettingsScreen(
                preferences = settings.notifications,
                systemNotificationsAllowed = true,
                onPreferencesChange = viewModel::setNotificationPreferences,
                onOpenSystemSettings = {},
                onTurnEverythingOff = viewModel::turnAllNotificationsOff,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PACKING_GUIDE) {
            PackingGuideScreen(
                state = editor,
                unit = settings.unit,
                onStepChange = viewModel::setGuideStep,
                onMarkPacked = viewModel::markPacked,
                onFinished = { navController.navigate(Routes.PACKING_DONE) },
                onDoesntFit = { navController.navigate(Routes.DOESNT_FIT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.PACKING_DONE,
            enterTransition = { resultEnter() },
        ) {
            PackingDoneScreen(
                state = editor,
                limits = viewModel.limits,
                savedPackCount = projects.size,
                onDone = {
                    home()
                },
                onSeePlan = {
                    navController.popBackStack(if (editor.space?.isScanned == true) Routes.PLAN_IRREGULAR else Routes.PLAN_RESULT, false)
                },
                onUpgrade = { navController.navigate(Routes.UPGRADE) },
                onFitAnswer = viewModel::recordDidItFit,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                savedPackCount = projects.size,
                onUnitChange = viewModel::setUnit,
                onNavigate = { destination ->
                    if (destination == NavDestination.Projects) {
                        home()
                    }
                },
                onNewPack = {
                    viewModel.startNewPack()
                    navController.navigate(Routes.SPACE_TYPE)
                },
                onUpgrade = { navController.navigate(Routes.UPGRADE) },
                onAccount = { notice = "Your packs are saved on this device. Accounts and cloud backup are not connected in this build." },
                onManageSubscription = { notice = "Google Play Billing is not connected in this build." },
                onRestorePurchases = { notice = "Purchase restoration is not available until Google Play Billing is connected." },
                onNotifications = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onDeleteAllData = viewModel::deleteAllLocalData,
                onPrivacy = { notice = "A published privacy policy has not been configured. This preview stores packs locally." },
                onTerms = { notice = "Published terms have not been configured for this preview." },
                onSupport = { notice = "A support address has not been configured for this preview." },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.UPGRADE) {
            UpgradeScreen(
                // Null until Play Billing is wired: the button stays disabled rather than
                // showing a price we invented.
                price = null,
                period = "a month",
                purchaseEnabled = false,
                onSubscribe = {},
                onRestore = {},
                onTerms = {},
                onPrivacy = {},
                onCompare = { navController.navigate(Routes.PLAN_COMPARISON) },
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
