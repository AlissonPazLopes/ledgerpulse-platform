import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Download,
  FileCheck2,
  Lock,
  Play,
  RefreshCw,
} from 'lucide-react';
import { PaginationControls } from '../components/PaginationControls';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { useAuth } from '../context/AuthContext';
import { formatCurrencyFromCents } from '../lib/currency';
import { formatDateTimeParts, truncateId } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { reconciliationService } from '../services/reconciliationService';
import type {
  PageResponse,
  ProblemDetail,
  ReconciliationReportResponse,
} from '../types/api';

export function ReconciliationPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState<PageResponse<ReconciliationReportResponse> | null>(null);
  const [latestReport, setLatestReport] = useState<ReconciliationReportResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRunning, setIsRunning] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [actionProblem, setActionProblem] = useState<ProblemDetail | null>(null);

  const loadReports = useCallback(async () => {
    setIsLoading(true);
    setProblem(null);

    try {
      const response = await reconciliationService.listReports(page, size);
      setData(response);
      if (page === 0) {
        setLatestReport(response.items[0] ?? null);
      }
    } catch (error) {
      setData(null);
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar relatórios',
          status: 500,
          detail: 'Falha inesperada ao consultar /api/v1/reconciliation/reports.',
          instance: '/api/v1/reconciliation/reports',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [page, size]);

  useEffect(() => {
    void loadReports();
  }, [loadReports]);

  const handleRunReconciliation = async () => {
    if (!isAdmin) return;
    setIsRunning(true);
    setActionProblem(null);

    try {
      const created = await reconciliationService.runReconciliation();
      setLatestReport(created);
      if (page !== 0) {
        setPage(0);
      } else {
        await loadReports();
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setActionProblem(error.problem);
      } else {
        setActionProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao executar conciliação',
          status: 500,
          detail: 'Não foi possível executar POST /api/v1/reconciliation/run.',
          instance: '/api/v1/reconciliation/run',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsRunning(false);
    }
  };

  const handleDownloadCsv = async () => {
    setIsExporting(true);
    setActionProblem(null);

    try {
      await reconciliationService.downloadCsvReport();
    } catch (error) {
      if (error instanceof ApiError) {
        setActionProblem(error.problem);
      } else {
        setActionProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao exportar CSV',
          status: 500,
          detail: 'Não foi possível baixar o arquivo ledgerpulse-reconciliation.csv.',
          instance: '/api/v1/reconciliation/export.csv',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsExporting(false);
    }
  };

  const items = data?.items ?? [];
  const activeBannerReport = latestReport ?? (page === 0 ? items[0] ?? null : null);

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <FileCheck2 className="h-3.5 w-3.5" />
            <span>Auditoria Contábil Automatizada & Sob Demanda</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">
            Integridade & Conciliação
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Confronte saldos em <code className="text-emerald-300">accounts</code>, partidas
            dobradas em <code className="text-emerald-300">ledger_entries</code> e totais de{' '}
            <code className="text-emerald-300">payments</code>.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 self-start sm:self-center">
          <button
            type="button"
            onClick={() => void loadReports()}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Atualizar</span>
          </button>

          <button
            type="button"
            onClick={() => void handleDownloadCsv()}
            disabled={isExporting}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-200 hover:border-sky-500/40 hover:text-sky-300 disabled:opacity-60"
          >
            <Download className="h-3.5 w-3.5" />
            <span>{isExporting ? 'Exportando...' : 'Exportar CSV'}</span>
          </button>

          <button
            type="button"
            disabled={!isAdmin || isRunning}
            onClick={() => void handleRunReconciliation()}
            title={
              isAdmin
                ? 'Executar verificação de conciliação contábil'
                : 'Execução manual restrita ao perfil ADMIN'
            }
            className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
          >
            <Play className="h-3.5 w-3.5" />
            <span>{isRunning ? 'Executando...' : 'Executar Conciliação'}</span>
          </button>
        </div>
      </section>

      {/* RBAC Notice for non-ADMIN */}
      {!isAdmin && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-xs text-amber-200"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
          <p>
            <strong>Permissão somente leitura ({user?.role ?? 'OPERADOR'}):</strong> você pode
            consultar relatórios e exportar CSV, mas o disparo manual via{' '}
            <code className="text-amber-100">POST /api/v1/reconciliation/run</code> é exclusivo do
            perfil <code className="text-amber-100">ADMIN</code>.
          </p>
        </div>
      )}

      {actionProblem && <ProblemDetailAlert problem={actionProblem} />}

      {/* Highlighted Integrity Banner */}
      {activeBannerReport && (
        <section
          aria-label="Status de integridade do Ledger"
          className={[
            'rounded-2xl border p-6',
            activeBannerReport.status === 'BALANCED'
              ? 'border-emerald-500/40 bg-emerald-500/10'
              : 'border-rose-500/50 bg-rose-950/35',
          ].join(' ')}
        >
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div className="flex items-start gap-3.5">
              <div
                className={[
                  'flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl',
                  activeBannerReport.status === 'BALANCED'
                    ? 'bg-emerald-500/20 text-emerald-300'
                    : 'bg-rose-500/20 text-rose-300',
                ].join(' ')}
              >
                {activeBannerReport.status === 'BALANCED' ? (
                  <CheckCircle2 className="h-6 w-6" />
                ) : (
                  <AlertTriangle className="h-6 w-6" />
                )}
              </div>
              <div>
                <h3 className="text-lg font-bold text-white">
                  {activeBannerReport.status === 'BALANCED'
                    ? 'Ledger 100% Balanceado'
                    : 'Divergência Contábil Detectada'}
                </h3>
                <p className="mt-0.5 text-xs text-slate-300">
                  Último relatório ({activeBannerReport.triggerSource}) gerado em{' '}
                  {formatDateTimeParts(activeBannerReport.createdAt).local} ·{' '}
                  {activeBannerReport.checkedAccountsCount} contas e{' '}
                  {activeBannerReport.checkedTransactionsCount} transações verificadas.
                </p>
              </div>
            </div>

            <div className="rounded-xl border border-slate-800/80 bg-slate-950/70 px-4 py-2.5 text-right">
              <p className="text-[11px] uppercase tracking-wider text-slate-400">
                Discrepância (Δ)
              </p>
              <p
                className={[
                  'font-mono text-lg font-bold',
                  activeBannerReport.status === 'BALANCED'
                    ? 'text-emerald-300'
                    : 'text-rose-300',
                ].join(' ')}
              >
                {formatCurrencyFromCents(activeBannerReport.discrepancyCents, 'BRL')}
              </p>
            </div>
          </div>

          <dl className="mt-5 grid gap-3 border-t border-slate-800/70 pt-4 text-xs sm:grid-cols-4">
            <div className="rounded-xl bg-slate-950/60 p-3">
              <dt className="text-slate-400">Pagamentos Liquidados</dt>
              <dd className="mt-1 font-mono font-semibold text-white">
                {formatCurrencyFromCents(activeBannerReport.totalSettledPaymentsCents, 'BRL')}
              </dd>
            </div>
            <div className="rounded-xl bg-slate-950/60 p-3">
              <dt className="text-slate-400">Liquidações no Ledger</dt>
              <dd className="mt-1 font-mono font-semibold text-white">
                {formatCurrencyFromCents(activeBannerReport.totalLedgerSettlementsCents, 'BRL')}
              </dd>
            </div>
            <div className="rounded-xl bg-slate-950/60 p-3">
              <dt className="text-slate-400">Estornos (Payments)</dt>
              <dd className="mt-1 font-mono font-semibold text-white">
                {formatCurrencyFromCents(activeBannerReport.totalRefundsCents, 'BRL')}
              </dd>
            </div>
            <div className="rounded-xl bg-slate-950/60 p-3">
              <dt className="text-slate-400">Estornos no Ledger</dt>
              <dd className="mt-1 font-mono font-semibold text-white">
                {formatCurrencyFromCents(activeBannerReport.totalLedgerRefundsCents, 'BRL')}
              </dd>
            </div>
          </dl>

          {activeBannerReport.status === 'DIVERGENT' && activeBannerReport.findings.length > 0 && (
            <div className="mt-4 rounded-xl border border-rose-500/40 bg-rose-950/50 p-4 text-xs text-rose-100">
              <p className="font-semibold text-rose-200">
                Achados da Auditoria ({activeBannerReport.findings.length})
              </p>
              <ul className="mt-2 list-disc space-y-1 pl-5 font-mono text-[11px] text-rose-200">
                {activeBannerReport.findings.map((finding, idx) => (
                  <li key={`${finding}-${idx}`}>{finding}</li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {problem && <ProblemDetailAlert problem={problem} onRetry={() => void loadReports()} />}

      {/* History Table */}
      <section className="overflow-hidden rounded-2xl border border-slate-800/90 bg-slate-900/60">
        {isLoading && (
          <div className="space-y-3 p-6">
            {[1, 2, 3].map((row) => (
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
              <FileCheck2 className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-sm font-semibold text-white">
              Nenhum relatório de conciliação gerado
            </h3>
            <p className="mt-1 max-w-md text-xs text-slate-400">
              {isAdmin
                ? 'Clique em "Executar Conciliação" acima para rodar a primeira auditoria contábil.'
                : 'Aguarde a execução agendada ou solicite a um administrador.'}
            </p>
          </div>
        )}

        {!isLoading && !problem && items.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-800 text-left text-xs">
                <thead className="bg-slate-950/70 text-[11px] uppercase tracking-wider text-slate-400">
                  <tr>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Data / ID
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Status
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Origem
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Escopo Verificado
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Liquidado
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Estornado
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Discrepância
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/70">
                  {items.map((report) => {
                    const dateParts = formatDateTimeParts(report.createdAt);
                    const isBalanced = report.status === 'BALANCED';

                    return (
                      <tr key={report.id} className="hover:bg-slate-800/35">
                        <td className="whitespace-nowrap px-4 py-3.5">
                          <p className="font-medium text-slate-100">{dateParts.local}</p>
                          <p title={report.id} className="mt-0.5 font-mono text-[11px] text-slate-500">
                            {truncateId(report.id)}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            className={[
                              'inline-flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold',
                              isBalanced
                                ? 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300'
                                : 'border-rose-500/40 bg-rose-500/15 text-rose-300',
                            ].join(' ')}
                          >
                            {report.status}
                          </span>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 font-mono text-slate-300">
                          {report.triggerSource}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-slate-300">
                          <p>
                            {report.checkedAccountsCount} contas ({report.divergentAccountsCount}{' '}
                            diverg.)
                          </p>
                          <p className="text-[11px] text-slate-400">
                            {report.checkedTransactionsCount} txs ({report.unbalancedTransactionsCount}{' '}
                            desbal.)
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right font-mono text-slate-200">
                          {formatCurrencyFromCents(report.totalSettledPaymentsCents, 'BRL')}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right font-mono text-slate-300">
                          {formatCurrencyFromCents(report.totalRefundsCents, 'BRL')}
                        </td>

                        <td
                          className={[
                            'whitespace-nowrap px-4 py-3.5 text-right font-mono font-bold',
                            isBalanced ? 'text-emerald-300' : 'text-rose-300',
                          ].join(' ')}
                        >
                          {formatCurrencyFromCents(report.discrepancyCents, 'BRL')}
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
    </div>
  );
}
