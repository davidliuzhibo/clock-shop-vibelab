package com.vrgames.clockshop.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6.0 (PICO 1125: the UI hid what was to be played): with every room's pinch boxes as measured on the emulator
 * (docs/targets_20261125.txt, logged by RoomView.logTargets), the note and the room panel of each room, and the
 * clock/dial panel of each tool, hide none of them, nor the tray in front.
 */
class PanelPlacesTest {
    private class Box(val room: String, val id: String, val c: Vec3, val w: Double, val h: Double, val d: Double) {
        val points: List<Vec3> get() = buildList {
            for (a in -1..1) for (b in -1..1) for (e in -1..1) add(Vec3(c.x + a * w / 2, c.y + b * h / 2, c.z + e * d / 2))
        }
    }

    private val boxes = requireNotNull(javaClass.getResourceAsStream("/targets_20261125.txt")) { "Missing panel-layout test fixture" }.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }.map { l ->
        val p = l.trim().split(Regex("\\s+"))
        Box(p[0], p[1], Vec3(p[2].toDouble(), p[3].toDouble(), p[4].toDouble()), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
    }

    /** Whether [panel] stands between the eyes and [p] (with a 3% margin round the frame). */
    private fun hides(panel: PanelLayout.Placed, p: Vec3): Boolean {
        val eye = PanelLayout.EYE
        val o = panel.point(0.0, 0.0)
        val ex = panel.point(0.5, 0.0) - o
        val ey = panel.point(0.0, 0.5) - o
        val n = ex.cross(ey)
        val dir = p - eye
        val den = n.dot(dir)
        if (kotlin.math.abs(den) < 1e-9) return false
        val t = n.dot(o - eye) / den
        if (t <= 0 || t >= 1) return false
        val hit = eye + dir * t - o
        val u = hit.dot(ex) / ex.dot(ex) * 0.5
        val w = hit.dot(ey) / ey.dot(ey) * 0.5
        return kotlin.math.abs(u) <= 0.53 && kotlin.math.abs(w) <= 0.53
    }

    private fun hidden(panel: PanelLayout.Placed, room: String) =
        boxes.filter { it.room == room }.filter { b -> b.points.any { hides(panel, it) } }.map { it.id }

    @Test
    fun theDataCoversEveryRoom() {
        assertTrue(boxes.map { it.room }.toSet() == Shop.Room.entries.map { it.name }.toSet())
        assertTrue(boxes.size >= 50)
    }

    @Test
    fun theNoteAndTheRoomPanelHideNothingInTheirRoom() {
        for (r in Shop.Room.entries) {
            assertTrue("$r note hides ${hidden(PanelLayout.noteFor(r), r.name)}", hidden(PanelLayout.noteFor(r), r.name).isEmpty())
            assertTrue("$r room panel hides ${hidden(PanelLayout.hudFor(r), r.name)}", hidden(PanelLayout.hudFor(r), r.name).isEmpty())
            assertTrue("$r note and room panel apart", PanelLayout.gapDegrees(PanelLayout.noteFor(r), PanelLayout.hudFor(r)) >= 3.0)
        }
    }

    @Test
    fun theClockAndDialPanelOpensBesideItsThingHidingNothing() {
        val roomOf = mapOf("clock:big" to "FRONT", "clock:attic" to "ATTIC", "clock:tower" to "TOWER", "clock:wall" to "WORKSHOP",
            "safe" to "WORKSHOP", "rings" to "CELLAR", "pendulum" to "TOWER", "calendar" to "STUDY")
        for (t in PanelLayout.TOOLS) {
            val room = Shop.Room.valueOf(roomOf.getValue(t))
            val p = PanelLayout.toolFor(t, room)
            assertTrue("$t panel hides ${hidden(p, room.name)}", hidden(p, room.name).isEmpty())
            assertTrue("$t panel and the note apart", PanelLayout.gapDegrees(p, PanelLayout.noteFor(room)) >= 3.0)
            // v0.6.1: the dock stays while a tool is open: the two never touch.
            assertTrue("$t panel and the dock apart", PanelLayout.gapDegrees(p, PanelLayout.hudFor(room)) >= 3.0)
        }
    }

    /** v0.6.2: the music box's numbers hide nothing in the workshop (the gears, the wall clock, the bench). */
    @Test
    fun theTunePlaqueHidesNothing() {
        assertTrue("tune hides ${hidden(PanelLayout.TUNE, "WORKSHOP")}", hidden(PanelLayout.TUNE, "WORKSHOP").isEmpty())
    }

    /** v0.6.1 (device 2026-11-25): the dock is low at the left in every room, never to the right or up. */
    @Test
    fun theDockIsLowAtTheLeft() {
        for (r in Shop.Room.entries) {
            val d = PanelLayout.hudFor(r)
            assertTrue("$r dock x ${d.at.x}", d.at.x < -0.2)
            assertTrue("$r dock y ${d.at.y}", d.at.y < PanelLayout.EYE.y - 0.3)
        }
    }

    @Test
    fun nothingCoversTheTray() {
        for (r in Shop.Room.entries) for (p in listOf(PanelLayout.noteFor(r), PanelLayout.hudFor(r))) for (x in listOf(-0.26, -0.13, 0.0, 0.13, 0.26)) {
            assertTrue("$r ${p.at} over the tray at $x", !hides(p, Vec3(x, 0.86, -0.46)))
        }
    }
}
