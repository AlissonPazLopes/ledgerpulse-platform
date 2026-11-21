import { AlertCircle, RefreshCw } from 'lucide-react';
import type { ProblemDetail } from '../types/api';

interface ProblemDetailAlertProps {
  problem: ProblemDetail;
  onRetry?: () => void;
  retryLabel?: string;
}

export function ProblemDetailAlert({
  problem,
  onRetry,
  retryLabel = 'Tentar novamente',
}: ProblemDetailAlertProps) {
  return (
    <div
      role="alert"
      className="rounded-2xl border border-rose-500/40 bg-rose-950/35 p-4 text-sm text-rose-100"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-rose-400" />
          <div className="min-w-0 space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <p className="font-semibold text-rose-100">{problem.title}</p>
              <span className="rounded bg-rose-500/20 px-2 py-0.5 font-mono text-[11px] text-rose-300">
                HTTP {problem.status}
              </span>
            </div>
            <p className="text-xs leading-relaxed text-rose-200/90">{problem.detail}</p>
            {problem.errors && problem.errors.length > 0 && (
              <ul className="space-y-1 pt-1 text-xs text-rose-200">
                {problem.errors.map((err, index) => (
                  <li key={`${err.field}-${index}`} className="font-mono text-[11px]">
                    <span className="text-rose-300">{err.field}</span>: {err.message}
                  </li>
                ))}
              </ul>
            )}
            <p className="truncate font-mono text-[11px] text-rose-300/80">
              correlationId: {problem.correlationId}
            </p>
          </div>
        </div>

        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-xl border border-rose-500/40 bg-rose-500/15 px-3 py-1.5 text-xs font-medium text-rose-100 transition-colors hover:bg-rose-500/25"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>{retryLabel}</span>
          </button>
        )}
      </div>
    </div>
  );
}
