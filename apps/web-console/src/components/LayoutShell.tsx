import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Activity,
  BookOpenCheck,
  CreditCard,
  FileCheck2,
  LayoutDashboard,
  LogOut,
  Menu,
  Scale,
  ShieldAlert,
  Webhook,
  X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/authService';
import type { HealthResponse, UserRole } from '../types/api';

interface NavItem {
  to: string;
  label: string;
  subtitle: string;
  icon: typeof LayoutDashboard;
  end?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  {
    to: '/',
    label: 'Dashboard Executivo',
    subtitle: 'KPIs & Stream SSE',
    icon: LayoutDashboard,
    end: true,
  },
  {
    to: '/ledger',
    label: 'Auditoria do Ledger',
    subtitle: 'Partidas dobradas',
    icon: BookOpenCheck,
  },
  {
    to: '/accounts',
    label: 'Contas & Saldos',
    subtitle: 'Escrow, Fee & Splits',
    icon: Scale,
  },
  {
    to: '/fraud-review',
    label: 'Central Antifraude',
    subtitle: 'Fila MANUAL_REVIEW',
    icon: ShieldAlert,
  },
  {
    to: '/webhooks',
    label: 'Webhooks & DLQ',
    subtitle: 'HMAC & Replay manual',
    icon: Webhook,
  },
  {
    to: '/checkout-simulator',
    label: 'Simulador Checkout',
    subtitle: 'Cobrança, Split & Idemp.',
    icon: CreditCard,
  },
  {
    to: '/reconciliation',
    label: 'Integridade & Conciliação',
    subtitle: 'Relatórios & CSV',
    icon: FileCheck2,
  },
];

const ROLE_BADGE_STYLES: Record<UserRole, string> = {
  ADMIN: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300',
  ANALYST: 'border-sky-500/40 bg-sky-500/15 text-sky-300',
  VIEWER: 'border-slate-600 bg-slate-800 text-slate-300',
};

export function LayoutShell() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);
  const [apiHealth, setApiHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState(false);

  useEffect(() => {
    setIsMobileSidebarOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    let isActive = true;

    authService
      .getHealth()
      .then((health) => {
        if (!isActive) return;
        setApiHealth(health);
        setHealthError(false);
      })
      .catch(() => {
        if (!isActive) return;
        setApiHealth(null);
        setHealthError(true);
      });

    return () => {
      isActive = false;
    };
  }, []);

  const currentNavItem =
    NAV_ITEMS.find((item) =>
      item.end ? location.pathname === item.to : location.pathname.startsWith(item.to),
    ) ?? NAV_ITEMS[0];

  const renderNavLinks = () => (
    <nav aria-label="Navegação principal" className="flex-1 space-y-1.5 px-3 py-4">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              [
                'group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors',
                isActive
                  ? 'border border-emerald-500/30 bg-emerald-500/15 text-emerald-200 shadow-sm'
                  : 'border border-transparent text-slate-400 hover:border-slate-800 hover:bg-slate-900 hover:text-slate-100',
              ].join(' ')
            }
          >
            <Icon className="h-4 w-4 shrink-0 text-emerald-400/90" />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium leading-tight">{item.label}</p>
              <p className="truncate text-[11px] text-slate-500 group-hover:text-slate-400">
                {item.subtitle}
              </p>
            </div>
          </NavLink>
        );
      })}
    </nav>
  );

  return (
    <div className="flex min-h-screen bg-slate-950 text-slate-100">
      {/* Sidebar Desktop */}
      <aside className="hidden w-72 shrink-0 flex-col border-r border-slate-800/80 bg-slate-900/60 lg:flex">
        <div className="flex h-16 items-center gap-3 border-b border-slate-800/80 px-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/15 ring-1 ring-emerald-500/30">
            <Activity className="h-5 w-5 text-emerald-400" />
          </div>
          <div>
            <span className="block text-sm font-bold tracking-tight text-white">
              LedgerPulse
            </span>
            <span className="block text-[11px] font-medium text-emerald-400/90">
              Financial Core Console
            </span>
          </div>
        </div>

        {renderNavLinks()}

        <div className="border-t border-slate-800/80 p-4">
          <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-3 text-xs">
            <p className="font-semibold text-slate-300">Invariante Monetário</p>
            <p className="mt-1 text-slate-400">
              Todos os valores trafegam em <code className="text-emerald-400">amountCents</code>{' '}
              (inteiros) sem ponto flutuante.
            </p>
          </div>
        </div>
      </aside>

      {/* Mobile Drawer */}
      {isMobileSidebarOpen && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <button
            type="button"
            aria-label="Fechar menu lateral"
            onClick={() => setIsMobileSidebarOpen(false)}
            className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm"
          />
          <aside className="relative z-10 flex w-72 max-w-[85vw] flex-col border-r border-slate-800 bg-slate-900">
            <div className="flex h-16 items-center justify-between border-b border-slate-800 px-5">
              <div className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/15 ring-1 ring-emerald-500/30">
                  <Activity className="h-4 w-4 text-emerald-400" />
                </div>
                <span className="text-sm font-bold text-white">LedgerPulse</span>
              </div>
              <button
                type="button"
                aria-label="Fechar menu"
                onClick={() => setIsMobileSidebarOpen(false)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            {renderNavLinks()}
          </aside>
        </div>
      )}

      {/* Main Column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-slate-800/80 bg-slate-950/90 px-4 backdrop-blur-md sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              aria-label="Abrir menu de navegação"
              onClick={() => setIsMobileSidebarOpen(true)}
              className="rounded-lg border border-slate-800 bg-slate-900 p-2 text-slate-300 hover:bg-slate-800 hover:text-white lg:hidden"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="min-w-0">
              <h1 className="truncate text-base font-semibold text-white">
                {currentNavItem.label}
              </h1>
              <p className="hidden truncate text-xs text-slate-400 sm:block">
                {currentNavItem.subtitle}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Environment & Core Health Status */}
            <div className="hidden items-center gap-2 rounded-full border border-slate-800 bg-slate-900/90 px-3 py-1.5 text-xs md:flex">
              <span
                className={[
                  'h-2 w-2 rounded-full',
                  apiHealth?.status === 'UP'
                    ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.7)]'
                    : healthError
                      ? 'bg-amber-400'
                      : 'bg-slate-500 animate-pulse',
                ].join(' ')}
              />
              <span className="font-medium text-slate-300">
                {apiHealth?.status === 'UP'
                  ? `Core API UP (v${apiHealth.version})`
                  : healthError
                    ? 'Core API Offline'
                    : 'Verificando API...'}
              </span>
              <span className="rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] text-slate-400">
                DEV LOCAL
              </span>
            </div>

            {/* Operator Info */}
            {user && (
              <div className="flex items-center gap-2.5 rounded-xl border border-slate-800 bg-slate-900/70 px-3 py-1.5">
                <div className="hidden text-right sm:block">
                  <p className="text-xs font-semibold leading-tight text-slate-100">
                    {user.name}
                  </p>
                  <p className="text-[11px] leading-tight text-slate-400">{user.email}</p>
                </div>
                <span
                  className={[
                    'rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold tracking-wide',
                    ROLE_BADGE_STYLES[user.role],
                  ].join(' ')}
                >
                  {user.role}
                </span>
              </div>
            )}

            <button
              type="button"
              onClick={logout}
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-xs font-medium text-slate-300 transition-colors hover:border-rose-500/40 hover:bg-rose-500/10 hover:text-rose-200"
            >
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sair</span>
            </button>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-7xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
