package com.example.game
import androidx.compose.ui.platform.LocalContext

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
                setVolume(0.5f, 0.5f)
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
    c.speed = Random.nextFloat() * 2f + 1f
    c.collected.value = false
}

private fun checkCollisionPlaneMonster(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    monster: MonsterState
): Boolean {
    val monsterWidth = 80f
    val monsterHeight = 80f
    return planeX < monster.x + monsterWidth &&
            planeX + planeWidth > monster.x &&
            planeY < monster.y.value + monsterHeight &&
            planeY + planeHeight > monster.y.value
}

private fun checkCollisionPlaneCoin(
    planeX: Float,
    planeY: Float,
    planeWidth: Float = 100f,
    planeHeight: Float = 100f,
    coin: Coin
): Boolean {
    val coinWidth = 40f
    val coinHeight = 40f
    return planeX < coin.x + coinWidth &&
            planeX + planeWidth > coin.x &&
            planeY < coin.y.value + coinHeight &&
            planeY + planeHeight > coin.y.value
}

// ---------------------- MAIN SCREEN ----------------------
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
    var isSoundEnabled by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val playerName = PrefManager.getPlayerName(LocalContext.current)

    // ------------------ BAGCOIN SCORE STATE ------------------
    val totalScore = remember { mutableStateOf(0) }

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
                .addOnFailureListener { e -> Log.w("Firebase", "Failed to load score", e) }
        }
    }

    // MediaPlayer toggle
    LaunchedEffect(isSoundEnabled) {
        try {
            if (isSoundEnabled) mediaPlayer?.start() else mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    // Plane
    var planeX by remember { mutableStateOf(screenWidthPx / 2f - 50f) }
    val planeY = screenHeightPx - 250f
    val planeHp = remember { mutableStateOf(1000) }

    // Bullets
    val bullets = remember { mutableStateListOf<Bullet>() }

    LaunchedEffect(Unit) {
        while (true) {
            bullets.add(Bullet(planeX - 20f, planeY))
            bullets.add(Bullet(planeX + 20f, planeY))
            if (isSoundEnabled) soundPool.play(shootSoundId, 0.5f, 0.5f, 1, 0, 1f)
            delay(350)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
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
            while (true) {
                m.y.value += m.speed
                if (m.y.value > screenHeightPx + 100f && m.hp.value > 0) {
                    respawnMonster(m, screenWidthPx)
                    planeHp.value -= 10
                    if (planeHp.value < 0) planeHp.value = 0
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
            while (true) {
                if (!c.collected.value) {
                    c.y.value += c.speed
                    if (c.y.value > screenHeightPx + 50f) {
                        respawnCoin(c, screenWidthPx)
                    }
                }
                delay(16)
            }
        }
    }

    // Bagcoin displays
    val bagCoins = remember { mutableStateListOf<BagCoinDisplay>() }

    // ------------------ THROTTLE HIT SOUND ------------------
    var lastHitTime by remember { mutableStateOf(0L) }
    fun playHitSound() {
        val now = System.currentTimeMillis()
        if (now - lastHitTime > 50) {
            if (isSoundEnabled) {
                soundPool.play(hitSoundId, 0.8f, 0.8f, 2, 0, 1f)
            }
            lastHitTime = now
        }
    }

    // Collision: bullets ↔ monsters
    LaunchedEffect(Unit) {
        while (true) {
            val bulletIterator = bullets.iterator()
            while (bulletIterator.hasNext()) {
                val b = bulletIterator.next()
                monsters.forEach { m ->
                    if (b.x in (m.x - 10f)..(m.x + 110f) &&
                        b.y in (m.y.value - 10f)..(m.y.value + 110f)
                    ) {
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

    // Collision: plane ↔ monsters
    LaunchedEffect(Unit) {
        while (true) {
            monsters.forEach { m ->
                if (checkCollisionPlaneMonster(planeX, planeY, 100f, 100f, m)) {
                    planeHp.value -= 50
                    if (planeHp.value < 0) planeHp.value = 0
                    respawnMonster(m, screenWidthPx)
                }
            }
            delay(200)
        }
    }

    // Collision: plane ↔ coins
    LaunchedEffect(Unit) {
        while (true) {
            coins.forEach { c ->
                if (!c.collected.value && checkCollisionPlaneCoin(planeX, planeY, 100f, 100f, c)) {
                    c.collected.value = true
                    totalScore.value += 1  // tăng tổng số coin

                    // ------------------ LƯU SCORE LÊN FIREBASE ------------------
                    if (!playerName.isNullOrBlank()) {
                        db.collection("rankings")
                            .whereEqualTo("name", playerName)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    val docId = docs.documents[0].id
                                    db.collection("rankings").document(docId)
                                        .update("score", totalScore.value)
                                        .addOnFailureListener { e -> Log.e("Firebase", "Failed to update score", e) }
                                } else {
                                    val data = hashMapOf("name" to playerName, "score" to totalScore.value)
                                    db.collection("rankings").add(data)
                                }
                            }
                    }

                    if (isSoundEnabled) soundPool.play(coinSoundId, 1f, 1f, 3, 0, 1f)

                    // Thêm bagcoin hiển thị
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
                    planeX = (planeX + dragAmount.x).coerceIn(0f, screenWidthPx - 100f)
                    change.consume()
                }
            }
    ) {
        MovingBackgroundOffset(screenWidthPx)
        var context= LocalContext.current
        IconButton(
            onClick = {

                // Upload score trước khi thoát
                val playerName = PrefManager.getPlayerName(context)
                if (!playerName.isNullOrBlank()) {
                    uploadScoreToFirebase(playerName, totalScore.value)
                }
                onExit()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Exit")
        }

        // Sound toggle
        var soundEnabled by remember { mutableStateOf(true) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            IconToggleButton(
                checked = soundEnabled,
                onCheckedChange = {
                    soundEnabled = it
                    isSoundEnabled = soundEnabled
                    if (soundEnabled) mediaPlayer?.start() else mediaPlayer?.pause()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.speaker),
                    contentDescription = null,
                    tint = if (soundEnabled) Color.White else Color.Gray,
                    modifier = Modifier.size(30.dp)
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
                drawRoundRect(
                    color = Color.Green,
                    size = Size(size.width * ratio, size.height)
                )
            }
        }

        // Draw monsters
        monsters.forEach { m ->
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
                drawRect(
                    color = Color.Green,
                    size = Size(size.width * hpRatio, size.height)
                )
            }
        }

        // Draw coins
        coins.forEach { c ->
            if (!c.collected.value) {
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

        // Draw bagcoin + số
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
            Image(
                painter = painterResource(R.drawable.dan2),
                contentDescription = null,
                modifier = Modifier
                    .absoluteOffset { IntOffset(b.x.roundToInt(), b.y.roundToInt()) }
                    .size(30.dp),
                contentScale = ContentScale.Fit
            )
        }

        // Draw plane
        Image(
            painter = painterResource(R.drawable.maybay1),
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset { IntOffset(planeX.roundToInt(), planeY.roundToInt()) }
                .size(100.dp),
            contentScale = ContentScale.Fit
        )
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
