import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Check,
  Copy,
  Download,
  Edit3,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Plus,
  Radio,
  RefreshCw,
  Search,
  ShieldAlert,
  Trash2,
  X,
} from 'lucide-react';
import { tileEnterMotion } from '../core/motion';
import {
  buildChirpCsv,
  computeDuplexOffsetMhz,
  createDefaultRadioChannels,
  CTCSS_TONES,
  DCS_CODES,
  generateRadioCryptoKey,
  normalizeFrequencyMhz,
  RADIO_ENCRYPTION_LABELS,
  RADIO_MODULATION_LABELS,
  RADIO_POWER_LABELS,
  TONE_SQUELCH_LABELS,
} from '../core/radio';
import type {
  RadioChannelPreset,
  RadioEncryptionType,
  RadioModulation,
  RadioPowerLevel,
  ToneSquelchMode,
} from '../core/types';
import { UrbexSelect } from './UrbexSelect';

interface RadioChannelsPanelProps {
  channels: RadioChannelPreset[];
  onSaveChannels: (next: RadioChannelPreset[]) => Promise<void>;
  compact?: boolean;
  canEdit?: boolean;
}

const MODULATIONS: RadioModulation[] = ['PMR446', 'LPD', 'NFM', 'FM', 'DMR', 'LORA', 'AM'];
const TONE_MODES: ToneSquelchMode[] = ['NONE', 'CTCSS', 'DCS', 'DCS_INV'];
const POWER_LEVELS: RadioPowerLevel[] = ['LOW', 'MID', 'HIGH'];
const ENCRYPTION_TYPES: RadioEncryptionType[] = ['NONE', 'SCRAMBLER', 'ARC4', 'AES128', 'AES256'];

function buildBlankChannel(): RadioChannelPreset {
  return {
    id: `rch-${Date.now().toString(36)}`,
    name: '',
    rxFreqMhz: '446.00625',
    txFreqMhz: '446.00625',
    mode: 'NFM',
    bandwidthKhz: '12.5',
    toneMode: 'CTCSS',
    rxTone: '88.5',
    txTone: '88.5',
    squelchLevel: 3,
    powerLevel: 'MID',
    encryptionType: 'NONE',
    encryptionKey: '',
    txInhibit: false,
    notes: '',
    updatedAt: Date.now(),
  };
}

export function RadioChannelsPanel({
  channels,
  onSaveChannels,
  compact = false,
  canEdit = true,
}: RadioChannelsPanelProps) {
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<RadioChannelPreset | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [revealedIds, setRevealedIds] = useState<Record<string, boolean>>({});
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [statusToast, setStatusToast] = useState<string | null>(null);

  useEffect(() => {
    if (!canEdit) {
      setEditing(null);
      setFormError(null);
    }
  }, [canEdit]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return channels;
    return channels.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.rxFreqMhz.includes(q) ||
        c.txFreqMhz.includes(q) ||
        c.mode.toLowerCase().includes(q) ||
        (c.notes ?? '').toLowerCase().includes(q)
    );
  }, [channels, query]);

  const showToast = (msg: string) => {
    setStatusToast(msg);
    window.setTimeout(() => setStatusToast(null), 2800);
  };

  const handleDownloadChirp = () => {
    if (channels.length === 0) return;
    const csv = buildChirpCsv(channels);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `urbex-t-radio-plan-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showToast('Таблица каналов экспортирована в CHIRP CSV.');
  };

  const handleCopyKey = async (id: string, key: string) => {
    if (!key) return;
    try {
      await navigator.clipboard?.writeText(key);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId(null), 1600);
    } catch {
      showToast('Не удалось скопировать ключ в буфер обмена.');
    }
  };

  const handleSubmitForm = async (e: FormEvent) => {
    e.preventDefault();
    if (!canEdit || !editing) return;
    const rx = normalizeFrequencyMhz(editing.rxFreqMhz);
    const tx = normalizeFrequencyMhz(editing.txFreqMhz || editing.rxFreqMhz);
    if (!editing.name.trim()) {
      setFormError('Укажите название или номер канала (например, «Группа А · Основной»).');
      return;
    }
    if (!rx || !tx) {
      setFormError('Частота должна быть в диапазоне 1.00000 – 1300.00000 МГц.');
      return;
    }
    if (editing.encryptionType !== 'NONE' && !editing.encryptionKey.trim()) {
      setFormError('Введите ключ шифрования или нажмите кнопку генерации ключа.');
      return;
    }

    const normalized: RadioChannelPreset = {
      ...editing,
      name: editing.name.trim(),
      rxFreqMhz: rx,
      txFreqMhz: tx,
      encryptionKey: editing.encryptionType === 'NONE' ? '' : editing.encryptionKey.trim(),
      rxTone: editing.toneMode === 'NONE' ? 'NONE' : editing.rxTone || 'NONE',
      txTone: editing.toneMode === 'NONE' ? 'NONE' : editing.txTone || 'NONE',
      notes: editing.notes?.trim() || '',
      updatedAt: Date.now(),
    };

    const exists = channels.some((c) => c.id === normalized.id);
    const next = exists
      ? channels.map((c) => (c.id === normalized.id ? normalized : c))
      : [normalized, ...channels];

    await onSaveChannels(next);
    setEditing(null);
    setFormError(null);
    showToast(exists ? `Канал «${normalized.name}» обновлён.` : `Добавлен канал «${normalized.name}».`);
  };

  const handleDelete = async (id: string) => {
    if (!canEdit) return;
    const next = channels.filter((c) => c.id !== id);
    await onSaveChannels(next);
    if (editing?.id === id) setEditing(null);
    showToast('Канал удалён из радиоплана.');
  };

  const toneValues = editing?.toneMode === 'CTCSS' ? CTCSS_TONES : DCS_CODES;

  return (
    <motion.section
      {...tileEnterMotion(0)}
      data-testid="radio-channels-panel"
      className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3"
    >
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
        <div className="flex items-start gap-2.5 min-w-0">
          <Radio className="w-5 h-5 text-[#6DB386] shrink-0 mt-0.5" />
          <div className="min-w-0">
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Частотный план радиостанций, CTCSS/DCS и шифрование
            </h2>
            <p className="text-[11px] sm:text-xs text-[#98A49E]">
              Хранение рабочих частот (VHF/UHF/PMR/LPD/DMR/LoRa), субтонов шумоподавления и ключей группы.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={handleDownloadChirp}
            disabled={channels.length === 0}
            title="Экспорт таблицы каналов для программатора раций CHIRP"
            className="min-h-[34px] px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] disabled:opacity-40 flex items-center gap-1.5"
          >
            <Download className="w-3.5 h-3.5 text-[#DF9E43]" />
            <span>CHIRP CSV</span>
          </button>

          {canEdit && channels.length === 0 && (
            <button
              type="button"
              onClick={() => onSaveChannels(createDefaultRadioChannels())}
              className="min-h-[34px] px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#98A49E] hover:text-[#E6EBE8]"
            >
              Стандартный сет
            </button>
          )}

          {canEdit && (
            <button
              type="button"
              data-testid="add-radio-channel-btn"
              onClick={() => {
                setFormError(null);
                setEditing(buildBlankChannel());
              }}
              className="min-h-[34px] px-3 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Добавить канал</span>
            </button>
          )}
        </div>
      </div>

      {!canEdit && (
        <div
          data-testid="radio-channels-readonly-banner"
          className="px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center gap-2 text-[11px] sm:text-xs text-[#98A49E]"
        >
          <Lock className="w-3.5 h-3.5 text-[#DF9E43] shrink-0" />
          <span>Режим просмотра · включите «Режим редактора» в Настройках для изменения частот.</span>
        </div>
      )}

      {/* Quick Search */}
      {!compact && channels.length > 2 && (
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-[#6B7771] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Поиск по частоте (напр. 446.006), названию или модуляции..."
            aria-label="Поиск радиоканалов"
            className="w-full h-9 pl-8 pr-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
          />
        </div>
      )}

      <AnimatePresence>
        {statusToast && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            className="p-2.5 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs text-[#E6EBE8]"
          >
            {statusToast}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Channel Add / Edit Form */}
      <AnimatePresence>
        {canEdit && editing && (
          <motion.form
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.16 }}
            onSubmit={handleSubmitForm}
            data-testid="radio-channel-form"
            className="p-3.5 rounded-xl bg-[#0C0F0E] border border-[#5B9D72]/70 space-y-3"
          >
            <div className="flex items-center justify-between gap-2 pb-2 border-b border-[#252E2A]">
              <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8]">
                {channels.some((c) => c.id === editing.id)
                  ? 'Редактирование радиоканала'
                  : 'Новый радиоканал / пресет частоты'}
              </span>
              <button
                type="button"
                onClick={() => {
                  setEditing(null);
                  setFormError(null);
                }}
                aria-label="Закрыть редактор канала"
                className="p-1 rounded hover:bg-[#1A201E] text-[#98A49E]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {formError && (
              <div className="p-2 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs text-[#F1ACA7]">
                {formError}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
              <div className="sm:col-span-2">
                <label className="block text-[11px] text-[#98A49E] mb-1">Название канала</label>
                <input
                  type="text"
                  value={editing.name}
                  onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                  placeholder="Напр. Группа Альфа · Основной"
                  aria-label="Название радиоканала"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                />
              </div>

              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">Частота RX (МГц)</label>
                <input
                  type="text"
                  inputMode="decimal"
                  value={editing.rxFreqMhz}
                  onChange={(e) => {
                    const val = e.target.value;
                    setEditing((prev) =>
                      prev
                        ? {
                            ...prev,
                            rxFreqMhz: val,
                            txFreqMhz:
                              prev.txFreqMhz === prev.rxFreqMhz ? val : prev.txFreqMhz,
                          }
                        : null
                    );
                  }}
                  placeholder="446.00625"
                  aria-label="Частота приёма МГц"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-[11px] text-[#98A49E]">Частота TX (МГц)</label>
                  <button
                    type="button"
                    onClick={() =>
                      setEditing({ ...editing, txFreqMhz: editing.rxFreqMhz })
                    }
                    className="text-[10px] text-[#6DB386] hover:underline"
                  >
                    = RX
                  </button>
                </div>
                <input
                  type="text"
                  inputMode="decimal"
                  value={editing.txFreqMhz}
                  onChange={(e) => setEditing({ ...editing, txFreqMhz: e.target.value })}
                  placeholder="446.00625"
                  aria-label="Частота передачи МГц"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                />
              </div>

              <UrbexSelect<RadioModulation>
                label="Модуляция"
                ariaLabel="Модуляция"
                compact
                value={editing.mode}
                options={MODULATIONS.map((m) => ({
                  value: m,
                  label: RADIO_MODULATION_LABELS[m],
                }))}
                onChange={(val) => setEditing({ ...editing, mode: val })}
              />

              <UrbexSelect<string>
                label="Полоса (кГц)"
                ariaLabel="Ширина полосы кГц"
                compact
                value={editing.bandwidthKhz}
                options={[
                  { value: '12.5', label: '12.5 кГц (узкая)' },
                  { value: '25', label: '25.0 кГц (широкая)' },
                  { value: '62.5', label: '62.5 кГц' },
                  { value: '125', label: '125 кГц (LoRa)' },
                  { value: '250', label: '250 кГц (LoRa)' },
                ]}
                onChange={(val) => setEditing({ ...editing, bandwidthKhz: val })}
              />

              <UrbexSelect<RadioPowerLevel>
                label="Мощность TX"
                ariaLabel="Мощность передатчика"
                compact
                value={editing.powerLevel}
                options={POWER_LEVELS.map((p) => ({
                  value: p,
                  label: RADIO_POWER_LABELS[p],
                }))}
                onChange={(val) => setEditing({ ...editing, powerLevel: val })}
              />

              <UrbexSelect<number>
                label="Шумодав (SQL 0–9)"
                ariaLabel="Порог шумоподавления"
                compact
                value={editing.squelchLevel}
                options={[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => ({
                  value: n,
                  label: `SQL ${n} ${n === 0 ? '(открытый)' : n <= 3 ? '(мягкий)' : '(плотный)'}`,
                }))}
                onChange={(val) => setEditing({ ...editing, squelchLevel: Number(val) })}
              />
            </div>

            {/* CTCSS / DCS Subtone Config */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-2 border-t border-[#252E2A]">
              <UrbexSelect<ToneSquelchMode>
                label="Субтон / Код"
                ariaLabel="Режим субтона CTCSS/DCS"
                compact
                value={editing.toneMode}
                options={TONE_MODES.map((tm) => ({
                  value: tm,
                  label: TONE_SQUELCH_LABELS[tm],
                }))}
                onChange={(nextMode) => {
                  const defaultTone =
                    nextMode === 'CTCSS' ? '88.5' : nextMode === 'NONE' ? 'NONE' : '023';
                  setEditing({
                    ...editing,
                    toneMode: nextMode,
                    rxTone: defaultTone,
                    txTone: defaultTone,
                  });
                }}
              />

              <UrbexSelect<string>
                label="RX тон (приём)"
                ariaLabel="Субтон RX"
                compact
                disabled={editing.toneMode === 'NONE'}
                value={editing.toneMode === 'NONE' ? 'NONE' : editing.rxTone}
                options={[
                  { value: 'NONE', label: 'Без RX тона (CSQ)' },
                  ...toneValues.map((val) => ({
                    value: val,
                    label:
                      editing.toneMode === 'CTCSS'
                        ? `${val} Hz`
                        : `D${val}${editing.toneMode === 'DCS_INV' ? 'I' : 'N'}`,
                  })),
                ]}
                onChange={(val) => setEditing({ ...editing, rxTone: val })}
              />

              <UrbexSelect<string>
                label="TX тон (передача)"
                ariaLabel="Субтон TX"
                compact
                disabled={editing.toneMode === 'NONE'}
                value={editing.toneMode === 'NONE' ? 'NONE' : editing.txTone}
                options={[
                  { value: 'NONE', label: 'Без TX тона' },
                  ...toneValues.map((val) => ({
                    value: val,
                    label:
                      editing.toneMode === 'CTCSS'
                        ? `${val} Hz`
                        : `D${val}${editing.toneMode === 'DCS_INV' ? 'I' : 'N'}`,
                  })),
                ]}
                onChange={(val) => setEditing({ ...editing, txTone: val })}
              />
            </div>

            {/* Encryption & TX Inhibit */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-2 border-t border-[#252E2A]">
              <UrbexSelect<RadioEncryptionType>
                label="Шифрование / скремблер"
                ariaLabel="Алгоритм шифрования"
                compact
                value={editing.encryptionType}
                options={ENCRYPTION_TYPES.map((enc) => ({
                  value: enc,
                  label: RADIO_ENCRYPTION_LABELS[enc],
                }))}
                onChange={(nextType) => {
                  setEditing({
                    ...editing,
                    encryptionType: nextType,
                    encryptionKey:
                      nextType === 'NONE'
                        ? ''
                        : editing.encryptionKey || generateRadioCryptoKey(nextType),
                  });
                }}
              />

              <div className="sm:col-span-2">
                <div className="flex items-center justify-between mb-1">
                  <label className="text-[11px] text-[#98A49E]">
                    Ключ шифрования / частота скремблера
                  </label>
                  {editing.encryptionType !== 'NONE' && (
                    <button
                      type="button"
                      onClick={() =>
                        setEditing({
                          ...editing,
                          encryptionKey: generateRadioCryptoKey(editing.encryptionType),
                        })
                      }
                      className="text-[11px] text-[#DF9E43] hover:underline flex items-center gap-1"
                    >
                      <RefreshCw className="w-3 h-3" />
                      <span>Сгенерировать ключ</span>
                    </button>
                  )}
                </div>
                <input
                  type="text"
                  disabled={editing.encryptionType === 'NONE'}
                  value={editing.encryptionKey}
                  onChange={(e) => setEditing({ ...editing, encryptionKey: e.target.value })}
                  placeholder={
                    editing.encryptionType === 'NONE'
                      ? 'Без шифрования'
                      : editing.encryptionType === 'SCRAMBLER'
                        ? 'Напр. 3300 Гц'
                        : 'HEX-ключ (напр. 32/64 символа)'
                  }
                  aria-label="Ключ шифрования радиоканала"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8] disabled:opacity-45"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-2.5 items-center">
              <input
                type="text"
                value={editing.notes ?? ''}
                onChange={(e) => setEditing({ ...editing, notes: e.target.value })}
                placeholder="Заметка: позывные, сектор, репитер..."
                aria-label="Примечание к радиоканалу"
                className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
              />
              <label className="flex items-center gap-2 px-3 h-9 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] cursor-pointer select-none whitespace-nowrap">
                <input
                  type="checkbox"
                  checked={Boolean(editing.txInhibit)}
                  onChange={(e) => setEditing({ ...editing, txInhibit: e.target.checked })}
                  className="accent-[#DF9E43]"
                />
                <span>Только приём (TX Lock)</span>
              </label>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-[#252E2A]">
              <button
                type="button"
                onClick={() => {
                  setEditing(null);
                  setFormError(null);
                }}
                className="min-h-[34px] px-3.5 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
              >
                Отмена
              </button>
              <button
                type="submit"
                className="min-h-[34px] px-4 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386]"
              >
                Сохранить канал
              </button>
            </div>
          </motion.form>
        )}
      </AnimatePresence>

      {/* Channel List */}
      {filtered.length === 0 ? (
        <div className="p-6 rounded-xl bg-[#0C0F0E] border border-[#252E2A] text-center text-xs text-[#98A49E]">
          {canEdit
            ? 'Радиоканалы не найдены. Нажмите «Добавить канал» чтобы забить частоты и субтоны.'
            : 'Радиоканалы не найдены. Включите «Режим редактора» в Настройках, чтобы добавить частоты.'}
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-2.5">
          {filtered.map((ch, idx) => {
            const { duplex, offsetMhz } = computeDuplexOffsetMhz(ch.rxFreqMhz, ch.txFreqMhz);
            const revealed = Boolean(revealedIds[ch.id]);
            const hasCrypto = ch.encryptionType !== 'NONE' && Boolean(ch.encryptionKey);
            const toneBadge =
              ch.toneMode === 'NONE'
                ? 'CSQ'
                : `${ch.toneMode === 'CTCSS' ? 'CTCSS' : ch.toneMode === 'DCS_INV' ? 'DCS-I' : 'DCS'} RX:${ch.rxTone} / TX:${ch.txTone}`;

            return (
              <motion.div
                key={ch.id}
                {...tileEnterMotion(idx + 1)}
                className="p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] hover:border-[#323E39] transition-colors space-y-2"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                        {ch.name}
                      </span>
                      {ch.txInhibit && (
                        <span className="px-1.5 py-0.5 rounded bg-[#2A2218] border border-[#DF9E43]/40 text-[10px] font-medium text-[#DF9E43] flex items-center gap-1">
                          <ShieldAlert className="w-2.5 h-2.5" />
                          <span>RX ONLY</span>
                        </span>
                      )}
                    </div>
                    <div className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5 text-xs font-mono tabular-nums mt-0.5">
                      <span className="text-[#6DB386] font-semibold">
                        RX {ch.rxFreqMhz} МГц
                      </span>
                      {duplex ? (
                        <span className="text-[#DF9E43]">
                          TX {ch.txFreqMhz} ({duplex}{offsetMhz})
                        </span>
                      ) : (
                        <span className="text-[#6B7771]">Симплекс</span>
                      )}
                      <span className="text-[#98A49E]">
                        {ch.mode} · {ch.bandwidthKhz} кГц · {ch.powerLevel} · SQL {ch.squelchLevel}
                      </span>
                    </div>
                  </div>

                  {canEdit && (
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        onClick={() => {
                          setFormError(null);
                          setEditing({ ...ch });
                        }}
                        aria-label={`Изменить канал ${ch.name}`}
                        className="min-h-[30px] min-w-[30px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#5B9D72]"
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(ch.id)}
                        aria-label={`Удалить канал ${ch.name}`}
                        className="min-h-[30px] min-w-[30px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860]"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </div>

                {/* Tone Squelch + Crypto Row */}
                <div className="flex flex-wrap items-center justify-between gap-2 pt-1.5 border-t border-[#252E2A]/80 text-[11px]">
                  <span className="px-2 py-0.5 rounded bg-[#131816] border border-[#252E2A] font-mono text-[#E6EBE8]">
                    {toneBadge}
                  </span>

                  {hasCrypto ? (
                    <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-[#17211C] border border-[#5B9D72]/50 font-mono text-[#6DB386] max-w-full">
                      <Lock className="w-3 h-3 shrink-0" />
                      <span>{ch.encryptionType}:</span>
                      <span className="truncate max-w-[140px]">
                        {revealed
                          ? ch.encryptionKey
                          : `${ch.encryptionKey.slice(0, 4)}••••${ch.encryptionKey.slice(-2)}`}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setRevealedIds((prev) => ({ ...prev, [ch.id]: !prev[ch.id] }))
                        }
                        aria-label={revealed ? 'Скрыть ключ' : 'Показать ключ'}
                        className="p-0.5 hover:text-[#E6EBE8]"
                      >
                        {revealed ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleCopyKey(ch.id, ch.encryptionKey)}
                        aria-label="Скопировать ключ"
                        className="p-0.5 hover:text-[#E6EBE8]"
                      >
                        {copiedId === ch.id ? (
                          <Check className="w-3 h-3 text-[#6DB386]" />
                        ) : (
                          <Copy className="w-3 h-3" />
                        )}
                      </button>
                    </div>
                  ) : (
                    <span className="px-2 py-0.5 rounded bg-[#131816] border border-[#252E2A] text-[#6B7771] flex items-center gap-1">
                      <KeyRound className="w-3 h-3" />
                      <span>Открытый канал</span>
                    </span>
                  )}
                </div>

                {ch.notes && (
                  <p className="text-[11px] text-[#98A49E] leading-snug">{ch.notes}</p>
                )}
              </motion.div>
            );
          })}
        </div>
      )}
    </motion.section>
  );
}
