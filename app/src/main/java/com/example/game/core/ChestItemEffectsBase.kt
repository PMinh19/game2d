package com.example.game.core

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.*
import android.util.Log

object ChestItemEffectsBase {

    fun <M : BaseMonster, C : BaseCoin> applyItemEffect(
        itemName: String,
        monsters: List<M>,
        coins: List<C>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        coroutineScope: CoroutineScope,
        screenHeightPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onShieldToggle: (Boolean) -> Unit,
        onWallToggle: (Boolean) -> Unit,
        onTimeToggle: (Boolean) -> Unit,
        onLevelClear: () -> Unit,
        onShowBoomEffect: (Float, Float) -> Unit = { _, _ -> } // Thêm tham số này với default value
    ) {
        when (itemName.trim()) {
            "Fireworks", "Pháo sáng", "Pháo ", "Pháo" -> applyFireworksEffect(
                coins, bagCoins, coroutineScope, screenHeightPx, planeX,
                onScoreUpdate, onLevelClear, onShowBoomEffect
            )
            "Bom", "Bomb" -> applyBombEffect(monsters, coins, bagCoins, coroutineScope, onScoreUpdate, onLevelClear)
            "Shield", "Khiên" -> applyTimedToggle(coroutineScope, 10_000, onShieldToggle)
            "Wall", "Tường", "Tường chắn" -> applyTimedToggle(coroutineScope, 10_000, onWallToggle)
            "Time", "Đồng hồ" -> applyTimedToggle(coroutineScope, 10_000, onTimeToggle)
            else -> Log.w("ChestItemEffectsBase", "⚠ Unknown item: $itemName")
        }
    }

    private fun <C : BaseCoin> applyFireworksEffect(
        coins: List<C>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        scope: CoroutineScope,
        screenHeightPx: Float,
        planeX: Float,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit,
        onShowBoomEffect: (Float, Float) -> Unit
    ) {
        // Đã tắt hiệu ứng boom khi dùng pháo sáng
        // onShowBoomEffect(planeX, screenHeightPx / 2)

        var count = 0
        coins.forEach {
            if (!it.collected.value && it.y.value in 0f..screenHeightPx) {
                it.collected.value = true
                count++
                val bag = BagCoinDisplay(it.x, it.y.value, 1)
                bagCoins.add(bag)
                scope.launch { delay(800); bagCoins.remove(bag) }
            }
        }
        if (count > 0) onScoreUpdate(count)
        onLevelClear()
    }

    private fun <M : BaseMonster, C : BaseCoin> applyBombEffect(
        monsters: List<M>,
        coins: List<C>,
        bagCoins: SnapshotStateList<BagCoinDisplay>,
        scope: CoroutineScope,
        onScoreUpdate: (Int) -> Unit,
        onLevelClear: () -> Unit
    ) {
        scope.launch {
            // Collect all visible coins first
            var coinCount = 0
            coins.forEach {
                if (!it.collected.value) {
                    it.collected.value = true
                    coinCount++
                    val bag = BagCoinDisplay(it.x, it.y.value, 1)
                    bagCoins.add(bag)
                    launch { delay(800); bagCoins.remove(bag) }
                }
            }
            if (coinCount > 0) onScoreUpdate(coinCount)

            // Kill all monsters on screen
            monsters.forEach {
                if (it.alive.value) {
                    it.hp.value = 0
                    it.alive.value = false
                }
            }

            // DON'T automatically call onLevelClear()
            // Let the normal collision check handle level clear when all monsters are dead
        }
    }

    private fun applyTimedToggle(scope: CoroutineScope, duration: Long, toggle: (Boolean) -> Unit) {
        scope.launch {
            toggle(true)
            delay(duration)
            toggle(false)
        }
    }
}
