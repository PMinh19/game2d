package com.example.game

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Ảnh nền
                Image(
                    painter = painterResource(id = R.drawable.nen1),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                // Animatable để scale mượt
                val scale = remember { Animatable(0.8f) }

                LaunchedEffect(Unit) {
                    scale.animateTo(
                        targetValue = 1.3f,
                        animationSpec = tween(durationMillis = 1800)
                    )
                }

                // Tên game
                Text(
                    text = "My Game",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.scale(scale.value)
                )
            }
        }

        lifecycleScope.launch {
            delay(2500)

            val savedName = PrefManager.getPlayerName(this@SplashActivity)
            Log.d("SplashActivity", "Saved name from SharedPreferences: $savedName")

            if (savedName.isNullOrBlank()) {
                // Nếu chưa có tên → sang màn nhập
                Log.d("SplashActivity", "No saved name, going to NameInputActivity")
                startActivity(Intent(this@SplashActivity, NameInputActivity::class.java))
            } else {
                // Nếu đã có tên → sang Main luôn VỚI TÊN
                Log.d("SplashActivity", "Found saved name: $savedName, going to MainActivity")
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                intent.putExtra("PLAYER_NAME", savedName)
                startActivity(intent)
            }
            finish()
        }
    }
}