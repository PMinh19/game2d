
package com.example.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star

@Composable
fun RankScreen(onClose: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var rankList by remember { mutableStateOf(listOf<Pair<String, Int>>()) }

    // Load dữ liệu từ Firebase
    LaunchedEffect(Unit) {
        db.collection("rankings")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                val list = result.map { doc ->
                    val name = doc.getString("name") ?: "Unknown"
                    val score = doc.getLong("score")?.toInt() ?: 0
                    name to score
                }
                rankList = list
            }
    }

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        // Ảnh nền
        Image(
            painter = painterResource(id = R.drawable.vutru1), // Giả sử dùng vutru1
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Nội dung chính
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tiêu đề
            Text(
                text = "BẢNG XẾP HẠNG",
                color = Color.Cyan,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Danh sách xếp hạng
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(rankList) { index, (name, score) ->
                    // Animation scale cho mỗi mục
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 500, delayMillis = index * 100)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .scale(scale),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.2f) // Nền ảo
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Số thứ tự và badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color.Yellow,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (index < 3) { // Badge cho top 3
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Top ${index + 1}",
                                        tint = when (index) {
                                            0 -> Color(0xFFFFD700) // Vàng cho #1
                                            1 -> Color(0xFFC0C0C0) // Bạc cho #2
                                            2 -> Color(0xFFCD7F32) // Đồng cho #3
                                            else -> Color.Transparent
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Tên người chơi
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Điểm
                            Text(
                                text = score.toString(),
                                color = Color.Cyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Nút Close
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

class RankScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RankScreen(onClose = { finish() })
            }
        }
    }
}