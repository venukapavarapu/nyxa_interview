package com.example.nyxa_interview.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nyxa_interview.presentation.auth.login.LoginRoute
import com.example.nyxa_interview.presentation.box.BoxRoute
import com.example.nyxa_interview.presentation.checkout.CheckoutRoute
import com.example.nyxa_interview.presentation.spin.SpinRoute
import com.example.nyxa_interview.presentation.store.detail.PRODUCT_ID_ARG
import com.example.nyxa_interview.presentation.store.detail.ProductDetailRoute
import com.example.nyxa_interview.presentation.store.grid.StoreGridRoute
import com.example.nyxa_interview.presentation.wallet.LedgerRoute
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object Routes {
    const val LOGIN = "login"
    const val STORE = "store"
    const val SPIN = "spin"
    const val BOX = "box"
    const val WALLET = "wallet"
    const val PRODUCT_DETAIL = "product/{$PRODUCT_ID_ARG}"
    const val CART = "cart"

    // Product ids look like "gid://shopify/Product/0" — URL-encode before putting them in a
    // path segment, since NavHost's default String NavType splits path segments on '/'.
    fun productDetail(id: String) = "product/${URLEncoder.encode(id, StandardCharsets.UTF_8.name())}"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.STORE, "Store", Icons.Filled.Inventory2),
    BottomTab(Routes.SPIN, "Spin", Icons.Filled.Casino),
    BottomTab(Routes.BOX, "Boxes", Icons.Filled.Redeem),
    BottomTab(Routes.WALLET, "Wallet", Icons.Filled.Wallet),
)

@Composable
fun NyxaNavHost(isLoggedIn: Boolean, showDebugControls: Boolean = true) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            val onTabRoute = bottomTabs.any { tab ->
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            }
            if (onTabRoute) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) Routes.STORE else Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginRoute(onLoggedIn = {
                    navController.navigate(Routes.STORE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }

            composable(Routes.STORE) {
                StoreGridRoute(
                    onOpenProduct = { id -> navController.navigate(Routes.productDetail(id)) },
                    onOpenCart = { navController.navigate(Routes.CART) },
                )
            }

            composable(route = Routes.PRODUCT_DETAIL) {
                ProductDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenCart = { navController.navigate(Routes.CART) },
                )
            }

            composable(Routes.CART) {
                CheckoutRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.SPIN) {
                SpinRoute(showDebugControls = showDebugControls)
            }

            composable(Routes.BOX) {
                BoxRoute()
            }

            composable(Routes.WALLET) {
                LedgerRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
