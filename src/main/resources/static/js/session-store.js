'use strict';
(() => {
  let connection;

  /** Opens the origin-local backup; successful analyses also have a durable server copy. */
  function database() {
    if (!connection) connection = new Promise((resolve, reject) => {
      if (!window.indexedDB) { reject(new Error('Browser storage is unavailable.')); return; }
      const request = window.indexedDB.open('vision-lab', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('session');
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('Close older Vision Lab tabs to enable retention.'));
      request.onsuccess = () => {
        const db = request.result;
        db.onversionchange = () => { db.close(); connection = undefined; };
        resolve(db);
      };
    }).catch(error => { connection = undefined; throw error; });
    return connection;
  }

  /** Resolves on transaction completion, including asynchronous quota/abort failures. */
  async function access(mode, action) {
    const db = await database();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('session', mode);
      const request = action(transaction.objectStore('session'));
      transaction.oncomplete = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error || request.error || new Error('Browser storage transaction was aborted.'));
      transaction.onerror = () => reject(transaction.error || request.error);
    });
  }

  window.VisionSessionStore = {
    /** Returns the last snapshot; no server request or automatic reprocessing occurs. */
    load: () => access('readonly', store => store.get('latest')),
    /** Atomically replaces the single snapshot, including its Blob, measurements and controls. */
    save: snapshot => access('readwrite', store => store.put(snapshot, 'latest'))
  };
})();
