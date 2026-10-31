'use strict';
(() => {
  const get = id => document.getElementById(id);
  const form = get('process-form');
  const cameraButton = get('camera-button'), cameraDialog = get('camera-dialog');
  const cameraVideo = get('camera-video'), cameraShutter = get('camera-shutter');
  const cameraClose = get('camera-close'), cameraStatus = get('camera-status');
  const status = get('status'), results = get('results'), preview = get('preview-image');
  const overlay = get('geometry-overlay'), showEdges = get('show-edges'), showContours = get('show-contours');
  let cameraStream = null, cameraRevision = 0, capturing = false, processing = false;
  let revision = 0, displayRevision = 0, resultUrls = null, lastAnalysis = null;
  let selectedFile = null, resultLocation = null, saves = Promise.resolve();

  function message(text = '', state = 'ready') {
    status.textContent = text;
    status.dataset.state = state;
    status.hidden = !text;
  }
  function retainSession() {
    if (!selectedFile || !lastAnalysis || !window.VisionSessionStore) return;
    const snapshot = { version: 1, photo: selectedFile, photoName: selectedFile.name,
      result: lastAnalysis, analysisCurrent: true, resultLocation,
      display: { version: 2, edges: showEdges.checked, contours: showContours.checked } };
    // Serialize immutable snapshots so an earlier write cannot replace a newer capture.
    saves = saves.then(() => window.VisionSessionStore.save(snapshot)).catch(() => {
      message('Browser storage is unavailable. ' + (snapshot.resultLocation
        ? 'Your processed photo is still saved on the server.' : 'Your photo remains in this page.'), 'error');
    });
  }
  function releaseResults() {
    if (resultUrls) Object.values(resultUrls).forEach(url => URL.revokeObjectURL(url));
    resultUrls = null;
  }
  function imageUrl(dataUrl) {
    const [header, encoded] = dataUrl.split(',', 2);
    const bytes = Uint8Array.from(atob(encoded), character => character.charCodeAt(0));
    return URL.createObjectURL(new Blob([bytes], { type: header.startsWith('data:image/jpeg;') ? 'image/jpeg' : 'image/png' }));
  }
  function svgElement(tag, attributes) {
    const element = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
    return element;
  }
  function updatePreview() {
    overlay.replaceChildren();
    overlay.toggleAttribute('hidden', !lastAnalysis || !showContours.checked);
    if (!lastAnalysis) return;
    preview.src = showEdges.checked ? resultUrls.edges : resultUrls.original;
    preview.alt = 'Photograph' + (showEdges.checked ? ' with blue edges' : '') + (showContours.checked ? ' with green contours' : '');
    preview.hidden = false;
    get('image-placeholder').hidden = true;
    if (!showContours.checked) return;
    const data = lastAnalysis;
    overlay.setAttribute('viewBox', `0 0 ${data.previewWidth} ${data.previewHeight}`);
    // Presentation only: paths and dimensions come from Java, in original photograph coordinates.
    const group = svgElement('g', { transform: `scale(${data.previewWidth / data.width} ${data.previewHeight / data.height}) translate(0.5 0.5)` });
    for (const contour of data.contourDrawings) {
      const common = { d: contour.path, fill: 'none', 'vector-effect': 'non-scaling-stroke', 'stroke-linejoin': 'round' };
      group.append(svgElement('path', { ...common, stroke: '#fff', 'stroke-width': 5 }),
        svgElement('path', { ...common, stroke: '#19b83c', 'stroke-width': 2.5 }));
    }
    overlay.append(group);
  }
  for (const control of [showEdges, showContours]) control.addEventListener('change', () => {
    ++displayRevision;
    updatePreview();
    retainSession();
  });
  // Capture drives processing; the dashboard has no manual submit or parameter controls.
  form.addEventListener('submit', event => event.preventDefault());

  async function validatePhoto(photo) {
    if (photo.size > Number(form.dataset.maxBytes)) throw new Error('Photo exceeds the size limit. Use a lower camera resolution.');
    let bitmap;
    try { bitmap = await createImageBitmap(photo, { imageOrientation: 'none' }); }
    catch { throw new Error('The photo could not be read. Take another photo.'); }
    try {
      if (!bitmap.width || !bitmap.height || bitmap.width * bitmap.height > Number(form.dataset.maxPixels)) {
        throw new Error('Photo exceeds the pixel limit. Use a lower camera resolution.');
      }
    } finally { bitmap.close(); }
  }
  async function displayAnalysis(data, photo, location, expectedRevision) {
    const urls = {};
    try {
      if (!Array.isArray(data.contourDrawings) || ![data.width, data.height, data.previewWidth, data.previewHeight].every(n => Number.isFinite(n) && n > 0)) {
        throw new Error('Invalid result');
      }
      urls.original = imageUrl(data.originalImage);
      urls.edges = imageUrl(data.edgesImage);
      await Promise.all(Object.values(urls).map(async url => {
        const image = new Image(); image.src = url; await image.decode();
      }));
    } catch {
      Object.values(urls).forEach(url => URL.revokeObjectURL(url));
      throw new Error('The result could not be displayed. Take another photo.');
    }
    if (expectedRevision !== revision) {
      Object.values(urls).forEach(url => URL.revokeObjectURL(url));
      return false;
    }
    // Commit photograph and result together, only after both previews can be decoded.
    releaseResults();
    resultUrls = urls;
    selectedFile = photo;
    lastAnalysis = data;
    resultLocation = location;
    return true;
  }
  async function processImage(photo, savedParameters) {
    if (processing) return;
    const expectedRevision = ++revision;
    processing = true;
    cameraButton.disabled = true;
    results.setAttribute('aria-busy', 'true');
    message('Processing photo…', 'busy');
    try {
      await validatePhoto(photo);
      const payload = new FormData();
      payload.set('image', photo, photo.name || 'camera-photo.jpg');
      // Existing photos keep their baseline parameters on upgrade; all dashboard requests enable refinement.
      if (savedParameters && window.VisionServerResults) {
        for (const [name, value] of Object.entries(window.VisionServerResults.settings(savedParameters))) {
          if (value !== '' && value != null) payload.set(name, String(value));
        }
      }
      payload.set('detectionMode', 'DENOISED');
      const response = await fetch(form.action, { method: 'POST', body: payload, cache: 'no-store' });
      let data;
      try { data = await response.json(); }
      catch { throw new Error(`Could not load results (HTTP ${response.status}).`); }
      if (!response.ok) throw new Error(data.message || `Processing failed (HTTP ${response.status}).`);
      if (!await displayAnalysis(data, photo, response.headers?.get?.('Location') || null, expectedRevision)) return;
      updatePreview();
      message();
      retainSession();
    } catch (error) {
      message(error.message + (lastAnalysis ? ' Your previous photo is still available.' : ''), 'error');
    } finally {
      processing = false;
      cameraButton.disabled = cameraDialog.open;
      results.setAttribute('aria-busy', 'false');
    }
  }
  function validSnapshot(saved) {
    return saved?.version === 1 && saved.photo instanceof Blob;
  }
  async function restoreSession() {
    const expectedRevision = revision, expectedDisplayRevision = displayRevision;
    try {
      let saved;
      try { saved = await window.VisionSessionStore?.load(); } catch { /* Fall back to durable server state. */ }
      if (!validSnapshot(saved)) saved = await window.VisionServerResults?.loadLatest();
      if (!validSnapshot(saved) || expectedRevision !== revision) return;
      const photo = new File([saved.photo], saved.photoName || 'saved-photo.jpg', { type: saved.photo.type });
      if (expectedDisplayRevision === displayRevision) {
        const display = saved.display;
        if (display?.version === 2) {
          showEdges.checked = Boolean(display.edges); showContours.checked = Boolean(display.contours);
        } else if (Array.isArray(display?.checks)) {
          showEdges.checked = Boolean(display.checks[0]); showContours.checked = Boolean(display.checks[1]);
        }
      }
      if (!saved.analysisCurrent || !saved.result || !Array.isArray(saved.result.contourDrawings)) {
        // A pending old photo must never inherit measurements/parameters from a different photograph.
        await processImage(photo, saved.analysisCurrent ? saved.result?.parameters : undefined);
        return;
      }
      if (!await displayAnalysis(saved.result, photo, saved.resultLocation || null, expectedRevision)) return;
      updatePreview();
      retainSession();
      if (saved.result.parameters && window.VisionServerResults
          && (!resultLocation || saved.result.parameters.detectionMode !== 'DENOISED')) {
        await processImage(photo, saved.result.parameters);
      }
    } catch {
      if (expectedRevision === revision) message('Saved photo could not be restored. Take a photo to begin.', 'error');
    }
  }
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
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') closeCamera();
  });

  cameraButton.addEventListener('click', async () => {
    if (processing || capturing || cameraDialog.open) return;
    ++revision; // User acquisition takes precedence over a late saved-result restore.
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !cameraDialog.showModal) {
      message('The in-page camera is unavailable. Open this site in a browser over HTTPS.', 'error');
      return;
    }
    const attempt = ++cameraRevision;
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
      if (attempt !== cameraRevision || !cameraDialog.open) {
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
      if (attempt !== cameraRevision) return;
      cameraStatus.textContent = '';
      updateShutter();
    } catch (error) {
      if (attempt !== cameraRevision) return;
      closeCamera();
      const explanation = error.name === 'NotAllowedError' || error.name === 'SecurityError'
        ? 'Allow camera access in your browser, then try again.'
        : error.name === 'NotFoundError' ? 'No camera was found. Connect a camera and try again.'
          : 'The camera could not open. Close other apps using it and try again.';
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
      const photo = new File([blob], `camera-${Date.now()}.jpg`, { type: blob.type });
      await processImage(photo);
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
  void restoreSession();
})();
