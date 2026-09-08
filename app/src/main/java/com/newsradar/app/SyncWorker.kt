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
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.newsradar.app.data.NewsClient
import com.newsradar.app.data.RefreshLog
import com.newsradar.app.data.NewsItem

class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val res = NewsClient.news(ctx)
            val arr = res.data as org.json.JSONArray
            val items = mutableListOf<NewsItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(NewsItem(
                    o.optString("title"), o.optString("url"), o.optString("summary"),
                    o.optString("pub_time"), o.optString("source"), o.optInt("score", 0),
                    o.optString("priority"), o.optString("topic"), emptyList<String>(),
                    o.optBoolean("is_elite"), o.optString("dedup_key"), null,
                    o.optString("en_title", o.optString("title")), emptyList()
                ))
            }
            RefreshLog.record(ctx, "自动同步", true, res.live, items.size, 0, "")
            PickEngine.runDue(ctx)
            val newItems = findNew(ctx, items)
            if (newItems.isNotEmpty()) {
                postNotification(ctx, newItems.first())
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun findNew(ctx: Context, items: List<NewsItem>): List<NewsItem> {
        val seen = seenSet(ctx)
        val out = mutableListOf<NewsItem>()
        val comm = setOf("贵金属", "能源", "基本金属", "黑色系", "农产品", "综合")
        val sorted = items.sortedWith(compareBy({ !comm.contains(it.topic) }, { it.priority != "P1" }, { it.pubTime }))
        for (it in sorted) {
            val key = it.dedupKey.ifBlank { it.title }
            if (key.isBlank()) continue
            if (key in seen) continue
            seen.add(key)
            // 优先推送大宗商品(P1)，其次国内政策；每次最多2条
            if (it.priority == "P1" && out.size < 2) {
                out.add(it)
            }
        }
        persistSeen(ctx, seen)
        return out
    }

    private fun seenSet(ctx: Context): MutableSet<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SEEN, "[]")
        val set = mutableSetOf<String>()
        try {
            val a = org.json.JSONArray(raw)
            for (i in 0 until a.length()) set.add(a.optString(i))
        } catch (e: Exception) {}
        return set
    }

    private fun persistSeen(ctx: Context, set: Set<String>) {
        val a = org.json.JSONArray()
        // 只保留最近 200 条，避免无限增长
        val recent = set.toList().takeLast(200)
        for (k in recent) a.put(k)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SEEN, a.toString()).apply()
    }

    private fun postNotification(ctx: Context, item: NewsItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "心得", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)

        val open = Intent(ctx, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("url", item.url)
            putExtra("title", item.title)
            putExtra("summary", item.summary)
        }
        val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val text = item.summary.ifBlank { item.title }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(item.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try { nm.notify(1001, notif) } catch (e: Exception) {}
    }

    companion object {
        private const val PREFS = "nr_store"
        private const val SEEN = "seen_keys"
        private const val CHANNEL_ID = "newsradar_push"
        const val PERIOD_MIN = 15L
    }
}
