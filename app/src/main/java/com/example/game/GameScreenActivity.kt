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
import kotlin.random.Random
import android.graphics.RectF

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
    var hp: MutableState<Int>
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
private fun respawnMonster(m: MonsterState, screenWidthPx: Float) {
    m.y.value = -Random.nextInt(200, 600).toFloat()
    m.x = Random.nextFloat() * (screenWidthPx - 100f)
    m.speed = Random.nextFloat() * 1.5f + 1.5f
    m.hp.value = 100
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

    var musicEnabled by remember { mutableStateOf(true) }   // Nhạc nền
    var sfxEnabled by remember { mutableStateOf(true) }
    val totalScore = remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    // Load score từ Firebase
    LaunchedEffect(playerName) {
        if (!playerName.isNullOrBlank()) {
            db.collection("rankings")
                .whereEqualTo("name", playerName)
                .get()
                .addOnSuccessListener { docs ->
                    if (!docs.isEmpty) {
                        val score = docs.documents[0].getLong("score") ?: 0
                        totalScore.value = score.toInt()
                    }
                }
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
                m.y.value += m.speed
                if (m.y.value > screenHeightPx + 100f && m.hp.value > 0) {
                    respawnMonster(m, screenWidthPx)
                    planeHp.value -= 50
                    if (planeHp.value <= 0) {
                        planeHp.value = 0
                        isGameOver = true

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
                        if (m.hp.value <= 0) respawnMonster(m, screenWidthPx)
                    }
                }

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
                    respawnMonster(m, screenWidthPx)
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
                    totalScore.value += 1

                    // Upload score
                    if (!playerName.isNullOrBlank()) {
                        db.collection("rankings")
                            .whereEqualTo("name", playerName)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    val docId = docs.documents[0].id
                                    db.collection("rankings").document(docId)
                                        .update("score", totalScore.value)
                                } else {
                                    db.collection("rankings").add(
                                        hashMapOf("name" to playerName, "score" to totalScore.value)
                                    )
                                }
                            }
                    }

                    val bag = BagCoinDisplay(c.x, c.y.value, totalScore.value)
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
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (!isGameOver) planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - 100f)
                    change.consume()
                }
            }
    ) {
        MovingBackgroundOffset(screenWidthPx)

        val context = LocalContext.current
        IconButton(
            onClick = {
                val playerName = PrefManager.getPlayerName(context)
                if (!playerName.isNullOrBlank()) uploadScoreToFirebase(playerName, totalScore.value)
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

        // Draw monsters
        monsters.forEach { m ->
            if (!isGameOver) {
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

        // GAME OVER overlay
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)), // mờ nền
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

    }
}


// ---------------------- BACKGROUND ----------------------
@Composable
fun MovingBackgroundOffset(screenWidthPx: Float) {
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
private fun uploadScoreToFirebase(playerName: String, score: Int) {
    val db = FirebaseFirestore.getInstance()
    db.collection("rankings")
        .whereEqualTo("name", playerName)
        .get()
        .addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val docId = docs.documents[0].id
                db.collection("rankings").document(docId)
                    .update("score", score)
            } else {
                // Nếu chưa có, thêm mới
                val playerData = hashMapOf(
                    "name" to playerName,
                    "score" to score
                )
                db.collection("rankings").add(playerData)
            }
        }
        .addOnFailureListener { e ->
            Log.w("Firebase", "Failed to upload score", e)
        }
}
