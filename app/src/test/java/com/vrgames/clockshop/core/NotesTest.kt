package com.vrgames.clockshop.core

import com.vrgames.clockshop.core.Shop.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.5.2: every step that goes through says something in the note in front. */
class NotesTest {
    private fun say(s: Shop, move: String): String? {
        val out = Walkthrough.run(s, move)
        return out.filterIsInstance<Shop.Event.Refused>().firstOrNull()?.why
            ?: out.filterIsInstance<Shop.Event.Said>().firstOrNull()?.text
            ?: out.filterIsInstance<Shop.Event.Part>().firstOrNull()?.let { "找到第 ${it.n} 枚怀表零件！" }
            ?: Notes.fallback(move, out)
    }

    @Test
    fun theMantelClockSaysWhatItShows() {
        val s = Shop()
        assertEquals(Notes.MANTEL_TURNED, say(s, "open mantel"))
        s.setClock("big", 15, 0)
        assertEquals(Notes.MANTEL_DOOR, say(s, "open mantel"))
        assertEquals("拿到了柜台钥匙，在面前的托盘里。", say(s, "take KEY_DRAWER"))
    }

    @Test
    fun placingAndTakingBackSaySo() {
        assertEquals("大齿轮装上了。", Notes.fallback("use GEAR_L gear1", listOf(Shop.Event.Placed(Item.GEAR_L, "gear1"))))
        assertEquals("少年学徒的照片挂上了。", Notes.fallback("hang PHOTO_1 frame0", listOf(Shop.Event.Placed(Item.PHOTO_1, "frame0"))))
        assertEquals("取下了少年学徒的照片。", Notes.fallback("unhang 0", listOf(Shop.Event.Took(Item.PHOTO_1))))
        assertEquals("取下了三比一的传动轮。", Notes.fallback("untrain 1", listOf(Shop.Event.Took(Item.RATIO_3))))
        assertEquals("拿到了大齿轮，在面前的托盘里。", Notes.fallback("take GEAR_L", listOf(Shop.Event.Took(Item.GEAR_L))))
    }

    @Test
    fun theShopsOwnWordsAndRefusalsComeFirst() {
        // A refusal, or the shop's own words, are never covered by a fallback line.
        assertNull(Notes.fallback("open drawer", listOf(Shop.Event.Refused("抽屉锁着。"))))
        assertNull(Notes.fallback("hang PHOTO_5 frame4", listOf(Shop.Event.Placed(Item.PHOTO_5, "frame4"), Shop.Event.Said("五张照片排好了"))))
        // Setting a clock to an hour that changes nothing adds no line (the clock panel shows the time).
        assertNull(Notes.fallback("clock big 10 0", emptyList()))
    }

    @Test
    fun theWholeWalkthroughSaysSomethingAtEveryStepButTheDials() {
        val s = Shop()
        val silent = ArrayList<String>()
        for (m in Walkthrough.steps) if (say(s, m) == null) silent += m
        // Only turning a dial or a clock to a time that changes nothing may stay silent: the panel shows them.
        assertTrue("silent: $silent", silent.all { it.startsWith("dial ") || it.startsWith("clock ") })
    }

    @Test
    fun notesAreShorterThanBefore() {
        assertEquals(7_000L, Notes.NOTE_MS)
        assertEquals(15_000L, Notes.FIRST_NOTE_MS)
        assertEquals(15_000L, Notes.HINT_NOTE_MS)
    }
}
