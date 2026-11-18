package com.urbext.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbext.core.mesh.MeshtasticCodec
import com.urbext.core.ui.UrbexColors
import com.urbext.core.ui.urbexBounceClick
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.UserProfile
import com.urbext.domain.repository.ArchiveRepository
import com.urbext.domain.repository.ObjectRepository
import com.urbext.domain.repository.ProfileRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profileRepository: ProfileRepository,
    objectRepository: ObjectRepository,
    archiveRepository: ArchiveRepository
) {
    val scope = rememberCoroutineScope()
    val profile by profileRepository.observeProfile().collectAsStateWithLifecycle(
        initialValue = UserProfile("local", "Сокол-1", "!a48f20c1", OperatorRole.SCOUT, "", "", 0L)
    )
    val objects by objectRepository.observeObjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val archives by archiveRepository.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Локальный профиль оператора", color = UrbexColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Объекты", objects.size.toString(), Modifier.weight(1f))
            StatCard("Архив", archives.size.toString(), Modifier.weight(1f))
        }

        OutlinedTextField(value = callsign, onValueChange = { callsign = it }, label = { Text("Позывной") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = nodeId, onValueChange = { nodeId = it }, label = { Text("Mesh Node ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("Роль в группе", color = UrbexColors.TextSecondary, fontSize = 12.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OperatorRole.entries.forEach { r ->
                val selected = role == r
                val bgColor by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Elevated else UrbexColors.Surface,
                    animationSpec = tween(160),
                    label = "roleBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (selected) UrbexColors.Green else UrbexColors.Border,
                    animationSpec = tween(160),
                    label = "roleBorder"
                )
                Text(
                    text = r.label,
                    color = if (selected) UrbexColors.TextPrimary else UrbexColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .urbexBounceClick(0.98f) { role = r }
                        .padding(12.dp)
                )
            }
        }

        OutlinedTextField(value = emergency, onValueChange = { emergency = it }, label = { Text("Аварийный контакт") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Специализация") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        AnimatedVisibility(
            visible = savedMsg != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Text(savedMsg.orEmpty(), color = UrbexColors.GreenBright, fontSize = 12.sp)
        }

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = UrbexColors.Green),
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
                    savedMsg = "Профиль сохранён локально."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить профиль", color = UrbexColors.Background)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(UrbexColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, UrbexColors.Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, color = UrbexColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = UrbexColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
