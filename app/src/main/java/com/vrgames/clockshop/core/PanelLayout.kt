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
    data class Placed(val at: Vec3, val pitch: Double, val yaw: Double, val textW: Double, val textH: Double, val rim: Double = RIM) {
        val frameW get() = textW + 2 * rim
        val frameH get() = textH + 2 * rim

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
    /** v0.6.1: the dock (room, parts, a status line, 提示 / 线索簿 / 菜单), small and wide (content.DOCK_W). */
    val HUD = Placed(Vec3(-0.38, 0.74, -0.27), -45.0, 55.0, 0.34, 0.12, 0.0)
    /** The clock and dial controls (v0.6.1: 6 cm wider for the − and + of each dial). */
    val TOOL = Placed(Vec3(0.55, 0.93, -0.5), -22.0, -32.0, 0.50, 0.24)

    // ---- v0.6.0 (PICO 1125: 「游戏内的UI会挡住场景游玩的内容」) ----
    //
    // Measured on the emulator (2026-11-25, every room's pinch boxes logged: docs/targets_20261125.txt): one fixed place
    // for the panels cannot work — 0.5.2's note straight ahead hid the study's newspaper, the shop's drawer and letter, the
    // tower's lever and key; the clock/dial panel low at the right hid the workshop safe's three dials (wholly, while the
    // player was turning them) and the attic's five photos. The only spots that hide nothing in every room are 55–80°
    // to the left, too far to read.
    // So each room has its own: the note straight ahead a little low where nothing is, the room panel where that room
    // has room for it; and the clock/dial panel opens next to the thing being worked on, never over anything. Searched
    // over every room's boxes (each box's 27 corner/edge/middle points, 3% margin), the tray in front kept clear.

    /** The distance all three stand at, from the eyes. */
    const val D = 0.65

    /** A panel [az] degrees right and [el] degrees up from the eyes, at [D], facing them. */
    fun facing(az: Double, el: Double, w: Double, h: Double, rim: Double = RIM): Placed {
        val a = Math.toRadians(az); val e = Math.toRadians(el)
        return Placed(Vec3(D * cos(e) * sin(a), EYE.y + D * sin(e), -D * cos(e) * cos(a)), el, -az, w, h, rim)
    }

    /**
     * (az, el) of the note and the dock in each room. v0.6.1 (device 2026-11-25: the room panel up at the right hid the
     * look to the right): the dock low at the left, 55° left and 45° down (the study, whose note is
     * lower, 60° left): clear of every room's things and of each room's note. Searched over every room's pinch boxes as before.
     */
    private val ROOM_SPOTS = mapOf(
        Shop.Room.FRONT to ((0.0 to -35.0) to (-55.0 to -45.0)),
        Shop.Room.WORKSHOP to ((0.0 to -30.0) to (-55.0 to -45.0)),
        Shop.Room.ATTIC to ((0.0 to -20.0) to (-55.0 to -45.0)),
        Shop.Room.CELLAR to ((-5.0 to -30.0) to (-55.0 to -45.0)),
        Shop.Room.TOWER to ((0.0 to -35.0) to (-55.0 to -45.0)),
        Shop.Room.STUDY to ((0.0 to -40.0) to (-60.0 to -45.0)),
    )

    /**
     * v0.6.2: the music box's numbers (content.TunePanel, 26 × 15 cm, no frame), in the workshop beside the spindle with
     * the gears, to its right and a little above: clear of the gears, the wall clock behind and the things on the bench.
     */
    val TUNE: Placed get() {
        val at = Vec3(0.42, 1.48, -1.25)
        val az = Math.toDegrees(kotlin.math.atan2(at.x, -at.z)); val el = Math.toDegrees(kotlin.math.atan2(at.y - EYE.y, kotlin.math.hypot(at.x, at.z)))
        return Placed(at, el, -az, 0.26, 0.15, 0.0)
    }

    /** v0.6.1: the unfolded menu, over the dock at the left (it stands only while open, over whatever is there). */
    val MENU: Placed get() = facing(-40.0, -6.0, 0.24, 0.34)

    /** (az, el) of the clock/dial panel for each tool: beside the thing being worked on. */
    private val TOOL_SPOTS = mapOf(
        "clock:big" to (-35.0 to 10.0), "clock:attic" to (-80.0 to -10.0), "clock:tower" to (-45.0 to 35.0), "clock:wall" to (-35.0 to 20.0),
        "safe" to (45.0 to 10.0), "rings" to (-60.0 to 0.0), "pendulum" to (-45.0 to 35.0), "calendar" to (-40.0 to 25.0),
    )

    fun noteFor(r: Shop.Room): Placed { val (az, el) = ROOM_SPOTS.getValue(r).first; return facing(az, el, NOTE_W, NOTE_H) }
    /** The dock has no wooden frame (a dark rounded panel, content.HudPanel): its size is its own. */
    fun hudFor(r: Shop.Room): Placed { val (az, el) = ROOM_SPOTS.getValue(r).second; return facing(az, el, HUD.textW, HUD.textH, 0.0) }
    /** The clock/dial panel for [tool] (in its room); an unknown tool takes the room panel's place. */
    fun toolFor(tool: String, r: Shop.Room): Placed {
        val (az, el) = TOOL_SPOTS[tool] ?: ROOM_SPOTS.getValue(r).second
        return facing(az, el, TOOL.textW, TOOL.textH)
    }
    val TOOLS: Set<String> get() = TOOL_SPOTS.keys

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
