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
import com.example.game.ui.MonsterUI
import com.example.game.ui.WallUI
import com.example.game.ui.SoundControlButton
import com.example.game.ui.BagCoinAnimatedView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class GameScreenActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAudio()

        // Initialize AI Avoidance Helper for smart bullet dodging
        try {
            AIAvoidanceHelper.init(this)
        } catch (e: Exception) {
            android.util.Log.e("GameScreenActivity", "AI init failed: ${e.message}", e)
            // Continue without AI - game will still work with basic logic
        }

        setContent {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

            GameScreen(
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
            android.util.Log.e("GameScreenActivity", "AI release failed: ${e.message}", e)
        }
    }
}

@Composable
fun GameScreen(
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
    var planeHp by remember { mutableStateOf(100) } // 0..100

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

    // --- Plane ---
    var planeX by remember { mutableStateOf(screenWidthPx / 2 - 50f) }
    val planeY = screenHeightPx - 250f
    val planeWidth = 100f
    val planeHeight = 100f

    // --- Background (2 images loop) ---
    var offsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            offsetY += 4f
            // Sử dụng modulo để giữ chuyển động liên tục
            if (offsetY >= screenHeightPx) {
                offsetY %= screenHeightPx
            }
            delay(16)
        }
    }

    // --- Entities ---
    val monsters = remember {
        List(10) {
            BaseMonster(
                x = Random.nextFloat() * (screenWidthPx - 100f),
                // Spawn monsters at different Y positions (more spread out)
                y = mutableStateOf(-Random.nextInt(200, 2000).toFloat()), // Increased range
                speed = Random.nextFloat() * 1.5f + 1.5f,
                hp = mutableStateOf(100)
            )
        }
    }

    // Track respawn times for each monster
    val monsterRespawnTimes = remember { MutableList(monsters.size) { 0L } }

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
    val bagCoins = remember { mutableStateListOf<BagCoinDisplay>() } // uses core.BagCoinDisplay
    var chestItems by remember { mutableStateOf<List<ChestItem>>(emptyList()) }

    // --- Load player data (score / chest) ---
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
            delay(350)
        }
    }

    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 20f }
            bullets.removeAll { it.y < -40f }
            delay(16)
        }
    }

    // --- Monster movement ---
    monsters.forEachIndexed { index, m ->
        LaunchedEffect(m, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Check if monster needs to respawn after death
                if (!m.alive.value && System.currentTimeMillis() >= monsterRespawnTimes[index]) {
                    // Respawn the monster at a random position
                    m.y.value = -Random.nextInt(200, 1500).toFloat()
                    m.x = Random.nextFloat() * (screenWidthPx - 100f)
                    m.hp.value = 100
                    m.alive.value = true
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

                    // Apply evasion movement (horizontal dodge)
                    m.x = (m.x + evasion.first).coerceIn(0f, screenWidthPx - 100f)

                    // Wall is at planeY - 60f, so stop monsters closer to actually touch it
                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + 80f // Monster height is 80px

                    if (wallActive && monsterBottom >= wallTop) {
                        // Monster has reached wall - STOP here
                        // Wall collision check will drain HP
                    } else {
                        // Normal movement
                        m.y.value += m.speed
                    }

                    // If monster falls below plane (passed), damage plane and respawn
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) planeHp -= 50
                        // Schedule respawn with random delay (3-8 seconds)
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

    // --- Bullet vs Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            val iter = bullets.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                monsters.forEach { m ->
                    if (CollisionUtils.checkCollisionBulletMonster(b, m)) {
                        // hit: reduce monster HP
                        m.hp.value -= 25
                        // Play hit sound
                        SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                        iter.remove()
                        if (m.hp.value <= 0) {
                            m.alive.value = false
                            // Schedule respawn with random delay (3-8 seconds)
                            val index = monsters.indexOf(m)
                            if (index >= 0) {
                                monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(3000, 8000)
                            }
                        }
                    }
                }
            }
            // DON'T check for level clear here - let monsters respawn naturally
            delay(16)
        }
    }

    // --- Plane - Coin collision (collect coin) ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            coins.forEach { c ->
                if (!c.collected.value && CollisionUtils.checkCollisionPlaneCoin(planeX, planeY, planeWidth, planeHeight, c)) {
                    c.collected.value = true
                    totalScore += 1
                    currentSessionScore += 1
                    // add bag coin display (will be animated in UI)
                    val bag = BagCoinDisplay(c.x, c.y.value, 1)
                    bagCoins.add(bag)
                    // update backend
                    if (!playerName.isNullOrBlank()) FirebaseHelper.updateScore(playerName, totalScore)
                }
            }
            delay(50)
        }
    }

    // --- Plane - Monster collision (direct hit) ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            monsters.forEach { m ->
                if (m.alive.value && m.hp.value > 0 &&
                    CollisionUtils.checkCollisionPlaneMonster(planeX, planeY, planeWidth, planeHeight, m)
                ) {
                    if (!shieldActive && !wallActive) planeHp -= 50
                    // kill this monster
                    m.hp.value = 0
                    m.alive.value = false
                }
            }
            if (planeHp <= 0) isGameOver = true
            delay(50)
        }
    }

    // --- Wall - Monster collision (wall damages monsters) ---
    LaunchedEffect(wallActive, isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            if (wallActive) {
                monsters.forEach { m ->
                    if (m.alive.value && m.hp.value > 0) {
                        val isColliding = CollisionUtils.checkCollisionWallMonster(planeY, m)
                        if (isColliding) {
                            // Wall drains monster HP continuously
                            m.hp.value -= 2 // Drain 2 HP per tick
                            if (m.hp.value <= 0) {
                                m.alive.value = false
                            }
                        }
                    }
                }
            }
            delay(50) // Check every 50ms
        }
    }

    // --- Use chest item wrapper ---
    fun useChestItem(item: ChestItem) {
        ChestItemEffectsBase.applyItemEffect(
            itemName = item.name,
            monsters = monsters,
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
                painter = painterResource(R.drawable.nen2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.roundToInt()) },
                contentScale = ContentScale.Crop
            )
            // Hình nền phụ để tạo hiệu ứng lặp
            Image(
                painter = painterResource(R.drawable.nen2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (offsetY - screenHeightPx).roundToInt()) },
                contentScale = ContentScale.Crop
            )
        }

        // Game content with drag gesture
        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            // --- Monsters (using MonsterUI component) ---
            monsters.forEach { m ->
                MonsterUI(monster = m)
            }

            // --- Coins ---
            coins.filter { !it.collected.value }.forEach { c ->
                Image(
                    painter = painterResource(R.drawable.coin),
                    contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(c.x.roundToInt(), c.y.value.roundToInt()) }
                        .size(40.dp)
                )
            }

            // --- BagCoin animated views (spawned when coin collected) ---
            bagCoins.toList().forEach { bag ->
                BagCoinAnimatedView(bag = bag, onFinished = { finishedBag ->
                    bagCoins.remove(finishedBag)
                })
            }

            // --- Bullets ---
            bullets.forEach { b ->
                Image(
                    painter = painterResource(R.drawable.dan2),
                    contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }
                        .size(30.dp)
                )
            }

            // --- Plane (using PlaneUI component) ---
            PlaneUI(
                planeX = planeX,
                planeY = planeY,
                planeHp = planeHp,
                shieldActive = shieldActive
            )

            // --- Wall effect (using WallUI component) ---
            if (wallActive) {
                WallUI(planeY = planeY)
            }

            // --- Top bar with chest items and score ---
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
            level = 1,
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
                monsters.forEachIndexed { index, m ->
                    m.x = Random.nextFloat() * (screenWidthPx - 100f)
                    m.y.value = -Random.nextInt(200, 2000).toFloat()
                    m.hp.value = 100
                    m.alive.value = true
                    monsterRespawnTimes[index] = 0L
                }

                // Reset coins
                coins.forEach { c ->
                    c.collected.value = false
                    c.y.value = -Random.nextInt(100, 600).toFloat()
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
            },
            playerName = playerName,
            totalScore = totalScore
        )
    }
}
