import { isSafePublicHttpsUrl } from './security.js';
import { formatPrice, normalizeProductUrl, parsePrice } from './recommendation-engine.js';

const MAX_HTML_BYTES = 750 * 1024;
const FETCH_TIMEOUT_MS = 10000;
const REMINDER_PREF_KEY = 'dolabim_price_reminder_v1';
const LAST_AUTO_CHECK_KEY = 'dolabim_price_autocheck_ts_v1';

// Read-only public CORS bridges used solely for GETting public https:// product pages.
// Every target URL is strictly validated by isSafePublicHttpsUrl (blocks localhost, private IPs, credentials, non-HTTPS).
const CORS_HTML_BRIDGES = [
  (url) => `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`,
  (url) => `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(url)}`
];

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

export function extractPriceFromHtml(html) {
  if (typeof html !== 'string' || !html) return null;

  // 1) JSON-LD structured product data (most accurate across global & TR retailers)
  const ldMatches = html.matchAll(/<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi);
  for (const m of ldMatches) {
    const body = m[1] || '';
    const pMatch = /"(?:lowPrice|price)"\s*:\s*"?([0-9][0-9.,\s]{0,16})"?/i.exec(body);
    if (pMatch) {
      const val = parsePrice(pMatch[1]);
      if (val > 0) return val;
    }
  }

  // 2) OpenGraph / Twitter / Schema meta tags
  const metaPatterns = [
    /<meta[^>]+(?:property|name|itemprop)=["'](?:product:price:amount|og:price:amount|price|twitter:data1)["'][^>]+content=["']([^"']{1,40})["']/i,
    /<meta[^>]+content=["']([^"']{1,40})["'][^>]+(?:property|name|itemprop)=["'](?:product:price:amount|og:price:amount|price|twitter:data1)["']/i
  ];
  for (const rx of metaPatterns) {
    const mm = rx.exec(html);
    if (mm) {
      const val = parsePrice(mm[1]);
      if (val > 0) return val;
    }
  }

  // 3) Embedded storefront JSON (Trendyol, Hepsiburada, Zara, Bershka, Pull&Bear, Mavi, Boyner, LCW)
  const embeddedPatterns = [
    /"discountedPrice"\s*:\s*(?:\{[^}]*"value"\s*:\s*)?"?([0-9][0-9.,]{0,14})"?/i,
    /"sellingPrice"\s*:\s*(?:\{[^}]*"value"\s*:\s*)?"?([0-9][0-9.,]{0,14})"?/i,
    /"salePrice"\s*:\s*(?:\{[^}]*"value"\s*:\s*)?"?([0-9][0-9.,]{0,14})"?/i,
    /"currentPrice"\s*:\s*"?([0-9][0-9.,]{0,14})"?/i,
    /"price"\s*:\s*"?([0-9]{2,6}(?:[.,][0-9]{1,2})?)"?/i
  ];
  for (const rx of embeddedPatterns) {
    const em = rx.exec(html);
    if (em) {
      const val = parsePrice(em[1]);
      if (val > 0) return val;
    }
  }

  // 4) Visible currency text fallback (TL / ₺ / $ / € / £)
  const currencyPatterns = [
    /([0-9]{1,3}(?:\.[0-9]{3})+(?:,[0-9]{1,2})?|[0-9]{2,6}(?:,[0-9]{1,2})?)\s*(?:₺|TL\b)/i,
    /(?:₺|\$|€|£)\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?)/
  ];
  for (const rx of currencyPatterns) {
    const cm = rx.exec(html);
    if (cm) {
      const val = parsePrice(cm[1]);
      if (val > 0) return val;
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

export async function fetchProductPrice(rawUrl) {
  const cleanUrl = normalizeProductUrl(rawUrl);
  if (!isSafePublicHttpsUrl(cleanUrl, 1000)) {
    return null;
  }

  for (const buildProxyUrl of CORS_HTML_BRIDGES) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
      const res = await fetch(buildProxyUrl(cleanUrl), {
        method: 'GET',
        mode: 'cors',
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
        cache: 'no-store',
        signal: controller.signal
      });
      if (!res.ok) continue;
      const html = await readTextCapped(res, MAX_HTML_BYTES);
      const price = extractPriceFromHtml(html);
      if (price && price > 0) {
        return price;
      }
    } catch {
      // try next bridge
    } finally {
      clearTimeout(timer);
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
export function applyFetchedPriceToItem(item, fetchedPrice, lang = 'TR') {
  const newPrice = Number(fetchedPrice);
  if (!item || !Number.isFinite(newPrice) || newPrice <= 0) {
    return { updatedItem: item, dropped: false, previousPrice: 0, newPrice: 0, formatted: '' };
  }
  const existingParsed = item.lastCheckedPrice > 0 ? item.lastCheckedPrice : parsePrice(item.priceText);
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
