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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Tier
import com.packabunch.ui.AppSettings
import com.packabunch.ui.components.NavDestination
import com.packabunch.ui.components.NavPill
import com.packabunch.ui.components.NavPillClearance
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Settings — `design/artboards/Settings.dc.html`.
 *
 * Contains the things Google Play requires to be reachable and the things a person needs
 * to undo a decision: manage the subscription (which links out to Play, because that is
 * where it is actually cancelled), restore purchases, delete every trace of local data, and
 * read the privacy policy.
 *
 * Deleting local data really does delete it — projects, and the item photo files with them.
 * A "delete" that leaves photos on disk would make the Data safety declaration untrue.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedPackCount: Int,
    onUnitChange: (LengthUnit) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onNewPack: () -> Unit,
    onUpgrade: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit,
    onNotifications: () -> Unit,
    onDeleteAllData: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ScreenScaffold {
            PackAppBar(title = "Settings", onBack = onBack)

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .padding(bottom = NavPillClearance),
            ) {
                Spacer(Modifier.height(Spacing.md))

                PlanCard(
                    tier = settings.tier,
                    savedPackCount = savedPackCount,
                    onUpgrade = onUpgrade,
                )

                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("Measuring")
                Spacer(Modifier.height(10.dp))

                PackCard(contentPadding = 0.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Units",
                                color = TextPrimary,
                                fontFamily = UiFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                "Everything is stored the same way either way.",
                                color = TextTertiary,
                                fontFamily = UiFamily,
                                fontSize = 12.5f.sp,
                            )
                        }
                        UnitToggle(unit = settings.unit, onUnitChange = onUnitChange)
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("Notifications")
                Spacer(Modifier.height(10.dp))
                SettingRow(PackIcons.Bell, "Notification settings", onClick = onNotifications)

                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("Subscription")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(
                        PackIcons.Forward,
                        "Manage subscription",
                        detail = "Opens Google Play",
                        onClick = onManageSubscription,
                    )
                    SettingRow(PackIcons.Undo, "Restore purchases", onClick = onRestorePurchases)
                }

                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("About")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingRow(PackIcons.Lock, "Privacy policy", onClick = onPrivacy)
                    SettingRow(PackIcons.Info, "Terms", onClick = onTerms)
                    SettingRow(PackIcons.Mail, "Support", onClick = onSupport)
                }

                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("Your data")
                Spacer(Modifier.height(10.dp))

                SettingRow(
                    icon = PackIcons.Trash,
                    title = "Delete all local data",
                    detail = "Every pack and every item photo on this phone. Cannot be undone.",
                    tint = ErrorRed,
                    onClick = onDeleteAllData,
                )

                Spacer(Modifier.height(Spacing.base))
                Text(
                    text = "Packs are stored on this phone only. Nothing is uploaded.",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }

        NavPill(
            current = NavDestination.Settings,
            onNavigate = onNavigate,
            onNewPack = onNewPack,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun PlanCard(tier: Tier, savedPackCount: Int, onUpgrade: () -> Unit) {
    PackCard(elevation = 8.dp, contentPadding = Spacing.base) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(BrandTint, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PackIcons.Cube,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (tier == Tier.PLUS) "Pack Plus" else "Free plan",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = if (savedPackCount == 1) "1 pack saved" else "$savedPackCount packs saved",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                )
            }
            if (tier != Tier.PLUS) {
                com.packabunch.ui.components.PackTextButton(text = "See Plus", onClick = onUpgrade)
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    detail: String? = null,
    tint: Color = Primary,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.99f)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (tint == ErrorRed) ErrorRed else TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            tint = Color(0xFFC3B0A0),
            modifier = Modifier.size(18.dp),
        )
    }
}
