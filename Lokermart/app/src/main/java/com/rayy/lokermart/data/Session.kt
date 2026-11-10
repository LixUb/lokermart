package com.rayy.lokermart.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Menyimpan token login (SharedPreferences) dan data pengguna yang sedang masuk. */
object Session {
    private var prefs: SharedPreferences? = null

    @Volatile
    var token: String? = null
        private set

    /** null = belum masuk (atau data pengguna belum dimuat). */
    var user by mutableStateOf<User?>(null)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("lokermart_session", Context.MODE_PRIVATE)
        prefs = p
        token = p.getString("token", null)
    }

    fun login(newToken: String, newUser: User) {
        token = newToken
        prefs?.edit()?.putString("token", newToken)?.apply()
        user = newUser
    }

    // token dikosongkan lebih dulu, user terakhir: UI bereaksi pada perubahan user
    fun logout() {
        token = null
        prefs?.edit()?.remove("token")?.apply()
        user = null
    }
}

/** Membungkus panggilan API: hasilnya Result dengan pesan error berbahasa Indonesia. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    // 401 saat sudah login = token kedaluwarsa -> keluar otomatis
    if (e.code() == 401 && Session.token != null) Session.logout()
    val message = try {
        Network.gson.fromJson(e.response()?.errorBody()?.string(), ErrorBody::class.java)?.error
    } catch (_: Exception) {
        null
    }
    Result.failure(Exception(message ?: "Terjadi kesalahan (${e.code()})"))
} catch (e: IOException) {
    Result.failure(Exception("Tidak dapat terhubung ke server. Periksa koneksi dan alamat server."))
} catch (e: Exception) {
    Result.failure(Exception(e.message ?: "Terjadi kesalahan tak terduga"))
}
