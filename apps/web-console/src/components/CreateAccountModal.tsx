import { useEffect, useState, type FormEvent } from 'react';
import { Building2, PlusCircle, Sparkles, X } from 'lucide-react';
import { isValidUuid } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { accountsService } from '../services/accountsService';
import type { Account, AccountType, ProblemDetail } from '../types/api';
import { ProblemDetailAlert } from './ProblemDetailAlert';

interface CreateAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: (account: Account) => void;
}

interface FormErrors {
  accountType?: string;
  holderName?: string;
  merchantId?: string;
  currency?: string;
}

const DEV_MERCHANT_ID = '7b92c3e0-1f42-4876-a1e2-901283471234';

const ACCOUNT_TYPE_OPTIONS: Array<{
  value: AccountType;
  label: string;
  requiresMerchant: boolean;
}> = [
  { value: 'RECIPIENT', label: 'RECIPIENT (Recebedor de Split)', requiresMerchant: true },
  { value: 'MERCHANT', label: 'MERCHANT (Conta do Estabelecimento)', requiresMerchant: true },
  { value: 'ESCROW', label: 'ESCROW (Clearing Sistêmico)', requiresMerchant: false },
  { value: 'PLATFORM_FEE', label: 'PLATFORM_FEE (Receita Plataforma)', requiresMerchant: false },
];

export function CreateAccountModal({
  isOpen,
  onClose,
  onCreated,
}: CreateAccountModalProps) {
  const [accountType, setAccountType] = useState<AccountType>('RECIPIENT');
  const [holderName, setHolderName] = useState('');
  const [merchantId, setMerchantId] = useState('');
  const [currency] = useState('BRL');

  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const requiresMerchant = accountType === 'MERCHANT' || accountType === 'RECIPIENT';

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
    const trimmedHolder = holderName.trim();

    if (trimmedHolder.length < 2 || trimmedHolder.length > 160) {
      errors.holderName = 'O nome do titular deve ter entre 2 e 160 caracteres.';
    }

    if (requiresMerchant) {
      if (!merchantId.trim()) {
        errors.merchantId = `merchantId é obrigatório para contas do tipo ${accountType}.`;
      } else if (!isValidUuid(merchantId)) {
        errors.merchantId = 'Informe um UUID válido para merchantId.';
      }
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
      const created = await accountsService.createAccount({
        accountType,
        holderName: holderName.trim(),
        merchantId: requiresMerchant ? merchantId.trim() : null,
        currency,
      });

      setHolderName('');
      setMerchantId('');
      setAccountType('RECIPIENT');
      onCreated(created);
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);

        if (error.problem.errors && error.problem.errors.length > 0) {
          const mappedErrors: FormErrors = {};
          for (const err of error.problem.errors) {
            if (err.field === 'holderName') mappedErrors.holderName = err.message;
            if (err.field === 'merchantId') mappedErrors.merchantId = err.message;
            if (err.field === 'accountType') mappedErrors.accountType = err.message;
            if (err.field === 'currency') mappedErrors.currency = err.message;
          }
          setFormErrors(mappedErrors);
        }
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao criar conta',
          status: 500,
          detail: 'Não foi possível criar a conta no ledger-core-api.',
          instance: '/api/v1/accounts',
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
      aria-label="Criar nova conta financeira"
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
              <Building2 className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-white">Nova Conta Financeira</h2>
              <p className="text-xs text-slate-400">POST /api/v1/accounts (exclusivo ADMIN)</p>
            </div>
          </div>

          <button
            type="button"
            onClick={resetAndClose}
            disabled={isSubmitting}
            aria-label="Fechar janela de criação de conta"
            className="rounded-xl p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="space-y-4 p-6">
          {problem && <ProblemDetailAlert problem={problem} />}

          <div>
            <label
              htmlFor="create-account-type"
              className="mb-1.5 block text-xs font-medium text-slate-300"
            >
              Tipo de Conta (`accountType`)
            </label>
            <select
              id="create-account-type"
              value={accountType}
              onChange={(e) => {
                const nextType = e.target.value as AccountType;
                setAccountType(nextType);
                setFormErrors((prev) => ({ ...prev, merchantId: undefined, accountType: undefined }));
              }}
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
            >
              {ACCOUNT_TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {formErrors.accountType && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.accountType}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="create-holder-name"
              className="mb-1.5 block text-xs font-medium text-slate-300"
            >
              Nome do Titular (`holderName`)
            </label>
            <input
              id="create-holder-name"
              type="text"
              value={holderName}
              onChange={(e) => {
                setHolderName(e.target.value);
                if (formErrors.holderName) {
                  setFormErrors((prev) => ({ ...prev, holderName: undefined }));
                }
              }}
              placeholder="ex: Partner Seller Beta"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
            />
            {formErrors.holderName && (
              <p className="mt-1 text-xs text-rose-400">{formErrors.holderName}</p>
            )}
          </div>

          {requiresMerchant ? (
            <div>
              <div className="mb-1.5 flex items-center justify-between gap-2">
                <label
                  htmlFor="create-merchant-id"
                  className="text-xs font-medium text-slate-300"
                >
                  Merchant ID (`UUID` obrigatório)
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
                id="create-merchant-id"
                type="text"
                value={merchantId}
                onChange={(e) => {
                  setMerchantId(e.target.value);
                  if (formErrors.merchantId) {
                    setFormErrors((prev) => ({ ...prev, merchantId: undefined }));
                  }
                }}
                placeholder={DEV_MERCHANT_ID}
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 font-mono text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              {formErrors.merchantId && (
                <p className="mt-1 text-xs text-rose-400">{formErrors.merchantId}</p>
              )}
            </div>
          ) : (
            <div className="rounded-xl border border-slate-800 bg-slate-950/70 px-3.5 py-2.5 text-xs text-slate-400">
              Conta sistêmica global (<code className="text-emerald-300">{accountType}</code>):{' '}
              <code className="text-slate-200">merchantId</code> será enviado como{' '}
              <code className="text-emerald-300">null</code>.
            </div>
          )}

          <div>
            <label
              htmlFor="create-currency"
              className="mb-1.5 block text-xs font-medium text-slate-300"
            >
              Moeda (`currency`)
            </label>
            <input
              id="create-currency"
              type="text"
              value={currency}
              readOnly
              className="w-full cursor-not-allowed rounded-xl border border-slate-800 bg-slate-950/60 px-3.5 py-2 font-mono text-xs text-slate-400"
            />
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
              <span>{isSubmitting ? 'Criando...' : 'Criar Conta'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
