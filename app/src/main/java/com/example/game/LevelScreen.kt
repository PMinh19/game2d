package com.example.game

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
    val buttonSize = 100.dp

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

    var showTutorialDialog by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf(0) }

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

            // Danh sách các hình ảnh planet
            val planetImages = listOf(
                R.drawable.planet1,
                R.drawable.planet2,
                R.drawable.planet3,
                R.drawable.planet4,
                R.drawable.planet5
            )

            Box(
                modifier = Modifier
                    .offset(x = centerOffsetX, y = centerOffsetY)
                    .size(buttonSize)
            ) {
                IconButton(
                    onClick = {
                        if (index == 0 || index == 1 || index == 2 || index == 3 || index == 4) {
                            // Level 1, 2, 3, 4, 5 - Hiển thị hộp thoại hướng dẫn
                            selectedLevel = index
                            showTutorialDialog = true
                        } else {
                            // Các level khác - Vào game luôn
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
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = planetImages[index]),
                        contentDescription = "Level ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
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

        // Hộp thoại hướng dẫn cho Level 1
        if (showTutorialDialog) {
            when (selectedLevel) {
                0 -> LevelTutorialDialog(
                    level = selectedLevel,
                    onDismiss = { showTutorialDialog = false },
                    onStartGame = {
                        showTutorialDialog = false
                        context.startActivity(Intent(context, GameScreenActivity::class.java))
                    }
                )
                1 -> Level2TutorialDialog(
                    onDismiss = { showTutorialDialog = false },
                    onStartGame = {
                        showTutorialDialog = false
                        context.startActivity(Intent(context, Level2Activity::class.java))
                    }
                )
                2 -> Level3TutorialDialog(
                    onDismiss = { showTutorialDialog = false },
                    onStartGame = {
                        showTutorialDialog = false
                        context.startActivity(Intent(context, Level3Activity::class.java))
                    }
                )
                3 -> Level4TutorialDialog(
                    onDismiss = { showTutorialDialog = false },
                    onStartGame = {
                        showTutorialDialog = false
                        context.startActivity(Intent(context, Level4Activity::class.java))
                    }
                )
                4 -> Level5TutorialDialog(
                    onDismiss = { showTutorialDialog = false },
                    onStartGame = {
                        showTutorialDialog = false
                        context.startActivity(Intent(context, Level5Activity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun LevelTutorialDialog(
    level: Int,
    onDismiss: () -> Unit,
    onStartGame: () -> Unit
) {
    val title = "HƯỚNG DẪN CHƠI LEVEL"
    val monsterImage = R.drawable.quaivat1
    val planeImage = R.drawable.maybay1
    val coinImage = R.drawable.coin
    val additionalInfo = "💡 Kéo máy bay để di chuyển\n🎯 Bắn tự động khi vào game"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ảnh quái vật
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(monsterImage),
                        contentDescription = "Monster",
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text hướng dẫn về quái vật
                Text(
                    text = "sẽ xuất hiện liên tục từ trên màn hình. Chúng sẽ rơi xuống và tìm cách tránh đạn.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh máy bay + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hãy dùng ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(planeImage),
                        contentDescription = "Plane",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = " để tiêu diệt chúng",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh coin + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "và thu thập nhiều ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(coinImage),
                        contentDescription = "Coin",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " nhất có thể!",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưu ý thêm
                Text(
                    text = additionalInfo,
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("BẮT ĐẦU CHƠI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY", color = Color.White)
            }
        }
    )
}

@Composable
fun Level2TutorialDialog(
    onDismiss: () -> Unit,
    onStartGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = "HƯỚNG DẪN CHƠI ",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ảnh máy bay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Điều khiển ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.plane2),
                        contentDescription = "Plane",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh monster + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "tiêu diệt các nhóm ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.monster2),
                        contentDescription = "Monster",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = " quay tròn",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh coin + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "và thu thập ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.coin),
                        contentDescription = "Coin",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " nhiều nhất có thể.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưu ý thêm
                Text(
                    text = "🎯 Quái vật di chuyển theo nhóm và xoay tròn\n⚠️ Chúng sẽ nảy lại khi chạm tường!",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("BẮT ĐẦU CHƠI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY", color = Color.White)
            }
        }
    )
}

@Composable
fun Level3TutorialDialog(
    onDismiss: () -> Unit,
    onStartGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = "HƯỚNG DẪN CHƠI",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ảnh máy bay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Điều khiển ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.plane3),
                        contentDescription = "Plane",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh monster + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "để tiêu diệt ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.monster3),
                        contentDescription = "Monster",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = " tàng hình",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh coin + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "và thu thập ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.coin),
                        contentDescription = "Coin",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " nhiều nhất có thể.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưu ý thêm
                Text(
                    text = "👻 Quái vật sẽ tàng hình và xuất hiện\n⚠️ Chỉ bắn trúng khi chúng hiện hình!",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("BẮT ĐẦU CHƠI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY", color = Color.White)
            }
        }
    )
}

@Composable
fun Level4TutorialDialog(
    onDismiss: () -> Unit,
    onStartGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = "HƯỚNG DẪN CHƠI",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ảnh máy bay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Điều khiển ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.plane4),
                        contentDescription = "Plane",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh monster + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "tiêu diệt ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.monster4),
                        contentDescription = "Monster",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = " lớn dần theo thời gian",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh coin + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "và thu thập ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.coin),
                        contentDescription = "Coin",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " nhiều nhất có thể.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưu ý thêm
                Text(
                    text = "🎯 Quái vật sẽ lớn dần và mạnh hơn\n⚠️ Tiêu diệt chúng trước khi quá lớn!",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("BẮT ĐẦU CHƠI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY", color = Color.White)
            }
        }
    )
}

@Composable
fun Level5TutorialDialog(
    onDismiss: () -> Unit,
    onStartGame: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = "HƯỚNG DẪN CHƠI ",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ảnh máy bay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Điều khiển ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.plane5),
                        contentDescription = "Plane",
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh monster + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "để tiêu diệt ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.monster5),
                        contentDescription = "Monster",
                        modifier = Modifier.size(50.dp)
                    )
                    Text(
                        text = " mẹ và ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.monster5),
                        contentDescription = "Monster child",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " con",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ảnh coin + hướng dẫn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "và thu thập ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Image(
                        painter = painterResource(R.drawable.coin),
                        contentDescription = "Coin",
                        modifier = Modifier.size(35.dp)
                    )
                    Text(
                        text = " nhiều nhất có thể.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưu ý thêm
                Text(
                    text = "👨‍👩‍👧 Quái vật mẹ sẽ sinh ra quái vật con khi chết\n⚠️ Hãy cẩn thận với số lượng quái vật!",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("BẮT ĐẦU CHƠI", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY", color = Color.White)
            }
        }
    )
}
