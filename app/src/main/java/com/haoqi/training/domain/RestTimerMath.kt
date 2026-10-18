package com.haoqi.training.domain

/** 休息倒计时的纯计算，方便单元测试。 */
object RestTimerMath {

    /** 剩余毫秒 → 显示的剩余秒数（向上取整，避免显示 0 秒却还要等）。 */
    fun remainingSeconds(remainingMs: Long): Int =
        ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
}
