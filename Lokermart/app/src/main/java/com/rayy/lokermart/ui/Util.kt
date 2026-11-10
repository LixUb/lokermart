package com.rayy.lokermart.ui

import android.content.Context
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

val Categories = listOf(
    "Semua", "Elektronik", "Buku & Alat Tulis", "Pakaian", "Perabot", "Hobi & Olahraga", "Kendaraan", "Lainnya",
)
val ProductCategories = Categories.drop(1)
val Conditions = listOf("Bekas - Seperti Baru", "Bekas - Baik", "Bekas - Layak Pakai")

val PaymentMethods = listOf(
    "qris" to "QRIS",
    "va_bca" to "Virtual Account BCA",
    "va_mandiri" to "Virtual Account Mandiri",
    "gopay" to "GoPay",
    "ovo" to "OVO",
)

/** Harus sama dengan PLATFORM_FEE_PERCENT di backend (server yang menghitung angka final). */
const val FEE_PERCENT = 2

private val idLocale: Locale = Locale.forLanguageTag("id-ID")

fun rupiah(value: Double): String = "Rp " + NumberFormat.getIntegerInstance(idLocale).format(Math.round(value))

fun feeOf(price: Double): Double = Math.round(price * FEE_PERCENT / 100.0).toDouble()

/** Mengubah timestamp ISO dari server (UTC) menjadi "9 Nov, 19:30" di zona waktu perangkat. */
fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 19) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.substring(0, 19)) ?: return ""
        SimpleDateFormat("d MMM, HH:mm", idLocale).format(date)
    } catch (_: Exception) {
        ""
    }
}

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

fun orderStatusLabel(status: String): String = when (status) {
    "pending_payment" -> "Menunggu pembayaran"
    "paid" -> "Menunggu barang dititipkan"
    "deposited" -> "Siap diambil"
    "picked_up" -> "Sudah diambil"
    "completed" -> "Selesai"
    "disputed" -> "Dalam sengketa"
    "cancelled" -> "Dibatalkan"
    "refunded" -> "Dana dikembalikan"
    else -> status
}

fun productStatusLabel(status: String): String = when (status) {
    "active" -> "Tayang"
    "reserved" -> "Dipesan"
    "sold" -> "Terjual"
    "removed" -> "Dihapus"
    else -> status
}

fun slotStatusLabel(status: String): String = when (status) {
    "free" -> "Kosong"
    "reserved" -> "Dipesan"
    "occupied" -> "Terisi"
    "maintenance" -> "Perawatan"
    else -> status
}

fun disputeStatusLabel(status: String): String = when (status) {
    "open" -> "Sedang ditinjau"
    "resolved_buyer" -> "Diputuskan untuk pembeli"
    "resolved_seller" -> "Diputuskan untuk penjual"
    else -> status
}

fun offerStatusLabel(status: String?): String = when (status) {
    "pending" -> "Menunggu respons"
    "accepted" -> "Disepakati"
    "rejected" -> "Ditolak"
    "countered" -> "Sudah ditawar balik"
    "withdrawn" -> "Digantikan tawaran baru"
    else -> ""
}

fun sizeLabel(size: String): String = when (size) {
    "S" -> "Kecil"
    "M" -> "Sedang"
    "L" -> "Besar"
    else -> size
}

enum class Tone { Success, Warning, Danger, Info, Neutral }

fun toneOf(status: String): Tone = when (status) {
    "completed", "free", "active", "accepted", "resolved_buyer", "resolved_seller" -> Tone.Success
    "paid", "deposited", "picked_up", "reserved", "open", "pending" -> Tone.Warning
    "disputed", "cancelled", "refunded", "maintenance", "removed", "rejected" -> Tone.Danger
    "pending_payment", "occupied" -> Tone.Info
    else -> Tone.Neutral
}
