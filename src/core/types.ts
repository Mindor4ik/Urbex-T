export type ModuleId =
  | 'map'
  | 'objects'
  | 'mesh'
  | 'base'
  | 'archive'
  | 'profile'
  | 'settings'
  | 'menu';

export type ReliabilityStatus =
  | 'verified'
  | 'unverified'
  | 'contested'
  | 'hazardous';

export type ObjectCategory =
  | 'industrial'
  | 'bunker'
  | 'civilian'
  | 'infrastructure'
  | 'underground';

export type PowerProfile = 'eco' | 'balanced' | 'precision' | 'manual';

export type CoordinateFormat = 'dd' | 'dms' | 'utm';

export type OperatorRole =
  | 'scout'
  | 'cartographer'
  | 'comms'
  | 'archivist';

export type ArchiveMediaType =
  | 'photo'
  | 'video'
  | 'audio'
  | 'note'
  | 'document';

export type MeshPacketKind = 'text' | 'position' | 'sos';

export type ConflictStrategy = 'skip_existing' | 'keep_newer' | 'overwrite';

export interface GpsFix {
  lat: number;
  lon: number;
  altitudeM: number | null;
  accuracyM: number | null;
  headingDeg: number | null;
  speedMs: number | null;
  timestamp: number;
  source: 'gps' | 'manual' | 'cached';
}

export type SecurityLevel = 'none' | 'low' | 'medium' | 'high';

export type EntryDifficulty = 'easy' | 'moderate' | 'hard' | 'technical';

export interface GeoPoint {
  lat: number;
  lon: number;
}

export interface ObjectParam {
  key: string;
  value: string;
}

export interface UrbexObject {
  id: string;
  title: string;
  category: ObjectCategory;
  status: ReliabilityStatus;
  lat: number;
  lon: number;
  elevationM: number;
  description: string;
  accessNotes: string;
  hazards: string[];
  photoUrl?: string;
  securityLevel?: SecurityLevel;
  entryDifficulty?: EntryDifficulty;
  floorsOrDepth?: string;
  customParams?: ObjectParam[];
  zone?: GeoPoint[];
  createdAt: number;
  updatedAt: number;
  authorNodeId: string;
}

export type ObjectsViewMode = 'list' | 'compact' | 'cards';

export type MapLayerMode = 'satellite' | 'offline_vector';

export interface MeshChannel {
  id: string;
  name: string;
  hopLimit: number;
  isEmergency?: boolean;
}

export interface MeshNode {
  nodeId: string;
  callsign: string;
  role: OperatorRole;
  snrDb: number;
  rssiDbm: number;
  hopsAway: number;
  lastHeardAt: number;
  batteryPct: number;
  lat?: number;
  lon?: number;
}

export interface MeshMessage {
  id: string;
  channelId: string;
  senderNodeId: string;
  senderCallsign: string;
  kind: MeshPacketKind;
  body: string;
  lat?: number;
  lon?: number;
  timestamp: number;
  hopLimit: number;
  acknowledged: boolean;
  frameHex: string;
}

export interface BaseArticle {
  id: string;
  title: string;
  section: 'safety' | 'navigation' | 'comms' | 'architecture' | 'field_note';
  summary: string;
  content: string;
  updatedAt: number;
  pinned: boolean;
}

export interface ArchiveItem {
  id: string;
  title: string;
  type: ArchiveMediaType;
  objectId?: string;
  mimeType: string;
  sizeBytes: number;
  dataUrl?: string;
  textContent?: string;
  notes: string;
  createdAt: number;
  updatedAt: number;
}

export interface UserProfile {
  id: string;
  callsign: string;
  nodeId: string;
  role: OperatorRole;
  emergencyContact: string;
  bio: string;
  updatedAt: number;
}

export interface OfflineMapSector {
  id: string;
  name: string;
  centerLat: number;
  centerLon: number;
  radiusKm: number;
  zoomMin: number;
  zoomMax: number;
  sizeMb: number;
  cachedAt: number;
}

export interface AppSettings {
  id: 'primary';
  powerProfile: PowerProfile;
  gpsIntervalMs: number;
  coordinateFormat: CoordinateFormat;
  imageCacheLimitMb: number;
  pageSize: number;
  meshEnabled: boolean;
  mapLayer: MapLayerMode;
  autoSyncOnMesh: boolean;
  lowMemoryMode: boolean;
  showObjectZones?: boolean;
  showObjectLabels?: boolean;
  defaultObjectsView?: ObjectsViewMode;
  autoCenterGps?: boolean;
  keepScreenAwake?: boolean;
  defaultZoneRadiusM?: number;
  offlineSectors: OfflineMapSector[];
  updatedAt: number;
}

export interface UrbexDbManifest {
  format: 'urbex-t-db';
  version: string;
  exportedAt: number;
  deviceNodeId: string;
  checksum: string;
  counts: {
    objects: number;
    meshMessages: number;
    baseArticles: number;
    archiveItems: number;
  };
}

export interface UrbexDbPayload {
  objects: UrbexObject[];
  meshMessages: MeshMessage[];
  baseArticles: BaseArticle[];
  archiveItems: ArchiveItem[];
  profile: UserProfile;
  settings: AppSettings;
}

export interface UrbexDbBundle {
  manifest: UrbexDbManifest;
  payload: UrbexDbPayload;
}
