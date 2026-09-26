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
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    const val SWEEP_ITEMS = "sweepItems"
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
    const val TERMS = "terms"
    const val PRIVACY = "privacy"
    const val INFO = "info"                        // Privacy, terms and help
    const val RESTORE = "restore"
    const val PACK_PLAN = "packPlan"
}

/**
 * Screen motion.
 *
 * Forward travel slides in from the right and settles; going back reverses it. That is the
 * Android convention rather than an invention, and it is doing a job here: this app is a
 * sequence — space, then items, then the plan, then the pack — and the direction of travel
 * is what tells you where you are in it.
 *
 * Durations come from Motion: 320 ms in, 180 ms out — visible, never slow, because the whole
 * app is meant to feel like a tool rather than a presentation.
 */
private const val SLIDE_FRACTION = 4

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

/** Google Play's own page for this app's subscription: cancel, change plan, update payment. */
private fun openPlaySubscriptions(context: android.content.Context) {
    val uri = android.net.Uri.parse("https://play.google.com/store/account/subscriptions?package=" + context.packageName)
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
}

/**
 * A page that keeps the nav bar, with the right-hand slot renamed to wherever you are.
 *
 * The page gets bottom room for the bar rather than sitting under it, so nothing a person needs
 * to tap ends up behind it.
 */
@Composable
private fun WithNavBar(
    here: com.packabunch.ui.components.NavSlot,
    onProjects: () -> Unit,
    onSettings: () -> Unit,
    onNewPack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content(Modifier.padding(bottom = com.packabunch.ui.components.NavPillClearance))
        com.packabunch.ui.components.PackBar(
            here = here,
            onProjects = onProjects,
            onSettings = onSettings,
            onNewPack = onNewPack,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

/** The plain-text summary a pack shares: what it is, how big, and everything in it. */
private fun shareSummary(project: com.packabunch.data.Project, unit: com.packabunch.ui.format.LengthUnit): String =
    buildString {
        appendLine(project.name)
        appendLine(formatDimensions(project.space.dimensions, unit))
        appendLine(if (project.space.measurementSource == com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE) "Camera estimate" else "Typed in")
        project.items.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.name} × ${item.quantity}: ${formatDimensions(item.dimensions, unit)} " +
                if (item.measurementSource == com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE) "Camera estimate" else "Typed in")
        }
    }

private fun sharePack(context: android.content.Context, project: com.packabunch.data.Project, unit: com.packabunch.ui.format.LengthUnit) {
    context.startActivity(
        android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, shareSummary(project, unit)),
            "Share pack",
        ),
    )
}

private val TabRoutes = setOf(Routes.PROJECTS, Routes.SETTINGS)

private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in TabRoutes && targetState.destination.route in TabRoutes

@Composable
fun PackNavHost(
    viewModel: AppViewModel,
    account: com.packabunch.auth.SupabaseAccount,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val projectsLoading by viewModel.projectsLoading.collectAsStateWithLifecycle()
    val dismissedResumes by viewModel.dismissedResumes.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var changeEmailOpen by rememberSaveable { mutableStateOf(false) }
    if (changeEmailOpen) {
        var newEmail by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { changeEmailOpen = false },
            title = { androidx.compose.material3.Text("Change your email") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        "We send a link to the new address. The change happens when you open it.",
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        singleLine = true,
                        label = { androidx.compose.material3.Text("New email") },
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = newEmail.contains('@') && newEmail.length > 3,
                    onClick = {
                        val address = newEmail.trim()
                        changeEmailOpen = false
                        scope.launch {
                            notice = runCatching { account.changeEmail(address) }.fold(
                                { "Check " + address + " for the link that finishes the change." },
                                { it.message ?: "Couldn't start the change. Try again." },
                            )
                        }
                    },
                ) { androidx.compose.material3.Text("Send the link") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { changeEmailOpen = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

    notice?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notice = null },
            title = { androidx.compose.material3.Text("Pack a Bunch") },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { notice = null }) {
                androidx.compose.material3.Text("OK")
            } },
        )
    }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var packMenuOpen by rememberSaveable { mutableStateOf(false) }
    var renamePack by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    var confirmDeletePack by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    var deletedForUndo by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    // Onboarding runs before sign-in (RequiredAccount), so a signed-in person always lands on their packs.
    val startRoute = Routes.PROJECTS
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
        home()
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
        // Tabs are siblings, not steps: switching between them fades through instead of sliding.
        enterTransition = { if (isTabSwitch()) resultEnter() else enterForward() },
        exitTransition = { if (isTabSwitch()) resultExit() else exitForward() },
        popEnterTransition = { if (isTabSwitch()) resultEnter() else enterBack() },
        popExitTransition = { if (isTabSwitch()) resultExit() else exitBack() },
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
            com.packabunch.ui.screens.GoogleAccountScreen(account = account,
                onSignOut = onSignOut, onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Account,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
            // Android's own photo picker: no storage permission, and it only ever hands back
            // the one picture the person chose.
            val avatars = remember(account.userId) {
                com.packabunch.data.cloud.CloudAvatar(account, account.userId.orEmpty())
            }
            val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
            ) { picked ->
                if (picked != null) scope.launch {
                    val url = avatars.upload(context, picked)
                    if (url != null) viewModel.setAvatar(url)
                    else notice = "Couldn't upload that picture. Check your connection and try again."
                }
            }
            ProfileScreen(
                modifier = pageModifier,
                avatarUrl = settings.avatarUrl ?: account.providerPhoto,
                onPickPhoto = {
                    pickPhoto.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onRemovePhoto = {
                    scope.launch {
                        avatars.remove()
                        viewModel.setAvatar(null)
                    }
                },
                email = account.email,
                tier = settings.tier,
                savedPackCount = projects.size,
                itemsMeasured = viewModel.libraryItems(projects).size,
                completedPacks = projects.count { project ->
                    project.currentPlan?.let { plan -> plan.placements.isNotEmpty() &&
                        plan.metrics.unplacedInstanceCount == 0 &&
                        plan.placements.all { it.instanceId in project.packedInstanceIds } } == true
                },
                // Says what is true today, not what the design assumed.
                backupEnabled = syncState.backedUp,
                syncMessage = syncState.message,
                syncRunning = syncState.running,
                onSync = viewModel::syncNow,
                onChangeEmail = { changeEmailOpen = true },
                onChangePassword = {
                    val email = account.email
                    if (email == null) notice = "Your account has no email address to send a reset link to."
                    else scope.launch {
                        notice = runCatching { account.sendRecovery(email) }.fold(
                            { "We sent a password reset link to $email." }, { it.message ?: "Couldn't send the reset link. Try again." })
                    }
                },
                onManageSubscription = { openPlaySubscriptions(context) },
                onSignOut = onSignOut,
                onDeleteAccount = { navController.navigate(Routes.ACCOUNT_DELETE) },
                onDownloadData = {
                    // Everything this account holds, as plain text somebody can actually read.
                    val everything = buildString {
                        appendLine("Pack a Bunch, data for " + (account.email ?: "this account"))
                        appendLine(projects.size.toString() + " packs")
                        appendLine()
                        projects.forEach { pack ->
                            appendLine(shareSummary(pack, settings.unit))
                            appendLine()
                        }
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TITLE, "pack-a-bunch-data.txt")
                                .putExtra(android.content.Intent.EXTRA_TEXT, everything),
                            "Your Pack a Bunch data",
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
                    }
}

        composable(Routes.ACCOUNT_DELETE) {
            AccountDeleteScreen(
                email = account.email.orEmpty(),
                hasActiveSubscription = settings.tier == com.packabunch.packing.Tier.PLUS,
                onConfirmDelete = {
                    scope.launch {
                        val problem = runCatching { account.deleteAccount() }.exceptionOrNull()
                        if (problem != null) {
                            notice = "Couldn't delete the account. " +
                                (problem.message ?: "Try again when you have a connection.")
                        } else {
                            viewModel.deleteAllLocalData()
                            onSignOut()
                        }
                    }
                },
                onManageSubscription = { openPlaySubscriptions(context) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PLAN_IRREGULAR) {
            PlanIrregularScreen(
                state = editor,
                onStartLoading = {
                    viewModel.resumeGuide()
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                onFixOpening = { navController.navigate(Routes.CREATE_SPACE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROJECTS) {
            val sync by viewModel.syncState.collectAsStateWithLifecycle()
            // Read once per arrival: greet after a sign in, stay quiet on every later visit.
            val greet = remember { com.packabunch.auth.JustSignedIn.consume() }
            ProjectsScreen(
                greet = greet,
                deleted = deletedForUndo,
                refreshing = sync.running,
                onRefresh = viewModel::syncNow,
                loading = projectsLoading,
                projects = projects,
                unit = settings.unit,
                onOpen = { id ->
                    // A pack that has already been planned opens on its plan; one that has not
                    // opens where the work is, on its items.
                    val planned = projects.firstOrNull { it.id == id }?.currentPlan != null
                    viewModel.openPack(id) {
                        navController.navigate(if (planned) Routes.PLAN_RESULT else Routes.ITEMS)
                    }
                },
                onNewPack = {
                    viewModel.startNewPack()
                    navController.navigate(Routes.SPACE_TYPE)
                },
                onBack = { navController.popBackStack() },
                onDelete = { viewModel.deleteProject(it.id) },
                onRestore = { viewModel.restoreProject(it); deletedForUndo = null },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onRename = viewModel::renameProject,
                onDuplicate = viewModel::duplicateProject,
                onRemeasure = { project ->
                    viewModel.openPack(project.id) { navController.navigate(Routes.MEASURE_REVIEW) }
                },
                onShare = { project -> sharePack(context, project, settings.unit) },
                onDeletedExpired = { deletedForUndo = null },
                onResumePacking = { project ->
                    viewModel.openPack(project.id) {
                        viewModel.resumeGuide()
                        navController.navigate(Routes.PACKING_GUIDE)
                    }
                },
                onStartOver = { project ->
                    viewModel.openPack(project.id) {
                        viewModel.restartPacking()
                        navController.navigate(Routes.PACKING_GUIDE)
                    }
                },
                onDismissResume = { viewModel.dismissResume(it.id) },
                dismissedResumes = dismissedResumes,
            )
        }

        composable(Routes.SPACE_TYPE) {
            // Asked here rather than at launch: it costs a short-lived ARCore session, and
            // this is the first moment the answer changes anything on screen.
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.updateDepthCapable(
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.packabunch.ar.ArAvailability.supportsDepth(context)
                    },
                )
            }

            SpaceTypeScreen(
                selected = editor.spaceKind,
                // Not every ARCore phone can sense depth, and mapping needs it. Checked
                // here so the choice is honest before it is made, not after.
                depthCapable = viewModel.depthCapable && settings.cameraMeasuring,
                limits = viewModel.limits,
                onSelect = viewModel::setSpaceKind,
                onContinue = {
                    if (!settings.cameraMeasuring) {
                        navController.navigate(Routes.CREATE_SPACE)
                    } else if (editor.spaceKind == SpaceKind.ANY_SHAPE) {
                        navController.navigate(Routes.SPACE_SCAN)
                    } else {
                        navController.navigate(Routes.MEASURE)
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
                spaceName = editor.space?.name?.takeIf { it.isNotBlank() },
                unit = settings.unit,
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
                habit = settings.packingHabit,
                onNext = { navController.navigate(Routes.ITEMS) },
                onBack = { navController.popBackStack() },
                onMeasureWithCamera = {
                    if (settings.cameraMeasuring) navController.navigate(Routes.MEASURE)
                    else notice = "Camera measuring is turned off in Settings."
                },
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
                onTypeInstead = { navController.navigate(Routes.CREATE_SPACE) {
                    popUpTo(Routes.MEASURE) { inclusive = true }
                } },
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
                onScan = {
                    if (settings.cameraMeasuring) navController.navigate(Routes.SWEEP_ITEMS)
                    else notice = "Camera measuring is turned off in Settings. You can still add items by typing their dimensions."
                },
            )
        }

        composable(Routes.SWEEP_ITEMS) {
            // The item scan: outlines on the objects, measured where they stand. What it
            // hands back already carries each object's crop; the photo is written under the
            // new item's id first, so the items list shows it the moment the item appears.
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val planLimit = viewModel.limits.maxPiecesPerPack
            com.packabunch.ui.screens.ItemScanScreen(
                unit = settings.unit,
                // What this pack can still take. Past it the scan stops starting new objects,
                // rather than measuring twenty more and refusing them at the end.
                maxItems = (planLimit ?: com.packabunch.packing.PackingEngine.MAX_INSTANCE_COUNT)
                    .minus(editor.pieceCount).coerceAtLeast(0),
                planLimit = planLimit,
                alreadyInPack = editor.pieceCount,
                onDone = { results ->
                    scope.launch {
                        val withIds = results.map { java.util.UUID.randomUUID().toString() to it }
                        for ((id, result) in withIds) {
                            result.photo?.let { com.packabunch.data.ItemPhotos.store(context, id, it) }
                        }
                        if (viewModel.importScannedItems(withIds)) navController.popBackStack()
                        else notice = "These items exceed this pack's piece limit. Review the current items first."
                    }
                },
                onManual = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
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
                    onMeasureAgain = { navController.navigate(if (settings.cameraMeasuring) Routes.MEASURE else Routes.CREATE_SPACE) },
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
                    viewModel.resumeGuide()
                    navController.navigate(Routes.PACKING_GUIDE)
                },
                // Goes to the items list rather than back: a planned pack opens on its plan, so
                // there is nothing underneath to pop to.
                onEditItems = { navController.navigate(Routes.ITEMS) { launchSingleTop = true } },
                onShowLayers = { navController.navigate(Routes.PLAN_LAYERS) },
                onMenu = { packMenuOpen = true },
                onBack = { navController.popBackStack() },
            )

            renamePack?.let { pack ->
                var newName by remember(pack.id) { mutableStateOf(pack.name) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { renamePack = null },
                    title = { androidx.compose.material3.Text("Rename pack") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(newName, { newName = it }, singleLine = true)
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = { viewModel.renameProject(pack, newName.trim()); renamePack = null },
                        ) { androidx.compose.material3.Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { renamePack = null }) {
                            androidx.compose.material3.Text("Cancel")
                        }
                    },
                )
            }

            confirmDeletePack?.let { pack ->
                com.packabunch.ui.screens.DeleteConfirmDialog(
                    packName = pack.name,
                    itemCount = pack.items.size,
                    photoCount = 0,
                    onConfirm = {
                        confirmDeletePack = null
                        viewModel.deleteProject(pack.id)
                        deletedForUndo = pack
                        home()
                    },
                    onCancel = { confirmDeletePack = null },
                )
            }

            // Rename, duplicate, remeasure, share and delete: one sheet, opened from the pack's
            // own page rather than from a menu button on every card in the list.
            val pack = projects.firstOrNull { it.id == editor.projectId }
            if (packMenuOpen && pack != null) {
                com.packabunch.ui.screens.ProjectMenuSheet(
                    project = pack,
                    unit = settings.unit,
                    onRename = { packMenuOpen = false; renamePack = pack },
                    onDuplicate = { packMenuOpen = false; viewModel.duplicateProject(pack) },
                    onRemeasure = {
                        packMenuOpen = false
                        viewModel.openPack(pack.id) { navController.navigate(Routes.CREATE_SPACE) }
                    },
                    onShare = { packMenuOpen = false; sharePack(context, pack, settings.unit) },
                    onDelete = { packMenuOpen = false; confirmDeletePack = pack },
                    onDismiss = { packMenuOpen = false },
                )
            }
        }

        composable(Routes.PLAN_LAYERS) {
            PlanLayersScreen(
                state = editor,
                unit = settings.unit,
                onShowOverview = { navController.popBackStack() },
                onStartPacking = {
                    viewModel.resumeGuide()
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
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Notifications,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
            NotificationSettingsScreen(
                modifier = pageModifier,
                preferences = settings.notifications,
                systemNotificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
                onPreferencesChange = viewModel::setNotificationPreferences,
                onOpenSystemSettings = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                },
                onTurnEverythingOff = viewModel::turnAllNotificationsOff,
                onBack = { navController.popBackStack() },
            )
                    }
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
                onAccount = { navController.navigate(Routes.PROFILE) },
                email = account.email,
                avatarUrl = settings.avatarUrl ?: account.providerPhoto,
                onCameraMeasuringChange = viewModel::setCameraMeasuring,
                onDefaultEdgeGapChange = viewModel::setDefaultEdgeGap,
                onManageSubscription = { navController.navigate(Routes.PACK_PLAN) },
                onRestorePurchases = { navController.navigate(Routes.RESTORE) },
                onNotifications = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                onDeleteAllData = viewModel::deleteAllLocalData,
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onTerms = { navController.navigate(Routes.TERMS) },
                onSupport = { navController.navigate(Routes.INFO) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.INFO) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Info,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
                com.packabunch.ui.screens.InfoScreen(
                    modifier = pageModifier,
                    version = "Pack a Bunch " + com.packabunch.BuildConfig.VERSION_NAME +
                        " (" + com.packabunch.BuildConfig.VERSION_CODE + ")",
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onTerms = { navController.navigate(Routes.TERMS) },
                    onSupport = { notice = "Support: " + com.packabunch.ui.screens.SUPPORT_EMAIL },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.RESTORE) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Restore,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
                com.packabunch.ui.screens.RestorePurchasesScreen(
                    modifier = pageModifier,
                    onRestore = { done -> viewModel.restorePurchases(done) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.PACK_PLAN) {
            val plusPackage by viewModel.plusPackage.collectAsStateWithLifecycle()
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.PackPlan,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
                com.packabunch.ui.screens.PackPlanScreen(
                    modifier = pageModifier,
                    tier = settings.tier,
                    price = plusPackage?.product?.price?.formatted,
                    onUpgrade = { navController.navigate(Routes.UPGRADE) },
                    onManageInPlay = { openPlaySubscriptions(context) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.TERMS) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Info,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
            com.packabunch.ui.screens.TermsScreen(
                modifier = pageModifier,onBack = { navController.popBackStack() })
                    }
}

        composable(Routes.PRIVACY) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Info,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
            com.packabunch.ui.screens.PrivacyPolicyScreen(
                modifier = pageModifier,onBack = { navController.popBackStack() })
                    }
}

        composable(Routes.UPGRADE) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.PackPlan,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { viewModel.startNewPack(); navController.navigate(Routes.SPACE_TYPE) },
            ) { pageModifier ->
            val plusPackage by viewModel.plusPackage.collectAsStateWithLifecycle()
            val outcome by viewModel.purchaseOutcome.collectAsStateWithLifecycle()
            val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
            outcome?.let { result ->
                com.packabunch.ui.screens.PurchaseOutcomeScreen(
                    outcome = result,
                    onContinue = {
                        viewModel.clearPurchaseOutcome()
                        if (result == com.packabunch.ui.screens.PurchaseOutcome.Succeeded) navController.popBackStack()
                    },
                    onTryAgain = { viewModel.clearPurchaseOutcome(); viewModel.subscribe(activity) },
                    onContactSupport = { viewModel.clearPurchaseOutcome() },
                )
                return@WithNavBar
            }
            UpgradeScreen(
                modifier = pageModifier,
                // Google Play's localised price, or null — never a price we invented.
                price = plusPackage?.product?.price?.formatted,
                period = "a month",
                purchaseEnabled = plusPackage != null,
                onSubscribe = { viewModel.subscribe(activity) },
                onRestore = {
                    viewModel.restorePurchases { restored ->
                        notice = if (restored) "Pack a Bunch Pro restored." else
                            "No active Pack a Bunch Pro on this Google account. Restoring does not bring back deleted packs."
                    }
                },
                onTerms = { navController.navigate(Routes.TERMS) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onCompare = { navController.navigate(Routes.PLAN_COMPARISON) },
                onBack = { navController.popBackStack() },
            )
                    }
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

