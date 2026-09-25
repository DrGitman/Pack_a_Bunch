package com.packabunch.ui.screens

import com.packabunch.ui.components.swallowTaps
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.packabunch.ui.motion.pressScale
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemSpec
import com.packabunch.ui.components.LabelledTextField
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.StackedDimensionField
import com.packabunch.ui.components.Stepper
import com.packabunch.ui.components.SwitchRow
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatEditableLength as formatLength
import com.packabunch.ui.format.parseLengthToMm
import com.packabunch.ui.theme.ItemTints
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Add or edit an item — `design/artboards/ItemEditor.dc.html`.
 *
 * "Size, at its widest points" is the heading for a reason. The engine models a cuboid
 * envelope, so a kettle's spout and a toolbox's handle are part of the size whether or not
 * they feel like it. Understating them is how a plan that works on screen fails at the
 * crate.
 *
 * The photo does not set the size and the screen says so outright. A single unscaled photo
 * carries no metric information, and a suggested label is a category, never a measurement.
 */
@Composable
fun ItemEditorSheet(
    existing: ItemSpec?,
    unit: LengthUnit,
    nextIndex: Int,
    onDismiss: () -> Unit,
    onSave: (ItemSpec) -> Unit,
    onDelete: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var width by remember(existing) {
        mutableStateOf(existing?.dimensions?.widthMm?.let { formatLength(it, unit) } ?: "")
    }
    var depth by remember(existing) {
        mutableStateOf(existing?.dimensions?.depthMm?.let { formatLength(it, unit) } ?: "")
    }
    var height by remember(existing) {
        mutableStateOf(existing?.dimensions?.heightMm?.let { formatLength(it, unit) } ?: "")
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val itemId = remember(existing) { existing?.id ?: "item-${System.currentTimeMillis()}" }
    var photoPath by remember(itemId) { mutableStateOf(com.packabunch.data.ItemPhotos.pathFor(context, itemId)) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) scope.launch {
            photoPath = com.packabunch.data.ItemPhotos.store(context, itemId, picked)
        }
    }

    // Set here for this item only. The account's own unit is what the sheet opens in.
    var entryUnit by remember(existing) { mutableStateOf(unit) }
    var quantity by remember(existing) { mutableStateOf(existing?.quantity ?: 1) }
    var keepUpright by remember(existing) { mutableStateOf(existing?.keepUpright ?: false) }
    var nothingOnTop by remember(existing) { mutableStateOf(existing?.maySupportItems == false) }

    val widthMm = parseLengthToMm(width, entryUnit)
    val depthMm = parseLengthToMm(depth, entryUnit)
    val heightMm = parseLengthToMm(height, entryUnit)
    // The name is not required: leaving it blank saves as "Item 3", which is what the
    // placeholder has been promising all along. Only the three sizes actually gate saving.
    val valid = widthMm != null && depthMm != null && heightMm != null

    Box(modifier.fillMaxSize()) {
        // Scrim. Tapping it dismisses, which is the Android expectation for a sheet.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x7A2B1D14))
                .clickable(indication = null, interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                }) { onDismiss() },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .swallowTaps()
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

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (existing == null) "Add an item" else "Edit item",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.weight(1f),
                )
                PackIconButton(
                    icon = PackIcons.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 40.dp,
                    iconSize = 19.dp,
                    background = Color(0xFFF4EDE4),
                    tint = Color(0xFF5C4A3A),
                )
            }

            Spacer(Modifier.height(Spacing.base))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            ItemTints[nextIndex % ItemTints.size],
                            RoundedCornerShape(20.dp),
                        )
                        .border(1.5.dp, Color(0xFFD3BEA6), RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .pressScale(pressedScale = 0.96f)
                        .clickable {
                            pickPhoto.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts
                                        .PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photoPath != null) {
                        coil3.compose.AsyncImage(
                            model = photoPath,
                            contentDescription = "Change the photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        // Small, in the corner, so it cannot be hit while aiming for the photo.
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .background(Color(0xCC2B1D14), RoundedCornerShape(12.dp))
                                .clickable {
                                    com.packabunch.data.ItemPhotos.remove(photoPath)
                                    photoPath = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                PackIcons.Close,
                                contentDescription = "Remove the photo",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                PackIcons.Camera,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                "Photo",
                                color = Color(0xFF7C4223),
                                fontFamily = UiFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5f.sp,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabelledTextField(
                        label = "Name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Item ${nextIndex + 1}",
                        focused = true,
                    )
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF4EDE4), RoundedCornerShape(12.dp))
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            PackIcons.Sparkle,
                            contentDescription = null,
                            tint = Color(0xFF8A7565),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            "A photo doesn't set the size",
                            color = Color(0xFF7C6857),
                            fontFamily = UiFamily,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Size, at its widest points",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                UnitToggle(
                    unit = entryUnit,
                    onUnitChange = { picked ->
                        // Carry the numbers over rather than reinterpreting them: 30 cm
                        // typed in must not silently become 30 inches.
                        fun convert(text: String): String =
                            parseLengthToMm(text, entryUnit)?.let { formatLength(it, picked) } ?: text
                        width = convert(width)
                        depth = convert(depth)
                        height = convert(height)
                        entryUnit = picked
                    },
                    compact = true,
                )
            }

            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StackedDimensionField(
                    label = "Width",
                    value = width,
                    onValueChange = { width = it },
                    modifier = Modifier.weight(1f),
                    error = width.isNotEmpty() && widthMm == null,
                )
                StackedDimensionField(
                    label = "Depth",
                    value = depth,
                    onValueChange = { depth = it },
                    modifier = Modifier.weight(1f),
                    error = depth.isNotEmpty() && depthMm == null,
                )
                StackedDimensionField(
                    label = "Height",
                    value = height,
                    onValueChange = { height = it },
                    modifier = Modifier.weight(1f),
                    error = height.isNotEmpty() && heightMm == null,
                )
            }

            Text(
                text = "Include handles and anything that sticks out.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(Spacing.base))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceField, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "How many",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5f.sp,
                    modifier = Modifier.weight(1f),
                )
                Stepper(value = quantity, onValueChange = { quantity = it })
            }

            Spacer(Modifier.height(10.dp))

            SwitchRow(
                title = "Keep it upright",
                subtitle = "Stops it being laid on its side",
                checked = keepUpright,
                onCheckedChange = { keepUpright = it },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Nothing on top",
                subtitle = "Fragile, or shouldn't be squashed",
                checked = nothingOnTop,
                onCheckedChange = { nothingOnTop = it },
            )

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDuplicate != null) {
                    SecondaryButton(
                        text = "Duplicate",
                        onClick = onDuplicate,
                        modifier = Modifier.weight(1f),
                        icon = PackIcons.Copy,
                    )
                }
                PrimaryButton(
                    text = "Save item",
                    onClick = {
                        onSave(
                            ItemSpec(
                                id = itemId,
                                name = name.ifBlank { "Item ${nextIndex + 1}" },
                                dimensions = Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0),
                                quantity = quantity,
                                keepUpright = keepUpright,
                                maySupportItems = !nothingOnTop,
                                measurementSource = if (existing?.dimensions == Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0))
                                    existing.measurementSource else com.packabunch.packing.MeasurementSource.TYPED_IN,
                                shape = existing?.shape?.takeIf { existing.dimensions == Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0) },
                                visualShape = existing?.visualShape?.takeIf { existing.dimensions == Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0) },
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    height = 54.dp,
                )
            }

            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.packabunch.ui.components.PackTextButton(
                        text = "Remove this item",
                        onClick = onDelete,
                        color = Color(0xFF8E3322),
                    )
                }
            }
        }
    }
}
