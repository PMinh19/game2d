
package com.example.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.nen1),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    val context = LocalContext.current
                    val playerName = intent.getStringExtra("PLAYER_NAME")
                        ?: PrefManager.getPlayerName(context)
                        ?: "Người chơi"

                    Text(
                        text = "Xin chào, $playerName!",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp)
                    )

                    // Play, Rank, và Settings Buttons ở dưới cùng
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                // Gọi trực tiếp LevelPathScreen bằng cách set lại content
                                setContent {
                                    MaterialTheme {
                                        LevelPathScreen()
                                    }
                                }
                            }
                        ) {
                            Text("Play")
                        }



                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val intent = Intent(this@MainActivity, RankScreenActivity::class.java)
                                startActivity(intent)
                            },
                            modifier = Modifier.scale(scale)
                        ) {
                            Text("Rank")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val intent = Intent(this@MainActivity, SettingScreenActivity::class.java)
                                startActivity(intent)
                            },
                            modifier = Modifier.scale(scale)
                        ) {
                            Text("Settings")
                        }
                    }
                }
            }
        }
    }
}