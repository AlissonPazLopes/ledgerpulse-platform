import { useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, ShieldAlert, X, XCircle } from 'lucide-react';
import { formatCurrencyFromCents, parseSupportedCurrency } from '../lib/currency';
import { ApiError } from '../lib/httpClient';
import { riskReviewService } from '../services/riskReviewService';
import type {
  ProblemDetail,
  RiskReviewDecisionResponse,
  RiskReviewDecisionType,
  RiskReviewItem,
} from '../types/api';
import { ProblemDetailAlert } from './ProblemDetailAlert';
import { ReasonCodeChips } from './ReasonCodeChips';
import { RiskScoreGauge } from './RiskScoreGauge';

interface ReviewDecisionModalProps {
  item: RiskReviewItem | null;
  initialDecision: RiskReviewDecisionType;
  isOpen: boolean;
  onClose: () => void;
  onDecisionSubmitted: (
    response: RiskReviewDecisionResponse,
    item: RiskReviewItem,
  ) => void;
}

const MIN_JUSTIFICATION_LENGTH = 5;
const MAX_JUSTIFICATION_LENGTH = 500;

export function ReviewDecisionModal({
  item,
  initialDecision,
  isOpen,
  onClose,
  onDecisionSubmitted,
}: ReviewDecisionModalProps) {
  const [decision, setDecision] = useState<RiskReviewDecisionType>(initialDecision);
  const [justification, setJustification] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen || !item) {
      return;
    }
    setDecision(initialDecision);
    setJustification('');
    setFieldError(null);
    setProblem(null);
  }, [isOpen, item, initialDecision]);

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

  if (!isOpen || !item) {
    return null;
  }

  const currency = parseSupportedCurrency(item.currency);
  const trimmedLength = justification.trim().length;

  const validate = (): string | null => {
    if (trimmedLength < MIN_JUSTIFICATION_LENGTH) {
      return `A justificativa deve ter ao menos ${MIN_JUSTIFICATION_LENGTH} caracteres.`;
    }
    if (trimmedLength > MAX_JUSTIFICATION_LENGTH) {
      return `A justificativa deve ter no máximo ${MAX_JUSTIFICATION_LENGTH} caracteres.`;
    }
    return null;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setProblem(null);

    const validationMessage = validate();
    if (validationMessage) {
      setFieldError(validationMessage);
      return;
    }

    setFieldError(null);
    setIsSubmitting(true);

    try {
      const response = await riskReviewService.submitRiskReviewDecision(item.paymentId, {
        decision,
        justification: justification.trim(),
      });
      onDecisionSubmitted(response, item);
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);

        const justificationProblem = error.problem.errors?.find(
          (err) => err.field === 'justification',
        );
        if (justificationProblem) {
          setFieldError(justificationProblem.message);
        }
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao registrar decisão',
          status: 500,
          detail: 'Falha inesperada ao enviar a decisão de revisão manual.',
          instance: `/api/v1/risk/reviews/${item.paymentId}/decision`,
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
      aria-label="Registrar decisão de revisão antifraude"
    >
      <button
        type="button"
        aria-label="Fechar modal"
        onClick={onClose}
        disabled={isSubmitting}
        className="fixed inset-0 bg-slate-950/85 backdrop-blur-sm"
      />

      <div className="relative z-10 max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-slate-800 bg-slate-900 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-slate-800 px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/15 text-amber-300">
              <ShieldAlert className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-white">
                Decisão de Revisão Manual
              </h2>
              <p className="font-mono text-xs text-slate-400">paymentId: {item.paymentId}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Fechar janela de decisão"
            className="rounded-xl p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="space-y-5 p-6">
          {problem && <ProblemDetailAlert problem={problem} />}

          {/* Financial & Risk Snapshot */}
          <div className="space-y-4 rounded-2xl border border-slate-800 bg-slate-950/75 p-4">
            <div className="grid gap-3 sm:grid-cols-3">
              <div>
                <p className="text-[11px] text-slate-400">Valor Bruto</p>
                <p className="mt-0.5 font-mono text-base font-bold text-white">
                  {formatCurrencyFromCents(item.amountCents, currency)}
                </p>
              </div>
              <div>
                <p className="text-[11px] text-slate-400">Taxa Plataforma</p>
                <p className="mt-0.5 font-mono text-sm font-semibold text-slate-200">
                  {formatCurrencyFromCents(item.platformFeeCents, currency)}
                </p>
              </div>
              <div>
                <p className="text-[11px] text-slate-400">Líquido Merchant</p>
                <p className="mt-0.5 font-mono text-sm font-semibold text-emerald-300">
                  {formatCurrencyFromCents(item.netMerchantCents, currency)}
                </p>
              </div>
            </div>

            <div className="grid gap-3 border-t border-slate-800/80 pt-3 sm:grid-cols-2">
              <div className="space-y-1 text-xs">
                <p className="text-slate-400">
                  Pagador: <span className="font-mono text-slate-200">{item.payerId}</span>
                </p>
                <p className="text-slate-400">
                  Método: <span className="font-mono text-slate-200">{item.paymentMethod}</span>
                </p>
                {item.description && (
                  <p className="text-slate-400">
                    Descrição: <span className="text-slate-200">{item.description}</span>
                  </p>
                )}
              </div>

              <RiskScoreGauge score={item.riskScore} compact />
            </div>

            <div className="border-t border-slate-800/80 pt-3">
              <p className="mb-2 text-[11px] font-medium uppercase tracking-wider text-slate-400">
                Sinais / Reason Codes
              </p>
              <ReasonCodeChips reasonCodes={item.reasonCodes} />
            </div>
          </div>

          {/* Decision Selector */}
          <fieldset className="space-y-2">
            <legend className="text-xs font-medium text-slate-300">
              Ação sobre o Pagamento
            </legend>
            <div className="grid gap-3 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => setDecision('APPROVE')}
                aria-pressed={decision === 'APPROVE'}
                className={[
                  'flex items-start gap-3 rounded-2xl border p-3.5 text-left transition-colors',
                  decision === 'APPROVE'
                    ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-100'
                    : 'border-slate-800 bg-slate-950/70 text-slate-300 hover:border-slate-700',
                ].join(' ')}
              >
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" />
                <div>
                  <p className="text-xs font-semibold">APPROVE · Liquidar no Ledger</p>
                  <p className="mt-0.5 text-[11px] text-slate-400">
                    Avança para SETTLED, gera partidas dobradas e publica Outbox.
                  </p>
                </div>
              </button>

              <button
                type="button"
                onClick={() => setDecision('DECLINE')}
                aria-pressed={decision === 'DECLINE'}
                className={[
                  'flex items-start gap-3 rounded-2xl border p-3.5 text-left transition-colors',
                  decision === 'DECLINE'
                    ? 'border-rose-500/60 bg-rose-500/15 text-rose-100'
                    : 'border-slate-800 bg-slate-950/70 text-slate-300 hover:border-slate-700',
                ].join(' ')}
              >
                <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-rose-400" />
                <div>
                  <p className="text-xs font-semibold">DECLINE · Recusar Cobrança</p>
                  <p className="mt-0.5 text-[11px] text-slate-400">
                    Finaliza como DECLINED sem movimentação no Ledger.
                  </p>
                </div>
              </button>
            </div>
          </fieldset>

          {/* Justification */}
          <div>
            <div className="mb-1.5 flex items-center justify-between">
              <label
                htmlFor="review-justification"
                className="text-xs font-medium text-slate-300"
              >
                Justificativa Auditável (5 a 500 caracteres)
              </label>
              <span
                className={[
                  'font-mono text-[11px]',
                  trimmedLength < MIN_JUSTIFICATION_LENGTH || trimmedLength > MAX_JUSTIFICATION_LENGTH
                    ? 'text-slate-400'
                    : 'text-emerald-300',
                ].join(' ')}
              >
                {trimmedLength} / {MAX_JUSTIFICATION_LENGTH}
              </span>
            </div>
            <textarea
              id="review-justification"
              rows={3}
              maxLength={MAX_JUSTIFICATION_LENGTH}
              value={justification}
              onChange={(e) => {
                setJustification(e.target.value);
                if (fieldError) {
                  setFieldError(null);
                }
              }}
              placeholder={
                decision === 'APPROVE'
                  ? 'Ex.: Titular confirmou a operação via contato telefônico antifraude.'
                  : 'Ex.: Divergência cadastral e velocidade anômala confirmadas pela análise.'
              }
              aria-invalid={Boolean(fieldError)}
              className={[
                'w-full rounded-xl border bg-slate-950/90 px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2',
                fieldError
                  ? 'border-rose-500/60 focus:border-rose-400 focus:ring-rose-500/25'
                  : 'border-slate-800 focus:border-emerald-500/70 focus:ring-emerald-500/20',
              ].join(' ')}
            />
            {fieldError && <p className="mt-1.5 text-xs text-rose-400">{fieldError}</p>}
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-2 border-t border-slate-800 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-xl border border-slate-800 bg-slate-950 px-4 py-2 text-xs font-medium text-slate-300 hover:text-white"
            >
              Cancelar
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              className={[
                'inline-flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-semibold transition-colors disabled:opacity-60',
                decision === 'APPROVE'
                  ? 'bg-emerald-500 text-slate-950 hover:bg-emerald-400'
                  : 'bg-rose-500 text-white hover:bg-rose-400',
              ].join(' ')}
            >
              {decision === 'APPROVE' ? (
                <CheckCircle2 className="h-4 w-4" />
              ) : (
                <XCircle className="h-4 w-4" />
              )}
              <span>
                {isSubmitting
                  ? 'Registrando decisão...'
                  : decision === 'APPROVE'
                    ? 'Confirmar Aprovação'
                    : 'Confirmar Recusa'}
              </span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
