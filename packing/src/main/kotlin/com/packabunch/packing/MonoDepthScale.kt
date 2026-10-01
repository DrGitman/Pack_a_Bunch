package com.packabunch.packing

/**
 * Puts millimetres on MiDaS's depth — HarshdeepJ's scale calibration (their §2.3, equation 2).
 *
 * MiDaS predicts *relative* inverse depth from one photo: bigger means nearer, with no units. They
 * fix the scale with an ID card of known width that the user boxes by hand. Here the references are
 * invisible: points the phone already knows the true distance to — markers placed on the table in
 * the tracked 3D scene, worked out from the card in view. Each gives a pair
 * (MiDaS value, true distance), and the fit is
 *
 *     1 / z = a · r + b
 *
 * Theirs is the single-reference case `D = S / r` (b = 0). MiDaS's output also has an unknown
 * offset, so with several references at different distances both are solved, which is what lets a
 * far shelf and a near cup come out right in the same picture.
 *
 * Robust: fit, drop the worst quarter (a marker that landed on an object instead of the table is
 * exactly such an outlier), fit again.
 */
class MonoDepthScale private constructor(val a: Float, val b: Float) {

    /** Millimetres for a MiDaS value, or 0 where the fit says "infinitely far" or behind. */
    fun mm(r: Float): Int {
        val inv = a * r + b
        if (inv <= 0f) return 0
        val z = 1f / inv
        return if (z > MAX_MM) 0 else z.toInt()
    }

    companion object {
        const val MIN_REFERENCES = 8
        private const val MAX_MM = 10_000f
        private const val TRIM = 0.25f

        /**
         * @param r MiDaS values at the references.
         * @param zMm true distances to them, in millimetres.
         * @return the scale, or null if the references are too few, too alike, or say nearer is farther.
         */
        fun fit(r: FloatArray, zMm: FloatArray): MonoDepthScale? {
            require(r.size == zMm.size)
            var idx = r.indices.filter { zMm[it] > 0f && r[it].isFinite() }
            if (idx.size < MIN_REFERENCES) return null
            var fit = leastSquares(idx, r, zMm) ?: return null
            // Drop the worst-fitting quarter and refit.
            val keep = (idx.size * (1 - TRIM)).toInt().coerceAtLeast(MIN_REFERENCES)
            idx = idx.sortedBy { i -> kotlin.math.abs(fit.first * r[i] + fit.second - 1f / zMm[i]) }.take(keep)
            fit = leastSquares(idx, r, zMm) ?: return null
            // MiDaS: larger means nearer, so 1/z must rise with r.
            if (fit.first <= 0f) return null
            return MonoDepthScale(fit.first, fit.second)
        }

        private fun leastSquares(idx: List<Int>, r: FloatArray, zMm: FloatArray): Pair<Float, Float>? {
            var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
            for (i in idx) {
                val x = r[i].toDouble(); val y = 1.0 / zMm[i]
                sx += x; sy += y; sxx += x * x; sxy += x * y
            }
            val n = idx.size.toDouble()
            val den = n * sxx - sx * sx
            if (kotlin.math.abs(den) < 1e-12) return null
            val a = (n * sxy - sx * sy) / den
            val b = (sy - a * sx) / n
            return a.toFloat() to b.toFloat()
        }
    }
}
