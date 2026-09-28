package com.packabunch.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.packabunch.data.Project
import com.packabunch.data.ProjectRepository
import com.packabunch.packing.Dimensions
import com.packabunch.packing.InputProblem
import com.packabunch.packing.ItemSpec
import com.packabunch.packing.MeasurementSource
import com.packabunch.packing.PackingEngine
import com.packabunch.packing.PackingPlan
import com.packabunch.packing.PackingRequest
import com.packabunch.packing.SolveBudget
import com.packabunch.packing.SolveResult
import com.packabunch.packing.Space
import com.packabunch.packing.Tier
import com.packabunch.packing.TierLimits
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.screens.LibraryItem
import com.packabunch.ui.screens.NotificationPreferences
import com.packabunch.ui.screens.PackingHabit
import com.packabunch.ui.screens.SpaceKind
import com.packabunch.packing.ScannedSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One store for the whole app.
 *
 * Small on purpose. The screens are many but the state is not: the saved packs, whichever
 * one is being edited, the unit preference and the tier. Splitting that across a dozen
 * ViewModels would add wiring without adding clarity.
 */
class AppViewModel(
    private val repository: ProjectRepository,
    private val preferences: android.content.SharedPreferences,
    private val cloudSync: com.packabunch.data.cloud.CloudPackSync? = null,
    private val userId: String? = null,
    private val cloudSettings: com.packabunch.data.cloud.CloudSettings? = null,
    /** Only for the Realtime connection that tells sync when another device changed a pack. */
    private val account: com.packabunch.auth.SupabaseAccount? = null,
    /** Puts each new notice on the phone too, not only on the Notifications page. */
    private val notifier: com.packabunch.notify.SystemNotifier? = null,
) : ViewModel() {
    val syncState = cloudSync?.state ?: MutableStateFlow(com.packabunch.data.cloud.CloudSyncState()).asStateFlow()
    fun syncNow() { viewModelScope.launch { cloudSync?.sync() } }

    // -- Pack a Bunch Pro ------------------------------------------------------------------------

    private val _plans = MutableStateFlow<List<com.packabunch.billing.PlanOffer>>(emptyList())
    /** Empty until Google Play returns real, localised products. The paywall stays disabled. */
    val plans = _plans.asStateFlow()

    private val _selectedPlan = MutableStateFlow(0)
    /** Index into [plans]. Starts on the first — the weekly plan, when one is offered. */
    val selectedPlan = _selectedPlan.asStateFlow()

    fun selectPlan(index: Int) { _selectedPlan.value = index }

    private val _purchaseOutcome = MutableStateFlow<com.packabunch.ui.screens.PurchaseAttempt?>(null)
    /** The pop-up over the Pack Plus page, or null when there is none. */
    val purchaseOutcome = _purchaseOutcome.asStateFlow()

    fun subscribe(activity: android.app.Activity) {
        val pkg = _plans.value.getOrNull(_selectedPlan.value)?.pkg ?: return
        viewModelScope.launch { _purchaseOutcome.value = com.packabunch.billing.Billing.purchase(activity, pkg) }
    }

    /** "Check again" on a pending payment: asks Google Play afresh, and says so if it has cleared. */
    fun checkPurchaseAgain(onStillPending: () -> Unit) {
        viewModelScope.launch {
            if (com.packabunch.billing.Billing.refresh()) setTier(Tier.PLUS) else onStillPending()
        }
    }

    fun restorePurchases(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(com.packabunch.billing.Billing.restore()) }
    }

    fun clearPurchaseOutcome() { _purchaseOutcome.value = null }

    // Pack Plus comes from a Google Play subscription or a promo code; either switches it on.
    private var storePlus = false
    private var promoPlusUntil: java.time.Instant? = null
    private fun refreshTier() {
        val promo = promoPlusUntil?.isAfter(java.time.Instant.now()) == true
        val plus = storePlus || promo
        if (plus && _settings.value.tier != Tier.PLUS) preferences.edit().putLong("plusSeenAt", System.currentTimeMillis()).apply()
        setTier(if (plus) Tier.PLUS else Tier.FREE)
        plusChanged.update { it + 1 }
    }

    /** When Pack Plus from a promo code runs out; null when it comes from Google Play or is off. */
    fun promoPlusEnds(): java.time.Instant? =
        if (storePlus) null else promoPlusUntil?.takeIf { it.isAfter(java.time.Instant.now()) }

    /** Asks Google Play for the plans again — the first try can fail offline. */
    fun reloadPlans() {
        viewModelScope.launch {
            val fresh = com.packabunch.billing.Billing.plans()
            if (fresh.isNotEmpty()) _plans.value = fresh
        }
    }

    /** Redeems a promo code; tells [onResult] until when Plus now runs, or null if refused. */
    fun redeemPromo(code: String, onResult: (java.time.Instant?, String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { account?.redeemPromo(code) }
            result.onSuccess { until -> if (until != null) { promoPlusUntil = until; refreshTier() }; onResult(until, null) }
                .onFailure { onResult(null, it.message ?: "Couldn't check the code. Try again when you're connected.") }
        }
    }

    // -- notifications ------------------------------------------------------------------------

    private val readNotifications = MutableStateFlow(preferences.getStringSet("readNotifications", emptySet())!!.toSet())

    /** The last backup that finished, and the last one that didn't — both real sync results. */
    private data class BackupLog(val at: Long, val packs: Int, val failedAt: Long)
    private val backupLog = MutableStateFlow(BackupLog(
        at = preferences.getLong("lastBackupAt", 0L),
        packs = preferences.getInt("lastBackupPacks", 0),
        failedAt = preferences.getLong("lastBackupFailedAt", 0L),
    ))

    /** Bumped whenever the Pack Plus source changes, so the billing notices are worked out again. */
    private val plusChanged = MutableStateFlow(0)

    /**
     * What the app has to tell you, worked out from what is actually true: a pack left part
     * packed, a pack finished, the last backup (or the one that failed), Pack Plus starting or
     * about to end. Nothing is seeded, and each kind obeys its switch on the notification
     * settings page — "Choose what shows up here" really does choose.
     */
    val notifications: StateFlow<List<com.packabunch.ui.screens.AppNotification>> by lazy {
        kotlinx.coroutines.flow.combine(repository.projects, _settings, backupLog, readNotifications, plusChanged) { projects, settings, backup, read, _ ->
            val show = settings.notifications
            val now = System.currentTimeMillis()
            buildList {
                for (p in projects) {
                    if (p.id == "sample") continue
                    val total = p.plan?.placements?.size ?: 0
                    val packed = p.packedInstanceIds.size
                    if (total == 0 || packed == 0) continue
                    val name = p.name.ifBlank { "pack" }.lowercase()
                    if (packed < total && show.halfFinishedPack) {
                        val how = if (packed * 2 in total - 1..total + 1) "half packed" else "$packed of $total packed"
                        val left = total - packed
                        add(com.packabunch.ui.screens.AppNotification(
                            id = "progress-${p.id}-$packed",
                            icon = com.packabunch.ui.components.PackIcons.Clock,
                            title = "The $name is $how",
                            body = "You stopped at step ${packed + 1} of $total. $left ${if (left == 1) "piece" else "pieces"} still to go in.",
                            whenText = whenText(p.updatedAtMillis), unread = false,
                            tint = com.packabunch.ui.theme.Caution, tile = com.packabunch.ui.theme.CautionTint, atMillis = p.updatedAtMillis,
                            // Not every step on the phone: once, when the pack is nearly done and left.
                            phoneKey = if (packed * 4 >= total * 3) "almost-${p.id}" else null,
                        ))
                    } else if (packed >= total && show.askHowItWent) {
                        add(com.packabunch.ui.screens.AppNotification(
                            id = "done-${p.id}-${p.updatedAtMillis}",
                            icon = com.packabunch.ui.components.PackIcons.Check,
                            title = "The $name is packed",
                            body = "All $total ${if (total == 1) "piece" else "pieces"} went in. Did it go to plan? Tell us if something didn't fit.",
                            whenText = whenText(p.updatedAtMillis), unread = false,
                            tint = com.packabunch.ui.theme.Success, tile = com.packabunch.ui.theme.SuccessTint, atMillis = p.updatedAtMillis,
                            phoneKey = "done-${p.id}",
                        ))
                    }
                }
                if (show.backupState) {
                    if (backup.failedAt > backup.at) add(com.packabunch.ui.screens.AppNotification(
                        id = "backup-failed-${backup.failedAt / DAY_MS}",
                        icon = com.packabunch.ui.components.PackIcons.Download,
                        title = "Backup didn't finish",
                        body = "Your packs are safe on this phone. It tries again by itself when you're connected.",
                        whenText = whenText(backup.failedAt), unread = false,
                        tint = com.packabunch.ui.theme.Caution, tile = com.packabunch.ui.theme.CautionTint, atMillis = backup.failedAt,
                    )) else if (backup.at > 0) add(com.packabunch.ui.screens.AppNotification(
                        // One a day at most: a sync after every edit is not news each time.
                        id = "backup-${backup.at / DAY_MS}",
                        icon = com.packabunch.ui.components.PackIcons.Download,
                        title = "Backup finished",
                        body = "${backup.packs} ${if (backup.packs == 1) "pack is" else "packs are"} safe in your account.",
                        whenText = whenText(backup.at), unread = false,
                        tint = com.packabunch.ui.theme.Success, tile = com.packabunch.ui.theme.SuccessTint, atMillis = backup.at,
                    ))
                }
                if (show.billing) {
                    val until = promoPlusUntil
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM")
                    fun day(i: java.time.Instant) = fmt.format(i.atZone(java.time.ZoneId.systemDefault()))
                    if (settings.tier == Tier.PLUS) {
                        val since = preferences.getLong("plusSeenAt", 0L)
                        add(com.packabunch.ui.screens.AppNotification(
                            id = "plus-${until?.toEpochMilli() ?: "store"}",
                            icon = com.packabunch.ui.components.PackIcons.Cube,
                            title = "Pack Plus is on",
                            body = if (until != null && !storePlus) "From a promo code, until ${day(until)}."
                            else "Billed by Google Play. Manage it there any time.",
                            whenText = if (since > 0) whenText(since) else "", unread = false,
                            atMillis = since,
                        ))
                        if (until != null && !storePlus) {
                            val daysLeft = java.time.Duration.between(java.time.Instant.ofEpochMilli(now), until).toDays()
                            if (daysLeft in 0L..3L) add(com.packabunch.ui.screens.AppNotification(
                                id = "plus-ending-${until.toEpochMilli()}",
                                icon = com.packabunch.ui.components.PackIcons.Clock,
                                title = if (daysLeft == 0L) "Pack Plus ends today" else "Pack Plus ends in $daysLeft ${if (daysLeft == 1L) "day" else "days"}",
                                body = "Your promo code runs out on ${day(until)}. Your packs stay; only the Plus limits come back.",
                                whenText = whenText(now), unread = false,
                                tint = com.packabunch.ui.theme.Caution, tile = com.packabunch.ui.theme.CautionTint,
                                atMillis = until.toEpochMilli() - 3 * DAY_MS,
                            ))
                        }
                    } else if (until != null && until.toEpochMilli() <= now) add(com.packabunch.ui.screens.AppNotification(
                        id = "plus-ended-${until.toEpochMilli()}",
                        icon = com.packabunch.ui.components.PackIcons.Cube,
                        title = "Pack Plus has ended",
                        body = "The promo code ran out on ${day(until)}. Everything you saved is still here.",
                        whenText = whenText(until.toEpochMilli()), unread = false,
                        atMillis = until.toEpochMilli(),
                    ))
                }
                // "New kinds of space" has no source yet — it gets a notice only when one ships.
            }.map { n ->
                // Backups (at most one a day) and Pack Plus going through, ending soon or ended
                // are worth the phone; pack notices set their own key above.
                val pack = n.id.startsWith("progress-") || n.id.startsWith("done-")
                n.copy(unread = n.id !in read, phoneKey = if (pack) n.phoneKey else n.id)
            }.sortedByDescending { it.atMillis }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    }

    /** On screen or not, for the phone notices: a pack nearly done is only worth one once it is left. */
    private val onScreen = MutableStateFlow(true)

    /**
     * The notices that earn a place on the phone go there once: a pack nearly done and left, a
     * pack finished, a backup done or failed, Pack Plus starting or ending. Each kind obeys its
     * switch (the list above already leaves out the switched-off ones). What was already there
     * when this first ran is not replayed.
     */
    private fun postToPhone() = notifier?.let { n ->
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(notifications, onScreen) { list, shown -> list to shown }.collect { (list, shown) ->
                val candidates = list.filter { it.unread && it.phoneKey != null }
                val posted = preferences.getStringSet("postedNotifications", null)
                if (posted == null) {
                    preferences.edit().putStringSet("postedNotifications", candidates.map { it.phoneKey!! }.toSet()).apply()
                    return@collect
                }
                val fresh = candidates.filter { it.phoneKey!! !in posted && !(shown && it.phoneKey.startsWith("almost-")) }
                if (fresh.isEmpty()) return@collect
                fresh.forEach { n.post(it.phoneKey!!, it.title, it.body, it.atMillis) }
                preferences.edit().putStringSet("postedNotifications", posted + fresh.map { it.phoneKey!! }).apply()
            }
        }
    }

    fun markNotificationRead(id: String) {
        if (id in readNotifications.value) return
        val all = readNotifications.value + id
        preferences.edit().putStringSet("readNotifications", all).apply()
        readNotifications.value = all
    }

    fun markNotificationsRead() {
        val all = readNotifications.value + notifications.value.map { it.id }
        preferences.edit().putStringSet("readNotifications", all).apply()
        readNotifications.value = all
    }

    private fun whenText(millis: Long): String {
        val zone = java.time.ZoneId.systemDefault()
        val at = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), java.time.LocalDate.now(zone))
        val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(at)
        return when (days) {
            0L -> "Today, $time"
            1L -> "Yesterday, $time"
            else -> java.time.format.DateTimeFormatter.ofPattern("d MMMM").format(at)
        }
    }

    /** Set once this phone is being cleared for an account deletion; no sync runs after it. */
    private var syncStopped = false

    private val _accountNotice = MutableStateFlow<String?>(null)
    /** One thing to tell the person about their account, once — a stopped deletion. */
    val accountNotice = _accountNotice.asStateFlow()
    fun accountNoticeShown() { _accountNotice.value = null }

    /** False until the first sync has been attempted. Nothing to wait for with no account. */
    private val _firstSyncDone = MutableStateFlow(cloudSync == null)

    // Sync runs when something happened, never on a timer: when the app comes to the front, a
    // couple of seconds after a local edit settles, and when the server says another device
    // changed a pack. In the background nothing runs at all.
    private var foreground = false
    private var pendingSync: kotlinx.coroutines.Job? = null
    private val realtime = cloudSync?.let { _ -> account?.let { com.packabunch.data.cloud.CloudRealtime(it) { requestSync(REMOTE_SETTLE_MS) } } }

    init {
        if (cloudSync != null) {
            viewModelScope.launch {
                repository.projects.collect {
                    cloudSync.localChanged()
                    if (foreground) requestSync(LOCAL_SETTLE_MS)
                }
            }
        }
    }

    /** Called when the app's screen starts. Catches up on anything missed while away. */
    fun onForeground() {
        onScreen.value = true
        if (foreground) return
        foreground = true
        requestSync(0)
        // Back in the app: a photo changed or removed on another phone shows here too.
        refreshAccountSettings()
        realtime?.start()
    }

    fun onBackground() {
        onScreen.value = false
        foreground = false
        realtime?.stop()
    }

    override fun onCleared() {
        realtime?.stop()
        super.onCleared()
    }

    /**
     * Runs one sync after [afterMillis], replacing any sync already waiting. A burst of edits
     * or of remote changes therefore costs one sync, not one each.
     */
    private fun requestSync(afterMillis: Long) {
        if (syncStopped) return
        val sync = cloudSync ?: return
        pendingSync?.cancel()
        pendingSync = viewModelScope.launch {
            kotlinx.coroutines.delay(afterMillis)
            sync.sync()
            // A completed backup is worth telling about, once each time it happens.
            val now = System.currentTimeMillis()
            if (sync.state.value.backedUp) {
                val count = projects.value.count { it.id != "sample" }
                preferences.edit().putLong("lastBackupAt", now).putInt("lastBackupPacks", count).apply()
                backupLog.update { it.copy(at = now, packs = count) }
            } else if (sync.state.value.message.startsWith("Couldn't")) {
                preferences.edit().putLong("lastBackupFailedAt", now).apply()
                backupLog.update { it.copy(failedAt = now) }
            }
            // Whether it worked or not, the packs have had their chance to turn up.
            _firstSyncDone.value = true
        }
    }

    val projects: StateFlow<List<Project>> = repository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Room answers instantly, and on a fresh install its answer is "nothing" whatever the
     * account actually has. Signing in on a new phone would then be met with the empty state,
     * so an empty list only counts as empty once the packs have had their chance to arrive.
     */
    val projectsLoading: StateFlow<Boolean> =
        combine(projects, _firstSyncDone) { list, synced -> list.isEmpty() && !synced }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _settings = MutableStateFlow(AppSettings(
        unit = LengthUnit.entries.firstOrNull { it.name == preferences.getString("unit", null) }
            ?: LengthUnit.CENTIMETRES,
        packingHabit = PackingHabit.entries.firstOrNull { it.name == preferences.getString("habit", null) },
        cameraMeasuring = preferences.getBoolean("cameraMeasuring", true),
        defaultEdgeGapMm = preferences.getInt("defaultEdgeGapMm", 5).coerceIn(0, 50),
        avatarUrl = preferences.getString("avatar", null),
    ))

    fun completeSetup() {
        preferences.edit().putBoolean("setupComplete", true).apply()
    }
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * The account's measuring preferences beat whatever was chosen before signing in — that
     * choice was only a guess for an account that had none yet, so a new account gets it
     * pushed up instead.
     */
    init {
        if (cloudSettings != null) viewModelScope.launch {
            cloudSettings.load().onSuccess { remote ->
                if (remote == null) {
                    cloudSettings.save(_settings.value.unit.name, _settings.value.packingHabit?.name)
                    _settings.value.avatarUrl?.let { cloudSettings.saveAvatar(_settings.value.unit.name, it) }
                } else {
                    applyRemoteSettings(remote)
                }
            }
            // A failed read changes nothing, here or in the account: it is tried again next launch.
        }
    }

    /**
     * Takes the account's settings onto this phone, all at once and without saving them back.
     * Applying them one by one through the setters saved each in turn, with the photo not yet
     * applied — and a save that said "no photo" could land last and erase it from the account.
     * That is how the profile photo vanished on a second phone.
     */
    private fun applyRemoteSettings(remote: com.packabunch.data.cloud.RemoteSettings) {
        val unit = LengthUnit.entries.firstOrNull { it.name == remote.unit }
        val habit = PackingHabit.entries.firstOrNull { it.name == remote.habit }
        preferences.edit().apply {
            unit?.let { putString("unit", it.name) }
            habit?.let { putString("habit", it.name) }
            // The account's photo is the photo, removed included: only saveAvatar ever writes it,
            // so an empty one means it was taken off on some phone, not that a save lost it.
            putString("avatar", remote.avatarUrl)
        }.apply()
        _settings.update { current ->
            current.copy(
                unit = unit ?: current.unit,
                packingHabit = habit ?: current.packingHabit,
                avatarUrl = remote.avatarUrl,
            )
        }
    }

    /** Reads the account's settings again — a photo changed on another phone shows up here. */
    fun refreshAccountSettings() {
        val cloud = cloudSettings ?: return
        viewModelScope.launch { cloud.load().onSuccess { remote -> if (remote != null) applyRemoteSettings(remote) } }
    }

    private fun pushSettings() {
        val settings = cloudSettings ?: return
        viewModelScope.launch {
            settings.save(_settings.value.unit.name, _settings.value.packingHabit?.name)
        }
    }

    // After _settings on purpose: RevenueCat may report cached entitlement synchronously.
    init {
        // Last, once everything the notices are worked out from exists.
        postToPhone()
        if (userId != null) viewModelScope.launch {
            // Signing in again is what stops a deletion this account asked for.
            if (runCatching { account?.cancelPendingDeletion() }.getOrNull() == true) {
                _accountNotice.value = "Welcome back. Your account is no longer being deleted — everything is still here."
            }
            promoPlusUntil = runCatching { account?.promoPlusUntil() }.getOrNull()
            refreshTier()
            com.packabunch.billing.Billing.identify(userId) { active -> storePlus = active; refreshTier() }
            _plans.value = com.packabunch.billing.Billing.plans()
        }
    }

    private val _editor = MutableStateFlow(PackEditorState())
    private var openGeneration = 0
    private var solveGeneration = 0
    val editor: StateFlow<PackEditorState> = _editor.asStateFlow()

    val limits: TierLimits get() = TierLimits.forTier(_settings.value.tier)

    /**
     * Whether this phone can sense depth, which mapping an irregular space needs.
     *
     * Set once from the AR availability check. Defaults to false, so the "any shape" route
     * is offered only when it has been confirmed — never assumed and then failed at.
     */
    var depthCapable: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun updateDepthCapable(capable: Boolean) {
        depthCapable = capable
    }

    // -- settings -------------------------------------------------------------------------

    fun setUnit(unit: LengthUnit) {
        preferences.edit().putString("unit", unit.name).apply()
        _settings.update { it.copy(unit = unit) }
        pushSettings()
    }

    fun setTier(tier: Tier) {
        _settings.update { it.copy(tier = tier) }
        // A pending payment that clears while its pop-up is showing turns into the success one.
        if (tier == Tier.PLUS && _purchaseOutcome.value?.outcome == com.packabunch.ui.screens.PurchaseOutcome.Pending) {
            _purchaseOutcome.value = com.packabunch.ui.screens.PurchaseAttempt(com.packabunch.ui.screens.PurchaseOutcome.Succeeded)
        }
    }

    fun setCameraMeasuring(enabled: Boolean) {
        preferences.edit().putBoolean("cameraMeasuring", enabled).apply()
        _settings.update { it.copy(cameraMeasuring = enabled) }
    }

    fun setDefaultEdgeGap(mm: Int) {
        require(mm in 0..50)
        preferences.edit().putInt("defaultEdgeGapMm", mm).apply()
        _settings.update { it.copy(defaultEdgeGapMm = mm) }
    }

    /** Remembers the photo on this phone and against the account. Null clears it. */
    suspend fun setAvatar(url: String?): Result<Unit> {
        val cloud = cloudSettings
            ?: return Result.failure(IllegalStateException("Sign in to save your profile photo."))
        val saved = cloud.saveAvatar(_settings.value.unit.name, url)
        saved.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return Result.failure(it)
        }
        preferences.edit().putString("avatar", url).apply()
        _settings.update { it.copy(avatarUrl = url) }
        return Result.success(Unit)
    }

    fun setPackingHabit(habit: PackingHabit) {
        preferences.edit().putString("habit", habit.name).apply()
        _settings.update { it.copy(packingHabit = habit) }
        pushSettings()
    }

    fun setNotificationPreferences(preferences: NotificationPreferences) =
        _settings.update { it.copy(notifications = preferences) }

    /** Turns off every notification the app has. There is no sixth kind hiding elsewhere. */
    fun turnAllNotificationsOff() = _settings.update {
        it.copy(
            notifications = NotificationPreferences(
                halfFinishedPack = false,
                backupState = false,
                billing = false,
                newKindsOfSpace = false,
                askHowItWent = false,
            ),
        )
    }

    /**
     * Every distinct item measured across every saved pack.
     *
     * Deduplicated by name and size rather than by id, because the same real object added
     * to two packs separately is one measured thing to the person who measured it.
     */
    fun libraryItems(projects: List<Project>): List<LibraryItem> = projects
        .flatMap { project -> project.items.map { project.id to it } }
        .groupBy { (_, spec) -> spec.name.trim().lowercase() to spec.dimensions }
        .map { (_, entries) ->
            LibraryItem(
                spec = entries.first().second,
                usedInPackCount = entries.map { it.first }.distinct().size,
            )
        }
        .sortedByDescending { it.usedInPackCount }

    // -- editing a pack -------------------------------------------------------------------

    /**
     * Starts a scan if the plan allows another today, and counts it. Free includes
     * [TierLimits.maxScansPerDay] a day — mapping a space or scanning items; typing sizes is
     * never limited. Counted on this phone, per calendar day.
     */
    fun tryStartScan(): Boolean {
        val today = java.time.LocalDate.now().toString()
        val sofar = if (preferences.getString("scanDay", null) == today) preferences.getInt("scanCount", 0) else 0
        if (!limits.allowsAnotherScanToday(sofar)) return false
        preferences.edit().putString("scanDay", today).putInt("scanCount", sofar + 1).apply()
        return true
    }

    /** Saved packs that count against the plan. The sample pack is the app's, not theirs. */
    fun savedPackCount(): Int = projects.value.count { it.id != "sample" }

    fun startNewPack() {
        openGeneration++
        solveGeneration++
        _editor.value = PackEditorState(
            projectId = "pack-${System.currentTimeMillis()}",
            isNew = true,
        )
    }

    /** Restore a validated saved arrangement. Recreate the sample only when requested. */
    fun openPack(id: String, onOpened: () -> Unit = {}) {
        val generation = ++openGeneration
        solveGeneration++
        viewModelScope.launch {
            if (id == "sample" && repository.project(id) == null) {
                repository.upsert(ProjectRepository.sampleProject())
            }
            val project = repository.solvedProject(id) ?: return@launch
            if (generation != openGeneration) return@launch
            _editor.value = PackEditorState(
                projectId = project.id,
                name = project.name,
                space = project.space,
                items = project.items,
                plan = project.plan,
                packedInstanceIds = project.packedInstanceIds,
                isNew = false,
            )
            onOpened()
        }
    }

    fun setSpaceKind(kind: SpaceKind) = _editor.update { it.copy(spaceKind = kind) }

    /**
     * Attaches a finished scan to the pack being edited.
     *
     * The scan's own envelope becomes the space's nominal dimensions, but the *usable*
     * volume comes from the grid and is almost always smaller. Nothing downstream should
     * read the envelope as capacity.
     */
    fun setScannedSpace(scan: ScannedSpace) {
        _editor.update { state ->
            val bounds = scan.effectiveGrid.boundsMm
            state.copy(
                space = Space(
                    id = "${state.projectId}-space",
                    name = state.name,
                    dimensions = Dimensions(bounds.widthMm, bounds.depthMm, bounds.heightMm),
                    measurementSource = MeasurementSource.CAMERA_ESTIMATE,
                    scan = scan,
                    edgeGapMm = _settings.value.defaultEdgeGapMm,
                ),
                plan = null,
            )
        }
        autosave()
    }

    /** Toggling an obstruction changes the usable volume, so the old plan is dropped. */
    fun setObstructionIncluded(id: String, included: Boolean) {
        _editor.update { state ->
            val scan = state.space?.scan ?: return@update state
            state.copy(
                space = state.space.copy(scan = scan.withObstructionIncluded(id, included)),
                plan = null,
            )
        }
    }

    fun setSpaceName(name: String) {
        _editor.update { it.copy(name = name, space = it.space?.copy(name = name)) }
        autosave()
    }

    fun setSpaceDimensions(dimensions: Dimensions, source: MeasurementSource) {
        _editor.update { state ->
            state.copy(
                space = (state.space ?: blankSpace(state)).copy(
                    dimensions = dimensions,
                    measurementSource = source,
                ),
                // Any dimension change invalidates the arrangement it was solved from.
                plan = null,
            )
        }
        autosave()
    }

    fun setEdgeGap(millimetres: Int) {
        _editor.update { state ->
            state.copy(
                space = (state.space ?: blankSpace(state)).copy(edgeGapMm = millimetres),
                plan = null,
            )
        }
        autosave()
    }

    private fun blankSpace(state: PackEditorState) = Space(
        id = "${state.projectId}-space",
        name = state.name,
        dimensions = Dimensions(0, 0, 0),
        edgeGapMm = _settings.value.defaultEdgeGapMm,
    )

    fun upsertItem(item: ItemSpec) {
        _editor.update { state ->
            val index = state.items.indexOfFirst { it.id == item.id }
            val items = if (index >= 0) {
                state.items.toMutableList().apply { set(index, item) }
            } else {
                state.items + item
            }
            state.copy(items = items, plan = null)
        }
        autosave()
    }

    fun removeItem(id: String) {
        _editor.update { state ->
            state.copy(items = state.items.filterNot { it.id == id }, plan = null)
        }
        autosave()
    }

    fun duplicateItem(id: String) {
        _editor.update { state ->
            val source = state.items.firstOrNull { it.id == id } ?: return@update state
            state.copy(
                items = state.items + source.copy(id = "${source.id}-${state.items.size + 1}"),
                plan = null,
            )
        }
        autosave()
    }

    fun piecesWouldExceedPlan(extra: Int = 1): Boolean =
        !limits.allowsPieces(_editor.value.pieceCount + extra)

    // -- solving ----------------------------------------------------------------------------

    /**
     * Runs the search off the main thread with a real time budget.
     *
     * The engine is pure, so there is nothing to synchronise — but it can take a second or
     * two on a large pack, and the UI thread is not where that belongs.
     */
    fun solve() {
        val generation = ++solveGeneration
        val state = _editor.value
        val space = state.space ?: return
        val request = PackingRequest(space, state.items)

        _editor.update { it.copy(solving = true, inputProblems = emptyList()) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 2_000))
            }
            if (generation != solveGeneration) return@launch
            val current = _editor.value
            if (current.projectId != state.projectId || current.space == null ||
                PackingRequest(current.space, current.items).revision() != request.revision()) {
                _editor.update { it.copy(solving = false) }
                return@launch
            }
            when (result) {
                is SolveResult.InvalidInput -> _editor.update {
                    it.copy(solving = false, inputProblems = result.problems)
                }
                is SolveResult.Solved -> {
                    _editor.update { it.copy(solving = false, plan = result.plan,
                        packedInstanceIds = emptySet(), guideStep = 0) }
                    save()
                }
            }
        }
    }

    /**
     * Writes the pack out.
     *
     * Called after every confirmed edit rather than on a "save" button, because the plan's
     * own release gate requires a pack to survive an interrupted session — and a session
     * can be interrupted between any two taps.
     */
    fun save() {
        val state = _editor.value
        val space = state.space ?: return
        if (!space.dimensions.isValid()) return

        viewModelScope.launch {
            repository.upsert(
                Project(
                    id = state.projectId,
                    name = state.name,
                    space = space,
                    items = state.items,
                    plan = state.plan,
                    packedInstanceIds = state.packedInstanceIds,
                ),
            )
        }
    }

    private fun autosave() = save()

    private val fitReports = account?.let { com.packabunch.data.cloud.CloudFitReports(it) }

    /**
     * Records why the item at the current guide step didn't fit — the reason and the sizes,
     * nothing else — so plans can be checked against what really happened.
     */
    fun reportFit(reason: com.packabunch.data.cloud.FitReason) {
        val reports = fitReports ?: return
        val state = _editor.value
        val placements = state.plan?.placements?.sortedBy { it.sequenceIndex }.orEmpty()
        val item = state.items.firstOrNull { it.id == placements.getOrNull(state.guideStep)?.specId }
        val space = state.space
        viewModelScope.launch {
            reports.send(
                reason = reason,
                item = item?.dimensions,
                space = space?.dimensions,
                spaceMeasured = space?.measurementSource?.name,
                spaceScanned = space?.scan != null,
                step = state.guideStep + 1,
                steps = placements.size,
            )
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun restoreProject(project: Project) {
        viewModelScope.launch { repository.restore(project) }
    }

    /**
     * Items measured by the item scan ([com.packabunch.ui.screens.ItemScanScreen]).
     *
     * Each arrives with its id already chosen, because its photo was written under that id
     * before this was called — photos live beside the item by id, never in a column.
     *
     * The dimensions are the fitted bounding box: what the solver needs, and all it is given.
     * The object's recognised name and shape are for the person reading the plan, and nothing
     * here lets either change a size. Round and irregular objects keep "nothing on top", as
     * scanned items always have — the scan saw their outside, not whether their top is flat
     * and firm. A box measured on every side has a flat top by definition, so it may carry
     * things, the same default a typed-in item gets.
     */
    fun importScannedItems(scanned: List<Pair<String, com.packabunch.ar.ScannedItemResult>>): Boolean {
        if (scanned.isEmpty()) return true
        if (!limits.allowsPieces(_editor.value.pieceCount + scanned.size)) return false
        val initialCount = _editor.value.items.size
        val additions = scanned.mapIndexed { index, (id, result) ->
            ItemSpec(
                id = id,
                name = result.name ?: "Scanned item ${initialCount + index + 1}",
                dimensions = result.dimensions.asDimensions(),
                measurementSource = MeasurementSource.CAMERA_ESTIMATE,
                maySupportItems = result.shape == com.packabunch.packing.ShapeFamily.BOX,
                form = com.packabunch.packing.ItemForm.fromFit(result.fit),
            )
        }
        _editor.update { it.copy(items = it.items + additions, plan = null) }
        autosave()
        return true
    }

    fun renameProject(project: Project, name: String) {
        viewModelScope.launch { repository.upsert(project.copy(name = name, space = project.space.copy(name = name))) }
    }

    fun duplicateProject(project: Project) {
        val id = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.upsert(project.copy(id = id, name = "${project.name} copy",
                space = project.space.copy(id = "$id-space", name = "${project.name} copy"),
                items = project.items.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                plan = null, packedInstanceIds = emptySet()))
        }
        autosave()
    }

    // -- the packing guide ---------------------------------------------------------------------

    fun markPacked(instanceId: String, packed: Boolean) {
        _editor.update { state ->
            state.copy(
                packedInstanceIds = if (packed) state.packedInstanceIds + instanceId
                else state.packedInstanceIds - instanceId,
            )
        }
        save()
    }

    fun setGuideStep(step: Int) = _editor.update { it.copy(guideStep = step) }

    /**
     * Packs whose "carry on packing" reminder has been swiped away. Swiping is a decision,
     * not a postponement, so it is kept against the account and the card does not return.
     */
    private val _dismissedResumes =
        MutableStateFlow(preferences.getStringSet("resumeDismissed", emptySet()).orEmpty())
    val dismissedResumes: StateFlow<Set<String>> = _dismissedResumes.asStateFlow()

    fun dismissResume(id: String) {
        val next = _dismissedResumes.value + id
        preferences.edit().putStringSet("resumeDismissed", next).apply()
        _dismissedResumes.value = next
    }

    /** Back to the first step with nothing ticked off. The plan itself is left alone. */
    fun restartPacking() {
        _editor.update { it.copy(packedInstanceIds = emptySet(), guideStep = 0) }
        save()
    }

    fun resumeGuide() = _editor.update {
        it.copy(guideStep = com.packabunch.ui.nav.nextPackingStep(it.plan?.placements.orEmpty(), it.packedInstanceIds))
    }

    /**
     * "Did it actually go in?"
     *
     * The single most valuable signal the app can collect, because it is the only thing
     * that connects the geometric model to real crates. Held locally for now — it is not
     * sent anywhere, and it must not be until there is a privacy policy and a Data safety
     * declaration that cover it.
     */
    private val feedback = account?.let { com.packabunch.data.cloud.CloudFeedback(it) }

    /** Sends the "How are we doing?" card; [done] is told whether it arrived. */
    fun sendFeedback(stars: Int, words: String, packsFinished: Int, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(feedback?.send(stars, words, packsFinished) ?: false) }
    }

    fun recordDidItFit(fitted: Boolean) {
        _editor.update { it.copy(realWorldFitReport = fitted) }
    }

    /**
     * Deletes everything on the device.
     *
     * Genuinely everything: the packs, and the item photo files with them. A delete that
     * leaves photos behind would make the Data safety declaration untrue.
     */
    fun deleteAllLocalData() {
        viewModelScope.launch {
            repository.deleteAll()
            _editor.value = PackEditorState()
        }
    }

    /**
     * Clears this account off this phone after it has asked to be deleted, without touching
     * the copy in the account.
     *
     * Sync treats a pack that vanished from the phone as deleted, so clearing the phone the
     * ordinary way would push every pack's deletion straight away — and signing in again within
     * the 30 days would bring back an empty account. So sync is stopped first, and its record of
     * what it has seen is cleared with the packs: to the next sign-in this is a new phone, and
     * it downloads everything.
     */
    suspend fun forgetThisPhone() {
        syncStopped = true
        pendingSync?.cancel()
        realtime?.stop()
        repository.deleteAll()
        cloudSync?.forget()
        _editor.value = PackEditorState()
    }


    class Factory(private val context: Context, private val userId: String,
        private val account: com.packabunch.auth.SupabaseAccount? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = ProjectRepository.create(context,userId)
            return AppViewModel(repository,
                context.getSharedPreferences("app_preferences_$userId", Context.MODE_PRIVATE),
                account?.let { com.packabunch.data.cloud.CloudPackSync(repository,it,userId,
                    context.getSharedPreferences("cloud_sync_$userId",Context.MODE_PRIVATE)) }, userId,
                account?.let { com.packabunch.data.cloud.CloudSettings(it,userId) }, account,
                com.packabunch.notify.SystemNotifier(context.applicationContext)) as T
        }
    }
}

/** How long local edits must settle before they sync: one upload for a burst of typing. */
private const val LOCAL_SETTLE_MS = 2_000L

/** A remote change usually arrives as several row events; they are gathered into one sync. */
private const val REMOTE_SETTLE_MS = 500L

data class AppSettings(
    val cameraMeasuring: Boolean = true,
    val defaultEdgeGapMm: Int = 5,
    val unit: LengthUnit = LengthUnit.CENTIMETRES,
    /** The photo this account shows: the person's own, or the one their provider gave us. */
    val avatarUrl: String? = null,
    val tier: Tier = Tier.FREE,
    val scansToday: Int = 0,
    val notifications: NotificationPreferences = NotificationPreferences(),
    /** Only ever changes which space presets are suggested first. */
    val packingHabit: PackingHabit? = null,
)

/** Everything the create-a-pack flow is holding, across its several screens. */
data class PackEditorState(
    val projectId: String = "",
    val name: String = "",
    val space: Space? = null,
    val items: List<ItemSpec> = emptyList(),
    val plan: PackingPlan? = null,
    val solving: Boolean = false,
    val inputProblems: List<InputProblem> = emptyList(),
    val packedInstanceIds: Set<String> = emptySet(),
    val guideStep: Int = 0,
    val isNew: Boolean = true,
    val spaceKind: SpaceKind = SpaceKind.BOX_SHAPED,
    /** Null until the user answers "did it actually go in?" on the finished screen. */
    val realWorldFitReport: Boolean? = null,
) {
    val pieceCount: Int get() = items.sumOf { it.quantity }

    val hasUsableSpace: Boolean get() = space?.dimensions?.isValid() == true
}

/** A day, for keeping a notice to one a day. */
private const val DAY_MS = 86_400_000L
