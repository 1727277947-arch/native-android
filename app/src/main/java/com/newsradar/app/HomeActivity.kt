package com.newsradar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.TimeUnit
import com.newsradar.app.ui.AccountFragment
import com.newsradar.app.ui.ForeignFragment
import com.newsradar.app.ui.PricesFragment

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        requestNotificationPermission()
        scheduleSync()
        ensureBatteryExempt()

        // 从推送通知进入时，直接打开对应文章
        val pushUrl = intent?.getStringExtra("url")
        val pushTitle = intent?.getStringExtra("title")
        val pushSummary = intent?.getStringExtra("summary")
        if (!pushUrl.isNullOrBlank()) {
            ArticleActivity.open(this, pushTitle ?: "文章阅读", pushUrl, pushSummary ?: "")
            // 清除，避免下次从任务返回再次弹
            intent?.removeExtra("url")
        } else if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NewsFragment())
                .commit()
        }

        findViewById<BottomNavigationView>(R.id.bottom_nav).setOnItemSelectedListener { item ->
            val f = when (item.itemId) {
                R.id.nav_prices -> PricesFragment()
                R.id.nav_foreign -> ForeignFragment()
                R.id.nav_account -> AccountFragment()
                else -> NewsFragment()
            }
            if (supportFragmentManager.findFragmentById(R.id.fragment_container)?.javaClass != f.javaClass) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .commit()
            }
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pushUrl = intent.getStringExtra("url")
        val pushTitle = intent.getStringExtra("title")
        val pushSummary = intent.getStringExtra("summary")
        if (!pushUrl.isNullOrBlank()) {
            ArticleActivity.open(this, pushTitle ?: "文章阅读", pushUrl, pushSummary ?: "")
            intent.removeExtra("url")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun scheduleSync() {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(SyncWorker.PERIOD_MIN, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("newsradar_sync", ExistingPeriodicWorkPolicy.KEEP, periodic)

        // 本地 30 分钟兑底：每日主推(03:30/12:00/20:00)后 +30 分钟各触发一次 SyncWorker，
        // 使得即便当天主推在到点那次未成功，+30min 也会自动补推。(呈底重复驱动由 SyncWorker 内容规待重推控制)
        backstopDailyPicks()
        PushAlarm.scheduleAll(this)
        ensureBatteryExempt()
    }

    /** 为未来今日的每个主推时段置执行 +30min OneTime 兑底 */
    private fun backstopDailyPicks() {
        val now = java.util.Calendar.getInstance()
        val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val slots = intArrayOf(3*60+30, 12*60, 20*60)   // 早/午/晚主推时刻(min-of-day)
        for (slot in slots) {
            val target = slot + 30
            if (nowMin >= target) continue
            val delayMin = (target - nowMin).toLong()
            if (delayMin <= 0) continue
            val one = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(delayMin, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniqueWork("dp_bs_" + target, ExistingWorkPolicy.REPLACE, one)
        }
        // 云端每日北京 11:40 重算当天午后板，本场同一分钟唤醒一次后达到推通知
        val md = 11 * 60 + 40
        if (nowMin < md) {
            val d0 = (md - nowMin).toLong()
            val wake = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(d0, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniqueWork("aft_noon", ExistingWorkPolicy.REPLACE, wake)
        }
    }

    /**
     * vivo / OPPO / 小米等厂商的省电策略会把 AlarmManager 闹钟冻结（dumpsys alarm 里显示 Reason=frozen），
     * 表现就是闹钟挂着却不响、午后板不推送。这里在启动时弹一次系统授权，让用户把 App 加入
     * 电池优化白名单；只有用户点过"允许"，闹钟才真正能到点触发。用 SharedPreferences 只提示一次，
     * 后续若仍未加白名单，则每次冷启动都再提醒一次，直到真正生效为止。
     */
    private fun ensureBatteryExempt() {
        try {
            if (PushAlarm.isBatteryExempt(this)) return
            val sp = getSharedPreferences("newsradar_prefs", MODE_PRIVATE)
            val last = sp.getLong("battery_prompt_at", 0L)
            val nowMs = System.currentTimeMillis()
            // 已提示过且在 12 小时内，不重复弹（避免每次回前台都打扰）
            if (nowMs - last < 12 * 60 * 60 * 1000L) return
            sp.edit().putLong("battery_prompt_at", nowMs).apply()
            PushAlarm.requestBatteryExempt(this)
        } catch (e: Exception) {
            // 静默：拿不到就等用户手动在系统设置里开
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时重新注册当天剩余时段闹钟（跨日或闹钟被系统清掉时也能及时补上）
        PushAlarm.scheduleAll(this)
    }
}