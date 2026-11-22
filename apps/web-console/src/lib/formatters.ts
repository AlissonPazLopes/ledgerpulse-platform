const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const LOCAL_DATE_TIME_FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export function isValidUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

export function truncateId(id: string, visibleStart = 8, visibleEnd = 4): string {
  if (id.length <= visibleStart + visibleEnd + 3) {
    return id;
  }
  return `${id.slice(0, visibleStart)}…${id.slice(-visibleEnd)}`;
}

export function formatDateTimeParts(isoString: string): { local: string; utc: string } {
  const parsed = new Date(isoString);
  if (Number.isNaN(parsed.getTime())) {
    return { local: isoString, utc: isoString };
  }

  return {
    local: LOCAL_DATE_TIME_FORMATTER.format(parsed),
    utc: parsed.toISOString().replace('.000Z', 'Z'),
  };
}

export function toIsoUtcFromLocalInput(localValue: string): string | undefined {
  const trimmed = localValue.trim();
  if (!trimmed) {
    return undefined;
  }

  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }

  return parsed.toISOString();
}
