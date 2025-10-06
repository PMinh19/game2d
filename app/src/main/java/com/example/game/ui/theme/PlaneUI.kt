package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.R
import kotlin.math.roundToInt

@Composable
fun PlaneUI(
    planeX: Float,
    planeY: Float,
    planeHp: Int,
    shieldActive: Boolean
) {
    // Plane
    Image(
        painter = painterResource(R.drawable.maybay1),
        contentDescription = null,
        modifier = Modifier
            .absoluteOffset { IntOffset(planeX.roundToInt(), planeY.roundToInt()) }
            .size(100.dp)
    )

    // HP bar
    Canvas(
        modifier = Modifier
            .absoluteOffset {
                IntOffset(planeX.roundToInt(), (planeY - 20).roundToInt())
            }
            .size(width = 100.dp, height = 8.dp)
    ) {
        drawRect(color = Color.DarkGray, size = Size(size.width, size.height))
        val ratio = planeHp.coerceIn(0, 100) / 100f
        drawRect(color = Color.Green, size = Size(size.width * ratio, size.height))
    }

    // Shield
    if (shieldActive) {
        Canvas(
            modifier = Modifier
                .absoluteOffset { IntOffset((planeX - 10).roundToInt(), (planeY - 10).roundToInt()) }
                .size(120.dp)
        ) {
            drawCircle(color = Color.Cyan.copy(alpha = 0.35f))
        }
    }
}
