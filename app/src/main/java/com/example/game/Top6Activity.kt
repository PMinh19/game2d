package com.example.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class Top6Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Top6Screen(onBack = { finish() })
        }
    }
}

@Composable
fun Top6Screen(onBack: () -> Unit) {
    var topScores by remember { mutableStateOf<List<Pair<String, ScoreEntry>>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()

    // Fetch top 6 scores
    LaunchedEffect(Unit) {
        FirebaseHelper.getTop6Scores { scores ->
            topScores = scores
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🏆 Top 6 Lịch Sử Điểm 🏆",
                color = Color.Yellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (topScores.isEmpty()) {
                Text(
                    text = "Đang tải dữ liệu...",
                    color = Color.White,
                    fontSize = 20.sp
                )
            } else {
                topScores.forEachIndexed { index, (playerName, scoreEntry) ->
                    val date = Date(scoreEntry.timestamp)
                    val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}.",
                            color = Color.Yellow,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = "$playerName: ${scoreEntry.score} xu ($formattedDate)",
                            color = Color.White,
                            fontSize = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text(text = "Quay lại", fontSize = 20.sp)
            }
        }
    }
}