package com.vrgames.clockshop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeHeightTest {
    private fun EyeHeight.feedN(n: Int, y: Float?, dt: Double = 0.05): Boolean {
        var any = false
        repeat(n) { if (feed(if (y == null) null else 0.1f, y, if (y == null) null else -0.2f, dt)) any = true }
        return any
    }

    @Test fun zeroPosesAreIgnored() {
        val e = EyeHeight()
        repeat(20) { assertFalse(e.feed(0f, 0f, 0f, 0.05)) }
        assertNull(e.value)
    }

    @Test fun settlesOnSteadyReadings() {
        val e = EyeHeight()
        assertTrue(e.feedN(EyeHeight.NEED, 1.23f))
        assertEquals(1.23f, e.value!!, 1e-4f)
    }

    @Test fun jumpyStartDoesNotSettleOnTheJump() {
        val e = EyeHeight()
        e.feed(0f, 0f, 0f, 0.05); e.feed(0.1f, 0.6f, 0f, 0.05)
        assertTrue(e.feedN(EyeHeight.NEED, 1.25f))
        assertEquals(1.25f, e.value!!, 1e-4f)
    }

    @Test fun outOfRangeHeightsAreIgnored() {
        val e = EyeHeight()
        e.feedN(30, 0.2f); e.feedN(30, 3.5f)
        assertNull(e.value)
    }

    @Test fun fallsBackThenCorrectsOnce() {
        val e = EyeHeight()
        assertTrue(e.feedN(100, null))          // 5 s with nothing
        assertTrue(e.guessed)
        assertEquals(EyeHeight.FALLBACK, e.value!!, 1e-4f)
        assertTrue(e.feedN(EyeHeight.NEED, 1.31f))   // a real reading arrives late: one correction
        assertFalse(e.guessed)
        assertEquals(1.31f, e.value!!, 1e-4f)
        assertFalse(e.feedN(EyeHeight.NEED, 1.6f))   // and then it never moves again
        assertEquals(1.31f, e.value!!, 1e-4f)
    }

    @Test fun standingOrSeatedBothMeasured() {
        val seated = EyeHeight(); seated.feedN(EyeHeight.NEED, 1.18f)
        val standing = EyeHeight(); standing.feedN(EyeHeight.NEED, 1.62f)
        assertEquals(1.18f, seated.value!!, 1e-4f)
        assertEquals(1.62f, standing.value!!, 1e-4f)
    }
}
