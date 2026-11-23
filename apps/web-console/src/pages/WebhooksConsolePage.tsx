import { Fragment, useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Filter,
  KeyRound,
  Lock,
  PlusCircle,
  RefreshCw,
  RotateCcw,
  RotateCw,
  Webhook,
  X,
} from 'lucide-react';
import { PaginationControls } from '../components/PaginationControls';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { SinkModePanel } from '../components/SinkModePanel';
import { WebhookEndpointModal } from '../components/WebhookEndpointModal';
import { useAuth } from '../context/AuthContext';
import {
  WEBHOOK_DELIVERY_STATUS_BADGE_MAP,
  WEBHOOK_ENDPOINT_STATUS_BADGE_MAP,
} from '../lib/domainBadges';
import { formatDateTimeParts, isValidUuid, truncateId } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { webhooksService } from '../services/webhooksService';
import type {
  DlqReplayResponse,
  PageResponse,
  ProblemDetail,
  SinkMode,
  SinkModeResponse,
  WebhookDelivery,
  WebhookDeliveryStatus,
  WebhookEndpoint,
} from '../types/api';

type DeliveryStatusFilter = WebhookDeliveryStatus | '';

interface DeliveryFiltersState {
  status: DeliveryStatusFilter;
  merchantId: string;
  eventType: string;
}

const INITIAL_DELIVERY_FILTERS: DeliveryFiltersState = {
  status: '',
  merchantId: '',
  eventType: '',
};

const DELIVERY_STATUS_TABS: Array<{ value: DeliveryStatusFilter; label: string }> = [
  { value: '', label: 'ALL' },
  { value: 'DELIVERED', label: 'DELIVERED' },
  { value: 'RETRYING', label: 'RETRYING' },
  { value: 'DLQ', label: 'DLQ' },
  { value: 'PENDING', label: 'PENDING' },
];

function extractUnexpectedProblem(detail: string, instance: string): ProblemDetail {
  return {
    type: 'https://ledgerpulse.dev/errors/unexpected',
    title: 'Erro de comunicação',
    status: 500,
    detail,
    instance,
    correlationId: 'unavailable',
    timestamp: new Date().toISOString(),
  };
}

export function WebhooksConsolePage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const canOperate = user?.role === 'ADMIN' || user?.role === 'ANALYST';

  // Endpoints state
  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [endpointsLoading, setEndpointsLoading] = useState(true);
  const [endpointsProblem, setEndpointsProblem] = useState<ProblemDetail | null>(null);
  const [isEndpointModalOpen, setIsEndpointModalOpen] = useState(false);
  const [createdEndpointBanner, setCreatedEndpointBanner] = useState<WebhookEndpoint | null>(null);

  // Sink Mode state
  const [sinkMode, setSinkMode] = useState<SinkModeResponse | null>(null);
  const [sinkLoading, setSinkLoading] = useState(true);
  const [sinkUpdating, setSinkUpdating] = useState(false);
  const [sinkProblem, setSinkProblem] = useState<ProblemDetail | null>(null);

  // Deliveries state
  const [draftFilters, setDraftFilters] = useState<DeliveryFiltersState>(INITIAL_DELIVERY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<DeliveryFiltersState>(
    INITIAL_DELIVERY_FILTERS,
  );
  const [filterError, setFilterError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [deliveriesData, setDeliveriesData] = useState<PageResponse<WebhookDelivery> | null>(null);
  const [deliveriesLoading, setDeliveriesLoading] = useState(true);
  const [deliveriesProblem, setDeliveriesProblem] = useState<ProblemDetail | null>(null);

  // Expandable attempts & Replay DLQ state
  const [expandedDeliveryIds, setExpandedDeliveryIds] = useState<Record<string, boolean>>({});
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [replayProblem, setReplayProblem] = useState<ProblemDetail | null>(null);
  const [replayBanner, setReplayBanner] = useState<DlqReplayResponse | null>(null);

  const loadEndpoints = useCallback(async () => {
    setEndpointsLoading(true);
    setEndpointsProblem(null);

    try {
      const response = await webhooksService.listWebhookEndpoints({ page: 0, size: 20 });
      setEndpoints(response.items);
    } catch (error) {
      setEndpoints([]);
      setEndpointsProblem(
        error instanceof ApiError
          ? error.problem
          : extractUnexpectedProblem(
              'Não foi possível consultar /api/v1/webhooks/endpoints.',
              '/api/v1/webhooks/endpoints',
            ),
      );
    } finally {
      setEndpointsLoading(false);
    }
  }, []);

  const loadSinkMode = useCallback(async () => {
    setSinkLoading(true);
    setSinkProblem(null);

    try {
      const response = await webhooksService.getSinkMode();
      setSinkMode(response);
    } catch (error) {
      setSinkMode(null);
      setSinkProblem(
        error instanceof ApiError
          ? error.problem
          : extractUnexpectedProblem(
              'Não foi possível consultar /api/v1/sink/mode.',
              '/api/v1/sink/mode',
            ),
      );
    } finally {
      setSinkLoading(false);
    }
  }, []);

  const loadDeliveries = useCallback(async () => {
    setDeliveriesLoading(true);
    setDeliveriesProblem(null);

    try {
      const response = await webhooksService.listWebhookDeliveries({
        page,
        size,
        status: appliedFilters.status,
        merchantId: appliedFilters.merchantId,
        eventType: appliedFilters.eventType,
      });
      setDeliveriesData(response);
    } catch (error) {
      setDeliveriesData(null);
      setDeliveriesProblem(
        error instanceof ApiError
          ? error.problem
          : extractUnexpectedProblem(
              'Não foi possível consultar /api/v1/webhooks/deliveries.',
              '/api/v1/webhooks/deliveries',
            ),
      );
    } finally {
      setDeliveriesLoading(false);
    }
  }, [page, size, appliedFilters]);

  useEffect(() => {
    void loadEndpoints();
    void loadSinkMode();
  }, [loadEndpoints, loadSinkMode]);

  useEffect(() => {
    void loadDeliveries();
  }, [loadDeliveries]);

  const handleChangeSinkMode = async (nextMode: SinkMode) => {
    if (!canOperate) return;
    setSinkUpdating(true);
    setSinkProblem(null);

    try {
      const updated = await webhooksService.updateSinkMode(nextMode);
      setSinkMode(updated);
    } catch (error) {
      setSinkProblem(
        error instanceof ApiError
          ? error.problem
          : extractUnexpectedProblem(
              'Falha ao alterar o modo do Webhook Sink.',
              '/api/v1/sink/mode',
            ),
      );
    } finally {
      setSinkUpdating(false);
    }
  };

  const handleSelectStatusTab = (nextStatus: DeliveryStatusFilter) => {
    setDraftFilters((prev) => ({ ...prev, status: nextStatus }));
    setAppliedFilters((prev) => ({ ...prev, status: nextStatus }));
    setPage(0);
  };

  const handleApplyDeliveryFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFilterError(null);

    if (draftFilters.merchantId.trim() && !isValidUuid(draftFilters.merchantId)) {
      setFilterError('O campo Merchant ID deve conter um UUID válido.');
      return;
    }

    setPage(0);
    setAppliedFilters({ ...draftFilters });
  };

  const handleClearDeliveryFilters = () => {
    setFilterError(null);
    setDraftFilters(INITIAL_DELIVERY_FILTERS);
    setAppliedFilters(INITIAL_DELIVERY_FILTERS);
    setPage(0);
  };

  const toggleExpandAttempts = (deliveryId: string) => {
    setExpandedDeliveryIds((prev) => ({
      ...prev,
      [deliveryId]: !prev[deliveryId],
    }));
  };

  const handleReplayDlq = async (deliveryId: string) => {
    if (!canOperate) return;

    setReplayingId(deliveryId);
    setReplayProblem(null);
    setReplayBanner(null);

    try {
      const result = await webhooksService.replayDlqDelivery(deliveryId);
      setReplayBanner(result);
      await loadDeliveries();
    } catch (error) {
      setReplayProblem(
        error instanceof ApiError
          ? error.problem
          : extractUnexpectedProblem(
              'Falha ao executar replay manual da DLQ.',
              `/api/v1/webhooks/dlq/${deliveryId}/replay`,
            ),
      );
    } finally {
      setReplayingId(null);
    }
  };

  const handleEndpointCreated = (endpoint: WebhookEndpoint) => {
    setIsEndpointModalOpen(false);
    setCreatedEndpointBanner(endpoint);
    void loadEndpoints();
  };

  const deliveries = deliveriesData?.items ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <Webhook className="h-3.5 w-3.5" />
            <span>Virtual Threads + HMAC-SHA256 + RabbitMQ DLQ (:8082)</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">Console de Webhooks & DLQ</h2>
          <p className="mt-1 text-sm text-slate-400">
            Gerencie destinos de webhook, simule cenários no Sink local, inspecione assinaturas{' '}
            <code className="text-emerald-300">X-LedgerPulse-Signature</code> e execute replay da
            DLQ.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 self-start sm:self-center">
          <button
            type="button"
            onClick={() => {
              void loadEndpoints();
              void loadSinkMode();
              void loadDeliveries();
            }}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Atualizar Tudo</span>
          </button>

          <button
            type="button"
            disabled={!isAdmin}
            onClick={() => setIsEndpointModalOpen(true)}
            title={
              isAdmin
                ? 'Cadastrar novo endpoint webhook'
                : 'Cadastro de endpoints restrito ao perfil ADMIN'
            }
            className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
          >
            <PlusCircle className="h-4 w-4" />
            <span>Novo Endpoint</span>
          </button>
        </div>
      </section>

      {/* RBAC Notice */}
      {!isAdmin && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-xs text-amber-200"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
          <p>
            <strong>Permissão {user?.role ?? 'VIEWER'}:</strong>{' '}
            {canOperate
              ? 'você pode alterar o modo do Webhook Sink e executar Replay DLQ, porém o cadastro de novos endpoints é exclusivo do perfil ADMIN.'
              : 'acesso somente leitura — alterações no Webhook Sink, cadastro de endpoints e Replay DLQ exigem ADMIN/ANALYST.'}
          </p>
        </div>
      )}

      {/* Feedback Banners */}
      {createdEndpointBanner && (
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-100"
        >
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
            <span>
              Endpoint cadastrado: <code className="font-mono">{createdEndpointBanner.targetUrl}</code>{' '}
              (secret mascarado:{' '}
              <code className="font-mono text-emerald-300">
                {createdEndpointBanner.secretPreview}
              </code>
              ).
            </span>
          </div>
          <button
            type="button"
            aria-label="Fechar aviso"
            onClick={() => setCreatedEndpointBanner(null)}
            className="rounded p-1 text-emerald-300 hover:bg-emerald-500/20"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {replayBanner && (
        <div
          role="status"
          className="flex flex-col justify-between gap-2 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-100 sm:flex-row sm:items-center"
        >
          <div className="flex items-start gap-2.5">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
            <div>
              <p>
                Replay DLQ executado para{' '}
                <code className="font-mono text-emerald-200">
                  {truncateId(replayBanner.deliveryId)}
                </code>{' '}
                → status <strong className="font-mono">{replayBanner.status}</strong>
                {replayBanner.httpStatus !== null && replayBanner.httpStatus !== undefined
                  ? ` (HTTP ${replayBanner.httpStatus} · ${replayBanner.latencyMs} ms)`
                  : ` (${replayBanner.latencyMs} ms)`}
                .
              </p>
              {replayBanner.errorMessage && (
                <p className="mt-0.5 font-mono text-[11px] text-amber-200">
                  {replayBanner.errorMessage}
                </p>
              )}
            </div>
          </div>
          <button
            type="button"
            aria-label="Fechar aviso de replay"
            onClick={() => setReplayBanner(null)}
            className="self-end rounded p-1 text-emerald-300 hover:bg-emerald-500/20 sm:self-center"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {replayProblem && <ProblemDetailAlert problem={replayProblem} />}

      {/* Top Grid: Endpoints Summary + Sink Mode Panel */}
      <div className="grid gap-6 lg:grid-cols-12">
        <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5 lg:col-span-7">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h3 className="text-sm font-semibold text-white">Endpoints Cadastrados</h3>
              <p className="text-xs text-slate-400">
                Destinos ativos por merchant com{' '}
                <code className="text-emerald-300">secretPreview</code> mascarado.
              </p>
            </div>
            <span className="rounded-full border border-slate-700 bg-slate-950 px-2.5 py-0.5 font-mono text-[11px] text-slate-300">
              {endpoints.length} endpoint(s)
            </span>
          </div>

          {endpointsProblem && (
            <div className="mt-4">
              <ProblemDetailAlert problem={endpointsProblem} onRetry={() => void loadEndpoints()} />
            </div>
          )}

          {endpointsLoading && (
            <div className="mt-4 space-y-2.5">
              {[1, 2].map((row) => (
                <div
                  key={row}
                  className="h-16 animate-pulse rounded-xl border border-slate-800 bg-slate-950/60"
                />
              ))}
            </div>
          )}

          {!endpointsLoading && !endpointsProblem && endpoints.length === 0 && (
            <div className="mt-4 rounded-xl border border-dashed border-slate-800 bg-slate-950/50 p-6 text-center text-xs text-slate-400">
              Nenhum endpoint webhook cadastrado.{' '}
              {isAdmin && 'Clique em "Novo Endpoint" para registrar o Merchant Webhook Sink local.'}
            </div>
          )}

          {!endpointsLoading && !endpointsProblem && endpoints.length > 0 && (
            <div className="mt-4 space-y-2.5">
              {endpoints.map((ep) => {
                const statusBadge = WEBHOOK_ENDPOINT_STATUS_BADGE_MAP[ep.status];
                return (
                  <article
                    key={ep.id}
                    className="rounded-xl border border-slate-800 bg-slate-950/70 p-3.5 text-xs"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="truncate font-mono font-semibold text-slate-100">
                        {ep.targetUrl}
                      </span>
                      <span
                        className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[10px] font-semibold ${statusBadge.badgeClass}`}
                      >
                        {statusBadge.label}
                      </span>
                    </div>
                    {ep.description && (
                      <p className="mt-1 text-slate-400">{ep.description}</p>
                    )}
                    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-slate-400">
                      <span className="font-mono" title={ep.merchantId}>
                        merchant: {truncateId(ep.merchantId)}
                      </span>
                      <span className="inline-flex items-center gap-1 font-mono text-emerald-300">
                        <KeyRound className="h-3 w-3" />
                        <span>{ep.secretPreview}</span>
                      </span>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <div className="lg:col-span-5">
          <SinkModePanel
            sinkMode={sinkMode}
            isLoading={sinkLoading}
            isUpdating={sinkUpdating}
            canMutate={canOperate}
            problem={sinkProblem}
            onChangeMode={(mode) => void handleChangeSinkMode(mode)}
            onRefresh={() => void loadSinkMode()}
          />
        </div>
      </div>

      {/* Deliveries Filter & Status Tabs */}
      <section className="space-y-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5" role="tablist" aria-label="Filtro de status das entregas">
            {DELIVERY_STATUS_TABS.map((tab) => {
              const isActive = appliedFilters.status === tab.value;
              return (
                <button
                  key={tab.label}
                  type="button"
                  role="tab"
                  aria-selected={isActive}
                  onClick={() => handleSelectStatusTab(tab.value)}
                  className={[
                    'rounded-xl border px-3 py-1.5 font-mono text-xs font-semibold transition-colors',
                    isActive
                      ? 'border-emerald-500/45 bg-emerald-500/15 text-emerald-200'
                      : 'border-slate-800 bg-slate-950 text-slate-400 hover:border-slate-700 hover:text-slate-200',
                  ].join(' ')}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => void loadDeliveries()}
            disabled={deliveriesLoading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${deliveriesLoading ? 'animate-spin' : ''}`} />
            <span>Atualizar Entregas</span>
          </button>
        </div>

        <form onSubmit={handleApplyDeliveryFilters} className="grid gap-3 sm:grid-cols-4">
          <div className="sm:col-span-2">
            <label
              htmlFor="deliveries-filter-merchant"
              className="mb-1 block text-[11px] font-medium text-slate-400"
            >
              Merchant ID (`UUID`)
            </label>
            <input
              id="deliveries-filter-merchant"
              type="text"
              value={draftFilters.merchantId}
              onChange={(e) =>
                setDraftFilters((prev) => ({ ...prev, merchantId: e.target.value }))
              }
              placeholder="ex: 7b92c3e0-1f42-4876-a1e2-901283471234"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor="deliveries-filter-event-type"
              className="mb-1 block text-[11px] font-medium text-slate-400"
            >
              Tipo de Evento (`eventType`)
            </label>
            <input
              id="deliveries-filter-event-type"
              type="text"
              value={draftFilters.eventType}
              onChange={(e) =>
                setDraftFilters((prev) => ({ ...prev, eventType: e.target.value }))
              }
              placeholder="ex: payment.settled"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
          </div>

          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={handleClearDeliveryFilters}
              className="inline-flex flex-1 items-center justify-center gap-1 rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs font-medium text-slate-300 hover:text-white"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Limpar</span>
            </button>
            <button
              type="submit"
              className="inline-flex flex-1 items-center justify-center gap-1 rounded-xl bg-emerald-500 px-3 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-400"
            >
              <Filter className="h-3.5 w-3.5" />
              <span>Filtrar</span>
            </button>
          </div>
        </form>

        {filterError && (
          <p role="alert" className="text-xs font-medium text-rose-400">
            {filterError}
          </p>
        )}
      </section>

      {deliveriesProblem && (
        <ProblemDetailAlert problem={deliveriesProblem} onRetry={() => void loadDeliveries()} />
      )}

      {/* Deliveries Table */}
      <section className="overflow-hidden rounded-2xl border border-slate-800/90 bg-slate-900/60">
        {deliveriesLoading && (
          <div className="space-y-3 p-6">
            {[1, 2, 3].map((row) => (
              <div
                key={row}
                className="h-14 animate-pulse rounded-xl border border-slate-800/80 bg-slate-950/60"
              />
            ))}
          </div>
        )}

        {!deliveriesLoading && !deliveriesProblem && deliveries.length === 0 && (
          <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-800 bg-slate-950 text-slate-400">
              <Webhook className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-sm font-semibold text-white">
              Nenhuma entrega de webhook encontrada
            </h3>
            <p className="mt-1 max-w-md text-xs text-slate-400">
              Dispare um pagamento no Simulador Checkout para gerar eventos no Outbox e acompanhar
              as entregas assinadas por HMAC-SHA256.
            </p>
          </div>
        )}

        {!deliveriesLoading && !deliveriesProblem && deliveries.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-800 text-left text-xs">
                <thead className="bg-slate-950/70 text-[11px] uppercase tracking-wider text-slate-400">
                  <tr>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Evento / Correlação
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Destino
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Status
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Tentativas / HTTP
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Atualizado
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Ações
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/70">
                  {deliveries.map((delivery) => {
                    const statusBadge = WEBHOOK_DELIVERY_STATUS_BADGE_MAP[delivery.status];
                    const isExpanded = Boolean(expandedDeliveryIds[delivery.id]);
                    const dateParts = formatDateTimeParts(delivery.updatedAt);

                    return (
                      <Fragment key={delivery.id}>
                        <tr className="hover:bg-slate-800/35">
                          <td className="px-4 py-3.5">
                            <p className="font-mono font-semibold text-white">
                              {delivery.eventType}
                            </p>
                            <p
                              title={delivery.eventId}
                              className="mt-0.5 font-mono text-[11px] text-slate-400"
                            >
                              event: {truncateId(delivery.eventId)}
                            </p>
                            <p
                              title={delivery.correlationId}
                              className="mt-0.5 font-mono text-[11px] text-slate-500"
                            >
                              corr: {truncateId(delivery.correlationId)}
                            </p>
                          </td>

                          <td className="max-w-xs px-4 py-3.5">
                            <p
                              title={delivery.targetUrl}
                              className="truncate font-mono text-slate-200"
                            >
                              {delivery.targetUrl}
                            </p>
                            <p
                              title={delivery.id}
                              className="mt-0.5 font-mono text-[11px] text-slate-500"
                            >
                              delivery: {truncateId(delivery.id)}
                            </p>
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5">
                            <span
                              className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${statusBadge.badgeClass}`}
                            >
                              {statusBadge.label}
                            </span>
                            {delivery.lastError && (
                              <p
                                title={delivery.lastError}
                                className="mt-1 max-w-[14rem] truncate text-[11px] text-rose-300/90"
                              >
                                {delivery.lastError}
                              </p>
                            )}
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5 font-mono">
                            <p className="text-slate-200">
                              {delivery.attemptCount} / {delivery.maxAttempts}
                            </p>
                            <p className="mt-0.5 text-[11px] text-slate-400">
                              {delivery.lastHttpStatus ? `HTTP ${delivery.lastHttpStatus}` : 'Sem HTTP'}{' '}
                              {delivery.lastLatencyMs !== null &&
                              delivery.lastLatencyMs !== undefined
                                ? `· ${delivery.lastLatencyMs} ms`
                                : ''}
                            </p>
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5">
                            <p className="text-slate-200">{dateParts.local}</p>
                            <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                              {dateParts.utc}
                            </p>
                          </td>

                          <td className="whitespace-nowrap px-4 py-3.5 text-right">
                            <div className="inline-flex items-center gap-2">
                              <button
                                type="button"
                                onClick={() => toggleExpandAttempts(delivery.id)}
                                aria-expanded={isExpanded}
                                className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 hover:border-slate-600"
                              >
                                {isExpanded ? (
                                  <ChevronDown className="h-3.5 w-3.5" />
                                ) : (
                                  <ChevronRight className="h-3.5 w-3.5" />
                                )}
                                <span>Tentativas ({delivery.attempts.length})</span>
                              </button>

                              {delivery.status === 'DLQ' && (
                                <button
                                  type="button"
                                  disabled={!canOperate || replayingId === delivery.id}
                                  onClick={() => void handleReplayDlq(delivery.id)}
                                  title={
                                    canOperate
                                      ? 'Executar replay manual da DLQ'
                                      : 'Replay restrito a ADMIN e ANALYST'
                                  }
                                  className="inline-flex items-center gap-1 rounded-lg bg-emerald-500 px-2.5 py-1.5 text-[11px] font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
                                >
                                  <RotateCw
                                    className={`h-3.5 w-3.5 ${
                                      replayingId === delivery.id ? 'animate-spin' : ''
                                    }`}
                                  />
                                  <span>
                                    {replayingId === delivery.id
                                      ? 'Reenviando...'
                                      : 'Replay DLQ'}
                                  </span>
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>

                        {isExpanded && (
                          <tr className="bg-slate-950/75">
                            <td colSpan={6} className="px-5 py-4">
                              <div className="space-y-3">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                  <p className="text-xs font-semibold text-slate-200">
                                    Histórico de Tentativas & Assinatura HMAC-SHA256
                                  </p>
                                  {delivery.signatureHeader && (
                                    <code className="max-w-full truncate rounded border border-slate-800 bg-slate-900 px-2.5 py-1 font-mono text-[11px] text-emerald-300">
                                      X-LedgerPulse-Signature: {delivery.signatureHeader}
                                    </code>
                                  )}
                                </div>

                                {delivery.attempts.length === 0 ? (
                                  <p className="text-xs text-slate-400">
                                    Nenhuma tentativa registrada ainda.
                                  </p>
                                ) : (
                                  <div className="overflow-x-auto rounded-xl border border-slate-800">
                                    <table className="min-w-full divide-y divide-slate-800 text-left text-[11px]">
                                      <thead className="bg-slate-900 text-slate-400">
                                        <tr>
                                          <th className="px-3 py-2 font-semibold">#</th>
                                          <th className="px-3 py-2 font-semibold">HTTP</th>
                                          <th className="px-3 py-2 font-semibold">Latência</th>
                                          <th className="px-3 py-2 font-semibold">
                                            X-LedgerPulse-Signature
                                          </th>
                                          <th className="px-3 py-2 font-semibold">Erro</th>
                                          <th className="px-3 py-2 font-semibold">Data/Hora</th>
                                        </tr>
                                      </thead>
                                      <tbody className="divide-y divide-slate-800/70">
                                        {delivery.attempts.map((attempt) => (
                                          <tr key={attempt.attemptNumber}>
                                            <td className="px-3 py-2 font-mono font-semibold text-slate-200">
                                              #{attempt.attemptNumber}
                                            </td>
                                            <td className="px-3 py-2 font-mono">
                                              <span
                                                className={
                                                  attempt.httpStatus && attempt.httpStatus < 300
                                                    ? 'text-emerald-300'
                                                    : 'text-rose-300'
                                                }
                                              >
                                                {attempt.httpStatus ?? 'TIMEOUT'}
                                              </span>
                                            </td>
                                            <td className="px-3 py-2 font-mono text-slate-300">
                                              {attempt.latencyMs} ms
                                            </td>
                                            <td className="max-w-xs truncate px-3 py-2 font-mono text-emerald-300/90">
                                              {attempt.signatureHeader}
                                            </td>
                                            <td className="max-w-xs truncate px-3 py-2 text-rose-300">
                                              {attempt.errorMessage ?? '—'}
                                            </td>
                                            <td className="whitespace-nowrap px-3 py-2 text-slate-400">
                                              {formatDateTimeParts(attempt.attemptedAt).local}
                                            </td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  </div>
                                )}
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {deliveriesData && (
              <PaginationControls
                page={deliveriesData.page}
                size={deliveriesData.size}
                totalElements={deliveriesData.totalElements}
                totalPages={deliveriesData.totalPages}
                isLoading={deliveriesLoading}
                onPageChange={(nextPage) => setPage(nextPage)}
                onSizeChange={(nextSize) => {
                  setSize(nextSize);
                  setPage(0);
                }}
              />
            )}
          </>
        )}
      </section>

      <WebhookEndpointModal
        isOpen={isEndpointModalOpen}
        onClose={() => setIsEndpointModalOpen(false)}
        onCreated={handleEndpointCreated}
      />
    </div>
  );
}
