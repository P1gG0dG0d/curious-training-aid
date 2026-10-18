package com.haoqi.training.domain

import kotlin.math.abs
import kotlin.math.max

/**
 * 双进度条（灰 = 总消耗、蓝 = 摄入）的几何计算。
 * 纯函数，方便单元测试。
 *
 * 用户确认的规则：两条共用同一条横向 kcal 刻度；
 * 竖虚线固定在 max(消耗, 摄入) − 500 的位置，
 * 差值无论正负（消耗大 / 摄入大）都使用同一画法：
 *  - 虚线落在较短条内部   → 差值不足 500 kcal
 *  - 虚线落在较短条末端外 → 差值已超过 500 kcal
 */
object CalorieChartMath {

    const val TARGET_DELTA_KCAL = 500f
    private const val AXIS_HEAD_ROOM = 1.12f
    private const val AXIS_MIN_KCAL = 500f

    fun lineKcal(expenditureKcal: Float, intakeKcal: Float): Float =
        (max(expenditureKcal, intakeKcal) - TARGET_DELTA_KCAL).coerceAtLeast(0f)

    fun axisMaxKcal(expenditureKcal: Float, intakeKcal: Float): Float =
        max(max(expenditureKcal, intakeKcal) * AXIS_HEAD_ROOM, AXIS_MIN_KCAL)

    fun fractionOf(valueKcal: Float, axisMaxKcal: Float): Float {
        if (axisMaxKcal <= 0f) return 0f
        return (valueKcal / axisMaxKcal).coerceIn(0f, 1f)
    }

    fun deltaKcal(expenditureKcal: Float, intakeKcal: Float): Float =
        intakeKcal - expenditureKcal

    fun isAtLeastTarget(expenditureKcal: Float, intakeKcal: Float): Boolean =
        abs(deltaKcal(expenditureKcal, intakeKcal)) >= TARGET_DELTA_KCAL

    /** 虚线是否落在较短条内部（true = 差值还没到 500 kcal） */
    fun lineInsideShorterBar(expenditureKcal: Float, intakeKcal: Float): Boolean =
        lineKcal(expenditureKcal, intakeKcal) < minOf(expenditureKcal, intakeKcal)
}
