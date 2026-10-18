package com.haoqi.training.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieChartMathTest {

    @Test
    fun `dashed line sits 500 below the larger value when expenditure is larger`() {
        assertEquals(1900f, CalorieChartMath.lineKcal(expenditureKcal = 2400f, intakeKcal = 1500f), 0.01f)
    }

    @Test
    fun `dashed line is symmetric when intake is larger`() {
        assertEquals(1900f, CalorieChartMath.lineKcal(expenditureKcal = 1500f, intakeKcal = 2400f), 0.01f)
    }

    @Test
    fun `dashed line never goes below zero`() {
        assertEquals(0f, CalorieChartMath.lineKcal(expenditureKcal = 300f, intakeKcal = 500f), 0.01f)
        assertEquals(0f, CalorieChartMath.lineKcal(expenditureKcal = 0f, intakeKcal = 0f), 0.01f)
    }

    @Test
    fun `line stays inside the shorter bar when the gap is under 500`() {
        // 2400 消耗 vs 2100 摄入：差 300，虚线 1900 落在摄入条内部
        assertTrue(CalorieChartMath.lineInsideShorterBar(2400f, 2100f))
    }

    @Test
    fun `line falls outside the shorter bar when the gap exceeds 500`() {
        // 2400 消耗 vs 1500 摄入：差 900，虚线 1900 超出摄入条末端
        assertFalse(CalorieChartMath.lineInsideShorterBar(2400f, 1500f))
    }

    @Test
    fun `exactly 500 difference counts as reaching the target`() {
        assertTrue(CalorieChartMath.isAtLeastTarget(2400f, 1900f))
        assertTrue(CalorieChartMath.isAtLeastTarget(1900f, 2400f))
        assertFalse(CalorieChartMath.isAtLeastTarget(2400f, 1901f))
    }

    @Test
    fun `axis keeps the dashed line visible and handles empty days`() {
        val axis = CalorieChartMath.axisMaxKcal(2400f, 1500f)
        assertTrue(axis >= 2400f)
        assertEquals(500f, CalorieChartMath.axisMaxKcal(0f, 0f), 0.01f)
    }

    @Test
    fun `fraction is clamped into zero to one`() {
        assertEquals(0f, CalorieChartMath.fractionOf(-10f, 1000f), 0.0001f)
        assertEquals(1f, CalorieChartMath.fractionOf(1200f, 1000f), 0.0001f)
        assertEquals(0f, CalorieChartMath.fractionOf(100f, 0f), 0.0001f)
    }
}
