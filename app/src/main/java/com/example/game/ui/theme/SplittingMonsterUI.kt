package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.R
import com.example.game.core.SplittingMonster
import kotlin.math.roundToInt

/**
 * UI for Splitting Monster with dynamic size based on generation
 */
@Composable
fun SplittingMonsterUI(monster: SplittingMonster, level: Int = 5) {
    if (monster.alive.value && monster.hp.value > 0) {
        val density = LocalDensity.current
        val sizeDp = with(density) { monster.size.toDp() }

        val monsterDrawable = when(level) {
            5 -> R.drawable.monster5
            else -> R.drawable.quaivat1
        }

        Image(
            painter = painterResource(monsterDrawable),
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset { IntOffset(monster.x.roundToInt(), monster.y.value.roundToInt()) }
                .size(sizeDp)
        )

        // HP bar (scales with monster size)
        Canvas(
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(monster.x.roundToInt(), (monster.y.value - 15).roundToInt())
                }
                .size(width = sizeDp, height = 5.dp)
        ) {
            drawRect(color = Color.DarkGray, size = Size(size.width, size.height))
            val hpPercent = (monster.hp.value.toFloat() / 100f).coerceIn(0f, 1f)
            drawRect(color = Color.Red, size = Size(size.width * hpPercent, size.height))
        }
    }
}
