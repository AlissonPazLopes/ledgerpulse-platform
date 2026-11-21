import { useCallback, useEffect, useState } from 'react';
import {
  Check,
  CheckCircle2,
  Copy,
  FileSpreadsheet,
  Scale,
  ShieldAlert,
  X,
} from 'lucide-react';
import { formatCurrencyFromCents, parseSupportedCurrency } from '../lib/currency';
import {
  ACCOUNT_TYPE_BADGE_MAP,
  ENTRY_DIRECTION_BADGE_MAP,
  LEDGER_REFERENCE_BADGE_MAP,
} from '../lib/domainBadges';
import { formatDateTimeParts } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { ledgerService } from '../services/ledgerService';
import type { LedgerTransactionDetail, ProblemDetail } from '../types/api';
import { ProblemDetailAlert } from './ProblemDetailAlert';

interface LedgerTransactionDrawerProps {
  transactionId: string | null;
  onClose: () => void;
}

export function LedgerTransactionDrawer({
  transactionId,
  onClose,
}: LedgerTransactionDrawerProps) {
  const [detail, setDetail] = useState<LedgerTransactionDetail | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [copiedField, setCopiedField] = useState<'id' | 'correlationId' | null>(null);

  const fetchDetail = useCallback(async (id: string) => {
    setIsLoading(true);
    setProblem(null);

    try {
      const response = await ledgerService.getLedgerTransactionById(id);
      setDetail(response);
    } catch (error) {
      setDetail(null);
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar transação',
          status: 500,
          detail: 'Não foi possível consultar os lançamentos contábeis desta transação.',
          instance: `/api/v1/ledger/transactions/${id}`,
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!transactionId) {
      setDetail(null);
      setProblem(null);
      return;
    }

    void fetchDetail(transactionId);
  }, [transactionId, fetchDetail]);

  useEffect(() => {
    if (!transactionId) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [transactionId, onClose]);

  if (!transactionId) {
    return null;
  }

  const handleCopy = async (field: 'id' | 'correlationId', value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedField(field);
      window.setTimeout(() => {
        setCopiedField((prev) => (prev === field ? null : prev));
      }, 1500);
    } catch {
      // Clipboard API may be unavailable in restricted contexts
    }
  };

  const currency = parseSupportedCurrency(detail?.currency);
  const deltaCents = detail ? detail.totalDebitCents - detail.totalCreditCents : 0;
  const isStrictlyBalanced = Boolean(detail && detail.balanced && deltaCents === 0);

  const entryDebitSum = detail
    ? detail.entries
        .filter((entry) => entry.direction === 'DEBIT')
        .reduce((acc, entry) => acc + entry.amountCents, 0)
    : 0;
  const entryCreditSum = detail
    ? detail.entries
        .filter((entry) => entry.direction === 'CREDIT')
        .reduce((acc, entry) => acc + entry.amountCents, 0)
    : 0;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end"
      role="dialog"
      aria-modal="true"
      aria-label="Detalhes da transação contábil"
    >
      <button
        type="button"
        aria-label="Fechar painel lateral"
        onClick={onClose}
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm"
      />

      <aside className="relative z-10 flex h-full w-full max-w-3xl flex-col border-l border-slate-800 bg-slate-900 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-slate-800 px-6 py-5">
          <div className="min-w-0 space-y-1">
            <div className="flex items-center gap-2 text-xs font-medium text-emerald-400">
              <FileSpreadsheet className="h-4 w-4" />
              <span>Auditoria de Partidas Dobradas (Append-Only)</span>
            </div>
            <h2 className="truncate text-lg font-semibold text-white">
              Transação Contábil
            </h2>
            <p className="truncate font-mono text-xs text-slate-400">{transactionId}</p>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar detalhes da transação"
            className="rounded-xl border border-slate-800 bg-slate-950 p-2 text-slate-400 transition-colors hover:border-slate-700 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 space-y-6 overflow-y-auto p-6">
          {isLoading && (
            <div className="space-y-4">
              <div className="h-28 animate-pulse rounded-2xl border border-slate-800 bg-slate-950/60" />
              <div className="h-48 animate-pulse rounded-2xl border border-slate-800 bg-slate-950/60" />
            </div>
          )}

          {!isLoading && problem && (
            <ProblemDetailAlert
              problem={problem}
              onRetry={() => void fetchDetail(transactionId)}
            />
          )}

          {!isLoading && detail && (
            <>
              {/* Integrity & Reference Summary */}
              <section className="rounded-2xl border border-slate-800 bg-slate-950/70 p-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <span
                    className={[
                      'inline-flex items-center rounded-lg border px-2.5 py-1 font-mono text-xs font-semibold',
                      LEDGER_REFERENCE_BADGE_MAP[detail.referenceType].badgeClass,
                    ].join(' ')}
                  >
                    {LEDGER_REFERENCE_BADGE_MAP[detail.referenceType].label}
                  </span>

                  <span
                    className={[
                      'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 font-mono text-xs font-semibold',
                      isStrictlyBalanced
                        ? 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300'
                        : 'border-rose-500/40 bg-rose-500/15 text-rose-300',
                    ].join(' ')}
                  >
                    {isStrictlyBalanced ? (
                      <CheckCircle2 className="h-3.5 w-3.5" />
                    ) : (
                      <ShieldAlert className="h-3.5 w-3.5" />
                    )}
                    <span>
                      {isStrictlyBalanced
                        ? `Balanceado · Δ ${formatCurrencyFromCents(0, currency)}`
                        : `Desbalanceado · Δ ${formatCurrencyFromCents(deltaCents, currency)}`}
                    </span>
                  </span>
                </div>

                <p className="mt-3 text-sm font-medium text-slate-100">{detail.description}</p>

                <dl className="mt-4 grid gap-3 border-t border-slate-800/80 pt-4 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-400">Reference ID</dt>
                    <dd className="mt-1 break-all font-mono text-slate-200">{detail.referenceId}</dd>
                  </div>

                  <div>
                    <dt className="text-slate-400">Correlation ID</dt>
                    <dd className="mt-1 flex items-center gap-2">
                      <span className="truncate font-mono text-slate-200">
                        {detail.correlationId}
                      </span>
                      <button
                        type="button"
                        onClick={() => void handleCopy('correlationId', detail.correlationId)}
                        title="Copiar correlationId"
                        className="inline-flex shrink-0 items-center gap-1 rounded-md border border-slate-700 bg-slate-900 px-1.5 py-0.5 text-[11px] text-slate-300 hover:border-emerald-500/40 hover:text-emerald-300"
                      >
                        {copiedField === 'correlationId' ? (
                          <Check className="h-3 w-3 text-emerald-400" />
                        ) : (
                          <Copy className="h-3 w-3" />
                        )}
                      </button>
                    </dd>
                  </div>

                  <div>
                    <dt className="text-slate-400">Criado em (Local / UTC)</dt>
                    <dd className="mt-1 text-slate-200">
                      {formatDateTimeParts(detail.createdAt).local}
                      <span className="ml-2 font-mono text-[11px] text-slate-400">
                        ({formatDateTimeParts(detail.createdAt).utc})
                      </span>
                    </dd>
                  </div>

                  <div>
                    <dt className="text-slate-400">Moeda / Entradas</dt>
                    <dd className="mt-1 font-mono text-slate-200">
                      {detail.currency} · {detail.entries.length} lançamentos
                    </dd>
                  </div>
                </dl>
              </section>

              {/* Double-Entry Mathematical Proof */}
              <section className="rounded-2xl border border-slate-800 bg-slate-950/70 p-5">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-300">
                  <Scale className="h-4 w-4 text-emerald-400" />
                  <span>Prova Contábil (Σ Débitos = Σ Créditos)</span>
                </div>

                <div className="mt-4 grid gap-3 sm:grid-cols-3">
                  <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 p-3.5">
                    <p className="text-[11px] font-medium text-amber-300">Total DEBIT</p>
                    <p className="mt-1 font-mono text-lg font-bold text-white">
                      {formatCurrencyFromCents(detail.totalDebitCents, currency)}
                    </p>
                    <p className="mt-1 font-mono text-[11px] text-slate-400">
                      Soma entradas: {formatCurrencyFromCents(entryDebitSum, currency)}
                    </p>
                  </div>

                  <div className="rounded-xl border border-emerald-500/25 bg-emerald-500/5 p-3.5">
                    <p className="text-[11px] font-medium text-emerald-300">Total CREDIT</p>
                    <p className="mt-1 font-mono text-lg font-bold text-white">
                      {formatCurrencyFromCents(detail.totalCreditCents, currency)}
                    </p>
                    <p className="mt-1 font-mono text-[11px] text-slate-400">
                      Soma entradas: {formatCurrencyFromCents(entryCreditSum, currency)}
                    </p>
                  </div>

                  <div
                    className={[
                      'rounded-xl border p-3.5',
                      isStrictlyBalanced
                        ? 'border-emerald-500/35 bg-emerald-500/10'
                        : 'border-rose-500/35 bg-rose-500/10',
                    ].join(' ')}
                  >
                    <p className="text-[11px] font-medium text-slate-300">Diferença (Δ)</p>
                    <p className="mt-1 font-mono text-lg font-bold text-white">
                      {formatCurrencyFromCents(deltaCents, currency)}
                    </p>
                    <p className="mt-1 text-[11px] text-slate-300">
                      {isStrictlyBalanced
                        ? 'Partidas dobradas consistentes'
                        : 'Divergência contábil detectada'}
                    </p>
                  </div>
                </div>
              </section>

              {/* Entries Table */}
              <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-950/70">
                <div className="border-b border-slate-800 px-5 py-3.5">
                  <h3 className="text-sm font-semibold text-white">
                    Lançamentos Individuais (`ledger_entries`)
                  </h3>
                </div>

                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-slate-800 text-left text-xs">
                    <thead className="bg-slate-900/90 text-[11px] uppercase tracking-wider text-slate-400">
                      <tr>
                        <th scope="col" className="px-4 py-3 font-semibold">
                          Conta
                        </th>
                        <th scope="col" className="px-4 py-3 font-semibold">
                          Direção
                        </th>
                        <th scope="col" className="px-4 py-3 text-right font-semibold">
                          Valor
                        </th>
                        <th scope="col" className="px-4 py-3 text-right font-semibold">
                          Saldo Após
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/70">
                      {detail.entries.map((entry) => {
                        const accountBadge = ACCOUNT_TYPE_BADGE_MAP[entry.accountType];
                        const directionBadge = ENTRY_DIRECTION_BADGE_MAP[entry.direction];
                        return (
                          <tr key={entry.id} className="hover:bg-slate-900/50">
                            <td className="px-4 py-3">
                              <span
                                className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${accountBadge.badgeClass}`}
                              >
                                {accountBadge.label}
                              </span>
                              <p className="mt-1 break-all font-mono text-[11px] text-slate-400">
                                {entry.accountId}
                              </p>
                            </td>
                            <td className="px-4 py-3">
                              <span
                                className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${directionBadge.badgeClass}`}
                              >
                                {directionBadge.label}
                              </span>
                            </td>
                            <td className="whitespace-nowrap px-4 py-3 text-right font-mono font-semibold text-white">
                              {formatCurrencyFromCents(entry.amountCents, currency)}
                            </td>
                            <td
                              className={[
                                'whitespace-nowrap px-4 py-3 text-right font-mono',
                                entry.balanceAfterCents < 0 ? 'text-amber-300' : 'text-slate-300',
                              ].join(' ')}
                            >
                              {formatCurrencyFromCents(entry.balanceAfterCents, currency)}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
