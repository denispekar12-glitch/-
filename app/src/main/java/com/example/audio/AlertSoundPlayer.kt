package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.AlertLevel
import com.example.model.SoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class AlertSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val pendingQueue = ArrayDeque<AlertLevel>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentlyPlayingTitle = MutableStateFlow("")
    val currentlyPlayingTitle: StateFlow<String> = _currentlyPlayingTitle.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Plays a single official emergency voice recording EXACTLY ONCE.
     */
    fun playAlert(level: AlertLevel, settings: SoundSettings) {
        playAlertsSequence(listOf(level), settings)
    }

    /**
     * Plays one or more selected alert voice tracks sequentially,
     * each EXACTLY ONCE without repeating or looping.
     */
    fun playAlertsSequence(levels: List<AlertLevel>, settings: SoundSettings) {
        stopAll()

        if (!settings.autoPlayVoice || levels.isEmpty()) return

        val uniqueOrdered = levels.distinct()
        pendingQueue.clear()
        pendingQueue.addAll(uniqueOrdered)

        if (settings.vibrationEnabled) {
            triggerSingleVibration(isAlarm = uniqueOrdered.any { it.isDanger })
        }

        playNextInQueue(settings)
    }

    private fun playNextInQueue(settings: SoundSettings) {
        val nextLevel = pendingQueue.pollFirst()
        if (nextLevel == null) {
            stopAll()
            return
        }

        _isPlaying.value = true
        _currentlyPlayingTitle.value = nextLevel.title
        playVoiceResourceOnce(nextLevel.soundResId, settings) {
            playNextInQueue(settings)
        }
    }

    fun previewResource(resId: Int, title: String, settings: SoundSettings) {
        stopAll()
        _isPlaying.value = true
        _currentlyPlayingTitle.value = "Тест: $title"

        if (settings.vibrationEnabled) {
            triggerSingleVibration(isAlarm = false)
        }

        playVoiceResourceOnce(resId, settings) {
            stopAll()
        }
    }

    private fun playVoiceResourceOnce(
        resId: Int,
        settings: SoundSettings,
        onFinished: () -> Unit
    ) {
        try {
            releaseCurrentPlayerOnly()
            val player = MediaPlayer.create(context, resId)
            if (player != null) {
                mediaPlayer = player.apply {
                    val vol = settings.volume.coerceIn(0.0f, 1.0f)
                    setVolume(vol, vol)
                    // Strictly play ONCE - do not loop!
                    isLooping = false

                    setOnCompletionListener {
                        onFinished()
                    }

                    setOnErrorListener { _, _, _ ->
                        onFinished()
                        true
                    }

                    start()
                }
            } else {
                onFinished()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFinished()
        }
    }

    private fun releaseCurrentPlayerOnly() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun triggerSingleVibration(isAlarm: Boolean) {
        vibrator?.let { vib ->
            if (!vib.hasVibrator()) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = if (isAlarm) {
                        longArrayOf(0, 300, 150, 300) // 2 quick pulses
                    } else {
                        longArrayOf(0, 200) // 1 quick pulse
                    }
                    val amplitudes = if (isAlarm) {
                        intArrayOf(0, 255, 0, 255)
                    } else {
                        intArrayOf(0, 180)
                    }
                    vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    if (isAlarm) {
                        vib.vibrate(longArrayOf(0, 300, 150, 300), -1)
                    } else {
                        vib.vibrate(200)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopAll() {
        pendingQueue.clear()
        releaseCurrentPlayerOnly()

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        _isPlaying.value = false
        _currentlyPlayingTitle.value = ""
    }
}
