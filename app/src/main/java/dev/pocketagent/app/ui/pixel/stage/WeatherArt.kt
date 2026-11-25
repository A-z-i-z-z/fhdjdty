package dev.pocketagent.app.ui.pixel.stage

/*
 * Small pure helpers of the weather art. The pictures themselves (cloud strips, fog bands) are drawn by
 * WeatherLayers; this file holds what is plain arithmetic on pixel arrays and the look knob of the clouds.
 */
object WeatherArt {
    /** Number of tones in a cloud (top, mid, shade). The look review may set it to 2. */
    const val CLOUD_TONES = 3

    /** Alpha at which a pixel counts as solid for [topEdges]. */
    const val EDGE_ALPHA = 128

    /**
     * Per column of the image [px] ([w] by [h] ARGB) the first row whose alpha is at least 128, else -1. Used to find
     * the top of a ridge so snow can settle on it. A short [px] counts as transparent where it ends.
     */
    fun topEdges(px: IntArray, w: Int, h: Int): IntArray {
        if (w <= 0) return IntArray(0)
        val out = IntArray(w) { -1 }
        if (h <= 0) return out
        for (x in 0 until w) {
            var y = 0
            while (y < h) {
                val i = y * w + x
                if (i >= px.size) break
                if ((px[i] ushr 24) >= EDGE_ALPHA) {
                    out[x] = y
                    break
                }
                y++
            }
        }
        return out
    }
}
