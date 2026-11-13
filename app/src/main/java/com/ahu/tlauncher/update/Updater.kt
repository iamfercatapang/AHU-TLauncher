package com.ahu.tlauncher.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.ahu.tlauncher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub "latest" release for a newer build and installs it through PackageInstaller.
 * On Android 12+ updates install without a confirmation screen once TLauncher installed itself
 * at least once; older versions show the system's one-tap confirm dialog.
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {
    private val base = "https://github.com/iamfercatapang/AHU-TLauncher/releases/latest/download"
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Versions the user said "Later" to: no toast again until a newer one appears. */
    private val _dismissed = MutableStateFlow(0)
    val dismissedCode: StateFlow<Int> = _dismissed.asStateFlow()

    private var lastCheck = 0L
    private var job: Job? = null

    fun startPeriodicChecks() {
        if (job != null) return
        job = scope.launch {
            delay(20_000) // let the head unit finish booting / connecting
            while (isActive) { check(); delay(6 * 60 * 60 * 1000L) }
        }
    }

    /** Called on resume: re-check at most once an hour. */
    fun checkIfStale() {
        if (SystemClock.elapsedRealtime() - lastCheck > 60 * 60 * 1000L && lastCheck != 0L) scope.launch { check() }
    }

    suspend fun check(manual: Boolean = false) {
        val s = _state.value
        if (s is UpdateState.Downloading || s is UpdateState.Installing) return
        if (manual) _state.value = UpdateState.Checking
        lastCheck = SystemClock.elapsedRealtime()
        val remote = withContext(Dispatchers.IO) {
            runCatching { RemoteVersion.parse(get("$base/version.json")) }.getOrNull()
        }
        _state.value = when {
            remote == null -> if (manual) UpdateState.Failed("Couldn't reach GitHub. Check the internet connection.") else UpdateState.Idle
            remote.isNewerThan(BuildConfig.VERSION_CODE) -> UpdateState.Available(remote)
            else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
        }
    }

    fun dismiss() {
        (_state.value as? UpdateState.Available)?.let { _dismissed.value = it.version.versionCode }
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) _state.value = UpdateState.Idle
    }

    fun install() {
        val version = when (val s = _state.value) {
            is UpdateState.Available -> s.version
            is UpdateState.Failed -> s.version ?: return
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Failed("Allow TLauncher to install apps, then tap Update again.", version)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        scope.launch {
            try {
                _state.value = UpdateState.Downloading(version, 0f)
                val apk = withContext(Dispatchers.IO) { download("$base/TLauncher.apk", version) }
                _state.value = UpdateState.Installing(version)
                withContext(Dispatchers.IO) { commit(apk) }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Update failed: ${e.message ?: e.javaClass.simpleName}", version)
            }
        }
    }

    internal fun onInstallFailed(message: String?) {
        val v = (_state.value as? UpdateState.Installing)?.version
        _state.value = UpdateState.Failed("Update failed: ${message ?: "unknown error"}", v)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TLauncher/${BuildConfig.VERSION_NAME}")
        }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun download(url: String, version: RemoteVersion): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "TLauncher-${version.versionCode}.apk")
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) _state.value = UpdateState.Downloading(version, done.toFloat() / total)
                    }
                }
            }
        } finally { c.disconnect() }
        return out
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}
