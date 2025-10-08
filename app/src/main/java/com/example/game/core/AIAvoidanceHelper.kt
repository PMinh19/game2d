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
    private const val UPDATE_INTERVAL = 50L // ms - Giảm từ 100ms xuống 50ms để phản ứng nhanh hơn

    /**
     * Initialize the TensorFlow Lite model
     */
    fun init(context: Context) {
        try {
            val model = loadModelFile(context)
            if (model != null) {
                interpreter = Interpreter(model)
            } else {
                // Model file not found, continue without AI
                interpreter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: nếu không load được model, sẽ dùng logic đơn giản
            // Không throw exception, chỉ log lỗi
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
            null // Return null if model file not found
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

        // Find threatening bullets (bullets that are close and moving towards monster)
        val threateningBullets = bullets.filter { bullet ->
            // Check if bullet is below monster (moving upward toward it)
            val isBelowMonster = bullet.y > monsterY - 250f // Tăng từ 200f lên 250f để phát hiện sớm hơn
            // Check if bullet is in horizontal range
            val isInRange = bullet.x >= monsterX - 120f && bullet.x <= monsterX + monsterSize + 120f // Tăng từ 100f lên 120f
            isBelowMonster && isInRange
        }

        if (threateningBullets.isEmpty()) {
            return Pair(0f, 0f) // No threat, no need to evade
        }

        // Simple AI logic: move away from the closest threatening bullet
        val closestBullet = threateningBullets.minByOrNull { bullet ->
            val dx = bullet.x - (monsterX + monsterSize / 2f)
            val dy = bullet.y - monsterY
            sqrt(dx * dx + dy * dy)
        } ?: return Pair(0f, 0f)

        // Calculate evasion direction (move perpendicular to bullet trajectory)
        val bulletToMonsterX = (monsterX + monsterSize / 2f) - closestBullet.x

        // Move away from bullet horizontally - Tăng tốc độ né từ 3f lên 6f
        val evasionX = when {
            bulletToMonsterX > 0 -> 6f // Move right faster
            bulletToMonsterX < 0 -> -6f // Move left faster
            else -> if (Math.random() > 0.5) 6f else -6f // Random if directly aligned
        }

        // Check screen boundaries
        val finalEvasionX = when {
            monsterX + evasionX < 0 -> 6f // Too close to left, move right
            monsterX + monsterSize + evasionX > screenWidth -> -6f // Too close to right, move left
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

        // Threat is high when bullet is close (within 300px)
        return when {
            closestDistance < 100f -> 1.0f // Very high threat
            closestDistance < 200f -> 0.7f // High threat
            closestDistance < 300f -> 0.4f // Medium threat
            else -> 0.1f // Low threat
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
        // Simple prediction: check if bullet trajectory will intersect monster
        val bulletSpeed = 25f // Bullet moves up at -25f per frame

        for (frame in 1..lookaheadFrames) {
            val futureY = bullet.y - (bulletSpeed * frame)

            // Check if bullet will be at monster's Y position
            if (futureY <= monsterY + monsterSize && futureY >= monsterY) {
                // Check horizontal alignment
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
