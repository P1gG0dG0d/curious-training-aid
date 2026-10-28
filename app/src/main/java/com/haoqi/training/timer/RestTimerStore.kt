package com.haoqi.training.timer

import android.content.Context

/**
 * 休息计时的持久化快照：
 * 进程被系统杀掉、应用被回收之后，靠它把「还剩多少秒」重新算出来，
 * 而不是把倒计时状态只放在内存里。
 */
data class RestTimerSnapshot(
    /** 结束时刻（System.currentTimeMillis 口径），进程重启后用它重算剩余时间。 */
    val endWallMillis: Long,
    val totalSeconds: Int,
    val label: String,
)

/** 用 SharedPreferences 存一份休息计时的结束时刻，进程被杀也不丢。 */
object RestTimerStore {

    private const val PREFS = "rest_timer_state"
    private const val KEY_END = "end_wall_millis"
    private const val KEY_TOTAL = "total_seconds"
    private const val KEY_LABEL = "label"

    fun save(context: Context, endWallMillis: Long, totalSeconds: Int, label: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_END, endWallMillis)
            .putInt(KEY_TOTAL, totalSeconds)
            .putString(KEY_LABEL, label)
            .apply()
    }

    fun load(context: Context): RestTimerSnapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val end = prefs.getLong(KEY_END, 0L)
        val total = prefs.getInt(KEY_TOTAL, 0)
        if (end <= 0L || total <= 0) return null
        return RestTimerSnapshot(
            endWallMillis = end,
            totalSeconds = total,
            label = prefs.getString(KEY_LABEL, null) ?: "休息",
        )
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
