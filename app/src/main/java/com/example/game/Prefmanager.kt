package com.example.game

import android.content.Context

object PrefManager {
    private const val PREF_NAME = "game_prefs"
    private const val KEY_PLAYER_NAME = "player_name"
    private const val KEY_PLAYER_DOC_ID = "player_doc_id"

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
}
