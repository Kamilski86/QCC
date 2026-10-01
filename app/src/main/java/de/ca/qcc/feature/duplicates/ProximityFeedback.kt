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

/** Owner must call release() when done. */
interface ProximityFeedback {
    fun update(proximity: Int)
    fun stop()
    fun release()

    // Result of a confirmed find: distinct from the proximity beeps so it is noticed without looking
    fun resultTone(success: Boolean) = Unit
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

    override fun resultTone(success: Boolean) {
        if (released) return
        scope.launch {
            val generator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull()
            runCatching {
                generator?.startTone(if (success) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, RESULT_TONE_MS)
            }
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(RESULT_VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            delay(RESULT_TONE_MS + 100L)
            generator?.release()
        }
    }

    @Synchronized
    override fun release() {
        released = true
        stop()
    }

    // The loop owns the ToneGenerator: created lazily here, released when the loop ends.
    private suspend fun beepLoop() {
        var generator: ToneGenerator? = null
        try {
            while (currentCoroutineContext().isActive) {
                val interval = beepIntervalMs(proximity)
                if (interval == null) {
                    // Silent: end the loop; update() restarts it. Decide under the lock so a
                    // concurrent update() cannot be lost between the check and the exit.
                    val exit = synchronized(this) {
                        if (beepIntervalMs(proximity) == null) {
                            loop = null
                            true
                        } else false
                    }
                    if (exit) return
                    continue
                }
                if (generator == null) {
                    generator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
                }
                beep(generator)
                delay(interval)
            }
        } finally {
            generator?.release()
        }
    }

    private fun beep(generator: ToneGenerator?) {
        runCatching { generator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val BEEP_MS = 40
        const val VIBRATE_MS = 25L
        const val RESULT_TONE_MS = 300
        const val RESULT_VIBRATE_MS = 150L
    }
}
