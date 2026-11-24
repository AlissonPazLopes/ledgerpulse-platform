import { Radio, Trash2, Wifi, WifiOff } from 'lucide-react';
import { formatCurrencyFromCents } from '../lib/currency';
import { formatDateTimeParts, truncateId } from '../lib/formatters';
import type { SseConnectionStatus } from '../lib/sseClient';
import type { SseDomainEvent, SseDomainEventType } from '../types/api';

interface LiveEventFeedProps {
  connectionStatus: SseConnectionStatus;
  events: SseDomainEvent[];
  lastHeartbeatAt?: string | null;
  onClearEvents?: () => void;
}

const EVENT_TYPE_BADGE_MAP: Record<
  SseDomainEventType,
  { label: string; badgeClass: string }
> = {
  'payment.settled': {
    label: 'payment.settled',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300',
  },
  'payment.manual_review_required': {
    label: 'payment.manual_review_required',
    badgeClass: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
  },
  'payment.declined': {
    label: 'payment.declined',
    badgeClass: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
  },
  'payment.refunded': {
    label: 'payment.refunded',
    badgeClass: 'border-violet-500/40 bg-violet-500/15 text-violet-300',
  },
  'risk.feedback.recorded': {
    label: 'risk.feedback.recorded',
    badgeClass: 'border-sky-500/40 bg-sky-500/15 text-sky-300',
  },
  'reconciliation.completed': {
    label: 'reconciliation.completed',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/10 text-emerald-200',
  },
};

const CONNECTION_STATUS_UI: Record<
  SseConnectionStatus,
  { label: string; badgeClass: string; dotClass: string }
> = {
  connected: {
    label: 'Conectado',
    badgeClass: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300',
    dotClass: 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.75)]',
  },
  connecting: {
    label: 'Reconectando',
    badgeClass: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
    dotClass: 'bg-amber-400 animate-pulse',
  },
  reconnecting: {
    label: 'Reconectando',
    badgeClass: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
    dotClass: 'bg-amber-400 animate-pulse',
  },
  offline: {
    label: 'Offline',
    badgeClass: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
    dotClass: 'bg-rose-400',
  },
};

function getPayloadAmountCents(payload: Record<string, unknown>): number | null {
  const candidate = payload.amountCents;
  return typeof candidate === 'number' && Number.isSafeInteger(candidate) ? candidate : null;
}

function getPayloadString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key];
  return typeof value === 'string' && value.length > 0 ? value : null;
}

export function LiveEventFeed({
  connectionStatus,
  events,
  lastHeartbeatAt,
  onClearEvents,
}: LiveEventFeedProps) {
  const statusUi = CONNECTION_STATUS_UI[connectionStatus];

  return (
    <section
      aria-label="Feed ao Vivo SSE"
      className="flex flex-col rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-400">
            <Radio className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Feed ao Vivo SSE</h3>
            <p className="text-xs text-slate-400">
              Eventos de domínio via <code className="text-emerald-300">/api/v1/stream/events</code>
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span
            role="status"
            aria-label={`Status SSE: ${statusUi.label}`}
            className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 font-mono text-xs font-semibold ${statusUi.badgeClass}`}
          >
            {connectionStatus === 'offline' ? (
              <WifiOff className="h-3.5 w-3.5" />
            ) : (
              <Wifi className="h-3.5 w-3.5" />
            )}
            <span className={`h-2 w-2 rounded-full ${statusUi.dotClass}`} />
            <span>{statusUi.label}</span>
          </span>

          {events.length > 0 && onClearEvents && (
            <button
              type="button"
              onClick={onClearEvents}
              aria-label="Limpar feed de eventos"
              className="inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-300 hover:border-slate-700 hover:text-white"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>Limpar</span>
            </button>
          )}
        </div>
      </div>

      {lastHeartbeatAt && (
        <p className="mt-2.5 font-mono text-[11px] text-slate-500">
          Último sinal SSE: {formatDateTimeParts(lastHeartbeatAt).local}
        </p>
      )}

      {events.length === 0 ? (
        <div className="mt-4 flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-800 bg-slate-950/50 px-4 py-10 text-center">
          <p className="text-xs font-medium text-slate-300">
            Aguardando eventos em tempo real...
          </p>
          <p className="mt-1 max-w-md text-[11px] text-slate-500">
            Dispare um pagamento no Simulador de Checkout, aprove uma revisão antifraude ou execute
            a conciliação para visualizar eventos transmitidos instantaneamente.
          </p>
        </div>
      ) : (
        <ul className="mt-4 max-h-96 space-y-2.5 overflow-y-auto pr-1">
          {events.map((event) => {
            const eventBadge = EVENT_TYPE_BADGE_MAP[event.eventType];
            const dateParts = formatDateTimeParts(event.occurredAt);
            const amountCents = getPayloadAmountCents(event.payload);
            const paymentId = getPayloadString(event.payload, 'paymentId');
            const status = getPayloadString(event.payload, 'status');

            return (
              <li
                key={event.eventId}
                className="rounded-xl border border-slate-800 bg-slate-950/75 p-3.5 text-xs"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span
                    className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${eventBadge.badgeClass}`}
                  >
                    {eventBadge.label}
                  </span>

                  <span title={dateParts.utc} className="font-mono text-[11px] text-slate-400">
                    {dateParts.local}
                  </span>
                </div>

                <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-300">
                  {amountCents !== null && (
                    <span className="font-mono font-bold text-emerald-300">
                      {formatCurrencyFromCents(amountCents, 'BRL')}
                    </span>
                  )}
                  {status && (
                    <span>
                      status: <strong className="font-mono text-white">{status}</strong>
                    </span>
                  )}
                  {paymentId && (
                    <span title={paymentId} className="font-mono text-slate-400">
                      payment: {truncateId(paymentId)}
                    </span>
                  )}
                  <span title={event.correlationId} className="font-mono text-slate-500">
                    corr: {truncateId(event.correlationId)}
                  </span>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
