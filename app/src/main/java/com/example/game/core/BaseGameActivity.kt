package com.example.game.core

import android.media.AudioAttributes
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.game.R

open class BaseGameActivity : ComponentActivity() {
    protected lateinit var soundPool: android.media.SoundPool
    protected var shootSoundId: Int = 0
    protected var hitSoundId: Int = 0
    protected var coinSoundId: Int = 0

    protected fun initAudio() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        shootSoundId = soundPool.load(this, R.raw.shoot, 1)
        hitSoundId = soundPool.load(this, R.raw.hit, 1)

        // Initialize background music with SoundManager
        try {
            SoundManager.initBackgroundMusic(this, R.raw.background_music)
        } catch (e: Exception) {
            Log.e("BaseGameActivity", "Media init failed: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        SoundManager.pauseBackgroundMusic()
    }

    override fun onResume() {
        super.onResume()
        // Only play if background music is enabled
        if (SoundManager.isBackgroundMusicEnabled.value) {
            SoundManager.playBackgroundMusic()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
        // Don't release SoundManager here as it's shared across activities
    }
}
