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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
import com.urbext.core.ui.ObjectEditorDraft
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexTheme
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexGlow
import com.urbext.core.ui.urbexTileEnter
import com.urbext.data.seed.SeedData
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.CloudSyncProvider
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PdaOrientation
import com.urbext.domain.model.SettingsCategory
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import com.urbext.domain.model.WorkspaceState
import com.urbext.feature.archive.ArchiveScreen
import com.urbext.feature.base.BaseScreen
import com.urbext.feature.map.MapFocusRequest
import com.urbext.feature.map.MapPointMoveRequest
import com.urbext.feature.map.MapPointMoveResult
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
    MAP("map", "КАРТА", Icons.Default.Map),
    OBJECTS("objects", "ОБЪЕКТЫ", Icons.Default.Business),
    MESH("mesh", "СВЯЗЬ", Icons.Default.Radio),
    BASE("base", "БАЗА", Icons.Default.Book),
    ARCHIVE("archive", "АРХИВ", Icons.Default.Archive),
    APPS("apps", "ПРИЛ.", Icons.Default.Apps),
    PROFILE("profile", "ПРОФИЛЬ", Icons.Default.Person),
    SETTINGS("settings", "НАСТРОЙКИ", Icons.Default.Settings),
    MENU("menu", "МЕНЮ", Icons.Default.Menu)
}

private data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)

private data class PointMoveSession(
    val request: MapPointMoveRequest,
    val returnTarget: String,
    val draft: ObjectEditorDraft? = null
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

private fun loadInstalledApps(context: Context): List<InstalledAppEntry> {
    return runCatching {
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

@Composable
fun UrbexPdaApp(container: AppContainer) {
    LaunchedEffect(Unit) {
        container.workspaceManager.ensureInitialized()
    }
    val settingsState by container.settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = null)
    val workspaceState by container.workspaceRepository.observeWorkspaces()
        .collectAsStateWithLifecycle(initialValue = WorkspaceState())
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
    var showWelcomeFromSettings by rememberSaveable { mutableStateOf(false) }

    val settings = settingsState
    val topStage = when {
        settings == null || !workspaceState.initialized -> "loading"
        workspaceState.workspaces.isEmpty() || showWelcomeFromSettings -> "welcome"
        else -> "workspace"
    }

    AnimatedContent(
        targetState = topStage,
        transitionSpec = {
            (fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
                (fadeOut(tween(180)) +
                    scaleOut(targetScale = 1.015f, animationSpec = tween(180)))
        },
        label = "UrbexTopStage"
    ) { stage ->
        when (stage) {
            "loading" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(UrbexColors.Background)
                )
            }
            "welcome" -> {
                if (settings != null) {
                    DatabaseWelcomeScreen(
                        workspaceRepository = container.workspaceRepository,
                        settingsRepository = container.settingsRepository,
                        profileRepository = container.profileRepository,
                        currentSettings = settings,
                        currentProfile = profile,
                        isReopenedFromSettings = workspaceState.workspaces.isNotEmpty(),
                        onClose = if (workspaceState.workspaces.isNotEmpty()) {
                            { showWelcomeFromSettings = false }
                        } else {
                            null
                        }
                    )
                }
            }
            else -> {
                if (settings != null) {
                    UrbexMainWorkspace(
                        container = container,
                        settings = settings,
                        profile = profile,
                        onOpenWelcomeScreen = { showWelcomeFromSettings = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun UrbexMainWorkspace(
    container: AppContainer,
    settings: AppSettings,
    profile: UserProfile,
    onOpenWelcomeScreen: () -> Unit
) {
    val uiReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        uiReveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
        )
    }
    val density = LocalDensity.current
    val topBarSlidePx = with(density) { 14.dp.toPx() }
    val railSlidePx = with(density) { 18.dp.toPx() }
    val dockSlidePx = with(density) { 20.dp.toPx() }

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val navPrefs = remember(context) {
        context.getSharedPreferences("urbex_nav_prefs", Context.MODE_PRIVATE)
    }
    val commsVisible = settings.meshEnabled || settings.radioChannelsTabEnabled
    val initialRoute = remember {
        val saved = navPrefs.getString("last_active_route", PdaRoute.MAP.route) ?: PdaRoute.MAP.route
        val valid = PdaRoute.entries.any { it.route == saved }
        when {
            !valid -> PdaRoute.MAP.route
            saved == PdaRoute.APPS.route && !settings.pdaModeEnabled -> PdaRoute.MAP.route
            saved == PdaRoute.MESH.route && !commsVisible -> PdaRoute.MAP.route
            else -> saved
        }
    }
    val routeHistory = remember {
        val raw = navPrefs.getString("route_history_csv", "") ?: ""
        val restored = raw.split(",")
            .map { it.trim() }
            .filter { r -> PdaRoute.entries.any { it.route == r } }
            .filterNot { r ->
                (r == PdaRoute.APPS.route && !settings.pdaModeEnabled) ||
                    (r == PdaRoute.MESH.route && !commsVisible)
            }
            .toMutableList()
        if (restored.lastOrNull() != initialRoute) {
            restored.add(initialRoute)
        }
        mutableStateListOf<String>().apply {
            addAll(restored.takeLast(24))
        }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: initialRoute
    var selectedObjectId by remember { mutableStateOf<String?>(null) }
    var zoneEditingObjectId by remember { mutableStateOf<String?>(null) }
    var mapFocusRequest by remember { mutableStateOf<MapFocusRequest?>(null) }
    var pointMoveSession by remember { mutableStateOf<PointMoveSession?>(null) }
    var showQuickCreateDialog by remember { mutableStateOf(false) }
    var resumedQuickCreateDraft by remember { mutableStateOf<ObjectEditorDraft?>(null) }
    var resumedObjectsEditorDraft by remember { mutableStateOf<ObjectEditorDraft?>(null) }
    var activeSettingsCategory by remember {
        val savedCat = navPrefs.getString("last_settings_category", SettingsCategory.DATABASES.name)
        val parsedCat = runCatching { SettingsCategory.valueOf(savedCat ?: "") }
            .getOrDefault(SettingsCategory.DATABASES)
        mutableStateOf(parsedCat)
    }
    var installedAppsCache by remember { mutableStateOf<List<InstalledAppEntry>?>(null) }

    val initialKnownFix = remember(context) { AdaptiveLocationClient(context).getLastKnownFixOrDefault() }
    val mapCursorCoords = remember { doubleArrayOf(initialKnownFix.lat, initialKnownFix.lon) }

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
        container.workspaceManager.ensureInitialized()
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

    val persistNavState: (String) -> Unit = { active ->
        navPrefs.edit()
            .putString("last_active_route", active)
            .putString("route_history_csv", routeHistory.joinToString(","))
            .apply()
    }

    val navigateTo: (PdaRoute) -> Unit = { target ->
        if (target != PdaRoute.MAP) {
            zoneEditingObjectId = null
            pointMoveSession = null
        }
        if (routeHistory.size >= 2 && routeHistory[routeHistory.lastIndex - 1] == target.route) {
            routeHistory.removeAt(routeHistory.lastIndex)
        } else if (routeHistory.lastOrNull() != target.route) {
            routeHistory.add(target.route)
            if (routeHistory.size > 24) {
                routeHistory.removeAt(0)
            }
        }
        persistNavState(target.route)
        navController.navigate(target.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateBack: () -> Unit = {
        if (routeHistory.size > 1) {
            routeHistory.removeAt(routeHistory.lastIndex)
            val prevRouteName = routeHistory.lastOrNull() ?: PdaRoute.MAP.route
            val prevTarget = PdaRoute.entries.firstOrNull { it.route == prevRouteName }
                ?.takeUnless {
                    (it == PdaRoute.APPS && !settings.pdaModeEnabled) ||
                        (it == PdaRoute.MESH && !commsVisible)
                }
                ?: PdaRoute.MAP
            if (routeHistory.lastOrNull() != prevTarget.route) {
                routeHistory[routeHistory.lastIndex] = prevTarget.route
            }
            if (prevTarget != PdaRoute.MAP) {
                zoneEditingObjectId = null
                pointMoveSession = null
            }
            persistNavState(prevTarget.route)
            navController.navigate(prevTarget.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Back gesture returns through actual tab history; stays locked at root MAP in PDA Home mode
    BackHandler(
        enabled = routeHistory.size > 1 || (settings.pdaModeEnabled && currentRoute == PdaRoute.MAP.route)
    ) {
        if (routeHistory.size > 1) {
            navigateBack()
        }
    }

    LaunchedEffect(settings.pdaModeEnabled, currentRoute) {
        if ((settings.pdaModeEnabled || currentRoute == PdaRoute.APPS.route) && installedAppsCache == null) {
            installedAppsCache = withContext(Dispatchers.IO) {
                loadInstalledApps(context)
            }
        }
    }

    // Leave APPS/MESH routes automatically only when their backing modules are turned off
    LaunchedEffect(settings.pdaModeEnabled, commsVisible, currentRoute) {
        if (!settings.pdaModeEnabled && currentRoute == PdaRoute.APPS.route) {
            navigateTo(PdaRoute.MAP)
        } else if (!commsVisible && currentRoute == PdaRoute.MESH.route) {
            navigateTo(PdaRoute.MAP)
        }
    }

    // Close active editing modals/HUDs if Editor Mode is turned off
    LaunchedEffect(settings.editorEnabled) {
        if (!settings.editorEnabled) {
            showQuickCreateDialog = false
            zoneEditingObjectId = null
            pointMoveSession = null
            resumedQuickCreateDraft = null
            resumedObjectsEditorDraft = null
        }
    }

    val startPointMoveFromDraft: (ObjectEditorDraft, String) -> Unit = { draft, returnTarget ->
        if (settings.editorEnabled) {
            val latVal = draft.latStr.replace(',', '.').toDoubleOrNull() ?: mapCursorCoords[0]
            val lonVal = draft.lonStr.replace(',', '.').toDoubleOrNull() ?: mapCursorCoords[1]
            val req = MapPointMoveRequest(
                objectId = draft.editingObject?.id,
                title = draft.title.ifBlank { draft.editingObject?.title ?: "Новый объект" },
                status = draft.status,
                category = draft.category,
                icon = draft.icon,
                lat = latVal,
                lon = lonVal,
                zone = draft.zone,
                zoneColor = draft.zoneColor.takeIf { it.isNotBlank() },
                zoneStyle = draft.zoneStyle,
                zonePattern = draft.zonePattern,
                zoneHatched = draft.zonePattern != com.urbext.domain.model.ZonePattern.NONE,
                returnToEditor = true,
                token = System.currentTimeMillis()
            )
            zoneEditingObjectId = null
            navigateTo(PdaRoute.MAP)
            pointMoveSession = PointMoveSession(request = req, returnTarget = returnTarget, draft = draft)
        }
    }

    val startPointMoveFromObject: (UrbexObject, String) -> Unit = { obj, returnTarget ->
        if (settings.editorEnabled) {
            val req = MapPointMoveRequest(
                objectId = obj.id,
                title = obj.title,
                status = obj.status,
                category = obj.category,
                icon = obj.icon,
                lat = obj.lat,
                lon = obj.lon,
                zone = obj.zone,
                zoneColor = obj.zoneColor,
                zoneStyle = obj.zoneStyle,
                zonePattern = obj.zonePattern,
                zoneHatched = obj.zoneHatched,
                returnToEditor = false,
                token = System.currentTimeMillis()
            )
            zoneEditingObjectId = null
            navigateTo(PdaRoute.MAP)
            pointMoveSession = PointMoveSession(request = req, returnTarget = returnTarget, draft = null)
        }
    }

    val tabletRailTabs = remember(commsVisible, settings.pdaModeEnabled) {
        buildList {
            add(PdaRoute.MAP)
            add(PdaRoute.OBJECTS)
            if (commsVisible) add(PdaRoute.MESH)
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
            val barHeight = if (isLandscape && !isLargeTablet) 38.dp else 52.dp
            Surface(
                color = UrbexColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = uiReveal.value
                        alpha = p
                        translationY = (1f - p) * -topBarSlidePx
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(topInsets)
                            .height(barHeight)
                            .padding(horizontal = if (isLandscape && !isLargeTablet) 10.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .urbexBounceClick(0.97f) { navigateTo(PdaRoute.MAP) }
                                .padding(vertical = 3.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = "Urbex-T",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLandscape && !isLargeTablet) 15.sp else 18.sp,
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
                                        .urbexGlow(UrbexColors.Amber, radius = 8.dp, cornerRadius = 4.dp, alpha = 0.24f)
                                        .background(UrbexColors.Amber.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                        .border(1.dp, UrbexColors.Amber.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape && !isLargeTablet) 6.dp else 8.dp)
                        ) {
                            val chipHeight = if (isLandscape && !isLargeTablet) 28.dp else 32.dp
                            // Live PDA telemetry chip (clock · battery% · orientation in landscape)
                            Row(
                                modifier = Modifier
                                    .height(chipHeight)
                                    .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = if (isLandscape && !isLargeTablet) 8.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .urbexGlow(
                                            color = if (settings.meshEnabled) UrbexColors.Amber else UrbexColors.GreenBright,
                                            radius = 7.dp,
                                            cornerRadius = 4.dp,
                                            alpha = 0.75f
                                        )
                                        .clip(CircleShape)
                                        .background(if (settings.meshEnabled) UrbexColors.Amber else UrbexColors.GreenBright)
                                )
                                Text(
                                    text = clockText,
                                    color = UrbexColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                                Text(
                                    text = "$batteryPct%",
                                    color = if (batteryPct <= 20) UrbexColors.Danger else UrbexColors.GreenBright,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (settings.pdaModeEnabled && isLandscape) {
                                    Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                                    Text(
                                        text = settings.pdaOrientation.label,
                                        color = UrbexColors.Amber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Primary + Точка button matching Web TopBar Zone 3 (only in Editor Mode and not actively editing on map)
                            val isMapEditing = currentRoute == PdaRoute.MAP.route &&
                                (zoneEditingObjectId != null || pointMoveSession != null || showQuickCreateDialog)
                            if (settings.editorEnabled && !isMapEditing) {
                                Row(
                                    modifier = Modifier
                                        .height(chipHeight)
                                        .urbexGlow(
                                            color = UrbexColors.GreenBright,
                                            radius = 16.dp,
                                            cornerRadius = 8.dp,
                                            alpha = 0.40f
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.Green, RoundedCornerShape(8.dp))
                                        .urbexBounceClick(0.95f) { showQuickCreateDialog = true }
                                        .padding(horizontal = if (isLandscape && !isLargeTablet) 9.dp else 11.dp),
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
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
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
                val railWidth = if (isLargeTablet) 80.dp else 66.dp
                Surface(
                    color = UrbexColors.Surface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        .width(railWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val p = uiReveal.value
                            alpha = p
                            translationX = (1f - p) * -railSlidePx
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = if (isLargeTablet) 6.dp else 5.dp,
                                end = if (isLargeTablet) 6.dp else 5.dp,
                                top = 8.dp,
                                bottom = (if (isLargeTablet) 6.dp else 4.dp) + bottomSafeInset
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isLargeTablet) 4.dp else 3.dp)
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
                                    compact = true,
                                    onClick = { navigateTo(tab) }
                                )
                            }

                            AnimatedVisibility(
                                visible = commsVisible,
                                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                TacticalRailButton(
                                    label = PdaRoute.MESH.label,
                                    icon = PdaRoute.MESH.icon,
                                    selected = currentRoute == PdaRoute.MESH.route,
                                    accentColor = UrbexColors.GreenBright,
                                    pillBg = UrbexColors.GreenSoft,
                                    compact = true,
                                    onClick = { navigateTo(PdaRoute.MESH) }
                                )
                            }

                            TacticalRailButton(
                                label = PdaRoute.ARCHIVE.label,
                                icon = PdaRoute.ARCHIVE.icon,
                                selected = currentRoute == PdaRoute.ARCHIVE.route,
                                accentColor = UrbexColors.GreenBright,
                                pillBg = UrbexColors.GreenSoft,
                                compact = true,
                                onClick = { navigateTo(PdaRoute.ARCHIVE) }
                            )

                            TacticalRailButton(
                                label = PdaRoute.MENU.label,
                                icon = PdaRoute.MENU.icon,
                                selected = isMenuRouteActive,
                                accentColor = UrbexColors.Amber,
                                pillBg = UrbexColors.Amber.copy(alpha = 0.16f),
                                compact = true,
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
                                    compact = true,
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
                    .graphicsLayer {
                        val p = uiReveal.value
                        alpha = p
                        val s = 0.988f + 0.012f * p
                        scaleX = s
                        scaleY = s
                    }
            ) {
                val dockClearance = if (!useLeftRail) 100.dp + bottomSafeInset else 14.dp + bottomSafeInset
                val mapBottomOverlayPadding = if (!useLeftRail) 96.dp + bottomSafeInset else 8.dp + bottomSafeInset

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
                                    .padding(
                                        horizontal = if (isLandscape) 10.dp else 12.dp,
                                        vertical = if (isLandscape) 4.dp else 7.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(UrbexColors.Elevated)
                                        .urbexBounceClick(0.95f) { navigateTo(PdaRoute.MENU) }
                                        .padding(
                                            horizontal = 10.dp,
                                            vertical = if (isLandscape) 4.dp else 6.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад в меню",
                                        tint = UrbexColors.Amber,
                                        modifier = Modifier.size(15.dp)
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
                            onOpenObject = { id ->
                                selectedObjectId = id
                                navigateTo(PdaRoute.OBJECTS)
                            },
                            focusRequest = mapFocusRequest,
                            zoneEditingObjectId = zoneEditingObjectId,
                            onRequestZoneEdit = { id ->
                                if (settings.editorEnabled) {
                                    pointMoveSession = null
                                    zoneEditingObjectId = id
                                    navigateTo(PdaRoute.MAP)
                                }
                            },
                            onClearZoneEdit = {
                                zoneEditingObjectId = null
                            },
                            pointMoveRequest = pointMoveSession?.request,
                            onRequestPointMove = { obj ->
                                startPointMoveFromObject(obj, "mapDirect")
                            },
                            onCompletePointMove = { result ->
                                val active = pointMoveSession
                                pointMoveSession = null
                                if (active != null) {
                                    val draft = active.draft
                                    when {
                                        active.returnTarget == "quickCreate" && draft != null -> {
                                            resumedQuickCreateDraft = draft.copy(
                                                latStr = String.format(Locale.US, "%.6f", result.lat),
                                                lonStr = String.format(Locale.US, "%.6f", result.lon),
                                                zone = result.shiftedZone ?: draft.zone
                                            )
                                            showQuickCreateDialog = true
                                        }
                                        active.returnTarget == "objectsEditor" && draft != null -> {
                                            resumedObjectsEditorDraft = draft.copy(
                                                latStr = String.format(Locale.US, "%.6f", result.lat),
                                                lonStr = String.format(Locale.US, "%.6f", result.lon),
                                                zone = result.shiftedZone ?: draft.zone
                                            )
                                            navigateTo(PdaRoute.OBJECTS)
                                        }
                                        else -> {
                                            scope.launch {
                                                val objId = result.objectId ?: return@launch
                                                val existing = container.objectRepository.getObjectById(objId) ?: return@launch
                                                val updated = existing.copy(
                                                    lat = result.lat,
                                                    lon = result.lon,
                                                    zone = result.shiftedZone ?: existing.zone,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                                container.objectRepository.upsertObject(updated)
                                                selectedObjectId = updated.id
                                                if (active.returnTarget == "objectsDetail") {
                                                    navigateTo(PdaRoute.OBJECTS)
                                                } else {
                                                    mapFocusRequest = MapFocusRequest(
                                                        objectId = updated.id,
                                                        lat = updated.lat,
                                                        lon = updated.lon,
                                                        token = System.currentTimeMillis()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onCancelPointMove = {
                                val active = pointMoveSession
                                pointMoveSession = null
                                if (active != null) {
                                    when (active.returnTarget) {
                                        "quickCreate" -> if (active.draft != null) {
                                            resumedQuickCreateDraft = active.draft
                                            showQuickCreateDialog = true
                                        }
                                        "objectsEditor" -> if (active.draft != null) {
                                            resumedObjectsEditorDraft = active.draft
                                            navigateTo(PdaRoute.OBJECTS)
                                        }
                                        "objectsDetail" -> {
                                            active.request.objectId?.let { selectedObjectId = it }
                                            navigateTo(PdaRoute.OBJECTS)
                                        }
                                    }
                                }
                            },
                            onCursorChange = { la, lo ->
                                mapCursorCoords[0] = la
                                mapCursorCoords[1] = lo
                            },
                            hideHud = showQuickCreateDialog,
                            isActive = isMapRoute,
                            bottomOverlayPadding = mapBottomOverlayPadding
                        )

                        // Smoothly animated backdrop covers MapLibre TextureView when transitioning to/from Map
                        AnimatedVisibility(
                            visible = !isMapRoute,
                            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(190, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 1.025f, animationSpec = tween(190, easing = FastOutSlowInEasing))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(UrbexColors.Background)
                            )
                        }

                        fun routeTransitionRank(route: String?): Int = when (route) {
                            PdaRoute.MAP.route -> 0
                            PdaRoute.OBJECTS.route -> 1
                            PdaRoute.MESH.route -> 2
                            PdaRoute.ARCHIVE.route -> 3
                            PdaRoute.MENU.route -> 4
                            PdaRoute.APPS.route -> 5
                            PdaRoute.PROFILE.route -> 10
                            PdaRoute.BASE.route -> 11
                            PdaRoute.SETTINGS.route -> 12
                            else -> 0
                        }

                        NavHost(
                            navController = navController,
                            startDestination = initialRoute,
                            enterTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                when {
                                    toRoute == PdaRoute.MAP.route ->
                                        fadeIn(tween(210, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 1.02f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                                    fromRoute == PdaRoute.MAP.route && toRoute == PdaRoute.OBJECTS.route ->
                                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } +
                                            fadeIn(tween(220)) +
                                            scaleIn(initialScale = 0.97f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                                    routeTransitionRank(toRoute) >= routeTransitionRank(fromRoute) ->
                                        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 5 } +
                                            fadeIn(tween(200))
                                    else ->
                                        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 5 } +
                                            fadeIn(tween(200))
                                }
                            },
                            exitTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                when {
                                    toRoute == PdaRoute.MAP.route ->
                                        slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 6 } +
                                            fadeOut(tween(180))
                                    routeTransitionRank(toRoute) >= routeTransitionRank(fromRoute) ->
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 8 } +
                                            fadeOut(tween(160))
                                    else ->
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { it / 8 } +
                                            fadeOut(tween(160))
                                }
                            },
                            popEnterTransition = {
                                val toRoute = targetState.destination.route
                                if (toRoute == PdaRoute.MAP.route) {
                                    fadeIn(tween(200)) + scaleIn(initialScale = 1.02f, animationSpec = tween(210))
                                } else {
                                    slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 5 } +
                                        fadeIn(tween(200))
                                }
                            },
                            popExitTransition = {
                                slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 5 } +
                                    fadeOut(tween(170))
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
                                    workspaceRepository = container.workspaceRepository,
                                    initialSelectedId = selectedObjectId,
                                    onActiveObjectChange = { selectedObjectId = it },
                                    resumedEditorDraft = resumedObjectsEditorDraft,
                                    onConsumeResumedEditorDraft = { resumedObjectsEditorDraft = null },
                                    onEditZoneOnMap = { obj ->
                                        if (settings.editorEnabled) {
                                            pointMoveSession = null
                                            zoneEditingObjectId = obj.id
                                            navigateTo(PdaRoute.MAP)
                                        }
                                    },
                                    onFocusOnMap = { obj ->
                                        pointMoveSession = null
                                        mapFocusRequest = MapFocusRequest(
                                            objectId = obj.id,
                                            lat = obj.lat,
                                            lon = obj.lon,
                                            token = System.currentTimeMillis()
                                        )
                                        zoneEditingObjectId = null
                                        navigateTo(PdaRoute.MAP)
                                    },
                                    onMovePointOnMap = { obj ->
                                        startPointMoveFromObject(obj, "objectsDetail")
                                    },
                                    onMovePointFromEditor = { draft ->
                                        startPointMoveFromDraft(draft, "objectsEditor")
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
                                    onOpenSettings = {
                                        activeSettingsCategory = SettingsCategory.RADIO_MESH
                                        navPrefs.edit().putString("last_settings_category", SettingsCategory.RADIO_MESH.name).apply()
                                        navigateTo(PdaRoute.SETTINGS)
                                    },
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.BASE.route) {
                                BaseScreen(
                                    baseRepository = container.baseRepository,
                                    editorEnabled = settings.editorEnabled,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.ARCHIVE.route) {
                                ArchiveScreen(
                                    archiveRepository = container.archiveRepository,
                                    objectRepository = container.objectRepository,
                                    workspaceRepository = container.workspaceRepository,
                                    editorEnabled = settings.editorEnabled,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.APPS.route) {
                                PdaAppsScreen(
                                    settings = settings,
                                    container = container,
                                    installedApps = installedAppsCache ?: emptyList(),
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
                                    workspaceRepository = container.workspaceRepository,
                                    activeCategory = activeSettingsCategory,
                                    onSelectCategory = { cat ->
                                        activeSettingsCategory = cat
                                        navPrefs.edit().putString("last_settings_category", cat.name).apply()
                                    },
                                    onSyncNow = { container.cloudSyncEngine.syncNow(manual = true) },
                                    onOpenWelcomeScreen = onOpenWelcomeScreen,
                                    bottomContentPadding = dockClearance
                                )
                            }
                            composable(PdaRoute.MENU.route) {
                                FullscreenMenuScreen(
                                    settings = settings,
                                    profile = profile,
                                    container = container,
                                    onNavigate = navigateTo,
                                    onOpenSettingsCategory = { cat ->
                                        activeSettingsCategory = cat
                                        navPrefs.edit().putString("last_settings_category", cat.name).apply()
                                        navigateTo(PdaRoute.SETTINGS)
                                    },
                                    bottomContentPadding = dockClearance
                                )
                            }
                        }
                    }
                }

                // Floating Sliding Capsule Bottom Navigation (Portrait Phone)
                if (!useLeftRail) {
                    UrbexFloatingBottomDock(
                        currentRoute = currentRoute,
                        isMenuRouteActive = isMenuRouteActive,
                        commsVisible = commsVisible,
                        pdaModeEnabled = settings.pdaModeEnabled,
                        hapticEnabled = settings.hapticFeedback,
                        bottomSafeInset = bottomSafeInset,
                        onSelectRoute = navigateTo,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val p = uiReveal.value
                                alpha = p
                                translationY = (1f - p) * dockSlidePx
                                val s = 0.965f + 0.035f * p
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }
    }

    if (settings.editorEnabled && showQuickCreateDialog) {
        val locationClient = remember(context) { AdaptiveLocationClient(context) }
        val initialFix = remember(showQuickCreateDialog) { locationClient.getLastKnownFixOrDefault() }
        val targetLat = if (currentRoute == PdaRoute.MAP.route) mapCursorCoords[0] else initialFix.lat
        val targetLon = if (currentRoute == PdaRoute.MAP.route) mapCursorCoords[1] else initialFix.lon
        ObjectEditorDialog(
            initialLat = targetLat,
            initialLon = targetLon,
            initialElevationM = initialFix.altitudeM ?: 140,
            defaultZoneRadiusM = settings.defaultZoneRadiusM,
            authorNodeId = profile.nodeId,
            editingObject = null,
            initialDraft = resumedQuickCreateDraft,
            onDismiss = {
                showQuickCreateDialog = false
                resumedQuickCreateDraft = null
            },
            onSave = { newObj ->
                scope.launch {
                    container.objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    showQuickCreateDialog = false
                    resumedQuickCreateDraft = null
                }
            },
            onOpenZoneEditor = { newObj ->
                scope.launch {
                    container.objectRepository.upsertObject(newObj)
                    selectedObjectId = newObj.id
                    zoneEditingObjectId = newObj.id
                    showQuickCreateDialog = false
                    resumedQuickCreateDraft = null
                    navigateTo(PdaRoute.MAP)
                }
            },
            onOpenPointMover = { draft ->
                showQuickCreateDialog = false
                resumedQuickCreateDraft = null
                startPointMoveFromDraft(draft, "quickCreate")
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
    onOpenSettingsCategory: (SettingsCategory) -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    val objects by container.objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val archives by container.archiveRepository.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by container.baseRepository.observeArticles().collectAsStateWithLifecycle(initialValue = emptyList())
    val meshMessages by container.meshRepository.observeAllMessages().collectAsStateWithLifecycle(initialValue = emptyList())
    val workspaceState by container.workspaceRepository.observeWorkspaces().collectAsStateWithLifecycle(initialValue = WorkspaceState())
    val activeDbName = workspaceState.workspaces.firstOrNull { it.meta.id == workspaceState.activeDbId }?.meta?.name
    val activeDbBadge = if (activeDbName != null) {
        if (workspaceState.mountedDbIds.size > 1) "$activeDbName +${workspaceState.mountedDbIds.size - 1}" else activeDbName
    } else null

    val layerLabel = settings.mapLayer.label
    val gpsLabel = settings.powerProfile.label
    val syncMeta = if (settings.cloudSync.enabled && settings.cloudSync.provider != CloudSyncProvider.NONE) {
        " · Облако: ${settings.cloudSync.provider.label}"
    } else ""

    val systemCards = remember(settings, profile, articles.size, activeDbBadge, syncMeta) {
        listOf(
            Triple(
                PdaRoute.SETTINGS,
                "Настройки" to "SETTINGS",
                if (activeDbBadge != null) "БД: $activeDbBadge · $layerLabel$syncMeta" else "GPS: $gpsLabel · $layerLabel$syncMeta"
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
            if (settings.meshEnabled || settings.radioChannelsTabEnabled) {
                add(
                    Triple(
                        PdaRoute.MESH,
                        if (settings.meshEnabled) "Связь и радио" else "Частоты радиостанций",
                        if (settings.meshEnabled) {
                            "${meshMessages.size} пакетов · ${settings.radioChannels.size} каналов"
                        } else {
                            "${settings.radioChannels.size} каналов"
                        }
                    )
                )
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
                start = if (isLandscape) 10.dp else 16.dp,
                top = if (isLandscape) 8.dp else 16.dp,
                end = if (isLandscape) 10.dp else 16.dp,
                bottom = (if (isLandscape) 10.dp else 16.dp) + bottomContentPadding
            ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 9.dp else 16.dp)
    ) {
        // Operator Banner matching Web MenuView.tsx (side-by-side stats in landscape)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .urbexTileEnter(0)
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = UrbexColors.GreenBright, modifier = Modifier.size(15.dp))
                            Text("Терминал PDA", color = UrbexColors.TextSecondary, fontSize = 11.sp)
                            Text("·", color = UrbexColors.TextDim, fontSize = 11.sp)
                            Text(profile.nodeId, color = UrbexColors.Amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = profile.callsign,
                                color = UrbexColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "· ${profile.role.label}",
                                color = UrbexColors.TextSecondary,
                                fontSize = 11.sp
                            )
                            if (activeDbBadge != null) {
                                Text(
                                    text = "· БД: $activeDbBadge",
                                    color = UrbexColors.GreenBright,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MenuStatChip("Объекты", objects.size.toString(), Modifier.weight(1f).urbexTileEnter(1, offsetDp = 6.dp))
                        MenuStatChip("Архив", archives.size.toString(), Modifier.weight(1f).urbexTileEnter(2, offsetDp = 6.dp))
                        MenuStatChip("База", articles.size.toString(), Modifier.weight(1f).urbexTileEnter(3, offsetDp = 6.dp))
                        MenuStatChip("Радио", settings.radioChannels.size.toString(), Modifier.weight(1f).urbexTileEnter(4, offsetDp = 6.dp))
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
                        text = if (activeDbBadge != null) "Роль: ${profile.role.label} · БД: $activeDbBadge" else "Роль: ${profile.role.label}",
                        color = UrbexColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MenuStatChip("Объекты", objects.size.toString(), Modifier.weight(1f).urbexTileEnter(1, offsetDp = 6.dp))
                    MenuStatChip("Архив", archives.size.toString(), Modifier.weight(1f).urbexTileEnter(2, offsetDp = 6.dp))
                    MenuStatChip("База", articles.size.toString(), Modifier.weight(1f).urbexTileEnter(3, offsetDp = 6.dp))
                    MenuStatChip("Радио", settings.radioChannels.size.toString(), Modifier.weight(1f).urbexTileEnter(4, offsetDp = 6.dp))
                }
            }
        }

        // System & Profile Cards matching Web MenuView.tsx (3-column row in landscape)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "СИСТЕМА И ПРОФИЛЬ",
                color = UrbexColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            val sysCols = if (isLandscape) 3 else 1
            systemCards.chunked(sysCols).forEachIndexed { rowIdx, rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCards.forEachIndexed { colIdx, (route, titlePair, meta) ->
                        val tileIdx = 2 + rowIdx * sysCols + colIdx
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
                                .urbexTileEnter(tileIdx)
                                .clip(RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.97f) { onNavigate(route) },
                            color = UrbexColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, UrbexColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isLandscape) 10.dp else 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isLandscape) 32.dp else 38.dp)
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(route.icon, contentDescription = title, tint = accent, modifier = Modifier.size(if (isLandscape) 16.dp else 19.dp))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                color = UrbexColors.TextPrimary,
                                                fontSize = if (isLandscape) 13.sp else 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!isLandscape) {
                                                Text(code, color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                            }
                                        }
                                        if (!isLandscape) {
                                            Text(description, color = UrbexColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(meta, color = UrbexColors.GreenBright, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = UrbexColors.TextDim,
                                    modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
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

        // Settings Submenus Quick Access Cards
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ПОДМЕНЮ НАСТРОЕК",
                color = UrbexColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            val subCols = if (isLandscape) 4 else 2
            SettingsCategory.entries.chunked(subCols).forEachIndexed { rowIdx, rowCats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCats.forEachIndexed { colIdx, cat ->
                        val tileIdx = 5 + rowIdx * subCols + colIdx
                        val catIcon = when (cat) {
                            SettingsCategory.DATABASES -> Icons.Default.Layers
                            SettingsCategory.MAP_GPS -> Icons.Default.Map
                            SettingsCategory.RADIO_MESH -> Icons.Default.Radio
                            SettingsCategory.PDA_UI -> Icons.Default.Smartphone
                        }
                        val catTint = when (cat) {
                            SettingsCategory.DATABASES -> UrbexColors.GreenBright
                            SettingsCategory.MAP_GPS -> UrbexColors.GreenBright
                            SettingsCategory.RADIO_MESH -> UrbexColors.Amber
                            SettingsCategory.PDA_UI -> UrbexColors.Amber
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .urbexTileEnter(tileIdx)
                                .clip(RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.96f) { onOpenSettingsCategory(cat) },
                            color = UrbexColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, UrbexColors.Border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isLandscape) 9.dp else 11.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(UrbexColors.Background, RoundedCornerShape(7.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(7.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(catIcon, contentDescription = cat.label, tint = catTint, modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = cat.label,
                                        color = UrbexColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = cat.subtitle,
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    repeat(subCols - rowCats.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Workspace Quick Navigation Cards matching Web MenuView.tsx (3-column rows in landscape)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "РАБОЧИЕ РАЗДЕЛЫ",
                color = UrbexColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            val wsCols = if (isLandscape) 3 else 1
            workspaceCards.chunked(wsCols).forEachIndexed { rowIdx, rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowCards.forEachIndexed { colIdx, (route, title, meta) ->
                        val tileIdx = 9 + rowIdx * wsCols + colIdx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .urbexTileEnter(tileIdx)
                                .clip(RoundedCornerShape(12.dp))
                                .urbexBounceClick(0.97f) { onNavigate(route) },
                            color = UrbexColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, UrbexColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (isLandscape) 10.dp else 13.dp,
                                        vertical = if (isLandscape) 9.dp else 11.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isLandscape) 30.dp else 34.dp)
                                            .background(UrbexColors.Background, RoundedCornerShape(8.dp))
                                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(route.icon, contentDescription = title, tint = UrbexColors.TextSecondary, modifier = Modifier.size(if (isLandscape) 16.dp else 18.dp))
                                    }
                                    Column {
                                        Text(
                                            text = title,
                                            color = UrbexColors.TextPrimary,
                                            fontSize = if (isLandscape) 13.sp else 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(meta, color = UrbexColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = UrbexColors.TextDim,
                                    modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
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
    installedApps: List<InstalledAppEntry>,
    onNavigate: (PdaRoute) -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    var appQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, appQuery) {
        val q = appQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) installedApps else installedApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
    }

    val appGridCols = if (isLandscape) 7 else 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isLandscape) 10.dp else 16.dp,
                top = if (isLandscape) 8.dp else 16.dp,
                end = if (isLandscape) 10.dp else 16.dp,
                bottom = (if (isLandscape) 10.dp else 16.dp) + bottomContentPadding
            ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 14.dp)
    ) {
        // PDA Mode Header, Orientation Switcher & OS System Shortcuts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .urbexTileEnter(0)
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 7.dp else 10.dp)
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
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(15.dp))
                    Text(
                        text = "ПРИЛОЖЕНИЯ И ЛАУНЧЕР PDA",
                        color = UrbexColors.TextPrimary,
                        fontSize = 12.sp,
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

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PdaOrientation.entries.forEach { orient ->
                            val selected = settings.pdaOrientation == orient
                            Text(
                                text = orient.label,
                                color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.22f, enabled = selected)
                                    .background(if (selected) UrbexColors.Elevated else UrbexColors.Background, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (selected) UrbexColors.Green else UrbexColors.Border, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.96f) {
                                        scope.launch {
                                            container.settingsRepository.updateSettings(
                                                settings.copy(pdaOrientation = orient, updatedAt = System.currentTimeMillis())
                                            )
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickSystemShortcut(
                            icon = Icons.Default.Home,
                            label = "Главный",
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Settings,
                            label = "ОС",
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Wifi,
                            label = "Wi-Fi",
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
                        )
                        QuickSystemShortcut(
                            icon = Icons.Default.Bluetooth,
                            label = "BLE",
                            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
                        )
                    }
                }
            } else {
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
                                .urbexGlow(UrbexColors.GreenBright, radius = 10.dp, cornerRadius = 8.dp, alpha = 0.22f, enabled = selected)
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
        }

        // Quick PDA Internal Modules Strip (1:1 parity with Web AppsView)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                PdaRoute.MAP to "Карта",
                PdaRoute.OBJECTS to "Объекты",
                PdaRoute.ARCHIVE to "Архив",
                PdaRoute.BASE to "База знаний",
                PdaRoute.PROFILE to "Профиль",
                PdaRoute.SETTINGS to "Настройки"
            ).forEachIndexed { idx, (route, shortLabel) ->
                Row(
                    modifier = Modifier
                        .urbexTileEnter(1 + idx, delayStepMs = 18, offsetDp = 8.dp)
                        .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
                        .urbexBounceClick(0.95f) { onNavigate(route) }
                        .padding(
                            horizontal = if (isLandscape) 9.dp else 11.dp,
                            vertical = if (isLandscape) 6.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(route.icon, contentDescription = shortLabel, tint = UrbexColors.GreenBright, modifier = Modifier.size(14.dp))
                    Text(shortLabel, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Installed Android Applications Search & Responsive Grid
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                UrbexCompactInputField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    placeholder = "Поиск установленного приложения...",
                    leadingIcon = Icons.Default.Search,
                    showClearButton = true,
                    minHeight = 36.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
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

            UrbexCompactInputField(
                value = appQuery,
                onValueChange = { appQuery = it },
                placeholder = "Поиск установленного приложения...",
                leadingIcon = Icons.Default.Search,
                showClearButton = true,
                minHeight = 38.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
        ) {
            if (filteredApps.isEmpty()) {
                Text(
                    text = "Приложения не найдены.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(6.dp)
                )
            } else {
                filteredApps.chunked(appGridCols).forEachIndexed { rowIdx, rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                    ) {
                        rowApps.forEachIndexed { colIdx, app ->
                            val appTileIdx = 3 + rowIdx * appGridCols + colIdx
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .urbexTileEnter(appTileIdx, delayStepMs = 14, maxCascadeItems = 16, offsetDp = 8.dp)
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
                                    .padding(
                                        horizontal = if (isLandscape) 4.dp else 6.dp,
                                        vertical = if (isLandscape) 7.dp else 10.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
                            ) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(if (isLandscape) 28.dp else 34.dp)
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
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (selected) accentColor else UrbexColors.TextSecondary
    val bgColor = if (selected) pillBg else Color.Transparent
    val borderColor = if (selected) accentColor.copy(alpha = 0.55f) else Color.Transparent

    val railFontSize = if (compact) 8.sp else 9.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 40.dp else 46.dp)
            .urbexGlow(
                color = accentColor,
                radius = 12.dp,
                cornerRadius = 10.dp,
                alpha = 0.22f,
                enabled = selected
            )
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
        )
        Text(
            text = label,
            color = contentColor,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = railFontSize,
                lineHeight = railFontSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
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
private fun UrbexFloatingBottomDock(
    currentRoute: String,
    isMenuRouteActive: Boolean,
    commsVisible: Boolean,
    pdaModeEnabled: Boolean,
    hapticEnabled: Boolean,
    bottomSafeInset: Dp,
    onSelectRoute: (PdaRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val visibleTabs = remember(commsVisible, pdaModeEnabled) {
        buildList {
            add(PdaRoute.MAP)
            add(PdaRoute.OBJECTS)
            if (commsVisible) add(PdaRoute.MESH)
            add(PdaRoute.ARCHIVE)
            add(PdaRoute.MENU)
            if (pdaModeEnabled) add(PdaRoute.APPS)
        }
    }

    val selectedIndex = visibleTabs.indexOfFirst { tab ->
        if (tab == PdaRoute.MENU) isMenuRouteActive else currentRoute == tab.route
    }.coerceAtLeast(0)
    val selectedTab = visibleTabs.getOrNull(selectedIndex) ?: PdaRoute.MAP
    val isAmberSelected = selectedTab == PdaRoute.MENU

    val capsuleTopColor by animateColorAsState(
        targetValue = if (isAmberSelected) Color(0xFF33271A) else Color(0xFF23362C),
        animationSpec = tween(70),
        label = "dockCapsuleTopBg"
    )
    val capsuleBottomColor by animateColorAsState(
        targetValue = if (isAmberSelected) Color(0xFF211A13) else Color(0xFF17221D),
        animationSpec = tween(70),
        label = "dockCapsuleBottomBg"
    )
    val capsuleBorderColor by animateColorAsState(
        targetValue = if (isAmberSelected) UrbexColors.Amber.copy(alpha = 0.55f) else UrbexColors.Green.copy(alpha = 0.55f),
        animationSpec = tween(70),
        label = "dockCapsuleBorder"
    )
    val topBarColor by animateColorAsState(
        targetValue = if (isAmberSelected) UrbexColors.Amber else UrbexColors.GreenBright,
        animationSpec = tween(70),
        label = "dockTopBarColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 14.dp + bottomSafeInset
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            color = UrbexColors.Surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color(0xFF2C3732)),
            shadowElevation = 14.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
            ) {
                val slotCount = visibleTabs.size.coerceAtLeast(1)
                val slotWidth = maxWidth / slotCount
                val indicatorOffsetState = animateDpAsState(
                    targetValue = slotWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 950f
                    ),
                    label = "dockSlideOffset"
                )

                // Sliding tactile highlight matching Web AppShell (rounded-xl inset-1 gradient + top 24dp x 3dp accent bar)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = indicatorOffsetState.value.toPx()
                        }
                        .width(slotWidth)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .urbexGlow(
                            color = if (isAmberSelected) UrbexColors.Amber else UrbexColors.GreenBright,
                            radius = 14.dp,
                            cornerRadius = 12.dp,
                            alpha = 0.32f
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(capsuleTopColor, capsuleBottomColor)
                            )
                        )
                        .border(1.dp, capsuleBorderColor, RoundedCornerShape(12.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 3.dp)
                            .width(24.dp)
                            .height(3.dp)
                            .urbexGlow(
                                color = topBarColor,
                                radius = 10.dp,
                                cornerRadius = 4.dp,
                                alpha = 0.78f
                            )
                            .clip(RoundedCornerShape(50))
                            .background(topBarColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTabs.forEach { tab ->
                        val isMenu = tab == PdaRoute.MENU
                        val selected = if (isMenu) isMenuRouteActive else currentRoute == tab.route
                        val accentColor = if (isMenu) UrbexColors.Amber else UrbexColors.GreenBright

                        val interactionSource = remember(tab) { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val contentColor = if (selected) accentColor else UrbexColors.TextSecondary
                        val iconScaleState = animateFloatAsState(
                            targetValue = when {
                                isPressed -> 0.92f
                                selected -> 1.08f
                                else -> 1.0f
                            },
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 950f
                            ),
                            label = "dockIconScale_${tab.route}"
                        )
                        val iconLiftState = animateDpAsState(
                            targetValue = if (selected) (-1.5).dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 950f
                            ),
                            label = "dockIconLift_${tab.route}"
                        )

                        val labelFontSize = if (visibleTabs.size >= 6) 8.5.sp else 9.5.sp

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSelectRoute(tab)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val s = iconScaleState.value
                                            scaleX = s
                                            scaleY = s
                                            translationY = iconLiftState.value.toPx()
                                        }
                                        .size(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = contentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = tab.label,
                                    color = contentColor,
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = labelFontSize,
                                        lineHeight = labelFontSize,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = if (selected && visibleTabs.size < 6) 0.25.sp else 0.sp,
                                        textAlign = TextAlign.Center,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both
                                        )
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
