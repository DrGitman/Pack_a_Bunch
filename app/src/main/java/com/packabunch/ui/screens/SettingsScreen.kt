package com.packabunch.ui.screens

import com.packabunch.ui.motion.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.BuildConfig
import com.packabunch.packing.Tier
import com.packabunch.ui.AppSettings
import com.packabunch.ui.components.*
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.theme.*

/** Settings.dc.html, populated with the current account and persisted preferences. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedPackCount: Int,
    onUnitChange: (LengthUnit) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onNewPack: () -> Unit,
    onUpgrade: () -> Unit,
    onAccount: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit,
    onNotifications: () -> Unit,
    onDeleteAllData: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    email: String? = null,
    /** The same photo the account page shows, so the two never disagree. */
    avatarUrl: String? = null,
    onCameraMeasuringChange: (Boolean) -> Unit = {},
    onDefaultEdgeGapChange: (Int) -> Unit = {},
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showGap by rememberSaveable { mutableStateOf(false) }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete all saved packs?") },
        text = { Text("This removes this account's $savedPackCount saved packs from this phone and marks them deleted in your cloud account when sync completes. Your sign-in account is not deleted.") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDeleteAllData() }) { Text("Delete saved packs", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })
    if (showGap) AlertDialog(onDismissRequest = { showGap = false }, title = { Text("Gap around the edges") },
        text = { Column {
            Text("Default for new spaces. Existing packs keep their own gap.")
            listOf(0, 2, 5, 10, 20, 50).forEach { mm ->
                Row(Modifier.fillMaxWidth().pressScale(pressedScale = 0.98f).clickable { onDefaultEdgeGapChange(mm); showGap = false }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.defaultEdgeGapMm == mm, onClick = { onDefaultEdgeGapChange(mm); showGap = false })
                    Text("$mm mm", fontFamily = NumericFamily)
                }
            }
        } }, confirmButton = { TextButton(onClick = { showGap = false }) { Text("Cancel") } })

    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            Text("Settings", Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                color = TextPrimary, fontFamily = UiFamily, fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-.7).sp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = NavPillClearance)) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().pressScale(pressedScale = 0.97f).clip(RoundedCornerShape(22.dp)).background(Surface)
                    .clickable(onClick = onAccount).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            coil3.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(email?.firstOrNull()?.uppercase() ?: "P", color = OnPrimary, fontFamily = UiFamily,
                                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(email?.substringBefore('@') ?: "Your account", color = TextPrimary, fontFamily = UiFamily,
                            fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                        Text(if (settings.tier == Tier.PLUS) "Pack a Bunch Pro" else "Free plan",
                            color = TextSecondary, fontFamily = UiFamily, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                    Icon(PackIcons.Forward, null, Modifier.size(19.dp), tint = TextTertiary)
                }
                SettingsHeading("MEASURING", 20)
                SettingsGroup {
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsLabel("Units", Modifier.weight(1f))
                        UnitToggle(settings.unit, onUnitChange)
                    }
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SettingsLabel("Camera measuring")
                            Text(if (settings.cameraMeasuring) "Checks support when opened" else "Typed measurements only",
                                color = TextSecondary, fontFamily = UiFamily, fontSize = 12.5.sp)
                        }
                        Switch(settings.cameraMeasuring, onCameraMeasuringChange)
                    }
                    SettingsDivider()
                    Row(Modifier.fillMaxWidth().pressScale(pressedScale = 0.98f).clickable { showGap = true }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsLabel("Gap around the edges", Modifier.weight(1f))
                        Text("${settings.defaultEdgeGapMm} mm", Modifier.background(SurfaceMuted, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                            color = BodyInk, fontFamily = NumericFamily, fontSize = 14.sp)
                    }
                }
                SettingsHeading("APP", 18)
                SettingsGroup {
                    SettingsAction(PackIcons.Bell, "Notifications", onNotifications, motion = com.packabunch.R.raw.icon_bell)
                    SettingsDivider()
                    SettingsAction(PackIcons.Cube, "Manage Pack a Bunch Pro", onManageSubscription, motion = com.packabunch.R.raw.icon_package)
                    SettingsDivider()
                    SettingsAction(PackIcons.Undo, "Restore purchases", onRestorePurchases, motion = com.packabunch.R.raw.icon_restore)
                    SettingsDivider()
                    SettingsAction(PackIcons.Info, "Privacy, terms and help", onSupport, motion = com.packabunch.R.raw.icon_info)
                    SettingsDivider()
                    SettingsAction(PackIcons.Trash, "Delete all saved packs", { showDelete = true }, ErrorRed,
                        motion = com.packabunch.R.raw.icon_bin)
                }
                Text("Pack a Bunch ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp), color = TextDisabled,
                    fontFamily = NumericFamily, fontSize = 12.sp)
            }
        }
com.packabunch.ui.components.PackBar(
            here = com.packabunch.ui.components.NavSlots.Settings,
            onProjects = { onNavigate(NavDestination.Projects) },
            onSettings = {},
            onNewPack = onNewPack,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

@Composable private fun SettingsHeading(text: String, top: Int) {
    Text(text, Modifier.padding(top = top.dp, bottom = 10.dp), color = TextTertiary,
        fontFamily = UiFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
}
@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
}
@Composable private fun SettingsDivider() { HorizontalDivider(color = Divider, thickness = 1.dp) }
@Composable private fun SettingsLabel(text: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Text(text, modifier, color = color, fontFamily = UiFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
@Composable private fun SettingsAction(icon: ImageVector, label: String, onClick: () -> Unit,
    tint: Color = TextSecondary, @androidx.annotation.RawRes motion: Int? = null) {
    // One press shared by the row and its icon, so the motion plays wherever the row is touched.
    val press = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().pressScale(pressedScale = 0.98f)
        .clickable(interactionSource = press, indication = null, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        if (motion != null) LottieTapIcon(motion, null, onClick = null, size = 20.dp, interactionSource = press)
        else Icon(icon, null, Modifier.size(20.dp), tint = tint)
        SettingsLabel(label, Modifier.weight(1f), if (tint == ErrorRed) ErrorRed else TextPrimary)
        if (tint != ErrorRed) Icon(PackIcons.Forward, null, Modifier.size(19.dp), tint = TextTertiary)
    }
}
