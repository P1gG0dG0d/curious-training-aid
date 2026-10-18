package com.haoqi.training.domain

import java.util.Locale

/**
 * 训练状态相关的纯逻辑（不依赖 Android / Room），方便单元测试。
 * 「第n天」只是计划编号，不代表日期先后，也不限制训练顺序。
 */
object TrainingLogic {

    fun toggleSet(doneIndexes: Set<Int>, index: Int): Set<Int> =
        if (index in doneIndexes) doneIndexes - index else doneIndexes + index

    fun allSetsDone(doneIndexes: Set<Int>, totalSets: Int): Boolean =
        totalSets > 0 && doneIndexes.size >= totalSets

    fun dayTitle(dayIndex: Int): String = "循环第${dayIndex}天"

    fun completedLabel(dayIndex: Int): String = "循环第${dayIndex}天已练完"

    /** 容量分开显示：重量「60 kg」 */
    fun weightLabel(weightKg: Float): String =
        if (weightKg == weightKg.toInt().toFloat()) {
            "${weightKg.toInt()} kg"
        } else {
            String.format(Locale.US, "%.1f kg", weightKg)
        }

    /** 容量分开显示：次数「8 次」 */
    fun repsLabel(reps: Int): String = "$reps 次"

    fun restLabel(seconds: Int): String = when {
        seconds <= 0 -> "无休息"
        seconds < 60 -> "${seconds}秒"
        seconds % 60 == 0 -> "${seconds / 60}分钟"
        else -> "${seconds / 60}分${seconds % 60}秒"
    }

    /** 一组方块上的组号文案 */
    fun setLabel(setIndex: Int): String = "${setIndex + 1}"
}
