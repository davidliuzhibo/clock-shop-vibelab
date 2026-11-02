package com.vrgames.clockshop.core

import com.vrgames.clockshop.core.Shop.Event
import com.vrgames.clockshop.core.Shop.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {
    private fun List<Event>.refused() = filterIsInstance<Event.Refused>().firstOrNull()?.why

    @Test
    fun theWholeShopCanBeSolved() {
        val s = Shop()
        for (step in Walkthrough.steps) {
            val out = Walkthrough.run(s, step)
            assertEquals("$step: ${out.refused()}", null, out.refused())
        }
        assertTrue(s.finished)
        assertEquals(setOf(1, 2, 3, 4), s.parts)
        assertEquals(Shop.Room.CELLAR, s.room)
    }

    @Test
    fun theMantelWaitsForThreeOClock() {
        val s = Shop()
        assertTrue(s.open("mantel").refused()!!.contains("三"))
        s.setClock("big", 3, 0)
        assertTrue(s.sunlit)
        assertEquals(null, s.open("mantel").refused())
        assertTrue(s.take(Item.KEY_DRAWER).none { it is Event.Refused })
    }

    @Test
    fun gearsGoInBySize() {
        val s = Shop()
        Walkthrough.steps.takeWhile { it != "go workshop" }.forEach { Walkthrough.run(s, it) }
        Walkthrough.run(s, "go workshop")
        s.take(Item.GEAR_L)
        s.take(Item.GEAR_M)
        assertTrue(s.use(Item.GEAR_M, "gear1").refused()!!.contains("太小"))
        assertTrue(s.use(Item.GEAR_L, "gear2").refused()!!.contains("太大"))
        assertTrue(s.take(Item.GEAR_S).refused()!!.contains("拿不到"))
    }

    @Test
    fun theMusicBoxSingsOnlyAtNight() {
        val s = Shop()
        Walkthrough.steps.takeWhile { it != "clock wall 22" }.forEach { Walkthrough.run(s, it) }
        assertTrue(s.musicBoxBuilt)
        assertFalse(s.musicPlays)
        Walkthrough.run(s, "clock wall 22")
        assertTrue(s.musicPlays)
        assertTrue(s.clues.any { it.contains("3、1、5") })
    }

    @Test
    fun photosInTheWrongOrderSaySo() {
        val s = Shop()
        Walkthrough.steps.takeWhile { !it.startsWith("hang") }.forEach { Walkthrough.run(s, it) }
        val photos = Shop.PHOTO_ORDER.reversed()
        var last = emptyList<Event>()
        photos.forEachIndexed { i, p -> last = s.use(p, "frame$i") }
        assertTrue(last.filterIsInstance<Event.Said>().single().text.contains("顺序好像不对"))
        assertTrue(s.unhang(0).single() is Event.Took)
    }

    @Test
    fun hintsFollowTheProgress() {
        val s = Shop()
        assertTrue(s.hint().contains("三点"))
        s.setClock("big", 15, 0)
        s.open("mantel")
        assertTrue(s.hint().contains("钥匙"))
    }

    /** The game saves every move that was not refused and replays them: the replay must land in the same state. */
    @Test
    fun aSavedGameReplaysToTheSameState() {
        val played = Walkthrough.steps.take(24) + listOf(
            "hang PHOTO_2 frame0", "unhang 0", "hang PHOTO_1 frame0", "clock attic 3 45", "dial safe1 7",
        )
        val s = Shop()
        val saved = ArrayList<String>()
        for (m in played) {
            val out = Walkthrough.run(s, m)
            if (out.none { it is Event.Refused }) saved += m
        }
        val r = Shop()
        for (m in saved) Walkthrough.run(r, m)
        assertEquals(s.room, r.room)
        assertEquals(s.tray, r.tray)
        assertEquals(s.parts, r.parts)
        assertEquals(s.frames, r.frames)
        assertEquals(s.clocks, r.clocks)
        assertEquals(s.opened, r.opened)
        assertEquals(s.clues, r.clues)
        assertEquals(s.dials, r.dials)
        assertEquals(3 to 45, r.clocks["attic"])
        assertEquals(Item.PHOTO_1, r.frames[0])
    }
}
