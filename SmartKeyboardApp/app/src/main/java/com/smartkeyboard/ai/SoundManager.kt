package com.smartkeyboard.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.preference.PreferenceManager

class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var defaultClickSoundId: Int = 0
    private var customSoundId: Int = 0
    private var isUsingCustomSound: Boolean = false
    private var customUriString: String? = null

    private val vibrator: Vibrator? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        initSoundPool()
        reloadSoundPreferences()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun reloadSoundPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        customUriString = prefs.getString("custom_sound_uri", null)

        if (!customUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(customUriString)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    soundPool?.let { sp ->
                        customSoundId = sp.load(pfd.fileDescriptor, 0, pfd.statSize, 1)
                        isUsingCustomSound = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isUsingCustomSound = false
            }
        } else {
            isUsingCustomSound = false
        }
    }

    fun playKeyClick() {
        // Haptic Feedback
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (_: Exception) {}

        // Sound Playback
        try {
            if (isUsingCustomSound && customSoundId != 0) {
                soundPool?.play(customSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                // If custom sound is loaded via MediaPlayer fallback
                if (!customUriString.isNullOrBlank()) {
                    playViaMediaPlayer(Uri.parse(customUriString))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playViaMediaPlayer(uri: Uri) {
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context, uri)
            mp.prepare()
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
