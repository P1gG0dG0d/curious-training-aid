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

/** 用 SharedPreferences 存休息计时的结束时刻和「已经提醒过」的标记，进程被杀也不丢。 */
object RestTimerStore {

    private const val PREFS = "rest_timer_state"
    private const val KEY_END = "end_wall_millis"
    private const val KEY_TOTAL = "total_seconds"
    private const val KEY_LABEL = "label"
    private const val KEY_ALERTED_AT = "alerted_at"

    fun save(context: Context, endWallMillis: Long, totalSeconds: Int, label: String) {
        prefs(context).edit()
            .putLong(KEY_END, endWallMillis)
            .putInt(KEY_TOTAL, totalSeconds)
            .putString(KEY_LABEL, label)
            .apply()
    }

    fun load(context: Context): RestTimerSnapshot? {
        val prefs = prefs(context)
        val end = prefs.getLong(KEY_END, 0L)
        val total = prefs.getInt(KEY_TOTAL, 0)
        if (end <= 0L || total <= 0) return null
        return RestTimerSnapshot(
            endWallMillis = end,
            totalSeconds = total,
            label = prefs.getString(KEY_LABEL, null) ?: "休息",
        )
    }

    /** 只清计时本身，保留「已提醒」标记（方便判断是否需要补响）。 */
    fun clearTimer(context: Context) {
        prefs(context).edit()
            .remove(KEY_END)
            .remove(KEY_TOTAL)
            .remove(KEY_LABEL)
            .apply()
    }

    /** 已经为这一轮休息提醒过（铃声响过/通知发过），避免闹钟和收尾逻辑重复提醒。 */
    fun markAlerted(context: Context) {
        prefs(context).edit().putLong(KEY_ALERTED_AT, System.currentTimeMillis()).apply()
    }

    fun isAlerted(context: Context, withinMillis: Long): Boolean {
        val at = prefs(context).getLong(KEY_ALERTED_AT, 0L)
        return at > 0L && System.currentTimeMillis() - at in 0..withinMillis
    }

    /** 开始新一次休息时调用，清掉上一轮的提醒标记。 */
    fun clearAlerted(context: Context) {
        prefs(context).edit().remove(KEY_ALERTED_AT).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
