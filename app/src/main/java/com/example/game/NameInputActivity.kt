package com.example.game

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

class NameInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.nen1),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NHẬP TÊN CỦA BẠN",
                        color = Color.Cyan,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    val playerName = remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = playerName.value,
                        onValueChange = { playerName.value = it },
                        label = { Text("Tên người chơi", color = Color.White.copy(0.8f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Cyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.Cyan,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            if (playerName.value.isNotBlank()) {
                                val name = playerName.value
                                PrefManager.savePlayerName(this@NameInputActivity, name)

                                val db = FirebaseFirestore.getInstance()
                                val playerData = hashMapOf(
                                    "name" to name,
                                    "score" to 0
                                )

                                db.collection("rankings")
                                    .add(playerData)
                                    .addOnSuccessListener { documentReference ->
                                        Log.d("Firebase", "Người chơi được lưu: ${documentReference.id}")
                                        PrefManager.savePlayerDocId(this@NameInputActivity, documentReference.id)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("Firebase", "Lỗi khi lưu người chơi", e)
                                    }

                                val intent = Intent(this@NameInputActivity, MainActivity::class.java)
                                intent.putExtra("PLAYER_NAME", name)
                                startActivity(intent)
                                finish()
                            }
                        },
                        enabled = playerName.value.isNotBlank(),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.Cyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(60.dp)
                    ) {
                        Text(
                            "XÁC NHẬN",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
