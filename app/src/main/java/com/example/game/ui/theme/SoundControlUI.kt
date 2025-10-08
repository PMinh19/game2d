package com.example.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.R
import com.example.game.core.SoundManager

/**
 * Sound Control Button - Icon loa để mở dialog cài đặt âm thanh
 */
@Composable
fun SoundControlButton(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    // Speaker icon button
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable { showDialog = true }
            .background(
                color = Color(0x80000000),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.speaker),
            contentDescription = "Sound Settings",
            modifier = Modifier.fillMaxSize()
        )
    }

    // Show dialog when clicked
    if (showDialog) {
        SoundSettingsDialog(
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Sound Settings Dialog - Dialog cài đặt âm thanh
 */
@Composable
fun SoundSettingsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    color = Color(0xEE1A1A2E),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "🔊 Cài Đặt Âm Thanh",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Background Music Control
                SoundOptionItem(
                    icon = "🎵",
                    title = "Nhạc Nền",
                    isEnabled = SoundManager.isBackgroundMusicEnabled.value,
                    onToggle = { SoundManager.toggleBackgroundMusic() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sound Effects Control
                SoundOptionItem(
                    icon = "🔫",
                    title = "Âm Thanh Hiệu Ứng",
                    subtitle = "Bắn đạn, quái vật",
                    isEnabled = SoundManager.isSoundEffectsEnabled.value,
                    onToggle = { SoundManager.toggleSoundEffects() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Đóng",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Sound Option Item - Một dòng cài đặt âm thanh với toggle switch
 */
@Composable
fun SoundOptionItem(
    icon: String,
    title: String,
    subtitle: String? = null,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0x40FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon and text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        // Toggle switch
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF555555)
            )
        )
    }
}

