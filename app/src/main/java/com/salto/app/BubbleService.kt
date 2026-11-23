package com.salto.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private var root: LinearLayout? = null
    private lateinit var lp: WindowManager.LayoutParams
    private var expanded = false
    private var bx = 0
    private var by = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val sw get() = resources.displayMetrics.widthPixels
    private val sh get() = resources.displayMetrics.heightPixels

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        bx = sw - dp(56); by = dp(260)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) collapse()
                false
            }
        }
        wm.addView(root, lp)
        render()
        store.setBubble(true)
    }

    override fun onDestroy() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        store.setBubble(false)
        super.onDestroy()
    }

    private fun pkgs() = store.favs()
        .filter { packageManager.getLaunchIntentForPackage(it) != null }
        .take(7)

    private fun panelHeight() = (pkgs().size + 2) * dp(52) + dp(20)

    private fun render() {
        val r = root ?: return
        r.removeAllViews()
        val bubble = bubbleView()
        if (!expanded) {
            r.addView(bubble); lp.x = bx; lp.y = by
        } else {
            val panel = panelView()
            val ph = panelHeight()
            val above = by + dp(56) + ph > sh - dp(24)
            if (above) {
                r.addView(panel); r.addView(bubble); lp.y = (by - ph).coerceAtLeast(0)
            } else {
                r.addView(bubble); r.addView(panel); lp.y = by
            }
            lp.x = bx
        }
        wm.updateViewLayout(r, lp)
    }

    private fun collapse() { if (expanded) { expanded = false; render() } }

    private fun toggle() {
        expanded = !expanded
        if (expanded) { bx = lp.x; by = lp.y }
        render()
    }

    private fun snap() {
        bx = if (lp.x + dp(28) < sw / 2) 0 else sw - dp(56)
        by = lp.y.coerceIn(dp(24), sh - dp(120))
        lp.x = bx; lp.y = by
        wm.updateViewLayout(root, lp)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bubbleView(): View {
        val v = FrameLayout(this)
        v.layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1F6F5C")) }
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        v.clipToOutline = true
        v.elevation = dp(4).toFloat()
        v.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_fg)
            scaleX = 1.5f; scaleY = 1.5f
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        })
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!expanded && (abs(dx) > dp(6) || abs(dy) > dp(6))) moved = true
                    if (moved) {
                        lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt()
                        wm.updateViewLayout(root, lp)
                    }
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggle() else snap() }
            }
            true
        }
        return v
    }

    private fun panelView(): View {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2162420")); cornerRadius = dp(28).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        for (pkg in pkgs()) {
            val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } ?: continue
            p.addView(tile(ImageView(this).apply { setImageDrawable(icon) }, Color.TRANSPARENT) {
                launch(this@BubbleService, pkg); collapse()
            })
        }
        p.addView(tile(label("+"), Color.parseColor("#E8A93B")) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            collapse()
        })
        p.addView(tile(label("✕"), Color.parseColor("#33FFFFFF")) { stopSelf() })
        return p
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; gravity = Gravity.CENTER; textSize = 18f; setTextColor(Color.WHITE)
    }

    private fun tile(v: View, bg: Int, onClick: () -> Unit): View {
        v.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) }
        v.background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(14).toFloat() }
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        v.clipToOutline = true
        v.setOnClickListener { onClick() }
        return v
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bubble", "Burbuja", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "bubble")
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("Salto está activo")
            .setContentText("Toca para elegir tus apps")
            .setContentIntent(pi).setOngoing(true).build()
    }

    companion object {
        fun start(c: Context) { ContextCompat.startForegroundService(c, Intent(c, BubbleService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, BubbleService::class.java)) }
    }
}
