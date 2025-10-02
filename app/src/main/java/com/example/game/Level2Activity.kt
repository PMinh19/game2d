package com.example.game

import android.graphics.RectF
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material3.Button
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import com.example.game.TopBarComponent.TopBarUI

class Level2Activity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var soundPool: android.media.SoundPool
    private var shootSoundId: Int = 0
    private var hitSoundId: Int = 0
    private var coinSoundId: Int = 0
    private var soundsLoaded = false
    private var loadedSounds = 0
    private val totalSounds = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loadedSounds++
                if (loadedSounds >= totalSounds) {
                    soundsLoaded = true
                    Log.d("SoundPool", "All sounds loaded successfully!")
                }
            } else {
                Log.e("SoundPool", "Failed to load sound, status: $status")
            }
        }

        shootSoundId = soundPool.load(this, R.raw.shoot, 1)
        hitSoundId = soundPool.load(this, R.raw.hit, 1)
        coinSoundId = soundPool.load(this, R.raw.hit, 1)

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.background_music).apply {
                setOnPreparedListener { start() }
                setOnCompletionListener { seekTo(0); start() }
                setVolume(1f, 1f)
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Failed to initialize MediaPlayer: ${e.message}")
        }

        setContent {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
            Level2Screen(
                onExit = { finish() },
                soundPool = soundPool,
                shootSoundId = shootSoundId,
                hitSoundId = hitSoundId,
                coinSoundId = coinSoundId,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                mediaPlayer = mediaPlayer
            )
        }
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        try { mediaPlayer?.start() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
            soundPool.release()
        } catch (_: Exception) {}
    }
}

// ---------------------- DATA CLASSES ----------------------
data class Bullet2(var x: Float, var y: Float)
data class MonsterState2(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var hp: MutableState<Int>,
    var angle: Float,
    var radius: Float,
    var alive: MutableState<Boolean> = mutableStateOf(true)
)

data class MonsterGroup(
    var centerX: MutableState<Float>,
    var centerY: MutableState<Float>,
    var speedX: Float,
    var speedY: Float,
    var monsters: List<MonsterState2>,
)

data class Coin2(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var collected: MutableState<Boolean> = mutableStateOf(false)
)

data class BagCoinDisplay2(
    val x: Float,
    val y: Float,
    val score: Int
)

// ---------------------- HELPER FUNCTIONS ----------------------
private fun respawnMonster(m: MonsterState2, screenWidthPx: Float) {
    m.y.value = -Random.nextInt(200, 600).toFloat()
    m.x = Random.nextFloat() * (screenWidthPx - 100f)
    m.speed = Random.nextFloat() * 0.5f + 0.5f
    m.hp.value = 100
    m.alive.value = true
}

private fun respawnCoin(c: Coin2, screenWidthPx: Float) {
    c.y.value = -Random.nextInt(100, 600).toFloat()
    c.x = Random.nextFloat() * (screenWidthPx - 50f)
    c.speed = Random.nextFloat() * 2f + 1f
    c.collected.value = false
}

private fun checkCollisionPlaneMonster(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    monster: MonsterState2
): Boolean {
    val monsterWidth = 80f
    val monsterHeight = 80f

    val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
    val monsterRect = RectF(monster.x, monster.y.value, monster.x + monsterWidth, monster.y.value + monsterHeight)

    val intersects = RectF.intersects(planeRect, monsterRect)
    Log.d("COLLISION_DEBUG", "PlaneRect: $planeRect, MonsterRect: $monsterRect, Intersects: $intersects")
    return intersects
}

private fun checkCollisionPlaneCoin(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    coin: Coin2
): Boolean {
    val coinWidth = 40f
    val coinHeight = 40f

    val planeRect = RectF(planeX, planeY, planeX + planeWidth, planeY + planeHeight)
    val coinRect = RectF(coin.x, coin.y.value, coin.x + coinWidth, coin.y.value + coinHeight)

    return RectF.intersects(planeRect, coinRect)
}

private fun checkCollisionBulletMonster(
    bullet: Bullet2,
    monster: MonsterState2
): Boolean {
    val bulletWidth = 30f
    val bulletHeight = 30f
    val monsterWidth = 80f
    val monsterHeight = 80f

    val bulletRect = RectF(bullet.x, bullet.y, bullet.x + bulletWidth, bullet.y + bulletHeight)
    val monsterRect = RectF(monster.x, monster.y.value, monster.x + monsterWidth, monster.y.value + monsterHeight)

    return RectF.intersects(bulletRect, monsterRect)
}

@Composable
fun Level2Screen(
    onExit: () -> Unit,
    soundPool: android.media.SoundPool,
    shootSoundId: Int,
    hitSoundId: Int,
    coinSoundId: Int,
    screenWidthPx: Float,
    screenHeightPx: Float,
    mediaPlayer: MediaPlayer?
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerName = PrefManager.getPlayerName(context)

    var musicEnabled by remember { mutableStateOf(true) }
    var sfxEnabled by remember { mutableStateOf(true) }
    var totalScore by remember { mutableStateOf(0) }
    var currentSessionScore by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var isLevelClear by remember { mutableStateOf(false) }
    val monsterGroups = remember { mutableStateListOf<MonsterGroup>() }
    val monsters = remember { mutableStateListOf<MonsterState2>() }
    var chestItems by remember { mutableStateOf<List<ChestItem>>(emptyList()) }
    var lastFirebaseSync by remember { mutableStateOf(0L) }
    val firebaseSyncInterval = 5000L

    fun spawnMonsterGroup(centerX: Float, centerY: Float) {
        val newMonsters = List(3) { i ->
            MonsterState2(
                x = centerX,
                y = mutableStateOf(centerY),
                speed = 0f,
                hp = mutableStateOf(100),
                angle = (i * 2 * PI / 3).toFloat(),
                radius = 150f
            )
        }

        val group = MonsterGroup(
            centerX = mutableStateOf(centerX),
            centerY = mutableStateOf(centerY),
            speedX = (Random.nextFloat() * 60f - 30f),
            speedY = (Random.nextFloat() * 60f + 30f),
            monsters = newMonsters
        )

        monsterGroups.add(group)
        monsters.addAll(newMonsters)
        Log.d("SPAWN_DEBUG", "Spawned group at centerY: ${group.centerY.value}")
    }

    fun spawnMonsterGroupFromRandomSide() {
        val side = Random.nextInt(4)
        val centerX: Float
        val centerY: Float

        when (side) {
            0 -> {
                centerX = Random.nextFloat() * (screenWidthPx - 400f) + 200f
                centerY = -300f
            }
            1 -> {
                centerX = screenWidthPx + 300f
                centerY = Random.nextFloat() * (screenHeightPx - 600f) + 200f
            }
            2 -> {
                centerX = Random.nextFloat() * (screenWidthPx - 400f) + 200f
                centerY = screenHeightPx - 400f
            }
            else -> {
                centerX = -300f
                centerY = Random.nextFloat() * (screenHeightPx - 600f) + 200f
            }
        }

        spawnMonsterGroup(centerX, centerY)
    }

    fun startLevel(monsterGroups: MutableList<MonsterGroup>, monsters: MutableList<MonsterState2>) {
        monsters.clear()
        monsterGroups.clear()
        isLevelClear = false
        currentSessionScore = 0
    }

    LaunchedEffect(Unit) {
        startLevel(monsterGroups, monsters)
        repeat(10) { i ->
            spawnMonsterGroupFromRandomSide()
            Log.d("SPAWN_DEBUG", "Spawned monster group $i, total monsters: ${monsters.size}")
            delay(6000)
        }
    }

    LaunchedEffect(playerName) {
        if (!playerName.isNullOrBlank()) {
            FirebaseHelper.getScore(playerName) { score ->
                totalScore = score
            }
            FirebaseHelper.getChestItems(playerName) { items ->
                chestItems = items
                if (items.isEmpty()) {
                    Log.d("CHEST_DEBUG", "🔧 DEBUG: Adding test items to empty chest")
                    val testItems = listOf(
                        ChestItem("Pháo sáng", R.drawable.fireworks),
                        ChestItem("Bom", R.drawable.bom1),
                        ChestItem("Khiên", R.drawable.shield1)
                    )
                    chestItems = testItems
                }
            }
            if (totalScore == 0) {
                Log.d("CHEST_DEBUG", "🔧 DEBUG: Giving player 50 test coins")
                totalScore = 50
            }
        }
    }

    fun syncScoreToFirebase(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (force || currentTime - lastFirebaseSync > firebaseSyncInterval) {
            if (!playerName.isNullOrBlank()) {
                FirebaseHelper.updateScore(playerName, totalScore)
                lastFirebaseSync = currentTime
            }
        }
    }

    var shieldActive by remember { mutableStateOf(false) }
    var wallActive by remember { mutableStateOf(false) }
    var timeActive by remember { mutableStateOf(false) }
    var shieldTimeLeft by remember { mutableStateOf(0f) }
    var wallTimeLeft by remember { mutableStateOf(0f) }
    var timeTimeLeft by remember { mutableStateOf(0f) }

    LaunchedEffect(musicEnabled) {
        try {
            if (musicEnabled) mediaPlayer?.start() else mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    var planeX by remember { mutableStateOf(screenWidthPx / 2f - 50f) }
    val planeY = screenHeightPx - 250f
    val planeHp = remember { mutableStateOf(1000) }

    LaunchedEffect(planeHp.value) {
        Log.d("PLANE_HP", "Plane HP changed: ${planeHp.value}")
    }

    val bullets = remember { mutableStateListOf<Bullet2>() }

    val coins = remember {
        List(5) {
            val speed = Random.nextFloat() * 2f + 1f
            Coin2(
                x = Random.nextFloat() * (screenWidthPx - 50f),
                y = mutableStateOf(-Random.nextInt(100, 600).toFloat()),
                speed = speed
            )
        }
    }

    val bagCoins = remember { mutableStateListOf<BagCoinDisplay2>() }

    fun applyChestItemEffect(item: ChestItem) {
        Log.d("Level2Screen", "🎁 CHEST ITEM SELECTED: ${item.name} 🎁")
        try {
            ChestItemEffects.applyLevel2Effect(
                item = item,
                monsters = monsters,
                coins = coins,
                bagCoins = bagCoins,
                coroutineScope = coroutineScope,
                screenWidthPx = screenHeightPx,
                planeX = planeX,
                onScoreUpdate = { scoreToAdd ->
                    Log.d("Level2Screen", "Score update: +$scoreToAdd")
                    totalScore += scoreToAdd
                    currentSessionScore += scoreToAdd
                    syncScoreToFirebase()
                },
                onShieldActivate = {
                    Log.d("Level2Screen", "Shield activated!")
                    shieldActive = true
                },
                onWallActivate = {
                    Log.d("Level2Screen", "Wall activated!")
                    wallActive = true
                },
                onTimeActivate = {
                    Log.d("Level2Screen", "Time freeze activated!")
                    timeActive = true
                    timeTimeLeft = 10f
                },
                onLevelClear = {
                    Log.d("Level2Screen", "🏆 LEVEL CLEAR TRIGGERED! Setting isLevelClear = true 🏆")
                    isLevelClear = true
                }
            )
            val updatedItems = chestItems.toMutableList().also { it.remove(item) }
            chestItems = updatedItems
            if (!playerName.isNullOrBlank()) {
                FirebaseHelper.updateChest(playerName, updatedItems)
            }
        } catch (e: Exception) {
            Log.e("LEVEL2_DEBUG", "Exception in Level2 ChestItemEffects: ${e.message}")
        }
    }

    LaunchedEffect(shieldActive) {
        if (shieldActive) {
            shieldTimeLeft = 10f
            while (shieldTimeLeft > 0) {
                delay(100)
                shieldTimeLeft -= 0.1f
            }
            shieldActive = false
        }
    }

    LaunchedEffect(wallActive) {
        if (wallActive) {
            wallTimeLeft = 10f
            while (wallTimeLeft > 0) {
                delay(100)
                wallTimeLeft -= 0.1f
            }
            wallActive = false
        }
    }

    LaunchedEffect(timeActive) {
        if (timeActive) {
            while (timeTimeLeft > 0) {
                delay(100)
                timeTimeLeft -= 0.1f
            }
            timeActive = false
        }
    }

    LaunchedEffect(Unit) {
        while (!isGameOver) {
            bullets.add(Bullet2(planeX - 20f, planeY))
            bullets.add(Bullet2(planeX + 20f, planeY))

            delay(350)
        }
    }

    LaunchedEffect(Unit) {
        while (!isGameOver) {
            bullets.forEach { it.y -= 20f }
            bullets.removeAll { it.y < -40f }
            delay(16)
        }
    }

    monsterGroups.forEach { group ->
        LaunchedEffect(group, timeActive) {
            while (!isGameOver) {
                if (!timeActive) {
                    group.centerX.value += group.speedX
                    group.centerY.value += group.speedY

                    val margin = 200f
                    if (group.centerX.value - margin < 0) {
                        group.centerX.value = margin
                        group.speedX = kotlin.math.abs(group.speedX)
                    } else if (group.centerX.value + margin > screenWidthPx) {
                        group.centerX.value = screenWidthPx - margin
                        group.speedX = -kotlin.math.abs(group.speedX)
                    }

                    if (group.centerY.value - margin < 0) {
                        group.centerY.value = margin
                        group.speedY = kotlin.math.abs(group.speedY)
                    } else if (group.centerY.value + margin > screenHeightPx - 100f) {
                        group.centerY.value = screenHeightPx - 100f - margin
                        group.speedY = -kotlin.math.abs(group.speedY)
                    }

                    group.monsters.forEachIndexed { i, m ->
                        m.angle += 0.08f
                        m.x = group.centerX.value + m.radius * cos(m.angle + i * 2 * PI / 3).toFloat()
                        m.y.value = group.centerY.value + m.radius * sin(m.angle + i * 2 * PI / 3).toFloat()
                    }

                    Log.d("MOVEMENT_DEBUG", "Group centerY: ${group.centerY.value}")
                }
                delay(16)
            }
        }
    }

    coins.forEach { c ->
        LaunchedEffect(c, timeActive) {
            while (!isGameOver) {
                if (!c.collected.value && !timeActive) {
                    c.y.value += c.speed
                    if (c.y.value > screenHeightPx + 50f) respawnCoin(c, screenWidthPx)
                }
                delay(16)
            }
        }
    }

    var lastHitTime by remember { mutableStateOf(0L) }
    fun playHitSound() {
        val now = System.currentTimeMillis()
        if (now - lastHitTime > 50 && sfxEnabled) {
            try {
                val result = soundPool.play(hitSoundId, 0.8f, 0.8f, 2, 0, 1f)
                if (result == 0) {
                    Log.w("SoundPool", "Hit sound not ready yet")
                }
            } catch (e: Exception) {
                Log.e("SoundPool", "Error playing hit sound: ${e.message}")
            }
            lastHitTime = now
        }
    }

    fun playShootSound() {
        if (sfxEnabled) {
            try {
                val result = soundPool.play(shootSoundId, 0.5f, 0.5f, 1, 0, 1f)
                if (result == 0) {
                    Log.w("SoundPool", "Shoot sound not ready yet")
                }
            } catch (e: Exception) {
                Log.e("SoundPool", "Error playing shoot sound: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        while (!isGameOver) {
            Log.d("COLLISION_LOOP", "Checking collisions, isGameOver: $isGameOver, monsterCount: ${monsters.size}")
            val bulletsToRemove = mutableListOf<Bullet2>()

            monsters.forEach { m ->
                if (m.alive.value) {
                    Log.d("COLLISION_LOOP", "Checking monster at (${m.x}, ${m.y.value}), alive: ${m.alive.value}")
                    val collision = checkCollisionPlaneMonster(planeX, planeY, 100f, 100f, m)
                    Log.d("COLLISION_DEBUG", "Plane: ($planeX, $planeY), Monster: (${m.x}, ${m.y.value}), Collision: $collision, Shield: $shieldActive")
                    if (collision) {
                        Log.e("COLLISION_DEBUG", "🚨 COLLISION DETECTED! Plane: ($planeX, $planeY) Monster: (${m.x}, ${m.y.value})")
                        Log.e("COLLISION_DEBUG", "Shield Active: $shieldActive")

                        if (!shieldActive) {
                            val oldHp = planeHp.value
                            planeHp.value -= 50
                            Log.e("COLLISION_DEBUG", "🔥 PLANE DAMAGED! HP: $oldHp → ${planeHp.value}")

                            if (planeHp.value <= 0) {
                                planeHp.value = 0
                                isGameOver = true
                                Log.e("COLLISION_DEBUG", "💀 PLANE DEAD - GAME OVER!")
                            }
                        } else {
                            Log.e("COLLISION_DEBUG", "🛡️ SHIELD BLOCKED DAMAGE!")
                        }

                        m.alive.value = false
                        Log.e("COLLISION_DEBUG", "👻 MONSTER KILLED BY COLLISION")
                    }
                }
            }

            coins.forEach { c ->
                if (!c.collected.value && checkCollisionPlaneCoin(planeX, planeY, 100f, 100f, c)) {
                    c.collected.value = true
                    totalScore += 1
                    currentSessionScore += 1
                    syncScoreToFirebase()

                    val bag = BagCoinDisplay2(c.x, c.y.value, 1)
                    bagCoins.add(bag)

                    coroutineScope.launch {
                        delay(1000)
                        bagCoins.remove(bag)
                        respawnCoin(c, screenWidthPx)
                    }
                }
            }

            bullets.toList().forEach { b ->
                monsters.forEach { m ->
                    if (m.alive.value && checkCollisionBulletMonster(b, m)) {
                        val oldHp = m.hp.value
                        m.hp.value -= 20
                        Log.d("COLLISION", "💥 MONSTER HIT! HP: $oldHp → ${m.hp.value}")
                        bulletsToRemove.add(b)
                        playHitSound()
                        if (m.hp.value <= 0) {
                            m.hp.value = 0
                            m.alive.value = false
                            Log.d("COLLISION", "☠️ MONSTER KILLED BY BULLET - STAYS DEAD")
                        }
                    }
                }
            }
            bullets.removeAll(bulletsToRemove)

            delay(16)
        }
    }

    LaunchedEffect(Unit) {
        while (!isGameOver && !isLevelClear) {
            delay(5000)
            val allMonstersActuallyDead = monsters.isNotEmpty() && monsters.all {
                !it.alive.value && it.hp.value <= 0
            }

            if (allMonstersActuallyDead) {
                delay(3000)
                if (monsters.all { !it.alive.value && it.hp.value <= 0 }) {
                    isLevelClear = true
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isGameOver) planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - 100f)
                    change.consume()
                }
            }
    ) {
        MovingBackgroundOffset2(screenWidthPx)

        IconButton(
            onClick = {
                syncScoreToFirebase(force = true)
                coroutineScope.launch {
                    delay(500)
                    onExit()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Exit")
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp)
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.speaker),
                    contentDescription = "Sound Options",
                    tint = if (musicEnabled || sfxEnabled) Color.White else Color.Gray,
                    modifier = Modifier.size(30.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Nhạc nền") },
                    onClick = {
                        musicEnabled = !musicEnabled
                        if (musicEnabled) mediaPlayer?.start() else mediaPlayer?.pause()
                        expanded = false
                    },
                    trailingIcon = { Checkbox(checked = musicEnabled, onCheckedChange = null) }
                )
                DropdownMenuItem(
                    text = { Text("Hiệu ứng (Hit + Shoot)") },
                    onClick = { sfxEnabled = !sfxEnabled; expanded = false },
                    trailingIcon = { Checkbox(checked = sfxEnabled, onCheckedChange = null) }
                )
            }
        }

        monsters.forEach { m ->
            if (!isGameOver && m.alive.value) {
                Image(
                    painter = painterResource(R.drawable.quaivat1),
                    contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(m.x.roundToInt(), m.y.value.roundToInt()) }
                        .size(80.dp),
                    contentScale = ContentScale.Fit
                )
                Canvas(
                    modifier = Modifier
                        .absoluteOffset { IntOffset(m.x.roundToInt(), (m.y.value - 10f).roundToInt()) }
                        .size(width = 80.dp, height = 5.dp)
                ) {
                    val hpRatio = m.hp.value / 100f
                    drawRect(
                        color = Color.Red,
                        size = Size(size.width * hpRatio, size.height)
                    )
                }
            }
        }

        coins.forEach { c ->
            if (!c.collected.value && !isGameOver) {
                Image(
                    painter = painterResource(R.drawable.coin),
                    contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(c.x.roundToInt(), c.y.value.roundToInt()) }
                        .size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        bagCoins.forEach { b ->
            Image(
                painter = painterResource(R.drawable.bagcoin),
                contentDescription = null,
                modifier = Modifier
                    .absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }
                    .size(40.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "${b.score}",
                color = Color.Yellow,
                fontSize = 16.sp,
                modifier = Modifier.absoluteOffset { IntOffset((b.x + 10).roundToInt(), (b.y + 10).roundToInt()) }
            )
        }

        bullets.forEach { b ->
            if (!isGameOver) {
                Image(
                    painter = painterResource(R.drawable.dan2),
                    contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }
                        .size(30.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (!isGameOver) {
            Box(
                modifier = Modifier
                    .absoluteOffset { IntOffset(planeX.roundToInt(), planeY.roundToInt()) }
                    .size(100.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.maybay1),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (shieldActive) 0.7f else 1f),
                    contentScale = ContentScale.Fit
                )

                Canvas(
                    modifier = Modifier
                        .offset(y = (-15).dp)
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(color = Color.Gray, size = size)
                    val ratio = planeHp.value / 1000f
                    drawRoundRect(color = Color.Green, size = Size(size.width * ratio, size.height))
                }
            }

            if (shieldActive) {
                Canvas(
                    modifier = Modifier
                        .absoluteOffset { IntOffset((planeX - 30f).roundToInt(), (planeY - 30f).roundToInt()) }
                        .size(160.dp)
                ) {
                    drawCircle(
                        color = Color.Cyan.copy(alpha = 0.3f),
                        radius = 80.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(80.dp.toPx(), 80.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Cyan,
                        radius = 80.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(80.dp.toPx(), 80.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }

                Text(
                    text = "🛡️ ${shieldTimeLeft.toInt()}s",
                    color = Color.Cyan,
                    fontSize = 16.sp,
                    modifier = Modifier.absoluteOffset {
                        IntOffset((planeX - 20f).roundToInt(), (planeY - 50f).roundToInt())
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            TopBarUI(
                bagCoinScore = totalScore,
                chestItems = chestItems,
                onBuyItem = { item: ChestItem, price: Int ->
                    if (totalScore >= price) {
                        totalScore -= price
                        chestItems = chestItems.toList() + item
                        syncScoreToFirebase(force = true)
                        if (!playerName.isNullOrBlank()) {
                            FirebaseHelper.updateChest(playerName, chestItems)
                        }
                    }
                },
                onUseChestItem = { item: ChestItem ->
                    applyChestItemEffect(item)
                }
            )
        }

        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GAME OVER",
                        color = Color.Red,
                        fontSize = 36.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        syncScoreToFirebase(force = true)
                        onExit()
                    }) {
                        Text(text = "Thoát", fontSize = 20.sp)
                    }
                }
            }
        }
        if (isLevelClear) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🎉 Chúc mừng bạn đã chiến thắng! 🎉",
                        color = Color.Green,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Bạn thu thêm được $currentSessionScore xu",
                        color = Color.Yellow,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        syncScoreToFirebase(force = true)
                        onExit()
                    }) {
                        Text(text = "Thoát", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MovingBackgroundOffset2(screenWidthPx: Float) {
    val bg = painterResource(R.drawable.nen2)
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            offsetX.snapTo(0f)
            offsetX.animateTo(
                targetValue = -screenWidthPx,
                animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = bg,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            contentScale = ContentScale.Crop
        )
        Image(
            painter = bg,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((offsetX.value + screenWidthPx).roundToInt(), 0) },
            contentScale = ContentScale.Crop
        )
    }
}