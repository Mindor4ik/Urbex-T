import test from 'node:test';
import assert from 'node:assert/strict';
import {
  bearingDeg,
  buildZoneCircle,
  computePolygonAreaM2,
  computePolygonPerimeterM,
  formatArea,
  formatCoordinates,
  formatDistance,
  getGpsSignalQuality,
  haversineDistanceM,
  isPointInPolygon,
  isValidCoordinate,
  latLonToPixel,
  pixelToLatLon,
  simplifyPolygonPoints,
} from './geo';
import {
  computePayloadChecksum,
  mergeUrbexDbPayloads,
  parseAndVerifyUrbexDb,
  serializeUrbexDb,
} from './urbexdb';
import { buildMeshMessage, normalizeNodeId } from './meshtastic';
import type { UrbexDbPayload } from './types';

function makeSamplePayload(): UrbexDbPayload {
  return {
    objects: [
      {
        id: 'obj-1',
        title: 'Градирня ТЭЦ-4',
        category: 'industrial',
        status: 'verified',
        lat: 55.7512,
        lon: 37.6184,
        elevationM: 142,
        description: 'Бетонная градирня',
        accessNotes: 'Южный периметр',
        hazards: ['Арматура'],
        createdAt: 1000,
        updatedAt: 2000,
        authorNodeId: '!a1b2c3d4',
      },
    ],
    meshMessages: [],
    baseArticles: [],
    archiveItems: [],
    profile: {
      id: 'local',
      callsign: 'Сокол-1',
      nodeId: '!a1b2c3d4',
      role: 'scout',
      emergencyContact: '',
      bio: '',
      updatedAt: 1000,
    },
    settings: {
      id: 'primary',
      powerProfile: 'balanced',
      gpsIntervalMs: 10000,
      coordinateFormat: 'dd',
      imageCacheLimitMb: 64,
      pageSize: 20,
      meshEnabled: false,
      mapLayer: 'satellite',
      autoSyncOnMesh: false,
      lowMemoryMode: false,
      offlineSectors: [],
      updatedAt: 1000,
    },
  };
}

test('geo: validates coordinates and formats DD, DMS, UTM', () => {
  assert.equal(isValidCoordinate(55.7512, 37.6184), true);
  assert.equal(isValidCoordinate(91, 37), false);
  assert.equal(isValidCoordinate(55, -181), false);

  const dd = formatCoordinates(55.7512, 37.6184, 'dd');
  assert.match(dd, /55\.75120° N, 37\.61840° E/);

  const dms = formatCoordinates(55.7512, 37.6184, 'dms');
  assert.match(dms, /55°45'/);

  const utm = formatCoordinates(55.7512, 37.6184, 'utm');
  assert.match(utm, /37U/);
});

test('geo: haversine distance, bearing, and pixel projection roundtrip', () => {
  const dist = haversineDistanceM(55.7512, 37.6184, 55.7602, 37.6184);
  assert.ok(dist > 990 && dist < 1010, `Expected ~1000m, got ${dist}`);
  assert.equal(formatDistance(850), '850 м');
  assert.equal(formatDistance(1540), '1.54 км');

  const brg = bearingDeg(55.7512, 37.6184, 55.7602, 37.6184);
  assert.ok(brg < 1 || brg > 359);

  const proj = {
    centerLat: 55.7512,
    centerLon: 37.6184,
    metersPerPixel: 5,
    width: 800,
    height: 600,
  };
  const px = latLonToPixel(55.755, 37.622, proj);
  const back = pixelToLatLon(px.x, px.y, proj);
  assert.ok(Math.abs(back.lat - 55.755) < 0.0001);
  assert.ok(Math.abs(back.lon - 37.622) < 0.0001);
});

test('geo: slippy tile coordinates roundtrip accurately', async () => {
  const { lonToTileX, latToTileY, tileXToLon, tileYToLat } = await import('./geo');
  const z = 14;
  const tx = lonToTileX(37.6184, z);
  const ty = latToTileY(55.7512, z);
  assert.ok(Math.abs(tileXToLon(tx, z) - 37.6184) < 0.0001);
  assert.ok(Math.abs(tileYToLat(ty, z) - 55.7512) < 0.0001);
});

test('urbexdb: serializes, verifies checksum, and detects tampering', () => {
  const payload = makeSamplePayload();
  const serialized = serializeUrbexDb(payload, '!a1b2c3d4', 5000);
  const parsed = parseAndVerifyUrbexDb(serialized);
  assert.equal(parsed.ok, true);
  if (parsed.ok) {
    assert.equal(parsed.bundle.manifest.checksum, computePayloadChecksum(payload));
    assert.equal(parsed.bundle.payload.objects.length, 1);
  }

  // Tamper with object title without updating manifest checksum
  const tampered = serialized.replace('Градирня ТЭЦ-4', 'Подменённый объект');
  const failed = parseAndVerifyUrbexDb(tampered);
  assert.equal(failed.ok, false);
  if (!failed.ok) {
    assert.equal(failed.code, 'CHECKSUM_MISMATCH');
  }
});

test('urbexdb: resolves merge conflicts across strategies', () => {
  const current = makeSamplePayload();
  const incoming = makeSamplePayload();
  incoming.objects[0] = {
    ...incoming.objects[0],
    title: 'Обновлённая градирня',
    updatedAt: 5000,
  };
  incoming.objects.push({
    ...incoming.objects[0],
    id: 'obj-2',
    title: 'Новый бункер',
    updatedAt: 3000,
  });

  const keepNewer = mergeUrbexDbPayloads(current, incoming, 'keep_newer');
  assert.equal(keepNewer.stats.added, 1);
  assert.equal(keepNewer.stats.updated, 1);
  assert.equal(keepNewer.payload.objects.find((o) => o.id === 'obj-1')?.title, 'Обновлённая градирня');

  const skipExisting = mergeUrbexDbPayloads(current, incoming, 'skip_existing');
  assert.equal(skipExisting.stats.added, 1);
  assert.equal(skipExisting.stats.skipped, 1);
  assert.equal(skipExisting.payload.objects.find((o) => o.id === 'obj-1')?.title, 'Градирня ТЭЦ-4');
});

test('meshtastic: normalizes node IDs and frames SOS packets', () => {
  assert.equal(normalizeNodeId('a1b2'), '!a1b20000');
  const pkt = buildMeshMessage({
    channelId: 'ch-sos',
    senderNodeId: '!a1b2c3d4',
    senderCallsign: 'Сокол-1',
    kind: 'sos',
    body: 'Травма голеностопа, сектор Б',
    hopLimit: 5,
    lat: 55.7512,
    lon: 37.6184,
    timestamp: 1700000000000,
  });

  assert.equal(pkt.kind, 'sos');
  assert.ok(pkt.frameHex.startsWith('94c30305'));
  assert.equal(pkt.lat, 55.7512);
});

test('geo: classifies GPS signal accuracy into green, yellow, and red levels', () => {
  const good = getGpsSignalQuality(12);
  assert.equal(good.level, 'good');
  assert.equal(good.colorHex, '#6DB386');

  const edgeGood = getGpsSignalQuality(15);
  assert.equal(edgeGood.level, 'good');

  const moderate = getGpsSignalQuality(28);
  assert.equal(moderate.level, 'moderate');
  assert.equal(moderate.colorHex, '#DF9E43');

  const poorDist = getGpsSignalQuality(72);
  assert.equal(poorDist.level, 'poor');
  assert.equal(poorDist.colorHex, '#DC6860');

  const poorErr = getGpsSignalQuality(10, true);
  assert.equal(poorErr.level, 'poor');

  const poorNull = getGpsSignalQuality(null);
  assert.equal(poorNull.level, 'poor');
});

test('geo: computes polygon zone area, perimeter, hit-testing, circle, and simplification', () => {
  const circle = buildZoneCircle(55.7512, 37.6184, 100, 16);
  assert.equal(circle.length, 16);

  const area = computePolygonAreaM2(circle);
  // 100m radius 16-gon area ~ 30,614 m2 (close to pi*100^2 = 31,416)
  assert.ok(area > 29_000 && area < 32_500, `Expected ~30614 m2, got ${area}`);
  assert.match(formatArea(area), /га/);
  assert.equal(formatArea(450), '450 м²');

  const perimeter = computePolygonPerimeterM(circle);
  assert.ok(perimeter > 600 && perimeter < 640, `Expected ~624m, got ${perimeter}`);

  assert.equal(isPointInPolygon(55.7512, 37.6184, circle), true);
  assert.equal(isPointInPolygon(55.7600, 37.6184, circle), false);

  const densePath = [
    { lat: 55.751200, lon: 37.618400 },
    { lat: 55.751210, lon: 37.618410 }, // ~1.3m apart -> pruned
    { lat: 55.751400, lon: 37.618400 }, // ~22m apart -> kept
    { lat: 55.751400, lon: 37.618800 }, // ~25m apart -> kept
  ];
  const simplified = simplifyPolygonPoints(densePath, 8);
  assert.equal(simplified.length, 3);
});

test('urbexdb: preserves PDA mode and orientation settings across export and import', () => {
  const payload = makeSamplePayload();
  payload.settings.pdaModeEnabled = true;
  payload.settings.pdaOrientation = 'landscape';
  payload.settings.updatedAt = 8000;

  const serialized = serializeUrbexDb(payload, '!a1b2c3d4', 9000);
  const parsed = parseAndVerifyUrbexDb(serialized);
  assert.equal(parsed.ok, true);
  if (parsed.ok) {
    assert.equal(parsed.bundle.payload.settings.pdaModeEnabled, true);
    assert.equal(parsed.bundle.payload.settings.pdaOrientation, 'landscape');
  }

  const current = makeSamplePayload();
  const merged = mergeUrbexDbPayloads(current, payload, 'keep_newer');
  assert.equal(merged.payload.settings.pdaModeEnabled, true);
  assert.equal(merged.payload.settings.pdaOrientation, 'landscape');
});

test('urbexdb: parses manual code template and normalizes Android photoUri compatibility', async () => {
  const { buildUrbexDbTemplateJson } = await import('./urbexdb');
  const templateRaw = buildUrbexDbTemplateJson();
  const res = parseAndVerifyUrbexDb(templateRaw);
  assert.equal(res.ok, true);
  if (res.ok) {
    assert.equal(res.bundle.payload.objects.length, 1);
    assert.equal(res.bundle.payload.objects[0].title, 'ТЭЦ-1 · Заброшенный мазутный корпус');
    assert.match(res.bundle.manifest.checksum, /^uxdb-/);
    assert.ok(res.warnings.length >= 1);
  }

  const minimalManual = JSON.stringify({
    manifest: { format: 'urbex-t-db', version: '1.0.0', checksum: 'manual' },
    payload: {
      objects: [
        {
          id: 'tmn-1',
          title: 'Водонапорная башня (Тюмень)',
          lat: 57.15,
          lon: 65.53,
          photoUri: 'https://example.com/tower.jpg',
          zoneColor: '38bdf8',
          zoneStyle: 'DASHED',
          zoneHatched: true,
        },
      ],
    },
  });
  const parsedMinimal = parseAndVerifyUrbexDb(minimalManual);
  assert.equal(parsedMinimal.ok, true);
  if (parsedMinimal.ok) {
    assert.equal(parsedMinimal.bundle.payload.objects[0].photoUrl, 'https://example.com/tower.jpg');
    assert.equal(parsedMinimal.bundle.payload.objects[0].category, 'industrial');
    assert.equal(parsedMinimal.bundle.payload.objects[0].zoneColor, '#38BDF8');
    assert.equal(parsedMinimal.bundle.payload.objects[0].zoneStyle, 'dashed');
    assert.equal(parsedMinimal.bundle.payload.objects[0].zoneHatched, true);
    assert.equal(parsedMinimal.bundle.payload.settings.editorEnabled, false);
  }
});

test('labels: normalizes zone colors, zone styles, and falls back to status color', async () => {
  const {
    normalizeZoneColor,
    normalizeZoneStyle,
    getEffectiveZoneColor,
    getObjectPhotos,
  } = await import('./labels');
  assert.equal(normalizeZoneColor('38bdf8'), '#38BDF8');
  assert.equal(normalizeZoneColor(' #a78bfa '), '#A78BFA');
  assert.equal(normalizeZoneColor('invalid'), undefined);

  assert.equal(normalizeZoneStyle('DOTTED'), 'dotted');
  assert.equal(normalizeZoneStyle('outline'), 'outline');
  assert.equal(normalizeZoneStyle('unknown'), 'solid');

  assert.equal(getEffectiveZoneColor({ status: 'hazardous', zoneColor: undefined }), '#C7564E');
  assert.equal(getEffectiveZoneColor({ status: 'hazardous', zoneColor: '#38BDF8' }), '#38BDF8');

  assert.deepEqual(
    getObjectPhotos({
      photoUrl: 'https://example.com/a.jpg',
      photos: ['https://example.com/b.jpg', 'https://example.com/a.jpg', '  '],
    }),
    ['https://example.com/a.jpg', 'https://example.com/b.jpg']
  );
  assert.deepEqual(
    getObjectPhotos({
      photoUrl: 'https://example.com/cover.jpg',
      photos: ['https://example.com/cover.jpg', 'https://example.com/extra.jpg'],
    }),
    ['https://example.com/cover.jpg', 'https://example.com/extra.jpg']
  );
});

test('urbexdb: preserves multiple photos across photos, photoUrls, and photoUrl', () => {
  const manual = JSON.stringify({
    manifest: { format: 'urbex-t-db', version: '1.0.0', checksum: 'manual' },
    payload: {
      objects: [
        {
          id: 'multi-1',
          title: 'ТЭЦ-2 (multi)',
          lat: 55.7,
          lon: 37.6,
          photoUrl: 'https://example.com/1.jpg',
          photoUrls: ['https://example.com/2.jpg', 'https://example.com/1.jpg'],
          photos: ['https://example.com/3.jpg'],
        },
      ],
    },
  });
  const parsed = parseAndVerifyUrbexDb(manual);
  assert.equal(parsed.ok, true);
  if (parsed.ok) {
    const obj = parsed.bundle.payload.objects[0];
    assert.equal(obj.photoUrl, 'https://example.com/1.jpg');
    assert.deepEqual(obj.photos, [
      'https://example.com/1.jpg',
      'https://example.com/3.jpg',
      'https://example.com/2.jpg',
    ]);
  }
});

test('urbexdb: strips comments outside strings while preserving URLs and parses annotated template', async () => {
  const { buildUrbexDbTemplateJson, stripJsonComments } = await import('./urbexdb');

  const input = `/* header comment */\n{\n  // inline comment\n  "url": "https://example.com/a//b/*keep*/",\n  "note": "line \\"quoted\\" // keep"\n}`;
  const cleaned = stripJsonComments(input);
  const parsedJson = JSON.parse(cleaned) as { url: string; note: string };
  assert.equal(parsedJson.url, 'https://example.com/a//b/*keep*/');
  assert.equal(parsedJson.note, 'line "quoted" // keep');

  const plainTemplate = buildUrbexDbTemplateJson(false);
  const plainJson = JSON.parse(plainTemplate) as { manifest: { checksum: string } };
  assert.equal(plainJson.manifest.checksum, 'manual');

  const hintedTemplate = buildUrbexDbTemplateJson(true);
  assert.match(hintedTemplate, /ПРАВИЛА СОЗДАНИЯ БАЗЫ ДАННЫХ/);
  assert.match(hintedTemplate, /Дополнительные таблицы:/);
  assert.match(hintedTemplate, /\/\/ Уникальный ID объекта/);

  const parsedTemplate = parseAndVerifyUrbexDb(hintedTemplate);
  assert.equal(parsedTemplate.ok, true);
  if (parsedTemplate.ok) {
    assert.equal(parsedTemplate.bundle.payload.objects.length, 1);
    assert.equal(parsedTemplate.bundle.payload.baseArticles.length, 1);
    assert.equal(parsedTemplate.bundle.payload.objects[0].photoUrl, 'https://example.com/photo.jpg');
  }
});

test('radio: normalizes frequencies, computes duplex offsets, generates keys, and builds CHIRP CSV', async () => {
  const {
    buildChirpCsv,
    computeDuplexOffsetMhz,
    createDefaultRadioChannels,
    generateRadioCryptoKey,
    normalizeFrequencyMhz,
  } = await import('./radio');

  assert.equal(normalizeFrequencyMhz('446,00625'), '446.00625');
  assert.equal(normalizeFrequencyMhz('145.5'), '145.50000');
  assert.equal(normalizeFrequencyMhz('0.2'), null);
  assert.equal(normalizeFrequencyMhz('9999'), null);

  assert.deepEqual(computeDuplexOffsetMhz('145.60000', '145.00000'), {
    duplex: '-',
    offsetMhz: '0.60000',
  });
  assert.deepEqual(computeDuplexOffsetMhz('433.625', '433.625'), {
    duplex: '',
    offsetMhz: '0.00000',
  });

  const aes256 = generateRadioCryptoKey('AES256');
  assert.equal(aes256.length, 64);
  assert.match(aes256, /^[0-9a-f]{64}$/);
  assert.equal(generateRadioCryptoKey('AES128').length, 32);
  assert.equal(generateRadioCryptoKey('ARC4').length, 10);
  assert.equal(generateRadioCryptoKey('NONE'), '');

  const defaults = createDefaultRadioChannels(1000);
  assert.ok(defaults.length >= 4);
  const csv = buildChirpCsv(defaults);
  const lines = csv.split('\n');
  assert.equal(lines.length, defaults.length + 1);
  assert.match(lines[0], /^Location,Name,Frequency,Duplex,Offset,Tone/);
  assert.match(csv, /446\.00625.*TSQL,88\.5,88\.5/);
  assert.match(csv, /433\.62500.*DTCS,.*023,NN/);
  assert.match(csv, /\[AES256\]/);
});

test('cloudSync: sanitizes secrets before export and encrypts/decrypts payload with AES-256-GCM', async () => {
  const {
    DEFAULT_CLOUD_SYNC_CONFIG,
    decodeRemoteSnapshot,
    decryptSyncContent,
    encodeOutgoingSnapshot,
    encryptSyncContent,
    isEncryptedSyncEnvelope,
    sanitizePayloadForSync,
  } = await import('./cloudSync');

  const payload = makeSamplePayload();
  payload.settings.cloudSync = {
    ...DEFAULT_CLOUD_SYNC_CONFIG,
    enabled: true,
    provider: 'github_gist',
    authToken: 'ghp_super_secret_token',
    username: 'scout-op',
    encryptionPassphrase: 'my-strong-passphrase',
    gistId: 'gist-12345',
  };

  const sanitized = sanitizePayloadForSync(payload);
  assert.equal(sanitized.settings.cloudSync?.authToken, '');
  assert.equal(sanitized.settings.cloudSync?.username, '');
  assert.equal(sanitized.settings.cloudSync?.encryptionPassphrase, '');
  // Original remains intact
  assert.equal(payload.settings.cloudSync?.authToken, 'ghp_super_secret_token');

  const cipherText = await encryptSyncContent('{"hello":"urbex"}', 'secret-field-pass');
  assert.equal(isEncryptedSyncEnvelope(cipherText), true);
  const decrypted = await decryptSyncContent(cipherText, 'secret-field-pass');
  assert.equal(decrypted, '{"hello":"urbex"}');

  await assert.rejects(
    () => decryptSyncContent(cipherText, 'wrong-pass'),
    /Неверный пароль/
  );

  // Full snapshot encode -> decode roundtrip
  const outgoing = await encodeOutgoingSnapshot(
    payload,
    '!a1b2c3d4',
    payload.settings.cloudSync
  );
  assert.equal(isEncryptedSyncEnvelope(outgoing), true);
  assert.equal(outgoing.includes('ghp_super_secret_token'), false);

  const restored = await decodeRemoteSnapshot(outgoing, 'my-strong-passphrase');
  assert.equal(restored.payload.objects.length, 1);
  assert.equal(restored.payload.settings.cloudSync?.authToken, '');

  // Merging remote snapshot retains local secrets
  const merged = mergeUrbexDbPayloads(payload, restored.payload, 'keep_newer');
  assert.equal(merged.payload.settings.cloudSync?.authToken, 'ghp_super_secret_token');
  assert.equal(merged.payload.settings.cloudSync?.encryptionPassphrase, 'my-strong-passphrase');
});

test('navigation: tracks deduplicated tab history, pops back to real previous tab, and validates IDs', async () => {
  const {
    isValidModuleId,
    isValidSettingsCategory,
    popTabHistory,
    pushTabHistory,
  } = await import('./navigation');

  assert.equal(isValidModuleId('objects'), true);
  assert.equal(isValidModuleId('invalid'), false);
  assert.equal(isValidSettingsCategory('radio_mesh'), true);
  assert.equal(isValidSettingsCategory('unknown'), false);

  let hist = pushTabHistory(['map'], 'objects');
  hist = pushTabHistory(hist, 'objects'); // deduplicate repeated same tab
  hist = pushTabHistory(hist, 'menu');
  hist = pushTabHistory(hist, 'settings');
  assert.deepEqual(hist, ['map', 'objects', 'menu', 'settings']);

  const step1 = popTabHistory(hist);
  assert.equal(step1.previous, 'menu');
  assert.deepEqual(step1.remaining, ['map', 'objects', 'menu']);

  const step2 = popTabHistory(step1.remaining);
  assert.equal(step2.previous, 'objects');
  assert.deepEqual(step2.remaining, ['map', 'objects']);

  const stepRoot = popTabHistory(['map']);
  assert.equal(stepRoot.previous, null);
  assert.deepEqual(stepRoot.remaining, ['map']);
});

test('navigation & urbexdb: resolves independent Mesh/Frequencies tabs and roundtrips radioChannelsTabEnabled', async () => {
  const {
    isCommsModuleVisible,
    resolveCommsSubTab,
    sanitizeActiveModule,
  } = await import('./navigation');

  assert.equal(isCommsModuleVisible(false, false), false);
  assert.equal(isCommsModuleVisible(false, true), true);
  assert.equal(isCommsModuleVisible(true, false), true);

  assert.equal(resolveCommsSubTab('packets', false, true), 'radio');
  assert.equal(resolveCommsSubTab('radio', true, false), 'packets');
  assert.equal(resolveCommsSubTab('radio', true, true), 'radio');

  assert.equal(
    sanitizeActiveModule('mesh', { pdaModeEnabled: true, meshEnabled: false, radioChannelsTabEnabled: true }),
    'mesh'
  );
  assert.equal(
    sanitizeActiveModule('mesh', { pdaModeEnabled: true, meshEnabled: false, radioChannelsTabEnabled: false }),
    'map'
  );
  assert.equal(
    sanitizeActiveModule('apps', { pdaModeEnabled: false, meshEnabled: true, radioChannelsTabEnabled: true }),
    'map'
  );

  const payload = makeSamplePayload();
  payload.settings.meshEnabled = true;
  payload.settings.radioChannelsTabEnabled = false;
  const serialized = serializeUrbexDb(payload, '!a1b2c3d4', 12000);
  const parsed = parseAndVerifyUrbexDb(serialized);
  assert.equal(parsed.ok, true);
  if (parsed.ok) {
    assert.equal(parsed.bundle.payload.settings.meshEnabled, true);
    assert.equal(parsed.bundle.payload.settings.radioChannelsTabEnabled, false);
  }
});

test('geo: offsetCoordinate moves coordinates accurately and translateZone shifts polygon perimeter', async () => {
  const { offsetCoordinate, translateZone } = await import('./geo');
  const origin = { lat: 55.7512, lon: 37.6184 };
  const movedNorth = offsetCoordinate(origin.lat, origin.lon, 100, 0);
  const movedEast = offsetCoordinate(origin.lat, origin.lon, 0, 100);

  const distN = haversineDistanceM(origin.lat, origin.lon, movedNorth.lat, movedNorth.lon);
  const distE = haversineDistanceM(origin.lat, origin.lon, movedEast.lat, movedEast.lon);
  assert.ok(distN > 98 && distN < 102, `Expected ~100m N, got ${distN}`);
  assert.ok(distE > 98 && distE < 102, `Expected ~100m E, got ${distE}`);
  assert.ok(movedNorth.lat > origin.lat);
  assert.ok(movedEast.lon > origin.lon);

  const zone = buildZoneCircle(origin.lat, origin.lon, 50, 8);
  const dLat = movedNorth.lat - origin.lat;
  const dLon = movedEast.lon - origin.lon;
  const shiftedZone = translateZone(zone, dLat, dLon);
  assert.ok(shiftedZone && shiftedZone.length === zone.length);
  assert.equal(isPointInPolygon(origin.lat + dLat, origin.lon + dLon, shiftedZone), true);
  assert.equal(translateZone(undefined, dLat, dLon), undefined);
});

test('labels & urbexdb: normalizes 72 object icons, 12 structure categories, subtype presets, and roundtrips .urbexdb', async () => {
  const {
    CATEGORY_LABELS,
    CATEGORY_DEFAULT_ICON,
    CATEGORY_SUBTYPES,
    OBJECT_ICONS,
    OBJECT_ICON_META,
    getEffectiveObjectIcon,
    normalizeObjectCategory,
    normalizeObjectIcon,
    normalizeObjectSubtype,
  } = await import('./labels');

  assert.equal(OBJECT_ICONS.length, 72);
  for (const id of OBJECT_ICONS) {
    assert.ok(OBJECT_ICON_META[id]?.label.length > 0);
  }
  const categoryKeys = Object.keys(CATEGORY_LABELS) as Array<keyof typeof CATEGORY_LABELS>;
  assert.equal(categoryKeys.length, 12);
  for (const cat of categoryKeys) {
    assert.ok(CATEGORY_LABELS[cat].length > 0);
    assert.ok(OBJECT_ICON_META[CATEGORY_DEFAULT_ICON[cat]]?.label.length > 0);
    assert.ok(CATEGORY_SUBTYPES[cat].length >= 4);
    for (const preset of CATEGORY_SUBTYPES[cat]) {
      assert.ok(preset.label.trim().length > 0);
      assert.ok(OBJECT_ICON_META[preset.icon]?.label.length > 0);
    }
  }

  assert.equal(normalizeObjectCategory('  MILITARY '), 'military');
  assert.equal(normalizeObjectCategory('unknown_cat'), 'industrial');
  assert.equal(normalizeObjectIcon('  RADIATION '), 'radiation');
  assert.equal(normalizeObjectIcon('  SILO '), 'silo');
  assert.equal(normalizeObjectIcon('  SUB-STATION '), undefined);
  assert.equal(normalizeObjectIcon('bridge-rail'), 'bridge_rail');
  assert.equal(normalizeObjectIcon('  CATACOMB '), 'catacomb');
  assert.equal(normalizeObjectIcon('unknown-icon'), undefined);
  assert.equal(normalizeObjectSubtype('  Бомбоубежище ЗС ГО  '), 'Бомбоубежище ЗС ГО');
  assert.equal(normalizeObjectSubtype('   '), undefined);
  assert.equal(getEffectiveObjectIcon({ category: 'bunker', icon: undefined }), 'bunker');
  assert.equal(getEffectiveObjectIcon({ category: 'underground', icon: undefined }), 'tunnel');
  assert.equal(getEffectiveObjectIcon({ category: 'military', icon: undefined }), 'military');
  assert.equal(getEffectiveObjectIcon({ category: 'hydro', icon: undefined }), 'dam');
  assert.equal(getEffectiveObjectIcon({ category: 'industrial', icon: 'radar' }), 'radar');
  assert.equal(getEffectiveObjectIcon({ category: 'energy', icon: 'substation' }), 'substation');

  const manualJson = JSON.stringify({
    manifest: { format: 'urbex-t-db', version: '1.0.0', checksum: 'manual' },
    payload: {
      objects: [
        {
          id: 'icon-test-1',
          title: 'ТП-110 кВ Сектор-9',
          category: 'ENERGY',
          subtype: '  Подстанция / ОРУ / ТП  ',
          icon: 'SUBSTATION',
          lat: 55.75,
          lon: 37.62,
        },
        {
          id: 'icon-test-2',
          title: 'Неизвестная категория и иконка',
          category: 'weird_type',
          subtype: '   ',
          icon: 'not_valid',
          lat: 55.76,
          lon: 37.63,
        },
      ],
    },
  });
  const parsed = parseAndVerifyUrbexDb(manualJson);
  assert.equal(parsed.ok, true);
  if (parsed.ok) {
    assert.equal(parsed.bundle.payload.objects[0].category, 'energy');
    assert.equal(parsed.bundle.payload.objects[0].subtype, 'Подстанция / ОРУ / ТП');
    assert.equal(parsed.bundle.payload.objects[0].icon, 'substation');
    assert.equal(parsed.bundle.payload.objects[1].category, 'industrial');
    assert.equal(parsed.bundle.payload.objects[1].subtype, undefined);
    assert.equal(parsed.bundle.payload.objects[1].icon, undefined);
    assert.equal(getEffectiveObjectIcon(parsed.bundle.payload.objects[1]), 'factory');
  }
});

test('geo: hides map object labels when zoomed out while respecting setting', async () => {
  const {
    MAP_LABEL_MIN_TILE_ZOOM,
    MAP_LABEL_MIN_ZOOM_IDX,
    shouldShowMapObjectLabels,
    shouldShowMapObjectLabelsByZoomIdx,
  } = await import('./geo');

  assert.equal(shouldShowMapObjectLabels(true, MAP_LABEL_MIN_TILE_ZOOM), true);
  assert.equal(shouldShowMapObjectLabels(undefined, 14.0), true);
  assert.equal(shouldShowMapObjectLabels(true, 13.0), false);
  assert.equal(shouldShowMapObjectLabels(false, 16.0), false);

  assert.equal(shouldShowMapObjectLabelsByZoomIdx(true, MAP_LABEL_MIN_ZOOM_IDX), true);
  assert.equal(shouldShowMapObjectLabelsByZoomIdx(undefined, 4), true);
  assert.equal(shouldShowMapObjectLabelsByZoomIdx(true, 2), false);
  assert.equal(shouldShowMapObjectLabelsByZoomIdx(true, 0), false);
  assert.equal(shouldShowMapObjectLabelsByZoomIdx(false, 5), false);
});
