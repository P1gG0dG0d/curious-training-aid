package com.haoqi.training.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerMathTest {

    @Test
    fun `full seconds map exactly`() {
        assertEquals(90, RestTimerMath.remainingSeconds(90_000L))
        assertEquals(1, RestTimerMath.remainingSeconds(1_000L))
    }

    @Test
    fun `partial seconds round up so 0 is never shown early`() {
        assertEquals(1, RestTimerMath.remainingSeconds(1L))
        assertEquals(1, RestTimerMath.remainingSeconds(999L))
        assertEquals(2, RestTimerMath.remainingSeconds(1_001L))
    }

    @Test
    fun `negative time clamps to zero`() {
        assertEquals(0, RestTimerMath.remainingSeconds(-500L))
        assertEquals(0, RestTimerMath.remainingSeconds(0L))
    }
}
