package com.example.game.core

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableStateOf

/**
 * Sound Manager - Quản lý âm thanh background và sound effects
 */
object SoundManager {
    // States for sound settings
    var isBackgroundMusicEnabled = mutableStateOf(true)
    var isSoundEffectsEnabled = mutableStateOf(true)

    private var backgroundMusic: MediaPlayer? = null
    private var currentMusicResId: Int = -1

    // Background music volume (0.0 to 1.0)
    private var backgroundMusicVolume = 0.5f

    // Sound effects volume (0.0 to 1.0)
    private var soundEffectsVolume = 0.5f

    /**
     * Initialize background music
     */
    fun initBackgroundMusic(context: Context, musicResId: Int) {
        if (currentMusicResId != musicResId) {
            stopBackgroundMusic()
            backgroundMusic = MediaPlayer.create(context, musicResId).apply {
                isLooping = true
                setVolume(backgroundMusicVolume, backgroundMusicVolume)
            }
            currentMusicResId = musicResId
        }

        if (isBackgroundMusicEnabled.value) {
            playBackgroundMusic()
        }
    }

    /**
     * Play background music
     */
    fun playBackgroundMusic() {
        if (isBackgroundMusicEnabled.value && backgroundMusic?.isPlaying == false) {
            backgroundMusic?.start()
        }
    }

    /**
     * Pause background music
     */
    fun pauseBackgroundMusic() {
        backgroundMusic?.pause()
    }

    /**
     * Stop background music
     */
    fun stopBackgroundMusic() {
        backgroundMusic?.stop()
        backgroundMusic?.release()
        backgroundMusic = null
        currentMusicResId = -1
    }

    /**
     * Toggle background music on/off
     */
    fun toggleBackgroundMusic() {
        isBackgroundMusicEnabled.value = !isBackgroundMusicEnabled.value
        if (isBackgroundMusicEnabled.value) {
            playBackgroundMusic()
        } else {
            pauseBackgroundMusic()
        }
    }

    /**
     * Toggle sound effects on/off
     */
    fun toggleSoundEffects() {
        isSoundEffectsEnabled.value = !isSoundEffectsEnabled.value
    }

    /**
     * Play sound effect with volume control
     */
    fun playSoundEffect(soundPool: android.media.SoundPool, soundId: Int, volume: Float = 1.0f) {
        if (isSoundEffectsEnabled.value) {
            val effectiveVolume = volume * soundEffectsVolume
            soundPool.play(soundId, effectiveVolume, effectiveVolume, 1, 0, 1f)
        }
    }

    /**
     * Set background music volume
     */
    fun setBackgroundMusicVolume(volume: Float) {
        backgroundMusicVolume = volume.coerceIn(0f, 1f)
        backgroundMusic?.setVolume(backgroundMusicVolume, backgroundMusicVolume)
    }

    /**
     * Set sound effects volume
     */
    fun setSoundEffectsVolume(volume: Float) {
        soundEffectsVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * Clean up resources
     */
    fun release() {
        stopBackgroundMusic()
    }
}
