package com.rayy.lokermart.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rayy.lokermart.data.*
import com.rayy.lokermart.ui.*
import com.rayy.lokermart.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Beranda & pencarian

private val SortOptions = listOf("newest" to "Terbaru", "cheapest" to "Termurah", "priciest" to "Termahal")

@Composable
fun HomeScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    var sort by remember { mutableStateOf("newest") }
    var sortMenu by remember { mutableStateOf(false) }

    // Tunggu pengguna berhenti mengetik sebelum mencari
    LaunchedEffect(query) {
        delay(400)
        debounced = query.trim()
    }

    val loader = rememberLoader(debounced, category, sort) {
        Network.api.products(
            q = debounced.ifBlank { null },
            category = if (category == "Semua") null else category,
            sort = sort,
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Lokermart")
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Cari barang bekas…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus") }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.SwapVert, "Urutkan") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { sort = key; sortMenu = false },
                            trailingIcon = { if (sort == key) Icon(Icons.Default.Check, null) },
                        )
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Categories) { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(Icons.Default.SearchOff, "Barang tidak ditemukan", "Coba kata kunci atau kategori lain.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { p ->
                        ProductCard(p, onClick = { nav.navigate(Route.product(p.id)) })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Detail produk

@Composable
fun ProductDetailScreen(nav: NavController, id: Int) {
    val loader = rememberLoader(id) { Network.api.product(id) }
    val me = Session.user

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Detail barang", onBack = { nav.popBackStack() })
        LoadView(loader) { p ->
            val isMine = me != null && me.id == p.sellerId
            val available = p.status == "active"
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RemoteImage(p.imageUrl, Modifier.fillMaxWidth().aspectRatio(1.2f))
                Column(Modifier.padding(16.dp)) {
                    Text(rupiah(p.price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {}, label = { Text(p.condition.ifBlank { "Bekas" }) })
                        AssistChip(onClick = {}, label = { Text(p.category) })
                        if (!available) StatusChip(productStatusLabel(p.status), p.status)
                    }
                    if (!p.description.isNullOrBlank()) {
                        SectionTitle("Deskripsi")
                        Text(p.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    SectionTitle("Penjual")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.sellerName ?: "Penjual", style = MaterialTheme.typography.titleSmall)
                                RatingStars(p.sellerRating ?: 0.0, p.sellerRatingCount ?: 0)
                            }
                        }
                    }
                    SectionTitle("Cara kerja")
                    HowItWorks()
                }
            }
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        isMine -> OutlinedButton(
                            onClick = { nav.navigate(Route.productForm(p.id)) },
                            enabled = available,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(if (available) "Ubah barang" else "Barang tidak bisa diubah") }

                        !available -> Text(
                            "Barang ini sudah tidak tersedia.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )

                        else -> {
                            OutlinedButton(
                                onClick = { nav.navigate(Route.chat(p.id, p.sellerId)) },
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text("Chat / Nego") }
                            Button(
                                onClick = { nav.navigate(Route.checkout(p.id)) },
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Text("Beli") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    val steps = listOf(
        "Bayar ke Lokermart. Dana ditahan aman sampai barang diterima.",
        "Penjual menitipkan barang di loker yang kamu pilih.",
        "Masukkan kode loker yang disepakati untuk mengambil barang.",
        "Konfirmasi barang sesuai, dana baru diteruskan ke penjual.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ Barang saya (tab Jual)

@Composable
fun MyProductsScreen(nav: NavController) {
    val loader = rememberLoader { Network.api.myProducts() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Barang saya", actions = {
            FilledTonalButton(onClick = { nav.navigate(Route.productForm()) }, modifier = Modifier.padding(end = 12.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Jual")
            }
        })
        LoadView(loader) { list ->
            if (list.isEmpty()) {
                EmptyBox(Icons.Default.Inventory2, "Belum ada barang", "Ketuk “Jual” untuk memasang barang pertamamu.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { p ->
                        Card(
                            onClick = { nav.navigate(Route.product(p.id)) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RemoteImage(p.imageUrl, Modifier.size(72.dp).clip(MaterialTheme.shapes.small))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                                    Text(rupiah(p.price), color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    StatusChip(productStatusLabel(p.status), p.status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Form tambah / ubah barang

@Composable
fun ProductFormScreen(nav: NavController, id: Int) {
    val editing = id > 0
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val existing = rememberLoader(id) { if (editing) Network.api.product(id) else null }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (editing) "Ubah barang" else "Jual barang", onBack = { nav.popBackStack() })
        LoadView(existing) { p ->
            var title by remember { mutableStateOf(p?.title ?: "") }
            var description by remember { mutableStateOf(p?.description ?: "") }
            var price by remember { mutableStateOf(p?.price?.let { Math.round(it).toString() } ?: "") }
            var category by remember { mutableStateOf(p?.category ?: ProductCategories.first()) }
            var condition by remember { mutableStateOf(p?.condition?.ifBlank { null } ?: Conditions[1]) }
            var imageUrl by remember { mutableStateOf(p?.imageUrl ?: "") }
            var saving by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var confirmDelete by remember { mutableStateOf(false) }

            fun save() {
                val amount = price.toDoubleOrNull()
                when {
                    title.isBlank() -> { error = "Judul wajib diisi"; return }
                    amount == null || amount <= 0 -> { error = "Harga harus lebih dari 0"; return }
                }
                error = null
                saving = true
                val body = ProductIn(title.trim(), description.trim().ifBlank { null }, amount!!, category, condition, imageUrl.trim().ifBlank { null })
                scope.launch {
                    apiCall { if (editing) Network.api.updateProduct(id, body) else Network.api.addProduct(body) }
                        .onSuccess { toast(context, if (editing) "Perubahan disimpan" else "Barang berhasil dipasang"); nav.popBackStack() }
                        .onFailure { error = it.message }
                    saving = false
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul barang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    price, { v -> price = v.filter { it.isDigit() }.take(12) },
                    label = { Text("Harga (Rp)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField("Kategori", category, ProductCategories, { category = it })
                DropdownField("Kondisi", condition, Conditions, { condition = it })
                OutlinedTextField(
                    description, { description = it }, label = { Text("Deskripsi (kelengkapan, cacat, alasan jual)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    imageUrl, { imageUrl = it }, label = { Text("Link foto (opsional)") }, singleLine = true,
                    supportingText = { Text("Tempel alamat gambar https://…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (imageUrl.isNotBlank()) RemoteImage(imageUrl.trim(), Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium))

                val message = error
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
                PrimaryButton(if (editing) "Simpan perubahan" else "Pasang barang", onClick = { save() }, loading = saving)
                if (editing) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hapus barang", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (confirmDelete) {
                ConfirmDialog(
                    "Hapus barang?", "Barang tidak akan tampil lagi di pencarian.", "Hapus",
                    onConfirm = {
                        confirmDelete = false
                        scope.launch {
                            apiCall { Network.api.removeProduct(id) }
                                .onSuccess { toast(context, "Barang dihapus"); nav.popBackStack(Route.PRODUCT, true) }
                                .onFailure { toast(context, it.message ?: "Gagal menghapus") }
                        }
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }
    }
}
