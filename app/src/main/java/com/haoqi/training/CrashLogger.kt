package com.haoqi.training

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把未捕获异常的堆栈写到应用私有目录，下次启动时弹窗展示。
 * 个人自用、没接崩溃平台时，这是拿到真机闪退原因最省事的办法。
 */
object CrashLogger {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 60_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(context: Context, thread: Thread, error: Throwable) {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
        val text = buildString {
            appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("thread: ${thread.name}")
            appendLine("version: $version")
            appendLine(
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            appendLine()
            append(error.stackTraceToString())
        }
        File(context.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
    }

    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
