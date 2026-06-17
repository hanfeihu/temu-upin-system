import type { AlibabaImageProxyConfigPayload, AlibabaImageProxyConfigVO } from '@/types/api';

const DEFAULT_IMAGE_PROXY_PATH = '/img-proxy';
export const DEFAULT_ALIBABA_IMAGE_PROXY_PATH = DEFAULT_IMAGE_PROXY_PATH;
const DEFAULT_ALIBABA_IMAGE_HOST_PATTERNS = [
  /^cbu\d+\.alicdn\.com$/i,
  /^img\.alicdn\.com$/i,
  /^gw\.alicdn\.com$/i,
];

type AlibabaImageProxyConfigLike = Pick<
  AlibabaImageProxyConfigVO,
  'enabled' | 'proxyBaseUrl' | 'imageProxyPath' | 'allowedHostsText'
> | Pick<
  AlibabaImageProxyConfigPayload,
  'enabled' | 'proxyBaseUrl' | 'imageProxyPath' | 'allowedHostsText'
>;
export const DEFAULT_ALIBABA_IMAGE_PROXY_HOST_LABELS = [
  'cbu*.alicdn.com',
  'img.alicdn.com',
  'gw.alicdn.com',
];

export const DEFAULT_ALIBABA_IMAGE_HOSTS = [
  'cbu01.alicdn.com',
  'cbu02.alicdn.com',
  'cbu03.alicdn.com',
  'cbu04.alicdn.com',
  'cbu05.alicdn.com',
  'gw.alicdn.com',
  'img.alicdn.com',
  'sc01.alicdn.com',
  'sc02.alicdn.com',
  'sc03.alicdn.com',
  'sc04.alicdn.com',
];

export function parseAllowedHosts(text?: string | null) {
  return (text || '')
    .split(/[\n,，;\s]+/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function normalizeBaseUrl(value?: string | null) {
  const text = value?.trim();
  if (!text) {
    return null;
  }
  return text.replace(/\/+$/, '');
}

function normalizePath(value?: string | null) {
  const text = value?.trim();
  if (!text) {
    return DEFAULT_IMAGE_PROXY_PATH;
  }
  const withLeadingSlash = text.startsWith('/') ? text : `/${text}`;
  return withLeadingSlash.replace(/\/+$/, '') || DEFAULT_IMAGE_PROXY_PATH;
}

function isDefaultAlibabaHost(host: string) {
  const normalized = host.toLowerCase();
  return DEFAULT_ALIBABA_IMAGE_HOSTS.includes(normalized)
    || DEFAULT_ALIBABA_IMAGE_HOST_PATTERNS.some((pattern) => pattern.test(normalized));
}

export function buildAlibabaImageProxyUrl(url?: string | null, config?: AlibabaImageProxyConfigLike | null) {
  const original = url?.trim();
  if (!original) {
    return original || '';
  }
  if (!config?.enabled) {
    return original;
  }

  const proxyBaseUrl = normalizeBaseUrl(config.proxyBaseUrl);
  if (!proxyBaseUrl) {
    return original;
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(original);
  } catch (error) {
    return original;
  }

  if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
    return original;
  }

  const host = parsedUrl.host.toLowerCase();
  const hostname = parsedUrl.hostname.toLowerCase();
  const allowedHosts = parseAllowedHosts(config.allowedHostsText);
  const allowed = allowedHosts.length > 0
    ? allowedHosts.includes(host) || allowedHosts.includes(hostname)
    : isDefaultAlibabaHost(hostname);
  if (!allowed) {
    return original;
  }

  const imageProxyPath = normalizePath(config.imageProxyPath);
  return `${proxyBaseUrl}${imageProxyPath}/${host}${parsedUrl.pathname}${parsedUrl.search}`;
}

export function mapAlibabaImageProxyUrls(urls?: Array<string | null> | null, config?: AlibabaImageProxyConfigLike | null) {
  return (urls || []).map((item) => buildAlibabaImageProxyUrl(item, config)).filter(Boolean);
}
