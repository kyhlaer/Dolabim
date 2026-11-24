// Offline Animated GIF89a Sticker Generator (24 Looping Reaction GIFs) & GIF URL Guard

import { localizedText } from './constants.js';

const ALLOWED_GIF_HOSTS = new Set([
  'media.tenor.com',
  'c.tenor.com',
  'media.giphy.com',
  'media0.giphy.com',
  'media1.giphy.com',
  'media2.giphy.com',
  'media3.giphy.com',
  'media4.giphy.com',
  'i.giphy.com'
]);

const GIF_SPECS = [
  { id: 'heart_pop', tr: 'Bayıldım', en: 'Love', bg: 0x1A0F14, card: 0x2E1521, fg: 0xF43F5E, acc: 0xFDA4AF, glyph: 'heart' },
  { id: 'fire_look', tr: 'Çok İyi', en: 'Fire', bg: 0x1A110B, card: 0x2F1B10, fg: 0xF97316, acc: 0xFACC15, glyph: 'flame' },
  { id: 'star_drip', tr: 'Yıldız Kombin', en: 'Iconic', bg: 0x111119, card: 0x1F2032, fg: 0xFACC15, acc: 0xFEF08A, glyph: 'star' },
  { id: 'sparkle_chic', tr: 'Çok Şık', en: 'Chic', bg: 0x101418, card: 0x1A242D, fg: 0xE2E8F0, acc: 0x38BDF8, glyph: 'sparkle' },
  { id: 'crown_fit', tr: 'Kral / Kraliçe', en: 'Slay', bg: 0x16120B, card: 0x292011, fg: 0xF59E0B, acc: 0xFDE68A, glyph: 'crown' },
  { id: 'check_ok', tr: 'Onaylandı', en: 'Approved', bg: 0x0B1612, card: 0x132A21, fg: 0x10B981, acc: 0x6EE7B7, glyph: 'check' },
  { id: 'flash_cam', tr: 'Paparazzi', en: 'Snap', bg: 0x121214, card: 0x222228, fg: 0xFAFAFA, acc: 0x34D399, glyph: 'sparkle' },
  { id: 'drip_bag', tr: 'Sepete At', en: 'Cop It', bg: 0x131118, card: 0x241F30, fg: 0xA855F7, acc: 0xE9D5FF, glyph: 'star' },
  { id: 'vibe_sun', tr: 'Cool Vibe', en: 'Cool', bg: 0x0D141C, card: 0x162436, fg: 0x38BDF8, acc: 0xBAE6FD, glyph: 'sparkle' },
  { id: 'party_win', tr: 'Kutlama', en: 'Party', bg: 0x181018, card: 0x2D192D, fg: 0xEC4899, acc: 0xFDE047, glyph: 'star' },
  { id: 'wow_shock', tr: 'Efsane', en: 'Whoa', bg: 0x141410, card: 0x262619, fg: 0xEAB308, acc: 0xFFFFFF, glyph: 'crown' },
  { id: 'mood_cozy', tr: 'Sıcak Vibe', en: 'Cozy', bg: 0x18130E, card: 0x2A2018, fg: 0xD97706, acc: 0xFCD34D, glyph: 'heart' },
  { id: 'hanger_fit', tr: 'Tam Kombin', en: 'Fit', bg: 0x101012, card: 0x1F1F24, fg: 0xFAFAFA, acc: 0xD4A373, glyph: 'hanger' },
  { id: 'diamond_lux', tr: 'Lüks', en: 'Luxe', bg: 0x0B1519, card: 0x132830, fg: 0x22D3EE, acc: 0xCFFAFE, glyph: 'sparkle' },
  { id: 'eye_stare', tr: 'Göz Kamaştırdı', en: 'Serve', bg: 0x141016, card: 0x251B2B, fg: 0xC084FC, acc: 0xF5D0FE, glyph: 'star' },
  { id: 'note_vibe', tr: 'Ritim', en: 'Mood', bg: 0x101614, card: 0x1A2824, fg: 0x34D399, acc: 0xA7F3D0, glyph: 'check' },
  { id: 'kiss_blush', tr: 'Muhteşem', en: "Chef's Kiss", bg: 0x1B0F13, card: 0x311820, fg: 0xFB7185, acc: 0xFECDD3, glyph: 'heart' },
  { id: 'moon_night', tr: 'Gece Kombini', en: 'Night', bg: 0x0B0C14, card: 0x151829, fg: 0x93C5FD, acc: 0xFDE047, glyph: 'sparkle' },
  { id: 'sun_day', tr: 'Gündüz Stili', en: 'Sunny', bg: 0x19150A, card: 0x2C2410, fg: 0xFBBF24, acc: 0xFEF3C7, glyph: 'star' },
  { id: 'ribbon_cute', tr: 'Tatlı', en: 'Cute', bg: 0x191015, card: 0x2F1B26, fg: 0xF472B6, acc: 0xFBCFE8, glyph: 'heart' },
  { id: 'street_tag', tr: 'Streetwear', en: 'Street', bg: 0x0E0E10, card: 0x1D1D22, fg: 0xFAFAFA, acc: 0xF97316, glyph: 'flame' },
  { id: 'vintage_sepia', tr: 'Retro', en: 'Vintage', bg: 0x171410, card: 0x2B241B, fg: 0xD4A373, acc: 0xFAEDCD, glyph: 'hanger' },
  { id: 'clean_mono', tr: 'Minimal', en: 'Clean', bg: 0x09090B, card: 0x18181C, fg: 0xFAFAFA, acc: 0x94A3B8, glyph: 'check' },
  { id: 'plus_one', tr: 'Destek', en: 'Boost', bg: 0x0F1512, card: 0x1A2922, fg: 0x4ADE80, acc: 0xBBF7D0, glyph: 'check' }
];

let cachedPresets = null;

export function isSafeExternalGifUrl(raw) {
  try {
    const trimmed = String(raw || '').trim();
    if (!trimmed || trimmed.length > 600) return false;
    const u = new URL(trimmed);
    return u.protocol === 'https:' && ALLOWED_GIF_HOSTS.has(u.hostname.toLowerCase());
  } catch {
    return false;
  }
}

export function isSafeGifDataUrl(raw, maxLen = 260000) {
  if (typeof raw !== 'string' || !raw.startsWith('data:image/gif;base64,') || raw.length > maxLen) return false;
  const b64 = raw.slice('data:image/gif;base64,'.length);
  return b64.startsWith('R0lGOD') && b64.length <= maxLen;
}

function pushShortLe(arr, v) {
  arr.push(v & 0xff, (v >> 8) & 0xff);
}

function renderFramePixels(w, h, frame, totalFrames, spec) {
  const px = new Uint8Array(w * h);
  const phase = (frame / totalFrames) * Math.PI * 2;
  const bobY = Math.sin(phase) * 2.2;
  const pulseR = 1.0 + 0.14 * Math.cos(phase);

  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      const dxBorder = Math.min(x, w - 1 - x, y, h - 1 - y);
      let color = 0;
      if (dxBorder >= 2 && dxBorder <= 3) color = 2;
      else if (dxBorder > 3) color = 1;

      const cx = x - 24;
      const cy = y - (21 + bobY);

      if (spec.glyph === 'heart') {
        const sx = cx / (9.5 * pulseR);
        const sy = -cy / (9.5 * pulseR);
        const eq = sx * sx + sy * sy - 1;
        const v = eq * eq * eq - sx * sx * sy * sy * sy;
        if (v <= 0) color = v > -0.22 ? 4 : 3;
      } else if (spec.glyph === 'star') {
        const r = Math.hypot(cx, cy);
        const ang = Math.atan2(cy, cx) + phase * 0.2;
        const edge = (9.5 + 4.8 * Math.cos(5 * ang)) * pulseR;
        if (r <= edge) color = r <= edge * 0.45 ? 5 : 3;
      } else if (spec.glyph === 'flame') {
        const sway = Math.sin(phase + y * 0.25) * 1.8;
        const fx = cx - sway;
        const fy = y - (25 + bobY);
        if (fy >= -13 && fy <= 10) {
          const halfW = ((10 - fy) * 0.45) * (1 - (fy + 13) / 30) * pulseR * 1.4;
          if (Math.abs(fx) <= halfW) {
            color = Math.abs(fx) <= halfW * 0.48 && fy > -5 ? 4 : 3;
          }
        }
      } else if (spec.glyph === 'crown') {
        if (cy >= 2 && cy <= 8 && Math.abs(cx) <= 11) color = 3;
        else if (cy >= -8 && cy <= 2 && Math.abs(cx) <= 11) {
          const col = Math.floor(Math.abs(cx) / 5.5);
          const tip = Math.abs(cx) <= 2 ? -8 : Math.abs(cx) > 7 ? -7 : 1;
          if (cy >= tip || col === 2) color = cy < -5 ? 4 : 3;
        }
      } else if (spec.glyph === 'check') {
        const leg1 = Math.abs((cx + 3) - (cy - 4)) < 2.2 && cx >= -9 && cx <= -1;
        const leg2 = Math.abs((cx + 1) + (cy - 4)) < 2.2 && cx >= -1 && cx <= 10;
        const ring = Math.abs(Math.hypot(cx, cy) - 12 * pulseR) < 1.8;
        if (leg1 || leg2) color = 5;
        else if (ring) color = 3;
      } else if (spec.glyph === 'hanger') {
        const hookR = Math.hypot(cx, cy + 7);
        if (hookR >= 1.6 && hookR <= 3.8 && cy < -5) color = 4;
        const bar = Math.abs(cy - 6) < 1.3 && Math.abs(cx) <= 12;
        const slope = Math.abs(cy - (-2 + Math.abs(cx) * 0.65)) < 1.3 && Math.abs(cx) <= 12;
        if (bar || slope) color = 3;
      } else {
        const r = Math.hypot(cx, cy);
        if (r <= 10.5 * pulseR) color = r <= 5.5 * pulseR ? 4 : 3;
      }

      for (let s = 0; s < 3; s += 1) {
        const a = phase + s * 2.094;
        const sx = 24 + Math.trunc(Math.cos(a) * 17.5);
        const sy = 23 + Math.trunc(Math.sin(a) * 17.5);
        if (Math.abs(x - sx) + Math.abs(y - sy) <= 1) color = 5;
      }

      if (y >= 38 && y <= 42 && Math.abs(x - 24) <= (12 + (frame % 3) * 2)) {
        color = y === 40 && x % 3 !== 0 ? 5 : 4;
      }

      px[y * w + x] = color;
    }
  }
  return px;
}

function writeLzwPixels(bytes, pixels, minCodeSize = 4) {
  bytes.push(minCodeSize);
  const clearCode = 1 << minCodeSize;
  const eoiCode = clearCode + 1;
  const codeBits = minCodeSize + 1;

  const lzw = [];
  let bitBuf = 0;
  let bitCount = 0;

  const emit = (code) => {
    bitBuf |= code << bitCount;
    bitCount += codeBits;
    while (bitCount >= 8) {
      lzw.push(bitBuf & 0xff);
      bitBuf >>>= 8;
      bitCount -= 8;
    }
  };

  emit(clearCode);
  let countSinceClear = 0;
  for (let i = 0; i < pixels.length; i += 1) {
    emit(pixels[i] & 0x0f);
    countSinceClear += 1;
    if (countSinceClear >= 10) {
      emit(clearCode);
      countSinceClear = 0;
    }
  }
  emit(eoiCode);
  if (bitCount > 0) lzw.push(bitBuf & 0xff);

  for (let pos = 0; pos < lzw.length; pos += 255) {
    const slice = lzw.slice(pos, Math.min(lzw.length, pos + 255));
    bytes.push(slice.length);
    for (let j = 0; j < slice.length; j += 1) bytes.push(slice[j]);
  }
  bytes.push(0x00);
}

function encodeSpecGifDataUrl(spec) {
  const w = 48;
  const h = 48;
  const frames = 6;
  const palette = [
    spec.bg, spec.card, 0x2b2b34, spec.fg, spec.acc, 0xffffff, 0x18181d, 0x09090b,
    0xd4d4d8, 0xa1a1aa, spec.bg, spec.card, spec.fg, spec.acc, 0xffffff, 0x18181d
  ];
  const bytes = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61]; // GIF89a
  pushShortLe(bytes, w);
  pushShortLe(bytes, h);
  bytes.push(0xb3, 0x00, 0x00);
  for (const c of palette) {
    bytes.push((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff);
  }
  // NETSCAPE2.0 infinite loop
  bytes.push(0x21, 0xff, 0x0b);
  for (const ch of 'NETSCAPE2.0') bytes.push(ch.charCodeAt(0));
  bytes.push(0x03, 0x01, 0x00, 0x00, 0x00);

  for (let f = 0; f < frames; f += 1) {
    bytes.push(0x21, 0xf9, 0x04, 0x04);
    pushShortLe(bytes, 13);
    bytes.push(0x00, 0x00);
    bytes.push(0x2c);
    pushShortLe(bytes, 0);
    pushShortLe(bytes, 0);
    pushShortLe(bytes, w);
    pushShortLe(bytes, h);
    bytes.push(0x00);
    writeLzwPixels(bytes, renderFramePixels(w, h, f, frames, spec), 4);
  }
  bytes.push(0x3b);

  let bin = '';
  for (let i = 0; i < bytes.length; i += 4096) {
    bin += String.fromCharCode(...bytes.slice(i, Math.min(bytes.length, i + 4096)));
  }
  return `data:image/gif;base64,${btoa(bin)}`;
}

export function getGifPresets(lang = 'TR') {
  if (!cachedPresets) {
    cachedPresets = GIF_SPECS.map((s) => ({
      id: s.id,
      tr: s.tr,
      en: s.en,
      dataUrl: encodeSpecGifDataUrl(s)
    }));
  }
  return cachedPresets.map((g) => ({
    ...g,
    label: localizedText(lang, g.tr, g.en)
  }));
}
