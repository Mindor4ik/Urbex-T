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

  const hintedTemplate = buildUrbexDbTemplateJson(true);
  assert.match(hintedTemplate, /ПРАВИЛА СОЗДАНИЯ БАЗЫ ДАННЫХ/);
  assert.match(hintedTemplate, /\/\/ Уникальный ID объекта/);

  const parsedTemplate = parseAndVerifyUrbexDb(hintedTemplate);
  assert.equal(parsedTemplate.ok, true);
  if (parsedTemplate.ok) {
    assert.equal(parsedTemplate.bundle.payload.objects.length, 1);
    assert.equal(parsedTemplate.bundle.payload.baseArticles.length, 1);
    assert.equal(parsedTemplate.bundle.payload.objects[0].photoUrl, 'https://example.com/photo.jpg');
  }
});


