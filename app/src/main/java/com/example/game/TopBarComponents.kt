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

// ----- Chest item model -----
data class ChestItem(val name: String, val resId: Int)

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
fun ChestDisplay(chestItems: List<ChestItem>) {
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
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(item.resId),
                                contentDescription = item.name,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(item.name)
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
        ChestItem("Fireworks", R.drawable.fireworks) to 5,
        ChestItem("Firework2", R.drawable.firework2) to 5
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
fun TopBarUI() {
    var bagCoinScore by remember { mutableStateOf(10) }
    var chestItems by remember { mutableStateOf(listOf<ChestItem>()) }

    Column(
        modifier = Modifier
            .padding(top = 16.dp, start = 16.dp)
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        StoreDisplay(bagCoinScore) { item, price ->
            bagCoinScore -= price
            chestItems = chestItems + item
        }
        ChestDisplay(chestItems)
        BagCoinDisplay(bagCoinScore)
    }
}
