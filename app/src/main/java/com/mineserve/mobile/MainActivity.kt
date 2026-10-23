package com.mineserve.mobile

import android.util.Log
import android.view.WindowManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mineserve.mobile.ui.McApp
import com.mineserve.mobile.ui.theme.MineServeMobileTheme
import com.mineserve.mobile.service.McForegroundService

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    // Android 13+ 通知权限运行时申请
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户拒绝仅影响通知可见性，前台服务仍可保活 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val repo = McApplication.get(this@MainActivity).repository
            combine(repo.configFlow, repo.serverState) { config, server ->
                config.keepScreenOnWhileRunning && server.isRunning
            }.distinctUntilChanged().collect { keepOn ->
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 三键导航默认强制半透明对比度遮罩，关闭后才是全透明
            window.isNavigationBarContrastEnforced = false
        }
        ensureNotificationPermission()
        // 高刷新率：见 applyHighestRefreshRate() 的说明
        // Let Android choose the display mode.
        // 更新通知点击进入：通知带 open_update extra，通知 MainActivity 打开更新对话框
        if (intent?.getBooleanExtra("open_update", false) == true) {
            McApplication.get().requestOpenUpdate()
        }
        setContent {
            MineServeMobileTheme {
                McApp()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val config = McApplication.get(this@MainActivity).repository.configFlow.first()
            if (config.keepStatusOverlay) {
                // 授权页返回时服务可能还没完成启动，确保服务存在并让它重新挂载悬浮窗。
                if (McForegroundService.isRunning) {
                    startService(Intent(this@MainActivity, McForegroundService::class.java).apply {
                        action = McForegroundService.ACTION_REFRESH_KEEP_ALIVE
                    })
                } else {
                    McApplication.get(this@MainActivity).startForegroundService(
                        Intent(this@MainActivity, McForegroundService::class.java).apply {
                            action = McForegroundService.ACTION_START
                        }
                    )
                }
            } else if (McForegroundService.isRunning) {
                startService(Intent(this@MainActivity, McForegroundService::class.java).apply {
                    action = McForegroundService.ACTION_REFRESH_KEEP_ALIVE
                })
            }
        }
    }
}

