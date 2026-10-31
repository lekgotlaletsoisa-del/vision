'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { Blob, File } = require('node:buffer');
const { harness } = require('./camera.test.cjs');
const settle = () => new Promise(setImmediate);
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/server-results.js'), 'utf8');
const id = '00000000-0000-4000-8000-000000000001';
const location = `/results/${id}`;
const jpg = 'data:image/jpeg;base64,AQ==';
const result = { width: 220, height: 160, previewWidth: 220, previewHeight: 160, contourCount: 0,
    detectedContourCount: 0, edgePixelCount: 0, boundaryPixelCount: 0, contours: [], contourDrawings: [],
    originalImage: jpg, edgesImage: jpg, contoursImage: jpg, overlayImage: jpg,
    parameters: { cannyLow: 35, cannyHigh: 120, minContourArea: 100, blurKernel: 3,
        filters: { maxContourArea: 4000, minCircularity: .2, excludeBoundary: false },
        boundary: { mode: 'MORPHOLOGY', closeKernel: 5, openKernel: 1, foreground: 'BRIGHT', shape: 'RECTANGLE' },
        tracing: { retrieval: 'TREE', chain: 'NONE', approximationPercent: 2 } } };
function moduleWith(fetch) {
    const window = {};
    new vm.Script(source).runInNewContext({ window, fetch });
    return window.VisionServerResults;
}
const serverModule = moduleWith(() => { throw new Error('Unexpected serverModule request'); });
const photo = new File(['original photo'], 'existing.jpg', { type: 'image/jpeg' });
const snapshot = extra => ({ version: 1, photo, photoName: photo.name, result, analysisCurrent: true,
    settings: serverModule.settings(result.parameters), display: { view: 'photo', selection: '', checks: [true, true, true, true, true, true], overlayDefaultsVersion: 1 }, ...extra });
async function run() {
    const reads = [];
    const loader = moduleWith(async url => {
        reads.push(url);
        if (reads.length === 1) return { ok: true, json: async () => ({ id, photoType: 'image/jpeg', result }) };
        return { ok: true, blob: async () => photo };
    });
    const loaded = await loader.loadLatest();
    assert.deepEqual(reads, ['/api/vision/results/latest', location + '/original']);
    assert.equal(loaded.resultLocation, location);
    assert.equal(loaded.settings.kernelShape, 'RECTANGLE');
    assert.equal(await moduleWith(async () => ({ status: 404 })).loadLatest(), null);
    await assert.rejects(moduleWith(async () => ({ ok: false, status: 500 })).loadLatest());

    let saved;
    const storage = { load: async () => saved, save: async value => { saved = value; } };
    const fresh = harness({ storage, server: { loadLatest: async () => loaded, settings: serverModule.settings } }); await settle();
    assert.equal(fresh.requests.length, 0, 'Restoring a server result must not rerun processing.');
    assert.match(fresh.get('status').textContent, /Server photo and analysis restored/);
    assert.equal(fresh.get('cannyLow').value, 35);
    assert.equal(fresh.get('saved-result-link').href, location + '?view=photo');
    assert.equal(saved.resultLocation, location);
    await fresh.get('load-server-result').emit('click'); await settle();
    assert.equal(fresh.requests.length, 0);

    saved = snapshot({ settings: { ...serverModule.settings(result.parameters), cannyLow: 90 } });
    const migrated = harness({ storage, response: result, server: { settings: serverModule.settings },
        request: async () => ({ ok: true, json: async () => result, headers: { get: () => location } }) });
    await settle(); await settle();
    assert.equal(migrated.requests.length, 1, 'Publish a browser-only matching result once.');
    assert.equal(migrated.requests[0].body.parts.get('cannyLow')[0], '35', 'Use the analysis parameters rather than unsent form edits.');
    assert.equal(migrated.get('cannyLow').value, 90, 'Preserve pending form edits.');
    assert.equal(saved.resultLocation, location);
    const reopened = harness({ storage, server: { settings: serverModule.settings } }); await settle();
    assert.equal(reopened.requests.length, 0, 'An already published session must not publish again.');
    saved = snapshot({ analysisCurrent: false, display: { view: 'source', selection: '', checks: [true, true, true, true, true, true], overlayDefaultsVersion: 1 } });
    const pending = harness({ storage, server: { settings: serverModule.settings } }); await settle();
    assert.equal(pending.requests.length, 0, 'Never publish previous measurements against a pending replacement photo.');
    assert.equal(pending.get('preview-view').value, 'source');

    saved = snapshot();
    const previous = saved;
    const failed = harness({ storage, server: { settings: serverModule.settings },
        request: async () => ({ ok: false, json: async () => ({ message: 'Storage unavailable.' }) }) });
    await settle(); await settle();
    assert.equal(saved, previous, 'A failed publication must preserve the local successful result.');
    assert.match(failed.get('status').textContent, /Storage unavailable/);

    let finishLoad;
    saved = undefined;
    const late = harness({ storage, server: { loadLatest: () => new Promise(resolve => { finishLoad = resolve; }), settings: serverModule.settings } });
    await settle(); await late.open(); await late.shutter(); await late.submit();
    finishLoad(loaded); await settle();
    assert.ok(saved.photo.name.startsWith('camera-'), 'A late server restore must not replace a new photo.');

    saved = snapshot({ resultLocation: location });
    let finishDecode, count = 0;
    const samePhoto = harness({ storage, response: result, decodeResult: () => ++count === 1 ? new Promise(resolve => { finishDecode = resolve; }) : Promise.resolve(),
        request: async () => ({ ok: true, json: async () => result, headers: { get: () => '/results/00000000-0000-4000-8000-000000000002' } }) });
    await settle();
    await samePhoto.get('process-form').emit('submit', { preventDefault() {} }); await settle();
    finishDecode(); await settle();
    assert.equal(saved.resultLocation, '/results/00000000-0000-4000-8000-000000000002', 'A late restore of the same photograph must not overwrite newer processing.');
    console.log('Server-result checks passed: immutable original URLs, cross-browser restoration without processing, one-time local publication, stored parameter fidelity, pending-photo separation, publication failures and late-restore races.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
