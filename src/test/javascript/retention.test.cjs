'use strict';
const assert = require('node:assert/strict');
const { harness } = require('./camera.test.cjs');
const { result, snapshot, settle } = require('./fixtures.cjs');
async function run() {
    let saved;
    const storage = { load: async () => saved, save: async value => { saved = value; } };
    const capture = harness({ storage, response: result });
    await capture.open(); await capture.shutter(); await settle();
    assert.ok(saved.photo.name.startsWith('camera-'));
    assert.equal(saved.result, result);
    assert.equal(saved.analysisCurrent, true);
    capture.get('show-edges').checked = false;
    capture.get('show-contours').checked = false;
    await capture.get('show-edges').emit('change'); await settle();
    const reopen = harness({ storage }); await settle();
    assert.equal(reopen.requests.length, 0);
    assert.equal(reopen.get('show-edges').checked, false);
    assert.equal(reopen.get('show-contours').checked, false, 'Both off survives refresh.');
    const before = reopen.get('preview-image').src;
    const priorPhoto = saved.photo;
    await reopen.open(); await reopen.close();
    assert.equal(reopen.get('preview-image').src, before);
    assert.equal(saved.photo.name, priorPhoto.name); assert.equal(await saved.photo.text(), await priorPhoto.text());

    const failure = harness({ storage, request: async () => ({ ok: false, json: async () => ({ message: 'Processing unavailable.' }) }) });
    await settle(); const old = failure.get('preview-image').src;
    await failure.open(); await failure.shutter(); await settle();
    assert.equal(failure.get('preview-image').src, old);
    assert.equal(saved.photo.name, priorPhoto.name); assert.equal(await saved.photo.text(), await priorPhoto.text());
    assert.equal(saved.result, result);
    assert.match(failure.get('status').textContent, /previous photo/);

    const invalid = harness({ storage, decode: async () => ({ width: 9000, height: 9000, close() {} }) });
    await settle(); await invalid.open(); await invalid.shutter(); await settle();
    assert.equal(invalid.requests.length, 0, 'Validate limits before upload.');
    assert.equal(saved.photo.name, priorPhoto.name); assert.equal(await saved.photo.text(), await priorPhoto.text());
    assert.match(invalid.get('status').textContent, /pixel limit/);

    let decodes = 0;
    const corrupt = harness({ storage, decodeResult: async () => { if (++decodes > 2) throw new Error('Decode failed'); } });
    await settle(); const previous = corrupt.get('preview-image').src;
    await corrupt.open(); await corrupt.shutter(); await settle();
    assert.equal(corrupt.get('preview-image').src, previous, 'Undecodable result never replaces successful previews.');
    assert.equal(saved.photo.name, priorPhoto.name); assert.equal(await saved.photo.text(), await priorPhoto.text());

    const unavailable = harness({ response: result, storage: { load: async () => { throw new Error(); }, save: async () => { throw new Error(); } } });
    await unavailable.open(); await unavailable.shutter(); await settle();
    assert.equal(unavailable.get('preview-image').hidden, false);
    assert.match(unavailable.get('status').textContent, /storage is unavailable/);

    let finishLoad;
    const late = harness({ response: result, storage: { load: () => new Promise(resolve => { finishLoad = resolve; }), save: async value => { saved = value; } } });
    await late.open(); await late.shutter(); const captured = late.get('preview-image').src;
    finishLoad(snapshot()); await settle();
    assert.equal(late.get('preview-image').src, captured, 'Late local restore cannot replace a new capture.');
    console.log('Retention checks passed: capture/restore, both-off persistence, cancellation, failed/invalid/undecodable replacement, storage failure and late restoration.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
