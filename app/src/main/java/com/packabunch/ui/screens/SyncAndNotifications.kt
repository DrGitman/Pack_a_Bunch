package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Sync conflict — `design/artboards/SyncConflict.dc.html`.
 *
 * The rule, stated on the screen and enforced by there being no third button that does it:
 * **we never merge behind your back.** Two edited copies of a pack cannot be reconciled
 * automatically without silently throwing away somebody's work, so the user chooses — and
 * "keep both" is offered so the choice is never forced.
 *
 * Each side shows what actually changed rather than just a timestamp, because "edited 2
 * hours ago" is not enough information to choose with.
 */
data class PackVersion(
    val label: String,
    val editedAgo: String,
    val dimensionSummary: String,
    val pieceCount: Int,
    val changeSummary: String,
)

@Composable
fun SyncConflictScreen(
    packName: String,
    thisDevice: PackVersion,
    otherDevice: PackVersion,
    onKeepThis: () -> Unit,
    onKeepOther: () -> Unit,
    onKeepBoth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(0) }

    ScreenScaffold(modifier) {
        PackAppBar(title = "Two versions of this pack", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            ScreenHeading("Edited in two places at once")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "“$packName” changed here and on your other device while one of them " +
                    "was offline. Pick which one to keep, we won't merge them behind your back.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            VersionCard(
                version = thisDevice,
                selected = selected == 0,
                onClick = { selected = 0 },
            )
            Spacer(Modifier.height(10.dp))
            VersionCard(
                version = otherDevice,
                selected = selected == 1,
                onClick = { selected = 1 },
            )

            Spacer(Modifier.height(Spacing.base))

            Note(
                text = "Not sure? Keep both, the other becomes “$packName (2)”. Nothing is " +
                    "lost that way.",
                icon = PackIcons.Info,
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = "Keep ${if (selected == 0) thisDevice.label else otherDevice.label}",
                onClick = { if (selected == 0) onKeepThis() else onKeepOther() },
            )
            SecondaryButton(text = "Keep both", onClick = onKeepBoth)
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun VersionCard(version: PackVersion, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f)
            .background(if (selected) BrandTint else Color.White, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Primary else Outline, shape)
            .clickable(onClick = onClick)
            .padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = version.label,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    PackIcons.Check,
                    contentDescription = "Selected",
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = "Edited ${version.editedAgo}",
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.5f.sp,
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = version.dimensionSummary,
                style = NumeralChip,
                color = TextSecondary,
                modifier = Modifier
                    .background(SurfaceField, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(
                text = "${version.pieceCount} pieces",
                style = NumeralChip,
                color = TextSecondary,
                modifier = Modifier
                    .background(SurfaceField, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // What changed, not just when. Choosing needs the difference.
        Text(
            text = version.changeSummary,
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 13.5f.sp,
            lineHeight = 20.sp,
        )
    }
}

/** One thing the app has told you about. */
data class AppNotification(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val body: String,
    val whenText: String,
    val unread: Boolean,
)

/**
 * Notification centre — `design/artboards/Notifications.dc.html`.
 *
 * Everything the app has said, kept in one place so nothing is only ever a banner you
 * swiped away. The link out to the settings is at the bottom because the honest answer to
 * "why am I getting this" should always be one tap from the thing itself.
 */
@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    onMarkAllRead: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onChooseWhatShows: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(
            title = "Notifications",
            onBack = onBack,
            actions = {
                if (notifications.any { it.unread }) {
                    PackTextButton(text = "Mark all read", onClick = onMarkAllRead)
                }
            },
        )

        if (notifications.isEmpty()) {
            Spacer(Modifier.height(Spacing.xxl))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconTile(
                    icon = PackIcons.Bell,
                    tint = Primary,
                    background = BrandTint,
                    size = 56.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.height(Spacing.base))
                Text(
                    text = "Nothing yet",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "We only get in touch about your packs, your backup and your " +
                        "subscription. There's no marketing list.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            val unread = notifications.filter { it.unread }
            val earlier = notifications.filterNot { it.unread }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.sm,
                    bottom = Spacing.base,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (unread.isNotEmpty()) {
                    item { SectionHeading("New") }
                    items(unread, key = { it.id }) { notification ->
                        NotificationRow(notification, onClick = { onOpen(notification) })
                    }
                }
                if (earlier.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Spacing.sm))
                        SectionHeading("Earlier")
                    }
                    items(earlier, key = { it.id }) { notification ->
                        NotificationRow(notification, onClick = { onOpen(notification) })
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SecondaryButton(
                text = "Choose what shows up here",
                onClick = onChooseWhatShows,
                icon = PackIcons.Settings,
            )
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f)
            .background(if (notification.unread) BrandTint else Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            notification.icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = notification.title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5f.sp,
            )
            Text(
                text = notification.body,
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                text = notification.whenText,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 11.5f.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
