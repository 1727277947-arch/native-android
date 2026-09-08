package com.newsradar.app.data

/**
 * 展示给用户的“自动抓取 / 每日打板推送”时刻说明。
 * 与云端定时(Cron)保持一致：24 小时均有整点行情刷新；
 * 每日打板主推 03:30 / 12:00 / 20:00，各主推时刻后 30 分钟自动兜底补推一次。
 * 注：整点刷新已覆盖 03:30 早盘主推在 04:00 的重试。
 */
object ScheduleInfo {

    /** 自动抓取/刷新节奏（行情+国内大宗新闻） */
    fun dataRefreshChips(): List<String> = listOf(
        "行情(现货/期货)  全天每 2 小时自动刷新",
        "大宗+政策新闻    随行情一起自动抓取更新",
        "本地离线同步      每 15 分钟自动拉一次云端"
    )

    /** 每日打板主推推送时点 */
    fun mainPushTimes(): List<String> = listOf("03:30 早盘", "12:00 午盘", "20:00 晚盘")

    /** 主推脱推失败后的自动兜底补推时点（各主推后 +30 分钟）*/
    fun backstopTimes(): List<String> = listOf("04:00", "12:30", "20:30")

    fun header(): String = "· 自动刷新 · 推送时刻 ·"

    fun formattedBlock(): String {
        val sb = StringBuilder()
        sb.append("数据全天每 2 小时自动刷新一次，网络可用时到点即更新，无需手动点。\n")
        sb.append("每日打板主推：早盘 03:30 / 午盘 12:00 / 晚盘 20:00 各推一条（同类目不重复打扰）。\n")
        sb.append("云端兜底：主推时刻后 30 分钟(04:00/12:30/20:30)若当天主推还没成功，会自动补推一次。\n")
        sb.append("本地也会每 15 分钟自动同步一次云端数据。\n")
        sb.append("注：以上均为后台自动任务，无需每天手动检查。")
        return sb.toString()
    }
}