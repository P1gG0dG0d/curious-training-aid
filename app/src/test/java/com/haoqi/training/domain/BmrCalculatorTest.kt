package com.haoqi.training.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BmrCalculatorTest {

    @Test
    fun `male bmr uses Mifflin St Jeor formula`() {
        // 10*70 + 6.25*175 - 5*30 + 5 = 1648.75
        val bmr = BmrCalculator.bmrKcal(Sex.MALE, weightKg = 70f, heightCm = 175f, ageYears = 30)
        assertEquals(1648.75f, bmr, 0.01f)
    }

    @Test
    fun `female bmr subtracts 161`() {
        val bmr = BmrCalculator.bmrKcal(Sex.FEMALE, weightKg = 70f, heightCm = 175f, ageYears = 30)
        assertEquals(1482.75f, bmr, 0.01f)
    }

    @Test
    fun `total adds manually entered activity calories`() {
        val total = BmrCalculator.totalKcal(
            sex = Sex.MALE,
            weightKg = 70f,
            heightCm = 175f,
            ageYears = 30,
            activityKcal = 300f,
        )
        assertEquals(1948.75f, total, 0.01f)
    }

    @Test
    fun `total is never negative`() {
        val total = BmrCalculator.totalKcal(
            sex = Sex.FEMALE,
            weightKg = 30f,
            heightCm = 140f,
            ageYears = 90,
            activityKcal = -1000f,
        )
        assertEquals(0f, total, 0.001f)
    }
}
