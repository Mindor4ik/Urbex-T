import { useEffect, useState } from 'react';
import type { ObjectIconId } from '../core/types';
import { ObjectGlyph } from './ObjectGlyph';

interface ObjectImageProps {
  src?: string;
  alt: string;
  className?: string;
  compact?: boolean;
  fallbackIcon?: ObjectIconId;
}

export function ObjectImage({
  src,
  alt,
  className = '',
  compact = false,
  fallbackIcon = 'building',
}: ObjectImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) {
    return (
      <div
        className={`flex flex-col items-center justify-center bg-[#141917] text-[#6B7771] border border-[#252E2A] ${className}`}
        role="img"
        aria-label={alt}
      >
        <ObjectGlyph
          icon={fallbackIcon}
          className={compact ? 'w-6 h-6 text-[#5B9D72]/75' : 'w-7 h-7 mb-1.5 text-[#5B9D72]/75'}
          strokeWidth={1.6}
        />
        {!compact && (
          <span className="text-xs px-3 text-center truncate max-w-full">{alt}</span>
        )}
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      loading="lazy"
      decoding="async"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={`object-cover bg-[#141917] ${className}`}
    />
  );
}
