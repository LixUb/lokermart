package com.rayy.lokermart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rayy.lokermart.data.*
import com.rayy.lokermart.ui.components.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val scope = rememberCoroutineScope()
    var isLogin by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(): String? {
        if (!isLogin && name.isBlank()) return "Nama wajib diisi"
        if (!email.contains("@") || email.trim().length < 5) return "Format email belum benar"
        if (isLogin && password.isEmpty()) return "Password wajib diisi"
        if (!isLogin && password.length < 8) return "Password minimal 8 karakter"
        return null
    }

    fun submit() {
        val problem = validate()
        if (problem != null) { error = problem; return }
        error = null
        loading = true
        scope.launch {
            apiCall {
                if (isLogin) Network.api.login(LoginIn(email.trim(), password))
                else Network.api.register(RegisterIn(name.trim(), email.trim(), phone.trim().ifBlank { null }, password))
            }.onSuccess { Session.login(it.token, it.user) }
                .onFailure { error = it.message }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Lokermart", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Jual beli barang bekas lewat loker berkode. Aman, tanpa tatap muka.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(32.dp))

        if (!isLogin) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (!isLogin) {
            OutlinedTextField(
                phone, { phone = it }, label = { Text("No. HP (opsional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (!isLogin) ({ Text("Minimal 8 karakter") }) else null,
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Tampilkan password")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        val message = error
        if (message != null) {
            Text(
                message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (isLogin) "Masuk" else "Daftar", onClick = { submit() }, loading = loading)
        TextButton(onClick = { isLogin = !isLogin; error = null }, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (isLogin) "Belum punya akun? Daftar" else "Sudah punya akun? Masuk")
        }
        Spacer(Modifier.height(24.dp))
    }
}
