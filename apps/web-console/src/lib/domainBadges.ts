import type { AccountStatus, AccountType, EntryDirection, LedgerReferenceType } from '../types/api';

export const ACCOUNT_TYPE_BADGE_MAP: Record<
  AccountType,
  { label: string; badgeClass: string }
> = {
  MERCHANT: {
    label: 'MERCHANT',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300',
  },
  RECIPIENT: {
    label: 'RECIPIENT',
    badgeClass: 'border-sky-500/35 bg-sky-500/15 text-sky-300',
  },
  ESCROW: {
    label: 'ESCROW',
    badgeClass: 'border-amber-500/35 bg-amber-500/15 text-amber-300',
  },
  PLATFORM_FEE: {
    label: 'PLATFORM_FEE',
    badgeClass: 'border-violet-500/35 bg-violet-500/15 text-violet-300',
  },
};

export const ACCOUNT_STATUS_BADGE_MAP: Record<
  AccountStatus,
  { label: string; badgeClass: string }
> = {
  ACTIVE: {
    label: 'ACTIVE',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/10 text-emerald-300',
  },
  BLOCKED: {
    label: 'BLOCKED',
    badgeClass: 'border-rose-500/35 bg-rose-500/10 text-rose-300',
  },
};

export const LEDGER_REFERENCE_BADGE_MAP: Record<
  LedgerReferenceType,
  { label: string; badgeClass: string }
> = {
  PAYMENT_SETTLEMENT: {
    label: 'PAYMENT_SETTLEMENT',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300',
  },
  PAYMENT_REFUND: {
    label: 'PAYMENT_REFUND',
    badgeClass: 'border-amber-500/35 bg-amber-500/15 text-amber-300',
  },
  MANUAL_ADJUSTMENT: {
    label: 'MANUAL_ADJUSTMENT',
    badgeClass: 'border-sky-500/35 bg-sky-500/15 text-sky-300',
  },
};

export const ENTRY_DIRECTION_BADGE_MAP: Record<
  EntryDirection,
  { label: string; badgeClass: string }
> = {
  DEBIT: {
    label: 'DEBIT',
    badgeClass: 'border-amber-500/35 bg-amber-500/15 text-amber-300',
  },
  CREDIT: {
    label: 'CREDIT',
    badgeClass: 'border-emerald-500/35 bg-emerald-500/15 text-emerald-300',
  },
};
