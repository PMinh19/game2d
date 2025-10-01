package com.example.game

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class ChestItem(val name: String, val resId: Int)

object FirebaseHelper {
    private val db = FirebaseFirestore.getInstance()

    private fun resolveResIdFromName(name: String): Int {
        return when (name) {
            "Fireworks" -> R.drawable.fireworks
            "Firework2" -> R.drawable.firework2
            "Bom", "Bomb" -> R.drawable.bom1
            "Shield", "Khiên" -> R.drawable.shield1
            "Time", "Đồng hồ", "Clock" -> R.drawable.time
            "Wall", "Tường" -> R.drawable.wall
            else -> R.drawable.store
        }
    }

    // ---------------- SYNC ----------------
    fun syncAllPlayers() {
        db.collection("rankings").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val updates = mutableMapOf<String, Any>()

                    if (doc.get("score") == null) updates["score"] = 0
                    if (doc.get("chest") == null) updates["chest"] = emptyList<Map<String, Any>>()

                    if (updates.isNotEmpty()) {
                        db.collection("rankings").document(doc.id)
                            .update(updates)
                            .addOnSuccessListener {
                                Log.d("FirebaseHelper", "Updated ${doc.getString("name")}")
                            }
                            .addOnFailureListener { e ->
                                Log.w("FirebaseHelper", "Failed update ${doc.getString("name")}", e)
                            }
                    }
                }
                Log.d("FirebaseHelper", "All players synced")
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "Failed to fetch rankings", e)
            }
    }

    fun syncNewPlayer(playerName: String) {
        if (playerName.isBlank()) return

        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    val newPlayer = hashMapOf(
                        "name" to playerName,
                        "score" to 0,
                        "chest" to emptyList<Map<String, Any>>()
                    )
                    db.collection("rankings").add(newPlayer)
                        .addOnSuccessListener {
                            Log.d("FirebaseHelper", "New player $playerName created")
                        }
                        .addOnFailureListener { e ->
                            Log.w("FirebaseHelper", "Failed to create new player", e)
                        }
                }
            }
    }

    // ---------------- SCORE ----------------
    fun getScore(playerName: String, onResult: (Int) -> Unit) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val score = docs.documents[0].getLong("score")?.toInt() ?: 0
                    onResult(score)
                } else {
                    onResult(0)
                }
            }
            .addOnFailureListener {
                onResult(0)
            }
    }

    fun updateScore(playerName: String, score: Int) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val docId = docs.documents[0].id
                    db.collection("rankings").document(docId)
                        .update("score", score)
                } else {
                    val data = hashMapOf(
                        "name" to playerName,
                        "score" to score,
                        "chest" to emptyList<Map<String, Any>>()
                    )
                    db.collection("rankings").add(data)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "updateScore failed", e)
            }
    }

    // ---------------- ENHANCED SCORE SYNC ----------------
    fun syncScoreWithRetry(playerName: String, score: Int, retryCount: Int = 3) {
        if (playerName.isBlank()) return

        fun attemptSync(attemptsLeft: Int) {
            if (attemptsLeft <= 0) {
                Log.e("FirebaseHelper", "Failed to sync score after all retries")
                return
            }

            updateScore(playerName, score)
            // Add verification after update
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                getScore(playerName) { retrievedScore ->
                    if (retrievedScore != score && attemptsLeft > 1) {
                        Log.w("FirebaseHelper", "Score mismatch, retrying... ($attemptsLeft attempts left)")
                        attemptSync(attemptsLeft - 1)
                    } else {
                        Log.d("FirebaseHelper", "Score sync successful: $score")
                    }
                }
            }, 1000)
        }

        attemptSync(retryCount)
    }

    // Auto-sync score every 30 seconds during gameplay
    fun startPeriodicScoreSync(playerName: String, getCurrentScore: () -> Int) {
        if (playerName.isBlank()) return

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val syncRunnable = object : Runnable {
            override fun run() {
                val currentScore = getCurrentScore()
                Log.d("FirebaseHelper", "Periodic sync: $currentScore points for $playerName")
                updateScore(playerName, currentScore)
                handler.postDelayed(this, 30000) // Sync every 30 seconds
            }
        }
        handler.post(syncRunnable)
    }

    // ---------------- CHEST ----------------
    fun getChestItems(playerName: String, onResult: (List<ChestItem>) -> Unit) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val chestRaw = docs.documents[0].get("chest")
                    val chest: List<ChestItem> = when (chestRaw) {
                        is List<*> -> {
                            if (chestRaw.firstOrNull() is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val maps = chestRaw as List<Map<String, Any>>
                                maps.mapNotNull { m ->
                                    val name = m["name"] as? String
                                    val res = (m["resId"] as? Number)?.toInt()
                                    if (name != null && res != null) ChestItem(name, res) else null
                                }
                            } else if (chestRaw.firstOrNull() is String) {
                                val names = chestRaw.filterIsInstance<String>()
                                names.map { n -> ChestItem(n, resolveResIdFromName(n)) }
                            } else emptyList()
                        }
                        else -> emptyList()
                    }
                    onResult(chest)
                } else {
                    onResult(emptyList())
                }
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun addItemToChest(playerName: String, newItem: ChestItem) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val doc = docs.documents[0]
                    val docId = doc.id
                    val chestList = doc.get("chest") as? MutableList<Map<String, Any>> ?: mutableListOf()
                    chestList.add(mapOf("name" to newItem.name, "resId" to newItem.resId))
                    db.collection("rankings").document(docId)
                        .update("chest", chestList)
                } else {
                    val data = hashMapOf(
                        "name" to playerName,
                        "score" to 0,
                        "chest" to listOf(mapOf("name" to newItem.name, "resId" to newItem.resId))
                    )
                    db.collection("rankings").add(data)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "addItemToChest failed", e)
            }
    }

    fun updateChest(playerName: String, newChest: List<ChestItem>, onComplete: (() -> Unit)? = null) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val docId = docs.documents[0].id
                    val chestMapList = newChest.map { mapOf("name" to it.name, "resId" to it.resId) }
                    db.collection("rankings")
                        .document(docId)
                        .update("chest", chestMapList)
                        .addOnSuccessListener { onComplete?.invoke() }
                }
            }
    }
}
