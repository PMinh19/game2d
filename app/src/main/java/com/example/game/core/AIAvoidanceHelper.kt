package com.example.game.core

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * AI Helper for smart bullet avoidance using TensorFlow Lite model
 * Simple implementation: analyzes bullet positions and suggests evasion direction
 */
object AIAvoidanceHelper {
    private var interpreter: Interpreter? = null
    private const val MODEL_FILE = "avoidance.tflite"

    // Thời gian cache để tránh tính toán quá nhiều
    private var lastUpdateTime = 0L
    private const val UPDATE_INTERVAL = 50L // ms

    /**
     * Initialize the TensorFlow Lite model
     */
    fun init(context: Context) {
        try {
            val model = loadModelFile(context)
            if (model != null) {
                interpreter = Interpreter(model)
            } else {
                interpreter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculate the best evasion direction for a monster to avoid bullets
     * Returns a pair of (deltaX, deltaY) indicating the direction to move
     */
    fun calculateEvasion(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullets: List<Bullet>,
        screenWidth: Float
    ): Pair<Float, Float> {
        // Throttle updates
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
            return Pair(0f, 0f)
        }
        lastUpdateTime = currentTime

        // Find threatening bullets
        val threateningBullets = bullets.filter { bullet ->
            val isBelowMonster = bullet.y > monsterY - 250f
            val isInRange = bullet.x >= monsterX - 120f && bullet.x <= monsterX + monsterSize + 120f
            isBelowMonster && isInRange
        }

        if (threateningBullets.isEmpty()) {
            return Pair(0f, 0f)
        }

        // Closest threatening bullet
        val closestBullet = threateningBullets.minByOrNull { bullet ->
            val dx = bullet.x - (monsterX + monsterSize / 2f)
            val dy = bullet.y - monsterY
            sqrt(dx * dx + dy * dy)
        } ?: return Pair(0f, 0f)

        val bulletToMonsterX = (monsterX + monsterSize / 2f) - closestBullet.x

        // Base evasion (horizontal only)
        val evasionX = when {
            bulletToMonsterX > 0 -> 6f  // move right
            bulletToMonsterX < 0 -> -6f // move left
            else -> if (Math.random() > 0.5) 6f else -6f
        }

        // --- FIX: rebound logic ---
        val finalEvasionX = when {
            monsterX <= 0f -> 6f // đang chạm trái → bật sang phải
            monsterX + monsterSize >= screenWidth -> -6f // đang chạm phải → bật sang trái
            else -> evasionX
        }

        return Pair(finalEvasionX, 0f)
    }

    /**
     * Calculate threat level (0.0 to 1.0) based on bullet proximity
     */
    fun calculateThreatLevel(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullets: List<Bullet>
    ): Float {
        if (bullets.isEmpty()) return 0f

        val closestDistance = bullets.minOfOrNull { bullet ->
            val dx = bullet.x - (monsterX + monsterSize / 2f)
            val dy = bullet.y - monsterY
            sqrt(dx * dx + dy * dy)
        } ?: Float.MAX_VALUE

        return when {
            closestDistance < 100f -> 1.0f
            closestDistance < 200f -> 0.7f
            closestDistance < 300f -> 0.4f
            else -> 0.1f
        }
    }

    /**
     * Predict if a collision will occur in the near future
     */
    fun willCollide(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullet: Bullet,
        lookaheadFrames: Int = 10
    ): Boolean {
        val bulletSpeed = 25f

        for (frame in 1..lookaheadFrames) {
            val futureY = bullet.y - (bulletSpeed * frame)
            if (futureY <= monsterY + monsterSize && futureY >= monsterY) {
                if (bullet.x >= monsterX && bullet.x <= monsterX + monsterSize) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Release resources
     */
    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
