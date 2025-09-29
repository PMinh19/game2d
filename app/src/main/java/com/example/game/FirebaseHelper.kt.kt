package com.example.game

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class ChestItem(val name: String, val resId: Int)

object FirebaseHelper {
    private val db = FirebaseFirestore.getInstance()

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

    // ---------------- CHEST ----------------
    fun getChestItems(playerName: String, onResult: (List<ChestItem>) -> Unit) {
        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val chestList = docs.documents[0].get("chest") as? List<Map<String, Any>>
                    val chest = chestList?.map {
                        ChestItem(it["name"] as String, (it["resId"] as Long).toInt())
                    } ?: emptyList()
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
