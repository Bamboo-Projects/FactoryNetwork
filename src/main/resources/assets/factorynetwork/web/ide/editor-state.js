/* SPDX-License-Identifier: MIT
 * Pure editor state, shared by the page and the Node regression tests. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.FnEditorState = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  function validName(name) {
    return typeof name === 'string' && name.length <= 96 &&
      /^([a-z0-9_]{1,32}\/)*[a-z0-9_]{1,32}\.mf$/.test(name);
  }
  function filesOf(files) {
    const result = Object.create(null);
    Object.keys(files || {}).sort().forEach(name => {
      if (validName(name) && typeof files[name] === 'string') result[name] = files[name];
    });
    return result;
  }
  function treeOf(files) {
    const root = { name: '', path: '', folders: new Map(), files: [] };
    Object.keys(files).sort().forEach(path => {
      const pieces = path.split('/');
      let node = root;
      pieces.slice(0, -1).forEach(name => {
        const folderPath = node.path ? node.path + '/' + name : name;
        if (!node.folders.has(name)) node.folders.set(name,
          { name, path: folderPath, folders: new Map(), files: [] });
        node = node.folders.get(name);
      });
      node.files.push({ name: pieces[pieces.length - 1], path });
    });
    return root;
  }
  function projectError(files) {
    const names = Object.keys(files);
    if (!names.length || names.length > 128) return 'Ein Projekt braucht zwischen 1 und 128 Dateien.';
    let bytes = 0;
    const encoder = new TextEncoder();
    for (const name of names) {
      if (!validName(name)) return 'Ungültiger Dateiname: ' + name;
      if (typeof files[name] !== 'string') return 'Dateiinhalt muss Text sein: ' + name;
      if (files[name].length > 65536) return 'Die Datei ist zu lang (höchstens 65.536 Zeichen): ' + name;
      bytes += encoder.encode(name).length + encoder.encode(files[name]).length;
    }
    return bytes > 262144 ? 'Der Entwurf überschreitet 256 KiB. Teile ihn auf mehrere Controller auf.' : '';
  }
  class Session {
    constructor() {
      this.files = Object.create(null);
      this.tabs = [];
      this.active = null;
      this.revision = 0;
      this.connected = false;
      this.snapshot = null;
    }
    accept(state) {
      if (!state || state.type !== 'state' || !Number.isSafeInteger(state.revision) ||
          state.revision < this.revision) return false;
      this.revision = state.revision;
      this.connected = state.connected !== false;
      this.snapshot = state;
      this.files = filesOf(state.files);
      this.tabs = this.tabs.filter(name => Object.hasOwn(this.files, name));
      if (this.active && !Object.hasOwn(this.files, this.active)) this.active = null;
      if (!this.active && this.tabs.length) this.active = this.tabs[0];
      if (!this.active && !this.initialized) {
        this.open(Object.hasOwn(this.files, 'main.mf') ? 'main.mf' : Object.keys(this.files)[0]);
      }
      this.initialized = true;
      return true;
    }
    open(name) {
      if (!name || !Object.hasOwn(this.files, name)) return false;
      if (!this.tabs.includes(name)) this.tabs.push(name);
      this.active = name;
      return true;
    }
    close(name) {
      const index = this.tabs.indexOf(name);
      if (index < 0) return;
      this.tabs.splice(index, 1);
      if (this.active === name) this.active = this.tabs[Math.min(index, this.tabs.length - 1)] || null;
    }
    change(name, text) {
      if (!this.connected || !Object.hasOwn(this.files, name) || this.files[name] === text ||
          (this.snapshot.locks || {})[name]) return false;
      this.files[name] = text;
      this.revision++;
      return true;
    }
    create(name) {
      if (!this.connected || !validName(name) || Object.hasOwn(this.files, name)) return false;
      this.files[name] = '';
      this.revision++;
      this.open(name);
      return true;
    }
    rename(name, to) {
      if (!this.connected || !Object.hasOwn(this.files, name) || !validName(to) ||
          Object.hasOwn(this.files, to) || (this.snapshot.locks || {})[name]) return false;
      this.files[to] = this.files[name];
      delete this.files[name];
      this.tabs = this.tabs.map(tab => tab === name ? to : tab);
      if (this.active === name) this.active = to;
      this.revision++;
      return true;
    }
    remove(name) {
      if (!this.connected || !Object.hasOwn(this.files, name) || Object.keys(this.files).length < 2 ||
          (this.snapshot.locks || {})[name]) return false;
      this.close(name);
      delete this.files[name];
      this.revision++;
      return true;
    }
    pending() { return !!this.snapshot && this.revision > this.snapshot.revision; }
    message(type) { return { type, revision: this.revision, files: { ...this.files } }; }
  }
  return { validName, filesOf, treeOf, projectError, Session };
});
