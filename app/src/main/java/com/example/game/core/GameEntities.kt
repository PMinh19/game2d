package com.example.game.core

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.random.Random

open class BaseMonster(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var hp: MutableState<Int>,
    var alive: MutableState<Boolean> = mutableStateOf(true)
)

open class BaseCoin(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var collected: MutableState<Boolean> = mutableStateOf(false)
)

data class BagCoinDisplay(
    val x: Float,
    val y: Float,
    val score: Int
)

data class Bullet(var x: Float, var y: Float)

/**
 * Invisible Monster - can toggle between visible and invisible states
 */
class InvisibleMonster(
    x: Float,
    y: MutableState<Float>,
    speed: Float,
    hp: MutableState<Int>,
    val invisibleDuration: Long,
    val visibleDuration: Long
) : BaseMonster(x, y, speed, hp) {
    var isVisible = mutableStateOf(true)
    var lastToggleTime = System.currentTimeMillis()

    // Zigzag movement properties
    var horizontalSpeed = Random.nextFloat() * 3f + 2f // Tốc độ di chuyển ngang
    var direction = if (Random.nextBoolean()) 1 else -1 // Hướng di chuyển: 1 = phải, -1 = trái
}

/**
 * Growing Monster - grows in size and HP over time
 */
class GrowingMonster(
    x: Float,
    y: MutableState<Float>,
    speed: Float,
    hp: MutableState<Int>,
    val initialSize: Float = 60f,
    val maxSize: Float = 150f,
    val growthRate: Float = 0.5f // Pixels per frame
) : BaseMonster(x, y, speed, hp) {
    var currentSize = mutableStateOf(initialSize)
    var maxHp = hp.value // Track original max HP
    var currentMaxHp = mutableStateOf(hp.value) // Track current max HP based on size
    val growthDuration = ((maxSize - initialSize) / growthRate).toLong()

    // Growth increases both size and HP proportionally
    fun grow() {
        if (currentSize.value < maxSize) {
            val oldSize = currentSize.value
            currentSize.value = (currentSize.value + growthRate).coerceAtMost(maxSize)

            // Only increase HP when size increases (not every frame)
            if (currentSize.value > oldSize) {
                val sizeRatio = currentSize.value / initialSize
                val newMaxHp = (maxHp * sizeRatio).toInt()

                // Only increase current HP if it wasn't damaged
                // Calculate HP percentage before growth
                val hpPercentage = hp.value.toFloat() / currentMaxHp.value.toFloat()
                currentMaxHp.value = newMaxHp

                // Maintain HP percentage after growth (damaged monsters stay damaged)
                hp.value = (newMaxHp * hpPercentage).toInt().coerceAtLeast(1)
            }
        }
    }
}

/**
 * Splitting Monster - splits into 2-3 smaller monsters when killed
 * Movement: zigzag or bounce off screen edges
 */
class SplittingMonster(
    x: Float,
    y: MutableState<Float>,
    speed: Float,
    hp: MutableState<Int>,
    val size: Float = 80f,
    val generation: Int = 1 // Thế hệ: 1 = lớn, 2 = vừa, 3 = nhỏ
) : BaseMonster(x, y, speed, hp) {
    // Movement type: true = zigzag, false = bounce
    var isZigzagMovement = Random.nextBoolean()

    // For zigzag movement
    var horizontalSpeed = Random.nextFloat() * 3f + 2f
    var direction = if (Random.nextBoolean()) 1 else -1

    // For bounce movement
    var velocityX = (Random.nextFloat() * 4f - 2f).coerceIn(-3f, 3f)
    var velocityY = Random.nextFloat() * 2f + 1f

    // Can split when dies
    var canSplit: Boolean = generation < 3 // Chỉ split tối đa 2 lần
    var hasSpawned = mutableStateOf(false) // Đánh dấu đã spawn con chưa
}
