package com.rayy.lokermart.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rayy.lokermart.data.*
import com.rayy.lokermart.ui.*
import com.rayy.lokermart.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Daftar percakapan

@Composable
fun ChatListScreen(nav: NavController) {
    val loader = rememberLoader { Network.api.conversations() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Chat")
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(
                    Icons.Default.ChatBubbleOutline,
                    "Belum ada percakapan",
                    "Buka sebuah barang lalu ketuk “Chat / Nego” untuk bertanya atau menawar.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { "${it.productId}-${it.otherId}" }) { c ->
                        Card(
                            onClick = { nav.navigate(Route.chat(c.productId, c.otherId)) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RemoteImage(c.imageUrl, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(c.otherName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(formatTime(c.lastAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(c.productTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val preview = when {
                                        c.lastOffer != null -> "Tawaran ${rupiah(c.lastOffer)}"
                                        else -> c.lastBody ?: ""
                                    }
                                    Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Ruang chat

@Composable
fun AmountDialog(title: String, hint: String, initial: String = "", onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val amount = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { v -> text = v.filter { it.isDigit() }.take(12) },
                label = { Text(hint) }, singleLine = true,
                prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (amount != null && amount > 0) onConfirm(amount) }, enabled = amount != null && amount > 0) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ChatRoomScreen(nav: NavController, pid: Int, other: Int) {
    val productLoader = rememberLoader(pid) { Network.api.product(pid) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Chat & negosiasi", onBack = { nav.popBackStack() })
        LoadView(productLoader) { product -> ChatBody(nav, product, other) }
    }
}

@Composable
private fun ColumnScope.ChatBody(nav: NavController, product: Product, other: Int) {
    val me = Session.user
    val myId = me?.id ?: 0
    val iAmBuyer = myId != product.sellerId
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loader = rememberLoader(product.id, other) { Network.api.chat(product.id, other) }
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var offerDialog by remember { mutableStateOf(false) }
    var counterFor by remember { mutableStateOf<Message?>(null) }

    // Polling sederhana tiap 4 detik agar pesan baru muncul
    LaunchedEffect(loader) {
        while (true) {
            delay(4000)
            loader.load(silent = true)
        }
    }

    val messages = loader.dataOrNull() ?: emptyList()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send(body: String?, offer: Double?) {
        sending = true
        scope.launch {
            apiCall { Network.api.send(product.id, MessageIn(other, body, offer)) }
                .onSuccess { text = ""; loader.load(silent = true) }
                .onFailure { toast(context, it.message ?: "Gagal mengirim") }
            sending = false
        }
    }

    fun respond(m: Message, action: String, amount: Double? = null) {
        scope.launch {
            apiCall { Network.api.respondOffer(m.id, OfferAction(action, amount)) }
                .onSuccess { loader.load(silent = true) }
                .onFailure { toast(context, it.message ?: "Gagal memproses tawaran") }
        }
    }

    // Kartu ringkas barang
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(product.imageUrl, Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("Harga awal ${rupiah(product.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (product.status != "active") StatusChip(productStatusLabel(product.status), product.status)
        }
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (val s = loader.state) {
            is Ui.Loading -> LoadingBox()
            is Ui.Err -> ErrorBox(s.message, onRetry = { loader.reload() })
            is Ui.Ok -> {
                if (s.data.isEmpty()) {
                    EmptyBox(Icons.Default.ChatBubbleOutline, "Mulai percakapan", "Tanyakan kondisi barang atau ajukan tawaran harga.")
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(s.data, key = { it.id }) { m ->
                            MessageBubble(
                                m = m,
                                mine = m.senderId == myId,
                                onAccept = { respond(m, "accept") },
                                onReject = { respond(m, "reject") },
                                onCounter = { counterFor = m },
                            )
                        }
                    }
                }
            }
        }
    }

    // Tawaran yang sudah disepakati -> pembeli bisa lanjut membayar
    val accepted = messages.lastOrNull { it.offerStatus == "accepted" }
    if (iAmBuyer && accepted != null && accepted.offerPrice != null && product.status == "active") {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Harga disepakati", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(rupiah(accepted.offerPrice), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Button(onClick = { nav.navigate(Route.checkout(product.id, accepted.id, Math.round(accepted.offerPrice))) }) { Text("Lanjut bayar") }
            }
        }
    }

    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iAmBuyer && product.status == "active") {
                IconButton(onClick = { offerDialog = true }) { Icon(Icons.Default.LocalOffer, "Ajukan tawaran", tint = MaterialTheme.colorScheme.primary) }
            }
            OutlinedTextField(
                text, { text = it },
                placeholder = { Text("Tulis pesan…") },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (text.isNotBlank()) send(text.trim(), null) },
                enabled = text.isNotBlank() && !sending,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = MaterialTheme.colorScheme.primary) }
        }
    }

    if (offerDialog) {
        AmountDialog(
            "Ajukan tawaran", "Harga tawaran",
            initial = Math.round(product.price).toString(),
            onConfirm = { offerDialog = false; send(null, it) },
            onDismiss = { offerDialog = false },
        )
    }
    val counter = counterFor
    if (counter != null) {
        AmountDialog(
            "Tawar balik", "Harga penawaran",
            initial = counter.offerPrice?.let { Math.round(it).toString() } ?: "",
            onConfirm = { counterFor = null; respond(counter, "counter", it) },
            onDismiss = { counterFor = null },
        )
    }
}

@Composable
private fun MessageBubble(m: Message, mine: Boolean, onAccept: () -> Unit, onReject: () -> Unit, onCounter: () -> Unit) {
    val bg = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            if (m.offerPrice != null) {
                Text("Tawaran harga", style = MaterialTheme.typography.labelMedium, color = fg)
                Text(rupiah(m.offerPrice), style = MaterialTheme.typography.titleLarge, color = fg)
                val label = offerStatusLabel(m.offerStatus)
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodySmall, color = fg)
                if (!mine && m.offerStatus == "pending") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAccept, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Terima") }
                        OutlinedButton(onClick = onCounter, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Tawar") }
                    }
                    TextButton(onClick = onReject) { Text("Tolak", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (!m.body.isNullOrBlank()) Text(m.body, color = fg)
        }
        Text(
            formatTime(m.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
