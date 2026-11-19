package com.ahu.tlauncher.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val key: String,            // "package/activity" – stable id used for pinning
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    /**
     * The minimal icon: an Arcticons line icon, or the app's own icon in one colour. When [minimalIsMask] it's
     * white on transparent, to be tinted with the theme's text colour; otherwise a grey version of the original.
     */
    val minimal: ImageBitmap = icon,
    val minimalIsMask: Boolean = false,
    /** User-installed (not part of the firmware), so it can be uninstalled. */
    val removable: Boolean = false,
)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = reload()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        reload()
    }

    fun reload() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) { load() }
        }
    }

    private fun load(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val sizePx = (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                val original = ri.loadIcon(pm)
                val icon = original.toBitmap(sizePx).asImageBitmap() // before minimalIcon moves its layers
                val (minimal, isMask) = minimalIcon(cn, original, sizePx)
                AppEntry(
                    key = cn.flattenToString(),
                    label = ri.loadLabel(pm).toString(),
                    packageName = cn.packageName,
                    component = cn,
                    icon = icon,
                    minimal = minimal.asImageBitmap(),
                    minimalIsMask = isMask,
                    removable = (ri.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0,
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(entry: AppEntry): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }.isSuccess

    fun launchPackage(pkg: String): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun isInstalled(pkg: String): Boolean = _apps.value.any { it.packageName == pkg } ||
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** First-run dock: common in-car apps that happen to be installed. */
    fun defaultPins(): List<String> {
        val wanted = listOf(
            "com.google.android.apps.maps", "com.waze", "com.spotify.music",
            "com.google.android.apps.youtube.music", "com.google.android.youtube",
            "com.google.android.dialer", "com.android.dialer", "com.android.settings",
        )
        val all = _apps.value
        return wanted.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg }?.key }.take(6)
    }

    /**
     * The app's minimal icon and whether it's a white mask: its Arcticons icon if there is one; else the monochrome
     * layer of its adaptive icon (Android 13+), or the foreground layer in one colour; else its icon in grey.
     */
    private fun minimalIcon(cn: ComponentName, original: Drawable, size: Int): Pair<Bitmap, Boolean> {
        val arc = ArcticonsMap.byComponent[cn.packageName + "/" + cn.className] ?: ArcticonsMap.byPackage[cn.packageName]
        if (arc != null) ContextCompat.getDrawable(context, arc)?.let { return it.toBitmap(size) to true }
        if (original is AdaptiveIconDrawable) {
            val layer = (if (Build.VERSION.SDK_INT >= 33) original.monochrome else null) ?: original.foreground
            if (layer != null) {
                // A layer is 108 units of which the icon shows the middle 72: draw it 1.5 × the size, centred.
                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                val pad = size / 4
                layer.setBounds(-pad, -pad, size + pad, size + pad)
                layer.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                layer.draw(c)
                layer.colorFilter = null
                return b to true
            }
        }
        val grey = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val src = original.toBitmap(size)
        Canvas(grey).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        return grey to false
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        setBounds(0, 0, size, size)
        draw(c)
        return b
    }
}
