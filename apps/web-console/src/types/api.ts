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

