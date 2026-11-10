package com.rayy.lokermart.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rayy.lokermart.data.Product
import com.rayy.lokermart.data.apiCall
import com.rayy.lokermart.ui.Tone
import com.rayy.lokermart.ui.rupiah
import com.rayy.lokermart.ui.toneOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Pemuat data

sealed interface Ui<out T> {
    data object Loading : Ui<Nothing>
    data class Ok<T>(val data: T) : Ui<T>
    data class Err(val message: String) : Ui<Nothing>
}

/** Memuat data dari API dan menyimpan statusnya (loading / sukses / error) sebagai state Compose. */
class Loader<T>(private val scope: CoroutineScope, private val call: suspend () -> T) {
    var state by mutableStateOf<Ui<T>>(Ui.Loading)
        private set

    /** silent = true: jangan tampilkan loading dan pertahankan data lama jika gagal (untuk polling). */
    suspend fun load(silent: Boolean = false) {
        if (!silent) state = Ui.Loading
        apiCall(call)
            .onSuccess { state = Ui.Ok(it) }
            .onFailure { if (!silent) state = Ui.Err(it.message ?: "Terjadi kesalahan") }
    }

    fun reload(silent: Boolean = false) {
        scope.launch { load(silent) }
    }

    /** Data terakhir yang berhasil dimuat, atau null. */
    fun dataOrNull(): T? = (state as? Ui.Ok<T>)?.data
}

/** Membuat Loader baru setiap kali [keys] berubah, lalu langsung memuat. */
@Composable
fun <T> rememberLoader(vararg keys: Any?, call: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val loader = remember(*keys) { Loader(scope, call) }
    LaunchedEffect(loader) { loader.load() }
    return loader
}

@Composable
fun <T> LoadView(loader: Loader<T>, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        is Ui.Loading -> LoadingBox()
        is Ui.Err -> ErrorBox(s.message, onRetry = { loader.reload() })
        is Ui.Ok -> content(s.data)
    }
}

// ---------------------------------------------------------------- Status layar

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
        }
    }
}

@Composable
fun EmptyBox(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- Elemen umum

@Composable
private fun toneColor(tone: Tone): Color {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.Success -> if (dark) Color(0xFF4ADE80) else Color(0xFF15803D)
        Tone.Warning -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        Tone.Danger -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
        Tone.Info -> MaterialTheme.colorScheme.primary
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun StatusChip(text: String, status: String, modifier: Modifier = Modifier) {
    val color = toneColor(toneOf(status))
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RatingStars(value: Double, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(4.dp))
        Text(
            if (count > 0) String.format("%.1f (%d)", value, count) else "Baru",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dropdown berbentuk field, tanpa API eksperimental. */
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Lapisan transparan seukuran field agar seluruh area bisa diketuk
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

// ---------------------------------------------------------------- Gambar dari URL (tanpa library tambahan)

private val imageCache = object : LruCache<String, Bitmap>(24) {}

private fun downloadBitmap(url: String): Bitmap? = try {
    // HttpURLConnection dipakai (bukan OkHttp) agar tidak bergantung pada versi OkHttp
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode in 200..299) {
            val bytes = conn.inputStream.use { it.readBytes() }
            // Perkecil saat decode agar hemat memori
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 800 && bounds.outHeight / (sample * 2) >= 800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } else {
            null
        }
    } finally {
        conn.disconnect()
    }
} catch (_: Exception) {
    null
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(if (url.isNullOrBlank()) null else imageCache.get(url)) }
    LaunchedEffect(url) {
        if (!url.isNullOrBlank() && bitmap == null) {
            val loaded = withContext(Dispatchers.IO) { downloadBitmap(url) }
            if (loaded != null) {
                imageCache.put(url, loaded)
                bitmap = loaded
            }
        }
    }
    val current = bitmap
    if (current != null) {
        Image(current.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        RemoteImage(product.imageUrl, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(12.dp)) {
            Text(rupiah(product.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(product.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                product.condition,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
