package com.example.game

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.example.game.TopBarComponent.TopBarUI

@Composable
fun LevelPathScreen(onExit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.dp
    val screenHeightDp = config.screenHeightDp.dp
    val buttonSize = 70.dp

    val bagCoinScore = remember { mutableStateOf(0) }
    val chestItems = remember { mutableStateListOf<ChestItem>() }
    val db = FirebaseFirestore.getInstance()
    val playerName = PrefManager.getPlayerName(context)

    // Load score + chest từ Firebase (hỗ trợ cả định dạng cũ và mới)
    LaunchedEffect(playerName) {
        if (!playerName.isNullOrBlank()) {
            db.collection("rankings")
                .whereEqualTo("name", playerName)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) { Log.w("Firebase", "Listen failed.", e); return@addSnapshotListener }
                    if (snapshots != null && !snapshots.isEmpty) {
                        val doc = snapshots.documents[0]
                        val chestRaw = doc.get("chest")
                        val items: List<ChestItem> = when (chestRaw) {
                            is List<*> -> {
                                when (chestRaw.firstOrNull()) {
                                    is Map<*, *> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val maps = chestRaw as List<Map<String, Any>>
                                        maps.mapNotNull { m ->
                                            val n = m["name"] as? String
                                            val r = (m["resId"] as? Number)?.toInt()
                                            if (n != null && r != null) ChestItem(n, r) else null
                                        }
                                    }
                                    is String -> {
                                        val names = chestRaw.filterIsInstance<String>()
                                        names.map { n ->
                                            val res = when (n) {
                                                "Fireworks" -> R.drawable.fireworks
                                                "Firework2" -> R.drawable.firework2
                                                else -> R.drawable.store
                                            }
                                            ChestItem(n, res)
                                        }
                                    }
                                    else -> emptyList()
                                }
                            }
                            else -> emptyList()
                        }
                        chestItems.clear()
                        chestItems.addAll(items)
                        val score = doc.getLong("score") ?: 0
                        bagCoinScore.value = score.toInt()
                    }
                }
        }
    }

    // Update score khi Resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!playerName.isNullOrBlank()) {
                    db.collection("rankings")
                        .whereEqualTo("name", playerName)
                        .get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                val score = docs.documents[0].getLong("score") ?: 0
                                bagCoinScore.value = score.toInt()
                            }
                        }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Level positions
    val levelPositions: List<Pair<Dp, Dp>> = listOf(
        Pair(screenWidthDp * 0.20f, screenHeightDp * 0.85f),
        Pair(screenWidthDp * 0.78f, screenHeightDp * 0.68f),
        Pair(screenWidthDp * 0.18f, screenHeightDp * 0.52f),
        Pair(screenWidthDp * 0.82f, screenHeightDp * 0.36f),
        Pair(screenWidthDp * 0.48f, screenHeightDp * 0.20f)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.vutru1),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Nút X thoát về MainActivity
        IconButton(
            onClick = {
                if (onExit != null) {
                    onExit()
                } else {
                    context.startActivity(Intent(context, MainActivity::class.java))
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Exit",
                tint = Color.White
            )
        }

        // Vẽ đường nối các level
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (levelPositions.isNotEmpty()) {
                val path = Path()
                val (sxDp, syDp) = levelPositions[0]
                path.moveTo(sxDp.toPx(), syDp.toPx())
                val screenWidthPx = screenWidthDp.toPx()
                for (i in 0 until levelPositions.size - 1) {
                    val p1x = levelPositions[i].first.toPx()
                    val p1y = levelPositions[i].second.toPx()
                    val p2x = levelPositions[i + 1].first.toPx()
                    val p2y = levelPositions[i + 1].second.toPx()
                    val controlX = if (i % 2 == 0) p1x - screenWidthPx * 0.15f else p1x + screenWidthPx * 0.15f
                    val controlY = (p1y + p2y) / 2f
                    path.quadraticBezierTo(controlX, controlY, p2x, p2y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.5f),
                    style = Stroke(width = 40f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }

        // Nút level
        levelPositions.forEachIndexed { index, pos ->
            val centerOffsetX = pos.first - (buttonSize / 2f)
            val centerOffsetY = pos.second - (buttonSize / 2f)

            Box(
                modifier = Modifier
                    .offset(x = centerOffsetX, y = centerOffsetY)
                    .size(buttonSize)
            ) {
                Button(
                    onClick = {
                        val levelActivities = listOf(
                            GameScreenActivity::class.java,
                            Level2Activity::class.java,
                            Level3Activity::class.java,
                            Level4Activity::class.java,
                            Level5Activity::class.java
                        )
                        if (index in levelActivities.indices) {
                            context.startActivity(Intent(context, levelActivities[index]))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.95f),
                        contentColor = Color.Black
                    ),
                    shape = CircleShape
                ) {
                    Text(text = "${index + 1}", color = Color.Black)
                }
            }
        }

        // TopBarUI ở trên cùng bên trái
        TopBarUI(
            bagCoinScore = bagCoinScore.value,
            chestItems = chestItems,
            onBuyItem = { item: ChestItem, price: Int ->
                if (playerName.isNullOrBlank()) return@TopBarUI
                if (bagCoinScore.value >= price) {
                    val newScore = bagCoinScore.value - price
                    bagCoinScore.value = newScore
                    chestItems.add(item)
                    FirebaseHelper.updateScore(playerName, newScore)
                    FirebaseHelper.updateChest(playerName, chestItems.toList())
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
}
