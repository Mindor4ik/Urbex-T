import { useMemo, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  AlertTriangle,
  Bluetooth,
  MapPin,
  Radio,
  Send,
  Settings,
  Usb,
} from 'lucide-react';
import { formatCoordinates } from '../../core/geo';
import { formatShortTime, ROLE_LABELS } from '../../core/labels';
import { detectMeshHardwareSupport } from '../../core/meshtastic';
import type {
  CoordinateFormat,
  GpsFix,
  MeshChannel,
  MeshMessage,
  MeshNode,
  MeshPacketKind,
  UserProfile,
} from '../../core/types';

interface MeshViewProps {
  meshEnabled: boolean;
  channels: MeshChannel[];
  nodes: MeshNode[];
  messages: MeshMessage[];
  profile: UserProfile;
  gpsFix: GpsFix;
  coordFormat: CoordinateFormat;
  onSendPacket: (params: {
    channelId: string;
    kind: MeshPacketKind;
    body: string;
    hopLimit: number;
    lat?: number;
    lon?: number;
  }) => Promise<void>;
  onOpenSettings: () => void;
}

export default function MeshView({
  meshEnabled,
  channels,
  nodes,
  messages,
  profile,
  gpsFix,
  coordFormat,
  onSendPacket,
  onOpenSettings,
}: MeshViewProps) {
  const [activeChannelId, setActiveChannelId] = useState(channels[0]?.id ?? 'ch-primary');
  const [draft, setDraft] = useState('');
  const [attachCoords, setAttachCoords] = useState(false);
  const [sosConfirmOpen, setSosConfirmOpen] = useState(false);
  const [sosNote, setSosNote] = useState('Требуется эвакуация / потеря ориентации');
  const [hwMessage, setHwMessage] = useState<string | null>(null);

  const hwSupport = useMemo(() => detectMeshHardwareSupport(), []);
  const activeChannel = useMemo(
    () => channels.find((c) => c.id === activeChannelId) ?? channels[0],
    [channels, activeChannelId]
  );

  const channelMessages = useMemo(
    () => messages.filter((m) => m.channelId === activeChannel?.id),
    [messages, activeChannel]
  );

  if (!meshEnabled) {
    return (
      <motion.section
        initial={{ opacity: 0, scale: 0.97, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.18, ease: 'easeOut' }}
        aria-label="Модуль Meshtastic отключён"
        className="bg-[#131816] border border-[#252E2A] rounded-xl p-8 sm:p-10 max-w-xl mx-auto mt-6 text-center space-y-4"
      >
        <div className="w-12 h-12 mx-auto rounded-xl bg-[#1A201E] border border-[#252E2A] flex items-center justify-center">
          <Radio className="w-6 h-6 text-[#6B7771]" />
        </div>
        <div className="space-y-2">
          <h2 className="text-lg font-semibold text-[#E6EBE8]">
            Модуль Meshtastic отключён
          </h2>
          <p className="text-sm text-[#98A49E] leading-relaxed">
            Обмен пакетами и фоновый ретранслятор отключены для экономии энергии. Включите модуль Meshtastic в настройках терминала, чтобы работать с каналами и подключёнными LoRa-узлами.
          </p>
        </div>
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.96 }}
          type="button"
          onClick={onOpenSettings}
          className="min-h-[44px] px-5 py-2.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-sm font-semibold hover:bg-[#6DB386] transition-colors inline-flex items-center gap-2"
        >
          <Settings className="w-4 h-4" />
          <span>Открыть настройки</span>
        </motion.button>
      </motion.section>
    );
  }

  const handleSend = async (e: FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body && !attachCoords) return;

    await onSendPacket({
      channelId: activeChannel.id,
      kind: attachCoords ? 'position' : 'text',
      body: body || 'Отметка текущей позиции',
      hopLimit: activeChannel.hopLimit,
      lat: attachCoords ? gpsFix.lat : undefined,
      lon: attachCoords ? gpsFix.lon : undefined,
    });
    setDraft('');
    setAttachCoords(false);
  };

  const handleConfirmSos = async () => {
    const sosChannel = channels.find((c) => c.id === 'ch-sos') ?? activeChannel;
    await onSendPacket({
      channelId: sosChannel.id,
      kind: 'sos',
      body: `SOS: ${sosNote.trim() || 'Аварийный маяк'}`,
      hopLimit: 7,
      lat: gpsFix.lat,
      lon: gpsFix.lon,
    });
    setActiveChannelId(sosChannel.id);
    setSosConfirmOpen(false);
  };

  const handleProbeSerial = async () => {
    const nav = navigator as Navigator & { serial?: { requestPort: () => Promise<unknown> } };
    if (!nav.serial) {
      setHwMessage('Web Serial недоступен в этом браузере. Используйте локальный ретранслятор или нативное Android-приложение.');
      return;
    }
    try {
      await nav.serial.requestPort();
      setHwMessage('USB LoRa-модем подключён по Web Serial.');
    } catch {
      setHwMessage('Выбор USB-порта отменён пользователем.');
    }
  };

  const handleProbeBle = async () => {
    const nav = navigator as Navigator & {
      bluetooth?: { requestDevice: (opts: { acceptAllDevices: boolean }) => Promise<unknown> };
    };
    if (!nav.bluetooth) {
      setHwMessage('Web Bluetooth недоступен на этом устройстве.');
      return;
    }
    try {
      await nav.bluetooth.requestDevice({ acceptAllDevices: true });
      setHwMessage('BLE-устройство Meshtastic сопряжено.');
    } catch {
      setHwMessage('Поиск BLE-модуля отменён.');
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-4">
      {/* Left: Channels, Hardware Bridge & Discovered Peers */}
      <aside className="space-y-4">
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3 space-y-2">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-sm font-semibold text-[#E6EBE8]">Каналы Meshtastic</h2>
            <span className="text-xs font-medium text-[#6DB386]">Активен</span>
          </div>

          <div className="space-y-1">
            {channels.map((ch) => {
              const count = messages.filter((m) => m.channelId === ch.id).length;
              const isSelected = ch.id === activeChannel?.id;
              return (
                <motion.button
                  key={ch.id}
                  whileTap={{ scale: 0.98 }}
                  type="button"
                  onClick={() => setActiveChannelId(ch.id)}
                  className={`relative w-full text-left px-3 py-2.5 rounded-lg border transition-colors ${
                    isSelected
                      ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-medium">{ch.name}</span>
                    <span className="text-xs font-mono tabular-nums">{count}</span>
                  </div>
                  <div className="text-xs text-[#6B7771] mt-0.5 font-mono tabular-nums">
                    Hop {ch.hopLimit}
                  </div>
                </motion.button>
              );
            })}
          </div>
        </section>

        {/* Hardware Interface Bridge */}
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3 space-y-2.5">
          <h2 className="text-sm font-semibold text-[#E6EBE8]">Радиомодуль и транспорт</h2>
          <div className="text-xs text-[#98A49E] space-y-1">
            <div>
              Свой узел:{' '}
              <span className="font-mono text-[#E6EBE8]">{profile.nodeId}</span> (
              {profile.callsign})
            </div>
            <div>
              Ретранслятор:{' '}
              <span className="text-[#6DB386]">
                {hwSupport.broadcastChannelSupported ? 'Broadcast активен' : 'Только локально'}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <motion.button
              whileTap={{ scale: 0.96 }}
              type="button"
              onClick={handleProbeSerial}
              className="min-h-[40px] px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center justify-center gap-1.5"
            >
              <Usb className="w-3.5 h-3.5 text-[#98A49E]" />
              <span>USB LoRa</span>
            </motion.button>
            <motion.button
              whileTap={{ scale: 0.96 }}
              type="button"
              onClick={handleProbeBle}
              className="min-h-[40px] px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center justify-center gap-1.5"
            >
              <Bluetooth className="w-3.5 h-3.5 text-[#98A49E]" />
              <span>BLE Модем</span>
            </motion.button>
          </div>
          <AnimatePresence>
            {hwMessage && (
              <motion.p
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.15 }}
                className="text-xs text-[#DF9E43]"
              >
                {hwMessage}
              </motion.p>
            )}
          </AnimatePresence>
        </section>

        {/* Real Discovered Mesh Nodes Only */}
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3 space-y-2">
          <h2 className="text-sm font-semibold text-[#E6EBE8]">
            Обнаруженные узлы ({nodes.length})
          </h2>
          {nodes.length === 0 ? (
            <p className="text-xs text-[#6B7771] leading-relaxed">
              Узлы в радиусе не обнаружены. Подключите LoRa-модем по USB/BLE или откройте соседнее устройство в сети.
            </p>
          ) : (
            <div className="divide-y divide-[#252E2A]">
              {nodes.map((node) => (
                <div key={node.nodeId} className="py-2 first:pt-0 last:pb-0 text-xs space-y-0.5">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-[#E6EBE8]">{node.callsign}</span>
                    <span className="font-mono tabular-nums text-[#98A49E]">
                      Hop {node.hopsAway}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[#6B7771] font-mono tabular-nums">
                    <span>
                      {node.nodeId} · {ROLE_LABELS[node.role]}
                    </span>
                    <span>{formatShortTime(node.lastHeardAt)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </aside>

      {/* Right: Packet Feed & Composer */}
      <section
        aria-label="Поток сообщений канала"
        className="flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden min-h-[520px]"
      >
        {/* Channel Header + SOS Trigger */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-b border-[#252E2A]">
          <div>
            <div className="flex items-center gap-2">
              <Radio className="w-4 h-4 text-[#5B9D72]" />
              <h2 className="text-base font-semibold text-[#E6EBE8]">
                {activeChannel?.name}
              </h2>
            </div>
            <p className="text-xs text-[#98A49E] font-mono tabular-nums mt-0.5">
              Hop Limit {activeChannel?.hopLimit}
            </p>
          </div>

          <button
            type="button"
            onClick={() => setSosConfirmOpen((v) => !v)}
            className="min-h-[44px] px-4 py-2 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs font-semibold text-[#F1ACA7] hover:bg-[#C7564E] hover:text-[#0C0F0E] transition-colors flex items-center gap-1.5 whitespace-nowrap"
          >
            <AlertTriangle className="w-4 h-4" />
            <span>Маяк SOS</span>
          </button>
        </div>

        {/* SOS Confirmation Panel */}
        <AnimatePresence>
          {sosConfirmOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              className="overflow-hidden border-b border-[#C7564E]"
            >
              <div className="p-4 bg-[#381B19]/70 space-y-3">
                <div className="text-sm font-semibold text-[#F1ACA7]">
                  Подтверждение передачи аварийного пакета SOS
                </div>
                <p className="text-xs text-[#E6EBE8]">
                  Пакет будет отправлен в аварийный канал (Hop 7) с текущими координатами:{' '}
                  <span className="font-mono tabular-nums">
                    {formatCoordinates(gpsFix.lat, gpsFix.lon, coordFormat)}
                  </span>
                </p>
                <div className="flex flex-col sm:flex-row gap-2">
                  <input
                    type="text"
                    value={sosNote}
                    onChange={(e) => setSosNote(e.target.value)}
                    aria-label="Причина SOS"
                    className="flex-1 h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#C7564E]/60 text-sm text-[#E6EBE8]"
                  />
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setSosConfirmOpen(false)}
                      className="min-h-[44px] px-4 py-2 rounded-lg bg-[#1A201E] text-xs font-medium text-[#E6EBE8]"
                    >
                      Отмена
                    </button>
                    <button
                      type="button"
                      onClick={handleConfirmSos}
                      className="min-h-[44px] px-4 py-2 rounded-lg bg-[#C7564E] text-[#0C0F0E] text-xs font-semibold"
                    >
                      Передать SOS
                    </button>
                  </div>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Messages Feed */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {channelMessages.length === 0 ? (
            <div className="py-12 text-center text-sm text-[#98A49E]">
              В канале пока нет принятых или отправленных пакетов.
            </div>
          ) : (
            channelMessages.map((msg) => {
              const isOwn = msg.senderNodeId === profile.nodeId;
              const isSos = msg.kind === 'sos';
              return (
                <motion.article
                  key={msg.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.14 }}
                  className={`p-3.5 rounded-xl border space-y-1.5 ${
                    isSos
                      ? 'bg-[#381B19]/60 border-[#C7564E]'
                      : isOwn
                      ? 'bg-[#1A201E] border-[#323E39]'
                      : 'bg-[#0C0F0E] border-[#252E2A]'
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-[#E6EBE8]">
                        {msg.senderCallsign}
                      </span>
                      <span className="font-mono text-[#6B7771]">{msg.senderNodeId}</span>
                      {isSos && (
                        <span className="font-semibold text-[#DC6860]">✖ АВАРИЙНЫЙ SOS</span>
                      )}
                    </div>
                    <span className="font-mono tabular-nums text-[#98A49E]">
                      {formatShortTime(msg.timestamp)} · Hop {msg.hopLimit}
                    </span>
                  </div>

                  <p className="text-sm text-[#E6EBE8] leading-relaxed">{msg.body}</p>

                  {msg.lat !== undefined && msg.lon !== undefined && (
                    <div className="text-xs font-mono tabular-nums text-[#DF9E43] flex items-center gap-1.5 pt-0.5">
                      <MapPin className="w-3.5 h-3.5 shrink-0" />
                      <span>{formatCoordinates(msg.lat, msg.lon, coordFormat)}</span>
                    </div>
                  )}

                  <div className="text-[11px] font-mono text-[#6B7771] truncate">
                    Кадр: {msg.frameHex.slice(0, 36)}…
                  </div>
                </motion.article>
              );
            })
          )}
        </div>

        {/* Message Composer */}
        <form
          onSubmit={handleSend}
          className="p-3 border-t border-[#252E2A] bg-[#0C0F0E] space-y-2"
        >
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <button
              type="button"
              onClick={() => setAttachCoords((v) => !v)}
              aria-pressed={attachCoords}
              className={`min-h-[36px] px-3 py-1 rounded-lg border flex items-center gap-1.5 transition-colors ${
                attachCoords
                  ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
              }`}
            >
              <MapPin className="w-3.5 h-3.5 text-[#D39239]" />
              <span>
                {attachCoords
                  ? `Координаты прикреплены (${formatCoordinates(gpsFix.lat, gpsFix.lon, coordFormat)})`
                  : 'Прикрепить мои координаты'}
              </span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="text"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              aria-label="Текст сообщения в Mesh-канал"
              placeholder={`Сообщение в ${activeChannel?.name}...`}
              className="flex-1 h-11 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
            <button
              type="submit"
              className="min-h-[44px] px-4 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-sm font-semibold hover:bg-[#6DB386] transition-colors flex items-center gap-1.5 whitespace-nowrap"
            >
              <Send className="w-4 h-4" />
              <span>Отправить</span>
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
