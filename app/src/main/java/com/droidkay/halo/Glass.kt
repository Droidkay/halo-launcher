package com.droidkay.halo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iPadOS 26-style Liquid Glass surface: a translucent body with a bright specular rim on the
 * top-left edge, a softer rim bottom-right, and a gentle sheen across the top.
 * [dark] gives the smoky dark-glass used by dark-mode widgets.
 */
fun Modifier.liquidGlass(shape: Shape, dark: Boolean = false, rim: Dp = 1.2.dp): Modifier {
    val body = if (dark) {
        Brush.verticalGradient(listOf(Color(0xA6161618), Color(0xB80B0B0D)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f)))
    }
    val edge = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.42f else 0.75f),
        0.35f to Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
        0.65f to Color.White.copy(alpha = if (dark) 0.04f else 0.08f),
        1f to Color.White.copy(alpha = if (dark) 0.22f else 0.40f),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return this
        .clip(shape)
        .background(body)
        .drawWithContent {
            // Light catching the top of the glass.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.07f else 0.16f),
                    0.45f to Color.Transparent,
                ),
            )
            drawContent()
        }
        .border(rim, edge, shape)
}
