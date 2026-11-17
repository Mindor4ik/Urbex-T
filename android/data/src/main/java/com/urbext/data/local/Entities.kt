package com.urbext.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.MeshPacketKind
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile

@Entity(
    tableName = "urbex_objects",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class UrbexObjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val status: String,
    val lat: Double,
    val lon: Double,
    val elevationM: Int,
    val description: String,
    val accessNotes: String,
    val hazardsJoined: String,
    val photoUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val authorNodeId: String
)

@Entity(
    tableName = "mesh_messages",
    indices = [
        Index(value = ["channelId", "timestamp"])
    ]
)
data class MeshMessageEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val senderNodeId: String,
    val senderCallsign: String,
    val kind: String,
    val body: String,
    val lat: Double?,
    val lon: Double?,
    val timestamp: Long,
    val hopLimit: Int,
    val acknowledged: Boolean,
    val frameHex: String
)

@Entity(
    tableName = "base_articles",
    indices = [
        Index(value = ["section"]),
        Index(value = ["pinned", "updatedAt"])
    ]
)
data class BaseArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val section: String,
    val summary: String,
    val content: String,
    val updatedAt: Long,
    val pinned: Boolean
)

@Entity(
    tableName = "archive_items",
    indices = [
        Index(value = ["type"]),
        Index(value = ["objectId"]),
        Index(value = ["createdAt"])
    ]
)
data class ArchiveItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val objectId: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val contentUri: String?,
    val textContent: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val callsign: String,
    val nodeId: String,
    val role: String,
    val emergencyContact: String,
    val bio: String,
    val updatedAt: Long
)

fun UrbexObjectEntity.toDomain(): UrbexObject = UrbexObject(
    id = id,
    title = title,
    category = runCatching { ObjectCategory.valueOf(category) }.getOrDefault(ObjectCategory.INDUSTRIAL),
    status = runCatching { ReliabilityStatus.valueOf(status) }.getOrDefault(ReliabilityStatus.UNVERIFIED),
    lat = lat,
    lon = lon,
    elevationM = elevationM,
    description = description,
    accessNotes = accessNotes,
    hazards = if (hazardsJoined.isBlank()) emptyList() else hazardsJoined.split("||"),
    photoUri = photoUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    authorNodeId = authorNodeId
)

fun UrbexObject.toEntity(): UrbexObjectEntity = UrbexObjectEntity(
    id = id,
    title = title,
    category = category.name,
    status = status.name,
    lat = lat,
    lon = lon,
    elevationM = elevationM,
    description = description,
    accessNotes = accessNotes,
    hazardsJoined = hazards.joinToString("||"),
    photoUri = photoUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
    authorNodeId = authorNodeId
)

fun MeshMessageEntity.toDomain(): MeshMessage = MeshMessage(
    id = id,
    channelId = channelId,
    senderNodeId = senderNodeId,
    senderCallsign = senderCallsign,
    kind = runCatching { MeshPacketKind.valueOf(kind) }.getOrDefault(MeshPacketKind.TEXT),
    body = body,
    lat = lat,
    lon = lon,
    timestamp = timestamp,
    hopLimit = hopLimit,
    acknowledged = acknowledged,
    frameHex = frameHex
)

fun MeshMessage.toEntity(): MeshMessageEntity = MeshMessageEntity(
    id = id,
    channelId = channelId,
    senderNodeId = senderNodeId,
    senderCallsign = senderCallsign,
    kind = kind.name,
    body = body,
    lat = lat,
    lon = lon,
    timestamp = timestamp,
    hopLimit = hopLimit,
    acknowledged = acknowledged,
    frameHex = frameHex
)

fun BaseArticleEntity.toDomain(): BaseArticle = BaseArticle(
    id = id,
    title = title,
    section = runCatching { BaseSection.valueOf(section) }.getOrDefault(BaseSection.FIELD_NOTE),
    summary = summary,
    content = content,
    updatedAt = updatedAt,
    pinned = pinned
)

fun BaseArticle.toEntity(): BaseArticleEntity = BaseArticleEntity(
    id = id,
    title = title,
    section = section.name,
    summary = summary,
    content = content,
    updatedAt = updatedAt,
    pinned = pinned
)

fun ArchiveItemEntity.toDomain(): ArchiveItem = ArchiveItem(
    id = id,
    title = title,
    type = runCatching { ArchiveMediaType.valueOf(type) }.getOrDefault(ArchiveMediaType.NOTE),
    objectId = objectId,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    contentUri = contentUri,
    textContent = textContent,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ArchiveItem.toEntity(): ArchiveItemEntity = ArchiveItemEntity(
    id = id,
    title = title,
    type = type.name,
    objectId = objectId,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    contentUri = contentUri,
    textContent = textContent,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    callsign = callsign,
    nodeId = nodeId,
    role = runCatching { OperatorRole.valueOf(role) }.getOrDefault(OperatorRole.SCOUT),
    emergencyContact = emergencyContact,
    bio = bio,
    updatedAt = updatedAt
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    id = id,
    callsign = callsign,
    nodeId = nodeId,
    role = role.name,
    emergencyContact = emergencyContact,
    bio = bio,
    updatedAt = updatedAt
)
