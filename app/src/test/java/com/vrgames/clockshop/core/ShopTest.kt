package com.vrgames.clockshop.core

import com.vrgames.clockshop.core.Shop.Event
import com.vrgames.clockshop.core.Shop.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertEquals(setOf(1, 2, 3, 4, 5, 6), s.parts)
        assertEquals(Shop.Room.STUDY, s.room)
        assertEquals(setOf(Shop.Plate.SUN, Shop.Plate.MOON, Shop.Plate.STAR), s.plates)
    }

    @Test
    fun theMantelWaitsForThreeOClock() {
        val s = Shop()
        assertTrue("the first pinch turns it round", s.open("mantel").any { it is Event.MantelTurned })
        assertTrue("mantelTurned" in s.opened)
        assertEquals("a refusal says what is true, not what to do", "小门锁着。", s.open("mantel").refused())
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
        assertTrue("first a nudge", s.hint().contains("歪"))
        assertTrue("asked again, the answer", s.hint().contains("座钟"))
        s.open("mantel")
        assertFalse("still a nudge at the next step", s.hint().contains("三点"))
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

    private fun toTower(): Shop {
        val s = Shop()
        for (step in Walkthrough.steps) {
            Walkthrough.run(s, step)
            if (step == "go tower") break
        }
        assertEquals(Shop.Room.TOWER, s.room)
        Shop.RATIOS.forEach { s.take(it) }
        return s
    }

    @Test
    fun theLeverSaysWhatIsStillWrong() {
        val s = toTower()
        assertTrue(s.open("lever").refused()!!.contains("摆"))
        s.setDial("pend", 1)
        assertTrue(s.open("lever").refused()!!.contains("轮"))
        s.use(Item.RATIO_2, "train1")
        val mismatch = s.use(Item.RATIO_5, "train2")
        assertTrue(mismatch.filterIsInstance<Event.Said>().single().text.contains("10"))
        assertTrue(s.open("lever").refused()!!.contains("10"))
        s.untrain(1)
        s.untrain(2)
        s.use(Item.RATIO_3, "train1")
        s.use(Item.RATIO_4, "train2")
        assertEquals(null, s.open("lever").refused())
        assertTrue(s.towerRuns)
    }

    @Test
    fun theTowerStrikesOnlyWhenRunningAndAtTwelve() {
        val s = toTower()
        s.setClock("tower", 12, 0)
        assertTrue(5 !in s.parts)
        s.setDial("pend", 1); s.use(Item.RATIO_3, "train1"); s.use(Item.RATIO_4, "train2"); s.open("lever")
        s.setClock("tower", 11, 0)
        assertTrue(5 !in s.parts)
        s.setClock("tower", 12, 0)
        assertTrue(5 in s.parts)
        assertTrue(s.visible(Item.KEY_STUDY))
    }

    @Test
    fun theNewspaperNeedsTheLampAndTheCabinetTheRightDate() {
        val s = Shop()
        for (step in Walkthrough.steps) {
            if (step == "open lamp") break
            Walkthrough.run(s, step)
        }
        assertEquals(Shop.Room.STUDY, s.room)
        assertTrue(s.open("news").refused()!!.contains("暗"))
        s.open("lamp")
        assertTrue(s.lampOn)
        assertEquals(null, s.open("news").refused())
        assertTrue(s.clues.any { it.contains("星期四") })
        assertNotNull(s.open("cabinet").refused())
        s.setDial("month", 2); s.setDial("day", 11); s.setDial("week", 2)
        assertTrue("Wednesday is wrong", s.open("cabinet").refused() != null)
        s.setDial("week", 3)
        assertEquals(null, s.open("cabinet").refused())
        assertTrue(s.finished)
    }

    @Test
    fun theDatesOnThePapersAreRealCalendarFacts() {
        val c = java.util.GregorianCalendar(1936, java.util.Calendar.MARCH, 12)
        assertEquals(java.util.Calendar.THURSDAY, c.get(java.util.Calendar.DAY_OF_WEEK))
        // A seconds pendulum: T = 2 pi sqrt(L / g), rounded as the game says it.
        Shop.PENDULUM_LENGTH.forEachIndexed { i, l ->
            assertEquals(Shop.PENDULUM_PERIOD[i], 2 * Math.PI * Math.sqrt(l / 9.81), .03)
        }
    }

    @Test
    fun refusalsAndAnnouncementsNeverGiveTheNextStepAway() {
        val s = Shop()
        val said = ArrayList<String>()
        for (step in Walkthrough.steps) {
            Walkthrough.run(s, step).forEach {
                when (it) {
                    is Event.Said -> said += it.text
                    is Event.Refused -> said += it.why
                    else -> Unit
                }
            }
        }
        val giveaways = listOf("拨到", "捏", "把塔钟", "转盘", "钟点", "去拿", "要等到")
        for (t in said) for (g in giveaways) assertFalse("\"$t\" says \"$g\"", t.contains(g))
    }

    @Test
    fun plateHoursMatchTheAstronomicalClockAnswer() {
        val s = Shop()
        assertEquals(listOf(3, 10, 4), Shop.Plate.values().map { it.hour })
        assertTrue(s.plates.isEmpty())
        s.setClock("big", 3, 0)
        assertTrue(Shop.Plate.SUN in s.plates)
    }
}
