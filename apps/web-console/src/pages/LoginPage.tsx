import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Mail,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/httpClient';
import { authService } from '../services/authService';
import type { HealthResponse, ProblemDetail, UserRole } from '../types/api';

interface DevCredentialPreset {
  role: UserRole;
  label: string;
  email: string;
  password: string;
  description: string;
}

const DEV_CREDENTIALS: DevCredentialPreset[] = [
  {
    role: 'ADMIN',
    label: 'Admin',
    email: 'admin@ledgerpulse.dev',
    password: 'LedgerPulse@2026',
    description: 'Acesso total (contas, conciliação e revisão)',
  },
  {
    role: 'ANALYST',
    label: 'Analyst',
    email: 'analyst@ledgerpulse.dev',
    password: 'LedgerPulse@2026',
    description: 'Revisão antifraude e replay DLQ',
  },
  {
    role: 'VIEWER',
    label: 'Viewer',
    email: 'viewer@ledgerpulse.dev',
    password: 'LedgerPulse@2026',
    description: 'Leitura de dashboards, ledger e relatórios',
  },
];

interface FormErrors {
  email?: string;
  password?: string;
}

interface RedirectState {
  from?: {
    pathname?: string;
  };
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginPage() {
  const { isAuthenticated, isLoading: isSessionLoading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [apiProblem, setApiProblem] = useState<ProblemDetail | null>(null);
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthChecked, setHealthChecked] = useState(false);

  const redirectState = location.state as RedirectState | null;
  const redirectTarget = redirectState?.from?.pathname || '/';

  useEffect(() => {
    let isMounted = true;
    authService
      .getHealth()
      .then((res) => {
        if (!isMounted) return;
        setHealth(res);
        setHealthChecked(true);
      })
      .catch(() => {
        if (!isMounted) return;
        setHealth(null);
        setHealthChecked(true);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  if (!isSessionLoading && isAuthenticated) {
    return <Navigate to={redirectTarget} replace />;
  }

  const validateForm = (): FormErrors => {
    const errors: FormErrors = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = 'Informe o e-mail corporativo do operador.';
    } else if (!EMAIL_PATTERN.test(trimmedEmail)) {
      errors.email = 'Informe um endereço de e-mail válido.';
    }

    if (!password) {
      errors.password = 'Informe a senha de acesso.';
    } else if (password.length < 6) {
      errors.password = 'A senha deve possuir no mínimo 6 caracteres.';
    }

    return errors;
  };

  const handleQuickFill = (preset: DevCredentialPreset) => {
    setEmail(preset.email);
    setPassword(preset.password);
    setFormErrors({});
    setApiProblem(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setApiProblem(null);

    const validationErrors = validateForm();
    if (Object.keys(validationErrors).length > 0) {
      setFormErrors(validationErrors);
      return;
    }

    setFormErrors({});
    setIsSubmitting(true);

    try {
      await login({
        email: email.trim().toLowerCase(),
        password,
      });
      navigate(redirectTarget, { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        setApiProblem(error.problem);

        if (error.problem.errors && error.problem.errors.length > 0) {
          const mappedErrors: FormErrors = {};
          for (const fieldError of error.problem.errors) {
            if (fieldError.field === 'email') {
              mappedErrors.email = fieldError.message;
            } else if (fieldError.field === 'password') {
              mappedErrors.password = fieldError.message;
            }
          }
          setFormErrors(mappedErrors);
        }
      } else {
        setApiProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro inesperado',
          status: 500,
          detail: 'Não foi possível concluir a autenticação. Tente novamente.',
          instance: '/api/v1/auth/login',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 px-4 py-10 text-slate-100">
      {/* Subtle Ambient Background */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 left-1/2 h-96 w-[44rem] -translate-x-1/2 rounded-full bg-emerald-500/10 blur-3xl"
      />

      <div className="relative grid w-full max-w-5xl overflow-hidden rounded-3xl border border-slate-800/90 bg-slate-900/75 shadow-2xl backdrop-blur-xl lg:grid-cols-12">
        {/* Left Brand & Architecture Summary Panel */}
        <section className="flex flex-col justify-between border-b border-slate-800/80 bg-gradient-to-br from-slate-900 via-slate-900 to-emerald-950/30 p-8 lg:col-span-5 lg:border-b-0 lg:border-r lg:p-10">
          <div>
            <div className="inline-flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-500/15 ring-1 ring-emerald-500/35">
                <Activity className="h-6 w-6 text-emerald-400" />
              </div>
              <div>
                <h1 className="text-xl font-bold tracking-tight text-white">LedgerPulse</h1>
                <p className="text-xs font-medium text-emerald-400">
                  Distributed Financial Core
                </p>
              </div>
            </div>

            <h2 className="mt-8 text-2xl font-semibold leading-snug text-white">
              Console operacional para pagamentos, ledger contábil e risco em tempo real.
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-slate-400">
              Autentique-se com seu perfil JWT para auditar partidas dobradas, revisar alertas
              antifraude e monitorar entregas de webhooks.
            </p>

            <ul className="mt-8 space-y-3 text-xs text-slate-300">
              <li className="flex items-start gap-2.5">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                <span>
                  Ledger append-only de partidas dobradas com controle otimista{' '}
                  <code className="text-emerald-300">@Version</code>.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                <span>
                  Precisão monetária absoluta em centavos inteiros (
                  <code className="text-emerald-300">amountCents</code>).
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                <span>
                  Rastreamento distribuído com <code className="text-emerald-300">X-Correlation-Id</code>{' '}
                  e erros RFC 7807.
                </span>
              </li>
            </ul>
          </div>

          <div className="mt-8 rounded-2xl border border-slate-800/90 bg-slate-950/70 px-4 py-3 text-xs">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-slate-400">Status ledger-core-api</span>
              <span className="inline-flex items-center gap-1.5 font-mono text-[11px]">
                <span
                  className={[
                    'h-2 w-2 rounded-full',
                    health?.status === 'UP'
                      ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.7)]'
                      : healthChecked
                        ? 'bg-amber-400'
                        : 'bg-slate-500 animate-pulse',
                  ].join(' ')}
                />
                <span className={health?.status === 'UP' ? 'text-emerald-300' : 'text-slate-300'}>
                  {health?.status === 'UP'
                    ? `UP · v${health.version}`
                    : healthChecked
                      ? 'Aguardando backend (:8080)'
                      : 'Verificando...'}
                </span>
              </span>
            </div>
          </div>
        </section>

        {/* Right Form & Dev Credentials Panel */}
        <section className="p-8 sm:p-10 lg:col-span-7">
          <div className="mb-6">
            <h2 className="text-xl font-semibold text-white">Entrar no Console</h2>
            <p className="mt-1 text-sm text-slate-400">
              Utilize suas credenciais de operador para gerar um token JWT de sessão.
            </p>
          </div>

          {/* RFC 7807 ProblemDetail Banner */}
          {apiProblem && (
            <div
              role="alert"
              className="mb-6 rounded-2xl border border-rose-500/40 bg-rose-950/40 p-4 text-sm text-rose-200"
            >
              <div className="flex items-start gap-3">
                <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-rose-400" />
                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-semibold text-rose-100">{apiProblem.title}</p>
                    <span className="rounded bg-rose-500/20 px-2 py-0.5 font-mono text-[11px] text-rose-300">
                      HTTP {apiProblem.status}
                    </span>
                  </div>
                  <p className="text-xs leading-relaxed text-rose-200/90">{apiProblem.detail}</p>
                  <p className="truncate font-mono text-[11px] text-rose-300/80">
                    correlationId: {apiProblem.correlationId}
                  </p>
                </div>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            <div>
              <label htmlFor="email" className="mb-1.5 block text-xs font-medium text-slate-300">
                E-mail do operador
              </label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (formErrors.email) {
                      setFormErrors((prev) => ({ ...prev, email: undefined }));
                    }
                  }}
                  placeholder="admin@ledgerpulse.dev"
                  aria-invalid={Boolean(formErrors.email)}
                  className={[
                    'w-full rounded-xl border bg-slate-950/90 py-2.5 pl-10 pr-4 text-sm text-white placeholder-slate-500 transition-colors focus:outline-none focus:ring-2',
                    formErrors.email
                      ? 'border-rose-500/60 focus:border-rose-400 focus:ring-rose-500/30'
                      : 'border-slate-800 focus:border-emerald-500/70 focus:ring-emerald-500/25',
                  ].join(' ')}
                />
              </div>
              {formErrors.email && (
                <p className="mt-1.5 text-xs text-rose-400">{formErrors.email}</p>
              )}
            </div>

            <div>
              <label htmlFor="password" className="mb-1.5 block text-xs font-medium text-slate-300">
                Senha
              </label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  id="password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (formErrors.password) {
                      setFormErrors((prev) => ({ ...prev, password: undefined }));
                    }
                  }}
                  placeholder="••••••••••••"
                  aria-invalid={Boolean(formErrors.password)}
                  className={[
                    'w-full rounded-xl border bg-slate-950/90 py-2.5 pl-10 pr-11 text-sm text-white placeholder-slate-500 transition-colors focus:outline-none focus:ring-2',
                    formErrors.password
                      ? 'border-rose-500/60 focus:border-rose-400 focus:ring-rose-500/30'
                      : 'border-slate-800 focus:border-emerald-500/70 focus:ring-emerald-500/25',
                  ].join(' ')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-800 hover:text-slate-200"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {formErrors.password && (
                <p className="mt-1.5 text-xs text-rose-400">{formErrors.password}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-slate-950 shadow-lg shadow-emerald-500/20 transition-all hover:bg-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-400 focus:ring-offset-2 focus:ring-offset-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <KeyRound className="h-4 w-4" />
              <span>{isSubmitting ? 'Autenticando...' : 'Entrar no Console'}</span>
            </button>
          </form>

          {/* Discreet Dev Credentials Preset Card */}
          <div className="mt-7 rounded-2xl border border-slate-800 bg-slate-950/60 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-emerald-400" />
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                  Credenciais Dev Locais
                </h3>
              </div>
              <span className="font-mono text-[11px] text-slate-400">
                Senha: <code className="text-emerald-300">LedgerPulse@2026</code>
              </span>
            </div>

            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {DEV_CREDENTIALS.map((preset) => (
                <button
                  key={preset.role}
                  type="button"
                  onClick={() => handleQuickFill(preset)}
                  title={preset.description}
                  className="group flex flex-col items-start rounded-xl border border-slate-800/90 bg-slate-900/70 p-2.5 text-left transition-colors hover:border-emerald-500/40 hover:bg-emerald-500/10"
                >
                  <div className="flex w-full items-center justify-between">
                    <span className="font-mono text-[11px] font-semibold text-emerald-300">
                      {preset.role}
                    </span>
                    <span className="text-[10px] text-slate-500 group-hover:text-emerald-300">
                      Preencher
                    </span>
                  </div>
                  <span className="mt-1 w-full truncate text-[11px] text-slate-300">
                    {preset.email}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
