package com.haoqi.training.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLogicTest {

    @Test
    fun `tapping a set toggles it into the done set`() {
        val afterFirstTap = TrainingLogic.toggleSet(emptySet(), 2)
        assertEquals(setOf(2), afterFirstTap)
    }

    @Test
    fun `tapping a done set un-does it`() {
        val afterUndo = TrainingLogic.toggleSet(setOf(0, 1, 2), 1)
        assertEquals(setOf(0, 2), afterUndo)
    }

    @Test
    fun `all sets done requires every set`() {
        assertTrue(TrainingLogic.allSetsDone(setOf(0, 1, 2), 3))
        assertFalse(TrainingLogic.allSetsDone(setOf(0, 1), 3))
        assertFalse(TrainingLogic.allSetsDone(emptySet(), 0))
    }

    @Test
    fun `day labels describe cycle position not calendar order`() {
        assertEquals("循环第3天", TrainingLogic.dayTitle(3))
        assertEquals("循环第3天已练完", TrainingLogic.completedLabel(3))
    }

    @Test
    fun `weight and reps render separately`() {
        assertEquals("60 kg", TrainingLogic.weightLabel(60f))
        assertEquals("62.5 kg", TrainingLogic.weightLabel(62.5f))
        assertEquals("8 次", TrainingLogic.repsLabel(8))
    }

    @Test
    fun `rest label formats comfortably`() {
        assertEquals("无休息", TrainingLogic.restLabel(0))
        assertEquals("45秒", TrainingLogic.restLabel(45))
        assertEquals("2分钟", TrainingLogic.restLabel(120))
        assertEquals("1分30秒", TrainingLogic.restLabel(90))
    }
}
