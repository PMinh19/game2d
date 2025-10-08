package com.example.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.R
import com.example.game.core.BagCoinDisplay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Common BagCoinAnimatedView component used across all levels
 * - Animates a bag coin sprite moving slightly up and fading out
 * - Calls onFinished(bag) when animation is complete
 *
 * Reusable across: GameScreen, Level2, Level3, Level4, Level5
 */
@Composable
fun BagCoinAnimatedView(bag: BagCoinDisplay, onFinished: (BagCoinDisplay) -> Unit) {
    var offsetY by remember { mutableStateOf(bag.y) }
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(bag) {
        val duration = 800L
        val steps = 40
        repeat(steps) { i ->
            offsetY -= 2f // move up total ~80 px over duration
            alpha = 1f - (i / steps.toFloat())
            delay(duration / steps)
        }
        // finished -> notify parent to remove
        onFinished(bag)
    }

    // Render image with graphicsLayer alpha
    Image(
        painter = painterResource(R.drawable.bagcoin),
        contentDescription = null,
        modifier = Modifier
            .absoluteOffset { IntOffset(bag.x.roundToInt(), offsetY.roundToInt()) }
            .size(60.dp)
            .graphicsLayer { this.alpha = alpha }
    )
}

