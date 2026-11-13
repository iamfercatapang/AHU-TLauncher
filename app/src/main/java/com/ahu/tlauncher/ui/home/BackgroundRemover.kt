package com.ahu.tlauncher.ui.home

/**
 * Makes a plain white/very-light studio background transparent, so product-style car photos
 * sit cleanly on the dark (or light) dashboard. Only near-neutral bright pixels that are
 * connected to the image border are removed, so a white car body inside the outline is kept.
 * Pure ARGB int math: unit-testable on the JVM.
 */
object BackgroundRemover {
    private const val MIN_BRIGHT = 234
    private const val MAX_CHROMA = 16

    private fun isBg(p: Int): Boolean {
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        return mn >= MIN_BRIGHT && mx - mn <= MAX_CHROMA
    }

    /** True when the image already has transparent corners (e.g. a cut-out PNG). */
    fun alreadyTransparent(px: IntArray, w: Int, h: Int): Boolean =
        listOf(0, w - 1, (h - 1) * w, h * w - 1).any { (px[it] ushr 24) < 250 }

    fun process(px: IntArray, w: Int, h: Int): IntArray {
        if (w == 0 || h == 0 || alreadyTransparent(px, w, h)) return px
        val out = px.copyOf()
        val removed = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun seed(i: Int) { if (!removed[i] && isBg(px[i])) { removed[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        if (tail == 0) return px
        for (i in out.indices) if (removed[i]) out[i] = 0
        // Feather: light pixels touching the removed area become semi-transparent (anti-aliased edge).
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (removed[i]) continue
            val touches = (x > 0 && removed[i - 1]) || (x < w - 1 && removed[i + 1]) ||
                (y > 0 && removed[i - w]) || (y < h - 1 && removed[i + w])
            if (!touches) continue
            val p = px[i]
            val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
            if (lum > 200) {
                val a = ((255 - lum) * 255 / 55).coerceIn(90, 255)
                out[i] = (a shl 24) or (p and 0x00FFFFFF)
            }
        }
        return out
    }
}
