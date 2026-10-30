'use strict';
// Optional frontend regression checks: node src/test/javascript/camera.test.cjs
// No npm packages or camera device are required; browser APIs are simulated.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { Blob, File, Buffer } = require('node:buffer');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/vision.js'), 'utf8');

class Element {
    constructor(tag = 'div') {
        Object.assign(this, { tag, listeners: {}, children: [], dataset: {}, checked: false,
            disabled: false, value: '', files: [], open: false, hidden: false,
            readyState: 2, videoWidth: 1280, videoHeight: 720 });
    }
    addEventListener(name, fn) { (this.listeners[name] ||= []).push(fn); }
    async emit(name, event = {}) { for (const fn of this.listeners[name] || []) await fn(event); }
    append(...items) { this.children.push(...items); }
    replaceChildren(...items) { this.children = items; }
    insertRow() { const row = new Element('tr'); this.append(row); return row; }
    insertCell() { const cell = new Element('td'); this.append(cell); return cell; }
    get cells() { return this.children; }
    setCustomValidity(value) { this.validationMessage = value; }
    setAttribute(name, value) { this[name] = value; }
    removeAttribute(name) { delete this[name]; }
    reportValidity() { return true; }
    closest() { return null; }
    scrollIntoView() { this.scrolled = true; }
    focus() { this.focused = true; }
    showModal() { this.open = true; }
    close() { this.open = false; }
    async play() { this.played = true; }
    pause() { this.paused = true; }
}

function harness(options = {}) {
    const elements = new Map();
    const get = id => {
        if (!elements.has(id)) elements.set(id, new Element());
        return elements.get(id);
    };
    const requests = [], streams = [], canvasCopies = [], urls = [];
    const track = new Element();
    track.stops = 0;
    track.stop = () => { track.stops++; };
    const stream = { getTracks: () => [track], getVideoTracks: () => [track] };
    get('cannyLow').value = '50';
    get('cannyHigh').value = '150';
    get('process-form').dataset = { maxBytes: '10485760', maxPixels: '16000000' };
    get('process-form').elements = ['image', 'camera-button', 'cannyLow', 'cannyHigh', 'process-button'].map(get);
    let submission = Promise.resolve();
    get('process-form').requestSubmit = () => { submission = get('process-form').emit('submit', { preventDefault() {} }); };
    const response = { originalImage: 'data:image/jpeg;base64,AQ==', edgesImage: 'data:image/jpeg;base64,Ag==',
        contoursImage: 'data:image/jpeg;base64,Aw==', overlayImage: 'data:image/jpeg;base64,BA==',
        contourCount: 0, edgePixelCount: 40, contours: [] };
    const document = new Element();
    document.visibilityState = 'visible';
    document.getElementById = get;
    document.createDocumentFragment = () => new Element();
    document.createElement = tag => {
        const element = new Element(tag);
        if (tag === 'canvas') {
            element.getContext = () => ({ drawImage: (...args) => canvasCopies.push(args) });
            element.toBlob = callback => options.encode
                ? options.encode(callback)
                : callback(new Blob(['captured-frame'], { type: 'image/jpeg' }));
        }
        return element;
    };
    const window = new Element();
    window.isSecureContext = options.secure !== false;
    window.matchMedia = () => ({ matches: true });
    const context = { document, window, Blob, File, Uint8Array, Date,
        navigator: { mediaDevices: { getUserMedia: async constraints => {
            streams.push(constraints);
            return options.open ? options.open(stream) : stream;
        } } },
        URL: { createObjectURL: blob => { const url = `blob:${urls.length}`; urls.push({ url, blob }); return url; }, revokeObjectURL() {} },
        atob: data => Buffer.from(data, 'base64').toString('binary'),
        createImageBitmap: async () => ({ width: 1280, height: 720, close() {} }),
        FormData: class { constructor() { this.parts = new Map(); } set(name, ...value) { this.parts.set(name, value); } },
        Image: class { async decode() {} },
        fetch: async (url, request) => { requests.push(request); return { ok: true, json: async () => response }; }
    };
    new vm.Script(source, { filename: 'vision.js' }).runInNewContext(context);
    return { get, requests, streams, track, stream, canvasCopies, urls, document, window,
        submit: () => submission, open: () => get('camera-button').emit('click'),
        shutter: () => get('camera-shutter').emit('click'), close: () => get('camera-close').emit('click') };
}

async function run() {
    const capture = harness();
    await capture.open();
    assert.equal(capture.get('camera-dialog').open, true);
    assert.equal(capture.get('camera-shutter').disabled, false);
    assert.equal(capture.streams[0].audio, false);
    assert.equal(capture.streams[0].video.facingMode.ideal, 'environment');
    await capture.shutter();
    await capture.submit();
    assert.equal(capture.canvasCopies.length, 1);
    assert.equal(capture.canvasCopies[0][0], capture.get('camera-video'));
    assert.equal(capture.requests.length, 1, 'One shutter press must submit without a confirmation step.');
    const photo = capture.requests[0].body.parts.get('image')[0];
    assert.equal(photo.type, 'image/jpeg');
    assert.ok(photo.name.startsWith('camera-'));
    assert.ok(capture.track.stops > 0);
    assert.equal(capture.get('camera-video').srcObject, null);
    assert.equal(capture.get('camera-dialog').open, false);
    assert.equal(capture.get('show-edges').checked, true);
    assert.equal(capture.get('show-contours').checked, true);
    await capture.shutter();
    assert.equal(capture.requests.length, 1, 'A second click after capture must not submit again.');
    await capture.get('process-form').emit('submit', { preventDefault() {} });
    assert.equal(capture.requests[1].body.parts.get('image')[0], photo, 'Reprocessing must reuse the frame.');

    const cancelled = harness();
    await cancelled.open();
    await cancelled.close();
    assert.equal(cancelled.requests.length, 0);
    assert.ok(cancelled.track.stops > 0);
    assert.equal(cancelled.get('camera-video').srcObject, null);

    let grantPermission;
    const pending = harness({ open: stream => new Promise(resolve => { grantPermission = () => resolve(stream); }) });
    const opening = pending.open();
    await pending.close();
    grantPermission();
    await opening;
    assert.ok(pending.track.stops > 0, 'A permission response arriving after close must release the camera.');
    assert.equal(pending.get('camera-video').srcObject, null);
    assert.equal(pending.requests.length, 0);

    const denied = harness({ open: async () => { throw Object.assign(new Error(), { name: 'NotAllowedError' }); } });
    await denied.open();
    assert.equal(denied.get('camera-dialog').open, false);
    assert.match(denied.get('status').textContent, /Allow camera access/);
    assert.equal(denied.requests.length, 0);

    const insecure = harness({ secure: false });
    await insecure.open();
    assert.equal(insecure.streams.length, 0);
    assert.match(insecure.get('status').textContent, /HTTPS/);

    const background = harness();
    await background.open();
    background.document.visibilityState = 'hidden';
    await background.document.emit('visibilitychange');
    assert.ok(background.track.stops > 0);
    assert.equal(background.get('camera-dialog').open, false);
    assert.equal(background.requests.length, 0);

    const warming = harness();
    warming.get('camera-video').readyState = 1;
    warming.get('camera-video').videoWidth = 0;
    await warming.open();
    assert.equal(warming.get('camera-shutter').disabled, true);
    await warming.shutter();
    assert.equal(warming.requests.length, 0);
    await warming.close();

    let finishEncoding;
    const encoding = harness({ encode: callback => { finishEncoding = callback; } });
    await encoding.open();
    const firstClick = encoding.shutter();
    await encoding.shutter();
    assert.equal(encoding.canvasCopies.length, 1, 'Rapid shutter clicks must produce only one frame.');
    await encoding.close();
    finishEncoding(new Blob(['frame'], { type: 'image/jpeg' }));
    await firstClick;
    assert.equal(encoding.requests.length, 0, 'Closing during encoding must cancel the pending capture.');
    assert.ok(encoding.track.stops > 0);
    console.log('Camera checks passed: automatic submission, duplicate protection, reprocessing, cancellation, late permission, denied permission, HTTPS, background cleanup and frame readiness.');
}

run();
