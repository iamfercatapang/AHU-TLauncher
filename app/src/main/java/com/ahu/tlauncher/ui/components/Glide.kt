package com.ahu.tlauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import com.ahu.tlauncher.ui.theme.TMotion
import kotlinx.coroutines.launch

/**
 * FLIP-style glide: when [key] changes and this element lands somewhere else (because the
 * layout switched instantly), it starts visually at its old position/size and glides to the new
 * one using transforms only (translation + scale), so nothing is re-laid out during the motion.
 * Interrupting mid-glide retargets from wherever it currently is.
 */
fun Modifier.glideOnChange(key: Any): Modifier = composed {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val s = remember { Animatable(1f) }
    var lastKey by remember { mutableStateOf(key) }
    var lastPos by remember { mutableStateOf<Offset?>(null) }
    var lastW by remember { mutableStateOf(0) }

    this
        .onPlaced { c ->
            val pos = c.positionInRoot()
            val w = c.size.width
            val prev = lastPos
            if (key != lastKey && prev != null && w > 0 && lastW > 0) {
                // Where it was drawn on screen (including any glide in flight) vs. where it is now.
                val shownX = prev.x + dx.value
                val shownY = prev.y + dy.value
                val shownW = lastW * s.value
                scope.launch {
                    dx.snapTo(shownX - pos.x); dy.snapTo(shownY - pos.y); s.snapTo(shownW / w)
                    launch { dx.animateTo(0f, TMotion.move()) }
                    launch { dy.animateTo(0f, TMotion.move()) }
                    launch { s.animateTo(1f, TMotion.move()) }
                }
            }
            lastKey = key; lastPos = pos; lastW = w
        }
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = dx.value; translationY = dy.value
            scaleX = s.value; scaleY = s.value
        }
}
