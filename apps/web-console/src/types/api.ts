export type UserRole = 'ADMIN' | 'ANALYST' | 'VIEWER';

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  correlationId: string;
  timestamp: string;
  errors?: FieldValidationError[];
}

export interface HealthResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type AccountType = 'MERCHANT' | 'PLATFORM_FEE' | 'ESCROW' | 'RECIPIENT';

export type AccountStatus = 'ACTIVE' | 'BLOCKED';

export interface Account {
  id: string;
  merchantId?: string | null;
  accountType: AccountType;
  holderName: string;
  currency: string;
  availableBalanceCents: number;
  pendingBalanceCents: number;
  status: AccountStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  merchantId?: string | null;
  accountType: AccountType;
  holderName: string;
  currency: string;
}

export type LedgerReferenceType =
  | 'PAYMENT_SETTLEMENT'
  | 'PAYMENT_REFUND'
  | 'MANUAL_ADJUSTMENT';

export type EntryDirection = 'DEBIT' | 'CREDIT';

export interface LedgerTransactionSummary {
  id: string;
  referenceType: LedgerReferenceType;
  referenceId: string;
  description: string;
  currency: string;
  totalDebitCents: number;
  totalCreditCents: number;
  balanced: boolean;
  correlationId: string;
  createdAt: string;
}

export interface LedgerEntry {
  id: string;
  accountId: string;
  accountType: AccountType;
  direction: EntryDirection;
  amountCents: number;
  balanceAfterCents: number;
  createdAt: string;
}

export interface LedgerTransactionDetail extends LedgerTransactionSummary {
  entries: LedgerEntry[];
}

export type PaymentMethod = 'CARD' | 'PIX' | 'BOLETO';

export type PaymentStatus =
  | 'CREATED'
  | 'RISK_ANALYSIS'
  | 'AUTHORIZED'
  | 'MANUAL_REVIEW'
  | 'SETTLED'
  | 'DECLINED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

export type RiskDecision = 'APPROVE' | 'REVIEW' | 'BLOCK';

export type SplitType = 'PERCENTAGE' | 'FIXED';

export interface PaymentSplit {
  recipientAccountId: string;
  splitType: SplitType;
  percentageBps?: number | null;
  amountCents: number;
}

export interface Payment {
  id: string;
  merchantId: string;
  payerId: string;
  amountCents: number;
  platformFeeCents: number;
  netMerchantCents: number;
  currency: string;
  paymentMethod: PaymentMethod;
  description?: string | null;
  status: PaymentStatus;
  riskScore?: number | null;
  riskDecision?: RiskDecision | null;
  reasonCodes: string[];
  ledgerTransactionId?: string | null;
  idempotencyKey: string;
  correlationId: string;
  splits: PaymentSplit[];
  createdAt: string;
  updatedAt: string;
}

export interface RiskReviewItem {
  paymentId: string;
  merchantId: string;
  payerId: string;
  amountCents: number;
  platformFeeCents: number;
  netMerchantCents: number;
  currency: string;
  paymentMethod: PaymentMethod;
  description?: string | null;
  status: 'MANUAL_REVIEW';
  riskScore?: number | null;
  riskDecision?: RiskDecision | null;
  reasonCodes: string[];
  correlationId: string;
  createdAt: string;
}

export type RiskReviewDecisionType = 'APPROVE' | 'DECLINE';

export interface RiskReviewDecisionRequest {
  decision: RiskReviewDecisionType;
  justification: string;
}

export interface RiskReviewDecisionResponse {
  decisionId: string;
  paymentId: string;
  decision: RiskReviewDecisionType;
  paymentStatus: 'SETTLED' | 'DECLINED';
  ledgerTransactionId?: string | null;
  justification: string;
  reviewedByUserId: string;
  reviewedByEmail: string;
  reviewedAt: string;
}

export type WebhookEndpointStatus = 'ACTIVE' | 'PAUSED';

export interface WebhookEndpoint {
  id: string;
  merchantId: string;
  targetUrl: string;
  secretPreview: string;
  description?: string | null;
  status: WebhookEndpointStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookEndpointRequest {
  merchantId: string;
  targetUrl: string;
  secret: string;
  description?: string | null;
}

export type WebhookDeliveryStatus = 'PENDING' | 'RETRYING' | 'DELIVERED' | 'DLQ';

export interface WebhookDeliveryAttempt {
  attemptNumber: number;
  httpStatus?: number | null;
  latencyMs: number;
  signatureHeader: string;
  errorMessage?: string | null;
  attemptedAt: string;
}

export interface WebhookDelivery {
  id: string;
  eventId: string;
  eventType: string;
  merchantId: string;
  endpointId: string;
  targetUrl: string;
  correlationId: string;
  status: WebhookDeliveryStatus;
  attemptCount: number;
  maxAttempts: number;
  lastHttpStatus?: number | null;
  lastLatencyMs?: number | null;
  lastError?: string | null;
  signatureHeader?: string | null;
  createdAt: string;
  updatedAt: string;
  deliveredAt?: string | null;
  attempts: WebhookDeliveryAttempt[];
}

export interface DlqReplayResponse {
  deliveryId: string;
  eventId: string;
  status: WebhookDeliveryStatus;
  replayedAt: string;
  httpStatus?: number | null;
  latencyMs: number;
  errorMessage?: string | null;
}

export type SinkMode = 'OK_200' | 'FAIL_500' | 'TIMEOUT';

export interface SinkModeResponse {
  mode: SinkMode;
  timeoutDelayMs: number;
  updatedAt: string;
}

export interface CreatePaymentSplitInput {
  recipientAccountId: string;
  splitType: SplitType;
  percentageBps?: number | null;
  amountCents?: number | null;
}

export interface CreatePaymentRequest {
  merchantId?: string | null;
  payerId: string;
  amountCents: number;
  currency: string;
  paymentMethod: PaymentMethod;
  description?: string | null;
  splits?: CreatePaymentSplitInput[];
}



