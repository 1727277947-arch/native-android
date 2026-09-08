package com.newsradar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {
    private lateinit var passInput: TextInputEditText
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Auth.isLoggedIn(this)) {
            ensureSyncAndPermission()
            startHome()
            return
        }
        setContentView(R.layout.activity_login)
        passInput = findViewById(R.id.login_pass)
        error = findViewById(R.id.login_error)
        findViewById<Button>(R.id.login_btn).setOnClickListener { doLogin() }
        passInput.setOnEditorActionListener { _, _, _ -> doLogin(); true }
    }

    private fun doLogin() {
        if (Auth.checkPassword(passInput.text.toString())) {
            Auth.login(this)
            ensureSyncAndPermission()
            startHome()
        } else {
            error.text = "密码错误，请重新输入"
            passInput.text?.clear()
        }
    }


    private fun ensureSyncAndPermission() {
        requestNotificationPermission()
        scheduleSync()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun scheduleSync() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(SyncWorker.PERIOD_MIN, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "newsradar_sync", ExistingPeriodicWorkPolicy.KEEP, req)
        PushAlarm.scheduleAll(this)
    }
    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
