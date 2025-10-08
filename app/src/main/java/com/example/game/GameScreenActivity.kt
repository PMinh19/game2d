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

        try {
            AIAvoidanceHelper.init(this)
        } catch (e: Exception) {
            android.util.Log.e("GameScreenActivity", "AI init failed: ${e.message}", e)
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

    var totalScore by remember { mutableIntStateOf(0) }
    var currentSessionScore by remember { mutableIntStateOf(0) }
    var planeHp by remember { mutableIntStateOf(100) }

    var shieldActive by remember { mutableStateOf(false) }
    var wallActive by remember { mutableStateOf(false) }
    var timeActive by remember { mutableStateOf(false) }

    var isGameOver by remember { mutableStateOf(false) }
    var isLevelClear by remember { mutableStateOf(false) }
    var showGameEndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isGameOver, isLevelClear) {
        if (isGameOver || isLevelClear) {
            delay(500)
            showGameEndDialog = true
        }
    }

    var planeX by remember { mutableFloatStateOf(screenWidthPx / 2 - 50f) }
    val planeY = screenHeightPx - 250f
    val planeWidth = 100f
    val planeHeight = 100f

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

    val monsters = remember {
        List(8) {
            BaseMonster(
                x = Random.nextFloat() * (screenWidthPx - 120f),
                y = mutableStateOf(-Random.nextInt(200, 2000).toFloat()),
                speed = Random.nextFloat() * 1.5f + 1.5f,
                hp = mutableStateOf(100)
            )
        }
    }

    val coins = remember { mutableStateListOf<BaseCoin>() }
    val bullets = remember { mutableStateListOf<Bullet>() }
    val bagCoins = remember { mutableStateListOf<BagCoinDisplay>() }
    var chestItems by remember { mutableStateOf<List<ChestItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (!playerName.isNullOrBlank()) {
            FirebaseHelper.syncNewPlayer(playerName)
            FirebaseHelper.getScore(playerName) { totalScore = it }
            FirebaseHelper.getChestItems(playerName) { chestItems = it }
        }
    }

    // Shooting
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.add(Bullet(planeX + planeWidth / 2f - 15f, planeY))
            SoundManager.playSoundEffect(soundPool, shootSoundId, 0.5f)
            delay(200)
        }
    }

    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            bullets.forEach { it.y -= 20f }
            bullets.removeAll { it.y < -40f }
            delay(16)
        }
    }

    // ✅ MONSTER MOVEMENT - NO RESPAWN VERSION
    monsters.forEachIndexed { index, m ->
        LaunchedEffect(m, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                // Only process alive monsters
                if (m.alive.value && m.hp.value > 0 && !timeActive) {
                    // AI-based evasion
                    val evasion = AIAvoidanceHelper.calculateEvasion(
                        monsterX = m.x,
                        monsterY = m.y.value,
                        monsterSize = 100f,
                        bullets = bullets,
                        screenWidth = screenWidthPx
                    )

                    m.x = (m.x + evasion.first).coerceIn(0f, screenWidthPx - 100f)

                    val wallTop = planeY - 60f
                    val monsterBottom = m.y.value + 80f

                    if (wallActive && monsterBottom >= wallTop) {
                        // Stopped by wall
                    } else {
                        m.y.value += m.speed
                    }

                    // Monster fell below plane - just die, no respawn
                    if (m.y.value > planeY + planeHeight / 2f) {
                        if (!shieldActive && !wallActive) planeHp -= 50
                        m.alive.value = false
                    }
                }
                delay(16)
            }
        }
    }

    // Coin spawning
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            if (coins.size < 10) {
                coins.add(
                    BaseCoin(
                        x = Random.nextFloat() * (screenWidthPx - 50f),
                        y = mutableStateOf(-100f),
                        speed = Random.nextFloat() * 2f + 2f
                    )
                )
            }
            delay(Random.nextLong(1000L, 3000L))
        }
    }

    // Coin movement
    coins.forEach { c ->
        LaunchedEffect(c, isGameOver, isLevelClear) {
            while (!isGameOver && !isLevelClear) {
                if (!c.collected.value) {
                    c.y.value += c.speed
                }
                if (c.collected.value || c.y.value > screenHeightPx + 80f) {
                    coins.remove(c)
                    break
                }
                delay(32L)
            }
        }
    }

    // ✅ BULLET-MONSTER COLLISION - NO RESPAWN VERSION
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            val iter = bullets.iterator()
            while (iter.hasNext()) {
                val b = iter.next()
                var bulletHit = false
                monsters.forEach { m ->
                    if (!bulletHit && m.alive.value && CollisionUtils.checkCollisionBulletMonster(b, m)) {
                        m.hp.value -= 50
                        SoundManager.playSoundEffect(soundPool, hitSoundId, 0.3f)
                        bulletHit = true

                        if (m.hp.value <= 0) {
                            m.alive.value = false
                            // NO RESPAWN SCHEDULING!
                        }
                    }
                }
                if (bulletHit) {
                    iter.remove()
                }
            }
            delay(16)
        }
    }

    // Plane-Coin collision
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

    // Plane-Monster collision
    LaunchedEffect(isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            monsters.forEach { m ->
                if (m.alive.value && m.hp.value > 0 &&
                    CollisionUtils.checkCollisionPlaneMonster(planeX, planeY, planeWidth, planeHeight, m)
                ) {
                    if (!shieldActive && !wallActive) planeHp -= 50
                    m.hp.value = 0
                    m.alive.value = false
                }
            }
            if (planeHp <= 0) isGameOver = true
            delay(50)
        }
    }

    // ✅ LEVEL CLEAR CHECK - KẾT THÚC KHI HẾT MONSTER
    LaunchedEffect(Unit) {
        while (!isGameOver && !isLevelClear) {
            val aliveCount = monsters.count { it.alive.value }
            if (aliveCount == 0) {
                delay(1000) // Đợi 1 giây để animation kết thúc
                isLevelClear = true
            }
            delay(500) // Kiểm tra mỗi 0.5 giây
        }
    }

    // Wall-Monster collision
    LaunchedEffect(wallActive, isGameOver, isLevelClear) {
        while (!isGameOver && !isLevelClear) {
            if (wallActive) {
                monsters.forEach { m ->
                    if (m.alive.value && m.hp.value > 0) {
                        val isColliding = CollisionUtils.checkCollisionWallMonster(planeY, m)
                        if (isColliding) {
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
            onLevelClear = { isLevelClear = true },
            onShowBoomEffect = { x, y -> }
        )
        chestItems = chestItems - item
        if (!playerName.isNullOrBlank()) FirebaseHelper.updateChest(playerName, chestItems)
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - planeWidth)
            change.consume()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.lv1),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().offset { IntOffset(0, offsetY.roundToInt()) },
                contentScale = ContentScale.Crop
            )
            Image(
                painter = painterResource(R.drawable.lv1),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().offset { IntOffset(0, (offsetY - screenHeightPx + 2).roundToInt()) },
                contentScale = ContentScale.Crop
            )
        }

        Box(modifier = Modifier.fillMaxSize().then(dragModifier)) {
            monsters.forEach { m ->
                MonsterUI(monster = m)
            }

            coins.filter { !it.collected.value }.forEach { c ->
                Image(
                    painter = painterResource(R.drawable.coin),
                    contentDescription = null,
                    modifier = Modifier.absoluteOffset { IntOffset(c.x.roundToInt(), c.y.value.roundToInt()) }.size(40.dp)
                )
            }

            bagCoins.toList().forEach { bag ->
                BagCoinAnimatedView(bag = bag, onFinished = { finishedBag ->
                    bagCoins.remove(finishedBag)
                })
            }

            bullets.forEach { b ->
                Image(
                    painter = painterResource(R.drawable.dan2),
                    contentDescription = null,
                    modifier = Modifier.absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }.size(30.dp)
                )
            }

            PlaneUI(planeX = planeX, planeY = planeY, planeHp = planeHp, shieldActive = shieldActive)

            if (wallActive) {
                WallUI(planeY = planeY)
            }

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

            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                SoundControlButton()
            }
        }
    }

    if (showGameEndDialog) {
        GameEndDialog(
            isWin = isLevelClear,
            score = currentSessionScore,
            level = 1,
            onDismiss = { showGameEndDialog = false },
            onReplay = {
                showGameEndDialog = false
                isGameOver = false
                isLevelClear = false
                planeHp = 100
                currentSessionScore = 0

                monsters.forEachIndexed { index, m ->
                    m.x = Random.nextFloat() * (screenWidthPx - 100f)
                    m.y.value = -Random.nextInt(200, 2000).toFloat()
                    m.hp.value = 100
                    m.alive.value = true
                }

                coins.forEach { c ->
                    c.collected.value = false
                    c.y.value = -Random.nextInt(100, 600).toFloat()
                    c.x = Random.nextFloat() * (screenWidthPx - 50f)
                }

                bullets.clear()
            },
            onNextLevel = { onExit() },
            onExit = { onExit() }
        )
    }
}