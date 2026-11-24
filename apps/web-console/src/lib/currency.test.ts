import { describe, expect, it } from 'vitest';
import {
  computeBpsAmountCents,
  formatBpsPercent,
  formatCurrencyFromCents,
  parseBrlInputToCents,
} from './currency';

describe('currency helpers (integer-only monetary invariants)', () => {
  it('formats integer cents to BRL and USD without floating-point drift', () => {
    expect(formatCurrencyFromCents(0, 'BRL')).toBe('R$\u00a00,00');
    expect(formatCurrencyFromCents(15000, 'BRL')).toBe('R$\u00a0150,00');
    expect(formatCurrencyFromCents(-5, 'BRL')).toBe('-R$\u00a00,05');
    expect(formatCurrencyFromCents(12845090, 'USD')).toBe('$128,450.90');
    expect(formatCurrencyFromCents(-15000, 'USD')).toBe('-$150.00');
    expect(formatCurrencyFromCents(999999999999, 'BRL')).toBe('R$\u00a09.999.999.999,99');
  });

  it('rejects non-integer / floating-point values in formatCurrencyFromCents', () => {
    expect(() => formatCurrencyFromCents(19.99, 'BRL')).toThrow(RangeError);
    expect(() => formatCurrencyFromCents(Number.NaN, 'BRL')).toThrow(RangeError);
    expect(() => formatCurrencyFromCents(Number.POSITIVE_INFINITY, 'USD')).toThrow(RangeError);
  });

  it('parses BRL text inputs into exact integer cents immune to float drift', () => {
    expect(parseBrlInputToCents('19,99')).toBe(1999);
    expect(parseBrlInputToCents('1.250,90')).toBe(125090);
    expect(parseBrlInputToCents('R$ 150,00')).toBe(15000);
    expect(parseBrlInputToCents('150.00')).toBe(15000);
    expect(parseBrlInputToCents('0,01')).toBe(1);

    // Classic 0.1 + 0.2 float-drift trap stays exact in integer cents
    const tenCents = parseBrlInputToCents('0,10');
    const twentyCents = parseBrlInputToCents('0,20');
    expect(tenCents).toBe(10);
    expect(twentyCents).toBe(20);
    expect((tenCents ?? 0) + (twentyCents ?? 0)).toBe(30);

    expect(parseBrlInputToCents('-10,00')).toBeNull();
    expect(parseBrlInputToCents('12,345')).toBeNull();
    expect(parseBrlInputToCents('abc')).toBeNull();
    expect(parseBrlInputToCents('')).toBeNull();
  });

  it('computes and formats basis points strictly with integer truncation', () => {
    expect(computeBpsAmountCents(15000, 250)).toBe(375);
    expect(computeBpsAmountCents(15000, 2000)).toBe(3000);
    expect(computeBpsAmountCents(100, 333)).toBe(3);
    expect(formatBpsPercent(8571)).toBe('85,71%');
    expect(formatBpsPercent(250)).toBe('2,50%');
  });
});
