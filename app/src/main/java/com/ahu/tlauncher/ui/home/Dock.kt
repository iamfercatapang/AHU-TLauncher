package com.ahu.tlauncher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.launcher.AppEntry
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.TMotion
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType
import kotlinx.coroutines.delay

/** The dock is always black, in both themes, like the in-car reference. */
private val DOCK_BG = Color.Black
private val DOCK_FG = Color.White
private val DOCK_MUTED = Color.White.copy(alpha = 0.6f)
/** Prototype's active tint (#c5d8ee12) and underline (#b9cbdd). */
private val ACTIVE_BG = Color(0x12C5D8EE)
private val ACTIVE_LINE = Color(0xFFB9CBDD)
private val BUBBLE_BG = Color(0xFF22252B)

/** Pinned apps shown in the dock (Tesla's "my apps" side). */
const val MAX_DOCK_APPS = 6
/** Recently used apps shown after the pinned ones, when there's room. */
const val MAX_RECENT_APPS = 3

private val APP_SLOT = 58.dp
private val BUTTON_SLOT = 60.dp
/** Finger travel for one step when dragging across the brightness or volume control. */
private val DRAG_STEP = 16.dp
/** Brightness changes 5 % per step. */
private const val BRIGHT_STEP = 5
/** The level bubble stays this long after the last change. */
private const val BUBBLE_MS = 1500L

/** Pinned apps that are installed, at most [MAX_DOCK_APPS]. */
internal fun dockPinned(state: HomeUiState): List<AppEntry> =
    state.settings.pinnedApps.mapNotNull { key -> state.apps.firstOrNull { it.key == key } }.take(MAX_DOCK_APPS)

/** Recently used apps that aren't pinned, newest first, at most [max]. */
internal fun dockRecents(state: HomeUiState, max: Int): List<AppEntry> {
    if (max <= 0) return emptyList()
    val pinned = state.settings.pinnedApps.toSet()
    return state.settings.recentApps.asSequence().filter { it !in pinned }
        .mapNotNull { key -> state.apps.firstOrNull { it.key == key } }.take(max).toList()
}

/**
 * Laid out like Tesla's control bar: the car (settings) on the far left, then where Tesla has the
 * driver's temperature, brightness `‹ ☀ ›`. In the middle your pinned apps, the app drawer button and the
 * recently used apps, then the voice mic. Volume `‹ 🔊 ›` sits on the far right.
 * Brightness and volume step with the chevrons, or follow a finger dragged sideways across them.
 */
@Composable
fun Dock(
    state: HomeUiState,
    actions: HomeActions,
    overlay: Overlay,
    onOpenSettings: () -> Unit,
    onToggleApps: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    onTogglePin: (AppEntry) -> Unit = {},
    onOpenBrightness: () -> Unit = {},
) {
    Row(
        modifier.background(DOCK_BG).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(com.ahu.tlauncher.ui.components.MpvFrontIcon, "Vehicle & settings", overlay == Overlay.SETTINGS, onOpenSettings)
        BrightnessControl(state, actions, overlay == Overlay.BRIGHTNESS, onOpenBrightness)
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val pinned = dockPinned(state)
            // Room left for apps once the app drawer button and the mic are placed; recents fill what the pinned apps leave.
            val slots = ((maxWidth - BUTTON_SLOT * 2) / APP_SLOT).toInt().coerceAtLeast(0)
            val shownPinned = pinned.take(slots)
            val recents = dockRecents(state, minOf(MAX_RECENT_APPS, slots - shownPinned.size))
            Row(verticalAlignment = Alignment.CenterVertically) {
                shownPinned.forEach { app -> DockApp(app, onLaunch, onTogglePin) }
                // The app drawer button also separates pinned apps from recent ones.
                DockButton(TIcons.AppGrid, "All apps", overlay == Overlay.APPS, onToggleApps)
                recents.forEach { app -> DockApp(app, onLaunch, onTogglePin) }
                DockButton(Icons.Rounded.Mic, "Voice command", false, actions::voiceCommand)
            }
        }
        VolumeControl(state, actions)
    }
}

/** An app in the dock: tap opens it, long-press pins or unpins it. */
@Composable
private fun DockApp(app: AppEntry, onLaunch: (AppEntry) -> Unit, onTogglePin: (AppEntry) -> Unit) {
    Box(
        Modifier.size(APP_SLOT, 56.dp).pressable(onLongClick = { onTogglePin(app) }) { onLaunch(app) },
        contentAlignment = Alignment.Center,
    ) {
        Image(app.icon, app.label, modifier = Modifier.size(40.dp).clip(TShapes.button))
    }
}

/** Icon button with the prototype's active state: soft tinted square plus a short underline. */
@Composable
private fun DockButton(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (active) 1f else 0f, TMotion.panel(), label = "dockActive")
    Box(Modifier.size(BUTTON_SLOT), contentAlignment = Alignment.Center) {
        Box(Modifier.size(54.dp).graphicsLayer { alpha = a }.clip(TShapes.surface).background(ACTIVE_BG))
        Box(Modifier.size(54.dp).clip(TShapes.surface).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = DOCK_FG, modifier = Modifier.size(32.dp))
        }
        // underline grows in from the centre
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).width(14.dp).height(2.dp)
                .graphicsLayer { alpha = a; scaleX = a }.clip(TShapes.button).background(ACTIVE_LINE),
        )
    }
}

/** Screen brightness `‹ ☀ ›`: 5 % per step. Tap the sun for the day / night levels. */
@Composable
private fun BrightnessControl(state: HomeUiState, actions: HomeActions, active: Boolean, onOpen: () -> Unit) {
    // Steps can come faster than the new level reaches the state, so count from the last one sent.
    var level by remember(state.brightness) { mutableIntStateOf(state.brightness) }
    // Without permission, ask once per gesture rather than on every step of a drag.
    var askedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    DockStepper(
        icon = TIcons.Sun, desc = "Brightness", active = active,
        bubble = { "Brightness ${level}%" }, fraction = { level / 100f },
        onStep = { up ->
            if (!state.brightnessAllowed) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - askedAt > 3000) { askedAt = now; actions.requestBrightnessPermission() }
                return@DockStepper
            }
            level = (level + if (up) BRIGHT_STEP else -BRIGHT_STEP).coerceIn(1, 100)
            actions.setBrightness(level)
        },
        onCenter = onOpen,
    )
}

/** Volume `‹ 🔊 ›` like Tesla's: chevrons or a sideways drag change it, tap the speaker to mute. */
@Composable
private fun VolumeControl(state: HomeUiState, actions: HomeActions) {
    val v = state.volume
    val off = v.muted || v.level == 0
    DockStepper(
        icon = if (off) TIcons.SpeakerOff else TIcons.SpeakerWaves, desc = if (v.muted) "Unmute" else "Mute",
        bubble = { if (v.muted) "Muted" else "Volume ${v.level}" },
        fraction = { if (v.muted || v.max == 0) 0f else v.level / v.max.toFloat() },
        onStep = actions::volumeStep,
        onCenter = actions::toggleMute,
    )
}

/**
 * `‹ icon ›` with Tesla's temperature-control layout. A finger dragged sideways across it steps once
 * every [DRAG_STEP], so it can be used without looking. A bubble with the level shows above it while
 * it changes.
 */
@Composable
private fun DockStepper(
    icon: ImageVector,
    desc: String,
    bubble: () -> String,
    fraction: () -> Float,
    onStep: (up: Boolean) -> Unit,
    onCenter: () -> Unit,
    active: Boolean = false,
) {
    var changed by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    LaunchedEffect(changed, dragging) {
        if (changed == 0) return@LaunchedEffect
        showBubble = true
        if (!dragging) { delay(BUBBLE_MS); showBubble = false }
    }
    val step by rememberUpdatedState { up: Boolean -> onStep(up); changed++ }
    val stepPx = with(LocalDensity.current) { DRAG_STEP.toPx() }
    Box(
        Modifier.width(140.dp).height(56.dp).pointerInput(stepPx) {
            var acc = 0f
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f; dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, dx ->
                change.consume()
                acc += dx
                while (acc >= stepPx) { acc -= stepPx; step(true) }
                while (acc <= -stepPx) { acc += stepPx; step(false) }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 56.dp).pressable { step(false) }, contentAlignment = Alignment.Center) {
                Icon(TIcons.ChevronLeft, "$desc down", tint = DOCK_MUTED, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier.size(56.dp, 48.dp).clip(TShapes.surface).background(if (active) ACTIVE_BG else Color.Transparent).pressable(onClick = onCenter),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, desc, tint = DOCK_FG, modifier = Modifier.size(30.dp))
            }
            Box(Modifier.size(40.dp, 56.dp).pressable { step(true) }, contentAlignment = Alignment.Center) {
                Icon(TIcons.ChevronRight, "$desc up", tint = DOCK_MUTED, modifier = Modifier.size(20.dp))
            }
        }
        // Level bubble, drawn above the dock (placed outside this box without taking space).
        Box(
            Modifier.align(Alignment.TopCenter).layout { m, _ ->
                val p = m.measure(Constraints())
                layout(0, 0) { p.place(-p.width / 2, -p.height - 10.dp.roundToPx()) }
            },
        ) {
            AnimatedVisibility(showBubble, enter = fadeIn(TMotion.panel()), exit = fadeOut(TMotion.std())) {
                Column(
                    Modifier.width(180.dp).clip(TShapes.surface).background(BUBBLE_BG).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(bubble(), style = TType.button, color = DOCK_FG, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(TShapes.button).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction().coerceIn(0f, 1f)).clip(TShapes.button).background(DOCK_FG))
                    }
                }
            }
        }
    }
}
