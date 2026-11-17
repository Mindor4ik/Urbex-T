package com.urbext.domain.usecase

import com.urbext.domain.model.ConflictStrategy
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.repository.MergeStats

class MergeUrbexDbUseCase {
    fun merge(
        current: UrbexDbPayload,
        incoming: UrbexDbPayload,
        strategy: ConflictStrategy
    ): Pair<UrbexDbPayload, MergeStats> {
        val objRes = mergeList(current.objects, incoming.objects, strategy, { it.id }, { it.updatedAt })
        val msgRes = mergeList(current.meshMessages, incoming.meshMessages, strategy, { it.id }, { it.timestamp })
        val baseRes = mergeList(current.baseArticles, incoming.baseArticles, strategy, { it.id }, { it.updatedAt })
        val archRes = mergeList(current.archiveItems, incoming.archiveItems, strategy, { it.id }, { it.updatedAt })

        val profile = when (strategy) {
            ConflictStrategy.OVERWRITE -> incoming.profile
            ConflictStrategy.KEEP_NEWER ->
                if (incoming.profile.updatedAt > current.profile.updatedAt) incoming.profile else current.profile
            ConflictStrategy.SKIP_EXISTING -> current.profile
        }

        val settings = when (strategy) {
            ConflictStrategy.OVERWRITE -> incoming.settings
            ConflictStrategy.KEEP_NEWER ->
                if (incoming.settings.updatedAt > current.settings.updatedAt) incoming.settings else current.settings
            ConflictStrategy.SKIP_EXISTING -> current.settings
        }

        val mergedPayload = UrbexDbPayload(
            objects = objRes.items.sortedByDescending { it.updatedAt },
            meshMessages = msgRes.items.sortedBy { it.timestamp },
            baseArticles = baseRes.items,
            archiveItems = archRes.items.sortedByDescending { it.createdAt },
            profile = profile,
            settings = settings
        )

        val stats = MergeStats(
            added = objRes.added + msgRes.added + baseRes.added + archRes.added,
            updated = objRes.updated + msgRes.updated + baseRes.updated + archRes.updated,
            skipped = objRes.skipped + msgRes.skipped + baseRes.skipped + archRes.skipped
        )
        return mergedPayload to stats
    }

    private data class ListMergeResult<T>(
        val items: List<T>,
        val added: Int,
        val updated: Int,
        val skipped: Int
    )

    private fun <T> mergeList(
        existing: List<T>,
        incoming: List<T>,
        strategy: ConflictStrategy,
        idSelector: (T) -> String,
        tsSelector: (T) -> Long
    ): ListMergeResult<T> {
        val map = LinkedHashMap<String, T>(existing.size + incoming.size)
        existing.forEach { map[idSelector(it)] = it }
        var added = 0
        var updated = 0
        var skipped = 0

        for (item in incoming) {
            val id = idSelector(item)
            val prev = map[id]
            if (prev == null) {
                map[id] = item
                added++
                continue
            }
            when (strategy) {
                ConflictStrategy.SKIP_EXISTING -> skipped++
                ConflictStrategy.OVERWRITE -> {
                    map[id] = item
                    updated++
                }
                ConflictStrategy.KEEP_NEWER -> {
                    if (tsSelector(item) > tsSelector(prev)) {
                        map[id] = item
                        updated++
                    } else {
                        skipped++
                    }
                }
            }
        }
        return ListMergeResult(map.values.toList(), added, updated, skipped)
    }
}
