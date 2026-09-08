package com.newsradar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.newsradar.app.data.NewsClient
import org.json.JSONObject

/** 打板/午后主推的推送逻辑，供 SyncWorker 与精确闹钟共用（共用同一套防重键，不会重复弹）。 */
object PickEngine {
    private const val PREFS = "nr_store"
    private const val LAST_DP_SIGN = "last_dp_sign"
    private const val LAST_AFT_SIGN = "last_aft_sign"
    private const val CHANNEL_ID = "newsradar_push"

    fun currentSlotLabel(): String {
        val cal = java.util.Calendar.getInstance()
        val hm = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return when {
            hm in 180..479 -> "早"
            hm in 700..900 -> "午"
            hm >= 1200 -> "晚"
            else -> "x"
        }
    }

    /** 到点唤醒用：拉当天云端，按当前时刻推每日打板/午后板（防重） */
    fun runDue(ctx: Context) {
        val slot = currentSlotLabel()
        if (slot == "x") return
        try {
            if (slot == "午") syncAfternoonPick(ctx) else syncDailyPick(ctx, slot)
        } catch (e: Exception) {
        }
    }

    private fun pricesObj(ctx: Context): JSONObject {
        val res = com.newsradar.app.data.NewsClient.prices(ctx)
        return res.data as JSONObject
    }

    private fun syncDailyPick(ctx: Context, slot: String) {
        val obj = pricesObj(ctx)
        val dp = obj.optJSONObject("daily_pick") ?: return
        val date = dp.optString("date", "")
        val name = dp.optString("name", "")
        if (date.isBlank() || name.isBlank()) return
        val anchor = dp.optDouble("anchor", 0.0)
        if (anchor <= 0.0) return
        val sign = date + "|" + slot + "|" + anchor.toString()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_DP_SIGN, "") == sign) return
        val dir = dp.optString("dir_label", "观望")
        val range = dp.optString("day_range_pct", "0")
        val tp = dp.optDouble("tp", 0.0)
        val sl = dp.optDouble("sl", 0.0)
        val margin = dp.optDouble("est_margin", 0.0)
        val text = "今日主推 " + name + " " + dir +
                " · 波幅" + "%.2f%%".format(range.toDouble()) +
                " · 进场" + fmt(anchor) + "/止盈" + fmt(tp) + "/止损" + fmt(sl) +
                " · 一手保证金≈" + fmtN(margin) + "元"
        postDaily(ctx, name + " 打板主推·心得", text)
        prefs.edit().putString(LAST_DP_SIGN, sign).apply()
    }

    private fun syncAfternoonPick(ctx: Context) {
        val obj = pricesObj(ctx)
        val ap = obj.optJSONObject("afternoon_pick") ?: return
        val date = ap.optString("date", "")
        val name = ap.optString("name", "")
        if (date.isBlank() || name.isBlank()) return
        val anchor = ap.optDouble("anchor", 0.0)
        if (anchor <= 0.0) return
        val sign = date + "|aft|" + anchor.toString()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_AFT_SIGN, "") == sign) return
        val dir = ap.optString("dir_label", "观望")
        val range = ap.optString("day_range_pct", "0")
        val tp = ap.optDouble("tp", 0.0)
        val sl = ap.optDouble("sl", 0.0)
        val margin = ap.optDouble("est_margin", 0.0)
        val text = "午后主推 " + name + " " + dir +
                " · 波幅" + "%.2f%%".format(range.toDouble()) +
                " · 进场" + fmt(anchor) + "/止盈" + fmt(tp) + "/止损" + fmt(sl) +
                " · 一手保证金≈" + fmtN(margin) + "元"
        postAfternoon(ctx, name + " 午后打板·心得", text)
        prefs.edit().putString(LAST_AFT_SIGN, sign).apply()
    }

    private fun postDaily(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "每日打板 · 心得", NotificationManager.IMPORTANCE_HIGH))
        val pi = openIntent(ctx)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { nm.notify(1002, notif) } catch (e: Exception) {}
    }

    private fun postAfternoon(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "午后打板 · 心得", NotificationManager.IMPORTANCE_HIGH))
        val pi = openIntent(ctx)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { nm.notify(1003, notif) } catch (e: Exception) {}
    }

    private fun openIntent(ctx: Context): PendingIntent {
        return PendingIntent.getActivity(ctx, 0, Intent(ctx, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun fmt(v: Double): String = if (v == 0.0) "-" else if (v >= 1000) "%.0f".format(v) else "%.2f".format(v)
    private fun fmtN(v: Double): String = if (v == 0.0) "-" else if (v >= 1000) "%.0f".format(v) else "%.2f".format(v)
}