package com.mineserve.mobile.data
import android.app.ActivityManager
import android.content.Context

object DeviceMemory {
    fun read(context: Context): DeviceProfile {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return DeviceProfile(
            (info.totalMem / (1024 * 1024)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            (info.availMem / (1024 * 1024)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            Runtime.getRuntime().availableProcessors(), manager.isLowRamDevice || info.lowMemory)
    }
}
