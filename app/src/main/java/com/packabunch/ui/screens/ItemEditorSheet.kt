package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawBehind
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
    /** For a new item: save it and start the next one straight away. */
    onSaveAndAddAnother: ((ItemSpec) -> Unit)? = null,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    // True while the name is one the photo suggested and the person has not touched. Only then
    // does the "Suggested label" chip show, so nobody mistakes a guess for what they typed.
    var nameSuggested by remember(existing) { mutableStateOf(false) }
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
    // What the photo shows, once it has been looked at, and whether that is still going on.
    var photoLabel by remember(itemId) { mutableStateOf<String?>(null) }
    var readingPhoto by remember(itemId) { mutableStateOf(false) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) scope.launch {
            photoPath = com.packabunch.data.ItemPhotos.store(context, itemId, picked)
            photoLabel = null
            readingPhoto = true
            val label = photoPath?.let { com.packabunch.ui.render.PhotoLabels.of(it) }?.replaceFirstChar { it.uppercase() }
            readingPhoto = false
            photoLabel = label
            // An unnamed item takes its name from what the photo shows. A name the person
            // already typed is never replaced; the label is offered beside it instead.
            if (label != null && name.isBlank()) { name = label; nameSuggested = true }
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

    // In the shared sheet: swiped away by its handle, and clear of the phone's own buttons —
    // "Remove this item" used to sit right on the navigation bar.
    com.packabunch.ui.components.PackSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        header = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        },
    ) {
            Spacer(Modifier.height(Spacing.base))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        // The `Photo` tile: always the brand's peach with a dashed edge. It used
                        // to take the item's own colour, which made it green for item two.
                        .background(PhotoTile, RoundedCornerShape(20.dp))
                        .drawBehind {
                            if (photoPath == null) drawRoundRect(
                                color = PhotoTileEdge,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                                ),
                            )
                        }
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
                                    photoLabel = null
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
                        onValueChange = { name = it; nameSuggested = false },
                        placeholder = "Item ${nextIndex + 1}",
                        focused = true,
                    )
                    // What the photo told us, or that it cannot tell us the size. Fades from one
                    // to the next as a photo is added, read and labelled.
                    val hint = when {
                        photoPath == null -> NameHint.SizeNote
                        readingPhoto -> NameHint.Reading
                        photoLabel == null -> NameHint.SizeNote
                        nameSuggested -> NameHint.Suggested
                        name.trim().equals(photoLabel, ignoreCase = true) -> NameHint.SizeNote
                        else -> NameHint.Offer
                    }
                    androidx.compose.animation.AnimatedContent(
                        targetState = hint,
                        transitionSpec = {
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) togetherWith
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                        },
                        label = "nameHint",
                    ) { shown ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF4EDE4), RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = shown == NameHint.Suggested || shown == NameHint.Offer) {
                                    if (shown == NameHint.Suggested) { name = ""; nameSuggested = false }       // tap to change
                                    else { name = photoLabel.orEmpty(); nameSuggested = true }                // tap to use
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (shown == NameHint.Reading || shown == NameHint.Suggested) {
                                // The "Feedback / Suggested label" spinner, played as the guess lands.
                                com.packabunch.ui.components.LottieTapIcon(
                                    animation = com.packabunch.R.raw.icon_suggestion_spinner,
                                    contentDescription = null,
                                    onClick = null,
                                    size = 15.dp,
                                    playOnAppear = true,
                                )
                            } else {
                                Icon(PackIcons.Sparkle, contentDescription = null, tint = Color(0xFF8A7565), modifier = Modifier.size(15.dp))
                            }
                            Text(
                                when (shown) {
                                    NameHint.SizeNote -> "A photo doesn't set the size"
                                    NameHint.Reading -> "Looking at the photo…"
                                    NameHint.Suggested -> "Suggested label — tap to change"
                                    NameHint.Offer -> "Suggested: ${photoLabel.orEmpty().lowercase()} — tap to use"
                                },
                                color = Color(0xFF7C6857),
                                fontFamily = UiFamily,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
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

            val draft = {
                ItemSpec(
                    id = itemId,
                    name = name.ifBlank { "Item ${nextIndex + 1}" },
                    dimensions = Dimensions(widthMm ?: 0, depthMm ?: 0, heightMm ?: 0),
                    quantity = quantity,
                    keepUpright = keepUpright,
                    maySupportItems = !nothingOnTop,
                    measurementSource = com.packabunch.packing.MeasurementSource.TYPED_IN,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing == null && onSaveAndAddAnother != null) {
                    SecondaryButton(
                        text = "Save & add another",
                        onClick = { onSaveAndAddAnother(draft()) },
                        modifier = Modifier.weight(1f),
                        enabled = valid,
                    )
                } else if (onDuplicate != null) {
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

/** The line under the name: about the size, reading the photo, or what the photo shows. */
private enum class NameHint { SizeNote, Reading, Suggested, Offer }

/** The `Photo` tile's peach and its dashed edge. */
private val PhotoTile = Color(0xFFF3DFD2)
private val PhotoTileEdge = Color(0xFFD9BFA8)
