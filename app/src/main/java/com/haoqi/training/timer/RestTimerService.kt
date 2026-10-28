package com.haoqi.training.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
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
import android.widget.RemoteViews
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
 *  - 结束时刻同时写进 SharedPreferences，并挂一个系统精确闹钟：
 *    进程被系统杀掉也会由闹钟把服务拉起来响铃，不会「静悄悄错过」
 *  - 通知栏的剩余时间用系统 Chronometer（RemoteViews）自己走秒，
 *    不再每秒 notify，绕开 HyperOS 等 ROM 对后台通知更新的限流
 *  - 计时结束震动 + 响铃（走闹钟音量通道），并弹「休息结束」通知
 *  - 通知栏可直接「跳过休息」
 */
class RestTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var screenWatcherRegistered = false

    private val screenWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (alertRingtone != null || vibrator != null) silenceAndDismiss()
        }
    }

    /** 结束时刻（单调时钟，不受用户改系统时间影响）。 */
    private var endAtElapsed = 0L

    /** 结束时刻（墙上时钟，只用于进程被杀后恢复）。 */
    private var endAtWall = 0L
    private var label = "休息"
    private var totalSeconds = 0
    private var lastShownSeconds = -1
    private var finished = false

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
                // 通知栏不再每秒 notify：
                // 剩余时间由系统 Chronometer 自己走秒，后台限流也不会冻结数字。
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 60).coerceIn(0, MAX_SECONDS)
                if (seconds <= 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTimer(seconds, intent.getStringExtra(EXTRA_LABEL) ?: "休息")
            }

            // 系统闹钟到点：可能是在进程被杀之后把服务拉起来，先满足前台要求再收尾。
            ACTION_ALARM_FIRE -> {
                if (!restoreIfNeeded()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundSafe(buildOngoing())
                handler.removeCallbacks(tickRunnable)
                if (endAtElapsed - SystemClock.elapsedRealtime() > ALARM_EARLY_TOLERANCE_MS) {
                    // 闹钟提前触发（例如用户改了系统时间）：继续正常走秒
                    scheduleEndAlarm()
                    handler.post(tickRunnable)
                } else {
                    finishRest()
                }
            }

            // 进程被杀后重建（START_STICKY）或回到 App 时对账
            ACTION_SYNC, null -> {
                if (endAtElapsed > 0L && !finished &&
                    endAtElapsed - SystemClock.elapsedRealtime() > 0L
                ) {
                    // 已经在计时，不重复启动
                    return START_STICKY
                }
                if (!restoreIfNeeded()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val remainingMs = endAtElapsed - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) {
                    startForegroundSafe(buildOngoing())
                    finishRest()
                } else {
                    acquireWakeLock(remainingMs)
                    scheduleEndAlarm()
                    startForegroundSafe(buildOngoing())
                    _state.value = RestTimerState(
                        label,
                        RestTimerMath.remainingSeconds(remainingMs),
                        totalSeconds,
                    )
                    handler.removeCallbacks(tickRunnable)
                    handler.post(tickRunnable)
                }
            }

            ACTION_SKIP -> {
                handler.removeCallbacksAndMessages(null)
                stopAlertSound()
                releaseWakeLock()
                cancelEndAlarm()
                RestTimerStore.clear(this)
                _state.value = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                notificationManager().cancel(NOTIF_ID)
                stopSelf()
            }

            // 铃声还在响时把「休息结束」通知划掉：立即停止铃声和震动。
            ACTION_DISMISS_ALERT -> silenceAndDismiss()
        }
        return START_STICKY
    }

    private fun startTimer(seconds: Int, exerciseLabel: String) {
        label = exerciseLabel
        totalSeconds = seconds
        endAtWall = System.currentTimeMillis() + seconds * 1000L
        endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L
        lastShownSeconds = -1
        finished = false
        stopAlertSound()
        acquireWakeLock(seconds * 1000L)
        RestTimerStore.save(this, endAtWall, totalSeconds, label)
        scheduleEndAlarm()
        // 清掉上一轮（可能还在响铃/等待退出）的所有回调
        handler.removeCallbacksAndMessages(null)
        startForegroundSafe(buildOngoing())
        _state.value = RestTimerState(label, seconds, seconds)
        handler.post(tickRunnable)
    }

    private fun restoreIfNeeded(): Boolean {
        if (endAtElapsed > 0L && !finished) return true
        val snapshot = RestTimerStore.load(this) ?: return false
        label = snapshot.label
        totalSeconds = snapshot.totalSeconds
        endAtWall = snapshot.endWallMillis
        // 用墙上时钟重算剩余时间，并夹在 [0, 总时长] 之间，避免改过系统时间后数值离谱
        val remainingMs = (endAtWall - System.currentTimeMillis()).coerceIn(0L, totalSeconds * 1000L)
        endAtElapsed = SystemClock.elapsedRealtime() + remainingMs
        finished = false
        lastShownSeconds = -1
        return true
    }

    private fun finishRest() {
        if (finished) return
        finished = true
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        RestTimerStore.clear(this)
        cancelEndAlarm()
        _state.value = null
        vibrateAlert()
        playAlertSound()
        // 结束提醒必须是「可划掉的普通通知」：前台通知不允许被划掉，
        // 所以先退出前台状态再发通知，用户划掉时 deleteIntent 才能停下铃声。
        // 服务本身仍作为已启动服务存活到铃声结束。
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager().notify(NOTIF_ID, buildFinished())
        registerScreenWatcher()
        handler.postDelayed({
            stopAlertSound()
            stopForegroundQuietly()
            stopSelf()
        }, ALERT_SOUND_MS + 500L)
    }

    /**
     * 响铃期间监听屏幕亮/灭：按电源键会让屏幕状态翻转（系统行为，App 拦不到电源键本身），
     * 但可以在这一瞬间立刻静音并清掉通知，等于「按一下电源键就安静」。
     */
    private fun registerScreenWatcher() {
        if (screenWatcherRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // 系统广播，Android 13+ 也要显式声明导出策略
        ContextCompat.registerReceiver(this, screenWatcher, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenWatcherRegistered = true
    }

    private fun unregisterScreenWatcher() {
        if (!screenWatcherRegistered) return
        runCatching { unregisterReceiver(screenWatcher) }
        screenWatcherRegistered = false
    }

    /** 静音 + 移掉结束通知 + 结束服务（划掉通知、按电源键、跳过都走这里）。 */
    private fun silenceAndDismiss() {
        stopAlertSound()
        handler.removeCallbacksAndMessages(null)
        RestTimerStore.clear(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager().cancel(NOTIF_ID)
        stopSelf()
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

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun acquireWakeLock(durationMs: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CuriousTraining:RestTimer",
        ).apply {
            setReferenceCounted(false)
            acquire(durationMs + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---- 系统精确闹钟：进程被杀也能把结束提醒拉起来 ----

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getForegroundService(
        this,
        REQUEST_ALARM,
        Intent(this, RestTimerService::class.java).setAction(ACTION_ALARM_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleEndAlarm() {
        val triggerAt = endAtElapsed
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = alarmPendingIntent()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            } else {
                // 没拿到精确闹钟权限时退化成系统闹钟（始终精确，代价是状态栏出现闹钟图标）
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        System.currentTimeMillis() + (triggerAt - SystemClock.elapsedRealtime()),
                        openAppIntent(),
                    ),
                    operation,
                )
            }
        } catch (e: SecurityException) {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + (triggerAt - SystemClock.elapsedRealtime()),
                    openAppIntent(),
                ),
                operation,
            )
        }
    }

    private fun cancelEndAlarm() {
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmPendingIntent())
    }

    // ---- 通知 ----

    /**
     * 进行中的休息通知：剩余时间用 RemoteViews 里的系统 Chronometer 显示，
     * 由 SystemUI 自己走秒；同时保留 setUsesChronometer 作为标准模板的兜底。
     */
    private fun buildOngoing(): Notification {
        val remainingMs = (endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val remaining = RestTimerMath.remainingSeconds(remainingMs)
        val dark = isNightMode()

        val titleColor = if (dark) Color.WHITE else Color.parseColor("#111111")
        val subtitleColor = if (dark) Color.parseColor("#B0B0B0") else Color.parseColor("#767676")

        val views = RemoteViews(packageName, R.layout.notification_rest).apply {
            setTextViewText(R.id.rest_notif_title, "休息中 · $label")
            setTextViewText(
                R.id.rest_notif_subtitle,
                "共 ${formatDuration(totalSeconds)}",
            )
            setTextColor(R.id.rest_notif_title, titleColor)
            setTextColor(R.id.rest_notif_subtitle, subtitleColor)
            setTextColor(R.id.rest_notif_chronometer, titleColor)
            // 基准 = 现在 + 剩余毫秒；倒计时由 SystemUI 里的 Chronometer 自己走
            setChronometer(
                R.id.rest_notif_chronometer,
                SystemClock.elapsedRealtime() + remainingMs,
                null,
                true,
            )
            setChronometerCountDown(R.id.rest_notif_chronometer, true)
        }

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
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remainingMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, "跳过休息", skipIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // 锁屏上不隐藏内容：很多系统默认把「私密通知」锁屏收起，
            // 这里显式公开，并提供一份标准布局的 publicVersion 给锁屏用
            // （自定义 RemoteViews 不一定能在锁屏渲染）。
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(buildPublicVersion(remainingMs, remaining))
            .build()
    }

    /** 锁屏用的「公开版本」：标准布局 + 系统计时器，不依赖自定义 RemoteViews。 */
    private fun buildPublicVersion(remainingMs: Long, remaining: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("休息中 · $label")
            .setContentText("剩余 ${formatDuration(remaining)}")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remainingMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun buildFinished(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("休息结束，开始下一组")
            .setContentText(label)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            // 划掉通知 = 停止铃声和震动，不用再去按电源键
            .setDeleteIntent(
                PendingIntent.getService(
                    this,
                    REQUEST_DISMISS,
                    Intent(this, RestTimerService::class.java).setAction(ACTION_DISMISS_ALERT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---- 响铃/震动 ----

    private fun vibrateAlert() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = device
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
        device.vibrate(VibrationEffect.createWaveform(pattern, -1))
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
        vibrator?.cancel()
        vibrator = null
        unregisterScreenWatcher()
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
        private const val REQUEST_DISMISS = 13
        private const val REQUEST_ALARM = 14
        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_LABEL = "label"
        private const val MAX_SECONDS = 3600
        private const val ALERT_SOUND_MS = 6000L
        private const val TICK_MS = 300L
        private const val ALARM_EARLY_TOLERANCE_MS = 1200L
        const val ACTION_START = "com.haoqi.training.action.REST_START"
        const val ACTION_SKIP = "com.haoqi.training.action.REST_SKIP"
        const val ACTION_DISMISS_ALERT = "com.haoqi.training.action.REST_DISMISS_ALERT"
        const val ACTION_ALARM_FIRE = "com.haoqi.training.action.REST_ALARM_FIRE"
        const val ACTION_SYNC = "com.haoqi.training.action.REST_SYNC"

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

        /**
         * 回到 App / 冷启动时对账：如果持久化里还有未结束的休息，
         * 就把服务和通知重新拉起来（进程被杀后不用等闹钟才恢复显示）。
         */
        fun ensureSynced(context: Context) {
            if (state.value != null) return
            val snapshot = RestTimerStore.load(context) ?: return
            if (snapshot.endWallMillis - System.currentTimeMillis() <= 0L) {
                RestTimerStore.clear(context)
                return
            }
            createChannel(context)
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RestTimerService::class.java).setAction(ACTION_SYNC),
                )
            }
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

        internal fun formatDuration(seconds: Int): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return if (minutes > 0) "${minutes}分${secs.toString().padStart(2, '0')}秒" else "${secs}秒"
        }
    }
}
