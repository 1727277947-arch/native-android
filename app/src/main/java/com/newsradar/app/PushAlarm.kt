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

    /**
     * 挂齐从现在起未来 48 小时内的所有主推时刻。
     *
     * 必须跨日：原来只挂"今天还没到"的时刻，过了零点第二天的闹钟根本不存在，
     * 于是推送依赖"用户每天至少打开一次 App"。改为覆盖今天剩余 + 明天的全部时刻后，
     * 只要闹钟被触发过一次（或开机重挂过），链条就能自己续下去。
     */
    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (dayOffset in 0..1) {
            for (minute in DUE_MINUTES) {
                val target = timeAtMinute(dayOffset, minute)
                if (target <= now) continue
                val pi = duePi(ctx, minute, dayOffset)
                if (canExact(am)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
                } else {
                    // 未授予精确闹钟：退化为较准时的非精确闹钟，配合多档闹钟与 WorkManager 兜底
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi)
                }
            }
        }
    }

    private fun canExact(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return am.canScheduleExactAlarms()
        return true
    }

    /** 是否已加入系统电池优化白名单（未加入时厂商省电策略会把闹钟标成 frozen 而不触发）。 */
    fun isBatteryExempt(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * 引导用户把本应用加入电池优化白名单。
     *
     * vivo / OPPO / 小米等厂商的省电策略会直接把 AlarmManager 闹钟冻结（dumpsys alarm 里
     * Reason=frozen），表现就是"闹钟明明挂着却一声不响"。这是唯一能真正解冻的开关，
     * 必须在用户可见的界面里主动弹一次，否则打板推送永远靠不住。
     */
    fun requestBatteryExempt(ctx: Context) {
        if (isBatteryExempt(ctx)) return
        try {
            val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = android.net.Uri.parse("package:" + ctx.packageName)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            try {
                val i = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e2: Exception) { }
        }
    }
    private fun timeAtMinute(dayOffset: Int, minuteOfDay: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        c.set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        c.set(java.util.Calendar.MINUTE, minuteOfDay % 60)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun duePi(ctx: Context, minute: Int, dayOffset: Int): PendingIntent {
        val intent = Intent(ctx, PickReceiver::class.java).apply {
            action = ACTION_DUE
            putExtra(EXTRA_MINUTE, minute)
        }
        // requestCode 必须带 dayOffset，否则明天的闹钟会覆盖掉今天的同一个 PendingIntent
        return PendingIntent.getBroadcast(ctx, minute + dayOffset * 10000, intent,
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
                // 每触发一次就补挂未来 48 小时，保证跨日不断链（不依赖用户打开 App）
                try { PushAlarm.scheduleAll(context) } catch (e: Exception) { }
                try { wake.release() } catch (e: Exception) { }
                pending.finish()
            }
        }.start()
    }
}


/** 开机/应用更新后重挂闹钟：AlarmManager 的闹钟在重启后会被系统清空，
 *  原来没有这个 receiver，导致重启后必须手动打开 App 才会重新排推送。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == "android.intent.action.QUICKBOOT_POWERON" ||
            a == "com.htc.intent.action.QUICKBOOT_POWERON") {
            try {
                PushAlarm.scheduleAll(context)
            } catch (e: Exception) {
                // 静默：下一次打开 App 仍会重挂
            }
        }
    }
}
