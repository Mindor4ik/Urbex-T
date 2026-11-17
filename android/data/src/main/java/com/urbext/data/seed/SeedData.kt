package com.urbext.data.seed

import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.MeshChannel
import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile

object SeedData {
    val channels = listOf(
        MeshChannel("ch-primary", "Основной канал", 3, isEmergency = false),
        MeshChannel("ch-sos", "SOS-Аварийный", 7, isEmergency = true)
    )

    fun defaultOfflineSectors(): List<OfflineMapSector> {
        val now = System.currentTimeMillis()
        return listOf(
            OfflineMapSector("sec-central", "Сектор Центр (спутник/вектор)", 55.751244, 37.618423, 6.0, 11, 17, 14.2, now - 172_800_000L),
            OfflineMapSector("sec-north", "Промзона Север-3", 55.764, 37.631, 4.0, 12, 17, 8.6, now - 64_800_000L)
        )
    }

    fun defaultProfile(): UserProfile = UserProfile(
        id = "local",
        callsign = "Сокол-1",
        nodeId = "!a48f20c1",
        role = OperatorRole.SCOUT,
        emergencyContact = "Локальный контакт",
        bio = "Автономный полевой узел. Промзоны, коллекторы, фотофиксация.",
        updatedAt = System.currentTimeMillis()
    )

    fun createPayload(): UrbexDbPayload {
        val now = System.currentTimeMillis()
        val objects = listOf(
            UrbexObject(
                id = "obj-cooling-tower",
                title = "Градирня ТЭЦ-4 (блок Б)",
                category = ObjectCategory.INDUSTRIAL,
                status = ReliabilityStatus.VERIFIED,
                lat = 55.7554,
                lon = 37.6248,
                elevationM = 148,
                description = "Четыре железобетонные башенные градирни 1974 года. Внешняя лестница сохранна до +32 м.",
                accessNotes = "Подход с восточной насыпи вдоль старой ж/д ветки.",
                hazards = listOf("Осыпающийся бетон свода", "Открытые приямки"),
                createdAt = now - 432_000_000L,
                updatedAt = now - 86_400_000L,
                authorNodeId = "!a48f20c1"
            ),
            UrbexObject(
                id = "obj-bunker-west",
                title = "Защитный узел связи В-12",
                category = ObjectCategory.BUNKER,
                status = ReliabilityStatus.UNVERIFIED,
                lat = 55.7476,
                lon = 37.6104,
                elevationM = 126,
                description = "Заглублённый оголовок с гермодверью ГД-1. Нижний ярус возможно подтоплен.",
                accessNotes = "Вентиляционная шахта в 45 м к северу от портала.",
                hazards = listOf("Застойный воздух", "Ржавые скобы"),
                createdAt = now - 259_200_000L,
                updatedAt = now - 43_200_000L,
                authorNodeId = "!a48f20c1"
            ),
            UrbexObject(
                id = "obj-sanatorium",
                title = "Санаторий «Лесной», западное крыло",
                category = ObjectCategory.CIVILIAN,
                status = ReliabilityStatus.CONTESTED,
                lat = 55.7582,
                lon = 37.6092,
                elevationM = 159,
                description = "Кирпичный корпус 1958 года с арочной галереей второго этажа.",
                accessNotes = "Сведения о патруле противоречивы, требуется перепроверка.",
                hazards = listOf("Ветхие перекрытия", "Битое стекло"),
                createdAt = now - 604_800_000L,
                updatedAt = now - 129_600_000L,
                authorNodeId = "!a48f20c1"
            ),
            UrbexObject(
                id = "obj-substation",
                title = "Щитовой зал подстанции 110 кВ",
                category = ObjectCategory.INFRASTRUCTURE,
                status = ReliabilityStatus.HAZARDOUS,
                lat = 55.7468,
                lon = 37.6289,
                elevationM = 138,
                description = "Выведенный из эксплуатации щит управления. Северная галерея граничит с действующим ОРУ.",
                accessNotes = "Вход со стороны действующих ячеек строго запрещён.",
                hazards = listOf("Близость действующей ЛЭП", "Кабельный канал"),
                createdAt = now - 518_400_000L,
                updatedAt = now - 21_600_000L,
                authorNodeId = "!a48f20c1"
            )
        )

        val messages = emptyList<MeshMessage>()

        val articles = listOf(
            BaseArticle(
                id = "base-gas-safety",
                title = "Контроль воздушной среды в подземных узлах",
                section = BaseSection.SAFETY,
                summary = "Пороги O2/CO/H2S и правила спуска в коллекторы.",
                content = "1. Выдержка открытого люка минимум 15 минут.\n2. Кислород O2 >= 19.5%, CO < 35 ppm, H2S < 10 ppm.\n3. При отсутствии газоанализатора запрещён спуск в застойные нижние ярусы.",
                updatedAt = now - 864_000_000L,
                pinned = true
            ),
            BaseArticle(
                id = "base-datums",
                title = "Системы координат: DD, DMS и UTM",
                section = BaseSection.NAVIGATION,
                summary = "Точность WGS-84 и минимизация дрейфа GPS.",
                content = "1. DD (5 знаков) даёт точность около 1.1 м.\n2. UTM удобен для оценки дистанций в метрах.\n3. У бетонных башен снимайте координаты на открытом участке.",
                updatedAt = now - 432_000_000L,
                pinned = true
            ),
            BaseArticle(
                id = "base-urbexdb",
                title = "Формат резервной копии .urbexdb",
                section = BaseSection.ARCHITECTURE,
                summary = "Контроль целостности и слияние конфликтов.",
                content = "Контейнер .urbexdb (v1.0.0) проверяется по контрольной сумме uxdb-* до записи в Room. Поддерживаются стратегии KEEP_NEWER, SKIP_EXISTING и OVERWRITE.",
                updatedAt = now - 172_800_000L,
                pinned = false
            )
        )

        val archive = listOf(
            ArchiveItem(
                id = "arch-note-1",
                title = "Полевой журнал — замер сектор Б",
                type = ArchiveMediaType.NOTE,
                objectId = "obj-bunker-west",
                mimeType = "text/plain",
                sizeBytes = 320L,
                textContent = "Температура +3°C. Вентиляционный канал чист до отметки -4 м.",
                notes = "Локальная заметка PDA",
                createdAt = now - 180_000_000L,
                updatedAt = now - 180_000_000L
            )
        )

        return UrbexDbPayload(
            objects = objects,
            meshMessages = messages,
            baseArticles = articles,
            archiveItems = archive,
            profile = defaultProfile(),
            settings = AppSettings(
                meshEnabled = false,
                mapLayer = MapLayerMode.SATELLITE,
                offlineSectors = defaultOfflineSectors(),
                updatedAt = now
            )
        )
    }
}
