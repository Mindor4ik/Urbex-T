import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  Code2,
  Compass,
  Database,
  FolderOpen,
  Layers,
  Map as MapIcon,
  MapPin,
  RefreshCw,
  Sliders,
  X,
} from 'lucide-react';
import { PDA_ORIENTATION_LABELS } from '../core/labels';
import { EASE_OUT_TACTICAL, tileEnterMotion } from '../core/motion';
import type {
  AppSettings,
  CoordinateFormat,
  DatabaseWorkspaceMeta,
  PdaOrientation,
  UrbexDbBundle,
  UserProfile,
} from '../core/types';
import type { ParseUrbexDbResult } from '../core/urbexdb';
import { DatabaseTemplateModal } from './DatabaseTemplateModal';

interface WelcomeScreenProps {
  settings: AppSettings;
  profile: UserProfile;
  isReopenedFromSettings?: boolean;
  onClose: () => void;
  onUpdateProfile: (next: UserProfile) => Promise<void>;
  onUpdateSettings: (next: AppSettings) => Promise<void>;
  onCreateDatabase: (params: {
    name: string;
    regionLabel: string;
    description?: string;
    mode: 'empty' | 'seed';
    activateImmediately?: boolean;
  }) => Promise<DatabaseWorkspaceMeta | null>;
  onInspectUrbexDb: (raw: string) => ParseUrbexDbResult;
  onImportIntoNewDatabase: (
    name: string,
    regionLabel: string,
    bundle: UrbexDbBundle
  ) => Promise<DatabaseWorkspaceMeta>;
}

const STEP_VARIANTS = {
  initial: (dir: number) => ({
    opacity: 0,
    x: dir >= 0 ? 28 : -28,
    scale: 0.982,
  }),
  animate: {
    opacity: 1,
    x: 0,
    scale: 1,
    transition: { duration: 0.24, ease: EASE_OUT_TACTICAL },
  },
  exit: (dir: number) => ({
    opacity: 0,
    x: dir >= 0 ? -22 : 22,
    scale: 0.985,
    transition: { duration: 0.16, ease: EASE_OUT_TACTICAL },
  }),
};

const HIGHLIGHTS = [
  { icon: MapIcon, title: 'Карты и GPS', subtitle: 'Офлайн-секторы и маркеры' },
  { icon: MapPin, title: 'Реестр и зоны', subtitle: 'Карточки точек и периметры' },
  { icon: BookOpen, title: 'Архив и база', subtitle: 'Фото, заметки и инструкции' },
  { icon: RefreshCw, title: 'Формат .urbexdb', subtitle: 'Несколько баз и E2EE облако' },
] as const;

const COORD_OPTIONS: Array<{ value: CoordinateFormat; label: string }> = [
  { value: 'dd', label: 'DD (градусы)' },
  { value: 'dms', label: 'DMS (мин/сек)' },
  { value: 'utm', label: 'UTM (метры)' },
];

const ORIENTATION_OPTIONS: PdaOrientation[] = ['portrait', 'landscape', 'auto'];

export function WelcomeScreen({
  settings,
  profile,
  isReopenedFromSettings = false,
  onClose,
  onUpdateProfile,
  onUpdateSettings,
  onCreateDatabase,
  onInspectUrbexDb,
  onImportIntoNewDatabase,
}: WelcomeScreenProps) {
  const [step, setStep] = useState(0);
  const [direction, setDirection] = useState(1);
  const [callsign, setCallsign] = useState(profile.callsign);
  const [enableEditor, setEnableEditor] = useState(
    isReopenedFromSettings ? Boolean(settings.editorEnabled) : true
  );
  const [coordFormat, setCoordFormat] = useState<CoordinateFormat>(settings.coordinateFormat);
  const [orientation, setOrientation] = useState<PdaOrientation>(
    settings.pdaOrientation ?? 'portrait'
  );

  const [dbName, setDbName] = useState('Мой сектор');
  const [dbRegion, setDbRegion] = useState('');
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [pendingImport, setPendingImport] = useState<UrbexDbBundle | null>(null);
  const [importWarnings, setImportWarnings] = useState<string[]>([]);
  const [importDbName, setImportDbName] = useState('');
  const [showTemplateModal, setShowTemplateModal] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const goToStep = (nextStep: number) => {
    setErrorMessage(null);
    setDirection(nextStep >= step ? 1 : -1);
    setStep(nextStep);
  };

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || busy || showTemplateModal || pendingImport) return;
      if (step > 0) {
        setErrorMessage(null);
        setDirection(-1);
        setStep((prev) => Math.max(0, prev - 1));
      } else {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [busy, showTemplateModal, pendingImport, step, onClose]);

  const applyOperatorPreferences = async () => {
    const trimmed = callsign.trim();
    if (trimmed && trimmed !== profile.callsign) {
      await onUpdateProfile({
        ...profile,
        callsign: trimmed,
        updatedAt: Date.now(),
      });
    }
    if (
      Boolean(settings.editorEnabled) !== enableEditor ||
      settings.coordinateFormat !== coordFormat ||
      (settings.pdaOrientation ?? 'portrait') !== orientation
    ) {
      await onUpdateSettings({
        ...settings,
        editorEnabled: enableEditor,
        coordinateFormat: coordFormat,
        pdaOrientation: orientation,
        updatedAt: Date.now(),
      });
    }
  };

  const handleFileChange = (file: File | undefined) => {
    if (!file) return;
    setErrorMessage(null);
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== 'string') return;
      const res = onInspectUrbexDb(reader.result);
      if (!res.ok) {
        setErrorMessage(res.error);
        return;
      }
      setImportWarnings(res.warnings);
      setImportDbName(
        file.name.replace(/\.(urbexdb|json)$/i, '').trim() || `БД ${res.bundle.manifest.deviceNodeId}`
      );
      setPendingImport(res.bundle);
    };
    reader.readAsText(file);
  };

  const handleCreateEmptyDb = async () => {
    const trimmed = dbName.trim();
    if (trimmed.length < 2) {
      setErrorMessage('Укажите название новой базы (минимум 2 символа).');
      return;
    }
    setBusy(true);
    setErrorMessage(null);
    try {
      await applyOperatorPreferences();
      await onCreateDatabase({
        name: trimmed,
        regionLabel: dbRegion.trim() || trimmed,
        mode: 'empty',
        activateImmediately: true,
      });
      onClose();
    } catch (err) {
      setErrorMessage(
        `Ошибка создания базы: ${err instanceof Error ? err.message : 'повторите попытку'}`
      );
    } finally {
      setBusy(false);
    }
  };

  const handleLoadDemoDb = async () => {
    setBusy(true);
    setErrorMessage(null);
    try {
      await applyOperatorPreferences();
      await onCreateDatabase({
        name: 'Москва · Сектор-1',
        regionLabel: 'Москва и область',
        mode: 'seed',
        activateImmediately: true,
      });
      onClose();
    } catch (err) {
      setErrorMessage(
        `Ошибка загрузки демо-базы: ${err instanceof Error ? err.message : 'повторите попытку'}`
      );
    } finally {
      setBusy(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!pendingImport) return;
    setBusy(true);
    setErrorMessage(null);
    try {
      await applyOperatorPreferences();
      const cleanName = importDbName.trim() || `БД ${pendingImport.manifest.deviceNodeId}`;
      await onImportIntoNewDatabase(cleanName, cleanName, pendingImport);
      setPendingImport(null);
      onClose();
    } catch (err) {
      setPendingImport(null);
      setErrorMessage(
        `Ошибка импорта: ${err instanceof Error ? err.message : 'повторите попытку'}`
      );
    } finally {
      setBusy(false);
    }
  };

  const handleFinishWithCurrentPrefs = async () => {
    setBusy(true);
    setErrorMessage(null);
    try {
      await applyOperatorPreferences();
      onClose();
    } finally {
      setBusy(false);
    }
  };

  return (
    <motion.div
      data-testid="welcome-screen"
      initial={{ opacity: 0, y: 18, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -10, scale: 0.985 }}
      transition={{ duration: 0.34, ease: EASE_OUT_TACTICAL }}
      className="min-h-screen bg-[#0C0F0E] text-[#E6EBE8] flex flex-col items-center px-3.5 py-4 sm:px-6 sm:py-8 overflow-y-auto"
    >
      <input
        ref={fileInputRef}
        type="file"
        accept=".urbexdb,.json,application/json"
        className="hidden"
        onChange={(e) => {
          handleFileChange(e.target.files?.[0]);
          e.target.value = '';
        }}
      />

      <div className="w-full max-w-[560px] lg:max-w-[780px] space-y-3">
        {/* Top progress header */}
        <motion.div {...tileEnterMotion(0, 8)} className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <span className="font-mono text-[11px] font-semibold tracking-wide text-[#6DB386]">
              {isReopenedFromSettings ? 'URBEX-T · СТАРТОВЫЙ ЭКРАН' : 'URBEX-T · ПЕРВЫЙ ЗАПУСК'}
            </span>
            <div className="flex items-center gap-2">
              <span className="font-mono text-[11px] text-[#98A49E]">Шаг {step + 1} из 3</span>
              <motion.button
                type="button"
                whileTap={{ scale: 0.95 }}
                disabled={busy}
                onClick={onClose}
                data-testid="welcome-close-btn"
                className="px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[11px] font-medium text-[#E6EBE8] flex items-center gap-1 transition-colors"
              >
                <X className="w-3.5 h-3.5 text-[#98A49E]" />
                <span>Закрыть</span>
              </motion.button>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-1.5">
            {[0, 1, 2].map((idx) => {
              const active = idx <= step;
              return (
                <motion.div
                  key={idx}
                  initial={false}
                  animate={{
                    backgroundColor: active ? '#6DB386' : '#252E2A',
                    boxShadow: active
                      ? '0 0 10px rgba(109, 179, 134, 0.36)'
                      : '0 0 0px rgba(109, 179, 134, 0)',
                  }}
                  transition={{ duration: 0.26, ease: EASE_OUT_TACTICAL }}
                  className="h-1 rounded-full"
                />
              );
            })}
          </div>
        </motion.div>

        <AnimatePresence>
          {errorMessage && (
            <motion.div
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.16 }}
              role="alert"
              className="px-3 py-2.5 rounded-xl bg-[#381B19] border border-[#C7564E]/60 text-xs text-[#F1ACA7] shadow-[0_0_12px_rgba(199,86,78,0.2)]"
            >
              {errorMessage}
            </motion.div>
          )}
        </AnimatePresence>

        {/* Step Content */}
        <AnimatePresence mode="wait" custom={direction}>
          {step === 0 && (
            <motion.div
              key="welcome-step-0"
              custom={direction}
              variants={STEP_VARIANTS}
              initial="initial"
              animate="animate"
              exit="exit"
              className="space-y-3"
            >
              <motion.section
                {...tileEnterMotion(0, 12)}
                className="bg-[#131816] border border-[#5B9D72]/45 rounded-2xl p-4 sm:p-5 space-y-3.5 shadow-[0_0_18px_rgba(91,157,114,0.16)]"
              >
                <div className="flex items-center gap-3">
                  <motion.div
                    initial={{ scale: 0.82, opacity: 0 }}
                    animate={{
                      scale: 1,
                      opacity: 1,
                      boxShadow: [
                        '0 0 12px rgba(109, 179, 134, 0.22)',
                        '0 0 22px rgba(109, 179, 134, 0.45)',
                        '0 0 12px rgba(109, 179, 134, 0.22)',
                      ],
                    }}
                    transition={{
                      scale: { type: 'spring', stiffness: 260, damping: 18 },
                      opacity: { duration: 0.22 },
                      boxShadow: { duration: 2.2, repeat: Infinity, ease: 'easeInOut' },
                    }}
                    className="w-11 h-11 sm:w-12 sm:h-12 shrink-0 rounded-xl bg-[#203328] border border-[#5B9D72] flex items-center justify-center"
                  >
                    <Compass className="w-6 h-6 text-[#6DB386]" />
                  </motion.div>
                  <div className="min-w-0">
                    <h1 className="text-lg sm:text-xl font-bold text-[#E6EBE8] leading-snug">
                      Добро пожаловать в Urbex-T!
                    </h1>
                    <p className="text-xs text-[#98A49E] leading-relaxed">
                      Автономный тактический КПК для карт, реестра объектов, полевого архива и связи.
                    </p>
                  </div>
                </div>

                {/* Equal-sized feature tiles */}
                <div
                  data-testid="welcome-tiles-grid"
                  className="grid grid-cols-2 sm:grid-cols-4 auto-rows-fr gap-2"
                >
                  {HIGHLIGHTS.map((item, idx) => {
                    const Icon = item.icon;
                    return (
                      <motion.div
                        key={item.title}
                        {...tileEnterMotion(idx + 1, 10)}
                        data-testid={`welcome-tile-${idx}`}
                        className="h-full min-h-[90px] p-2.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] flex flex-col gap-1"
                      >
                        <Icon className="w-4 h-4 text-[#6DB386] shrink-0" />
                        <div className="text-xs font-semibold text-[#E6EBE8] truncate">
                          {item.title}
                        </div>
                        <div className="text-[11px] leading-[1.35] text-[#98A49E] line-clamp-2">
                          {item.subtitle}
                        </div>
                      </motion.div>
                    );
                  })}
                </div>

                <p className="text-xs text-[#98A49E] leading-relaxed">
                  {isReopenedFromSettings
                    ? 'Вы можете заново настроить позывной, формат координат, режим экрана или подключить ещё одну базу данных.'
                    : 'Перед началом давайте за пару шагов настроим ваш позывной, формат экрана и подключим базу данных.'}
                </p>
              </motion.section>

              <motion.button
                {...tileEnterMotion(5, 10)}
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.97 }}
                type="button"
                disabled={busy}
                onClick={() => goToStep(1)}
                data-testid="welcome-start-btn"
                className="w-full min-h-[46px] px-4 rounded-xl bg-[#5B9D72] hover:bg-[#6DB386] text-[#0C0F0E] text-sm font-semibold flex items-center justify-center gap-2 shadow-[0_0_16px_rgba(109,179,134,0.34)] transition-colors"
              >
                <span>Начать настройку</span>
                <ArrowRight className="w-4 h-4" />
              </motion.button>

              <motion.button
                {...tileEnterMotion(6, 10)}
                whileTap={{ scale: 0.98 }}
                type="button"
                disabled={busy}
                onClick={() => fileInputRef.current?.click()}
                className="w-full min-h-[42px] px-4 rounded-xl bg-[#131816] border border-[#323E39] hover:border-[#D39239] text-[13px] font-medium text-[#E6EBE8] flex items-center justify-center gap-2 transition-colors"
              >
                <FolderOpen className="w-4 h-4 text-[#DF9E43]" />
                <span>У меня уже есть файл .urbexdb</span>
              </motion.button>

              <motion.button
                {...tileEnterMotion(7, 8)}
                whileTap={{ scale: 0.98 }}
                type="button"
                disabled={busy}
                onClick={onClose}
                data-testid="welcome-skip-btn"
                className="w-full min-h-[40px] px-4 rounded-xl bg-transparent border border-[#252E2A] hover:border-[#5B9D72] text-xs font-medium text-[#98A49E] hover:text-[#E6EBE8] transition-colors"
              >
                {isReopenedFromSettings ? 'Вернуться в терминал' : 'Пропустить и перейти в терминал'}
              </motion.button>
            </motion.div>
          )}

          {step === 1 && (
            <motion.div
              key="welcome-step-1"
              custom={direction}
              variants={STEP_VARIANTS}
              initial="initial"
              animate="animate"
              exit="exit"
              className="space-y-3"
            >
              <section className="bg-[#131816] border border-[#252E2A] rounded-2xl p-4 sm:p-5 space-y-4">
                <div className="flex items-start gap-2.5">
                  <Sliders className="w-4 h-4 sm:w-5 sm:h-5 text-[#6DB386] mt-0.5 shrink-0" />
                  <div>
                    <h2 className="text-base sm:text-lg font-bold text-[#E6EBE8]">
                      Давайте настроим терминал
                    </h2>
                    <p className="text-[11px] text-[#98A49E]">
                      Задайте позывной и базовые параметры — потом их можно изменить в настройках.
                    </p>
                  </div>
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-[#98A49E] mb-1.5">
                    Позывной оператора
                  </label>
                  <input
                    type="text"
                    value={callsign}
                    onChange={(e) => setCallsign(e.target.value)}
                    placeholder="Например: Сокол-1"
                    className="w-full min-h-[40px] px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] focus:border-[#5B9D72] focus:outline-none text-sm text-[#E6EBE8] placeholder:text-[#6B7771]"
                  />
                </div>

                <button
                  type="button"
                  onClick={() => setEnableEditor((prev) => !prev)}
                  className={`w-full rounded-xl p-3 bg-[#0C0F0E] border text-left flex items-center justify-between gap-3 transition-colors ${
                    enableEditor
                      ? 'border-[#5B9D72]/60 shadow-[0_0_12px_rgba(109,179,134,0.16)]'
                      : 'border-[#252E2A]'
                  }`}
                >
                  <div className="min-w-0">
                    <div className="text-[13px] font-medium text-[#E6EBE8]">
                      Включить редактор объектов
                    </div>
                    <div className="text-[11px] text-[#98A49E]">
                      Позволяет сразу создавать точки, зоны и записи в архиве
                    </div>
                  </div>
                  <span
                    className={`w-10 h-6 shrink-0 rounded-full p-0.5 transition-colors flex items-center ${
                      enableEditor ? 'bg-[#5B9D72] justify-end' : 'bg-[#252E2A] justify-start'
                    }`}
                  >
                    <span className="w-5 h-5 rounded-full bg-[#E6EBE8]" />
                  </span>
                </button>

                <div className="space-y-1.5">
                  <div className="text-[11px] font-semibold text-[#98A49E]">Формат координат</div>
                  <div className="grid grid-cols-3 gap-1.5">
                    {COORD_OPTIONS.map((opt) => {
                      const selected = coordFormat === opt.value;
                      return (
                        <button
                          key={opt.value}
                          type="button"
                          onClick={() => setCoordFormat(opt.value)}
                          className={`min-h-[38px] px-2 py-1.5 rounded-lg border text-[11px] flex items-center justify-center gap-1 transition-colors ${
                            selected
                              ? 'bg-[#1A201E] border-[#6DB386] text-[#E6EBE8] font-semibold shadow-[0_0_10px_rgba(109,179,134,0.2)]'
                              : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                          }`}
                        >
                          {selected && <Check className="w-3 h-3 text-[#6DB386] shrink-0" />}
                          <span className="truncate">{opt.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="text-[11px] font-semibold text-[#98A49E]">
                    Ориентация экрана PDA
                  </div>
                  <div className="grid grid-cols-3 gap-1.5">
                    {ORIENTATION_OPTIONS.map((orient) => {
                      const selected = orientation === orient;
                      return (
                        <button
                          key={orient}
                          type="button"
                          onClick={() => setOrientation(orient)}
                          className={`min-h-[38px] px-2 py-1.5 rounded-lg border text-[11px] flex items-center justify-center transition-colors ${
                            selected
                              ? 'bg-[#1A201E] border-[#6DB386] text-[#E6EBE8] font-semibold shadow-[0_0_10px_rgba(109,179,134,0.2)]'
                              : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                          }`}
                        >
                          <span className="truncate">{PDA_ORIENTATION_LABELS[orient]}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </section>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => goToStep(0)}
                  className="min-h-[44px] px-3.5 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-xs font-medium text-[#E6EBE8] flex items-center gap-1.5 transition-colors"
                >
                  <ArrowLeft className="w-4 h-4 text-[#98A49E]" />
                  <span>Назад</span>
                </button>
                <button
                  type="button"
                  onClick={() => goToStep(2)}
                  data-testid="welcome-next-db-btn"
                  className="flex-1 min-h-[44px] px-4 rounded-xl bg-[#5B9D72] hover:bg-[#6DB386] text-[#0C0F0E] text-[13px] font-semibold flex items-center justify-center gap-1.5 shadow-[0_0_14px_rgba(109,179,134,0.3)] transition-colors"
                >
                  <span>Далее: база данных</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          )}

          {step === 2 && (
            <motion.div
              key="welcome-step-2"
              custom={direction}
              variants={STEP_VARIANTS}
              initial="initial"
              animate="animate"
              exit="exit"
              className="space-y-3"
            >
              <section className="bg-[#131816] border border-[#252E2A] rounded-2xl p-4 space-y-3">
                <div className="flex items-start gap-2.5">
                  <Database className="w-4 h-4 sm:w-5 sm:h-5 text-[#6DB386] mt-0.5 shrink-0" />
                  <div>
                    <h2 className="text-base sm:text-lg font-bold text-[#E6EBE8]">
                      Подключим базу данных
                    </h2>
                    <p className="text-[11px] text-[#98A49E]">
                      Создайте пустую базу для своего сектора или откройте готовый файл .urbexdb.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  <div>
                    <label className="block text-[11px] font-semibold text-[#98A49E] mb-1">
                      Название базы
                    </label>
                    <input
                      type="text"
                      value={dbName}
                      onChange={(e) => {
                        setDbName(e.target.value);
                        setErrorMessage(null);
                      }}
                      placeholder="Например: Москва · Сектор-1"
                      className="w-full min-h-[38px] px-3 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] focus:border-[#5B9D72] focus:outline-none text-sm text-[#E6EBE8] placeholder:text-[#6B7771]"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-semibold text-[#98A49E] mb-1">
                      Регион / город (необязательно)
                    </label>
                    <input
                      type="text"
                      value={dbRegion}
                      onChange={(e) => setDbRegion(e.target.value)}
                      placeholder="Например: Центральный округ"
                      className="w-full min-h-[38px] px-3 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] focus:border-[#5B9D72] focus:outline-none text-sm text-[#E6EBE8] placeholder:text-[#6B7771]"
                    />
                  </div>
                </div>

                <button
                  type="button"
                  disabled={busy}
                  onClick={handleCreateEmptyDb}
                  className="w-full min-h-[44px] px-4 rounded-xl bg-[#5B9D72] hover:bg-[#6DB386] text-[#0C0F0E] text-[13px] font-semibold flex items-center justify-center gap-2 shadow-[0_0_14px_rgba(109,179,134,0.3)] transition-colors"
                >
                  <span>Создать базу и начать работу</span>
                </button>
              </section>

              <section className="bg-[#131816] border border-[#252E2A] rounded-2xl p-4 space-y-2">
                <div className="text-xs font-semibold text-[#98A49E]">
                  Или выберите готовый вариант
                </div>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => fileInputRef.current?.click()}
                  className="w-full min-h-[42px] px-3.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-[13px] font-medium text-[#E6EBE8] flex items-center justify-center gap-2 transition-colors"
                >
                  <FolderOpen className="w-4 h-4 text-[#DF9E43]" />
                  <span>Выбрать файл базы (.urbexdb)</span>
                </button>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => setShowTemplateModal(true)}
                    className="min-h-[40px] px-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <Code2 className="w-4 h-4 text-[#DF9E43] shrink-0" />
                    <span className="truncate">Схема и шаблон</span>
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={handleLoadDemoDb}
                    className="min-h-[40px] px-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] hover:border-[#6DB386] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <Layers className="w-4 h-4 text-[#6DB386] shrink-0" />
                    <span className="truncate">Демо-база</span>
                  </button>
                </div>
              </section>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => goToStep(1)}
                  className="min-h-[40px] px-3.5 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-xs font-medium text-[#E6EBE8] flex items-center gap-1.5 transition-colors"
                >
                  <ArrowLeft className="w-3.5 h-3.5 text-[#98A49E]" />
                  <span>Назад к настройкам</span>
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={handleFinishWithCurrentPrefs}
                  data-testid="welcome-finish-btn"
                  className="flex-1 min-h-[40px] px-3.5 rounded-xl bg-[#5B9D72] hover:bg-[#6DB386] text-[#0C0F0E] text-xs font-semibold flex items-center justify-center gap-1.5 shadow-[0_0_12px_rgba(109,179,134,0.28)] transition-colors"
                >
                  <Check className="w-3.5 h-3.5" />
                  <span>
                    {isReopenedFromSettings ? 'Сохранить и вернуться' : 'Сохранить и перейти в терминал'}
                  </span>
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <DatabaseTemplateModal
        open={showTemplateModal}
        onClose={() => setShowTemplateModal(false)}
        onCreateFromTemplate={async (templateBundle) => {
          setBusy(true);
          try {
            await applyOperatorPreferences();
            await onImportIntoNewDatabase('База по шаблону', 'Локальный сектор', templateBundle);
            onClose();
          } finally {
            setBusy(false);
          }
        }}
      />

      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {pendingImport && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.14 }}
                className="fixed inset-0 z-[80] bg-black/80 backdrop-blur-[2px] flex items-center justify-center p-4"
                onClick={() => setPendingImport(null)}
              >
                <motion.div
                  initial={{ opacity: 0, scale: 0.96, y: 8 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.96, y: 6 }}
                  transition={{ duration: 0.18, ease: EASE_OUT_TACTICAL }}
                  onClick={(e) => e.stopPropagation()}
                  className="w-full max-w-md bg-[#131816] border border-[#252E2A] rounded-2xl p-4 sm:p-5 space-y-3 text-[#E6EBE8] shadow-2xl"
                >
                  <h3 className="text-base font-semibold">Подключить базу (.urbexdb)</h3>
                  <p className="text-xs text-[#98A49E]">
                    Объектов: {pendingImport.payload.objects.length} · База знаний:{' '}
                    {pendingImport.payload.baseArticles.length} · Архив:{' '}
                    {pendingImport.payload.archiveItems.length}
                  </p>
                  {importWarnings.length > 0 && (
                    <div className="p-2.5 rounded-lg bg-[#0C0F0E] border border-[#D39239]/50 space-y-1">
                      {importWarnings.slice(0, 3).map((warn, i) => (
                        <div key={i} className="text-[11px] text-[#DF9E43]">
                          {warn}
                        </div>
                      ))}
                    </div>
                  )}
                  <div>
                    <label className="block text-[11px] font-semibold text-[#98A49E] mb-1">
                      Название базы
                    </label>
                    <input
                      type="text"
                      value={importDbName}
                      onChange={(e) => setImportDbName(e.target.value)}
                      placeholder="Например: Тюмень · Сектор А"
                      className="w-full min-h-[38px] px-3 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] focus:border-[#5B9D72] focus:outline-none text-sm text-[#E6EBE8]"
                    />
                  </div>
                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setPendingImport(null)}
                      className="min-h-[36px] px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#98A49E] hover:text-[#E6EBE8]"
                    >
                      Отмена
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={handleConfirmImport}
                      className="min-h-[36px] px-3.5 rounded-lg bg-[#5B9D72] hover:bg-[#6DB386] text-[#0C0F0E] text-xs font-semibold"
                    >
                      Открыть базу
                    </button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}
    </motion.div>
  );
}
