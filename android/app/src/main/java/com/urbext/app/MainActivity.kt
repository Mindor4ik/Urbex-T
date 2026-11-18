package com.urbext.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.ui.ObjectEditorDialog
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexTheme
import com.urbext.core.ui.urbexBounceClick
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.UserProfile
import com.urbext.feature.archive.ArchiveScreen
import com.urbext.feature.base.BaseScreen
import com.urbext.feature.map.MapScreen
import com.urbext.feature.mesh.MeshScreen
import com.urbext.feature.objects.ObjectsScreen
import com.urbext.feature.profile.ProfileScreen
import com.urbext.feature.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PdaRoute(val route: String, val label: String, val icon: ImageVector) {
    MAP("map", "MAP", Icons.Default.Map),
    OBJECTS("objects", "OBJECTS", Icons.Default.Business),
    MESH("mesh", "MESH", Icons.Default.Radio),
    BASE("base", "BASE", Icons.Default.Book),
    ARCHIVE("archive", "ARCHIVE", Icons.Default.Archive),
    PROFILE("profile", "PROFILE", Icons.Default.Person),
    SETTINGS("settings", "SETTINGS", Icons.Default.Settings),
    MENU("menu", "МЕНЮ", Icons.Default.Menu)
}

private data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)

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
    var zoneEditingObjectId by remember { mutableStateOf<String?>(null) }
    var showQuickCreateDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val settings by container.settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val profile by container.profileRepository.observeProfile()
        .collectAsStateWithLifecycle(
            initialValue = UserProfile(
                callsign = "Сокол-1",
                nodeId = "!a48f20c1",
                role = OperatorRole.SCOUT,
                emergencyContact = "",
                bio = "",
                updatedAt = 0L
            )
        )

    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val isTabletOrWide = cfg.screenWidthDp >= 720 || (isLandscape && cfg.screenWidthDp >= 580)

    var clockText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var batteryPct by remember {
        mutableIntStateOf(
            runCatching {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
            }.getOrDefault(100)
        )
    }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(15_000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                }
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryPct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // Apply requested PDA screen orientation whenever orientation setting changes
    LaunchedEffect(settings.pdaOrientation) {
        val act = activity ?: return@LaunchedEffect
        val target = when (settings.pdaOrientation) {
            PdaOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PdaOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PdaOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (act.requestedOrientation != target) {
            act.requestedOrientation = target
        }
    }

    // Toggle Home Launcher alias dynamically when PDA mode is enabled/disabled
    LaunchedEffect(settings.pdaModeEnabled) {
        runCatching {
            val component = ComponentName(context, "com.urbext.app.PdaHomeLauncher")
            val desiredState = if (settings.pdaModeEnabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (context.packageManager.getComponentEnabledSetting(component) != desiredState) {
                context.packageManager.setComponentEnabledSetting(
                    component,
                    desiredState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    // Fullscreen immersive status bar in PDA Mode so TopAppBar acts as the PDA system bar
    DisposableEffect(activity, view, settings.pdaModeEnabled) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (settings.pdaModeEnabled) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            if (window != null && settings.pdaModeEnabled) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    DisposableEffect(view, settings.keepScreenAwake, settings.pdaModeEnabled, currentRoute) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn =
            (settings.keepScreenAwake || settings.pdaModeEnabled) && currentRoute == PdaRoute.MAP.route
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    // When Urbex-T is used as a PDA Home screen, block accidental exit on root MAP screen
    BackHandler(enabled = settings.pdaModeEnabled && currentRoute == PdaRoute.MAP.route) {
        // Stay in PDA terminal root
    }

    val navigateTo: (PdaRoute) -> Unit = { target ->
        if (target != PdaRoute.MAP) {
            zoneEditingObjectId = null
        }
        navController.navigate(target.route) {
            popUpTo(PdaRoute.MAP.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val primaryRailTabs = remember(settings.meshEnabled) {
        buildList {
            add(PdaRoute.MAP)
            add(PdaRoute.OBJECTS)
            if (settings.meshEnabled) add(PdaRoute.MESH)
            add(PdaRoute.BASE)
            add(PdaRoute.ARCHIVE)
        }
    }

    val isMenuRouteActive = currentRoute == PdaRoute.MENU.route ||
        currentRoute == PdaRoute.BASE.route ||
        currentRoute == PdaRoute.PROFILE.route ||
        currentRoute == PdaRoute.SETTINGS.route
    val menuSubLabel = when (currentRoute) {
        PdaRoute.SETTINGS.route -> "Настройки"
        PdaRoute.PROFILE.route -> "Профиль оператора"
        PdaRoute.BASE.route -> "База знаний"
        else -> null
    }

    Scaffold(
        containerColor = UrbexColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Urbex-T",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = UrbexColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compact live PDA telemetry pill (clock, battery %, GPS/Mesh signal)
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (settings.meshEnabled) UrbexColors.Amber else UrbexColors.GreenBright)
                            )
                            Text(
                                text = clockText,
                                color = UrbexColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                            Text(
                                text = "$batteryPct%",
                                color = if (batteryPct <= 20) UrbexColors.Danger else UrbexColors.GreenBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Primary + Точка action matching Web TopBar Zone 3
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Green, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.95f) { showQuickCreateDialog = true }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить точку",
                                tint = UrbexColors.Background,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Точка",
                                color = UrbexColors.Background,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                    primaryRailTabs.forEach { tab ->
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
                        } else {
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontSize = 10.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(
                        color = UrbexColors.Border,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    listOf(PdaRoute.PROFILE, PdaRoute.SETTINGS).forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                val dockClearance = if (!isTabletOrWide) 88.dp else 0.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = !isTabletOrWide && menuSubLabel != null,
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.Elevated)
                                        .urbexBounceClick(0.95f) { navigateTo(PdaRoute.MENU) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад в меню",
                                        tint = UrbexColors.Amber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Назад в меню", color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Text(menuSubLabel.orEmpty(), color = UrbexColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(color = UrbexColors.Border)
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val isMapRoute = currentRoute == PdaRoute.MAP.route

                        // Persistent MapScreen prevents MapLibre tile & camera reload across tab switches
                        MapScreen(
                            objectRepository = container.objectRepository,
                            settingsRepository = container.settingsRepository,
                            profileRepository = container.profileRepository,
                            onOpenObject = { id ->
                                selectedObjectId = id
                                navigateTo(PdaRoute.OBJECTS)
                            },
                            zoneEditingObjectId = zoneEditingObjectId,
                            onRequestZoneEdit = { id ->
                                zoneEditingObjectId = id
                                navigateTo(PdaRoute.MAP)
                            },
                            onClearZoneEdit = {
                                zoneEditingObjectId = null
                            },
                            isActive = isMapRoute,
                            bottomOverlayPadding = if (!isTabletOrWide) 92.dp else 12.dp
                        )

                        // Opaque background scrim locks at 1f on all non-map routes so crossfades never leak the map behind them
                        val nonMapScrimAlpha by animateFloatAsState(
                            targetValue = if (isMapRoute) 0f else 1f,
                            animationSpec = tween(160),
                            label = "nonMapScrimAlpha"
                        )
                        if (nonMapScrimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(UrbexColors.Background.copy(alpha = nonMapScrimAlpha))
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = PdaRoute.MAP.route,
                            enterTransition = {
                                fadeIn(animationSpec = tween(220)) +
                                    slideInVertically(animationSpec = tween(220)) { it / 28 }
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(150)) +
                                    slideOutVertically(animationSpec = tween(150)) { -it / 40 }
                            }
                        ) {
                            composable(PdaRoute.MAP.route) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                            composable(PdaRoute.OBJECTS.route) {
                                ObjectsScreen(
                                    objectRepository = container.objectRepository,
                                    settingsRepository = container.settingsRepository,
                                    profileRepository = container.profileRepository,
                                    initialSelectedId = selectedObjectId,
                                    onEditZoneOnMap = { obj ->
                                        zoneEditingObjectId = obj.id
                                        navigateTo(PdaRoute.MAP)
                                    },
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.MESH.route) {
                                MeshScreen(
                                    channels = SeedData.channels,
                                    meshRepository = container.meshRepository,
                                    profileRepository = container.profileRepository,
                                    settingsRepository = container.settingsRepository,
                                    onOpenSettings = { navigateTo(PdaRoute.SETTINGS) },
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.BASE.route) {
                                BaseScreen(
                                    baseRepository = container.baseRepository,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.ARCHIVE.route) {
                                ArchiveScreen(
                                    archiveRepository = container.archiveRepository,
                                    objectRepository = container.objectRepository,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.PROFILE.route) {
                                ProfileScreen(
                                    profileRepository = container.profileRepository,
                                    objectRepository = container.objectRepository,
                                    archiveRepository = container.archiveRepository,
                                    meshRepository = container.meshRepository,
                                    baseRepository = container.baseRepository,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.SETTINGS.route) {
                                SettingsScreen(
                                    settingsRepository = container.settingsRepository,
                                    backupRepository = container.backupRepository,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.MENU.route) {
                                FullscreenMenuScreen(
                                    settings = settings,
                                    profile = profile,
                                    container = container,
                                    onNavigate = navigateTo,
                                    bottomContentPadding = dockClearance
                                )
                            }
                        }
                    }
                }

                // Floating Cloud Bottom Navigation (Portrait Phone)
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
                            color = UrbexColors.Surface.copy(alpha = 0.88f),
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
                                    selected = isMenuRouteActive,
                                    accentColor = UrbexColors.Amber,
                                    pillBg = UrbexColors.Amber.copy(alpha = 0.16f),
                                    onClick = { navigateTo(PdaRoute.MENU) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickCreateDialog) {
        val locationClient = remember(context) { AdaptiveLocationClient(context) }
        val initialFix = remember(showQuickCreateDialog) { locationClient.getLastKnownFixOrDefault() }
        ObjectEditorDialog(
            initialLat = initialFix.lat,
            initialLon = initialFix.lon,
            initialElevationM = initialFix.altitudeM ?: 140,
            defaultZoneRadiusM = settings.defaultZoneRadiusM,
            authorNodeId = profile.nodeId,
            editingObject = null,
            onDismiss = { showQuickCreateDialog = false },
            onSave = { newObj ->
                scope.launch {
                    container.objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    showQuickCreateDialog = false
                }
            },
            onOpenZoneEditor = { newObj ->
                scope.launch {
                    container.objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    zoneEditingObjectId = newObj.id
                    showQuickCreateDialog = false
                    navigateTo(PdaRoute.MAP)
                }
            }
        )
    }
}

@Composable
private fun FullscreenMenuScreen(
    settings: AppSettings,
    profile: UserProfile,
    container: AppContainer,
    onNavigate: (PdaRoute) -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val objects by container.objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val archives by container.archiveRepository.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by container.baseRepository.observeArticles().collectAsStateWithLifecycle(initialValue = emptyList())
    val meshMessages by container.meshRepository.observeAllMessages().collectAsStateWithLifecycle(initialValue = emptyList())

    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var appQuery by remember { mutableStateOf("") }

    LaunchedEffect(settings.pdaModeEnabled) {
        if (!settings.pdaModeEnabled) {
            installedApps = emptyList()
            return@LaunchedEffect
        }
        installedApps = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
                resolveInfos
                    .filter { it.activityInfo.packageName != context.packageName }
                    .distinctBy { it.activityInfo.packageName }
                    .map { info ->
                        val label = info.loadLabel(pm).toString()
                        val bmp = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                        InstalledAppEntry(
                            packageName = info.activityInfo.packageName,
                            label = label,
                            icon = bmp
                        )
                    }
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }
            }.getOrDefault(emptyList())
        }
    }

    val filteredApps = remember(installedApps, appQuery) {
        val q = appQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) installedApps else installedApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
    }

    val layerLabel = settings.mapLayer.label
    val gpsLabel = settings.powerProfile.label

    val systemCards = remember(settings, profile, articles.size) {
        listOf(
            Triple(
                PdaRoute.SETTINGS,
                "Настройки" to "SETTINGS",
                "GPS: $gpsLabel · $layerLabel · Mesh ${if (settings.meshEnabled) "ВКЛ" else "ВЫКЛ"}"
            ),
            Triple(
                PdaRoute.PROFILE,
                "Профиль оператора" to "PROFILE",
                "${profile.callsign} · ${profile.nodeId}"
            ),
            Triple(
                PdaRoute.BASE,
                "База знаний" to "BASE",
                "${articles.size} материалов офлайн"
            )
        )
    }

    val workspaceCards = remember(settings, objects.size, archives.size, meshMessages.size) {
        buildList {
            add(Triple(PdaRoute.MAP, "Спутниковая карта", "${settings.offlineSectors.size} кэш-секторов"))
            add(Triple(PdaRoute.OBJECTS, "Реестр объектов", "${objects.size} точек"))
            add(Triple(PdaRoute.ARCHIVE, "Полевой архив", "${archives.size} файлов"))
            if (settings.meshEnabled) {
                add(Triple(PdaRoute.MESH, "Связь Meshtastic", "${meshMessages.size} пакетов"))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Operator Banner matching Web MenuView.tsx
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(16.dp))
                    Text("Автономный терминал PDA", color = UrbexColors.TextSecondary, fontSize = 12.sp)
                    Text("·", color = UrbexColors.TextDim, fontSize = 12.sp)
                    Text(profile.nodeId, color = UrbexColors.Amber, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text(
                    text = profile.callsign,
                    color = UrbexColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Роль: ${profile.role.label}",
                    color = UrbexColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuStatChip("Объекты", objects.size.toString(), Modifier.weight(1f))
                MenuStatChip("Архив", archives.size.toString(), Modifier.weight(1f))
                MenuStatChip("База", articles.size.toString(), Modifier.weight(1f))
            }
        }

        // PDA Device Launcher Controls & Installed Apps Grid when PDA Mode is active
        if (settings.pdaModeEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                        Text(
                            text = "ТЕРМИНАЛ PDA",
                            color = UrbexColors.Amber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = settings.pdaOrientation.label,
                        color = UrbexColors.GreenBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdaOrientation.entries.forEach { orient ->
                            val selected = settings.pdaOrientation == orient
                            Text(
                                text = orient.label,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.96f) {
                                        scope.launch {
                                            container.settingsRepository.updateSettings(
                                                settings.copy(pdaOrientation = orient, updatedAt = System.currentTimeMillis())
                                            )
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickSystemShortcut(
                            icon = Icons.Default.Home,
                            label = "Главный экран",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                }
                            }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Settings,
                            label = "Настройки ОС",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Wifi,
                            label = "Wi-Fi",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                }
                            }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Bluetooth,
                            label = "Bluetooth",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            }
                        )
                    }
                }

                // Device Installed Applications Grid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        text = "ПРИЛОЖЕНИЯ УСТРОЙСТВА (${installedApps.size})",
                        color = UrbexColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    placeholder = { Text("Поиск установленного приложения...", color = UrbexColors.TextDim) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "Приложения не найдены.",
                            color = UrbexColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    } else {
                        filteredApps.chunked(4).forEach { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowApps.forEach { app ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(UrbexColors.Background)
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                                            .urbexBounceClick(0.94f) {
                                                runCatching {
                                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                    if (launchIntent != null) {
                                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(launchIntent)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Text(
                                            text = app.label,
                                            color = UrbexColors.TextPrimary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                repeat(4 - rowApps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // System & Profile Cards matching Web MenuView.tsx
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "СИСТЕМА И ПРОФИЛЬ",
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            systemCards.forEach { (route, titlePair, meta) ->
                val (title, code) = titlePair
                val description = when (route) {
                    PdaRoute.SETTINGS -> "Профиль GPS, слой карты, Meshtastic, кэш и резервное копирование .urbexdb."
                    PdaRoute.PROFILE -> "Позывной, HEX-идентификатор узла, роль в группе и локальная сводка."
                    else -> "Автономные полевые инструкции, ориентирование, безопасность и заметки."
                }
                val accent = if (route == PdaRoute.SETTINGS) UrbexColors.Amber else UrbexColors.GreenBright
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .urbexBounceClick(0.97f) { onNavigate(route) },
                    color = UrbexColors.Surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, UrbexColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(route.icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(title, color = UrbexColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(code, color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                                Text(description, color = UrbexColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                                Text(meta, color = UrbexColors.GreenBright, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = UrbexColors.TextDim
                        )
                    }
                }
            }
        }

        // Workspace Quick Navigation Cards matching Web MenuView.tsx
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "РАБОЧИЕ РАЗДЕЛЫ",
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            workspaceCards.forEach { (route, title, meta) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .urbexBounceClick(0.97f) { onNavigate(route) },
                    color = UrbexColors.Surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, UrbexColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(route.icon, contentDescription = title, tint = UrbexColors.TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(meta, color = UrbexColors.TextDim, fontSize = 12.sp)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = UrbexColors.TextDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = UrbexColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = UrbexColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickSystemShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
            .urbexBounceClick(0.95f, onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = UrbexColors.Amber, modifier = Modifier.size(14.dp))
        Text(label, color = UrbexColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor by animateColorAsState(
        targetValue = if (selected) accentColor else UrbexColors.TextSecondary,
        animationSpec = tween(180),
        label = "dockContentColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) pillBg else Color.Transparent,
        animationSpec = tween(200),
        label = "dockBgColor"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.91f
            selected -> 1.03f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockScale"
    )

    Column(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
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
