import { isSafePublicHttpsUrl } from './security.js';
import { formatPrice, normalizeProductUrl, parsePrice } from './recommendation-engine.js';

const MAX_HTML_BYTES = 1200 * 1024;
const FETCH_TIMEOUT_MS = 14000;
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

const NON_PRODUCT_PRICE_LINE_RX = /(kargo|[uü]cretsiz|bedava|taksit|[uü]zeri|alt\s*limit|kupon|indirim\s*kodu|puan\s*kazan|shipping|free\s*delivery|installment|voucher|gift\s*card)/i;

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
  const ldMatches = html.matchAll(/<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi);
  const offerKeyRx = /"(?:lowPrice|price|sellingPrice|discountedPrice)"\s*:\s*"?([0-9]+(?:[.,][0-9]+)*)"?/gi;
  const fallbackPrices = [];
  for (const m of ldMatches) {
    const rawJson = (m[1] || '').trim();
    if (!rawJson) continue;
    const looksLikeProduct = /Product|Offer|"offers"|priceSpecification/i.test(rawJson);
    let km;
    offerKeyRx.lastIndex = 0;
    while ((km = offerKeyRx.exec(rawJson)) !== null) {
      const parsed = parsePrice(km[1]);
      if (parsed != null && parsed >= 10) {
        if (looksLikeProduct) return parsed;
        fallbackPrices.push(parsed);
      }
    }
  }
  return fallbackPrices.length > 0 ? fallbackPrices[0] : null;
}

export function extractPriceFromHtml(html, sourceUrl = '') {
  if (typeof html !== 'string' || !html.trim()) return null;
  const isInditex = isInditexUrl(sourceUrl);

  // 1) JSON-LD structured product schema (1:1 with Android PriceTrackerService.kt)
  const ldPrice = extractFromJsonLdBlocks(html);
  if (ldPrice != null) return ldPrice;

  // 2) Meta & microdata price attributes
  const metaPatterns = [
    /property=["'](?:product:price:amount|og:price:amount)["'][^>]*content=["']([0-9.,]+)["']/gi,
    /content=["']([0-9.,]+)["'][^>]*property=["'](?:product:price:amount|og:price:amount)["']/gi,
    /(?:name|property)=["']twitter:data1["'][^>]*content=["']([^"']{1,32})["']/gi,
    /content=["']([^"']{1,32})["'][^>]*(?:name|property)=["']twitter:data1["']/gi,
    /itemprop=["']price["'][^>]*content=["']([0-9.,]+)["']/gi,
    /content=["']([0-9.,]+)["'][^>]*itemprop=["']price["']/gi,
    /data-(?:product-)?(?:sale-)?price=["']([0-9.,]+)["']/gi
  ];
  for (const rx of metaPatterns) {
    let m;
    while ((m = rx.exec(html)) !== null) {
      const parsed = parsePrice(m[1]);
      if (parsed != null && parsed >= 10) return parsed;
    }
  }

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
  return extractPriceFromVisibleText(html);
}

export function extractPriceFromVisibleText(text) {
  if (typeof text !== 'string' || !text.trim()) return null;
  // Focus on Markdown Content section if returned by reader bridge
  const mdIdx = text.indexOf('Markdown Content:');
  const body = mdIdx >= 0 ? text.slice(mdIdx + 17) : text;
  const lines = body.split(/\r?\n/);

  const visiblePatterns = [
    /([0-9]{1,3}(?:\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]{2,6}(?:,[0-9]{1,2})?)\s*(?:₺|TL\b|TRY\b)/i,
    /(?:₺|TL\b|TRY\b)\s*([0-9]{1,3}(?:\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]{2,6}(?:[.,][0-9]{1,2})?)/i,
    /(?:\$|€|£)\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?)/,
    /([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:€|\$|£)/
  ];

  // Pass 1: clean product lines (exclude kargo/shipping/taksit promos and struck-through old prices ~~1.999 TL~~)
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.length > 260) continue;
    if (NON_PRODUCT_PRICE_LINE_RX.test(line)) continue;
    const cleanedLine = line.replace(/~~[^~]+~~/g, ' ');
    for (const rx of visiblePatterns) {
      const m = rx.exec(cleanedLine);
      if (m) {
        const parsed = parsePrice(m[1]);
        if (parsed != null && parsed >= 10) return parsed;
      }
    }
  }

  // Pass 2: broad fallback across whole text
  for (const rx of visiblePatterns) {
    const m = rx.exec(body);
    if (m) {
      const parsed = parsePrice(m[1]);
      if (parsed != null && parsed >= 10) return parsed;
    }
  }
  return null;
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
        total += value.byteLength;
        out += decoder.decode(value, { stream: true });
      }
    }
  } finally {
    try { await reader.cancel(); } catch { /* ignore */ }
  }
  return out;
}

async function fetchViaUrl(url, headers = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: 'GET',
      mode: 'cors',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      cache: 'no-store',
      headers,
      signal: controller.signal
    });
    if (!res.ok) return null;
    return await readTextCapped(res, MAX_HTML_BYTES);
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export async function fetchProductPrice(rawUrl) {
  const cleanUrl = normalizeProductUrl(rawUrl);
  if (!isSafePublicHttpsUrl(cleanUrl, 1000)) {
    return null;
  }

  // 1) Primary: Jina Reader in simple CORS GET mode (zero preflight, renders JS/Shopify/Trendyol/Zara/Mavi cleanly)
  const readerMd = await fetchViaUrl(`https://r.jina.ai/${cleanUrl}`);
  if (readerMd) {
    const fromMd = extractPriceFromVisibleText(readerMd) ?? extractPriceFromHtml(readerMd, cleanUrl);
    if (fromMd != null && fromMd >= 10) return fromMd;
  }

  // 2) Secondary: Jina HTML mode (exposes full JSON-LD & embedded storefront JSON)
  const readerHtml = await fetchViaUrl(`https://r.jina.ai/${cleanUrl}`, { 'X-Return-Format': 'html' });
  if (readerHtml) {
    const fromHtml = extractPriceFromHtml(readerHtml, cleanUrl);
    if (fromHtml != null && fromHtml >= 10) return fromHtml;
  }

  // 3) Tertiary fallbacks (raw HTML proxies)
  const fallbackBridges = [
    `https://api.allorigins.win/raw?url=${encodeURIComponent(cleanUrl)}`,
    `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(cleanUrl)}`
  ];
  for (const bridgeUrl of fallbackBridges) {
    const html = await fetchViaUrl(bridgeUrl);
    if (html) {
      const price = extractPriceFromHtml(html, cleanUrl);
      if (price != null && price >= 10) return price;
    }
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
