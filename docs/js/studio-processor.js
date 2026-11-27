import { GARMENT_COLORS, STUDIO_ECOMMERCE_PROMPT, SUBCATEGORIES, localizedText } from './constants.js';
import { cleanGarmentTitleForName } from './recommendation-engine.js';
import {
  ALLOWED_HF_SPACE_HOSTS,
  assertSafeImageDimensions,
  clampText,
  isSafeImageDataUrl,
  isValidGeminiApiKey,
  isValidHfToken,
  isVertexExpressApiKey,
  safeFetch,
  sanitizeErrorMessage,
  validateUploadedImageFile
} from './security.js';

export function loadHtmlImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = (err) => reject(err);
    img.src = src;
  });
}

function drawSourceToBoundedCanvas(source, srcW, srcH, maxDim = 2048, fillWhite = false) {
  const wIn = Math.max(1, Number(srcW) || 1);
  const hIn = Math.max(1, Number(srcH) || 1);
  const scale = Math.min(1, maxDim / Math.max(wIn, hIn));
  const w = Math.max(1, Math.round(wIn * scale));
  const h = Math.max(1, Math.round(hIn * scale));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('CANVAS_CONTEXT_UNAVAILABLE');
  if (fillWhite) {
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, w, h);
  }
  ctx.drawImage(source, 0, 0, w, h);
  return canvas;
}

async function readFileBytes(file) {
  if (!file) return new Uint8Array(0);
  if (typeof file.arrayBuffer === 'function') {
    try {
      const ab = await file.arrayBuffer();
      if (ab && ab.byteLength > 0) return new Uint8Array(ab);
    } catch {
      // fall through
    }
  }
  if (typeof Blob !== 'undefined' && file instanceof Blob && typeof FileReader === 'function') {
    try {
      const ab = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error || new Error('READ_AB_FAILED'));
        reader.readAsArrayBuffer(file);
      });
      if (ab && ab.byteLength > 0) return new Uint8Array(ab);
    } catch {
      // fall through
    }
  }
  return new Uint8Array(0);
}

function asciiAt(bytes, offset, len) {
  if (!bytes || offset + len > bytes.length) return '';
  let out = '';
  for (let i = 0; i < len; i++) out += String.fromCharCode(bytes[offset + i]);
  return out;
}

function isHeifLike(bytes, file) {
  if (bytes && bytes.length >= 12 && asciiAt(bytes, 4, 4) === 'ftyp') {
    const brands = (asciiAt(bytes, 8, 4) + ' ' + asciiAt(bytes, 16, Math.min(16, bytes.length - 16))).toLowerCase();
    if (/heic|heix|hevc|hevx|heim|heis|hevm|hevs|mif1|msf1/.test(brands)) return true;
  }
  const mime = String(file?.type || '').toLowerCase();
  const name = String(file?.name || '').toLowerCase();
  return /image\/hei[cf]/.test(mime) || /\.(heic|heif|hif)$/.test(name);
}

function isAvifLike(bytes, file) {
  if (bytes && bytes.length >= 12 && asciiAt(bytes, 4, 4) === 'ftyp') {
    const brands = (asciiAt(bytes, 8, 4) + ' ' + asciiAt(bytes, 16, Math.min(16, bytes.length - 16))).toLowerCase();
    if (/avif|avis/.test(brands)) return true;
  }
  const mime = String(file?.type || '').toLowerCase();
  const name = String(file?.name || '').toLowerCase();
  return /image\/avif/.test(mime) || /\.avif$/.test(name);
}

function isTiffLike(bytes, file) {
  if (bytes && bytes.length >= 4) {
    if ((bytes[0] === 0x49 && bytes[1] === 0x49 && bytes[2] === 0x2A && bytes[3] === 0x00) ||
        (bytes[0] === 0x4D && bytes[1] === 0x4D && bytes[2] === 0x00 && bytes[3] === 0x2A)) {
      return true;
    }
  }
  const mime = String(file?.type || '').toLowerCase();
  const name = String(file?.name || '').toLowerCase();
  return /tiff|dng|x-canon-cr2|x-nikon-nef|x-sony-arw/.test(mime) || /\.(tiff?|dng|cr2|nef|arw)$/.test(name);
}

function sniffImageMimeFromBytes(bytes, fileName = '') {
  if (bytes && bytes.length > 0) {
    if (bytes.length >= 3 && bytes[0] === 0xFF && bytes[1] === 0xD8 && bytes[2] === 0xFF) return 'image/jpeg';
    if (bytes.length >= 8 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4E && bytes[3] === 0x47) return 'image/png';
    if (bytes.length >= 12 && asciiAt(bytes, 0, 4) === 'RIFF' && asciiAt(bytes, 8, 4) === 'WEBP') return 'image/webp';
    if (bytes.length >= 6 && asciiAt(bytes, 0, 3) === 'GIF') return 'image/gif';
    if (bytes.length >= 2 && bytes[0] === 0x42 && bytes[1] === 0x4D) return 'image/bmp';
    if (bytes.length >= 4 && ((bytes[0] === 0x49 && bytes[1] === 0x49 && bytes[2] === 0x2A && bytes[3] === 0x00) ||
      (bytes[0] === 0x4D && bytes[1] === 0x4D && bytes[2] === 0x00 && bytes[3] === 0x2A))) return 'image/tiff';
    if (bytes.length >= 4 && bytes[0] === 0x00 && bytes[1] === 0x00 && bytes[2] === 0x01 && bytes[3] === 0x00) return 'image/x-icon';
    if (bytes.length >= 12 && asciiAt(bytes, 4, 4) === 'ftyp') {
      const brand = asciiAt(bytes, 8, 4).toLowerCase();
      return brand.startsWith('avif') || brand.startsWith('avis') ? 'image/avif' : 'image/heic';
    }
    try {
      const head = new TextDecoder().decode(bytes.subarray(0, Math.min(512, bytes.length))).trim().toLowerCase();
      if (head.includes('<svg') || (head.startsWith('<?xml') && head.includes('svg'))) return 'image/svg+xml';
    } catch {
      // ignore
    }
  }
  const name = String(fileName || '').toLowerCase();
  if (name.endsWith('.png')) return 'image/png';
  if (name.endsWith('.webp')) return 'image/webp';
  if (name.endsWith('.gif')) return 'image/gif';
  if (name.endsWith('.bmp')) return 'image/bmp';
  if (name.endsWith('.svg')) return 'image/svg+xml';
  if (name.endsWith('.avif')) return 'image/avif';
  if (name.endsWith('.heic') || name.endsWith('.heif') || name.endsWith('.hif')) return 'image/heic';
  if (name.endsWith('.tif') || name.endsWith('.tiff') || name.endsWith('.dng')) return 'image/tiff';
  return 'image/jpeg';
}

function readFastImageDimensions(bytes) {
  if (!bytes || bytes.length < 16) return null;
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  try {
    if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4E && bytes[3] === 0x47 && bytes.length >= 24) {
      return { w: dv.getUint32(16, false), h: dv.getUint32(20, false) };
    }
    if (asciiAt(bytes, 0, 3) === 'GIF') {
      return { w: dv.getUint16(6, true), h: dv.getUint16(8, true) };
    }
    if (bytes[0] === 0x42 && bytes[1] === 0x4D && bytes.length >= 26) {
      return { w: Math.abs(dv.getInt32(18, true)), h: Math.abs(dv.getInt32(22, true)) };
    }
    if (asciiAt(bytes, 0, 4) === 'RIFF' && asciiAt(bytes, 8, 4) === 'WEBP' && bytes.length >= 30) {
      const chunk = asciiAt(bytes, 12, 4);
      if (chunk === 'VP8 ' && bytes[23] === 0x9D && bytes[24] === 0x01 && bytes[25] === 0x2A) {
        return { w: dv.getUint16(26, true) & 0x3FFF, h: dv.getUint16(28, true) & 0x3FFF };
      }
      if (chunk === 'VP8L' && bytes[20] === 0x2F) {
        const bits = dv.getUint32(21, true);
        return { w: (bits & 0x3FFF) + 1, h: ((bits >> 14) & 0x3FFF) + 1 };
      }
      if (chunk === 'VP8X') {
        return {
          w: 1 + (bytes[24] | (bytes[25] << 8) | (bytes[26] << 16)),
          h: 1 + (bytes[27] | (bytes[28] << 8) | (bytes[29] << 16))
        };
      }
    }
    if (bytes[0] === 0xFF && bytes[1] === 0xD8) {
      let i = 2;
      let steps = 0;
      while (i + 9 < bytes.length && steps++ < 4096) {
        if (bytes[i] !== 0xFF) break;
        while (i < bytes.length && bytes[i] === 0xFF) i++;
        if (i >= bytes.length) break;
        const marker = bytes[i++];
        if (marker === 0xD9 || marker === 0xDA) break;
        if (marker === 0x00 || marker === 0x01 || (marker >= 0xD0 && marker <= 0xD8)) continue;
        if (i + 2 > bytes.length) break;
        const segLen = dv.getUint16(i, false);
        if (segLen < 2) break;
        if ((marker >= 0xC0 && marker <= 0xC3) || (marker >= 0xC5 && marker <= 0xC7) ||
            (marker >= 0xC9 && marker <= 0xCB) || (marker >= 0xCD && marker <= 0xCF)) {
          if (i + 7 <= bytes.length) {
            const h = dv.getUint16(i + 3, false);
            const w = dv.getUint16(i + 5, false);
            if (w > 0 && h > 0) return { w, h };
          }
          break;
        }
        i += segLen;
      }
    }
  } catch {
    // ignore
  }
  return null;
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(reader.error || new Error('READ_FAILED'));
    reader.readAsDataURL(file);
  });
}

async function tryDecodeViaCreateImageBitmap(blob, bytes, maxDim) {
  if (typeof createImageBitmap !== 'function' || !blob) return null;
  const dims = readFastImageDimensions(bytes);
  const attempts = [];
  if (dims && Math.max(dims.w, dims.h) > maxDim) {
    const resizeOpt = dims.w >= dims.h ? { resizeWidth: maxDim } : { resizeHeight: maxDim };
    attempts.push({ imageOrientation: 'from-image', ...resizeOpt, resizeQuality: 'high' });
    attempts.push({ ...resizeOpt, resizeQuality: 'high' });
  }
  attempts.push({ imageOrientation: 'from-image' });
  attempts.push(null);

  for (const opts of attempts) {
    let bitmap = null;
    try {
      bitmap = opts ? await createImageBitmap(blob, opts) : await createImageBitmap(blob);
      if (bitmap && bitmap.width > 0 && bitmap.height > 0) {
        return drawSourceToBoundedCanvas(bitmap, bitmap.width, bitmap.height, maxDim, false);
      }
    } catch {
      // try next option set
    } finally {
      try { bitmap?.close?.(); } catch {}
    }
  }
  return null;
}

async function decodeSvgWithExplicitDimensions(bytes, maxDim) {
  if (!bytes || bytes.length === 0) return null;
  const rawText = new TextDecoder().decode(bytes);
  if (!/<svg\b/i.test(rawText)) return null;
  const vbMatch = rawText.match(/viewBox\s*=\s*["']\s*[+-]?[\d.]+\s+[+-]?[\d.]+\s+([\d.]+)\s+([\d.]+)\s*["']/i);
  const vbW = vbMatch ? Math.max(1, parseFloat(vbMatch[1]) || 1024) : 1024;
  const vbH = vbMatch ? Math.max(1, parseFloat(vbMatch[2]) || 1024) : 1024;
  const scale = Math.min(maxDim, 1024) / Math.max(vbW, vbH);
  const targetW = Math.max(1, Math.round(vbW * (scale > 0 ? scale : 1)));
  const targetH = Math.max(1, Math.round(vbH * (scale > 0 ? scale : 1)));
  const patched = rawText.replace(/<svg\b([^>]*)>/i, (_, attrs) => {
    const cleaned = attrs.replace(/\b(width|height)\s*=\s*["'][^"']*["']/gi, '');
    return `<svg${cleaned} width="${targetW}" height="${targetH}">`;
  });
  const svgBlob = new Blob([patched], { type: 'image/svg+xml' });
  const url = URL.createObjectURL(svgBlob);
  try {
    const img = await loadHtmlImage(url);
    await img.decode?.().catch(() => {});
    const iw = img.naturalWidth || img.width || targetW;
    const ih = img.naturalHeight || img.height || targetH;
    if (iw > 0 && ih > 0) {
      return drawSourceToBoundedCanvas(img, iw, ih, maxDim, false);
    }
  } finally {
    URL.revokeObjectURL(url);
  }
  return null;
}

async function tryDecodeViaHtmlImage(blob, bytes, maxDim) {
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  try {
    const img = await loadHtmlImage(url);
    await img.decode?.().catch(() => {});
    const w = img.naturalWidth || img.width || 0;
    const h = img.naturalHeight || img.height || 0;
    if (w > 0 && h > 0) {
      assertSafeImageDimensions(img);
      return drawSourceToBoundedCanvas(img, w, h, maxDim, false);
    }
  } finally {
    URL.revokeObjectURL(url);
  }
  return decodeSvgWithExplicitDimensions(bytes, maxDim);
}

let libheifModulePromise = null;
async function getLibheifModule() {
  if (!libheifModulePromise) {
    libheifModulePromise = new Promise((resolve, reject) => {
      const initFactory = () => {
        const entry = window.libheif;
        if (!entry) {
          libheifModulePromise = null;
          return reject(new Error('LIBHEIF_GLOBAL_MISSING'));
        }
        Promise.resolve(typeof entry === 'function' ? entry() : entry)
          .then(resolve)
          .catch((err) => {
            libheifModulePromise = null;
            reject(err);
          });
      };
      if (typeof window !== 'undefined' && window.libheif) {
        initFactory();
        return;
      }
      const s = document.createElement('script');
      s.src = './js/vendor/libheif.js';
      s.async = true;
      s.onload = initFactory;
      s.onerror = () => {
        libheifModulePromise = null;
        reject(new Error('LIBHEIF_SCRIPT_LOAD_FAILED'));
      };
      document.head.appendChild(s);
    });
  }
  return libheifModulePromise;
}

async function decodeHeifViaWasm(bytes, maxDim) {
  if (!bytes || bytes.length < 16) return null;
  const mod = await getLibheifModule();
  if (!mod?.HeifDecoder) return null;
  await new Promise((r) => setTimeout(r, 20));
  const decoder = new mod.HeifDecoder();
  let images = null;
  try {
    images = decoder.decode(bytes);
    if (!images || images.length === 0) return null;
    const sorted = [...images].sort((a, b) => (b.is_primary?.() ? 1 : 0) - (a.is_primary?.() ? 1 : 0));
    for (const image of sorted) {
      const w = Number(image.get_width?.() || 0);
      const h = Number(image.get_height?.() || 0);
      if (!(w > 0 && h > 0)) continue;
      const c = document.createElement('canvas');
      c.width = w;
      c.height = h;
      const ctx = c.getContext('2d');
      if (!ctx) continue;
      const imgData = ctx.createImageData(w, h);
      try {
        await new Promise((resolve, reject) => {
          let settled = false;
          const timer = setTimeout(() => {
            if (!settled) { settled = true; reject(new Error('HEIF_DECODE_TIMEOUT')); }
          }, 25000);
          image.display(imgData, (rendered) => {
            if (settled) return;
            settled = true;
            clearTimeout(timer);
            if (!rendered) reject(new Error('HEIF_DISPLAY_FAILED'));
            else resolve(rendered);
          });
        });
        ctx.putImageData(imgData, 0, 0);
        return drawSourceToBoundedCanvas(c, w, h, maxDim, false);
      } catch {
        // try next decoded image in container
      }
    }
    return null;
  } finally {
    if (Array.isArray(images)) {
      for (const img of images) {
        try { img?.free?.(); } catch {}
      }
    }
    try { if (decoder?.decoder) mod.heif_context_free?.(decoder.decoder); } catch {}
  }
}

let utifPromise = null;
async function getUtifDecoder() {
  if (typeof window !== 'undefined' && window.UTIF) return window.UTIF;
  if (!utifPromise) {
    utifPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = './js/vendor/utif.js';
      s.async = true;
      s.onload = () => {
        if (window.UTIF) resolve(window.UTIF);
        else {
          utifPromise = null;
          reject(new Error('UTIF_GLOBAL_MISSING'));
        }
      };
      s.onerror = () => {
        utifPromise = null;
        reject(new Error('UTIF_SCRIPT_LOAD_FAILED'));
      };
      document.head.appendChild(s);
    });
  }
  return utifPromise;
}

async function decodeTiffViaUtif(bytes, maxDim) {
  if (!bytes || bytes.length < 16) return null;
  const UTIF = await getUtifDecoder();
  const ab = bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength
    ? bytes.buffer
    : bytes.slice().buffer;
  const ifds = UTIF.decode(ab);
  if (!ifds || ifds.length === 0) return null;
  const ranked = [...ifds].sort((a, b) => {
    const pa = (a.t256?.[0] || 0) * (a.t257?.[0] || 0);
    const pb = (b.t256?.[0] || 0) * (b.t257?.[0] || 0);
    return pb - pa;
  });
  for (const ifd of ranked) {
    try {
      UTIF.decodeImage(ab, ifd);
      const w = Number(ifd.width || 0);
      const h = Number(ifd.height || 0);
      if (!(w > 0 && h > 0)) continue;
      const rgba = UTIF.toRGBA8(ifd);
      if (!rgba || rgba.length < w * h * 4) continue;
      const c = document.createElement('canvas');
      c.width = w;
      c.height = h;
      const ctx = c.getContext('2d');
      const imgData = new ImageData(new Uint8ClampedArray(rgba.buffer, rgba.byteOffset, w * h * 4), w, h);
      ctx.putImageData(imgData, 0, 0);
      return drawSourceToBoundedCanvas(c, w, h, maxDim, false);
    } catch {
      // try next IFD
    }
  }
  return null;
}

async function decodeEmbeddedJpegRescue(bytes, maxDim) {
  if (!bytes || bytes.length < 2048) return null;
  const candidates = [];
  for (let i = 0; i + 4 < bytes.length; i++) {
    if (bytes[i] === 0xFF && bytes[i + 1] === 0xD8 && bytes[i + 2] === 0xFF) {
      let pos = i + 2;
      let hitSos = false;
      let steps = 0;
      let endIdx = -1;
      while (pos + 3 < bytes.length && steps++ < 512) {
        if (bytes[pos] !== 0xFF) break;
        while (pos < bytes.length && bytes[pos] === 0xFF) pos++;
        if (pos >= bytes.length) break;
        const marker = bytes[pos++];
        if (marker === 0xD9) { endIdx = pos; break; }
        if (marker === 0x00 || marker === 0x01 || (marker >= 0xD0 && marker <= 0xD8)) continue;
        if (marker === 0xDA) { hitSos = true; break; }
        if (pos + 2 > bytes.length) break;
        const segLen = (bytes[pos] << 8) | bytes[pos + 1];
        if (segLen < 2) break;
        pos += segLen;
      }
      if (hitSos) {
        for (let j = pos; j + 1 < bytes.length; j++) {
          if (bytes[j] === 0xFF && bytes[j + 1] === 0xD9) {
            endIdx = j + 2;
            break;
          }
        }
      }
      if (endIdx > i + 2048) {
        candidates.push({ start: i, end: endIdx, len: endIdx - i });
        i = endIdx - 1;
      }
    }
  }
  candidates.sort((a, b) => b.len - a.len);
  for (const cand of candidates.slice(0, 5)) {
    const slice = bytes.subarray(cand.start, cand.end);
    const jpegBlob = new Blob([slice], { type: 'image/jpeg' });
    const viaBmp = await tryDecodeViaCreateImageBitmap(jpegBlob, slice, maxDim);
    if (viaBmp) return viaBmp;
    try {
      const viaImg = await tryDecodeViaHtmlImage(jpegBlob, slice, maxDim);
      if (viaImg) return viaImg;
    } catch {
      // continue trying next candidate
    }
  }
  return null;
}

export async function decodeImageFileToCanvasImage(file, maxDim = 2048) {
  await validateUploadedImageFile(file);

  const bytes = await readFileBytes(file);
  const rawBlob = bytes.length > 0 ? new Blob([bytes], { type: file.type || '' }) : file;

  // Stage 1: Native ImageBitmap decoder (supports EXIF orientation & OOM-safe header resize)
  const fromBitmap = await tryDecodeViaCreateImageBitmap(rawBlob, bytes, maxDim);
  if (fromBitmap) return fromBitmap;

  // Stage 2: ObjectURL via HTMLImageElement (handles SVG, WebP, legacy codecs)
  try {
    const fromUrl = await tryDecodeViaHtmlImage(rawBlob, bytes, maxDim);
    if (fromUrl) return fromUrl;
  } catch {
    // continue to sniffed-MIME fallback
  }

  // Stage 3: Retry with sniffed image MIME when picker leaves type empty / octet-stream
  const guessedMime = sniffImageMimeFromBytes(bytes, file.name);
  const typedBlob = bytes.length > 0
    ? new Blob([bytes], { type: guessedMime })
    : new Blob([file], { type: guessedMime });
  try {
    const fromTypedBmp = await tryDecodeViaCreateImageBitmap(typedBlob, bytes, maxDim);
    if (fromTypedBmp) return fromTypedBmp;
    const fromTypedUrl = await tryDecodeViaHtmlImage(typedBlob, bytes, maxDim);
    if (fromTypedUrl) return fromTypedUrl;
  } catch {
    // continue to DataURL fallback
  }

  // Stage 3b: Data URL fallback for WebViews blocking blob URLs (use typedBlob so empty MIME still decodes)
  try {
    const dataUrl = await readFileAsDataUrl(typedBlob);
    const img = await loadHtmlImage(dataUrl);
    await img.decode?.().catch(() => {});
    assertSafeImageDimensions(img);
    return drawSourceToBoundedCanvas(img, img.naturalWidth || img.width, img.naturalHeight || img.height, maxDim, false);
  } catch {
    // proceed to dedicated codec fallbacks
  }

  // Stage 4: Lazy-loaded libheif WASM decoder for HEIC / HEIF (bundled libheif lacks AV1 codec for AVIF)
  const looksHeif = isHeifLike(bytes, file);
  const looksAvif = isAvifLike(bytes, file);
  if (looksHeif) {
    try {
      const heifCanvas = await decodeHeifViaWasm(bytes, maxDim);
      if (heifCanvas) return heifCanvas;
    } catch {
      // fall through
    }
  }

  // Stage 5: Lazy-loaded UTIF decoder for TIFF / DNG / RAW containers
  const looksTiff = isTiffLike(bytes, file);
  if (looksTiff) {
    try {
      const tiffCanvas = await decodeTiffViaUtif(bytes, maxDim);
      if (tiffCanvas) return tiffCanvas;
    } catch {
      // fall through to embedded preview
    }
  }

  // Stage 6: Embedded JPEG preview rescue (DNG/RAW/Motion Photos/container wrappers)
  try {
    const rescued = await decodeEmbeddedJpegRescue(bytes, maxDim);
    if (rescued) return rescued;
  } catch {
    // fall through
  }

  // Stage 7: Unconditional HEIF / TIFF final attempt if file header/extension was misleading
  if (!looksHeif && !looksAvif) {
    try {
      const heifFallback = await decodeHeifViaWasm(bytes, maxDim);
      if (heifFallback) return heifFallback;
    } catch {
      // ignore
    }
  }
  if (!looksTiff) {
    try {
      const tiffFallback = await decodeTiffViaUtif(bytes, maxDim);
      if (tiffFallback) return tiffFallback;
    } catch {
      // ignore
    }
  }

  if (looksAvif) throw new Error('UNSUPPORTED_AVIF_FORMAT');
  if (looksHeif) throw new Error('UNSUPPORTED_HEIF_FORMAT');
  throw new Error('IMAGE_DECODE_FAILED');
}

export async function fileToOrientedDataUrl(file, maxDim = 1440) {
  const decodedCanvas = await decodeImageFileToCanvasImage(file, maxDim);
  const out = drawSourceToBoundedCanvas(decodedCanvas, decodedCanvas.width, decodedCanvas.height, maxDim, true);
  const dataUrl = out.toDataURL('image/jpeg', 0.90);
  if (!isSafeImageDataUrl(dataUrl)) throw new Error('IMAGE_ENCODE_FAILED');
  return dataUrl;
}

function smoothstep(edge0, edge1, x) {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

function rgbToLab(r, g, b) {
  let sr = r / 255, sg = g / 255, sb = b / 255;
  sr = sr <= 0.04045 ? sr / 12.92 : Math.pow((sr + 0.055) / 1.055, 2.4);
  sg = sg <= 0.04045 ? sg / 12.92 : Math.pow((sg + 0.055) / 1.055, 2.4);
  sb = sb <= 0.04045 ? sb / 12.92 : Math.pow((sb + 0.055) / 1.055, 2.4);

  const x = (0.4124564 * sr + 0.3575761 * sg + 0.1804375 * sb) / 0.95047;
  const y = (0.2126729 * sr + 0.7151522 * sg + 0.0721750 * sb);
  const z = (0.0193339 * sr + 0.1191920 * sg + 0.9503041 * sb) / 1.08883;

  const fx = x > 0.008856 ? Math.cbrt(x) : (7.787 * x + 16 / 116);
  const fy = y > 0.008856 ? Math.cbrt(y) : (7.787 * y + 16 / 116);
  const fz = z > 0.008856 ? Math.cbrt(z) : (7.787 * z + 16 / 116);
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

function clusterLabPixels(indices, L, A, B, maxClusters) {
  if (!indices || indices.length === 0) return [];
  const bins = new Map();
  let sumL = 0, sumA = 0, sumB = 0;
  for (let k = 0; k < indices.length; k++) {
    const idx = indices[k];
    const l = L[idx], a = A[idx], b = B[idx];
    sumL += l; sumA += a; sumB += b;
    const bl = Math.max(0, Math.min(9, (l / 12) | 0));
    const ba = Math.max(0, Math.min(18, ((a + 128) / 14) | 0));
    const bb = Math.max(0, Math.min(18, ((b + 128) / 14) | 0));
    const key = bl * 400 + ba * 20 + bb;
    let entry = bins.get(key);
    if (!entry) {
      entry = { count: 0, sl: 0, sa: 0, sb: 0 };
      bins.set(key, entry);
    }
    entry.count++; entry.sl += l; entry.sa += a; entry.sb += b;
  }

  const sorted = [...bins.values()].sort((x, y) => y.count - x.count);
  const clusters = [];
  for (const bin of sorted) {
    const cl = bin.sl / bin.count;
    const ca = bin.sa / bin.count;
    const cb = bin.sb / bin.count;
    const merged = clusters.some((c) => Math.hypot(cl - c.l, ca - c.a, cb - c.b) < 10);
    if (!merged) {
      clusters.push({ l: cl, a: ca, b: cb, weight: bin.count });
      if (clusters.length >= maxClusters) break;
    }
  }
  if (clusters.length === 0) {
    const n = indices.length;
    clusters.push({ l: sumL / n, a: sumA / n, b: sumB / n, weight: n });
  }
  return clusters;
}

function minLabDistance(l, a, b, clusters) {
  let best = 1e9;
  for (let i = 0; i < clusters.length; i++) {
    const c = clusters[i];
    const d = Math.hypot(l - c.l, a - c.a, b - c.b);
    if (d < best) best = d;
  }
  return best;
}

function morphOpenBinaryMask(mask, w, h) {
  const total = w * h;
  const eroded = new Uint8Array(total);
  for (let y = 1; y < h - 1; y++) {
    const off = y * w;
    for (let x = 1; x < w - 1; x++) {
      const idx = off + x;
      if (
        mask[idx] >= 128 &&
        mask[idx - 1] >= 128 && mask[idx + 1] >= 128 &&
        mask[idx - w] >= 128 && mask[idx + w] >= 128 &&
        mask[idx - w - 1] >= 128 && mask[idx - w + 1] >= 128 &&
        mask[idx + w - 1] >= 128 && mask[idx + w + 1] >= 128
      ) {
        eroded[idx] = 255;
      }
    }
  }
  const opened = new Uint8Array(total);
  for (let y = 1; y < h - 1; y++) {
    const off = y * w;
    for (let x = 1; x < w - 1; x++) {
      const idx = off + x;
      if (
        eroded[idx] >= 128 ||
        eroded[idx - 1] >= 128 || eroded[idx + 1] >= 128 ||
        eroded[idx - w] >= 128 || eroded[idx + w] >= 128 ||
        eroded[idx - w - 1] >= 128 || eroded[idx - w + 1] >= 128 ||
        eroded[idx + w - 1] >= 128 || eroded[idx + w + 1] >= 128
      ) {
        opened[idx] = 255;
      }
    }
  }
  return opened;
}

function fillInteriorMaskHoles(alpha, w, h) {
  const total = w * h;
  const extBg = new Uint8Array(total);
  const q = new Int32Array(total);
  let head = 0, tail = 0;
  const pushBg = (idx) => {
    if (!extBg[idx] && alpha[idx] < 128) {
      extBg[idx] = 1;
      q[tail++] = idx;
    }
  };
  for (let x = 0; x < w; x++) { pushBg(x); pushBg((h - 1) * w + x); }
  for (let y = 0; y < h; y++) { pushBg(y * w); pushBg(y * w + (w - 1)); }
  while (head < tail) {
    const idx = q[head++];
    const x = idx % w, y = (idx / w) | 0;
    if (x > 0) pushBg(idx - 1);
    if (x + 1 < w) pushBg(idx + 1);
    if (y > 0) pushBg(idx - w);
    if (y + 1 < h) pushBg(idx + w);
  }
  for (let i = 0; i < total; i++) {
    if (!extBg[i]) alpha[i] = 255;
  }
}

/**
 * Zero-API Offline Background Remover:
 * Multi-cluster CIE Lab foreground/background modeling with spatial priors,
 * 2 iterative GrabCut-like border flood-fill passes, largest-component cleanup,
 * hole filling, 3x3 morphological open, hanger-hook strip, edge feathering,
 * and centered pure #FFFFFF 1:1 (1080x1080) output.
 */
export async function toEcommerceFlatLayDataUrl(sourceDataUrl) {
  try {
    const img = await loadHtmlImage(sourceDataUrl);
    const maxWork = 360;
    const scale = Math.min(1, maxWork / Math.max(img.width || 1, img.height || 1));
    const w = Math.max(32, Math.round((img.width || 1) * scale));
    const h = Math.max(32, Math.round((img.height || 1) * scale));
    const total = w * h;

    const workCanvas = document.createElement('canvas');
    workCanvas.width = w;
    workCanvas.height = h;
    const wCtx = workCanvas.getContext('2d');
    wCtx.drawImage(img, 0, 0, w, h);
    const px = wCtx.getImageData(0, 0, w, h).data;

    const L = new Float32Array(total);
    const A = new Float32Array(total);
    const B = new Float32Array(total);
    for (let i = 0; i < total; i++) {
      const p4 = i * 4;
      const [l, a, b] = rgbToLab(px[p4], px[p4 + 1], px[p4 + 2]);
      L[i] = l; A[i] = a; B[i] = b;
    }

    const edgeMarginX = Math.max(2, Math.round(w * 0.07));
    const edgeMarginY = Math.max(2, Math.round(h * 0.07));
    const borderIndices = [];
    const centerCandidates = [];

    for (let y = 0; y < h; y++) {
      const inEdgeY = y < edgeMarginY || y >= h - edgeMarginY;
      const inCoreY = y >= h * 0.20 && y <= h * 0.80;
      const off = y * w;
      for (let x = 0; x < w; x++) {
        const idx = off + x;
        const inEdgeX = x < edgeMarginX || x >= w - edgeMarginX;
        if (inEdgeY || inEdgeX) {
          borderIndices.push(idx);
        } else if (inCoreY && x >= w * 0.24 && x <= w * 0.76) {
          centerCandidates.push(idx);
        }
      }
    }

    let bgClusters = clusterLabPixels(borderIndices, L, A, B, 6);
    const fgSeedIndices = centerCandidates.filter(
      (idx) => minLabDistance(L[idx], A[idx], B[idx], bgClusters) >= 14
    );
    let fgClusters = clusterLabPixels(
      fgSeedIndices.length >= 24 ? fgSeedIndices : centerCandidates,
      L, A, B, 5
    );

    const spatialEdgeX = Math.max(2, Math.round(w * 0.06));
    const spatialEdgeY = Math.max(2, Math.round(h * 0.06));
    const queue = new Int32Array(total);
    let bgConfirmed = new Uint8Array(total);

    for (let pass = 0; pass < 2; pass++) {
      const provBg = new Uint8Array(total);
      for (let y = 0; y < h; y++) {
        const inEdgeY = y < spatialEdgeY || y >= h - spatialEdgeY;
        const inCoreY = y >= h * 0.18 && y <= h * 0.82;
        const off = y * w;
        for (let x = 0; x < w; x++) {
          const idx = off + x;
          const dBg = minLabDistance(L[idx], A[idx], B[idx], bgClusters);
          const dFg = minLabDistance(L[idx], A[idx], B[idx], fgClusters);
          let effBg = dBg;
          let effFg = dFg * 0.94;
          if (inEdgeY || x < spatialEdgeX || x >= w - spatialEdgeX) effBg -= 6;
          else if (inCoreY && x >= w * 0.22 && x <= w * 0.78) effFg -= 4;
          if (effBg < effFg) provBg[idx] = 1;
        }
      }

      bgConfirmed = new Uint8Array(total);
      let head = 0, tail = 0;
      const seedBorder = (idx) => {
        if (!bgConfirmed[idx] && provBg[idx]) {
          bgConfirmed[idx] = 1;
          queue[tail++] = idx;
        }
      };
      for (let x = 0; x < w; x++) { seedBorder(x); seedBorder((h - 1) * w + x); }
      for (let y = 0; y < h; y++) { seedBorder(y * w); seedBorder(y * w + (w - 1)); }

      while (head < tail) {
        const idx = queue[head++];
        const x = idx % w, y = (idx / w) | 0;
        if (x > 0) seedBorder(idx - 1);
        if (x + 1 < w) seedBorder(idx + 1);
        if (y > 0) seedBorder(idx - w);
        if (y + 1 < h) seedBorder(idx + w);
      }

      if (pass === 0) {
        const bgSample = [];
        const fgSample = [];
        const step = Math.max(1, (total / 8000) | 0);
        for (let i = 0; i < total; i += step) {
          if (bgConfirmed[i]) bgSample.push(i);
          else fgSample.push(i);
        }
        if (bgSample.length >= 32) bgClusters = clusterLabPixels(bgSample, L, A, B, 6);
        if (fgSample.length >= 32) fgClusters = clusterLabPixels(fgSample, L, A, B, 5);
      }
    }

    let alpha = new Uint8Array(total);
    for (let i = 0; i < total; i++) alpha[i] = bgConfirmed[i] ? 0 : 255;

    keepLargestComponent(alpha, w, h);
    fillInteriorMaskHoles(alpha, w, h);
    alpha = morphOpenBinaryMask(alpha, w, h);
    keepLargestComponent(alpha, w, h);
    fillInteriorMaskHoles(alpha, w, h);

    let bbox = computeForegroundBbox(alpha, w, h);
    if (!bbox) {
      return centerBitmapOnStudioWhite(workCanvas, w, h);
    }
    stripTopHangerHook(alpha, w, bbox);
    bbox = computeForegroundBbox(alpha, w, h);
    if (!bbox || (bbox.maxX - bbox.minX < 16) || (bbox.maxY - bbox.minY < 16)) {
      return centerBitmapOnStudioWhite(workCanvas, w, h);
    }

    let fgPixels = 0;
    for (let i = 0; i < total; i++) {
      if (alpha[i] >= 128) fgPixels++;
    }
    const fgRatio = fgPixels / Math.max(1, total);
    if (fgRatio < 0.06 || fgRatio > 0.96) {
      return centerBitmapOnStudioWhite(workCanvas, w, h);
    }

    // Soft 3x3 feathering strictly on boundary pixels while keeping core solid
    const feathered = new Uint8Array(alpha);
    for (let y = 1; y < h - 1; y++) {
      const off = y * w;
      for (let x = 1; x < w - 1; x++) {
        const idx = off + x;
        const c = alpha[idx];
        const hasSame =
          alpha[idx - 1] === c && alpha[idx + 1] === c &&
          alpha[idx - w] === c && alpha[idx + w] === c &&
          alpha[idx - w - 1] === c && alpha[idx - w + 1] === c &&
          alpha[idx + w - 1] === c && alpha[idx + w + 1] === c;
        if (!hasSame) {
          const blur = (
            alpha[idx - w - 1] + 2 * alpha[idx - w] + alpha[idx - w + 1] +
            2 * alpha[idx - 1] + 4 * c + 2 * alpha[idx + 1] +
            alpha[idx + w - 1] + 2 * alpha[idx + w] + alpha[idx + w + 1]
          ) / 16;
          feathered[idx] = Math.round(blur);
        }
      }
    }

    // Upscale alpha mask to hi-res image (maxDim = 960), matte cleanly onto #FFFFFF
    const maxHi = 960;
    const hiScale = Math.min(1, maxHi / Math.max(img.width || 1, img.height || 1));
    const hw = Math.max(32, Math.round((img.width || 1) * hiScale));
    const hh = Math.max(32, Math.round((img.height || 1) * hiScale));

    const hiCanvas = document.createElement('canvas');
    hiCanvas.width = hw; hiCanvas.height = hh;
    const hiCtx = hiCanvas.getContext('2d');
    hiCtx.drawImage(img, 0, 0, hw, hh);
    const hiImgData = hiCtx.getImageData(0, 0, hw, hh);
    const hiPx = hiImgData.data;

    const maskSmall = document.createElement('canvas');
    maskSmall.width = w; maskSmall.height = h;
    const msCtx = maskSmall.getContext('2d');
    const msData = msCtx.createImageData(w, h);
    for (let i = 0; i < total; i++) {
      const p4 = i * 4;
      msData.data[p4] = 255; msData.data[p4 + 1] = 255; msData.data[p4 + 2] = 255;
      msData.data[p4 + 3] = feathered[i];
    }
    msCtx.putImageData(msData, 0, 0);

    const maskHi = document.createElement('canvas');
    maskHi.width = hw; maskHi.height = hh;
    const mhCtx = maskHi.getContext('2d');
    mhCtx.imageSmoothingEnabled = true;
    mhCtx.imageSmoothingQuality = 'high';
    mhCtx.drawImage(maskSmall, 0, 0, hw, hh);
    const mhPx = mhCtx.getImageData(0, 0, hw, hh).data;

    const hiAlpha = new Uint8Array(hw * hh);
    for (let i = 0; i < hw * hh; i++) {
      const p4 = i * 4;
      const aByte = mhPx[p4 + 3];
      hiAlpha[i] = aByte;
      const a = aByte / 255;
      hiPx[p4] = Math.round(hiPx[p4] * a + 255 * (1 - a));
      hiPx[p4 + 1] = Math.round(hiPx[p4 + 1] * a + 255 * (1 - a));
      hiPx[p4 + 2] = Math.round(hiPx[p4 + 2] * a + 255 * (1 - a));
      hiPx[p4 + 3] = 255;
    }

    const hiBbox = computeForegroundBbox(hiAlpha, hw, hh);
    if (!hiBbox) {
      return centerBitmapOnStudioWhite(hiCanvas, hw, hh);
    }
    applyStudioLightingEqualization(hiPx, hiAlpha, hw, hiBbox);
    hiCtx.putImageData(hiImgData, 0, 0);

    const cropW = hiBbox.maxX - hiBbox.minX + 1;
    const cropH = hiBbox.maxY - hiBbox.minY + 1;
    const cropCanvas = document.createElement('canvas');
    cropCanvas.width = cropW;
    cropCanvas.height = cropH;
    cropCanvas.getContext('2d').drawImage(hiCanvas, hiBbox.minX, hiBbox.minY, cropW, cropH, 0, 0, cropW, cropH);

    return centerBitmapOnStudioWhite(cropCanvas, cropW, cropH);
  } catch {
    return sourceDataUrl;
  }
}

export const removeBackgroundOfflineDataUrl = toEcommerceFlatLayDataUrl;

function keepLargestComponent(alpha, w, h) {
  const total = w * h;
  const labels = new Int32Array(total);
  const queue = new Int32Array(total);
  let bestLabel = 0;
  let bestCount = 0;
  let curLabel = 0;

  for (let i = 0; i < total; i++) {
    if (alpha[i] < 115 || labels[i] !== 0) continue;
    curLabel++;
    let head = 0, tail = 0;
    labels[i] = curLabel;
    queue[tail++] = i;
    while (head < tail) {
      const idx = queue[head++];
      const x = idx % w;
      const y = (idx / w) | 0;
      if (x > 0 && alpha[idx - 1] >= 115 && labels[idx - 1] === 0) { labels[idx - 1] = curLabel; queue[tail++] = idx - 1; }
      if (x + 1 < w && alpha[idx + 1] >= 115 && labels[idx + 1] === 0) { labels[idx + 1] = curLabel; queue[tail++] = idx + 1; }
      if (y > 0 && alpha[idx - w] >= 115 && labels[idx - w] === 0) { labels[idx - w] = curLabel; queue[tail++] = idx - w; }
      if (y + 1 < h && alpha[idx + w] >= 115 && labels[idx + w] === 0) { labels[idx + w] = curLabel; queue[tail++] = idx + w; }
    }
    if (tail > bestCount) {
      bestCount = tail;
      bestLabel = curLabel;
    }
  }
  if (bestLabel === 0 || bestCount < 32) return;
  for (let i = 0; i < total; i++) {
    if (alpha[i] >= 115 && labels[i] !== bestLabel) alpha[i] = 0;
  }
}

function computeForegroundBbox(alpha, w, h) {
  let minX = w, minY = h, maxX = -1, maxY = -1;
  for (let y = 0; y < h; y++) {
    const off = y * w;
    for (let x = 0; x < w; x++) {
      if (alpha[off + x] >= 115) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  return maxX > minX && maxY > minY ? { minX, minY, maxX, maxY } : null;
}

function stripTopHangerHook(alpha, w, bbox) {
  const boxW = Math.max(1, bbox.maxX - bbox.minX + 1);
  const boxH = Math.max(1, bbox.maxY - bbox.minY + 1);
  const scanEndY = Math.min(bbox.maxY, Math.round(bbox.minY + boxH * 0.16));
  const narrowMaxW = Math.max(4, boxW * 0.14);
  const boxCenterX = (bbox.minX + bbox.maxX) * 0.5;

  for (let y = bbox.minY; y <= scanEndY; y++) {
    const off = y * w;
    let rMinX = bbox.maxX + 1, rMaxX = bbox.minX - 1;
    for (let x = bbox.minX; x <= bbox.maxX; x++) {
      if (alpha[off + x] >= 115) {
        if (x < rMinX) rMinX = x;
        if (x > rMaxX) rMaxX = x;
      }
    }
    if (rMaxX < rMinX) continue;
    const spanW = rMaxX - rMinX + 1;
    const spanCenter = (rMinX + rMaxX) * 0.5;
    if (spanW < narrowMaxW && Math.abs(spanCenter - boxCenterX) < boxW * 0.25) {
      for (let x = rMinX; x <= rMaxX; x++) alpha[off + x] = 0;
    } else {
      break;
    }
  }
}

function applyStudioLightingEqualization(px, alpha, w, bbox) {
  const grid = 36;
  const gSum = new Float32Array(grid * grid);
  const gCnt = new Int32Array(grid * grid);
  const hist = new Int32Array(256);
  const boxW = Math.max(1, bbox.maxX - bbox.minX + 1);
  const boxH = Math.max(1, bbox.maxY - bbox.minY + 1);
  let fgTotal = 0;

  for (let y = bbox.minY; y <= bbox.maxY; y++) {
    const gy = Math.min(grid - 1, Math.floor(((y - bbox.minY) / boxH) * grid));
    const off = y * w;
    for (let x = bbox.minX; x <= bbox.maxX; x++) {
      const idx = off + x;
      if (alpha[idx] < 130) continue;
      const p4 = idx * 4;
      const luma = 0.299 * px[p4] + 0.587 * px[p4 + 1] + 0.114 * px[p4 + 2];
      const gx = Math.min(grid - 1, Math.floor(((x - bbox.minX) / boxW) * grid));
      const gi = gy * grid + gx;
      gSum[gi] += luma;
      gCnt[gi]++;
      hist[Math.min(255, Math.max(0, luma | 0))]++;
      fgTotal++;
    }
  }
  if (fgTotal < 48) return;

  const targetRank = Math.floor(fgTotal * 0.72);
  let running = 0, targetLuma = 180;
  for (let b = 0; b < 256; b++) {
    running += hist[b];
    if (running >= targetRank) { targetLuma = b; break; }
  }
  targetLuma = Math.min(240, Math.max(40, targetLuma));

  const field = new Float32Array(grid * grid);
  for (let i = 0; i < field.length; i++) {
    field[i] = gCnt[i] > 0 ? gSum[i] / gCnt[i] : -1;
  }
  for (let iter = 0; iter < 3; iter++) {
    const next = field.slice();
    for (let gy = 0; gy < grid; gy++) {
      for (let gx = 0; gx < grid; gx++) {
        if (iter === 0 && field[gy * grid + gx] >= 0) continue;
        let sum = 0, cnt = 0;
        for (let dy = -1; dy <= 1; dy++) {
          for (let dx = -1; dx <= 1; dx++) {
            const ny = gy + dy, nx = gx + dx;
            if (ny >= 0 && ny < grid && nx >= 0 && nx < grid) {
              const v = field[ny * grid + nx];
              if (v >= 0) { sum += v; cnt++; }
            }
          }
        }
        if (cnt > 0) next[gy * grid + gx] = sum / cnt;
      }
    }
    field.set(next);
  }

  for (let y = bbox.minY; y <= bbox.maxY; y++) {
    const fy = Math.min(grid - 1, Math.max(0, ((y - bbox.minY) / boxH) * (grid - 1)));
    const y0 = Math.min(grid - 1, Math.floor(fy));
    const y1 = Math.min(grid - 1, y0 + 1);
    const wy = fy - y0;
    const off = y * w;
    for (let x = bbox.minX; x <= bbox.maxX; x++) {
      const idx = off + x;
      if (alpha[idx] < 90) continue;
      const fx = Math.min(grid - 1, Math.max(0, ((x - bbox.minX) / boxW) * (grid - 1)));
      const x0 = Math.min(grid - 1, Math.floor(fx));
      const x1 = Math.min(grid - 1, x0 + 1);
      const wx = fx - x0;
      const l00 = field[y0 * grid + x0] < 0 ? targetLuma : field[y0 * grid + x0];
      const l10 = field[y0 * grid + x1] < 0 ? targetLuma : field[y0 * grid + x1];
      const l01 = field[y1 * grid + x0] < 0 ? targetLuma : field[y1 * grid + x0];
      const l11 = field[y1 * grid + x1] < 0 ? targetLuma : field[y1 * grid + x1];
      const localLuma = Math.max(16, (l00 * (1 - wx) + l10 * wx) * (1 - wy) + (l01 * (1 - wx) + l11 * wx) * wy);

      const p4 = idx * 4;
      const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
      const pxLuma = 0.299 * r + 0.587 * g + 0.114 * b;
      const blackProtect = smoothstep(18, 48, pxLuma);
      const rawGain = Math.min(1.34, Math.max(0.84, targetLuma / localLuma));
      const studioGain = (1 + (rawGain - 1) * 0.56 * blackProtect) * (1 + 0.03 * blackProtect);
      px[p4] = Math.min(255, Math.max(0, Math.round(r * studioGain)));
      px[p4 + 1] = Math.min(255, Math.max(0, Math.round(g * studioGain)));
      px[p4 + 2] = Math.min(255, Math.max(0, Math.round(b * studioGain)));
    }
  }
}

function centerBitmapOnStudioWhite(subjectCanvas, sw, sh) {
  const STUDIO = 1080;
  const PAD_RATIO = 0.095;
  const out = document.createElement('canvas');
  out.width = STUDIO;
  out.height = STUDIO;
  const ctx = out.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, STUDIO, STUDIO);

  const pad = STUDIO * PAD_RATIO;
  const avail = STUDIO - 2 * pad;
  const scale = Math.min(avail / Math.max(1, sw), avail / Math.max(1, sh));
  const dw = sw * scale;
  const dh = sh * scale;
  const left = (STUDIO - dw) * 0.5;
  const top = (STUDIO - dh) * 0.5;
  ctx.drawImage(subjectCanvas, 0, 0, sw, sh, left, top, dw, dh);
  return out.toDataURL('image/jpeg', 0.90);
}

/**
 * Analyzes a studio garment photo to detect dominant hex colors, matching GarmentColor IDs,
 * predicted SubCategory, and suggested clean localized name.
 */
export async function analyzeGarmentDataUrl(studioDataUrl, lang = 'TR') {
  try {
    const img = await loadHtmlImage(studioDataUrl);
    const size = 160;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, size, size);
    const data = ctx.getImageData(0, 0, size, size).data;

    const isFg = new Uint8Array(size * size);
    let minX = size, minY = size, maxX = -1, maxY = -1, fgCount = 0;
    const buckets = new Map();

    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const i = y * size + x;
        const p4 = i * 4;
        const r = data[p4], g = data[p4 + 1], b = data[p4 + 2];
        if (!(r >= 246 && g >= 246 && b >= 246)) {
          isFg[i] = 1;
          fgCount++;
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;

          const key = ((r / 20) | 0) * 1024 + ((g / 20) | 0) * 32 + ((b / 20) | 0);
          const prev = buckets.get(key) || { count: 0, r: 0, g: 0, b: 0 };
          prev.count++; prev.r += r; prev.g += g; prev.b += b;
          buckets.set(key, prev);
        }
      }
    }

    if (fgCount < 25 || maxX <= minX || maxY <= minY) {
      return {
        extractedHexes: ['#FAFAFA'],
        detectedColors: ['WHITE'],
        predictedSubCategory: 'TSHIRT',
        suggestedName: cleanGarmentTitleForName('WHITE', 'TSHIRT', lang)
      };
    }

    const sorted = [...buckets.values()].sort((a, b) => b.count - a.count);
    const pickedHexes = [];
    const detectedColors = [];
    const rgbList = [];

    for (const entry of sorted) {
      const ar = Math.round(entry.r / entry.count);
      const ag = Math.round(entry.g / entry.count);
      const ab = Math.round(entry.b / entry.count);
      const tooClose = rgbList.some(([er, eg, eb]) => Math.hypot(ar - er, ag - eg, ab - eb) < 42);
      if (!tooClose) {
        rgbList.push([ar, ag, ab]);
        const hex = `#${ar.toString(16).padStart(2, '0')}${ag.toString(16).padStart(2, '0')}${ab.toString(16).padStart(2, '0')}`.toUpperCase();
        pickedHexes.push(hex);
        const colId = matchGarmentColorHsv(ar, ag, ab);
        if (!detectedColors.includes(colId)) detectedColors.push(colId);
        if (pickedHexes.length >= 3) break;
      }
    }

    const primaryColor = detectedColors[0] || 'BLACK';
    const subCat = predictSubCategory(isFg, size, minX, minY, maxX, maxY, fgCount, primaryColor);
    return {
      extractedHexes: pickedHexes,
      detectedColors: detectedColors.slice(0, 2),
      predictedSubCategory: subCat,
      suggestedName: cleanGarmentTitleForName(primaryColor, subCat, lang)
    };
  } catch {
    return {
      extractedHexes: [],
      detectedColors: ['BLACK'],
      predictedSubCategory: 'TSHIRT',
      suggestedName: cleanGarmentTitleForName('BLACK', 'TSHIRT', lang)
    };
  }
}

function predictSubCategory(isFg, w, minX, minY, maxX, maxY, fgCount, primaryColor) {
  const boxW = maxX - minX + 1;
  const boxH = maxY - minY + 1;
  const aspectRatio = boxH / boxW;
  const fillDensity = fgCount / (boxW * boxH);

  if (aspectRatio < 0.78) {
    return fillDensity < 0.35 ? 'GLASSES' : 'SNEAKER';
  }

  const midXStart = Math.floor(minX + boxW * 0.43);
  const midXEnd = Math.floor(minX + boxW * 0.57);
  const lowerYStart = Math.floor(minY + boxH * 0.62);
  const lowerYEnd = Math.floor(maxY - boxH * 0.04);
  let lowerTotal = 0, lowerEmpty = 0;
  for (let y = lowerYStart; y <= lowerYEnd; y++) {
    for (let x = midXStart; x <= midXEnd; x++) {
      lowerTotal++;
      if (!isFg[y * w + x]) lowerEmpty++;
    }
  }
  const legSplitRatio = lowerTotal > 0 ? lowerEmpty / lowerTotal : 0;

  const topY = Math.floor(minY + boxH * 0.25);
  const botY = Math.floor(minY + boxH * 0.80);
  let topW = 0, botW = 0;
  for (let x = minX; x <= maxX; x++) {
    if (isFg[topY * w + x]) topW++;
    if (isFg[botY * w + x]) botW++;
  }

  if (legSplitRatio > 0.46 && topW >= botW * 0.7) {
    if (aspectRatio < 1.15) return 'SHORTS';
    if (['DENIM', 'NAVY', 'SKY'].includes(primaryColor)) return 'JEANS';
    if (['LIGHT_GRAY', 'GRAY'].includes(primaryColor)) return 'SWEATPANTS';
    return 'PANTS';
  }

  const hoodEndY = Math.floor(minY + boxH * 0.14);
  const hoodXStart = Math.floor(minX + boxW * 0.38);
  const hoodXEnd = Math.floor(minX + boxW * 0.62);
  let hoodFg = 0, hoodTot = 0;
  for (let y = minY; y <= hoodEndY; y++) {
    for (let x = hoodXStart; x <= hoodXEnd; x++) {
      hoodTot++;
      if (isFg[y * w + x]) hoodFg++;
    }
  }
  const hoodFill = hoodTot > 0 ? hoodFg / hoodTot : 0;

  const sleeveCheckY = Math.floor(minY + boxH * 0.56);
  let sleeveFg = 0;
  for (let x = minX; x <= maxX; x++) {
    if (isFg[sleeveCheckY * w + x]) sleeveFg++;
  }
  const midWidthRatio = sleeveFg / boxW;

  if (aspectRatio > 1.28 && fillDensity > 0.56) return 'COAT';
  if (hoodFill > 0.72 && midWidthRatio > 0.62) return 'HOODIE';
  if (midWidthRatio > 0.64 && fillDensity > 0.52) return 'SWEATSHIRT';
  if (aspectRatio < 1.05) return 'TSHIRT';
  return 'SHIRT';
}

function rgbToHsv(r, g, b) {
  const rn = r / 255, gn = g / 255, bn = b / 255;
  const maxC = Math.max(rn, gn, bn);
  const minC = Math.min(rn, gn, bn);
  const d = maxC - minC;
  let h = 0;
  if (d !== 0) {
    if (maxC === rn) h = ((gn - bn) / d) % 6;
    else if (maxC === gn) h = (bn - rn) / d + 2;
    else h = (rn - gn) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  const s = maxC === 0 ? 0 : d / maxC;
  return [h, s, maxC];
}

function matchGarmentColorHsv(r, g, b) {
  const [hue, sat, valB] = rgbToHsv(r, g, b);
  if (sat < 0.14) {
    if (valB < 0.20) return 'BLACK';
    if (valB < 0.36) return 'CHARCOAL';
    if (valB < 0.62) return 'GRAY';
    if (valB < 0.84) return 'LIGHT_GRAY';
    return (r - b > 8) ? 'ECRU' : 'WHITE';
  }
  if (valB < 0.22) {
    if (hue >= 195 && hue <= 255) return 'NAVY';
    if (hue >= 15 && hue <= 45) return 'BROWN';
    if (hue >= 325 || hue < 15) return 'BURGUNDY';
    return 'BLACK';
  }
  if (sat < 0.32 && hue >= 20 && hue <= 55) {
    if (valB > 0.86) return 'ECRU';
    if (valB > 0.72) return 'BEIGE';
    if (valB > 0.52) return 'STONE';
    return 'BROWN';
  }
  if (hue < 14 || hue >= 348) {
    if (sat < 0.35 && valB > 0.72) return 'BLUSH';
    return valB < 0.45 ? 'BURGUNDY' : 'RED';
  }
  if (hue <= 32) {
    if (valB < 0.46) return 'BROWN';
    return (sat > 0.58 && valB > 0.68) ? 'ORANGE' : 'TERRACOTTA';
  }
  if (hue <= 44) {
    if (valB < 0.48) return 'BROWN';
    return sat > 0.55 ? 'MUSTARD' : 'CAMEL';
  }
  if (hue <= 68) {
    if (valB > 0.66) return 'YELLOW';
    return sat < 0.42 ? 'OLIVE' : 'MUSTARD';
  }
  if (hue <= 150) {
    if (sat < 0.32 && valB > 0.55) return 'SAGE';
    return valB < 0.45 ? 'OLIVE' : 'GREEN';
  }
  if (hue <= 185) return (valB > 0.68 && sat < 0.45) ? 'MINT' : 'TURQUOISE';
  if (hue <= 212) return valB > 0.68 ? 'SKY' : 'DENIM';
  if (hue <= 255) {
    if (valB < 0.38) return 'NAVY';
    return sat > 0.55 ? 'COBALT' : 'DENIM';
  }
  if (hue <= 312) return 'PURPLE';
  if (hue <= 348) {
    if (valB < 0.45) return 'BURGUNDY';
    return (sat < 0.36 && valB > 0.72) ? 'BLUSH' : 'PINK';
  }
  return 'BURGUNDY';
}

/**
 * 12-Season CIE L*a*b* (D65) Skin Tone Analyzer — Exact 1:1 port of PersonalColorAnalyzer.kt
 */
export const PERSONAL_COLOR_SEASONS = [
  'LIGHT_SPRING', 'TRUE_SPRING', 'BRIGHT_SPRING',
  'LIGHT_SUMMER', 'TRUE_SUMMER', 'SOFT_SUMMER',
  'SOFT_AUTUMN', 'TRUE_AUTUMN', 'DEEP_AUTUMN',
  'DEEP_WINTER', 'TRUE_WINTER', 'BRIGHT_WINTER'
];

const SEASON_PALETTES = {
  LIGHT_SPRING: [['CHAMPAGNE', 'ECRU', 'SAND', 'CORAL', 'MINT', 'SKY', 'TURQUOISE', 'BEIGE', 'CAMEL', 'SAGE', 'BLUSH', 'YELLOW'], ['BLACK', 'PLUM', 'BURGUNDY']],
  TRUE_SPRING: [['CORAL', 'ORANGE', 'YELLOW', 'TERRACOTTA', 'TURQUOISE', 'GREEN', 'CAMEL', 'ECRU', 'MINT', 'DENIM', 'OCHRE', 'CHOCOLATE'], ['BLACK', 'LIGHT_GRAY', 'PLUM']],
  BRIGHT_SPRING: [['RED', 'CORAL', 'COBALT', 'TURQUOISE', 'EMERALD', 'YELLOW', 'ECRU', 'BLACK', 'PINK', 'TEAL', 'TERRACOTTA', 'WHITE'], ['STONE', 'GRAY', 'ROSEWOOD']],
  LIGHT_SUMMER: [['ICE_BLUE', 'LAVENDER', 'BLUSH', 'SKY', 'MINT', 'LIGHT_GRAY', 'ROSEWOOD', 'WHITE', 'DENIM', 'SLATE_BLUE', 'PINK', 'SAGE'], ['ORANGE', 'MUSTARD', 'RUST']],
  TRUE_SUMMER: [['NAVY', 'SLATE_BLUE', 'ICE_BLUE', 'LAVENDER', 'BURGUNDY', 'ROSEWOOD', 'TEAL', 'WHITE', 'GRAY', 'BLUSH', 'PLUM', 'DENIM'], ['ORANGE', 'OCHRE', 'TERRACOTTA']],
  SOFT_SUMMER: [['SAGE', 'SLATE_BLUE', 'ROSEWOOD', 'LAVENDER', 'GRAY', 'STONE', 'DENIM', 'PLUM', 'TEAL', 'ECRU', 'CHARCOAL', 'BLUSH'], ['ORANGE', 'YELLOW', 'BLACK']],
  SOFT_AUTUMN: [['OLIVE', 'SAGE', 'BEIGE', 'SAND', 'STONE', 'CAMEL', 'TERRACOTTA', 'ROSEWOOD', 'TEAL', 'BROWN', 'ECRU', 'DENIM'], ['COBALT', 'LIGHT_GRAY', 'WHITE']],
  TRUE_AUTUMN: [['RUST', 'TERRACOTTA', 'OLIVE', 'MUSTARD', 'CAMEL', 'FOREST', 'OCHRE', 'CHOCOLATE', 'BROWN', 'ECRU', 'TEAL', 'WINE'], ['ICE_BLUE', 'LIGHT_GRAY', 'PINK']],
  DEEP_AUTUMN: [['CHOCOLATE', 'RUST', 'TERRACOTTA', 'FOREST', 'OLIVE', 'OCHRE', 'WINE', 'BURGUNDY', 'PETROL', 'CAMEL', 'ECRU', 'BLACK'], ['ICE_BLUE', 'BLUSH', 'LIGHT_GRAY']],
  DEEP_WINTER: [['BLACK', 'WHITE', 'BURGUNDY', 'WINE', 'EMERALD', 'COBALT', 'PLUM', 'NAVY', 'PETROL', 'RED', 'CHARCOAL', 'ICE_BLUE'], ['ORANGE', 'CAMEL', 'SAND']],
  TRUE_WINTER: [['BLACK', 'WHITE', 'COBALT', 'RED', 'EMERALD', 'ICE_BLUE', 'NAVY', 'BURGUNDY', 'PURPLE', 'CHARCOAL', 'PINK', 'TEAL'], ['OCHRE', 'CAMEL', 'RUST']],
  BRIGHT_WINTER: [['BLACK', 'WHITE', 'COBALT', 'EMERALD', 'RED', 'PINK', 'PURPLE', 'TURQUOISE', 'ICE_BLUE', 'NAVY', 'WINE', 'YELLOW'], ['BEIGE', 'CAMEL', 'STONE']]
};

const SEASON_META = {
  LIGHT_SPRING: { undertone: 'WARM', depth: 'FAIR', trTitle: 'Açık İlkbahar', enTitle: 'Light Spring', trSum: 'Açık, sıcak ve taze tonlar yüzünü canlandırır; şampanya, mercan, mint ve açık camel idealdir.', enSum: 'Light, warm, airy shades brighten your complexion; champagne, coral, mint, and light camel shine.' },
  TRUE_SPRING: { undertone: 'WARM', depth: 'FAIR', trTitle: 'Sıcak İlkbahar', enTitle: 'True Spring', trSum: 'Sıcak ve canlı tonlar enerjini öne çıkarır; kiremit, turkuaz, hardal, yeşil ve camel çok yakışır.', enSum: 'Warm golden hues flatter your natural glow; terracotta, turquoise, coral, and camel stand out.' },
  BRIGHT_SPRING: { undertone: 'WARM', depth: 'MEDIUM', trTitle: 'Canlı İlkbahar', enTitle: 'Bright Spring', trSum: 'Yüksek kontrastlı sıcak ve parlak renkler seni ön plana çıkarır; saks mavi, mercan ve net ekru dengelidir.', enSum: 'Crisp high-clarity warm colors suit your contrast; cobalt, coral, emerald, and sharp ecru pop.' },
  LIGHT_SUMMER: { undertone: 'COOL', depth: 'FAIR', trTitle: 'Açık Yaz', enTitle: 'Light Summer', trSum: 'Soğuk, pastel ve yumuşak tonlar cildini duru gösterir; buz mavisi, lavanta, pudra ve gül kurusu öne çıkar.', enSum: 'Cool pastel tones soften your features; ice blue, lavender, blush, and misty rose look effortless.' },
  TRUE_SUMMER: { undertone: 'COOL', depth: 'MEDIUM', trTitle: 'Soğuk Yaz', enTitle: 'True Summer', trSum: 'Serin ve dengeli tonlar tenine berraklık katar; lacivert, arduvaz mavi, bordo ve net beyaz idealdir.', enSum: 'Cool, refined shades clarify your skin; navy, slate blue, burgundy, and cool rosewood excel.' },
  SOFT_SUMMER: { undertone: 'COOL', depth: 'MEDIUM', trTitle: 'Yumuşak Yaz', enTitle: 'Soft Summer', trSum: 'Yumuşak, puslu ve nötr-soğuk renkler yüzünle bütünleşir; adaçayı, arduvaz mavi, gül kurusu ve taş tonları harikadır.', enSum: 'Muted cool-neutral tones blend harmoniously with your skin; sage, slate, taupe, and plum work best.' },
  SOFT_AUTUMN: { undertone: 'NEUTRAL', depth: 'MEDIUM', trTitle: 'Yumuşak Sonbahar', enTitle: 'Soft Autumn', trSum: 'Zeytin, adaçayı, bej, kiremit ve sıcak taş tonları nötr-sıcak dengeni mükemmel vurgular.', enSum: 'Muted earthy shades like olive, sage, sand, terracotta, and teal bring out your balanced warmth.' },
  TRUE_AUTUMN: { undertone: 'WARM', depth: 'MEDIUM', trTitle: 'Sıcak Sonbahar', enTitle: 'True Autumn', trSum: 'Zengin toprak ve baharat tonları yüzünü ısıtır; pas turuncu, haki, çikolata ve safran çok güçlü durur.', enSum: 'Rich spiced earth tones harmonize effortlessly; rust, forest green, mustard, and chocolate radiate.' },
  DEEP_AUTUMN: { undertone: 'WARM', depth: 'DEEP', trTitle: 'Derin Sonbahar', enTitle: 'Deep Autumn', trSum: 'Derin, yoğun ve sıcak tonlar yüksek kontrastını taşır; şarap bordo, petrol, çikolata ve orman yeşili idealdir.', enSum: 'Deep warm pigments anchor your rich complexion; wine, chocolate, forest, and petrol look regal.' },
  DEEP_WINTER: { undertone: 'COOL', depth: 'DEEP', trTitle: 'Derin Kış', enTitle: 'Deep Winter', trSum: 'Soğuk, derin ve net renkler cildini parlatır; siyah, net beyaz, bordo, saks mavi ve zümrüt çok şıktır.', enSum: 'Rich jewel tones and crisp neutrals suit you best; black, crisp white, emerald, and wine shine.' },
  TRUE_WINTER: { undertone: 'COOL', depth: 'DEEP', trTitle: 'Soğuk Kış', enTitle: 'True Winter', trSum: 'Keskin, soğuk ve yüksek kontrastlı tonlar senin içindir; net siyah-beyaz, kobalt mavi, kırmızı ve buz mavisi öne çıkar.', enSum: 'Pure icy cool shades amplify your clarity; cobalt, true red, black, crisp white, and ice blue win.' },
  BRIGHT_WINTER: { undertone: 'NEUTRAL', depth: 'FAIR', trTitle: 'Canlı Kış', enTitle: 'Bright Winter', trSum: 'Net, parlak ve soğuk/nötr tonlar doğal ışıltını öne çıkarır; kobalt, zümrüt, turkuaz ve siyah kontrastı parlatır.', enSum: 'Electric jewel tones balanced with sharp blacks and whites showcase your crisp facial contrast.' }
};

export function seasonShortLabel(season, lang = 'TR') {
  const m = SEASON_META[season] || SEASON_META.SOFT_AUTUMN;
  return localizedText(lang, m.trTitle, m.enTitle);
}

function rgbToLabJS(r, g, b) {
  const lin = (c) => {
    const s = c / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const lr = lin(r), lg = lin(g), lb = lin(b);
  const x = (lr * 0.4124564 + lg * 0.3575761 + lb * 0.1804375) / 0.95047;
  const y = (lr * 0.2126729 + lg * 0.7151522 + lb * 0.0721750) / 1.00000;
  const z = (lr * 0.0193339 + lg * 0.1191920 + lb * 0.9503041) / 1.08883;
  const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  const fx = f(x), fy = f(y), fz = f(z);
  return { l: 116 * fy - 16, a: 500 * (fx - fy), b: 200 * (fy - fz) };
}

function trimmedMeanJS(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((x, y) => x - y);
  const n = sorted.length;
  if (n < 8) return sorted[(n / 2) | 0];
  const start = (n / 4) | 0;
  const end = Math.max(start + 1, ((n * 3) / 4) | 0);
  let acc = 0;
  for (let i = start; i < end; i++) acc += sorted[i];
  return acc / (end - start);
}

export function buildPersonalColorForSeason(season, avgSkinHex = '#CDB99C', lang = 'TR', opts = {}) {
  const m = SEASON_META[season] || SEASON_META.SOFT_AUTUMN;
  const [flatteringColors, avoidColors] = SEASON_PALETTES[season] || SEASON_PALETTES.SOFT_AUTUMN;
  const undertone = opts.undertone || m.undertone;
  const depth = opts.depth || m.depth;
  const contrast = opts.contrast || 'MEDIUM';
  const undertoneLabel = undertone === 'WARM'
    ? localizedText(lang, 'Sıcak Alt Ton', 'Warm Undertone', 'Warmer Unterton', 'Sous-ton chaud', 'Subtono cálido', '暖色调肤色')
    : undertone === 'COOL'
      ? localizedText(lang, 'Soğuk Alt Ton', 'Cool Undertone', 'Kühler Unterton', 'Sous-ton froid', 'Subtono frío', '冷色调肤色')
      : localizedText(lang, 'Nötr Alt Ton', 'Neutral Undertone', 'Neutraler Unterton', 'Sous-ton neutre', 'Subtono neutro', '自然中性肤色');
  const contrastLabel = contrast === 'HIGH'
    ? localizedText(lang, 'Yüksek Kontrast', 'High Contrast')
    : contrast === 'SOFT'
      ? localizedText(lang, 'Yumuşak Kontrast', 'Soft Contrast')
      : localizedText(lang, 'Orta Kontrast', 'Medium Contrast');
  return {
    avgSkinHex,
    undertone,
    undertoneLabel,
    depth,
    contrast,
    contrastLabel,
    season,
    seasonTitle: localizedText(lang, m.trTitle, m.enTitle),
    summaryText: localizedText(lang, m.trSum, m.enSum),
    flatteringColors,
    avoidColors,
    labHueDeg: opts.labHueDeg ?? 54
  };
}

export function analyzeSkinSamplesLab(skinRgbList, ambientRgb = null, darkFeatureL = null, lang = 'TR') {
  if (!skinRgbList || !skinRgbList.length) {
    return buildPersonalColorForSeason('SOFT_AUTUMN', '#CDB99C', lang);
  }
  let gainR = 1, gainG = 1, gainB = 1;
  if (ambientRgb && ambientRgb[0] > 30 && ambientRgb[1] > 30 && ambientRgb[2] > 30) {
    const meanAmb = (ambientRgb[0] + ambientRgb[1] + ambientRgb[2]) / 3;
    gainR = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[0]));
    gainG = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[1]));
    gainB = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[2]));
  }
  const labs = [];
  let sumR = 0, sumG = 0, sumB = 0;
  for (const [rr, gg, bb] of skinRgbList) {
    const r = Math.min(255, Math.max(0, Math.round(rr * gainR)));
    const g = Math.min(255, Math.max(0, Math.round(gg * gainG)));
    const b = Math.min(255, Math.max(0, Math.round(bb * gainB)));
    const lab = rgbToLabJS(r, g, b);
    if (lab.l >= 16 && lab.l <= 92) {
      labs.push(lab);
      sumR += r; sumG += g; sumB += b;
    }
  }
  const safeLabs = labs.length ? labs : skinRgbList.map(([r, g, b]) => rgbToLabJS(r, g, b));
  const cnt = Math.max(1, labs.length);
  const avgR = labs.length ? Math.round(sumR / cnt) : 200;
  const avgG = labs.length ? Math.round(sumG / cnt) : 170;
  const avgB = labs.length ? Math.round(sumB / cnt) : 150;
  const avgSkinHex = `#${avgR.toString(16).padStart(2, '0')}${avgG.toString(16).padStart(2, '0')}${avgB.toString(16).padStart(2, '0')}`.toUpperCase();

  const lMed = trimmedMeanJS(safeLabs.map((x) => x.l));
  const aMed = trimmedMeanJS(safeLabs.map((x) => x.a));
  const bMed = trimmedMeanJS(safeLabs.map((x) => x.b));

  const chroma = Math.hypot(aMed, bMed);
  let hueDeg = Math.atan2(bMed, aMed) * 180 / Math.PI;
  if (hueDeg < 0) hueDeg += 360;

  const depth = lMed >= 68 ? 'FAIR' : (lMed >= 52 ? 'MEDIUM' : 'DEEP');
  const bMinusA = bMed - aMed;
  let undertone = 'NEUTRAL';
  if (hueDeg < 45 || bMinusA < -0.5) undertone = 'COOL';
  else if (hueDeg > 59.5 && hueDeg <= 80 && bMinusA >= 7.5 && aMed >= 5.0) undertone = 'WARM';

  const facialDelta = darkFeatureL != null
    ? Math.min(80, Math.max(0, lMed - darkFeatureL))
    : (depth === 'DEEP' ? 27 : depth === 'MEDIUM' ? 24 : 22);
  const contrast = (facialDelta >= 36 || chroma >= 25)
    ? 'HIGH'
    : (facialDelta < 22 && chroma < 19 ? 'SOFT' : 'MEDIUM');

  let season = 'SOFT_AUTUMN';
  if (undertone === 'WARM') {
    if (depth === 'FAIR') season = contrast === 'HIGH' ? 'BRIGHT_SPRING' : (contrast === 'SOFT' || chroma < 20 ? 'LIGHT_SPRING' : 'TRUE_SPRING');
    else if (depth === 'MEDIUM') season = contrast === 'SOFT' ? 'SOFT_AUTUMN' : (contrast === 'HIGH' ? 'TRUE_SPRING' : 'TRUE_AUTUMN');
    else season = 'DEEP_AUTUMN';
  } else if (undertone === 'COOL') {
    if (depth === 'FAIR') season = contrast === 'HIGH' ? 'BRIGHT_WINTER' : (contrast === 'SOFT' ? 'LIGHT_SUMMER' : 'TRUE_SUMMER');
    else if (depth === 'MEDIUM') season = contrast === 'HIGH' ? 'TRUE_WINTER' : (contrast === 'SOFT' ? 'SOFT_SUMMER' : 'TRUE_SUMMER');
    else season = contrast === 'HIGH' ? 'TRUE_WINTER' : 'DEEP_WINTER';
  } else {
    if (depth === 'FAIR') season = contrast === 'HIGH' ? 'BRIGHT_SPRING' : (hueDeg >= 53 ? 'LIGHT_SPRING' : 'LIGHT_SUMMER');
    else if (depth === 'MEDIUM') season = contrast === 'HIGH' ? 'BRIGHT_WINTER' : (hueDeg >= 53 ? 'SOFT_AUTUMN' : 'SOFT_SUMMER');
    else season = hueDeg >= 54 ? 'DEEP_AUTUMN' : 'DEEP_WINTER';
  }

  return buildPersonalColorForSeason(season, avgSkinHex, lang, { undertone, depth, contrast, labHueDeg: hueDeg });
}

export async function analyzeFaceSkinTone(faceDataUrl, lang = 'TR') {
  if (!faceDataUrl || !isSafeImageDataUrl(faceDataUrl)) return null;
  try {
    const img = await loadHtmlImage(faceDataUrl);
    const natW = img.naturalWidth || img.width || 0;
    const natH = img.naturalHeight || img.height || 0;
    if (natW < 2 || natH < 2) return null;
    const w = Math.min(320, natW);
    const h = Math.max(64, Math.round((w * natH) / Math.max(1, natW)));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    ctx.drawImage(img, 0, 0, w, h);
    const px = ctx.getImageData(0, 0, w, h).data;

    const minX = Math.floor(w * 0.25), maxX = Math.floor(w * 0.75);
    const minY = Math.floor(h * 0.08), maxY = Math.floor(h * 0.54);
    const eyeTop = Math.floor(h * 0.24), eyeBot = Math.floor(h * 0.34);

    const isLikelySkin = (r, g, b) => {
      if (r <= 65 || g <= 32 || b <= 18 || r <= g || r <= b) return false;
      const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
      const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
      return cb >= 77 && cb <= 129 && cr >= 132 && cr <= 178;
    };
    const isRelaxedSkin = (r, g, b) => {
      if (r <= 32 || g <= 18 || b <= 10 || r < g * 0.88 || r < b * 0.84) return false;
      const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
      const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
      return cb >= 68 && cb <= 142 && cr >= 122 && cr <= 188;
    };

    let ambR = 0, ambG = 0, ambB = 0, ambCnt = 0;
    const edgeStep = Math.max(1, Math.floor(Math.min(w, h) / 40));
    for (let y = 0; y < h; y += edgeStep) {
      for (let x = 0; x < w; x += edgeStep) {
        if (x >= minX && x < maxX && y >= minY && y < maxY) continue;
        const p4 = (y * w + x) * 4;
        if (px[p4 + 3] < 128) continue;
        const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        if (lum >= 55 && lum <= 242 && !isLikelySkin(r, g, b)) {
          ambR += r; ambG += g; ambB += b; ambCnt++;
        }
      }
    }
    const ambientRgb = ambCnt >= 12 ? [Math.round(ambR / ambCnt), Math.round(ambG / ambCnt), Math.round(ambB / ambCnt)] : null;
    let gainR = 1, gainG = 1, gainB = 1;
    if (ambientRgb && ambientRgb[0] > 30 && ambientRgb[1] > 30 && ambientRgb[2] > 30) {
      const meanAmb = (ambientRgb[0] + ambientRgb[1] + ambientRgb[2]) / 3;
      gainR = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[0]));
      gainG = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[1]));
      gainB = Math.min(1.14, Math.max(0.86, meanAmb / ambientRgb[2]));
    }

    const darkLs = [];
    const hairTop = Math.max(0, minY - Math.floor((maxY - minY) * 0.28));
    const hairBot = Math.min(h, minY + Math.floor((maxY - minY) * 0.45));
    for (let y = hairTop; y < hairBot; y += 2) {
      for (let x = minX; x < maxX; x += 2) {
        const p4 = (y * w + x) * 4;
        if (px[p4 + 3] < 128) continue;
        const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
        if (!isLikelySkin(r, g, b)) {
          const lum = 0.299 * r + 0.587 * g + 0.114 * b;
          if (lum >= 8 && lum <= 125) darkLs.push(rgbToLabJS(r, g, b).l);
        }
      }
    }
    let darkFeatureL = null;
    if (darkLs.length >= 8) {
      darkLs.sort((a, b) => a - b);
      darkFeatureL = darkLs[Math.floor(darkLs.length * 0.25)];
    }

    // Stage 1: strict skin pixels inside face ROI (preserving daylight calibration)
    const skinSamples = [];
    for (let y = minY; y < maxY; y++) {
      if (y >= eyeTop && y <= eyeBot) continue;
      for (let x = minX; x < maxX; x++) {
        const p4 = (y * w + x) * 4;
        if (px[p4 + 3] < 128) continue;
        const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
        if (isLikelySkin(r, g, b)) skinSamples.push([r, g, b]);
      }
    }

    // Stage 2: illuminant-balanced & relaxed chrominance for deep/shadowed/warm/cool lighting
    if (skinSamples.length < 25) {
      for (let y = minY; y < maxY; y++) {
        if (y >= eyeTop && y <= eyeBot) continue;
        for (let x = minX; x < maxX; x++) {
          const p4 = (y * w + x) * 4;
          if (px[p4 + 3] < 128) continue;
          const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
          const cr = Math.min(255, Math.max(0, Math.round(r * gainR)));
          const cg = Math.min(255, Math.max(0, Math.round(g * gainG)));
          const cb = Math.min(255, Math.max(0, Math.round(b * gainB)));
          if (!isLikelySkin(r, g, b) && isRelaxedSkin(cr, cg, cb)) {
            skinSamples.push([r, g, b]);
          }
        }
      }
    }

    // Stage 3: focused inner upper-center face ROI excluding eye band
    const fMinX = Math.floor(w * 0.28), fMaxX = Math.max(fMinX + 1, Math.floor(w * 0.72));
    const fMinY = Math.floor(h * 0.12), fMaxY = Math.max(fMinY + 1, Math.floor(h * 0.56));
    if (skinSamples.length < 16) {
      for (let y = fMinY; y < fMaxY; y += 2) {
        if (y >= eyeTop && y <= eyeBot) continue;
        for (let x = fMinX; x < fMaxX; x += 2) {
          const p4 = (y * w + x) * 4;
          if (px[p4 + 3] < 128) continue;
          const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
          const lum = 0.299 * r + 0.587 * g + 0.114 * b;
          if (lum >= 18 && lum <= 246) skinSamples.push([r, g, b]);
        }
      }
    }

    // Stage 4: ultimate inner ROI fallback so any decodable photo yields a valid result
    if (!skinSamples.length) {
      for (let y = fMinY; y < fMaxY; y += 2) {
        for (let x = fMinX; x < fMaxX; x += 2) {
          const p4 = (y * w + x) * 4;
          if (px[p4 + 3] >= 128) skinSamples.push([px[p4], px[p4 + 1], px[p4 + 2]]);
        }
      }
    }
    if (!skinSamples.length) {
      for (let y = 0; y < h; y += 4) {
        for (let x = 0; x < w; x += 4) {
          const p4 = (y * w + x) * 4;
          if (px[p4 + 3] > 0) skinSamples.push([px[p4], px[p4 + 1], px[p4 + 2]]);
        }
      }
    }
    if (!skinSamples.length) return null;
    return analyzeSkinSamplesLab(skinSamples, ambientRgb, darkFeatureL, lang);
  } catch {
    return null;
  }
}

/**
 * Generates person silhouette mask + normalized body anchor coordinates for 3D Kabin drape.
 */
export async function analyzeBodyFor3D(bodyImg) {
  const maxDim = 280;
  const scale = Math.min(1, maxDim / Math.max(bodyImg.width || 1, bodyImg.height || 1));
  const w = Math.max(64, Math.round(bodyImg.width * scale));
  const h = Math.max(64, Math.round(bodyImg.height * scale));

  const work = document.createElement('canvas');
  work.width = w;
  work.height = h;
  const ctx = work.getContext('2d');
  ctx.drawImage(bodyImg, 0, 0, w, h);
  const px = ctx.getImageData(0, 0, w, h).data;

  // Perimeter flood-fill + center salience for person silhouette mask
  let bgR = 0, bgG = 0, bgB = 0, bgCnt = 0;
  for (let x = 0; x < w; x++) {
    for (const y of [0, h - 1]) {
      const p4 = (y * w + x) * 4;
      bgR += px[p4]; bgG += px[p4 + 1]; bgB += px[p4 + 2]; bgCnt++;
    }
  }
  for (let y = 0; y < h; y++) {
    for (const x of [0, w - 1]) {
      const p4 = (y * w + x) * 4;
      bgR += px[p4]; bgG += px[p4 + 1]; bgB += px[p4 + 2]; bgCnt++;
    }
  }
  bgR /= Math.max(1, bgCnt); bgG /= Math.max(1, bgCnt); bgB /= Math.max(1, bgCnt);

  const maskData = new Uint8ClampedArray(w * h * 4);
  const alphaArr = new Uint8Array(w * h);
  alphaArr.fill(255);
  const visited = new Uint8Array(w * h);
  const q = new Int32Array(w * h);
  let head = 0, tail = 0;
  for (let x = 0; x < w; x++) { q[tail++] = x; visited[x] = 1; q[tail++] = (h - 1) * w + x; visited[(h - 1) * w + x] = 1; }
  for (let y = 0; y < h; y++) { q[tail++] = y * w; visited[y * w] = 1; q[tail++] = y * w + w - 1; visited[y * w + w - 1] = 1; }

  while (head < tail) {
    const idx = q[head++];
    const p4 = idx * 4;
    const dist = Math.hypot(px[p4] - bgR, px[p4 + 1] - bgG, px[p4 + 2] - bgB);
    if (dist < 42) {
      alphaArr[idx] = 0;
      const x = idx % w, y = (idx / w) | 0;
      if (x > 0 && !visited[idx - 1]) { visited[idx - 1] = 1; q[tail++] = idx - 1; }
      if (x + 1 < w && !visited[idx + 1]) { visited[idx + 1] = 1; q[tail++] = idx + 1; }
      if (y > 0 && !visited[idx - w]) { visited[idx - w] = 1; q[tail++] = idx - w; }
      if (y + 1 < h && !visited[idx + w]) { visited[idx + w] = 1; q[tail++] = idx + w; }
    }
  }
  keepLargestComponent(alphaArr, w, h);

  let minX = w, minY = h, maxX = -1, maxY = -1, fg = 0;
  let ambR = 0, ambG = 0, ambB = 0, ambCnt = 0;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * w + x;
      const a = alphaArr[i];
      const p4 = i * 4;
      maskData[p4] = 255; maskData[p4 + 1] = 255; maskData[p4 + 2] = 255; maskData[p4 + 3] = a;
      if (a >= 100) {
        fg++;
        if (x < minX) minX = x; if (x > maxX) maxX = x;
        if (y < minY) minY = y; if (y > maxY) maxY = y;
      }
      const lum = (px[p4] + px[p4 + 1] + px[p4 + 2]) / 3;
      if (lum > 35 && lum < 230) {
        ambR += px[p4]; ambG += px[p4 + 1]; ambB += px[p4 + 2]; ambCnt++;
      }
    }
  }

  const maskCanvas = document.createElement('canvas');
  maskCanvas.width = w; maskCanvas.height = h;
  maskCanvas.getContext('2d').putImageData(new ImageData(maskData, w, h), 0, 0);

  const valid = fg > w * h * 0.04 && maxX > minX && maxY > minY;
  const bounds = valid
    ? { left: minX / w, top: minY / h, right: (maxX + 1) / w, bottom: (maxY + 1) / h }
    : { left: 0.24, top: 0.08, right: 0.76, bottom: 0.94 };
  const bodyH = Math.max(0.35, bounds.bottom - bounds.top);
  const neckY = Math.min(0.40, Math.max(0.10, bounds.top + bodyH * 0.17));
  const shoulderY = Math.min(0.50, neckY + bodyH * 0.04);
  const waistY = Math.min(0.78, shoulderY + bodyH * 0.34);
  const ankleY = Math.min(0.96, bounds.bottom - bodyH * 0.04);

  return {
    maskCanvas,
    bounds,
    headCenterX: (bounds.left + bounds.right) * 0.5,
    neckY,
    shoulderY,
    waistY,
    ankleY,
    shoulderSpanNorm: Math.min(0.78, Math.max(0.28, (bounds.right - bounds.left) * 0.72)),
    ambientRgb: ambCnt > 0
      ? [Math.round(ambR / ambCnt), Math.round(ambG / ambCnt), Math.round(ambB / ambCnt)]
      : [190, 190, 190]
  };
}

export const FLAT_LAY_DIFFUSION_PROMPT = STUDIO_ECOMMERCE_PROMPT;

export class StudioQuotaError extends Error {
  constructor(message) {
    super(message || 'Free daily AI quota exceeded');
    this.name = 'StudioQuotaError';
  }
}

export class StudioBusyError extends Error {
  constructor(message) {
    super(message || 'Remote AI servers busy');
    this.name = 'StudioBusyError';
  }
}

export class GeminiApiError extends Error {
  constructor(message, status = null) {
    super(message || 'Gemini API error');
    this.name = 'GeminiApiError';
    this.status = status;
  }
}

const HF_SPACE_CHAIN = [
  { host: 'black-forest-labs-flux-2-dev.hf.space', endpoint: '/infer', mode: 'UPLOAD_GALLERY_FLUX2_DEV' },
  { host: 'black-forest-labs-flux-2-klein-9b.hf.space', endpoint: '/generate', mode: 'UPLOAD_GALLERY_FLUX2_KLEIN_BASE' },
  { host: 'qwen-qwen-image-edit-2511.hf.space', endpoint: '/infer', mode: 'UPLOAD_GALLERY_QWEN_OFFICIAL' },
  { host: 'fireredteam-firered-image-edit-1-1.hf.space', endpoint: '/infer', mode: 'UPLOAD_GALLERY_FIRERED_1_1' },
  { host: 'black-forest-labs-flux-1-kontext-dev.hf.space', endpoint: '/infer', mode: 'UPLOAD_SINGLE_KONTEXT' }
];

const ALLOWED_GEMINI_HOSTS = new Set([
  'generativelanguage.googleapis.com',
  'aiplatform.googleapis.com'
]);
const ALLOWED_OUTPUT_IMAGE_MIMES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp']);
const GEMINI_IMAGE_MODELS = [
  'gemini-nano-banana-2.1',
  'gemini-3.1-flash-image-preview',
  'gemini-2.5-flash-image',
  'gemini-3-pro-image-preview',
  'gemini-3-pro-image',
  'gemini-3.1-flash-image',
  'gemini-3.1-flash-lite-image'
];

function buildSafeDataImageUrl(mimeRaw, b64Raw) {
  const mime = String(mimeRaw || 'image/jpeg').trim().toLowerCase();
  if (!ALLOWED_OUTPUT_IMAGE_MIMES.has(mime)) {
    throw new Error('Desteklenmeyen görsel formatı');
  }
  const normMime = mime === 'image/jpg' ? 'image/jpeg' : mime;
  const cleanB64 = String(b64Raw || '').replace(/\s+/g, '');
  const url = `data:${normMime};base64,${cleanB64}`;
  if (!isSafeImageDataUrl(url)) {
    throw new Error('Geçersiz görsel verisi');
  }
  return url;
}

function extractImageFromInteractions(data) {
  const direct = data?.output_image?.data;
  if (direct) return buildSafeDataImageUrl(data.output_image.mime_type, direct);
  for (const step of (data?.steps || [])) {
    for (const c of (step?.content || [])) {
      if (c?.type === 'image' && c?.data) {
        return buildSafeDataImageUrl(c.mime_type, c.data);
      }
    }
  }
  return null;
}

function extractImageFromGenerateContent(data) {
  for (const cand of (data?.candidates || [])) {
    for (const part of (cand?.content?.parts || [])) {
      const inline = part?.inlineData || part?.inline_data;
      if (inline?.data) {
        return buildSafeDataImageUrl(inline.mimeType || inline.mime_type, inline.data);
      }
    }
  }
  return null;
}

function buildInteractionsRequest(model, prompt, imageB64List, aspectRatio) {
  const input = [{ type: 'text', text: prompt }];
  for (const b64 of imageB64List) {
    input.push({ type: 'image', mime_type: 'image/jpeg', data: b64 });
  }
  return {
    model,
    input,
    response_format: { type: 'image', mime_type: 'image/jpeg', aspect_ratio: aspectRatio }
  };
}

function buildGenerateContentRequest(prompt, imageB64List, aspectRatio, includeImageConfig = true) {
  const parts = [{ text: prompt }];
  for (const b64 of imageB64List) {
    parts.push({ inlineData: { mimeType: 'image/jpeg', data: b64 } });
  }
  const generationConfig = {
    responseModalities: ['TEXT', 'IMAGE']
  };
  if (includeImageConfig && aspectRatio) {
    generationConfig.imageConfig = { aspectRatio };
  }
  return {
    contents: [{ role: 'user', parts }],
    generationConfig
  };
}

function isDefiniteInvalidKeyError(err) {
  if (err?.status === 401) return true;
  const apiMsg = String(err?.apiMessage || '');
  return /api[_\s-]*key.*(invalid|not valid|expired|missing)|unauthenticated/i.test(apiMsg);
}

async function postGeminiEndpoint(url, apiKey, payload, { isGenerateContent = false } = {}) {
  const headers = {
    'x-goog-api-key': apiKey,
    'Content-Type': 'application/json'
  };
  const res = await safeFetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  }, {
    allowedHosts: ALLOWED_GEMINI_HOSTS,
    timeoutMs: 60000,
    maxResponseBytes: 15 * 1024 * 1024
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const apiMsg = data?.error?.message || data?.message || '';
    const detail = apiMsg ? `HTTP ${res.status}: ${sanitizeErrorMessage(apiMsg)}` : `HTTP ${res.status}`;
    const err = new Error(detail);
    err.status = res.status;
    err.apiMessage = String(apiMsg || '');
    throw err;
  }
  const outUrl = isGenerateContent
    ? extractImageFromGenerateContent(data)
    : (extractImageFromInteractions(data) || extractImageFromGenerateContent(data));
  if (!outUrl) {
    throw new Error('Model görsel döndürmedi');
  }
  return outUrl;
}

async function tryGenerateContentUrl(url, apiKey, prompt, imageB64List, aspectRatio) {
  try {
    const gcPayload = buildGenerateContentRequest(prompt, imageB64List, aspectRatio, true);
    return await postGeminiEndpoint(url, apiKey, gcPayload, { isGenerateContent: true });
  } catch (err) {
    if (err?.status === 400 && !isDefiniteInvalidKeyError(err)) {
      const fallbackPayload = buildGenerateContentRequest(prompt, imageB64List, aspectRatio, false);
      return await postGeminiEndpoint(url, apiKey, fallbackPayload, { isGenerateContent: true });
    }
    throw err;
  }
}

/**
 * Unified Gemini image caller supporting both Google AI Studio keys (`AIza...`)
 * and Vertex AI Express Mode keys (`AQ....`).
 */
async function callGeminiImageUnified(apiKey, { prompt, imageB64List, aspectRatio }) {
  const cleanKey = String(apiKey || '').trim();
  if (!isValidGeminiApiKey(cleanKey)) {
    throw new Error('Geçersiz Gemini API anahtarı biçimi');
  }
  let lastErr = new Error('Model görsel döndürmedi');

  if (isVertexExpressApiKey(cleanKey)) {
    for (const model of GEMINI_IMAGE_MODELS) {
      const v1Url = `https://aiplatform.googleapis.com/v1/publishers/google/models/${model}:generateContent`;
      try {
        return await tryGenerateContentUrl(v1Url, cleanKey, prompt, imageB64List, aspectRatio);
      } catch (err) {
        lastErr = err;
        if (isDefiniteInvalidKeyError(err)) throw err;
        if (err?.status === 404 || err?.status === 400) {
          const betaUrl = `https://aiplatform.googleapis.com/v1beta1/publishers/google/models/${model}:generateContent`;
          try {
            return await tryGenerateContentUrl(betaUrl, cleanKey, prompt, imageB64List, aspectRatio);
          } catch (betaErr) {
            lastErr = betaErr;
            if (isDefiniteInvalidKeyError(betaErr)) throw betaErr;
          }
        }
      }
    }
    for (const model of GEMINI_IMAGE_MODELS.slice(0, 4)) {
      const glUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
      try {
        return await tryGenerateContentUrl(glUrl, cleanKey, prompt, imageB64List, aspectRatio);
      } catch (err) {
        lastErr = err;
        if (isDefiniteInvalidKeyError(err)) throw err;
      }
    }
    throw lastErr;
  }

  for (const model of GEMINI_IMAGE_MODELS) {
    const glUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
    try {
      return await tryGenerateContentUrl(glUrl, cleanKey, prompt, imageB64List, aspectRatio);
    } catch (err) {
      lastErr = err;
      if (isDefiniteInvalidKeyError(err)) throw err;
    }
  }

  const interPayload = buildInteractionsRequest(GEMINI_IMAGE_MODELS[0], prompt, imageB64List, aspectRatio);
  const endpoints = [
    'https://generativelanguage.googleapis.com/v1/interactions',
    'https://generativelanguage.googleapis.com/v1beta/interactions'
  ];
  for (const ep of endpoints) {
    try {
      return await postGeminiEndpoint(ep, cleanKey, interPayload, { isGenerateContent: false });
    } catch (err) {
      lastErr = err;
      if (isDefiniteInvalidKeyError(err)) throw err;
    }
  }
  throw lastErr;
}

async function renderGarmentToJpeg(sourceDataUrl, { maxDim = 1024, squareSize = 0, padRatio = 0, quality = 0.92 } = {}) {
  const img = await loadHtmlImage(sourceDataUrl);
  assertSafeImageDimensions(img);
  const srcW = Math.max(1, img.width || 1);
  const srcH = Math.max(1, img.height || 1);
  const canvas = document.createElement('canvas');
  let cw, ch, dw, dh;
  if (squareSize > 0) {
    cw = squareSize;
    ch = squareSize;
    const avail = squareSize * (1 - 2 * padRatio);
    const scale = Math.min(avail / srcW, avail / srcH);
    dw = srcW * scale;
    dh = srcH * scale;
  } else {
    const scale = Math.min(1, maxDim / Math.max(srcW, srcH));
    cw = Math.max(1, Math.round(srcW * scale));
    ch = Math.max(1, Math.round(srcH * scale));
    dw = cw;
    dh = ch;
  }
  canvas.width = cw;
  canvas.height = ch;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, cw, ch);
  ctx.drawImage(img, (cw - dw) * 0.5, (ch - dh) * 0.5, dw, dh);
  const dataUrl = canvas.toDataURL('image/jpeg', quality);
  const b64 = dataUrl.split(',')[1];
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
  return { blob, b64 };
}

async function prepareNaturalInput(sourceDataUrl, maxDim = 1024) {
  return renderGarmentToJpeg(sourceDataUrl, { maxDim, quality: 0.92 });
}

async function prepareSquareInput(sourceDataUrl, targetSize = 1024, padRatio = 0) {
  return renderGarmentToJpeg(sourceDataUrl, { squareSize: targetSize, padRatio, quality: 0.92 });
}

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

export async function polishCatalogDataUrl(rawUrl) {
  if (!isSafeImageDataUrl(rawUrl)) {
    throw new Error('Geçersiz katalog çıktısı');
  }
  try {
    const img = await loadHtmlImage(rawUrl);
    assertSafeImageDimensions(img);
    const w = Math.max(1, img.width || 1);
    const h = Math.max(1, img.height || 1);
    const work = document.createElement('canvas');
    work.width = w;
    work.height = h;
    const ctx = work.getContext('2d');
    ctx.drawImage(img, 0, 0, w, h);
    const imgData = ctx.getImageData(0, 0, w, h);
    const px = imgData.data;

    const isNearWhite = (p4) => {
      const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
      const maxC = Math.max(r, g, b);
      const minC = Math.min(r, g, b);
      return minC >= 242 && (maxC - minC) <= 9;
    };

    const total = w * h;
    const isBg = new Uint8Array(total);
    const visited = new Uint8Array(total);
    const queue = new Int32Array(total);
    let head = 0, tail = 0;

    const seed = (idx) => {
      if (!visited[idx] && isNearWhite(idx * 4)) {
        visited[idx] = 1;
        isBg[idx] = 1;
        queue[tail++] = idx;
      }
    };
    for (let x = 0; x < w; x++) { seed(x); seed((h - 1) * w + x); }
    for (let y = 0; y < h; y++) { seed(y * w); seed(y * w + (w - 1)); }

    while (head < tail) {
      const idx = queue[head++];
      const x = idx % w;
      const y = (idx / w) | 0;
      if (x > 0) seed(idx - 1);
      if (x + 1 < w) seed(idx + 1);
      if (y > 0) seed(idx - w);
      if (y + 1 < h) seed(idx + w);
    }

    const rowFg = new Int32Array(h);
    const colFg = new Int32Array(w);
    let rawMinX = w, rawMinY = h, rawMaxX = -1, rawMaxY = -1, fgCount = 0;
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const idx = y * w + x;
        const p4 = idx * 4;
        if (isBg[idx]) {
          px[p4] = 255; px[p4 + 1] = 255; px[p4 + 2] = 255; px[p4 + 3] = 255;
        } else {
          if (!(px[p4] >= 247 && px[p4 + 1] >= 247 && px[p4 + 2] >= 247)) {
            fgCount++;
            rowFg[y]++;
            colFg[x]++;
            if (x < rawMinX) rawMinX = x; if (x > rawMaxX) rawMaxX = x;
            if (y < rawMinY) rawMinY = y; if (y > rawMaxY) rawMaxY = y;
          }
          px[p4 + 3] = 255;
        }
      }
    }

    const STUDIO = 1080;
    const out = document.createElement('canvas');
    out.width = STUDIO;
    out.height = STUDIO;
    const oCtx = out.getContext('2d');
    oCtx.fillStyle = '#FFFFFF';
    oCtx.fillRect(0, 0, STUDIO, STUDIO);

    const safeFg = fgCount > total * 0.04 && (total - fgCount) < total * 0.96;
    if (!safeFg) {
      const padPx = STUDIO * 0.065;
      const avail = STUDIO - 2 * padPx;
      const scale = Math.min(avail / w, avail / h);
      const dw = w * scale;
      const dh = h * scale;
      oCtx.drawImage(img, 0, 0, w, h, (STUDIO - dw) * 0.5, (STUDIO - dh) * 0.5, dw, dh);
      return out.toDataURL('image/jpeg', 0.92);
    }

    const minRowPx = Math.max(2, Math.floor(w * 0.005));
    const minColPx = Math.max(2, Math.floor(h * 0.005));
    let minX = 0;
    while (minX < w && colFg[minX] < minColPx) minX++;
    let maxX = w - 1;
    while (maxX > minX && colFg[maxX] < minColPx) maxX--;
    let minY = 0;
    while (minY < h && rowFg[minY] < minRowPx) minY++;
    let maxY = h - 1;
    while (maxY > minY && rowFg[maxY] < minRowPx) maxY--;

    if (maxX <= minX + 16 || maxY <= minY + 16) {
      minX = rawMinX; maxX = rawMaxX; minY = rawMinY; maxY = rawMaxY;
    }

    ctx.putImageData(imgData, 0, 0);
    const validBox = maxX > minX + 16 && maxY > minY + 16;
    const sx = validBox ? minX : 0;
    const sy = validBox ? minY : 0;
    const sw = validBox ? (maxX - minX + 1) : w;
    const sh = validBox ? (maxY - minY + 1) : h;

    const padPx = STUDIO * 0.065;
    const avail = STUDIO - 2 * padPx;
    const scale = Math.min(avail / sw, avail / sh);
    const dw = sw * scale;
    const dh = sh * scale;
    oCtx.drawImage(work, sx, sy, sw, sh, (STUDIO - dw) * 0.5, (STUDIO - dh) * 0.5, dw, dh);
    return out.toDataURL('image/jpeg', 0.92);
  } catch {
    return rawUrl;
  }
}

function parseGradioSseOutput(rawSse, spaceHost) {
  if (!ALLOWED_HF_SPACE_HOSTS.has(spaceHost)) {
    return { kind: 'error', message: 'Untrusted host' };
  }
  let lastEvent = '';
  for (const rawLine of rawSse.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line.startsWith('event:')) {
      lastEvent = line.slice(6).trim().toLowerCase();
      continue;
    }
    if (!line.startsWith('data:')) continue;
    const payload = line.slice(5).trim();

    if (lastEvent === 'error') {
      const lower = payload.toLowerCase();
      const msgMatch = /"(?:error|message|title)"\s*:\s*"([^"]{1,240})"/.exec(payload);
      const msg = msgMatch ? clampText(msgMatch[1], 140) : 'Remote studio error';
      if (lower.includes('zerogpu') || lower.includes('quota') || lower.includes('runs limit')) {
        return { kind: 'quota', message: msg };
      }
      return { kind: 'error', message: msg };
    }

    if (lastEvent === 'complete') {
      if (/"status"\s*:\s*"blocked"/.test(payload)) {
        return { kind: 'error', message: 'Safety filter blocked output' };
      }
      const b64Match = /"(data:image\/(?:png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=\s]{32,})"/.exec(payload);
      if (b64Match) {
        const cleanUrl = b64Match[1].replace(/\s+/g, '');
        if (isSafeImageDataUrl(cleanUrl)) {
          return { kind: 'dataUrl', value: cleanUrl };
        }
        return { kind: 'error', message: 'Invalid inline output image' };
      }
      const urlMatch = /"url"\s*:\s*"([^"]+)"/.exec(payload);
      if (urlMatch) {
        const outUrl = urlMatch[1].trim();
        try {
          const parsed = new URL(outUrl);
          if (
            parsed.protocol === 'https:' &&
            !parsed.username &&
            !parsed.password &&
            (parsed.port === '' || parsed.port === '443') &&
            parsed.hostname.toLowerCase() === spaceHost.toLowerCase() &&
            !outUrl.includes('..')
          ) {
            return { kind: 'url', value: outUrl };
          }
        } catch {
          return { kind: 'error', message: 'Invalid output URL' };
        }
        return { kind: 'error', message: 'Rejected untrusted output host' };
      }
      const pathMatch = /"path"\s*:\s*"(\/[^"]+)"/.exec(payload);
      if (pathMatch) {
        const cleanPath = pathMatch[1].trim();
        if (!cleanPath.includes('..') && /^\/[A-Za-z0-9_./-]{1,300}$/.test(cleanPath)) {
          return { kind: 'url', value: `https://${spaceHost}/gradio_api/file=${cleanPath}` };
        }
        return { kind: 'error', message: 'Unsafe output path' };
      }
      return { kind: 'error', message: 'No image in response' };
    }
  }
  return { kind: 'error', message: 'Incomplete SSE stream' };
}

async function invokeHfSpaceFlatLay(space, inputBlob, hfToken) {
  if (!ALLOWED_HF_SPACE_HOSTS.has(space?.host)) {
    throw new Error('Untrusted Hugging Face Space');
  }
  const hostSet = new Set([space.host]);
  const validToken = hfToken && isValidHfToken(hfToken) ? hfToken.trim() : '';
  const authHeaders = validToken ? { Authorization: `Bearer ${validToken}` } : {};

  const fd = new FormData();
  fd.append('files', inputBlob, 'garment.jpg');
  const upRes = await safeFetch(
    `https://${space.host}/gradio_api/upload`,
    { method: 'POST', headers: authHeaders, body: fd },
    { allowedHosts: hostSet, timeoutMs: 30000, maxResponseBytes: 512 * 1024 }
  );
  if (!upRes.ok) {
    const e = new Error(`Upload HTTP ${upRes.status}`);
    e.serverResponded = true;
    throw e;
  }
  const upJson = await upRes.json();
  const remotePath = Array.isArray(upJson) && typeof upJson[0] === 'string' ? upJson[0].trim() : '';
  if (!remotePath || remotePath.includes('..') || !/^[A-Za-z0-9_./-]{1,300}$/.test(remotePath)) {
    const e = new Error('Invalid uploaded path');
    e.serverResponded = true;
    throw e;
  }

  const fileObj = {
    path: remotePath,
    url: `https://${space.host}/gradio_api/file=${remotePath}`,
    orig_name: 'garment.jpg',
    mime_type: 'image/jpeg',
    meta: { _type: 'gradio.FileData' }
  };
  const galleryArr = [{ image: fileObj, caption: null }];

  let dataArr;
  if (space.mode === 'UPLOAD_GALLERY_FLUX2_DEV') {
    dataArr = [FLAT_LAY_DIFFUSION_PROMPT, galleryArr, 0, true, 1024, 1024, 28, 4.0, false];
  } else if (space.mode === 'UPLOAD_GALLERY_FLUX2_KLEIN_BASE') {
    dataArr = [FLAT_LAY_DIFFUSION_PROMPT, galleryArr, 'Base (50 steps)', 0, true, 1024, 1024, 28, 4.0, false];
  } else if (space.mode === 'UPLOAD_GALLERY_QWEN_OFFICIAL') {
    dataArr = [galleryArr, FLAT_LAY_DIFFUSION_PROMPT, 0, true, 4.0, 32, 1024, 1024, false];
  } else if (space.mode === 'UPLOAD_GALLERY_FIRERED_1_1') {
    dataArr = [galleryArr, FLAT_LAY_DIFFUSION_PROMPT, 'None', 0, true, 4.0, 28, 1024, 1024, false, 1];
  } else {
    dataArr = [fileObj, FLAT_LAY_DIFFUSION_PROMPT, 0, true, 2.5, 28];
  }

  const callRes = await safeFetch(
    `https://${space.host}/gradio_api/call${space.endpoint}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders },
      body: JSON.stringify({ data: dataArr })
    },
    { allowedHosts: hostSet, timeoutMs: 30000, maxResponseBytes: 512 * 1024 }
  );
  if (!callRes.ok) {
    const e = new Error(`Call HTTP ${callRes.status}`);
    e.serverResponded = true;
    throw e;
  }
  const callJson = await callRes.json();
  const eventId = typeof callJson?.event_id === 'string' ? callJson.event_id.trim() : '';
  if (!/^[A-Za-z0-9_-]{4,128}$/.test(eventId)) {
    const e = new Error('Invalid event_id');
    e.serverResponded = true;
    throw e;
  }

  const sseRes = await safeFetch(
    `https://${space.host}/gradio_api/call${space.endpoint}/${eventId}`,
    { method: 'GET', headers: authHeaders },
    { allowedHosts: hostSet, timeoutMs: 130000, maxResponseBytes: 2 * 1024 * 1024 }
  );
  if (!sseRes.ok) {
    const e = new Error(`SSE HTTP ${sseRes.status}`);
    e.serverResponded = true;
    throw e;
  }
  const sseRaw = await sseRes.text();
  if (sseRaw.length > 2 * 1024 * 1024) {
    const e = new Error('SSE response too large');
    e.serverResponded = true;
    throw e;
  }
  const parsed = parseGradioSseOutput(sseRaw, space.host);

  if (parsed.kind === 'quota') throw new StudioQuotaError(parsed.message);
  if (parsed.kind === 'error') {
    const e = new Error(parsed.message);
    e.serverResponded = true;
    throw e;
  }
  if (parsed.kind === 'dataUrl') return parsed.value;

  const imgRes = await safeFetch(
    parsed.value,
    { method: 'GET', headers: authHeaders },
    { allowedHosts: hostSet, timeoutMs: 45000, maxResponseBytes: 15 * 1024 * 1024 }
  );
  if (!imgRes.ok) {
    const e = new Error(`Image fetch HTTP ${imgRes.status}`);
    e.serverResponded = true;
    throw e;
  }
  const outBlob = await imgRes.blob();
  if (outBlob.size <= 0 || outBlob.size > 15 * 1024 * 1024) {
    const e = new Error('Invalid output image size');
    e.serverResponded = true;
    throw e;
  }
  const outMime = String(outBlob.type || '').trim().toLowerCase();
  if (outMime && !ALLOWED_OUTPUT_IMAGE_MIMES.has(outMime)) {
    const e = new Error('Unsafe output image MIME');
    e.serverResponded = true;
    throw e;
  }
  const dataUrl = await blobToDataUrl(outBlob);
  if (!isSafeImageDataUrl(dataUrl)) {
    const e = new Error('Invalid output image encoding');
    e.serverResponded = true;
    throw e;
  }
  return dataUrl;
}

export async function generateAiEcommerceFlatLay(sourceDataUrl, keyOrOpts = {}) {
  const opts = typeof keyOrOpts === 'string'
    ? { geminiApiKey: keyOrOpts, hfToken: '' }
    : (keyOrOpts || {});
  const geminiApiKey = (opts.geminiApiKey || '').trim();
  const hfToken = (opts.hfToken || '').trim();
  let geminiErr = null;
  let quotaErr = null;
  let lastErr = null;

  if (geminiApiKey && isValidGeminiApiKey(geminiApiKey)) {
    try {
      const b64 = await resizeForApiBase64(sourceDataUrl, 1024, 0.92);
      const rawUrl = await callGeminiImageUnified(geminiApiKey, {
        prompt: FLAT_LAY_DIFFUSION_PROMPT,
        imageB64List: [b64],
        aspectRatio: '1:1'
      });
      return await polishCatalogDataUrl(rawUrl);
    } catch (err) {
      geminiErr = err;
    }
  }

  const naturalInput = await prepareNaturalInput(sourceDataUrl, 1024);
  let squareInput = null;
  let nullErrCount = 0;
  let anyServerResponded = false;
  for (const space of HF_SPACE_CHAIN) {
    try {
      const input = space.mode === 'UPLOAD_SINGLE_KONTEXT'
        ? (squareInput || (squareInput = await prepareSquareInput(sourceDataUrl, 1024, 0)))
        : naturalInput;
      const rawUrl = await invokeHfSpaceFlatLay(space, input.blob, hfToken);
      return await polishCatalogDataUrl(rawUrl);
    } catch (err) {
      if (err instanceof StudioQuotaError) {
        anyServerResponded = true;
        if (!quotaErr) quotaErr = err;
      } else {
        if (err?.serverResponded) anyServerResponded = true;
        if (err?.message === 'Remote studio error') {
          anyServerResponded = true;
          nullErrCount++;
        }
        lastErr = err;
      }
    }
  }

  if (geminiErr) {
    throw new GeminiApiError(
      sanitizeErrorMessage(geminiErr, 'Gemini API isteği başarısız oldu'),
      geminiErr?.status ?? null
    );
  }
  if (!quotaErr && nullErrCount > 0) {
    quotaErr = new StudioQuotaError(hfToken ? 'HF ZeroGPU quota reached' : 'ZeroGPU anonymous quota exceeded');
  }
  if (quotaErr) throw quotaErr;
  if (anyServerResponded) {
    throw new StudioBusyError('Remote AI servers busy');
  }
  throw lastErr || new Error('Catalog AI service unreachable');
}

export async function generateAiVirtualTryOn3D(bodyDataUrl, garmentEntries, apiKey) {
  const limited = (garmentEntries || []).slice(0, 4);
  const desc = limited.map((g, i) => `Reference garment ${i + 1}: ${clampText(g?.label || 'garment', 60)}`).join('; ');
  const prompt =
    `Virtual 3D try-on synthesis. The first attached image shows the person. ` +
    `The following images show the garments (${desc}). ` +
    `Dress the person in the first image wearing these exact garments seamlessly integrated onto their body in 3D with natural fabric drape, realistic folds, shadows, sleeves, collar fit, and body contour occlusion. ` +
    `Strictly preserve the person's exact face, facial identity, hair, skin tone, body proportions, pose, hands, camera perspective, ambient lighting, and background without altering them. ` +
    `Preserve the exact garment colors, prints, logos, graphics, and textures.`;

  const imageB64List = [await resizeForApiBase64(bodyDataUrl, 960)];
  for (const g of limited) {
    imageB64List.push(await resizeForApiBase64(g.dataUrl, 768));
  }
  return callGeminiImageUnified(apiKey, {
    prompt,
    imageB64List,
    aspectRatio: '3:4'
  });
}

async function resizeForApiBase64(dataUrl, maxDim, quality = 0.86) {
  const img = await loadHtmlImage(dataUrl);
  const scale = Math.min(1, maxDim / Math.max(img.width || 1, img.height || 1));
  const w = Math.max(1, Math.round(img.width * scale));
  const h = Math.max(1, Math.round(img.height * scale));
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  c.getContext('2d').drawImage(img, 0, 0, w, h);
  return c.toDataURL('image/jpeg', quality).split(',')[1];
}
