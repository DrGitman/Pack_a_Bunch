package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.data.Project
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Project menu — `design/artboards/ProjectMenu.dc.html`.
 *
 * Each row says what it will actually do rather than just naming itself. "Duplicate it"
 * alone leaves you guessing whether the plan comes too; "Same space and items, fresh plan"
 * does not. Delete says outright that the photos go with it, because they do.
 */
@Composable
fun ProjectMenuSheet(
    project: Project,
    unit: LengthUnit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRemeasure: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x7A2B1D14))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .padding(horizontal = Spacing.gutter)
                .padding(top = 14.dp, bottom = 30.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFFE2D5C6), RoundedCornerShape(999.dp)),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = project.name.ifEmpty { "Untitled pack" },
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                letterSpacing = (-0.4).sp,
            )
            Text(
                text = "${formatDimensions(project.space.dimensions, unit)} · " +
                    "${project.pieceCount} pieces",
                style = NumeralChip,
                color = TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(Spacing.base))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuRow(PackIcons.Pencil, "Rename this pack", null, onRename)
                MenuRow(
                    PackIcons.Copy,
                    "Duplicate it",
                    "Same space and items, fresh plan",
                    onDuplicate,
                )
                MenuRow(
                    PackIcons.Camera,
                    "Measure the space again",
                    "Replans once you confirm the new size",
                    onRemeasure,
                )
                MenuRow(
                    PackIcons.Mail,
                    "Share the packing list",
                    "Plain text, for whoever is helping",
                    onShare,
                )
                MenuRow(
                    PackIcons.Trash,
                    "Delete this pack",
                    "Takes its items and photos with it",
                    onDelete,
                    tint = ErrorRed,
                )
            }

            Spacer(Modifier.height(Spacing.base))

            SecondaryButton(text = "Close", onClick = onDismiss)
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    tint: Color = Primary,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.99f)
            .background(SurfaceField, shape)
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
    }
}
