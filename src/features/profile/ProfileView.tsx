import { useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Shield, User } from 'lucide-react';
import { ROLE_LABELS } from '../../core/labels';
import { normalizeNodeId } from '../../core/meshtastic';
import type { OperatorRole, UserProfile } from '../../core/types';
import { UrbexSelect } from '../../components/UrbexSelect';

interface ProfileViewProps {
  profile: UserProfile;
  stats: {
    objectsCount: number;
    archiveCount: number;
    meshCount: number;
    articlesCount: number;
  };
  onSaveProfile: (profile: UserProfile) => Promise<void>;
}

const ROLES: OperatorRole[] = ['scout', 'cartographer', 'comms', 'archivist'];

export default function ProfileView({
  profile,
  stats,
  onSaveProfile,
}: ProfileViewProps) {
  const [callsign, setCallsign] = useState(profile.callsign);
  const [nodeId, setNodeId] = useState(profile.nodeId);
  const [role, setRole] = useState<OperatorRole>(profile.role);
  const [emergencyContact, setEmergencyContact] = useState(profile.emergencyContact);
  const [bio, setBio] = useState(profile.bio);
  const [savedToast, setSavedToast] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const nextCallsign = callsign.trim() || profile.callsign;
    const normalizedNode = normalizeNodeId(nodeId || profile.nodeId);
    await onSaveProfile({
      ...profile,
      callsign: nextCallsign,
      nodeId: normalizedNode,
      role,
      emergencyContact: emergencyContact.trim(),
      bio: bio.trim(),
      updatedAt: Date.now(),
    });
    setNodeId(normalizedNode);
    setSavedToast(true);
    window.setTimeout(() => setSavedToast(false), 2600);
  };

  return (
    <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1.25fr_1fr] gap-3 sm:gap-4">
      <motion.form
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.18, ease: 'easeOut' }}
        onSubmit={handleSubmit}
        className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5 sm:space-y-3.5"
      >
        <div className="flex items-center justify-between gap-2 pb-2 sm:pb-3 border-b border-[#252E2A]">
          <div className="flex items-center gap-2">
            <User className="w-4 h-4 sm:w-5 sm:h-5 text-[#5B9D72]" />
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Локальный профиль оператора
            </h2>
          </div>
          <span className="text-xs font-mono text-[#98A49E]">{profile.nodeId}</span>
        </div>

        <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5 sm:gap-3">
          <div>
            <label htmlFor="prof-callsign" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
              Позывной
            </label>
            <input
              id="prof-callsign"
              type="text"
              value={callsign}
              onChange={(e) => setCallsign(e.target.value)}
              className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72] transition-colors"
            />
          </div>

          <div>
            <label htmlFor="prof-node" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
              Mesh Node ID (HEX)
            </label>
            <input
              id="prof-node"
              type="text"
              value={nodeId}
              onChange={(e) => setNodeId(e.target.value)}
              className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72] transition-colors"
            />
          </div>
        </div>

        <UrbexSelect<OperatorRole>
          label="Роль в группе"
          ariaLabel="Роль в группе"
          value={role}
          options={ROLES.map((r) => ({
            value: r,
            label: ROLE_LABELS[r],
          }))}
          onChange={(nextRole) => setRole(nextRole)}
        />

        <div>
          <label htmlFor="prof-emergency" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
            Аварийный контакт / резервная частота
          </label>
          <input
            id="prof-emergency"
            type="text"
            value={emergencyContact}
            onChange={(e) => setEmergencyContact(e.target.value)}
            className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72] transition-colors"
          />
        </div>

        <div>
          <label htmlFor="prof-bio" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
            Специализация и примечания
          </label>
          <textarea
            id="prof-bio"
            rows={2}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            className="w-full p-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72] transition-colors"
          />
        </div>

        <div className="flex items-center justify-between gap-3 pt-1">
          <div className="min-h-[20px]">
            <AnimatePresence>
              {savedToast && (
                <motion.span
                  initial={{ opacity: 0, x: -6 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -4 }}
                  transition={{ duration: 0.16 }}
                  className="text-xs text-[#6DB386] inline-block"
                >
                  Профиль сохранён локально.
                </motion.span>
              )}
            </AnimatePresence>
          </div>
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.96 }}
            type="submit"
            className="min-h-[36px] lg:min-h-[42px] px-4 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] transition-colors"
          >
            Сохранить профиль
          </motion.button>
        </div>
      </motion.form>

      {/* Local PDA Stats & Role Permissions */}
      <motion.aside
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2, delay: 0.04, ease: 'easeOut' }}
        className="space-y-2.5 sm:space-y-4"
      >
        <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
          <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">Сводка локальной базы</h2>
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {[
              { label: 'Объекты', value: stats.objectsCount },
              { label: 'Файлы архива', value: stats.archiveCount },
              { label: 'Mesh-пакеты', value: stats.meshCount },
              { label: 'Справочники', value: stats.articlesCount },
            ].map((stat) => (
              <div
                key={stat.label}
                className="p-2.5 sm:p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A]"
              >
                <div className="text-[11px] sm:text-xs text-[#98A49E]">{stat.label}</div>
                <div className="text-xl sm:text-2xl font-mono tabular-nums font-semibold text-[#E6EBE8] mt-0.5">
                  {stat.value}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs sm:text-sm font-semibold text-[#E6EBE8]">
            <Shield className="w-4 h-4 text-[#D39239]" />
            <span>Автономность идентификации</span>
          </div>
          <p className="text-[11px] sm:text-xs text-[#98A49E] leading-relaxed">
            Учётная запись хранится только в локальной базе устройства без облачных серверов и привязки к аккаунтам. Ваш Node ID подписывает создаваемые точки и пакеты в Mesh-сети и экспортируется в резервную копию .urbexdb.
          </p>
        </div>
      </motion.aside>
    </div>
  );
}
