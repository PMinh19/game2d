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
import com.example.game.ui.SplittingMonsterUI
import com.example.game.ui.SoundControlButton
import com.example.game.ui.BagCoinAnimatedView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class Level5Activity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            Level5Game(
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
            e.printStackTrace()
        }
    }
}

@Composable
fun Level5Game(
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

    // --- Splitting Monsters (dynamic list) ---
    val splittingMonsters = remember { mutableStateListOf<SplittingMonster>() }

    // Initialize with some parent monsters
    LaunchedEffect(Unit) {
        repeat(8) {
            splittingMonsters.add(
                SplittingMonster(
                    x = Random.nextFloat() * (screenWidthPx - 80f),
                    y = mutableStateOf(-Random.nextInt(200, 2000).toFloat()),
                    speed = Random.nextFloat() * 1.5f + 1.5f,
                    hp = mutableStateOf(100),
                    size = 80f,
                    generation = 1
                )
            )
        }
    }

    val coins = remember {

        List(5) {

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

    // --- Monster movement (zigzag or bounce) + AI evasion ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            // Create a snapshot to avoid concurrent modification
            val currentMonsters = splittingMonsters.toList()
            currentMonsters.forEach { m ->
                if (m.alive.value && m.hp.value > 0 && !timeActive) {
                    // AI-based evasion: monster tries to dodge bullets intelligently
                    val evasion = AIAvoidanceHelper.calculateEvasion(
                        monsterX = m.x,
                        monsterY = m.y.value,
                        monsterSize = m.size,
                        bullets = bullets,
                        screenWidth = screenWidthPx
                    )

                    if (m.isZigzagMovement) {
                        // Zigzag movement combined with AI evasion
                        val combinedX = (m.horizontalSpeed * m.direction) + evasion.first
                        m.x = (m.x + combinedX).coerceIn(0f, screenWidthPx - m.size)

                        if (m.x <= 0 || m.x >= screenWidthPx - m.size) {
                            m.direction *= -1
                        }
                        m.y.value += m.speed
                    } else {
                        // Bounce movement combined with AI evasion
                        m.x = (m.x + m.velocityX + evasion.first).coerceIn(0f, screenWidthPx - m.size)
                        m.y.value += m.velocityY

                        // Bounce off walls
                        if (m.x <= 0 || m.x >= screenWidthPx - m.size) {
                            m.velocityX *= -1
                        }

                        // Bounce off top (optional)
                        if (m.y.value <= 0) {
                            m.velocityY *= -1
                            m.y.value = 0f
                        }
                    }

                    // Wall collision check
                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + m.size

                    if (wallActive && monsterBottom >= wallTop) {
                        // Bounce back from wall
                        if (!m.isZigzagMovement) {
                            m.velocityY *= -1
                        }
                    }

                    // If monster passes plane
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) {
                            val damage = when(m.generation) {
                                1 -> 50 // Large
                                2 -> 30 // Medium
                                else -> 20 // Small
                            }
                            planeHp -= damage
                        }
                        m.alive.value = false
                    }
                }
            }

            // Remove dead monsters (but spawn children first)
            splittingMonsters.removeAll { !it.alive.value && it.hasSpawned.value }

            delay(16)
        }
    }

    // --- Split monsters when killed ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            // Create a snapshot to avoid concurrent modification
            val currentMonsters = splittingMonsters.toList()
            currentMonsters.forEach { m ->
                if (!m.alive.value && m.canSplit && !m.hasSpawned.value) {
                    m.hasSpawned.value = true

                    // Spawn 2-3 smaller monsters
                    val childCount = Random.nextInt(2, 4) // 2 or 3
                    val newSize = m.size * 0.6f // 60% of parent size
                    val newGeneration = m.generation + 1

                    repeat(childCount) {
                        splittingMonsters.add(
                            SplittingMonster(
                                x = m.x + Random.nextFloat() * 20f - 10f,
                                y = mutableStateOf(m.y.value),
                                speed = m.speed * 1.2f,
                                hp = mutableStateOf(60),
                                size = newSize,
                                generation = newGeneration
                            )
                        )
                    }
                }
            }
            delay(50)
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
                // Create snapshot to avoid concurrent modification
                val currentMonsters = splittingMonsters.toList()
                var bulletRemoved = false
                currentMonsters.forEach { m ->
                    if (!bulletRemoved && CollisionUtils.checkCollisionBulletSplittingMonster(b, m)) {
                        m.hp.value -= 25
                        bulletRemoved = true
                        // Play hit sound when bullet hits monster
                        SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                        if (m.hp.value <= 0) {
                            m.alive.value = false
                        }
                    }
                }
                if (bulletRemoved) {
                    iter.remove()
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

    // --- Plane - Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            // Create snapshot to avoid concurrent modification
            val currentMonsters = splittingMonsters.toList()
            currentMonsters.forEach { m ->
                if (CollisionUtils.checkCollisionPlaneSplittingMonster(planeX, planeY, planeWidth, planeHeight, m)) {
                    if (!shieldActive && !wallActive) {
                        val damage = when(m.generation) {
                            1 -> 50
                            2 -> 30
                            else -> 20
                        }
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
                // Create snapshot to avoid concurrent modification
                val currentMonsters = splittingMonsters.toList()
                currentMonsters.forEach { m ->
                    if (CollisionUtils.checkCollisionWallSplittingMonster(planeY, m)) {
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
            monsters = splittingMonsters,
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
            // Background
            Image(
                painter = painterResource(R.drawable.nen5),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, bg1Y.roundToInt()) },
                contentScale = ContentScale.Crop
            )
            Image(
                painter = painterResource(R.drawable.nen5),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, bg2Y.roundToInt()) },
                contentScale = ContentScale.Crop
            )
        }

        // Game content with drag gesture
        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            // Splitting Monsters
            splittingMonsters.forEach { m ->
                SplittingMonsterUI(monster = m, level = 5)
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
                level = 5
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
            level = 5,
            onDismiss = {
                showGameEndDialog = false
            },
            onReplay = {
                showGameEndDialog = false
                isGameOver = false
                isLevelClear = false
                planeHp = 100
                currentSessionScore = 0

                splittingMonsters.clear()
                repeat(8) {
                    splittingMonsters.add(
                        SplittingMonster(
                            x = Random.nextFloat() * (screenWidthPx - 80f),
                            y = mutableStateOf(-Random.nextInt(200, 2000).toFloat()),
                            speed = Random.nextFloat() * 1.5f + 1.5f,
                            hp = mutableStateOf(100),
                            size = 80f,
                            generation = 1
                        )
                    )
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
