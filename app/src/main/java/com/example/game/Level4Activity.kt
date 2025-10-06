package com.example.game

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.TopBarComponent.TopBarUI
import com.example.game.core.*
import com.example.game.ui.PlaneUI
import com.example.game.ui.WallUI
import com.example.game.ui.GrowingMonsterUI
import com.example.game.ui.SoundControlButton
import com.example.game.ui.BagCoinAnimatedView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class Level4Activity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAudio()

        // Initialize AI Avoidance Helper for smart bullet dodging
        try {
            AIAvoidanceHelper.init(this)
        } catch (e: Exception) {
            android.util.Log.e("Level4Activity", "AI init failed: ${e.message}", e)
            // Continue without AI - game will still work with basic logic
        }

        setContent {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

            Level4Game(
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
            android.util.Log.e("Level4Activity", "AI release failed: ${e.message}", e)
        }
    }
}

@Composable
fun Level4Game(
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

    // Show dialog when game ends
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
    var offsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            offsetY += 4f
            if (offsetY >= screenHeightPx) {
                offsetY %= screenHeightPx
            }
            delay(16)
        }
    }

    // --- Growing Monsters ---
    val growingMonsters = remember {
        List(10) {
            GrowingMonster(
                x = Random.nextFloat() * (screenWidthPx - 200f) + 100f,
                y = mutableStateOf(-Random.nextInt(200, 1500).toFloat()),
                speed = Random.nextFloat() * 1.2f + 1.0f,
                hp = mutableStateOf(80),  // Tăng HP lên 80
                initialSize = 80f,  // Tăng kích thước ban đầu từ 60f lên 80f
                maxSize = 500f,  // Tăng kích thước tối đa từ 200f lên 500f
                growthRate = 0.5f  // Tăng tốc độ lớn từ 0.3f lên 0.5f
            )
        }
    }

    // Track respawn times
    val monsterRespawnTimes = remember { MutableList(growingMonsters.size) { 0L } }

    val coins = remember {
        List(6) {
            BaseCoin(
                x = Random.nextFloat() * (screenWidthPx - 50f),
                y = mutableStateOf(-Random.nextInt(100, 600).toFloat()),
                speed = Random.nextFloat() * 2f + 1f
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
            bullets.add(Bullet(planeX + planeWidth / 2f - 15f, planeY))
            SoundManager.playSoundEffect(soundPool, shootSoundId, 0.5f)
            delay(300)
        }
    }

    // --- Bullet movement ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 25f }
            bullets.removeAll { it.y < -50f }
            delay(16)
        }
    }

    // --- Monster movement + growing + AI evasion ---
    growingMonsters.forEachIndexed { index, m ->
        LaunchedEffect(m, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Check if monster needs to respawn
                if (!m.alive.value && System.currentTimeMillis() >= monsterRespawnTimes[index]) {
                    m.y.value = -Random.nextInt(200, 1500).toFloat()
                    m.x = Random.nextFloat() * (screenWidthPx - 200f) + 100f
                    m.hp.value = 80  // Sửa từ 50 thành 80 để khớp với initialSize mới
                    m.maxHp = 80
                    m.currentMaxHp.value = 80
                    m.currentSize.value = m.initialSize
                    m.alive.value = true
                }

                if (m.alive.value && !timeActive) {  // Thêm điều kiện !timeActive để grow khi không bị time stop
                    // AI-based evasion: monster tries to dodge bullets intelligently
                    val evasion = AIAvoidanceHelper.calculateEvasion(
                        monsterX = m.x,
                        monsterY = m.y.value,
                        monsterSize = m.currentSize.value,
                        bullets = bullets,
                        screenWidth = screenWidthPx
                    )

                    // Apply evasion movement (horizontal dodge)
                    m.x = (m.x + evasion.first).coerceIn(0f, screenWidthPx - m.currentSize.value)

                    // Grow over time
                    m.grow()

                    // Wall collision check
                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + m.currentSize.value

                    if (wallActive && monsterBottom >= wallTop) {
                        // Stop at wall
                    } else {
                        // Normal downward movement
                        m.y.value += m.speed
                    }

                    // If monster passes plane
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) {
                            // Damage scales with monster size
                            val damage = (30 * (m.currentSize.value / m.initialSize)).toInt()
                            planeHp -= damage
                        }
                        monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(500, 1500)  // Giảm thời gian respawn từ 1000-2500 xuống 500-1500
                        m.alive.value = false
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
                        c.y.value = -Random.nextInt(100, 600).toFloat()
                        c.x = Random.nextFloat() * (screenWidthPx - 50f)
                    }
                }
                delay(32)
            }
        }
    }

    // --- Bullet - Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            val toRemove = mutableSetOf<Bullet>()
            bullets.toList().forEach { b ->
                var shouldRemove = false
                growingMonsters.forEach { m ->
                    if (m.alive.value && !shouldRemove) {
                        if (CollisionUtils.checkCollisionBulletMonster(b, m)) {
                            m.hp.value -= 20
                            SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                            shouldRemove = true
                            if (m.hp.value <= 0) {
                                m.alive.value = false
                                val index = growingMonsters.indexOf(m)
                                if (index >= 0) {
                                    monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(500, 1500)
                                }
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
            growingMonsters.forEach { m ->
                if (m.alive.value && CollisionUtils.checkCollisionPlaneMonster(planeX, planeY, planeWidth, planeHeight, m)) {
                    if (!shieldActive) {
                        val damage = (30 * (m.currentSize.value / m.initialSize)).toInt()
                        planeHp -= damage
                    }
                    m.hp.value = 0
                    m.alive.value = false
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
                growingMonsters.forEach { m ->
                    if (m.alive.value) {
                        val wallTop = planeY - 60f
                        val monsterBottom = m.y.value + m.currentSize.value
                        if (monsterBottom >= wallTop) {
                            m.hp.value -= 2
                            if (m.hp.value <= 0) {
                                m.alive.value = false
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
        ChestItemEffectsBase.applyItemEffect(
            itemName = item.name,
            monsters = growingMonsters,
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
    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer - separate from drag gestures
        Box(modifier = Modifier.fillMaxSize()) {
            // Hình nền chính
            Image(
                painter = painterResource(R.drawable.nen4),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.roundToInt()) },
                contentScale = ContentScale.Crop
            )
            // Hình nền phụ để tạo hiệu ứng lặp
            Image(
                painter = painterResource(R.drawable.nen4),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (offsetY - screenHeightPx).roundToInt()) },
                contentScale = ContentScale.Crop
            )
        }

        // Game content with drag gesture
        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            // Growing Monsters
            growingMonsters.forEach { m ->
                GrowingMonsterUI(monster = m, level = 4)
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

            // Plane
            PlaneUI(
                planeX = planeX,
                planeY = planeY,
                planeHp = planeHp,
                shieldActive = shieldActive,
                level = 4
            )

            // Wall
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
    }

    // --- Game End Dialog ---
    if (showGameEndDialog) {
        GameEndDialog(
            isWin = isLevelClear,
            score = currentSessionScore,
            level = 4,
            onDismiss = {
                showGameEndDialog = false
            },
            onReplay = {
                // Reset game
                showGameEndDialog = false
                isGameOver = false
                isLevelClear = false
                planeHp = 100
                currentSessionScore = 0

                growingMonsters.forEachIndexed { index, m ->
                    m.x = Random.nextFloat() * (screenWidthPx - 200f) + 100f
                    m.y.value = -Random.nextInt(200, 2500).toFloat()
                    m.hp.value = 80
                    m.maxHp = 80
                    m.currentMaxHp.value = 80
                    m.currentSize.value = m.initialSize
                    m.alive.value = true
                    monsterRespawnTimes[index] = 0L
                }

                coins.forEach { c ->
                    c.collected.value = false
                    c.y.value = -Random.nextInt(100, 600).toFloat()
                    c.x = Random.nextFloat() * (screenWidthPx - 50f)
                }

                bullets.clear()
            },
            onNextLevel = {
                onExit()
            },
            onExit = {
                onExit()
            }
        )
    }
}
