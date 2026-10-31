'use strict';
const { File } = require('node:buffer');
const jpg = 'data:image/jpeg;base64,AQ==';
const result = { width: 4000, height: 3000, previewWidth: 1600, previewHeight: 1200,
    originalImage: jpg, edgesImage: 'data:image/jpeg;base64,Ag==', contourCount: 1,
    contours: [{ id: 1 }], contourDrawings: [{ id: 1, path: 'M1000 800L1400 800L1400 1000Z' }],
    parameters: { detectionMode: 'ENHANCED', cannyLow: 35, cannyHigh: 120, minContourArea: 100, blurKernel: 3,
        boundary: { mode: 'MORPHOLOGY', closeKernel: 5, openKernel: 1, shape: 'RECTANGLE' } } };
const photo = new File(['original-photo'], 'existing.jpg', { type: 'image/jpeg' });
const location = '/results/00000000-0000-4000-8000-000000000001';
const snapshot = extra => ({ version: 1, photo, photoName: photo.name, result,
    analysisCurrent: true, resultLocation: location,
    display: { version: 2, edges: true, contours: true }, ...extra });
const settle = () => new Promise(setImmediate);
module.exports = { result, photo, location, snapshot, settle };
