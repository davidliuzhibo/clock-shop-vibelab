package com.vrgames.clockshop.core

/**
 * The player's eye height, measured from the headset the same way in every game (2026-11-19).
 *
 * Why this exists: heights broke again and again after unrelated changes (微缩奇案 0.8/0.9 near the floor, 引力弹弓
 * 2.3 30–40 cm low on a first install). Each game had its own copy of the placement: some put the scene at a fixed
 * height over the room origin (wrong whenever the origin is not the floor), some trusted the first head pose
 * (on the device it can still be all zeros right after tracking starts), some reused a height saved in another
 * session. The emulator hides all of this: its eye is always at 1.65 m from the first frame.
 *
 * Rules:
 * - feed it every head sample (null while there is none); it ignores samples that cannot be an eye: all-zero
 *   positions and heights outside [MIN_Y]..[MAX_Y];
 * - it settles on the median of [NEED] valid samples in a row that agree within [SPREAD];
 * - if nothing settles within [GIVE_UP_S] it answers [FALLBACK] (a seated eye) and keeps listening for
 *   [LATE_S] more seconds: a real reading then corrects the height once ([settled] fires a second time);
 * - nothing is saved between sessions except the player's own up/down adjustment, kept elsewhere.
 */
class EyeHeight {
    /** The eye height in use (null until settled or given up). */
    var value: Float? = null
        private set
    /** True while [value] is the fallback, not a measurement. */
    var guessed = false
        private set
    /** False once the height is final: measured, or given up for good. */
    val listening: Boolean get() = !done
    private val run = ArrayList<Float>()
    private var waited = 0.0
    private var done = false

    /**
     * One head sample ([x], [y], [z] in the scene's frame, or nulls when the headset has none) after [dt] seconds.
     * Returns true when [value] has just been set or corrected: place the scene then.
     */
    fun feed(x: Float?, y: Float?, z: Float?, dt: Double): Boolean {
        if (done) return false
        waited += dt
        if (valid(x, y, z)) {
            run += y!!
            val lo = run.minOrNull()!!; val hi = run.maxOrNull()!!
            if (hi - lo > SPREAD) { run.clear(); run += y }
            if (run.size >= NEED) {
                val m = run.sorted()[run.size / 2]
                done = true
                val changed = value == null || guessed
                value = m; guessed = false
                return changed
            }
        } else run.clear()
        if (value == null && waited >= GIVE_UP_S) { value = FALLBACK; guessed = true; return true }
        if (guessed && waited >= GIVE_UP_S + LATE_S) done = true
        return false
    }

    companion object {
        const val MIN_Y = 0.5f
        const val MAX_Y = 2.3f
        const val NEED = 6
        const val SPREAD = 0.04f
        const val GIVE_UP_S = 4.0
        const val LATE_S = 12.0
        /** A seated adult's eye over the floor. */
        const val FALLBACK = 1.2f

        fun valid(x: Float?, y: Float?, z: Float?): Boolean {
            if (x == null || y == null || z == null) return false
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
            if (kotlin.math.abs(x) + kotlin.math.abs(y) + kotlin.math.abs(z) < 1e-4f) return false
            return y in MIN_Y..MAX_Y
        }
    }
}
