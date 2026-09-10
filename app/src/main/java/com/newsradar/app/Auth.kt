package com.newsradar.app

import android.content.Context
import java.security.MessageDigest

object Auth {
    const val USER = "yangs"
    // 仅存口令的 SHA-256，不在源码中保留明文（本类为本地单用户门禁，非服务端鉴权）
    private const val PASS_SHA256 = "6bedfdbfeb24041951b431abaea02bd89d8911e267f035528dd56538e981c336"
    private const val PREFS = "nr_auth"
    private const val KEY = "token"

    fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { String.format("%02x", it) }
    }

    private fun token(): String = USER + "|" + PASS_SHA256


    fun checkPassword(pass: String): Boolean = sha256(pass) == PASS_SHA256
    fun check(user: String, pass: String): Boolean {
        return user.trim() == USER && sha256(pass) == PASS_SHA256
    }

    fun login(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, token()).apply()
    }

    fun logout(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun isLoggedIn(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") == token()
    }
}
