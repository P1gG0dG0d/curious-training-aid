package com.haoqi.training.domain

import java.time.LocalDate

/** Reject stale editor writes rather than silently moving them to a new day. */
object DailyDiet {
    fun validActivity(value: Float): Boolean = value.isFinite() && value >= 0f
    fun canSave(editorDate: String, today: LocalDate): Boolean =
        runCatching { LocalDate.parse(editorDate) == today }.getOrDefault(false)
    fun expenditure(bmr: Float?, activity: Float): Float = (bmr ?: 0f) + activity
}
