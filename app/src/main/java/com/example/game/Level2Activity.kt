package com.example.game
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
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

class Level2Activity : ComponentActivity() {
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
            Level2Screen(
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
data class Bullet2(var x: Float, var y: Float)
data class MonsterState2(
    var x: Float,
    var y: MutableState<Float>,
    var speed: Float,
    var hp: MutableState<Int>,
    var angle: Float = 0f,
    var radius: Float = 50f,
    var alive: MutableState<Boolean> = mutableStateOf(true)
)

data class MonsterGroup(
    var centerX: MutableState<Float>,
    var centerY: MutableState<Float>,
    var speedX: Float,
    var speedY: Float,
    val monsters: List<MonsterState2>
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
    m.speed = Random.nextFloat() * 1.5f + 1.5f
    m.hp.value = 100
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
    coin: Coin2
): Boolean {
    val coinWidth = 40f
    val coinHeight = 40f
    return planeX < coin.x + coinWidth &&
            planeX + planeWidth > coin.x &&
            planeY < coin.y.value + coinHeight &&
            planeY + planeHeight > coin.y.value
}

@Composable
fun Level2Screen(
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
    val monsterGroups = remember {
        List(6) {
            val centerX = mutableStateOf(Random.nextFloat() * (screenWidthPx - 100f))
            val centerY = mutableStateOf(Random.nextFloat() * 200f)
            val speedX = Random.nextFloat() * 4f - 2f
            val speedY = Random.nextFloat() * 3f + 1f
            MonsterGroup(
                centerX = centerX,
                centerY = centerY,
                speedX = speedX,
                speedY = speedY,
                monsters = List(3) {
                    MonsterState2(
                        x = centerX.value,
                        y = mutableStateOf(centerY.value),
                        speed = 0f,
                        hp = mutableStateOf(100),
                        angle = Random.nextFloat() * 2 * PI.toFloat(),
                        radius = 50f
                    )
                }
            )
        }
    }

    val monsters = remember {
        mutableStateListOf<MonsterState2>().apply {
            monsterGroups.forEach { group -> addAll(group.monsters) }
        }
    }

    monsterGroups.forEach { group ->
        LaunchedEffect(group) {
            while (!isGameOver) {
                group.centerX.value += group.speedX
                group.centerY.value += group.speedY

                if (group.centerX.value < 0 || group.centerX.value > screenWidthPx - 100f) group.speedX *= -1
                if (group.centerY.value < 0 || group.centerY.value > screenHeightPx - 200f) group.speedY *= -1

                group.monsters.forEachIndexed { i, m ->
                    val angle = m.angle + 0.05f
                    m.angle = angle
                    m.x = group.centerX.value + m.radius * cos(angle + i * 2 * PI / 3).toFloat()
                    m.y.value = group.centerY.value + m.radius * sin(angle + i * 2 * PI / 3).toFloat()
                }

                delay(16)
            }
        }
    }

    // Coins
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

    coins.forEach { c ->
        LaunchedEffect(c) {
            while (!isGameOver) {
                if (!c.collected.value) {
                    c.y.value += c.speed
                    if (c.y.value > screenHeightPx + 50f) respawnCoin(c, screenWidthPx)
                }
                delay(16)
            }
        }
    }

    val bagCoins = remember { mutableStateListOf<BagCoinDisplay2>() }

    // Throttle hit sound
    var lastHitTime by remember { mutableStateOf(0L) }
    fun playHitSound() {
        val now = System.currentTimeMillis()
        if (now - lastHitTime > 50) {
            if (sfxEnabled) soundPool.play(hitSoundId, 0.8f, 0.8f, 2, 0, 1f)
            lastHitTime = now
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

                    // Upload score to Firebase
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

                    val bag = BagCoinDisplay2(c.x, c.y.value, totalScore.value)
                    bagCoins.add(bag)
                    coroutineScope.launch { delay(1000); bagCoins.remove(bag) }

                    respawnCoin(c, screenWidthPx)
                }
            }
            delay(50)
        }
    }

    // Collision bullets ↔ monsters
    LaunchedEffect(Unit) {
        while (!isGameOver) {
            val bulletsToRemove = mutableListOf<Bullet>()
            monsters.forEach { m ->
                bullets.forEach { b ->
                    val monsterWidth = 80f
                    val monsterHeight = 80f
                    if (b.x < m.x + monsterWidth &&
                        b.x + 30f > m.x &&
                        b.y < m.y.value + monsterHeight &&
                        b.y + 30f > m.y.value
                    ) {
                        m.hp.value -= 20
                        playHitSound()
                        if (m.hp.value <= 0) {
                            m.hp.value = 0
                            m.alive.value = false  // quái chết
                            // Không respawn ngay
                        }
                        bulletsToRemove.add(b)
                    }

                }
            }
            bullets.removeAll(bulletsToRemove)
            delay(16)
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
        MovingBackgroundOffset2(screenWidthPx)

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

        // Sound options
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

        // Monsters
        monsters.forEach { m ->
            if (!isGameOver && m.alive.value) {   // chỉ vẽ quái còn sống
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


        // Coins
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

        // BagCoins
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

        // Bullets
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

        // Plane
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
fun MovingBackgroundOffset2(screenWidthPx: Float) {
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
