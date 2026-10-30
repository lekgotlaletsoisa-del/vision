'use strict';
(() => {
  const form = document.getElementById('process-form');
  const fileInput = document.getElementById('image');
  const cameraButton = document.getElementById('camera-button');
  const cameraDialog = document.getElementById('camera-dialog');
  const cameraVideo = document.getElementById('camera-video');
  const cameraShutter = document.getElementById('camera-shutter');
  const cameraClose = document.getElementById('camera-close');
  const cameraStatus = document.getElementById('camera-status');
  const status = document.getElementById('status');
  const results = document.getElementById('results');
  const preview = document.getElementById('preview-image');
  const fullSize = document.getElementById('full-size-link');
  const low = document.getElementById('cannyLow');
  const high = document.getElementById('cannyHigh');
  const button = document.getElementById('process-button');
  const showEdges = document.getElementById('show-edges');
  const showContours = document.getElementById('show-contours');
  const overlay = document.getElementById('geometry-overlay');
  const view = document.getElementById('preview-view');
  const selection = document.getElementById('contour-selection');
  const compare = document.getElementById('compare-raw');
  const annotations = ['show-boxes', 'show-centroids', 'show-ids', 'show-axes'].map(id => document.getElementById(id));
  const body = document.getElementById('measurements-body');
  let previewUrl;
  let previewRevision = 0;
  let processing = false;
  let resultUrls = null;
  let resultData = null;
  let exportUrl = null;
  let selectedFile = null;
  let cameraStream = null;
  let cameraRevision = 0;
  let capturing = false;
  const format = value => Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
  const coordinate = point => `${format(point.x)}, ${format(point.y)}`;

  function message(text, state = 'ready') {
    status.textContent = text;
    status.dataset.state = state;
  }
  function releaseResults() {
    if (resultUrls) Object.values(resultUrls).forEach(url => URL.revokeObjectURL(url));
    resultUrls = null;
    resultData = null;
    if (exportUrl) URL.revokeObjectURL(exportUrl);
    exportUrl = null;
  }
  function imageUrl(dataUrl) {
    // Encoding only: OpenCV supplies all boundaries and geometry.
    const [header, encoded] = dataUrl.split(',', 2);
    const type = header.startsWith('data:image/jpeg;') ? 'image/jpeg' : 'image/png';
    const bytes = Uint8Array.from(atob(encoded), character => character.charCodeAt(0));
    return URL.createObjectURL(new Blob([bytes], { type }));
  }
  function svgElement(tag, attributes) {
    const element = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
    return element;
  }
  function drawGeometry() {
    overlay.replaceChildren();
    // SVGElement does not consistently reflect the HTML hidden property.
    overlay.toggleAttribute('hidden', !resultData || view.value === 'mask');
    if (overlay.hasAttribute('hidden')) return;
    const data = resultData;
    const sx = data.previewWidth / data.width, sy = data.previewHeight / data.height;
    const scale = Math.min(sx, sy);
    overlay.setAttribute('viewBox', `0 0 ${data.previewWidth} ${data.previewHeight}`);
    // Map source pixel centres to the resized photograph. This is presentation scaling only.
    const group = svgElement('g', { transform: `scale(${sx} ${sy}) translate(0.5 0.5)` });
    const paths = new Map(data.contourDrawings.map(item => [item.id, item.path]));
    const [boxes, centroids, ids, axes] = annotations.map(input => input.checked);
    const stroke = (tag, attributes, colour) => {
      const common = { ...attributes, fill: 'none', 'vector-effect': 'non-scaling-stroke', 'stroke-linejoin': 'round' };
      group.append(svgElement(tag, { ...common, stroke: '#fff', 'stroke-width': 5 }),
        svgElement(tag, { ...common, stroke: colour, 'stroke-width': 2.5 }));
    };
    for (const item of data.contours) {
      if (selection.value && String(item.id) !== selection.value) continue;
      if (showContours.checked && paths.has(item.id)) stroke('path', { d: paths.get(item.id) }, '#19b83c');
      if (boxes) stroke('polygon', { points: item.rotatedRectangle.vertices.map(p => `${p.x},${p.y}`).join(' ') }, '#cf8b00');
      if (axes && item.orientationAxis) {
        const axis = item.orientationAxis;
        stroke('line', { x1: axis.start.x, y1: axis.start.y, x2: axis.end.x, y2: axis.end.y }, '#e64e20');
      }
      if (centroids) stroke('circle', { cx: item.centre.x, cy: item.centre.y, r: 5 / scale }, '#c4289c');
      if (ids) {
        const label = svgElement('text', { x: item.centre.x, y: item.centre.y, dx: 9 / scale, dy: -9 / scale,
          fill: '#125c2b', stroke: '#fff', 'stroke-width': 3 / scale, 'paint-order': 'stroke',
          'font-family': 'system-ui, sans-serif', 'font-size': 17 / scale, 'font-weight': 700 });
        label.textContent = `#${item.id}`;
        group.append(label);
      }
    }
    overlay.append(group);
  }
  function exportPreview(baseImage) {
    if (exportUrl) URL.revokeObjectURL(exportUrl);
    const data = resultData;
    const svg = svgElement('svg', { width: data.previewWidth, height: data.previewHeight,
      viewBox: `0 0 ${data.previewWidth} ${data.previewHeight}` });
    svg.append(svgElement('image', { href: baseImage, width: data.previewWidth, height: data.previewHeight }));
    if (!overlay.hasAttribute('hidden')) for (const child of overlay.children) svg.append(child.cloneNode(true));
    exportUrl = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' }));
    fullSize.href = exportUrl;
  }
  function updatePreview() {
    let url = previewUrl;
    let description = 'Source photograph';
    if (resultData) {
      drawGeometry();
      const mask = view.value === 'mask';
      url = mask ? resultUrls.mask : showEdges.checked ? resultUrls.edges : resultUrls.original;
      description = mask ? 'Active binary contour extraction mask' : 'Photograph with selected geometry overlays';
    } else if (resultUrls) {
      const edges = showEdges.checked;
      const contours = showContours.checked;
      url = edges && contours ? resultUrls.overlay : edges ? resultUrls.edges
        : contours ? resultUrls.contours : resultUrls.original;
      if (edges || contours) description = `Photograph with ${edges ? 'blue edges' : ''}${edges && contours ? ' and ' : ''}${contours ? 'green contours' : ''}`;
    }
    if (!resultData) { overlay.setAttribute('hidden', ''); overlay.replaceChildren(); }
    preview.hidden = !url;
    fullSize.hidden = !url;
    document.getElementById('image-placeholder').hidden = Boolean(url);
    preview.alt = description;
    if (url) { preview.src = url; fullSize.href = url; }
    else { preview.removeAttribute('src'); fullSize.removeAttribute('href'); }
    if (resultData) exportPreview(view.value === 'mask' ? resultData.boundaryMaskImage
      : showEdges.checked ? resultData.edgesImage : resultData.originalImage);
  }
  showEdges.addEventListener('change', updatePreview);
  showContours.addEventListener('change', updatePreview);
  for (const control of [...annotations, view, selection]) control.addEventListener('change', updatePreview);
  document.getElementById('original-view').addEventListener('click', () => {
    view.value = 'photo';
    for (const input of [showEdges, showContours, ...annotations]) input.checked = false;
    updatePreview();
  });

  function emptyMeasurements(text) {
    body.replaceChildren();
    const cell = body.insertRow().insertCell();
    cell.colSpan = 12;
    cell.className = 'empty-table';
    cell.textContent = text;
  }
  function validateThresholds() {
    high.setCustomValidity(Number(low.value) < Number(high.value) ? '' : 'High threshold must be greater than low threshold.');
    document.getElementById('low-value').textContent = low.value;
    document.getElementById('high-value').textContent = high.value;
  }
  for (const input of [low, high]) input.addEventListener('input', validateThresholds);
  form.addEventListener('input', event => {
    if (event.target !== fileInput && resultUrls && !processing) {
      message('Settings changed. Reprocess to update.');
    }
  });
  form.addEventListener('invalid', event => {
    const details = event.target.closest('details');
    if (details) details.open = true;
  }, true);
  validateThresholds();

  function stopCamera() {
    ++cameraRevision; // Invalidates a late permission response or pending frame encoding.
    if (cameraStream) cameraStream.getTracks().forEach(track => track.stop());
    cameraStream = null;
    cameraVideo.pause();
    cameraVideo.srcObject = null;
    cameraShutter.disabled = true;
    cameraButton.disabled = processing;
  }
  function closeCamera() {
    stopCamera();
    if (cameraDialog.open) cameraDialog.close();
  }
  function updateShutter() {
    cameraShutter.disabled = capturing || !cameraStream || cameraVideo.readyState < 2
      || !cameraVideo.videoWidth || !cameraVideo.videoHeight;
  }
  cameraVideo.addEventListener('loadeddata', updateShutter);
  cameraVideo.addEventListener('resize', updateShutter);
  cameraClose.addEventListener('click', closeCamera);
  cameraDialog.addEventListener('cancel', event => { event.preventDefault(); closeCamera(); });
  cameraDialog.addEventListener('close', () => { if (!cameraDialog.open) stopCamera(); });
  window.addEventListener('pagehide', closeCamera);
  window.addEventListener('pagehide', event => {
    if (event.persisted) return; // Keep previews valid when the browser caches this page for Back.
    releaseResults();
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = undefined;
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') closeCamera();
  });

  cameraButton.addEventListener('click', async () => {
    if (processing || capturing || cameraDialog.open) return;
    validateThresholds();
    if (!form.reportValidity()) return;
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !cameraDialog.showModal) {
      message('The in-page camera is unavailable. Open this site in a browser over HTTPS, or upload an image.', 'error');
      return;
    }
    const revision = ++cameraRevision;
    cameraStatus.textContent = 'Opening camera…';
    cameraShutter.disabled = true;
    cameraDialog.showModal();
    cameraButton.disabled = true;
    cameraClose.focus();
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { ideal: 'environment' },
          width: { ideal: 2560, max: 3840 }, height: { ideal: 1920, max: 2880 } }
      });
      if (revision !== cameraRevision || !cameraDialog.open) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      cameraStream = stream;
      cameraVideo.srcObject = stream;
      stream.getVideoTracks().forEach(track => track.addEventListener('ended', () => {
        if (cameraStream === stream) {
          closeCamera();
          message('Camera stopped. Open it again to take a photo.', 'error');
        }
      }));
      await cameraVideo.play();
      if (revision !== cameraRevision) return;
      cameraStatus.textContent = '';
      updateShutter();
    } catch (error) {
      if (revision !== cameraRevision) return;
      closeCamera();
      const explanation = error.name === 'NotAllowedError' || error.name === 'SecurityError'
        ? 'Allow camera access in your browser, then try again.'
        : error.name === 'NotFoundError' ? 'No camera was found. You can upload an image instead.'
          : 'The camera could not open. Close other apps using it, or upload an image.';
      message(explanation, 'error');
    }
  });

  cameraShutter.addEventListener('click', async () => {
    if (capturing || processing || cameraShutter.disabled || !cameraStream) return;
    const revision = cameraRevision;
    capturing = true;
    cameraShutter.disabled = true;
    cameraStatus.textContent = 'Capturing…';
    const canvas = document.createElement('canvas');
    try {
      canvas.width = cameraVideo.videoWidth;
      canvas.height = cameraVideo.videoHeight;
      if (!canvas.width || !canvas.height) throw new Error('Camera frame is not ready.');
      const context = canvas.getContext('2d', { alpha: false });
      if (!context) throw new Error('Camera frame could not be captured.');
      // Acquisition/encoding only: no filters, resizing, edge detection or geometry in JavaScript.
      context.drawImage(cameraVideo, 0, 0);
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.92));
      if (revision !== cameraRevision || !cameraDialog.open) return;
      if (!blob) throw new Error('Camera frame could not be encoded.');
      closeCamera(); // Release the device before uploading the single captured frame.
      fileInput.value = '';
      const photo = new File([blob], `camera-${Date.now()}.jpg`, { type: blob.type });
      if (await selectImage(photo)) form.requestSubmit();
    } catch {
      if (revision === cameraRevision && cameraDialog.open) {
        cameraStatus.textContent = 'Could not capture the photo. Tap the shutter again.';
      } else {
        message('Could not capture the photo. Open the camera and try again.', 'error');
      }
    } finally {
      canvas.width = 0;
      canvas.height = 0;
      capturing = false;
      updateShutter();
    }
  });
  fileInput.addEventListener('change', () => {
    const file = fileInput.files[0];
    if (file) void selectImage(file);
  });

  async function selectImage(file) {
    const revision = ++previewRevision;
    selectedFile = null;
    releaseResults();
    compare.disabled = true;
    document.getElementById('result-mode').textContent = '';
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = undefined;
    updatePreview();
    emptyMeasurements('No results yet.');
    document.getElementById('contour-count').textContent = '';
    document.getElementById('measurements-details').open = false;
    document.getElementById('process-label').textContent = 'Process image';
    if (file.size > Number(form.dataset.maxBytes)) {
      fileInput.value = '';
      button.disabled = false;
      message('Photo exceeds the size limit. Use a lower camera resolution or a smaller image.', 'error');
      return false;
    }
    button.disabled = true;
    try {
      // Browser decoding is for preview only; Java supplies processing and geometry.
      const bitmap = await createImageBitmap(file, { imageOrientation: 'none' });
      if (revision !== previewRevision) { bitmap.close(); return false; }
      const pixels = bitmap.width * bitmap.height;
      bitmap.close();
      if (pixels > Number(form.dataset.maxPixels)) {
        fileInput.value = '';
        message('Photo exceeds the pixel limit. Use a lower camera resolution or a smaller image.', 'error');
        return false;
      }
      previewUrl = URL.createObjectURL(file);
      selectedFile = file;
      updatePreview();
      message('Ready to process.');
      return true;
    } catch {
      if (revision !== previewRevision) return false;
      fileInput.value = '';
      message('Choose a valid JPEG or PNG.', 'error');
      return false;
    } finally {
      if (revision === previewRevision && !processing) button.disabled = false;
    }
  }

  function showMeasurements(data) {
    if (!data.contours.length) { emptyMeasurements('No contours retained.'); return; }
    body.replaceChildren();
    const fragment = document.createDocumentFragment();
    for (const item of data.contours) {
      const row = document.createElement('tr');
      const box = item.rotatedRectangle;
      const bounds = item.boundingRectangle;
      const values = [
        `#${item.id}`, format(item.area), format(item.perimeter),
        `${coordinate(item.centre)}${item.centroidFallback ? ' *' : ''}`,
        `${bounds.x}, ${bounds.y} / ${bounds.width} × ${bounds.height}`,
        `${format(box.width)} × ${format(box.height)}`,
        box.orientationDegrees === null ? 'Undefined' : format(box.orientationDegrees),
        item.aspectRatio == null ? 'Undefined' : format(item.aspectRatio),
        ...[item.circularity, item.solidity, item.extent].map(value => value == null ? '—' : format(value)),
        item.touchesImageBoundary == null ? '—' : item.touchesImageBoundary ? 'Yes' : 'No'
      ];
      for (const value of values) {
        const cell = document.createElement('td');
        cell.textContent = value;
        row.append(cell);
      }
      const detail = document.createElement('small');
      detail.textContent = `centre ${coordinate(box.centre)}`;
      row.cells[5].append(detail);
      const corners = document.createElement('details');
      const summary = document.createElement('summary');
      summary.textContent = '4 corners';
      corners.append(summary);
      box.vertices.forEach((vertex, index) => {
        const point = document.createElement('small');
        point.textContent = `${index + 1}: (${coordinate(vertex)})`;
        corners.append(point);
      });
      row.cells[5].append(corners);
      if (item.centroidFallback) row.cells[3].title = 'Zero polygon area: rotated-box centre used.';
      fragment.append(row);
    }
    body.append(fragment);
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    await processImage();
  });
  compare.addEventListener('click', () => processImage('CANNY'));
  async function processImage(modeOverride) {
    if (processing) return;
    if (!selectedFile) {
      message('Take a photo or upload an image first.', 'error');
      cameraButton.focus();
      return;
    }
    validateThresholds();
    if (!form.reportValidity()) return;
    const payload = new FormData(form);
    for (const name of ['maxContourArea', 'minAspectRatio', 'maxAspectRatio']) {
      if (!document.getElementById(name).value) payload.delete(name);
    }
    if (modeOverride) payload.set('processingMode', modeOverride);
    // Replace any empty/stale upload part when the current image came from the camera.
    payload.set('image', selectedFile, selectedFile.name || 'camera-photo.jpg');
    processing = true;
    for (const element of form.elements) element.disabled = true;
    results.setAttribute('aria-busy', 'true');
    document.getElementById('process-spinner').hidden = false;
    document.getElementById('process-label').textContent = 'Processing…';
    message('Processing…', 'busy');
    try {
      const response = await fetch(form.action, { method: 'POST', body: payload, cache: 'no-store' });
      let data;
      try { data = await response.json(); }
      catch { throw new Error(`Could not load results (HTTP ${response.status}).`); }
      if (!response.ok) throw new Error(data.message || `Processing failed (HTTP ${response.status}).`);
      // Await the overlaid image decoding before replacing the source preview.
      // A decode failure must be visible, rather than leaving the source photo labelled "Done".
      const nextUrls = {
        original: imageUrl(data.originalImage), edges: imageUrl(data.edgesImage),
        contours: imageUrl(data.contoursImage), overlay: imageUrl(data.overlayImage)
      };
      if (data.boundaryMaskImage) nextUrls.mask = imageUrl(data.boundaryMaskImage);
      try {
        const image = new Image();
        image.src = nextUrls.overlay;
        await image.decode();
      } catch {
        Object.values(nextUrls).forEach(url => URL.revokeObjectURL(url));
        throw new Error('The result image could not be displayed. Try a smaller photograph.');
      }
      releaseResults();
      resultUrls = nextUrls;
      resultData = Array.isArray(data.contourDrawings) ? data : null;
      selection.replaceChildren();
      const all = document.createElement('option');
      all.value = ''; all.textContent = 'All retained contours'; selection.append(all);
      for (const item of data.contours) {
        const option = document.createElement('option');
        option.value = String(item.id); option.textContent = `Contour #${item.id}`; selection.append(option);
      }
      selection.value = '';
      showEdges.checked = true;
      showContours.checked = true;
      updatePreview();
      if (previewUrl) { URL.revokeObjectURL(previewUrl); previewUrl = undefined; }
      document.getElementById('contour-count').textContent = `${data.contourCount} retained / ${data.detectedContourCount ?? data.contourCount} detected`;
      const mode = data.parameters?.boundary?.mode || 'CANNY';
      document.getElementById('result-mode').textContent = mode === 'CANNY' ? 'Raw Canny result' : `Experimental ${mode.toLowerCase()} result`;
      showMeasurements(data);
      message(data.contourCount ? 'Contours measured. Blue edges always show raw Canny; use Extraction mask to inspect the active contour source.'
        : 'No contours retained. Review the extraction mask, thresholds and filters.');
      if (window.matchMedia('(max-width: 991px)').matches) {
        results.scrollIntoView({ behavior: 'instant', block: 'start' });
      }
    } catch (error) {
      message(`${error.message}${resultUrls ? ' Previous result shown.' : ''}`, 'error');
    } finally {
      processing = false;
      for (const element of form.elements) element.disabled = false;
      compare.disabled = !selectedFile;
      results.setAttribute('aria-busy', 'false');
      document.getElementById('process-spinner').hidden = true;
      document.getElementById('process-label').textContent = 'Reprocess image';
    }
  }
})();
