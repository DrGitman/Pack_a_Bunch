package com.packabunch.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.packabunch.packing.Placement
import com.packabunch.packing.Space
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.itemColor

/**
 * The thumbnail on a project card.
 *
 * Same renderer as the full view, with the interaction and the entrance animation off — a
 * list of eight cards each running its own drop-in animation would be noise, and none of
 * them are meant to be dragged. It is still drawn from real placements, so the thumbnail
 * and the plan can never disagree.
 */
@Composable
fun MiniCratePreview(
    space: Space,
    placements: List<Placement>,
    modifier: Modifier = Modifier,
    specOrder: List<String> = emptyList(),
) {
    Box(
        modifier = modifier.background(BrandTint, RoundedCornerShape(18.dp)),
    ) {
        if (placements.isNotEmpty()) {
            IsometricCrate(
                space = space,
                placements = placements,
                modifier = Modifier.fillMaxSize(),
                itemColorFor = { placement ->
                    val index = specOrder.indexOf(placement.specId)
                    itemColor(if (index >= 0) index else placement.sequenceIndex)
                },
                interactive = false,
                animateEntrance = false,
            )
        } else {
            CrateDiagram(Modifier.fillMaxSize().background(Color.Transparent))
        }
    }
}
