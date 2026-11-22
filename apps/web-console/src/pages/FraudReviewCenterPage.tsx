import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  CheckCircle2,
  Filter,
  Lock,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  X,
  XCircle,
} from 'lucide-react';
import { PaginationControls } from '../components/PaginationControls';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { ReasonCodeChips } from '../components/ReasonCodeChips';
import { ReviewDecisionModal } from '../components/ReviewDecisionModal';
import { RiskScoreGauge } from '../components/RiskScoreGauge';
import { useAuth } from '../context/AuthContext';
import { formatCurrencyFromCents, parseSupportedCurrency } from '../lib/currency';
import {
  formatDateTimeParts,
  isValidUuid,
  toIsoUtcFromLocalInput,
  truncateId,
} from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { riskReviewService } from '../services/riskReviewService';
import type {
  PageResponse,
  ProblemDetail,
  RiskReviewDecisionResponse,
  RiskReviewDecisionType,
  RiskReviewItem,
} from '../types/api';

interface FraudFiltersState {
  minRiskScore: string;
  merchantId: string;
  fromLocal: string;
  toLocal: string;
}

const INITIAL_FILTERS: FraudFiltersState = {
  minRiskScore: '',
  merchantId: '',
  fromLocal: '',
  toLocal: '',
};

export function FraudReviewCenterPage() {
  const { user } = useAuth();
  const canReview = user?.role === 'ADMIN' || user?.role === 'ANALYST';

  const [draftFilters, setDraftFilters] = useState<FraudFiltersState>(INITIAL_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<FraudFiltersState>(INITIAL_FILTERS);
  const [filterError, setFilterError] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState<PageResponse<RiskReviewItem> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [forbiddenProblem, setForbiddenProblem] = useState<ProblemDetail | null>(null);

  const [modalItem, setModalItem] = useState<RiskReviewItem | null>(null);
  const [modalInitialDecision, setModalInitialDecision] =
    useState<RiskReviewDecisionType>('APPROVE');
  const [lastDecision, setLastDecision] = useState<RiskReviewDecisionResponse | null>(null);

  const loadReviews = useCallback(async () => {
    setIsLoading(true);
    setProblem(null);
    setForbiddenProblem(null);

    const parsedMinScore =
      appliedFilters.minRiskScore.trim() !== ''
        ? Number.parseInt(appliedFilters.minRiskScore.trim(), 10)
        : undefined;

    try {
      const response = await riskReviewService.listRiskReviews({
        page,
        size,
        merchantId: appliedFilters.merchantId,
        minRiskScore: parsedMinScore,
        from: toIsoUtcFromLocalInput(appliedFilters.fromLocal),
        to: toIsoUtcFromLocalInput(appliedFilters.toLocal),
      });
      setData(response);
    } catch (error) {
      setData(null);
      if (error instanceof ApiError) {
        if (error.status === 403) {
          setForbiddenProblem(error.problem);
        } else {
          setProblem(error.problem);
        }
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar fila antifraude',
          status: 500,
          detail: 'Falha inesperada ao consultar /api/v1/risk/reviews.',
          instance: '/api/v1/risk/reviews',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [page, size, appliedFilters]);

  useEffect(() => {
    void loadReviews();
  }, [loadReviews]);

  const handleApplyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFilterError(null);

    const trimmedScore = draftFilters.minRiskScore.trim();
    if (trimmedScore !== '') {
      const parsed = Number(trimmedScore);
      if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
        setFilterError('Score mínimo deve ser um número inteiro entre 0 e 100.');
        return;
      }
    }

    if (draftFilters.merchantId.trim() && !isValidUuid(draftFilters.merchantId)) {
      setFilterError('O campo Merchant ID deve conter um UUID válido.');
      return;
    }

    const fromIso = toIsoUtcFromLocalInput(draftFilters.fromLocal);
    const toIso = toIsoUtcFromLocalInput(draftFilters.toLocal);
    if (fromIso && toIso && new Date(fromIso).getTime() > new Date(toIso).getTime()) {
      setFilterError('A data inicial (De) não pode ser maior que a data final (Até).');
      return;
    }

    setPage(0);
    setAppliedFilters({ ...draftFilters });
  };

  const handleClearFilters = () => {
    setFilterError(null);
    setDraftFilters(INITIAL_FILTERS);
    setAppliedFilters(INITIAL_FILTERS);
    setPage(0);
  };

  const handleOpenDecisionModal = (
    item: RiskReviewItem,
    initialDecision: RiskReviewDecisionType,
  ) => {
    if (!canReview) {
      return;
    }
    setModalItem(item);
    setModalInitialDecision(initialDecision);
  };

  const handleDecisionSubmitted = (response: RiskReviewDecisionResponse) => {
    setModalItem(null);
    setLastDecision(response);

    if (page > 0 && (data?.items.length ?? 0) <= 1) {
      setPage((prev) => Math.max(0, prev - 1));
    } else {
      void loadReviews();
    }
  };

  const hasActiveFilters = Boolean(
    appliedFilters.minRiskScore.trim() ||
      appliedFilters.merchantId.trim() ||
      appliedFilters.fromLocal ||
      appliedFilters.toLocal,
  );

  const items = data?.items ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-amber-500/35 bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-300">
            <ShieldAlert className="h-3.5 w-3.5" />
            <span>Motor Antifraude Híbrido + Fallback Resilience4j</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">Central Antifraude</h2>
          <p className="mt-1 text-sm text-slate-400">
            Avalie pagamentos retidos em <code className="text-amber-300">MANUAL_REVIEW</code>,
            inspecione os <code className="text-emerald-300">reasonCodes</code> e registre decisões
            auditáveis de liquidação ou recusa.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadReviews()}
          disabled={isLoading}
          className="inline-flex items-center gap-2 self-start rounded-xl border border-slate-700 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60 sm:self-center"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Atualizar Fila</span>
        </button>
      </section>

      {/* RBAC Restricted Banner for VIEWER */}
      {!canReview && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-xs text-amber-200"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
          <div className="space-y-1">
            <p className="font-semibold text-amber-100">
              Acesso restrito para perfil {user?.role ?? 'VIEWER'}
            </p>
            <p className="text-amber-200/90">
              A fila <code className="text-amber-100">GET /api/v1/risk/reviews</code> e o registro
              de decisões (<code className="text-amber-100">APPROVE</code> /{' '}
              <code className="text-amber-100">DECLINE</code>) são permitidos apenas para
              operadores <code className="text-amber-100">ANALYST</code> ou{' '}
              <code className="text-amber-100">ADMIN</code>.
            </p>
          </div>
        </div>
      )}

      {/* Decision Success Feedback */}
      {lastDecision && (
        <div
          role="status"
          className="flex flex-col justify-between gap-3 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-100 sm:flex-row sm:items-center"
        >
          <div className="flex items-start gap-2.5">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
            <div className="space-y-0.5">
              <p>
                Decisão <strong>{lastDecision.decision}</strong> registrada para pagamento{' '}
                <code className="font-mono text-emerald-200">
                  {truncateId(lastDecision.paymentId)}
                </code>{' '}
                → status <code className="font-mono font-semibold">{lastDecision.paymentStatus}</code>.
              </p>
              <p className="font-mono text-[11px] text-emerald-300/85">
                decisionId: {truncateId(lastDecision.decisionId)} · analista:{' '}
                {lastDecision.reviewedByEmail}
                {lastDecision.ledgerTransactionId
                  ? ` · ledgerTx: ${truncateId(lastDecision.ledgerTransactionId)}`
                  : ''}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 self-end sm:self-center">
            {lastDecision.ledgerTransactionId && (
              <Link
                to="/ledger"
                className="rounded-lg border border-emerald-400/40 bg-emerald-500/15 px-2.5 py-1 text-[11px] font-medium text-emerald-200 hover:bg-emerald-500/25"
              >
                Ver no Ledger
              </Link>
            )}
            <button
              type="button"
              aria-label="Fechar aviso de decisão"
              onClick={() => setLastDecision(null)}
              className="rounded p-1 text-emerald-300 hover:bg-emerald-500/20"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}

      {/* Filter Bar */}
      <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
        <form onSubmit={handleApplyFilters} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label
                htmlFor="fraud-filter-min-score"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Risk Score Mínimo (0–100)
              </label>
              <input
                id="fraud-filter-min-score"
                type="number"
                min={0}
                max={100}
                value={draftFilters.minRiskScore}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, minRiskScore: e.target.value }))
                }
                placeholder="ex: 45"
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="fraud-filter-merchant"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Merchant ID (`UUID`)
              </label>
              <input
                id="fraud-filter-merchant"
                type="text"
                value={draftFilters.merchantId}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, merchantId: e.target.value }))
                }
                placeholder="ex: 7b92c3e0-1f42-..."
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="fraud-filter-from"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                De (Data/Hora Local)
              </label>
              <input
                id="fraud-filter-from"
                type="datetime-local"
                value={draftFilters.fromLocal}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, fromLocal: e.target.value }))
                }
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="fraud-filter-to"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Até (Data/Hora Local)
              </label>
              <input
                id="fraud-filter-to"
                type="datetime-local"
                value={draftFilters.toLocal}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, toLocal: e.target.value }))
                }
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              />
            </div>
          </div>

          {filterError && (
            <p role="alert" className="text-xs font-medium text-rose-400">
              {filterError}
            </p>
          )}

          <div className="flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              onClick={handleClearFilters}
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-300 hover:border-slate-700 hover:text-white"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Limpar filtros</span>
            </button>

            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-400"
            >
              <Filter className="h-3.5 w-3.5" />
              <span>Aplicar filtros</span>
            </button>
          </div>
        </form>
      </section>

      {/* Graceful 403 Forbidden Panel */}
      {forbiddenProblem && (
        <section
          role="status"
          className="rounded-2xl border border-amber-500/35 bg-slate-900/75 p-8 text-center"
        >
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl border border-amber-500/35 bg-amber-500/15 text-amber-300">
            <Lock className="h-6 w-6" />
          </div>
          <h3 className="mt-4 text-base font-semibold text-white">
            Permissão insuficiente para a fila de revisão (HTTP 403)
          </h3>
          <p className="mx-auto mt-1.5 max-w-lg text-xs leading-relaxed text-slate-400">
            {forbiddenProblem.detail}
          </p>
          <p className="mt-2 font-mono text-[11px] text-amber-300/80">
            correlationId: {forbiddenProblem.correlationId}
          </p>
        </section>
      )}

      {/* Non-403 RFC 7807 Error State */}
      {problem && <ProblemDetailAlert problem={problem} onRetry={() => void loadReviews()} />}

      {/* Review Queue List */}
      {!forbiddenProblem && (
        <section className="overflow-hidden rounded-2xl border border-slate-800/90 bg-slate-900/60">
          {isLoading && (
            <div className="space-y-3 p-6">
              {[1, 2, 3].map((row) => (
                <div
                  key={row}
                  className="h-32 animate-pulse rounded-2xl border border-slate-800/80 bg-slate-950/60"
                />
              ))}
            </div>
          )}

          {!isLoading && !problem && items.length === 0 && (
            <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-400">
                <ShieldCheck className="h-6 w-6" />
              </div>
              <h3 className="mt-4 text-sm font-semibold text-white">
                Fila de revisão manual limpa
              </h3>
              <p className="mt-1 max-w-md text-xs text-slate-400">
                {hasActiveFilters
                  ? 'Nenhum pagamento em MANUAL_REVIEW corresponde aos filtros aplicados.'
                  : 'Não há pagamentos aguardando análise manual no momento.'}
              </p>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="mt-4 inline-flex items-center gap-1.5 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-2 text-xs font-medium text-emerald-300 hover:bg-emerald-500/20"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  <span>Limpar filtros</span>
                </button>
              )}
            </div>
          )}

          {!isLoading && !problem && items.length > 0 && (
            <>
              <div className="divide-y divide-slate-800/80">
                {items.map((item) => {
                  const currency = parseSupportedCurrency(item.currency);
                  const dateParts = formatDateTimeParts(item.createdAt);
                  const isCircuitBreakerFallback = item.reasonCodes.includes(
                    'RISK_ENGINE_UNAVAILABLE',
                  );

                  return (
                    <article
                      key={item.paymentId}
                      className="grid gap-5 p-5 transition-colors hover:bg-slate-800/25 lg:grid-cols-12 lg:items-center"
                    >
                      {/* Payment Identity & Metadata */}
                      <div className="space-y-2.5 lg:col-span-5">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="inline-flex items-center rounded-md border border-amber-500/35 bg-amber-500/15 px-2 py-0.5 font-mono text-[11px] font-semibold text-amber-300">
                            {item.status}
                          </span>
                          <span className="rounded-md border border-slate-700 bg-slate-950 px-2 py-0.5 font-mono text-[11px] text-slate-300">
                            {item.paymentMethod}
                          </span>
                          {isCircuitBreakerFallback && (
                            <span className="inline-flex items-center gap-1 rounded-md border border-amber-400/50 bg-amber-500/20 px-2 py-0.5 text-[11px] font-semibold text-amber-200">
                              <AlertTriangle className="h-3 w-3" />
                              <span>Fallback IA</span>
                            </span>
                          )}
                        </div>

                        <div>
                          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                            <span className="font-mono text-lg font-bold text-white">
                              {formatCurrencyFromCents(item.amountCents, currency)}
                            </span>
                            <span className="text-xs text-slate-400">
                              Líquido:{' '}
                              <strong className="font-mono text-emerald-300">
                                {formatCurrencyFromCents(item.netMerchantCents, currency)}
                              </strong>{' '}
                              · Taxa:{' '}
                              <span className="font-mono text-slate-300">
                                {formatCurrencyFromCents(item.platformFeeCents, currency)}
                              </span>
                            </span>
                          </div>
                          {item.description && (
                            <p className="mt-1 text-xs text-slate-300">{item.description}</p>
                          )}
                        </div>

                        <div className="grid gap-1 text-[11px] text-slate-400 sm:grid-cols-2">
                          <p title={item.paymentId} className="truncate font-mono">
                            payment: {truncateId(item.paymentId)}
                          </p>
                          <p className="truncate font-mono">payer: {item.payerId}</p>
                          <p title={item.correlationId} className="truncate font-mono">
                            corr: {truncateId(item.correlationId)}
                          </p>
                          <p title={dateParts.utc} className="truncate">
                            {dateParts.local}
                          </p>
                        </div>
                      </div>

                      {/* Risk Score & Reason Codes */}
                      <div className="space-y-3 lg:col-span-5">
                        <RiskScoreGauge score={item.riskScore} />
                        <ReasonCodeChips reasonCodes={item.reasonCodes} />
                      </div>

                      {/* Decision Actions */}
                      <div className="flex flex-row gap-2 lg:col-span-2 lg:flex-col">
                        <button
                          type="button"
                          disabled={!canReview}
                          onClick={() => handleOpenDecisionModal(item, 'APPROVE')}
                          title={
                            canReview
                              ? 'Aprovar pagamento e liquidar no Ledger'
                              : 'Ação restrita aos perfis ANALYST e ADMIN'
                          }
                          className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-emerald-500 px-3 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
                        >
                          <CheckCircle2 className="h-4 w-4" />
                          <span>Aprovar</span>
                        </button>

                        <button
                          type="button"
                          disabled={!canReview}
                          onClick={() => handleOpenDecisionModal(item, 'DECLINE')}
                          title={
                            canReview
                              ? 'Recusar pagamento por suspeita de fraude'
                              : 'Ação restrita aos perfis ANALYST e ADMIN'
                          }
                          className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-rose-500/45 bg-rose-500/15 px-3 py-2 text-xs font-semibold text-rose-200 transition-colors hover:bg-rose-500/25 disabled:cursor-not-allowed disabled:border-slate-800 disabled:bg-slate-900 disabled:text-slate-500"
                        >
                          <XCircle className="h-4 w-4" />
                          <span>Recusar</span>
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>

              {data && (
                <PaginationControls
                  page={data.page}
                  size={data.size}
                  totalElements={data.totalElements}
                  totalPages={data.totalPages}
                  isLoading={isLoading}
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
      )}

      <ReviewDecisionModal
        item={modalItem}
        initialDecision={modalInitialDecision}
        isOpen={Boolean(modalItem)}
        onClose={() => setModalItem(null)}
        onDecisionSubmitted={handleDecisionSubmitted}
      />
    </div>
  );
}
