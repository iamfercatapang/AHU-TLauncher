package com.ahu.tlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.notify.InboxItem
import com.ahu.tlauncher.ui.components.TIcons
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TShapes
import com.ahu.tlauncher.ui.theme.TType

/** The bell's list: newest first; tap to open, × to dismiss, "Clear all". */
@Composable
fun NotificationsPopup(state: HomeUiState, actions: HomeActions, onClose: () -> Unit) {
    val surface = if (T.colors.isDark) Color(0xFF22252B) else T.colors.panel
    Column(Modifier.width(STATUS_POPUP_W).clip(TShapes.surface).background(surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", style = TType.title, color = T.colors.text, modifier = Modifier.weight(1f))
            if (state.inbox.isNotEmpty()) Box(Modifier.clip(TShapes.button).pressable(onClick = actions::dismissAllInbox).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Clear all", style = TType.label, color = T.colors.muted)
            }
            Box(Modifier.size(32.dp).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(TIcons.Close, "Close", tint = T.colors.text, modifier = Modifier.size(16.dp))
            }
        }
        if (!state.notificationAccess) {
            Text("Allow notification access to see them here.", style = TType.label, color = T.colors.muted, modifier = Modifier.padding(vertical = 14.dp))
            return@Column
        }
        if (state.inbox.isEmpty()) {
            Text("No notifications", style = TType.body, color = T.colors.muted, modifier = Modifier.padding(vertical = 18.dp))
            return@Column
        }
        LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 6.dp)) {
            items(state.inbox, key = { it.key }) { n -> InboxRow(state, n, actions) }
        }
    }
}

@Composable
private fun InboxRow(state: HomeUiState, n: InboxItem, actions: HomeActions) {
    val app = state.apps.firstOrNull { it.packageName == n.packageName }
    Row(
        Modifier.fillMaxWidth().clip(TShapes.card).pressable { actions.openInbox(n) }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (app != null) Image(app.icon, null, Modifier.size(28.dp).clip(TShapes.inner))
        else Box(Modifier.size(28.dp).clip(TShapes.inner).background(T.colors.card))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text((app?.label ?: n.packageName.substringAfterLast('.')) + " · " + ago(state.now, n.postedMs), style = TType.caption, color = T.colors.muted, maxLines = 1)
            Text(n.title, style = TType.button, color = T.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) Text(n.text, style = TType.label, color = T.colors.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(28.dp).pressable { actions.dismissInbox(n) }, contentAlignment = Alignment.Center) {
            Icon(TIcons.Close, "Dismiss", tint = T.colors.muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** "now", "5 min", "2 h", "3 d". */
internal fun ago(now: java.time.LocalDateTime, postedMs: Long): String {
    val nowMs = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val m = ((nowMs - postedMs) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "now"; m < 60 -> "$m min"; m < 24 * 60 -> "${m / 60} h"; else -> "${m / 1440} d" }
}
