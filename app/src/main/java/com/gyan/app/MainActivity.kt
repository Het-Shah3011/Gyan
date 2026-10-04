package com.gyan.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gyan.app.files.FileOrganizer
import com.gyan.app.ui.FilesScreen
import com.gyan.app.ui.MoneyScreen
import com.gyan.app.ui.SettingsScreen
import com.gyan.app.ui.StudyScreen
import com.gyan.app.ui.TodayScreen
import com.gyan.app.ui.TrackersScreen
import com.gyan.app.ui.theme.GyanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var navController: androidx.navigation.NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GyanTheme {
                val nav = rememberNavController()
                navController = nav

                // ---- handle "Share to GYAN" ----
                var sharedUri by remember { mutableStateOf(extractShareUri(intent)) }
                LaunchedEffect(sharedUri) {
                    val uri = sharedUri ?: return@LaunchedEffect
                    FileOrganizer.importFromUri(this@MainActivity, uri)
                    sharedUri = null
                    nav.navigate("files")
                }

                // ---- notification permission (Android 13+) ----
                if (Build.VERSION.SDK_INT >= 33) {
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) {}
                    LaunchedEffect(Unit) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val backStackEntry by nav.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        nav.navigate(item.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = nav,
                        startDestination = "today",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("today") { TodayScreen(nav) }
                        composable("study") { StudyScreen() }
                        composable("money") { MoneyScreen() }
                        composable("track") { TrackersScreen() }
                        composable("files") { FilesScreen() }
                        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = extractShareUri(intent) ?: return
        lifecycleScope.launch {
            FileOrganizer.importFromUri(this@MainActivity, uri)
            if (::navController.isInitialized) navController.navigate("files")
        }
    }

    private fun extractShareUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

private data class NavItem(val label: String, val route: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Today", "today", Icons.Filled.Home),
    NavItem("Study", "study", Icons.Filled.Star),
    NavItem("Money", "money", Icons.Filled.ShoppingCart),
    NavItem("Track", "track", Icons.Filled.DateRange),
    NavItem("Files", "files", Icons.Filled.Folder)
)
