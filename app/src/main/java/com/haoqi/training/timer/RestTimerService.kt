package com.haoqi.training.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.haoqi.training.MainActivity
import com.haoqi.training.R
import com.haoqi.training.domain.RestTimerMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 供界面展示的休息状态（null = 没有在休息）。 */
data class RestTimerState(
    val label: String,
    val remainingSeconds: Int,
    val totalSeconds: Int,
)

/**
 * 组间休息倒计时前台服务：
 *  - 前台服务 + 通知栏倒计时，锁屏/切后台继续跑
 *  - 计时结束震动 + 响铃（走闹钟音量通道），并弹「休息结束」通知
 *  - 通知栏可直接「跳过休息」
 */
class RestTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertRingtone: Ringtone? = null
    private var endAtElapsed = 0L
    private var label = "休息"
    private var totalSeconds = 0
    private var lastShownSeconds = -1

    private val tickRunnable = object : Runnable {
        override fun run() {
            val remainingMs = endAtElapsed - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) {
                finishRest()
                return
            }
            // 向上取整，避免显示 0 秒却还要等 1 秒才结束
            val remaining = RestTimerMath.remainingSeconds(remainingMs)
            if (remaining != lastShownSeconds) {
                lastShownSeconds = remaining
                _state.value = RestTimerState(label, remaining, totalSeconds)
                notifyOngoing(remaining)
            }
            handler.postDelayed(this, 200L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 60).coerceIn(0, MAX_SECONDS)
                label = intent.getStringExtra(EXTRA_LABEL) ?: "休息"
                if (seconds <= 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                totalSeconds = seconds
                endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L
                lastShownSeconds = -1
                stopAlertSound()
                acquireWakeLock(seconds)
                // 清掉上一轮（可能还在响铃/等待退出）的所有回调
                handler.removeCallbacksAndMessages(null)
                startForegroundSafe(buildOngoing(seconds))
                _state.value = RestTimerState(label, seconds, seconds)
                handler.post(tickRunnable)
            }

            ACTION_SKIP -> {
                handler.removeCallbacksAndMessages(null)
                stopAlertSound()
                releaseWakeLock()
                _state.value = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishRest() {
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        _state.value = null
        vibrateAlert()
        playAlertSound()
        // 保持前台状态 6 秒，避免系统回收服务导致铃声中断；
        // 结束后 detach 通知（留在通知栏），再退出服务。
        startForegroundSafe(buildFinished())
        handler.postDelayed({
            stopForegroundQuietly()
            stopSelf()
        }, ALERT_SOUND_MS + 500L)
    }

    /** Android 14 起显式传前台服务类型，避免特殊用途类型未生效被系统杀。 */
    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notifyOngoing(remaining: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildOngoing(remaining))
    }

    private fun buildOngoing(remaining: Int): Notification {
        val skipIntent = PendingIntent.getService(
            this,
            REQUEST_SKIP,
            Intent(this, RestTimerService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("休息中 · $label")
            .setContentText("剩余 ${formatDuration(remaining)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "跳过休息", skipIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildFinished(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("休息结束，开始下一组")
            .setContentText(label)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun acquireWakeLock(seconds: Int) {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CuriousTraining:RestTimer").apply {
            setReferenceCounted(false)
            acquire((seconds + 60) * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun vibrateAlert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun playAlertSound() {
        stopAlertSound()
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        alertRingtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
        handler.postDelayed({ stopAlertSound() }, ALERT_SOUND_MS)
    }

    private fun stopAlertSound() {
        alertRingtone?.stop()
        alertRingtone = null
    }

    private fun stopForegroundQuietly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        _state.value = null
        stopAlertSound()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private val _state = MutableStateFlow<RestTimerState?>(null)

        /** 界面用它显示「休息中 · 剩余 xx 秒」，不用轮询服务。 */
        val state: StateFlow<RestTimerState?> = _state.asStateFlow()

        private const val CHANNEL_ID = "rest_timer_v2"
        private const val CHANNEL_NAME = "休息计时与结束提醒"
        private const val NOTIF_ID = 1001
        private const val REQUEST_SKIP = 11
        private const val REQUEST_OPEN = 12
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_LABEL = "label"
        private const val MAX_SECONDS = 3600
        private const val ALERT_SOUND_MS = 6000L
        const val ACTION_START = "com.haoqi.training.action.REST_START"
        const val ACTION_SKIP = "com.haoqi.training.action.REST_SKIP"

        fun start(context: Context, seconds: Int, label: String) {
            createChannel(context)
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun skip(context: Context) {
            context.startService(
                Intent(context, RestTimerService::class.java).setAction(ACTION_SKIP)
            )
        }

        fun createChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "组间休息倒计时与结束提醒（声音由 App 自己播放）"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        private fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return if (minutes > 0) "${minutes}分${secs.toString().padStart(2, '0')}秒" else "${secs}秒"
        }
    }
}
