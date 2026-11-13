package com.ahu.tlauncher.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ahu.tlauncher.R
import com.ahu.tlauncher.data.CarView
import com.ahu.tlauncher.ui.components.pressable
import com.ahu.tlauncher.ui.theme.T
import com.ahu.tlauncher.ui.theme.TMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a car view's picture comes from. */
sealed interface CarSource {
    data class Custom(val uri: String) : CarSource
    data class Bundled(val resId: Int) : CarSource
    data object Illustration : CarSource
}

object CarImages {
    private val cache = LruCache<String, ImageBitmap>(6)

    /** Optional bundled photos: app/src/main/res/drawable-nodpi/car_side.png, car_front.png, car_rear.png */
    fun bundledRes(ctx: Context, view: CarView): Int =
        ctx.resources.getIdentifier("car_" + view.name.lowercase(), "drawable", ctx.packageName)

    fun source(ctx: Context, view: CarView, custom: Map<CarView, String>): CarSource? {
        custom[view]?.let { return CarSource.Custom(it) }
        val res = bundledRes(ctx, view)
        if (res != 0) return CarSource.Bundled(res)
        return if (view == CarView.SIDE) CarSource.Illustration else null
    }

    /** Views that have something to show, in display order (side always present). */
    fun available(ctx: Context, custom: Map<CarView, String>): List<Pair<CarView, CarSource>> =
        CarView.entries.mapNotNull { v -> source(ctx, v, custom)?.let { v to it } }

    suspend fun load(ctx: Context, src: CarSource): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = when (src) { is CarSource.Custom -> src.uri; is CarSource.Bundled -> "res:${src.resId}"; else -> return@withContext null }
        cache.get(key)?.let { return@withContext it }
        val bmp = runCatching {
            when (src) {
                is CarSource.Custom -> decodeUri(ctx, Uri.parse(src.uri))
                is CarSource.Bundled -> decodeRes(ctx, src.resId)
                else -> null
            }
        }.getOrNull() ?: return@withContext null
        val cleaned = removeBackground(bmp).asImageBitmap()
        cache.put(key, cleaned)
        cleaned
    }

    private const val MAX_PX = 900

    private fun sample(w: Int, h: Int): Int { var s = 1; while (w / (s * 2) >= MAX_PX || h / (s * 2) >= MAX_PX) s *= 2; return s }

    private fun decodeUri(ctx: Context, uri: Uri): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight) }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun decodeRes(ctx: Context, id: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, id, o)
        return BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight) })
    }

    private fun removeBackground(src: Bitmap): Bitmap {
        val b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val out = BackgroundRemover.process(px, w, h)
        if (out === px) return b
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
