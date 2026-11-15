package com.ahu.tlauncher.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ahu.tlauncher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

enum class AreaStatus { DOWNLOADING, READY, FAILED, TOO_LARGE }

/** A saved offline map area: everything within [radiusKm] of a spot, in both the day and night map styles. */
data class OfflineArea(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusKm: Int,
    val savedAtMs: Long,
    val status: AreaStatus,
    /** 0..1 while downloading. */
    val progress: Float = 0f,
    val sizeBytes: Long = 0,
    val error: String? = null,
)

/**
 * Offline areas for the free (OpenFreeMap) map, kept in MapLibre's offline database so the map still works with no
 * data connection. Downloads follow OpenFreeMap's rules for offline use: at most 3,000 tiles and 50 MB per area,
 * at most 4 requests at a time, and an identifying User-Agent. Google Maps can't be saved offline this way.
 */
class OfflineMaps(private val context: Context, private val scope: CoroutineScope) {
    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()

    private val manager by lazy { setUp(context); OfflineManager.getInstance(context) }
    private var loaded = false

    /** Reads the saved areas (once) and refreshes old ones on an unmetered connection. Call when they're needed. */
    fun load() {
        if (loaded) return
        loaded = true
        scope.launch {
            reloadList()
            refreshStale()
        }
    }

    /** Saves the area within [radiusKm] of a spot. */
    fun save(name: String, lat: Double, lon: Double, radiusKm: Int) {
        if (!OfflineMath.fits(lat, lon, radiusKm)) return
        val id = System.currentTimeMillis()
        val area = OfflineArea(id, name, lat, lon, radiusKm, id, AreaStatus.DOWNLOADING)
        _areas.value = _areas.value + area
        scope.launch { download(area, fresh = true) }
    }

    /** Downloads the area again (new map data, or after a failure). */
    fun update(id: Long) {
        val a = _areas.value.firstOrNull { it.id == id } ?: return
        if (a.status == AreaStatus.DOWNLOADING) return
        scope.launch { download(a.copy(savedAtMs = System.currentTimeMillis()), fresh = false) }
    }

    fun delete(id: Long) {
        scope.launch {
            regionsOf(id).forEach { it.setDownloadState(OfflineRegion.STATE_INACTIVE); deleteRegion(it) }
            _areas.value = _areas.value.filterNot { it.id == id }
        }
    }

    // ---------------------------------------------------------------- download

    /**
     * Downloads [area] in both styles. On an update the old copy is kept until the new one is complete, so a
     * failed update (lost signal) still leaves a working map; then the old copy is deleted.
     */
    private suspend fun download(area: OfflineArea, fresh: Boolean) {
        val before = _areas.value.firstOrNull { it.id == area.id }
        val old = if (fresh) emptyList() else regionsOf(area.id)
        set(area.copy(status = AreaStatus.DOWNLOADING, progress = 0f, error = null))
        val created = mutableListOf<OfflineRegion>()
        suspend fun fail(status: AreaStatus, message: String) {
            created.forEach { deleteRegion(it) }
            set(if (old.isNotEmpty() && before != null) before.copy(error = "Update failed: $message") else area.copy(status = status, progress = 0f, sizeBytes = 0, error = message))
        }
        var bytes = 0L
        for ((i, style) in listOf(STYLE_LIGHT, STYLE_DARK).withIndex()) {
            val region = createRegion(area, style) ?: return fail(AreaStatus.FAILED, "Couldn't create the offline area")
            created += region
            val result = downloadRegion(region) { p, size ->
                set(area.copy(status = AreaStatus.DOWNLOADING, progress = (i + p) / 2f, sizeBytes = bytes + size))
            }
            when (result) {
                is Result.Done -> bytes += result.bytes
                is Result.TooLarge -> return fail(AreaStatus.TOO_LARGE, "Over 50 MB — choose a smaller radius")
                is Result.Failed -> return fail(AreaStatus.FAILED, result.message)
            }
        }
        old.forEach { deleteRegion(it) }
        set(area.copy(status = AreaStatus.READY, progress = 1f, sizeBytes = bytes))
    }

    private sealed interface Result {
        data class Done(val bytes: Long) : Result
        data object TooLarge : Result
        data class Failed(val message: String) : Result
    }

    /** Downloads one region, reporting progress (0..1) and bytes so far. */
    private suspend fun downloadRegion(region: OfflineRegion, progress: (Float, Long) -> Unit): Result = suspendCancellableCoroutine { c ->
        var errors = 0
        fun finish(r: Result) {
            region.setObserver(null); region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            if (c.isActive) c.resume(r)
        }
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val size = status.completedResourceSize
                if (size > OfflineMath.SIZE_LIMIT_BYTES) return finish(Result.TooLarge)
                if (status.isComplete) return finish(Result.Done(size))
                val p = if (status.requiredResourceCount > 0) status.completedResourceCount.toFloat() / status.requiredResourceCount else 0f
                progress(p.coerceIn(0f, 1f), size)
            }
            override fun onError(error: OfflineRegionError) {
                // MapLibre retries network errors itself; give up only if they keep coming.
                if (++errors >= 30) finish(Result.Failed(error.message.ifBlank { "Download failed" }))
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) = finish(Result.TooLarge)
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        c.invokeOnCancellation { region.setObserver(null); region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
    }

    private suspend fun createRegion(area: OfflineArea, style: String): OfflineRegion? {
        val b = OfflineMath.box(area.lat, area.lon, area.radiusKm.toDouble())
        val def = OfflineTilePyramidRegionDefinition(
            style, LatLngBounds.from(b.north, b.east, b.south, b.west), 0.0, OfflineMath.MAX_ZOOM.toDouble(),
            context.resources.displayMetrics.density, false,
        )
        val meta = JSONObject().put("area", area.id).put("name", area.name).put("lat", area.lat).put("lon", area.lon)
            .put("r", area.radiusKm).put("at", area.savedAtMs).put("style", style).toString().toByteArray()
        return suspendCancellableCoroutine { c ->
            manager.createOfflineRegion(def, meta, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = c.resume(offlineRegion)
                override fun onError(error: String) = c.resume(null)
            })
        }
    }

    // ---------------------------------------------------------------- list

    private suspend fun allRegions(): List<OfflineRegion> = suspendCancellableCoroutine { c ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = c.resume(offlineRegions?.toList().orEmpty())
            override fun onError(error: String) = c.resume(emptyList())
        })
    }

    private suspend fun regionsOf(id: Long) = allRegions().filter { meta(it)?.optLong("area") == id }

    private fun meta(r: OfflineRegion): JSONObject? = runCatching { JSONObject(String(r.metadata)) }.getOrNull()

    private suspend fun deleteRegion(r: OfflineRegion) = suspendCancellableCoroutine { c ->
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = c.resume(Unit)
            override fun onError(error: String) = c.resume(Unit)
        })
    }

    private suspend fun statusOf(r: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { c ->
        r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = c.resume(status)
            override fun onError(error: String?) = c.resume(null)
        })
    }

    private suspend fun reloadList() {
        val byArea = allRegions().groupBy { meta(it)?.optLong("area") ?: 0L }.filterKeys { it != 0L }
        _areas.value = byArea.map { (id, regions) ->
            val m = meta(regions.first())!!
            val statuses = regions.mapNotNull { r -> statusOf(r)?.let { meta(r)?.optString("style") to it } }
            val complete = listOf(STYLE_LIGHT, STYLE_DARK).all { st -> statuses.any { it.first == st && it.second.isComplete } }
            OfflineArea(
                id, m.optString("name"), m.optDouble("lat"), m.optDouble("lon"), m.optInt("r"), m.optLong("at"),
                // An unfinished area (the unit switched off mid-download) needs Update to finish it.
                if (complete) AreaStatus.READY else AreaStatus.FAILED,
                progress = if (complete) 1f else 0f, sizeBytes = statuses.sumOf { it.second.completedResourceSize },
                error = if (complete) null else "Download didn't finish",
            )
        }.sortedBy { it.id }
    }

    /** Areas older than a week are downloaded again, but only on Wi-Fi that isn't metered (not a phone hotspot). */
    private suspend fun refreshStale() {
        while (true) {
            if (unmetered()) {
                val now = System.currentTimeMillis()
                _areas.value.filter { it.status == AreaStatus.READY && OfflineMath.isStale(it.savedAtMs, now) }
                    .forEach { download(it.copy(savedAtMs = now), fresh = false) }
            }
            delay(6 * 60 * 60_000L)
        }
    }

    private fun unmetered(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private fun set(a: OfflineArea) {
        _areas.value = _areas.value.map { if (it.id == a.id) a else it }.let { l -> if (l.any { it.id == a.id }) l else l + a }
    }

    companion object {
        @Volatile private var httpReady = false

        /**
         * MapLibre's HTTP client, set up once before any map or download: at most 4 requests at a time to the tile
         * server and a User-Agent naming the app, as OpenFreeMap asks of offline-capable apps.
         */
        fun setUp(context: Context) {
            MapLibre.getInstance(context.applicationContext)
            if (httpReady) return
            synchronized(this) {
                if (httpReady) return
                val ua = "TLauncher/${BuildConfig.VERSION_NAME} (Android head-unit launcher; github.com/iamfercatapang/AHU-TLauncher)"
                val client = okhttp3.OkHttpClient.Builder()
                    .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 4 })
                    .addNetworkInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
                    .build()
                HttpRequestUtil.setOkHttpClient(client)
                httpReady = true
            }
        }
    }
}
