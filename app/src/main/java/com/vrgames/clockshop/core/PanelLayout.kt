package com.vrgames.clockshop.core

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the panels stand while playing (room frame: the seated eye at [EYE], -z ahead), with the size of their
 * redwood frames (assets/models/panel_*.glb: the text area plus a 4 cm rim). The stage places them from here, and
 * the tests check from here that they never overlap as seen from the seat.
 *
 * v0.5.2 (device 2026-11-20: the note and the clock/dial panel overlapped by about 1.6 degrees): the note is 6 cm
 * narrower (0.50 to 0.44 m) and 5 cm further left, the clock/dial panel 3 cm further right.
 */
object PanelLayout {
    /** A panel placed in the room: centre, pitch then yaw (degrees, as the SDK's EulerAngles), and its framed size. */
    data class Placed(val at: Vec3, val pitch: Double, val yaw: Double, val textW: Double, val textH: Double) {
        val frameW get() = textW + 2 * RIM
        val frameH get() = textH + 2 * RIM

        /** A point of the frame's face, [u] and [w] from -0.5 to 0.5 across and up. */
        fun point(u: Double, w: Double): Vec3 {
            // EulerAngles(pitch, yaw, 0).toQuat() = Ry * Rx: pitched about the panel's own x, then turned about y.
            val x0 = u * frameW
            val y0 = w * frameH
            val z0 = FRAME_Z
            val p = Math.toRadians(pitch)
            val y1 = y0 * cos(p) - z0 * sin(p)
            val z1 = y0 * sin(p) + z0 * cos(p)
            val q = Math.toRadians(yaw)
            val x2 = x0 * cos(q) + z1 * sin(q)
            val z2 = -x0 * sin(q) + z1 * cos(q)
            return Vec3(at.x + x2, at.y + y1, at.z + z2)
        }

        /** The frame's outline, [n] points to a side. */
        fun outline(n: Int = 120): List<Vec3> = buildList {
            val corners = listOf(-0.5 to -0.5, 0.5 to -0.5, 0.5 to 0.5, -0.5 to 0.5)
            for (k in 0 until 4) {
                val (u0, w0) = corners[k]
                val (u1, w1) = corners[(k + 1) % 4]
                for (i in 0 until n) {
                    val t = i.toDouble() / n
                    add(point(u0 + (u1 - u0) * t, w0 + (w1 - w0) * t))
                }
            }
        }
    }

    /** The frames' rim, each side (build_panel.py, rim 0.04). */
    const val RIM = 0.04
    /** The frame sits 6 mm behind the panel (ShopStage.attach). */
    const val FRAME_Z = -0.006
    /** The seated eye over the room's floor (the room is placed from the measured eyes, so this is where they are). */
    val EYE = Vec3(0.0, 1.2, 0.0)

    const val NOTE_W = 0.44
    const val NOTE_H = 0.13
    val NOTE = Placed(Vec3(-0.05, 1.0, -0.66), -12.0, 0.0, NOTE_W, NOTE_H)
    /** The room panel (room, parts, 提示, 线索簿). */
    val HUD = Placed(Vec3(0.50, 0.9, -0.5), -22.0, -32.0, 0.34, 0.27)
    /** The clock and dial controls, in the room panel's place. */
    val TOOL = Placed(Vec3(0.51, 0.93, -0.5), -22.0, -32.0, 0.44, 0.24)

    /** The smallest angle, seen from [eye], between the outlines of [a] and [b] (0 when they touch or cross). */
    fun gapDegrees(a: Placed, b: Placed, eye: Vec3 = EYE): Double {
        val ra = a.outline().map { (it - eye).normalized() }
        val rb = b.outline().map { (it - eye).normalized() }
        var most = -1.0
        for (p in ra) for (q in rb) most = maxOf(most, p.dot(q))
        return Math.toDegrees(acos(most.coerceIn(-1.0, 1.0)))
    }

    /** Whether the direction to [p] from [eye] falls inside [b]'s frame (one panel wholly over another). */
    fun covers(b: Placed, p: Vec3, eye: Vec3 = EYE): Boolean {
        // Where the ray from the eye through p meets b's plane, in b's own u, w.
        val o = b.point(0.0, 0.0)
        val ex = b.point(0.5, 0.0) - o
        val ey = b.point(0.0, 0.5) - o
        val n = ex.cross(ey)
        val dir = p - eye
        val den = n.dot(dir)
        if (kotlin.math.abs(den) < 1e-9) return false
        val t = n.dot(o - eye) / den
        if (t <= 0) return false
        val hit = eye + dir * t - o
        val u = hit.dot(ex) / ex.dot(ex) * 0.5
        val w = hit.dot(ey) / ey.dot(ey) * 0.5
        return kotlin.math.abs(u) <= 0.5 && kotlin.math.abs(w) <= 0.5
    }

    /** One line for the debug log: the gaps from the seat. */
    fun report(): String = "layout note ${"%.2f".format(NOTE.frameW)}x${"%.2f".format(NOTE.frameH)} at ${NOTE.at}: " +
        "note-hud ${"%.1f".format(gapDegrees(NOTE, HUD))} deg, note-tool ${"%.1f".format(gapDegrees(NOTE, TOOL))} deg"
}
