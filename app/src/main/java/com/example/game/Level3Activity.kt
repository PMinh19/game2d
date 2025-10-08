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
import com.example.game.ui.InvisibleMonsterUI
import com.example.game.ui.SoundControlButton
import com.example.game.ui.BagCoinAnimatedView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class Level3Activity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initAudio()

            // Initialize AI Avoidance Helper for smart bullet dodging
            try {
                AIAvoidanceHelper.init(this)
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue without AI if it fails
            }

            setContent {
                val density = LocalDensity.current
                val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

                Level3Game(
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    soundPool = soundPool,
                    shootSoundId = shootSoundId,
                    hitSoundId = hitSoundId,
                    onExit = { finish() }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish() // Exit gracefully if something goes wrong
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            AIAvoidanceHelper.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun Level3Game(
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

    // --- Invisible Monsters ---
    val invisibleMonsters = remember {
        List(12) {
            InvisibleMonster(
                x = Random.nextFloat() * (screenWidthPx - 100f),
                y = mutableStateOf(-Random.nextInt(200, 3000).toFloat()),
                speed = Random.nextFloat() * 1.5f + 1.5f,
                hp = mutableStateOf(100),
                invisibleDuration = 2000L, // Invisible for 2 seconds
                visibleDuration = 1500L     // Visible for 1.5 seconds
            )
        }
    }

    // Track respawn times
    val monsterRespawnTimes = remember { MutableList(invisibleMonsters.size) { 0L } }

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

            delay(200)

        }
    }

    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 25f }
            bullets.removeAll { it.y < -50f }
            delay(16)
        }
    }

    // --- Monster movement + invisible toggle ---
    invisibleMonsters.forEachIndexed { index, m ->
        LaunchedEffect(m, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Check if monster needs to respawn
                if (!m.alive.value && System.currentTimeMillis() >= monsterRespawnTimes[index]) {
                    m.y.value = -Random.nextInt(200, 1500).toFloat()
                    m.x = Random.nextFloat() * (screenWidthPx - 100f)
                    m.hp.value = 100
                    m.alive.value = true
                    m.lastToggleTime = System.currentTimeMillis()
                    m.isVisible.value = Random.nextBoolean() // Random start state
                }

                if (m.alive.value && m.hp.value > 0 && !timeActive) {
                    // AI-based evasion: monster tries to dodge bullets intelligently
                    val evasion = AIAvoidanceHelper.calculateEvasion(
                        monsterX = m.x,
                        monsterY = m.y.value,
                        monsterSize = 100f,
                        bullets = bullets,
                        screenWidth = screenWidthPx
                    )

                    // Apply AI evasion (combines with zigzag movement)
                    val aiDodgeX = evasion.first

                    // Toggle visibility
                    val currentTime = System.currentTimeMillis()
                    val elapsed = currentTime - m.lastToggleTime
                    val threshold = if (m.isVisible.value) m.visibleDuration else m.invisibleDuration

                    if (elapsed >= threshold) {
                        m.isVisible.value = !m.isVisible.value
                        m.lastToggleTime = currentTime
                    }

                    // Wall collision check
                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + 80f

                    if (wallActive && monsterBottom >= wallTop) {
                        // Stop at wall
                    } else {
                        // Normal movement
                        m.y.value += m.speed
                    }

                    // Zigzag movement combined with AI evasion
                    val combinedX = (m.horizontalSpeed * m.direction) + aiDodgeX
                    m.x = (m.x + combinedX).coerceIn(0f, screenWidthPx - 100f)

                    if (m.x <= 0 || m.x >= screenWidthPx - 100f) {
                        m.direction *= -1
                    }

                    // If monster passes plane
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) planeHp -= 50
                        monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(3000, 8000)
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

    // --- Bullet vs Monster collision (only when visible) ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            val iter = bullets.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                invisibleMonsters.forEach { m ->
                    // Can only hit visible monsters
                    if (m.isVisible.value && CollisionUtils.checkCollisionBulletInvisibleMonster(b, m)) {
                        m.hp.value -= 25
                        // Play hit sound
                        SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                        iter.remove()
                        if (m.hp.value <= 0) {
                            m.alive.value = false
                            val index = invisibleMonsters.indexOf(m)
                            if (index >= 0) {
                                monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(3000, 8000)
                            }
                        }
                    }
                }
            }
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

    // --- Plane - Monster collision (only when visible) ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            invisibleMonsters.forEach { m ->
                if (m.isVisible.value && CollisionUtils.checkCollisionPlaneInvisibleMonster(planeX, planeY, planeWidth, planeHeight, m)) {
                    if (!shieldActive && !wallActive) planeHp -= 50
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
                invisibleMonsters.forEach { m ->
                    if (m.isVisible.value && CollisionUtils.checkCollisionWallInvisibleMonster(planeY, m)) {
                        m.hp.value -= 2
                        if (m.hp.value <= 0) {
                            m.alive.value = false
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
            monsters = invisibleMonsters,
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
                painter = painterResource(R.drawable.nen3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.roundToInt()) },
                contentScale = ContentScale.Crop
            )
            // Hình nền phụ để tạo hiệu ứng lặp
            Image(
                painter = painterResource(R.drawable.nen3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (offsetY - screenHeightPx).roundToInt()) },
                contentScale = ContentScale.Crop
            )
        }

        // Game content with drag gesture
        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            // Monsters (Level 3 uses InvisibleMonsterUI)
            invisibleMonsters.forEach { m ->
                InvisibleMonsterUI(monster = m, level = 3)
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
                level = 3
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
                contentAlignment = androidx.compose.ui.Alignment.TopEnd
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
            level = 3,
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

                invisibleMonsters.forEachIndexed { index, m ->
                    m.x = Random.nextFloat() * (screenWidthPx - 100f)
                    m.y.value = -Random.nextInt(200, 3000).toFloat()
                    m.hp.value = 100
                    m.alive.value = true
                    m.isVisible.value = Random.nextBoolean()
                    m.lastToggleTime = System.currentTimeMillis()
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
            },
            playerName = playerName,
            totalScore = totalScore
        )
    }
}
