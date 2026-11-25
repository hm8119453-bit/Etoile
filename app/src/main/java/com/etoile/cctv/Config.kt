package com.etoile.cctv

import android.content.Context
import android.net.Uri

data class NvrConfig(
    val host: String = "",
    val port: String = "554",
    val user: String = "admin",
    val pass: String = "",
    val channels: Int = 64,
    val perPage: Int = 16,      // 4x4. كل صفحة بس هي اللي بتشتغل لايف
    val autoRotateSec: Int = 0  // 0 = تبديل يدوي
) {
    val isReady get() = host.isNotBlank() && pass.isNotBlank()

    /** stream: 1 = main, 2 = sub. Hikvision: channel N => N*100 + stream */
    fun rtsp(channel: Int, stream: Int): String =
        "rtsp://${Uri.encode(user)}:${Uri.encode(pass)}@$host:$port/Streaming/Channels/${channel * 100 + stream}"
}

object ConfigStore {
    private fun prefs(c: Context) = c.getSharedPreferences("nvr", Context.MODE_PRIVATE)

    fun load(c: Context): NvrConfig = prefs(c).run {
        NvrConfig(
            getString("host", "") ?: "", getString("port", "554") ?: "554",
            getString("user", "admin") ?: "admin", getString("pass", "") ?: "",
            getInt("channels", 64), getInt("perPage", 16), getInt("rotate", 0)
        )
    }

    fun save(c: Context, n: NvrConfig) = prefs(c).edit()
        .putString("host", n.host).putString("port", n.port)
        .putString("user", n.user).putString("pass", n.pass)
        .putInt("channels", n.channels).putInt("perPage", n.perPage)
        .putInt("rotate", n.autoRotateSec).apply()
}
