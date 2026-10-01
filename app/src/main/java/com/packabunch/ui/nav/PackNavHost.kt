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
    const val PHOTO_ITEMS = "photoItems"            // ItemPhoto · Take / Finding sizes / Done / Check item
    const val PHOTO_SPACE = "photoSpace"            // SpacePhoto · Take / Finding sizes / Done / Check the space
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
    // Tapped a notification on the phone: straight to the Notifications page.
    val openNotifications by com.packabunch.notify.NotificationLink.pending.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(openNotifications) {
        if (openNotifications) {
            com.packabunch.notify.NotificationLink.pending.value = false
            navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var promoOpen by remember { mutableStateOf(false) }
    // A deletion stopped by signing in again is said once, as a notice.
    val accountNotice by viewModel.accountNotice.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(accountNotice) {
        accountNotice?.let { notice = it; viewModel.accountNoticeShown() }
    }
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
        // The app's own feedback strip, not a system dialog: it rises in above the nav bar,
        // stays long enough to read, and leaves by itself. Longer messages stay longer.
        androidx.compose.ui.window.Popup(
            alignment = androidx.compose.ui.Alignment.BottomCenter,
            offset = androidx.compose.ui.unit.IntOffset(0, -with(androidx.compose.ui.platform.LocalDensity.current) { 104.dp.roundToPx() }),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        ) {
            androidx.compose.runtime.key(message) {
                com.packabunch.ui.components.PackToast(
                    message = message,
                    dwellMillis = (2_500L + message.length * 45L).coerceAtMost(8_000L),
                    onDismiss = { notice = null },
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
    // A free limit reached: a small card that says which one, with the way on to Pack Plus —
    // not the whole Pack Plus page thrown up in the middle of what the person was doing.
    var limitHit by remember { mutableStateOf<LimitHit?>(null) }
    limitHit?.let { hit ->
        androidx.compose.ui.window.Popup(
            onDismissRequest = { limitHit = null },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            com.packabunch.ui.components.PackPopup(
                icon = com.packabunch.ui.components.PackIcons.Cube,
                iconTint = com.packabunch.ui.theme.Primary,
                iconBackground = com.packabunch.ui.theme.BrandTint,
                title = hit.title,
                body = hit.body,
                primary = "Try Pack Plus",
                onPrimary = { limitHit = null; navController.navigate(Routes.UPGRADE) },
                secondary = hit.otherWay,
                onSecondary = { limitHit = null; hit.onOtherWay() },
                footnote = "Pack Plus has no limits: packs, pieces, scans and any size of space.",
                onDismiss = { limitHit = null },
            )
        }
    }
    // "How are we doing?" — now and then after a pack is finished, over whatever comes next.
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    if (feedbackOpen) {
        var sending by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Popup(
            onDismissRequest = { feedbackOpen = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            com.packabunch.ui.screens.FeedbackCard(
                sending = sending,
                problem = problem,
                onSend = { stars, words ->
                    sending = true; problem = null
                    viewModel.sendFeedback(stars, words, com.packabunch.review.ReviewPrompt.packsFinished(context)) { sent ->
                        sending = false
                        if (sent) { feedbackOpen = false; notice = "Thank you. We read every one." }
                        else problem = "Couldn't send it. Check your connection and try again."
                    }
                },
                onSkip = { feedbackOpen = false },
            )
        }
    }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var packMenuOpen by rememberSaveable { mutableStateOf(false) }
    var renamePack by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    var confirmDeletePack by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    var deletedForUndo by remember { mutableStateOf<com.packabunch.data.Project?>(null) }
    // Onboarding runs before sign-in (RequiredAccount), so a signed-in person always lands on their packs.
    val startRoute = Routes.PROJECTS
    // Free keeps five saved packs. Starting or copying another goes to Pack Plus instead, with
    // the reason said; deleting one is always the free way out.
    fun packRoomOrUpsell(): Boolean {
        if (viewModel.limits.allowsAnotherPack(viewModel.savedPackCount())) return true
        val max = viewModel.limits.maxSavedPacks
        limitHit = LimitHit(
            title = "You've used your $max free packs",
            body = "Free keeps $max saved packs. Delete one to start another, or try Pack Plus to keep as many as you like.",
        )
        return false
    }
    fun newPack() {
        if (!packRoomOrUpsell()) return
        viewModel.startNewPack()
        navController.navigate(Routes.SPACE_TYPE)
    }
    // Free includes a few scans a day; typing sizes in is never limited, so that is offered.
    fun scanOrType(route: String, typed: () -> Unit) {
        if (viewModel.tryStartScan()) { navController.navigate(route); return }
        limitHit = LimitHit(
            title = "No scans left today",
            body = "Free includes ${viewModel.limits.maxScansPerDay} scans a day, and they come back tomorrow. Type the sizes in for now, or try Pack Plus to scan as often as you like.",
            otherWay = "Type the sizes in",
            onOtherWay = typed,
        )
    }
    fun noScansLeft(typed: () -> Unit) {
        limitHit = LimitHit(
            title = "No scans left today",
            body = "Free includes ${viewModel.limits.maxScansPerDay} photo scans a day, and they come back tomorrow. Type the sizes in for now, or try Pack Plus to scan as often as you like.",
            otherWay = "Type the sizes in",
            onOtherWay = typed,
        )
    }
    // A photo spends a scan when it is taken, not when the camera opens: looking is free.
    fun photoOrType(route: String, typed: () -> Unit) {
        if (viewModel.canScan()) navController.navigate(route) else noScansLeft(typed)
    }
    fun home() = navController.navigate(Routes.PROJECTS) {
        popUpTo(navController.graph.id) { inclusive = true }
        launchSingleTop = true
    }
    /**
     * A pack's main pages — its items, its plan and the packing guide. Opening one clears
     * whatever pages were stacked in front of it, so back from any of them is one step to
     * Projects instead of a walk back through every screen the pack went through. The steps
     * in between (scans, reviews, the space's type) still go back to where they came from.
     */
    fun toPackPage(route: String) = navController.navigate(route) {
        popUpTo(Routes.PROJECTS)
        launchSingleTop = true
    }
    fun items() = toPackPage(Routes.ITEMS)
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
                    newPack()
                },
                onTrySample = {
                    viewModel.openPack("sample") { toPackPage(Routes.ITEMS) }
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
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Notifications,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { newPack() },
            ) { pageModifier ->
                val notifications by viewModel.notifications.collectAsStateWithLifecycle()
                NotificationsScreen(
                    modifier = pageModifier,
                    // Worked out from what is true of this account; nothing seeded.
                    notifications = notifications,
                    onMarkAllRead = viewModel::markNotificationsRead,
                    onOpen = { n ->
                        viewModel.markNotificationRead(n.id)
                        when {
                            n.id.startsWith("progress-") -> {
                                val packId = n.id.removePrefix("progress-").substringBeforeLast('-')
                                viewModel.openPack(packId) { viewModel.resumeGuide(); toPackPage(Routes.PACKING_GUIDE) }
                            }
                            n.id.startsWith("done-") -> {
                                val packId = n.id.removePrefix("done-").substringBeforeLast('-')
                                viewModel.openPack(packId) { toPackPage(Routes.PLAN_RESULT) }
                            }
                            n.id.startsWith("plus-ending-") || n.id.startsWith("plus-ended-") ->
                                navController.navigate(Routes.UPGRADE)
                            n.id.startsWith("plus-") -> navController.navigate(Routes.PACK_PLAN)
                            else -> Unit
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
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
                onNewPack = { newPack() },
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
                    avatars.uploadOrWhy(context, picked).fold(
                        { url -> viewModel.setAvatar(url).onFailure { notice = "The picture uploaded, but could not be saved to your account. Please try again." } },
                        // The real reason, so a refusal from storage is not passed off as bad internet.
                        { notice = "Couldn't upload that picture. " + (it.message ?: "") },
                    )
                }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshAccountSettings() }
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
                        viewModel.setAvatar(null).onSuccess { avatars.remove() }
                            .onFailure { notice = "Couldn't remove the photo from your account. Please try again." }
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
            var deleting by remember { mutableStateOf(false) }
            AccountDeleteScreen(
                email = account.email.orEmpty(),
                packCount = projects.size,
                thingCount = projects.sumOf { it.items.size },
                hasActiveSubscription = settings.tier == com.packabunch.packing.Tier.PLUS,
                deleting = deleting,
                onConfirmDelete = {
                    deleting = true
                    scope.launch {
                        // The deletion itself happens in 30 days (request_account_deletion);
                        // signing in before then stops it. The profile photo sits in a public
                        // bucket, not in a database row, so it is taken down now rather than
                        // left online for a month.
                        val problem = runCatching { account.requestDeletion() }.exceptionOrNull()
                        if (problem != null) {
                            deleting = false
                            notice = "Couldn't delete the account. " +
                                (problem.message ?: "Try again when you have a connection.")
                        } else {
                            runCatching { com.packabunch.data.cloud.CloudAvatar(account, account.userId.orEmpty()).remove() }
                            // Off this phone, without telling the account the packs are gone.
                            viewModel.forgetThisPhone()
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
                    toPackPage(Routes.PACKING_GUIDE)
                },
                onFixOpening = { navController.navigate(Routes.CREATE_SPACE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROJECTS) {
            val sync by viewModel.syncState.collectAsStateWithLifecycle()
            // Read once per arrival: greet after a sign in, stay quiet on every later visit.
            val greet = remember { com.packabunch.auth.JustSignedIn.consume() }
            val notificationList by viewModel.notifications.collectAsStateWithLifecycle()
            ProjectsScreen(
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                unreadNotifications = notificationList.count { it.unread },
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
                    newPack()
                },
                onBack = { navController.popBackStack() },
                onDelete = { viewModel.deleteProject(it.id) },
                onRestore = { viewModel.restoreProject(it); deletedForUndo = null },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onRename = viewModel::renameProject,
                onDuplicate = { if (packRoomOrUpsell()) viewModel.duplicateProject(it) },
                onRemeasure = { project ->
                    viewModel.openPack(project.id) { navController.navigate(Routes.MEASURE_REVIEW) }
                },
                onShare = { project -> sharePack(context, project, settings.unit) },
                onDeletedExpired = {
                    // Past undo: the deleted pack's photos go too, except any another pack still uses.
                    deletedForUndo?.let { gone ->
                        val stillUsed = projects.flatMap { p -> p.items.map { it.id } }.toSet()
                        gone.items.filter { it.id !in stillUsed }.forEach { item ->
                            com.packabunch.data.ItemPhotos.remove(com.packabunch.data.ItemPhotos.pathFor(context, item.id))
                        }
                    }
                    deletedForUndo = null
                },
                onResumePacking = { project ->
                    viewModel.openPack(project.id) {
                        viewModel.resumeGuide()
                        toPackPage(Routes.PACKING_GUIDE)
                    }
                },
                onStartOver = { project ->
                    viewModel.openPack(project.id) {
                        viewModel.restartPacking()
                        toPackPage(Routes.PACKING_GUIDE)
                    }
                },
                onDismissResume = { viewModel.dismissResume(it.id) },
                dismissedResumes = dismissedResumes,
            )
        }

        composable(Routes.SPACE_TYPE) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.updateCameraCapable(
                    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY),
                )
            }

            SpaceTypeScreen(
                selected = editor.spaceKind,
                cameraCapable = viewModel.cameraCapable && settings.cameraMeasuring,
                limits = viewModel.limits,
                onSelect = viewModel::setSpaceKind,
                onContinue = {
                    if (!settings.cameraMeasuring) {
                        navController.navigate(Routes.CREATE_SPACE)
                    } else {
                        photoOrType(Routes.PHOTO_SPACE) { navController.navigate(Routes.CREATE_SPACE) }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SPACE_SCAN) {
            SpaceScanScreen(
                onScanned = { scan ->
                    // Free maps spaces up to a size; a bigger one is Pack Plus. The same space
                    // can always be typed in, at any size.
                    val g = scan.baseGrid
                    val litres = g.countX.toDouble() * g.countY * g.countZ * g.resolutionMm.toDouble().let { it * it * it } / 1e6
                    if (!viewModel.limits.allowsSpaceLitres(litres)) {
                        limitHit = LimitHit(
                            title = "This space is too big for Free",
                            body = "Free maps spaces up to ${viewModel.limits.maxScannedSpaceLitres} litres; this one is about ${litres.toInt()}. Type its size in, or try Pack Plus to map any size.",
                        )
                        return@SpaceScanScreen
                    }
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
                    onContinue = { toPackPage(Routes.ITEMS) },
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
                onNext = { toPackPage(Routes.ITEMS) },
                onBack = { navController.popBackStack() },
                onMeasureWithCamera = {
                    if (settings.cameraMeasuring) photoOrType(Routes.PHOTO_SPACE) { }
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
                spaceName = editor.space?.name?.takeIf { it.isNotBlank() },
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
                    if (settings.cameraMeasuring) photoOrType(Routes.PHOTO_ITEMS) { }
                    else notice = "Camera measuring is turned off in Settings. You can still add items by typing their dimensions."
                },
            )
        }

        composable(Routes.PHOTO_ITEMS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val planLimit = viewModel.limits.maxPiecesPerPack
            com.packabunch.ui.screens.PhotoItemsFlow(
                unit = settings.unit,
                onUnitChange = viewModel::setUnit,
                // Free stops at its piece limit; Pack Plus has no limit of its own.
                maxItems = (planLimit ?: com.packabunch.packing.PackingEngine.MAX_INSTANCE_COUNT).minus(editor.pieceCount).coerceAtLeast(1),
                planLimited = planLimit != null,
                lookup = remember { viewModel.itemLookup() },
                takeScan = { viewModel.tryStartScan().also { if (!it) noScansLeft { navController.popBackStack() } } },
                onSave = { checked ->
                    scope.launch {
                        val withIds = checked.map { java.util.UUID.randomUUID().toString() to it }
                        for ((id, c) in withIds) c.photo?.let { com.packabunch.data.ItemPhotos.store(context, id, it) }
                        if (viewModel.importCheckedItems(withIds)) navController.popBackStack()
                        else notice = "These items exceed this pack's piece limit. Review the current items first."
                    }
                },
                onTypeInstead = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PHOTO_SPACE) {
            com.packabunch.ui.screens.PhotoSpaceFlow(
                spaceName = editor.space?.name?.takeIf { it.isNotBlank() },
                unit = settings.unit,
                onUnitChange = viewModel::setUnit,
                edgeGapMm = editor.space?.edgeGapMm ?: 5,
                takeScan = { viewModel.tryStartScan().also { if (!it) noScansLeft { navController.navigate(Routes.CREATE_SPACE) } } },
                onSave = { name, dimensions, typed, gap ->
                    viewModel.setSpaceName(name)
                    viewModel.setSpaceDimensions(dimensions,
                        if (typed) com.packabunch.packing.MeasurementSource.TYPED_IN else com.packabunch.packing.MeasurementSource.CAMERA_ESTIMATE)
                    viewModel.setEdgeGap(gap)
                    items()
                },
                onTypeInstead = { navController.navigate(Routes.CREATE_SPACE) },
                onBack = { navController.popBackStack() },
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
                    onMeasureAgain = { navController.navigate(if (settings.cameraMeasuring) Routes.PHOTO_SPACE else Routes.CREATE_SPACE) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.DOESNT_FIT) {
            val placements = editor.plan?.placements?.sortedBy { it.sequenceIndex }.orEmpty()
            val current = placements.getOrNull(editor.guideStep)
            val item = editor.items.firstOrNull { it.id == current?.specId }
            DoesntFitScreen(
                itemNumber = editor.items.indexOfFirst { it.id == item?.id } + 1,
                spaceNoun = com.packabunch.ui.screens.spaceNoun(editor.space?.name, standingInside = false),
                onReport = viewModel::reportFit,
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
                onReplan = { editItemId = item?.id; items() },
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
                    toPackPage(Routes.PACKING_GUIDE)
                },
                // Goes to the items list rather than back: a planned pack opens on its plan, so
                // there is nothing underneath to pop to.
                onEditItems = { toPackPage(Routes.ITEMS) },
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
                    // The item photos kept on this phone go with the pack.
                    photoCount = pack.items.count { com.packabunch.data.ItemPhotos.pathFor(context, it.id) != null },
                    hasPlan = pack.plan != null,
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
                    onDuplicate = { packMenuOpen = false; if (packRoomOrUpsell()) viewModel.duplicateProject(pack) },
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
                    toPackPage(Routes.PACKING_GUIDE)
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
                onNewPack = { newPack() },
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
                    // Whatever they answered about the fit: the prompt never depends on it.
                    val askedPlay = (context as? android.app.Activity)?.let(com.packabunch.review.ReviewPrompt::packFinished) ?: false
                    // Once or twice a week, our own card — never on top of Play's.
                    if (!askedPlay && com.packabunch.review.ReviewPrompt.feedbackDue(context)) feedbackOpen = true
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
            androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshAccountSettings() }
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
                    newPack()
                },
                onUpgrade = { navController.navigate(Routes.UPGRADE) },
                onAccount = { navController.navigate(Routes.PROFILE) },
                email = account.email,
                avatarUrl = settings.avatarUrl ?: account.providerPhoto,
                onCameraMeasuringChange = viewModel::setCameraMeasuring,
                onLookUpItemsChange = viewModel::setLookUpItems,
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
                onNewPack = { newPack() },
            ) { pageModifier ->
                com.packabunch.ui.screens.InfoScreen(
                    modifier = pageModifier,
                    version = "Pack a Bunch " + com.packabunch.BuildConfig.VERSION_NAME +
                        " (" + com.packabunch.BuildConfig.VERSION_CODE + ")",
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onTerms = { navController.navigate(Routes.TERMS) },
                    onSupport = {
                        val opened = com.packabunch.review.ReviewPrompt.feedback(context, com.packabunch.ui.screens.SUPPORT_EMAIL,
                            com.packabunch.BuildConfig.VERSION_NAME + " (" + com.packabunch.BuildConfig.VERSION_CODE + ")")
                        if (!opened) notice = "Support: " + com.packabunch.ui.screens.SUPPORT_EMAIL
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.RESTORE) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Restore,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { newPack() },
            ) { pageModifier ->
                com.packabunch.ui.screens.RestorePurchasesScreen(
                    modifier = pageModifier,
                    onRestore = { done -> viewModel.restorePurchases(done) },
                    onBack = { navController.popBackStack() },
                )
            }
        }


        composable(Routes.TERMS) {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.Info,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { newPack() },
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
                onNewPack = { newPack() },
            ) { pageModifier ->
            com.packabunch.ui.screens.PrivacyPolicyScreen(
                modifier = pageModifier,onBack = { navController.popBackStack() })
                    }
}

        // Pack Plus: the one page for buying it and for managing it — the Pack Plan tab and
        // Settings' "Manage" both land here.
        @Composable
        fun PackPlusPage() {
            WithNavBar(
                here = com.packabunch.ui.components.NavSlots.PackPlan,
                onProjects = ::home,
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onNewPack = { newPack() },
            ) { pageModifier ->
            val plans by viewModel.plans.collectAsStateWithLifecycle()
            // Ask Google Play again each time the page opens: a failed first load is not final.
            androidx.compose.runtime.LaunchedEffect(Unit) { if (plans.isEmpty()) viewModel.reloadPlans() }
            val selectedPlan by viewModel.selectedPlan.collectAsStateWithLifecycle()
            val outcome by viewModel.purchaseOutcome.collectAsStateWithLifecycle()
            val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
            androidx.compose.foundation.layout.Box(pageModifier) {
            UpgradeScreen(
                // Google Play's localised plans, or none — never a price we invented.
                plans = plans,
                selected = selectedPlan,
                onSelect = viewModel::selectPlan,
                onSubscribe = { viewModel.subscribe(activity) },
                onRestore = {
                    viewModel.restorePurchases { restored ->
                        notice = if (restored) "Pack Plus restored." else
                            "No active Pack Plus on this Google account. Restoring does not bring back deleted packs."
                    }
                },
                onTerms = { navController.navigate(Routes.TERMS) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onCompare = { navController.navigate(Routes.PLAN_COMPARISON) },
                onBack = { navController.popBackStack() },
                onHaveCode = { promoOpen = true },
                plusOn = settings.tier == com.packabunch.packing.Tier.PLUS,
                plusUntil = viewModel.promoPlusEnds(),
                onManageInPlay = { openPlaySubscriptions(context) },
                onRetry = viewModel::reloadPlans,
            )
            if (promoOpen) {
                var code by remember { mutableStateOf("") }
                var checking by remember { mutableStateOf(false) }
                var problem by remember { mutableStateOf<String?>(null) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { if (!checking) promoOpen = false },
                    title = { androidx.compose.material3.Text("Promo code") },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.OutlinedTextField(
                                value = code, onValueChange = { code = it; problem = null },
                                singleLine = true, label = { androidx.compose.material3.Text("Code") },
                            )
                            problem?.let { androidx.compose.material3.Text(it, color = com.packabunch.ui.theme.ErrorRed) }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(enabled = code.isNotBlank() && !checking, onClick = {
                            checking = true
                            viewModel.redeemPromo(code.trim()) { until, error ->
                                checking = false
                                when {
                                    error != null -> problem = error
                                    until == null -> problem = "That code isn't valid, has been used up or has expired."
                                    else -> {
                                        promoOpen = false
                                        notice = "Pack Plus is on until " + java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")
                                            .format(until.atZone(java.time.ZoneId.systemDefault())) + "."
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }) { androidx.compose.material3.Text(if (checking) "Checking…" else "Use code") }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { promoOpen = false }) { androidx.compose.material3.Text("Cancel") } },
                )
            }
            // What the purchase did, over the page it was made from.
            outcome?.let { attempt ->
                val context = androidx.compose.ui.platform.LocalContext.current
                com.packabunch.ui.screens.PurchasePopup(
                    attempt = attempt,
                    renewsEvery = plans.getOrNull(selectedPlan)?.kind?.every ?: "month",
                    onClose = viewModel::clearPurchaseOutcome,
                    onTryAgain = { viewModel.clearPurchaseOutcome(); viewModel.subscribe(activity) },
                    onCheckAgain = {
                        viewModel.checkPurchaseAgain { notice = "Still waiting on Google Play. Pack Plus switches on by itself when it clears." }
                    },
                    onDone = { viewModel.clearPurchaseOutcome(); navController.popBackStack() },
                    onSeeUnlocked = { viewModel.clearPurchaseOutcome(); navController.navigate(Routes.PLAN_COMPARISON) },
                    onGetHelp = { reference ->
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(com.packabunch.ui.screens.SUPPORT_EMAIL))
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Pack Plus purchase didn't go through" + (reference?.let { " ($it)" } ?: ""))
                        }
                        if (runCatching { context.startActivity(intent) }.isFailure) {
                            notice = "No email app found. Write to ${com.packabunch.ui.screens.SUPPORT_EMAIL}" + (reference?.let { " with the code $it." } ?: ".")
                        }
                    },
                )
            }
            }
                    }
}
        composable(Routes.UPGRADE) { PackPlusPage() }
        composable(Routes.PACK_PLAN) { PackPlusPage() }
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

/** Which free limit was reached, in words, and the free way on when there is one. */
private data class LimitHit(
    val title: String,
    val body: String,
    val otherWay: String = "Not now",
    val onOtherWay: () -> Unit = {},
)
