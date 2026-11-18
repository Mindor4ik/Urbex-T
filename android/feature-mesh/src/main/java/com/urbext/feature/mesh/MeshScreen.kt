package com.urbext.feature.mesh

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.geo.GeoUtils
import com.urbext.core.location.AdaptiveLocationClient
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.MeshChannel
import com.urbext.domain.model.MeshPacketKind
import com.urbext.domain.model.OperatorRole
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
    var activeChannel by remember { mutableStateOf(channels.first()) }
    var draft by remember { mutableStateOf("") }
    var attachCoords by remember { mutableStateOf(false) }
    var showSosDialog by remember { mutableStateOf(false) }

    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    val messages by meshRepository.observeMessages(activeChannel.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by profileRepository.observeProfile().collectAsStateWithLifecycle(
        initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
    )

    val locationClient = remember(context) { AdaptiveLocationClient(context) }
    val hasBle = remember(context) {
        val pm = context.packageManager
        val bt = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && bt != null
    }
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
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + bottomContentPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UrbexColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, UrbexColors.Border, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Модуль Meshtastic отключён",
                        color = UrbexColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Обмен пакетами и поиск LoRa-узлов выключены для экономии энергии. Активируйте модуль в настройках терминала.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 13.sp,
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
        } else {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(activeChannel.name, color = UrbexColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = "Hop ${activeChannel.hopLimit} · BLE: ${if (hasBle) "Готов" else "Нет модуля"}",
                    color = UrbexColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            val sosBtnInteraction = remember { MutableInteractionSource() }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.DangerSoft),
                onClick = { showSosDialog = true },
                interactionSource = sosBtnInteraction,
                modifier = Modifier.urbexScaleOnPress(sosBtnInteraction, 0.94f)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = UrbexColors.Danger)
                Text(" Маяк SOS", color = UrbexColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels, key = { it.id }) { ch ->
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
                val pillText by animateColorAsState(
                    targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    animationSpec = tween(160),
                    label = "meshChText"
                )
                Text(
                    text = "${ch.name} · Hop ${ch.hopLimit}",
                    color = pillText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(pillBg, RoundedCornerShape(8.dp))
                        .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.95f) { activeChannel = ch }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .background(
                                    if (isSos) UrbexColors.DangerSoft else UrbexColors.Surface,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSos) UrbexColors.Danger else UrbexColors.Border,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${msg.senderCallsign} ${msg.senderNodeId}",
                                    color = UrbexColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${timeFormatter.format(Date(msg.timestamp))} · Hop ${msg.hopLimit}",
                                    color = UrbexColors.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                            Text(msg.body, color = UrbexColors.TextPrimary, fontSize = 14.sp)
                            if (msg.lat != null && msg.lon != null) {
                                Text(
                                    text = GeoUtils.formatCoordinates(msg.lat!!, msg.lon!!, settings.coordinateFormat),
                                    color = UrbexColors.Amber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val coordInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = { attachCoords = !attachCoords },
                interactionSource = coordInteraction,
                modifier = Modifier
                    .urbexScaleOnPress(coordInteraction, 0.91f)
                    .background(coordBg, RoundedCornerShape(8.dp))
                    .border(1.dp, coordBorder, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Прикрепить координаты",
                    tint = coordTint
                )
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Пакет в ${activeChannel.name}...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            val sendInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty() && !attachCoords) return@IconButton
                    val fix = if (attachCoords) locationClient.getLastKnownFixOrDefault() else null
                    val msg = MeshtasticCodec.buildMessage(
                        channelId = activeChannel.id,
                        senderNodeId = profile.nodeId,
                        senderCallsign = profile.callsign,
                        kind = if (attachCoords) MeshPacketKind.POSITION else MeshPacketKind.TEXT,
                        body = text.ifEmpty { "Отметка позиции" },
                        hopLimit = activeChannel.hopLimit,
                        lat = fix?.lat,
                        lon = fix?.lon
                    )
                    scope.launch {
                        meshRepository.sendMessage(msg)
                        draft = ""
                        attachCoords = false
                    }
                },
                interactionSource = sendInteraction,
                modifier = Modifier
                    .urbexScaleOnPress(sendInteraction, 0.91f)
                    .background(UrbexColors.Green, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = UrbexColors.Background)
            }
        }
    }
        }
    }

    if (showSosDialog) {
        var sosText by remember { mutableStateOf("Требуется помощь") }
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            containerColor = UrbexColors.Surface,
            title = { Text("Передача аварийного маяка SOS", color = UrbexColors.Danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Пакет уйдёт в аварийный канал (Hop 7) с текущей позицией.",
                        color = UrbexColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(value = sosText, onValueChange = { sosText = it }, label = { Text("Сообщение") })
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Danger),
                    onClick = {
                        val fix = locationClient.getLastKnownFixOrDefault()
                        val sosChannel = channels.firstOrNull { it.id == "ch-sos" } ?: activeChannel
                        val msg = MeshtasticCodec.buildMessage(
                            channelId = sosChannel.id,
                            senderNodeId = profile.nodeId,
                            senderCallsign = profile.callsign,
                            kind = MeshPacketKind.SOS,
                            body = "SOS: ${sosText.trim()}",
                            hopLimit = 7,
                            lat = fix.lat,
                            lon = fix.lon
                        )
                        scope.launch {
                            meshRepository.sendMessage(msg)
                            activeChannel = sosChannel
                            showSosDialog = false
                        }
                    }
                ) {
                    Text("Передать SOS", color = UrbexColors.Background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text("Отмена", color = UrbexColors.TextSecondary)
                }
            }
        )
    }
}
