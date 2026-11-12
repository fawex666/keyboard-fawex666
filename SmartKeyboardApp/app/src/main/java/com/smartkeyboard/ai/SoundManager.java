package com.smartkeyboard.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.preference.PreferenceManager;

public class SoundManager {

    private final Context context;
    private SoundPool soundPool;
    private int customSoundId = 0;
    private boolean isUsingCustomSound = false;
    private String customUriString = null;
    private Vibrator vibrator;

    public SoundManager(Context context) {
        this.context = context;
        initVibrator();
        initSoundPool();
        reloadSoundPreferences();
    }

    private void initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Exception ignored) {}
    }

    private void initSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(audioAttributes)
                .build();
    }

    public void reloadSoundPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        customUriString = prefs.getString("custom_sound_uri", null);

        if (customUriString != null && !customUriString.trim().isEmpty()) {
            try {
                Uri uri = Uri.parse(customUriString);
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    customSoundId = soundPool.load(pfd.getFileDescriptor(), 0, pfd.getStatSize(), 1);
                    pfd.close();
                    isUsingCustomSound = true;
                }
            } catch (Exception e) {
                isUsingCustomSound = false;
            }
        } else {
            isUsingCustomSound = false;
        }
    }

    public void playKeyClick() {
        // Haptic feedback
        try {
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(12);
                }
            }
        } catch (Exception ignored) {}

        // Sound playback
        try {
            if (isUsingCustomSound && customSoundId != 0 && soundPool != null) {
                soundPool.play(customSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
            } else if (customUriString != null && !customUriString.trim().isEmpty()) {
                playViaMediaPlayer(Uri.parse(customUriString));
            }
        } catch (Exception ignored) {}
    }

    private void playViaMediaPlayer(Uri uri) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(context, uri);
            mp.prepare();
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } catch (Exception ignored) {}
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
