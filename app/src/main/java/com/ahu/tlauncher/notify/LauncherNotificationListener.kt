package com.ahu.tlauncher.notify

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Grants the launcher access to media sessions (music card) and lets it read the ongoing
 * turn-by-turn notification posted by Google Maps / Waze (navigation card).
 * Nothing is stored or sent anywhere.
 */
class LauncherNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.refresh(this)
        NotificationHub.clearNav()
        NotificationHub.clearCalls()
        runCatching { activeNotifications?.forEach { handle(it) } }
    }

    override fun onListenerDisconnected() { NotificationHub.clearNav(); NotificationHub.clearCalls() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
        if (sbn.notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) {
            NotificationHub.refresh(this)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NavParser.NAV_PACKAGES) NotificationHub.onNavRemoved(sbn.key)
        NotificationHub.onCallRemoved(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification) {
        handleCall(sbn)
        if (sbn.packageName !in NavParser.NAV_PACKAGES) return
        val n = sbn.notification
        val isNav = n.category == "navigation" || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!isNav) return
        val e = n.extras ?: return
        val info = NavParser.parse(
            sbn.packageName,
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_TEXT),
            e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ) ?: return
        NotificationHub.onNavPosted(sbn.key, NavCardState(info, largeIcon(n), n.contentIntent))
    }

    private fun handleCall(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val e = n.extras ?: return
        val acts = n.actions ?: emptyArray()
        val info = CallParser.parse(
            sbn.packageName, n.category,
            e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT),
            acts.map { it.title },
            ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            usesChronometer = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
            whenMs = n.`when`,
        )
        if (info == null) { NotificationHub.onCallRemoved(sbn.key); return }
        val intents = info.actions.mapNotNull { (k, i) -> acts.getOrNull(i)?.actionIntent?.let { k to it } }.toMap()
        NotificationHub.onCallPosted(sbn.key, CallCardState(info, intents, n.contentIntent, System.currentTimeMillis()))
    }

    private fun largeIcon(n: Notification): Bitmap? = runCatching {
        val icon: Icon? = if (Build.VERSION.SDK_INT >= 23) n.getLargeIcon() else null
        val d = icon?.loadDrawable(this) ?: return@runCatching null
        if (d is BitmapDrawable && d.bitmap != null) return@runCatching d.bitmap
        val w = d.intrinsicWidth.coerceAtLeast(1); val h = d.intrinsicHeight.coerceAtLeast(1)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b); d.setBounds(0, 0, w, h); d.draw(c)
        }
    }.getOrNull()
}
