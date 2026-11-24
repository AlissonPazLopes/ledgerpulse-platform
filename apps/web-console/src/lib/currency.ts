export type SupportedCurrency = 'BRL' | 'USD';

export function parseSupportedCurrency(currency?: string): SupportedCurrency {
  return currency === 'USD' ? 'USD' : 'BRL';
}

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

/**
 * Converte uma entrada textual BRL (ex.: "150,00", "1.250,90", "150.00") para centavos inteiros
 * usando exclusivamente manipulação de strings e inteiros (zero ponto flutuante).
 */
export function parseBrlInputToCents(rawInput: string): number | null {
  const cleaned = rawInput.replace(/R\$/gi, '').replace(/\s+/g, '').trim();
  if (!cleaned || cleaned.startsWith('-')) {
    return null;
  }

  let wholePart = '';
  let fractionPart = '';

  if (cleaned.includes(',')) {
    const commaParts = cleaned.split(',');
    if (commaParts.length !== 2) {
      return null;
    }
    wholePart = commaParts[0].replace(/\./g, '');
    fractionPart = commaParts[1];
  } else if (cleaned.includes('.')) {
    const dotParts = cleaned.split('.');
    if (dotParts.length === 2 && dotParts[1].length <= 2) {
      wholePart = dotParts[0];
      fractionPart = dotParts[1];
    } else if (dotParts.every((segment, idx) => (idx === 0 ? segment.length >= 1 : segment.length === 3))) {
      wholePart = dotParts.join('');
      fractionPart = '';
    } else {
      return null;
    }
  } else {
    wholePart = cleaned;
  }

  if (!/^\d+$/.test(wholePart) || !/^\d{0,2}$/.test(fractionPart)) {
    return null;
  }

  const wholeUnits = BigInt(wholePart);
  const centsRemainder = BigInt(fractionPart.padEnd(2, '0'));
  const totalCents = wholeUnits * 100n + centsRemainder;

  if (totalCents > BigInt(Number.MAX_SAFE_INTEGER)) {
    return null;
  }

  return Number(totalCents);
}

/**
 * Calcula o valor inteiro em centavos para uma taxa/percentual em basis points (`bps` de 0..10000)
 * usando aritmética inteira com truncamento (`BigInt`), idêntica ao `SplitCalculator` do backend.
 */
export function computeBpsAmountCents(grossAmountCents: number, bps: number): number {
  if (!Number.isSafeInteger(grossAmountCents) || !Number.isSafeInteger(bps) || bps < 0) {
    return 0;
  }
  const result = (BigInt(grossAmountCents) * BigInt(bps)) / 10000n;
  return Number(result);
}

/**
 * Formata basis points inteiros (ex.: 8571 -> "85,71%", 250 -> "2,50%") sem aritmética de ponto flutuante.
 */
export function formatBpsPercent(bps: number): string {
  if (!Number.isSafeInteger(bps) || bps < 0) {
    return '0,00%';
  }
  const whole = Math.floor(bps / 100);
  const remainder = String(bps % 100).padStart(2, '0');
  return `${whole},${remainder}%`;
}

