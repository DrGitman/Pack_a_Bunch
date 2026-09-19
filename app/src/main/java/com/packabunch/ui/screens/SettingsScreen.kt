package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onCameraMeasuringChange: (Boolean) -> Unit = {},
    onDefaultEdgeGapChange: (Int) -> Unit = {},
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showGap by rememberSaveable { mutableStateOf(false) }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Privacy, terms and help") },
        text = { Column {
            TextButton(onClick = { showHelp = false; onPrivacy() }) { Text("Privacy policy") }
            TextButton(onClick = { showHelp = false; onTerms() }) { Text("Terms") }
            TextButton(onClick = { showHelp = false; onSupport() }) { Text("Support") }
        } }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } })
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete all saved packs?") },
        text = { Text("This removes this account's $savedPackCount saved packs from this phone and marks them deleted in your cloud account when sync completes. Your sign-in account is not deleted.") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDeleteAllData() }) { Text("Delete saved packs", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })
    if (showGap) AlertDialog(onDismissRequest = { showGap = false }, title = { Text("Gap around the edges") },
        text = { Column {
            Text("Default for new spaces. Existing packs keep their own gap.")
            listOf(0, 2, 5, 10, 20, 50).forEach { mm ->
                Row(Modifier.fillMaxWidth().clickable { onDefaultEdgeGapChange(mm); showGap = false }.padding(vertical = 4.dp),
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface)
                    .clickable(onClick = onAccount).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(46.dp).background(Primary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Text(email?.firstOrNull()?.uppercase() ?: "P", color = OnPrimary, fontFamily = UiFamily,
                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(email?.substringBefore('@') ?: "Your account", color = TextPrimary, fontFamily = UiFamily,
                            fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                        Text((if (settings.tier == Tier.PLUS) "Pack-a-Bunch Pro" else "Free plan") + " · saved on this phone",
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
                    Row(Modifier.fillMaxWidth().clickable { showGap = true }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsLabel("Gap around the edges", Modifier.weight(1f))
                        Text("${settings.defaultEdgeGapMm} mm", Modifier.background(SurfaceMuted, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                            color = BodyInk, fontFamily = NumericFamily, fontSize = 14.sp)
                    }
                }
                SettingsHeading("APP", 18)
                SettingsGroup {
                    SettingsAction(PackIcons.Bell, "Notifications", onNotifications)
                    SettingsDivider()
                    SettingsAction(PackIcons.Cube, "Manage Pack-a-Bunch Pro", onManageSubscription)
                    SettingsDivider()
                    SettingsAction(PackIcons.Undo, "Restore purchases", onRestorePurchases)
                    SettingsDivider()
                    SettingsAction(PackIcons.Info, "Privacy, terms and help", { showHelp = true })
                    SettingsDivider()
                    SettingsAction(PackIcons.Trash, "Delete all saved packs", { showDelete = true }, ErrorRed)
                }
                Text("Pack a Bunch ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp), color = TextDisabled,
                    fontFamily = NumericFamily, fontSize = 12.sp)
            }
        }
        NavPill(NavDestination.Settings, onNavigate, onNewPack,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
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
@Composable private fun SettingsAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = TextSecondary) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        SettingsLabel(label, Modifier.weight(1f), if (tint == ErrorRed) ErrorRed else TextPrimary)
        if (tint != ErrorRed) Icon(PackIcons.Forward, null, Modifier.size(19.dp), tint = TextTertiary)
    }
}
