'use strict';
(() => {
  /** Maps stored parameters to API fields for one-time legacy browser-result publication. */
  function settings(parameters) {
    const filters = parameters.filters || {};
    const boundary = parameters.boundary || {};
    const tracing = parameters.tracing || {};
    return {
      cannyLow: parameters.cannyLow, cannyHigh: parameters.cannyHigh,
      minContourArea: parameters.minContourArea, blurKernel: parameters.blurKernel,
      maxContourArea: filters.maxContourArea ?? '', minAspectRatio: filters.minAspectRatio ?? '',
      maxAspectRatio: filters.maxAspectRatio ?? '', minCircularity: filters.minCircularity ?? 0,
      minSolidity: filters.minSolidity ?? 0, excludeBoundary: Boolean(filters.excludeBoundary),
      processingMode: boundary.mode || 'CANNY', closeKernel: boundary.closeKernel ?? 3,
      openKernel: boundary.openKernel ?? 1, foreground: boundary.foreground || 'BRIGHT',
      kernelShape: boundary.shape || 'ELLIPSE', retrievalMode: tracing.retrieval || 'EXTERNAL',
      chainMode: tracing.chain || 'SIMPLE', approximationPercent: tracing.approximationPercent ?? 1
    };
  }

  /** Reads a durable server version and its matching original; neither request starts processing. */
  async function loadLatest() {
    const response = await fetch('/api/vision/results/latest', { cache: 'no-store' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error('Saved server analysis could not be loaded.');
    const saved = await response.json();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(saved.id)) {
      throw new Error('Saved server analysis has an invalid ID.');
    }
    const location = `/results/${saved.id}`;
    // Use the resolved immutable ID, not latest: another request may publish a newer photo meanwhile.
    const original = await fetch(`${location}/original`, { cache: 'no-store' });
    if (!original.ok) throw new Error('The matching saved photograph could not be loaded.');
    return {
      version: 1, photo: await original.blob(), photoName: saved.photoType === 'image/png' ? 'saved-photo.png' : 'saved-photo.jpg',
      result: saved.result, analysisCurrent: true, resultLocation: location,
      display: { version: 2, edges: true, contours: true }
    };
  }

  window.VisionServerResults = { loadLatest, settings };
})();
