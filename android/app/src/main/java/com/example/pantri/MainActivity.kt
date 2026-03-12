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
import com.example.pantri.ui.screens.StatsScreen
import com.example.pantri.ui.screens.WeightScreen
import com.example.pantri.ui.theme.PantriTheme

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
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Today") },
                    label = { Text("Today") },
                    selected = currentRoute?.destination?.route == "dashboard",
                    onClick = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentRoute?.destination?.route == "history",
                    onClick = {
                        navController.navigate("history") {
                            popUpTo("dashboard")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Weight") },
                    label = { Text("Weight") },
                    selected = currentRoute?.destination?.route == "weight",
                    onClick = {
                        navController.navigate("weight") {
                            popUpTo("dashboard")
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Stats") },
                    label = { Text("Stats") },
                    selected = currentRoute?.destination?.route == "stats",
                    onClick = {
                        navController.navigate("stats") {
                            popUpTo("dashboard")
                        }
                    }
                )
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
            composable("weight") { WeightScreen() }
            composable("stats") { StatsScreen() }
        }
    }
}
