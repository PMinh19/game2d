package com.example.game

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.TopBarComponent.TopBarUI
import com.example.game.core.*
import com.example.game.ui.PlaneUI
import com.example.game.ui.MonsterUI
import com.example.game.ui.WallUI
import com.example.game.ui.SoundControlButton
import com.example.game.ui.BagCoinAnimatedView
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

class Level2Activity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAudio()

        // Initialize AI Avoidance Helper for smart bullet dodging
        try {
            AIAvoidanceHelper.init(this)
        } catch (e: Exception) {
            android.util.Log.e("Level2Activity", "AI init failed: ${e.message}", e)
            // Continue without AI - game will still work with basic logic
        }

        setContent {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

            Level2Game(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                soundPool = soundPool,
                shootSoundId = shootSoundId,
                hitSoundId = hitSoundId,
                onExit = { finish() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            AIAvoidanceHelper.release()
        } catch (e: Exception) {
            android.util.Log.e("Level2Activity", "AI release failed: ${e.message}", e)
        }
    }
}

@Composable
fun Level2Game(
    screenWidthPx: Float,
    screenHeightPx: Float,
    soundPool: android.media.SoundPool,
    shootSoundId: Int,
    hitSoundId: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val playerName = PrefManager.getPlayerName(context)
    val coroutineScope = rememberCoroutineScope()

    // --- State ---
    var totalScore by remember { mutableStateOf(0) }
    var currentSessionScore by remember { mutableStateOf(0) }
    var planeHp by remember { mutableStateOf(100) }

    var shieldActive by remember { mutableStateOf(false) }
    var wallActive by remember { mutableStateOf(false) }
    var timeActive by remember { mutableStateOf(false) }

    var isGameOver by remember { mutableStateOf(false) }
    var isLevelClear by remember { mutableStateOf(false) }
    var showGameEndDialog by remember { mutableStateOf(false) }

    // Show dialog when game ends instead of navigating to new activity
    LaunchedEffect(isGameOver, isLevelClear) {
        if (isGameOver || isLevelClear) {
            delay(500)
            showGameEndDialog = true
        }
    }

    // --- Plane setup ---
    var planeX by remember { mutableStateOf(screenWidthPx / 2 - 50f) }
    val planeY = screenHeightPx - 250f
    val planeWidth = 100f
    val planeHeight = 100f

    // --- Background ---
    var bg1Y by remember { mutableStateOf(0f) }
    var bg2Y by remember { mutableStateOf(-screenHeightPx) }
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bg1Y += 4f
            bg2Y += 4f
            if (bg1Y >= screenHeightPx) bg1Y = bg2Y - screenHeightPx
            if (bg2Y >= screenHeightPx) bg2Y = bg1Y - screenHeightPx
            delay(16)
        }
    }

    // --- Entities: 5 rotating monster groups ---
    val monsterGroups = remember {
        List(5) { i ->
            RotatingMonsterGroup(
                centerX = Random.nextFloat() * (screenWidthPx - 300f) + 150f, // Random X position
                centerY = -300f, // Start just above screen
                radius = 100f,
                angleOffset = Random.nextFloat() * 360f, // Random starting angle
                vx = if (Random.nextBoolean()) Random.nextFloat() * 2f + 2f else -(Random.nextFloat() * 2f + 2f),
                vy = Random.nextFloat() * 2f + 3f // Random vertical speed (3-5f)
            ).apply {
                // Initially set monsters as dead - they will spawn with delay
                monsters.forEach { it.alive.value = false }
            }
        }
    }

    // Track respawn times for each group
    val groupRespawnTimes = remember { MutableList(monsterGroups.size) { i -> System.currentTimeMillis() + (i * 3000L) } }

    val coins = remember {
        List(7) {
            BaseCoin(
                x = Random.nextFloat() * (screenWidthPx - 50f),
                y = mutableStateOf(-Random.nextInt(100, 800).toFloat()),
                speed = Random.nextFloat() * 2f + 1.5f
            )
        }
    }

    val bullets = remember { mutableStateListOf<Bullet>() }
    val bagCoins = remember { mutableStateListOf<BagCoinDisplay>() }
    var chestItems by remember { mutableStateOf<List<ChestItem>>(emptyList()) }

    // --- Load player data ---
    LaunchedEffect(Unit) {
        if (!playerName.isNullOrBlank()) {
            FirebaseHelper.syncNewPlayer(playerName)
            FirebaseHelper.getScore(playerName) { totalScore = it }
            FirebaseHelper.getChestItems(playerName) { chestItems = it }
        }
    }

    // --- Shooting ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.add(Bullet(planeX + planeWidth / 2 - 15f, planeY))
            SoundManager.playSoundEffect(soundPool, shootSoundId, 0.5f)
            delay(300)
        }
    }

    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 25f }
            bullets.removeAll { it.y < -50f }
            delay(16)
        }
    }

    // --- Monster groups movement + rotation ---
    monsterGroups.forEachIndexed { index, group ->
        LaunchedEffect(group, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Check if group needs to respawn
                val allDead = group.monsters.all { !it.alive.value }
                if (allDead && System.currentTimeMillis() >= groupRespawnTimes[index]) {
                    // Respawn group at random position
                    group.centerX = Random.nextFloat() * (screenWidthPx - 300f) + 150f
                    group.centerY = -300f
                    group.vx = if (Random.nextBoolean()) Random.nextFloat() * 2f + 2f else -(Random.nextFloat() * 2f + 2f)
                    group.vy = Random.nextFloat() * 2f + 3f
                    group.angleOffset = Random.nextFloat() * 360f // Random rotation angle
                    group.monsters.forEach { m ->
                        m.hp.value = 100
                        m.alive.value = true
                    }
                    // Update positions to match new center and angle
                    group.updatePositions()
                    // Set next respawn time with random delay (3-5 seconds)
                    groupRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(3000, 5000)
                }

                if (!timeActive && !allDead) {
                    // Rotate monsters
                    group.angleOffset += 3f

                    // Move center
                    group.centerX += group.vx
                    group.centerY += group.vy

                    // Bounce off LEFT wall - reverse to RIGHT
                    if (group.centerX <= 150f) {
                        group.centerX = 150f
                        group.vx = abs(group.vx) // Bounce to opposite direction (right)
                    }

                    // Bounce off RIGHT wall - reverse to LEFT
                    if (group.centerX >= screenWidthPx - 150f) {
                        group.centerX = screenWidthPx - 150f
                        group.vx = -abs(group.vx) // Bounce to opposite direction (left)
                    }

                    // Bounce off TOP - reverse to DOWN
                    if (group.centerY <= 150f) {
                        group.centerY = 150f
                        group.vy = abs(group.vy) // Bounce to opposite direction (down)
                    }

                    // Bounce off BOTTOM - reverse to UP
                    if (group.centerY >= screenHeightPx - 300f) {
                        group.centerY = screenHeightPx - 300f
                        group.vy = -abs(group.vy) // Bounce to opposite direction (up)
                    }

                    // Update monster positions
                    group.updatePositions()

                    // Respawn if group goes way off screen (safety check)
                    if (group.centerY > screenHeightPx + 500f || group.centerY < -1000f) {
                        groupRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(2000, 5000)
                        group.monsters.forEach { it.alive.value = false }
                    }
                }
                delay(16)
            }
        }
    }

    // --- Coin movement ---
    coins.forEach { c ->
        LaunchedEffect(c, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                if (!c.collected.value && !timeActive) {
                    c.y.value += c.speed
                    if (c.y.value > screenHeightPx) {
                        c.y.value = -Random.nextInt(100, 800).toFloat()
                        c.x = Random.nextFloat() * (screenWidthPx - 50f)
                    }
                }
                delay(32)
            }
        }
    }

    // --- Bullet vs Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            val toRemove = mutableSetOf<Bullet>()
            bullets.toList().forEach { b ->
                var shouldRemove = false
                monsterGroups.forEach { group ->
                    group.monsters.forEach { m ->
                        if (m.alive.value && CollisionUtils.checkCollisionBulletMonster(b, m)) {
                            m.hp.value -= 25
                            // Play hit sound
                            SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                            shouldRemove = true
                            if (m.hp.value <= 0) {
                                m.alive.value = false
                            }
                        }
                    }
                }
                if (shouldRemove) {
                    toRemove.add(b)
                }
            }
            bullets.removeAll(toRemove)
            delay(16)
        }
    }

    // --- Plane - Coin collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            coins.forEach { c ->
                if (!c.collected.value && CollisionUtils.checkCollisionPlaneCoin(planeX, planeY, planeWidth, planeHeight, c)) {
                    c.collected.value = true
                    totalScore += 1
                    currentSessionScore += 1
                    val bag = BagCoinDisplay(c.x, c.y.value, 1)
                    bagCoins.add(bag)
                    if (!playerName.isNullOrBlank()) FirebaseHelper.updateScore(playerName, totalScore)
                }
            }
            delay(50)
        }
    }

    // --- Plane - Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            monsterGroups.forEach { group ->
                group.monsters.forEach { m ->
                    if (m.alive.value && m.hp.value > 0 &&
                        CollisionUtils.checkCollisionPlaneMonster(planeX, planeY, planeWidth, planeHeight, m)
                    ) {
                        if (!shieldActive && !wallActive) planeHp -= 50
                        m.hp.value = 0
                        m.alive.value = false
                    }
                }
            }
            if (planeHp <= 0) isGameOver = true
            delay(50)
        }
    }

    // --- Wall - Monster collision ---
    LaunchedEffect(wallActive, isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            if (wallActive) {
                monsterGroups.forEach { group ->
                    group.monsters.forEach { m ->
                        if (m.alive.value && m.hp.value > 0) {
                            if (CollisionUtils.checkCollisionWallMonster(planeY, m)) {
                                m.hp.value -= 2
                                if (m.hp.value <= 0) {
                                    m.alive.value = false
                                }
                            }
                        }
                    }
                }
            }
            delay(50)
        }
    }

    // --- Use chest item ---
    fun useChestItem(item: ChestItem) {
        // Flatten all monsters from groups for ChestItemEffectsBase
        val allMonsters = monsterGroups.flatMap { it.monsters }
        ChestItemEffectsBase.applyItemEffect(
            itemName = item.name,
            monsters = allMonsters,
            coins = coins,
            bagCoins = bagCoins,
            coroutineScope = coroutineScope,
            screenHeightPx = screenHeightPx,
            planeX = planeX,
            onScoreUpdate = { add ->
                totalScore += add
                currentSessionScore += add
                if (!playerName.isNullOrBlank()) FirebaseHelper.updateScore(playerName, totalScore)
            },
            onShieldToggle = { active -> shieldActive = active },
            onWallToggle = { active -> wallActive = active },
            onTimeToggle = { active -> timeActive = active },
            onLevelClear = { isLevelClear = true }
        )
        chestItems = chestItems - item
        if (!playerName.isNullOrBlank()) FirebaseHelper.updateChest(playerName, chestItems)
    }

    // --- Drag plane ---
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - planeWidth)
            change.consume()
        }
    }

    // --- UI ---
    Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
        // Background
        Image(
            painter = painterResource(R.drawable.nen2),
            contentDescription = null,
            modifier = Modifier.absoluteOffset { IntOffset(0, bg1Y.roundToInt()) }.fillMaxSize()
        )
        Image(
            painter = painterResource(R.drawable.nen2),
            contentDescription = null,
            modifier = Modifier.absoluteOffset { IntOffset(0, bg2Y.roundToInt()) }.fillMaxSize()
        )

        // Monsters (using MonsterUI component)
        monsterGroups.forEach { group ->
            group.monsters.forEach { m ->
                MonsterUI(monster = m)
            }
        }

        // Coins
        coins.filter { !it.collected.value }.forEach { c ->
            Image(
                painter = painterResource(R.drawable.coin),
                contentDescription = null,
                modifier = Modifier
                    .absoluteOffset { IntOffset(c.x.roundToInt(), c.y.value.roundToInt()) }
                    .size(40.dp)
            )
        }

        // BagCoin animated views
        bagCoins.toList().forEach { bag ->
            BagCoinAnimatedView(bag = bag, onFinished = { finishedBag ->
                bagCoins.remove(finishedBag)
            })
        }

        // Bullets
        bullets.forEach { b ->
            Image(
                painter = painterResource(R.drawable.dan2),
                contentDescription = null,
                modifier = Modifier
                    .absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }
                    .size(30.dp)
            )
        }

        // Plane (using PlaneUI component)
        PlaneUI(
            planeX = planeX,
            planeY = planeY,
            planeHp = planeHp,
            shieldActive = shieldActive
        )

        // Wall (using WallUI component)
        if (wallActive) {
            WallUI(planeY = planeY)
        }

        // Top bar
        TopBarUI(
            bagCoinScore = totalScore,
            chestItems = chestItems,
            onBuyItem = { item, price ->
                if (totalScore >= price) {
                    totalScore -= price
                    chestItems = chestItems + item
                    if (!playerName.isNullOrBlank()) {
                        FirebaseHelper.updateScore(playerName, totalScore)
                        FirebaseHelper.updateChest(playerName, chestItems)
                    }
                }
            },
            onUseChestItem = { useChestItem(it) }
        )

        // --- Sound Control Button (top-right corner) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            SoundControlButton()
        }
    }

    // --- Game End Dialog ---
    if (showGameEndDialog) {
        GameEndDialog(
            isWin = isLevelClear,
            score = currentSessionScore,
            level = 2,
            onDismiss = {
                showGameEndDialog = false
            },
            onReplay = {
                // Reset game state to replay
                showGameEndDialog = false
                isGameOver = false
                isLevelClear = false
                planeHp = 100
                currentSessionScore = 0

                // Reset monsters
                monsterGroups.forEach { group ->
                    group.centerX = Random.nextFloat() * (screenWidthPx - 300f) + 150f
                    group.centerY = -300f
                    group.vx = if (Random.nextBoolean()) Random.nextFloat() * 2f + 2f else -(Random.nextFloat() * 2f + 2f)
                    group.vy = Random.nextFloat() * 2f + 3f
                    group.angleOffset = Random.nextFloat() * 360f
                    group.monsters.forEach { m ->
                        m.hp.value = 100
                        m.alive.value = false
                    }
                }

                // Reset respawn times
                for (i in groupRespawnTimes.indices) {
                    groupRespawnTimes[i] = System.currentTimeMillis() + (i * 3000L)
                }

                // Reset coins
                coins.forEach { c ->
                    c.collected.value = false
                    c.y.value = -Random.nextInt(100, 800).toFloat()
                    c.x = Random.nextFloat() * (screenWidthPx - 50f)
                }

                // Clear bullets
                bullets.clear()
            },
            onNextLevel = {
                // Navigate to next level handled by GameEndDialog itself
                onExit() // Close this activity
            },
            onExit = {
                // Back to main menu
                onExit()
            }
        )
    }
}

/**
 * Rotating Monster Group - 3 monsters rotating around a center point
 */
class RotatingMonsterGroup(
    var centerX: Float,
    var centerY: Float,
    var radius: Float,
    var angleOffset: Float,
    var vx: Float, // velocity X
    var vy: Float  // velocity Y
) {
    val monsters = List(3) { i ->
        val angle = angleOffset + i * 120f
        val rad = Math.toRadians(angle.toDouble())
        BaseMonster(
            x = (centerX + cos(rad) * radius).toFloat(),
            y = mutableStateOf((centerY + sin(rad) * radius).toFloat()),
            speed = 0f,
            hp = mutableStateOf(100)
        )
    }

    fun updatePositions() {
        val angles = listOf(0f, 120f, 240f)
        monsters.forEachIndexed { i, m ->
            if (m.alive.value) {
                val rad = Math.toRadians((angleOffset + angles[i]).toDouble())
                m.x = (centerX + cos(rad) * radius).toFloat()
                m.y.value = (centerY + sin(rad) * radius).toFloat()
            }
        }
    }
}
