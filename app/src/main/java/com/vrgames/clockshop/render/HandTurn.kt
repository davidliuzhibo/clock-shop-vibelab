package com.vrgames.clockshop.render

import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Quat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Turning things by the hand's own rotation while it pinches (PICO's advice, 2026-11-12: a pinched
 * hand can drag, or turn what it holds the way the hand turns). The drag event carries the hand's
 * orientation; this keeps the last one and tells how far the hand has turned about an axis since.
 */
class HandTurn {
    private var last: Quat? = null

    fun reset() {
        last = null
    }

    /**
     * Degrees the hand turned about [ax],[ay],[az] (unit, in the content's frame, right-handed) since the
     * last call, from its orientation [raw] in the view's global frame. 0 on the first call or when
     * no orientation comes (gaze input without a tracked hand).
     */
    fun twist(content: SpatialViewContent?, raw: Quat?, ax: Double, ay: Double, az: Double): Double {
        if (raw == null || content == null) return 0.0
        val q = runCatching { content.convertRotation(raw, ViewCoordinateSpace.Global, content.localSpatialCoordinateSpace) }.getOrNull() ?: return 0.0
        val n = sqrt((q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w).toDouble())
        if (!(n > 0.5)) return 0.0
        val prev = last
        last = q
        if (prev == null) return 0.0
        return twistDeg(prev, q, ax, ay, az)
    }

    companion object {
        /** The angle (degrees) of [now] relative to [before] about the unit axis, wrapped to ±180. */
        fun twistDeg(before: Quat, now: Quat, ax: Double, ay: Double, az: Double): Double {
            val bx = -before.x.toDouble(); val by = -before.y.toDouble(); val bz = -before.z.toDouble(); val bw = before.w.toDouble()
            val nx = now.x.toDouble(); val ny = now.y.toDouble(); val nz = now.z.toDouble(); val nw = now.w.toDouble()
            // r = now * conj(before)
            val rw = nw * bw - nx * bx - ny * by - nz * bz
            val rx = nw * bx + nx * bw + ny * bz - nz * by
            val ry = nw * by - nx * bz + ny * bw + nz * bx
            val rz = nw * bz + nx * by - ny * bx + nz * bw
            val d = rx * ax + ry * ay + rz * az
            var a = 2 * atan2(d, rw)
            if (a > PI) a -= 2 * PI
            if (a < -PI) a += 2 * PI
            return Math.toDegrees(a)
        }
    }
}
