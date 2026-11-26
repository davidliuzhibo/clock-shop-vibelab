package com.vrgames.clockshop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.math.max

/**
 * Placement is locked (2026-11-23: tables and scenes moved in new versions without being asked). Where the shop's rooms,
 * furniture, hand tray and panels sit relative to the player's eyes must stay as in 0.5.2, the last version passed on
 * the device (2026-11-21; source copy Pico_Vibe_Lab/提交包_20261121/ClockShop). The numbers are read from the current
 * source and held to 0.5.2's, and nothing solid in front comes within 0.30 m of the eyes (horizontally).
 *
 * Room frame: the seat (the eyes' x/z) at the origin, the floor [SEATED_EYE] under the measured eyes, -z ahead, turned
 * once to the settled heading.
 */
class PlacementLockTest {
    private val src = File("src/main/java/com/vrgames/clockshop")
    private val stage = File(src, "content/ShopStage.kt").readText()
    private val room = File(src, "render/RoomView.kt").readText()

    private fun const(text: String, name: String): Double =
        Regex("const val $name = (-?[0-9.]+)f?").find(text)?.groupValues?.get(1)?.toDouble() ?: error("$name not found")

    /** A piece of furniture: centre x/z on the floor, footprint width (x) and depth (z), and the code that puts it there. */
    data class Piece(val name: String, val x: Double, val z: Double, val w: Double, val d: Double, val code: String) {
        /** Across the floor from the eyes (the origin) to the nearest point of the footprint. */
        val gap get() = hypot(max(kotlin.math.abs(x) - w / 2, 0.0), max(kotlin.math.abs(z) - d / 2, 0.0))
    }

    companion object {
        /** 0.5.2 (device-approved 2026-11-21): the eyes over the room's floor. */
        const val SEATED_EYE = 1.2
        /** The hand tray's items, room frame (turned about the seat after the head). */
        const val TRAY_Y = 0.86
        const val TRAY_Z = -0.46
        const val TRAY_ITEM_DEPTH = 0.10
        /** The furniture standing in front of the seat; footprints from the fitted sizes or the models' bounds × scale. */
        val PIECES = listOf(
            Piece("front counter", 0.75, -1.45, 1.66, 0.56, """fitted("counter", 1.66f, 1.04f, 0.56f)?.apply { setPos(Vector3(0.75f, 0f, -1.45f))"""),
            Piece("front side counter", -1.4, -1.6, 1.0, 0.5, """fitted("counter", 1.0f, 0.9f, 0.5f)?.apply { setPos(Vector3(-1.4f, 0f, -1.6f))"""),
            Piece("grandfather clock", 0.0, -2.6, 0.53, 0.51, """it.setPos(Vector3(0f, 0f, -2.6f))"""),
            Piece("workshop bench", 0.0, -1.25, 2.06, 0.86, """fitted("bench", 2.06f, 0.94f, 0.86f)?.apply { setPos(Vector3(0f, 0f, -1.25f))"""),
            Piece("attic trunk", 1.25, -1.1, 0.9, 0.5, """fitted("trunk", 0.9f, 0.45f, 0.5f, yaw = -90f)?.apply { setPos(Vector3(1.25f, 0f, -1.1f))"""),
            Piece("cellar astro clock", 0.0, -2.55, 1.84, 1.36, """piece("astro", 2.5f) ?: box(Vector3(1.2f, 1.7f, 0.4f), Vector3(0f, 0.85f, 0f), woodDark)).apply { setPos(Vector3(0f, 0f, -2.55f))"""),
            Piece("tower movement", 0.0, -2.15, 0.9, 0.6, """piece("tower_movement", 1.25f) ?: box(Vector3(1.6f, 1.2f, 0.7f), Vector3(0f, 0.6f, 0f), dark)).apply { setPos(Vector3(0f, 0f, -2.15f))"""),
            Piece("tower shelf", 0.0, -1.1, 1.6, 0.5, """box(Vector3(1.6f, 0.06f, 0.5f), Vector3(0f, 0.86f, -1.1f), woodDark)"""),
            Piece("study writing desk", 0.0, -1.35, 1.26, 0.74, """piece("writing_desk", 0.8f) ?: box(Vector3(1.3f, 0.76f, 0.7f), Vector3(0f, 0.38f, 0f), woodMid)).apply { setPos(Vector3(0f, 0f, -1.35f))"""),
        )
        /** The panels: x, y, z (room frame), pitch, yaw. */
        val CARD = doubleArrayOf(0.0, 1.2, -0.85, 0.0, 0.0)
        val NOTE = doubleArrayOf(-0.05, 1.0, -0.66, -12.0, 0.0)
        // v0.6.1 (device 2026-11-25, asked by the user): the dock moved low to the left; the tool panel 4 cm right, 6 cm wider.
        val HUD = doubleArrayOf(-0.38, 0.74, -0.27, -45.0, 55.0)
        val TOOL = doubleArrayOf(0.55, 0.93, -0.5, -22.0, -32.0)
        const val BODY_CLEAR = 0.30
    }

    private fun placed(p: PanelLayout.Placed) = doubleArrayOf(p.at.x, p.at.y, p.at.z, p.pitch, p.yaw)

    @Test
    fun roomSitsUnderTheEyesAs052() {
        assertEquals(SEATED_EYE, const(stage, "SEATED_EYE"), 1e-3)
        assertTrue("floor = measured eyes − SEATED_EYE", stage.contains("floorY = eyes.value!! - SEATED_EYE"))
        assertTrue("room at the eyes' x/z", stage.contains("seatAt = Vector3(eye.x, 0f, eye.z)") && stage.contains("it.setPos(Vector3(seatAt.x, floorY, seatAt.z))"))
        assertTrue("turned once, yaw only", stage.contains("it.transform().eulerAngles = EulerAngles(0f, facing, 0f)"))
        assertEquals(SEATED_EYE, PanelLayout.EYE.y, 1e-3)
        for (eye in listOf(1.65f, 1.25f)) {
            val e = EyeHeight()
            repeat(EyeHeight.NEED + 2) { e.feed(0.1f, eye, -0.2f, 0.05) }
            assertEquals(eye - 1.2, e.value!! - SEATED_EYE, 1e-3)
        }
    }

    @Test
    fun furnitureTrayAndPanelsStayWhere052PutThem() {
        for (p in PIECES) assertTrue("${p.name} moved: expected `${p.code}`", room.contains(p.code))
        assertEquals(TRAY_Y, const(room, "TRAY_Y"), 1e-3)
        assertEquals(TRAY_Z, const(room, "TRAY_Z"), 1e-3)
        assertTrue("tray items 0.13 m apart, centred", room.contains("e.setPos(Vector3((-0.065 * (n - 1) + k * 0.13).toFloat(), TRAY_Y, TRAY_Z))"))
        assertTrue(stage.contains("put(PANEL_CARD, Vector3(0f, 1.2f, -0.85f), 0f)") && stage.contains("put(PANEL_RESULT, Vector3(0f, 1.2f, -0.85f), 0f)"))
        for ((want, got) in listOf(NOTE to PanelLayout.NOTE, HUD to PanelLayout.HUD, TOOL to PanelLayout.TOOL)) {
            val g = placed(got)
            for (i in want.indices) assertEquals(want[i], g[i], if (i >= 3) 0.1 else 1e-3)
        }
        // v0.6.1: the menu is the dock's button; unfolded it stands over the dock (core.PanelLayout.MENU).
        assertTrue(stage.contains("putAt(PANEL_MENU, MENU_OPEN)"))
    }

    @Test
    fun nothingSolidWithin30cmOfTheEyes() {
        val trayEdge = -TRAY_Z - TRAY_ITEM_DEPTH / 2
        assertTrue("tray items $trayEdge m", trayEdge >= BODY_CLEAR)
        for (p in PIECES) assertTrue("${p.name} ${p.gap} m", p.gap >= BODY_CLEAR)
        for (v in listOf(CARD, NOTE, HUD, TOOL)) assertTrue(hypot(v[0], v[2]) >= BODY_CLEAR)
        val near = PIECES.minByOrNull { it.gap }!!
        println("placement: floor ${SEATED_EYE} m under the eyes; tray items ${-TRAY_Z} m ahead ${"%.2f".format(SEATED_EYE - TRAY_Y)} m under the eyes (near edge ${"%.2f".format(trayEdge)} m); nearest furniture ${near.name} ${"%.3f".format(near.gap)} m; card ${-CARD[2]} m")
    }
}
