package com.salto.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private var root: LinearLayout? = null
    private lateinit var lp: WindowManager.LayoutParams
    private var expanded = false
    private var hidden = false
    private var dragging = false
    private var bx = 0
    private var by = 0
    private var cur: String? = null
    private var prev: String? = null
    private var last: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val sw get() = resources.displayMetrics.widthPixels
    private val sh get() = resources.displayMetrics.heightPixels
    private val bs get() = dp(store.size())

    private val hideTask = Runnable {
        val r = root
        if (r != null && !expanded && !dragging && store.edge()) {
            hidden = true
            lp.x = if (bx < sw / 2) -bs / 2 else sw - bs / 2
            wm.updateViewLayout(r, lp)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "size" || k == "alpha" || k == "color" || k == "edge" || k == "favs") {
            if (k == "size") bx = if (bx < sw / 2) 0 else sw - bs
            render()
        }
    }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        bx = sw - bs; by = dp(260)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) collapse()
                false
            }
        }
        wm.addView(root, lp)
        store.p.registerOnSharedPreferenceChangeListener(prefListener)
        render()
        bubbleRunning.value = true
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideTask)
        if (root != null) store.p.unregisterOnSharedPreferenceChangeListener(prefListener)
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubbleRunning.value = false
        super.onDestroy()
    }

    private fun pkgs() = store.favs().filter { packageManager.getLaunchIntentForPackage(it) != null }

    private fun scheduleHide() {
        handler.removeCallbacks(hideTask)
        if (store.edge() && !expanded) handler.postDelayed(hideTask, 2500)
    }

    private fun render() {
        val r = root ?: return
        handler.removeCallbacks(hideTask)
        r.removeAllViews()
        val bubble = bubbleView()
        if (!expanded) {
            bubble.alpha = store.alpha() / 100f
            r.addView(bubble); lp.x = bx; lp.y = by; hidden = false
            scheduleHide()
        } else {
            val list = pkgs()
            val row = dp(52); val footer = row * 3; val pad = dp(12); val margin = dp(8)
            val below = sh - (by + bs) - dp(40)
            val up = by - dp(40)
            val appsH = list.size * row
            val above = appsH + footer + pad + margin > below && up > below
            val avail = if (above) up else below
            val h = appsH.coerceAtMost((avail - footer - pad - margin).coerceAtLeast(row))
            val panel = panelView(list, h)
            if (above) {
                r.addView(panel); r.addView(bubble)
                lp.y = (by - (h + footer + pad + margin)).coerceAtLeast(0)
            } else {
                r.addView(bubble); r.addView(panel); lp.y = by
            }
            lp.x = bx; hidden = false
        }
        wm.updateViewLayout(r, lp)
    }

    private fun collapse() { if (expanded) { expanded = false; render() } }

    private fun toggle() {
        expanded = !expanded
        if (expanded) {
            bx = lp.x; by = lp.y
            cur = foregroundPkg(this) ?: last
        }
        render()
    }

    private fun snap() {
        bx = if (lp.x + bs / 2 < sw / 2) 0 else sw - bs
        by = lp.y.coerceIn(dp(24), sh - dp(120))
        lp.x = bx; lp.y = by
        wm.updateViewLayout(root, lp)
        scheduleHide()
    }

    private fun roundClip(v: View) {
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        v.clipToOutline = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bubbleView(): View {
        val i = store.colorIdx().coerceIn(0, Palette.starts.lastIndex)
        val v = FrameLayout(this)
        v.layoutParams = LinearLayout.LayoutParams(bs, bs)
        v.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(Palette.starts[i], Palette.ends[i])
        ).apply { shape = GradientDrawable.OVAL; setStroke(dp(2), Color.parseColor("#80FFFFFF")) }
        roundClip(v)
        v.elevation = dp(4).toFloat()
        v.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_fg)
            scaleX = 1.1f; scaleY = 1.1f
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        })
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(hideTask)
                    if (hidden) { hidden = false; lp.x = bx; wm.updateViewLayout(root, lp) }
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!expanded && (abs(dx) > dp(6) || abs(dy) > dp(6))) { moved = true; dragging = true }
                    if (moved) {
                        lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt()
                        wm.updateViewLayout(root, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    if (!moved) toggle() else snap()
                }
                MotionEvent.ACTION_CANCEL -> { dragging = false; scheduleHide() }
            }
            true
        }
        return v
    }

    private fun goTo(pkg: String) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
        if (pkg == home) startActivity(homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else launch(this, pkg)
    }

    private fun panelView(list: List<String>, appsHeight: Int): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2162420")); cornerRadius = dp(28).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        for (pkg in list) {
            val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } ?: continue
            col.addView(tile(ImageView(this).apply { setImageDrawable(icon) }, Color.TRANSPARENT) {
                prev = cur; last = pkg
                launch(this@BubbleService, pkg)
                collapse()
            })
        }
        panel.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(col)
            layoutParams = LinearLayout.LayoutParams(dp(48), appsHeight)
        })
        val canBack = prev != null
        panel.addView(iconTile(R.drawable.ic_back, "#33FFFFFF", null, if (canBack) 1f else 0.35f) {
            val t = prev
            if (t != null) { prev = cur; last = t; goTo(t) }
            collapse()
        })
        panel.addView(iconTile(R.drawable.ic_plus, "#F2B84B", "#3A2A00", 1f) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            collapse()
        })
        panel.addView(iconTile(R.drawable.ic_close, "#33FFFFFF", null, 1f) {
            store.setWant(false); stopSelf()
        })
        return panel
    }

    private fun iconTile(res: Int, bg: String, tint: String?, a: Float, onClick: () -> Unit): View {
        val iv = ImageView(this).apply {
            setImageResource(res)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            if (tint != null) setColorFilter(Color.parseColor(tint))
            alpha = a
        }
        return tile(iv, Color.parseColor(bg), onClick)
    }

    private fun tile(v: View, bg: Int, onClick: () -> Unit): View {
        v.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(0, dp(4), 0, dp(4)) }
        v.background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(14).toFloat() }
        roundClip(v)
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
