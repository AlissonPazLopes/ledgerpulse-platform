export type SupportedCurrency = 'BRL' | 'USD';

const CURRENCY_LOCALE_MAP: Record<SupportedCurrency, string> = {
  BRL: 'pt-BR',
  USD: 'en-US',
};

/**
 * Formata um valor financeiro representado estritamente em centavos inteiros (`amountCents`)
 * usando aritmética inteira com `BigInt` para evitar imprecisões de ponto flutuante.
 */
export function formatCurrencyFromCents(
  amountCents: number,
  currency: SupportedCurrency = 'BRL',
): string {
  if (!Number.isSafeInteger(amountCents)) {
    throw new RangeError(
      `amountCents deve ser um inteiro seguro em centavos; recebido: ${String(amountCents)}`,
    );
  }

  const locale = CURRENCY_LOCALE_MAP[currency];
  const isNegative = amountCents < 0;
  const absoluteCents = isNegative ? -BigInt(amountCents) : BigInt(amountCents);
  const wholeUnits = absoluteCents / 100n;
  const fractionCents = Number(absoluteCents % 100n);

  const integerFormatter = new Intl.NumberFormat(locale, {
    maximumFractionDigits: 0,
    useGrouping: true,
  });

  const currencyFormatter = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  const formattedWhole = integerFormatter.format(wholeUnits);
  const formattedFraction = String(fractionCents).padStart(2, '0');
  const referenceSample = isNegative ? -1 : 1;

  return currencyFormatter
    .formatToParts(referenceSample)
    .map((part) => {
      if (part.type === 'integer') {
        return formattedWhole;
      }
      if (part.type === 'fraction') {
        return formattedFraction;
      }
      return part.value;
    })
    .join('');
}
