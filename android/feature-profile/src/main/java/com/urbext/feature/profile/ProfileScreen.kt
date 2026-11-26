package com.urbext.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.UrbexCompactInputField
import com.urbext.core.ui.UrbexCompactMultilineField
import com.urbext.core.ui.UrbexDropdownField
import com.urbext.core.ui.UrbexDropdownOption
import com.urbext.core.ui.UrbexFilledButton
import com.urbext.core.ui.urbexTileEnter
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.BaseRepository
import com.urbext.domain.repository.MeshRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profileRepository: ProfileRepository,
    objectRepository: ObjectRepository,
    archiveRepository: ArchiveRepository,
    meshRepository: MeshRepository,
    baseRepository: BaseRepository,
    bottomContentPadding: Dp = 0.dp
) {
    val scope = rememberCoroutineScope()
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp
    val profile by profileRepository.observeProfile().collectAsStateWithLifecycle(
        initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
    )
    val objects by objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val archives by archiveRepository.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val meshMessages by meshRepository.observeAllMessages().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by baseRepository.observeArticles().collectAsStateWithLifecycle(initialValue = emptyList())

    var callsign by remember { mutableStateOf(profile.callsign) }
    var nodeId by remember { mutableStateOf(profile.nodeId) }
    var role by remember { mutableStateOf(profile.role) }
    var emergency by remember { mutableStateOf(profile.emergencyContact) }
    var bio by remember { mutableStateOf(profile.bio) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile.updatedAt) {
        callsign = profile.callsign
        nodeId = profile.nodeId
        role = profile.role
        emergency = profile.emergencyContact
        bio = profile.bio
    }

    val profileCard: @Composable (Modifier) -> Unit = { cardMod ->
        Column(
            modifier = cardMod
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(if (isLandscape) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Локальный профиль оператора",
                    color = UrbexColors.TextPrimary,
                    fontSize = if (isLandscape) 15.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = profile.nodeId,
                    color = UrbexColors.GreenBright,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UrbexCompactInputField(
                        value = callsign,
                        onValueChange = { callsign = it },
                        label = "Позывной",
                        minHeight = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                    UrbexCompactInputField(
                        value = nodeId,
                        onValueChange = { nodeId = it },
                        label = "Mesh Node ID (HEX)",
                        minHeight = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                UrbexCompactInputField(
                    value = callsign,
                    onValueChange = { callsign = it },
                    label = "Позывной",
                    minHeight = 38.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                UrbexCompactInputField(
                    value = nodeId,
                    onValueChange = { nodeId = it },
                    label = "Mesh Node ID (HEX)",
                    minHeight = 38.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            UrbexDropdownField(
                selectedValue = role,
                label = "Роль в группе",
                compact = isLandscape,
                options = remember {
                    OperatorRole.entries.map { r ->
                        UrbexDropdownOption(value = r, label = r.label)
                    }
                },
                onSelect = { role = it },
                modifier = Modifier.fillMaxWidth()
            )

            UrbexCompactInputField(
                value = emergency,
                onValueChange = { emergency = it },
                label = "Аварийный контакт",
                minHeight = if (isLandscape) 36.dp else 38.dp,
                modifier = Modifier.fillMaxWidth()
            )
            UrbexCompactMultilineField(
                value = bio,
                onValueChange = { bio = it },
                label = "Специализация и оснащение",
                minLines = 2,
                minHeight = if (isLandscape) 52.dp else 64.dp,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = savedMsg != null,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(160))
            ) {
                Text(savedMsg.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
            }

            UrbexFilledButton(
                height = if (isLandscape) 36.dp else 40.dp,
                cornerRadius = 9.dp,
                glowColor = UrbexColors.GreenBright,
                glowRadius = 14.dp,
                glowAlpha = 0.30f,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val normalized = MeshtasticCodec.normalizeNodeId(nodeId)
                    nodeId = normalized
                    scope.launch {
                        profileRepository.updateProfile(
                            profile.copy(
                                callsign = callsign.trim().ifEmpty { profile.callsign },
                                nodeId = normalized,
                                role = role,
                                emergencyContact = emergency.trim(),
                                bio = bio.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        savedMsg = "Профиль оператора сохранён локально."
                    }
                }
            ) {
                Text("Сохранить профиль", color = UrbexColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    val summaryAndAutonomy: @Composable (Modifier) -> Unit = { colMod ->
        Column(
            modifier = colMod,
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
        ) {
            // Local Database Summary Card (4 stats matching Web ProfileView.tsx)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .urbexTileEnter(1)
                    .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                    .padding(if (isLandscape) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Сводка локальной базы",
                    color = UrbexColors.TextPrimary,
                    fontSize = if (isLandscape) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Объекты", objects.size.toString(), Modifier.weight(1f).urbexTileEnter(2, offsetDp = 8.dp), compact = isLandscape)
                    StatCard("Файлы архива", archives.size.toString(), Modifier.weight(1f).urbexTileEnter(3, offsetDp = 8.dp), compact = isLandscape)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Mesh-пакеты", meshMessages.size.toString(), Modifier.weight(1f).urbexTileEnter(4, offsetDp = 8.dp), compact = isLandscape)
                    StatCard("Справочники", articles.size.toString(), Modifier.weight(1f).urbexTileEnter(5, offsetDp = 8.dp), compact = isLandscape)
                }
            }

            // Identity Autonomy Info Card matching Web ProfileView.tsx
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .urbexTileEnter(6)
                    .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                    .padding(if (isLandscape) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Автономность идентификации",
                        color = UrbexColors.TextPrimary,
                        fontSize = if (isLandscape) 14.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Профиль не использует облачные аккаунты или серверную авторизацию. Позывной и Node ID хранятся только на этом устройстве и подписывают создаваемые вами точки и Mesh-пакеты.",
                    color = UrbexColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
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
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                profileCard(Modifier.weight(1.28f).urbexTileEnter(0))
                summaryAndAutonomy(Modifier.weight(0.92f))
            }
        } else {
            profileCard(Modifier.fillMaxWidth().urbexTileEnter(0))
            summaryAndAutonomy(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            .padding(if (compact) 9.dp else 12.dp)
    ) {
        Text(label, color = UrbexColors.TextSecondary, fontSize = if (compact) 11.sp else 12.sp)
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "StatValueTransition"
        ) { animatedValue ->
            Text(
                text = animatedValue,
                color = UrbexColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
