import type { ZonePattern, ZoneStyle } from '../core/types';

interface ZoneLinePreviewProps {
  style: ZoneStyle;
  color: string;
}

export function ZoneLinePreview({ style, color }: ZoneLinePreviewProps) {
  if (style === 'double_line') {
    return (
      <svg width="24" height="10" viewBox="0 0 24 10" className="shrink-0" aria-hidden="true">
        <line x1="1" y1="3" x2="23" y2="3" stroke={color} strokeWidth="1.6" />
        <line x1="1" y1="7" x2="23" y2="7" stroke={color} strokeWidth="1.6" />
      </svg>
    );
  }
  if (style === 'glow') {
    return (
      <svg width="24" height="10" viewBox="0 0 24 10" className="shrink-0" aria-hidden="true">
        <line x1="1" y1="5" x2="23" y2="5" stroke={color} strokeOpacity="0.36" strokeWidth="5.5" strokeLinecap="round" />
        <line x1="1" y1="5" x2="23" y2="5" stroke={color} strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    );
  }
  if (style === 'outline' || style === 'dashed_outline') {
    const dashArray = style === 'dashed_outline' ? '4.5 2.8' : undefined;
    return (
      <svg width="24" height="10" viewBox="0 0 24 10" className="shrink-0" aria-hidden="true">
        <rect
          x="2"
          y="2"
          width="20"
          height="6"
          rx="1"
          fill="none"
          stroke={color}
          strokeWidth="1.4"
          strokeDasharray={dashArray}
        />
      </svg>
    );
  }

  const dashArray =
    style === 'dashed'
      ? '5 3'
      : style === 'dotted'
        ? '1.6 3'
        : style === 'dash_dot'
          ? '6.5 2.5 1.5 2.5'
          : style === 'long_dash'
            ? '9 3.5'
            : undefined;
  const strokeWidth = style === 'bold' ? 3 : style === 'dotted' ? 2.2 : 1.8;

  return (
    <svg width="24" height="10" viewBox="0 0 24 10" className="shrink-0" aria-hidden="true">
      <line
        x1="1"
        y1="5"
        x2="23"
        y2="5"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeDasharray={dashArray}
        strokeLinecap={style === 'dotted' ? 'round' : 'butt'}
      />
    </svg>
  );
}

interface ZonePatternPreviewProps {
  pattern: ZonePattern;
  color: string;
}

export function ZonePatternPreview({ pattern, color }: ZonePatternPreviewProps) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      className="shrink-0 rounded-[3px] border border-white/15 bg-[#0A0D0C]"
      aria-hidden="true"
    >
      <rect x="0" y="0" width="16" height="16" fill={color} fillOpacity={pattern === 'none' ? 0.34 : 0.16} />
      {pattern === 'hatch' && (
        <g stroke={color} strokeWidth="1.4" strokeOpacity="0.9">
          <line x1="-2" y1="8" x2="8" y2="-2" />
          <line x1="0" y1="16" x2="16" y2="0" />
          <line x1="8" y1="18" x2="18" y2="8" />
        </g>
      )}
      {pattern === 'cross_hatch' && (
        <g stroke={color} strokeWidth="1.2" strokeOpacity="0.9">
          <line x1="-2" y1="8" x2="8" y2="-2" />
          <line x1="0" y1="16" x2="16" y2="0" />
          <line x1="8" y1="18" x2="18" y2="8" />
          <line x1="-2" y1="8" x2="8" y2="18" />
          <line x1="0" y1="0" x2="16" y2="16" />
          <line x1="8" y1="-2" x2="18" y2="8" />
        </g>
      )}
      {pattern === 'grid' && (
        <g stroke={color} strokeWidth="1.15" strokeOpacity="0.88">
          <line x1="5.5" y1="0" x2="5.5" y2="16" />
          <line x1="11.5" y1="0" x2="11.5" y2="16" />
          <line x1="0" y1="5.5" x2="16" y2="5.5" />
          <line x1="0" y1="11.5" x2="16" y2="11.5" />
        </g>
      )}
      {pattern === 'dots' && (
        <g fill={color} fillOpacity="0.92">
          <circle cx="4" cy="4" r="1.4" />
          <circle cx="12" cy="4" r="1.4" />
          <circle cx="8" cy="8" r="1.4" />
          <circle cx="4" cy="12" r="1.4" />
          <circle cx="12" cy="12" r="1.4" />
        </g>
      )}
      {pattern === 'vertical' && (
        <g stroke={color} strokeWidth="1.4" strokeOpacity="0.9">
          <line x1="3.5" y1="0" x2="3.5" y2="16" />
          <line x1="8" y1="0" x2="8" y2="16" />
          <line x1="12.5" y1="0" x2="12.5" y2="16" />
        </g>
      )}
      {pattern === 'horizontal' && (
        <g stroke={color} strokeWidth="1.4" strokeOpacity="0.9">
          <line x1="0" y1="3.5" x2="16" y2="3.5" />
          <line x1="0" y1="8" x2="16" y2="8" />
          <line x1="0" y1="12.5" x2="16" y2="12.5" />
        </g>
      )}
      {pattern === 'hazard_stripes' && (
        <g stroke={color} strokeWidth="3.2" strokeOpacity="0.85">
          <line x1="-2" y1="10" x2="10" y2="-2" />
          <line x1="4" y1="18" x2="18" y2="4" />
        </g>
      )}
    </svg>
  );
}
