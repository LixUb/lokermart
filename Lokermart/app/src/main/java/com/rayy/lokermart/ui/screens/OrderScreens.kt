package com.rayy.lokermart.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rayy.lokermart.data.*
import com.rayy.lokermart.ui.*
import com.rayy.lokermart.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Checkout

@Composable
fun CheckoutScreen(nav: NavController, pid: Int, offerId: Int, amount: Long) {
    val productLoader = rememberLoader(pid) { Network.api.product(pid) }
    val lockerLoader = rememberLoader { Network.api.lockers() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Checkout", onBack = { nav.popBackStack() })
        LoadView(productLoader) { product ->
            LoadView(lockerLoader) { lockers -> CheckoutForm(nav, product, lockers, offerId, amount) }
        }
    }
}

@Composable
private fun CheckoutForm(nav: NavController, product: Product, lockers: List<Locker>, offerId: Int, amount: Long) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val price = if (offerId > 0 && amount > 0) amount.toDouble() else product.price
    val fee = feeOf(price)

    var lockerId by remember { mutableStateOf<Int?>(null) }
    var customCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var methodKey by remember { mutableStateOf(PaymentMethods.first().first) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val lid = lockerId
        when {
            lid == null -> { error = "Pilih loker pengambilan"; return }
            customCode && !Regex("^\\d{4,8}$").matches(code) -> { error = "Kode harus 4–8 digit angka"; return }
        }
        error = null
        loading = true
        scope.launch {
            val created = apiCall {
                Network.api.createOrder(
                    OrderIn(product.id, lid!!, if (offerId > 0) offerId else null, if (customCode) code else null)
                )
            }
            val order = created.getOrNull()
            if (order == null) {
                error = created.exceptionOrNull()?.message
                loading = false
                return@launch
            }
            val paid = apiCall { Network.api.pay(order.id, PayIn(methodKey)) }
            if (paid.isFailure) toast(context, "Pesanan dibuat, tetapi pembayaran gagal. Coba bayar lagi dari halaman pesanan.")
            loading = false
            nav.navigate(Route.order(order.id)) {
                popUpTo(Route.CHECKOUT) { inclusive = true }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(product.imageUrl, Modifier.size(64.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        if (offerId > 0 && amount > 0) "Harga disepakati ${rupiah(price)}" else rupiah(price),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        SectionTitle("1. Pilih loker pengambilan", Modifier.padding(top = 8.dp))
        if (lockers.isEmpty()) Text("Belum ada loker tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lockers.forEach { l ->
            val full = l.freeSlots <= 0
            val selected = lockerId == l.id
            OutlinedCard(
                onClick = { if (!full) lockerId = l.id },
                enabled = !full,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, enabled = !full)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.titleSmall)
                        Text(l.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (full) StatusChip("Penuh", "maintenance") else StatusChip("${l.freeSlots} kosong", "free")
                }
            }
        }

        SectionTitle("2. Kode loker")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Tentukan kode sendiri", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (customCode) "Kode ini dipakai untuk membuka loker." else "Kode acak dibuat otomatis dan tampil setelah pesanan dibuat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = customCode, onCheckedChange = { customCode = it })
        }
        if (customCode) {
            OutlinedTextField(
                code, { v -> code = v.filter { it.isDigit() }.take(8) },
                label = { Text("Kode 4–8 digit") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle("3. Metode pembayaran")
        DropdownField(
            "Metode", PaymentMethods.first { it.first == methodKey }.second,
            PaymentMethods.map { it.second },
            onSelect = { label -> methodKey = PaymentMethods.first { it.second == label }.first },
        )

        SectionTitle("Ringkasan")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryRow("Harga barang", rupiah(price))
                SummaryRow("Biaya layanan ($FEE_PERCENT%)", rupiah(fee))
                HorizontalDivider()
                SummaryRow("Total bayar", rupiah(price + fee), bold = true)
            }
        }
        Text(
            "Pembayaran ditahan Lokermart dan baru diteruskan ke penjual setelah kamu mengonfirmasi barang. (Mode simulasi: tidak ada uang sungguhan yang dipindahkan.)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val message = error
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Bayar ${rupiah(price + fee)}", onClick = { submit() }, loading = loading)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------ Daftar pesanan

@Composable
fun OrdersScreen(nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    val role = if (tab == 0) "buyer" else "seller"
    val loader = rememberLoader(role) { Network.api.orders(role) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pesanan")
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pembelian") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Penjualan") })
        }
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(
                    Icons.Default.Receipt,
                    if (tab == 0) "Belum ada pembelian" else "Belum ada penjualan",
                    if (tab == 0) "Pesanan yang kamu buat akan muncul di sini." else "Saat ada yang membeli barangmu, pesanannya muncul di sini.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { o ->
                        Card(
                            onClick = { nav.navigate(Route.order(o.id)) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RemoteImage(o.imageUrl, Modifier.size(64.dp).clip(MaterialTheme.shapes.small))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(o.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        if (tab == 0) "Penjual: ${o.sellerName}" else "Pembeli: ${o.buyerName}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    StatusChip(orderStatusLabel(o.status), o.status)
                                }
                                Text(rupiah(o.agreedPrice), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Detail pesanan

private val ProgressSteps = listOf("Dibayar", "Dititipkan", "Diambil", "Selesai")

private fun progressIndex(status: String): Int = when (status) {
    "paid" -> 0
    "deposited" -> 1
    "picked_up" -> 2
    "completed" -> 3
    else -> -1
}

@Composable
fun OrderDetailScreen(nav: NavController, id: Int) {
    val loader = rememberLoader(id) { Network.api.order(id) }

    // Status berubah dari sisi lawan transaksi (mis. penjual menitipkan barang): perbarui otomatis
    LaunchedEffect(loader) {
        while (true) {
            delay(5000)
            loader.load(silent = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pesanan #$id", onBack = { nav.popBackStack() })
        LoadView(loader) { order -> OrderContent(nav, order, loader) }
    }
}

@Composable
private fun OrderContent(nav: NavController, order: Order, loader: Loader<Order>) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isBuyer = order.role == "buyer"
    var busy by remember { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf(false) }
    var disputeDialog by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }

    fun <T> act(successMessage: String?, call: suspend () -> T) {
        busy = true
        scope.launch {
            apiCall(call)
                .onSuccess { if (successMessage != null) toast(context, successMessage); loader.load(silent = true) }
                .onFailure { toast(context, it.message ?: "Gagal") }
            busy = false
        }
    }

    fun retrieve() {
        if (!Regex("^\\d{4,8}$").matches(code)) { codeError = "Masukkan kode 4–8 digit"; return }
        codeError = null
        busy = true
        scope.launch {
            apiCall { Network.api.retrieve(order.id, CodeIn(code)) }
                .onSuccess {
                    toast(context, "Loker terbuka. Silakan ambil barangmu.")
                    code = ""
                    loader.load(silent = true)
                }
                .onFailure { codeError = it.message }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(orderStatusLabel(order.status), order.status)
                Text(statusHint(order, isBuyer), style = MaterialTheme.typography.bodyMedium)
                val idx = progressIndex(order.status)
                if (idx >= 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        ProgressSteps.forEachIndexed { i, label ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (i <= idx) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                                    tint = if (i <= idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        // Kode pengambilan (hanya pembeli, hanya selama menunggu pengambilan)
        if (isBuyer && order.pickupCode != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Kode loker kamu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        order.pickupCode,
                        fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Rahasia — jangan dibagikan ke siapa pun, termasuk penjual.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Barang & loker
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(order.imageUrl, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(order.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (isBuyer) "Penjual: ${order.sellerName}" else "Pembeli: ${order.buyerName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SummaryRow("Harga barang", rupiah(order.agreedPrice))
                SummaryRow("Biaya layanan", rupiah(order.fee))
                SummaryRow("Total", rupiah(order.total), bold = true)
                if (!order.lockerName.isNullOrBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${order.lockerName}${if (order.slotLabel != null) " • Slot ${order.slotLabel}" else ""}", style = MaterialTheme.typography.titleSmall)
                            Text(order.lockerAddress ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Aksi sesuai peran & status
        when {
            isBuyer && order.status == "pending_payment" -> {
                PrimaryButton("Bayar ${rupiah(order.total)}", onClick = { payDialog = true }, loading = busy)
                OutlinedButton(onClick = { cancelDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batalkan pesanan") }
            }

            isBuyer && order.status == "deposited" -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shownCodeError = codeError
                        Text("Ambil barang", style = MaterialTheme.typography.titleSmall)
                        Text("Datang ke loker lalu masukkan kode di bawah untuk membuka slot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            code, { v -> code = v.filter { it.isDigit() }.take(8) },
                            label = { Text("Kode loker") }, singleLine = true, isError = shownCodeError != null,
                            supportingText = if (shownCodeError != null) ({ Text(shownCodeError) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton("Buka loker", onClick = { retrieve() }, loading = busy)
                    }
                }
            }

            isBuyer && order.status == "picked_up" -> {
                PrimaryButton("Barang sesuai — selesaikan pesanan", onClick = { act("Pesanan selesai. Dana diteruskan ke penjual.") { Network.api.confirm(order.id) } }, loading = busy)
            }

            !isBuyer && order.status == "paid" -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(
                        "Pembeli sudah membayar. Taruh barang di ${order.lockerName ?: "loker"}" +
                            (if (order.slotLabel != null) " slot ${order.slotLabel}" else "") + ", tutup pintunya, lalu tekan tombol di bawah.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                PrimaryButton("Barang sudah saya titipkan", onClick = { act("Pembeli akan diberi tahu.") { Network.api.deposit(order.id) } }, loading = busy)
            }
        }

        if (order.status == "paid") {
            OutlinedButton(onClick = { cancelDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batalkan & refund") }
        }
        if (order.status in listOf("paid", "deposited", "picked_up") && !order.disputeOpen) {
            TextButton(onClick = { disputeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ReportProblem, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Laporkan masalah")
            }
        }
        if (order.status == "disputed") {
            OutlinedButton(onClick = { nav.navigate(Route.DISPUTES) }, modifier = Modifier.fillMaxWidth()) { Text("Lihat status sengketa") }
        }
        if (order.status == "completed" && !order.ratedByMe) {
            PrimaryButton("Beri rating", onClick = { rateDialog = true })
        }
        if (order.status == "completed" && order.ratedByMe) {
            Text("Terima kasih, ratingmu sudah terkirim.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (payDialog) {
        var key by remember { mutableStateOf(PaymentMethods.first().first) }
        AlertDialog(
            onDismissRequest = { payDialog = false },
            title = { Text("Pilih metode pembayaran") },
            text = {
                Column {
                    PaymentMethods.forEach { (k, label) ->
                        Row(Modifier.fillMaxWidth().clickable { key = k }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = key == k, onClick = { key = k })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { payDialog = false; act("Pembayaran berhasil") { Network.api.pay(order.id, PayIn(key)) } }) { Text("Bayar ${rupiah(order.total)}") }
            },
            dismissButton = { TextButton(onClick = { payDialog = false }) { Text("Batal") } },
        )
    }
    if (cancelDialog) {
        ConfirmDialog(
            "Batalkan pesanan?",
            if (order.status == "paid") "Dana akan dikembalikan ke pembeli dan loker dikosongkan." else "Pesanan akan dibatalkan.",
            "Ya, batalkan",
            onConfirm = { cancelDialog = false; act("Pesanan dibatalkan") { Network.api.cancel(order.id) } },
            onDismiss = { cancelDialog = false },
        )
    }
    if (rateDialog) {
        RateDialog(
            onSubmit = { stars, comment ->
                rateDialog = false
                act("Terima kasih atas ratingmu") { Network.api.rate(order.id, RateIn(stars, comment)) }
            },
            onDismiss = { rateDialog = false },
        )
    }
    if (disputeDialog) {
        TextInputDialog(
            title = "Laporkan masalah",
            label = "Ceritakan masalahnya (min. 10 karakter)",
            confirmText = "Kirim laporan",
            minLength = 10,
            onSubmit = { reason ->
                disputeDialog = false
                act("Laporan dikirim. Dana tetap ditahan sampai ada keputusan.") { Network.api.dispute(order.id, DisputeIn(reason)) }
            },
            onDismiss = { disputeDialog = false },
        )
    }
}

private fun statusHint(o: Order, isBuyer: Boolean): String = when (o.status) {
    "pending_payment" -> if (isBuyer) "Selesaikan pembayaran agar penjual bisa menitipkan barang. Pesanan batal otomatis jika tidak dibayar dalam 30 menit." else "Menunggu pembeli membayar."
    "paid" -> if (isBuyer) "Pembayaran diterima. Menunggu penjual menitipkan barang di loker." else "Pembayaran diterima. Titipkan barang di loker."
    "deposited" -> if (isBuyer) "Barang sudah ada di loker. Masukkan kode untuk mengambilnya." else "Barang sudah dititipkan. Menunggu pembeli mengambil."
    "picked_up" -> if (isBuyer) "Periksa barangmu, lalu selesaikan pesanan. Otomatis selesai dalam 48 jam jika tidak ada laporan." else "Pembeli sudah mengambil barang. Dana cair setelah dikonfirmasi."
    "completed" -> "Transaksi selesai."
    "disputed" -> "Laporan masalah sedang ditinjau tim Lokermart. Dana ditahan."
    "refunded" -> "Dana dikembalikan ke pembeli."
    "cancelled" -> "Pesanan dibatalkan."
    else -> ""
}

// ------------------------------------------------------------------ Dialog bersama

@Composable
fun RateDialog(onSubmit: (Int, String?) -> Unit, onDismiss: () -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beri rating") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    for (i in 1..5) {
                        IconButton(onClick = { stars = i }) {
                            Icon(
                                if (i <= stars) Icons.Default.Star else Icons.Default.StarBorder, "$i bintang",
                                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(comment, { comment = it }, label = { Text("Komentar (opsional)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(stars, comment.trim().ifBlank { null }) }) { Text("Kirim") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    minLength: Int = 1,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, minLines = 3) },
        confirmButton = {
            TextButton(onClick = { onSubmit(text.trim()) }, enabled = text.trim().length >= minLength) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
