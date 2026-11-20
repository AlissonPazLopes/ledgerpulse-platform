import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  Coins,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { formatCurrencyFromCents, type SupportedCurrency } from '../lib/currency';
import { ApiError } from '../lib/httpClient';
import { authService } from '../services/authService';
import type { HealthResponse } from '../types/api';

interface PreviewKpi {
  id: string;
  label: string;
  amountCents?: number;
  displayValue?: string;
  helper: string;
  icon: typeof TrendingUp;
  accentClass: string;
}

const PREVIEW_MONETARY_KPIS: PreviewKpi[] = [
  {
    id: 'tpv',
    label: 'TPV Bruto (Baseline Dev)',
    amountCents: 12845090,
    helper: 'Representado em centavos inteiros: 12845090',
    icon: TrendingUp,
    accentClass: 'bg-emerald-500/15 text-emerald-400',
  },
  {
    id: 'platform-fee',
    label: 'Receita Plataforma (PLATFORM_FEE)',
    amountCents: 321127,
    helper: 'Taxa padrão 250 bps (2,50%) em centavos inteiros',
    icon: Coins,
    accentClass: 'bg-sky-500/15 text-sky-400',
  },
  {
    id: 'escrow',
    label: 'Saldo em Escrow (Clearing)',
    amountCents: 4520000,
    helper: 'Movimentações aguardando liquidação / split',
    icon: Wallet,
    accentClass: 'bg-amber-500/15 text-amber-400',
  },
  {
    id: 'avg-ticket',
    label: 'Ticket Médio (Simulação)',
    amountCents: 15000,
    helper: 'Exemplo canônico OpenAPI: 15000 cents',
    icon: Activity,
    accentClass: 'bg-violet-500/15 text-violet-400',
  },
];

const UPCOMING_MODULES = [
  {
    title: 'Auditoria do Ledger',
    week: 'Semana 2 · Dia 13',
    to: '/ledger',
    description: 'Tabela paginada de transações contábeis e drawer DEBIT vs CREDIT.',
  },
  {
    title: 'Contas & Saldos',
    week: 'Semana 2 · Dia 14',
    to: '/accounts',
    description: 'Criação de contas MERCHANT/RECIPIENT e saldos com @Version.',
  },
  {
    title: 'Central Antifraude',
    week: 'Semana 3 · Dia 20',
    to: '/fraud-review',
    description: 'Fila MANUAL_REVIEW, score ML (0–100) e decisão com reasonCodes.',
  },
  {
    title: 'Webhooks & DLQ',
    week: 'Semana 4 · Dia 26',
    to: '/webhooks',
    description: 'Endpoints HMAC-SHA256, Webhook Sink e Replay de DLQ em 1 clique.',
  },
  {
    title: 'Simulador Checkout',
    week: 'Semana 4 · Dia 27',
    to: '/checkout-simulator',
    description: 'Cobrança com split percentual/fixo e teste de Idempotency-Key.',
  },
  {
    title: 'Integridade & Conciliação',
    week: 'Semana 5 · Dia 33',
    to: '/reconciliation',
    description: 'Conciliação contábil agendada/sob demanda e exportação CSV.',
  },
];

export function DashboardPlaceholderPage() {
  const { user } = useAuth();
  const [currency, setCurrency] = useState<SupportedCurrency>('BRL');
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [isCheckingHealth, setIsCheckingHealth] = useState(false);

  const checkHealth = useCallback(async () => {
    setIsCheckingHealth(true);
    setHealthError(null);
    try {
      const response = await authService.getHealth();
      setHealth(response);
    } catch (error) {
      setHealth(null);
      if (error instanceof ApiError) {
        setHealthError(`${error.problem.detail} (correlationId: ${error.correlationId})`);
      } else {
        setHealthError('Falha inesperada ao consultar /api/v1/health.');
      }
    } finally {
      setIsCheckingHealth(false);
    }
  }, []);

  useEffect(() => {
    void checkHealth();
  }, [checkHealth]);

  return (
    <div className="space-y-6">
      {/* Welcome & Currency Selector Banner */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <CheckCircle2 className="h-3.5 w-3.5" />
            <span>Semana 1 · Fundação & Sessão JWT Ativa</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">
            Olá, {user?.name ?? 'Operador'}
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Painel inicial do Console Web. Os KPIs abaixo demonstram a formatação segura de
            centavos inteiros antes da conexão SSE na Semana 5 (Dia 32).
          </p>
        </div>

        <div className="flex items-center gap-2 self-start rounded-xl border border-slate-800 bg-slate-950 p-1 sm:self-center">
          {(['BRL', 'USD'] as const).map((code) => (
            <button
              key={code}
              type="button"
              onClick={() => setCurrency(code)}
              className={[
                'rounded-lg px-3 py-1.5 font-mono text-xs font-semibold transition-colors',
                currency === code
                  ? 'bg-emerald-500 text-slate-950'
                  : 'text-slate-400 hover:text-slate-200',
              ].join(' ')}
            >
              {code}
            </button>
          ))}
        </div>
      </section>

      {/* Initial Monetary KPIs (Integer Cents Formatted via formatCurrencyFromCents) */}
      <section aria-label="KPIs iniciais">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {PREVIEW_MONETARY_KPIS.map((kpi) => {
            const Icon = kpi.icon;
            const formattedValue =
              kpi.amountCents !== undefined
                ? formatCurrencyFromCents(kpi.amountCents, currency)
                : kpi.displayValue ?? '-';

            return (
              <article
                key={kpi.id}
                className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5 transition-colors hover:border-slate-700"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="text-xs font-medium text-slate-400">{kpi.label}</span>
                  <div className={`flex h-9 w-9 items-center justify-center rounded-xl ${kpi.accentClass}`}>
                    <Icon className="h-4 w-4" />
                  </div>
                </div>
                <p className="mt-3 font-mono text-2xl font-bold tracking-tight text-white">
                  {formattedValue}
                </p>
                <p className="mt-2 text-xs text-slate-500">{kpi.helper}</p>
              </article>
            );
          })}
        </div>
      </section>

      {/* Operational Health & Operational Posture Row */}
      <section className="grid gap-4 lg:grid-cols-3">
        <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5 lg:col-span-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-white">
                Conectividade com ledger-core-api
              </h3>
              <p className="text-xs text-slate-400">
                Verificação ao vivo via proxy Vite (<code className="text-emerald-300">GET /api/v1/health</code>)
              </p>
            </div>
            <button
              type="button"
              onClick={() => void checkHealth()}
              disabled={isCheckingHealth}
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300 disabled:opacity-60"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isCheckingHealth ? 'animate-spin' : ''}`} />
              <span>Testar Health</span>
            </button>
          </div>

          {health ? (
            <dl className="mt-4 grid gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/5 p-4 text-xs sm:grid-cols-4">
              <div>
                <dt className="text-slate-400">Status</dt>
                <dd className="mt-1 font-mono font-semibold text-emerald-300">{health.status}</dd>
              </div>
              <div>
                <dt className="text-slate-400">Serviço</dt>
                <dd className="mt-1 font-mono text-slate-200">{health.service}</dd>
              </div>
              <div>
                <dt className="text-slate-400">Versão</dt>
                <dd className="mt-1 font-mono text-slate-200">{health.version}</dd>
              </div>
              <div>
                <dt className="text-slate-400">Timestamp UTC</dt>
                <dd className="mt-1 truncate font-mono text-slate-300">{health.timestamp}</dd>
              </div>
            </dl>
          ) : (
            <div className="mt-4 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-xs text-amber-200">
              {healthError ??
                'Backend ledger-core-api (:8080) indisponível no momento. Inicie o serviço Java para verificar o healthcheck.'}
            </div>
          )}
        </article>

        <article className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
          <div className="flex items-center gap-2 text-amber-300">
            <ShieldAlert className="h-4 w-4" />
            <h3 className="text-sm font-semibold text-white">Estado do Stream SSE</h3>
          </div>
          <p className="mt-2 text-xs leading-relaxed text-slate-400">
            O feed executivo em tempo real (<code className="text-emerald-300">/api/v1/stream/events</code>)
            será conectado via <code className="text-emerald-300">fetch</code> +{' '}
            <code className="text-emerald-300">ReadableStream</code> na Semana 5 (Dia 32), com
            header <code className="text-emerald-300">Authorization: Bearer</code>.
          </p>
          <div className="mt-4 flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/70 px-3 py-2 text-xs">
            <span className="text-slate-400">Taxa de aprovação preview</span>
            <span className="font-mono font-semibold text-emerald-300">98,4%</span>
          </div>
        </article>
      </section>

      {/* Upcoming Modules Grid */}
      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-200">Módulos Planejados no Roadmap</h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {UPCOMING_MODULES.map((mod) => (
            <Link
              key={mod.to}
              to={mod.to}
              className="group flex flex-col justify-between rounded-2xl border border-slate-800/90 bg-slate-900/50 p-4 transition-colors hover:border-emerald-500/40 hover:bg-slate-900"
            >
              <div>
                <div className="flex items-center justify-between gap-2">
                  <span className="rounded-md bg-slate-800 px-2 py-0.5 font-mono text-[11px] text-emerald-300">
                    {mod.week}
                  </span>
                  <ArrowUpRight className="h-4 w-4 text-slate-500 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-emerald-300" />
                </div>
                <h4 className="mt-3 text-sm font-semibold text-white">{mod.title}</h4>
                <p className="mt-1 text-xs leading-relaxed text-slate-400">{mod.description}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
