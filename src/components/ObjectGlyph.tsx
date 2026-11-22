import {
  AlertTriangle,
  Building2,
  Camera,
  Construction,
  Droplets,
  Eye,
  Factory,
  Flame,
  Fuel,
  Hospital,
  Lock,
  MapPin,
  Mountain,
  Plane,
  Radar,
  Radiation,
  RadioTower,
  Shield,
  Ship,
  Skull,
  TrainFront,
  Warehouse,
  Wrench,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import type { ObjectIconId } from '../core/types';

export const OBJECT_ICON_COMPONENTS: Record<ObjectIconId, LucideIcon> = {
  factory: Factory,
  bunker: Shield,
  building: Building2,
  tower: RadioTower,
  tunnel: TrainFront,
  warehouse: Warehouse,
  power: Zap,
  boiler: Flame,
  water: Droplets,
  fuel: Fuel,
  radar: Radar,
  radiation: Radiation,
  hazard: AlertTriangle,
  skull: Skull,
  observation: Eye,
  gate: Lock,
  crane: Construction,
  hospital: Hospital,
  airfield: Plane,
  port: Ship,
  shaft: Mountain,
  workshop: Wrench,
  camera: Camera,
  pin: MapPin,
};

interface ObjectGlyphProps {
  icon: ObjectIconId;
  className?: string;
  color?: string;
  strokeWidth?: number;
}

export function ObjectGlyph({
  icon,
  className = 'w-4 h-4',
  color,
  strokeWidth = 1.9,
}: ObjectGlyphProps) {
  const Icon = OBJECT_ICON_COMPONENTS[icon] ?? MapPin;
  return (
    <Icon
      className={className}
      style={color ? { color } : undefined}
      strokeWidth={strokeWidth}
      aria-hidden="true"
    />
  );
}
