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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/**
 * Press feedback like the prototype: the control shrinks while pressed (150 ms, ease-out) and springs back.
 * Buttons shrink to 90 %; anything wider than ~200 dp (rows, cards) only to 97 % so big areas don't jump.
 * [pressedScale] overrides the automatic choice. Disabled controls are drawn at 30 %.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var wide by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val target = if (pressedScale > 0f) pressedScale else if (wide) 0.97f else 0.9f
    val scale by animateFloatAsState(if (pressed && enabled) target else 1f, TMotion.press(), label = "press")
    val alpha = if (enabled) 1f else 0.3f
    return this
        .onSizeChanged { wide = with(density) { it.width.toDp() } > 200.dp }
        .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
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

/**
 * Thin slider like the prototype's popups: 4 dp track, filled part in [fill], round thumb.
 * Drag or tap anywhere on it.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    fill: Color = Color.White,
    track: Color = Color.White.copy(alpha = 0.2f),
    enabled: Boolean = true,
) {
    val latest = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    androidx.compose.foundation.Canvas(
        modifier.height(28.dp).then(
            if (!enabled) Modifier else Modifier.pointerInput(range) {
                fun at(x: Float): Float {
                    val pad = 8.dp.toPx()
                    val t = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    return range.start + t * (range.endInclusive - range.start)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latest.value(at(down.position.x))
                    drag(down.id) { c -> c.consume(); latest.value(at(c.position.x)) }
                }
            },
        ),
    ) {
        val pad = 8.dp.toPx()
        val cy = size.height / 2
        val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
        val x = pad + ((value - range.start) / span).coerceIn(0f, 1f) * (size.width - 2 * pad)
        val w = 4.dp.toPx()
        drawLine(track, androidx.compose.ui.geometry.Offset(pad, cy), androidx.compose.ui.geometry.Offset(size.width - pad, cy), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(fill, androidx.compose.ui.geometry.Offset(pad, cy), androidx.compose.ui.geometry.Offset(x, cy), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(fill.copy(alpha = if (enabled) 1f else 0.4f), 8.dp.toPx(), androidx.compose.ui.geometry.Offset(x, cy))
    }
}

/**
 * Shrinks this element from the bottom by however much the on-screen keyboard covers it, so a
 * scrolling list put after this modifier keeps the focused text field in view above the keyboard.
 * The launcher draws edge-to-edge and the window never resizes, so only what uses this moves.
 */
@Composable
fun Modifier.avoidKeyboard(): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    // Distance from this element's bottom edge to the bottom of the window.
    var gap by remember { mutableStateOf(0) }
    val covered = (imeBottom - gap).coerceAtLeast(0)
    return this
        .onGloballyPositioned { c ->
            val root = c.findRootCoordinates()
            val bottom = root.localBoundingBoxOf(c, clipBounds = false).bottom
            gap = (root.size.height - bottom).toInt().coerceAtLeast(0)
        }
        .padding(bottom = with(density) { covered.toDp() })
}
