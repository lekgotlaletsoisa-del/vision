'use strict';
// node src/test/javascript/retention.test.cjs — built-ins only; no browser packages required.
const assert = require('node:assert/strict');
const { File } = require('node:buffer');
const { harness } = require('./camera.test.cjs');
const settle = () => new Promise(setImmediate);

async function run() {
    let saved;
    const storage = { load: async () => saved, save: async value => { saved = value; } };
    const first = harness({ storage });
    await first.open(); await first.shutter(); await first.submit(); await settle();
    assert.equal(saved.photo.type, 'image/jpeg');
    assert.equal(saved.analysisCurrent, true);
    const originalResult = saved.result;
    first.get('show-boxes').checked = true;
    await first.get('show-boxes').emit('change');
    first.get('cannyLow').value = '35';
    await first.get('process-form').emit('input', { target: first.get('cannyLow') });
    await settle();
    assert.equal(saved.settings.cannyLow, '35');
    assert.equal(saved.display.checks[2], true);

    const reopened = harness({ storage }); await settle();
    assert.match(reopened.get('status').textContent, /photo and analysis restored/);
    assert.equal(reopened.requests.length, 0, 'Restoring must not send a new image or recompute analysis.');
    assert.equal(reopened.get('cannyLow').value, '35');
    assert.equal(reopened.get('show-boxes').checked, true);
    assert.equal(reopened.get('preview-image').hidden, false);
    await reopened.open(); await reopened.close(); await settle();
    assert.equal(saved.result, originalResult, 'Cancelling the camera preserves the result.');

    reopened.get('image').files = [new File([new Uint8Array(10_485_761)], 'too-big.png', { type: 'image/png' })];
    await reopened.get('image').emit('change'); await settle();
    assert.equal(saved.result, originalResult, 'An invalid new photo must not erase retained analysis.');
    assert.equal(saved.analysisCurrent, true);

    const secondPhoto = new File(['second frame'], 'second.png', { type: 'image/png' });
    reopened.get('image').files = [secondPhoto];
    await reopened.get('image').emit('change'); await settle();
    assert.equal(saved.photo.name, 'second.png');
    assert.equal(saved.result, originalResult);
    assert.equal(saved.analysisCurrent, false);
    assert.equal(reopened.get('preview-view').value, 'source');
    assert.equal(reopened.get('analysis-note').hidden, false);

    const pendingSnapshot = saved;
    const failed = harness({ storage, request: async () => ({ ok: false, json: async () => ({ message: 'Test processing failure.' }) }) });
    await settle();
    assert.match(failed.get('status').textContent, /Previous analysis/);
    assert.equal(failed.get('preview-view').value, 'source');
    await failed.get('process-form').emit('submit', { preventDefault() {} }); await settle();
    assert.match(failed.get('status').textContent, /Test processing failure/);
    assert.equal(saved.result, originalResult, 'A failed process must not replace the saved result.');
    assert.equal(saved.photo.name, 'second.png');

    const success = harness({ storage }); await settle();
    await success.get('process-form').emit('submit', { preventDefault() {} }); await settle();
    assert.equal(saved.analysisCurrent, true);
    assert.notEqual(saved.result, originalResult);
    assert.equal(success.requests[0].body.parts.get('image')[0].name, 'second.png');
    assert.equal(success.get('analysis-note').hidden, true);

    const full = harness({ storage: { load: async () => undefined, save: async () => { throw new Error('QuotaExceededError'); } } });
    await full.open(); await full.shutter(); await full.submit(); await settle();
    assert.match(full.get('retention-status').textContent, /unavailable or full/);
    assert.equal(full.get('preview-image').hidden, false, 'Storage failures must not prevent camera or processing.');
    const blocked = harness({ storage: { load: async () => { throw new Error('SecurityError'); }, save: async () => {} } });
    await settle();
    assert.match(blocked.get('retention-status').textContent, /could not be restored/);
    await blocked.open(); await blocked.shutter(); await blocked.submit();
    assert.equal(blocked.requests.length, 1);

    let finishLoad;
    const late = harness({ storage: { load: () => new Promise(resolve => { finishLoad = resolve; }), save: storage.save } });
    await late.open(); await late.shutter(); await late.submit();
    finishLoad(pendingSnapshot); await settle();
    assert.ok(saved.photo.name.startsWith('camera-'), 'A late restore must not overwrite a newly captured photo.');
    assert.equal(saved.analysisCurrent, true);

    let finishDecode, decodeCount = 0;
    const decoding = harness({ storage: { load: async () => pendingSnapshot, save: storage.save },
        decodeResult: () => ++decodeCount === 1 ? new Promise(resolve => { finishDecode = resolve; }) : Promise.resolve() });
    await settle();
    await decoding.open(); await decoding.shutter(); await decoding.submit();
    finishDecode(); await settle();
    assert.ok(saved.photo.name.startsWith('camera-'));
    assert.equal(saved.analysisCurrent, true, 'A late restored preview must not label a new result as previous.');
    assert.notEqual(saved.result, pendingSnapshot.result, 'Retain the new response rather than the late restored one.');
    assert.equal(decoding.get('cannyLow').value, '50', 'Late restoration must not replace the active controls.');
    console.log('Retention checks passed: reopening, settings/overlays, camera cancellation, invalid replacement, pending-photo labels, failed processing, successful replacement, blocked/full storage and late-restore races.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
