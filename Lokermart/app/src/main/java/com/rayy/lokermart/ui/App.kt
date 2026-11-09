package com.rayy.lokermart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.rayy.lokermart.data.*
import kotlinx.coroutines.launch

private val Teal = Color(0xFF0F766E)

@Composable
fun App() {
    val nav = rememberNavController()
    NavHost(nav, "login") {
        composable("login") { LoginScreen { nav.navigate("home") { popUpTo("login") { inclusive = true } } } }
        composable("home") { HomeScreen(nav) }
        composable("product/{id}") { PurchaseScreen(it.arguments!!.getString("id")!!.toInt()) }
        composable("orders") { OrdersScreen() }
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("LockerMarket", style = MaterialTheme.typography.headlineLarge, color = Teal)
        Text("Buy & sell second-hand, safely.", Modifier.padding(bottom = 24.dp))
        if (register) OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({
            scope.launch {
                runCatching {
                    val c = Credentials(name.takeIf { register }, email, pass)
                    if (register) Network.api.register(c) else Network.api.login(c)
                }.onSuccess { Network.token = it.token; onDone() }
                    .onFailure { error = "Check your details and try again" }
            }
        }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (register) "Create account" else "Log in") }
        TextButton({ register = !register }) { Text(if (register) "Have an account? Log in" else "New here? Register") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<Product>()) }
    LaunchedEffect(query) { items = runCatching { Network.api.products(query.ifBlank { null }) }.getOrDefault(emptyList()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Browse") }, actions = {
            IconButton({ nav.navigate("orders") }) { Icon(Icons.Default.ShoppingCart, "Orders") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search items") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 12.dp)) {
                items(items) { p ->
                    Card(onClick = { nav.navigate("product/${p.id}") }, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            Text("Rp ${p.price}", color = Teal, style = MaterialTheme.typography.titleLarge)
                            Text("${p.condition ?: ""} · ${p.seller_name ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// Pick a locker -> create order -> pay -> receive one-time pickup code.
@Composable
fun PurchaseScreen(productId: Int) {
    var lockers by remember { mutableStateOf(listOf<Locker>()) }
    var code by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { lockers = runCatching { Network.api.lockers() }.getOrDefault(emptyList()) }
    Column(Modifier.padding(16.dp)) {
        Text("Choose a pickup locker", style = MaterialTheme.typography.titleLarge)
        if (code != null) {
            Text("Your pickup code", Modifier.padding(top = 24.dp))
            Text(code!!, style = MaterialTheme.typography.displayMedium, color = Teal)
            Text("Shown once. The seller will now place the item in the locker.")
        } else lockers.forEach { l ->
            ListItem(
                headlineContent = { Text(l.name) },
                supportingContent = { Text("${l.address} · ${l.free_slots} free") },
                trailingContent = {
                    Button({
                        scope.launch {
                            runCatching {
                                val o = Network.api.createOrder(OrderIn(productId, l.id, 0.0))
                                Network.api.pay(o.id).pickup_code
                            }.onSuccess { code = it }
                        }
                    }) { Text("Pay") }
                })
        }
    }
}

@Composable
fun OrdersScreen() {
    var orders by remember { mutableStateOf(listOf<Order>()) }
    var entry by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { orders = runCatching { Network.api.orders() }.getOrDefault(emptyList()) }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(orders) { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(o.title ?: "Order #${o.id}", style = MaterialTheme.typography.titleMedium)
                    AssistChip({}, label = { Text(o.status.replace('_', ' ')) })
                    if (o.status == "deposited") {
                        OutlinedTextField(entry, { entry = it.take(6) }, label = { Text("Enter 6-digit code") })
                        Button({ scope.launch { runCatching { Network.api.retrieve(o.id, CodeIn(entry)) } } }) { Text("Open locker") }
                    }
                    if (o.status == "picked_up") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ scope.launch { runCatching { Network.api.confirm(o.id) } } }) { Text("All good") }
                        OutlinedButton({ scope.launch { runCatching { Network.api.dispute(o.id, DisputeIn("Item not as described")) } } }) { Text("Report problem") }
                    }
                }
            }
        }
    }
}
