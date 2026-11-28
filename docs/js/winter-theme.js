const SNOW_CANVAS_ID = 'winterSnowCanvas';
const TOP_GARLAND_ID = 'winterTopGarland';
const DOCK_ORNAMENTS_ID = 'winterDockOrnaments';

const DOCK_BINS = 56;
const DEPOSIT_KERNEL = [0.14, 0.38, 0.72, 1.0, 0.72, 0.38, 0.14];
const SLOPE_THRESHOLD = 1.45;
const DOCK_CORNER_INSET = 18;
const MAX_DOCK_SNOW_PX = 12.5;

let winterEnabled = false;
let rafId = null;
let flakes = [];
let targetDockHeights = new Float32Array(DOCK_BINS);
let displayDockHeights = new Float32Array(DOCK_BINS);
let shedClumps = [];
let snowInitialized = false;
let lastTickMs = 0;
let elapsedSec = 0;
let listenersBound = false;
let shakeTimeoutId = null;
let lastDockSpan = null; // { left, right, top, width }

function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function isDarkActiveTheme() {
  const theme = document.body?.dataset?.theme || 'MODERN';
  return theme === 'MODERN_DARK' || theme === 'STREET';
}

function dockMaxCapAt(u) {
  const clamped = Math.max(0, Math.min(1, u));
  const envelope = Math.pow(Math.sin(clamped * Math.PI), 0.42);
  return MAX_DOCK_SNOW_PX * envelope;
}

function initDockHeights(initialScale = 0) {
  targetDockHeights = new Float32Array(DOCK_BINS);
  displayDockHeights = new Float32Array(DOCK_BINS);
  if (initialScale <= 0) {
    snowInitialized = true;
    return;
  }
  for (let i = 0; i < DOCK_BINS; i++) {
    const u = i / (DOCK_BINS - 1);
    const cap = dockMaxCapAt(u);
    const wave = 0.72 + 0.28 * Math.sin(u * Math.PI * 5.2 + 0.6);
    const val = Math.min(cap, cap * initialScale * wave);
    targetDockHeights[i] = val;
    displayDockHeights[i] = val;
  }
  targetDockHeights[0] = 0;
  targetDockHeights[DOCK_BINS - 1] = 0;
  displayDockHeights[0] = 0;
  displayDockHeights[DOCK_BINS - 1] = 0;
  snowInitialized = true;
}

function sampleDockSnow(u) {
  const clamped = Math.max(0, Math.min(1, u));
  const f = clamped * (DOCK_BINS - 1);
  const i0 = Math.floor(f);
  const i1 = Math.min(DOCK_BINS - 1, i0 + 1);
  const frac = f - i0;
  return displayDockHeights[i0] * (1 - frac) + displayDockHeights[i1] * frac;
}

function depositOnDock(u, radius) {
  const center = Math.max(0, Math.min(DOCK_BINS - 1, Math.round(u * (DOCK_BINS - 1))));
  const amount = 0.52 + radius * 0.24;
  for (let k = -3; k <= 3; k++) {
    const idx = center + k;
    if (idx <= 0 || idx >= DOCK_BINS - 1) continue;
    const cap = dockMaxCapAt(idx / (DOCK_BINS - 1));
    if (cap <= 0.1) continue;
    const cur = targetDockHeights[idx];
    const headroom = Math.max(0.10, (cap - cur) / cap);
    targetDockHeights[idx] = Math.min(cap, cur + amount * DEPOSIT_KERNEL[k + 3] * headroom);
  }
  relaxDockAvalanche();
}

function relaxDockAvalanche() {
  for (let i = 1; i < DOCK_BINS - 1; i++) {
    const h = targetDockHeights[i];
    const leftDiff = h - targetDockHeights[i - 1];
    if (leftDiff > SLOPE_THRESHOLD && i > 1) {
      const flow = (leftDiff - SLOPE_THRESHOLD) * 0.30;
      targetDockHeights[i] -= flow;
      targetDockHeights[i - 1] += flow;
    }
    const rightDiff = h - targetDockHeights[i + 1];
    if (rightDiff > SLOPE_THRESHOLD && i < DOCK_BINS - 2) {
      const flow = (rightDiff - SLOPE_THRESHOLD) * 0.30;
      targetDockHeights[i] -= flow;
      targetDockHeights[i + 1] += flow;
    }
  }
  for (let i = 2; i < DOCK_BINS - 2; i++) {
    const avg = (targetDockHeights[i - 1] + targetDockHeights[i + 1]) * 0.5;
    const next = targetDockHeights[i] + (avg - targetDockHeights[i]) * 0.12;
    const cap = dockMaxCapAt(i / (DOCK_BINS - 1));
    targetDockHeights[i] = Math.max(0, Math.min(cap, next));
  }
  targetDockHeights[0] = 0;
  targetDockHeights[DOCK_BINS - 1] = 0;
}

function stepDisplayDockHeights(dt) {
  if (dt <= 0) return;
  const alpha = 1 - Math.exp(-dt * 2.6);
  for (let i = 1; i < DOCK_BINS - 1; i++) {
    const diff = targetDockHeights[i] - displayDockHeights[i];
    if (Math.abs(diff) < 0.002) {
      displayDockHeights[i] = targetDockHeights[i];
    } else {
      displayDockHeights[i] += diff * alpha;
    }
  }
  displayDockHeights[0] = 0;
  displayDockHeights[DOCK_BINS - 1] = 0;
}

function getDockSnowSpan() {
  const dock = document.getElementById('bottomDock');
  if (!dock) return null;
  const r = dock.getBoundingClientRect();
  if (r.width <= DOCK_CORNER_INSET * 2 + 20 || r.height <= 0) return null;
  const left = r.left + DOCK_CORNER_INSET;
  const right = r.right - DOCK_CORNER_INSET;
  const top = r.top + 1.5; // flush on upper dock rim
  lastDockSpan = { left, right, top, width: right - left };
  return lastDockSpan;
}

function createFlakePool(width, height) {
  const count = width <= 520 ? 44 : 54;
  const arr = [];
  for (let i = 0; i < count; i++) {
    const layer = i < Math.floor(count * 0.36)
      ? 0
      : i < Math.floor(count * 0.74)
        ? 1
        : 2;
    const speedPx = layer === 0
      ? 24 + Math.random() * 14
      : layer === 1
        ? 40 + Math.random() * 18
        : 58 + Math.random() * 22;
    const radius = layer === 0
      ? 1.3 + Math.random() * 0.8
      : layer === 1
        ? 2.1 + Math.random() * 1.0
        : 2.9 + Math.random() * 1.2;
    const amp = layer === 0 ? 6 + Math.random() * 6 : layer === 1 ? 10 + Math.random() * 8 : 14 + Math.random() * 9;
    const alpha = layer === 0 ? 0.28 + Math.random() * 0.16 : layer === 1 ? 0.44 + Math.random() * 0.18 : 0.58 + Math.random() * 0.18;
    arr.push({
      baseXNorm: Math.random(),
      y: Math.random() * height,
      layer,
      speedPx,
      radius,
      amp,
      freq: 0.55 + Math.random() * 0.75,
      phase: Math.random() * Math.PI * 2,
      alpha
    });
  }
  return arr;
}

function sizeCanvasToViewport(canvas) {
  const w = Math.max(320, window.innerWidth || 390);
  const h = Math.max(480, window.innerHeight || 844);
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
  }
  return { w, h, dpr };
}

function traceDockSnowCurve(ctx, span, offsetY, lipScale = 1) {
  const stepX = span.width / (DOCK_BINS - 1);
  const yAt = (idx) => span.top - Math.max(0, displayDockHeights[idx] + offsetY);
  const lipY = span.top + 1.2 * lipScale;
  ctx.beginPath();
  ctx.moveTo(span.left, lipY);
  for (let i = 0; i < DOCK_BINS - 1; i++) {
    const x0 = span.left + i * stepX;
    const y0 = yAt(i);
    const x1 = span.left + (i + 1) * stepX;
    const y1 = yAt(i + 1);
    ctx.quadraticCurveTo(x0, y0, (x0 + x1) * 0.5, (y0 + y1) * 0.5);
  }
  ctx.lineTo(span.right, lipY);
}

function drawDockSnowBlanket(ctx, span, dark) {
  let maxH = 0;
  for (let i = 0; i < DOCK_BINS; i++) {
    if (displayDockHeights[i] > maxH) maxH = displayDockHeights[i];
  }
  if (maxH < 0.04) return;

  // Smooth frost-in alpha (0 -> 1) so snow emerges organically from zero without any sudden jump
  const rawT = Math.max(0, Math.min(1, (maxH - 0.04) / 1.45));
  const fadeAlpha = rawT * rawT * (3 - 2 * rawT);

  ctx.save();
  ctx.globalAlpha = fadeAlpha;

  // 1) Soft icy shadow rim right beneath/behind crest for depth
  traceDockSnowCurve(ctx, span, 0.75 * fadeAlpha, fadeAlpha);
  ctx.lineTo(span.right, span.top + 1.6 * fadeAlpha);
  ctx.lineTo(span.left, span.top + 1.6 * fadeAlpha);
  ctx.closePath();
  ctx.fillStyle = dark ? 'rgba(170, 200, 234, 0.28)' : 'rgba(136, 172, 210, 0.34)';
  ctx.fill();

  // 2) Main smooth organic snow cushion hugging dock upper rim
  traceDockSnowCurve(ctx, span, 0, fadeAlpha);
  ctx.lineTo(span.right, span.top + 1.5 * fadeAlpha);
  ctx.quadraticCurveTo(
    span.left + span.width * 0.5,
    span.top + 2.4 * fadeAlpha,
    span.left,
    span.top + 1.5 * fadeAlpha
  );
  ctx.closePath();
  const grad = ctx.createLinearGradient(0, span.top - maxH - 1, 0, span.top + 2.5);
  if (dark) {
    grad.addColorStop(0, 'rgba(252, 254, 255, 0.96)');
    grad.addColorStop(0.58, 'rgba(232, 242, 254, 0.92)');
    grad.addColorStop(1, 'rgba(196, 216, 240, 0.84)');
  } else {
    grad.addColorStop(0, 'rgba(255, 255, 255, 0.99)');
    grad.addColorStop(0.62, 'rgba(238, 246, 255, 0.96)');
    grad.addColorStop(1, 'rgba(202, 222, 244, 0.92)');
  }
  ctx.fillStyle = grad;
  ctx.fill();

  // 3) Delicate luminous frost highlight crest
  traceDockSnowCurve(ctx, span, 0, fadeAlpha);
  ctx.strokeStyle = dark ? 'rgba(255, 255, 255, 0.72)' : 'rgba(154, 188, 224, 0.65)';
  ctx.lineWidth = 0.95;
  ctx.stroke();

  // 4) Subtle twinkling ice glints along the snow peak
  if (maxH > 2.4) {
    for (let g = 0; g < 5; g++) {
      const idx = Math.floor(((g + 0.6) / 5.2) * (DOCK_BINS - 2)) + 1;
      const gh = displayDockHeights[idx];
      if (gh < 2.0) continue;
      const gx = span.left + (idx / (DOCK_BINS - 1)) * span.width;
      const gy = span.top - gh + 1.8;
      const pulse = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(elapsedSec * 2.6 + g * 1.9));
      ctx.fillStyle = dark ? `rgba(255,255,255,${pulse.toFixed(2)})` : `rgba(180,214,248,${pulse.toFixed(2)})`;
      ctx.beginPath();
      ctx.arc(gx, gy, 1.05, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  ctx.restore();
}

function updateAndDrawShedClumps(ctx, dt, h, dark) {
  if (shedClumps.length === 0) return;
  const next = [];
  const baseRgb = dark ? '242,248,255' : '195,218,242';
  const coreRgb = dark ? '255,255,255' : '255,255,255';
  for (let i = 0; i < shedClumps.length; i++) {
    const c = shedClumps[i];
    if (dt > 0) {
      c.vy += 360 * dt;
      c.x += c.vx * dt;
      c.y += c.vy * dt;
      c.vx *= 0.985;
      c.life -= dt * 1.05;
    }
    if (c.life <= 0 || c.y > h + 16) continue;
    const a = Math.max(0, Math.min(0.95, c.life * c.alpha));
    ctx.fillStyle = `rgba(${baseRgb},${a.toFixed(3)})`;
    ctx.beginPath();
    ctx.arc(c.x, c.y, c.r, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = `rgba(${coreRgb},${(a * 0.7).toFixed(3)})`;
    ctx.beginPath();
    ctx.arc(c.x - c.r * 0.22, c.y - c.r * 0.22, c.r * 0.48, 0, Math.PI * 2);
    ctx.fill();
    next.push(c);
  }
  shedClumps = next;
}

function drawSnowFrame(dt, animate) {
  const canvas = document.getElementById(SNOW_CANVAS_ID);
  if (!canvas || !winterEnabled) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const { w, h, dpr } = sizeCanvasToViewport(canvas);
  if (flakes.length === 0) {
    flakes = createFlakePool(w, h);
  }
  if (!snowInitialized) {
    initDockHeights(animate ? 0 : 0.65);
  }
  const span = getDockSnowSpan();

  if (animate && dt > 0) {
    stepDisplayDockHeights(dt);
  }

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  const dark = isDarkActiveTheme();
  const farRgb = dark ? '214,230,250' : '147,179,214';
  const midRgb = dark ? '237,245,255' : '123,159,201';
  const nearRgb = dark ? '255,255,255' : '105,145,191';
  const coreRgb = dark ? '255,255,255' : '243,248,255';

  for (let i = 0; i < flakes.length; i++) {
    const f = flakes[i];
    const prevY = f.y;
    if (animate && dt > 0) {
      f.y += f.speedPx * dt;
    }
    const sway = Math.sin(elapsedSec * f.freq + f.phase) * f.amp;
    const px = ((f.baseXNorm * w + sway) % w + w) % w;

    // Collide & accumulate on top of the Floating Dock
    if (animate && span && px >= span.left && px <= span.right) {
      const u = (px - span.left) / span.width;
      const surfaceY = span.top - sampleDockSnow(u);
      if (prevY <= span.top + 8 && f.y + f.radius >= surfaceY) {
        depositOnDock(u, f.radius);
        f.y = -f.radius - (4 + Math.random() * 24);
        f.baseXNorm = Math.random();
        continue;
      }
    }

    if (animate && f.y - f.radius > h + 10) {
      f.y = -f.radius - (4 + Math.random() * 24);
      f.baseXNorm = Math.random();
      continue;
    }

    const py = f.y;
    if (py < -8 || py > h) continue;
    const rgb = f.layer === 0 ? farRgb : f.layer === 1 ? midRgb : nearRgb;

    ctx.fillStyle = `rgba(${rgb},${f.alpha.toFixed(3)})`;
    ctx.beginPath();
    ctx.arc(px, py, f.radius, 0, Math.PI * 2);
    ctx.fill();

    if (f.layer === 2) {
      const coreAlpha = Math.min(0.75, f.alpha * 0.72);
      ctx.fillStyle = `rgba(${coreRgb},${coreAlpha.toFixed(3)})`;
      ctx.beginPath();
      ctx.arc(px, py, f.radius * 0.45, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  if (span) {
    drawDockSnowBlanket(ctx, span, dark);
  }
  updateAndDrawShedClumps(ctx, animate ? dt : 0, h, dark);
}

function buildTopGarlandSvg() {
  const bulbs = [];
  const count = 11;
  for (let i = 0; i < count; i++) {
    const x = Math.round(((i + 0.5) / count) * 360);
    const u = ((i + 0.5) / count) * 4 % 1;
    const drop = Math.round(4 * u * (1 - u) * 7.5 + 8);
    const toneClass = i % 3 === 0 ? 'winter-bulb-gold' : i % 3 === 1 ? 'winter-bulb-ice' : 'winter-bulb-red';
    const delayClass = `winter-twinkle-${i % 3}`;
    bulbs.push(
      `<circle class="winter-bulb-glow ${toneClass} ${delayClass}" cx="${x}" cy="${drop}" r="4.5"/>` +
      `<circle class="winter-bulb-core ${toneClass} ${delayClass}" cx="${x}" cy="${drop}" r="2.1"/>`
    );
  }
  return `
    <svg class="winter-garland-svg" viewBox="0 0 360 22" preserveAspectRatio="none" aria-hidden="true">
      <path class="winter-garland-wire" d="M0,4 Q45,14 90,4 Q135,14 180,4 Q225,14 270,4 Q315,14 360,4" fill="none" stroke-width="1.2"/>
      ${bulbs.join('')}
    </svg>
  `;
}

function buildDockMiniOrnamentsSvg() {
  // 4 warm, softly rounded miniature holiday ornaments resting gently on the dock top edge
  return `
    <svg class="winter-dock-ornaments-svg" viewBox="0 0 380 22" preserveAspectRatio="xMidYMax meet" aria-hidden="true">
      <!-- 1. Left (11%): Snow-dusted mini pine tree with tiny star & micro ornaments -->
      <g class="winter-orn-item" transform="translate(34, 1)">
        <rect class="winter-orn-trunk" x="8.6" y="16.8" width="2.8" height="3.2" rx="1"/>
        <path class="winter-orn-pine" d="M10,3.2 C8.5,6.8 6.2,9.2 4.6,10.6 C4.1,11.1 4.5,11.8 5.2,11.8 L7.0,11.8 C5.4,13.6 3.6,15.2 2.4,16.4 C1.9,16.9 2.3,17.6 3.0,17.6 L17.0,17.6 C17.7,17.6 18.1,16.9 17.6,16.4 C16.4,15.2 14.6,13.6 13.0,11.8 L14.8,11.8 C15.5,11.8 15.9,11.1 15.4,10.6 C13.8,9.2 11.5,6.8 10,3.2 Z"/>
        <path class="winter-orn-snowtip" d="M10,3.5 C9.2,5.4 8.0,6.8 6.8,7.9 Q10,6.8 13.2,7.9 C12.0,6.8 10.8,5.4 10,3.5 Z"/>
        <circle class="winter-orn-star" cx="10" cy="2.2" r="1.75"/>
        <circle class="winter-orn-red" cx="8.2" cy="12.8" r="1.05"/>
        <circle class="winter-orn-gold" cx="12.2" cy="14.5" r="1.05"/>
      </g>

      <!-- 2. Mid-left (34%): Playful miniature Santa cap & soft mini snowball -->
      <g class="winter-orn-item" transform="translate(124, 7)">
        <g transform="rotate(-12 8 10)">
          <path d="M2.2,10.2 C3.8,4.0 8.5,1.8 13.8,4.2 C11.8,6.2 11.0,8.2 11.8,10.2 Z" fill="#D9383A"/>
          <rect x="1.2" y="9.4" width="11.4" height="3.1" rx="1.55" fill="#F8FAFD" stroke="#D5DFEC" stroke-width="0.45"/>
          <circle cx="14.3" cy="4.6" r="1.7" fill="#FFFFFF" stroke="#DCE5F0" stroke-width="0.4"/>
        </g>
        <circle cx="17.8" cy="11.2" r="2.1" fill="#F2F7FE" stroke="#D2E0F0" stroke-width="0.45"/>
      </g>

      <!-- 3. Mid-right (74%): Cute mini reindeer with delicate antlers & tiny red Rudolph nose -->
      <g class="winter-orn-item" transform="translate(274, 2)">
        <path class="winter-orn-deer" d="M3.4,18.2 C3.4,15.6 3.7,13.6 4.4,12.4 C5.2,11.6 9.8,11.4 11.2,11.2 C11.8,9.4 12.4,7.4 13.2,6.4 C14.0,5.6 15.8,5.6 16.5,6.5 C16.9,7.1 16.4,8.0 15.4,8.3 L13.8,8.7 C13.5,10.2 13.3,11.8 13.4,13.5 L13.8,18.2 C13.8,18.8 12.4,18.8 12.2,18.2 L11.5,14.6 L6.4,14.6 L5.6,18.2 C5.4,18.8 3.4,18.8 3.4,18.2 Z"/>
        <path class="winter-orn-antler" d="M13.2,6.4 C12.4,4.8 11.6,3.4 10.8,2.2 M12.2,4.4 L13.8,3.0 M13.7,6.2 C14.2,4.6 14.9,3.5 15.6,2.6" fill="none" stroke-width="1.15" stroke-linecap="round"/>
        <circle cx="16.6" cy="6.8" r="1.15" fill="#E53935"/>
      </g>

      <!-- 4. Right (88%): Soft rounded gift box with golden ribbon & bow -->
      <g class="winter-orn-item" transform="translate(328, 7)">
        <rect class="winter-orn-gift" x="0.8" y="3.2" width="11.4" height="9.8" rx="2.1"/>
        <rect class="winter-orn-ribbon" x="5.6" y="3.2" width="1.8" height="9.8"/>
        <rect class="winter-orn-ribbon" x="0.8" y="7.2" width="11.4" height="1.7"/>
        <ellipse class="winter-orn-bow" cx="4.6" cy="2.1" rx="2.0" ry="1.35" fill="none" stroke-width="1.15"/>
        <ellipse class="winter-orn-bow" cx="8.4" cy="2.1" rx="2.0" ry="1.35" fill="none" stroke-width="1.15"/>
      </g>
    </svg>
  `;
}

function ensureOverlayNodes() {
  if (!document.getElementById(TOP_GARLAND_ID)) {
    const garland = document.createElement('div');
    garland.id = TOP_GARLAND_ID;
    garland.className = 'winter-top-garland';
    garland.setAttribute('aria-hidden', 'true');
    garland.innerHTML = buildTopGarlandSvg();
    document.body.appendChild(garland);
  }
  if (!document.getElementById(SNOW_CANVAS_ID)) {
    const canvas = document.createElement('canvas');
    canvas.id = SNOW_CANVAS_ID;
    canvas.className = 'winter-snow-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.appendChild(canvas);
  }
  const dock = document.getElementById('bottomDock');
  if (dock && !document.getElementById(DOCK_ORNAMENTS_ID)) {
    const orn = document.createElement('div');
    orn.id = DOCK_ORNAMENTS_ID;
    orn.className = 'winter-dock-ornaments';
    orn.setAttribute('aria-hidden', 'true');
    orn.innerHTML = buildDockMiniOrnamentsSvg();
    dock.appendChild(orn);
  }
}

function removeOverlayNodes() {
  document.getElementById(SNOW_CANVAS_ID)?.remove();
  document.getElementById(TOP_GARLAND_ID)?.remove();
  document.getElementById(DOCK_ORNAMENTS_ID)?.remove();
  document.getElementById('bottomDock')?.classList.remove('dock-winter-shake');
  document.querySelectorAll('.winter-title-hat').forEach((el) => el.remove());
  snowInitialized = false;
  shedClumps = [];
}

function startSnowLoop() {
  stopSnowLoop();
  if (!winterEnabled || document.hidden) return;
  if (prefersReducedMotion()) {
    initDockHeights(0.65);
    drawSnowFrame(0, false);
    return;
  }
  if (!snowInitialized) initDockHeights(0);
  lastTickMs = performance.now();
  const tick = (nowMs) => {
    if (!winterEnabled || document.hidden) {
      rafId = null;
      return;
    }
    const dt = Math.min(0.05, Math.max(0, (nowMs - lastTickMs) / 1000));
    lastTickMs = nowMs;
    elapsedSec += dt;
    drawSnowFrame(dt, true);
    rafId = window.requestAnimationFrame(tick);
  };
  rafId = window.requestAnimationFrame(tick);
}

function stopSnowLoop() {
  if (rafId !== null) {
    window.cancelAnimationFrame(rafId);
    rafId = null;
  }
}

function bindGlobalLifecycleListeners() {
  if (listenersBound) return;
  listenersBound = true;
  document.addEventListener('visibilitychange', () => {
    if (!winterEnabled) return;
    if (document.hidden) stopSnowLoop();
    else startSnowLoop();
  });
  window.addEventListener('resize', () => {
    if (!winterEnabled) return;
    flakes = createFlakePool(window.innerWidth || 390, window.innerHeight || 844);
    drawSnowFrame(0, !prefersReducedMotion());
  });
  if (typeof window.matchMedia === 'function') {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => {
      if (!winterEnabled) return;
      startSnowLoop();
    };
    if (typeof mq.addEventListener === 'function') mq.addEventListener('change', onChange);
  }
}

export function triggerDockSnowShake() {
  if (!winterEnabled || prefersReducedMotion()) return;
  const dock = document.getElementById('bottomDock');
  if (dock) {
    dock.classList.remove('dock-winter-shake');
    void dock.offsetWidth;
    dock.classList.add('dock-winter-shake');
    if (shakeTimeoutId) clearTimeout(shakeTimeoutId);
    shakeTimeoutId = setTimeout(() => {
      dock.classList.remove('dock-winter-shake');
    }, 210);
  }

  const span = getDockSnowSpan() || lastDockSpan;
  if (span) {
    const stepX = span.width / (DOCK_BINS - 1);
    for (let i = 1; i < DOCK_BINS - 1; i++) {
      const hVal = Math.max(targetDockHeights[i], displayDockHeights[i]);
      if (hVal < 0.45) continue;
      const pieces = hVal > 5 ? 2 : 1;
      for (let p = 0; p < pieces; p++) {
        if (shedClumps.length >= 64) break;
        shedClumps.push({
          x: span.left + i * stepX + (Math.random() - 0.5) * 6,
          y: span.top - hVal * (0.35 + Math.random() * 0.55),
          vx: (Math.random() - 0.5) * 105,
          vy: -(16 + Math.random() * 34),
          r: Math.max(1.5, Math.min(4.2, 1.3 + hVal * 0.25 + Math.random() * 0.8)),
          life: 0.95 + Math.random() * 0.45,
          alpha: 0.85 + Math.random() * 0.15
        });
      }
    }
  }
  targetDockHeights.fill(0);
  displayDockHeights.fill(0);
}

export function getWinterDockSnowDebugState() {
  let maxDisp = 0;
  let maxTarget = 0;
  let sumDisp = 0;
  for (let i = 0; i < DOCK_BINS; i++) {
    if (displayDockHeights[i] > maxDisp) maxDisp = displayDockHeights[i];
    if (targetDockHeights[i] > maxTarget) maxTarget = targetDockHeights[i];
    sumDisp += displayDockHeights[i];
  }
  return {
    maxHeightPx: Number(maxDisp.toFixed(2)),
    targetMaxHeightPx: Number(maxTarget.toFixed(2)),
    avgHeightPx: Number((sumDisp / DOCK_BINS).toFixed(2)),
    activeClumps: shedClumps.length,
    hasOrnaments: Boolean(document.getElementById(DOCK_ORNAMENTS_ID))
  };
}

export function setWinterThemeEnabled(enabled) {
  winterEnabled = Boolean(enabled);
  document.body?.classList.toggle('winter-theme-on', winterEnabled);
  if (!winterEnabled) {
    stopSnowLoop();
    removeOverlayNodes();
    return;
  }
  bindGlobalLifecycleListeners();
  ensureOverlayNodes();
  startSnowLoop();
}

export function isWinterThemeEnabled() {
  return winterEnabled;
}

export function refreshWinterThemeColors() {
  if (!winterEnabled) return;
  ensureOverlayNodes();
  if (prefersReducedMotion()) drawSnowFrame(0, false);
}

export function santaHatBadgeHtml() {
  if (!winterEnabled) return '';
  return `<span class="winter-title-hat" aria-hidden="true"><svg viewBox="0 0 28 24" width="26" height="22"><g transform="rotate(-14 13 17)"><path d="M5,17 C7.5,7 14.5,3.5 22.5,6.8 C19.5,10 18.2,13.5 19.6,17 Z" fill="#D9383A"/><path d="M13.5,6.2 C16.5,5.3 19.5,5.9 22.2,7.1 C19.8,8.8 18.4,10.8 17.6,13.2 C16.8,9.8 15.6,7.5 13.5,6.2 Z" fill="#B8282B" opacity="0.55"/><path d="M3.2,17.4 C3.2,15.3 5.2,14.4 11.8,14.2 C18.2,14.0 21.2,15.2 21.2,17.4 C21.2,19.4 18.4,20.1 12.2,20.0 C6.2,19.9 3.2,19.4 3.2,17.4 Z" fill="#F8FAFD" stroke="#D5DFEC" stroke-width="0.65"/><circle cx="23.2" cy="7.4" r="2.9" fill="#FFFFFF" stroke="#DCE5F0" stroke-width="0.55"/></g></svg></span>`;
}
