package de.ca.qcc.feature.duplicates

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ProximityFeedback {
    fun update(proximity: Int)
    fun stop()
    fun release()
}

// Pause between beeps: silent at 0, 1000 ms at 1 %, 80 ms at 100 %
fun beepIntervalMs(proximity: Int): Long? =
    if (proximity <= 0) null else 1000L - (proximity.coerceAtMost(100) - 1) * 920L / 99

class ToneProximityFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) : ProximityFeedback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var proximity = 0
    @Volatile private var released = false
    private var loop: Job? = null
    private var tone: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Synchronized
    override fun update(proximity: Int) {
        if (released) return
        this.proximity = proximity
        if (loop?.isActive != true) loop = scope.launch { beepLoop() }
    }

    @Synchronized
    override fun stop() {
        loop?.cancel()
        loop = null
        proximity = 0
    }

    @Synchronized
    override fun release() {
        released = true
        stop()
        tone?.release()
        tone = null
    }

    private suspend fun beepLoop() {
        while (currentCoroutineContext().isActive) {
            val interval = beepIntervalMs(proximity)
            if (interval == null) {
                delay(IDLE_POLL_MS)
                continue
            }
            beep()
            delay(interval)
        }
    }

    private fun beep() {
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val IDLE_POLL_MS = 100L
        const val BEEP_MS = 40
        const val VIBRATE_MS = 25L
    }
}
