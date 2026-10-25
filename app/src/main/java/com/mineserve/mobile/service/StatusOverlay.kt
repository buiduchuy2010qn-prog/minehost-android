package com.mineserve.mobile.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mineserve.mobile.MainActivity
import kotlin.math.roundToInt

/**
 * [v2.8.3] Bong Bóng Menu Nổi Admin Trong Game (Floating In-Game Admin Overlay):
 * - Thu gọn: Hiển thị trực tiếp CPU % & RAM MB, kéo thả tự do trên màn hình khi đang chơi Minecraft PE / PojavLauncher.
 * - Chạm nhẹ vào bong bóng: Bung ra Menu Điều Khiển Nhanh 6 nút ngay đè lên màn hình game mà không cần thoát game!
 */
internal object StatusOverlay {
    private var rootView: LinearLayout? = null
    private var infoText: TextView? = null
    private var menuPanel: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var attachPending = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    @Synchronized
    fun show(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (!attachPending) {
                attachPending = true
                mainHandler.post {
                    synchronized(this) { attachPending = false }
                    show(context)
                }
            }
            return
        }
        if (rootView != null) return
        if (!canDraw(context)) {
            Log.w(TAG, "Overlay permission is not granted")
            return
        }
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = appCtx.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(10).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xEE0B1519.toInt())
                setStroke(dp(1), 0xFF10B981.toInt())
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        // Thanh tiêu đề bong bóng (hiển thị CPU / RAM + mũi tên đóng/mở)
        val headerRow = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dotView = View(appCtx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF10B981.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) }
        }
        val titleTv = TextView(appCtx).also { infoText = it }.apply {
            text = "⚡ SV CPU:--% | RAM:--M ▾"
            setTextColor(0xFFECFDF5.toInt())
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        }
        headerRow.addView(dotView)
        headerRow.addView(titleTv)
        container.addView(headerRow)

        // Khung Menu Admin mở rộng khi chạm vào bong bóng
        val panel = LinearLayout(appCtx).also { menuPanel = it }.apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(6), 0, dp(2))
        }

        fun createQuickBtn(label: String, bgColor: Int, onClick: () -> Unit): TextView {
            return TextView(appCtx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 10.5f
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(bgColor)
                }
                layoutParams = LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                }
                setOnClickListener { onClick() }
            }
        }

        fun dispatchServiceAction(action: String, toastMsg: String) {
            runCatching {
                val intent = Intent(appCtx, McForegroundService::class.java).setAction(action)
                if (McForegroundService.isRunning) appCtx.startService(intent)
                else appCtx.startForegroundService(intent)
                Toast.makeText(appCtx, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }

        val row1 = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
            addView(createQuickBtn("☀️ Trời Sáng", 0xFF0284C7.toInt()) {
                dispatchServiceAction(McForegroundService.ACTION_QUICK_DAY_SUN, "☀️ Đã chỉnh Trời Sáng & Tạnh Mưa!")
            })
            addView(createQuickBtn("🧹 Dọn Lag", 0xFF059669.toInt()) {
                dispatchServiceAction(McForegroundService.ACTION_QUICK_CLEAR_LAG, "🧹 Đang dọn rác & lưu Map!")
            })
        }

        val row2 = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
            addView(createQuickBtn("❤️ Hồi Máu @a", 0xFFE11D48.toInt()) {
                dispatchServiceAction(McForegroundService.ACTION_QUICK_HEAL_ALL, "❤️ Đã hồi đầy máu & thức ăn cho cả Server!")
            })
            addView(createQuickBtn("📋 Copy IP", 0xFF0D9488.toInt()) {
                dispatchServiceAction(McForegroundService.ACTION_QUICK_COPY_IP, "📋 Đã sao chép IP Server!")
            })
        }

        val row3 = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createQuickBtn("🏠 Mở App", 0xFF4F46E5.toInt()) {
                toggleExpand(wm, dp(220), false)
                appCtx.startActivity(
                    Intent(appCtx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            })
            addView(createQuickBtn("🛑 Lưu & Tắt", 0xFFDC2626.toInt()) {
                toggleExpand(wm, dp(220), false)
                dispatchServiceAction(McForegroundService.ACTION_QUICK_SAVE_STOP, "🛑 Đang lưu Map & tắt Server an toàn...")
            })
        }

        panel.addView(row1)
        panel.addView(row2)
        panel.addView(row3)
        container.addView(panel)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(90)
        }

        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        headerRow.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = originX + (event.rawX - downX).roundToInt()
                    params.y = originY + (event.rawY - downY).roundToInt()
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - downX) < dp(8) &&
                        kotlin.math.abs(event.rawY - downY) < dp(8)
                    ) {
                        toggleExpand(wm, dp(220), !expanded)
                    }
                    true
                }
                else -> true
            }
        }

        runCatching {
            wm.addView(container, params)
            rootView = container
            layoutParams = params
            expanded = false
        }.onFailure { Log.w(TAG, "Unable to attach status overlay", it) }
    }

    private fun toggleExpand(wm: WindowManager, expandedWidthPx: Int, wantExpand: Boolean) {
        val container = rootView ?: return
        val panel = menuPanel ?: return
        val params = layoutParams ?: return
        expanded = wantExpand
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
        params.width = if (expanded) expandedWidthPx else WindowManager.LayoutParams.WRAP_CONTENT
        val curText = infoText?.text?.toString().orEmpty()
        if (curText.isNotBlank()) {
            val base = curText.removeSuffix(" ▾").removeSuffix(" ▴")
            infoText?.text = "$base ${if (expanded) "▴" else "▾"}"
        }
        runCatching { wm.updateViewLayout(container, params) }
    }

    fun update(cpuPercent: Int?, memoryMb: Long) {
        mainHandler.post {
            val arrow = if (expanded) "▴" else "▾"
            val cpuStr = cpuPercent?.let { "$it%" } ?: "--%"
            val ramStr = if (memoryMb > 0) "${memoryMb}M" else "--M"
            infoText?.text = "⚡ SV CPU:$cpuStr | RAM:$ramStr $arrow"
        }
    }

    @Synchronized
    fun hide(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide(context) }
            return
        }
        val target = rootView ?: return
        rootView = null
        infoText = null
        menuPanel = null
        layoutParams = null
        expanded = false
        runCatching {
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .removeView(target)
        }
    }

    private const val TAG = "MineHostOverlay"
}
