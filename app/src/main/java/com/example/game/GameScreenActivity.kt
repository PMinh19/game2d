package com.example.game
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.random.Random
import android.graphics.RectF
import com.example.game.FirebaseHelper
class GameScreenActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var soundPool: android.media.SoundPool
    private var shootSoundId: Int = 0
    private var hitSoundId: Int = 0
    private var coinSoundId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = android.media.SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()
        shootSoundId = soundPool.load(this, R.raw.shoot, 1)
        hitSoundId = soundPool.load(this, R.raw.hit, 1)

        // Initialize MediaPlayer for background music
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
            GameScreen(
                onExit = { finish() },
                soundPool = soundPool,
                shootSoundId = shootSoundId,
                hitSoundId = hitSoundId,
                coinSoundId = coinSoundId,
                screenWidthPx = screenWidthPx,
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
data class Bullet(var x: Float, var y: Float)

data class MonsterState(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var hp: MutableState<Int>,
    var respawnCount: Int = 0,
    val maxRespawn: Int = 10,
    var isDying: MutableState<Boolean> = mutableStateOf(false),
    var isRespawning: MutableState<Boolean> = mutableStateOf(false)
)

data class Coin(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var collected: MutableState<Boolean> = mutableStateOf(false)
)

data class BagCoinDisplay(
    val x: Float,
    val y: Float,
    val score: Int
)

private fun getBoundingBox(x: Float, y: Float, width: Float, height: Float): RectF {
    return RectF(x, y, x + width, y + height)
}

// ---------------------- HELPER FUNCTIONS ----------------------
private suspend fun respawnMonster(m: MonsterState, screenWidthPx: Float, planeX: Float) {
    if (m.respawnCount < m.maxRespawn && !m.isRespawning.value) {
        m.isRespawning.value = true
        m.isDying.value = true

        // Delay để tránh respawn ngay lập tức
        delay(300)

        // Reset vị trí y
        m.y.value = -Random.nextInt(200, 800).toFloat()

        // Đảm bảo quái không tái sinh gần máy bay (tối thiểu 200px)
        var attempts = 0
        do {
            m.x = Random.nextFloat() * (screenWidthPx - 100f)
            attempts++
        } while (abs(m.x - planeX) < 200f && attempts < 10)

        // Nếu sau 10 lần thử vẫn không tìm được vị trí an toàn, đặt ở góc
        if (attempts >= 10) {
            m.x = if (planeX > screenWidthPx / 2) 50f else screenWidthPx - 150f
        }

        m.speed = Random.nextFloat() * 1.5f + 1.5f
        m.hp.value = 100
        m.respawnCount++

        // Delay thêm để đảm bảo quái đã ở vị trí mới
        delay(100)

        m.isDying.value = false
        m.isRespawning.value = false
    } else {
        m.hp.value = 0
        m.y.value = screenWidthPx * 2  // đưa ra khỏi màn hình
        m.isDying.value = true
    }
}

private fun respawnCoin(c: Coin, screenWidthPx: Float) {
    c.y.value = -Random.nextInt(100, 600).toFloat()
    c.x = Random.nextFloat() * (screenWidthPx - 50f)
    c.speed = Random.nextFloat() * 0.5f + 0.5f
    c.collected.value = false
}

private fun checkCollisionPlaneMonster(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    monster: MonsterState
): Boolean {
    if (monster.isDying.value || monster.isRespawning.value || monster.hp.value <= 0) return false

    val planeBox = getBoundingBox(planeX, planeY, planeWidth, planeHeight)
    val monsterBox = getBoundingBox(monster.x, monster.y.value, 80f, 80f)
    return RectF.intersects(planeBox, monsterBox)
}

private fun checkCollisionPlaneCoin(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    coin: Coin
): Boolean {
    val planeBox = getBoundingBox(planeX, planeY, planeWidth, planeHeight)
    val coinBox = getBoundingBox(coin.x, coin.y.value, 40f, 40f)
    return RectF.intersects(planeBox, coinBox)
}

private fun checkCollisionBulletMonster(
    bullet: Bullet,
    monster: MonsterState,
    bulletWidth: Float = 30f,
    bulletHeight: Float = 30f,
    monsterWidth: Float = 80f,
    monsterHeight: Float = 80f
): Boolean {
    if (monster.isDying.value || monster.isRespawning.value || monster.hp.value <= 0) return false

    val bulletBox = getBoundingBox(bullet.x, bullet.y, bulletWidth, bulletHeight)
    val monsterBox = getBoundingBox(monster.x, monster.y.value, monsterWidth, monsterHeight)
    return RectF.intersects(bulletBox, monsterBox)
}
@Composable
fun GameScreen(
    onExit: () -> Unit,
    soundPool: android.media.SoundPool,
    shootSoundId: Int,
    hitSoundId: Int,
    coinSoundId: Int,
    screenWidthPx: Float,
    mediaPlayer: MediaPlayer?
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val playerName = PrefManager.getPlayerName(LocalContext.current)

    var musicEnabled by remember { mutableStateOf(true) }
    var sfxEnabled by remember { mutableStateOf(true) }
    var totalScore by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    // Chest state
    var chestItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var showChest by remember { mutableStateOf(false) }

    // Load score từ Firebase
    LaunchedEffect(playerName) {
        if (!playerName.isNullOrBlank()) {
            FirebaseHelper.syncNewPlayer(playerName)

            FirebaseHelper.getScore(playerName) { score ->
                totalScore = score
            }

            FirebaseHelper.getChestItems(playerName) { items ->
                chestItems = items
            }
            FirebaseHelper.updateChest(playerName, chestItems)
            FirebaseHelper.updateScore(playerName, totalScore)
        }
    }

    // MediaPlayer toggle
    LaunchedEffect(musicEnabled) {
        try {
            if (musicEnabled) mediaPlayer?.start() else mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    // Plane
    var planeX by remember { mutableStateOf(screenWidthPx / 2f - 50f) }
    val planeY = screenHeightPx - 250f
    val planeHp = remember { mutableStateOf(1000) }

    // Bullets
    val bullets = remember { mutableStateListOf<Bullet>() }
    LaunchedEffect(Unit) {
        while (!isGameOver) {
            bullets.add(Bullet(planeX - 20f, planeY))
            bullets.add(Bullet(planeX + 20f, planeY))
            if (sfxEnabled) soundPool.play(shootSoundId, 0.5f, 0.5f, 1, 0, 1f)
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

    // Monsters
    val monsters = remember {
        List(3) {
            val speed = Random.nextFloat() * 1.5f + 1.5f
            MonsterState(
                x = Random.nextFloat() * (screenWidthPx - 100f),
                y = mutableStateOf(-Random.nextInt(0, 600).toFloat()),
                speed = speed,
                hp = mutableStateOf(100)
            )
        }
    }

    monsters.forEach { m ->
        LaunchedEffect(m) {
            while (!isGameOver) {
                if (!m.isDying.value && !m.isRespawning.value && m.hp.value > 0) {
                    m.y.value += m.speed
                    if (m.y.value > screenHeightPx + 100f) {
                        respawnMonster(m, screenWidthPx, planeX)
                        planeHp.value -= 50
                        if (planeHp.value <= 0) {
                            planeHp.value = 0
                            isGameOver = true
                        }
                    }
                }
                delay(16)
            }
        }
    }

    // Coins
    val coins = remember {
        List(5) {
            val speed = Random.nextFloat() * 2f + 1f
            Coin(
                x = Random.nextFloat() * (screenWidthPx - 50f),
                y = mutableStateOf(-Random.nextInt(100, 600).toFloat()),
                speed = speed
            )
        }
    }
    coins.forEach { c ->
        LaunchedEffect(c) {
            while (!isGameOver) {
                if (!c.collected.value) {
                    c.y.value += c.speed
                    if (c.y.value > screenHeightPx + 50f) respawnCoin(c, screenWidthPx)
                }
                delay(32)
            }
        }
    }

    // Bagcoin displays
    val bagCoins = remember { mutableStateListOf<BagCoinDisplay>() }

    // Throttle hit sound
    var lastHitTime by remember { mutableStateOf(0L) }
    fun playHitSound() {
        val now = System.currentTimeMillis()
        if (now - lastHitTime > 50) {
            if (sfxEnabled) soundPool.play(hitSoundId, 0.8f, 0.8f, 2, 0, 1f)
            lastHitTime = now
        }
    }

    var isLevelClear by remember { mutableStateOf(false) }

    // Collision bullets ↔ monsters
    LaunchedEffect(Unit) {
        while (!isGameOver) {
            val bulletIterator = bullets.iterator()
            while (bulletIterator.hasNext()) {
                val b = bulletIterator.next()
                monsters.forEach { m ->
                    if (checkCollisionBulletMonster(b, m)) {
                        m.hp.value -= 20
                        bulletIterator.remove()
                        playHitSound()

                        if (m.hp.value <= 0) {
                            coroutineScope.launch {
                                respawnMonster(m, screenWidthPx, planeX)
                            }
                        }
                    }
                }
            }

            // Check level clear
            if (monsters.all { it.hp.value <= 0 && it.respawnCount >= it.maxRespawn }) {
                isLevelClear = true
            }

            delay(16)
        }
    }

    // Collision plane ↔ monsters
    LaunchedEffect(Unit) {
        while (!isGameOver) {
            monsters.forEach { m ->
                if (checkCollisionPlaneMonster(planeX, planeY, 100f, 100f, m)) {
                    planeHp.value -= 50
                    if (planeHp.value <= 0) {
                        planeHp.value = 0
                        isGameOver = true
                    }
                    coroutineScope.launch {
                        respawnMonster(m, screenWidthPx, planeX)
                    }
                }
            }
            delay(200)
        }
    }

    // Collision plane ↔ coins
    LaunchedEffect(Unit) {
        while (!isGameOver) {
            coins.forEach { c ->
                if (!c.collected.value && checkCollisionPlaneCoin(planeX, planeY, 100f, 100f, c)) {
                    c.collected.value = true
                    totalScore += 1

                    // Upload score
                    if (!playerName.isNullOrBlank()) {
                        db.collection("rankings")
                            .whereEqualTo("name", playerName)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    val docId = docs.documents[0].id
                                    db.collection("rankings").document(docId)
                                        .update("score", totalScore)
                                } else {
                                    db.collection("rankings").add(
                                        hashMapOf("name" to playerName, "score" to totalScore)
                                    )
                                }
                            }
                    }

                    val bag = BagCoinDisplay(c.x, c.y.value, totalScore)
                    bagCoins.add(bag)
                    coroutineScope.launch { delay(1000); bagCoins.remove(bag) }

                    respawnCoin(c, screenWidthPx)
                }
            }
            delay(50)
        }
    }

    // ------------------ UI ------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isGameOver) planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - 100f)
                    change.consume()
                }
            }
    ) {
        // Moving background (layer 1)
        val bg = painterResource(R.drawable.nenms)
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

        // Draw monsters
        monsters.forEach { m ->
            if (!isGameOver && !m.isDying.value && m.hp.value > 0) {
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
                    drawRect(color = Color.Red, size = size)
                    val hpRatio = m.hp.value / 100f
                    drawRect(color = Color.Green, size = Size(size.width * hpRatio, size.height))
                }
            }
        }

        // Draw coins
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

        // Draw bagCoins
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

        // Draw bullets
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

        // Draw plane
        if (!isGameOver) {
            Image(
                painter = painterResource(R.drawable.maybay1),
                contentDescription = null,
                modifier = Modifier
                    .absoluteOffset { IntOffset(planeX.roundToInt(), planeY.roundToInt()) }
                    .size(100.dp),
                contentScale = ContentScale.Fit
            )
        }

        // TopBarUI - layer trên cùng với positioning tuyệt đối
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            TopBarUI(
                bagCoinScore = totalScore,
                chestItems = chestItems,
                onBuyItem = { itemName, price ->
                    if (totalScore >= price) {
                        totalScore -= price
                        chestItems = chestItems + itemName

                        if (!playerName.isNullOrBlank()) {
                            FirebaseHelper.updateScore(playerName, totalScore)
                            FirebaseHelper.updateChest(playerName, chestItems)
                        }
                    }
                }
            )
        }

        // Exit button
        val context = LocalContext.current
        IconButton(
            onClick = {
                val playerName = PrefManager.getPlayerName(context)
                if (!playerName.isNullOrBlank()) {
                    db.collection("rankings")
                        .whereEqualTo("name", playerName)
                        .get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                val docId = docs.documents[0].id
                                db.collection("rankings").document(docId)
                                    .update("score", totalScore)
                            } else {
                                val playerData = hashMapOf(
                                    "name" to playerName,
                                    "score" to totalScore
                                )
                                db.collection("rankings").add(playerData)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.w("Firebase", "Failed to upload score", e)
                        }
                }
                onExit()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Exit")
        }

        // Sound menu
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

        // Health bar
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 116.dp, end = 16.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 200.dp, height = 25.dp)) {
                drawRoundRect(color = Color.Gray, size = size)
                val ratio = planeHp.value / 1000f
                drawRoundRect(color = Color.Green, size = Size(size.width * ratio, size.height))
            }
        }

        // GAME OVER overlay
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
                    Button(onClick = { onExit() }) {
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
                        text = "Bạn thu được ${totalScore} xu",
                        color = Color.Yellow,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onExit() }) {
                        Text(text = "Thoát", fontSize = 20.sp)
                    }
                }
            }
        }

        if (showChest) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁 Chest của bạn 🎁", fontSize = 24.sp, color = Color.Yellow)
                    Spacer(Modifier.height(16.dp))
                    chestItems.forEach { item ->
                        Text(text = "- $item", fontSize = 18.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showChest = false }) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}