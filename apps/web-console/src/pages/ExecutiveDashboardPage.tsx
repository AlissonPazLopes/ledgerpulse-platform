import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
  Webhook,
} from 'lucide-react';
import { LiveEventFeed } from '../components/LiveEventFeed';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { useAuth } from '../context/AuthContext';
import { formatBpsPercent, formatCurrencyFromCents } from '../lib/currency';
import { ApiError } from '../lib/httpClient';
import { connectSseStream, type SseConnectionStatus } from '../lib/sseClient';
import { dashboardService } from '../services/dashboardService';
import { webhooksService } from '../services/webhooksService';
import type {
  DashboardSummaryResponse,
  ProblemDetail,
  SseDomainEvent,
  WebhookDelivery,
} from '../types/api';

const MAX_LIVE_EVENTS = 30;

interface WebhookHealthSummary {
  available: boolean;
  totalSampled: number;
  deliveredCount: number;
  retryingCount: number;
  dlqCount: number;
}

export function ExecutiveDashboardPage() {
  const { token } = useAuth();

  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  const [webhookHealth, setWebhookHealth] = useState<WebhookHealthSummary>({
    available: false,
    totalSampled: 0,
    deliveredCount: 0,
    retryingCount: 0,
    dlqCount: 0,
  });
  const [webhooksLoading, setWebhooksLoading] = useState(true);

  const [sseStatus, setSseStatus] = useState<SseConnectionStatus>('connecting');
  const [liveEvents, setLiveEvents] = useState<SseDomainEvent[]>([]);
  const [lastHeartbeatAt, setLastHeartbeatAt] = useState<string | null>(null);

  const loadSummary = useCallback(async () => {
    setProblem(null);
    try {
      const response = await dashboardService.getSummary();
      setSummary(response);
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar Dashboard',
          status: 500,
          detail: 'Falha inesperada ao consultar /api/v1/dashboard/summary.',
          instance: '/api/v1/dashboard/summary',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadWebhookHealth = useCallback(async () => {
    setWebhooksLoading(true);
    try {
      const page = await webhooksService.listWebhookDeliveries({ page: 0, size: 20 });
      const items: WebhookDelivery[] = page.items;
      setWebhookHealth({
        available: true,
        totalSampled: items.length,
        deliveredCount: items.filter((item) => item.status === 'DELIVERED').length,
        retryingCount: items.filter((item) => item.status === 'RETRYING').length,
        dlqCount: items.filter((item) => item.status === 'DLQ').length,
      });
    } catch {
      // Graceful degradation when webhook-dispatcher (:8082) is offline
      setWebhookHealth({
        available: false,
        totalSampled: 0,
        deliveredCount: 0,
        retryingCount: 0,
        dlqCount: 0,
      });
    } finally {
      setWebhooksLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSummary();
    void loadWebhookHealth();
  }, [loadSummary, loadWebhookHealth]);

  useEffect(() => {
    const disconnect = connectSseStream({
      token,
      onStatusChange: setSseStatus,
      onControlEvent: (controlEvent) => {
        setLastHeartbeatAt(controlEvent.timestamp);
      },
      onDomainEvent: (domainEvent) => {
        setLastHeartbeatAt(domainEvent.occurredAt);
        setLiveEvents((prev) => {
          if (prev.some((existing) => existing.eventId === domainEvent.eventId)) {
            return prev;
          }
          return [domainEvent, ...prev].slice(0, MAX_LIVE_EVENTS);
        });
        void loadSummary();
        if (domainEvent.eventType.startsWith('payment.')) {
          void loadWebhookHealth();
        }
      },
    });

    return disconnect;
  }, [token, loadSummary, loadWebhookHealth]);

  const hourlySeries = useMemo(() => summary?.hourlySeries ?? [], [summary]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <Activity className="h-3.5 w-3.5" />
            <span>Observabilidade Executiva · Stream SSE Real-Time</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">
            Dashboard Executivo Real-Time
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Acompanhe TPV, taxa de aprovação, fila antifraude e saúde das entregas de webhook em
            tempo real.
          </p>
        </div>

        <button
          type="button"
          onClick={() => {
            void loadSummary();
            void loadWebhookHealth();
          }}
          disabled={isLoading}
          className="inline-flex items-center gap-2 self-start rounded-xl border border-slate-700 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60 sm:self-center"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Atualizar KPIs</span>
        </button>
      </section>

      {problem && <ProblemDetailAlert problem={problem} onRetry={() => void loadSummary()} />}

      {/* KPI Cards */}
      <section aria-label="Indicadores executivos">
        {isLoading && !summary ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {[1, 2, 3, 4].map((card) => (
              <div
                key={card}
                className="h-32 animate-pulse rounded-2xl border border-slate-800 bg-slate-900/60"
              />
            ))}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {/* TPV */}
            <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-slate-400">TPV Liquidado</span>
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-400">
                  <TrendingUp className="h-4 w-4" />
                </div>
              </div>
              <p className="mt-3 font-mono text-2xl font-bold text-white">
                {formatCurrencyFromCents(summary?.tpvCents ?? 0, 'BRL')}
              </p>
              <p className="mt-2 text-xs text-slate-400">
                {summary?.settledCount ?? 0} liquidado(s) · Estornos:{' '}
                <span className="font-mono text-slate-300">
                  {formatCurrencyFromCents(summary?.totalRefundedCents ?? 0, 'BRL')}
                </span>
              </p>
            </article>

            {/* Taxa de Aprovação */}
            <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-slate-400">Taxa de Aprovação</span>
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-sky-500/15 text-sky-400">
                  <CheckCircle2 className="h-4 w-4" />
                </div>
              </div>
              <p className="mt-3 font-mono text-2xl font-bold text-white">
                {formatBpsPercent(summary?.approvalRateBps ?? 0)}
              </p>
              <p className="mt-2 text-xs text-slate-400">
                {summary?.totalPayments ?? 0} pagamento(s) · Risk médio:{' '}
                <span className="font-mono text-sky-300">
                  {summary?.averageRiskScore ?? 0}/100
                </span>
              </p>
            </article>

            {/* Fila Antifraude */}
            <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-slate-400">Fila Antifraude</span>
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber-500/15 text-amber-400">
                  <ShieldAlert className="h-4 w-4" />
                </div>
              </div>
              <p className="mt-3 font-mono text-2xl font-bold text-white">
                {summary?.manualReviewCount ?? 0} pendente(s)
              </p>
              <div className="mt-2 flex items-center justify-between text-xs text-slate-400">
                <span>{summary?.declinedCount ?? 0} recusado(s)</span>
                <Link
                  to="/fraud-review"
                  className="inline-flex items-center gap-1 font-medium text-amber-300 hover:underline"
                >
                  <span>Revisar</span>
                  <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </article>

            {/* Saúde Webhooks */}
            <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-slate-400">Saúde Webhooks</span>
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-violet-500/15 text-violet-400">
                  <Webhook className="h-4 w-4" />
                </div>
              </div>

              {webhooksLoading ? (
                <p className="mt-3 font-mono text-sm text-slate-400">Consultando :8082...</p>
              ) : webhookHealth.available ? (
                <>
                  <p className="mt-3 font-mono text-2xl font-bold text-white">
                    {webhookHealth.deliveredCount}/{webhookHealth.totalSampled} OK
                  </p>
                  <div className="mt-2 flex items-center justify-between text-xs text-slate-400">
                    <span
                      className={
                        webhookHealth.dlqCount > 0 ? 'font-semibold text-rose-300' : 'text-emerald-300'
                      }
                    >
                      {webhookHealth.dlqCount} DLQ · {webhookHealth.retryingCount} retry
                    </span>
                    <Link
                      to="/webhooks"
                      className="inline-flex items-center gap-1 font-medium text-violet-300 hover:underline"
                    >
                      <span>Console</span>
                      <ArrowRight className="h-3 w-3" />
                    </Link>
                  </div>
                </>
              ) : (
                <>
                  <p className="mt-3 font-mono text-lg font-bold text-amber-300">
                    Offline (:8082)
                  </p>
                  <p className="mt-2 text-xs text-slate-400">
                    webhook-dispatcher indisponível no momento.
                  </p>
                </>
              )}
            </article>
          </div>
        )}
      </section>

      {/* Recharts Analytics Row */}
      <section className="grid gap-6 lg:grid-cols-2">
        <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
          <div className="mb-4 flex items-center justify-between gap-2">
            <div>
              <h3 className="text-sm font-semibold text-white">Volume Financeiro por Hora</h3>
              <p className="text-xs text-slate-400">
                Série horária em centavos formatada via <code className="text-emerald-300">BRL</code>
              </p>
            </div>
          </div>

          {hourlySeries.length === 0 ? (
            <div className="flex h-64 items-center justify-center rounded-xl border border-dashed border-slate-800 text-xs text-slate-500">
              Sem movimentações horárias registradas ainda.
            </div>
          ) : (
            <div className="h-64 w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={hourlySeries} margin={{ top: 8, right: 12, left: 8, bottom: 0 }}>
                  <defs>
                    <linearGradient id="tpvGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.35} />
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="hourLabel" stroke="#94a3b8" fontSize={11} />
                  <YAxis
                    stroke="#94a3b8"
                    fontSize={11}
                    width={82}
                    tickFormatter={(val: number) =>
                      formatCurrencyFromCents(Math.round(Number(val)), 'BRL')
                    }
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#0f172a',
                      borderColor: '#334155',
                      borderRadius: '0.75rem',
                      fontSize: '12px',
                    }}
                    formatter={(value: number | string) => [
                      formatCurrencyFromCents(Math.round(Number(value)), 'BRL'),
                      'Volume',
                    ]}
                  />
                  <Area
                    type="monotone"
                    dataKey="volumeCents"
                    name="Volume"
                    stroke="#10b981"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#tpvGradient)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </article>

        <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-white">
              Pagamentos & Score de Risco por Hora
            </h3>
            <p className="text-xs text-slate-400">
              Contagem de pagamentos comparada ao score médio do <code className="text-amber-300">risk-engine-ai</code>
            </p>
          </div>

          {hourlySeries.length === 0 ? (
            <div className="flex h-64 items-center justify-center rounded-xl border border-dashed border-slate-800 text-xs text-slate-500">
              Sem série horária de risco registrada ainda.
            </div>
          ) : (
            <div className="h-64 w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={hourlySeries} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="hourLabel" stroke="#94a3b8" fontSize={11} />
                  <YAxis stroke="#94a3b8" fontSize={11} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#0f172a',
                      borderColor: '#334155',
                      borderRadius: '0.75rem',
                      fontSize: '12px',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: '11px' }} />
                  <Bar
                    dataKey="paymentCount"
                    name="Pagamentos"
                    fill="#38bdf8"
                    radius={[6, 6, 0, 0]}
                  />
                  <Bar
                    dataKey="averageRiskScore"
                    name="Score Médio (0–100)"
                    fill="#f59e0b"
                    radius={[6, 6, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </article>
      </section>

      {/* Live SSE Feed */}
      <LiveEventFeed
        connectionStatus={sseStatus}
        events={liveEvents}
        lastHeartbeatAt={lastHeartbeatAt}
        onClearEvents={() => setLiveEvents([])}
      />
    </div>
  );
}
