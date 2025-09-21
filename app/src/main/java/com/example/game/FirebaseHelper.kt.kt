package com.example.game

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseHelper {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Đồng bộ chest và score cho tất cả player hiện tại
     */
    fun syncAllPlayers() {
        db.collection("rankings").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val docId = doc.id
                    val chest = doc.get("chest")
                    val score = doc.get("score")

                    val updates = mutableMapOf<String, Any>()

                    if (chest == null) updates["chest"] = emptyList<String>()
                    if (score == null) updates["score"] = 0

                    if (updates.isNotEmpty()) {
                        db.collection("rankings").document(docId)
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

    /**
     * Đồng bộ chest + score cho player mới
     * Nếu player chưa có document trong rankings, tạo mới với chest trống và score 0
     */
    fun syncNewPlayer(playerName: String) {
        if (playerName.isBlank()) return

        db.collection("rankings")
            .whereEqualTo("name", playerName)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    // Player mới, tạo document
                    val newPlayer = hashMapOf(
                        "name" to playerName,
                        "score" to 0,
                        "chest" to emptyList<String>()
                    )
                    db.collection("rankings").add(newPlayer)
                        .addOnSuccessListener { Log.d("FirebaseHelper", "New player $playerName created") }
                        .addOnFailureListener { e -> Log.w("FirebaseHelper", "Failed to create new player", e) }
                }
            }
    }
}
