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
import com.example.game.core.BaseMonster
import kotlin.math.roundToInt

@Composable
fun MonsterUI(monster: BaseMonster) {
    if (monster.alive.value && monster.hp.value > 0) {
        Image(
            painter = painterResource(R.drawable.quaivat1),
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset { IntOffset(monster.x.roundToInt(), monster.y.value.roundToInt()) }
                .size(80.dp)
        )

        // HP bar
        Canvas(
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(monster.x.roundToInt(), (monster.y.value - 20).roundToInt())
                }
                .size(width = 80.dp, height = 6.dp)
        ) {
            drawRect(color = Color.DarkGray, size = Size(size.width, size.height))
            val hpPercent = (monster.hp.value / 100f).coerceIn(0f, 1f)
            drawRect(color = Color.Red, size = Size(size.width * hpPercent, size.height))
        }
    }
}
