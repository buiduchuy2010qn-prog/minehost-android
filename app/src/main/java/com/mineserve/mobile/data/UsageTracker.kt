package com.mineserve.mobile.data

import android.content.Context

/**
 * Upstream usage pulse is deliberately disabled in MineHost.
 * No Android ID is read or sent, and no analytics endpoint is contacted here.
 */
object UsageTracker {
    @Suppress("UNUSED_PARAMETER")
    fun maybePulse(context: Context) = Unit
}
