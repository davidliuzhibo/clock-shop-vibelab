package com.vrgames.clockshop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5.2 (device 2026-11-20: the note and the clock/dial panel overlapped): the frames as seen from the seat. */
class PanelLayoutTest {
    @Test
    fun theNoteStaysClearOfTheRoomAndDialPanels() {
        val hud = PanelLayout.gapDegrees(PanelLayout.NOTE, PanelLayout.HUD)
        val tool = PanelLayout.gapDegrees(PanelLayout.NOTE, PanelLayout.TOOL)
        assertTrue("note-hud $hud deg", hud >= 4.0)
        assertTrue("note-tool $tool deg", tool >= 4.0)
        // Neither panel lies wholly over the other.
        assertFalse(PanelLayout.covers(PanelLayout.TOOL, PanelLayout.NOTE.point(0.0, 0.0)))
        assertFalse(PanelLayout.covers(PanelLayout.NOTE, PanelLayout.TOOL.point(0.0, 0.0)))
    }

    @Test
    fun theOldPlacesDidTouch() {
        // 0.5.1: the note 0.50 m wide straight ahead, the dial panel at x 0.48 - the check has to catch that.
        val oldNote = PanelLayout.Placed(Vec3(0.0, 1.0, -0.66), -12.0, 0.0, 0.50, 0.13)
        val oldTool = PanelLayout.Placed(Vec3(0.48, 0.93, -0.5), -22.0, -32.0, 0.44, 0.24)
        assertTrue(PanelLayout.gapDegrees(oldNote, oldTool) < 1.0)
    }

    @Test
    fun theFramesMatchTheText() {
        // The rebuilt panel_note.glb: 0.44 x 0.13 inside a 4 cm rim.
        assertEquals(0.52, PanelLayout.NOTE.frameW, 1e-9)
        assertEquals(0.21, PanelLayout.NOTE.frameH, 1e-9)
        // A pitched panel's centre stays where it was put; its top leans back (away from the seat).
        val top = PanelLayout.NOTE.point(0.0, 0.5)
        assertTrue(top.z < PanelLayout.NOTE.at.z)
        assertTrue(PanelLayout.covers(PanelLayout.NOTE, PanelLayout.NOTE.point(0.1, 0.1)))
    }
}
