import { localizedText } from './constants.js';
import { svgIcon } from './icons.js';
import {
  assertSafeImageDimensions,
  escHtml,
  validateUploadedImageFile
} from './security.js';
import { loadHtmlImage } from './studio-processor.js';

export async function openCropperQueueModal({ files, lang, modalRoot, onCompleteAll, showToast, registerModalBack }) {
  const safeFiles = Array.from(files || []).slice(0, 12);
  const results = [];
  let activeCancel = null;
  let closedByBack = false;
  const closeBackEntry = typeof registerModalBack === 'function'
    ? registerModalBack(() => {
      closedByBack = true;
      activeCancel?.();
    })
    : null;

  for (let i = 0; i < safeFiles.length; i++) {
    if (closedByBack) {
      return;
    }
    const file = safeFiles[i];
    try {
      await validateUploadedImageFile(file);
    } catch (err) {
      const msg = err?.message === 'FILE_TOO_LARGE'
        ? localizedText(lang, 'Görsel çok büyük (maks. 20 MB)', 'Image too large (max 20 MB)')
        : localizedText(lang, 'Yalnızca geçerli görseller desteklenir (JPG, PNG, WebP, HEIC)', 'Only valid images allowed (JPG, PNG, WebP, HEIC)');
      showToast(msg);
      continue;
    }

    const objUrl = URL.createObjectURL(file);
    let img;
    try {
      img = await loadHtmlImage(objUrl);
      assertSafeImageDimensions(img);
    } catch {
      URL.revokeObjectURL(objUrl);
      showToast(localizedText(lang, 'Görsel okunamadı veya boyut sınırı aşıldı', 'Could not decode image or exceeds size limit'));
      continue;
    }

    const qLabel = safeFiles.length > 1 ? `${i + 1}/${safeFiles.length}` : null;
    const chosenDataUrl = await showSingleCropModal({
      img,
      qLabel,
      lang,
      modalRoot,
      setCancel: (fn) => { activeCancel = fn; }
    });
    activeCancel = null;
    URL.revokeObjectURL(objUrl);
    if (chosenDataUrl === null) {
      if (!closedByBack) closeBackEntry?.();
      return; // User cancelled batch
    }
    results.push(chosenDataUrl);
  }
  if (!closedByBack) closeBackEntry?.();
  if (results.length > 0) {
    onCompleteAll(results);
  } else {
    showToast(localizedText(lang, 'Görsel okunamadı', 'Could not load image'));
  }
}

function showSingleCropModal({ img, qLabel, lang, modalRoot, setCancel }) {
  return new Promise((resolve) => {
    let rotation = 0;
    let aspect = null; // null = Free, 1 = 1:1, 0.75 = 3:4
    let cropNorm = { x: 0.08, y: 0.08, w: 0.84, h: 0.84 };
    let settled = false;

    const title = qLabel
      ? localizedText(lang, `Fotoğrafı Kırp (${qLabel})`, `Crop Photo (${qLabel})`)
      : localizedText(lang, 'Fotoğrafı Kırp', 'Crop Photo');

    const wrap = document.createElement('div');
    wrap.className = 'modal-backdrop';
    wrap.innerHTML = `
      <div class="modal-panel fullscreen">
        <div class="modal-header">
          <h3 class="modal-title">${escHtml(title)}</h3>
          <button type="button" class="btn-icon-ghost" id="cropCancelX" aria-label="Kapat">${svgIcon('close', 18)}</button>
        </div>
        <div class="modal-body" style="padding:12px;">
          <div class="wrap-row" style="justify-content:center;">
            <button type="button" class="chip-pill selected" data-ratio="free">${localizedText(lang, 'Serbest', 'Free')}</button>
            <button type="button" class="chip-pill" data-ratio="1">1:1</button>
            <button type="button" class="chip-pill" data-ratio="0.75">3:4</button>
            <button type="button" class="chip-pill accent" id="cropRotateBtn">${svgIcon('rotateRight', 15)} 90°</button>
          </div>
          <div class="canvas-stage" id="cropStage">
            <canvas id="cropCanvas"></canvas>
          </div>
          <p class="muted-note" style="margin:0; text-align:center;">
            ${localizedText(lang, 'Çerçeveyi sürükleyerek kıyafeti ortala veya tam boyut kullan.', 'Drag box center to pan or bottom-right corner to resize.')}
          </p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-secondary" id="cropUseFullBtn">${localizedText(lang, 'Tamamını Kullan', 'Use Full')}</button>
          <button type="button" class="btn-primary" id="cropConfirmBtn">${localizedText(lang, 'Kırp ve Devam Et', 'Crop & Continue')}</button>
        </div>
      </div>
    `;
    modalRoot.appendChild(wrap);

    const stage = wrap.querySelector('#cropStage');
    const canvas = wrap.querySelector('#cropCanvas');
    const ctx = canvas.getContext('2d');
    let imgBox = { x: 0, y: 0, w: 100, h: 100 };

    const rotatedDimensions = () => {
      const swap = (rotation % 180) !== 0;
      return {
        rw: swap ? img.height : img.width,
        rh: swap ? img.width : img.height
      };
    };

    const draw = () => {
      const rect = stage.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const cw = Math.max(200, Math.floor(rect.width));
      const ch = Math.max(240, Math.floor(rect.height));
      if (canvas.width !== Math.floor(cw * dpr) || canvas.height !== Math.floor(ch * dpr)) {
        canvas.width = Math.floor(cw * dpr);
        canvas.height = Math.floor(ch * dpr);
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, cw, ch);

      const { rw, rh } = rotatedDimensions();
      const scale = Math.min((cw - 24) / rw, (ch - 24) / rh);
      const dw = rw * scale;
      const dh = rh * scale;
      const dx = (cw - dw) * 0.5;
      const dy = (ch - dh) * 0.5;
      imgBox = { x: dx, y: dy, w: dw, h: dh };

      ctx.save();
      ctx.translate(cw * 0.5, ch * 0.5);
      ctx.rotate((rotation * Math.PI) / 180);
      const drawW = (rotation % 180) !== 0 ? dh : dw;
      const drawH = (rotation % 180) !== 0 ? dw : dh;
      ctx.drawImage(img, -drawW * 0.5, -drawH * 0.5, drawW, drawH);
      ctx.restore();

      // Dim outer region around crop rect
      const cx = dx + cropNorm.x * dw;
      const cy = dy + cropNorm.y * dh;
      const rx = cropNorm.w * dw;
      const ry = cropNorm.h * dh;

      ctx.fillStyle = 'rgba(0,0,0,0.48)';
      ctx.fillRect(0, 0, cw, cy);
      ctx.fillRect(0, cy, cx, ry);
      ctx.fillRect(cx + rx, cy, cw - (cx + rx), ry);
      ctx.fillRect(0, cy + ry, cw, ch - (cy + ry));

      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 2;
      ctx.strokeRect(cx, cy, rx, ry);

      // Corner resize dot
      ctx.fillStyle = '#F59E0B';
      ctx.beginPath();
      ctx.arc(cx + rx, cy + ry, 10, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 2;
      ctx.stroke();
    };

    const enforceAspect = () => {
      if (!aspect) return;
      const desiredRatio = aspect * (imgBox.h / Math.max(1, imgBox.w));
      let nh = cropNorm.w / Math.max(0.1, desiredRatio);
      if (cropNorm.y + nh > 0.98) {
        nh = 0.98 - cropNorm.y;
        cropNorm.w = Math.max(0.15, nh * desiredRatio);
      }
      cropNorm.h = Math.max(0.15, Math.min(0.96, nh));
    };

    let dragMode = null;
    let startPt = null;
    let startCrop = null;

    canvas.addEventListener('pointerdown', (e) => {
      canvas.setPointerCapture(e.pointerId);
      const r = canvas.getBoundingClientRect();
      const px = e.clientX - r.left;
      const py = e.clientY - r.top;
      const handleX = imgBox.x + (cropNorm.x + cropNorm.w) * imgBox.w;
      const handleY = imgBox.y + (cropNorm.y + cropNorm.h) * imgBox.h;
      dragMode = Math.hypot(px - handleX, py - handleY) < 34 ? 'resize' : 'move';
      startPt = { x: px, y: py };
      startCrop = { ...cropNorm };
    });

    canvas.addEventListener('pointermove', (e) => {
      if (!dragMode || !startPt) return;
      const r = canvas.getBoundingClientRect();
      const px = e.clientX - r.left;
      const py = e.clientY - r.top;
      const ndx = (px - startPt.x) / Math.max(1, imgBox.w);
      const ndy = (py - startPt.y) / Math.max(1, imgBox.h);

      if (dragMode === 'move') {
        cropNorm.x = Math.max(0.01, Math.min(0.99 - startCrop.w, startCrop.x + ndx));
        cropNorm.y = Math.max(0.01, Math.min(0.99 - startCrop.h, startCrop.y + ndy));
      } else {
        cropNorm.w = Math.max(0.18, Math.min(0.99 - startCrop.x, startCrop.w + ndx));
        cropNorm.h = Math.max(0.18, Math.min(0.99 - startCrop.y, startCrop.h + ndy));
        enforceAspect();
      }
      draw();
    });

    const endDrag = () => { dragMode = null; };
    canvas.addEventListener('pointerup', endDrag);
    canvas.addEventListener('pointercancel', endDrag);

    wrap.querySelectorAll('[data-ratio]').forEach((btn) => {
      btn.addEventListener('click', () => {
        wrap.querySelectorAll('[data-ratio]').forEach((b) => b.classList.remove('selected'));
        btn.classList.add('selected');
        const val = btn.dataset.ratio;
        aspect = val === 'free' ? null : parseFloat(val);
        enforceAspect();
        draw();
      });
    });

    wrap.querySelector('#cropRotateBtn').addEventListener('click', () => {
      rotation = (rotation + 90) % 360;
      draw();
    });

    const exportCropped = (fullImage) => {
      const { rw, rh } = rotatedDimensions();
      const maxDim = 1440;
      const scale = Math.min(1, maxDim / Math.max(rw, rh));
      const fullW = Math.max(1, Math.round(rw * scale));
      const fullH = Math.max(1, Math.round(rh * scale));
      const tmp = document.createElement('canvas');
      tmp.width = fullW;
      tmp.height = fullH;
      const tCtx = tmp.getContext('2d');
      tCtx.save();
      tCtx.translate(fullW * 0.5, fullH * 0.5);
      tCtx.rotate((rotation * Math.PI) / 180);
      const dw = (rotation % 180) !== 0 ? fullH : fullW;
      const dh = (rotation % 180) !== 0 ? fullW : fullH;
      tCtx.drawImage(img, -dw * 0.5, -dh * 0.5, dw, dh);
      tCtx.restore();

      if (fullImage) return tmp.toDataURL('image/jpeg', 0.90);

      const sx = Math.max(0, Math.round(cropNorm.x * fullW));
      const sy = Math.max(0, Math.round(cropNorm.y * fullH));
      const sw = Math.max(16, Math.round(cropNorm.w * fullW));
      const sh = Math.max(16, Math.round(cropNorm.h * fullH));
      const out = document.createElement('canvas');
      out.width = sw;
      out.height = sh;
      out.getContext('2d').drawImage(tmp, sx, sy, sw, sh, 0, 0, sw, sh);
      return out.toDataURL('image/jpeg', 0.90);
    };

    const cleanupAndResolve = (val) => {
      if (settled) return;
      settled = true;
      window.removeEventListener('resize', draw);
      wrap.remove();
      resolve(val);
    };
    setCancel?.(() => cleanupAndResolve(null));

    wrap.querySelector('#cropCancelX').addEventListener('click', () => cleanupAndResolve(null));
    wrap.querySelector('#cropUseFullBtn').addEventListener('click', () => cleanupAndResolve(exportCropped(true)));
    wrap.querySelector('#cropConfirmBtn').addEventListener('click', () => cleanupAndResolve(exportCropped(false)));

    window.addEventListener('resize', draw);
    requestAnimationFrame(draw);
  });
}
