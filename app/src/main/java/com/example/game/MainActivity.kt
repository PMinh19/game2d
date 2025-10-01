package com.example.game

import android.content.Intent
import android.os.Bundle
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
import com.example.game.TopBarComponent.TopBarUI

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
                var showLevelScreen by remember { mutableStateOf(false) }

                if (showLevelScreen) {
                    LevelPathScreen(onExit = {
                        showLevelScreen = false
                    })
                } else {
                    MainScreen(
                        playerName = playerName,
                        onPlayClicked = { showLevelScreen = true }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    playerName: String,
    onPlayClicked: () -> Unit
) {
    // State cho BagCoin & Chest
    val bagCoinScore = remember { mutableStateOf(0) }
    val chestItems = remember { mutableStateListOf<ChestItem>() }
    val context = LocalContext.current

    // Load score + chest từ Firebase (khôi phục hệ thống cũ)
    LaunchedEffect(playerName) {
        if (playerName.isNotBlank()) {
            FirebaseHelper.getScore(playerName) { score -> bagCoinScore.value = score }
            FirebaseHelper.getChestItems(playerName) { items ->
                chestItems.clear()
                chestItems.addAll(items)
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
                onBuyItem = { item: ChestItem, price: Int ->
                    if (bagCoinScore.value >= price) {
                        val newScore = bagCoinScore.value - price
                        bagCoinScore.value = newScore
                        chestItems.add(item)
                        if (playerName.isNotBlank()) {
                            FirebaseHelper.updateScore(playerName, newScore)
                            FirebaseHelper.updateChest(playerName, chestItems.toList())
                        }
                        Toast.makeText(context, "Mua ${item.name} thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không đủ coins để mua ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onUseChestItem = { item: ChestItem ->
                    // Handle chest item usage if needed
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
            Button(onClick = onPlayClicked) {
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
