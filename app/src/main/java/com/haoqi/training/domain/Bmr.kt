package com.haoqi.training.domain

/** 性别（只用于基础代谢计算） */
enum class Sex(val label: String) {
    MALE("男"),
    FEMALE("女");
}

/**
 * 基础代谢（Mifflin-St Jeor）与每日总消耗。
 * 纯函数，不依赖 Android，方便单元测试。
 */
object BmrCalculator {

    fun bmrKcal(sex: Sex, weightKg: Float, heightCm: Float, ageYears: Int): Float {
        val base = 10f * weightKg + 6.25f * heightCm - 5f * ageYears.toFloat()
        return when (sex) {
            Sex.MALE -> base + 5f
            Sex.FEMALE -> base - 161f
        }
    }

    /** 每日总消耗 = 基础代谢 + 用户手填的活动代谢 */
    fun totalKcal(
        sex: Sex,
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        activityKcal: Float,
    ): Float = (bmrKcal(sex, weightKg, heightCm, ageYears) + activityKcal).coerceAtLeast(0f)
}
