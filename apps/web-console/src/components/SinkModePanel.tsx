import { CheckCircle2, Clock, Radio, RefreshCw, XCircle } from 'lucide-react';
import { formatDateTimeParts } from '../lib/formatters';
import type { ProblemDetail, SinkMode, SinkModeResponse } from '../types/api';
import { ProblemDetailAlert } from './ProblemDetailAlert';

interface SinkModePanelProps {
  sinkMode: SinkModeResponse | null;
  isLoading: boolean;
  isUpdating: boolean;
  canMutate: boolean;
  problem: ProblemDetail | null;
  onChangeMode: (mode: SinkMode) => void;
  onRefresh: () => void;
}

const SINK_MODE_OPTIONS: Array<{
  mode: SinkMode;
  title: string;
  description: string;
  icon: typeof CheckCircle2;
  activeClass: string;
  iconClass: string;
}> = [
  {
    mode: 'OK_200',
    title: 'OK_200',
    description: 'Responde 200 OK imediatamente validando entrega HMAC.',
    icon: CheckCircle2,
    activeClass: 'border-emerald-500/60 bg-emerald-500/15 text-emerald-100',
    iconClass: 'text-emerald-400',
  },
  {
    mode: 'FAIL_500',
    title: 'FAIL_500',
    description: 'Simula HTTP 500 para acionar retries (5s→30s→120s) e DLQ.',
    icon: XCircle,
    activeClass: 'border-rose-500/60 bg-rose-500/15 text-rose-100',
    iconClass: 'text-rose-400',
  },
  {
    mode: 'TIMEOUT',
    title: 'TIMEOUT',
    description: 'Atrasa resposta além do timeout do dispatcher (HTTP 504).',
    icon: Clock,
    activeClass: 'border-amber-500/60 bg-amber-500/15 text-amber-100',
    iconClass: 'text-amber-400',
  },
];

export function SinkModePanel({
  sinkMode,
  isLoading,
  isUpdating,
  canMutate,
  problem,
  onChangeMode,
  onRefresh,
}: SinkModePanelProps) {
  return (
    <section className="flex flex-col justify-between rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Radio className="h-4 w-4 text-emerald-400" />
            <h3 className="text-sm font-semibold text-white">
              Simulador Merchant Webhook Sink
            </h3>
          </div>

          <button
            type="button"
            onClick={onRefresh}
            disabled={isLoading || isUpdating}
            className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1 text-[11px] font-medium text-slate-300 hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-50"
          >
            <RefreshCw className={`h-3 w-3 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Atualizar</span>
          </button>
        </div>

        <p className="mt-1 text-xs text-slate-400">
          Controle o comportamento do endpoint{' '}
          <code className="text-emerald-300">/api/v1/sink/test</code> para demonstrar falhas,
          backoff e replay da DLQ.
        </p>

        {problem && (
          <div className="mt-3">
            <ProblemDetailAlert problem={problem} onRetry={onRefresh} />
          </div>
        )}

        <div className="mt-4 grid gap-2.5">
          {SINK_MODE_OPTIONS.map((option) => {
            const Icon = option.icon;
            const isSelected = sinkMode?.mode === option.mode;

            return (
              <button
                key={option.mode}
                type="button"
                disabled={!canMutate || isUpdating || isLoading}
                onClick={() => onChangeMode(option.mode)}
                aria-pressed={isSelected}
                title={
                  canMutate
                    ? `Alternar Webhook Sink para ${option.mode}`
                    : 'Alteração restrita a ADMIN e ANALYST'
                }
                className={[
                  'flex items-start gap-3 rounded-xl border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-60',
                  isSelected
                    ? option.activeClass
                    : 'border-slate-800 bg-slate-950/70 text-slate-300 hover:border-slate-700',
                ].join(' ')}
              >
                <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${option.iconClass}`} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-semibold">{option.title}</span>
                    {isSelected && (
                      <span className="rounded bg-slate-950/70 px-2 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wide text-white">
                        Ativo
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 text-[11px] text-slate-400">{option.description}</p>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-slate-800/80 pt-3 text-[11px] text-slate-400">
        {sinkMode ? (
          <>
            <span>
              Timeout simulado:{' '}
              <strong className="font-mono text-slate-200">{sinkMode.timeoutDelayMs} ms</strong>
            </span>
            <span title={formatDateTimeParts(sinkMode.updatedAt).utc}>
              Atualizado: {formatDateTimeParts(sinkMode.updatedAt).local}
            </span>
          </>
        ) : (
          <span>{isLoading ? 'Consultando /api/v1/sink/mode...' : 'Modo não carregado'}</span>
        )}
      </div>
    </section>
  );
}
