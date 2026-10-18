package com.haoqi.training.domain

import com.haoqi.training.data.db.ProfileEntity
import com.haoqi.training.ui.diet.DietUiState
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyDietTest {
    @Test fun rejectsInvalidActivityWithoutClamping() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertFalse(DailyDiet.validActivity(it))
        }
        assertTrue(DailyDiet.validActivity(0f))
        assertTrue(DailyDiet.validActivity(325.5f))
    }
    @Test fun staleEditorsCannotWriteAfterMidnightOrDateChange() {
        val today = LocalDate.of(2026, 10, 19)
        assertFalse(DailyDiet.canSave("2026-10-18", today))
        assertFalse(DailyDiet.canSave("2026-10-20", today))
        assertFalse(DailyDiet.canSave("bad-date", today))
        assertTrue(DailyDiet.canSave("2026-10-19", today))
    }
    @Test fun legacyProfileActivityIsNeverUsed() {
        val profile = ProfileEntity(configured = true, activityKcal = 9999f)
        val state = DietUiState(profile = profile, activityKcal = 200f)
        assertEquals(state.bmrKcal!! + 200f, state.expenditureKcal, 0.001f)
        assertEquals(state.bmrKcal!!, state.copy(activityKcal = 0f).expenditureKcal, 0.001f)
    }
    @Test fun unconfiguredBmrIsDistinctFromActivityOnlySubtotal() {
        val state = DietUiState(profile = ProfileEntity(configured = false), activityKcal = 300f)
        assertNull(state.bmrKcal)
        assertEquals(300f, state.expenditureKcal, 0.001f)
        assertEquals(0f, DietUiState().expenditureKcal, 0.001f)
    }
}
