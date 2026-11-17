package com.urbext.domain.usecase

import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupUseCasesTest {
    private fun samplePayload(updatedAt: Long, title: String): UrbexDbPayload {
        return UrbexDbPayload(
            objects = listOf(
                UrbexObject(
                    id = "obj-1",
                    title = title,
                    category = ObjectCategory.INDUSTRIAL,
                    status = ReliabilityStatus.VERIFIED,
                    lat = 55.7512,
                    lon = 37.6184,
                    elevationM = 140,
                    description = "Desc",
                    accessNotes = "North",
                    hazards = emptyList(),
                    createdAt = 1000L,
                    updatedAt = updatedAt,
                    authorNodeId = "!a1b2c3d4"
                )
            ),
            meshMessages = emptyList(),
            baseArticles = emptyList(),
            archiveItems = emptyList(),
            profile = UserProfile(
                callsign = "Сокол-1",
                nodeId = "!a1b2c3d4",
                role = OperatorRole.SCOUT,
                emergencyContact = "",
                bio = "",
                updatedAt = 1000L
            ),
            settings = AppSettings(updatedAt = 1000L)
        )
    }

    @Test
    fun mergeKeepNewerUpdatesOlderRecord() {
        val useCase = MergeUrbexDbUseCase()
        val current = samplePayload(2000L, "Старое")
        val incoming = samplePayload(5000L, "Новое")
        val (merged, stats) = useCase.merge(current, incoming, ConflictStrategy.KEEP_NEWER)
        assertEquals("Новое", merged.objects.first().title)
        assertEquals(1, stats.updated)
    }

    @Test
    fun mergeSkipExistingPreservesCurrentRecord() {
        val useCase = MergeUrbexDbUseCase()
        val current = samplePayload(2000L, "Текущее")
        val incoming = samplePayload(5000L, "Новое")
        val (merged, stats) = useCase.merge(current, incoming, ConflictStrategy.SKIP_EXISTING)
        assertEquals("Текущее", merged.objects.first().title)
        assertEquals(1, stats.skipped)
    }
}
