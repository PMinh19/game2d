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

@Composable
fun ChestDisplay(chestItems: List<String>) {
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
                    else chestItems.forEach { item -> Text(item) }
                }
            },
            confirmButton = { Button(onClick = { showChestDialog.value = false }) { Text("OK") } }
        )
    }
}

@Composable
fun StoreDisplay(
    bagCoinScore: Int,
    chestItems: MutableList<String>,
    onBuyItem: (itemName: String, price: Int) -> Unit
) {
    val showStoreDialog = remember { mutableStateOf(false) }
    val showConfirmBuyDialog = remember { mutableStateOf<Pair<String, Int>?>(null) }

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

    if (showStoreDialog.value) {
        AlertDialog(
            onDismissRequest = { showStoreDialog.value = false },
            title = { Text("Store") },
            text = {
                Column {
                    val items = listOf(
                        Triple("Fireworks", R.drawable.fireworks, 20),
                        Triple("Firework2", R.drawable.firework2, 20)
                    )
                    items.forEach { (name, res, price) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(res),
                                contentDescription = name,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$price Coins")
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = { showConfirmBuyDialog.value = Pair(name, price) }) {
                                Text("Mua")
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showStoreDialog.value = false }) { Text("Close") } }
        )
    }

    showConfirmBuyDialog.value?.let { (itemName, itemPrice) ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showConfirmBuyDialog.value = null },
            title = { Text("Mua $itemName?") },
            text = { Text("Bạn có muốn mua $itemName với $itemPrice Coins không?") },
            confirmButton = {
                Button(onClick = {
                    if (bagCoinScore >= itemPrice) {
                        onBuyItem(itemName, itemPrice)
                    } else {
                        Toast.makeText(context, "Không đủ coins để mua $itemName", Toast.LENGTH_SHORT).show()
                    }
                    showConfirmBuyDialog.value = null
                    showStoreDialog.value = false
                }) { Text("Yes") }
            },
            dismissButton = { Button(onClick = { showConfirmBuyDialog.value = null }) { Text("No") } }
        )
    }
}

@Composable
fun TopBarUI(
    bagCoinScore: Int,
    chestItems: List<String>,
    onBuyItem: (itemName: String, price: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp, start = 16.dp)
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        StoreDisplay(bagCoinScore, chestItems.toMutableList(), onBuyItem)
        ChestDisplay(chestItems)
        BagCoinDisplay(bagCoinScore)
    }
}
