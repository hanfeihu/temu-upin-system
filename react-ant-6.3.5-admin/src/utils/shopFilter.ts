const STORAGE_PREFIX = 'platform.shop-filter.';

function getStorage() {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function buildStorageKey(key: string) {
  return `${STORAGE_PREFIX}${key}`;
}

export function loadStoredShopFilter(key: string) {
  const storage = getStorage();
  if (!storage) {
    return undefined;
  }
  const value = storage.getItem(buildStorageKey(key))?.trim();
  return value || undefined;
}

export function loadStoredNumericShopFilter(key: string) {
  const raw = loadStoredShopFilter(key);
  if (!raw) {
    return undefined;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function saveStoredShopFilter(key: string, value: string | number | null | undefined) {
  const storage = getStorage();
  if (!storage) {
    return;
  }
  const storageKey = buildStorageKey(key);
  if (value === null || value === undefined || value === '') {
    storage.removeItem(storageKey);
    return;
  }
  storage.setItem(storageKey, String(value));
}

export function resolveStoredShopFilter<T extends string | number>(
  options: Array<{ value: T }>,
  currentValue?: T,
) {
  if (currentValue !== undefined && currentValue !== null && options.some((item) => item.value === currentValue)) {
    return currentValue;
  }
  return options[0]?.value;
}
