import test from 'node:test';
import assert from 'node:assert/strict';
import {
  bearingDeg,
  formatCoordinates,
  formatDistance,
  getGpsSignalQuality,
  haversineDistanceM,
  isValidCoordinate,
  latLonToPixel,
  pixelToLatLon,
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

