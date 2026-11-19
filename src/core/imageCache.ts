export interface EmbeddedImageResult {
  dataUrl: string;
  sizeBytes: number;
  embedded: boolean;
  warning?: string;
}

const MAX_IMAGE_DIM = 1280;
const JPEG_QUALITY = 0.84;

export function estimateDataUrlBytes(uri?: string): number {
  if (!uri) return 0;
  if (uri.startsWith('data:')) {
    const commaIdx = uri.indexOf(',');
    if (commaIdx < 0) return uri.length;
    const base64Part = uri.slice(commaIdx + 1);
    return Math.round((base64Part.length * 3) / 4);
  }
  return 0;
}

export function isEmbeddedDataUrl(uri?: string): boolean {
  return Boolean(uri && uri.startsWith('data:image/'));
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result);
      } else {
        reject(new Error('Не удалось прочитать изображение.'));
      }
    };
    reader.onerror = () => reject(new Error('Ошибка чтения бинарных данных.'));
    reader.readAsDataURL(blob);
  });
}

function compressImageElementToJpegDataUrl(img: HTMLImageElement): string {
  const width = img.naturalWidth || img.width || 1;
  const height = img.naturalHeight || img.height || 1;
  const scale = Math.min(1, MAX_IMAGE_DIM / Math.max(width, height));
  const targetW = Math.max(1, Math.round(width * scale));
  const targetH = Math.max(1, Math.round(height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = targetW;
  canvas.height = targetH;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('Canvas 2D недоступен');
  }
  ctx.drawImage(img, 0, 0, targetW, targetH);
  return canvas.toDataURL('image/jpeg', JPEG_QUALITY);
}

async function compressBlobToDataUrl(blob: Blob): Promise<string> {
  const rawDataUrl = await blobToDataUrl(blob);
  if (typeof document === 'undefined' || blob.type === 'image/svg+xml') {
    return rawDataUrl;
  }

  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        const compressed = compressImageElementToJpegDataUrl(img);
        resolve(compressed.length < rawDataUrl.length || rawDataUrl.length > 260_000 ? compressed : rawDataUrl);
      } catch {
        resolve(rawDataUrl);
      }
    };
    img.onerror = () => resolve(rawDataUrl);
    img.src = rawDataUrl;
  });
}

export async function optimizeImageFileToDataUrl(file: File): Promise<EmbeddedImageResult> {
  const dataUrl = await compressBlobToDataUrl(file);
  return {
    dataUrl,
    sizeBytes: estimateDataUrlBytes(dataUrl),
    embedded: true,
  };
}

function loadCorsImageToDataUrl(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.referrerPolicy = 'no-referrer';
    img.onload = () => {
      try {
        resolve(compressImageElementToJpegDataUrl(img));
      } catch (err) {
        reject(err);
      }
    };
    img.onerror = () => reject(new Error('CORS or network load failed'));
    img.src = url;
  });
}

/**
 * Downloads an image from a URL and embeds it offline as a compressed JPEG data: URL inside the DB.
 * If the remote server blocks browser CORS, preserves the external URL cleanly with a clear notice.
 */
export async function downloadImageUrlToDataUrl(rawUrl: string): Promise<EmbeddedImageResult> {
  const trimmed = rawUrl.trim();
  if (!trimmed) {
    throw new Error('Укажите ссылку на изображение.');
  }

  if (trimmed.startsWith('data:image/')) {
    return {
      dataUrl: trimmed,
      sizeBytes: estimateDataUrlBytes(trimmed),
      embedded: true,
    };
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error('Некорректный URL (ожидается https://... или http://...).');
  }

  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new Error('Поддерживаются только ссылки http:// и https://.');
  }

  // Attempt 1: direct CORS fetch -> Blob -> compressed JPEG Data URL
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 12_000);
    const response = await fetch(parsed.toString(), {
      mode: 'cors',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      signal: controller.signal,
    });
    clearTimeout(timer);

    if (response.ok) {
      const blob = await response.blob();
      if (blob.type && !blob.type.startsWith('image/')) {
        throw new Error(`Ссылка вернула не изображение (${blob.type}).`);
      }
      if (blob.size > 12 * 1024 * 1024) {
        throw new Error('Файл по ссылке слишком большой (>12 МБ).');
      }
      const dataUrl = await compressBlobToDataUrl(blob);
      return {
        dataUrl,
        sizeBytes: estimateDataUrlBytes(dataUrl),
        embedded: true,
      };
    }
  } catch (err) {
    if (err instanceof Error && (err.message.includes('не изображение') || err.message.includes('слишком большой'))) {
      throw err;
    }
  }

  // Attempt 2: Image with crossOrigin="anonymous" -> canvas JPEG
  if (typeof document !== 'undefined') {
    try {
      const dataUrl = await loadCorsImageToDataUrl(parsed.toString());
      return {
        dataUrl,
        sizeBytes: estimateDataUrlBytes(dataUrl),
        embedded: true,
      };
    } catch {
      // Fallback below when remote host blocks CORS
    }
  }

  // Fallback: verify if browser can display it without CORS; keep external link in DB with honest notice
  return {
    dataUrl: parsed.toString(),
    sizeBytes: 0,
    embedded: false,
    warning:
      'Удалённый сервер запрещает CORS-чтение в браузере — сохранена внешняя ссылка (в Android APK фото скачивается напрямую в память базы).',
  };
}
