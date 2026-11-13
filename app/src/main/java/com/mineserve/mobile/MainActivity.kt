package com.mineserve.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mineserve.mobile.service.McForegroundService
import com.mineserve.mobile.ui.McApp
import com.mineserve.mobile.ui.theme.AppUiSettings
import com.mineserve.mobile.ui.theme.MineServeMobileTheme
import com.mineserve.mobile.ui.theme.horizonGlassTokensFor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    // Android 13+ runtime notification permission
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUiSettings.ensureLoaded(this)
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

        val initialGlass = horizonGlassTokensFor(AppUiSettings.colorPreset.value)
        enableEdgeToEdge(
            statusBarStyle = if (initialGlass.isLight) {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (initialGlass.isLight) {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        lifecycleScope.launch {
            AppUiSettings.colorPreset.collect { preset ->
                val glass = horizonGlassTokensFor(preset)
                window.setBackgroundDrawable(ColorDrawable(glass.skyTop.toArgb()))
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = glass.isLight
                    isAppearanceLightNavigationBars = glass.isLight
                }
            }
        }

        ensureNotificationPermission()
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
