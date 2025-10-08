package com.example.game

import android.content.Context

object PrefManager {
    private const val PREF_NAME = "game_prefs"
    private const val KEY_PLAYER_NAME = "player_name"
    private const val KEY_PLAYER_DOC_ID = "player_doc_id"
    private const val KEY_TOTAL_SCORE = "total_score"
    private const val KEY_CURRENT_SESSION_SCORE = "current_session_score"

    fun savePlayerName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PLAYER_NAME, name).apply()
    }

    fun getPlayerName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PLAYER_NAME, null)
    }

    fun savePlayerDocId(context: Context, docId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PLAYER_DOC_ID, docId).apply()
    }

    fun getPlayerDocId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PLAYER_DOC_ID, null)
    }

    // Score management functions
    fun saveTotalScore(context: Context, score: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TOTAL_SCORE, score).apply()
    }

    fun getTotalScore(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TOTAL_SCORE, 0)
    }

    fun saveCurrentSessionScore(context: Context, score: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CURRENT_SESSION_SCORE, score).apply()
    }

    fun getCurrentSessionScore(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CURRENT_SESSION_SCORE, 0)
    }

    fun addToTotalScore(context: Context, scoreToAdd: Int): Int {
        val currentScore = getTotalScore(context)
        val newScore = currentScore + scoreToAdd
        saveTotalScore(context, newScore)
        return newScore
    }

    fun addToSessionScore(context: Context, scoreToAdd: Int): Int {
        val currentScore = getCurrentSessionScore(context)
        val newScore = currentScore + scoreToAdd
        saveCurrentSessionScore(context, newScore)
        return newScore
    }

    fun resetSessionScore(context: Context) {
        saveCurrentSessionScore(context, 0)
    }
}
