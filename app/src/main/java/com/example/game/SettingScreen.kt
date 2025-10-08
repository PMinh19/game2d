package com.example.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
<<<<<<< HEAD
import androidx.compose.foundation.background
=======
>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
<<<<<<< HEAD
=======
import androidx.compose.ui.layout.ContentScale
>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
<<<<<<< HEAD
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
=======

>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
class SettingScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
<<<<<<< HEAD
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 🔘 Nút thoát
=======
            Box(modifier = Modifier.fillMaxSize()) {
                // Ảnh nền (thêm code bị comment)
                Image(
                    painter = painterResource(id = R.drawable.vutru1), // Giả sử dùng ảnh tương tự
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Icon Exit ở góc trên bên phải
>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
                IconButton(
                    onClick = { finish() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Exit",
                        tint = Color.White
                    )
                }

<<<<<<< HEAD
                val storeImage = R.drawable.store
                val chestImage = R.drawable.chest
                val coinImage = R.drawable.coin

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // 🟦 Tiêu đề căn giữa
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HƯỚNG DẪN CHƠI GAME",
                            color = Color.Cyan,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Cách sử dụng vật phẩm",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 🔹 Bước 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bước 1: Mở ",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Image(
                            painter = painterResource(storeImage),
                            contentDescription = "Store",
                            modifier = Modifier
                                .size(30.dp)
                                .padding(horizontal = 4.dp)
                        )
                        Text(
                            text = " để mua vật phẩm.",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Mỗi vật phẩm có giá 2 ",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Image(
                            painter = painterResource(coinImage),
                            contentDescription = "Coin",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    @OptIn(ExperimentalLayoutApi::class)

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Bước 2: Sau khi mua, vật phẩm sẽ chuyển vào ",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Image(
                            painter = painterResource(id = chestImage),
                            contentDescription = "Chest",
                            modifier = Modifier
                                .size(30.dp)
                                .padding(horizontal = 4.dp)
                        )
                        Text(
                            text = " và bấm vào vật phẩm muốn dùng.",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }

                    @OptIn(ExperimentalLayoutApi::class)

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Để dùng vật phẩm, mở ",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Image(
                            painter = painterResource(id = chestImage),
                            contentDescription = "Chest",
                            modifier = Modifier
                                .size(30.dp)
                                .padding(horizontal = 4.dp)
                        )
                        Text(
                            text = " và bấm vào vật phẩm muốn dùng.",
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }

=======
                // Nội dung chính ở giữa (thêm placeholder)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Cài Đặt (Placeholder)",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    // Thêm setting options ở đây
>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
                }
            }
        }
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> ef0bb07ae765123f4629eec8810ab08d56555fb0
