package com.salto.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

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
    fun bubble() = p.getBoolean("bubble", false)
    fun setBubble(v: Boolean) = p.edit().putBoolean("bubble", v).apply()
    fun favs() = read("favs")
    fun recents() = read("recents")
    fun saveFavs(l: List<String>) = p.edit().putString("favs", l.joinToString(",")).apply()
    fun saveRecents(l: List<String>) = p.edit().putString("recents", l.joinToString(",")).apply()
}
