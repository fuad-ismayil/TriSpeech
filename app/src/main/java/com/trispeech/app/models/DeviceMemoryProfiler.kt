package com.trispeech.app.models

import android.app.ActivityManager
import android.content.Context
import com.trispeech.app.transcription.ModelTier

object DeviceMemoryProfiler {
    const val GIB = 1024L * 1024L * 1024L
    fun recommend(totalMemoryBytes: Long): ModelTier = when {
        totalMemoryBytes < 4L * GIB -> ModelTier.MAXIMUM_SPEED
        totalMemoryBytes <= 6L * GIB -> ModelTier.BEST_BALANCE
        else -> ModelTier.MAXIMUM_QUALITY
    }

    fun totalMemory(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }
}
