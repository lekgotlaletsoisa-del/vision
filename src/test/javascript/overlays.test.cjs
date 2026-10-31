'use strict';
const assert = require('node:assert/strict');
const { harness } = require('./camera.test.cjs');
const { result, snapshot, settle } = require('./fixtures.cjs');
async function run() {
    const page = harness({ response: result });
    await page.open(); await page.shutter();
    const svg = page.get('geometry-overlay');
    assert.equal(svg.viewBox, '0 0 1600 1200');
    assert.equal(svg.children[0].transform, 'scale(0.4 0.4) translate(0.5 0.5)');
    assert.equal(svg.children[0].children[0].d, result.contourDrawings[0].path);
    assert.equal(svg.children[0].children.length, 2, 'Only contour path and white halo; no IDs or geometry.');
    assert.ok(svg.children[0].children.every(item => item.tag === 'path'));
    assert.equal(page.urls.length, 2, 'Decode only original and edges, no unused preview copies.');
    const edgeUrl = page.get('preview-image').src;
    for (const edges of [false, true]) for (const contours of [false, true]) {
        page.get('show-edges').checked = edges;
        page.get('show-contours').checked = contours;
        await page.get('show-edges').emit('change');
        assert.equal(svg.hidden, !contours);
        assert.equal(page.get('preview-image').src === edgeUrl, edges);
        assert.equal(page.get('preview-image').hidden, false);
    }
    assert.equal(page.requests.length, 1, 'Display choices never reprocess or reupload.');
    const legacy = harness({ storage: { load: async () => snapshot({ display: { view: 'closed', selection: '15', checks: [false, true, true, true, true, true] } }), save: async () => {} } });
    await settle();
    assert.equal(legacy.get('show-edges').checked, false);
    assert.equal(legacy.get('show-contours').checked, true);
    assert.equal(legacy.get('geometry-overlay').children[0].children.length, 2, 'Ignore removed stage/selection/geometry preferences.');
    const blank = harness({ response: { ...result, contourDrawings: [], contours: [], contourCount: 0 } });
    await blank.open(); await blank.shutter();
    assert.equal(blank.get('preview-image').hidden, false, 'No contours is a valid photograph result.');
    console.log('Overlay checks passed: four combinations, path-only rendering, source coordinates, no requests/copies, legacy preferences and empty results.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
