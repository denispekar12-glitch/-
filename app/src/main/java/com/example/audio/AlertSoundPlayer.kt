package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.AlertLevel
import com.example.model.SoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class AlertSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val pendingQueue = ArrayDeque<AlertLevel>()
    private val pendingRawQueue = ArrayDeque<Int>()
    private val customAudioFiles = ConcurrentHashMap<AlertLevel, String>()
    private val playbackGeneration = AtomicLong(0L)

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

    fun updateCustomAudioPaths(paths: Map<AlertLevel, String>) {
        customAudioFiles.clear()
        paths.forEach { (level, path) ->
            if (path.isNotBlank() && File(path).exists()) {
                customAudioFiles[level] = path
            }
        }
    }

    /**
     * Copies a user-picked audio file into app internal filesDir/custom_alert_audio so it persists across restarts and days.
     */
    fun importCustomAudioFile(level: AlertLevel, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "custom_alert_audio").apply { mkdirs() }
            val target = File(dir, "${level.name.lowercase()}.audio")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (target.length() > 0L) {
                customAudioFiles[level] = target.absolutePath
                target.absolutePath
            } else {
                target.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Plays a single official or user-attached emergency voice recording EXACTLY ONCE.
     */
    fun playAlert(level: AlertLevel, settings: SoundSettings) {
        playAlertsSequence(listOf(level), settings)
    }

    /**
     * Plays explicit per-threat all-clear raw resources if provided, otherwise plays the queued AlertLevels.
     */
    fun playAlertsOrExplicitRaw(
        levels: List<AlertLevel>,
        explicitRawResIds: List<Int>,
        titleOverride: String?,
        settings: SoundSettings
    ) {
        if (explicitRawResIds.isEmpty()) {
            playAlertsSequence(levels, settings)
            return
        }
        stopAll()
        if (!settings.autoPlayVoice) return

        pendingRawQueue.clear()
        pendingRawQueue.addAll(explicitRawResIds.distinct())

        if (settings.vibrationEnabled) {
            triggerSingleVibration(isAlarm = false)
        }
        playNextRawInQueue(titleOverride ?: "Відбій загрози", settings)
    }

    private fun playNextRawInQueue(title: String, settings: SoundSettings) {
        val resId = pendingRawQueue.pollFirst()
        if (resId == null) {
            stopAll()
            return
        }
        _isPlaying.value = true
        _currentlyPlayingTitle.value = title
        playVoiceResourceOnce(resId, settings) {
            playNextRawInQueue(title, settings)
        }
    }

    /**
     * Plays one or more selected alert voice tracks sequentially,
     * each EXACTLY ONCE without repeating or looping.
     */
    fun playAlertsSequence(levels: List<AlertLevel>, settings: SoundSettings) {
        stopAll()

        if (!settings.autoPlayVoice || levels.isEmpty()) return

        // Deduplicate both by enum level and by resolved audio asset so paired levels
        // (e.g. RED + MISSILE, YELLOW + DRONE) never speak the identical track twice.
        val uniqueOrdered = levels
            .distinct()
            .distinctBy { lvl ->
                val customPath = customAudioFiles[lvl]
                if (!customPath.isNullOrBlank() && File(customPath).exists()) {
                    "FILE:$customPath"
                } else {
                    "RES:${lvl.soundResId}"
                }
            }
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
        val customPath = customAudioFiles[nextLevel]
        if (!customPath.isNullOrBlank() && File(customPath).exists()) {
            playCustomFileOnce(customPath, fallbackResId = nextLevel.soundResId, settings = settings) {
                playNextInQueue(settings)
            }
        } else {
            playVoiceResourceOnce(nextLevel.soundResId, settings) {
                playNextInQueue(settings)
            }
        }
    }

    fun previewLevel(level: AlertLevel, title: String, settings: SoundSettings) {
        stopAll()
        _isPlaying.value = true
        _currentlyPlayingTitle.value = "Тест: $title"

        if (settings.vibrationEnabled) {
            triggerSingleVibration(isAlarm = false)
        }

        val customPath = customAudioFiles[level]
        if (!customPath.isNullOrBlank() && File(customPath).exists()) {
            playCustomFileOnce(customPath, fallbackResId = level.soundResId, settings = settings) {
                stopAll()
            }
        } else {
            playVoiceResourceOnce(level.soundResId, settings) {
                stopAll()
            }
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

    private fun playCustomFileOnce(
        filePath: String,
        fallbackResId: Int,
        settings: SoundSettings,
        onFinished: () -> Unit
    ) {
        try {
            releaseCurrentPlayerOnly()
            val token = playbackGeneration.incrementAndGet()
            val vol = settings.volume.coerceIn(0.0f, 1.0f)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(filePath)
                setVolume(vol, vol)
                isLooping = false
                setOnCompletionListener {
                    if (playbackGeneration.get() == token) {
                        onFinished()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (playbackGeneration.get() == token) {
                        playVoiceResourceOnce(fallbackResId, settings, onFinished)
                    }
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            playVoiceResourceOnce(fallbackResId, settings, onFinished)
        }
    }

    private fun playVoiceResourceOnce(
        resId: Int,
        settings: SoundSettings,
        onFinished: () -> Unit
    ) {
        try {
            releaseCurrentPlayerOnly()
            val token = playbackGeneration.incrementAndGet()
            val player = MediaPlayer.create(context, resId)
            if (player != null) {
                mediaPlayer = player.apply {
                    val vol = settings.volume.coerceIn(0.0f, 1.0f)
                    setVolume(vol, vol)
                    // Strictly play ONCE - do not loop!
                    isLooping = false

                    setOnCompletionListener {
                        if (playbackGeneration.get() == token) {
                            onFinished()
                        }
                    }

                    setOnErrorListener { _, _, _ ->
                        if (playbackGeneration.get() == token) {
                            onFinished()
                        }
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
        playbackGeneration.incrementAndGet()
        pendingQueue.clear()
        pendingRawQueue.clear()
        releaseCurrentPlayerOnly()

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        _isPlaying.value = false
        _currentlyPlayingTitle.value = ""
    }
}
