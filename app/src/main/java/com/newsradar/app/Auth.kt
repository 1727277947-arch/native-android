package com.newsradar.app

import android.content.Context
import java.security.MessageDigest

object Auth {
    const val USER = "yangs"
    private const val PASS = "15251239086"
    private const val PREFS = "nr_auth"
    private const val KEY = "token"

    fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { String.format("%02x", it) }
    }

    private fun token(): String = USER + "|" + sha256(PASS)


    fun checkPassword(pass: String): Boolean = sha256(pass) == sha256(PASS)
    fun check(user: String, pass: String): Boolean {
        return user.trim() == USER && sha256(pass) == sha256(PASS)
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
