package com.haoqi.training.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.haoqi.training.MainActivity
import com.haoqi.training.R

/**
 * 休息结束的「兜底闹钟」。
 *
 * 之前用 [android.app.AlarmManager] + `getForegroundService` 到点拉起服务：
 * 在澎湃 OS 上进程被冻结/杀掉后，系统会拦掉后台启动前台服务，
 * 结果通知栏里的倒计时一直走到负数，只有再次打开 App 才补响。
 *
 * 现在闹钟直接投递到本广播接收器（广播不依赖前台服务，也不受 FGS 启动限制）：
 *  - 服务还在正常走秒（说明它马上会自己收尾、响 6 秒铃声）→ 交给服务，不重复；
 *  - 服务已经冻结/被杀 → 直接震动 + 发一条走「闹钟音频通道」的通知，
 *    声音由系统播放，静音模式下也能响。
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM_FIRE) return

        // 服务还活着并且在走秒：它会在 300ms 内自己 finishRest()，这里不要重复提醒
        val ticking = SystemClock.elapsedRealtime() - RestTimerService.lastTickElapsed in 0..3_000
        if (ticking) return

        // 已经提醒过（例如服务刚结束、闹钟还没来得及取消），不重复
        if (RestTimerStore.isAlerted(context, ALERT_DEDUP_MS)) return

        val snapshot = RestTimerStore.load(context)
        RestTimerStore.clearTimer(context)
        RestTimerStore.markAlerted(context)

        // 把可能还挂着的「休息中」前台服务/通知收掉，避免通知栏里继续出现负数
        runCatching { context.stopService(Intent(context, RestTimerService::class.java)) }

        RestTimerAlert.vibrate(context)
        RestTimerAlert.notify(context, snapshot?.label ?: "休息")
    }

    companion object {
        const val ACTION_ALARM_FIRE = "com.haoqi.training.action.REST_ALARM_FIRE"
        private const val ALERT_DEDUP_MS = 3 * 60 * 1000L
    }
}

/** 结束提醒：走系统闹钟音频通道的通知 + 直接震动，静音模式下也能提醒。 */
object RestTimerAlert {

    const val CHANNEL_ALERT_ID = "rest_timer_alert_v1"
    private const val CHANNEL_ALERT_NAME = "休息结束提醒"
    const val NOTIFICATION_ID = 1001
    private const val REQUEST_OPEN = 21

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ALERT_ID) != null) return

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ALERT_ID,
            CHANNEL_ALERT_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "组间休息到点提醒；走闹钟音频通道，手机静音也能响"
            if (alarmUri != null) {
                setSound(
                    alarmUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            // 震动由 App 自己调 Vibrator，不受系统通知振动开关影响；通道这里不再重复
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, label: String) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, build(context, label))
    }

    private fun build(context: Context, label: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("休息结束，开始下一组")
            .setContentText(label)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            // 服务先响过 6 秒铃声、闹钟又补发同一条通知时，不重复出声
            .setOnlyAlertOnce(true)
            .build()
    }

    /** 直接震动：静音/振动模式下都有效，系统振动服务会在进程退出后继续走完。 */
    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 300, 600, 300, 600, 300, 600)
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
