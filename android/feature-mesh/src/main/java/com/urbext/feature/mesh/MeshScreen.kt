package com.urbext.feature.mesh

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.geo.GeoUtils
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.MeshChannel
import com.urbext.domain.model.MeshNode
import com.urbext.domain.model.MeshPacketKind
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.PowerProfile
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ProfileRepository
import com.urbext.domain.repository.SettingsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeshScreen(
    channels: List<MeshChannel>,
    meshRepository: MeshRepository,
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val twoPaneLandscape = isLandscape && cfg.screenWidthDp >= 560
    var activeChannel by remember { mutableStateOf(channels.first()) }
    var draft by remember { mutableStateOf("") }
    var attachCoords by remember { mutableStateOf(false) }
    var sosPanelOpen by remember { mutableStateOf(false) }
    var sosNote by remember { mutableStateOf("Требуется эвакуация / потеря ориентации") }
    var hwMessage by remember { mutableStateOf<String?>(null) }

    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val allMessages by meshRepository.observeAllMessages()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = remember(allMessages, activeChannel.id) {
        allMessages.filter { it.channelId == activeChannel.id }
    }
    val profile by profileRepository.observeProfile().collectAsStateWithLifecycle(
        initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
    )

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    var currentFix by remember { mutableStateOf(locationClient.getLastKnownFixOrDefault()) }
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasLocationPermission, settings.powerProfile, settings.meshEnabled) {
        if (settings.meshEnabled && hasLocationPermission && settings.powerProfile != PowerProfile.MANUAL) {
            locationClient.observeLocationUpdates(settings.powerProfile).collect { fix ->
                currentFix = fix
            }
        }
    }

    val hasBle = remember(context) {
        val pm = context.packageManager
        val bt = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && bt != null
    }
    val discoveredNodes = remember { emptyList<MeshNode>() }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    AnimatedContent(
        targetState = settings.meshEnabled,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "MeshEnabledTransition"
    ) { meshEnabled ->
        if (!meshEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = if (isLandscape) 16.dp else 24.dp,
                        top = if (isLandscape) 12.dp else 24.dp,
                        end = if (isLandscape) 16.dp else 24.dp,
                        bottom = (if (isLandscape) 12.dp else 24.dp) + bottomContentPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(14.dp))
                        .padding(if (isLandscape) 16.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
                ) {
                    Icon(Icons.Default.Radio, contentDescription = null, tint = UrbexColors.TextDim, modifier = Modifier.size(if (isLandscape) 26.dp else 32.dp))
                    Text(
                        text = "Модуль Meshtastic отключён",
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 16.sp else 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Обмен пакетами и поиск LoRa-узлов выключены для экономии энергии. Активируйте модуль в настройках терминала.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    val openSettingsInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onOpenSettings,
                        interactionSource = openSettingsInteraction,
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                        modifier = Modifier.urbexScaleOnPress(openSettingsInteraction, 0.96f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = UrbexColors.Background)
                        Text(" Открыть настройки", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else if (twoPaneLandscape) {
            val coordBg by animateColorAsState(
                targetValue = if (attachCoords) UrbexColors.GreenSoft else UrbexColors.Surface,
                animationSpec = tween(160),
                label = "coordBgLandscape"
            )
            val coordBorder by animateColorAsState(
                targetValue = if (attachCoords) UrbexColors.Green else UrbexColors.Border,
                animationSpec = tween(160),
                label = "coordBorderLandscape"
            )
            val coordTint by animateColorAsState(
                targetValue = if (attachCoords) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                animationSpec = tween(160),
                label = "coordTintLandscape"
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp + bottomContentPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Pane: Channels + Radio Hardware
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "КАНАЛЫ MESH",
                            color = UrbexColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        channels.forEach { ch ->
                            val count = allMessages.count { it.channelId == ch.id }
                            val selected = ch.id == activeChannel.id
                            val pillBg by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
                                animationSpec = tween(160),
                                label = "meshChBgL"
                            )
                            val pillBorder by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                                animationSpec = tween(160),
                                label = "meshChBorderL"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(pillBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.96f) { activeChannel = ch }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ch.name,
                                        color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Text(
                                            text = "Активен",
                                            color = UrbexColors.GreenBright,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = "Пакеты: $count · Hop ${ch.hopLimit}",
                                    color = UrbexColors.TextDim,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Hardware Bridge & Peers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Радиомодуль",
                                color = UrbexColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Узлы: ${discoveredNodes.size}",
                                color = UrbexColors.GreenBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.94f) {
                                        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                                        val usbCount = runCatching { usbManager?.deviceList?.size ?: 0 }.getOrDefault(0)
                                        hwMessage = if (usbCount > 0) {
                                            "USB-устройств: $usbCount. Выберите Meshtastic OTG-порт."
                                        } else {
                                            "USB LoRa-модем не подключён к OTG-порту."
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Usb, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(13.dp))
                                Text("USB LoRa", color = UrbexColors.TextPrimary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                    .urbexBounceClick(0.94f) {
                                        hwMessage = if (!hasBle) {
                                            "Модуль Bluetooth LE недоступен или выключен."
                                        } else {
                                            "BLE готов к сопряжению. Узлы в радиусе не найдены."
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(13.dp))
                                Text("BLE Модем", color = UrbexColors.TextPrimary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        AnimatedVisibility(visible = hwMessage != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                            Text(hwMessage.orEmpty(), color = UrbexColors.Amber, fontSize = 11.sp)
                        }

                        if (discoveredNodes.isEmpty()) {
                            HorizontalDivider(color = UrbexColors.Border)
                            Text(
                                text = "Узлы в радиусе не обнаружены. Подключите LoRa по USB/BLE.",
                                color = UrbexColors.TextDim,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Right Pane: Header + SOS + Feed + Single-Row Composer
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(activeChannel.name, color = UrbexColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "${profile.nodeId} (${profile.callsign}) · Hop ${activeChannel.hopLimit}",
                                color = UrbexColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val sosBtnInteraction = remember { MutableInteractionSource() }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.DangerSoft),
                            onClick = { sosPanelOpen = !sosPanelOpen },
                            interactionSource = sosBtnInteraction,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .urbexScaleOnPress(sosBtnInteraction, 0.94f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = UrbexColors.Danger, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Маяк SOS", color = UrbexColors.Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AnimatedVisibility(
                        visible = sosPanelOpen,
                        enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                        exit = fadeOut(tween(130)) + shrinkVertically(tween(130))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(UrbexColors.DangerSoft, RoundedCornerShape(12.dp))
                                .border(1.dp, UrbexColors.Danger, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Передача аварийного пакета SOS (Hop 7 + координаты)",
                                color = Color(0xFFF1ACA7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UrbexCompactInputField(
                                    value = sosNote,
                                    onValueChange = { sosNote = it },
                                    minHeight = 34.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { sosPanelOpen = false },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Отмена", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                                }
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Danger),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp),
                                    onClick = {
                                        val fix = currentFix
                                        val sosChannel = channels.firstOrNull { it.id == "ch-sos" } ?: activeChannel
                                        val msg = MeshtasticCodec.buildMessage(
                                            channelId = sosChannel.id,
                                            senderNodeId = profile.nodeId,
                                            senderCallsign = profile.callsign,
                                            kind = MeshPacketKind.SOS,
                                            body = "SOS: ${sosNote.trim().ifEmpty { "Требуется помощь" }}",
                                            hopLimit = 7,
                                            lat = fix.lat,
                                            lon = fix.lon
                                        )
                                        scope.launch {
                                            meshRepository.sendMessage(msg)
                                            activeChannel = sosChannel
                                            sosPanelOpen = false
                                        }
                                    }
                                ) {
                                    Text("SOS", color = UrbexColors.Background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = messages.isEmpty(),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                        modifier = Modifier.weight(1f),
                        label = "MeshMessagesEmptyTransitionLandscape"
                    ) { isEmpty ->
                        if (isEmpty) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "В канале пока нет принятых или отправленных пакетов.",
                                    color = UrbexColors.TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val isSos = msg.kind == MeshPacketKind.SOS
                                    val isOwn = msg.senderNodeId == profile.nodeId
                                    val cardBg = when {
                                        isSos -> UrbexColors.DangerSoft
                                        isOwn -> UrbexColors.Elevated
                                        else -> UrbexColors.Surface
                                    }
                                    val cardBorder = when {
                                        isSos -> UrbexColors.Danger
                                        isOwn -> UrbexColors.BorderStrong
                                        else -> UrbexColors.Border
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                            .background(cardBg, RoundedCornerShape(10.dp))
                                            .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                            .padding(9.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(msg.senderCallsign, color = UrbexColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                Text(msg.senderNodeId, color = UrbexColors.TextDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                                if (isSos) {
                                                    Text("✖ SOS", color = UrbexColors.Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(
                                                text = "${timeFormatter.format(Date(msg.timestamp))} · Hop ${msg.hopLimit}",
                                                color = UrbexColors.TextSecondary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(msg.body, color = UrbexColors.TextPrimary, fontSize = 13.sp)
                                        if (msg.lat != null && msg.lon != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(12.dp))
                                                Text(
                                                    text = GeoUtils.formatCoordinates(msg.lat!!, msg.lon!!, settings.coordinateFormat),
                                                    color = UrbexColors.Amber,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Кадр: ${msg.frameHex.take(36)}…",
                                            color = UrbexColors.TextDim,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Single-row compact composer in Landscape
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .background(coordBg, RoundedCornerShape(8.dp))
                                .border(1.dp, coordBorder, RoundedCornerShape(8.dp))
                                .urbexBounceClick(0.96f) { attachCoords = !attachCoords }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Прикрепить координаты", tint = coordTint, modifier = Modifier.size(15.dp))
                            Text(
                                text = if (attachCoords) "GPS ✓" else "GPS",
                                color = if (attachCoords) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        UrbexCompactInputField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = "Пакет в ${activeChannel.name}...",
                            minHeight = 36.dp,
                            modifier = Modifier.weight(1f)
                        )

                        val sendInteraction = remember { MutableInteractionSource() }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                            interactionSource = sendInteraction,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .urbexScaleOnPress(sendInteraction, 0.94f),
                            onClick = {
                                val text = draft.trim()
                                if (text.isEmpty() && !attachCoords) return@Button
                                val fix = if (attachCoords) currentFix else null
                                val msg = MeshtasticCodec.buildMessage(
                                    channelId = activeChannel.id,
                                    senderNodeId = profile.nodeId,
                                    senderCallsign = profile.callsign,
                                    kind = if (attachCoords) MeshPacketKind.POSITION else MeshPacketKind.TEXT,
                                    body = text.ifEmpty { "Отметка текущей позиции" },
                                    hopLimit = activeChannel.hopLimit,
                                    lat = fix?.lat,
                                    lon = fix?.lon
                                )
                                scope.launch {
                                    meshRepository.sendMessage(msg)
                                    draft = ""
                                    attachCoords = false
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Отправить", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UrbexColors.Background)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header + Inline SOS Trigger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(activeChannel.name, color = UrbexColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            text = "Свой узел: ${profile.nodeId} (${profile.callsign}) · Hop ${activeChannel.hopLimit}",
                            color = UrbexColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val sosBtnInteraction = remember { MutableInteractionSource() }
                    Button(
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.DangerSoft),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        onClick = { sosPanelOpen = !sosPanelOpen },
                        interactionSource = sosBtnInteraction,
                        modifier = Modifier
                            .height(36.dp)
                            .urbexScaleOnPress(sosBtnInteraction, 0.94f)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = UrbexColors.Danger, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Маяк SOS", color = UrbexColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }

                // Inline Expandable SOS Confirmation Panel (matching Web MeshView.tsx)
                AnimatedVisibility(
                    visible = sosPanelOpen,
                    enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                    exit = fadeOut(tween(130)) + shrinkVertically(tween(130))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrbexColors.DangerSoft, RoundedCornerShape(12.dp))
                            .border(1.dp, UrbexColors.Danger, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Подтверждение передачи аварийного пакета SOS",
                            color = Color(0xFFF1ACA7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Пакет уйдёт в аварийный канал с Hop 7 и вашими координатами.",
                            color = Color(0xFFF1ACA7).copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                        UrbexCompactInputField(
                            value = sosNote,
                            onValueChange = { sosNote = it },
                            minHeight = 38.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            OutlinedButton(onClick = { sosPanelOpen = false }) {
                                Text("Отмена", color = UrbexColors.TextPrimary, fontSize = 12.sp)
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Danger),
                                onClick = {
                                    val fix = currentFix
                                    val sosChannel = channels.firstOrNull { it.id == "ch-sos" } ?: activeChannel
                                    val msg = MeshtasticCodec.buildMessage(
                                        channelId = sosChannel.id,
                                        senderNodeId = profile.nodeId,
                                        senderCallsign = profile.callsign,
                                        kind = MeshPacketKind.SOS,
                                        body = "SOS: ${sosNote.trim().ifEmpty { "Требуется помощь" }}",
                                        hopLimit = 7,
                                        lat = fix.lat,
                                        lon = fix.lon
                                    )
                                    scope.launch {
                                        meshRepository.sendMessage(msg)
                                        activeChannel = sosChannel
                                        sosPanelOpen = false
                                    }
                                }
                            ) {
                                Text("Передать SOS", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Channel selector with per-channel counts and Активен badge
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { ch ->
                        val count = allMessages.count { it.channelId == ch.id }
                        val selected = ch.id == activeChannel.id
                        val pillBg by animateColorAsState(
                            targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                            animationSpec = tween(160),
                            label = "meshChBg"
                        )
                        val pillBorder by animateColorAsState(
                            targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                            animationSpec = tween(160),
                            label = "meshChBorder"
                        )
                        Column(
                            modifier = Modifier
                                .background(pillBg, RoundedCornerShape(10.dp))
                                .border(1.dp, pillBorder, RoundedCornerShape(10.dp))
                                .urbexBounceClick(0.95f) { activeChannel = ch }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ch.name,
                                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selected) {
                                    Text(
                                        text = "Активен",
                                        color = UrbexColors.GreenBright,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                text = "Пакеты: $count · Hop ${ch.hopLimit}",
                                color = UrbexColors.TextDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Hardware Bridge & Discovered Peers Panel matching Web MeshView.tsx
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Радиомодуль и транспорт",
                            color = UrbexColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Узлы: ${discoveredNodes.size}",
                            color = UrbexColors.GreenBright,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.GreenSoft, RoundedCornerShape(6.dp))
                                .border(1.dp, UrbexColors.Green, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Broadcast", color = UrbexColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                .urbexBounceClick(0.94f) {
                                    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                                    val usbCount = runCatching { usbManager?.deviceList?.size ?: 0 }.getOrDefault(0)
                                    hwMessage = if (usbCount > 0) {
                                        "Обнаружено USB-устройств: $usbCount. Выберите Meshtastic OTG-порт."
                                    } else {
                                        "USB LoRa-модем не подключён к OTG-порту."
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(13.dp))
                            Text("USB LoRa", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                        }
                        Row(
                            modifier = Modifier
                                .background(UrbexColors.Background, RoundedCornerShape(6.dp))
                                .border(1.dp, UrbexColors.Border, RoundedCornerShape(6.dp))
                                .urbexBounceClick(0.94f) {
                                    hwMessage = if (!hasBle) {
                                        "Модуль Bluetooth LE недоступен или выключен."
                                    } else {
                                        "BLE готов к сопряжению. Узлы Meshtastic в радиусе не найдены."
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = UrbexColors.TextSecondary, modifier = Modifier.size(13.dp))
                            Text("BLE Модем", color = UrbexColors.TextPrimary, fontSize = 11.sp)
                        }
                    }

                    AnimatedVisibility(visible = hwMessage != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                        Text(hwMessage.orEmpty(), color = UrbexColors.Amber, fontSize = 11.sp)
                    }

                    if (discoveredNodes.isEmpty()) {
                        HorizontalDivider(color = UrbexColors.Border)
                        Text(
                            text = "Узлы в радиусе не обнаружены. Подключите LoRa-модем по USB/BLE или откройте соседнее устройство в сети.",
                            color = UrbexColors.TextDim,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Messages Feed
                AnimatedContent(
                    targetState = messages.isEmpty(),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
                    modifier = Modifier.weight(1f),
                    label = "MeshMessagesEmptyTransition"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "В канале пока нет принятых или отправленных пакетов.",
                                color = UrbexColors.TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isSos = msg.kind == MeshPacketKind.SOS
                                val isOwn = msg.senderNodeId == profile.nodeId
                                val cardBg = when {
                                    isSos -> UrbexColors.DangerSoft
                                    isOwn -> UrbexColors.Elevated
                                    else -> UrbexColors.Surface
                                }
                                val cardBorder = when {
                                    isSos -> UrbexColors.Danger
                                    isOwn -> UrbexColors.BorderStrong
                                    else -> UrbexColors.Border
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .background(cardBg, RoundedCornerShape(10.dp))
                                        .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = msg.senderCallsign,
                                                color = UrbexColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = msg.senderNodeId,
                                                color = UrbexColors.TextDim,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                            if (isSos) {
                                                Text(
                                                    text = "✖ SOS",
                                                    color = UrbexColors.Danger,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${timeFormatter.format(Date(msg.timestamp))} · Hop ${msg.hopLimit}",
                                            color = UrbexColors.TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(msg.body, color = UrbexColors.TextPrimary, fontSize = 14.sp)
                                    if (msg.lat != null && msg.lon != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = GeoUtils.formatCoordinates(msg.lat!!, msg.lon!!, settings.coordinateFormat),
                                                color = UrbexColors.Amber,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Кадр: ${msg.frameHex.take(36)}…",
                                        color = UrbexColors.TextDim,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Composer
                val coordBg by animateColorAsState(
                    targetValue = if (attachCoords) UrbexColors.GreenSoft else UrbexColors.Surface,
                    animationSpec = tween(160),
                    label = "coordBg"
                )
                val coordBorder by animateColorAsState(
                    targetValue = if (attachCoords) UrbexColors.Green else UrbexColors.Border,
                    animationSpec = tween(160),
                    label = "coordBorder"
                )
                val coordTint by animateColorAsState(
                    targetValue = if (attachCoords) UrbexColors.GreenBright else UrbexColors.TextSecondary,
                    animationSpec = tween(160),
                    label = "coordTint"
                )

                Row(
                    modifier = Modifier
                        .background(coordBg, RoundedCornerShape(8.dp))
                        .border(1.dp, coordBorder, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.97f) { attachCoords = !attachCoords }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = coordTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (attachCoords) {
                            "Координаты прикреплены (${GeoUtils.formatCoordinates(currentFix.lat, currentFix.lon, settings.coordinateFormat)})"
                        } else {
                            "Прикрепить мои координаты"
                        },
                        color = if (attachCoords) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UrbexCompactInputField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "Пакет в ${activeChannel.name}...",
                        minHeight = 38.dp,
                        modifier = Modifier.weight(1f)
                    )

                    val sendInteraction = remember { MutableInteractionSource() }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                        interactionSource = sendInteraction,
                        modifier = Modifier.urbexScaleOnPress(sendInteraction, 0.94f),
                        onClick = {
                            val text = draft.trim()
                            if (text.isEmpty() && !attachCoords) return@Button
                            val fix = if (attachCoords) currentFix else null
                            val msg = MeshtasticCodec.buildMessage(
                                channelId = activeChannel.id,
                                senderNodeId = profile.nodeId,
                                senderCallsign = profile.callsign,
                                kind = if (attachCoords) MeshPacketKind.POSITION else MeshPacketKind.TEXT,
                                body = text.ifEmpty { "Отметка текущей позиции" },
                                hopLimit = activeChannel.hopLimit,
                                lat = fix?.lat,
                                lon = fix?.lon
                            )
                            scope.launch {
                                meshRepository.sendMessage(msg)
                                draft = ""
                                attachCoords = false
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = UrbexColors.Background, modifier = Modifier.size(16.dp))
                        Text(" Отправить", color = UrbexColors.Background, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
