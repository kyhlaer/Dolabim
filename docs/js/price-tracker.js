import { isSafePublicHttpsUrl } from './security.js';
import { formatPrice, normalizeProductUrl, parsePrice } from './recommendation-engine.js';

const MAX_HTML_BYTES = 1200 * 1024;
const FETCH_TIMEOUT_MS = 10000;
const JINA_REQUEST_INTERVAL_MS = 3100;
const RATE_LIMIT_BACKOFF_MS = 30000;
const REMINDER_PREF_KEY = 'dolabim_price_reminder_v1';
const LAST_AUTO_CHECK_KEY = 'dolabim_price_autocheck_ts_v1';

const INDITEX_HOST_SUFFIXES = [
  'zara.com',
  'bershka.com',
  'pullandbear.com',
  'stradivarius.com',
  'massimodutti.com',
  'oysho.com',
  'lefties.com'
];

const NON_PRODUCT_PRICE_LINE_RX = /(kargo|[uü]cretsiz|bedava|taksit|[uü]zeri|alt\s*limit|kupon|indirim\s*kodu|puan\s*kazan|havale|kap[ıi]da\s*[oö]deme|shipping|free\s*delivery|installment|voucher|gift\s*card)/i;

export { normalizeProductUrl, parsePrice, formatPrice };

export function isPriceReminderEnabled() {
  try {
    return window.localStorage?.getItem(REMINDER_PREF_KEY) !== '0';
  } catch {
    return true;
  }
}

export function setPriceReminderEnabled(enabled) {
  try {
    window.localStorage?.setItem(REMINDER_PREF_KEY, enabled ? '1' : '0');
  } catch {
    // ignore storage errors
  }
}

export function shouldRunAutoPriceCheck(intervalMs = 12 * 60 * 60 * 1000) {
  if (!isPriceReminderEnabled()) return false;
  try {
    const last = Number(window.localStorage?.getItem(LAST_AUTO_CHECK_KEY) || 0);
    return !Number.isFinite(last) || (Date.now() - last) >= intervalMs;
  } catch {
    return false;
  }
}

export function markAutoPriceCheckDone() {
  try {
    window.localStorage?.setItem(LAST_AUTO_CHECK_KEY, String(Date.now()));
  } catch {
    // ignore
  }
}

function isInditexUrl(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return INDITEX_HOST_SUFFIXES.some((s) => host === s || host.endsWith(`.${s}`));
  } catch {
    return false;
  }
}

function normalizeNumericCandidate(rawToken, isInditex) {
  const s = String(rawToken || '').trim();
  if (!s) return null;
  if (isInditex && !s.includes('.') && !s.includes(',') && /^\d+$/.test(s)) {
    const intVal = Number(s);
    if (Number.isFinite(intVal) && intVal >= 10000 && intVal <= 9999999) {
      const scaled = intVal / 100;
      if (scaled >= 10 && scaled < 1000000) return scaled;
    }
  }
  const parsed = parsePrice(s);
  return parsed != null && parsed >= 10 ? parsed : null;
}

export function detectCurrencyHint(text, sourceUrl = '') {
  const src = String(text || '');
  if (/"priceCurrency"\s*:\s*"USD"/i.test(src)) return '$';
  if (/"priceCurrency"\s*:\s*"EUR"/i.test(src)) return '€';
  if (/"priceCurrency"\s*:\s*"GBP"/i.test(src)) return '£';
  if (/"priceCurrency"\s*:\s*"TRY"/i.test(src)) return '₺';
  if (/(?:₺|\bTL\b|\bTRY\b)/i.test(src)) return '₺';
  if (sourceUrl.toLowerCase().includes('.tr') || sourceUrl.toLowerCase().includes('/tr/')) return '₺';
  if (/€/.test(src)) return '€';
  if (/£/.test(src)) return '£';
  if (/\$[0-9]/.test(src)) return '$';
  return '₺';
}

function extractFromJsonLdBlocks(html) {
  const scripts = html.matchAll(/<script\b[^>]*\stype\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi);
  const nodes = [];
  for (const [, json] of scripts) {
    try {
      const root = JSON.parse(json);
      for (const node of [root].flat()) {
        nodes.push(node);
        if (Array.isArray(node?.['@graph'])) nodes.push(...node['@graph']);
      }
    } catch { /* malformed structured data; continue with other sources */ }
  }
  const byId = new Map(nodes.filter((node) => node?.['@id']).map((node) => [node['@id'], node]));
  const hasType = (node, types) => [node?.['@type']].flat()
    .some((type) => typeof type === 'string' && types.includes(type.split(/[\/#]/).pop()));
  const products = nodes.filter((node) => hasType(node, ['Product', 'ProductGroup']));
  const offers = products.length
    ? products.flatMap((product) => product.offers || [])
    : nodes.filter((node) => hasType(node, ['Offer', 'AggregateOffer']));
  for (const ref of offers) {
    const offer = ref?.['@id'] && !ref.price && !ref.lowPrice ? byId.get(ref['@id']) ?? ref : ref;
    for (const amount of [offer?.price, offer?.lowPrice, offer?.priceSpecification?.price]) {
      const price = normalizeNumericCandidate(amount, false);
      if (price != null) return price;
    }
  }
  return null;
}

function storefrontText(html) {
  return html
    .replace(/<!--[\s\S]*?(?:-->|$)|<(script|style|template|del|s)\b[^>]*>[\s\S]*?(?:<\/\1\s*>|$)/gi, '')
    .replace(/<\/?(?:div|p|h[1-6]|li|section|br)\b[^>]*>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;|&#160;|&#xa0;/gi, ' ')
    .replace(/&#8378;|&#x20ba;/gi, '₺');
}

export function extractPriceFromHtml(html, sourceUrl = '') {
  if (typeof html !== 'string' || !html.trim()) return null;
  const isInditex = isInditexUrl(sourceUrl);

  // 1) JSON-LD structured product schema (1:1 with Android PriceTrackerService.kt)
  const ldPrice = extractFromJsonLdBlocks(html);
  if (ldPrice != null) return ldPrice;

  // Ticimax exposes the tax-inclusive sale price separately from productPrice (before tax).
  if (/\bid\s*=\s*["']divFiyatAlanlari["']/i.test(html)) {
    const amount = html.match(/"productPriceStr"\s*:\s*"([0-9.,]+)"/i)?.[1];
    const price = normalizeNumericCandidate(amount, false);
    if (price != null) return price;
  }

  // Meta attributes can appear in either order and use either name or property.
  let secondaryMetaPrice = null;
  const priceTags = html.matchAll(/<meta\b[^>]*>|<[a-z][\w-]*\b[^>]*\s(?:itemprop|data-(?:product-)?(?:sale-)?price)\s*=[^>]*>/gi);
  for (const [tag] of priceTags) {
    const attrs = Object.fromEntries(Array.from(
      tag.matchAll(/\s([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g),
      ([, key, double, single, bare]) => [key.toLowerCase(), double ?? single ?? bare]
    ));
    const key = (attrs.property || attrs.name || attrs.itemprop || '').toLowerCase();
    const price = normalizeNumericCandidate(attrs.content, false);
    if (['product:price:amount', 'og:price:amount', 'price'].includes(key) && price != null) return price;
    if (key === 'twitter:data1' && /₺|€|£|\$|\b(?:TL|TRY|USD|EUR|GBP)\b/i.test(attrs.content || '')) {
      secondaryMetaPrice ??= price;
    }
    const dataPrice = attrs['data-product-sale-price'] ?? attrs['data-sale-price']
      ?? attrs['data-product-price'] ?? attrs['data-price'];
    secondaryMetaPrice ??= normalizeNumericCandidate(dataPrice, false);
  }
  if (secondaryMetaPrice != null) return secondaryMetaPrice;

  // 3) Nested JSON price objects (Trendyol, Hepsiburada, Koton, DeFacto, LCW, H&M, Shopify, Ticimax, Ikas)
  const nestedJsonRx = /"(?:discountedPrice|sellingPrice|salePrice|currentPrice|finalPrice|current|price)"\s*:\s*\{[^{}]{0,260}?"(?:value|amount|text)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?/gi;
  let nm;
  while ((nm = nestedJsonRx.exec(html)) !== null) {
    const parsed = normalizeNumericCandidate(nm[1], isInditex);
    if (parsed != null) return parsed;
  }

  // 4) Flat JSON price keys (including Inditex cents-to-TL conversion)
  const flatJsonRx = /"(?:discountedPrice|sellingPrice|salePrice|currentPrice|finalPrice|lowPrice|price)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?/gi;
  let fm;
  while ((fm = flatJsonRx.exec(html)) !== null) {
    const parsed = normalizeNumericCandidate(fm[1], isInditex);
    if (parsed != null) return parsed;
  }

  // 5) Visible storefront currency text (smart line-filtered for Markdown & HTML)
  return extractPriceFromVisibleText(storefrontText(html));
}

export function extractPriceFromVisibleText(text) {
  if (typeof text !== 'string' || !text.trim()) return null;
  // Focus on Markdown Content section if returned by reader bridge
  const mdIdx = text.indexOf('Markdown Content:');
  const body = mdIdx >= 0 ? text.slice(mdIdx + 17) : text;
  const lines = body.split(/\r?\n/);

  const priceRx = /(?:₺|€|£|\$|\b(?:TL|TRY)\b)\s*([0-9]+(?:[.,][0-9]+)*)|([0-9]+(?:[.,][0-9]+)*)\s*(?:₺|€|£|\$|TL\b|TRY\b)/i;
  let fallback = null;
  let regular = null;
  for (const rawLine of lines) {
    const line = rawLine.replace(/~~[^~]+~~/g, ' ').replace(/[*_]/g, '').trim();
    if (!line || line.length > 260 || NON_PRODUCT_PRICE_LINE_RX.test(line)) continue;
    const amount = priceRx.exec(line);
    if (!amount) continue;
    const price = normalizeNumericCandidate(amount[1] ?? amount[2], false);
    if (price == null) continue;
    if (/^[iİ]ndirimli(?:\s*fiyat[ıi]?)?\s*:/i.test(line) || /^sale price\s*:/i.test(line)) return price;
    if (/^(?:fiyat[ıi]?|price)\s*:/i.test(line)) regular ??= price;
    fallback ??= price;
  }
  return regular ?? fallback;
}

async function readTextCapped(response, maxBytes = MAX_HTML_BYTES) {
  if (!response?.body?.getReader) {
    const txt = await response.text();
    return txt.slice(0, maxBytes);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: false });
  let total = 0;
  let out = '';
  try {
    while (total < maxBytes) {
      const { value, done } = await reader.read();
      if (done) break;
      if (value) {
        const chunk = value.subarray(0, maxBytes - total);
        total += chunk.byteLength;
        out += decoder.decode(chunk, { stream: true });
      }
    }
  } finally {
    try { await reader.cancel(); } catch { /* ignore */ }
  }
  return out + decoder.decode();
}

let priceRequestQueue = Promise.resolve();
let nextJinaRequestAt = 0;
let jinaBackoffUntil = 0;
let microlinkBackoffUntil = 0;

function warnBridgeFailure(url, reason) {
  console.warn(`[price-tracker] ${new URL(url).hostname}: ${reason}`);
}

async function fetchViaUrl(url, onRateLimit) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: 'GET',
      mode: 'cors',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      cache: 'no-store',
      signal: controller.signal
    });
    if (!res.ok) {
      warnBridgeFailure(url, `HTTP ${res.status}`);
      if (res.status === 429) onRateLimit?.();
      return null;
    }
    return await readTextCapped(res, MAX_HTML_BYTES);
  } catch (error) {
    warnBridgeFailure(url, controller.signal.aborted
      ? `timeout after ${FETCH_TIMEOUT_MS / 1000}s`
      : `${error?.name || 'Error'}: ${error?.message || 'request failed'}`);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function fetchViaJina(url) {
  if (Date.now() < jinaBackoffUntil) return null;
  const delay = nextJinaRequestAt - Date.now();
  if (delay > 0) await new Promise((resolve) => setTimeout(resolve, delay));
  nextJinaRequestAt = Date.now() + JINA_REQUEST_INTERVAL_MS;
  return fetchViaUrl(url, () => { jinaBackoffUntil = Date.now() + RATE_LIMIT_BACKOFF_MS; });
}

function extractPriceFromMicrolink(body, sourceUrl) {
  try {
    const result = JSON.parse(body);
    return result.status === 'success' ? extractPriceFromHtml(result.data?.html, sourceUrl) : null;
  } catch {
    return null;
  }
}

export function fetchProductPrice(rawUrl) {
  // Keep bulk, automatic and manual checks from hitting the same bridge concurrently.
  const request = priceRequestQueue.then(() => fetchProductPriceOnce(rawUrl));
  priceRequestQueue = request.catch(() => {});
  return request;
}

async function fetchProductPriceOnce(rawUrl) {
  const cleanUrl = normalizeProductUrl(rawUrl);
  if (!isSafePublicHttpsUrl(cleanUrl, 1000)) {
    return null;
  }

  const target = new URL(cleanUrl);
  target.hash = '';
  if (['slatra.com', 'www.slatra.com'].includes(target.hostname)) {
    target.hostname = 'www.slatra.com.tr';
  }
  const encoded = encodeURIComponent(target.href);
  const jinaUrl = `https://r.jina.ai/${target.href}`;
  const reader = await fetchViaJina(jinaUrl);
  const readerPrice = extractPriceFromVisibleText(reader);
  if (readerPrice != null) return readerPrice;
  if (reader != null) warnBridgeFailure(jinaUrl, 'no product price in response');

  if (Date.now() >= microlinkBackoffUntil) {
    const microlinkUrl = `https://api.microlink.io/?url=${encoded}&data.html.selector=html&data.html.attr=html`;
    const body = await fetchViaUrl(
      microlinkUrl,
      () => { microlinkBackoffUntil = Date.now() + RATE_LIMIT_BACKOFF_MS; }
    );
    const price = body ? extractPriceFromMicrolink(body, target.href) : null;
    if (price != null) return price;
    if (body != null) warnBridgeFailure(microlinkUrl, 'no product price in response');
  }

  const bridges = [
    `https://api.allorigins.win/raw?url=${encoded}`,
    `https://api.codetabs.com/v1/proxy?quest=${encoded}`
  ];
  for (const url of bridges) {
    const body = await fetchViaUrl(url);
    if (body == null) continue;
    const price = extractPriceFromHtml(body, target.href);
    if (price != null) return price;
    warnBridgeFailure(url, 'no product price in response');
  }

  return null;
}

export async function requestPriceNotificationPermission() {
  if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported';
  if (Notification.permission === 'granted') return 'granted';
  if (Notification.permission === 'denied') return 'denied';
  try {
    return await Notification.requestPermission();
  } catch {
    return 'default';
  }
}

export function showPriceDropBrowserNotification(title, body) {
  if (!isPriceReminderEnabled()) return;
  if (typeof window === 'undefined' || !('Notification' in window)) return;
  if (Notification.permission !== 'granted') return;
  try {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.ready.then(reg => reg.showNotification(title, { body, icon: './icons/icon-192.png' })).catch(() => {});
      return;
    }
    new Notification(title, {
      body,
      icon: './icons/icon-192.png',
      badge: './icons/icon-192.png'
    });
  } catch {
    // ignore if browser blocks Notification constructor
  }
}

/**
 * Evaluates a freshly fetched price against an item's stored price.
 * Returns { updatedItem, dropped, previousPrice, newPrice, formatted }.
 */
export function applyFetchedPriceToItem(item, fetchedPrice) {
  const newPrice = Number(fetchedPrice);
  if (!item || !Number.isFinite(newPrice) || newPrice <= 0) {
    return { updatedItem: item, dropped: false, previousPrice: 0, newPrice: 0, formatted: '' };
  }
  const existingParsed = item.lastCheckedPrice > 0 ? item.lastCheckedPrice : (parsePrice(item.priceText) || 0);
  const dropped = existingParsed > 0 && newPrice < (existingParsed - 0.5);
  const formatted = formatPrice(newPrice, item.priceText || '');
  const updatedItem = {
    ...item,
    priceText: formatted,
    lastCheckedPrice: newPrice,
    previousPrice: dropped ? existingParsed : (item.previousPrice || 0),
    priceDropped: dropped ? true : (existingParsed > 0 && newPrice > existingParsed ? false : Boolean(item.priceDropped))
  };
  return {
    updatedItem,
    dropped,
    previousPrice: dropped ? existingParsed : 0,
    newPrice,
    formatted
  };
}
