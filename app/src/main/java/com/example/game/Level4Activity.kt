package com.example.game

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.game.ui.WallUI
import com.example.game.ui.GrowingMonsterUI
import com.example.game.ui.SoundControlButton
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class Level4Activity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAudio()

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

    // --- Growing Monsters ---
    val growingMonsters = remember {
        List(10) {
            GrowingMonster(
                x = Random.nextFloat() * (screenWidthPx - 200f) + 100f,
                y = mutableStateOf(-Random.nextInt(200, 2500).toFloat()),
                speed = Random.nextFloat() * 1.2f + 1.0f,
                hp = mutableStateOf(80),
                initialSize = 60f,
                maxSize = 600f, // Gấp 10 lần: 60 * 10 = 600
                growthRate = 0.5f // Tăng tốc độ lớn lên để đạt 600px
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

    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 25f }
            bullets.removeAll { it.y < -50f }
            delay(16)
        }
    }

    // --- Monster movement + growing ---
    growingMonsters.forEachIndexed { index, m ->
        LaunchedEffect(m, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Check if monster needs to respawn
                if (!m.alive.value && System.currentTimeMillis() >= monsterRespawnTimes[index]) {
                    m.y.value = -Random.nextInt(200, 1500).toFloat()
                    m.x = Random.nextFloat() * (screenWidthPx - 200f) + 100f
                    m.hp.value = 80
                    m.maxHp = 80
                    m.currentMaxHp.value = 80
                    m.currentSize.value = m.initialSize
                    m.alive.value = true
                }

                if (m.alive.value && m.hp.value > 0 && !timeActive) {
                    // Grow over time
                    m.grow()

                    // Wall collision check
                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + m.currentSize.value

                    if (wallActive && monsterBottom >= wallTop) {
                        // Stop at wall
                    } else {
                        // Normal movement
                        m.y.value += m.speed
                    }

                    // If monster passes plane
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) {
                            // Damage scales with monster size
                            val damage = (30 * (m.currentSize.value / m.initialSize)).toInt()
                            planeHp -= damage
                        }
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
                growingMonsters.forEach { m ->
                    if (m.alive.value && m.hp.value > 0) {
                        // Collision detection with dynamic size
                        val monsterLeft = m.x - (m.currentSize.value - 80f) / 2
                        val monsterRight = monsterLeft + m.currentSize.value
                        val monsterTop = m.y.value
                        val monsterBottom = monsterTop + m.currentSize.value

                        if (b.x >= monsterLeft && b.x <= monsterRight &&
                            b.y >= monsterTop && b.y <= monsterBottom) {
                            m.hp.value -= 75 // Tăng từ 25 lên 75 (x3 lần)
                            // Play hit sound
                            SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                            iter.remove()
                            if (m.hp.value <= 0) {
                                m.alive.value = false
                                val index = growingMonsters.indexOf(m)
                                if (index >= 0) {
                                    monsterRespawnTimes[index] = System.currentTimeMillis() + Random.nextLong(3000, 8000)
                                }
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

    // --- Plane - Monster collision ---
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            growingMonsters.forEach { m ->
                if (m.alive.value && m.hp.value > 0) {
                    val monsterLeft = m.x - (m.currentSize.value - 80f) / 2
                    val monsterRight = monsterLeft + m.currentSize.value
                    val monsterTop = m.y.value
                    val monsterBottom = monsterTop + m.currentSize.value

                    if (planeX + planeWidth > monsterLeft && planeX < monsterRight &&
                        planeY + planeHeight > monsterTop && planeY < monsterBottom) {
                        if (!shieldActive && !wallActive) {
                            val damage = (30 * (m.currentSize.value / m.initialSize)).toInt()
                            planeHp -= damage
                        }
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
                growingMonsters.forEach { m ->
                    if (m.alive.value && m.hp.value > 0) {
                        val wallTop = planeY - 60f
                        val monsterBottom = m.y.value + m.currentSize.value
                        if (monsterBottom >= wallTop && monsterBottom <= wallTop + 10f) {
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
    Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
        // Background
        Image(
            painter = painterResource(R.drawable.vutru1),
            contentDescription = null,
            modifier = Modifier.absoluteOffset { IntOffset(0, bg1Y.roundToInt()) }.fillMaxSize()
        )
        Image(
            painter = painterResource(R.drawable.vutru1),
            contentDescription = null,
            modifier = Modifier.absoluteOffset { IntOffset(0, bg2Y.roundToInt()) }.fillMaxSize()
        )

        // Growing Monsters
        growingMonsters.forEach { m ->
            GrowingMonsterUI(monster = m)
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
            shieldActive = shieldActive
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