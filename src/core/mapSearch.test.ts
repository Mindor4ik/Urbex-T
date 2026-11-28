import test from 'node:test';
import assert from 'node:assert/strict';
import {
  mergeMapSearchResults,
  parseCoordinateSearch,
  parseNominatimResults,
  searchOfflineMapLocations,
} from './mapSearch';
import type { UrbexObject } from './types';

const SAMPLE_OBJECTS: UrbexObject[] = [
  {
    id: 'obj-1',
    title: 'Бункер Таганский-42',
    category: 'underground',
    subtype: 'Бункер ГО',
    status: 'verified',
    lat: 55.7405,
    lon: 37.6512,
    elevationM: 120,
    description: 'Тест',
    accessNotes: 'Закрыт',
    hazards: [],
    photos: [],
    authorNodeId: 'UT-01',
    createdAt: 1,
    updatedAt: 1,
  },
  {
    id: 'obj-2',
    title: 'Завод АМО-ЗИЛ',
    category: 'industrial',
    subtype: 'Цех',
    status: 'contested',
    lat: 55.699,
    lon: 37.641,
    elevationM: 135,
    description: 'Тест',
    accessNotes: 'Охрана',
    hazards: [],
    photos: [],
    authorNodeId: 'UT-01',
    createdAt: 1,
    updatedAt: 1,
  },
];

test('parseCoordinateSearch parses comma, space, and degree notations', () => {
  assert.deepEqual(parseCoordinateSearch('55.7558, 37.6173'), {
    lat: 55.7558,
    lon: 37.6173,
  });
  assert.deepEqual(parseCoordinateSearch('59.9343 30.3351'), {
    lat: 59.9343,
    lon: 30.3351,
  });
  assert.deepEqual(parseCoordinateSearch('33.8688° S, 151.2093° E'), {
    lat: -33.8688,
    lon: 151.2093,
  });
  assert.equal(parseCoordinateSearch('95.123, 37.6'), null);
  assert.equal(parseCoordinateSearch('Казань'), null);
});

test('searchOfflineMapLocations finds cities by Russian, ё/е, and Latin aliases', () => {
  const kazan = searchOfflineMapLocations('Казань', SAMPLE_OBJECTS);
  assert.equal(kazan[0]?.title, 'Казань');
  assert.equal(kazan[0]?.kind, 'city');
  assert.equal(kazan[0]?.zoomIdx, 1);

  const spbAlias = searchOfflineMapLocations('питер', SAMPLE_OBJECTS);
  assert.equal(spbAlias[0]?.title, 'Санкт-Петербург');

  const latin = searchOfflineMapLocations('ekb', SAMPLE_OBJECTS);
  assert.equal(latin[0]?.title, 'Екатеринбург');

  const quick = searchOfflineMapLocations('', SAMPLE_OBJECTS);
  assert.equal(quick.length, 5);
  assert.equal(quick[0]?.title, 'Москва');
});

test('searchOfflineMapLocations prioritizes coordinates and matching saved objects', () => {
  const coordMatches = searchOfflineMapLocations('59.9343, 30.3351', SAMPLE_OBJECTS);
  assert.equal(coordMatches[0]?.kind, 'coord');
  assert.equal(coordMatches[0]?.lat, 59.9343);

  const objectMatches = searchOfflineMapLocations('Таганский', SAMPLE_OBJECTS);
  assert.equal(objectMatches[0]?.kind, 'object');
  assert.equal(objectMatches[0]?.objectId, 'obj-1');
  assert.equal(objectMatches[0]?.zoomIdx, 4);
});

test('parseNominatimResults maps city and place entries and deduplicates via mergeMapSearchResults', () => {
  const parsed = parseNominatimResults([
    {
      place_id: 101,
      lat: '55.7887',
      lon: '49.1221',
      name: 'Казань',
      display_name: 'Казань, Татарстан, Россия',
      addresstype: 'city',
    },
    {
      place_id: 102,
      lat: '55.798551',
      lon: '49.106324',
      name: 'Казанский Кремль',
      display_name: 'Казанский Кремль, Казань, Татарстан, Россия',
      addresstype: 'tourism',
    },
    {
      place_id: 999,
      lat: 'invalid',
      lon: '49.0',
    },
  ]);

  assert.equal(parsed.length, 2);
  assert.equal(parsed[0]?.kind, 'city');
  assert.equal(parsed[0]?.subtitle, 'Татарстан, Россия');
  assert.equal(parsed[1]?.kind, 'place');
  assert.equal(parsed[1]?.zoomIdx, 3);

  const local = searchOfflineMapLocations('Казань', SAMPLE_OBJECTS);
  const merged = mergeMapSearchResults(local, parsed);
  assert.equal(merged.filter((m) => m.title === 'Казань').length, 1);
  assert.equal(merged.some((m) => m.title === 'Казанский Кремль'), true);
});
