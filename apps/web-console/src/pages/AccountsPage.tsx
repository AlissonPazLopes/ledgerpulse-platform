import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  CheckCircle2,
  Filter,
  Lock,
  PlusCircle,
  RefreshCw,
  RotateCcw,
  Scale,
  X,
} from 'lucide-react';
import { CreateAccountModal } from '../components/CreateAccountModal';
import { PaginationControls } from '../components/PaginationControls';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { useAuth } from '../context/AuthContext';
import { formatCurrencyFromCents, parseSupportedCurrency } from '../lib/currency';
import { ACCOUNT_STATUS_BADGE_MAP, ACCOUNT_TYPE_BADGE_MAP } from '../lib/domainBadges';
import { formatDateTimeParts, isValidUuid, truncateId } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { accountsService } from '../services/accountsService';
import type { Account, AccountType, PageResponse, ProblemDetail } from '../types/api';

interface AccountsFiltersState {
  accountType: AccountType | '';
  merchantId: string;
}

const INITIAL_FILTERS: AccountsFiltersState = {
  accountType: '',
  merchantId: '',
};

const ACCOUNT_TYPE_FILTER_OPTIONS: Array<{ value: AccountType | ''; label: string }> = [
  { value: '', label: 'Todos os tipos' },
  { value: 'MERCHANT', label: 'MERCHANT' },
  { value: 'RECIPIENT', label: 'RECIPIENT' },
  { value: 'ESCROW', label: 'ESCROW' },
  { value: 'PLATFORM_FEE', label: 'PLATFORM_FEE' },
];

export function AccountsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [draftFilters, setDraftFilters] = useState<AccountsFiltersState>(INITIAL_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<AccountsFiltersState>(INITIAL_FILTERS);
  const [filterError, setFilterError] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState<PageResponse<Account> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createdBannerAccount, setCreatedBannerAccount] = useState<Account | null>(null);
  const [inspectedAccount, setInspectedAccount] = useState<Account | null>(null);
  const [isInspectingId, setIsInspectingId] = useState<string | null>(null);

  const loadAccounts = useCallback(async () => {
    setIsLoading(true);
    setProblem(null);

    try {
      const response = await accountsService.listAccounts({
        page,
        size,
        accountType: appliedFilters.accountType,
        merchantId: appliedFilters.merchantId,
      });
      setData(response);
    } catch (error) {
      setData(null);
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao carregar contas',
          status: 500,
          detail: 'Falha inesperada ao consultar /api/v1/accounts.',
          instance: '/api/v1/accounts',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [page, size, appliedFilters]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  const handleApplyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFilterError(null);

    if (draftFilters.merchantId.trim() && !isValidUuid(draftFilters.merchantId)) {
      setFilterError('O campo Merchant ID deve conter um UUID válido.');
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

  const handleInspectAccount = async (accountId: string) => {
    setIsInspectingId(accountId);
    setProblem(null);

    try {
      const freshAccount = await accountsService.getAccountById(accountId);
      setInspectedAccount(freshAccount);
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);
      }
    } finally {
      setIsInspectingId(null);
    }
  };

  const handleAccountCreated = (account: Account) => {
    setIsCreateModalOpen(false);
    setCreatedBannerAccount(account);
    if (page !== 0) {
      setPage(0);
    } else {
      void loadAccounts();
    }
  };

  const hasActiveFilters = Boolean(
    appliedFilters.accountType || appliedFilters.merchantId.trim(),
  );

  const items = data?.items ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <Scale className="h-3.5 w-3.5" />
            <span>Optimistic Locking · @Version</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">Contas & Saldos</h2>
          <p className="mt-1 text-sm text-slate-400">
            Acompanhe saldos disponíveis, pendentes e versão de concorrência das contas de
            merchants, recebedores e contas sistêmicas.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 self-start sm:self-center">
          <button
            type="button"
            onClick={() => void loadAccounts()}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Atualizar</span>
          </button>

          <button
            type="button"
            disabled={!isAdmin}
            onClick={() => setIsCreateModalOpen(true)}
            title={
              isAdmin
                ? 'Criar nova conta financeira'
                : 'Apenas operadores com role ADMIN podem criar contas'
            }
            className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-800 disabled:text-slate-500"
          >
            <PlusCircle className="h-4 w-4" />
            <span>Nova Conta</span>
          </button>
        </div>
      </section>

      {/* RBAC Read-Only Notice for ANALYST / VIEWER */}
      {!isAdmin && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-2xl border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-xs text-amber-200"
        >
          <Lock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
          <p>
            <strong>Permissão somente leitura ({user?.role ?? 'OPERADOR'}):</strong> você pode
            consultar todas as contas e saldos, porém a criação de contas via{' '}
            <code className="text-amber-100">POST /api/v1/accounts</code> é exclusiva do perfil{' '}
            <code className="text-amber-100">ADMIN</code>.
          </p>
        </div>
      )}

      {/* Creation Success Feedback */}
      {createdBannerAccount && (
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-200"
        >
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
            <span>
              Conta <strong>{createdBannerAccount.holderName}</strong> (
              <code className="text-emerald-100">{createdBannerAccount.accountType}</code> ·{' '}
              <code className="text-emerald-100">v{createdBannerAccount.version}</code>) criada com
              sucesso.
            </span>
          </div>
          <button
            type="button"
            aria-label="Fechar aviso de criação"
            onClick={() => setCreatedBannerAccount(null)}
            className="rounded p-1 text-emerald-300 hover:bg-emerald-500/20"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Inspected Account Live Snapshot */}
      {inspectedAccount && (
        <section className="rounded-2xl border border-sky-500/35 bg-sky-950/20 p-4 text-xs">
          <div className="flex items-center justify-between gap-3">
            <p className="font-semibold text-sky-200">
              Consulta Pontual (`GET /api/v1/accounts/{truncateId(inspectedAccount.id)}`)
            </p>
            <button
              type="button"
              onClick={() => setInspectedAccount(null)}
              className="rounded p-1 text-slate-400 hover:text-white"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <dl className="mt-3 grid gap-3 sm:grid-cols-4">
            <div>
              <dt className="text-slate-400">Titular</dt>
              <dd className="mt-0.5 font-medium text-white">{inspectedAccount.holderName}</dd>
            </div>
            <div>
              <dt className="text-slate-400">Versão (@Version)</dt>
              <dd className="mt-0.5 font-mono text-emerald-300">v{inspectedAccount.version}</dd>
            </div>
            <div>
              <dt className="text-slate-400">Saldo Disponível</dt>
              <dd className="mt-0.5 font-mono text-white">
                {formatCurrencyFromCents(
                  inspectedAccount.availableBalanceCents,
                  parseSupportedCurrency(inspectedAccount.currency),
                )}
              </dd>
            </div>
            <div>
              <dt className="text-slate-400">Atualizada em</dt>
              <dd className="mt-0.5 text-slate-300">
                {formatDateTimeParts(inspectedAccount.updatedAt).local}
              </dd>
            </div>
          </dl>
        </section>
      )}

      {/* Filter Bar */}
      <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
        <form onSubmit={handleApplyFilters} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label
                htmlFor="accounts-filter-type"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Tipo de Conta (`accountType`)
              </label>
              <select
                id="accounts-filter-type"
                value={draftFilters.accountType}
                onChange={(e) =>
                  setDraftFilters((prev) => ({
                    ...prev,
                    accountType: e.target.value as AccountType | '',
                  }))
                }
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              >
                {ACCOUNT_TYPE_FILTER_OPTIONS.map((opt) => (
                  <option key={opt.label} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label
                htmlFor="accounts-filter-merchant"
                className="mb-1.5 block text-xs font-medium text-slate-300"
              >
                Merchant ID (`UUID`)
              </label>
              <input
                id="accounts-filter-merchant"
                type="text"
                value={draftFilters.merchantId}
                onChange={(e) =>
                  setDraftFilters((prev) => ({ ...prev, merchantId: e.target.value }))
                }
                placeholder="ex: 7b92c3e0-1f42-4876-a1e2-901283471234"
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
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
              <span>Limpar</span>
            </button>

            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-400"
            >
              <Filter className="h-3.5 w-3.5" />
              <span>Filtrar Contas</span>
            </button>
          </div>
        </form>
      </section>

      {/* Error State */}
      {problem && <ProblemDetailAlert problem={problem} onRetry={() => void loadAccounts()} />}

      {/* Accounts Table */}
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
              <Scale className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-sm font-semibold text-white">
              Nenhuma conta encontrada
            </h3>
            <p className="mt-1 max-w-md text-xs text-slate-400">
              {hasActiveFilters
                ? 'Nenhuma conta corresponde aos filtros selecionados.'
                : 'Nenhuma conta cadastrada no momento.'}
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
                      Titular / Identificação
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Tipo
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Saldo Disponível
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Saldo Bloqueado
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Status
                    </th>
                    <th scope="col" className="px-4 py-3.5 font-semibold">
                      Concorrência
                    </th>
                    <th scope="col" className="px-4 py-3.5 text-right font-semibold">
                      Ação
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/70">
                  {items.map((account) => {
                    const currency = parseSupportedCurrency(account.currency);
                    const typeBadge = ACCOUNT_TYPE_BADGE_MAP[account.accountType];
                    const statusBadge = ACCOUNT_STATUS_BADGE_MAP[account.status];
                    const isNegativeEscrow =
                      account.accountType === 'ESCROW' && account.availableBalanceCents < 0;

                    return (
                      <tr key={account.id} className="hover:bg-slate-800/40">
                        <td className="px-4 py-3.5">
                          <p className="font-semibold text-white">{account.holderName}</p>
                          <p
                            title={account.id}
                            className="mt-0.5 font-mono text-[11px] text-slate-400"
                          >
                            id: {truncateId(account.id)}
                          </p>
                          <p className="mt-0.5 font-mono text-[11px] text-slate-500">
                            {account.merchantId
                              ? `merchant: ${truncateId(account.merchantId)}`
                              : 'Sistêmica (Global)'}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${typeBadge.badgeClass}`}
                          >
                            {typeBadge.label}
                          </span>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right">
                          <span
                            className={[
                              'font-mono text-sm font-bold',
                              account.availableBalanceCents < 0
                                ? 'text-amber-300'
                                : 'text-emerald-300',
                            ].join(' ')}
                          >
                            {formatCurrencyFromCents(account.availableBalanceCents, currency)}
                          </span>
                          {isNegativeEscrow && (
                            <p className="mt-0.5 text-[10px] text-amber-400/80">
                              Conta de origem / clearing
                            </p>
                          )}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right font-mono text-slate-300">
                          {formatCurrencyFromCents(account.pendingBalanceCents, currency)}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${statusBadge.badgeClass}`}
                          >
                            {statusBadge.label}
                          </span>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5">
                          <span
                            title="Versão de Optimistic Locking (@Version)"
                            className="inline-flex rounded-md border border-slate-700 bg-slate-950 px-2 py-0.5 font-mono text-[11px] font-semibold text-slate-200"
                          >
                            v{account.version}
                          </span>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3.5 text-right">
                          <button
                            type="button"
                            disabled={isInspectingId === account.id}
                            onClick={() => void handleInspectAccount(account.id)}
                            className="rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1 text-[11px] font-medium text-slate-300 transition-colors hover:border-sky-500/40 hover:text-sky-200 disabled:opacity-50"
                          >
                            {isInspectingId === account.id ? 'Consultando...' : 'Consultar'}
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

      <CreateAccountModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onCreated={handleAccountCreated}
      />
    </div>
  );
}
