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
import com.example.game.core.GrowingMonster
import kotlin.math.roundToInt

/**
 * UI for Growing Monster with dynamic size and HP bar
 */
@Composable
fun GrowingMonsterUI(monster: GrowingMonster) {
    if (monster.alive.value && monster.hp.value > 0) {
        val density = LocalDensity.current
        val sizeDp = with(density) { monster.currentSize.value.toDp() }

        Image(
            painter = painterResource(R.drawable.quaivat1),
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (monster.x - (monster.currentSize.value - 80f) / 2).roundToInt(),
                        monster.y.value.roundToInt()
                    )
                }
                .size(sizeDp)
        )

        // HP bar (scales with monster size)
        val hpBarWidth = monster.currentSize.value
        Canvas(
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (monster.x - (monster.currentSize.value - 80f) / 2).roundToInt(),
                        (monster.y.value - 20).roundToInt()
                    )
                }
                .size(width = with(density) { hpBarWidth.toDp() }, height = 6.dp)
        ) {
            drawRect(color = Color.DarkGray, size = Size(size.width, size.height))
            // Use currentMaxHp from monster instead of calculating
            val hpPercent = if (monster.currentMaxHp.value > 0) {
                (monster.hp.value.toFloat() / monster.currentMaxHp.value.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            drawRect(color = Color.Red, size = Size(size.width * hpPercent, size.height))
        }
    }
}
