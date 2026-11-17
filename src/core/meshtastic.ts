import type { MeshMessage, MeshPacketKind } from './types';

export function normalizeNodeId(raw: string): string {
  const cleaned = raw.replace(/[^a-fA-F0-9]/g, '').toLowerCase().slice(0, 8);
  return `!${cleaned.padEnd(8, '0')}`;
}

export function generatePacketId(timestamp: number, nodeId: string): string {
  const rand = Math.floor((timestamp % 100_000) * 1.73).toString(16);
  return `pkt-${nodeId.slice(1, 5)}-${timestamp.toString(36)}-${rand}`;
}

export function encodeMeshFrameHex(params: {
  channelId: string;
  senderNodeId: string;
  kind: MeshPacketKind;
  hopLimit: number;
  body: string;
  lat?: number;
  lon?: number;
}): string {
  const kindByte = params.kind === 'sos' ? 0x03 : params.kind === 'position' ? 0x02 : 0x01;
  const header = [
    0x94, // Meshtastic preamble magic byte
    0xc3,
    kindByte,
    Math.max(1, Math.min(7, params.hopLimit)),
  ];

  const encoder = new TextEncoder();
  const payloadText =
    params.lat !== undefined && params.lon !== undefined
      ? `${params.channelId}|${params.senderNodeId}|${params.lat.toFixed(5)},${params.lon.toFixed(5)}|${params.body}`
      : `${params.channelId}|${params.senderNodeId}|${params.body}`;
  const bodyBytes = encoder.encode(payloadText.slice(0, 180));

  const bytes = [...header, ...Array.from(bodyBytes)];
  return bytes.map((b) => b.toString(16).padStart(2, '0')).join('');
}

export function buildMeshMessage(params: {
  channelId: string;
  senderNodeId: string;
  senderCallsign: string;
  kind: MeshPacketKind;
  body: string;
  hopLimit: number;
  lat?: number;
  lon?: number;
  timestamp?: number;
}): MeshMessage {
  const ts = params.timestamp ?? Date.now();
  const trimmedBody = params.body.trim();
  const frameHex = encodeMeshFrameHex({
    channelId: params.channelId,
    senderNodeId: params.senderNodeId,
    kind: params.kind,
    hopLimit: params.hopLimit,
    body: trimmedBody,
    lat: params.lat,
    lon: params.lon,
  });

  return {
    id: generatePacketId(ts, params.senderNodeId),
    channelId: params.channelId,
    senderNodeId: params.senderNodeId,
    senderCallsign: params.senderCallsign,
    kind: params.kind,
    body: trimmedBody,
    lat: params.lat,
    lon: params.lon,
    timestamp: ts,
    hopLimit: params.hopLimit,
    acknowledged: true,
    frameHex,
  };
}

export interface HardwareRadioStatus {
  webSerialSupported: boolean;
  webBluetoothSupported: boolean;
  broadcastChannelSupported: boolean;
}

export function detectMeshHardwareSupport(): HardwareRadioStatus {
  const nav = typeof navigator !== 'undefined' ? (navigator as Navigator & { serial?: unknown; bluetooth?: unknown }) : undefined;
  return {
    webSerialSupported: Boolean(nav && 'serial' in nav),
    webBluetoothSupported: Boolean(nav && 'bluetooth' in nav),
    broadcastChannelSupported: typeof BroadcastChannel !== 'undefined',
  };
}
