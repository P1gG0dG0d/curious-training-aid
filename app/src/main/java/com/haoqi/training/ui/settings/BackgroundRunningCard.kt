package com.haoqi.training.ui.settings

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haoqi.training.ui.components.GroupCard
import com.haoqi.training.ui.components.SectionLabel
import com.haoqi.training.ui.theme.DangerRed
import com.haoqi.training.ui.theme.SuccessGreen

/**
 * 「后台运行」设置卡片：休息倒计时离不开前台服务 + 唤醒锁 + 精确闹钟，
 * 但小米 HyperOS 默认会限制后台，需要用户自己把 App 加进白名单。
 * 这里只负责检查和跳转系统页面，不替用户偷偷改系统设置。
 */
@Composable
fun BackgroundRunningCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var ignoringBattery by remember { mutableStateOf(BackgroundSettings.isIgnoringBatteryOptimizations(context)) }
    var exactAlarmAllowed by remember { mutableStateOf(BackgroundSettings.canScheduleExactAlarms(context)) }

    // 从系统设置页返回时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoringBattery = BackgroundSettings.isIgnoringBatteryOptimizations(context)
                exactAlarmAllowed = BackgroundSettings.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SectionLabel("后台运行与提醒")
    GroupCard(modifier) {
        Text(
            if (ignoringBattery) "电池优化：已允许后台运行" else "电池优化：未放行，锁屏后容易被系统杀掉",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ignoringBattery) SuccessGreen else DangerRed,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (exactAlarmAllowed) "计时结束提醒：系统精确闹钟可用" else "计时结束提醒：精确闹钟未授权，可能延迟",
            style = MaterialTheme.typography.bodySmall,
            color = if (exactAlarmAllowed) SuccessGreen else DangerRed,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "小米 / 澎湃 OS 还要求单独允许「自启动」，并把省电策略设为「无限制」；否则锁屏几分钟后仍可能被杀。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "锁屏只看到「更多通知」时，是系统的「锁屏通知样式」选了「堆叠」：在 设置 → 锁屏 → 锁屏通知样式 里改成「列表」，就能直接看到倒计时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ignoringBattery) {
                Button(
                    onClick = { BackgroundSettings.requestIgnoreBatteryOptimizations(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("允许后台运行") }
            }
            if (!exactAlarmAllowed) {
                OutlinedButton(
                    onClick = { BackgroundSettings.requestExactAlarmPermission(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("允许精确闹钟") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { BackgroundSettings.openAutostartSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("自启动管理") }
                OutlinedButton(
                    onClick = { BackgroundSettings.openBatteryOptimizationSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("电池优化设置") }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

object BackgroundSettings {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarm.canScheduleExactAlarms()
    }

    /** 系统弹窗「允许应用在后台运行」；没这个页面就退回电池优化列表。 */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (!startSafely(context, intent)) openBatteryOptimizationSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (!startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
            if (startSafely(context, intent)) return
        }
        openAppDetails(context)
    }

    /** 小米/澎湃 OS 的自启动管理；其他品牌或系统没有这个页面时打开应用详情。 */
    fun openAutostartSettings(context: Context) {
        val miui = Intent().apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        if (startSafely(context, miui)) return
        val miuiFallback = Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        if (startSafely(context, miuiFallback)) return
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        startSafely(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
        )
    }

    private fun startSafely(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
