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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    APPS("apps", "APPS", Icons.Default.Apps),
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
    val isLargeTablet = cfg.screenWidthDp >= 840 && cfg.screenHeightDp >= 560
    val useLeftRail = isLandscape || cfg.screenWidthDp >= 600
    val bottomSafeInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

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

    // Leave APPS route automatically if PDA mode is turned off
    LaunchedEffect(settings.pdaModeEnabled, currentRoute) {
        if (!settings.pdaModeEnabled && currentRoute == PdaRoute.APPS.route) {
            navigateTo(PdaRoute.MAP)
        }
    }

    val tabletRailTabs = remember(settings.meshEnabled, settings.pdaModeEnabled) {
        buildList {
            add(PdaRoute.MAP)
            add(PdaRoute.OBJECTS)
            if (settings.meshEnabled) add(PdaRoute.MESH)
            add(PdaRoute.BASE)
            add(PdaRoute.ARCHIVE)
            if (settings.pdaModeEnabled) add(PdaRoute.APPS)
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            val topInsets = if (settings.pdaModeEnabled) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            }
            val barHeight = if (isLandscape && !isLargeTablet) 44.dp else 52.dp
            Surface(
                color = UrbexColors.Surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(topInsets)
                            .height(barHeight)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .urbexBounceClick(0.97f) { navigateTo(PdaRoute.MAP) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "Urbex-T",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLandscape && !isLargeTablet) 16.sp else 18.sp,
                                color = UrbexColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (settings.pdaModeEnabled) {
                                Text(
                                    text = "PDA",
                                    color = UrbexColors.Amber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(UrbexColors.Amber.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                        .border(1.dp, UrbexColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Live PDA telemetry chip (clock · battery% · orientation in landscape)
                            Row(
                                modifier = Modifier
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                                if (settings.pdaModeEnabled && isLandscape) {
                                    Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                                    Text(
                                        text = settings.pdaOrientation.label,
                                        color = UrbexColors.Amber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Primary + Точка button matching Web TopBar Zone 3
                            Row(
                                modifier = Modifier
                                    .background(UrbexColors.Green, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.95f) { showQuickCreateDialog = true }
                                    .padding(
                                        horizontal = if (isLandscape && !isLargeTablet) 10.dp else 11.dp,
                                        vertical = if (isLandscape && !isLargeTablet) 6.dp else 7.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить точку",
                                    tint = UrbexColors.Background,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Точка",
                                    color = UrbexColors.Background,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = UrbexColors.Border, thickness = 1.dp)
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (useLeftRail) {
                val railWidth = if (isLargeTablet) 80.dp else 74.dp
                Surface(
                    color = UrbexColors.Surface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        .width(railWidth)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 6.dp,
                                end = 6.dp,
                                top = 6.dp,
                                bottom = 6.dp + bottomSafeInset
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLargeTablet) {
                            tabletRailTabs.forEach { tab ->
                                val isApps = tab == PdaRoute.APPS
                                TacticalRailButton(
                                    label = tab.label,
                                    icon = tab.icon,
                                    selected = currentRoute == tab.route,
                                    accentColor = if (isApps) UrbexColors.Amber else UrbexColors.GreenBright,
                                    pillBg = if (isApps) UrbexColors.Amber.copy(alpha = 0.16f) else UrbexColors.GreenSoft,
                                    onClick = { navigateTo(tab) }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(1.dp)
                                    .padding(vertical = 2.dp)
                                    .background(UrbexColors.Border)
                            )
                            listOf(PdaRoute.PROFILE, PdaRoute.SETTINGS).forEach { tab ->
                                TacticalRailButton(
                                    label = tab.label,
                                    icon = tab.icon,
                                    selected = currentRoute == tab.route,
                                    accentColor = UrbexColors.Amber,
                                    pillBg = UrbexColors.Amber.copy(alpha = 0.16f),
                                    onClick = { navigateTo(tab) }
                                )
                            }
                        } else {
                            // Compact Phone Landscape Control Rail (matches Web Landscape Rail 1:1)
                            listOf(PdaRoute.MAP, PdaRoute.OBJECTS).forEach { tab ->
                                TacticalRailButton(
                                    label = tab.label,
                                    icon = tab.icon,
                                    selected = currentRoute == tab.route,
                                    accentColor = UrbexColors.GreenBright,
                                    pillBg = UrbexColors.GreenSoft,
                                    onClick = { navigateTo(tab) }
                                )
                            }

                            AnimatedVisibility(
                                visible = settings.meshEnabled,
                                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                TacticalRailButton(
                                    label = PdaRoute.MESH.label,
                                    icon = PdaRoute.MESH.icon,
                                    selected = currentRoute == PdaRoute.MESH.route,
                                    accentColor = UrbexColors.GreenBright,
                                    pillBg = UrbexColors.GreenSoft,
                                    onClick = { navigateTo(PdaRoute.MESH) }
                                )
                            }

                            TacticalRailButton(
                                label = PdaRoute.ARCHIVE.label,
                                icon = PdaRoute.ARCHIVE.icon,
                                selected = currentRoute == PdaRoute.ARCHIVE.route,
                                accentColor = UrbexColors.GreenBright,
                                pillBg = UrbexColors.GreenSoft,
                                onClick = { navigateTo(PdaRoute.ARCHIVE) }
                            )

                            TacticalRailButton(
                                label = PdaRoute.MENU.label,
                                icon = PdaRoute.MENU.icon,
                                selected = isMenuRouteActive,
                                accentColor = UrbexColors.Amber,
                                pillBg = UrbexColors.Amber.copy(alpha = 0.16f),
                                onClick = { navigateTo(PdaRoute.MENU) }
                            )

                            // 5th control panel button (Apps Menu) — ONLY in PDA Mode
                            AnimatedVisibility(
                                visible = settings.pdaModeEnabled,
                                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                TacticalRailButton(
                                    label = PdaRoute.APPS.label,
                                    icon = PdaRoute.APPS.icon,
                                    selected = currentRoute == PdaRoute.APPS.route,
                                    accentColor = UrbexColors.GreenBright,
                                    pillBg = UrbexColors.GreenSoft,
                                    onClick = { navigateTo(PdaRoute.APPS) }
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(UrbexColors.Border)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
            ) {
                val dockClearance = if (!useLeftRail) 88.dp + bottomSafeInset else 12.dp + bottomSafeInset
                val mapBottomOverlayPadding = if (!useLeftRail) 92.dp + bottomSafeInset else 12.dp + bottomSafeInset

                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = !isLargeTablet && menuSubLabel != null,
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(UrbexColors.Surface)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
                            bottomOverlayPadding = mapBottomOverlayPadding
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
                            composable(PdaRoute.APPS.route) {
                                PdaAppsScreen(
                                    settings = settings,
                                    container = container,
                                    onNavigate = navigateTo,
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
                if (!useLeftRail) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 12.dp + bottomSafeInset
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 440.dp)
                                .fillMaxWidth()
                                .height(62.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = UrbexColors.Surface.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, UrbexColors.Border),
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 5.dp),
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
                                        modifier = Modifier.width(if (settings.pdaModeEnabled) 54.dp else 64.dp)
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

                                // 5th button in control dock (Apps Menu) — ONLY in PDA Mode
                                AnimatedVisibility(
                                    visible = settings.pdaModeEnabled,
                                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                                ) {
                                    FloatingDockItem(
                                        label = PdaRoute.APPS.label,
                                        icon = PdaRoute.APPS.icon,
                                        selected = currentRoute == PdaRoute.APPS.route,
                                        accentColor = UrbexColors.GreenBright,
                                        pillBg = UrbexColors.GreenSoft,
                                        onClick = { navigateTo(PdaRoute.APPS) },
                                        modifier = Modifier.width(if (settings.meshEnabled) 54.dp else 62.dp)
                                    )
                                }
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
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    val objects by container.objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val archives by container.archiveRepository.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by container.baseRepository.observeArticles().collectAsStateWithLifecycle(initialValue = emptyList())
    val meshMessages by container.meshRepository.observeAllMessages().collectAsStateWithLifecycle(initialValue = emptyList())

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
            if (settings.pdaModeEnabled) {
                add(Triple(PdaRoute.APPS, "Приложения PDA", "Установленные программы и система"))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isLandscape) 14.dp else 16.dp,
                top = if (isLandscape) 12.dp else 16.dp,
                end = if (isLandscape) 14.dp else 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 16.dp)
    ) {
        // Operator Banner matching Web MenuView.tsx (side-by-side stats in landscape)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Роль: ${profile.role.label}",
                            color = UrbexColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuStatChip("Объекты", objects.size.toString(), Modifier.weight(1f))
                        MenuStatChip("Архив", archives.size.toString(), Modifier.weight(1f))
                        MenuStatChip("База", articles.size.toString(), Modifier.weight(1f))
                    }
                }
            } else {
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
        }

        // System & Profile Cards matching Web MenuView.tsx (2-column rows in landscape)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "СИСТЕМА И ПРОФИЛЬ",
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            val sysCols = if (isLandscape) 2 else 1
            systemCards.chunked(sysCols).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCards.forEach { (route, titlePair, meta) ->
                        val (title, code) = titlePair
                        val description = when (route) {
                            PdaRoute.SETTINGS -> "Профиль GPS, слой карты, Meshtastic, кэш и резерв .urbexdb."
                            PdaRoute.PROFILE -> "Позывной, HEX-идентификатор узла, роль и сводка."
                            else -> "Автономные полевые инструкции, ориентирование и заметки."
                        }
                        val accent = if (route == PdaRoute.SETTINGS) UrbexColors.Amber else UrbexColors.GreenBright
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.97f) { onNavigate(route) },
                            color = UrbexColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, UrbexColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(13.dp),
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
                                            .size(38.dp)
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(route.icon, contentDescription = title, tint = accent, modifier = Modifier.size(19.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text(code, color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                        Text(description, color = UrbexColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(meta, color = UrbexColors.GreenBright, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    repeat(sysCols - rowCards.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Workspace Quick Navigation Cards matching Web MenuView.tsx (2-column rows in landscape)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "РАБОЧИЕ РАЗДЕЛЫ",
                color = UrbexColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            val wsCols = if (isLandscape) 2 else 1
            workspaceCards.chunked(wsCols).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCards.forEach { (route, title, meta) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.97f) { onNavigate(route) },
                            color = UrbexColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, UrbexColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 13.dp, vertical = 11.dp),
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
                                            .size(34.dp)
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(route.icon, contentDescription = title, tint = UrbexColors.TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text(title, color = UrbexColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(meta, color = UrbexColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    repeat(wsCols - rowCards.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdaAppsScreen(
    settings: AppSettings,
    container: AppContainer,
    onNavigate: (PdaRoute) -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var appQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
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

    val appGridCols = if (isLandscape) 6 else 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isLandscape) 14.dp else 16.dp,
                top = if (isLandscape) 12.dp else 16.dp,
                end = if (isLandscape) 14.dp else 16.dp,
                bottom = 16.dp + bottomContentPadding
            ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 14.dp)
    ) {
        // PDA Mode Header, Orientation Switcher & OS System Shortcuts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(16.dp))
                    Text(
                        text = "ПРИЛОЖЕНИЯ И ЛАУНЧЕР PDA",
                        color = UrbexColors.TextPrimary,
                        fontSize = 13.sp,
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

        // Quick PDA Internal Modules Strip (1:1 parity with Web AppsView)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                PdaRoute.MAP to "Карта",
                PdaRoute.OBJECTS to "Объекты",
                PdaRoute.ARCHIVE to "Архив",
                PdaRoute.BASE to "База знаний",
                PdaRoute.PROFILE to "Профиль",
                PdaRoute.SETTINGS to "Настройки"
            ).forEach { (route, shortLabel) ->
                Row(
                    modifier = Modifier
                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .urbexBounceClick(0.95f) { onNavigate(route) }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(route.icon, contentDescription = shortLabel, tint = UrbexColors.GreenBright, modifier = Modifier.size(15.dp))
                    Text(shortLabel, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Installed Android Applications Search & Responsive Grid
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
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = UrbexColors.TextDim) },
            trailingIcon = {
                if (appQuery.isNotEmpty()) {
                    IconButton(onClick = { appQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить поиск", tint = UrbexColors.TextSecondary)
                    }
                }
            },
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
                filteredApps.chunked(appGridCols).forEach { rowApps ->
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
                        repeat(appGridCols - rowApps.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalRailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    pillBg: Color,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) accentColor else UrbexColors.TextSecondary,
        animationSpec = tween(160),
        label = "railBtnColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) pillBg else Color.Transparent,
        animationSpec = tween(180),
        label = "railBtnBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = tween(180),
        label = "railBtnBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .urbexBounceClick(0.93f, onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
