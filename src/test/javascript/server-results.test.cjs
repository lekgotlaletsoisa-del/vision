'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { harness } = require('./camera.test.cjs');
const { result, photo, location, snapshot, settle } = require('./fixtures.cjs');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/server-results.js'), 'utf8');
function moduleWith(fetch) {
    const window = {};
    new vm.Script(source).runInNewContext({ window, fetch });
    return window.VisionServerResults;
}
async function run() {
    const reads = [];
    const loader = moduleWith(async url => {
        reads.push(url);
        return reads.length === 1 ? { ok: true, json: async () => ({ id: location.split('/')[2], photoType: 'image/jpeg', result }) }
            : { ok: true, blob: async () => photo };
    });
    const loaded = await loader.loadLatest();
    assert.deepEqual(reads, ['/api/vision/results/latest', location + '/original']);
    assert.equal(loaded.resultLocation, location);
    assert.equal(await moduleWith(async () => ({ status: 404 })).loadLatest(), null);
    await assert.rejects(moduleWith(async () => ({ ok: false, status: 500 })).loadLatest());
    await assert.rejects(moduleWith(async () => ({ ok: true, json: async () => ({ id: '../bad' }) })).loadLatest());
    let saved;
    const storage = { load: async () => saved, save: async value => { saved = value; } };
    const fresh = harness({ storage, server: { loadLatest: async () => loaded, settings: loader.settings } }); await settle();
    assert.equal(fresh.requests.length, 0, 'Fresh browser loads latest without reprocessing.');
    assert.equal(saved.resultLocation, location);
    assert.equal(fresh.get('preview-image').hidden, false);

    saved = snapshot({ resultLocation: null, settings: { cannyLow: 90 } });
    const migrate = harness({ storage, response: result, server: { settings: loader.settings },
        request: async () => ({ ok: true, json: async () => result, headers: { get: () => location } }) });
    await settle(); await settle();
    assert.equal(migrate.requests.length, 1);
    assert.equal(migrate.requests[0].body.parts.get('cannyLow')[0], '35', 'Publish matching analysis settings, never stale unsent form settings.');
    assert.equal(saved.resultLocation, location);
    const reopened = harness({ storage, server: { settings: loader.settings } }); await settle();
    assert.equal(reopened.requests.length, 0, 'Publication happens once.');

    saved = snapshot({ result: { ...result, parameters: { ...result.parameters, detectionMode: 'STANDARD' } } });
    const upgraded = harness({ storage, response: result, server: { settings: loader.settings } });
    await settle(); await settle();
    assert.equal(upgraded.requests.length, 1, 'Older matching analysis upgrades once without a new photo.');
    assert.equal(upgraded.requests[0].body.parts.get('detectionMode')[0], 'ENHANCED');
    assert.equal(saved.result.parameters.detectionMode, 'ENHANCED');

    saved = snapshot({ analysisCurrent: false });
    const pending = harness({ storage, response: result, server: { settings: loader.settings } }); await settle();
    assert.equal(pending.requests.length, 1, 'Pending old photo is processed automatically.');
    assert.deepEqual([...pending.requests[0].body.parts.keys()], ['image', 'detectionMode'], 'Never reuse previous-photo measurements or parameters.');

    saved = snapshot({ resultLocation: null });
    const failed = harness({ storage, server: { settings: loader.settings }, request: async () => ({ ok: false, json: async () => ({ message: 'Server storage unavailable.' }) }) });
    await settle(); await settle();
    assert.equal(await saved.photo.text(), await photo.text()); assert.equal(saved.result, result);
    assert.match(failed.get('status').textContent, /storage unavailable/);

    saved = snapshot({ result: { ...result, parameters: { ...result.parameters, detectionMode: 'STANDARD' } } });
    const legacyResult = saved.result;
    const refusedUpgrade = harness({ storage, server: { settings: loader.settings }, request: async () => ({ ok: false, json: async () => ({ message: 'Upgrade unavailable.' }) }) });
    await settle(); await settle();
    assert.equal(saved.result, legacyResult, 'Failed upgrades preserve a successful matching old analysis.');
    assert.equal(await saved.photo.text(), await photo.text());
    assert.match(refusedUpgrade.get('status').textContent, /previous photo/);

    let finishLoad;
    saved = undefined;
    const late = harness({ storage, response: result, server: { loadLatest: () => new Promise(resolve => { finishLoad = resolve; }) } });
    await settle(); await late.open(); await late.shutter();
    const captured = late.get('preview-image').src;
    finishLoad(loaded); await settle();
    assert.equal(late.get('preview-image').src, captured);
    assert.ok(saved.photo.name.startsWith('camera-'));

    saved = snapshot();
    let finishDecode, count = 0;
    const racing = harness({ storage, response: result, decodeResult: () => ++count === 1 ? new Promise(resolve => { finishDecode = resolve; }) : Promise.resolve() });
    await settle(); await racing.open(); await racing.shutter();
    const newest = racing.get('preview-image').src;
    finishDecode(); await settle();
    assert.equal(racing.get('preview-image').src, newest, 'Late result decoding cannot replace a new capture.');
    console.log('Server-result checks passed: immutable IDs, fresh-browser restoration, legacy publication, saved parameters, pending photos, failures and restore/decode races.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
