package com.example.game.core

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.GameScreenActivity
import com.example.game.Level2Activity
import com.example.game.Level3Activity
import com.example.game.Level4Activity
import com.example.game.Level5Activity
import com.example.game.MainActivity
import com.example.game.R
import com.example.game.Top6Activity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class OverlayType { WIN, GAME_OVER }

@Composable
fun GameOverlay(type: OverlayType, score: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val img = if (type == OverlayType.WIN) R.drawable.win else R.drawable.game_over
                Image(painterResource(img), null, Modifier.size(300.dp))
                Spacer(Modifier.height(16.dp))
                Text("Bạn thu thêm được $score xu", color = Color.Yellow, fontSize = 24.sp)
                Spacer(Modifier.height(24.dp))
                if (type == OverlayType.WIN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { onExit() }) { Text("Thoát", fontSize = 20.sp) }
                        Button(onClick = {
                            val intent = Intent(context, Top6Activity::class.java)
                            context.startActivity(intent)
                        }) { Text("Top 6", fontSize = 20.sp) }
                    }
                } else Button(onClick = { onExit() }) { Text("Thoát", fontSize = 20.sp) }
            }
        }
    }
}

/**
 * GameEndDialog - Reusable dialog for game end screen
 * Displays as an overlay without navigating to a new activity
 */
@Composable
fun GameEndDialog(
    isWin: Boolean,
    score: Int,
    level: Int,
    onDismiss: () -> Unit,
    onReplay: () -> Unit,
    onNextLevel: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Game End Image (Win or Game Over)
                val imageRes = if (isWin) R.drawable.win else R.drawable.game_over
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = if (isWin) "Win" else "Game Over",
                    modifier = Modifier.size(300.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Level info
                Text(
                    text = "Level $level",
                    color = Color.White,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Score info
                Text(
                    text = "Bạn thu thêm được $score xu",
                    color = Color.Yellow,
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                if (isWin) {
                    // WIN buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Next Level button (if not last level)
                        if (level < 5) {
                            Button(
                                onClick = {
                                    val nextLevelIntent = when (level) {
                                        1 -> Intent(context, Level2Activity::class.java)
                                        2 -> Intent(context, Level3Activity::class.java)
                                        3 -> Intent(context, Level4Activity::class.java)
                                        4 -> Intent(context, Level5Activity::class.java)
                                        else -> null
                                    }
                                    nextLevelIntent?.let {
                                        it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(it)
                                        onNextLevel()
                                    }
                                },
                                modifier = Modifier.width(200.dp)
                            ) {
                                Text("Level tiếp theo", fontSize = 20.sp)
                            }
                        }

                        // Replay button
                        Button(
                            onClick = onReplay,
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text("Chơi lại", fontSize = 20.sp)
                        }

                        // Top 6 button
                        Button(
                            onClick = {
                                val intent = Intent(context, Top6Activity::class.java)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text("Top 6", fontSize = 20.sp)
                        }

                        // Exit button
                        Button(
                            onClick = onExit,
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text("Thoát", fontSize = 20.sp)
                        }
                    }
                } else {
                    // GAME OVER buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Replay button
                        Button(
                            onClick = onReplay,
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text("Chơi lại", fontSize = 20.sp)
                        }

                        // Exit button
                        Button(
                            onClick = onExit,
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text("Thoát", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
