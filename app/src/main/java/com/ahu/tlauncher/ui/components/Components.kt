package com.ahu.tlauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/**
 * Press feedback is opacity-only (no scale/translate), using the standard 0.33s transition.
 * `pressedScale` is accepted for call-site compatibility but ignored.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") pressedScale: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        when { !enabled -> 0.3f; pressed -> 0.55f; else -> 1f }, TMotion.std(), label = "press",
    )
    return this
        .graphicsLayer { this.alpha = alpha }
        .combinedClickable(
            interactionSource = source, indication = null, enabled = enabled,
            onLongClick = onLongClick, onClick = onClick,
        )
}

/** Flat surface (Light Ash in light mode). No border, no shadow. */
@Composable
fun TCard(modifier: Modifier = Modifier, shape: Shape = TShapes.surface, color: Color = T.colors.card, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(color), content = content)
}

@Composable
fun IconButtonT(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: Color = T.colors.text,
    active: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) T.colors.card else Color.Transparent, TMotion.std(), label = "iconbg")
    Box(
        modifier.size(size).clip(TShapes.button).background(bg)
            .pressable(enabled = enabled, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

enum class ButtonKind { PRIMARY, SECONDARY }

/** Spec buttons: 4dp radius, 40dp min height, 15sp/500 label. Primary = Electric Blue, secondary = Light Ash. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ButtonKind = ButtonKind.SECONDARY,
    height: Dp = 40.dp,
    onClick: () -> Unit,
) {
    val bg = if (kind == ButtonKind.PRIMARY) T.colors.accent else T.colors.card
    val fg = if (kind == ButtonKind.PRIMARY) T.colors.onAccent else T.colors.text
    Row(
        modifier.height(height).clip(TShapes.button).background(bg).pressable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = TType.button, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Segmented control: Light Ash track, selected option on the canvas colour. */
@Composable
fun <E> Segmented(options: List<Pair<E, String>>, selected: E, onSelect: (E) -> Unit, modifier: Modifier = Modifier, segmentWidth: Dp = 72.dp) {
    Row(
        modifier.height(40.dp).clip(TShapes.button).background(T.colors.card).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            val selBg = if (T.colors.isDark) T.colors.divider else T.colors.panel
            val bg by animateColorAsState(if (sel) selBg else Color.Transparent, TMotion.std(), label = "seg")
            Box(
                Modifier.fillMaxHeight().width(segmentWidth).clip(TShapes.button).background(bg).pressable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TType.button, color = if (sel) T.colors.text else T.colors.muted)
            }
        }
    }
}

@Composable
fun TSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) T.colors.accent else T.colors.divider, TMotion.std(), label = "sw")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, TMotion.std(), label = "swx")
    Box(modifier.width(48.dp).height(28.dp).clip(CircleShape).background(track).pressable { onChange(!checked) }) {
        Box(Modifier.offset(x = x, y = 2.dp).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

/** Borderless readout: value over a quiet label, separated from neighbours by space alone. */
@Composable
fun RowScope.Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier, valueColor: Color = T.colors.text) {
    Column(modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = TType.promo, color = valueColor, maxLines = 1)
            Text(" $unit", style = TType.label, color = T.colors.muted, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, style = TType.label, color = T.colors.muted, maxLines = 1)
    }
}
