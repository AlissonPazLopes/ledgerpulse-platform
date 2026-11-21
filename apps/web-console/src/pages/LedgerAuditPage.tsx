import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  BookOpenCheck,
  CheckCircle2,
  Eye,
  Filter,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
} from 'lucide-react';
import { LedgerTransactionDrawer } from '../components/LedgerTransactionDrawer';
import { PaginationControls } from '../components/PaginationControls';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { formatCurrencyFromCents, parseSupportedCurrency } from '../lib/currency';
import { LEDGER_REFERENCE_BADGE_MAP } from '../lib/domainBadges';
import {
  formatDateTimeParts,
  isValidUuid,
  toIsoUtcFromLocalInput,
  truncateId,
} from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { ledgerService } from '../services/ledgerService';
import type {
  LedgerReferenceType,
  LedgerTransactionSummary,
  PageResponse,
  ProblemDetail,
} from '../types/api';

interface LedgerFiltersState {
  referenceType: LedgerReferenceType | '';
  accountId: string;
  referenceId: string;
  fromLocal: string;
  toLocal: string;
}

const INITIAL_FILTERS: LedgerFiltersState = {
  referenceType: '',
  accountId: '',
  referenceId: '',
  fromLocal: '',
  toLocal: '',
};

const REFERENCE_TYPE_OPTIONS: Array<{ value: LedgerReferenceType | ''; label: string }> = [
  { value: '', label: 'Todas as origens' },
  { value: 'PAYMENT_SETTLEMENT', label: 'PAYMENT_SETTLEMENT' },
  { value: 'PAYMENT_REFUND', label: 'PAYMENT_REFUND' },
  { value: 'MANUAL_ADJUSTMENT', label: 'MANUAL_ADJUSTMENT' },
];

export function LedgerAuditPage() {
  const [draftFilters, setDraftFilters] = useState<LedgerFiltersState>(INITIAL_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<LedgerFiltersState>(INITIAL_FILTERS);
  const [filterError, setFilterError] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState<PageResponse<LedgerTransactionSummary> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);

  const loadTransactions = useCallback(async () => {
    setIsLoading(true);
    setProblem(null);

    try {
      const response = await ledgerService.listLedgerTransactions({
        page,
        size,
        referenceType: appliedFilters.referenceType,
        accountId: appliedFilters.accountId,
        referenceId: appliedFilters.referenceId,
        from: toIsoUtcFromLocalInput(appliedFilters.fromLocal),
        to: toIsoUtcFromLocalInput(appliedFilters.toLocal),
      });
      setData(response);
    } catch (error) {
      setData(null);
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar auditoria do Ledger',
          status: 500,
          detail: 'Falha inesperada ao consultar /api/v1/ledger/transactions.',
          instance: '/api/v1/ledger/transactions',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [page, size, appliedFilters]);

  useEffect(() => {
    void loadTransactions();
  }, [loadTransactions]);

  const handleApplyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFilterError(null);

    if (draftFilters.accountId.trim() && !isValidUuid(draftFilters.accountId)) {
      setFilterError('O campo Account ID deve conter um UUID válido.');
      return;
    }

    if (draftFilters.referenceId.trim() && !isValidUuid(draftFilters.referenceId)) {
      setFilterError('O campo Reference ID deve conter um UUID válido.');
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

  const hasActiveFilters = Boolean(
    appliedFilters.referenceType ||
      appliedFilters.accountId.trim() ||
      appliedFilters.referenceId.trim() ||
      appliedFilters.fromLocal ||
      appliedFilters.toLocal,
  );

  const items = data?.items ?? [];

  return (
    <div className="space-y-6">
      {/* Header Summary */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <BookOpenCheck className="h-3.5 w-3.5" />
            <span>Partidas Dobradas Imutáveis · Append-Only</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">Auditoria do Ledger</h2>
          <p className="mt-1 text-sm text-slate-400">
            Inspecione transações contábeis e abra qualquer linha para auditar os lançamentos{' '}
            <code className="text-emerald-300">DEBIT</code> vs{' '}
            <code className="text-emerald-300">CREDIT</code>.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadTransactions()}
          disabled={isLoading}
          className="inline-flex items-center gap-2 self-start rounded-xl border border-slate-700 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60 sm:self-center"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Atualizar Ledger</span>
        </button>
      </section>

      {/* Filter Bar */}
      <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
        <form onSubmit={handleApplyFilters} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <div>
              <label
                htmlFor="filter-reference-type"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Tipo de Referência
              </label>
              <select
                id="filter-reference-type"
                value={draftFilters.referenceType}
                onChange={(e) =>
                  setDraftFilters((prev) => ({
                    ...prev,
                    referenceType: e.target.value as LedgerReferenceType | '',
                  }))
                }
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              >
                {REFERENCE_TYPE_OPTIONS.map((opt) => (
                  <option key={opt.label} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label
                htmlFor="filter-account-id"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Account ID (UUID)
              </label>
              <input
                id="filter-account-id"
                type="text"
                value={draftFilters.accountId}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, accountId: e.target.value }))
                }
                placeholder="ex: 7b92c3e0-1f42-..."
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="filter-reference-id"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Reference ID (UUID)
              </label>
              <input
                id="filter-reference-id"
                type="text"
                value={draftFilters.referenceId}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, referenceId: e.target.value }))
                }
                placeholder="ID do pagamento..."
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="filter-from"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                De (Data/Hora Local)
              </label>
              <input
                id="filter-from"
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
                htmlFor="filter-to"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Até (Data/Hora Local)
              </label>
              <input
                id="filter-to"
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
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-300 transition-colors hover:border-slate-700 hover:text-white"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Limpar filtros</span>
            </button>

            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400"
            >
              <Filter className="h-3.5 w-3.5" />
              <span>Aplicar filtros</span>
            </button>
          </div>
        </form>
      </section>

      {/* Error State (RFC 7807 ProblemDetail) */}
      {problem && (
        <ProblemDetailAlert problem={problem} onRetry={() => void loadTransactions()} />
      )}

      {/* Table Section */}
      <section className="overflow-hidden rounded-2xl border border-slate-800/90 bg-slate-900/60">
        {isLoading && (
          <div className="space-y-3 p-6">
            {[1, 2, 3, 4].map((row) => (
              <div
                key={row}
                className="h-14 animate-pulse rounded-xl border border-slate-800/80 bg-slate-950/60"
              />
            ))}
          </div>
        )}

        {!isLoading && !problem && items.length === 0 && (
          <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-800 bg-slate-950 text-slate-400">
              <BookOpenCheck className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-sm font-semibold text-white">
              Nenhuma transação contábil encontrada
            </h3>
            <p className="mt-1 max-w-md text-xs text-slate-400">
              {hasActiveFilters
                ? 'Nenhuma movimentação corresponde aos filtros informados. Tente ajustar ou limpar os critérios de busca.'
                : 'Ainda não existem transações registradas no Ledger. Liquidações de pagamentos aparecem aqui automaticamente.'}
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
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-800 text-left text-xs">
                <thead className="bg-slate-950/70 text-[11px] uppercase tracking-wider text-slate-400">
                  <tr>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Data (Local / UTC)
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Origem
                    </th>
                    <th scope="col" className="px-4 py-3 font-semibold">
                      Descrição
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Total Débitos
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Total Créditos
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Integridade
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Partidas
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/70">
                  {items.map((tx) => {
                    const currency = parseSupportedCurrency(tx.currency);
                    const dateParts = formatDateTimeParts(tx.createdAt);
                    const refBadge = LEDGER_REFERENCE_BADGE_MAP[tx.referenceType];
                    const deltaCents = tx.totalDebitCents - tx.totalCreditCents;
                    const isBalanced = tx.balanced && deltaCents === 0;

                    return (
                      <tr
                        key={tx.id}
                        onClick={() => setSelectedTransactionId(tx.id)}
                        className="cursor-pointer transition-colors hover:bg-slate-800/45"
                      >
                        <td className="whitespace-nowrap px-4 py-3.5">
                          <p className="font-medium text-slate-200">{dateParts.local}</p>
                          <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                            {dateParts.utc}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${refBadge.badgeClass}`}
                          >
                            {refBadge.label}
                          </span>
                          <p
                            title={tx.referenceId}
                            className="mt-1 font-mono text-[11px] text-slate-400"
                          >
                            ref: {truncateId(tx.referenceId)}
                          </p>
                        </td>

                        <td className="max-w-xs px-4 py-3.5">
                          <p className="truncate font-medium text-slate-100" title={tx.description}>
                            {tx.description}
                          </p>
                          <p
                            title={tx.correlationId}
                            className="mt-0.5 truncate font-mono text-[11px] text-slate-500"
                          >
                            corr: {truncateId(tx.correlationId)}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right font-mono font-semibold text-amber-200">
                          {formatCurrencyFromCents(tx.totalDebitCents, currency)}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right font-mono font-semibold text-emerald-200">
                          {formatCurrencyFromCents(tx.totalCreditCents, currency)}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            className={[
                              'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[11px] font-semibold',
                              isBalanced
                                ? 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300'
                                : 'border-rose-500/35 bg-rose-500/15 text-rose-300',
                            ].join(' ')}
                          >
                            {isBalanced ? (
                              <CheckCircle2 className="h-3.5 w-3.5" />
                            ) : (
                              <ShieldAlert className="h-3.5 w-3.5" />
                            )}
                            <span>
                              {isBalanced
                                ? `Balanceado · Δ ${formatCurrencyFromCents(0, currency)}`
                                : `Δ ${formatCurrencyFromCents(deltaCents, currency)}`}
                            </span>
                          </span>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right">
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              setSelectedTransactionId(tx.id);
                            }}
                            aria-label={`Inspecionar partidas da transação ${tx.id}`}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300"
                          >
                            <Eye className="h-3.5 w-3.5" />
                            <span>Detalhes</span>
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
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

      <LedgerTransactionDrawer
        transactionId={selectedTransactionId}
        onClose={() => setSelectedTransactionId(null)}
      />
    </div>
  );
}
