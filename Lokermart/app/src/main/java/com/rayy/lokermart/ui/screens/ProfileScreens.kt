package com.rayy.lokermart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavController
import com.rayy.lokermart.data.*
import com.rayy.lokermart.ui.*
import com.rayy.lokermart.ui.components.*
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Akun

@Composable
fun ProfileScreen(nav: NavController) {
    val user = Session.user ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Akun")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!user.phone.isNullOrBlank()) Text(user.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        RatingStars(user.ratingAvg, user.ratingCount)
                    }
                    IconButton(onClick = { editDialog = true }) { Icon(Icons.Default.Edit, "Ubah profil") }
                }
            }
            MenuItem(Icons.Default.ReportProblem, "Laporan masalah saya") { nav.navigate(Route.DISPUTES) }
            if (user.role == "admin") MenuItem(Icons.Default.AdminPanelSettings, "Panel admin") { nav.navigate(Route.ADMIN) }
            MenuItem(Icons.AutoMirrored.Filled.Logout, "Keluar") { logoutDialog = true }
        }
    }

    if (editDialog) {
        var name by remember { mutableStateOf(user.name) }
        var phone by remember { mutableStateOf(user.phone ?: "") }
        AlertDialog(
            onDismissRequest = { editDialog = false },
            title = { Text("Ubah profil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
                    OutlinedTextField(
                        phone, { phone = it }, label = { Text("No. HP") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    editDialog = false
                    scope.launch {
                        apiCall { Network.api.updateMe(UpdateMeIn(name.trim(), phone.trim().ifBlank { null })) }
                            .onSuccess { Session.user = it; toast(context, "Profil diperbarui") }
                            .onFailure { toast(context, it.message ?: "Gagal menyimpan") }
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editDialog = false }) { Text("Batal") } },
        )
    }
    if (logoutDialog) {
        ConfirmDialog("Keluar dari akun?", "Kamu perlu masuk lagi untuk memakai aplikasi.", "Keluar",
            onConfirm = { logoutDialog = false; Session.logout() }, onDismiss = { logoutDialog = false })
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Laporan masalah saya

@Composable
fun MyDisputesScreen(nav: NavController) {
    val loader = rememberLoader { Network.api.myDisputes() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Laporan masalah", onBack = { nav.popBackStack() })
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(Icons.Default.TaskAlt, "Tidak ada laporan", "Jika ada masalah pada pesanan, laporkan dari halaman pesanan.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { d -> DisputeCard(d, onOpenOrder = { nav.navigate(Route.order(d.orderId)) }) }
                }
            }
        }
    }
}

@Composable
private fun DisputeCard(d: Dispute, onOpenOrder: () -> Unit, footer: @Composable () -> Unit = {}) {
    Card(onClick = onOpenOrder, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.title ?: "Pesanan #${d.orderId}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                StatusChip(disputeStatusLabel(d.status), d.status)
            }
            if (d.buyerName != null || d.sellerName != null) {
                Text("Pembeli: ${d.buyerName ?: "-"} • Penjual: ${d.sellerName ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(d.reason, style = MaterialTheme.typography.bodyMedium)
            if (!d.resolution.isNullOrBlank()) Text("Keputusan: ${d.resolution}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(formatTime(d.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            footer()
        }
    }
}

// ------------------------------------------------------------------ Panel admin

@Composable
fun AdminScreen(nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Panel admin", onBack = { nav.popBackStack() })
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Loker") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sengketa") })
        }
        if (tab == 0) AdminLockers() else AdminDisputes(nav)
    }
}

@Composable
private fun AdminLockers() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loader = rememberLoader { Network.api.lockers() }
    var addDialog by remember { mutableStateOf(false) }
    var slotsFor by remember { mutableStateOf<Locker?>(null) }

    Box(Modifier.fillMaxSize()) {
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(Icons.Default.Lock, "Belum ada loker", "Ketuk tombol + untuk menambah loker.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { l ->
                        Card(onClick = { slotsFor = l }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, style = MaterialTheme.typography.titleSmall)
                                    Text(l.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${l.freeSlots}/${l.totalSlots} kosong", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { addDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Tambah loker")
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var count by remember { mutableStateOf("4") }
        val n = count.toIntOrNull() ?: 0
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Tambah loker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama loker") }, singleLine = true)
                    OutlinedTextField(address, { address = it }, label = { Text("Alamat") })
                    OutlinedTextField(
                        count, { v -> count = v.filter { it.isDigit() }.take(2) }, label = { Text("Jumlah slot (1–20)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && address.isNotBlank() && n in 1..20, onClick = {
                    addDialog = false
                    // Slot diberi label A1, A2, … dengan ukuran sedang
                    val slots = (1..n).map { SlotIn("A$it", "M") }
                    scope.launch {
                        apiCall { Network.api.addLocker(LockerIn(name.trim(), address.trim(), slots)) }
                            .onSuccess { toast(context, "Loker ditambahkan"); loader.reload(silent = true) }
                            .onFailure { toast(context, it.message ?: "Gagal menambah loker") }
                    }
                }) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Batal") } },
        )
    }

    val locker = slotsFor
    if (locker != null) {
        SlotsDialog(locker, onDismiss = { slotsFor = null; loader.reload(silent = true) })
    }
}

@Composable
private fun SlotsDialog(locker: Locker, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loader = rememberLoader(locker.id) { Network.api.slots(locker.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(locker.name) },
        text = {
            Box(Modifier.heightIn(max = 360.dp)) {
                LoadView(loader) { slots ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(slots, key = { it.id }) { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${s.label} (${sizeLabel(s.size)})", Modifier.weight(1f))
                                StatusChip(slotStatusLabel(s.status), s.status)
                                // Hanya slot kosong/perawatan yang boleh diubah admin; sisanya diatur pesanan
                                if (s.status == "free" || s.status == "maintenance") {
                                    TextButton(onClick = {
                                        val next = if (s.status == "free") "maintenance" else "free"
                                        scope.launch {
                                            apiCall { Network.api.setSlot(s.id, SlotStatusIn(next)) }
                                                .onSuccess { loader.reload(silent = true) }
                                                .onFailure { toast(context, it.message ?: "Gagal mengubah slot") }
                                        }
                                    }) { Text(if (s.status == "free") "Perawatan" else "Aktifkan") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun AdminDisputes(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loader = rememberLoader { Network.api.adminDisputes() }
    var resolving by remember { mutableStateOf<Dispute?>(null) }
    var decision by remember { mutableStateOf("buyer") }

    LoadView(loader) { list ->
        if (list.isEmpty()) {
            EmptyBox(Icons.Default.TaskAlt, "Tidak ada sengketa terbuka")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { d ->
                    DisputeCard(d, onOpenOrder = { nav.navigate(Route.order(d.orderId)) }, footer = {
                        if (d.status == "open") {
                            Button(onClick = { decision = "buyer"; resolving = d }) { Text("Putuskan") }
                        }
                    })
                }
            }
        }
    }

    val target = resolving
    if (target != null) {
        TextInputDialogWithChoice(
            decision = decision,
            onDecision = { decision = it },
            onSubmit = { note ->
                resolving = null
                scope.launch {
                    apiCall { Network.api.resolveDispute(target.id, ResolveIn(decision, note.ifBlank { null })) }
                        .onSuccess { toast(context, "Sengketa diputuskan"); loader.reload(silent = true) }
                        .onFailure { toast(context, it.message ?: "Gagal memutuskan") }
                }
            },
            onDismiss = { resolving = null },
        )
    }
}

@Composable
private fun TextInputDialogWithChoice(decision: String, onDecision: (String) -> Unit, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Putuskan sengketa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = decision == "buyer", onClick = { onDecision("buyer") })
                    Text("Refund ke pembeli")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = decision == "seller", onClick = { onDecision("seller") })
                    Text("Teruskan dana ke penjual")
                }
                OutlinedTextField(note, { note = it }, label = { Text("Catatan keputusan (opsional)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(note.trim()) }) { Text("Putuskan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
