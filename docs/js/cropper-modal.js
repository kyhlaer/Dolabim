import { localizedText } from './constants.js';
import { svgIcon } from './icons.js';
import { escHtml } from './security.js';
import { decodeImageFileToCanvasImage, loadHtmlImage } from './studio-processor.js';

export async function openSingleDataUrlCropModal({ dataUrl, lang, modalRoot, showToast, registerModalBack, onComplete }) {
  if (!dataUrl) return;
  let img;
  try {
    img = await loadHtmlImage(dataUrl);
  } catch {
    showToast?.(localizedText(lang, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
    return;
  }
  let activeCancel = null;
  let closedByBack = false;
  const closeBackEntry = typeof registerModalBack === 'function'
    ? registerModalBack(() => {
      closedByBack = true;
      activeCancel?.();
    })
    : null;
  const chosenDataUrl = await showSingleCropModal({
    img,
    qLabel: null,
    lang,
    modalRoot,
    setCancel: (fn) => { activeCancel = fn; }
  });
  activeCancel = null;
  if (!closedByBack) closeBackEntry?.();
  if (chosenDataUrl) {
    onComplete?.(chosenDataUrl);
  }
}

export async function openCropperQueueModal({ files, lang, modalRoot, onCompleteAll, showToast, registerModalBack }) {
  const safeFiles = Array.from(files || []).slice(0, 12);
  const results = [];
  let decodeFailed = false;
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
    let img;
    const slowToastTimer = setTimeout(() => {
      showToast(localizedText(lang, 'Görsel hazırlanıyor...', 'Preparing image...'));
    }, 400);
    try {
      img = await decodeImageFileToCanvasImage(file, 2048);
    } catch {
      decodeFailed = true;
      showToast(localizedText(lang, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
      continue;
    } finally {
      clearTimeout(slowToastTimer);
    }

    if (closedByBack) return;
    const qLabel = safeFiles.length > 1 ? `${i + 1} / ${safeFiles.length}` : null;
    const chosenDataUrl = await showSingleCropModal({
      img,
      qLabel,
      lang,
      modalRoot,
      setCancel: (fn) => { activeCancel = fn; }
    });
    activeCancel = null;
    if (chosenDataUrl === null) {
      if (!closedByBack) closeBackEntry?.();
      return; // User cancelled batch
    }
    results.push(chosenDataUrl);
  }
  if (!closedByBack) closeBackEntry?.();
  if (results.length > 0) {
    onCompleteAll(results);
  } else if (!decodeFailed) {
    showToast(localizedText(lang, 'Bu dosya görsel olarak açılamadı', 'Could not open this image'));
  }
}


const clamp = (value, min, max) => Math.max(min, Math.min(max, value));

function cropRectAt(centerX, centerY, width, height) {
  const scale = Math.min(1, 1 / width, 1 / height);
  const w = width * scale;
  const h = height * scale;
  return {
    x: clamp(centerX - w / 2, 0, 1 - w),
    y: clamp(centerY - h / 2, 0, 1 - h),
    w,
    h
  };
}

function resizeCrop(crop, corner, dx, dy, imageRatio, aspect) {
  const [signX, signY] = corner;
  const anchorX = signX < 0 ? crop.x + crop.w : crop.x;
  const anchorY = signY < 0 ? crop.y + crop.h : crop.y;
  const maxWidth = signX < 0 ? anchorX : 1 - anchorX;
  const maxHeight = signY < 0 ? anchorY : 1 - anchorY;
  const minSize = 0.16;
  let w = crop.w + dx * signX;
  let h = crop.h + dy * signY;
  if (aspect !== null) {
    const heightPerWidth = imageRatio / aspect;
    const maxAspectWidth = Math.min(maxWidth, maxHeight / heightPerWidth);
    const minAspectWidth = Math.min(minSize, minSize / heightPerWidth);
    const desiredWidth = Math.abs(dx) * imageRatio >= Math.abs(dy) ? w : h / heightPerWidth;
    w = clamp(desiredWidth, Math.min(minAspectWidth, maxAspectWidth), maxAspectWidth);
    h = w * heightPerWidth;
  } else {
    w = clamp(w, Math.min(minSize, maxWidth), maxWidth);
    h = clamp(h, Math.min(minSize, maxHeight), maxHeight);
  }
  return { x: Math.min(anchorX, anchorX + signX * w), y: Math.min(anchorY, anchorY + signY * h), w, h };
}

const CROP_CORNERS = [[-1, -1], [1, -1], [-1, 1], [1, 1]];

function showSingleCropModal({ img, qLabel, lang, modalRoot, setCancel }) {
  return new Promise((resolve) => {
    const sourceWidth = img.naturalWidth || img.width;
    const sourceHeight = img.naturalHeight || img.height;
    let rotation = 0;
    let aspect = null;
    let crop = { x: 0.06, y: 0.06, w: 0.88, h: 0.88 };
    let settled = false;

    const title = localizedText(lang, 'Fotoğrafı Kırp', 'Crop Photo');
    const cancelLabel = localizedText(lang, 'İptal', 'Cancel');
    const rotateLabel = localizedText(lang, '90° Döndür', 'Rotate 90°');
    const wrap = document.createElement('div');
    wrap.className = 'modal-backdrop crop-backdrop';
    wrap.innerHTML = `
      <section class="crop-panel" role="dialog" aria-modal="true" aria-labelledby="cropTitle">
        <header class="crop-header">
          <button type="button" class="crop-icon" id="cropCancelX" aria-label="${cancelLabel}" title="${cancelLabel}">${svgIcon('close', 24)}</button>
          <div>
            <h3 id="cropTitle">${title}</h3>
            ${qLabel ? `<div class="crop-count">${escHtml(qLabel)}</div>` : ''}
          </div>
          <button type="button" class="crop-icon" id="cropRotateBtn" aria-label="${rotateLabel}" title="${rotateLabel}">${svgIcon('rotateRight', 24)}</button>
        </header>
        <div class="crop-preview" id="cropStage">
          <canvas id="cropCanvas"></canvas>
        </div>
        <footer class="crop-controls">
          <div class="crop-ratios" role="group" aria-label="${localizedText(lang, 'Kırpma oranı', 'Crop ratio')}">
            <button type="button" data-ratio="free" aria-pressed="true">${localizedText(lang, 'Serbest', 'Free')}</button>
            <button type="button" data-ratio="1" aria-pressed="false">1:1</button>
            <button type="button" data-ratio="0.75" aria-pressed="false">3:4</button>
          </div>
          <div class="crop-actions">
            <button type="button" id="cropUseFullBtn">${localizedText(lang, 'Kırpmadan Kullan', 'Use Full')}</button>
            <button type="button" id="cropConfirmBtn">${svgIcon('check', 16)}${localizedText(lang, 'Kırp & Ekle', 'Crop & Add')}</button>
          </div>
        </footer>
      </section>
    `;
    modalRoot.appendChild(wrap);

    const stage = wrap.querySelector('#cropStage');
    const canvas = wrap.querySelector('#cropCanvas');
    const ctx = canvas.getContext('2d');
    let imgBox = { x: 0, y: 0, w: 0, h: 0 };
    const dimensions = () => rotation % 2
      ? { width: sourceHeight, height: sourceWidth }
      : { width: sourceWidth, height: sourceHeight };

    const drawPhoto = (context, x, y, width, height) => {
      context.save();
      context.translate(x + width / 2, y + height / 2);
      context.rotate(rotation * Math.PI / 2);
      const w = rotation % 2 ? height : width;
      const h = rotation % 2 ? width : height;
      context.drawImage(img, -w / 2, -h / 2, w, h);
      context.restore();
    };

    const draw = () => {
      const { width: cw, height: ch } = stage.getBoundingClientRect();
      if (cw <= 32 || ch <= 32) {
        imgBox = { x: 0, y: 0, w: 0, h: 0 };
        canvas.width = 0;
        canvas.height = 0;
        return;
      }
      const dpr = window.devicePixelRatio || 1;
      const pixelWidth = Math.round(cw * dpr);
      const pixelHeight = Math.round(ch * dpr);
      if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
        canvas.width = pixelWidth;
        canvas.height = pixelHeight;
      }
      ctx.setTransform(pixelWidth / cw, 0, 0, pixelHeight / ch, 0, 0);
      ctx.clearRect(0, 0, cw, ch);

      const { width, height } = dimensions();
      const scale = Math.min((cw - 32) / width, (ch - 32) / height);
      const w = width * scale;
      const h = height * scale;
      const x = (cw - w) / 2;
      const y = (ch - h) / 2;
      imgBox = { x, y, w, h };
      drawPhoto(ctx, x, y, w, h);

      const left = x + crop.x * w;
      const top = y + crop.y * h;
      const cropW = crop.w * w;
      const cropH = crop.h * h;
      ctx.fillStyle = 'rgba(0, 0, 0, 0.56)';
      ctx.beginPath();
      ctx.rect(x, y, w, h);
      ctx.rect(left, top, cropW, cropH);
      ctx.fill('evenodd');

      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.strokeRect(left, top, cropW, cropH);

      ctx.strokeStyle = 'rgba(255, 255, 255, 0.35)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let i = 1; i <= 2; i++) {
        const gx = left + cropW * i / 3;
        const gy = top + cropH * i / 3;
        ctx.moveTo(gx, top);
        ctx.lineTo(gx, top + cropH);
        ctx.moveTo(left, gy);
        ctx.lineTo(left + cropW, gy);
      }
      ctx.stroke();

      const accent = getComputedStyle(wrap).getPropertyValue('--accent');
      for (const [signX, signY] of CROP_CORNERS) {
        const hx = signX < 0 ? left : left + cropW;
        const hy = signY < 0 ? top : top + cropH;
        ctx.fillStyle = '#fff';
        ctx.beginPath();
        ctx.arc(hx, hy, 7, 0, 2 * Math.PI);
        ctx.fill();
        ctx.fillStyle = accent;
        ctx.beginPath();
        ctx.arc(hx, hy, 7 * 0.45, 0, 2 * Math.PI);
        ctx.fill();
      }
    };

    let drag = null;
    canvas.addEventListener('pointerdown', (e) => {
      if (drag || e.button !== 0 || !imgBox.w || !imgBox.h) return;
      const r = canvas.getBoundingClientRect();
      const px = e.clientX - r.left;
      const py = e.clientY - r.top;
      const left = imgBox.x + crop.x * imgBox.w;
      const top = imgBox.y + crop.y * imgBox.h;
      const right = left + crop.w * imgBox.w;
      const bottom = top + crop.h * imgBox.h;
      const corner = CROP_CORNERS.find(([sx, sy]) =>
        Math.abs(px - (sx < 0 ? left : right)) + Math.abs(py - (sy < 0 ? top : bottom)) < 48
      );
      if (!corner && !(px >= left && px <= right && py >= top && py <= bottom)) return;
      drag = { corner, pointerId: e.pointerId, x: e.clientX, y: e.clientY };
      canvas.setPointerCapture(e.pointerId);
    });

    canvas.addEventListener('pointermove', (e) => {
      if (e.pointerId !== drag?.pointerId || !imgBox.w || !imgBox.h) return;
      const dx = (e.clientX - drag.x) / imgBox.w;
      const dy = (e.clientY - drag.y) / imgBox.h;
      drag.x = e.clientX;
      drag.y = e.clientY;
      if (drag.corner) {
        const { width, height } = dimensions();
        crop = resizeCrop(crop, drag.corner, dx, dy, width / height, aspect);
      } else {
        crop = cropRectAt(crop.x + crop.w / 2 + dx, crop.y + crop.h / 2 + dy, crop.w, crop.h);
      }
      draw();
    });

    const endDrag = (e) => {
      if (e.pointerId === drag?.pointerId) drag = null;
    };
    canvas.addEventListener('pointerup', endDrag);
    canvas.addEventListener('pointercancel', endDrag);
    canvas.addEventListener('lostpointercapture', endDrag);

    const ratioButtons = wrap.querySelectorAll('[data-ratio]');
    ratioButtons.forEach((btn) => {
      btn.addEventListener('click', () => {
        if (btn.getAttribute('aria-pressed') === 'true') return;
        ratioButtons.forEach((button) => button.setAttribute('aria-pressed', button === btn));
        drag = null;
        aspect = btn.dataset.ratio === 'free' ? null : Number(btn.dataset.ratio);
        if (aspect !== null) {
          const { width, height } = dimensions();
          const heightPerWidth = width / height / aspect;
          const w = Math.min(clamp(crop.w, 0.25, 0.88), 0.88 / heightPerWidth);
          crop = cropRectAt(crop.x + crop.w / 2, crop.y + crop.h / 2, w, w * heightPerWidth);
        }
        draw();
      });
    });

    wrap.querySelector('#cropRotateBtn').addEventListener('click', () => {
      drag = null;
      const { width, height } = dimensions();
      if (aspect === null) {
        crop = { x: 1 - (crop.y + crop.h), y: crop.x, w: crop.h, h: crop.w };
      } else {
        const imageRatio = width / height;
        const w = aspect === 1 ? crop.h : crop.w * imageRatio;
        const h = aspect === 1 ? crop.w : crop.h / imageRatio;
        crop = cropRectAt(1 - (crop.y + crop.h / 2), crop.x + crop.w / 2, w, h);
      }
      rotation = (rotation + 1) % 4;
      draw();
    });

    const exportCropped = (fullImage) => {
      const { width, height } = dimensions();
      const scale = Math.min(1, 1440 / Math.max(width, height));
      let left = 0;
      let top = 0;
      let cropWidth = width;
      let cropHeight = height;
      const [ratioW, ratioH] = aspect === 1 ? [1, 1] : [3, 4];
      const maxUnits = Math.floor(Math.min(width / ratioW, height / ratioH));
      const locked = !fullImage && aspect !== null && maxUnits > 0;
      if (locked) {
        const units = clamp(Math.round(crop.w * width / ratioW), 1, maxUnits);
        cropWidth = units * ratioW;
        cropHeight = units * ratioH;
        left = clamp(Math.round((crop.x + crop.w / 2) * width - cropWidth / 2), 0, width - cropWidth);
        top = clamp(Math.round((crop.y + crop.h / 2) * height - cropHeight / 2), 0, height - cropHeight);
      } else if (!fullImage) {
        left = clamp(Math.round(crop.x * width), 0, width - 1);
        top = clamp(Math.round(crop.y * height), 0, height - 1);
        cropWidth = clamp(Math.round((crop.x + crop.w) * width), left + 1, width) - left;
        cropHeight = clamp(Math.round((crop.y + crop.h) * height), top + 1, height) - top;
      }
      const out = document.createElement('canvas');
      const scaledUnits = Math.max(1, Math.round(cropWidth * scale / ratioW));
      out.width = locked ? scaledUnits * ratioW : Math.max(1, Math.round(cropWidth * scale));
      out.height = locked ? scaledUnits * ratioH : Math.max(1, Math.round(cropHeight * scale));
      const context = out.getContext('2d');
      context.fillStyle = '#fff';
      context.fillRect(0, 0, out.width, out.height);
      context.scale(out.width / cropWidth, out.height / cropHeight);
      drawPhoto(context, -left, -top, width, height);
      return out.toDataURL('image/jpeg', 0.90);
    };

    const observer = new ResizeObserver(draw);
    const focusedBefore = document.activeElement;
    const cancelButton = wrap.querySelector('#cropCancelX');
    const cleanupAndResolve = (val) => {
      if (settled) return;
      settled = true;
      observer.disconnect();
      wrap.remove();
      focusedBefore?.focus({ preventScroll: true });
      resolve(val);
    };
    setCancel?.(() => cleanupAndResolve(null));
    cancelButton.addEventListener('click', () => cleanupAndResolve(null));
    wrap.querySelector('#cropUseFullBtn').addEventListener('click', () => cleanupAndResolve(exportCropped(true)));
    wrap.querySelector('#cropConfirmBtn').addEventListener('click', () => cleanupAndResolve(exportCropped(false)));

    wrap.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        cleanupAndResolve(null);
      } else if (event.key === 'Tab') {
        const buttons = wrap.querySelectorAll('button');
        const first = buttons[0];
        const last = buttons[buttons.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    });

    cancelButton.focus({ preventScroll: true });
    observer.observe(stage);
    draw();
  });
}
