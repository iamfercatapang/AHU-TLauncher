package com.ahu.tlauncher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.system.EqMath
import com.ahu.tlauncher.system.EqSettings
import com.ahu.tlauncher.ui.components.ButtonKind
import com.ahu.tlauncher.ui.components.PillButton
import com.ahu.tlauncher.ui.components.TSwitch
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.home.HomeActions
import com.ahu.tlauncher.ui.home.HomeUiState
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** Equalizer (bands, presets, bass boost, loudness) and a shortcut to the head unit's own EQ app. */
@Composable
internal fun SoundSection(state: HomeUiState, a: HomeActions) {
    val caps = state.eqCaps
    // Local copy so sliders move smoothly; every change is applied and saved through the action.
    var eq by remember(state.settings.eq) { mutableStateOf(EqMath.decode(state.settings.eq)) }
    val gains = if (eq.gainsDb.size == caps.freqsHz.size) eq.gainsDb else List(caps.freqsHz.size) { 0f }
    fun update(s: EqSettings) { eq = s; a.setEq(s) }

    SettingRow("Resume music when the car starts", "Plays YouTube Music or Spotify again if it was playing when the car was switched off") {
        TSwitch(state.settings.resumeMusic, a::setResumeMusic)
    }
    Divider()
    SettingRow(
        "Equalizer",
        if (caps.supported) "Applies to music and other sound played through Android" else "This head unit doesn't let apps change the sound. Use its own EQ app below.",
    ) { TSwitch(eq.enabled, { update(eq.copy(enabled = it, gainsDb = gains)) }, Modifier.alpha(if (caps.supported) 1f else 0.4f)) }

    Column(Modifier.alpha(if (eq.enabled && caps.supported) 1f else 0.45f)) {
        // presets
        Row(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (EqMath.PRESETS.keys + eq.custom.keys).forEach { name ->
                val sel = name == eq.preset
                Box(
                    Modifier.clip(TShapes.button).background(if (sel) T.colors.accent else T.colors.card)
                        .pressable {
                            val g = eq.custom[name] ?: EqMath.curve(EqMath.PRESETS.getValue(name), caps.freqsHz)
                            update(eq.copy(enabled = true, preset = name, gainsDb = g))
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(name, style = TType.label, color = if (sel) T.colors.onAccent else T.colors.text) }
            }
            if (eq.preset == "Custom") Box(Modifier.clip(TShapes.button).background(T.colors.accent).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Custom", style = TType.label, color = T.colors.onAccent)
            }
        }
        // bands: vertical sliders, value and frequency underneath
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            caps.freqsHz.forEachIndexed { i, f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VerticalBand(gains[i], caps.minDb, caps.maxDb) { v ->
                        update(eq.copy(enabled = true, preset = "Custom", gainsDb = gains.toMutableList().also { it[i] = v }))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("%.1f".format(gains[i]), style = TType.caption, color = T.colors.muted)
                    Text(EqMath.freqLabel(f), style = TType.caption, color = T.colors.text)
                }
            }
        }
        // save / delete your own preset
        var name by remember { mutableStateOf("") }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(40.dp).clip(TShapes.inner).background(T.colors.card).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) Text("Name for your preset", style = TType.body, color = T.colors.muted)
                BasicTextField(name, { name = it.take(20) }, singleLine = true, textStyle = TType.body.copy(color = T.colors.text), cursorBrush = SolidColor(T.colors.accent), modifier = Modifier.fillMaxWidth())
            }
            PillButton("Save preset", onClick = {
                val n = name.trim()
                if (n.isNotEmpty() && n !in EqMath.PRESETS) { update(eq.copy(preset = n, gainsDb = gains, custom = eq.custom + (n to gains))); name = "" }
            })
            if (eq.preset in eq.custom) PillButton("Delete \"${eq.preset}\"", onClick = { update(eq.copy(preset = "Custom", custom = eq.custom - eq.preset)) })
        }
        if (caps.bassSupported) LevelRow("Bass boost", eq.bass) { update(eq.copy(enabled = true, bass = it, gainsDb = gains)) }
        if (caps.loudnessSupported) LevelRow("Loudness", eq.loudness) { update(eq.copy(enabled = true, loudness = it, gainsDb = gains)) }
    }

    Divider()
    UnitEq(state, a)
}

/**
 * One EQ band as a tall, thin vertical slider: light track, filled from the bottom up to a round
 * thumb (drag or tap anywhere on it). Half-dB steps.
 */
@Composable
private fun VerticalBand(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val track = T.colors.divider
    val fill = T.colors.text
    val thumb = T.colors.panel
    val ring = T.colors.secondary
    val latest = androidx.compose.runtime.rememberUpdatedState(onChange)
    androidx.compose.foundation.Canvas(
        Modifier.width(36.dp).height(180.dp)
            .pointerInput(min, max) {
                fun at(y: Float): Float {
                    val pad = 12.dp.toPx()
                    val t = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return Math.round((min + t * (max - min)) * 2) / 2f
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latest.value(at(down.position.y))
                    drag(down.id) { c -> c.consume(); latest.value(at(c.position.y)) }
                }
            },
    ) {
        val pad = 12.dp.toPx()
        val cx = size.width / 2
        val w = 3.dp.toPx()
        val y = pad + (1f - (value - min) / (max - min)) * (size.height - 2 * pad)
        drawLine(track, androidx.compose.ui.geometry.Offset(cx, pad), androidx.compose.ui.geometry.Offset(cx, size.height - pad), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(fill, androidx.compose.ui.geometry.Offset(cx, y), androidx.compose.ui.geometry.Offset(cx, size.height - pad), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(ring, 9.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, y))
        drawCircle(thumb, 8.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, y))
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = T.colors.text, activeTrackColor = T.colors.accent, inactiveTrackColor = T.colors.divider)

@Composable
private fun LevelRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TType.body, color = T.colors.text, modifier = Modifier.width(110.dp))
        Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..100f, colors = sliderColors(), modifier = Modifier.weight(1f).height(32.dp))
        Text("$value%", style = TType.label, color = T.colors.text, modifier = Modifier.width(48.dp))
    }
}

/** Shortcut to the head unit's own EQ / DSP app, chosen once from the app list. */
@Composable
private fun UnitEq(state: HomeUiState, a: HomeActions) {
    val pkg = state.settings.unitEqApp
    val chosen = state.apps.firstOrNull { it.packageName == pkg }
    var picking by remember { mutableStateOf(false) }
    SmallTitle("Head unit equalizer")
    Text(
        "Your head unit's own EQ app usually works on all its audio, including radio. TLauncher can't read or change its settings, so " +
            "use one equalizer at a time and keep the other one flat, or the sound can boom or distort.",
        style = TType.label, color = T.colors.muted,
    )
    SettingRow(chosen?.label ?: "Not chosen yet", if (chosen == null) "Pick the head unit's EQ, DSP or Sound app" else null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chosen != null) PillButton("Open", kind = ButtonKind.PRIMARY, onClick = { a.launch(chosen) })
            PillButton(if (chosen == null) "Choose app" else "Change", onClick = { picking = !picking })
        }
    }
    if (picking) {
        val hint = Regex("eq|dsp|sound|audio|effect|amp", RegexOption.IGNORE_CASE)
        val list = state.apps.sortedByDescending { hint.containsMatchIn(it.label) || hint.containsMatchIn(it.packageName) }
        Column(Modifier.fillMaxWidth().clip(TShapes.card).background(T.colors.card).padding(6.dp)) {
            list.take(40).forEach { app ->
                Row(
                    Modifier.fillMaxWidth().clip(TShapes.inner).pressable { a.chooseUnitEq(app.packageName); picking = false }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(app.icon, null, Modifier.size(32.dp).clip(TShapes.inner))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, style = TType.body, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
