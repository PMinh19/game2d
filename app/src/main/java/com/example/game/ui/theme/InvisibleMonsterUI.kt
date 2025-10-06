package com.example.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.R
import com.example.game.core.InvisibleMonster
import kotlin.math.roundToInt

/**
 * UI for Invisible Monster with fade effect
 */
@Composable
fun InvisibleMonsterUI(monster: InvisibleMonster) {
    if (monster.alive.value && monster.hp.value > 0) {
        // Fade effect when appearing/disappearing
        val alpha = remember(monster.isVisible.value) {
            Animatable(if (monster.isVisible.value) 1f else 0.3f)
        }

        LaunchedEffect(monster.isVisible.value) {
            alpha.animateTo(
                targetValue = if (monster.isVisible.value) 1f else 0.3f,
                animationSpec = tween(300)
            )
        }

        Image(
            painter = painterResource(R.drawable.quaivat1),
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset { IntOffset(monster.x.roundToInt(), monster.y.value.roundToInt()) }
                .size(80.dp)
                .graphicsLayer { this.alpha = alpha.value }
        )

        // HP bar (only when visible)
        if (monster.isVisible.value) {
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
}

