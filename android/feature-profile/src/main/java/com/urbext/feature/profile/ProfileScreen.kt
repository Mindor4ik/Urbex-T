package com.urbext.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.core.ui.urbexScaleOnPress
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UrbexColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Card matching Web ProfileView.tsx
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Локальный профиль оператора",
                    color = UrbexColors.TextPrimary,
                    fontSize = 17.sp,
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

            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it },
                label = { Text("Позывной") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = nodeId,
                onValueChange = { nodeId = it },
                label = { Text("Mesh Node ID (HEX)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Роль в группе", color = UrbexColors.TextSecondary, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OperatorRole.entries.chunked(2).forEach { rowRoles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowRoles.forEach { r ->
                            val selected = role == r
                            val bgColor by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Background,
                                animationSpec = tween(160),
                                label = "roleBg"
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                                animationSpec = tween(160),
                                label = "roleBorder"
                            )
                            val roleTextColor by animateColorAsState(
                                targetValue = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                                animationSpec = tween(160),
                                label = "roleText"
                            )
                            Text(
                                text = r.label,
                                color = roleTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(bgColor, RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .urbexBounceClick(0.97f) { role = r }
                                    .padding(horizontal = 10.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = emergency,
                onValueChange = { emergency = it },
                label = { Text("Аварийный контакт") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Специализация и оснащение") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = savedMsg != null,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(160))
            ) {
                Text(savedMsg.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
            }

            val saveBtnInteraction = remember { MutableInteractionSource() }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
                interactionSource = saveBtnInteraction,
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .urbexScaleOnPress(saveBtnInteraction, 0.96f)
            ) {
                Text("Сохранить профиль", color = UrbexColors.Background, fontWeight = FontWeight.SemiBold)
            }
        }

        // Local Database Summary Card (4 stats matching Web ProfileView.tsx)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Сводка локальной базы",
                color = UrbexColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Объекты", objects.size.toString(), Modifier.weight(1f))
                StatCard("Файлы архива", archives.size.toString(), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Mesh-пакеты", meshMessages.size.toString(), Modifier.weight(1f))
                StatCard("Справочники", articles.size.toString(), Modifier.weight(1f))
            }
        }

        // Identity Autonomy Info Card matching Web ProfileView.tsx
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UrbexColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, UrbexColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = UrbexColors.Amber, modifier = Modifier.size(18.dp))
                Text(
                    text = "Автономность идентификации",
                    color = UrbexColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Профиль не использует облачные аккаунты или серверную авторизацию. Позывной и Node ID хранятся только на этом устройстве и подписывают создаваемые вами точки и Mesh-пакеты.",
                color = UrbexColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(UrbexColors.Background, RoundedCornerShape(10.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, color = UrbexColors.TextSecondary, fontSize = 12.sp)
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "StatValueTransition"
        ) { animatedValue ->
            Text(
                text = animatedValue,
                color = UrbexColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
