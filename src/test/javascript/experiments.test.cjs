'use strict';
// Processing-stage and contour identity regressions; Java remains responsible for all geometry.
const assert = require('node:assert/strict');
const { harness } = require('./camera.test.cjs');
const settle = () => new Promise(setImmediate);
const png = 'data:image/png;base64,AQ==';
const jpg = 'data:image/jpeg;base64,Ag==';
const measurement = id => ({ id, area: id * 100, perimeter: 80, centre: { x: 40, y: 30 },
    centroidFallback: false, boundingRectangle: { x: 20, y: 20, width: 41, height: 21 },
    rotatedRectangle: { width: 40, height: 20, centre: { x: 40, y: 30 }, orientationDegrees: 0,
        vertices: [{ x: 20, y: 20 }, { x: 60, y: 20 }, { x: 60, y: 40 }, { x: 20, y: 40 }] },
    aspectRatio: 2, circularity: .7, solidity: 1, extent: .9, touchesImageBoundary: false });
const diagnostic = id => ({ id, sourceIndex: id + 3, nextIndex: -1, previousIndex: -1, firstChildIndex: -1,
    parentIndex: id === 2 ? 4 : -1, depth: id === 2 ? 1 : 0, hole: id === 2, pointCount: 120,
    approximationVertices: 4, approximationEpsilon: 1.6, approximationArea: 190,
    approximationPerimeter: 78, rotatedRectangleArea: 800, rotatedExtent: .25, approximationPath: `M${id} 20L60 40Z` });
const response = { width: 200, height: 160, previewWidth: 200, previewHeight: 160,
    originalImage: jpg, edgesImage: jpg, contoursImage: jpg, overlayImage: jpg, boundaryMaskImage: png,
    contourCount: 2, detectedContourCount: 5, edgePixelCount: 300, boundaryPixelCount: 350,
    contourSource: 'closed', parameters: { boundary: { mode: 'CANNY_CLOSED' }, tracing: { retrieval: 'TREE', chain: 'NONE' } },
    contours: [measurement(1), measurement(2)], contourDrawings: [{ id: 1, path: 'M20 20Z' }, { id: 2, path: 'M30 30Z' }],
    contourDiagnostics: [diagnostic(1), diagnostic(2)], stages: ['raw', 'closed', 'opened'].map((id, i) => ({ id, label: id + ' stage', image: png, pixelCount: 300 + i * 50 })) };

async function run() {
    let saved;
    const storage = { load: async () => saved, save: async value => { saved = value; } };
    const app = harness({ response, storage });
    await app.open(); await app.shutter(); await app.submit(); await settle();
    assert.match(app.get('result-mode').textContent, /closed.*TREE \/ NONE/);
    for (const id of ['raw', 'closed', 'opened']) {
        app.get('preview-view').value = id; await app.get('preview-view').emit('change');
        assert.equal(app.get('geometry-overlay').hidden, true);
        assert.equal(app.get('preview-image').alt, id + ' stage');
        assert.match(app.get('stage-note').textContent, new RegExp(id + ' stage.*foreground pixels.*closed'));
    }
    assert.equal(app.requests.length, 1, 'Switching cached processing stages must not send another image.');
    app.get('preview-view').value = 'photo'; await app.get('preview-view').emit('change');
    app.get('contour-selection').value = '2'; await app.get('contour-selection').emit('change');
    let rows = app.get('measurements-body').children[0].children;
    assert.equal(rows.length, 1, 'Selection must isolate measurements as well as overlays.');
    assert.equal(rows[0].cells[0].textContent, '#2');
    assert.match(rows[0].cells[12].textContent, /800 px² \/ 25%/);
    assert.match(rows[0].cells[13].textContent, /index 5.*hole trace.*parent 4/);
    app.get('show-approximation').checked = true; await app.get('show-approximation').emit('change');
    const paths = app.get('geometry-overlay').children[0].children.filter(item => item.tag === 'path');
    assert.equal(paths.length, 4, 'Only the selected trace and approximation, each with a halo.');
    assert.ok(paths.some(p => p.d === diagnostic(2).approximationPath));
    app.get('retrievalMode').value = 'TREE'; app.get('chainMode').value = 'NONE'; app.get('kernelShape').value = 'RECTANGLE';
    await app.get('process-form').emit('input', { target: app.get('kernelShape') });
    app.get('preview-view').value = 'closed'; await app.get('preview-view').emit('change'); await settle();
    const restored = harness({ response, storage }); await settle();
    assert.equal(restored.get('preview-view').value, 'closed');
    assert.equal(restored.get('contour-selection').value, '2');
    assert.equal(restored.get('show-approximation').checked, true);
    assert.equal(restored.get('kernelShape').value, 'RECTANGLE');
    assert.equal(restored.requests.length, 0);
    await app.get('compare-raw').emit('click'); await settle();
    assert.equal(app.get('contour-selection').value, '', 'Never silently transfer an ID to a different result.');
    rows = app.get('measurements-body').children[0].children;
    assert.equal(rows.length, 2);
    assert.match(app.get('status').textContent, /Selection reset/);
    assert.equal(app.requests[1].body.parts.get('image')[0], app.requests[0].body.parts.get('image')[0], 'Reprocess the retained original file.');
    console.log('Experiment UI checks passed: cached stages/counts, source labels, isolated geometry/measurements, approximation, hierarchy, retained experiment settings, stage restoration and result-local IDs.');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
