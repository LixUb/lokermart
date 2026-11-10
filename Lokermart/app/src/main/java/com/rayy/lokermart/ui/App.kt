package com.rayy.lokermart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rayy.lokermart.data.Network
import com.rayy.lokermart.data.Session
import com.rayy.lokermart.data.apiCall
import com.rayy.lokermart.ui.components.ErrorBox
import com.rayy.lokermart.ui.components.LoadingBox
import com.rayy.lokermart.ui.screens.AdminScreen
import com.rayy.lokermart.ui.screens.AuthScreen
import com.rayy.lokermart.ui.screens.CheckoutScreen
import com.rayy.lokermart.ui.screens.ChatListScreen
import com.rayy.lokermart.ui.screens.ChatRoomScreen
import com.rayy.lokermart.ui.screens.HomeScreen
import com.rayy.lokermart.ui.screens.MyDisputesScreen
import com.rayy.lokermart.ui.screens.MyProductsScreen
import com.rayy.lokermart.ui.screens.OrderDetailScreen
import com.rayy.lokermart.ui.screens.OrdersScreen
import com.rayy.lokermart.ui.screens.ProductDetailScreen
import com.rayy.lokermart.ui.screens.ProductFormScreen
import com.rayy.lokermart.ui.screens.ProfileScreen

object Route {
    const val HOME = "home"
    const val SELL = "sell"
    const val CHATS = "chats"
    const val ORDERS = "orders"
    const val PROFILE = "profile"

    const val PRODUCT = "product/{id}"
    const val PRODUCT_FORM = "product_form?id={id}"
    const val CHAT = "chat/{pid}/{other}"
    const val CHECKOUT = "checkout/{pid}?offerId={offerId}&amount={amount}"
    const val ORDER = "order/{id}"
    const val DISPUTES = "disputes"
    const val ADMIN = "admin"

    fun product(id: Int) = "product/$id"
    fun productForm(id: Int? = null) = if (id == null) "product_form" else "product_form?id=$id"
    fun chat(pid: Int, other: Int) = "chat/$pid/$other"
    fun checkout(pid: Int, offerId: Int? = null, amount: Long? = null) =
        if (offerId == null || amount == null) "checkout/$pid" else "checkout/$pid?offerId=$offerId&amount=$amount"
    fun order(id: Int) = "order/$id"
}

@Composable
fun App() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val user = Session.user
        if (user != null) {
            MainApp()
        } else if (Session.token == null) {
            AuthScreen()
        } else {
            BootScreen()
        }
    }
}

/** Ada token tersimpan tapi data pengguna belum dimuat: ambil dari server. */
@Composable
private fun BootScreen() {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        error = null
        apiCall { Network.api.me() }
            .onSuccess { Session.user = it }
            .onFailure { error = it.message ?: "Gagal memuat akun" }
    }

    val message = error
    if (message == null) {
        LoadingBox()
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ErrorBox(message, onRetry = { attempt++ })
            TextButton(onClick = { Session.logout() }) { Text("Keluar") }
        }
    }
}

private class TabItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val Tabs = listOf(
    TabItem(Route.HOME, "Beranda", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(Route.SELL, "Jual", Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle),
    TabItem(Route.CHATS, "Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    TabItem(Route.ORDERS, "Pesanan", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    TabItem(Route.PROFILE, "Akun", Icons.Outlined.PersonOutline, Icons.Filled.Person),
)

@Composable
private fun MainApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (Tabs.any { it.route == current }) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Route.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Route.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Route.HOME) { HomeScreen(nav) }
            composable(Route.SELL) { MyProductsScreen(nav) }
            composable(Route.CHATS) { ChatListScreen(nav) }
            composable(Route.ORDERS) { OrdersScreen(nav) }
            composable(Route.PROFILE) { ProfileScreen(nav) }

            composable(Route.PRODUCT, listOf(navArgument("id") { type = NavType.IntType })) {
                ProductDetailScreen(nav, it.arguments?.getInt("id") ?: 0)
            }
            composable(
                Route.PRODUCT_FORM,
                listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 }),
            ) {
                ProductFormScreen(nav, it.arguments?.getInt("id") ?: -1)
            }
            composable(
                Route.CHAT,
                listOf(
                    navArgument("pid") { type = NavType.IntType },
                    navArgument("other") { type = NavType.IntType },
                ),
            ) {
                ChatRoomScreen(nav, it.arguments?.getInt("pid") ?: 0, it.arguments?.getInt("other") ?: 0)
            }
            composable(
                Route.CHECKOUT,
                listOf(
                    navArgument("pid") { type = NavType.IntType },
                    navArgument("offerId") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("amount") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                CheckoutScreen(
                    nav,
                    it.arguments?.getInt("pid") ?: 0,
                    it.arguments?.getInt("offerId") ?: -1,
                    it.arguments?.getLong("amount") ?: -1L,
                )
            }
            composable(Route.ORDER, listOf(navArgument("id") { type = NavType.IntType })) {
                OrderDetailScreen(nav, it.arguments?.getInt("id") ?: 0)
            }
            composable(Route.DISPUTES) { MyDisputesScreen(nav) }
            composable(Route.ADMIN) { AdminScreen(nav) }
        }
    }
}
