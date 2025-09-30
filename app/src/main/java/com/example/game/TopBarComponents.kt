package com.example.game

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Uses ChestItem from package `com.example.game`

// ----- BagCoin -----
@Composable
fun BagCoinDisplay(bagCoinScore: Int) {
    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.bagcoin),
            contentDescription = "BagCoin",
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "$bagCoinScore",
            color = androidx.compose.ui.graphics.Color.Yellow,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ----- Chest -----
@Composable
fun ChestDisplay(
    chestItems: List<ChestItem>,
    onUseItem: ((ChestItem) -> Unit)? = null
) {
    val showChestDialog = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(60.dp)
            .clickable { showChestDialog.value = true },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.chest),
            contentDescription = "Chest",
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showChestDialog.value) {
        AlertDialog(
            onDismissRequest = { showChestDialog.value = false },
            title = { Text("Chest") },
            text = {
                Column {
                    if (chestItems.isEmpty()) Text("Chest trống")
                    else chestItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Image(
                                painter = painterResource(item.resId),
                                contentDescription = item.name,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(item.name, modifier = Modifier.weight(1f))
                            onUseItem?.let { use ->
                                Button(onClick = {
                                    use(item)
                                    showChestDialog.value = false
                                }) { Text("Chọn") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showChestDialog.value = false }) { Text("OK") }
            }
        )
    }
}

// ----- Store -----
@Composable
fun StoreDisplay(
    bagCoinScore: Int,
    onBuyItem: (ChestItem, Int) -> Unit
) {
    val showStoreDialog = remember { mutableStateOf(false) }
    val showConfirmBuyDialog = remember { mutableStateOf<Pair<ChestItem, Int>?>(null) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(50.dp)
            .clickable { showStoreDialog.value = true },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.store),
            contentDescription = "Store",
            modifier = Modifier.fillMaxSize()
        )
    }

    val itemsForSale = listOf(
        ChestItem("Pháo ", R.drawable.fireworks) to 2,
        ChestItem("Pháo sáng", R.drawable.firework2) to 2,
        ChestItem("Bom", R.drawable.bom1) to 2,
        ChestItem("Khiên", R.drawable.shield1) to 2,
        ChestItem("Tường chắn", R.drawable.wall) to 2,
        ChestItem("Đồng hồ", R.drawable.time) to 2
    )

    if (showStoreDialog.value) {
        AlertDialog(
            onDismissRequest = { showStoreDialog.value = false },
            title = { Text("Store") },
            text = {
                Column {
                    itemsForSale.forEach { (item, price) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(item.resId),
                                contentDescription = item.name,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$price Coins")
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = { showConfirmBuyDialog.value = item to price }) {
                                Text("Mua")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStoreDialog.value = false }) { Text("Close") }
            }
        )
    }

    // Confirm buy dialog
    showConfirmBuyDialog.value?.let { (item, price) ->
        AlertDialog(
            onDismissRequest = { showConfirmBuyDialog.value = null },
            title = { Text("Mua ${item.name}?") },
            text = { Text("Bạn có muốn mua ${item.name} với $price Coins không?") },
            confirmButton = {
                Button(onClick = {
                    if (bagCoinScore >= price) {
                        onBuyItem(item, price)
                        Toast.makeText(context, "Mua ${item.name} thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không đủ coins để mua ${item.name}", Toast.LENGTH_SHORT).show()
                    }
                    showConfirmBuyDialog.value = null
                    showStoreDialog.value = false
                }) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = { showConfirmBuyDialog.value = null }) { Text("No") }
            }
        )
    }
}

// ----- TopBar -----
@Composable
fun TopBarUI(
    bagCoinScore: Int,
    chestItems: List<ChestItem>,
    onBuyItem: (ChestItem, Int) -> Unit,
    onUseChestItem: ((ChestItem) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp, start = 16.dp)
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        StoreDisplay(bagCoinScore, onBuyItem)
        ChestDisplay(chestItems, onUseItem = onUseChestItem)
        BagCoinDisplay(bagCoinScore)
    }
}
