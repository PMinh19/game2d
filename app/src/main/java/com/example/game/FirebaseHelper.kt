package com.example.game

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

data class ChestItem(val name: String, val resId: Int)
data class ScoreEntry(val score: Int, val timestamp: Long)

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
                    if (doc.get("scoreHistory") == null) updates["scoreHistory"] = emptyList<Map<String, Any>>()
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
                        "chest" to emptyList<Map<String, Any>>(),
                        "scoreHistory" to emptyList<Map<String, Any>>()
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

    // CẬP NHẬT ĐIỂM TRONG KHI CHƠI (không lưu vào history)
    fun updateScore(playerName: String, score: Int) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val docId = docs.documents[0].id
                    // CHỈ cập nhật điểm hiện tại, KHÔNG lưu vào scoreHistory
                    db.collection("rankings").document(docId)
                        .update("score", score)
                } else {
                    // Nếu player chưa tồn tại, tạo mới
                    val data = hashMapOf(
                        "name" to playerName,
                        "score" to score,
                        "chest" to emptyList<Map<String, Any>>(),
                        "scoreHistory" to emptyList<Map<String, Any>>()
                    )
                    db.collection("rankings").add(data)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "updateScore failed", e)
            }
    }

    // LƯU ĐIỂM CUỐI TRẬN vào history (chỉ gọi khi kết thúc trận)
    fun saveMatchScore(playerName: String, finalScore: Int) {
        if (finalScore <= 0) return // Không lưu nếu điểm = 0

        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val docId = docs.documents[0].id
                    val currentHistory = docs.documents[0].get("scoreHistory") as? List<Map<String, Any>> ?: emptyList()

                    // Thêm điểm cuối trận vào lịch sử
                    val newScoreEntry = mapOf(
                        "score" to finalScore,
                        "timestamp" to Date().time
                    )
                    val updatedHistory = currentHistory + newScoreEntry

                    db.collection("rankings").document(docId)
                        .update(
                            mapOf(
                                "score" to finalScore,
                                "scoreHistory" to updatedHistory
                            )
                        )
                        .addOnSuccessListener {
                            Log.d("FirebaseHelper", "Match score saved: $finalScore")
                        }
                } else {
                    // Nếu player chưa tồn tại, tạo mới với điểm này
                    val data = hashMapOf(
                        "name" to playerName,
                        "score" to finalScore,
                        "chest" to emptyList<Map<String, Any>>(),
                        "scoreHistory" to listOf(
                            mapOf(
                                "score" to finalScore,
                                "timestamp" to Date().time
                            )
                        )
                    )
                    db.collection("rankings").add(data)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "saveMatchScore failed", e)
            }
    }

    // ---------------- SCORE HISTORY ----------------
    fun getScoreHistory(playerName: String, onResult: (List<ScoreEntry>) -> Unit) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val historyRaw = docs.documents[0].get("scoreHistory")
                    val history: List<ScoreEntry> = when (historyRaw) {
                        is List<*> -> {
                            if (historyRaw.firstOrNull() is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val maps = historyRaw as List<Map<String, Any>>
                                maps.mapNotNull { m ->
                                    val score = (m["score"] as? Number)?.toInt()
                                    val timestamp = (m["timestamp"] as? Long)
                                    if (score != null && timestamp != null) ScoreEntry(score, timestamp) else null
                                }
                            } else {
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                    onResult(history)
                } else {
                    onResult(emptyList())
                }
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // ---------------- TOP 6 SCORES ----------------
    fun getTop6Scores(onResult: (List<Pair<String, ScoreEntry>>) -> Unit) {
        db.collection("rankings")
            .get()
            .addOnSuccessListener { snapshot ->
                val allScores = mutableListOf<Pair<String, ScoreEntry>>()
                for (doc in snapshot.documents) {
                    val playerName = doc.getString("name") ?: continue
                    val historyRaw = doc.get("scoreHistory")
                    val history: List<ScoreEntry> = when (historyRaw) {
                        is List<*> -> {
                            if (historyRaw.firstOrNull() is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val maps = historyRaw as List<Map<String, Any>>
                                maps.mapNotNull { m ->
                                    val score = (m["score"] as? Number)?.toInt()
                                    val timestamp = (m["timestamp"] as? Long)
                                    if (score != null && timestamp != null) ScoreEntry(score, timestamp) else null
                                }
                            } else {
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                    history.forEach { scoreEntry ->
                        allScores.add(Pair(playerName, scoreEntry))
                    }
                }
                // Sort by score in descending order and take top 6
                val topScores = allScores.sortedByDescending { it.second.score }.take(6)
                onResult(topScores)
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseHelper", "Failed to fetch top scores", e)
                onResult(emptyList())
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

    // Auto-sync score every 30 seconds
    fun startPeriodicScoreSync(playerName: String, getCurrentScore: () -> Int) {
        if (playerName.isBlank()) return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val syncRunnable = object : Runnable {
            override fun run() {
                val currentScore = getCurrentScore()
                Log.d("FirebaseHelper", "Periodic sync: $currentScore points for $playerName")
                updateScore(playerName, currentScore)
                handler.postDelayed(this, 30000)
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
                        "chest" to listOf(mapOf("name" to newItem.name, "resId" to newItem.resId)),
                        "scoreHistory" to emptyList<Map<String, Any>>()
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