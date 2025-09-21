package com.example.game

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseHelper.syncAllPlayers()

        val playerName = intent.getStringExtra("PLAYER_NAME")
            ?: PrefManager.getPlayerName(this)
            ?: "Người chơi"

        FirebaseHelper.syncNewPlayer(playerName)

        setContent {
            MaterialTheme {
                // State cho BagCoin & Chest
                val bagCoinScore = remember { mutableStateOf(0) }
                val chestItems = remember { mutableStateListOf<String>() }
                val db = FirebaseFirestore.getInstance()
                val context = LocalContext.current

                // Load score + chest từ Firebase
                LaunchedEffect(playerName) {
                    if (!playerName.isNullOrBlank()) {
                        db.collection("rankings")
                            .whereEqualTo("name", playerName)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    val doc = docs.documents[0]
                                    bagCoinScore.value = (doc.getLong("score") ?: 0).toInt()
                                    val chestFromDb = doc.get("chest") as? List<String> ?: emptyList()
                                    chestItems.clear()
                                    chestItems.addAll(chestFromDb)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w("Firebase", "Failed to load player data", e)
                            }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Background
                    Image(
                        painter = painterResource(R.drawable.nen1),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Welcome text
                    Text(
                        text = "Xin chào, $playerName!",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp)
                    )

                    // Top bar UI (Store, Chest, BagCoin) ở góc trên trái
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 16.dp, start = 16.dp)
                    ) {
                        TopBarUI(
                            bagCoinScore = bagCoinScore.value,
                            chestItems = chestItems,
                            onBuyItem = { itemName, price ->
                                if (bagCoinScore.value >= price) {
                                    if (!playerName.isNullOrBlank()) {
                                        db.collection("rankings")
                                            .whereEqualTo("name", playerName)
                                            .get()
                                            .addOnSuccessListener { docs ->
                                                if (!docs.isEmpty) {
                                                    val docId = docs.documents[0].id
                                                    val newScore = bagCoinScore.value - price
                                                    db.collection("rankings").document(docId)
                                                        .update(
                                                            "score", newScore,
                                                            "chest", chestItems.toList() + itemName
                                                        )
                                                    bagCoinScore.value = newScore
                                                    chestItems.add(itemName)
                                                }
                                            }
                                    }
                                } else {
                                    Toast.makeText(context, "Không đủ coins để mua $itemName", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // Buttons: Play / Rank / Settings
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = {
                            setContent { LevelPathScreen() } // chuyển sang màn hình level
                        }) {
                            Text("Play")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { context.startActivity(Intent(context, RankScreenActivity::class.java)) }
                        ) { Text("Rank") }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { context.startActivity(Intent(context, SettingScreenActivity::class.java)) }
                        ) { Text("Settings") }
                    }
                }
            }
        }
    }
}
