import { Link } from 'react-router-dom';
import { ArrowLeft, CalendarClock, Lock, Terminal } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import type { UserRole } from '../types/api';

export interface ModulePlaceholderProps {
  title: string;
  subtitle: string;
  schedule: string;
  endpoints: string[];
  allowedRoles: UserRole[];
  highlights: string[];
}

export function ModulePlaceholderPage({
  title,
  subtitle,
  schedule,
  endpoints,
  allowedRoles,
  highlights,
}: ModulePlaceholderProps) {
  const { user } = useAuth();
  const hasPermission = user ? allowedRoles.includes(user.role) : false;

  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="inline-flex items-center gap-2 rounded-full border border-slate-700 bg-slate-950 px-3 py-1 font-mono text-xs text-emerald-300">
            <CalendarClock className="h-3.5 w-3.5" />
            <span>{schedule}</span>
          </div>

          <span
            className={[
              'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium',
              hasPermission
                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                : 'border-amber-500/30 bg-amber-500/10 text-amber-300',
            ].join(' ')}
          >
            <Lock className="h-3.5 w-3.5" />
            <span>
              {hasPermission
                ? `Seu papel (${user?.role}) possui acesso previsto`
                : `Ações mutáveis restritas a: ${allowedRoles.join(', ')}`}
            </span>
          </span>
        </div>

        <h2 className="mt-4 text-xl font-semibold text-white">{title}</h2>
        <p className="mt-1.5 max-w-3xl text-sm leading-relaxed text-slate-400">{subtitle}</p>

        <div className="mt-6 grid gap-4 md:grid-cols-2">
          <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-300">
              <Terminal className="h-4 w-4 text-emerald-400" />
              <span>Contratos REST Previstos</span>
            </div>
            <ul className="mt-3 space-y-2">
              {endpoints.map((endpoint) => (
                <li
                  key={endpoint}
                  className="rounded-lg border border-slate-800/80 bg-slate-900/70 px-3 py-2 font-mono text-xs text-emerald-300"
                >
                  {endpoint}
                </li>
              ))}
            </ul>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
              Entregas Planejadas nesta Tela
            </h3>
            <ul className="mt-3 list-disc space-y-2 pl-4 text-xs leading-relaxed text-slate-400">
              {highlights.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-6">
          <Link
            to="/"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-200 transition-colors hover:border-emerald-500/40 hover:text-emerald-300"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Voltar para o Dashboard Executivo</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
