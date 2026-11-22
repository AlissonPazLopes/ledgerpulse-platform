interface RiskScoreGaugeProps {
  score?: number | null;
  compact?: boolean;
}

interface RiskBand {
  label: string;
  barClass: string;
  badgeClass: string;
  textClass: string;
  strokeClass: string;
}

function getRiskBand(score: number): RiskBand {
  if (score < 45) {
    return {
      label: 'Baixo',
      barClass: 'bg-emerald-500',
      badgeClass: 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300',
      textClass: 'text-emerald-300',
      strokeClass: 'stroke-emerald-400',
    };
  }

  if (score < 75) {
    return {
      label: 'Moderado',
      barClass: 'bg-amber-500',
      badgeClass: 'border-amber-500/35 bg-amber-500/15 text-amber-300',
      textClass: 'text-amber-300',
      strokeClass: 'stroke-amber-400',
    };
  }

  return {
    label: 'Alto',
    barClass: 'bg-rose-500',
    badgeClass: 'border-rose-500/35 bg-rose-500/15 text-rose-300',
    textClass: 'text-rose-300',
    strokeClass: 'stroke-rose-400',
  };
}

export function RiskScoreGauge({ score, compact = false }: RiskScoreGaugeProps) {
  if (score === null || score === undefined || !Number.isFinite(score)) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-amber-500/30 bg-amber-500/5 px-3 py-2">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-dashed border-amber-400/50 font-mono text-[11px] font-bold text-amber-300">
          N/D
        </div>
        <div className="min-w-0">
          <p className="text-xs font-semibold text-amber-200">Score indisponível</p>
          <p className="text-[11px] text-amber-300/80">Encaminhado via fallback</p>
        </div>
      </div>
    );
  }

  const normalized = Math.max(0, Math.min(100, Math.round(score)));
  const band = getRiskBand(normalized);
  const radius = 16;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference - (normalized / 100) * circumference;

  return (
    <div
      role="meter"
      aria-label="Risk score"
      aria-valuenow={normalized}
      aria-valuemin={0}
      aria-valuemax={100}
      className={[
        'flex items-center gap-3 rounded-xl border border-slate-800 bg-slate-950/80',
        compact ? 'px-3 py-2' : 'px-3.5 py-2.5',
      ].join(' ')}
    >
      <div className="relative flex h-11 w-11 shrink-0 items-center justify-center">
        <svg className="h-11 w-11 -rotate-90" viewBox="0 0 40 40" aria-hidden="true">
          <circle
            cx="20"
            cy="20"
            r={radius}
            fill="none"
            strokeWidth="4"
            className="stroke-slate-800"
          />
          <circle
            cx="20"
            cy="20"
            r={radius}
            fill="none"
            strokeWidth="4"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={dashOffset}
            className={band.strokeClass}
          />
        </svg>
        <span className={`absolute font-mono text-xs font-bold ${band.textClass}`}>
          {normalized}
        </span>
      </div>

      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex items-center justify-between gap-2">
          <span className="font-mono text-xs font-semibold text-slate-200">
            {normalized} / 100
          </span>
          <span
            className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[10px] font-semibold uppercase ${band.badgeClass}`}
          >
            {band.label}
          </span>
        </div>

        <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
          <div
            className={`h-full rounded-full transition-all ${band.barClass}`}
            style={{ width: `${normalized}%` }}
          />
        </div>
      </div>
    </div>
  );
}
