package com.zenith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.zenith.ui.MainViewModel
import com.zenith.ui.navigation.Screen
import com.zenith.ui.screens.*
import com.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        try {
            // We no longer auto-trigger scans here to prevent startup crashes.
            // Scans can be triggered manually or via periodic background tasks.
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ZenithTheme {
                MainScreen(viewModel, onCheckPermissions = { checkAndRequestPermissions() })
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter { 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            // Already have permissions. We don't auto-scan on every launch to save resources.
        }
    }
}

data class DrawerItem(val screen: Screen, val icon: ImageVector, val comingSoon: Boolean = false)
data class DrawerSection(val title: String, val items: List<DrawerItem>)

val drawerSections = listOf(
    DrawerSection("", listOf(
        DrawerItem(Screen.Home, Icons.Filled.Home),
        DrawerItem(Screen.Profile, Icons.Filled.Person)
    )),
    DrawerSection("Intelligence", listOf(
        DrawerItem(Screen.FinanceDashboard, Icons.Filled.AccountBalanceWallet),
        DrawerItem(Screen.Analytics, Icons.Filled.BarChart),
        DrawerItem(Screen.AiChat, Icons.Filled.Psychology)
    )),
    DrawerSection("Studio & Storage", listOf(
        DrawerItem(Screen.WhatsAppCleaner, Icons.Filled.Storage),
        DrawerItem(Screen.MediaFilter, Icons.Filled.AutoDelete),
        DrawerItem(Screen.SocialStudio, Icons.Filled.AutoAwesome)
    )),
    DrawerSection("System", listOf(
        DrawerItem(Screen.Settings, Icons.Filled.Settings)
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onCheckPermissions: () -> Unit) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Screen.Home.route

    LaunchedEffect(Unit) {
        delay(1000) // Warm-up delay to let UI render
        onCheckPermissions()
    }

    val currentTitle = drawerSections.flatMap { it.items }.find { it.screen.route == currentRoute }?.screen?.title ?: "Zenith"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(currentRoute = currentRoute, onNavigate = { route ->
                scope.launch { drawerState.close() }
                navController.navigate(route) {
                    popUpTo(Screen.Home.route)
                    launchSingleTop = true
                }
            })
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { innerPadding ->
            NavHost(navController = navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
                composable(Screen.Home.route) {
                    HomeScreen(viewModel, onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route)
                            launchSingleTop = true
                        }
                    })
                }
                composable(Screen.FinanceDashboard.route) { FinanceDashboardScreen(viewModel, onNavigate = { route -> navController.navigate(route) }) }
                composable(Screen.Transactions.route) { TransactionsScreen(viewModel, onNavigate = { route -> navController.navigate(route) }) }
                composable(Screen.Analytics.route) { AnalyticsScreen(viewModel) }
                composable(
                    Screen.TransactionDetail.route,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    TransactionDetailScreen(id, viewModel, onBack = { navController.popBackStack() })
                }
                composable(Screen.WhatsAppCleaner.route) { WhatsAppCleanerScreen(viewModel, onNavigate = { route -> navController.navigate(route) }) }
                composable(
                    Screen.MediaDetail.route,
                    arguments = listOf(navArgument("label") { type = NavType.StringType })
                ) { backStackEntry ->
                    val label = backStackEntry.arguments?.getString("label") ?: ""
                    MediaDetailScreen(label, viewModel)
                }
                composable(Screen.SocialStudio.route) { SocialStudioScreen(viewModel) }
                composable(Screen.AiChat.route) { AiChatScreen(viewModel) }
                composable(Screen.MediaFilter.route) { MediaFilterScreen(viewModel) }
                composable(Screen.Profile.route) { ProfileScreen(viewModel) }
                composable(Screen.Settings.route) { SettingsScreen(viewModel, onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
fun DrawerContent(currentRoute: String, onNavigate: (String) -> Unit) {
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(24.dp)) {
                Column {
                    Text("Zenith", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Your personal data hub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(8.dp))
            drawerSections.forEach { section ->
                if (section.title.isNotEmpty()) {
                    Text(section.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp))
                }
                section.items.forEach { item ->
                    DrawerNavItem(item = item, isSelected = currentRoute == item.screen.route, onClick = { onNavigate(item.screen.route) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DrawerNavItem(item: DrawerItem, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).background(containerColor, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = item.icon, contentDescription = item.screen.title, tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(item.screen.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = contentColor, modifier = Modifier.weight(1f))
        if (item.comingSoon) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}
