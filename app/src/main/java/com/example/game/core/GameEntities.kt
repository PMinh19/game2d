package com.example.game.core

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.random.Random

/**
 * Interface chung cho tất cả các loại monster
 */
interface IMonster {
    var x: Float
    val y: MutableState<Float>
    val alive: MutableState<Boolean>
    val hp: MutableState<Int>

    // ✅ Abstract function - mỗi class tự implement
    fun getCurrentSize(): Float
}

open class BaseMonster(
    override var x: Float,
    override var y: MutableState<Float>,
    var speed: Float,
    override var hp: MutableState<Int>,
    override var alive: MutableState<Boolean> = mutableStateOf(true)
) : IMonster {
    override fun getCurrentSize(): Float = 100f
}

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
 * Invisible Monster
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

    var horizontalSpeed = Random.nextFloat() * 3f + 2f
    var direction = if (Random.nextBoolean()) 1 else -1

    override fun getCurrentSize(): Float = 100f
}

/**
 * Growing Monster
 */
class GrowingMonster(
    x: Float,
    y: MutableState<Float>,
    speed: Float,
    hp: MutableState<Int>,
    val initialSize: Float = 60f,
    val maxSize: Float = 150f,
    val growthRate: Float = 0.5f
) : BaseMonster(x, y, speed, hp) {
    var currentSize = mutableStateOf(initialSize)
    var maxHp = hp.value
    var currentMaxHp = mutableStateOf(hp.value)
    val growthDuration = ((maxSize - initialSize) / growthRate).toLong()

    fun grow() {
        if (currentSize.value < maxSize) {
            val oldSize = currentSize.value
            currentSize.value = (currentSize.value + growthRate).coerceAtMost(maxSize)

            if (currentSize.value > oldSize) {
                val sizeRatio = currentSize.value / initialSize
                val newMaxHp = (maxHp * sizeRatio).toInt()
                val hpPercentage = hp.value.toFloat() / currentMaxHp.value.toFloat()
                currentMaxHp.value = newMaxHp
                hp.value = (newMaxHp * hpPercentage).toInt().coerceAtLeast(1)
            }
        }
    }

    // ✅ Return snapshot value
    override fun getCurrentSize(): Float = currentSize.value
}

/**
 * Splitting Monster
 */
class SplittingMonster(
    x: Float,
    y: MutableState<Float>,
    speed: Float,
    hp: MutableState<Int>,
    val size: Float = 80f,
    val generation: Int = 1
) : BaseMonster(x, y, speed, hp) {
    var isZigzagMovement = Random.nextBoolean()
    var horizontalSpeed = Random.nextFloat() * 3f + 2f
    var direction = if (Random.nextBoolean()) 1 else -1
    var velocityX = (Random.nextFloat() * 4f - 2f).coerceIn(-3f, 3f)
    var velocityY = Random.nextFloat() * 2f + 1f
    var canSplit: Boolean = generation < 3
    var hasSpawned = mutableStateOf(false)

    override fun getCurrentSize(): Float = size
}