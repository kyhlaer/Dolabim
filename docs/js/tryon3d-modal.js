import { localizedText } from './constants.js';
import { svgIcon } from './icons.js';
import { displaySubCategory } from './recommendation-engine.js';
import { escHtml, isSafeImageDataUrl, sanitizeErrorMessage } from './security.js';
import { getGeminiApiKey, getPhotoDataUrl } from './storage.js';
import {
  analyzeBodyFor3D,
  generateAiVirtualTryOn3D,
  loadHtmlImage
} from './studio-processor.js';

const Z_ORDER = { SHOES: 0, BOTTOM: 1, TOP: 2, SUIT: 3, OUTERWEAR: 4, ACCESSORY: 5 };

function default3DLayer(item, seg, fitRect, cw, ch) {
  if (!seg || !fitRect || cw <= 0 || ch <= 0) {
    const presets = {
      TOP: [0.50, 0.34, 0.68],
      OUTERWEAR: [0.50, 0.35, 0.74],
      BOTTOM: [0.50, 0.64, 0.74],
      SUIT: [0.50, 0.48, 0.88],
      SHOES: [0.50, 0.87, 0.42],
      ACCESSORY: [0.66, 0.24, 0.34]
    };
    const [nx, ny, sc] = presets[item.category] || [0.5, 0.45, 0.65];
    return { item, normX: nx, normY: ny, scale: sc, rotationDeg: 0, depthCurve: 0.58 };
  }

  const toCanvasX = (ix) => Math.min(0.92, Math.max(0.08, (fitRect.left + ix * fitRect.width) / cw));
  const toCanvasY = (iy) => Math.min(0.92, Math.max(0.08, (fitRect.top + iy * fitRect.height) / ch));
  const widthAdj = Math.min(1.35, Math.max(0.45, fitRect.width / Math.max(1, Math.min(cw, ch))));
  const shoulderScale = Math.min(1.50, Math.max(0.40, seg.shoulderSpanNorm * widthAdj * 1.38));
  const cx = toCanvasX(seg.headCenterX);
  const torsoMidY = toCanvasY((seg.shoulderY + seg.waistY) * 0.52);
  const legsMidY = toCanvasY((seg.waistY + seg.ankleY) * 0.50);

  switch (item.category) {
    case 'OUTERWEAR': return { item, normX: cx, normY: Math.min(0.92, torsoMidY + 0.01), scale: Math.min(1.6, shoulderScale * 1.12), rotationDeg: 0, depthCurve: 0.64 };
    case 'BOTTOM': return { item, normX: cx, normY: legsMidY, scale: Math.min(1.6, shoulderScale * 1.15), rotationDeg: 0, depthCurve: 0.54 };
    case 'SUIT': return { item, normX: cx, normY: toCanvasY((seg.shoulderY + seg.ankleY) * 0.48), scale: Math.min(1.65, shoulderScale * 1.25), rotationDeg: 0, depthCurve: 0.60 };
    case 'SHOES': return { item, normX: cx, normY: toCanvasY(seg.ankleY), scale: Math.max(0.28, shoulderScale * 0.65), rotationDeg: 0, depthCurve: 0.35 };
    case 'ACCESSORY': return { item, normX: toCanvasX(Math.min(0.85, seg.headCenterX + seg.shoulderSpanNorm * 0.52)), normY: toCanvasY(seg.waistY), scale: Math.max(0.25, shoulderScale * 0.55), rotationDeg: 0, depthCurve: 0.35 };
    default: return { item, normX: cx, normY: torsoMidY, scale: shoulderScale, rotationDeg: 0, depthCurve: 0.60 };
  }
}

async function createTransparentCutoutCanvas(dataUrl) {
  const img = await loadHtmlImage(dataUrl);
  const c = document.createElement('canvas');
  c.width = img.width;
  c.height = img.height;
  const ctx = c.getContext('2d');
  ctx.drawImage(img, 0, 0);
  const id = ctx.getImageData(0, 0, c.width, c.height);
  const d = id.data;
  for (let i = 0; i < d.length; i += 4) {
    const r = d[i], g = d[i + 1], b = d[i + 2];
    if (r >= 244 && g >= 244 && b >= 244) {
      d[i + 3] = 0;
    } else if (r >= 230 && g >= 230 && b >= 230) {
      const minC = Math.min(r, g, b);
      d[i + 3] = Math.min(255, Math.max(0, (244 - minC) * 18));
    }
  }
  ctx.putImageData(id, 0, 0);
  return { canvas: c, dataUrl };
}

export function openVirtualTryOn3DModal({
  initialItems = [],
  allWardrobeItems = [],
  lang = 'TR',
  modalRoot,
  onPickBodyGallery,
  onPickBodyCamera,
  onOpenSettings,
  registerModalBack,
  showToast
}) {
  const wrap = document.createElement('div');
  wrap.className = 'modal-backdrop';
  const isEn = lang !== 'TR';

  wrap.innerHTML = `
    <div class="modal-panel fullscreen">
      <div class="modal-header" style="padding:10px 12px;">
        <div style="display:flex; align-items:center; gap:8px; min-width:0; flex:1;">
          <button type="button" class="btn-icon-ghost" id="try3CloseBtn" aria-label="Kapat">${svgIcon('close', 18)}</button>
          <div style="min-width:0;">
            <div style="font-weight:700; font-size:15px; display:flex; align-items:center; gap:6px;">${svgIcon('accessibilityNew', 18)} ${localizedText(lang, '3D Sanal Kabin', '3D Virtual Fitting', '3D-Anprobe', 'Cabine 3D', 'Probador 3D', '3D 虚拟试衣间')}</div>
            <div class="muted-note" style="font-size:11px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
              ${localizedText(lang, 'Vücut entegrasyonlu 3D kıvrım · Sürükle / iki parmakla yakınlaştır', 'Body-integrated 3D drape · Drag & 2-finger pinch')}
            </div>
          </div>
        </div>
        <div class="header-actions" style="gap:4px;">
          <button type="button" class="icon-btn bordered hidden" id="try3AutoFitBtn" title="${localizedText(lang, 'Vücuda Oturt', 'Fit to Body')}">${svgIcon('target', 18)}</button>
          <button type="button" class="icon-btn bordered" id="try3CamBtn" title="${localizedText(lang, 'Kamera', 'Camera')}">${svgIcon('camera', 18)}</button>
          <button type="button" class="icon-btn bordered" id="try3GalBtn" title="${localizedText(lang, 'Galeri', 'Gallery')}">${svgIcon('photoLibrary', 18)}</button>
        </div>
      </div>

      <div class="chip-row" style="padding:6px 12px; border-bottom:1px solid var(--border);">
        <button type="button" class="chip-pill compact accent" id="try3SilhouetteToggle">${svgIcon('check', 13)} ${isEn ? 'Body Silhouette' : 'Vücut Entegrasyonu'}</button>
        <button type="button" class="chip-pill compact selected" id="try3AiGenBtn">${svgIcon('autoAwesome', 13)} ${isEn ? 'AI Reality Fit' : 'AI Gerçekçi Giydir'}</button>
        <button type="button" class="chip-pill compact hidden" id="try3CompareBtn">${isEn ? '3D Mesh View' : '3D Önizleme'}</button>
        <button type="button" class="chip-pill compact hidden" id="try3SaveAiBtn">${svgIcon('download', 13)} ${isEn ? 'Save' : 'Kaydet'}</button>
      </div>

      <div style="flex:1; padding:12px; display:flex; flex-direction:column; min-height:0; position:relative;">
        <div class="canvas-stage" id="try3Stage">
          <canvas id="try3Canvas"></canvas>
          <div id="try3NoBodyOverlay" class="hidden" style="position:absolute; top:12px; left:50%; transform:translateX(-50%); z-index:4;">
            <button type="button" class="chip-pill" id="try3UploadBodyCenterBtn">${svgIcon('accessibilityNew', 15)} ${isEn ? 'Upload your photo for 3D try-on' : '3D deneme için kendi fotoğrafını yükle'}</button>
          </div>
          <div id="try3LoadingOverlay" class="hidden" style="position:absolute; inset:0; background:rgba(0,0,0,0.55); display:flex; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:#fff; z-index:8;">
            <span class="spinner" style="width:28px; height:28px;"></span>
            <strong style="font-size:13px;">${isEn ? 'Integrating garments in 3D...' : 'Kıyafetler fotoğrafına 3D entegre ediliyor...'}</strong>
          </div>
        </div>
      </div>

      <div style="padding:8px 14px calc(10px + env(safe-area-inset-bottom, 0px)); border-top:1px solid var(--border); background:var(--card); display:flex; flex-direction:column; gap:6px;">
        <div class="chip-row flush" id="try3LayerChips"></div>
        <div class="chip-row flush hidden" id="try3AddStrip" style="background:var(--secondary); padding:6px; border-radius:12px;"></div>
        <div id="try3Sliders">
          <div style="display:flex; align-items:center; gap:8px;">
            <span class="muted-note" style="width:58px;">${isEn ? 'Size' : 'Boyut'}</span>
            <input type="range" id="try3ScaleSlider" min="0.25" max="1.85" step="0.01" value="0.68" style="flex:1;" />
            <button type="button" class="btn-icon-ghost" id="try3ResetLayerBtn" title="Sıfırla">${svgIcon('refresh', 16)}</button>
          </div>
          <div style="display:flex; align-items:center; gap:8px;">
            <span class="muted-note" style="width:58px;">${isEn ? '3D Wrap' : '3D Kıvrım'}</span>
            <input type="range" id="try3CurveSlider" min="0" max="1" step="0.02" value="0.58" style="flex:1;" />
            <span class="muted-note" style="width:36px; text-align:right;">${isEn ? 'Light' : 'Işık'}</span>
            <input type="range" id="try3LightSlider" min="0" max="1" step="0.02" value="0.45" style="flex:0.75;" />
          </div>
        </div>
      </div>
    </div>
  `;
  modalRoot.appendChild(wrap);

  const stage = wrap.querySelector('#try3Stage');
  const canvas = wrap.querySelector('#try3Canvas');
  const ctx = canvas.getContext('2d');
  const layerChipsEl = wrap.querySelector('#try3LayerChips');
  const addStripEl = wrap.querySelector('#try3AddStrip');
  const scaleSlider = wrap.querySelector('#try3ScaleSlider');
  const curveSlider = wrap.querySelector('#try3CurveSlider');
  const lightSlider = wrap.querySelector('#try3LightSlider');
  const noBodyOverlay = wrap.querySelector('#try3NoBodyOverlay');
  const autoFitBtn = wrap.querySelector('#try3AutoFitBtn');
  const compareBtn = wrap.querySelector('#try3CompareBtn');
  const saveAiBtn = wrap.querySelector('#try3SaveAiBtn');
  const loadingOverlay = wrap.querySelector('#try3LoadingOverlay');

  let bodyImg = null;
  let bodyDataUrl = null;
  let bodySeg = null;
  let fitRect = null;
  let cw = 320, ch = 420;

  let bodyIntegration = true;
  let lightHarmony = 0.45;
  let showAddPicker = false;
  let aiResultImg = null;
  let aiResultUrl = null;
  let showAiResult = false;

  const layers = initialItems.map((it) => default3DLayer(it, null));
  let selectedIdx = layers.length > 0 ? 0 : -1;
  const cutoutMap = new Map(); // studioPhotoId -> { canvas, dataUrl }

  const resolveCutoutPhotoId = (item) => {
    if (item?.useCatalogPhotos && item?.catalogPhotoFileNames?.[0]) return item.catalogPhotoFileNames[0];
    return item?.photoFileNames?.[0] || item?.coverPhoto;
  };

  const ensureCutoutLoaded = async (item) => {
    const pid = resolveCutoutPhotoId(item);
    if (!pid || cutoutMap.has(pid)) return;
    const url = await getPhotoDataUrl(pid);
    if (!url) return;
    const cut = await createTransparentCutoutCanvas(url);
    cutoutMap.set(pid, cut);
    renderCanvas();
  };

  const computeFitRect = () => {
    if (!bodyImg) { fitRect = null; return; }
    const imgRatio = bodyImg.width / Math.max(1, bodyImg.height);
    const boxRatio = cw / Math.max(1, ch);
    let dw, dh;
    if (imgRatio > boxRatio) { dw = cw; dh = cw / imgRatio; }
    else { dh = ch; dw = ch * imgRatio; }
    fitRect = { left: (cw - dw) * 0.5, top: (ch - dh) * 0.5, width: dw, height: dh };
  };

  const reloadBodyPhoto = async () => {
    const url = await getPhotoDataUrl('user_body_photo');
    if (url) {
      bodyDataUrl = url;
      bodyImg = await loadHtmlImage(url);
      bodySeg = await analyzeBodyFor3D(bodyImg);
      noBodyOverlay.classList.add('hidden');
      autoFitBtn.classList.remove('hidden');
      computeFitRect();
      layers.forEach((l, i) => {
        layers[i] = default3DLayer(l.item, bodySeg, fitRect, cw, ch);
      });
      syncSliders();
    } else {
      bodyImg = null; bodyDataUrl = null; bodySeg = null; fitRect = null;
      noBodyOverlay.classList.remove('hidden');
      autoFitBtn.classList.add('hidden');
    }
    aiResultImg = null; aiResultUrl = null; showAiResult = false;
    compareBtn.classList.add('hidden');
    saveAiBtn.classList.add('hidden');
    renderCanvas();
  };

  const syncSliders = () => {
    const active = layers[selectedIdx];
    wrap.querySelector('#try3Sliders').classList.toggle('hidden', !active || (showAiResult && aiResultImg != null));
    if (active) {
      scaleSlider.value = String(active.scale);
      curveSlider.value = String(active.depthCurve);
    }
  };

  const renderLayerChips = () => {
    layerChipsEl.innerHTML = '';
    layers.forEach((l, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `chip-pill compact ${idx === selectedIdx ? 'selected' : ''}`;
      btn.innerHTML = `<span>${escHtml(displaySubCategory(l.item.subCategory, lang))}</span> <span data-rm="${idx}" style="opacity:0.75; padding-left:4px; display:inline-flex; align-items:center;">${svgIcon('close', 12)}</span>`;
      btn.addEventListener('click', (e) => {
        if (e.target.dataset.rm != null) {
          layers.splice(idx, 1);
          selectedIdx = layers.length === 0 ? -1 : Math.min(selectedIdx, layers.length - 1);
          renderLayerChips(); syncSliders(); renderCanvas();
          return;
        }
        selectedIdx = idx;
        renderLayerChips(); syncSliders(); renderCanvas();
      });
      layerChipsEl.appendChild(btn);
    });

    const addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'chip-pill compact accent';
    addBtn.textContent = `+ ${isEn ? 'Add Piece' : 'Parça Ekle'}`;
    addBtn.addEventListener('click', () => {
      showAddPicker = !showAddPicker;
      renderAddPicker();
    });
    layerChipsEl.appendChild(addBtn);
  };

  const renderAddPicker = async () => {
    addStripEl.classList.toggle('hidden', !showAddPicker);
    if (!showAddPicker) return;
    addStripEl.innerHTML = '';
    for (const item of allWardrobeItems) {
      const thumbBtn = document.createElement('button');
      thumbBtn.type = 'button';
      thumbBtn.style.cssText = 'width:54px; height:54px; border-radius:10px; background:#fff; border:1px solid var(--border); padding:3px; flex-shrink:0; cursor:pointer;';
      const pid = (item.useOriginalPhotos && !item.useCatalogPhotos)
        ? (item.originalPhotoFileNames?.[0] || item.photoFileNames?.[0])
        : resolveCutoutPhotoId(item);
      const url = pid ? await getPhotoDataUrl(pid) : null;
      if (url && isSafeImageDataUrl(url)) {
        const im = document.createElement('img');
        im.src = url;
        im.alt = '';
        im.style.cssText = 'width:100%; height:100%; object-fit:contain;';
        thumbBtn.appendChild(im);
      } else {
        thumbBtn.textContent = displaySubCategory(item.subCategory, lang).slice(0, 4);
      }
      thumbBtn.addEventListener('click', async () => {
        layers.push(default3DLayer(item, bodySeg, fitRect, cw, ch));
        selectedIdx = layers.length - 1;
        showAddPicker = false;
        aiResultImg = null; showAiResult = false;
        compareBtn.classList.add('hidden');
        saveAiBtn.classList.add('hidden');
        await ensureCutoutLoaded(item);
        renderAddPicker(); renderLayerChips(); syncSliders(); renderCanvas();
      });
      addStripEl.appendChild(thumbBtn);
    }
  };

  // Dedicated scratch canvas for per-garment saveLayer compositing (silhouette + 3D shading)
  const layerBuf = document.createElement('canvas');
  const layerCtx = layerBuf.getContext('2d');

  const drawGarmentLayer3D = (l, isSelected) => {
    const pid = resolveCutoutPhotoId(l.item);
    const entry = pid ? cutoutMap.get(pid) : null;
    if (!entry) return;
    const gCanv = entry.canvas;

    const baseBox = Math.min(cw, ch) * 0.52;
    const asp = gCanv.width / Math.max(1, gCanv.height);
    const boxSize = baseBox * l.scale;
    const drawW = asp >= 1 ? boxSize : boxSize * asp;
    const drawH = asp >= 1 ? boxSize / asp : boxSize;
    const cx = l.normX * cw;
    const cy = l.normY * ch;
    const curve = l.depthCurve;

    if (layerBuf.width !== canvas.width || layerBuf.height !== canvas.height) {
      layerBuf.width = canvas.width;
      layerBuf.height = canvas.height;
    }
    const dpr = window.devicePixelRatio || 1;
    layerCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    layerCtx.clearRect(0, 0, cw, ch);

    if (bodyIntegration && bodySeg && fitRect) {
      layerCtx.save();
      const padW = fitRect.width * 0.045;
      layerCtx.beginPath();
      layerCtx.rect(fitRect.left - padW, fitRect.top, fitRect.width + padW * 2, fitRect.height);
      layerCtx.clip();
    }

    layerCtx.save();
    layerCtx.translate(cx, cy);
    layerCtx.rotate((l.rotationDeg * Math.PI) / 180);

    // Subtle soft body drop shadow
    if (curve > 0.1) {
      layerCtx.save();
      layerCtx.globalAlpha = 0.14 * curve;
      layerCtx.filter = 'brightness(0)';
      layerCtx.drawImage(gCanv, -drawW * 0.5 + 4 * curve, -drawH * 0.5 + 7 * curve, drawW, drawH);
      layerCtx.restore();
    }

    // 16 vertical strips for cylindrical 3D curvature & shoulder/hem wrap
    const strips = 16;
    const srcStripW = gCanv.width / strips;
    for (let s = 0; s < strips; s++) {
      const u0 = s / strips;
      const u1 = (s + 1) / strips;
      const nx0 = (u0 - 0.5) * 2;
      const nx1 = (u1 - 0.5) * 2;
      const midNx = (nx0 + nx1) * 0.5;
      const maxT = Math.max(0.001, Math.PI * 0.42 * curve);
      const wrap = (nx) => (curve > 0.02 ? Math.sin(nx * maxT) / Math.sin(maxT) : nx);
      const x0 = wrap(nx0) * drawW * 0.5;
      const x1 = wrap(nx1) * drawW * 0.5;
      const arcY = l.item.category === 'BOTTOM'
        ? (1 - midNx * midNx) * drawH * 0.022 * curve
        : -(1 - midNx * midNx) * drawH * 0.032 * curve;
      layerCtx.drawImage(
        gCanv,
        u0 * gCanv.width, 0, srcStripW, gCanv.height,
        x0, -drawH * 0.5 + arcY, (x1 - x0) + 0.8, drawH
      );
    }

    // 3D cylindrical side shading + ambient warmth inside garment pixels only (source-atop)
    layerCtx.globalCompositeOperation = 'source-atop';
    if (curve > 0.05) {
      const grad = layerCtx.createLinearGradient(-drawW * 0.5, 0, drawW * 0.5, 0);
      const edgeA = (0.24 * curve).toFixed(2);
      const midA = (0.06 * curve).toFixed(2);
      grad.addColorStop(0, `rgba(0,0,0,${edgeA})`);
      grad.addColorStop(0.5, `rgba(255,255,255,${midA})`);
      grad.addColorStop(1, `rgba(0,0,0,${edgeA})`);
      layerCtx.fillStyle = grad;
      layerCtx.fillRect(-drawW * 0.6, -drawH * 0.6, drawW * 1.2, drawH * 1.2);
    }
    if (bodySeg && lightHarmony > 0.02) {
      const [ar, ag, ab] = bodySeg.ambientRgb;
      layerCtx.fillStyle = `rgba(${ar},${ag},${ab},${(lightHarmony * 0.24).toFixed(2)})`;
      layerCtx.fillRect(-drawW * 0.6, -drawH * 0.6, drawW * 1.2, drawH * 1.2);
    }
    layerCtx.globalCompositeOperation = 'source-over';
    layerCtx.restore();

    // Apply person silhouette integration + head/neck cutout protection
    if (bodyIntegration && bodySeg && fitRect) {
      const padW = fitRect.width * 0.045;
      layerCtx.save();
      layerCtx.globalCompositeOperation = 'destination-in';
      layerCtx.globalAlpha = 0.92;
      layerCtx.drawImage(bodySeg.maskCanvas, fitRect.left - padW, fitRect.top, fitRect.width + padW * 2, fitRect.height);
      layerCtx.restore();

      if (l.item.category !== 'ACCESSORY') {
        const neckScreenY = fitRect.top + bodySeg.neckY * fitRect.height;
        const fade = Math.max(10, fitRect.height * 0.035);
        layerCtx.save();
        layerCtx.globalCompositeOperation = 'destination-out';
        const gHead = layerCtx.createLinearGradient(0, neckScreenY - fade, 0, neckScreenY);
        gHead.addColorStop(0, 'rgba(0,0,0,0.96)');
        gHead.addColorStop(1, 'rgba(0,0,0,0)');
        layerCtx.fillStyle = gHead;
        layerCtx.fillRect(fitRect.left - padW, 0, fitRect.width + padW * 2, neckScreenY);
        layerCtx.restore();
      }
      layerCtx.restore();
    }

    ctx.drawImage(layerBuf, 0, 0, cw, ch);

    if (isSelected) {
      ctx.save();
      ctx.translate(cx, cy);
      ctx.rotate((l.rotationDeg * Math.PI) / 180);
      ctx.strokeStyle = getComputedStyle(document.body).getPropertyValue('--accent') || '#B65B43';
      ctx.lineWidth = 2;
      ctx.strokeRect(-drawW * 0.5, -drawH * 0.5, drawW, drawH);
      ctx.restore();
    }
  };

  const renderCanvas = () => {
    const r = stage.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    cw = Math.max(200, Math.floor(r.width));
    ch = Math.max(260, Math.floor(r.height));
    if (canvas.width !== Math.floor(cw * dpr) || canvas.height !== Math.floor(ch * dpr)) {
      canvas.width = Math.floor(cw * dpr);
      canvas.height = Math.floor(ch * dpr);
      computeFitRect();
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cw, ch);

    if (showAiResult && aiResultImg) {
      const ir = aiResultImg.width / Math.max(1, aiResultImg.height);
      const br = cw / ch;
      const dw = ir > br ? cw : ch * ir;
      const dh = ir > br ? cw / ir : ch;
      ctx.drawImage(aiResultImg, (cw - dw) * 0.5, (ch - dh) * 0.5, dw, dh);
      return;
    }

    if (bodyImg && fitRect) {
      ctx.drawImage(bodyImg, fitRect.left, fitRect.top, fitRect.width, fitRect.height);
    } else {
      // Minimalist mannequin vector guide
      ctx.strokeStyle = '#9EA2AB';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(cw * 0.5, ch * 0.14, Math.min(cw, ch) * 0.065, 0, Math.PI * 2);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(cw * 0.34, ch * 0.23);
      ctx.lineTo(cw * 0.66, ch * 0.23);
      ctx.lineTo(cw * 0.72, ch * 0.46);
      ctx.lineTo(cw * 0.63, ch * 0.50);
      ctx.lineTo(cw * 0.61, ch * 0.87);
      ctx.lineTo(cw * 0.52, ch * 0.87);
      ctx.lineTo(cw * 0.50, ch * 0.55);
      ctx.lineTo(cw * 0.48, ch * 0.87);
      ctx.lineTo(cw * 0.39, ch * 0.87);
      ctx.lineTo(cw * 0.37, ch * 0.50);
      ctx.lineTo(cw * 0.28, ch * 0.46);
      ctx.closePath();
      ctx.stroke();
    }

    const ordered = layers
      .map((layer, origIdx) => ({ layer, origIdx }))
      .sort((a, b) => (Z_ORDER[a.layer.item.category] ?? 2) - (Z_ORDER[b.layer.item.category] ?? 2));
    ordered.forEach(({ layer, origIdx }) => drawGarmentLayer3D(layer, origIdx === selectedIdx));
  };

  // Multi-touch Pinch + Drag anywhere on canvas + Desktop mouse wheel zoom
  const pointers = new Map();
  let lastPinchDist = null;
  let lastPinchMid = null;
  let lastSingle = null;

  canvas.addEventListener('pointerdown', (e) => {
    if (showAiResult && aiResultImg) return;
    canvas.setPointerCapture(e.pointerId);
    const rect = canvas.getBoundingClientRect();
    const pt = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    pointers.set(e.pointerId, pt);
    if (pointers.size === 1 && layers.length > 0) {
      let best = selectedIdx, bestD2 = Infinity;
      layers.forEach((l, i) => {
        const d2 = (l.normX * cw - pt.x) ** 2 + (l.normY * ch - pt.y) ** 2;
        if (d2 < bestD2) { bestD2 = d2; best = i; }
      });
      if (bestD2 < (Math.min(cw, ch) * 0.32) ** 2 && best !== selectedIdx) {
        selectedIdx = best;
        renderLayerChips(); syncSliders(); renderCanvas();
      }
      lastSingle = pt;
    } else if (pointers.size >= 2) {
      const [p1, p2] = [...pointers.values()];
      lastPinchDist = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      lastPinchMid = { x: (p1.x + p2.x) * 0.5, y: (p1.y + p2.y) * 0.5 };
    }
  });

  canvas.addEventListener('pointermove', (e) => {
    if (!pointers.has(e.pointerId)) return;
    const rect = canvas.getBoundingClientRect();
    const pt = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    pointers.set(e.pointerId, pt);
    const active = layers[selectedIdx];
    if (!active) return;

    if (pointers.size >= 2) {
      const [p1, p2] = [...pointers.values()];
      const dist = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      const mid = { x: (p1.x + p2.x) * 0.5, y: (p1.y + p2.y) * 0.5 };
      if (lastPinchDist && lastPinchDist > 5) {
        active.scale = Math.min(1.85, Math.max(0.25, active.scale * (dist / lastPinchDist)));
      }
      if (lastPinchMid) {
        active.normX = Math.min(0.95, Math.max(0.05, active.normX + (mid.x - lastPinchMid.x) / cw));
        active.normY = Math.min(0.95, Math.max(0.05, active.normY + (mid.y - lastPinchMid.y) / ch));
      }
      lastPinchDist = dist; lastPinchMid = mid;
      syncSliders(); renderCanvas();
    } else if (pointers.size === 1 && lastSingle) {
      active.normX = Math.min(0.95, Math.max(0.05, active.normX + (pt.x - lastSingle.x) / cw));
      active.normY = Math.min(0.95, Math.max(0.05, active.normY + (pt.y - lastSingle.y) / ch));
      lastSingle = pt;
      renderCanvas();
    }
  });

  const upHandler = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) { lastPinchDist = null; lastPinchMid = null; }
    if (pointers.size === 1) lastSingle = [...pointers.values()][0];
    if (pointers.size === 0) lastSingle = null;
  };
  canvas.addEventListener('pointerup', upHandler);
  canvas.addEventListener('pointercancel', upHandler);
  canvas.addEventListener('wheel', (e) => {
    const active = layers[selectedIdx];
    if (!active || showAiResult) return;
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.06 : 0.94;
    active.scale = Math.min(1.85, Math.max(0.25, active.scale * factor));
    syncSliders(); renderCanvas();
  }, { passive: false });

  scaleSlider.addEventListener('input', () => {
    if (layers[selectedIdx]) {
      layers[selectedIdx].scale = parseFloat(scaleSlider.value);
      renderCanvas();
    }
  });
  curveSlider.addEventListener('input', () => {
    if (layers[selectedIdx]) {
      layers[selectedIdx].depthCurve = parseFloat(curveSlider.value);
      renderCanvas();
    }
  });
  lightSlider.addEventListener('input', () => {
    lightHarmony = parseFloat(lightSlider.value);
    renderCanvas();
  });

  wrap.querySelector('#try3ResetLayerBtn').addEventListener('click', () => {
    if (!layers[selectedIdx]) return;
    layers[selectedIdx] = default3DLayer(layers[selectedIdx].item, bodySeg, fitRect, cw, ch);
    syncSliders(); renderCanvas();
  });

  autoFitBtn.addEventListener('click', () => {
    if (!bodySeg) return;
    layers.forEach((l, i) => {
      layers[i] = default3DLayer(l.item, bodySeg, fitRect, cw, ch);
    });
    showAiResult = false;
    syncSliders(); renderCanvas();
    showToast(isEn ? 'Fitted to body contours' : 'Kıyafetler vücut hatlarına oturtuldu');
  });

  wrap.querySelector('#try3SilhouetteToggle').addEventListener('click', (e) => {
    bodyIntegration = !bodyIntegration;
    e.currentTarget.classList.toggle('accent', bodyIntegration);
    e.currentTarget.innerHTML = `${bodyIntegration ? `${svgIcon('check', 13)} ` : ''}${isEn ? 'Body Silhouette' : 'Vücut Entegrasyonu'}`;
    renderCanvas();
  });

  wrap.querySelector('#try3AiGenBtn').addEventListener('click', async () => {
    if (!bodyDataUrl) {
      showToast(isEn ? 'Please upload your photo first' : 'Önce kendi boy fotoğrafını yükle');
      return;
    }
    if (layers.length === 0) {
      showToast(isEn ? 'Add at least one garment first' : 'Önce en az bir kıyafet seç');
      return;
    }
    const key = await getGeminiApiKey();
    if (!key) {
      if (confirm(localizedText(lang,
        "Cihaz içi 3D entegrasyon şu an aktif. Bulut tabanlı gerçekçi AI giydirme (gemini-nano-banana-2.1) için Ayarlar'dan kendi Gemini API anahtarını eklemek ister misin?",
        'On-device 3D wrap is active. Open Settings to add an optional Gemini API key for cloud photorealistic try-on?'))) {
        closeModal({ skipHistoryBack: true });
        onOpenSettings?.({ replaceHistory: true });
      }
      return;
    }
    const garmentEntries = [];
    for (const l of layers) {
      const pid = resolveCutoutPhotoId(l.item);
      const entry = pid ? cutoutMap.get(pid) : null;
      if (entry?.dataUrl) {
        garmentEntries.push({
          label: l.item.name || displaySubCategory(l.item.subCategory, lang),
          dataUrl: entry.dataUrl
        });
      }
    }
    if (garmentEntries.length === 0) return;

    loadingOverlay.classList.remove('hidden');
    try {
      const outUrl = await generateAiVirtualTryOn3D(bodyDataUrl, garmentEntries, key);
      if (!isSafeImageDataUrl(outUrl)) {
        throw new Error('Geçersiz görsel çıktısı');
      }
      aiResultUrl = outUrl;
      aiResultImg = await loadHtmlImage(outUrl);
      showAiResult = true;
      compareBtn.classList.remove('hidden');
      saveAiBtn.classList.remove('hidden');
      compareBtn.textContent = isEn ? 'Compare 3D Mesh' : 'Önce / Sonra';
      syncSliders(); renderCanvas();
    } catch (err) {
      showToast(sanitizeErrorMessage(err, isEn ? 'AI 3D error' : 'AI 3D hatası'));
    } finally {
      loadingOverlay.classList.add('hidden');
    }
  });

  compareBtn.addEventListener('click', () => {
    showAiResult = !showAiResult;
    compareBtn.textContent = showAiResult
      ? (isEn ? 'Switch to 3D Mesh' : '3D Önizlemeye Geç')
      : (isEn ? 'Show AI Result' : 'AI Çıktısını Göster');
    syncSliders(); renderCanvas();
  });

  saveAiBtn.addEventListener('click', () => {
    if (!aiResultUrl || !isSafeImageDataUrl(aiResultUrl)) return;
    const a = document.createElement('a');
    a.href = aiResultUrl;
    a.download = `Dolabim_3D_${Date.now()}.jpg`;
    a.click();
    showToast(isEn ? 'Image downloaded!' : 'Görsel indirildi!');
  });

  const handleBodyUpdate = () => reloadBodyPhoto();
  wrap.querySelector('#try3GalBtn').addEventListener('click', () => onPickBodyGallery(handleBodyUpdate));
  wrap.querySelector('#try3UploadBodyCenterBtn').addEventListener('click', () => onPickBodyGallery(handleBodyUpdate));
  wrap.querySelector('#try3CamBtn').addEventListener('click', () => onPickBodyCamera(handleBodyUpdate));

  const rawCloseModal = () => {
    window.removeEventListener('resize', renderCanvas);
    wrap.remove();
  };
  const closeModal = typeof registerModalBack === 'function'
    ? registerModalBack(rawCloseModal)
    : rawCloseModal;
  wrap.querySelector('#try3CloseBtn').addEventListener('click', () => closeModal());
  window.addEventListener('resize', renderCanvas);

  renderLayerChips();
  syncSliders();
  reloadBodyPhoto();
  layers.forEach((l) => ensureCutoutLoaded(l.item));
}
