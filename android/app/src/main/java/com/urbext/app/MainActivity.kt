package com.urbext.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexTheme
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.feature.archive.ArchiveScreen
import com.urbext.feature.base.BaseScreen
import com.urbext.feature.map.MapScreen
import com.urbext.feature.mesh.MeshScreen
import com.urbext.feature.objects.ObjectsScreen
import com.urbext.feature.profile.ProfileScreen
import com.urbext.feature.settings.SettingsScreen

enum class PdaRoute(val route: String, val label: String, val icon: ImageVector) {
    MAP("map", "MAP", Icons.Default.Map),
    OBJECTS("objects", "OBJECTS", Icons.Default.Business),
    MESH("mesh", "MESH", Icons.Default.Radio),
    BASE("base", "BASE", Icons.Default.Book),
    ARCHIVE("archive", "ARCHIVE", Icons.Default.Archive),
    PROFILE("profile", "PROFILE", Icons.Default.Person),
    SETTINGS("settings", "SETTINGS", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UrbexApplication).container
        setContent {
            UrbexTheme {
                UrbexPdaApp(container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrbexPdaApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: PdaRoute.MAP.route
    var selectedObjectId by remember { mutableStateOf<String?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }

    val settings by container.settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val isTabletOrWide = LocalConfiguration.current.screenWidthDp >= 720

    val navigateTo: (PdaRoute) -> Unit = { target ->
        showMoreSheet = false
        navController.navigate(target.route) {
            popUpTo(PdaRoute.MAP.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val railTabs = remember(settings.meshEnabled) {
        if (settings.meshEnabled) {
            PdaRoute.entries
        } else {
            PdaRoute.entries.filter { it != PdaRoute.MESH }
        }
    }

    val isMenuRouteActive = currentRoute == PdaRoute.BASE.route ||
        currentRoute == PdaRoute.PROFILE.route ||
        currentRoute == PdaRoute.SETTINGS.route

    Scaffold(
        containerColor = UrbexColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Urbex-T",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = UrbexColors.TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UrbexColors.Surface)
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isTabletOrWide) {
                NavigationRail(containerColor = UrbexColors.Surface) {
                    PdaRoute.entries.forEach { tab ->
                        if (tab == PdaRoute.MESH) {
                            AnimatedVisibility(
                                visible = settings.meshEnabled,
                                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                NavigationRailItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { navigateTo(tab) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, fontSize = 10.sp) }
                                )
                            }
                        } else if (tab in railTabs) {
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                val contentBottomClearance = if (!isTabletOrWide && currentRoute != PdaRoute.MAP.route) 88.dp else 0.dp
                Box(modifier = Modifier.fillMaxSize().padding(bottom = contentBottomClearance)) {
                    NavHost(
                        navController = navController,
                        startDestination = PdaRoute.MAP.route,
                        enterTransition = { fadeIn(animationSpec = tween(160)) },
                        exitTransition = { fadeOut(animationSpec = tween(120)) }
                    ) {
                        composable(PdaRoute.MAP.route) {
                            MapScreen(
                                objectRepository = container.objectRepository,
                                settingsRepository = container.settingsRepository,
                                profileRepository = container.profileRepository,
                                onOpenObject = { id ->
                                    selectedObjectId = id
                                    navigateTo(PdaRoute.OBJECTS)
                                }
                            )
                        }
                        composable(PdaRoute.OBJECTS.route) {
                            ObjectsScreen(
                                objectRepository = container.objectRepository,
                                settingsRepository = container.settingsRepository,
                                initialSelectedId = selectedObjectId
                            )
                        }
                        composable(PdaRoute.MESH.route) {
                            MeshScreen(
                                channels = SeedData.channels,
                                meshRepository = container.meshRepository,
                                profileRepository = container.profileRepository,
                                settingsRepository = container.settingsRepository,
                                onOpenSettings = { navigateTo(PdaRoute.SETTINGS) }
                            )
                        }
                        composable(PdaRoute.BASE.route) {
                            BaseScreen(baseRepository = container.baseRepository)
                        }
                        composable(PdaRoute.ARCHIVE.route) {
                            ArchiveScreen(archiveRepository = container.archiveRepository)
                        }
                        composable(PdaRoute.PROFILE.route) {
                            ProfileScreen(
                                profileRepository = container.profileRepository,
                                objectRepository = container.objectRepository,
                                archiveRepository = container.archiveRepository
                            )
                        }
                        composable(PdaRoute.SETTINGS.route) {
                            SettingsScreen(
                                settingsRepository = container.settingsRepository,
                                backupRepository = container.backupRepository
                            )
                        }
                    }
                }

                // Floating Cloud Bottom Navigation (< 720dp)
                if (!isTabletOrWide) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 440.dp)
                                .fillMaxWidth()
                                .height(62.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = UrbexColors.Surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, UrbexColors.Border),
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(PdaRoute.MAP, PdaRoute.OBJECTS).forEach { tab ->
                                    FloatingDockItem(
                                        label = tab.label,
                                        icon = tab.icon,
                                        selected = currentRoute == tab.route,
                                        accentColor = UrbexColors.GreenBright,
                                        pillBg = UrbexColors.GreenSoft,
                                        onClick = { navigateTo(tab) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = settings.meshEnabled,
                                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                                ) {
                                    FloatingDockItem(
                                        label = PdaRoute.MESH.label,
                                        icon = PdaRoute.MESH.icon,
                                        selected = currentRoute == PdaRoute.MESH.route,
                                        accentColor = UrbexColors.GreenBright,
                                        pillBg = UrbexColors.GreenSoft,
                                        onClick = { navigateTo(PdaRoute.MESH) },
                                        modifier = Modifier.width(68.dp)
                                    )
                                }

                                FloatingDockItem(
                                    label = PdaRoute.ARCHIVE.label,
                                    icon = PdaRoute.ARCHIVE.icon,
                                    selected = currentRoute == PdaRoute.ARCHIVE.route,
                                    accentColor = UrbexColors.GreenBright,
                                    pillBg = UrbexColors.GreenSoft,
                                    onClick = { navigateTo(PdaRoute.ARCHIVE) },
                                    modifier = Modifier.weight(1f)
                                )

                                FloatingDockItem(
                                    label = "МЕНЮ",
                                    icon = Icons.Default.Menu,
                                    selected = isMenuRouteActive || showMoreSheet,
                                    accentColor = UrbexColors.Amber,
                                    pillBg = UrbexColors.Amber.copy(alpha = 0.16f),
                                    onClick = { showMoreSheet = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMoreSheet = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Модули PDA", color = UrbexColors.TextPrimary) },
            text = {
                Column {
                    listOf(PdaRoute.BASE, PdaRoute.PROFILE, PdaRoute.SETTINGS).forEach { route ->
                        NavigationDrawerItem(
                            label = { Text(route.label, color = UrbexColors.TextPrimary) },
                            icon = { Icon(route.icon, contentDescription = null, tint = UrbexColors.GreenBright) },
                            selected = currentRoute == route.route,
                            onClick = { navigateTo(route) }
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showMoreSheet = false }) {
                    Text("Закрыть", color = UrbexColors.GreenBright)
                }
            }
        )
    }
}

@Composable
private fun FloatingDockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    pillBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) accentColor else UrbexColors.TextSecondary
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) pillBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
