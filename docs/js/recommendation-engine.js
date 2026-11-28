import {
  CATEGORIES,
  GARMENT_COLORS,
  SEASONS,
  STYLES,
  SUBCATEGORIES,
  TURKISH_CITIES,
  WEATHER_CONDITIONS,
  localizedText
} from './constants.js';
import {
  clampText,
  isSafePublicHttpsUrl,
  safeFetch
} from './security.js';

export { isSafePublicHttpsUrl };

const COLOR_MAP = new Map(GARMENT_COLORS.map((c) => [c.id, c]));
const SUBCAT_MAP = new Map(SUBCATEGORIES.map((s) => [s.id, s]));
const CAT_MAP = new Map(CATEGORIES.map((c) => [c.id, c]));
const STYLE_MAP = new Map(STYLES.map((s) => [s.id, s]));
const SEASON_MAP = new Map(SEASONS.map((s) => [s.id, s]));

export function displayColor(colorId, lang = 'TR') {
  const c = COLOR_MAP.get(colorId);
  return c ? localizedText(lang, c.tr, c.en, c.de, c.fr, c.es, c.zh) : colorId;
}

export function getColorHex(colorId) {
  return COLOR_MAP.get(colorId)?.hex || '#8E908C';
}

export function displaySubCategory(subId, lang = 'TR') {
  const s = SUBCAT_MAP.get(subId);
  return s ? localizedText(lang, s.tr, s.en, s.de, s.fr, s.es, s.zh) : subId;
}

export function displayCategory(catId, lang = 'TR') {
  const c = CAT_MAP.get(catId);
  return c ? localizedText(lang, c.tr, c.en, c.de, c.fr, c.es, c.zh) : catId;
}

export function displayStyle(styleId, lang = 'TR') {
  const s = STYLE_MAP.get(styleId);
  return s ? localizedText(lang, s.tr, s.en, s.de, s.fr, s.es, s.zh) : styleId;
}

export function displaySeason(seasonId, lang = 'TR') {
  const s = SEASON_MAP.get(seasonId);
  return s ? localizedText(lang, s.tr, s.en, s.de, s.fr, s.es, s.zh) : seasonId;
}

export function cleanGarmentTitleForName(colorId, subCategoryId, lang = 'TR') {
  const colorLabel = (displayColor(colorId, lang) || '').split(' /')[0];
  const subLabel = displaySubCategory(subCategoryId, lang);
  return colorLabel ? `${colorLabel} ${subLabel}` : subLabel;
}

export function normalizeProductUrl(raw) {
  const trimmed = (raw || '').trim();
  if (!trimmed) return '';
  if (trimmed.includes(' ') && !trimmed.toLowerCase().startsWith('https://')) {
    const m = trimmed.match(/https:\/\/[^\s<>"'`]+/i);
    if (m) return m[0].replace(/[.,;)\]>\?!]+$/, '');
  }
  return trimmed;
}

export function parsePrice(text) {
  const cleaned = (text || '').trim();
  if (!cleaned) return null;
  const m = cleaned.match(/([0-9][0-9.,]{0,12})/);
  if (!m) return null;
  let num = m[1];
  if (num.includes(',') && num.includes('.')) {
    num = num.lastIndexOf(',') > num.lastIndexOf('.')
      ? num.replaceAll('.', '').replace(',', '.')
      : num.replaceAll(',', '');
  } else if (num.includes(',')) {
    const parts = num.split(',');
    num = (parts[parts.length - 1].length === 3 && parts[0].length <= 3)
      ? num.replaceAll(',', '')
      : num.replace(',', '.');
  } else if (num.includes('.')) {
    const parts = num.split('.');
    if (parts[parts.length - 1].length === 3 && parts.length >= 2) {
      num = num.replaceAll('.', '');
    }
  }
  const v = parseFloat(num);
  return (!Number.isNaN(v) && v > 0 && v < 1000000) ? v : null;
}

export function formatPrice(value, template = '') {
  const hasFraction = Math.abs(value - Math.trunc(value)) > 0.01;
  const formatted = new Intl.NumberFormat('tr-TR', {
    minimumFractionDigits: hasFraction ? 2 : 0,
    maximumFractionDigits: hasFraction ? 2 : 0
  }).format(value);
  if (template.includes('$')) return `$${formatted}`;
  if (template.includes('€')) return `${formatted} €`;
  if (template.includes('£')) return `£${formatted}`;
  return `${formatted} ₺`;
}

// Store Search Links (1:1 port of StoreSearchLinks.kt)
const STORE_NOISE_WORDS = new Set([
  'kombin', 'kombini', 'kombinler', 'stili', 'stil', 'tarz', 'tarzı',
  'veya', 've', 'ile', 'için', 'bir', 'olan', 'gibi',
  'look', 'outfit', 'style', 'or', 'with', 'and', 'the', 'a',
  'minimal', 'sokak', 'street', 'modern', 'günlük', 'casual',
  'temel', 'parça', 'parçalar', 'tonu', 'uyumu', 'dengesi',
  'kontrast', 'vurgusu', 'katman', 'katmanlı', 'yıkama', 'kalıp',
  'rahat', 'düz', 'heavy', 'heavyweight', 'fitted', 'relaxed', 'fit'
]);

const STORE_GENDER_WORDS = new Set([
  'erkek', 'kadın', 'kadin', 'unisex', 'men', 'man', 'women', 'woman', 'male', 'female', 'bay', 'bayan'
]);

function resolveStoreGenderKey(genderPrefix) {
  const g = (genderPrefix || '').trim().toLocaleLowerCase('tr-TR');
  if (!g) return null;
  if (g.startsWith('erkek') || g === 'men' || g === 'man' || g === 'male' || g === 'bay') return 'man';
  if (g.startsWith('kadın') || g.startsWith('kadin') || g === 'women' || g === 'woman' || g === 'female' || g === 'bayan') return 'woman';
  return null;
}

export function cleanItemOnlyQuery(rawQuery, maxKeywords = 3) {
  const primaryClause = (rawQuery || '')
    .split(/[,&+·–—|]+/)
    .map((s) => s.trim())
    .find((s) => s.length > 0) || (rawQuery || '').trim();

  const preCleaned = primaryClause
    .replace(/t-shirt/gi, 'tshirt')
    .replace(/low-top/gi, 'sneaker')
    .replace(/smart-street/gi, 'smart')
    .replace(/-/g, ' ')
    .replace(/[/(),]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  const rawTokens = preCleaned.split(' ')
    .map((t) => t.trim().replace(/^[.:;!?]+|[.:;!?]+$/g, ''))
    .filter((t) => t.length >= 2);

  const filtered = [];
  const seen = new Set();
  for (const tok of rawTokens) {
    const low = tok.toLocaleLowerCase('tr-TR');
    if (STORE_NOISE_WORDS.has(low) || STORE_GENDER_WORDS.has(low)) continue;
    if (!seen.has(low)) {
      seen.add(low);
      filtered.push(tok);
    }
  }

  let kept = filtered;
  if (filtered.length > maxKeywords) {
    kept = maxKeywords <= 1 ? [filtered[filtered.length - 1]] : [...filtered.slice(0, maxKeywords - 1), filtered[filtered.length - 1]];
  } else if (filtered.length === 0) {
    kept = rawTokens.filter((t) => !STORE_GENDER_WORDS.has(t.toLocaleLowerCase('tr-TR'))).slice(0, maxKeywords);
  }

  return kept.join(' ') || primaryClause || (rawQuery || '').trim();
}

export function cleanStoreSearchQuery(genderPrefix, rawQuery, maxKeywords = 3) {
  const itemOnly = cleanItemOnlyQuery(rawQuery, maxKeywords);
  const g = (genderPrefix || '').trim();
  return g ? `${g} ${itemOnly}`.trim() : itemOnly;
}

export function buildStoreSearchLinks(genderPrefix, rawQuery, googleLabel = 'Google Alışveriş') {
  const itemOnly = cleanItemOnlyQuery(rawQuery, 3);
  const clean = cleanStoreSearchQuery(genderPrefix, rawQuery, 3);
  const qPlus = encodeURIComponent(clean).replace(/%20/g, '+');
  const itemPlus = encodeURIComponent(itemOnly).replace(/%20/g, '+');
  const itemPath = encodeURIComponent(itemOnly);
  const genderKey = resolveStoreGenderKey(genderPrefix);

  const zaraUrl = `https://www.zara.com/tr/tr/search?searchTerm=${itemPlus}${genderKey === 'man' ? '&section=MAN' : genderKey === 'woman' ? '&section=WOMAN' : ''}`;
  const bershkaUrl = `https://www.bershka.com/tr/q/${itemPath}${genderKey ? `?gender=${genderKey}` : ''}`;

  return [
    [googleLabel, `https://www.google.com/search?tbm=shop&hl=tr&gl=tr&q=${qPlus}`],
    ['Trendyol', `https://www.trendyol.com/sr?q=${qPlus}`],
    ['Hepsiburada', `https://www.hepsiburada.com/ara?q=${qPlus}`],
    ['Zara', zaraUrl],
    ['Bershka', bershkaUrl],
    ['H&M', `https://www2.hm.com/tr_tr/search-results.html?q=${qPlus}`],
    ['Boyner', `https://www.boyner.com.tr/search?q=${qPlus}`],
    ['Koton', `https://www.koton.com/list/?search_text=${qPlus}`],
    ['DeFacto', `https://www.defacto.com.tr/arama?q=${qPlus}`],
    ['LCW', `https://www.lcw.com/arama?q=${qPlus}`]
  ];
}

export function googleShoppingUrl(genderPrefix, rawQuery) {
  const clean = cleanStoreSearchQuery(genderPrefix, rawQuery, 3);
  return `https://www.google.com/search?tbm=shop&hl=tr&gl=tr&q=${encodeURIComponent(clean).replace(/%20/g, '+')}`;
}

export function trendyolUrl(genderPrefix, rawQuery) {
  const clean = cleanStoreSearchQuery(genderPrefix, rawQuery, 3);
  return `https://www.trendyol.com/sr?q=${encodeURIComponent(clean).replace(/%20/g, '+')}`;
}

// Weather & Forecast Engine (Open-Meteo + Fallback)
function dayLabel(offset, dateObj, lang = 'TR') {
  if (offset === 0) return localizedText(lang, 'Bugün', 'Today', 'Heute', "Aujourd'hui", 'Hoy', '今天');
  if (offset === 1) return localizedText(lang, 'Yarın', 'Tomorrow', 'Morgen', 'Demain', 'Mañana', '明天');
  const dow = dateObj.getDay(); // 0=Sun..6=Sat
  const map = [
    ['Pazar', 'Sunday', 'Sonntag', 'Dimanche', 'Domingo', '星期日'],
    ['Pazartesi', 'Monday', 'Montag', 'Lundi', 'Lunes', '星期一'],
    ['Salı', 'Tuesday', 'Dienstag', 'Mardi', 'Martes', '星期二'],
    ['Çarşamba', 'Wednesday', 'Mittwoch', 'Mercredi', 'Miércoles', '星期三'],
    ['Perşembe', 'Thursday', 'Donnerstag', 'Jeudi', 'Jueves', '星期四'],
    ['Cuma', 'Friday', 'Freitag', 'Vendredi', 'Viernes', '星期五'],
    ['Cumartesi', 'Saturday', 'Samstag', 'Samedi', 'Sábado', '星期六']
  ][dow];
  return localizedText(lang, ...map);
}

function mapWeatherCodeToCondition(code, tempC) {
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 99)) return 'RAINY';
  if (tempC <= 10) return 'COLD';
  if (tempC <= 17) return 'COOL';
  if (tempC <= 24) return 'WARM';
  return 'HOT';
}

export function buildFallbackHourly(
  minTempC = 10,
  maxTempC = 18,
  condition = 'COOL',
  isToday = false,
  currentHour = new Date().getHours()
) {
  const minT = Number.isFinite(Number(minTempC)) ? Number(minTempC) : 10;
  const maxT = Math.max(minT + 1, Number.isFinite(Number(maxTempC)) ? Number(maxTempC) : 18);
  const startH = isToday ? Math.max(0, Math.min(23, Number(currentHour) || 0)) : 0;
  const hours = [];
  for (let step = 0; step < 24; step++) {
    const rawH = startH + step;
    const h = rawH % 24;
    const isNextDay = isToday && rawH >= 24;
    // Coolest around 05:00, warmest around 15:00
    const phase = ((h - 5) / 24) * Math.PI * 2;
    const wave = (1 - Math.cos(phase)) / 2;
    const tempC = Math.round(minT + (maxT - minT) * wave);
    const windKmh = Math.round(condition === 'RAINY' ? 18 + 6 * wave : 9 + 7 * wave);
    const feelsLikeC = Math.round(tempC - (windKmh >= 18 ? 1.5 : 0));
    const rainProb = condition === 'RAINY'
      ? Math.round(55 + 25 * Math.sin((h / 24) * Math.PI))
      : (condition === 'COLD' ? 15 : 5);
    const cond = (condition === 'RAINY' && rainProb >= 40)
      ? 'RAINY'
      : mapWeatherCodeToCondition(0, tempC);
    hours.push({
      hour: h,
      timeLabel: `${String(h).padStart(2, '0')}:00`,
      tempC,
      feelsLikeC,
      rainProb: Math.max(0, Math.min(100, rainProb)),
      windKmh,
      condition: cond,
      isCurrentHour: isToday && step === 0,
      isNextDay
    });
  }
  return hours;
}

export function parseHourlyForDate(
  hourlyObj,
  targetDateStr,
  fallbackMin,
  fallbackMax,
  fallbackCond,
  isToday = false,
  currentHour = new Date().getHours()
) {
  const times = hourlyObj?.time;
  const curH = Math.max(0, Math.min(23, Number(currentHour) || 0));
  if (!Array.isArray(times) || !targetDateStr) {
    return buildFallbackHourly(fallbackMin, fallbackMax, fallbackCond, isToday, curH);
  }
  const temps = hourlyObj.temperature_2m || [];
  const feels = hourlyObj.apparent_temperature || [];
  const rain = hourlyObj.precipitation_probability || [];
  const codes = hourlyObj.weathercode || [];
  const winds = hourlyObj.windspeed_10m || [];
  const list = [];

  if (isToday) {
    let startIdx = times.findIndex((raw) => {
      const tStr = String(raw || '');
      if (!tStr.startsWith(`${targetDateStr}T`)) return false;
      const h = Number.parseInt(tStr.slice(11, 13), 10);
      return Number.isFinite(h) && h >= curH;
    });
    if (startIdx < 0) {
      startIdx = times.findIndex((raw) => String(raw || '').startsWith(`${targetDateStr}T`));
    }
    if (startIdx >= 0) {
      const endIdx = Math.min(times.length, startIdx + 24);
      for (let i = startIdx; i < endIdx; i++) {
        const tStr = String(times[i] || '');
        const hourPart = Number.parseInt(tStr.slice(11, 13), 10);
        if (!Number.isFinite(hourPart) || hourPart < 0 || hourPart > 23) continue;
        const tVal = Math.round(Number(temps[i] ?? fallbackMin ?? 16));
        const flVal = Math.round(Number(feels[i] ?? tVal));
        const rpVal = Math.max(0, Math.min(100, Math.round(Number(rain[i] ?? 0))));
        const wVal = Math.max(0, Math.round(Number(winds[i] ?? 10)));
        const code = Number(codes[i] ?? 0);
        const cond = rpVal >= 55 ? 'RAINY' : mapWeatherCodeToCondition(code, tVal);
        list.push({
          hour: hourPart,
          timeLabel: `${String(hourPart).padStart(2, '0')}:00`,
          tempC: tVal,
          feelsLikeC: flVal,
          rainProb: rpVal,
          windKmh: wVal,
          condition: cond,
          isCurrentHour: i === startIdx,
          isNextDay: !tStr.startsWith(`${targetDateStr}T`)
        });
      }
    }
    return list.length >= 12 ? list.slice(0, 24) : buildFallbackHourly(fallbackMin, fallbackMax, fallbackCond, true, curH);
  }

  for (let i = 0; i < times.length; i++) {
    const tStr = String(times[i] || '');
    if (!tStr.startsWith(`${targetDateStr}T`)) continue;
    const hourPart = Number.parseInt(tStr.slice(11, 13), 10);
    if (!Number.isFinite(hourPart) || hourPart < 0 || hourPart > 23) continue;
    const tVal = Math.round(Number(temps[i] ?? fallbackMin ?? 16));
    const flVal = Math.round(Number(feels[i] ?? tVal));
    const rpVal = Math.max(0, Math.min(100, Math.round(Number(rain[i] ?? 0))));
    const wVal = Math.max(0, Math.round(Number(winds[i] ?? 10)));
    const code = Number(codes[i] ?? 0);
    const cond = rpVal >= 55 ? 'RAINY' : mapWeatherCodeToCondition(code, tVal);
    list.push({
      hour: hourPart,
      timeLabel: `${String(hourPart).padStart(2, '0')}:00`,
      tempC: tVal,
      feelsLikeC: flVal,
      rainProb: rpVal,
      windKmh: wVal,
      condition: cond,
      isCurrentHour: false,
      isNextDay: false
    });
  }
  return list.length >= 12 ? list.slice(0, 24) : buildFallbackHourly(fallbackMin, fallbackMax, fallbackCond, false, 0);
}

export function generateFallbackForecast(lang = 'TR') {
  const defs = [
    ['COOL', 16, 10], ['WARM', 20, 12], ['RAINY', 13, 9],
    ['COOL', 15, 9], ['WARM', 19, 11], ['COLD', 9, 4], ['HOT', 26, 16]
  ];
  const now = new Date();
  const curHour = now.getHours();
  return defs.map(([cond, maxT, minT], offset) => {
    const d = new Date(now.getTime() + offset * 86400000);
    return {
      dayIndex: offset,
      label: dayLabel(offset, d, lang),
      dateText: `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}`,
      maxTempC: maxT,
      minTempC: minT,
      condition: cond,
      isLive: false,
      hourly: buildFallbackHourly(minT, maxT, cond, offset === 0, curHour)
    };
  });
}

const forecastCache = new Map();

const OPEN_METEO_HOSTS = new Set(['api.open-meteo.com']);
const OPEN_METEO_GEO_HOSTS = new Set(['geocoding-api.open-meteo.com']);
const IPWHO_HOSTS = new Set(['ipwho.is']);
const ALLOWED_GEO_LANGS = new Set(['tr', 'en', 'de', 'fr', 'es', 'zh']);

function isValidCoords(lat, lon) {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
}

export async function fetchForecastForCoords(lat, lon, lang = 'TR') {
  const nLat = Number(lat);
  const nLon = Number(lon);
  if (!isValidCoords(nLat, nLon)) return null;

  const key = `${nLat.toFixed(2)},${nLon.toFixed(2)}_${lang}`;
  const cached = forecastCache.get(key);
  if (cached && Date.now() - cached.ts < 20 * 60 * 1000) return cached.days;

  try {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${encodeURIComponent(nLat.toFixed(4))}&longitude=${encodeURIComponent(nLon.toFixed(4))}&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&hourly=temperature_2m,apparent_temperature,precipitation_probability,weathercode,windspeed_10m&timezone=auto&forecast_days=7`;
    const res = await safeFetch(url, { method: 'GET' }, {
      allowedHosts: OPEN_METEO_HOSTS,
      timeoutMs: 10000,
      maxResponseBytes: 1024 * 1024
    });
    if (!res.ok) return null;
    const root = await res.json();
    const daily = root?.daily;
    const hourly = root?.hourly;
    const cur = root?.current_weather;
    if (!Array.isArray(daily?.time)) return null;

    const curHourParsed = Number.parseInt(String(cur?.time || '').slice(11, 13), 10);
    const targetCurHour = Number.isFinite(curHourParsed) ? curHourParsed : new Date().getHours();
    const list = [];
    const count = Math.min(7, daily.time.length);
    for (let i = 0; i < count; i++) {
      const iso = typeof daily.time[i] === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(daily.time[i]) ? daily.time[i] : null;
      const dateObj = iso ? new Date(`${iso}T12:00:00`) : new Date(Date.now() + i * 86400000);
      const maxT = (i === 0 && Number.isFinite(cur?.temperature))
        ? Math.round(cur.temperature)
        : Math.round(Number(daily.temperature_2m_max?.[i] ?? 18));
      const minT = Math.round(Number(daily.temperature_2m_min?.[i] ?? 10));
      const code = Number((i === 0 && cur?.weathercode != null) ? cur.weathercode : (daily.weathercode?.[i] ?? 0));
      const avgFeel = (i === 0 && cur) ? maxT : Math.round((maxT * 2 + minT) / 3);
      const cond = mapWeatherCodeToCondition(code, avgFeel);
      list.push({
        dayIndex: i,
        label: dayLabel(i, dateObj, lang),
        dateText: `${String(dateObj.getDate()).padStart(2, '0')}.${String(dateObj.getMonth() + 1).padStart(2, '0')}`,
        maxTempC: maxT,
        minTempC: minT,
        condition: cond,
        isLive: true,
        hourly: parseHourlyForDate(hourly, iso, minT, maxT, cond, i === 0, targetCurHour)
      });
    }
    if (list.length > 0) {
      forecastCache.set(key, { ts: Date.now(), days: list });
      return list;
    }
    return null;
  } catch {
    return null;
  }
}

export async function searchDestinations(query, lang = 'TR') {
  const clean = clampText(query || '', 80);
  if (clean.length < 2) return [];
  const presets = [
    ...TURKISH_CITIES.map((c) => ({ name: c.label, country: 'Türkiye', lat: c.lat, lon: c.lon })),
    { name: 'Muğla / Bodrum', country: 'Türkiye', lat: 37.0344, lon: 27.4305 },
    { name: 'Çeşme', country: 'Türkiye', lat: 38.3228, lon: 26.3025 },
    { name: 'Kapadokya / Nevşehir', country: 'Türkiye', lat: 38.6244, lon: 34.7239 },
    { name: 'Paris', country: 'France', lat: 48.8566, lon: 2.3522 },
    { name: 'London', country: 'UK', lat: 51.5072, lon: -0.1276 },
    { name: 'Berlin', country: 'Germany', lat: 52.52, lon: 13.405 },
    { name: 'Barcelona', country: 'Spain', lat: 41.3874, lon: 2.1686 },
    { name: 'Rome', country: 'Italy', lat: 41.9028, lon: 12.4964 },
    { name: 'Amsterdam', country: 'Netherlands', lat: 52.3676, lon: 4.9041 },
    { name: 'Dubai', country: 'UAE', lat: 25.2048, lon: 55.2708 }
  ];
  try {
    const rawLang = String(lang || 'tr').toLowerCase();
    const langCode = ALLOWED_GEO_LANGS.has(rawLang) ? rawLang : 'tr';
    const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(clean)}&count=5&language=${langCode}&format=json`;
    const res = await safeFetch(geoUrl, { method: 'GET' }, {
      allowedHosts: OPEN_METEO_GEO_HOSTS,
      timeoutMs: 8000,
      maxResponseBytes: 256 * 1024
    });
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data?.results) && data.results.length > 0) {
        const mapped = data.results
          .slice(0, 5)
          .filter((r) => isValidCoords(Number(r?.latitude), Number(r?.longitude)))
          .map((r) => ({
            name: clampText(r.name, 80),
            country: clampText(r.country || '', 80),
            lat: Number(r.latitude),
            lon: Number(r.longitude)
          }))
          .filter((r) => r.name.length > 0);
        if (mapped.length > 0) return mapped;
      }
    }
  } catch {
    // offline fallback below
  }
  const low = clean.toLowerCase();
  return presets.filter((p) => p.name.toLowerCase().includes(low) || p.country.toLowerCase().includes(low));
}

let placeNames;
function placeKey(name) {
  return name.trim().normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/ı/g, 'i');
}

export async function localPlaceName(name, country, lat, lon) {
  const inTurkey = country ? country.toUpperCase() === 'TR' : lat >= 35.8 && lat <= 42.2 && lon >= 25.6 && lon <= 44.9;
  if (!name || !inTurkey) return name;
  try {
    placeNames ??= fetch(new URL('../data/tr-places.txt', import.meta.url)).then(async (res) => {
      if (!res.ok) throw new Error('Konum adları yüklenemedi');
      const names = (await res.text()).trim().split(/\r?\n/);
      return new Map(names.map((place) => [placeKey(place), place]));
    });
    return (await placeNames).get(placeKey(name)) || name;
  } catch {
    placeNames = null;
    return name;
  }
}

export async function detectBrowserLocationAndForecast(lang = 'TR') {
  let lat = null, lon = null, label = null, country = null;
  if (navigator.geolocation) {
    try {
      const pos = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 4500 });
      });
      if (isValidCoords(pos.coords.latitude, pos.coords.longitude)) {
        lat = pos.coords.latitude;
        lon = pos.coords.longitude;
        const endpoint = new URL('https://api.bigdatacloud.net/data/reverse-geocode-client');
        endpoint.search = new URLSearchParams({ latitude: lat.toFixed(3), longitude: lon.toFixed(3), localityLanguage: 'tr' });
        const response = await safeFetch(endpoint.href, { method: 'GET' }, {
          allowedHosts: new Set([endpoint.hostname]), timeoutMs: 4500, maxResponseBytes: 128 * 1024
        });
        if (response.ok) {
          const place = await response.json();
          label = clampText(place.city || place.locality || place.principalSubdivision || '', 60);
          country = place.countryCode;
        }
      }
    } catch {
      // fallback to IP geo
    }
  }
  if (lat == null || lon == null) {
    try {
      const res = await safeFetch('https://ipwho.is/?lang=tr', { method: 'GET' }, {
        allowedHosts: IPWHO_HOSTS,
        timeoutMs: 6000,
        maxResponseBytes: 128 * 1024
      });
      if (res.ok) {
        const j = await res.json();
        if (j?.success !== false && isValidCoords(j?.latitude, j?.longitude)) {
          lat = Number(j.latitude);
          lon = Number(j.longitude);
          country = j.country_code;
          label = clampText(j.city || '', 60) || null;
        }
      }
    } catch {
      // offline
    }
  }
  if (!isValidCoords(lat, lon)) return null;
  const days = await fetchForecastForCoords(lat, lon, lang);
  if (!days) return null;
  return {
    locationLabel: (await localPlaceName(label, country, lat, lon)) || localizedText(lang, 'Konumum', 'My Location', 'Mein Standort', 'Ma position', 'Mi ubicación', '我的位置'),
    lat,
    lon,
    days
  };
}

// Pairwise Color Harmony Engine (1:1 port of OutfitRecommendationEngine.kt)
function pairHarmonyScore(c1Id, c2Id) {
  if (c1Id === c2Id) return 93;
  const hasBoth = (a, b) => (c1Id === a && c2Id === b) || (c1Id === b && c2Id === a);

  if (hasBoth('NAVY', 'BLACK')) return 58;
  if (hasBoth('BROWN', 'BLACK')) return 54;
  if (hasBoth('BROWN', 'GRAY')) return 62;
  if (hasBoth('RED', 'GREEN')) return 32;
  if (hasBoth('PINK', 'ORANGE')) return 35;
  if (hasBoth('PINK', 'RED')) return 38;
  if (hasBoth('YELLOW', 'PINK')) return 41;
  if (hasBoth('PURPLE', 'ORANGE')) return 36;
  if (hasBoth('PURPLE', 'YELLOW')) return 42;
  if (hasBoth('BURGUNDY', 'GREEN')) return 44;
  if (hasBoth('ORANGE', 'COBALT')) return 52;
  if (hasBoth('TERRACOTTA', 'PINK')) return 45;

  if (hasBoth('NAVY', 'BEIGE') || hasBoth('NAVY', 'ECRU') || hasBoth('NAVY', 'WHITE') || hasBoth('NAVY', 'CAMEL')) return 95;
  if (hasBoth('BLACK', 'WHITE') || hasBoth('BLACK', 'ECRU') || hasBoth('BLACK', 'GRAY') || hasBoth('BLACK', 'BEIGE') || hasBoth('BLACK', 'OLIVE')) return 94;
  if (hasBoth('DENIM', 'WHITE') || hasBoth('DENIM', 'ECRU') || hasBoth('DENIM', 'CAMEL') || hasBoth('DENIM', 'BLACK')) return 92;
  if (hasBoth('BLUSH', 'GRAY') || hasBoth('BLUSH', 'LIGHT_GRAY') || hasBoth('BLUSH', 'WHITE') || hasBoth('BLUSH', 'NAVY') || hasBoth('PINK', 'LIGHT_GRAY') || hasBoth('PINK', 'DENIM')) return 89;

  const c1 = COLOR_MAP.get(c1Id);
  const c2 = COLOR_MAP.get(c2Id);
  if (!c1 || !c2) return 80;
  if (c1.isNeutral && c2.isNeutral) return 86;
  if (c1.isNeutral || c2.isNeutral) {
    const neu = c1.isNeutral ? c1Id : c2Id;
    const chr = c1.isNeutral ? c2 : c1;
    if (['WHITE', 'ECRU', 'LIGHT_GRAY', 'GRAY'].includes(neu)) return 88;
    if (['BLACK', 'CHARCOAL'].includes(neu)) return (chr.hueGroup === 'BLUE' && chr.id === 'COBALT') ? 76 : 85;
    if (['BEIGE', 'STONE'].includes(neu)) return (chr.hueGroup === 'WARM_ACCENT' && chr.id === 'PINK') ? 68 : 86;
    return 78;
  }
  if (c1.hueGroup === c2.hueGroup) return 74;
  if ((c1.hueGroup === 'BLUE' && c2.hueGroup === 'EARTH') || (c2.hueGroup === 'BLUE' && c1.hueGroup === 'EARTH')) return 84;
  if ((c1.hueGroup === 'GREEN' && c2.hueGroup === 'EARTH') || (c2.hueGroup === 'GREEN' && c1.hueGroup === 'EARTH')) return 83;
  if ((c1.hueGroup === 'BLUE' && c2Id === 'MUSTARD') || (c2.hueGroup === 'BLUE' && c1Id === 'MUSTARD')) return 82;
  return 52;
}

export function calculateColorHarmony(items, lang = 'TR') {
  if (!items || items.length === 0) {
    return { score: 80, note: localizedText(lang, 'Parça Seçilmedi', 'No Pieces Selected', 'Keine Teile', 'Aucune pièce', 'Sin prendas', '未选择单品') };
  }
  const primary = items.map((it) => it.colors?.[0]).filter(Boolean);
  if (primary.length <= 1) {
    return { score: 94, note: localizedText(lang, 'Monokrom Ton Uyumu', 'Monochrome Tone Harmony', 'Monochrome Harmonie', 'Harmonie monochrome', 'Armonía monocromática', '单色和谐搭配') };
  }
  const distinct = [...new Set(primary)];
  if (distinct.length === 1) {
    return { score: 95, note: localizedText(lang, 'Tek Renk Ton Uyumu', 'Single-Tone Harmony', 'Einton-Harmonie', 'Harmonie ton sur ton', 'Armonía de un tono', '同色系和谐搭配') };
  }

  let total = 0, cnt = 0, minPair = 100;
  for (let i = 0; i < primary.length; i++) {
    for (let j = i + 1; j < primary.length; j++) {
      const s = pairHarmonyScore(primary[i], primary[j]);
      total += s; cnt++;
      if (s < minPair) minPair = s;
    }
  }
  let score = cnt > 0 ? Math.round((total / cnt) * 0.65 + minPair * 0.35) : 85;
  const saturatedCount = distinct.filter((id) => COLOR_MAP.get(id)?.isNeutral === false).length;
  if (saturatedCount >= 3) score -= (saturatedCount - 2) * 14;

  const styles = [...new Set(items.map((i) => i.styleTag))];
  if (styles.length === 1) score += 2;
  else if (styles.includes('SPORT') && styles.includes('FORMAL')) score -= 8;

  const finalScore = Math.min(98, Math.max(26, score));
  const note = finalScore >= 90
    ? localizedText(lang, 'Yüksek Renk Uyumu', 'High Color Harmony', 'Hohe Farbharmonie', 'Forte harmonie', 'Alta armonía', '高度色彩和谐')
    : finalScore >= 78
      ? localizedText(lang, 'Dengeli Kombin Uyumu', 'Balanced Outfit Harmony', 'Ausgewogene Harmonie', 'Harmonie équilibrée', 'Armonía equilibrada', '均衡穿搭协调')
      : finalScore >= 64
        ? localizedText(lang, 'Orta Düzey Kontrast', 'Moderate Contrast', 'Mittlerer Kontrast', 'Contraste modéré', 'Contraste moderado', '适度色彩对比')
        : finalScore >= 48
          ? localizedText(lang, 'Zorlayıcı Renk Eşleşmesi', 'Challenging Color Pairing', 'Mutige Kombination', 'Association audacieuse', 'Combinación atrevida', '个性撞色搭配')
          : localizedText(lang, 'Çarpışan Renk Tonları', 'Clashing Color Tones', 'Starker Farbkontrast', 'Couleurs contrastées', 'Tonos en contraste', '色调冲突较强');
  return { score: finalScore, note };
}

export function sortTopToBottom(items) {
  const rank = { ACCESSORY: 0, OUTERWEAR: 1, TOP: 2, SUIT: 3, BOTTOM: 4, SHOES: 5 };
  return [...items].sort((a, b) => (rank[a.category] ?? 9) - (rank[b.category] ?? 9));
}

export function inferWardrobeGender(items) {
  if (!items || items.length === 0) return 'UNISEX';
  let fem = 0, masc = 0;
  const femKeys = ['etek', 'elbise', 'bluz', 'crop', 'topuklu', 'babet', 'tayt', 'askılı', 'straplez', 'kadın', 'mini'];
  const mascKeys = ['kravat', 'erkek', 'damatlık', 'oxford', 'klasik kesim', 'kargo', 'bomber'];
  for (const item of items) {
    const txt = `${item.name || ''} ${item.notes || ''}`.toLowerCase();
    if (['SKIRT', 'DRESS', 'HEELS', 'CROP_TOP'].includes(item.subCategory) || femKeys.some((k) => txt.includes(k))) fem += 2;
    if (['SUIT_SET', 'POLO', 'CHINO'].includes(item.subCategory) || mascKeys.some((k) => txt.includes(k))) masc += 2;
    if ((item.colors || []).some((c) => c === 'BLUSH' || c === 'PINK')) fem += 1;
  }
  if (fem >= masc + 2) return 'FEMALE';
  if (masc >= fem + 2) return 'MALE';
  return 'UNISEX';
}

function weatherScore(item, weatherId) {
  const sub = item.subCategory;
  const warmth = SUBCAT_MAP.get(sub)?.warmth ?? 2;
  let score = 4;
  if (weatherId === 'HOT') {
    if (['TSHIRT', 'POLO', 'TANK', 'CROP_TOP', 'SKIRT', 'DRESS', 'SHORTS', 'SLIPPER', 'GLASSES'].includes(sub)) score = 16;
    else if (['SHIRT', 'SNEAKER', 'HEELS'].includes(sub)) score = 8;
    else if (['COAT', 'SWEATER', 'BOOT', 'SCARF', 'HOODIE'].includes(sub)) score = -25;
    else score = warmth <= 2 ? 6 : -10;
  } else if (weatherId === 'WARM') {
    if (['TSHIRT', 'POLO', 'SHIRT', 'CROP_TOP', 'SKIRT', 'DRESS', 'CHINO', 'JEANS', 'SNEAKER', 'LOAFER', 'HEELS'].includes(sub)) score = 14;
    else if (['SWEATSHIRT', 'CARDIGAN'].includes(sub)) score = 6;
    else if (['COAT', 'BOOT', 'SCARF'].includes(sub)) score = -20;
  } else if (weatherId === 'COOL') {
    if (['HOODIE', 'SWEATSHIRT', 'CARDIGAN', 'JACKET', 'VEST', 'PANTS', 'JEANS', 'CARGO', 'SWEATPANTS', 'SNEAKER'].includes(sub)) score = 15;
    else if (['SHIRT', 'BLAZER', 'TRENCH'].includes(sub)) score = 10;
    else if (['SHORTS', 'SLIPPER', 'TANK'].includes(sub)) score = -22;
  } else if (weatherId === 'RAINY') {
    if (['JACKET', 'TRENCH', 'COAT', 'BOOT', 'HOODIE', 'SWEATSHIRT', 'JEANS', 'PANTS'].includes(sub)) score = 16;
    else if (['SHORTS', 'SLIPPER', 'TANK'].includes(sub)) score = -28;
    else score = 5;
  } else if (weatherId === 'COLD') {
    if (['SWEATER', 'HOODIE', 'CARDIGAN', 'COAT', 'JACKET', 'VEST', 'BOOT', 'SCARF', 'PANTS', 'JEANS'].includes(sub)) score = 18;
    else if (['SHORTS', 'SLIPPER', 'TANK'].includes(sub)) score = -32;
    else if (sub === 'TSHIRT') score = -4;
  }
  const prefSeason = WEATHER_CONDITIONS[weatherId]?.preferredSeason;
  if (prefSeason && (item.seasons || []).includes(prefSeason)) score += 6;
  return score;
}

export function suggestOutfits({
  items,
  weather = 'COOL',
  targetDayLabel = '',
  recentSignatures = [],
  lastUsedPrimaryIds = new Set(),
  count = 3,
  lang = 'TR',
  genderPref = 'UNISEX',
  flatteringColors = new Set()
}) {
  if (!items || items.length < 2) return [];
  const femSubs = new Set(['SKIRT', 'DRESS', 'HEELS', 'CROP_TOP']);
  const pool = genderPref === 'MALE'
    ? (items.filter((i) => !femSubs.has(i.subCategory)).length >= 2 ? items.filter((i) => !femSubs.has(i.subCategory)) : items)
    : items;

  const tops = pool.filter((i) => i.category === 'TOP');
  const bottoms = pool.filter((i) => i.category === 'BOTTOM');
  const outer = pool.filter((i) => i.category === 'OUTERWEAR');
  const shoes = pool.filter((i) => i.category === 'SHOES');
  const accs = pool.filter((i) => i.category === 'ACCESSORY');
  const suits = pool.filter((i) => i.category === 'SUIT');

  const candidates = [];
  const scoreCandidate = (candItems) => {
    const signature = candItems.map((i) => i.id).sort().join('-');
    const { score: harmonyScore, note: harmonyNote } = calculateColorHarmony(candItems, lang);
    const avgWeatherFit = Math.round(candItems.reduce((s, it) => s + weatherScore(it, weather), 0) / Math.max(1, candItems.length));
    let bonus = 0;
    if (flatteringColors.size > 0) {
      bonus += candItems.filter((i) => i.colors?.[0] && flatteringColors.has(i.colors[0])).length * 3;
    }
    let penalty = recentSignatures.includes(signature) ? 38 : 0;
    penalty += candItems.filter((i) => lastUsedPrimaryIds.has(i.id)).length * 11;
    const totalScore = harmonyScore + avgWeatherFit + bonus + Math.floor(Math.random() * 6) - penalty;
    return { items: candItems, signature, harmonyScore, harmonyNote, totalScore };
  };

  if (tops.length > 0 && bottoms.length > 0) {
    const topPool = [...tops].sort((a, b) => weatherScore(b, weather) - weatherScore(a, weather)).slice(0, 10);
    const botPool = [...bottoms].sort((a, b) => weatherScore(b, weather) - weatherScore(a, weather)).slice(0, 8);
    const outPool = [...outer].sort((a, b) => weatherScore(b, weather) - weatherScore(a, weather)).slice(0, 6);
    const shoePool = [...shoes].sort((a, b) => weatherScore(b, weather) - weatherScore(a, weather)).slice(0, 5);

    for (const t of topPool) {
      for (const b of botPool) {
        const list = [t, b];
        if (outPool.length > 0 && ['COOL', 'RAINY', 'COLD'].includes(weather)) {
          const bestO = [...outPool].sort((x, y) =>
            (calculateColorHarmony([...list, y], lang).score + weatherScore(y, weather)) -
            (calculateColorHarmony([...list, x], lang).score + weatherScore(x, weather))
          )[0];
          if (bestO) list.push(bestO);
        }
        if (shoePool.length > 0) {
          const bestS = [...shoePool].sort((x, y) =>
            (calculateColorHarmony([...list, y], lang).score + weatherScore(y, weather)) -
            (calculateColorHarmony([...list, x], lang).score + weatherScore(x, weather))
          )[0];
          if (bestS) list.push(bestS);
        }
        if (accs.length > 0 && Math.random() < 0.45) {
          const goodAcc = accs.filter((a) => weatherScore(a, weather) >= 0);
          if (goodAcc.length > 0) list.push(goodAcc[Math.floor(Math.random() * goodAcc.length)]);
        }
        candidates.push(scoreCandidate(list));
      }
    }
  }

  for (const s of suits) {
    const list = [s];
    if (shoes.length > 0) {
      const sh = [...shoes].sort((a, b) => weatherScore(b, weather) - weatherScore(a, weather))[0];
      if (sh) list.push(sh);
    }
    if (list.length >= 2) candidates.push(scoreCandidate(list));
  }

  if (candidates.length === 0) {
    for (let i = 0; i < 6; i++) {
      const sample = [...pool].sort(() => Math.random() - 0.5).slice(0, Math.min(3, pool.length));
      candidates.push(scoreCandidate(sample));
    }
  }

  candidates.sort((a, b) => b.totalScore - a.totalScore);
  const chosen = [];
  const leadIds = new Set();
  for (const cand of candidates) {
    const leadId = cand.items[0]?.id;
    if (chosen.some((c) => c.signature === cand.signature)) continue;
    if (!leadIds.has(leadId) || candidates.length <= count) {
      chosen.push(cand);
      if (leadId) leadIds.add(leadId);
    }
    if (chosen.length >= count) break;
  }
  if (chosen.length < count) {
    for (const cand of candidates) {
      if (!chosen.some((c) => c.signature === cand.signature)) {
        chosen.push(cand);
        if (chosen.length >= count) break;
      }
    }
  }

  const wMeta = WEATHER_CONDITIONS[weather] || WEATHER_CONDITIONS.COOL;
  const wHint = lang === 'TR' ? wMeta.trHint : lang === 'ZH' ? wMeta.zhHint : wMeta.enHint;
  return chosen.map((c, idx) => ({
    title: localizedText(
      lang,
      `${wMeta.emoji} ${targetDayLabel} İçin Kombin #${idx + 1}`,
      `${wMeta.emoji} Outfit for ${targetDayLabel} #${idx + 1}`,
      `${wMeta.emoji} Outfit für ${targetDayLabel} #${idx + 1}`,
      `${wMeta.emoji} Tenue pour ${targetDayLabel} #${idx + 1}`,
      `${wMeta.emoji} Outfit para ${targetDayLabel} #${idx + 1}`,
      `${wMeta.emoji} ${targetDayLabel}穿搭 #${idx + 1}`
    ),
    items: sortTopToBottom(c.items),
    harmonyScore: c.harmonyScore,
    harmonyNote: c.harmonyNote,
    weatherReason: wHint
  }));
}

export function generateTravelPlan({
  items,
  destinationLabel,
  forecasts,
  tripDays,
  lang = 'TR',
  genderPref = 'UNISEX',
  flatteringColors = new Set()
}) {
  if (!items || items.length < 2 || !forecasts || forecasts.length === 0) {
    return { destinationLabel, dayPlans: [], packingChecklist: [] };
  }
  const daysCount = Math.min(7, Math.max(2, tripDays));
  const activeDays = Array.from({ length: daysCount }, (_, i) => forecasts[i] || forecasts[forecasts.length - 1]);
  const domCond = activeDays[0].condition;

  const bottoms = items.filter((i) => i.category === 'BOTTOM').sort((a, b) => weatherScore(b, domCond) - weatherScore(a, domCond)).slice(0, daysCount <= 3 ? 2 : 3);
  const outer = items.filter((i) => i.category === 'OUTERWEAR').sort((a, b) => weatherScore(b, domCond) - weatherScore(a, domCond)).slice(0, activeDays.every((d) => d.condition === 'HOT') ? 0 : 2);
  const shoes = items.filter((i) => i.category === 'SHOES').sort((a, b) => weatherScore(b, domCond) - weatherScore(a, domCond)).slice(0, daysCount >= 5 ? 2 : 1);
  const basePool = [...items.filter((i) => ['TOP', 'SUIT', 'ACCESSORY'].includes(i.category)), ...bottoms, ...outer, ...shoes];
  const travelPool = basePool.length >= 2 ? [...new Map(basePool.map((x) => [x.id, x])).values()] : items;

  const recentSignatures = [];
  const lastPrimary = new Set();
  const dayPlans = [];
  activeDays.forEach((df, idx) => {
    const dayLbl = localizedText(lang, `${idx + 1}. Gün`, `Day ${idx + 1}`, `Tag ${idx + 1}`, `Jour ${idx + 1}`, `Día ${idx + 1}`, `第${idx + 1}天`);
    const pick = suggestOutfits({
      items: travelPool,
      weather: df.condition,
      targetDayLabel: `${dayLbl} · ${df.maxTempC}°C`,
      recentSignatures,
      lastUsedPrimaryIds: lastPrimary,
      count: 1,
      lang,
      genderPref,
      flatteringColors
    })[0];
    if (pick) {
      dayPlans.push({ dayNumber: idx + 1, forecast: df, outfit: pick });
      pick.items.slice(0, 2).forEach((it) => lastPrimary.add(it.id));
    }
  });

  const checklistMap = new Map();
  dayPlans.forEach((dp) => dp.outfit.items.forEach((it) => checklistMap.set(it.id, it)));
  return {
    destinationLabel,
    dayPlans,
    packingChecklist: sortTopToBottom([...checklistMap.values()])
  };
}

// 3-Tier Never-Ending Missing Capsule Pieces + 24 Colors (1:1 port of PaletteStatsScreen.kt)
export function analyzeMissingCapsulePieces(items, lang = 'TR', genderPref = 'UNISEX', personalProfile = null) {
  const isTr = lang === 'TR';
  const topsCount = Math.max(1, items.filter((i) => i.category === 'TOP').length);
  const bottomsCount = Math.max(1, items.filter((i) => i.category === 'BOTTOM').length);
  const gaps = [];

  if (!items.some((i) => ['TSHIRT', 'POLO', 'CROP_TOP'].includes(i.subCategory) && ((i.colors || []).includes('WHITE') || (i.colors || []).includes('ECRU')))) {
    gaps.push({
      title: isTr ? 'Beyaz veya Ekru Basic Tişört' : 'White or Ecru Basic T-Shirt',
      unlocked: isTr ? `Mevcut ${bottomsCount} alt giyiminle +${bottomsCount} yeni kombin açar` : `Unlocks +${bottomsCount} new combos with your bottoms`,
      description: isTr ? 'Katmanlamanın ve günlük kombinlerin en kurtarıcı temel parçası.' : 'The ultimate everyday layering foundation.',
      recommendedColors: ['WHITE', 'ECRU', 'CHAMPAGNE'],
      searchQuery: isTr ? 'beyaz oversize basic tişört' : 'white heavyweight basic t-shirt'
    });
  }
  if (!items.some((i) => i.subCategory === 'SHIRT')) {
    gaps.push({
      title: isTr ? 'Düğmeli Gömlek (Beyaz / Mavi / Lacivert)' : 'Button-Down Shirt (White / Sky / Navy)',
      unlocked: isTr ? `Alt parçalarınla +${Math.max(2, bottomsCount)} kombin seçeneği ekler` : `Adds +${Math.max(2, bottomsCount)} smart styling options`,
      description: isTr ? 'Hem açık önlü tişört üstü katman hem smart casual görünüm için idealdir.' : 'Works buttoned up or worn open over a tee.',
      recommendedColors: ['WHITE', 'ICE_BLUE', 'NAVY', 'PETROL'],
      searchQuery: isTr ? 'poplin veya oxford gömlek' : 'oxford button down shirt'
    });
  }
  if (!items.some((i) => ['HOODIE', 'SWEATSHIRT'].includes(i.subCategory))) {
    gaps.push({
      title: isTr ? 'Hoodie veya Sweatshirt' : 'Hoodie or Sweatshirt',
      unlocked: isTr ? 'Serin hava sokak stili kombinlerini artırır' : 'Boosts cool-weather streetwear outfits',
      description: isTr ? "Kot, kargo ve eşofman altlarıyla zahmetsiz sokak stili oluşturur." : 'Pairs effortlessly with denim, cargo pants, and sneakers.',
      recommendedColors: ['BLACK', 'GRAY', 'ECRU', 'FOREST'],
      searchQuery: isTr ? 'oversize hoodie sweatshirt' : 'oversize cotton hoodie'
    });
  }
  if (!items.some((i) => ['SWEATER', 'CARDIGAN'].includes(i.subCategory))) {
    gaps.push({
      title: isTr ? 'Triko Kazak veya Hırka' : 'Knit Sweater or Cardigan',
      unlocked: isTr ? 'Sonbahar ve kış şıklığını dengeler' : 'Elevates autumn & winter smart layering',
      description: isTr ? 'Gömlek veya basic tişört üstüne sıcak ve temiz bir doku katar.' : 'Adds refined warmth over tees or shirts.',
      recommendedColors: ['SAND', 'CHARCOAL', 'OLIVE', 'WINE'],
      searchQuery: isTr ? 'triko kazak hırka' : 'knit sweater cardigan'
    });
  }
  if (!items.some((i) => i.subCategory === 'JEANS')) {
    gaps.push({
      title: isTr ? 'Mavi veya Antrasit Kot / Jean' : 'Blue or Charcoal Denim Jeans',
      unlocked: isTr ? `Üst giyimlerinle +${topsCount} kombin oluşturur` : `Pairs cleanly with your ${topsCount} tops`,
      description: isTr ? '4 mevsim her renk tişört, gömlek ve ceketle eşleşen ana alt parça.' : 'All-season anchor matching every top and jacket.',
      recommendedColors: ['DENIM', 'SLATE_BLUE', 'BLACK', 'CHARCOAL'],
      searchQuery: isTr ? 'straight jean pantolon' : 'straight fit denim jeans'
    });
  }
  if (!items.some((i) => ['CHINO', 'PANTS'].includes(i.subCategory) && ['BEIGE', 'SAND', 'STONE', 'CAMEL', 'BLACK'].some((c) => (i.colors || []).includes(c)))) {
    gaps.push({
      title: isTr ? 'Kum Beji / Taş veya Siyah Kumaş Pantolon' : 'Sand / Stone or Black Trousers',
      unlocked: isTr ? `Koyu üstleri dengeleyip +${Math.max(2, topsCount)} kombin açar` : 'Balances dark tops cleanly',
      description: isTr ? 'Siyah ve koyu lacivert üstlerle yüksek kontrastlı modern görünüm sağlar.' : 'Clean high-contrast silhouette with dark tops.',
      recommendedColors: ['SAND', 'BEIGE', 'STONE', 'BLACK'],
      searchQuery: isTr ? 'bej kumaş pantolon chino' : 'beige relaxed chino trousers'
    });
  }
  if (!items.some((i) => i.subCategory === 'CARGO')) {
    gaps.push({
      title: isTr ? 'Kargo Pantolon' : 'Cargo Pants',
      unlocked: isTr ? 'Sokak stili görünümünü çeşitlendirir' : 'Expands casual & street outfits',
      description: isTr ? "Hoodie, bomber ve sneaker'larla hacimli sokak stili sunar." : 'Pairs with hoodies, bombers, and sneakers.',
      recommendedColors: ['OLIVE', 'SAND', 'BLACK'],
      searchQuery: isTr ? 'kargo pantolon' : 'relaxed cargo pants'
    });
  }
  if (!items.some((i) => ['JACKET', 'BLAZER', 'VEST'].includes(i.subCategory))) {
    gaps.push({
      title: isTr ? 'Günlük Ceket veya Overshirt' : 'Everyday Jacket or Overshirt',
      unlocked: isTr ? 'Serin günlerin dış giyim katmanını tamamlar' : 'Completes transitional cool-day outfits',
      description: isTr ? 'Deri, denim veya bomber kesimli bir ceket sade tişörtleri anında yükseltir.' : 'Denim or bomber jackets instantly upgrade tees.',
      recommendedColors: ['CHOCOLATE', 'BLACK', 'DENIM', 'OLIVE'],
      searchQuery: isTr ? 'bomber ceket denim ceket' : 'bomber denim jacket'
    });
  }
  if (!items.some((i) => ['COAT', 'TRENCH'].includes(i.subCategory))) {
    gaps.push({
      title: isTr ? 'Trençkot veya Kışlık Mont / Kaban' : 'Trench Coat or Winter Coat',
      unlocked: isTr ? 'Yağmurlu & soğuk hava önerilerini eksiksiz yapar' : 'Completes rainy & cold weather picks',
      description: isTr ? 'Soğuk ve yağışlı günlerde şıklığı ve korumayı bir arada tutar.' : 'Keeps layered outfits warm and dry.',
      recommendedColors: ['BEIGE', 'CHOCOLATE', 'NAVY', 'CHARCOAL'],
      searchQuery: isTr ? 'trençkot kaban mont' : 'trench coat wool overcoat'
    });
  }
  if (!items.some((i) => i.subCategory === 'SNEAKER')) {
    gaps.push({
      title: isTr ? 'Temiz Beyaz veya Siyah Sneaker' : 'Clean White or Black Sneakers',
      unlocked: isTr ? 'Tüm günlük kombinleri eksiksiz tamamlar' : 'Finishes everyday outfits cleanly',
      description: isTr ? "Jean'den kumaş pantolona kadar her alt giyime uyan sade sneaker." : 'Versatile minimalist sneakers.',
      recommendedColors: ['WHITE', 'BLACK', 'ECRU'],
      searchQuery: isTr ? 'beyaz deri sneaker' : 'white minimalist leather sneakers'
    });
  }
  if (!items.some((i) => ['BOOT', 'LOAFER', 'HEELS'].includes(i.subCategory))) {
    gaps.push({
      title: isTr ? 'Bot veya Loafer / Klasik Ayakkabı' : 'Boots or Loafers',
      unlocked: isTr ? 'Kışlık ve smart casual kombinleri tamamlar' : 'Finishes winter & smart-casual looks',
      description: isTr ? 'Yağmurlu havalarda ve şık kombinlerde güçlü alternatif.' : 'Refined alternative for wet or smart days.',
      recommendedColors: ['BLACK', 'CHOCOLATE'],
      searchQuery: isTr ? 'chelsea bot loafer ayakkabı' : 'chelsea boots loafers'
    });
  }
  if (genderPref === 'FEMALE') {
    if (!items.some((i) => i.subCategory === 'BLAZER')) {
      gaps.push({
        title: isTr ? 'Oversize Blazer Ceket' : 'Oversized Blazer',
        unlocked: isTr ? 'Smart Street görünümü güçlendirir' : 'Elevates Smart Street styling',
        description: isTr ? 'Tişört, crop veya gömlek üzerine atılabilen kurtarıcı ceket.' : 'Sharp blazer styled over tees or dresses.',
        recommendedColors: ['BLACK', 'SAND', 'NAVY', 'SLATE_BLUE'],
        searchQuery: isTr ? 'oversize blazer ceket' : 'women oversized blazer'
      });
    }
    if (!items.some((i) => ['DRESS', 'SKIRT'].includes(i.subCategory))) {
      gaps.push({
        title: isTr ? 'Elbise veya Etek' : 'Versatile Dress or Skirt',
        unlocked: isTr ? 'Tek parça şık veya katmanlı kombin açar' : 'Unlocks effortless one-piece looks',
        description: isTr ? 'Sneaker, bot ve ceketlerle kolayca uyarlanır.' : 'Adaptable for sneakers, boots or blazers.',
        recommendedColors: ['BLACK', 'WINE', 'ROSEWOOD', 'OLIVE'],
        searchQuery: isTr ? 'midi elbise etek' : 'midi dress pleated skirt'
      });
    }
  }

  // Tier 3 continuous smart expansions
  const skinSet = new Set(personalProfile?.flatteringColors || []);
  const tier3 = [
    ['ECRU', 'HOODIE', isTr ? 'Ekru Oversize Hoodie' : 'Ecru Oversized Hoodie'],
    ['OLIVE', 'JACKET', isTr ? 'Haki Overshirt / Utility Ceket' : 'Olive Utility Overshirt'],
    ['ICE_BLUE', 'SHIRT', isTr ? 'Buz Mavisi Oxford Gömlek' : 'Ice Blue Oxford Shirt'],
    ['CHOCOLATE', 'JACKET', isTr ? 'Çikolata Kahve Deri / Süet Ceket' : 'Chocolate Suede Jacket'],
    ['SAND', 'CARGO', isTr ? 'Kum Beji Relaxed Kargo' : 'Sand Relaxed Cargo Trousers'],
    ['FOREST', 'SWEATER', isTr ? 'Orman Yeşili Triko Kazak' : 'Forest Green Knit Sweater'],
    ['PETROL', 'SHIRT', isTr ? 'Petrol Yeşili Oversize Gömlek' : 'Petrol Overshirt / Shirt'],
    ['WINE', 'SWEATSHIRT', isTr ? 'Şarap Bordo Heavy Sweatshirt' : 'Wine Heavyweight Sweatshirt'],
    ['SLATE_BLUE', 'BLAZER', isTr ? 'Arduvaz Mavi Smart Blazer' : 'Slate Blue Casual Blazer'],
    ['CHAMPAGNE', 'TSHIRT', isTr ? 'Şampanya Tonu Basic Tişört' : 'Champagne Heavyweight Tee'],
    ['ROSEWOOD', 'CARDIGAN', isTr ? 'Gül Kurusu Yumuşak Hırka' : 'Rosewood Textured Cardigan'],
    ['RUST', 'JACKET', isTr ? 'Pas Turuncu Mevsimlik Ceket' : 'Rust Transitional Jacket']
  ].sort((a, b) => (skinSet.has(b[0]) ? 1 : 0) - (skinSet.has(a[0]) ? 1 : 0));

  for (const [col, sub, title] of tier3) {
    if (gaps.length >= 12) break;
    if (genderPref === 'MALE' && ['DRESS', 'SKIRT', 'CROP_TOP', 'HEELS'].includes(sub)) continue;
    const owned = items.some((i) => i.subCategory === sub && (i.colors || []).includes(col));
    if (!owned) {
      const skinMatch = skinSet.has(col);
      gaps.push({
        title,
        unlocked: skinMatch
          ? (isTr ? '✨ Ten tonunla tam uyumlu · yeni stil kombinleri açar' : '✨ Skin-tone flattering pick · unlocks fresh combos')
          : (isTr ? `Mevcut dolabınla +${Math.max(3, bottomsCount)} yeni renk uyumu sağlar` : `Adds +${Math.max(3, bottomsCount)} fresh color pairings`),
        description: isTr
          ? `Dolabını ${displayColor(col, lang)} tonlu ${displaySubCategory(sub, lang)} ile zenginleştir.`
          : `Completing your closet with a ${displayColor(col, lang)} ${displaySubCategory(sub, lang)} adds variety.`,
        recommendedColors: [col],
        searchQuery: `${displayColor(col, lang)} ${displaySubCategory(sub, lang)}`
      });
    }
  }
  return gaps;
}

export function suggestMissingHarmoniousColors(items, lang = 'TR') {
  const isEn = lang !== 'TR';
  const existing = new Set(items.flatMap((i) => i.colors || []));
  const candidates = [
    ['BEIGE', isEn ? 'Matches black, navy & olive effortlessly' : 'Siyah, lacivert ve hakiyle yüksek uyum sağlar'],
    ['ECRU', isEn ? 'Softens dark garments with clean contrast' : 'Koyu parçaları yumuşatıp temiz kontrast yaratır'],
    ['SAND', isEn ? 'Modern neutral base for warm & cool tops' : 'Tüm üstlerle çalışan modern kum beji nötr ton'],
    ['NAVY', isEn ? 'Pairs cleanly with gray, white & beige' : 'Gri, beyaz ve bej tonlarla kolay eşleşir'],
    ['OLIVE', isEn ? 'Natural street harmony with black & denim' : 'Siyah, ekru ve denimle doğal sokak uyumu verir'],
    ['CHOCOLATE', isEn ? 'Quiet luxury depth with cream & ice blue' : 'Ekru, buz mavisi ve camel ile zengin görünüm sunar'],
    ['FOREST', isEn ? 'Deep botanical green elevating beige & gray' : 'Bej ve gri tonları derinleştiren şık orman yeşili'],
    ['SLATE_BLUE', isEn ? 'Crisp architectural blue pairing with neutrals' : 'Nötrlerle çok şık duran modern arduvaz mavisi'],
    ['ICE_BLUE', isEn ? 'Fresh airy contrast under navy & gray' : 'Lacivert, gri ve kahve altında ferah aydınlık ton'],
    ['WINE', isEn ? 'Statement burgundy accent with gray & denim' : 'Gri, ekru ve denimle dikkat çeken asil şarap bordo'],
    ['TERRACOTTA', isEn ? 'Sun-baked warmth boosting navy & cream' : 'Lacivert ve krem kombinlere sıcak enerji katar'],
    ['PETROL', isEn ? 'Sophisticated blue-green depth for smart fits' : 'Smart-street stillere derinlik katan petrol yeşili'],
    ['SAGE', isEn ? 'Calm earthy green matching sand & white' : 'Kum beji ve beyazla çok doğal duran adaçayı yeşili'],
    ['CHAMPAGNE', isEn ? 'Soft satin highlight replacing stark white' : 'Sert beyaz yerine yumuşak, şık şampanya tonu'],
    ['RUST', isEn ? 'Autumn accent popping over black & olive' : 'Siyah ve hakiyle harika kontrast kuran pas turuncu'],
    ['ROSEWOOD', isEn ? 'Dusty rosewood softening charcoal & denim' : 'Antrasit ve denimi yumuşatan gül kurusu vurgu'],
    ['EMERALD', isEn ? 'Rich jewel-tone green for white & black' : 'Siyah ve beyazla canlı duran zümrüt yeşili'],
    ['TEAL', isEn ? 'Balanced blue-green working year-round' : '4 mevsim kullanılabilen dengeli camgöbeği / teal'],
    ['OCHRE', isEn ? 'Warm saffron accent over dark layers' : 'Koyu katmanları canlandıran sıcak safran vurgusu'],
    ['LAVENDER', isEn ? 'Fresh pastel twist with charcoal & denim' : 'Antrasit ve beyazla modern duran soft lavanta'],
    ['PLUM', isEn ? 'Moody dark purple pairing with gray' : 'Gri ve taş tonlarıyla asil duran mürdüm rengi'],
    ['CORAL', isEn ? 'Lively warmth over ecru & beige' : 'Ekru ve bej üzerinde bahar enerjisi veren mercan'],
    ['BLACK', isEn ? 'Balances vibrant colors on its own' : 'Tüm renkli parçaları tek başına dengeler'],
    ['DENIM', isEn ? 'All-season staple with white & black tops' : 'Beyaz, camel ve siyah üstlerle her mevsim çalışır']
  ];
  return candidates.filter(([col]) => !existing.has(col)).map(([color, reason]) => ({ color, reason }));
}

export const OUTFIT_MOODS = [
  {
    id: 'chill',
    icon: 'moodChill',
    tr: 'Rahat & Chill', en: 'Chill & Relaxed', de: 'Entspannt & Chill',
    fr: 'Cool & Détente', es: 'Relax & Chill', zh: '慵懒休闲'
  },
  {
    id: 'confident',
    icon: 'moodConfident',
    tr: 'İddialı & Şık', en: 'Bold & Chic', de: 'Selbstbewusst & Chic',
    fr: 'Audacieux & Chic', es: 'Atrevido & Chic', zh: '气场时髦'
  },
  {
    id: 'campus',
    icon: 'moodCampus',
    tr: 'Kampüs / Odaklı', en: 'Campus / Focused', de: 'Campus & Fokus',
    fr: 'Campus & Focus', es: 'Campus & Enfoque', zh: '学院通勤'
  },
  {
    id: 'date',
    icon: 'moodDate',
    tr: 'Date & Gece', en: 'Date & Evening', de: 'Date & Abend',
    fr: 'Date & Soirée', es: 'Cita y Noche', zh: '约会之夜'
  },
  {
    id: 'energetic',
    icon: 'moodEnergetic',
    tr: 'Enerjik & Renkli', en: 'Energetic & Bright', de: 'Lebendig & Farbig',
    fr: 'Énergique & Coloré', es: 'Enérgico & Vivo', zh: '活力彩色'
  },
  {
    id: 'dark_minimal',
    icon: 'moodDark',
    tr: 'Karanlık & Cool', en: 'Dark & Cool', de: 'Dark & Minimal',
    fr: 'Sombre & Cool', es: 'Oscuro & Cool', zh: '暗黑酷感'
  }
];

export function moodLabel(moodId, lang = 'TR') {
  const m = OUTFIT_MOODS.find((x) => x.id === moodId) || OUTFIT_MOODS[0];
  return localizedText(lang, m.tr, m.en, m.de, m.fr, m.es, m.zh);
}

const DARK_MINIMAL_SET = new Set(['BLACK', 'CHARCOAL', 'NAVY', 'CHOCOLATE', 'PLUM']);
const BRIGHT_ENERGETIC_SET = new Set([
  'YELLOW', 'MUSTARD', 'ORANGE', 'CORAL', 'TERRACOTTA', 'RED', 'PINK',
  'COBALT', 'TURQUOISE', 'MINT', 'GREEN', 'EMERALD', 'SKY', 'LAVENDER'
]);

function moodItemScore(item, moodId) {
  const sub = item.subCategory;
  const style = item.styleTag || 'CASUAL';
  const primaryColor = item.colors?.[0];
  let score = 0;

  if (moodId === 'chill') {
    if (['HOODIE', 'SWEATSHIRT', 'SWEATPANTS', 'TRACKSUIT', 'TSHIRT', 'SNEAKER'].includes(sub)) score += 20;
    else if (['JEANS', 'CARGO', 'CARDIGAN', 'SHORTS', 'CAP', 'SLIPPER', 'TANK'].includes(sub)) score += 11;
    else if (['SUIT_SET', 'BLAZER', 'HEELS', 'LOAFER'].includes(sub)) score -= 10;
    else score += 3;

    if (style === 'CASUAL' || style === 'SPORT') score += 10;
    else if (style === 'SMART') score -= 4;
    else if (style === 'FORMAL') score -= 14;

    if (['BEIGE', 'ECRU', 'LIGHT_GRAY', 'GRAY', 'STONE', 'SAND', 'WHITE', 'SKY', 'ICE_BLUE', 'MINT', 'SAGE', 'LAVENDER', 'BLUSH', 'DENIM'].includes(primaryColor)) score += 10;
    else if (['NAVY', 'OLIVE', 'CAMEL'].includes(primaryColor)) score += 4;
  } else if (moodId === 'confident') {
    if (['BLAZER', 'COAT', 'JACKET', 'TRENCH', 'SHIRT', 'SUIT_SET', 'DRESS', 'BOOT', 'LOAFER', 'HEELS'].includes(sub)) score += 20;
    else if (['PANTS', 'SKIRT', 'GLASSES', 'WATCH', 'BAG', 'CHINO'].includes(sub)) score += 10;
    else if (['SWEATPANTS', 'TRACKSUIT', 'SLIPPER'].includes(sub)) score -= 10;
    else score += 2;

    if (style === 'FORMAL') score += 12;
    else if (style === 'SMART') score += 11;
    else if (style === 'CASUAL') score += 1;
    else if (style === 'SPORT') score -= 10;

    if (['BLACK', 'WHITE', 'BURGUNDY', 'RED', 'COBALT', 'WINE', 'CAMEL', 'CHOCOLATE', 'CHARCOAL', 'EMERALD'].includes(primaryColor)) score += 10;
    else score += 2;
  } else if (moodId === 'campus') {
    if (['SHIRT', 'SWEATER', 'CARDIGAN', 'POLO', 'CHINO', 'JEANS', 'LOAFER', 'SNEAKER', 'JACKET', 'VEST'].includes(sub)) score += 19;
    else if (['TSHIRT', 'SWEATSHIRT', 'PANTS', 'TRENCH', 'BAG', 'WATCH'].includes(sub)) score += 11;
    else if (['HEELS', 'SLIPPER', 'TRACKSUIT'].includes(sub)) score -= 9;
    else score += 3;

    if (style === 'SMART') score += 12;
    else if (style === 'CASUAL') score += 10;
    else if (style === 'FORMAL') score += 2;
    else if (style === 'SPORT') score += 1;

    if (['NAVY', 'BEIGE', 'ECRU', 'DENIM', 'OLIVE', 'CAMEL', 'LIGHT_GRAY', 'GRAY', 'WHITE', 'SLATE_BLUE', 'FOREST', 'BROWN'].includes(primaryColor)) score += 9;
    else score += 2;
  } else if (moodId === 'date') {
    if (['DRESS', 'SKIRT', 'BLAZER', 'SHIRT', 'CROP_TOP', 'COAT', 'TRENCH', 'SWEATER', 'BOOT', 'HEELS', 'LOAFER', 'SUIT_SET'].includes(sub)) score += 20;
    else if (['PANTS', 'JACKET', 'WATCH', 'BAG'].includes(sub)) score += 10;
    else if (['SWEATPANTS', 'TRACKSUIT', 'SLIPPER', 'SHORTS', 'CAP'].includes(sub)) score -= 14;
    else score += 2;

    if (style === 'SMART') score += 12;
    else if (style === 'FORMAL') score += 11;
    else if (style === 'CASUAL') score += 2;
    else if (style === 'SPORT') score -= 14;

    if (['BURGUNDY', 'WINE', 'NAVY', 'BLACK', 'ECRU', 'CHAMPAGNE', 'CHOCOLATE', 'PLUM', 'ROSEWOOD', 'CHARCOAL', 'EMERALD'].includes(primaryColor)) score += 11;
    else score += 1;
  } else if (moodId === 'energetic') {
    if (['TSHIRT', 'POLO', 'SWEATSHIRT', 'CROP_TOP', 'SHORTS', 'SKIRT', 'SNEAKER', 'JACKET', 'TRACKSUIT', 'GLASSES'].includes(sub)) score += 18;
    else if (['JEANS', 'CARGO', 'DRESS', 'SWEATPANTS', 'CAP'].includes(sub)) score += 10;
    else if (['SUIT_SET', 'COAT'].includes(sub)) score -= 6;
    else score += 3;

    if (style === 'SPORT') score += 11;
    else if (style === 'CASUAL') score += 10;
    else if (style === 'SMART') score += 2;
    else if (style === 'FORMAL') score -= 8;

    const cMeta = COLOR_MAP.get(primaryColor);
    if (BRIGHT_ENERGETIC_SET.has(primaryColor) || ['OCHRE', 'RUST'].includes(primaryColor)) score += 14;
    else if (cMeta && !cMeta.isNeutral) score += 6;
  } else {
    // dark_minimal
    if (['JACKET', 'COAT', 'HOODIE', 'SWEATER', 'TSHIRT', 'PANTS', 'JEANS', 'CARGO', 'BOOT', 'SNEAKER'].includes(sub)) score += 17;
    else if (['BLAZER', 'SHIRT', 'SWEATSHIRT', 'GLASSES', 'WATCH'].includes(sub)) score += 9;
    else if (['POLO', 'SHORTS'].includes(sub)) score -= 6;
    else score += 3;

    if (style === 'CASUAL') score += 9;
    else if (style === 'SMART') score += 8;
    else score += 5;

    if (primaryColor === 'BLACK') score += 18;
    else if (primaryColor === 'CHARCOAL') score += 16;
    else if (primaryColor === 'NAVY') score += 11;
    else if (primaryColor === 'GRAY') score += 10;
    else if (['SLATE_BLUE', 'PLUM', 'CHOCOLATE'].includes(primaryColor)) score += 8;
    else if (primaryColor === 'WHITE') score += 3;
    else if (['PINK', 'YELLOW', 'ORANGE', 'CORAL'].includes(primaryColor)) score -= 12;
    else score -= 2;
  }

  return score;
}

function moodComboCoherenceBonus(items, moodId) {
  if (moodId === 'chill') {
    return items.every((i) => i.styleTag === 'CASUAL' || i.styleTag === 'SPORT') ? 8 : 0;
  }
  if (moodId === 'confident') {
    return items.some((i) => ['BLAZER', 'COAT', 'SHIRT', 'SUIT_SET', 'BOOT', 'LOAFER'].includes(i.subCategory)) ? 8 : 0;
  }
  if (moodId === 'campus') {
    return items.some((i) => ['SHIRT', 'SWEATER', 'CARDIGAN', 'CHINO', 'JEANS'].includes(i.subCategory)) ? 8 : 0;
  }
  if (moodId === 'date') {
    const hasPiece = items.some((i) => ['DRESS', 'SKIRT', 'BLAZER', 'SHIRT', 'HEELS', 'LOAFER', 'BOOT'].includes(i.subCategory));
    const hasTone = items.some((i) => (i.colors || []).some((c) => ['BURGUNDY', 'WINE', 'NAVY', 'BLACK', 'ECRU', 'CHAMPAGNE'].includes(c)));
    return (hasPiece ? 5 : 0) + (hasTone ? 4 : 0);
  }
  if (moodId === 'energetic') {
    return items.some((i) => (i.colors || []).some((c) => BRIGHT_ENERGETIC_SET.has(c))) ? 10 : 0;
  }
  const darkCount = items.filter((i) => DARK_MINIMAL_SET.has(i.colors?.[0])).length;
  return darkCount >= 2 ? 11 : darkCount === 1 ? 4 : -6;
}

function buildMoodReasonNote(items, moodId, harmonyNote, lang) {
  const firstColorId = items.find((i) => i.colors?.length > 0)?.colors?.[0];
  const leadColor = firstColorId
    ? displayColor(firstColorId, lang)
    : localizedText(lang, 'Dengeli', 'Balanced', 'Ausgewogen', 'Équilibré', 'Equilibrado', '协调');
  const hero = items.find((i) => ['OUTERWEAR', 'TOP', 'SUIT'].includes(i.category)) || items[0];
  const keyPiece = hero ? displaySubCategory(hero.subCategory, lang) : '';

  switch (moodId) {
    case 'chill':
      return localizedText(
        lang,
        `${leadColor} tonlu ${keyPiece} ile yumuşak ve rahat siluet · ${harmonyNote}`,
        `Soft ${leadColor} ${keyPiece} anchors an effortless relaxed silhouette · ${harmonyNote}`,
        `Weiches ${leadColor} mit ${keyPiece} für einen entspannten Look · ${harmonyNote}`,
        `${keyPiece} ${leadColor} doux pour une allure décontractée · ${harmonyNote}`,
        `${keyPiece} en ${leadColor} para un estilo relajado · ${harmonyNote}`,
        `柔和${leadColor}${keyPiece}打造轻松自在造型 · ${harmonyNote}`
      );
    case 'confident':
      return localizedText(
        lang,
        `Net hatlı ${keyPiece} ve ${leadColor} vurgusuyla güçlü duruş · ${harmonyNote}`,
        `Structured ${keyPiece} and crisp ${leadColor} contrast project confidence · ${harmonyNote}`,
        `Klar geschnittenes ${keyPiece} in ${leadColor} für starken Auftritt · ${harmonyNote}`,
        `${keyPiece} structuré et contraste ${leadColor} affirmé · ${harmonyNote}`,
        `${keyPiece} estructurado y tono ${leadColor} con presencia · ${harmonyNote}`,
        `利落${keyPiece}与${leadColor}撞色尽显自信气场 · ${harmonyNote}`
      );
    case 'campus':
      return localizedText(
        lang,
        `Düzenli ${keyPiece} ve ${leadColor} tonuyla pratik kampüs dengesi · ${harmonyNote}`,
        `Smart-casual ${keyPiece} in ${leadColor} keeps you focused all day · ${harmonyNote}`,
        `Alltagstaugliches ${keyPiece} in ${leadColor} für Campus & Fokus · ${harmonyNote}`,
        `${keyPiece} soigné en ${leadColor} idéal pour le campus · ${harmonyNote}`,
        `${keyPiece} práctico en ${leadColor} para tu jornada · ${harmonyNote}`,
        `${leadColor}${keyPiece}利落百搭，适合校园通勤 · ${harmonyNote}`
      );
    case 'date':
      return localizedText(
        lang,
        `${leadColor} akşam tonu ve şık ${keyPiece} ile zarif gece uyumu · ${harmonyNote}`,
        `Refined ${keyPiece} paired with deep/warm ${leadColor} evening tones · ${harmonyNote}`,
        `Elegantes ${keyPiece} in abendlichen ${leadColor}-Nuancen · ${harmonyNote}`,
        `${keyPiece} raffiné aux nuances ${leadColor} du soir · ${harmonyNote}`,
        `${keyPiece} elegante en tonos ${leadColor} de noche · ${harmonyNote}`,
        `${leadColor}${keyPiece}勾勒柔和晚间约会格调 · ${harmonyNote}`
      );
    case 'energetic':
      return localizedText(
        lang,
        `Canlı ${leadColor} enerjisi ve dinamik ${keyPiece} katmanıyla ferah görünüm · ${harmonyNote}`,
        `Lively ${leadColor} energy and dynamic ${keyPiece} silhouette · ${harmonyNote}`,
        `Frische ${leadColor}-Energie mit dynamischem ${keyPiece} · ${harmonyNote}`,
        `Énergie ${leadColor} vive et coupe dynamique (${keyPiece}) · ${harmonyNote}`,
        `Energía ${leadColor} fresca y ${keyPiece} dinámico · ${harmonyNote}`,
        `鲜活${leadColor}与动感${keyPiece}注入满满活力 · ${harmonyNote}`
      );
    default:
      return localizedText(
        lang,
        `${leadColor} ağırlıklı monokrom koyu katmanlarla minimal & cool çizgi · ${harmonyNote}`,
        `Monochrome dark ${leadColor} layers build a sleek minimal edge · ${harmonyNote}`,
        `Dunkle ${leadColor}-Schichten für minimalistischen Cool-Look · ${harmonyNote}`,
        `Superpositions sombres ${leadColor} pour un style minimaliste · ${harmonyNote}`,
        `Capas oscuras ${leadColor} de corte minimal y cool · ${harmonyNote}`,
        `深邃${leadColor}单色调叠穿打造冷感极简风 · ${harmonyNote}`
      );
  }
}

export function suggestMoodOutfit({
  items,
  mood = 'chill',
  weather = 'COOL',
  excludeSignature = null,
  recentSignatures = [],
  lang = 'TR',
  genderPref = 'UNISEX',
  flatteringColors = new Set()
}) {
  const closet = (items || []).filter((i) => !i.isWishlist);
  if (closet.length < 2) return null;

  const femSubs = new Set(['SKIRT', 'DRESS', 'HEELS', 'CROP_TOP']);
  const pool = genderPref === 'MALE'
    ? (closet.filter((i) => !femSubs.has(i.subCategory)).length >= 2 ? closet.filter((i) => !femSubs.has(i.subCategory)) : closet)
    : closet;

  const tops = pool.filter((i) => i.category === 'TOP');
  const bottoms = pool.filter((i) => i.category === 'BOTTOM');
  const outer = pool.filter((i) => i.category === 'OUTERWEAR');
  const shoes = pool.filter((i) => i.category === 'SHOES');
  const accs = pool.filter((i) => i.category === 'ACCESSORY');
  const suits = pool.filter((i) => i.category === 'SUIT');

  const candidateLists = [];
  if (tops.length > 0 && bottoms.length > 0) {
    const topPool = [...tops].sort((a, b) => (moodItemScore(b, mood) + weatherScore(b, weather)) - (moodItemScore(a, mood) + weatherScore(a, weather))).slice(0, 8);
    const botPool = [...bottoms].sort((a, b) => (moodItemScore(b, mood) + weatherScore(b, weather)) - (moodItemScore(a, mood) + weatherScore(a, weather))).slice(0, 7);
    const outPool = [...outer].sort((a, b) => (moodItemScore(b, mood) + weatherScore(b, weather)) - (moodItemScore(a, mood) + weatherScore(a, weather))).slice(0, 5);
    const shoePool = [...shoes].sort((a, b) => (moodItemScore(b, mood) + weatherScore(b, weather)) - (moodItemScore(a, mood) + weatherScore(a, weather))).slice(0, 5);
    const accPool = [...accs].sort((a, b) => moodItemScore(b, mood) - moodItemScore(a, mood)).slice(0, 3);

    for (const t of topPool) {
      for (const b of botPool) {
        const list = [t, b];
        if (outPool.length > 0 && (['COOL', 'RAINY', 'COLD'].includes(weather) || ['confident', 'date', 'dark_minimal'].includes(mood))) {
          const bestO = [...outPool].sort((x, y) =>
            (calculateColorHarmony([...list, y], lang).score + moodItemScore(y, mood)) -
            (calculateColorHarmony([...list, x], lang).score + moodItemScore(x, mood))
          )[0];
          const allowOuter = bestO && (
            moodItemScore(bestO, mood) >= 0 || ['confident', 'date', 'dark_minimal'].includes(mood)
          );
          if (allowOuter) list.push(bestO);
        }
        if (shoePool.length > 0) {
          const bestS = [...shoePool].sort((x, y) =>
            (calculateColorHarmony([...list, y], lang).score + moodItemScore(y, mood)) -
            (calculateColorHarmony([...list, x], lang).score + moodItemScore(x, mood))
          )[0];
          if (bestS) list.push(bestS);
        }
        if (accPool.length > 0 && ['confident', 'date', 'energetic', 'campus'].includes(mood)) {
          const bestA = accPool[0];
          if (bestA && moodItemScore(bestA, mood) >= 6) list.push(bestA);
        }
        candidateLists.push(list);
      }
    }
  }

  for (const s of suits) {
    const list = [s];
    const bestOut = [...outer].sort((a, b) => (moodItemScore(b, mood) + weatherScore(b, weather)) - (moodItemScore(a, mood) + weatherScore(a, weather)))[0];
    if (bestOut && (['confident', 'date'].includes(mood) || weather !== 'HOT')) list.push(bestOut);
    const bestShoe = [...shoes].sort((a, b) => (calculateColorHarmony([...list, b], lang).score + moodItemScore(b, mood)) - (calculateColorHarmony([...list, a], lang).score + moodItemScore(a, mood)))[0];
    if (bestShoe) list.push(bestShoe);
    if (list.length >= 2) candidateLists.push(list);
  }

  if (candidateLists.length === 0) {
    const ranked = [...pool].sort((a, b) => moodItemScore(b, mood) - moodItemScore(a, mood));
    for (let i = 0; i < Math.min(ranked.length - 1, 5); i++) {
      for (let j = i + 1; j < Math.min(ranked.length, 6); j++) {
        if (ranked[i].category !== ranked[j].category || ranked.length <= 3) {
          candidateLists.push([ranked[i], ranked[j]]);
        }
      }
    }
    if (candidateLists.length === 0 && ranked.length >= 2) {
      candidateLists.push(ranked.slice(0, Math.min(3, ranked.length)));
    }
  }

  const deduped = new Map();
  for (const raw of candidateLists) {
    const unique = [];
    const seenIds = new Set();
    for (const it of raw) {
      if (!seenIds.has(it.id)) {
        seenIds.add(it.id);
        unique.push(it);
      }
    }
    const sortedItems = sortTopToBottom(unique);
    if (sortedItems.length < 2) continue;
    const signature = sortedItems.map((i) => i.id).join('-');
    if (deduped.has(signature)) continue;

    const { score: harmonyScore, note: harmonyNote } = calculateColorHarmony(sortedItems, lang);
    const avgItemMood = sortedItems.reduce((s, it) => s + moodItemScore(it, mood), 0) / sortedItems.length;
    const comboBonus = moodComboCoherenceBonus(sortedItems, mood);
    const moodFitPct = Math.min(99, Math.max(32, Math.round(((avgItemMood + 12) / 50) * 100 + comboBonus)));
    const matchScore = Math.min(99, Math.max(48, Math.round(harmonyScore * 0.48 + moodFitPct * 0.52)));
    const avgWeatherFit = Math.round(sortedItems.reduce((s, it) => s + weatherScore(it, weather), 0) / sortedItems.length);
    const skinBonus = flatteringColors.size > 0
      ? sortedItems.filter((i) => i.colors?.[0] && flatteringColors.has(i.colors[0])).length * 2
      : 0;
    const recentPenalty = excludeSignature && signature === excludeSignature
      ? 1000
      : recentSignatures.includes(signature)
        ? 28
        : 0;
    const rankScore = matchScore * 2 + avgWeatherFit + skinBonus - recentPenalty;
    deduped.set(signature, { items: sortedItems, signature, matchScore, harmonyScore, harmonyNote, rankScore });
  }

  const ordered = [...deduped.values()].sort((a, b) => b.rankScore - a.rankScore);
  const chosen = (excludeSignature ? ordered.find((c) => c.signature !== excludeSignature) : null) || ordered[0];
  if (!chosen) return null;

  return {
    mood,
    items: chosen.items,
    signature: chosen.signature,
    matchScore: chosen.matchScore,
    harmonyScore: chosen.harmonyScore,
    reasonNote: buildMoodReasonNote(chosen.items, mood, chosen.harmonyNote, lang)
  };
}
