import { GARMENT_COLORS, STUDIO_ECOMMERCE_PROMPT, SUBCATEGORIES, localizedText } from './constants.js';
import { cleanGarmentTitleForName } from './recommendation-engine.js';

export function loadHtmlImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = (err) => reject(err);
    img.src = src;
  });
}

export async function fileToOrientedDataUrl(file, maxDim = 1440) {
  const objectUrl = URL.createObjectURL(file);
  try {
    const img = await loadHtmlImage(objectUrl);
    const scale = Math.min(1, maxDim / Math.max(img.width || 1, img.height || 1));
    const w = Math.max(1, Math.round(img.width * scale));
    const h = Math.max(1, Math.round(img.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, w, h);
    return canvas.toDataURL('image/jpeg', 0.90);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

function smoothstep(edge0, edge1, x) {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

/**
 * On-device E-Commerce Flat-Lay Studio processor:
 * Strips studio/real backgrounds via border-sampled flood fill + edge/center salience,
 * isolates the main garment component, trims narrow hanger hooks, equalizes fold shadows,
 * and centers symmetrically on a pure #FFFFFF 1:1 (1080x1080) studio frame.
 */
export async function toEcommerceFlatLayDataUrl(sourceDataUrl) {
  try {
    const img = await loadHtmlImage(sourceDataUrl);
    const maxWork = 720;
    const scale = Math.min(1, maxWork / Math.max(img.width, img.height));
    const w = Math.max(32, Math.round(img.width * scale));
    const h = Math.max(32, Math.round(img.height * scale));

    const workCanvas = document.createElement('canvas');
    workCanvas.width = w;
    workCanvas.height = h;
    const wCtx = workCanvas.getContext('2d');
    wCtx.drawImage(img, 0, 0, w, h);
    const imgData = wCtx.getImageData(0, 0, w, h);
    const px = imgData.data;

    // Sample perimeter ring color statistics
    let bgR = 0, bgG = 0, bgB = 0, bgCnt = 0;
    for (let x = 0; x < w; x++) {
      for (const y of [0, 1, h - 2, h - 1]) {
        const idx = (y * w + x) * 4;
        bgR += px[idx]; bgG += px[idx + 1]; bgB += px[idx + 2]; bgCnt++;
      }
    }
    for (let y = 0; y < h; y++) {
      for (const x of [0, 1, w - 2, w - 1]) {
        const idx = (y * w + x) * 4;
        bgR += px[idx]; bgG += px[idx + 1]; bgB += px[idx + 2]; bgCnt++;
      }
    }
    bgR /= Math.max(1, bgCnt);
    bgG /= Math.max(1, bgCnt);
    bgB /= Math.max(1, bgCnt);

    // Build initial alpha array via color difference + flood fill from outer border
    const alpha = new Uint8Array(w * h);
    alpha.fill(255);

    const visited = new Uint8Array(w * h);
    const queue = new Int32Array(w * h);
    let head = 0, tail = 0;

    const pushSeed = (x, y) => {
      const i = y * w + x;
      if (!visited[i]) {
        visited[i] = 1;
        queue[tail++] = i;
      }
    };
    for (let x = 0; x < w; x++) { pushSeed(x, 0); pushSeed(x, h - 1); }
    for (let y = 0; y < h; y++) { pushSeed(0, y); pushSeed(w - 1, y); }

    const isBrightBg = bgR > 210 && bgG > 210 && bgB > 210;
    const seedTolerance = isBrightBg ? 34 : 42;

    while (head < tail) {
      const idx = queue[head++];
      const p4 = idx * 4;
      const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
      const dr = r - bgR, dg = g - bgG, db = b - bgB;
      const distBg = Math.sqrt(dr * dr + dg * dg + db * db);

      // Check local neighbor similarity so uniform backgrounds flood cleanly without piercing sharp garment borders
      const x = idx % w;
      const y = (idx / w) | 0;
      let grad = 0;
      if (x + 1 < w && y + 1 < h) {
        const rx = px[(idx + 1) * 4] - r;
        const bx = px[(idx + w) * 4] - r;
        grad = Math.abs(rx) + Math.abs(bx);
      }

      const effTol = seedTolerance + (grad < 18 ? 14 : -10);
      if (distBg <= effTol) {
        alpha[idx] = 0;
        if (x > 0 && !visited[idx - 1]) { visited[idx - 1] = 1; queue[tail++] = idx - 1; }
        if (x + 1 < w && !visited[idx + 1]) { visited[idx + 1] = 1; queue[tail++] = idx + 1; }
        if (y > 0 && !visited[idx - w]) { visited[idx - w] = 1; queue[tail++] = idx - w; }
        if (y + 1 < h && !visited[idx + w]) { visited[idx + w] = 1; queue[tail++] = idx + w; }
      } else if (distBg <= effTol + 14) {
        alpha[idx] = Math.round(smoothstep(effTol, effTol + 14, distBg) * 255);
      }
    }

    // Keep largest connected foreground component
    keepLargestComponent(alpha, w, h);

    // Measure bbox & strip narrow top hanger hook
    let bbox = computeForegroundBbox(alpha, w, h);
    if (!bbox) {
      return centerBitmapOnStudioWhite(workCanvas, w, h);
    }
    stripTopHangerHook(alpha, w, bbox);
    bbox = computeForegroundBbox(alpha, w, h);
    if (!bbox || (bbox.maxX - bbox.minX < 16) || (bbox.maxY - bbox.minY < 16)) {
      return centerBitmapOnStudioWhite(workCanvas, w, h);
    }

    // Apply subtle studio flat-lay shadow lift preserving prints & deep blacks
    applyStudioLightingEqualization(px, alpha, w, bbox);

    // Matte cleanly onto pure #FFFFFF
    for (let i = 0; i < w * h; i++) {
      const p4 = i * 4;
      const a = alpha[i] / 255;
      px[p4] = Math.round(px[p4] * a + 255 * (1 - a));
      px[p4 + 1] = Math.round(px[p4 + 1] * a + 255 * (1 - a));
      px[p4 + 2] = Math.round(px[p4 + 2] * a + 255 * (1 - a));
      px[p4 + 3] = 255;
    }
    wCtx.putImageData(imgData, 0, 0);

    const cropW = bbox.maxX - bbox.minX + 1;
    const cropH = bbox.maxY - bbox.minY + 1;
    const cropCanvas = document.createElement('canvas');
    cropCanvas.width = cropW;
    cropCanvas.height = cropH;
    cropCanvas.getContext('2d').drawImage(workCanvas, bbox.minX, bbox.minY, cropW, cropH, 0, 0, cropW, cropH);

    return centerBitmapOnStudioWhite(cropCanvas, cropW, cropH);
  } catch {
    return sourceDataUrl;
  }
}

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
 * Exact 1:1 browser port of PersonalColorAnalyzer.kt
 */
export async function analyzeFaceSkinTone(faceDataUrl, lang = 'TR') {
  if (!faceDataUrl) return null;
  try {
    const img = await loadHtmlImage(faceDataUrl);
    const w = Math.min(320, img.width || 320);
    const h = Math.max(64, Math.round((w * (img.height || 320)) / Math.max(1, img.width || 320)));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, w, h);
    const px = ctx.getImageData(0, 0, w, h).data;

    let sumR = 0, sumG = 0, sumB = 0, skinCount = 0;
    const minX = Math.floor(w * 0.24), maxX = Math.floor(w * 0.76);
    const minY = Math.floor(h * 0.08), maxY = Math.floor(h * 0.58);

    const isLikelySkin = (r, g, b) => {
      if (r <= 80 || g <= 38 || b <= 22 || r <= g || r <= b) return false;
      const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
      const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
      return cb >= 77 && cb <= 128 && cr >= 133 && cr <= 178;
    };

    for (let y = minY; y < maxY; y++) {
      for (let x = minX; x < maxX; x++) {
        const p4 = (y * w + x) * 4;
        const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
        if (isLikelySkin(r, g, b)) {
          sumR += r; sumG += g; sumB += b; skinCount++;
        }
      }
    }

    if (skinCount < 25) {
      for (let y = minY; y < maxY; y += 2) {
        for (let x = minX; x < maxX; x += 2) {
          const p4 = (y * w + x) * 4;
          const r = px[p4], g = px[p4 + 1], b = px[p4 + 2];
          const lum = 0.299 * r + 0.587 * g + 0.114 * b;
          if (lum >= 45 && lum <= 235) {
            sumR += r; sumG += g; sumB += b; skinCount++;
          }
        }
      }
    }
    if (skinCount === 0) return null;

    const avgR = Math.round(sumR / skinCount);
    const avgG = Math.round(sumG / skinCount);
    const avgB = Math.round(sumB / skinCount);
    const avgSkinHex = `#${avgR.toString(16).padStart(2, '0')}${avgG.toString(16).padStart(2, '0')}${avgB.toString(16).padStart(2, '0')}`.toUpperCase();

    const lum = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255;
    const depth = lum >= 0.64 ? 'FAIR' : (lum >= 0.42 ? 'MEDIUM' : 'DEEP');
    const warmthIndex = (avgR - avgB) - (avgG - avgB) * 0.35;
    const undertone = warmthIndex >= 28 ? 'WARM' : (warmthIndex <= 14 ? 'COOL' : 'NEUTRAL');

    const palettes = {
      WARM: {
        FAIR: ['CHAMPAGNE', 'ECRU', 'SAND', 'CAMEL', 'CORAL', 'OLIVE', 'SAGE', 'TURQUOISE', 'TERRACOTTA', 'MINT', 'DENIM', 'CHOCOLATE'],
        MEDIUM: ['OLIVE', 'TERRACOTTA', 'RUST', 'CAMEL', 'SAND', 'OCHRE', 'FOREST', 'CHOCOLATE', 'ECRU', 'TEAL', 'WINE', 'NAVY', 'ROSEWOOD'],
        DEEP: ['OCHRE', 'RUST', 'TERRACOTTA', 'MUSTARD', 'EMERALD', 'FOREST', 'CHAMPAGNE', 'ECRU', 'CORAL', 'CHOCOLATE', 'WINE', 'PETROL', 'CAMEL']
      },
      COOL: {
        FAIR: ['ICE_BLUE', 'LAVENDER', 'BLUSH', 'ROSEWOOD', 'SLATE_BLUE', 'NAVY', 'PLUM', 'WINE', 'EMERALD', 'WHITE', 'CHARCOAL', 'MINT', 'COBALT'],
        MEDIUM: ['NAVY', 'COBALT', 'PETROL', 'BURGUNDY', 'WINE', 'PLUM', 'EMERALD', 'SLATE_BLUE', 'ICE_BLUE', 'ROSEWOOD', 'WHITE', 'CHARCOAL', 'LAVENDER'],
        DEEP: ['COBALT', 'EMERALD', 'WINE', 'PLUM', 'WHITE', 'BLACK', 'ICE_BLUE', 'LAVENDER', 'PETROL', 'TEAL', 'ROSEWOOD', 'BURGUNDY', 'NAVY']
      },
      NEUTRAL: {
        FAIR: ['ECRU', 'CHAMPAGNE', 'SAGE', 'ICE_BLUE', 'ROSEWOOD', 'SLATE_BLUE', 'DENIM', 'TEAL', 'NAVY', 'CHARCOAL', 'LAVENDER', 'WINE'],
        MEDIUM: ['ECRU', 'SAND', 'SAGE', 'FOREST', 'PETROL', 'TEAL', 'DENIM', 'SLATE_BLUE', 'CHOCOLATE', 'WINE', 'ROSEWOOD', 'CHARCOAL', 'NAVY'],
        DEEP: ['ECRU', 'SAND', 'SAGE', 'FOREST', 'PETROL', 'TEAL', 'DENIM', 'SLATE_BLUE', 'CHOCOLATE', 'WINE', 'ROSEWOOD', 'CHARCOAL', 'NAVY']
      }
    };

    const undertoneLabel = undertone === 'WARM'
      ? localizedText(lang, 'Sıcak Alt Ton', 'Warm Undertone', 'Warmer Unterton', 'Sous-ton chaud', 'Subtono cálido', '暖色调肤色')
      : undertone === 'COOL'
        ? localizedText(lang, 'Soğuk Alt Ton', 'Cool Undertone', 'Kühler Unterton', 'Sous-ton froid', 'Subtono frío', '冷色调肤色')
        : localizedText(lang, 'Nötr Alt Ton', 'Neutral Undertone', 'Neutraler Unterton', 'Sous-ton neutre', 'Subtono neutro', '自然中性肤色');

    const seasonTitle = undertone === 'WARM'
      ? (depth === 'FAIR'
        ? localizedText(lang, 'İlkbahar · Sıcak & Aydınlık', 'Spring · Warm & Clear', 'Frühling · Warm & Klar', 'Printemps · Chaud', 'Primavera · Cálida', '春季 · 暖调明净')
        : localizedText(lang, 'Sonbahar · Sıcak Toprak Paleti', 'Autumn · Warm Earth Palette', 'Herbst · Warme Erdtöne', 'Automne · Tons Chauds', 'Otoño · Tonos Tierra', '秋季 · 暖大地色盘'))
      : undertone === 'COOL'
        ? (depth === 'DEEP'
          ? localizedText(lang, 'Kış · Soğuk & Yüksek Kontrast', 'Winter · Cool High Contrast', 'Winter · Kühl & Kontrastreich', 'Hiver · Froid & Contrasté', 'Invierno · Frío Intenso', '冬季 · 冷调高对比')
          : localizedText(lang, 'Yaz · Soğuk & Dengeli Tonlar', 'Summer · Cool & Soft Tones', 'Sommer · Kühle Töne', 'Été · Tons Froids', 'Verano · Tonos Fríos', '夏季 · 冷调柔色'))
        : localizedText(lang, 'Dengeli Nötr · Yumuşak Palet', 'Balanced Neutral · Versatile Palette', 'Neutral · Vielseitige Palette', 'Neutre · Palette Polyvalente', 'Neutro · Paleta Versátil', '自然中性 · 百搭色盘');

    const summaryText = undertone === 'WARM'
      ? localizedText(lang, 'Ten alt tonun sıcak görünüyor; haki, kiremit, ekru, camel ve zeytin tonları yüzünü daha canlı gösterir.', 'Your skin leans warm; olive, terracotta, ecru, camel, and earth tones bring out natural radiance.', 'Dein Hautunterton ist warm; Oliv, Terrakotta, Ecru und Camel lassen deinen Teint strahlen.', "Votre sous-ton est chaud ; l'olive, le terracotta, l'écru et le camel subliment votre teint.", 'Tu subtono es cálido; el oliva, terracota, crudo y camel iluminan tu rostro.', '你的肤色偏暖调；橄榄绿、赤陶橘、米白与驼色等大地色系能让气色更加明亮。')
      : undertone === 'COOL'
        ? localizedText(lang, 'Ten alt tonun soğuk görünüyor; lacivert, bordo, net beyaz, saks mavi ve gri tonları cildini daha parlak gösterir.', 'Your skin leans cool; navy, burgundy, crisp white, cobalt, and soft blush complement your complexion.', 'Dein Hautunterton ist kühl; Marineblau, Bordeaux, Weiß und Kobalt stehen dir besonders gut.', 'Votre sous-ton est froid ; le marine, le bordeaux, le blanc pur et le cobalt vous vont à ravir.', 'Tu subtono es frío; el marino, burdeos, blanco puro y cobalto favorecen tu piel.', '你的肤色偏冷调；藏青、酒红、纯白、钴蓝和柔粉色系能衬托肌肤更显透亮。')
        : localizedText(lang, 'Alt tonun nötr ve dengeli; adaçayı, denim mavi, ekru, antrasit ve bordo gibi çoğu tonu rahatlıkla taşırsın.', 'Your undertone is balanced neutral; sage, denim blue, ecru, charcoal, and burgundy look effortless on you.', 'Dein Unterton ist neutral; Salbei, Denim, Ecru und Anthrazit passen perfekt zu dir.', "Votre sous-ton est neutre ; sauge, bleu denim, écru et anthracite s'accordent facilement.", 'Tu subtono es neutro; salvia, azul vaquero, crudo y antracita te sientan de maravilla.', '你的肤色属于自然中性色调；鼠尾草绿、牛仔蓝、米白、炭灰与酒红都能轻松驾驭。');

    return {
      avgSkinHex,
      undertone,
      undertoneLabel,
      depth,
      seasonTitle,
      summaryText,
      flatteringColors: palettes[undertone][depth],
      avoidColors: undertone === 'WARM' ? ['LIGHT_GRAY', 'PINK'] : undertone === 'COOL' ? ['ORANGE', 'YELLOW'] : ['YELLOW']
    };
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

/**
 * Calls optional Gemini Nano Banana 2.1 Interactions API with user-supplied API key.
 */
async function callGeminiImageInteraction(apiKey, payload) {
  const res = await fetch('https://generativelanguage.googleapis.com/v1beta/interactions', {
    method: 'POST',
    headers: {
      'x-goog-api-key': apiKey,
      'Content-Type': 'application/json',
      'Api-Revision': '2026-05-20'
    },
    body: JSON.stringify(payload)
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data?.error?.message || `HTTP ${res.status}`);
  }
  const direct = data?.output_image?.data;
  if (direct) return `data:${data.output_image.mime_type || 'image/jpeg'};base64,${direct}`;
  for (const step of (data?.steps || [])) {
    for (const c of (step?.content || [])) {
      if (c?.type === 'image' && c?.data) {
        return `data:${c.mime_type || 'image/jpeg'};base64,${c.data}`;
      }
    }
  }
  throw new Error('Model görsel döndürmedi');
}

export async function generateAiEcommerceFlatLay(sourceDataUrl, apiKey) {
  const b64 = await resizeForApiBase64(sourceDataUrl, 1024);
  return callGeminiImageInteraction(apiKey, {
    model: 'gemini-nano-banana-2.1',
    input: [
      { type: 'text', text: STUDIO_ECOMMERCE_PROMPT },
      { type: 'image', mime_type: 'image/jpeg', data: b64 }
    ],
    response_format: { type: 'image', mime_type: 'image/jpeg', aspect_ratio: '1:1' }
  });
}

export async function generateAiVirtualTryOn3D(bodyDataUrl, garmentEntries, apiKey) {
  const limited = garmentEntries.slice(0, 4);
  const desc = limited.map((g, i) => `Reference garment ${i + 1}: ${g.label}`).join('; ');
  const prompt =
    `Virtual 3D try-on synthesis. The first attached image shows the person. ` +
    `The following images show the garments (${desc}). ` +
    `Dress the person in the first image wearing these exact garments seamlessly integrated onto their body in 3D with natural fabric drape, realistic folds, shadows, sleeves, collar fit, and body contour occlusion. ` +
    `Strictly preserve the person's exact face, facial identity, hair, skin tone, body proportions, pose, hands, camera perspective, ambient lighting, and background without altering them. ` +
    `Preserve the exact garment colors, prints, logos, graphics, and textures.`;

  const input = [
    { type: 'text', text: prompt },
    { type: 'image', mime_type: 'image/jpeg', data: await resizeForApiBase64(bodyDataUrl, 960) }
  ];
  for (const g of limited) {
    input.push({
      type: 'image',
      mime_type: 'image/jpeg',
      data: await resizeForApiBase64(g.dataUrl, 768)
    });
  }
  return callGeminiImageInteraction(apiKey, {
    model: 'gemini-nano-banana-2.1',
    input,
    response_format: { type: 'image', mime_type: 'image/jpeg', aspect_ratio: '3:4' }
  });
}

async function resizeForApiBase64(dataUrl, maxDim) {
  const img = await loadHtmlImage(dataUrl);
  const scale = Math.min(1, maxDim / Math.max(img.width || 1, img.height || 1));
  const w = Math.max(1, Math.round(img.width * scale));
  const h = Math.max(1, Math.round(img.height * scale));
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  c.getContext('2d').drawImage(img, 0, 0, w, h);
  return c.toDataURL('image/jpeg', 0.86).split(',')[1];
}
