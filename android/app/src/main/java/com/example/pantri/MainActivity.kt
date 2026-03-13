package com.example.pantri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pantri.api.Cache
import com.example.pantri.ui.screens.DashboardScreen
import com.example.pantri.ui.screens.HistoryScreen
import com.example.pantri.ui.screens.ScanScreen
import com.example.pantri.ui.screens.StatsScreen
import com.example.pantri.ui.screens.WeightScreen
import com.example.pantri.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cache.init(applicationContext)
        val startRoute = intent?.getStringExtra("navigate_to") ?: "dashboard"
        enableEdgeToEdge()
        setContent {
            PantriTheme {
                PantriApp(startRoute = startRoute)
            }
        }
    }
}

@Composable
fun PantriApp(startRoute: String = "dashboard") {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color)
                val items = listOf(
                    NavItem("dashboard", "Today", Icons.Default.Home, Mint),
                    NavItem("history", "History", Icons.Default.DateRange, Peach),
                    NavItem("scan", "Scan", Icons.Default.Search, CostCyan),
                    NavItem("weight", "Weight", Icons.Default.Favorite, WeightPurple),
                    NavItem("stats", "Stats", Icons.Default.Info, SkyBlue),
                )
                items.forEach { item ->
                    val selected = currentRoute?.destination?.route == item.route
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label, tint = if (selected) item.color else item.color.copy(alpha = 0.4f)) },
                        label = { Text(item.label, color = if (selected) item.color else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)) },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = item.color.copy(alpha = 0.12f)),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo("dashboard") { inclusive = item.route == "dashboard" }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") { DashboardScreen() }
            composable("history") { HistoryScreen() }
            composable("scan") { ScanScreen() }
            composable("weight") { WeightScreen() }
            composable("stats") { StatsScreen() }
        }
    }
}
