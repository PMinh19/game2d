package com.example.game.core

import android.content.Context
import androidx.compose.runtime.MutableState
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * AI Helper for smart bullet avoidance
 * Hỗ trợ tất cả các loại monster thông qua IMonster interface
 */
object AIAvoidanceHelper {
    private var interpreter: Interpreter? = null
    private const val MODEL_FILE = "avoidance.tflite"

    // Cache để tối ưu hiệu năng
    private var lastUpdateTime = 0L
    private const val UPDATE_INTERVAL = 50L // ms

    // Lưu hướng di chuyển trước đó để tránh dao động
    private var lastEvasionDirection = 0f
    private var sameDirectionFrames = 0

    /**
     * Khởi tạo TensorFlow Lite model (nếu có)
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
     * Tính toán hướng né đạn thông minh
     * Hỗ trợ tất cả loại monster thông qua IMonster interface
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
            return Pair(lastEvasionDirection, 0f)
        }
        lastUpdateTime = currentTime

        // Lọc đạn nguy hiểm (đang bay về phía quái)
        val threateningBullets = bullets.filter { bullet ->
            val isBelowMonster = bullet.y > monsterY - 300f
            val isInHorizontalRange = abs(bullet.x - (monsterX + monsterSize / 2f)) < 150f
            isBelowMonster && isInHorizontalRange
        }

        if (threateningBullets.isEmpty()) {
            lastEvasionDirection = 0f
            sameDirectionFrames = 0
            return Pair(0f, 0f)
        }

        // Tính điểm an toàn cho 3 hướng: trái, giữa, phải
        val leftSafety = calculateDirectionSafety(
            monsterX - 8f, monsterY, monsterSize, threateningBullets, screenWidth
        )
        val staySafety = calculateDirectionSafety(
            monsterX, monsterY, monsterSize, threateningBullets, screenWidth
        )
        val rightSafety = calculateDirectionSafety(
            monsterX + 8f, monsterY, monsterSize, threateningBullets, screenWidth
        )

        // Chọn hướng an toàn nhất
        val evasionX = when {
            // Ưu tiên giữ hướng cũ nếu vẫn an toàn (tránh dao động)
            lastEvasionDirection < 0 && leftSafety > 0.3f && leftSafety >= rightSafety * 0.8f -> {
                sameDirectionFrames++
                -7f
            }
            lastEvasionDirection > 0 && rightSafety > 0.3f && rightSafety >= leftSafety * 0.8f -> {
                sameDirectionFrames++
                7f
            }
            // Chọn hướng mới tốt nhất
            leftSafety > rightSafety && leftSafety > staySafety -> {
                sameDirectionFrames = 0
                -7f
            }
            rightSafety > leftSafety && rightSafety > staySafety -> {
                sameDirectionFrames = 0
                7f
            }
            else -> {
                sameDirectionFrames = 0
                0f
            }
        }

        // Xử lý va chạm biên màn hình
        val finalEvasionX = when {
            monsterX <= 10f -> maxOf(0f, evasionX) // Gần biên trái
            monsterX + monsterSize >= screenWidth - 10f -> minOf(0f, evasionX) // Gần biên phải
            else -> evasionX
        }

        lastEvasionDirection = finalEvasionX
        return Pair(finalEvasionX, 0f)
    }

    /**
     * Overload cho IMonster - tự động lấy thông tin từ monster
     */
    // AIAvoidanceHelper.kt
    fun calculateEvasion(
        monster: IMonster,
        bullets: List<Bullet>,
        screenWidth: Float
    ): Pair<Float, Float> {
        // ✅ Snapshot tất cả values cùng lúc
        val snapshotX = monster.x
        val snapshotY = monster.y.value
        val snapshotSize = monster.getCurrentSize()

        return calculateEvasion(
            monsterX = snapshotX,
            monsterY = snapshotY,
            monsterSize = snapshotSize,
            bullets = bullets,
            screenWidth = screenWidth
        )
    }
    /**
     * Tính độ an toàn của một vị trí
     * Return: 0.0 (rất nguy hiểm) -> 1.0 (rất an toàn)
     */
    private fun calculateDirectionSafety(
        futureX: Float,
        futureY: Float,
        monsterSize: Float,
        bullets: List<Bullet>,
        screenWidth: Float
    ): Float {
        // Penalty nếu ra ngoài màn hình
        if (futureX < 0f || futureX + monsterSize > screenWidth) {
            return 0f
        }

        var safetyScore = 1.0f
        val monsterCenterX = futureX + monsterSize / 2f

        for (bullet in bullets) {
            // Dự đoán vị trí đạn sau vài frame
            val bulletSpeed = 25f
            val framesToCollision = (futureY - bullet.y) / bulletSpeed

            if (framesToCollision > 0 && framesToCollision < 20) {
                val futureCollisionY = bullet.y + (bulletSpeed * framesToCollision)
                val distanceX = abs(bullet.x - monsterCenterX)

                // Nếu đạn sẽ va chạm
                if (distanceX < monsterSize / 2f + 10f &&
                    futureCollisionY >= futureY &&
                    futureCollisionY <= futureY + monsterSize) {
                    return 0f // Cực kỳ nguy hiểm
                }

                // Tính penalty dựa trên khoảng cách
                val distancePenalty = 1f - (distanceX / 150f).coerceIn(0f, 1f)
                val timePenalty = 1f - (framesToCollision / 20f).coerceIn(0f, 1f)
                safetyScore -= (distancePenalty * timePenalty * 0.5f)
            }
        }

        return safetyScore.coerceIn(0f, 1f)
    }

    /**
     * Tính mức độ đe dọa tổng thể
     */
    fun calculateThreatLevel(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullets: List<Bullet>
    ): Float {
        if (bullets.isEmpty()) return 0f

        val monsterCenterX = monsterX + monsterSize / 2f
        var maxThreat = 0f

        for (bullet in bullets) {
            val dx = bullet.x - monsterCenterX
            val dy = bullet.y - monsterY
            val distance = sqrt(dx * dx + dy * dy)

            // Chỉ tính đạn đang bay về phía quái
            if (dy > 0f) {
                val threat = when {
                    distance < 80f -> 1.0f
                    distance < 150f -> 0.8f
                    distance < 250f -> 0.5f
                    distance < 350f -> 0.3f
                    else -> 0.1f
                }
                maxThreat = maxOf(maxThreat, threat)
            }
        }

        return maxThreat
    }

    /**
     * Overload cho IMonster
     */
    fun calculateThreatLevel(
        monster: IMonster,
        bullets: List<Bullet>
    ): Float {
        return calculateThreatLevel(
            monsterX = monster.x,
            monsterY = monster.y.value,
            monsterSize = monster.getCurrentSize(),
            bullets = bullets
        )
    }

    /**
     * Dự đoán va chạm trong tương lai
     */
    fun willCollide(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullet: Bullet,
        lookaheadFrames: Int = 15
    ): Boolean {
        val bulletSpeed = 25f
        val monsterCenterX = monsterX + monsterSize / 2f

        for (frame in 1..lookaheadFrames) {
            val futureY = bullet.y - (bulletSpeed * frame)

            // Kiểm tra va chạm theo trục Y
            if (futureY <= monsterY + monsterSize && futureY >= monsterY - 10f) {
                // Kiểm tra va chạm theo trục X
                val distanceX = abs(bullet.x - monsterCenterX)
                if (distanceX <= monsterSize / 2f + 5f) {
                    return true
                }
            }

            // Đạn đã bay qua
            if (futureY < monsterY - 50f) {
                break
            }
        }

        return false
    }

    /**
     * Tìm hướng thoát tốt nhất khi bị bao vây
     */
    fun findEscapeRoute(
        monsterX: Float,
        monsterY: Float,
        monsterSize: Float,
        bullets: List<Bullet>,
        screenWidth: Float
    ): Float {
        val leftSpace = monsterX
        val rightSpace = screenWidth - (monsterX + monsterSize)

        // Đếm số đạn mỗi bên
        val leftBullets = bullets.count { it.x < monsterX }
        val rightBullets = bullets.count { it.x > monsterX + monsterSize }

        return when {
            leftBullets < rightBullets && leftSpace > 50f -> -8f
            rightBullets < leftBullets && rightSpace > 50f -> 8f
            leftSpace > rightSpace -> -8f
            else -> 8f
        }
    }

    /**
     * Reset trạng thái AI (gọi khi reset game)
     */
    fun reset() {
        lastEvasionDirection = 0f
        sameDirectionFrames = 0
        lastUpdateTime = 0L
    }

    /**
     * Giải phóng tài nguyên
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        reset()
    }
}