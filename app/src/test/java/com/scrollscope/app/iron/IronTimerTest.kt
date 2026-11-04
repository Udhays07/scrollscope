package com.scrollscope.app.iron

import org.junit.Assert.assertEquals
import org.junit.Test

class IronTimerTest {
    @Test fun clockUnderAnHour() {
        assertEquals("42:07", IronTimer.clock(42 * 60_000L + 7_000))
        assertEquals("0:09", IronTimer.clock(8_001))          // rounds partial seconds up
    }
    @Test fun clockOverAnHour() = assertEquals("1:05:09", IronTimer.clock(3_909_000))
    @Test fun negativeLeftClampsToZero() {
        val r = IronTimer.reading(-5_000, 3_600_000, null, "Reels")
        assertEquals("0:00", r.text); assertEquals(0f, r.fractionLeft, 1e-4f); assertEquals(TimerStage.Critical, r.stage)
    }
    @Test fun stageBoundariesAreInclusive() {
        assertEquals(TimerStage.Calm, IronTimer.reading(600_001, 3_600_000, null, "Reels").stage)
        assertEquals(TimerStage.Low, IronTimer.reading(600_000, 3_600_000, null, "Reels").stage)
        assertEquals(TimerStage.Critical, IronTimer.reading(300_000, 3_600_000, null, "Reels").stage)
    }
    @Test fun fractionLeft() = assertEquals(0.25f, IronTimer.reading(900_000, 3_600_000, null, "Reels").fractionLeft, 1e-4f)
    @Test fun passOverridesExhaustedLimit() {
        val r = IronTimer.reading(0, 3_600_000, 150_000, "Reels")
        assertEquals(TimerStage.Pass, r.stage); assertEquals("2:30", r.text); assertEquals(0.5f, r.fractionLeft, 1e-4f)
        assertEquals("Pass: 3 minutes left", r.spoken)
    }
    @Test fun spokenUsesMinutesRoundedUp() {
        assertEquals("42 minutes of Reels left", IronTimer.reading(41 * 60_000L + 1, 3_600_000, null, "Reels").spoken)
        assertEquals("1 minute of Shorts left", IronTimer.reading(20_000, 3_600_000, null, "Shorts").spoken)
        assertEquals("No Reels time left", IronTimer.reading(0, 3_600_000, null, "Reels").spoken)
    }
}
