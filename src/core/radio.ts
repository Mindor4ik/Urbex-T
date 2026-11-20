import type {
  RadioChannelPreset,
  RadioEncryptionType,
  RadioModulation,
  RadioPowerLevel,
  ToneSquelchMode,
} from './types';

export const CTCSS_TONES: string[] = [
  '67.0', '69.3', '71.9', '74.4', '77.0', '79.7', '82.5', '85.4', '88.5', '91.5',
  '94.8', '97.4', '100.0', '103.5', '107.2', '110.9', '114.8', '118.8', '123.0', '127.3',
  '131.8', '136.5', '141.3', '146.2', '151.4', '156.7', '162.2', '167.9', '173.8', '179.9',
  '186.2', '192.8', '203.5', '210.7', '218.1', '225.7', '233.6', '241.8', '250.3', '254.1',
];

export const DCS_CODES: string[] = [
  '023', '025', '026', '031', '032', '043', '047', '051', '054', '065',
  '071', '072', '073', '074', '114', '115', '116', '125', '131', '132',
  '134', '143', '152', '155', '156', '162', '165', '172', '174', '205',
  '223', '226', '243', '244', '245', '251', '261', '263', '265', '271',
  '306', '311', '315', '331', '343', '346', '351', '364', '365', '371',
  '411', '412', '413', '423', '431', '432', '445', '464', '465', '466',
  '503', '506', '516', '532', '546', '565', '606', '612', '624', '627',
  '631', '632', '654', '662', '664', '703', '712', '723', '731', '732',
  '734', '743', '754',
];

export const RADIO_MODULATION_LABELS: Record<RadioModulation, string> = {
  NFM: 'NFM (Узкая ЧМ)',
  FM: 'FM (Широкая ЧМ)',
  PMR446: 'PMR446',
  LPD: 'LPD433',
  DMR: 'DMR (Цифра)',
  LORA: 'LoRa / Mesh',
  AM: 'AM (Авиа/Си-Би)',
};

export const TONE_SQUELCH_LABELS: Record<ToneSquelchMode, string> = {
  NONE: 'Нет (CSQ)',
  CTCSS: 'CTCSS (субтон Hz)',
  DCS: 'DCS (цифровой N)',
  DCS_INV: 'DCS-I (инверсный)',
};

export const RADIO_POWER_LABELS: Record<RadioPowerLevel, string> = {
  LOW: 'LOW (0.5 Вт)',
  MID: 'MID (2 Вт)',
  HIGH: 'HIGH (5+ Вт)',
};

export const RADIO_ENCRYPTION_LABELS: Record<RadioEncryptionType, string> = {
  NONE: 'Открытый (нет)',
  SCRAMBLER: 'Скремблер',
  ARC4: 'ARC4 / DMR BP',
  AES128: 'AES-128',
  AES256: 'AES-256',
};

export function createDefaultRadioChannels(now = Date.now()): RadioChannelPreset[] {
  return [
    {
      id: 'rch-pmr-1',
      name: 'PMR-1 · Разведка (основной)',
      rxFreqMhz: '446.00625',
      txFreqMhz: '446.00625',
      mode: 'PMR446',
      bandwidthKhz: '12.5',
      toneMode: 'CTCSS',
      rxTone: '88.5',
      txTone: '88.5',
      squelchLevel: 3,
      powerLevel: 'LOW',
      encryptionType: 'NONE',
      encryptionKey: '',
      txInhibit: false,
      notes: 'Безлицензионный ближний канал группы, субтон 88.5 Hz.',
      updatedAt: now,
    },
    {
      id: 'rch-lpd-tactical',
      name: 'LPD-23 · Периметр (DCS)',
      rxFreqMhz: '433.62500',
      txFreqMhz: '433.62500',
      mode: 'LPD',
      bandwidthKhz: '12.5',
      toneMode: 'DCS',
      rxTone: '023',
      txTone: '023',
      squelchLevel: 4,
      powerLevel: 'MID',
      encryptionType: 'SCRAMBLER',
      encryptionKey: '3300',
      txInhibit: false,
      notes: 'Цифровой шумоподавитель D023N и инверсия спектра.',
      updatedAt: now,
    },
    {
      id: 'rch-vhf-call',
      name: '2м Вызывной / Репитер',
      rxFreqMhz: '145.50000',
      txFreqMhz: '145.50000',
      mode: 'NFM',
      bandwidthKhz: '12.5',
      toneMode: 'CTCSS',
      rxTone: 'NONE',
      txTone: '103.5',
      squelchLevel: 3,
      powerLevel: 'HIGH',
      encryptionType: 'NONE',
      encryptionKey: '',
      txInhibit: false,
      notes: 'Общий вызов 2м; приём открыт, передача с CTCSS 103.5 Hz.',
      updatedAt: now,
    },
    {
      id: 'rch-lora-868',
      name: 'LoRa RU868 · Шифрканал',
      rxFreqMhz: '868.12500',
      txFreqMhz: '868.12500',
      mode: 'LORA',
      bandwidthKhz: '125',
      toneMode: 'NONE',
      rxTone: 'NONE',
      txTone: 'NONE',
      squelchLevel: 0,
      powerLevel: 'LOW',
      encryptionType: 'AES256',
      encryptionKey: '4a9f8c2e10b7d53a6f119c8e2b4d70a15e3c91f48b26a0d7c3e91f20b8a45c61',
      txInhibit: false,
      notes: 'Цифровой канал Meshtastic LongFast RU868 с PSK AES-256.',
      updatedAt: now,
    },
  ];
}

export function normalizeFrequencyMhz(raw: string): string | null {
  const cleaned = raw.trim().replace(',', '.');
  if (!cleaned) return null;
  const parsed = Number(cleaned);
  if (!Number.isFinite(parsed) || parsed < 1.0 || parsed > 1300.0) {
    return null;
  }
  return parsed.toFixed(5);
}

export function computeDuplexOffsetMhz(rxFreqMhz: string, txFreqMhz: string): {
  duplex: '' | '+' | '-' | 'split' | 'off';
  offsetMhz: string;
} {
  const rx = Number(rxFreqMhz.replace(',', '.'));
  const tx = Number(txFreqMhz.replace(',', '.'));
  if (!Number.isFinite(rx) || !Number.isFinite(tx)) {
    return { duplex: '', offsetMhz: '0.00000' };
  }
  const diff = tx - rx;
  if (Math.abs(diff) < 0.00001) {
    return { duplex: '', offsetMhz: '0.00000' };
  }
  return {
    duplex: diff > 0 ? '+' : '-',
    offsetMhz: Math.abs(diff).toFixed(5),
  };
}

export function generateRadioCryptoKey(type: RadioEncryptionType): string {
  if (type === 'NONE') return '';
  if (type === 'SCRAMBLER') {
    const presets = ['2600', '2900', '3100', '3300', '3490'];
    return presets[Math.floor(Math.random() * presets.length)];
  }
  const byteLen = type === 'ARC4' ? 5 : type === 'AES128' ? 16 : 32;
  const bytes = new Uint8Array(byteLen);
  if (typeof globalThis.crypto !== 'undefined' && globalThis.crypto.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < byteLen; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

function csvEscape(val: string): string {
  if (/[",\n\r]/.test(val)) {
    return `"${val.replace(/"/g, '""')}"`;
  }
  return val;
}

/**
 * Generates a CHIRP-compatible CSV radio memory file for Baofeng / Quansheng / handheld transceivers.
 */
export function buildChirpCsv(channels: RadioChannelPreset[]): string {
  const header = 'Location,Name,Frequency,Duplex,Offset,Tone,rToneFreq,cToneFreq,DtcsCode,DtcsPolarity,Mode,TStep,Skip,Comment';
  const rows = channels.map((ch, idx) => {
    const rx = normalizeFrequencyMhz(ch.rxFreqMhz) ?? '446.00625';
    const tx = normalizeFrequencyMhz(ch.txFreqMhz) ?? rx;
    const { duplex, offsetMhz } = computeDuplexOffsetMhz(rx, tx);
    const effectiveDuplex = ch.txInhibit ? 'off' : duplex;

    let toneCol = '';
    let rTone = '88.5';
    let cTone = '88.5';
    let dtcs = '023';
    let dtcsPol = 'NN';

    if (ch.toneMode === 'CTCSS') {
      const hasRx = ch.rxTone !== 'NONE' && ch.rxTone !== '';
      const hasTx = ch.txTone !== 'NONE' && ch.txTone !== '';
      if (hasRx && hasTx) {
        toneCol = 'TSQL';
        rTone = ch.txTone;
        cTone = ch.rxTone;
      } else if (hasTx) {
        toneCol = 'Tone';
        rTone = ch.txTone;
      } else if (hasRx) {
        toneCol = 'TSQL';
        cTone = ch.rxTone;
      }
    } else if (ch.toneMode === 'DCS' || ch.toneMode === 'DCS_INV') {
      toneCol = 'DTCS';
      dtcs = (ch.txTone !== 'NONE' ? ch.txTone : ch.rxTone).replace(/[^0-9]/g, '') || '023';
      dtcsPol = ch.toneMode === 'DCS_INV' ? 'RR' : 'NN';
    }

    const chirpMode = ch.mode === 'FM' ? 'FM' : ch.mode === 'AM' ? 'AM' : 'NFM';
    const cryptoHint = ch.encryptionType !== 'NONE' ? `[${ch.encryptionType}] ` : '';
    const comment = `${cryptoHint}${ch.notes ?? ''}`.trim();

    return [
      String(idx + 1),
      csvEscape(ch.name.slice(0, 16)),
      rx,
      effectiveDuplex,
      offsetMhz,
      toneCol,
      rTone,
      cTone,
      dtcs,
      dtcsPol,
      chirpMode,
      '6.25',
      '',
      csvEscape(comment),
    ].join(',');
  });

  return [header, ...rows].join('\n');
}
