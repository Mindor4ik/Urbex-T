import type {
  AppSettings,
  ArchiveItem,
  BaseArticle,
  MeshChannel,
  MeshMessage,
  UrbexDbPayload,
  UrbexObject,
  UserProfile,
} from '../core/types';

export const DEFAULT_CENTER = { lat: 55.751244, lon: 37.618423 };

export const MESH_CHANNELS: MeshChannel[] = [
  {
    id: 'ch-primary',
    name: 'Основной канал',
    hopLimit: 3,
  },
  {
    id: 'ch-sos',
    name: 'SOS-Аварийный',
    hopLimit: 7,
    isEmergency: true,
  },
];

export function createSeedPayload(): UrbexDbPayload {
  const now = Date.now();
  const profile: UserProfile = {
    id: 'local',
    callsign: 'Сокол-1',
    nodeId: '!a48f20c1',
    role: 'scout',
    emergencyContact: 'Канал Recon-1 / +7 (900) 000-11-22',
    bio: 'Автономный полевой узел. Основная специализация: промзоны, подземные коллекторы и фотофиксация конструкций.',
    updatedAt: now - 86_400_000,
  };

  const settings: AppSettings = {
    id: 'primary',
    powerProfile: 'balanced',
    gpsIntervalMs: 10_000,
    coordinateFormat: 'dd',
    imageCacheLimitMb: 64,
    pageSize: 12,
    meshEnabled: false,
    mapLayer: 'satellite',
    autoSyncOnMesh: false,
    lowMemoryMode: false,
    offlineSectors: [
      {
        id: 'sec-central',
        name: 'Сектор Центр (вектор + сетка)',
        centerLat: DEFAULT_CENTER.lat,
        centerLon: DEFAULT_CENTER.lon,
        radiusKm: 6,
        zoomMin: 11,
        zoomMax: 17,
        sizeMb: 14.2,
        cachedAt: now - 172_800_000,
      },
      {
        id: 'sec-north',
        name: 'Промзона Север-3',
        centerLat: 55.764,
        centerLon: 37.631,
        radiusKm: 4,
        zoomMin: 12,
        zoomMax: 17,
        sizeMb: 8.6,
        cachedAt: now - 64_800_000,
      },
    ],
    updatedAt: now - 3_600_000,
  };

  const objects: UrbexObject[] = [
    {
      id: 'obj-cooling-tower',
      title: 'Градирня ТЭЦ-4 (блок Б)',
      category: 'industrial',
      status: 'verified',
      lat: 55.7554,
      lon: 37.6248,
      elevationM: 148,
      description:
        'Четыре железобетонные башенные градирни постройки 1974 года. Внешняя лестница сохранна до отметки +32 м, внутренняя чаша сухая.',
      accessNotes:
        'Подход с восточной насыпи вдоль старой ж/д ветки. Западный КПП под наблюдением.',
      hazards: ['Осыпающийся бетон свода', 'Открытые приямки в насосной'],
      photoUrl: '/src/assets/images/object_cooling_tower_1794947923914.jpg',
      createdAt: now - 432_000_000,
      updatedAt: now - 86_400_000,
      authorNodeId: '!94f1c208',
    },
    {
      id: 'obj-bunker-west',
      title: 'Защитный узел связи В-12',
      category: 'bunker',
      status: 'unverified',
      lat: 55.7476,
      lon: 37.6104,
      elevationM: 126,
      description:
        'Заглублённый бетонный оголовок с двойной гермодверью ГД-1. По предварительным данным, нижний ярус подтоплен на 20–30 см.',
      accessNotes:
        'Вентиляционная шахта в 45 м к северу от портала. Требуется проверка газоанализатором.',
      hazards: ['Застойный воздух / низкий O2', 'Ржавые скобы спуска'],
      photoUrl: '/src/assets/images/object_bunker_entrance_1794947924766.jpg',
      createdAt: now - 259_200_000,
      updatedAt: now - 43_200_000,
      authorNodeId: '!a48f20c1',
    },
    {
      id: 'obj-sanatorium',
      title: 'Санаторий «Лесной», западное крыло',
      category: 'civilian',
      status: 'contested',
      lat: 55.7582,
      lon: 37.6092,
      elevationM: 159,
      description:
        'Кирпичный корпус 1958 года с арочной галереей второго этажа. В части помещений зафиксированы следы недавнего демонтажа рам.',
      accessNotes:
        'Сведения о режиме охраны противоречивы: два отчёта за прошлый месяц расходятся по активности патруля.',
      hazards: ['Ветхие деревянные перекрытия чердака', 'Битое стекло'],
      photoUrl: '/src/assets/images/object_sanatorium_wing_1794947925290.jpg',
      createdAt: now - 604_800_000,
      updatedAt: now - 129_600_000,
      authorNodeId: '!3c08a91e',
    },
    {
      id: 'obj-substation',
      title: 'Щитовой зал подстанции 110 кВ',
      category: 'infrastructure',
      status: 'hazardous',
      lat: 55.7468,
      lon: 37.6289,
      elevationM: 138,
      description:
        'Выведенный из эксплуатации главный щит управления с аналоговой телеметрией. Северная галерея примыкает к действующему ОРУ.',
      accessNotes:
        'Вход строго запрещён со стороны действующих ячеек. Посещение только при подтверждённом снятии напряжения.',
      hazards: ['Близость действующей ЛЭП', 'Масляные следы в кабельном канале'],
      photoUrl: '/src/assets/images/object_substation_hall_1794947925813.jpg',
      createdAt: now - 518_400_000,
      updatedAt: now - 21_600_000,
      authorNodeId: '!94f1c208',
    },
  ];

  const meshMessages: MeshMessage[] = [];

  const baseArticles: BaseArticle[] = [
    {
      id: 'base-gas-safety',
      title: 'Контроль воздушной среды в подземных узлах',
      section: 'safety',
      summary: 'Пороги O2/CO/H2S, правила спуска в коллекторы и признаки застойных зон.',
      content: `1. Перед спуском в замкнутые объёмы (оголовки, дренажные камеры, кабельные тоннели) обязательна выдержка люка открытым не менее 15 минут.
2. Критические пороги:
   - Кислород (O2): норма 20.9%, нижний предел допуска 19.5%.
   - Угарный газ (CO): порог тревоги 35 ppm.
   - Сероводород (H2S): порог 10 ppm (притупляет обоняние при высокой концентрации).
3. Если газоанализатор отсутствует, запрещено спускаться ниже первого яруса при наличии запаха затхлости, гниения или видимой дымки у пола.`,
      updatedAt: now - 864_000_000,
      pinned: true,
    },
    {
      id: 'base-structural-check',
      title: 'Оценка несущих конструкций на объектах 1950–1980 гг.',
      section: 'safety',
      summary: 'Признаки аварийного перекрытия, коррозия арматуры и безопасные траектории.',
      content: `1. Деревянные перекрытия старых санаториев и казарм: двигайтесь вдоль капитальных стен и несущих балок, избегайте центра пролётов со следами протечек.
2. Сборный железобетон: продольные трещины на нижней грани плит ПК и оголённая ржавая арматура указывают на потерю преднапряжения.
3. Металлоконструкции градирен и эстакад: проверяйте опорные косынки и болтовые узлы, а не настил — прогнивший лист может держаться только на ржавчине.`,
      updatedAt: now - 691_200_000,
      pinned: false,
    },
    {
      id: 'base-datums-coords',
      title: 'Системы координат: DD, DMS и UTM в полевых условиях',
      section: 'navigation',
      summary: 'Быстрый перевод форматов, привязка к сетке и минимизация ошибки дрейфа GPS.',
      content: `1. DD (Decimal Degrees, WGS-84): оптимален для обмена пакетами по Meshtastic и быстрого ввода. Точность 5 знаков после запятой соответствует ~1.1 м.
2. DMS (градусы-минуты-секунды): удобно для сверки с бумажными топокартами.
3. UTM (метры в зоне): позволяет оценивать дистанции на сетке в метрах без тригонометрии.
4. В плотной застройке и у бетонных башен переотражение сигнала даёт ошибку 15–40 м — фиксируйте координаты с открытой площадки перед входом.`,
      updatedAt: now - 432_000_000,
      pinned: true,
    },
    {
      id: 'base-urbexdb-spec',
      title: 'Спецификация контейнера .urbexdb и архитектура модулей',
      section: 'architecture',
      summary: 'Структура резервной копии, контроль целостности и принципы энергосбережения PDA.',
      content: `Формат .urbexdb (v1.0.0) представляет собой автономный пакет синхронизации:
- manifest: идентификатор формата urbex-t-db, версия схемы, отметка времени, NodeID источника и 64-битная контрольная сумма FNV-1a/DJB2.
- payload: таблицы objects, meshMessages, baseArticles, archiveItems, profile и settings.
- При импорте проверяется схема и контрольная сумма; при расхождении импорт блокируется без повреждения текущей базы.
- Конфликты записей решаются по одной из трёх стратегий: «Оставить новее», «Пропустить существующие» или «Перезаписать».`,
      updatedAt: now - 172_800_000,
      pinned: false,
    },
  ];

  const archiveItems: ArchiveItem[] = [
    {
      id: 'arch-1',
      title: 'Общий вид градирни с восточной насыпи',
      type: 'photo',
      objectId: 'obj-cooling-tower',
      mimeType: 'image/jpeg',
      sizeBytes: 248_400,
      dataUrl: '/src/assets/images/object_cooling_tower_1794947923914.jpg',
      notes: 'Съёмка при сплошной облачности, видимость периметра 400 м.',
      createdAt: now - 430_000_000,
      updatedAt: now - 430_000_000,
    },
    {
      id: 'arch-2',
      title: 'Гермодверь узла В-12 — маркировка петли',
      type: 'photo',
      objectId: 'obj-bunker-west',
      mimeType: 'image/jpeg',
      sizeBytes: 215_900,
      dataUrl: '/src/assets/images/object_bunker_entrance_1794947924766.jpg',
      notes: 'Штурвал заклинен коррозией, нижний уплотнитель отсутствует.',
      createdAt: now - 250_000_000,
      updatedAt: now - 250_000_000,
    },
    {
      id: 'arch-3',
      title: 'Полевой журнал — замер сектор Б',
      type: 'note',
      objectId: 'obj-bunker-west',
      mimeType: 'text/plain',
      sizeBytes: 640,
      textContent:
        'Дата выхода: 14.11.\nТемпература воздуха +3°C, влажность высокая.\nВ 15 м от портала стены сухие, проход свободен до второй гермодвери.',
      notes: 'Запись из локального блокнота PDA',
      createdAt: now - 180_000_000,
      updatedAt: now - 180_000_000,
    },
    {
      id: 'arch-4',
      title: 'Щитовой зал — схема ячеек 110 кВ',
      type: 'document',
      objectId: 'obj-substation',
      mimeType: 'text/markdown',
      sizeBytes: 1_280,
      textContent:
        '# Сводка по щитовому залу\n\n- Панели №1–№6: обесточены, приборы демонтированы частично.\n- Кабельный полуэтаж: высокий риск задымления при замыкании на соседнем ОРУ.\n- Рекомендация: статус «Опасен» не снимать.',
      notes: 'Технический паспорт осмотра',
      createdAt: now - 90_000_000,
      updatedAt: now - 90_000_000,
    },
  ];

  return {
    objects,
    meshMessages,
    baseArticles,
    archiveItems,
    profile,
    settings,
  };
}
