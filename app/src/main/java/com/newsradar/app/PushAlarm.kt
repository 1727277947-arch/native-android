package com.newsradar.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

/**
 * 每日打板 / 午后板（心得）的准点闹钟。
 *
 * WorkManager 在厂商省电/系统延迟下可能不按时执行，导致 11:40 午后板没能准时推。
 * 这里用 Android AlarmManager 把今天还没到的每个主推时刻各挂一条闹钟，通知逻辑由 PickEngine 统一，
 * 与 SyncWorker 共用同一套防重键，双渠道同时跑也不会重复弹。
 *
 * 精度：Android 12+ 若允许精确闹钟（用户在系统「闹钟和提醒」里为该应用开了权限）就走
 * setExactAndAllowWhileIdle；否则自动退化为较准时的 setAndAllowWhileIdle，并保留内置的多档兜底，
 * 加上 SyncWorker 每 15 分钟一跑，任一链路成功即完成当日推送、其余被防重键去重。
 */
object PushAlarm {
    const val ACTION_DUE = "com.newsradar.app.action.PICK_DUE"
    private const val EXTRA_MINUTE = "minute_of_day"

    /** 一天里的主推时刻（分钟数，0 点起）：清晨 03:30/04:00 推每日打板，11:40/12:00/12:30 推午后板，晚间 20:00/20:30 推每日打板 */
    private val DUE_MINUTES = intArrayOf(3*60+30, 4*60, 11*60+40, 12*60, 12*60+30, 20*60, 20*60+30)

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (minute in DUE_MINUTES) {
            val target = todayAtMinute(minute)
            if (target <= now) continue
            val pi = duePi(ctx, minute)
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            } else {
                // 未授予精确闹钟：退化为较准时的非精确闹钟，配合多档闹钟与 WorkManager 兜底
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
            }
        }
    }

    private fun canExact(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return am.canScheduleExactAlarms()
        return true
    }

    private fun todayAtMinute(minuteOfDay: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        c.set(java.util.Calendar.MINUTE, minuteOfDay % 60)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun duePi(ctx: Context, minute: Int): PendingIntent {
        val intent = Intent(ctx, PickReceiver::class.java).apply {
            action = ACTION_DUE
            putExtra(EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(ctx, minute, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** 闹钟到点广播：持有 wake lock 并在后台线程里执行当日时段推送（带防重），结束后释放锁 */
class PickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PushAlarm.ACTION_DUE) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "newsradar:pick")
        wake.acquire(120000)
        val pending = goAsync()
        Thread {
            try {
                PickEngine.runDue(context)
            } catch (e: Exception) {
                // 推送失败静默，等待下一档闹钟或 WorkManager 兜底
            } finally {
                try { wake.release() } catch (e: Exception) { }
                pending.finish()
            }
        }.start()
    }
}