import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  Clock,
  CreditCard,
  KeyRound,
  Plus,
  RefreshCw,
  Send,
  ShieldAlert,
  Sparkles,
  Trash2,
  XCircle,
} from 'lucide-react';
import { ProblemDetailAlert } from '../components/ProblemDetailAlert';
import { ReasonCodeChips } from '../components/ReasonCodeChips';
import {
  computeBpsAmountCents,
  formatBpsPercent,
  formatCurrencyFromCents,
  parseBrlInputToCents,
  parseSupportedCurrency,
} from '../lib/currency';
import { PAYMENT_STATUS_BADGE_MAP } from '../lib/domainBadges';
import { isValidUuid, truncateId } from '../lib/formatters';
import { ApiError } from '../lib/httpClient';
import { accountsService } from '../services/accountsService';
import { paymentsService, type CreatePaymentResult } from '../services/paymentsService';
import type {
  Account,
  CreatePaymentSplitInput,
  PaymentMethod,
  ProblemDetail,
  SplitType,
} from '../types/api';

interface SplitDraftRow {
  rowId: string;
  recipientAccountId: string;
  splitType: SplitType;
  percentageBpsInput: string;
  fixedAmountInput: string;
}

const DEV_MERCHANT_ID = '7b92c3e0-1f42-4876-a1e2-901283471234';
const PLATFORM_FEE_BPS = 250; // 2.50% conforme seed padrão

function createRandomKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `sim-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

export function CheckoutSimulatorPage() {
  // Recipient accounts loaded from core API
  const [recipientAccounts, setRecipientAccounts] = useState<Account[]>([]);
  const [recipientsLoading, setRecipientsLoading] = useState(true);
  const [recipientsError, setRecipientsError] = useState<string | null>(null);

  // Checkout Form state
  const [merchantId, setMerchantId] = useState(DEV_MERCHANT_ID);
  const [amountInput, setAmountInput] = useState('150,00');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CARD');
  const [payerId, setPayerId] = useState('payer-98765');
  const [description, setDescription] = useState('Pedido #4021');
  const [splitRows, setSplitRows] = useState<SplitDraftRow[]>([]);

  // Idempotency control
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => createRandomKey());
  const [autoRotateIdempotencyKey, setAutoRotateIdempotencyKey] = useState(true);

  // Submission & validation state
  const [formError, setFormError] = useState<string | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<CreatePaymentResult | null>(null);

  const loadRecipientAccounts = useCallback(async () => {
    setRecipientsLoading(true);
    setRecipientsError(null);

    try {
      const response = await accountsService.listAccounts({
        accountType: 'RECIPIENT',
        page: 0,
        size: 100,
      });
      setRecipientAccounts(response.items);
    } catch (error) {
      setRecipientAccounts([]);
      setRecipientsError(
        error instanceof ApiError
          ? error.problem.detail
          : 'Não foi possível carregar as contas RECIPIENT.',
      );
    } finally {
      setRecipientsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRecipientAccounts();
  }, [loadRecipientAccounts]);

  const grossAmountCents = useMemo(() => parseBrlInputToCents(amountInput), [amountInput]);
  const validGrossCents = grossAmountCents !== null && grossAmountCents > 0 ? grossAmountCents : 0;

  const platformFeeCents = useMemo(
    () => computeBpsAmountCents(validGrossCents, PLATFORM_FEE_BPS),
    [validGrossCents],
  );

  const computedSplits = useMemo(() => {
    return splitRows.map((row) => {
      if (row.splitType === 'PERCENTAGE') {
        const parsedBps = Number(row.percentageBpsInput.trim());
        const validBps =
          Number.isInteger(parsedBps) && parsedBps >= 1 && parsedBps <= 10000 ? parsedBps : 0;
        const cents = computeBpsAmountCents(validGrossCents, validBps);
        return {
          rowId: row.rowId,
          valid: validBps > 0 && cents > 0,
          amountCents: cents,
          percentageBps: validBps,
        };
      }

      const fixedCents = parseBrlInputToCents(row.fixedAmountInput);
      const validFixed = fixedCents !== null && fixedCents > 0 ? fixedCents : 0;
      return {
        rowId: row.rowId,
        valid: validFixed > 0,
        amountCents: validFixed,
        percentageBps: null,
      };
    });
  }, [splitRows, validGrossCents]);

  const totalSplitsCents = useMemo(
    () => computedSplits.reduce((sum, item) => sum + item.amountCents, 0),
    [computedSplits],
  );

  const netMerchantCents = validGrossCents - platformFeeCents - totalSplitsCents;
  const isAllocationExceeded = validGrossCents > 0 && netMerchantCents < 0;

  const handleAddSplitRow = () => {
    const usedIds = new Set(splitRows.map((row) => row.recipientAccountId));
    const availableRecipient = recipientAccounts.find((acc) => !usedIds.has(acc.id));

    setSplitRows((prev) => [
      ...prev,
      {
        rowId: createRandomKey(),
        recipientAccountId: availableRecipient?.id ?? recipientAccounts[0]?.id ?? '',
        splitType: 'PERCENTAGE',
        percentageBpsInput: '2000',
        fixedAmountInput: '30,00',
      },
    ]);
  };

  const handleRemoveSplitRow = (rowId: string) => {
    setSplitRows((prev) => prev.filter((row) => row.rowId !== rowId));
  };

  const handleUpdateSplitRow = (rowId: string, patch: Partial<SplitDraftRow>) => {
    setSplitRows((prev) =>
      prev.map((row) => (row.rowId === rowId ? { ...row, ...patch } : row)),
    );
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);
    setProblem(null);

    if (!merchantId.trim() || !isValidUuid(merchantId)) {
      setFormError('Informe um UUID válido para Merchant ID.');
      return;
    }

    if (grossAmountCents === null || grossAmountCents <= 0) {
      setFormError('Informe um valor monetário válido maior que R$ 0,00 (ex.: 150,00).');
      return;
    }

    if (!payerId.trim() || payerId.trim().length > 120) {
      setFormError('Informe um Payer ID válido (1 a 120 caracteres).');
      return;
    }

    if (!idempotencyKey.trim() || idempotencyKey.trim().length > 128) {
      setFormError('Informe uma Idempotency-Key válida (1 a 128 caracteres).');
      return;
    }

    const seenRecipients = new Set<string>();
    const payloadSplits: CreatePaymentSplitInput[] = [];

    for (let i = 0; i < splitRows.length; i += 1) {
      const row = splitRows[i];
      if (!row.recipientAccountId.trim() || !isValidUuid(row.recipientAccountId)) {
        setFormError(`Split #${i + 1}: selecione uma conta RECIPIENT válida.`);
        return;
      }

      if (seenRecipients.has(row.recipientAccountId.trim())) {
        setFormError('Não é permitido duplicar a mesma conta RECIPIENT nos splits.');
        return;
      }
      seenRecipients.add(row.recipientAccountId.trim());

      if (row.splitType === 'PERCENTAGE') {
        const bps = Number(row.percentageBpsInput.trim());
        if (!Number.isInteger(bps) || bps < 1 || bps > 10000) {
          setFormError(`Split #${i + 1}: percentageBps deve ser um inteiro entre 1 e 10000.`);
          return;
        }
        if (computeBpsAmountCents(grossAmountCents, bps) <= 0) {
          setFormError(`Split #${i + 1}: o valor percentual calculado resultou em R$ 0,00.`);
          return;
        }
        payloadSplits.push({
          recipientAccountId: row.recipientAccountId.trim(),
          splitType: 'PERCENTAGE',
          percentageBps: bps,
        });
      } else {
        const fixedCents = parseBrlInputToCents(row.fixedAmountInput);
        if (fixedCents === null || fixedCents <= 0) {
          setFormError(`Split #${i + 1}: informe um valor fixo maior que R$ 0,00.`);
          return;
        }
        payloadSplits.push({
          recipientAccountId: row.recipientAccountId.trim(),
          splitType: 'FIXED',
          amountCents: fixedCents,
        });
      }
    }

    if (isAllocationExceeded) {
      setFormError('A soma da taxa da plataforma + splits excede o valor bruto da cobrança.');
      return;
    }

    const keyToSend = idempotencyKey.trim();
    setIsSubmitting(true);

    try {
      const response = await paymentsService.createPayment(
        {
          merchantId: merchantId.trim(),
          payerId: payerId.trim(),
          amountCents: grossAmountCents,
          currency: 'BRL',
          paymentMethod,
          description: description.trim() ? description.trim() : null,
          splits: payloadSplits,
        },
        keyToSend,
      );

      setResult(response);
      if (autoRotateIdempotencyKey) {
        setIdempotencyKey(createRandomKey());
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setProblem(error.problem);
      } else {
        setProblem({
          type: 'https://ledgerpulse.dev/errors/unexpected',
          title: 'Erro ao simular pagamento',
          status: 500,
          detail: 'Falha inesperada ao enviar POST /api/v1/payments.',
          instance: '/api/v1/payments',
          correlationId: 'unavailable',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const payment = result?.payment ?? null;
  const paymentCurrency = parseSupportedCurrency(payment?.currency);

  const timelineSteps = useMemo(() => {
    if (!payment) return [];

    const isSettled = payment.status === 'SETTLED';
    const isManualReview = payment.status === 'MANUAL_REVIEW';
    const isDeclined = payment.status === 'DECLINED';

    return [
      {
        title: '1. Idempotência (Redis)',
        state: 'done' as const,
        detail: result?.idempotentReplay
          ? 'Replay idêntico detectado (X-Idempotent-Replay: true).'
          : `Nova execução registrada (${truncateId(payment.idempotencyKey)}).`,
      },
      {
        title: '2. Antifraude IA (risk-engine-ai)',
        state: isDeclined ? ('failed' as const) : isManualReview ? ('warning' as const) : ('done' as const),
        detail: `Decisão: ${payment.riskDecision ?? 'N/D'} · Score: ${
          payment.riskScore ?? 'N/D (Fallback)'
        }`,
      },
      {
        title: '3. Double-Entry Ledger',
        state: isSettled
          ? ('done' as const)
          : isManualReview
            ? ('warning' as const)
            : ('failed' as const),
        detail: isSettled
          ? `Liquidado · ledgerTx: ${truncateId(payment.ledgerTransactionId ?? '—')}`
          : isManualReview
            ? 'Aguardando aprovação na Central Antifraude.'
            : 'Recusado — sem movimentação contábil.',
      },
      {
        title: '4. Transactional Outbox (PostgreSQL)',
        state: 'done' as const,
        detail: 'Evento gravado atomicamente na tabela outbox_events.',
      },
      {
        title: '5. Webhook Dispatcher (RabbitMQ)',
        state: isManualReview ? ('warning' as const) : ('done' as const),
        detail: isSettled
          ? 'Evento payment.settled encaminhado para entrega HMAC-SHA256.'
          : isManualReview
            ? 'Evento de revisão aguardando desfecho manual.'
            : 'Evento payment.declined encaminhado para entrega HMAC-SHA256.',
      },
    ];
  }, [payment, result?.idempotentReplay]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <section className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 sm:flex-row sm:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <CreditCard className="h-3.5 w-3.5" />
            <span>Laboratório Interativo · Split + Idempotência + Outbox</span>
          </div>
          <h2 className="mt-3 text-xl font-semibold text-white">
            Simulador de Checkout & Split
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Crie cobranças em <code className="text-emerald-300">amountCents</code> inteiros,
            configure splits percentuais ou fixos e teste replay idempotente ou conflito{' '}
            <code className="text-amber-300">HTTP 409</code>.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadRecipientAccounts()}
          disabled={recipientsLoading}
          className="inline-flex items-center gap-1.5 self-start rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300 sm:self-center"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${recipientsLoading ? 'animate-spin' : ''}`} />
          <span>Recarregar Recebedores</span>
        </button>
      </section>

      <div className="grid gap-6 xl:grid-cols-12">
        {/* Left: Checkout & Split Form */}
        <form
          onSubmit={handleSubmit}
          noValidate
          className="space-y-5 rounded-2xl border border-slate-800/90 bg-slate-900/60 p-6 xl:col-span-7"
        >
          {formError && (
            <div
              role="alert"
              className="rounded-xl border border-rose-500/40 bg-rose-950/40 px-4 py-3 text-xs font-medium text-rose-200"
            >
              {formError}
            </div>
          )}

          {problem && <ProblemDetailAlert problem={problem} />}

          {/* Monetary Value + Quick Presets */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 flex items-center justify-between gap-2">
                <label htmlFor="sim-amount" className="text-xs font-medium text-slate-300">
                  Valor Bruto (BRL)
                </label>
                <span className="font-mono text-[11px] text-emerald-300">
                  {grossAmountCents !== null ? `${grossAmountCents} cents` : 'Valor inválido'}
                </span>
              </div>
              <input
                id="sim-amount"
                type="text"
                inputMode="decimal"
                value={amountInput}
                onChange={(e) => setAmountInput(e.target.value)}
                placeholder="150,00"
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 font-mono text-base font-bold text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              <div className="mt-2 flex flex-wrap gap-1.5">
                {[
                  { label: 'R$ 150,00', value: '150,00' },
                  { label: 'R$ 1.250,90', value: '1.250,90' },
                  { label: 'R$ 8.500,00', value: '8.500,00' },
                ].map((preset) => (
                  <button
                    key={preset.value}
                    type="button"
                    onClick={() => setAmountInput(preset.value)}
                    className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-1 font-mono text-[11px] text-slate-300 hover:border-emerald-500/40 hover:text-emerald-300"
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label htmlFor="sim-method" className="mb-1.5 block text-xs font-medium text-slate-300">
                Método de Pagamento
              </label>
              <select
                id="sim-method"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-sm text-white focus:border-emerald-500 focus:outline-none"
              >
                <option value="CARD">CARD (Cartão)</option>
                <option value="PIX">PIX</option>
                <option value="BOLETO">BOLETO</option>
              </select>
            </div>
          </div>

          {/* Merchant, Payer, Description */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 flex items-center justify-between gap-2">
                <label htmlFor="sim-merchant-id" className="text-xs font-medium text-slate-300">
                  Merchant ID (`UUID`)
                </label>
                <button
                  type="button"
                  onClick={() => setMerchantId(DEV_MERCHANT_ID)}
                  className="inline-flex items-center gap-1 rounded border border-slate-700 bg-slate-950 px-1.5 py-0.5 text-[10px] text-emerald-300 hover:border-emerald-500/40"
                >
                  <Sparkles className="h-3 w-3" />
                  <span>Acme Dev</span>
                </button>
              </div>
              <input
                id="sim-merchant-id"
                type="text"
                value={merchantId}
                onChange={(e) => setMerchantId(e.target.value)}
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-white focus:border-emerald-500 focus:outline-none"
              />
            </div>

            <div>
              <label htmlFor="sim-payer-id" className="mb-1.5 block text-xs font-medium text-slate-300">
                Payer ID
              </label>
              <input
                id="sim-payer-id"
                type="text"
                value={payerId}
                onChange={(e) => setPayerId(e.target.value)}
                placeholder="payer-98765"
                className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 font-mono text-xs text-white focus:border-emerald-500 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label htmlFor="sim-description" className="mb-1.5 block text-xs font-medium text-slate-300">
              Descrição do Pedido
            </label>
            <input
              id="sim-description"
              type="text"
              maxLength={255}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Pedido #4021"
              className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white focus:border-emerald-500 focus:outline-none"
            />
          </div>

          {/* Dynamic Split Rows */}
          <div className="space-y-3 rounded-2xl border border-slate-800 bg-slate-950/60 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-200">
                  Regras de Split (Marketplace)
                </h3>
                <p className="text-[11px] text-slate-400">
                  Contas <code className="text-sky-300">RECIPIENT</code> carregadas de{' '}
                  <code>/api/v1/accounts</code> ({recipientAccounts.length} disponível(is)).
                </p>
              </div>
              <button
                type="button"
                onClick={handleAddSplitRow}
                className="inline-flex items-center gap-1 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-3 py-1.5 text-xs font-semibold text-emerald-300 hover:bg-emerald-500/20"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Adicionar Split</span>
              </button>
            </div>

            {recipientsError && (
              <p className="text-xs text-amber-300">{recipientsError}</p>
            )}

            {splitRows.length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-800 px-3 py-4 text-center text-xs text-slate-500">
                Sem splits configurados — 100% do líquido após taxa da plataforma será creditado ao
                Merchant.
              </p>
            ) : (
              <div className="space-y-3">
                {splitRows.map((row, index) => {
                  const preview = computedSplits[index];
                  return (
                    <div
                      key={row.rowId}
                      className="grid gap-3 rounded-xl border border-slate-800 bg-slate-900/75 p-3 sm:grid-cols-12 sm:items-end"
                    >
                      <div className="sm:col-span-5">
                        <label
                          htmlFor={`split-recipient-${index}`}
                          className="mb-1 block text-[11px] text-slate-400"
                        >
                          Conta Recebedora (`RECIPIENT`)
                        </label>
                        {recipientAccounts.length > 0 ? (
                          <select
                            id={`split-recipient-${index}`}
                            value={row.recipientAccountId}
                            onChange={(e) =>
                              handleUpdateSplitRow(row.rowId, {
                                recipientAccountId: e.target.value,
                              })
                            }
                            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 text-xs text-white focus:border-emerald-500 focus:outline-none"
                          >
                            <option value="">Selecione...</option>
                            {recipientAccounts.map((acc) => (
                              <option key={acc.id} value={acc.id}>
                                {acc.holderName} ({truncateId(acc.id)})
                              </option>
                            ))}
                          </select>
                        ) : (
                          <input
                            id={`split-recipient-${index}`}
                            type="text"
                            value={row.recipientAccountId}
                            onChange={(e) =>
                              handleUpdateSplitRow(row.rowId, {
                                recipientAccountId: e.target.value,
                              })
                            }
                            placeholder="UUID da conta RECIPIENT"
                            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 font-mono text-xs text-white focus:border-emerald-500 focus:outline-none"
                          />
                        )}
                      </div>

                      <div className="sm:col-span-3">
                        <label
                          htmlFor={`split-type-${index}`}
                          className="mb-1 block text-[11px] text-slate-400"
                        >
                          Tipo
                        </label>
                        <select
                          id={`split-type-${index}`}
                          value={row.splitType}
                          onChange={(e) =>
                            handleUpdateSplitRow(row.rowId, {
                              splitType: e.target.value as SplitType,
                            })
                          }
                          className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 text-xs text-white focus:border-emerald-500 focus:outline-none"
                        >
                          <option value="PERCENTAGE">PERCENTAGE (bps)</option>
                          <option value="FIXED">FIXED (BRL)</option>
                        </select>
                      </div>

                      <div className="sm:col-span-3">
                        <label
                          htmlFor={`split-value-${index}`}
                          className="mb-1 block text-[11px] text-slate-400"
                        >
                          {row.splitType === 'PERCENTAGE'
                            ? `Basis Points (${formatBpsPercent(preview?.percentageBps ?? 0)})`
                            : 'Valor Fixo (BRL)'}
                        </label>
                        {row.splitType === 'PERCENTAGE' ? (
                          <input
                            id={`split-value-${index}`}
                            type="number"
                            min={1}
                            max={10000}
                            value={row.percentageBpsInput}
                            onChange={(e) =>
                              handleUpdateSplitRow(row.rowId, {
                                percentageBpsInput: e.target.value,
                              })
                            }
                            placeholder="2000"
                            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 font-mono text-xs text-white focus:border-emerald-500 focus:outline-none"
                          />
                        ) : (
                          <input
                            id={`split-value-${index}`}
                            type="text"
                            inputMode="decimal"
                            value={row.fixedAmountInput}
                            onChange={(e) =>
                              handleUpdateSplitRow(row.rowId, {
                                fixedAmountInput: e.target.value,
                              })
                            }
                            placeholder="30,00"
                            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 font-mono text-xs text-white focus:border-emerald-500 focus:outline-none"
                          />
                        )}
                      </div>

                      <div className="flex items-center justify-between gap-2 sm:col-span-1 sm:justify-end">
                        <span className="font-mono text-[11px] text-sky-300 sm:hidden">
                          {formatCurrencyFromCents(preview?.amountCents ?? 0, 'BRL')}
                        </span>
                        <button
                          type="button"
                          onClick={() => handleRemoveSplitRow(row.rowId)}
                          aria-label={`Remover split ${index + 1}`}
                          className="rounded-lg border border-slate-700 bg-slate-950 p-2 text-slate-400 hover:border-rose-500/40 hover:text-rose-300"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      <div className="text-[11px] text-slate-400 sm:col-span-12">
                        Valor calculado em inteiros:{' '}
                        <strong className="font-mono text-sky-300">
                          {formatCurrencyFromCents(preview?.amountCents ?? 0, 'BRL')}
                        </strong>{' '}
                        <span className="font-mono text-slate-500">
                          ({preview?.amountCents ?? 0} cents)
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Idempotency-Key Control */}
          <div className="space-y-3 rounded-2xl border border-slate-800 bg-slate-950/60 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <KeyRound className="h-4 w-4 text-emerald-400" />
                <span className="text-xs font-semibold text-white">
                  Controle de `Idempotency-Key`
                </span>
              </div>

              <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-slate-300">
                <input
                  type="checkbox"
                  checked={autoRotateIdempotencyKey}
                  onChange={(e) => setAutoRotateIdempotencyKey(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500"
                />
                <span>Gerar nova chave após envio</span>
              </label>
            </div>

            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                type="text"
                aria-label="Idempotency-Key"
                value={idempotencyKey}
                onChange={(e) => setIdempotencyKey(e.target.value)}
                className="flex-1 rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 font-mono text-xs text-emerald-200 focus:border-emerald-500 focus:outline-none"
              />
              <button
                type="button"
                onClick={() => setIdempotencyKey(createRandomKey())}
                className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                <span>Nova Chave</span>
              </button>
            </div>

            <p className="text-[11px] text-slate-400">
              Desmarque &ldquo;Gerar nova chave após envio&rdquo; para testar{' '}
              <strong>replay idêntico</strong> (<code>X-Idempotent-Replay: true</code>) ou altere
              o valor mantendo a chave para disparar <code>HTTP 409 Conflict</code>.
            </p>
          </div>

          <button
            type="submit"
            disabled={isSubmitting || isAllocationExceeded}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-500 px-4 py-3 text-sm font-semibold text-slate-950 shadow-lg shadow-emerald-500/20 transition-all hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Send className="h-4 w-4" />
            <span>{isSubmitting ? 'Processando pagamento...' : 'Disparar Cobrança'}</span>
          </button>
        </form>

        {/* Right: Real-Time Integer Preview + Result Timeline */}
        <div className="space-y-6 xl:col-span-5">
          {/* Real-Time Integer Split Preview */}
          <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
            <h3 className="text-sm font-semibold text-white">
              Prévia Contábil em Tempo Real (Inteiros)
            </h3>
            <p className="mt-1 text-xs text-slate-400">
              Simulação exata em <code className="text-emerald-300">amountCents</code> antes do
              envio.
            </p>

            <dl className="mt-4 space-y-2.5 text-xs">
              <div className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/70 px-3.5 py-2.5">
                <dt className="text-slate-400">Valor Bruto</dt>
                <dd className="font-mono font-bold text-white">
                  {formatCurrencyFromCents(validGrossCents, 'BRL')}
                </dd>
              </div>

              <div className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/70 px-3.5 py-2.5">
                <dt className="text-slate-400">Taxa Plataforma (2,50% · 250 bps)</dt>
                <dd className="font-mono font-semibold text-violet-300">
                  {formatCurrencyFromCents(platformFeeCents, 'BRL')}
                </dd>
              </div>

              <div className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-950/70 px-3.5 py-2.5">
                <dt className="text-slate-400">Soma dos Splits ({splitRows.length})</dt>
                <dd className="font-mono font-semibold text-sky-300">
                  {formatCurrencyFromCents(totalSplitsCents, 'BRL')}
                </dd>
              </div>

              <div
                className={[
                  'flex items-center justify-between rounded-xl border px-3.5 py-3',
                  isAllocationExceeded
                    ? 'border-rose-500/40 bg-rose-500/10'
                    : 'border-emerald-500/35 bg-emerald-500/10',
                ].join(' ')}
              >
                <dt className="font-semibold text-slate-200">Líquido Merchant</dt>
                <dd
                  className={[
                    'font-mono text-sm font-bold',
                    isAllocationExceeded ? 'text-rose-300' : 'text-emerald-300',
                  ].join(' ')}
                >
                  {formatCurrencyFromCents(netMerchantCents, 'BRL')}
                </dd>
              </div>
            </dl>

            {isAllocationExceeded && (
              <p className="mt-3 text-xs font-medium text-rose-400">
                A soma da taxa + splits ultrapassa o valor bruto da cobrança.
              </p>
            )}
          </section>

          {/* Execution Result & Pipeline Timeline */}
          <section className="rounded-2xl border border-slate-800/90 bg-slate-900/60 p-5">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold text-white">Resultado & Fluxo Distribuído</h3>
              {result && (
                <span
                  role="status"
                  className={[
                    'rounded-full border px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase',
                    result.idempotentReplay
                      ? 'border-sky-500/45 bg-sky-500/15 text-sky-200'
                      : 'border-emerald-500/45 bg-emerald-500/15 text-emerald-200',
                  ].join(' ')}
                >
                  {result.idempotentReplay
                    ? 'X-Idempotent-Replay: true'
                    : `HTTP ${result.status} · Nova Execução`}
                </span>
              )}
            </div>

            {!payment ? (
              <p className="mt-4 rounded-xl border border-dashed border-slate-800 px-4 py-8 text-center text-xs text-slate-500">
                Envie uma cobrança ao lado para acompanhar o fluxo passo a passo.
              </p>
            ) : (
              <div className="mt-4 space-y-4">
                <div className="rounded-xl border border-slate-800 bg-slate-950/75 p-4 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span
                      className={`inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${PAYMENT_STATUS_BADGE_MAP[payment.status].badgeClass}`}
                    >
                      {PAYMENT_STATUS_BADGE_MAP[payment.status].label}
                    </span>
                    <span className="font-mono font-bold text-white">
                      {formatCurrencyFromCents(payment.amountCents, paymentCurrency)}
                    </span>
                  </div>

                  <dl className="mt-3 grid gap-2 text-[11px] text-slate-400">
                    <div className="flex justify-between gap-2">
                      <dt>paymentId</dt>
                      <dd className="font-mono text-slate-200">{payment.id}</dd>
                    </div>
                    <div className="flex justify-between gap-2">
                      <dt>correlationId</dt>
                      <dd className="font-mono text-slate-200">{payment.correlationId}</dd>
                    </div>
                    <div className="flex justify-between gap-2">
                      <dt>Líquido / Taxa</dt>
                      <dd className="font-mono text-emerald-300">
                        {formatCurrencyFromCents(payment.netMerchantCents, paymentCurrency)} /{' '}
                        {formatCurrencyFromCents(payment.platformFeeCents, paymentCurrency)}
                      </dd>
                    </div>
                  </dl>

                  {payment.reasonCodes.length > 0 && (
                    <div className="mt-3 border-t border-slate-800 pt-3">
                      <ReasonCodeChips reasonCodes={payment.reasonCodes} />
                    </div>
                  )}
                </div>

                {/* 5-step Timeline */}
                <ol className="space-y-2.5">
                  {timelineSteps.map((step) => (
                    <li
                      key={step.title}
                      className="flex items-start gap-3 rounded-xl border border-slate-800/80 bg-slate-950/60 px-3.5 py-2.5 text-xs"
                    >
                      {step.state === 'done' ? (
                        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                      ) : step.state === 'warning' ? (
                        <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
                      ) : step.state === 'failed' ? (
                        <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
                      ) : (
                        <Clock className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                      )}
                      <div className="min-w-0">
                        <p className="font-semibold text-slate-100">{step.title}</p>
                        <p className="mt-0.5 text-[11px] text-slate-400">{step.detail}</p>
                      </div>
                    </li>
                  ))}
                </ol>

                {/* Navigation Shortcuts */}
                <div className="flex flex-wrap gap-2 border-t border-slate-800 pt-3">
                  <Link
                    to="/ledger"
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 hover:border-emerald-500/40 hover:text-emerald-300"
                  >
                    <span>Auditoria Ledger</span>
                    <ArrowRight className="h-3 w-3" />
                  </Link>
                  <Link
                    to="/fraud-review"
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 hover:border-amber-500/40 hover:text-amber-300"
                  >
                    <span>Central Antifraude</span>
                    <ArrowRight className="h-3 w-3" />
                  </Link>
                  <Link
                    to="/webhooks"
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 hover:border-sky-500/40 hover:text-sky-300"
                  >
                    <span>Webhooks & DLQ</span>
                    <ArrowRight className="h-3 w-3" />
                  </Link>
                </div>
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
