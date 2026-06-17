const SHANGHAI_TIME_ZONE = 'Asia/Shanghai';

function formatTimestampByTimeZone(value: number, includeSeconds: boolean) {
  const date = new Date(value);
  const formatter = new Intl.DateTimeFormat('zh-CN', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: includeSeconds ? '2-digit' : undefined,
    hour12: false,
  });
  const parts = formatter.formatToParts(date).reduce<Record<string, string>>((acc, part) => {
    if (part.type !== 'literal') {
      acc[part.type] = part.value;
    }
    return acc;
  }, {});
  const second = includeSeconds ? `:${parts.second || '00'}` : '';
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}${second}`;
}

export function formatDateTime(value: string | number[] | null | undefined) {
  if (!value) {
    return '-';
  }

  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value;
    const pad = (input: number | undefined) => String(input || 0).padStart(2, '0');
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`;
  }

  const normalized = String(value).trim();
  if (!normalized) {
    return '-';
  }

  if (/^\d{4}-\d{2}-\d{2}T/.test(normalized)) {
    return normalized.replace('T', ' ').replace(/\.\d+$/, '');
  }

  return normalized;
}

export function formatTimestamp(value: number | null | undefined) {
  if (!value) {
    return '-';
  }
  return formatTimestampByTimeZone(value, true);
}

export function formatTimestampMinute(value: number | null | undefined) {
  if (!value) {
    return '-';
  }
  return formatTimestampByTimeZone(value, false);
}

export function formatPrice(value: number | string | null | undefined, divisor = 100) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }

  const numeric = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `¥${(numeric / divisor).toFixed(2)}`;
}

export function safeJsonParse<T>(value: string | null | undefined, fallback: T): T {
  if (!value) {
    return fallback;
  }

  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

export function prettyJson(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '';
  }

  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
