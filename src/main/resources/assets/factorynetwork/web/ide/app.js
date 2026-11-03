/* SPDX-License-Identifier: MIT
 * The Java bridge supplies the real project and compiler diagnostics.
 * This page never invents an inventory or interprets Manifold itself. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const session = new FnEditorState.Session();
  const models = new Map();
  const views = new Map();
  const expanded = new Set();
  const knownFolders = new Set();
  const changed = new Set();
  const output = [];
  let editor, monaco, applying = false, shownFile = null, panel = 'problems';
  let loaded = false, connectionTimer, readyTimer, lastState = null, latestError = '';
  let renameFrom = null, deleteTarget = null;
  const keywords = ('if else for in while break continue return fn let true false it ' +
    'worker group multiblock event display on import global const power all from to ' +
    'filter maintain rate per when priority strategy overflow members devices title ' +
    'row text progress indicator list button scale recipe at out store emit await ' +
    'where timeout sleep move except storage crafting world network workers multiblocks').split(' ');

  window.fnSetBackground = url => { $('minecraft-background').src = url; };
  function log(message, kind) {
    if (output.length && output[output.length - 1].message === message) return;
    output.push({ message, kind: kind || '', time: new Date().toLocaleTimeString('de-DE') });
    if (output.length > 80) output.shift();
    if (panel === 'output') renderPanel();
  }
  function send(message) {
    if (typeof window.fnSend !== 'function') {
      latestError = 'Keine Verbindung zum Spiel. Öffne den Editor über ein Factory-Network-Terminal.';
      log(latestError, 'error');
      return false;
    }
    try { window.fnSend(message); return true; }
    catch (error) { latestError = 'Nachricht konnte nicht übertragen werden: ' + error.message; log(latestError, 'error'); return false; }
  }
  function publish() {
    latestError = '';
    send(session.message('change'));
    clearMarkers();
    renderTabs();
    renderStatus();
    renderPanel();
  }
  function action(type) {
    if (!session.connected || !loaded) return;
    if (type === 'deploy' && $('deploy').disabled) return;
    send({ type });
  }
  // The same chain the Java editor reads: kiste.count(item:x), storage.count(...).
  function expressionAt(line, column) {
    const word = c => /[A-Za-z0-9_]/.test(c);
    if (column >= line.length || !word(line[column])) column--;
    if (column < 0 || column >= line.length || !word(line[column])) return '';
    let from = column, to = column;
    while (from > 0 && word(line[from - 1])) from--;
    while (to + 1 < line.length && word(line[to + 1])) to++;
    while (from > 1 && line[from - 1] === '.' && word(line[from - 2])) {
      from -= 2;
      while (from > 0 && word(line[from - 1])) from--;
    }
    let end = to + 1;
    while (end < line.length) {
      if (line[end] === '(') {
        let depth = 0, at = end;
        for (; at < line.length; at++) {
          if (line[at] === '(') depth++;
          else if (line[at] === ')' && --depth === 0) break;
        }
        if (at >= line.length) break;
        end = at + 1;
      } else if (line[end] === '.' && end + 1 < line.length && word(line[end + 1])) {
        end++;
        while (end < line.length && word(line[end])) end++;
      } else break;
    }
    return line.substring(from, end);
  }
  window.fnReceive = function (message) {
    if (typeof message === 'string') {
      try { message = JSON.parse(message); } catch (_) { return; }
    }
    if (!message) return;
    if (message.type === 'value') { if (window.fnValueAnswer) window.fnValueAnswer(message); return; }
    if (message.type === 'error') {
      latestError = message.message || 'Die Änderung konnte nicht übernommen werden.';
      log(latestError, 'error');
      panel = 'output';
      renderStatus();
      renderPanel();
      return;
    }
    if (!session.accept(message)) return;
    clearTimeout(connectionTimer);
    clearTimeout(readyTimer);
    if (message.connected === false) session.connected = false;
    if (!message.dirty) changed.clear();
    if (message.deploy && message.deploy.message && (!lastState || JSON.stringify(message.deploy) !== JSON.stringify(lastState.deploy))) {
      log(message.deploy.message, message.deploy.accepted ? 'success' : 'error');
    }
    if (message.status && (!lastState || message.status !== lastState.status)) log(message.status);
    lastState = message;
    render();
  };

  function ensureModels() {
    applying = true;
    try {
      for (const [name, model] of models) {
        if (!Object.hasOwn(session.files, name)) {
          if (editor.getModel() === model) editor.setModel(null);
          model.dispose(); models.delete(name); views.delete(name); changed.delete(name);
        }
      }
      for (const name of Object.keys(session.files)) {
        let model = models.get(name);
        if (!model) {
          model = monaco.editor.createModel(session.files[name], 'manifold',
            monaco.Uri.parse('inmemory://factorynetwork/' + name));
          models.set(name, model);
          model.onDidChangeContent(() => {
            if (applying) return;
            const text = model.getValue();
            const limitError = FnEditorState.projectError({ ...session.files, [name]: text });
            if (limitError) {
              applying = true;
              // undo() can include earlier accepted typing in the same undo
              // group. Restore the exact last accepted text instead. This rare
              // size-limit rejection resets only this model's undo history.
              try { model.setValue(session.files[name]); } finally { applying = false; }
              latestError = limitError;
              log(latestError, 'error'); panel = 'output'; renderPanel(); renderStatus();
              return;
            }
            if (session.change(name, text)) { changed.add(name); publish(); }
          });
        } else if (model.getValue() !== session.files[name]) {
          // Incoming authoritative edits must not become another outbound edit.
          model.pushEditOperations([], [{ range: model.getFullModelRange(), text: session.files[name] }], () => null);
        }
      }
    } finally { applying = false; }
  }
  function selectFile(name) {
    if (!session.open(name)) return;
    name.split('/').slice(0, -1).reduce((base, part) => {
      const next = base ? base + '/' + part : part; expanded.add(next); return next;
    }, '');
    renderTree(); renderTabs(); showModel();
    editor.focus();
  }
  function showModel() {
    if (!editor) return;
    if (shownFile && editor.getModel()) views.set(shownFile, editor.saveViewState());
    const model = models.get(session.active) || null;
    editor.setModel(model);
    shownFile = session.active;
    if (views.has(shownFile)) editor.restoreViewState(views.get(shownFile));
    const holder = session.snapshot && (session.snapshot.locks || {})[shownFile];
    editor.updateOptions({ readOnly: !!holder || !session.connected,
      readOnlyMessage: { value: holder ? 'Diese Datei wird gerade von ' + holder + ' bearbeitet.' : 'Keine Verbindung zum Controller.' } });
    $('editor-empty').hidden = !!model;
    $('editor-empty').textContent = session.connected
      ? 'Keine Datei geöffnet.\nWähle eine Datei im Projektbaum.'
      : 'Warte auf das Projekt aus dem Terminal.\nHier werden ausschließlich echte Projektdaten angezeigt.';
    $('breadcrumb').textContent = model ? session.active + (holder ? '  ·  Bearbeitet von ' + holder : '') : 'Keine Datei geöffnet';
    renderStatus();
  }
  function updatePosition() {
    const pos = editor && editor.getPosition();
    $('position').textContent = pos ? 'Zeile ' + pos.lineNumber + ', Spalte ' + pos.column : '';
    $('line-count').textContent = editor && editor.getModel() ? editor.getModel().getLineCount() + ' Zeilen' : '';
  }
  function renderTabs() {
    $('tabs').replaceChildren();
    for (const name of session.tabs) {
      const tab = document.createElement('div');
      tab.className = 'tab' + (name === session.active ? ' active' : '');
      tab.setAttribute('role', 'tab'); tab.setAttribute('aria-selected', String(name === session.active));
      tab.tabIndex = name === session.active ? 0 : -1; tab.title = name;
      const label = document.createElement('span');
      const basename = name.split('/').pop();
      label.textContent = session.tabs.some(other => other !== name && other.split('/').pop() === basename) ? name : basename;
      tab.append(label);
      if (changed.has(name)) {
        const dot = document.createElement('span'); dot.className = 'tab-dirty'; dot.textContent = '●'; dot.title = 'Entwurf noch nicht gespeichert'; tab.append(dot);
      }
      const close = document.createElement('button'); close.className = 'tab-close'; close.textContent = '×';
      close.title = 'Tab schließen · Entwurf bleibt erhalten'; close.setAttribute('aria-label', name + ' schließen');
      function closeTab() { session.close(name); renderTabs(); renderTree(); showModel(); }
      close.onclick = event => { event.stopPropagation(); closeTab(); };
      tab.onclick = () => selectFile(name);
      tab.onmousedown = event => { if (event.button === 1) event.preventDefault(); };
      tab.onauxclick = event => { if (event.button === 1) { event.preventDefault(); closeTab(); } };
      tab.onkeydown = event => {
        if (event.target !== tab) return;
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectFile(name); }
        if (event.key === 'Delete') { event.preventDefault(); closeTab(); }
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault();
          const next = session.tabs.indexOf(name) + (event.key === 'ArrowLeft' ? -1 : 1);
          selectFile(session.tabs[(next + session.tabs.length) % session.tabs.length]);
          $('tabs').querySelector('[aria-selected="true"]').focus();
        }
      };
      tab.append(close); $('tabs').append(tab);
    }
  }
  function renderTree() {
    const root = $('file-tree'); root.replaceChildren();
    function add(node, parent, depth) {
      for (const folder of node.folders.values()) {
        const details = document.createElement('details'); details.className = 'folder';
        if (!knownFolders.has(folder.path)) { knownFolders.add(folder.path); expanded.add(folder.path); }
        details.open = expanded.has(folder.path);
        const summary = document.createElement('summary'); summary.textContent = folder.name;
        summary.style.paddingLeft = (12 + depth * 13) + 'px';
        details.append(summary);
        details.addEventListener('toggle', () => { if (details.open) expanded.add(folder.path); else expanded.delete(folder.path); });
        add(folder, details, depth + 1); parent.append(details);
      }
      for (const file of node.files) {
        const button = document.createElement('button');
        button.className = 'file' + (file.path === session.active ? ' active' : '');
        button.style.paddingLeft = (16 + depth * 13) + 'px'; button.title = file.path;
        const label = document.createElement('span'); label.className = 'file-label'; label.textContent = file.name; button.append(label);
        const holder = (session.snapshot.locks || {})[file.path];
        if (holder) {
          const lock = document.createElement('span'); lock.className = 'file-indicator'; lock.textContent = '◆';
          lock.title = 'Bearbeitet von ' + holder; button.title += ' · Bearbeitet von ' + holder; button.append(lock);
        }
        button.onclick = () => selectFile(file.path); parent.append(button);
      }
    }
    if (session.snapshot) add(FnEditorState.treeOf(session.files), root, 0);
    else { const note = document.createElement('p'); note.className = 'empty'; note.textContent = 'Warte auf Projekt …'; root.append(note); }
  }
  function diagnostics() { return session.snapshot && !session.pending() ? session.snapshot.diagnostics || [] : []; }
  function clearMarkers() { if (monaco) for (const model of models.values()) monaco.editor.setModelMarkers(model, 'factorynetwork', []); }
  function markerRange(problem, model) {
    if (Number.isInteger(problem.start) && Number.isInteger(problem.end)) {
      const start = model.getPositionAt(Math.max(0, problem.start));
      const end = model.getPositionAt(Math.max(problem.start + 1, problem.end));
      return { startLineNumber: start.lineNumber, startColumn: start.column,
        endLineNumber: end.lineNumber, endColumn: end.column };
    }
    return { startLineNumber: problem.line || 1, startColumn: problem.column || 1,
      endLineNumber: problem.endLine || problem.line || 1,
      endColumn: problem.endColumn || (problem.column || 1) + 1 };
  }
  function renderMarkers() {
    if (!monaco) return;
    for (const [name, model] of models) {
      monaco.editor.setModelMarkers(model, 'factorynetwork', diagnostics().filter(p => p.file === name).map(p => ({
        ...markerRange(p, model), severity: p.severity === 'error' ? monaco.MarkerSeverity.Error : monaco.MarkerSeverity.Warning,
        message: p.message + (p.hint ? '\n' + p.hint : ''), source: 'Manifold'
      })));
    }
  }
  function renderPanel() {
    $('problems-tab').classList.toggle('selected', panel === 'problems');
    $('output-tab').classList.toggle('selected', panel === 'output');
    $('problems-tab').setAttribute('aria-selected', String(panel === 'problems'));
    $('output-tab').setAttribute('aria-selected', String(panel === 'output'));
    const body = $('panel-body'); body.replaceChildren();
    function empty(text) { const p = document.createElement('p'); p.className = 'empty'; p.textContent = text; body.append(p); }
    if (panel === 'output') {
      if (!output.length) empty('Noch keine Rückmeldung des Controllers.');
      for (const entry of output) {
        const row = document.createElement('div'); row.className = 'log-entry ' + entry.kind;
        const time = document.createElement('span'); time.className = 'log-time'; time.textContent = entry.time;
        const text = document.createElement('span'); text.textContent = entry.message; row.append(time, text); body.append(row);
      }
      body.scrollTop = body.scrollHeight; return;
    }
    if (!session.snapshot) return empty('Warte auf die Projektprüfung aus dem Spiel.');
    if (session.pending()) return empty('Änderungen werden geprüft …');
    const problems = diagnostics();
    if (!problems.length) return empty('Keine Probleme gefunden. Übernehmen aktiviert diesen Entwurf im Controller.');
    for (const problem of problems) {
      const row = document.createElement('button'); row.className = 'problem';
      const icon = document.createElement('span'); icon.className = 'severity-' + problem.severity; icon.textContent = problem.severity === 'error' ? '×' : '△';
      const text = document.createElement('span');
      const location = document.createElement('span'); location.className = 'problem-location';
      location.textContent = (problem.file || 'Projekt') + ':' + problem.line + ':' + problem.column + '  ';
      text.append(location, document.createTextNode(problem.message));
      if (problem.hint) { const hint = document.createElement('span'); hint.className = 'problem-hint'; hint.textContent = problem.hint; text.append(hint); }
      row.append(icon, text);
      row.onclick = () => {
        if (!models.has(problem.file)) return;
        selectFile(problem.file);
        const range = markerRange(problem, models.get(problem.file));
        editor.setSelection(range); editor.revealRangeInCenter(range); editor.focus();
      };
      body.append(row);
    }
  }
  function renderStatus() {
    const state = session.snapshot;
    const enabled = session.connected && loaded;
    $('connection').textContent = session.connected ? 'Controller verbunden' : 'Nicht verbunden';
    $('connection').classList.toggle('online', session.connected);
    for (const id of ['save', 'check', 'close', 'new-file']) $(id).disabled = !enabled;
    const activeLocked = !!(state && (state.locks || {})[session.active]);
    $('rename-file').disabled = !enabled || !session.active || activeLocked;
    $('delete-file').disabled = !enabled || !session.active || activeLocked || Object.keys(session.files).length <= 1;
    const errors = diagnostics().filter(p => p.severity === 'error').length;
    $('problem-count').textContent = state && !session.pending() ? String(diagnostics().length) : '…';
    $('deploy').disabled = !enabled || session.pending() || errors > 0 || !!(state && state.deployPending);
    $('deploy').textContent = state && state.deployPending ? 'Wird übernommen …' : 'Übernehmen';
    $('deploy').title = errors ? errors + ' Fehler verhindern die Übernahme.' : 'Entwurf im Controller übernehmen · Strg+Eingabe';
    $('draft-state').textContent = session.pending() ? 'Prüfung ausstehend' : latestError || (state && state.saveError) ||
      (state && state.status) || (!state ? 'Warte auf Projekt' : state.awaitingSave ? 'Warte auf Speicherbestätigung …' :
        state.dirty ? 'Entwurf geändert' : state.deployed ? 'Entwurf entspricht dem laufenden Programm' : 'Entwurf noch nicht übernommen');
    $('draft-state').className = latestError || (state && state.saveError) ? 'error' : '';
    $('file-status').textContent = session.active ? 'Manifold · ' + session.active : 'Manifold';
    updatePosition();
  }
  function renderNetwork() {
    const root = $('network-names'); root.replaceChildren();
    if (!session.snapshot) return;
    let count = 0;
    for (const [key, title] of [['connectors', 'Connectoren'], ['displays', 'Anzeigen'], ['workers', 'Worker']]) {
      const names = session.snapshot[key] || [];
      if (!names.length) continue;
      const heading = document.createElement('div'); heading.className = 'network-group'; heading.textContent = title; root.append(heading);
      for (const name of names) { const row = document.createElement('div'); row.className = 'network-name'; row.textContent = name; root.append(row); count++; }
    }
    if (!count) { const note = document.createElement('p'); note.className = 'empty'; note.textContent = 'Keine benannten Geräte gemeldet.'; root.append(note); }
  }
  function render() {
    if (loaded) { ensureModels(); renderTabs(); showModel(); renderMarkers(); }
    renderTree(); renderStatus(); renderPanel(); renderNetwork();
  }
  $('save').onclick = () => action('save'); $('check').onclick = () => action('check');
  $('deploy').onclick = () => action('deploy'); $('close').onclick = () => action('close');
  $('problems-tab').onclick = () => { panel = 'problems'; renderPanel(); };
  $('output-tab').onclick = () => { panel = 'output'; renderPanel(); };
  function openFileDialog(from) {
    renameFrom = from;
    $('file-dialog-title').textContent = from ? 'Datei umbenennen' : 'Neue Programmdatei';
    $('file-submit').textContent = from ? 'Umbenennen' : 'Anlegen';
    $('file-name').value = from || ''; $('file-error').textContent = '';
    $('file-dialog').showModal(); $('file-name').focus(); $('file-name').select();
  }
  $('new-file').onclick = () => openFileDialog(null);
  $('rename-file').onclick = () => openFileDialog(session.active);
  $('delete-file').onclick = () => {
    deleteTarget = session.active;
    $('delete-description').textContent = '„' + deleteTarget + '“ aus dem Projekt entfernen?';
    $('delete-dialog').showModal();
  };
  $('cancel-delete').onclick = () => $('delete-dialog').close();
  $('confirm-delete').onclick = () => {
    if (session.remove(deleteTarget)) {
      changed.delete(deleteTarget); ensureModels(); publish(); renderTree(); showModel();
    }
    $('delete-dialog').close();
  };
  $('cancel-file').onclick = () => $('file-dialog').close();
  $('file-form').onsubmit = event => {
    event.preventDefault();
    const name = $('file-name').value.trim();
    if (renameFrom === name) { $('file-dialog').close(); return; }
    if (!FnEditorState.validName(name)) { $('file-error').textContent = 'Ungültiger Dateiname. Beispiel: verarbeitung/ofen.mf'; return; }
    if (Object.hasOwn(session.files, name)) { $('file-error').textContent = 'Diese Datei existiert bereits.'; return; }
    const nextFiles = { ...session.files, [name]: renameFrom ? session.files[renameFrom] : '' };
    if (renameFrom) delete nextFiles[renameFrom];
    const limitError = FnEditorState.projectError(nextFiles);
    if (limitError) { $('file-error').textContent = limitError; return; }
    if (renameFrom ? session.rename(renameFrom, name) : session.create(name)) {
      changed.delete(renameFrom);
      changed.add(name); $('file-dialog').close(); ensureModels(); publish(); renderTree(); showModel(); editor.focus();
    } else $('file-error').textContent = 'Die Datei kann gerade nicht geändert werden. Prüfe, ob ein anderer Spieler sie bearbeitet.';
  };
  window.addEventListener('keydown', event => {
    if ($('file-dialog').open || $('delete-dialog').open || !(event.ctrlKey || event.metaKey)) return;
    const key = event.key.toLowerCase();
    let type = key === 's' ? 'save' : key === 'enter' ? 'deploy' : key === 'b' && event.shiftKey ? 'check' : null;
    if (type) { event.preventDefault(); event.stopPropagation(); action(type); }
  }, true);

  // A Blob worker keeps file:// installations offline without relaxing the
  // browser's file-origin security. Its source ships inside this same bundle.
  let workerUrl;
  const workerEnvironment = { getWorker: function () {
    if (!workerUrl) workerUrl = URL.createObjectURL(new Blob([window.FnEditorWorkerSource,
      "\nself.postMessage({ type: 'vscode-worker-ready' });"], { type: 'text/javascript' }));
    return new Worker(workerUrl, { name: 'factorynetwork-editor' });
  } };
  window.addEventListener('unload', () => { if (workerUrl) URL.revokeObjectURL(workerUrl); });
  require.config({ paths: { vs: 'vs' } });
  require(['vs/editor/editor.main'], function () {
    monaco = window.monaco;
    // Monaco's AMD entrypoint installs its own environment while loading.
    // Replace it afterwards, before creating the first model or editor.
    window.MonacoEnvironment = workerEnvironment;
    monaco.languages.register({ id: 'manifold', extensions: ['.mf'] });
    monaco.languages.setMonarchTokensProvider('manifold', { keywords, tokenizer: { root: [
      [/\/\/.*$/, 'comment'], [/"([^"\\]|\\.)*"/, 'string'], [/`[^`]*`/, 'string'],
      [/\b\d+(\.\d+)?\b/, 'number'], [/[a-zA-Z_][\w]*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
      [/[{}()\[\]]/, '@brackets'], [/[<>=!+\-*\/%]+/, 'operator']
    ] } });
    monaco.languages.setLanguageConfiguration('manifold', {
      comments: { lineComment: '//' }, brackets: [['{', '}'], ['[', ']'], ['(', ')']],
      autoClosingPairs: [{ open: '{', close: '}' }, { open: '[', close: ']' }, { open: '(', close: ')' },
        { open: '"', close: '"', notIn: ['string', 'comment'] }, { open: '`', close: '`', notIn: ['string', 'comment'] }]
    });
    monaco.languages.registerCompletionItemProvider('manifold', {
      provideCompletionItems: function (model, position) {
        const word = model.getWordUntilPosition(position);
        const range = { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: word.startColumn, endColumn: word.endColumn };
        const suggestions = keywords.map(word => ({ label: word, kind: monaco.languages.CompletionItemKind.Keyword, insertText: word, range }));
        const state = session.snapshot || {};
        for (const [key, label] of [['connectors', 'Connector'], ['displays', 'Anzeige'], ['workers', 'Worker']]) {
          for (const name of state[key] || []) suggestions.push({ label: name, detail: label + ' im verbundenen Netz',
            kind: monaco.languages.CompletionItemKind.Variable, insertText: /^[a-zA-Z_][\w]*$/.test(name) && !keywords.includes(name) ? name : '`' + name.replace(/`/g, '') + '`', range });
        }
        return { suggestions };
      }
    });
    monaco.editor.defineTheme('factory-network', { base: 'vs-dark', inherit: true, rules: [
      { token: 'keyword', foreground: 'A0D986' }, { token: 'string', foreground: 'E3BE85' },
      { token: 'number', foreground: 'BCA6DF' }, { token: 'comment', foreground: '7D9586', fontStyle: 'italic' }
    ], colors: { 'editor.background': '#151e1b', 'editor.foreground': '#e0e8e2',
      'editorLineNumber.foreground': '#536d5c', 'editorLineNumber.activeForeground': '#a0d986',
      'editor.selectionBackground': '#a0d98630', 'editor.lineHighlightBackground': '#ffffff04', 'minimap.background': '#151e1b' } });
    editor = monaco.editor.create($('editor'), { model: null, theme: 'factory-network', automaticLayout: true,
      fontFamily: 'Consolas, "Cascadia Mono", monospace', fontSize: 14, lineHeight: 22, minimap: { enabled: false },
      padding: { top: 10 }, tabSize: 4, insertSpaces: true, folding: true, stickyScroll: { enabled: true },
      bracketPairColorization: { enabled: true }, renderWhitespace: 'selection', scrollBeyondLastLine: false,
      smoothScrolling: false, wordWrap: 'off', mouseWheelZoom: true, readOnly: true });
    editor.onDidChangeCursorPosition(updatePosition);
    // Hover: the current value of a reading expression, asked of the game.
    const pendingValues = new Map();
    let valueId = 0;
    window.fnValueAnswer = message => {
      const resolve = pendingValues.get(message.id);
      if (resolve) { pendingValues.delete(message.id); resolve(message.value); }
    };
    monaco.languages.registerHoverProvider('manifold', {
      provideHover: (model, position) => {
        const expression = expressionAt(model.getLineContent(position.lineNumber), position.column - 1);
        if (!expression || typeof window.fnSend !== 'function') return null;
        const id = ++valueId;
        return new Promise(resolve => {
          pendingValues.set(id, value => resolve({ contents: [{ value: '`' + expression + '` = **' + value + '**' }] }));
          send({ type: 'evaluate', id, expression });
          setTimeout(() => { if (pendingValues.delete(id)) resolve(null); }, 1500);
        });
      }
    });
    // Ctrl+click on a word of the language opens its page in the manual.
    editor.onMouseDown(event => {
      if (!(event.event.ctrlKey || event.event.metaKey) || !event.target.position) return;
      const word = editor.getModel() && editor.getModel().getWordAtPosition(event.target.position);
      if (word) send({ type: 'guide', word: word.word });
    });
    loaded = true; render();
    window.fnIde = { editor, modelle: models, waehle: selectFile, dateien: () => Object.keys(session.files) };
    console.log('Factory Network IDE bereit');
    // BambooCEF injects fnSend in onLoadEnd. Monaco can finish first on a
    // warm file cache; retry the handshake instead of leaving an empty UI.
    function requestReady() {
      if (session.snapshot) return;
      if (typeof window.fnSend === 'function') send({ type: 'ready' });
      readyTimer = setTimeout(requestReady, 500);
    }
    requestReady();
  }, function (error) {
    latestError = 'Der Codeeditor konnte nicht geladen werden. ' + (error.message || 'Monaco fehlt.');
    $('editor-empty').textContent = latestError; log(latestError, 'error');
  });
  connectionTimer = setTimeout(() => {
    if (session.snapshot) return;
    $('connection').textContent = 'Nicht verbunden';
    $('editor-empty').textContent = 'Kein Controller verbunden.\nÖffne den Editor über ein Factory-Network-Terminal im Spiel.';
    log('Noch keine Projektdaten empfangen. Die Browseransicht allein verbindet sich nicht mit einer Welt.');
  }, 5000);
})();
