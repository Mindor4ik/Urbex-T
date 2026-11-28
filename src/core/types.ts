export type ModuleId =
  | 'map'
  | 'objects'
  | 'mesh'
  | 'base'
  | 'archive'
  | 'profile'
  | 'settings'
  | 'menu'
  | 'apps';

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
  | 'underground'
  | 'military'
  | 'energy'
  | 'medical'
  | 'transport'
  | 'hydro'
  | 'mining'
  | 'ruins';

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

export type ZoneStyle =
  | 'solid'
  | 'dashed'
  | 'dotted'
  | 'dash_dot'
  | 'long_dash'
  | 'double_line'
  | 'bold'
  | 'glow'
  | 'outline'
  | 'dashed_outline';

export type ZonePattern =
  | 'none'
  | 'no_fill'
  | 'hatch'
  | 'cross_hatch'
  | 'grid'
  | 'dots'
  | 'vertical'
  | 'horizontal'
  | 'hazard_stripes';

export type ObjectIconId =
  | 'factory'
  | 'bunker'
  | 'building'
  | 'tower'
  | 'tunnel'
  | 'warehouse'
  | 'power'
  | 'boiler'
  | 'water'
  | 'fuel'
  | 'radar'
  | 'radiation'
  | 'hazard'
  | 'skull'
  | 'observation'
  | 'gate'
  | 'crane'
  | 'hospital'
  | 'airfield'
  | 'port'
  | 'shaft'
  | 'workshop'
  | 'camera'
  | 'pin'
  | 'military'
  | 'silo'
  | 'lab'
  | 'biohazard'
  | 'antenna'
  | 'train'
  | 'bridge'
  | 'dam'
  | 'monument'
  | 'school'
  | 'hotel'
  | 'ruins'
  | 'shelter'
  | 'fire'
  | 'compass'
  | 'stash'
  | 'substation'
  | 'cooling'
  | 'cistern'
  | 'elevator'
  | 'hangar'
  | 'checkpoint'
  | 'barracks'
  | 'range'
  | 'searchlight'
  | 'munition'
  | 'metro'
  | 'vent'
  | 'drainage'
  | 'catacomb'
  | 'vault'
  | 'depot'
  | 'bridge_rail'
  | 'pier'
  | 'lighthouse'
  | 'quarry'
  | 'mine_cart'
  | 'oil_rig'
  | 'pipeline'
  | 'morgue'
  | 'sanatorium'
  | 'church'
  | 'castle'
  | 'theater'
  | 'stadium'
  | 'mall'
  | 'generator'
  | 'evac';

export interface UrbexObject {
  id: string;
  title: string;
  category: ObjectCategory;
  subtype?: string;
  icon?: ObjectIconId;
  status: ReliabilityStatus;
  lat: number;
  lon: number;
  elevationM: number;
  description: string;
  accessNotes: string;
  hazards: string[];
  photoUrl?: string;
  photos?: string[];
  securityLevel?: SecurityLevel;
  entryDifficulty?: EntryDifficulty;
  floorsOrDepth?: string;
  customParams?: ObjectParam[];
  zone?: GeoPoint[];
  zoneColor?: string;
  zoneStyle?: ZoneStyle;
  zonePattern?: ZonePattern;
  zoneHatched?: boolean;
  createdAt: number;
  updatedAt: number;
  authorNodeId: string;
}

export type ObjectsViewMode = 'list' | 'compact' | 'cards';

export type MapLayerMode = 'satellite' | 'offline_vector';

export type PdaOrientation = 'auto' | 'portrait' | 'landscape';

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

export type SettingsCategory = 'databases' | 'map_gps' | 'radio_mesh' | 'pda_ui';

export type RadioModulation = 'NFM' | 'FM' | 'PMR446' | 'LPD' | 'DMR' | 'LORA' | 'AM';

export type ToneSquelchMode = 'NONE' | 'CTCSS' | 'DCS' | 'DCS_INV';

export type RadioPowerLevel = 'LOW' | 'MID' | 'HIGH';

export type RadioEncryptionType = 'NONE' | 'SCRAMBLER' | 'ARC4' | 'AES128' | 'AES256';

export interface RadioChannelPreset {
  id: string;
  name: string;
  rxFreqMhz: string;
  txFreqMhz: string;
  mode: RadioModulation;
  bandwidthKhz: string;
  toneMode: ToneSquelchMode;
  rxTone: string;
  txTone: string;
  squelchLevel: number;
  powerLevel: RadioPowerLevel;
  encryptionType: RadioEncryptionType;
  encryptionKey: string;
  txInhibit?: boolean;
  notes?: string;
  updatedAt: number;
}

export type CloudSyncProvider = 'none' | 'github_gist' | 'webdav' | 'local_folder' | 'custom_http';

export type CloudSyncStatus = 'idle' | 'syncing' | 'success' | 'error';

export interface CloudSyncConfig {
  enabled: boolean;
  provider: CloudSyncProvider;
  autoSyncOnChange: boolean;
  autoSyncIntervalMin: number;
  wifiOnly: boolean;
  encryptBeforeUpload: boolean;
  encryptionPassphrase: string;
  conflictStrategy: ConflictStrategy;
  remoteEndpoint: string;
  authToken: string;
  username: string;
  gistId: string;
  remoteFileName: string;
  lastSyncAt?: number;
  lastSyncStatus?: CloudSyncStatus;
  lastSyncMessage?: string;
}

export interface AppSettings {
  id: 'primary';
  powerProfile: PowerProfile;
  gpsIntervalMs: number;
  coordinateFormat: CoordinateFormat;
  imageCacheLimitMb: number;
  pageSize: number;
  meshEnabled: boolean;
  radioChannelsTabEnabled?: boolean;
  mapLayer: MapLayerMode;
  autoSyncOnMesh: boolean;
  lowMemoryMode: boolean;
  showObjectZones?: boolean;
  showObjectLabels?: boolean;
  showMapGrid?: boolean;
  showCompassHud?: boolean;
  defaultObjectsView?: ObjectsViewMode;
  autoCenterGps?: boolean;
  keepScreenAwake?: boolean;
  defaultZoneRadiusM?: number;
  hazardProximityAlertM?: number;
  confirmBeforeDelete?: boolean;
  hapticFeedback?: boolean;
  maskCoordinatesOnScreen?: boolean;
  pdaModeEnabled?: boolean;
  pdaOrientation?: PdaOrientation;
  editorEnabled?: boolean;
  radioChannels?: RadioChannelPreset[];
  cloudSync?: CloudSyncConfig;
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

export interface DatabaseWorkspaceMeta {
  id: string;
  name: string;
  regionLabel: string;
  description: string;
  createdAt: number;
  updatedAt: number;
}

export interface DatabaseWorkspaceRecord {
  meta: DatabaseWorkspaceMeta;
  payload: UrbexDbPayload;
}

export interface DbItemSource {
  dbId: string;
  dbName: string;
}

