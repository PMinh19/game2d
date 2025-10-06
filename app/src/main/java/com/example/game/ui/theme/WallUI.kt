package com.example.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.R
import kotlin.math.roundToInt

@Composable
fun WallUI(
    planeY: Float
) {
    Image(
        painter = painterResource(R.drawable.wall),
        contentDescription = null,
        modifier = Modifier
            .absoluteOffset {
                IntOffset(0, (planeY - 60f).roundToInt())
            }
            .fillMaxWidth()
            .height(40.dp),
        contentScale = ContentScale.Crop
    )
}
