import { useEffect, useState, type FormEvent } from 'react';
import { Eye, EyeOff, PlusCircle, Sparkles, Webhook, X } from 'lucide-react';
import { isValidUuid } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { webhooksService } from '../services/webhooksService';
import type { ProblemDetail, WebhookEndpoint } from '../types/api';
import { ProblemDetailAlert } from './ProblemDetailAlert';

interface WebhookEndpointModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (endpoint: WebhookEndpoint) => void;
}

interface FormErrors {
  merchantId?: string;
  targetUrl?: string;
  secret?: string;
  description?: string;
}

const DEV_MERCHANT_ID = '7b92c3e0-1f42-4876-a1e2-901283471234';
const DEV_SINK_URL = 'http://localhost:8082/api/v1/sink/test';
const DEV_WEBHOOK_SECRET = 'whsec_ledgerpulse_dev_secret_2026';

function isValidHttpUrl(rawUrl: string): boolean {
  try {
    const parsed = new URL(rawUrl.trim());
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

export function WebhookEndpointModal({
  isOpen,
  onClose,
  onCreated,
}: WebhookEndpointModalProps) {
  const [merchantId, setMerchantId] = useState(DEV_MERCHANT_ID);
  const [targetUrl, setTargetUrl] = useState(DEV_SINK_URL);
  const [secret, setSecret] = useState('');
  const [description, setDescription] = useState('');
  const [showSecret, setShowSecret] = useState(false);

  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) {
    return null;
  }

  const resetAndClose = () => {
    setFormErrors({});
    setProblem(null);
    onClose();
  };

  const validate = (): FormErrors => {
    const errors: FormErrors = {};

    if (!merchantId.trim() || !isValidUuid(merchantId)) {
      errors.merchantId = 'Informe um UUID válido para merchantId.';
    }

    const trimmedUrl = targetUrl.trim();
    if (!trimmedUrl || trimmedUrl.length > 500 || !isValidHttpUrl(trimmedUrl)) {
      errors.targetUrl = 'Informe uma URL HTTP/HTTPS válida (máx. 500 caracteres).';
    }

    if (secret.length < 16 || secret.length > 255) {
      errors.secret = 'O segredo HMAC deve possuir entre 16 e 255 caracteres.';
    }

    if (description.trim().length > 255) {
      errors.description = 'A descrição deve ter no máximo 255 caracteres.';
    }

    return errors;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setProblem(null);

    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setFormErrors(validationErrors);
      return;
    }

    setFormErrors({});
    setIsSubmitting(true);

    try {
      const created = await webhooksService.createWebhookEndpoint({
        merchantId: merchantId.trim(),
        targetUrl: targetUrl.trim(),
        secret,
        description: description.trim() ? description.trim() : null,
      });

      setSecret('');
      setDescription('');
      onCreated(created);
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);

        if (error.problem.errors && error.problem.errors.length > 0) {
          const mappedErrors: FormErrors = {};
          for (const err of error.problem.errors) {
            if (err.field === 'merchantId') mappedErrors.merchantId = err.message;
            if (err.field === 'targetUrl') mappedErrors.targetUrl = err.message;
            if (err.field === 'secret') mappedErrors.secret = err.message;
            if (err.field === 'description') mappedErrors.description = err.message;
          }
          setFormErrors(mappedErrors);
        }
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao cadastrar endpoint',
          status: 500,
          detail: 'Não foi possível cadastrar o endpoint no webhook-dispatcher.',
          instance: '/api/v1/webhooks/endpoints',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Cadastrar endpoint webhook"
    >
      <button
        type="button"
        aria-label="Fechar modal"
        onClick={resetAndClose}
        disabled={isSubmitting}
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm"
      />

      <div className="relative z-10 w-full max-w-lg overflow-hidden rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-400">
              <Webhook className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-white">Novo Endpoint Webhook</h2>
              <p className="text-xs text-slate-400">
                POST /api/v1/webhooks/endpoints (exclusivo ADMIN)
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={resetAndClose}
            disabled={isSubmitting}
            aria-label="Fechar janela de cadastro"
            className="rounded-xl p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="space-y-4 p-6">
          {problem && <ProblemDetailAlert problem={problem} />}

          <div>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <label
                htmlFor="webhook-merchant-id"
                className="text-xs font-medium text-slate-300"
              >
                Merchant ID (`UUID`)
              </label>
              <button
                type="button"
                onClick={() => {
                  setMerchantId(DEV_MERCHANT_ID);
                  setFormErrors((prev) => ({ ...prev, merchantId: undefined }));
                }}
                className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-0.5 text-[11px] text-emerald-300 hover:border-emerald-500/40"
              >
                <Sparkles className="h-3 w-3" />
                <span>Usar Acme Dev</span>
              </button>
            </div>
            <input
              id="webhook-merchant-id"
              type="text"
              value={merchantId}
              onChange={(e) => {
                setMerchantId(e.target.value);
                if (formErrors.merchantId) {
                  setFormErrors((prev) => ({ ...prev, merchantId: undefined }));
                }
              }}
              placeholder={DEV_MERCHANT_ID}
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 font-mono text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
            {formErrors.merchantId && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.merchantId}</p>
            )}
          </div>

          <div>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <label htmlFor="webhook-target-url" className="text-xs font-medium text-slate-300">
                URL de Destino (`targetUrl`)
              </label>
              <button
                type="button"
                onClick={() => {
                  setTargetUrl(DEV_SINK_URL);
                  setFormErrors((prev) => ({ ...prev, targetUrl: undefined }));
                }}
                className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-0.5 text-[11px] text-sky-300 hover:border-sky-500/40"
              >
                <span>Usar Sink Local</span>
              </button>
            </div>
            <input
              id="webhook-target-url"
              type="url"
              value={targetUrl}
              onChange={(e) => {
                setTargetUrl(e.target.value);
                if (formErrors.targetUrl) {
                  setFormErrors((prev) => ({ ...prev, targetUrl: undefined }));
                }
              }}
              placeholder={DEV_SINK_URL}
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 font-mono text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
            {formErrors.targetUrl && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.targetUrl}</p>
            )}
          </div>

          <div>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <label htmlFor="webhook-secret" className="text-xs font-medium text-slate-300">
                Segredo HMAC-SHA256 (`secret`, mín. 16 chars)
              </label>
              <button
                type="button"
                onClick={() => {
                  setSecret(DEV_WEBHOOK_SECRET);
                  setFormErrors((prev) => ({ ...prev, secret: undefined }));
                }}
                className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-0.5 text-[11px] text-emerald-300 hover:border-emerald-500/40"
              >
                <span>Preencher Secret Dev</span>
              </button>
            </div>
            <div className="relative">
              <input
                id="webhook-secret"
                type={showSecret ? 'text' : 'password'}
                value={secret}
                onChange={(e) => {
                  setSecret(e.target.value);
                  if (formErrors.secret) {
                    setFormErrors((prev) => ({ ...prev, secret: undefined }));
                  }
                }}
                placeholder="whsec_..."
                className="w-full rounded-xl border border-slate-800 bg-slate-950 py-2 pl-3.5 pr-10 font-mono text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              <button
                type="button"
                onClick={() => setShowSecret((prev) => !prev)}
                aria-label={showSecret ? 'Ocultar secret' : 'Mostrar secret'}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-200"
              >
                {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <p className="mt-1 text-[11px] text-slate-500">
              Por segurança, a API nunca retorna o secret em texto puro após o cadastro (apenas{' '}
              <code>secretPreview</code> mascarado).
            </p>
            {formErrors.secret && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.secret}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="webhook-description"
              className="mb-1.5 block text-xs font-medium text-slate-300"
            >
              Descrição (opcional)
            </label>
            <input
              id="webhook-description"
              type="text"
              maxLength={255}
              value={description}
              onChange={(e) => {
                setDescription(e.target.value);
                if (formErrors.description) {
                  setFormErrors((prev) => ({ ...prev, description: undefined }));
                }
              }}
              placeholder="ex: Merchant Webhook Sink Local"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
            {formErrors.description && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.description}</p>
            )}
          </div>

          <div className="flex items-center justify-end gap-2 border-t border-slate-800 pt-4">
            <button
              type="button"
              onClick={resetAndClose}
              disabled={isSubmitting}
              className="rounded-xl border border-slate-800 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-300 hover:text-white"
            >
              Cancelar
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 transition-colors hover:bg-emerald-400 disabled:opacity-60"
            >
              <PlusCircle className="h-4 w-4" />
              <span>{isSubmitting ? 'Cadastrando...' : 'Cadastrar Endpoint'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
