package com.example.game

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object ChestItemEffects {

    /**
     * Apply chest item effects for GameScreen (Level 1)
     */
    fun applyGameEffect(
        item: ChestItem,
        monsters: List<MonsterState>,
        coins: List<Coin>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        coroutineScope: CoroutineScope,
        screenWidthPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onShieldActivate: () -> Unit,
        onWallActivate: () -> Unit,
        onTimeActivate: () -> Unit,
        onLevelClear: () -> Unit
    ) {
        android.util.Log.e("CHEST_ITEM_DEBUG", "📦📦📦 APPLYING GAME EFFECT FOR: '${item.name}' 📦📦📦")

        when (item.name) {
            "Fireworks", "Firework2", "Pháo ", "Pháo sáng" -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "✅ MATCHED FIREWORKS ITEM: '${item.name}'")
                // Pháo sáng: Win game + hiện thông báo xu thu thập được
                applyFireworksEffect(
                    monsters = monsters,
                    coins = coins,
                    bagCoins = bagCoins,
                    coroutineScope = coroutineScope,
                    screenWidthPx = screenWidthPx,
                    planeX = planeX,
                    onScoreUpdate = onScoreUpdate,
                    onLevelClear = onLevelClear
                )
            }
            "Bom", "Bomb" -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "✅ MATCHED BOMB ITEM: '${item.name}'")
                // Bom: Nổ tung coin và quái hiện có
                applyBombEffect(
                    monsters = monsters,
                    coins = coins,
                    bagCoins = bagCoins,
                    coroutineScope = coroutineScope,
                    screenWidthPx = screenWidthPx,
                    planeX = planeX,
                    onScoreUpdate = onScoreUpdate,
                    onLevelClear = onLevelClear
                )
            }
            "Shield", "Khiên" -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "✅ MATCHED SHIELD ITEM: '${item.name}'")
                // Khiên: Vẽ vòng tròn bảo vệ plane
                onShieldActivate()
            }
            "Time", "Đồng hồ", "Clock" -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "✅ MATCHED TIME ITEM: '${item.name}'")
                // Đồng hồ: Ngưng quái chạy
                onTimeActivate()
            }
            "Wall", "Tường", "Tường chắn" -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "✅ MATCHED WALL ITEM: '${item.name}'")
                // Tường: Vẽ bức tường chặn quái + hút máu quái
                onWallActivate()
            }
            else -> {
                android.util.Log.e("CHEST_ITEM_DEBUG", "❌❌❌ NO MATCH FOUND FOR ITEM: '${item.name}' ❌❌❌")
                android.util.Log.e("CHEST_ITEM_DEBUG", "Available patterns: Fireworks, Firework2, Pháo , Pháo sáng, Bom, Bomb, Shield, Khiên, Time, Đồng hồ, Clock, Wall, Tường, Tường chắn")
            }
        }
    }

    private fun applyFireworksEffect(
        monsters: List<MonsterState>,
        coins: List<Coin>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        coroutineScope: CoroutineScope,
        screenWidthPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit
    ) {
        android.util.Log.e("FIREWORKS_DEBUG", "🎆🎆🎆 FIREWORKS EFFECT STARTED! 🎆🎆🎆")
        android.util.Log.e("FIREWORKS_DEBUG", "Monsters count: ${monsters.size}")
        android.util.Log.e("FIREWORKS_DEBUG", "Coins count: ${coins.size}")

        // IMMEDIATELY destroy all monsters and collect coins - no coroutine delays
        var totalCoinsCollected = 0
        coins.forEach { coin ->
            if (!coin.collected.value) {
                coin.collected.value = true
                totalCoinsCollected += 1
                android.util.Log.e("FIREWORKS_DEBUG", "Collected coin at (${coin.x}, ${coin.y.value})")

                // Create bag coin display
                val bag = BagCoinDisplay(coin.x, coin.y.value, 1)
                bagCoins.add(bag)

                // Remove bag display after delay (but don't wait for it)
                coroutineScope.launch {
                    delay(1000)
                    bagCoins.remove(bag)
                }
            }
        }

        android.util.Log.e("FIREWORKS_DEBUG", "Total coins collected: $totalCoinsCollected")

        // IMMEDIATELY update score
        if (totalCoinsCollected > 0) {
            onScoreUpdate(totalCoinsCollected)
            android.util.Log.e("FIREWORKS_DEBUG", "Score updated with $totalCoinsCollected coins")
        }

        // IMMEDIATELY destroy all monsters
        monsters.forEach { monster ->
            val oldHp = monster.hp.value
            monster.hp.value = 0
            monster.isDying.value = true
            monster.respawnCount = monster.maxRespawn // Prevent respawn
            android.util.Log.e("FIREWORKS_DEBUG", "Monster destroyed - HP: $oldHp -> 0")
        }
        android.util.Log.e("FIREWORKS_DEBUG", "All ${monsters.size} monsters destroyed")

        // IMMEDIATELY trigger level clear - no delay!
        android.util.Log.e("FIREWORKS_DEBUG", "🚀🚀🚀 CALLING onLevelClear() NOW! 🚀🚀🚀")
        onLevelClear()
        android.util.Log.e("FIREWORKS_DEBUG", "onLevelClear() called successfully")
    }

    private fun applyBombEffect(
        monsters: List<MonsterState>,
        coins: List<Coin>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        coroutineScope: CoroutineScope,
        screenWidthPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit
    ) {
        coroutineScope.launch {
            // Collect all visible coins with explosion effect
            var totalCoinsCollected = 0
            coins.forEach { coin ->
                if (!coin.collected.value) {
                    coin.collected.value = true
                    totalCoinsCollected += 1

                    // Create bag coin display with explosion effect
                    val bag = BagCoinDisplay(coin.x, coin.y.value, 1)
                    bagCoins.add(bag)

                    // Remove bag display after delay
                    launch {
                        delay(800)
                        bagCoins.remove(bag)
                    }
                }
            }

            // Update score for collected coins
            if (totalCoinsCollected > 0) {
                onScoreUpdate(totalCoinsCollected)
            }

            // Explode all visible monsters
            monsters.forEach { monster ->
                if (monster.hp.value > 0 && !monster.isDying.value) {
                    monster.hp.value = 0
                    monster.isDying.value = true
                    // Allow respawn for bomb (unlike fireworks)
                }
            }

            // Check if all monsters are permanently destroyed and no more respawns
            delay(1000)
            val allMonstersDestroyed = monsters.all {
                it.hp.value <= 0 && it.respawnCount >= it.maxRespawn
            }

            if (allMonstersDestroyed) {
                // If no more monsters can respawn, end game
                onLevelClear()
            }
            // If monsters can still respawn, continue game
        }
    }

    /**
     * Apply chest item effects for Level2Activity
     */
    fun applyLevel2Effect(
        item: ChestItem,
        monsters: List<MonsterState2>,
        coins: List<Coin2>,
        bagCoins: SnapshotStateList<BagCoinDisplay2>,
        coroutineScope: CoroutineScope,
        screenWidthPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onShieldActivate: () -> Unit,
        onWallActivate: () -> Unit,
        onTimeActivate: () -> Unit,
        onLevelClear: () -> Unit
    ) {
        android.util.Log.e("LEVEL2_EFFECTS", "🔥🔥🔥 LEVEL2 EFFECT FOR: '${item.name}' 🔥🔥🔥")

        when (item.name) {
            "Fireworks", "Firework2", "Pháo ", "Pháo sáng" -> {
                android.util.Log.e("LEVEL2_EFFECTS", "✅ MATCHED FIREWORKS ITEM IN LEVEL2: '${item.name}'")
                // Pháo sáng: Win game + hiện thông báo xu thu thập được
                applyFireworksEffectLevel2(
                    monsters = monsters,
                    coins = coins,
                    bagCoins = bagCoins,
                    coroutineScope = coroutineScope,
                    onScoreUpdate = onScoreUpdate,
                    onLevelClear = onLevelClear
                )
            }
            "Bom", "Bomb" -> {
                android.util.Log.e("LEVEL2_EFFECTS", "✅ MATCHED BOMB ITEM IN LEVEL2: '${item.name}'")
                // Bom: Nổ tung coin và quái hiện có
                applyBombEffectLevel2(
                    monsters = monsters,
                    coins = coins,
                    bagCoins = bagCoins,
                    coroutineScope = coroutineScope,
                    onScoreUpdate = onScoreUpdate,
                    onLevelClear = onLevelClear
                )
            }
            "Shield", "Khiên" -> {
                android.util.Log.e("LEVEL2_EFFECTS", "✅ MATCHED SHIELD ITEM IN LEVEL2: '${item.name}'")
                // Khiên: Vẽ vòng tròn bảo vệ plane
                onShieldActivate()
            }
            "Time", "Đồng hồ", "Clock" -> {
                android.util.Log.e("LEVEL2_EFFECTS", "✅ MATCHED TIME ITEM IN LEVEL2: '${item.name}'")
                // Đồng hồ: Ngưng quái chạy
                onTimeActivate()
            }
            "Wall", "Tường", "Tường chắn" -> {
                android.util.Log.e("LEVEL2_EFFECTS", "✅ MATCHED WALL ITEM IN LEVEL2: '${item.name}'")
                // Tường: Vẽ bức tường chặn quái + hút máu quái
                onWallActivate()
            }
            else -> {
                android.util.Log.e("LEVEL2_EFFECTS", "❌❌❌ NO MATCH FOR LEVEL2 ITEM: '${item.name}' ❌❌❌")
                android.util.Log.e("LEVEL2_EFFECTS", "Available Level2 patterns: Fireworks, Firework2, Pháo , Pháo sáng, Bom, Bomb, Shield, Khiên, Time, Đồng hồ, Clock, Wall, Tường, Tường chắn")
            }
        }
    }

    private fun applyFireworksEffectLevel2(
        monsters: List<MonsterState2>,
        coins: List<Coin2>,
        bagCoins: SnapshotStateList<BagCoinDisplay2>,
        coroutineScope: CoroutineScope,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit
    ) {
        android.util.Log.e("LEVEL2_FIREWORKS", "🎆🎆🎆 LEVEL2 FIREWORKS EFFECT STARTED! 🎆🎆🎆")
        android.util.Log.e("LEVEL2_FIREWORKS", "Monsters count: ${monsters.size}")
        android.util.Log.e("LEVEL2_FIREWORKS", "Coins count: ${coins.size}")

        coroutineScope.launch {
            // Collect all visible coins
            var totalCoinsCollected = 0
            coins.forEach { coin ->
                if (!coin.collected.value) {
                    coin.collected.value = true
                    totalCoinsCollected += 1
                    android.util.Log.e("LEVEL2_FIREWORKS", "Collected coin at (${coin.x}, ${coin.y.value})")

                    // Create bag coin display
                    val bag = BagCoinDisplay2(coin.x, coin.y.value, 1)
                    bagCoins.add(bag)

                    // Remove bag display after delay
                    launch {
                        delay(1000)
                        bagCoins.remove(bag)
                    }
                }
            }

            android.util.Log.e("LEVEL2_FIREWORKS", "Total coins collected: $totalCoinsCollected")

            // Update score
            if (totalCoinsCollected > 0) {
                onScoreUpdate(totalCoinsCollected)
                android.util.Log.e("LEVEL2_FIREWORKS", "Score updated with $totalCoinsCollected coins")
            }

            // Destroy all monsters
            monsters.forEach { monster ->
                val oldHp = monster.hp.value
                val wasAlive = monster.alive.value
                monster.hp.value = 0
                monster.alive.value = false
                android.util.Log.e("LEVEL2_FIREWORKS", "Monster destroyed - HP: $oldHp -> 0, Alive: $wasAlive -> false")
            }
            android.util.Log.e("LEVEL2_FIREWORKS", "All ${monsters.size} monsters destroyed")

            // Delay then trigger level clear (win game)
            delay(500)
            android.util.Log.e("LEVEL2_FIREWORKS", "🚀🚀🚀 CALLING onLevelClear() FOR LEVEL2! 🚀🚀🚀")
            onLevelClear()
        }
    }

    private fun applyBombEffectLevel2(
        monsters: List<MonsterState2>,
        coins: List<Coin2>,
        bagCoins: SnapshotStateList<BagCoinDisplay2>,
        coroutineScope: CoroutineScope,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit
    ) {
        android.util.Log.e("LEVEL2_BOMB", "💣💣💣 LEVEL2 BOMB EFFECT STARTED! 💣💣💣")
        android.util.Log.e("LEVEL2_BOMB", "Monsters count: ${monsters.size}")
        android.util.Log.e("LEVEL2_BOMB", "Coins count: ${coins.size}")

        coroutineScope.launch {
            // Collect all visible coins with explosion effect
            var totalCoinsCollected = 0
            coins.forEach { coin ->
                if (!coin.collected.value) {
                    coin.collected.value = true
                    totalCoinsCollected += 1
                    android.util.Log.e("LEVEL2_BOMB", "Exploded and collected coin at (${coin.x}, ${coin.y.value})")

                    // Create bag coin display with explosion effect
                    val bag = BagCoinDisplay2(coin.x, coin.y.value, 1)
                    bagCoins.add(bag)

                    // Remove bag display after delay
                    launch {
                        delay(800)
                        bagCoins.remove(bag)
                    }
                }
            }

            android.util.Log.e("LEVEL2_BOMB", "Total coins exploded and collected: $totalCoinsCollected")

            // Update score for collected coins
            if (totalCoinsCollected > 0) {
                onScoreUpdate(totalCoinsCollected)
                android.util.Log.e("LEVEL2_BOMB", "Score updated with $totalCoinsCollected coins from bomb")
            }

            // Explode all visible monsters - but allow them to respawn
            var monstersExploded = 0
            monsters.forEach { monster ->
                if (monster.hp.value > 0 && monster.alive.value) {
                    val oldHp = monster.hp.value
                    monster.hp.value = 0
                    monster.alive.value = false
                    monstersExploded++
                    android.util.Log.e("LEVEL2_BOMB", "Monster exploded - HP: $oldHp -> 0, Alive: true -> false")
                }
            }
            android.util.Log.e("LEVEL2_BOMB", "Total monsters exploded: $monstersExploded")

            // IMPORTANT: Do NOT call onLevelClear() for bomb in Level2
            // Bomb should only explode current monsters/coins, not win the game
            // Monsters will respawn and game continues normally
            android.util.Log.e("LEVEL2_BOMB", "💣 Bomb effect complete - game continues, monsters will respawn")
        }
    }
}

// Helper function to respawn coin (if needed in effects)
private fun respawnCoinAfterDelay(
    coin: Coin,
    screenWidthPx: Float,
    coroutineScope: CoroutineScope,
    delayMs: Long = 2000
) {
    coroutineScope.launch {
        delay(delayMs)
        coin.y.value = -Random.nextInt(100, 600).toFloat()
        coin.x = Random.nextFloat() * (screenWidthPx - 50f)
        coin.speed = Random.nextFloat() * 0.5f + 0.5f
        coin.collected.value = false
    }
}
