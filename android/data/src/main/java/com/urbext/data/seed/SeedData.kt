package com.urbext.data.seed

import com.urbext.core.radio.RadioPlanUtils
import com.urbext.domain.model.AppSettings
import com.urbext.domain.model.ArchiveItem
import com.urbext.domain.model.ArchiveMediaType
import com.urbext.domain.model.BaseArticle
import com.urbext.domain.model.BaseSection
import com.urbext.domain.model.EntryDifficulty
import com.urbext.domain.model.GeoPoint
import com.urbext.domain.model.MapLayerMode
import com.urbext.domain.model.MeshChannel
import com.urbext.domain.model.MeshMessage
import com.urbext.domain.model.ObjectCategory
import com.urbext.domain.model.ObjectParam
import com.urbext.domain.model.OfflineMapSector
import com.urbext.domain.model.OperatorRole
import com.urbext.domain.model.ReliabilityStatus
import com.urbext.domain.model.SecurityLevel
import com.urbext.domain.model.UrbexDbPayload
import com.urbext.domain.model.UrbexObject
import com.urbext.domain.model.UserProfile

object SeedData {
    const val PHOTO_COOLING_TOWER = "file:///android_asset/seed_photos/object_cooling_tower.jpg"
    const val PHOTO_BUNKER_WEST = "file:///android_asset/seed_photos/object_bunker_entrance.jpg"
    const val PHOTO_SANATORIUM = "file:///android_asset/seed_photos/object_sanatorium_wing.jpg"
    const val PHOTO_SUBSTATION = "file:///android_asset/seed_photos/object_substation_hall.jpg"

    val seedPhotoByObjectId: Map<String, String> = mapOf(
        "obj-cooling-tower" to PHOTO_COOLING_TOWER,
        "obj-bunker-west" to PHOTO_BUNKER_WEST,
        "obj-sanatorium" to PHOTO_SANATORIUM,
        "obj-substation" to PHOTO_SUBSTATION
    )

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
        emergencyContact = "Канал Recon-1 / +7 (900) 000-11-22",
        bio = "Автономный полевой узел. Основная специализация: промзоны, подземные коллекторы и фотофиксация конструкций.",
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
                photoUri = PHOTO_COOLING_TOWER,
                photos = listOf(PHOTO_COOLING_TOWER),
                securityLevel = SecurityLevel.LOW,
                entryDifficulty = EntryDifficulty.MODERATE,
                floorsOrDepth = "+32 м (внешняя лестница)",
                customParams = listOf(
                    ObjectParam("Год постройки", "1974"),
                    ObjectParam("Конструкция", "Железобетон")
                ),
                zone = listOf(
                    GeoPoint(55.7561, 37.6238),
                    GeoPoint(55.7561, 37.6258),
                    GeoPoint(55.7547, 37.6258),
                    GeoPoint(55.7547, 37.6238)
                ),
                createdAt = now - 432_000_000L,
                updatedAt = now - 86_400_000L,
                authorNodeId = "!94f1c208"
            ),
            UrbexObject(
                id = "obj-bunker-west",
                title = "Защитный узел связи В-12",
                category = ObjectCategory.BUNKER,
                status = ReliabilityStatus.UNVERIFIED,
                lat = 55.7476,
                lon = 37.6104,
                elevationM = 126,
                description = "Заглублённый бетонный оголовок с двойной гермодверью ГД-1. Нижний ярус подтоплен на 20–30 см.",
                accessNotes = "Вентиляционная шахта в 45 м к северу от портала. Требуется проверка газоанализатором.",
                hazards = listOf("Застойный воздух / низкий O2", "Ржавые скобы спуска"),
                photoUri = PHOTO_BUNKER_WEST,
                photos = listOf(PHOTO_BUNKER_WEST),
                securityLevel = SecurityLevel.NONE,
                entryDifficulty = EntryDifficulty.HARD,
                floorsOrDepth = "-14 м (2 яруса)",
                customParams = listOf(
                    ObjectParam("Затопление", "20–30 см"),
                    ObjectParam("Гермодверь", "ГД-1")
                ),
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
                accessNotes = "Сведения о режиме охраны противоречивы, требуется перепроверка.",
                hazards = listOf("Ветхие деревянные перекрытия чердака", "Битое стекло"),
                photoUri = PHOTO_SANATORIUM,
                photos = listOf(PHOTO_SANATORIUM),
                securityLevel = SecurityLevel.LOW,
                entryDifficulty = EntryDifficulty.EASY,
                floorsOrDepth = "3 этажа + чердак",
                customParams = listOf(
                    ObjectParam("Год постройки", "1958")
                ),
                createdAt = now - 604_800_000L,
                updatedAt = now - 129_600_000L,
                authorNodeId = "!3c08a91e"
            ),
            UrbexObject(
                id = "obj-substation",
                title = "Щитовой зал подстанции 110 кВ",
                category = ObjectCategory.INFRASTRUCTURE,
                status = ReliabilityStatus.HAZARDOUS,
                lat = 55.7468,
                lon = 37.6289,
                elevationM = 138,
                description = "Выведенный из эксплуатации главный щит управления. Северная галерея граничит с действующим ОРУ.",
                accessNotes = "Вход со стороны действующих ячеек строго запрещён.",
                hazards = listOf("Близость действующей ЛЭП", "Масляные следы в кабельном канале"),
                photoUri = PHOTO_SUBSTATION,
                photos = listOf(PHOTO_SUBSTATION),
                securityLevel = SecurityLevel.HIGH,
                entryDifficulty = EntryDifficulty.TECHNICAL,
                floorsOrDepth = "1 этаж + кабельный полуэтаж",
                customParams = listOf(
                    ObjectParam("Напряжение рядом", "110 кВ ОРУ")
                ),
                zone = listOf(
                    GeoPoint(55.7473, 37.6281),
                    GeoPoint(55.7474, 37.6297),
                    GeoPoint(55.7463, 37.6296),
                    GeoPoint(55.7464, 37.6280)
                ),
                createdAt = now - 518_400_000L,
                updatedAt = now - 21_600_000L,
                authorNodeId = "!94f1c208"
            )
        )

        val messages = emptyList<MeshMessage>()

        val articles = listOf(
            BaseArticle(
                id = "base-gas-safety",
                title = "Контроль воздушной среды в подземных узлах",
                section = BaseSection.SAFETY,
                summary = "Пороги O2/CO/H2S, правила спуска в коллекторы и признаки застойных зон.",
                content = "1. Перед спуском в замкнутые объёмы обязательна выдержка люка открытым не менее 15 минут.\n2. Кислород O2: норма 20.9%, предел 19.5%. Угарный газ CO: порог 35 ppm. Сероводород H2S: порог 10 ppm.\n3. При отсутствии газоанализатора запрещён спуск в застойные нижние ярусы.",
                updatedAt = now - 864_000_000L,
                pinned = true
            ),
            BaseArticle(
                id = "base-structural-check",
                title = "Оценка несущих конструкций на объектах 1950–1980 гг.",
                section = BaseSection.SAFETY,
                summary = "Признаки аварийного перекрытия, коррозия арматуры и безопасные траектории.",
                content = "1. Деревянные перекрытия: двигайтесь вдоль капитальных стен и несущих балок.\n2. Сборный железобетон: продольные трещины на нижней грани плит и оголённая арматура указывают на потерю прочности.\n3. Металлоконструкции градирен и эстакад: проверяйте опорные косынки и болтовые узлы.",
                updatedAt = now - 691_200_000L,
                pinned = false
            ),
            BaseArticle(
                id = "base-datums",
                title = "Системы координат: DD, DMS и UTM в полевых условиях",
                section = BaseSection.NAVIGATION,
                summary = "Быстрый перевод форматов, привязка к сетке и минимизация ошибки дрейфа GPS.",
                content = "1. DD (5 знаков) даёт точность около 1.1 м и оптимален для Meshtastic.\n2. DMS удобен для сверки с бумажными топокартами.\n3. UTM позволяет оценивать дистанции в метрах.\n4. У бетонных башен снимайте координаты на открытом участке.",
                updatedAt = now - 432_000_000L,
                pinned = true
            ),
            BaseArticle(
                id = "base-urbexdb",
                title = "Спецификация контейнера .urbexdb и архитектура модулей",
                section = BaseSection.ARCHITECTURE,
                summary = "Структура резервной копии, контроль целостности и слияние конфликтов.",
                content = "Контейнер .urbexdb (v1.0.0) проверяется по контрольной сумме uxdb-* до записи в Room. Поддерживаются стратегии KEEP_NEWER, SKIP_EXISTING и OVERWRITE.",
                updatedAt = now - 172_800_000L,
                pinned = false
            )
        )

        val archive = listOf(
            ArchiveItem(
                id = "arch-1",
                title = "Общий вид градирни с восточной насыпи",
                type = ArchiveMediaType.PHOTO,
                objectId = "obj-cooling-tower",
                mimeType = "image/jpeg",
                sizeBytes = 945_181L,
                contentUri = PHOTO_COOLING_TOWER,
                notes = "Съёмка при сплошной облачности, видимость периметра 400 м.",
                createdAt = now - 430_000_000L,
                updatedAt = now - 430_000_000L
            ),
            ArchiveItem(
                id = "arch-2",
                title = "Гермодверь узла В-12 — маркировка петли",
                type = ArchiveMediaType.PHOTO,
                objectId = "obj-bunker-west",
                mimeType = "image/jpeg",
                sizeBytes = 1_216_039L,
                contentUri = PHOTO_BUNKER_WEST,
                notes = "Штурвал заклинен коррозией, нижний уплотнитель отсутствует.",
                createdAt = now - 250_000_000L,
                updatedAt = now - 250_000_000L
            ),
            ArchiveItem(
                id = "arch-3",
                title = "Полевой журнал — замер сектор Б",
                type = ArchiveMediaType.NOTE,
                objectId = "obj-bunker-west",
                mimeType = "text/plain",
                sizeBytes = 640L,
                textContent = "Дата выхода: 14.11.\nТемпература воздуха +3°C, влажность высокая.\nВ 15 м от портала стены сухие, проход свободен до второй гермодвери.",
                notes = "Запись из локального блокнота PDA",
                createdAt = now - 180_000_000L,
                updatedAt = now - 180_000_000L
            ),
            ArchiveItem(
                id = "arch-4",
                title = "Щитовой зал — схема ячеек 110 кВ",
                type = ArchiveMediaType.DOCUMENT,
                objectId = "obj-substation",
                mimeType = "text/markdown",
                sizeBytes = 1_280L,
                textContent = "# Сводка по щитовому залу\n\n- Панели №1–№6: обесточены, приборы демонтированы частично.\n- Кабельный полуэтаж: высокий риск задымления при замыкании на соседнем ОРУ.\n- Рекомендация: статус «Опасен» не снимать.",
                notes = "Технический паспорт осмотра",
                createdAt = now - 90_000_000L,
                updatedAt = now - 90_000_000L
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
                radioChannels = RadioPlanUtils.createDefaultRadioChannels(now),
                offlineSectors = defaultOfflineSectors(),
                updatedAt = now
            )
        )
    }

    fun createEmptyRegionPayload(
        baseProfile: UserProfile = defaultProfile(),
        baseSettings: AppSettings = AppSettings(
            meshEnabled = false,
            mapLayer = MapLayerMode.SATELLITE,
            offlineSectors = emptyList()
        )
    ): UrbexDbPayload {
        val now = System.currentTimeMillis()
        return UrbexDbPayload(
            objects = emptyList(),
            meshMessages = emptyList(),
            baseArticles = emptyList(),
            archiveItems = emptyList(),
            profile = baseProfile.copy(updatedAt = now),
            settings = baseSettings.copy(
                radioChannels = baseSettings.radioChannels.ifEmpty {
                    RadioPlanUtils.createDefaultRadioChannels(now)
                },
                offlineSectors = emptyList(),
                updatedAt = now
            )
        )
    }
}
