package com.salto.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Immutable
data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launch(ctx: Context, pkg: String): Boolean {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { ctx.startActivity(i); true } catch (e: Exception) { false }
}

class Store(ctx: Context) {
    val p = ctx.getSharedPreferences("salto", Context.MODE_PRIVATE)
    private fun read(k: String) = p.getString(k, "")!!.split(",").filter { it.isNotBlank() }
    fun want() = p.getBoolean("want", false)
    fun setWant(v: Boolean) = p.edit().putBoolean("want", v).apply()
    fun size() = p.getInt("size", 56)
    fun setSize(v: Int) = p.edit().putInt("size", v).apply()
    fun alpha() = p.getInt("alpha", 100)
    fun setAlpha(v: Int) = p.edit().putInt("alpha", v).apply()
    fun colorIdx() = p.getInt("color", 0)
    fun setColor(v: Int) = p.edit().putInt("color", v).apply()
    fun edge() = p.getBoolean("edge", false)
    fun setEdge(v: Boolean) = p.edit().putBoolean("edge", v).apply()
    fun bubble() = p.getBoolean("bubble", false)
    fun setBubble(v: Boolean) = p.edit().putBoolean("bubble", v).apply()
    fun favs() = read("favs")
    fun recents() = read("recents")
    fun saveFavs(l: List<String>) = p.edit().putString("favs", l.joinToString(",")).apply()
    fun saveRecents(l: List<String>) = p.edit().putString("recents", l.joinToString(",")).apply()
}

object Palette {
    val names = listOf("Pino", "Océano", "Violeta", "Coral", "Ámbar", "Grafito")
    val starts = intArrayOf(0xFF2FA68C.toInt(), 0xFF3B82F6.toInt(), 0xFFA78BFA.toInt(), 0xFFFB7185.toInt(), 0xFFFBBF24.toInt(), 0xFF64748B.toInt())
    val ends = intArrayOf(0xFF0F3A31.toInt(), 0xFF1E3A8A.toInt(), 0xFF4C1D95.toInt(), 0xFF9F1239.toInt(), 0xFF92400E.toInt(), 0xFF0F172A.toInt())
}

@Suppress("DEPRECATION")
fun hasUsage(c: Context): Boolean {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
        AppOpsManager.MODE_ALLOWED
}

@Suppress("DEPRECATION")
fun foregroundPkg(c: Context): String? {
    if (!hasUsage(c)) return null
    val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val ev = um.queryEvents(now - 2 * 3600_000L, now)
    val e = UsageEvents.Event()
    var last: String? = null
    while (ev.hasNextEvent()) {
        ev.getNextEvent(e)
        if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
            e.packageName != c.packageName && e.packageName != "com.android.systemui"
        ) last = e.packageName
    }
    return last
}
