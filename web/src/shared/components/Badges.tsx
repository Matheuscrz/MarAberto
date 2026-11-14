import type { FlagColor, RiskLevel } from "@/shared/types/flags";

const flagStyles: Record<FlagColor, { label: string; className: string; dot: string }> = {
  GREEN: {
    label: 'Bandeira verde',
    className: 'border-emerald-300 bg-emerald-50 text-emerald-950',
    dot: 'bg-emerald-500',
  },
  YELLOW: {
    label: 'Bandeira amarela',
    className: 'border-amber-300 bg-amber-50 text-amber-950',
    dot: 'bg-amber-500',
  },
  RED: {
    label: 'Bandeira vermelha',
    className: 'border-red-300 bg-red-50 text-red-950',
    dot: 'bg-red-500',
  },
  PURPLE: {
    label: 'Bandeira roxa (animais marinhos)',
    className: 'border-violet-300 bg-violet-50 text-violet-950',
    dot: 'bg-violet-500',
  },
  BLACK: {
    label: 'Bandeira preta',
    className: 'border-slate-700 bg-slate-900 text-white',
    dot: 'bg-white',
  },
};

export function FlagBadge({ flag }: { flag: FlagColor }) {
  const style = flagStyles[flag];
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-bold ${style.className}`}
    >
      <span aria-hidden="true" className={`size-2.5 rounded-full ${style.dot}`} />
      {style.label}
    </span>
  );
}

const riskStyles: Record<RiskLevel, { label: string; className: string }> = {
  LOW: { label: 'Risco baixo', className: 'bg-emerald-100 text-emerald-950' },
  MODERATE: { label: 'Risco moderado', className: 'bg-amber-100 text-amber-950' },
  HIGH: { label: 'Risco alto', className: 'bg-orange-100 text-orange-950' },
  EXTREME: { label: 'Risco extremo', className: 'bg-red-100 text-red-950' },
};

export function RiskBadge({ level }: { level: RiskLevel }) {
  const style = riskStyles[level];
  return (
    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${style.className}`}>
      {style.label}
    </span>
  );
}
