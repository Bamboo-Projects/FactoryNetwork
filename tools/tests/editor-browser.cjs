/* SPDX-License-Identifier: MIT
 * Run with node tools/tests/editor-browser.cjs.
 * PLAYWRIGHT_MODULE may point at a local Playwright installation.
 * Test fixtures are injected only here; the shipped page has no sample project. */
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = pathToFileURL(path.resolve(__dirname, '../../src/main/resources/assets/factorynetwork/web/ide/index.html')).href;
(async () => {
  const browser = await chromium.launch({ channel: process.env.CHROME_CHANNEL || 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const failures = [];
    page.on('pageerror', error => failures.push(error.message));
    await page.addInitScript(() => {
      window.sent = [];
      window.fnSend = message => window.sent.push(message);
    });
    await page.goto(url); await page.waitForFunction(() => window.fnIde);
    assert.equal(await page.locator('.file').count(), 0);
    assert.equal(await page.locator('#deploy').isDisabled(), true);
    assert.equal(await page.evaluate(() => sent[0].type), 'ready');
    const state = { type: 'state', revision: 0, connected: true,
      files: { 'main.mf': '// Testprojekt\nlet menge = 64\n', 'erz/ofen.mf': '// Ofen\n', 'chemie/ofen.mf': '// Chemie\n' },
      locks: {}, diagnostics: [], dirty: false, deployed: true, deployPending: false,
      connectors: ['ofen'], displays: ['lagerstand'], workers: [], status: 'Test: verbundener Controller' };
    await page.evaluate(state => window.fnReceive(state), state);
    assert.equal(await page.locator('.file').count(), 3);
    assert.equal(await page.locator('.folder').count(), 2);
    await page.getByRole('button', { name: 'mf ofen.mf', exact: false }).first().click();
    await page.locator('.file[title="erz/ofen.mf"]').click();
    await page.evaluate(() => fnIde.editor.executeEdits('test', [{ range: new monaco.Range(1, 1, 1, 1), text: '// Bearbeitet\n' }]));
    let change = await page.evaluate(() => sent.filter(m => m.type === 'change').at(-1));
    assert.match(change.files['erz/ofen.mf'], /^\/\/ Bearbeitet/);
    assert.equal(change.revision, 1);
    await page.locator('.tab[title="erz/ofen.mf"]').click({ button: 'middle' });
    assert.equal(await page.locator('.tab[title="erz/ofen.mf"]').count(), 0);
    await page.locator('.file[title="erz/ofen.mf"]').click();
    assert.match(await page.evaluate(() => fnIde.editor.getValue()), /^\/\/ Bearbeitet/);
    await page.evaluate(state => fnReceive(state), { ...state, diagnostics: [{ file: 'main.mf', line: 1, column: 1, message: 'VERALTET', severity: 'error' }] });
    assert.match(await page.evaluate(() => fnIde.editor.getValue()), /^\/\/ Bearbeitet/);
    assert.equal(await page.locator('.problem').count(), 0);
    const updated = { ...state, files: change.files, revision: 1, dirty: true, deployed: false,
      diagnostics: [{ file: 'main.mf', start: 20, end: 25, line: 2, column: 1, severity: 'error', message: 'Testfehler', hint: 'Testhinweis' }] };
    await page.evaluate(state => fnReceive(state), updated);
    assert.equal(await page.locator('#deploy').isDisabled(), true);
    await page.locator('.problem').click();
    assert.equal(await page.evaluate(() => fnIde.editor.getModel().uri.path), '/main.mf');
    assert.equal(await page.evaluate(() => monaco.editor.getModelMarkers({ owner: 'factorynetwork' }).length), 1);
    await page.evaluate(state => fnReceive(state), { ...updated, diagnostics: [], dirty: false, locks: { 'main.mf': 'Alex' } });
    assert.equal(await page.evaluate(() => fnIde.editor.getOption(monaco.editor.EditorOption.readOnly)), true);
    assert.match(await page.locator('#breadcrumb').textContent(), /Alex/);
    assert.equal(await page.locator('#rename-file').isDisabled(), true);
    await page.locator('.file[title="erz/ofen.mf"]').click();
    assert.equal(await page.locator('#rename-file').isDisabled(), false);
    assert.match(await page.locator('#file-status').textContent(), /erz\/ofen.mf/);
    await page.evaluate(state => fnReceive(state), { ...updated, diagnostics: [], dirty: false });
    await page.locator('#new-file').click(); await page.locator('#file-name').fill('../bad.mf');
    await page.locator('#file-submit').click(); assert.match(await page.locator('#file-error').textContent(), /Ungültig/);
    await page.locator('#file-name').fill('anlage/pumpe.mf'); await page.locator('#file-submit').click();
    assert.equal(await page.locator('.file[title="anlage/pumpe.mf"]').count(), 1);
    await page.locator('#rename-file').click(); await page.locator('#file-name').fill('anlage/wasser.mf'); await page.locator('#file-submit').click();
    assert.equal(await page.locator('.file[title="anlage/wasser.mf"]').count(), 1);
    assert.equal(await page.locator('.file[title="anlage/pumpe.mf"]').count(), 0);
    await page.locator('#delete-file').click(); await page.locator('#confirm-delete').click();
    assert.equal(await page.locator('.file[title="anlage/wasser.mf"]').count(), 0);
    change = await page.evaluate(() => sent.filter(m => m.type === 'change').at(-1));
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision, deployed: false });
    await page.keyboard.press('Control+s'); await page.keyboard.press('Control+Shift+b'); await page.keyboard.press('Control+Enter');
    assert.deepEqual(await page.evaluate(() => sent.slice(-3).map(m => m.type)), ['save', 'check', 'deploy']);
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision, deployPending: true });
    assert.equal(await page.locator('#deploy').isDisabled(), true);
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision,
      awaitingSave: true, status: 'Warte auf Speicherbestätigung …' });
    assert.match(await page.locator('#draft-state').textContent(), /Speicherbestätigung/);
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision,
      dirty: true, saveError: 'Speichern abgelehnt: Datei von Alex gesperrt', status: 'Speichern abgelehnt: Datei von Alex gesperrt' });
    assert.match(await page.locator('#draft-state').textContent(), /abgelehnt/);
    assert.equal(await page.locator('#draft-state').evaluate(e => e.classList.contains('error')), true);
    assert.equal(await page.locator('#draft-state').evaluate(e => getComputedStyle(e).color), 'rgb(255, 165, 165)');
    assert.doesNotMatch(await page.locator('#draft-state').textContent(), /automatisch|gespeichert/);
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision, deploy: { accepted: false, message: '<img src=x onerror=alert(1)> verweigert' } });
    await page.locator('#output-tab').click(); assert.equal(await page.locator('#panel-body img').count(), 0);
    await page.locator('#problems-tab').click();
    await page.evaluate(() => {
      window.beforeLargePaste = fnIde.editor.getValue();
      fnIde.editor.executeEdits('test-limit', [{ range: new monaco.Range(1, 1, 1, 1), text: 'a'.repeat(65537) }]);
    });
    assert.equal(await page.evaluate(() => fnIde.editor.getValue() === beforeLargePaste), true);
    assert.match(await page.locator('#draft-state').textContent(), /zu lang/);
    // Edit once to clear the local error, then acknowledge the latest revision.
    await page.evaluate(() => fnIde.editor.executeEdits('test-clear', [{ range: new monaco.Range(1, 1, 1, 1), text: '// Browserprüfung: Testdaten\n' }]));
    change = await page.evaluate(() => sent.filter(m => m.type === 'change').at(-1));
    await page.evaluate(state => fnReceive(state), { ...state, files: change.files, revision: change.revision,
      status: 'Browserprüfung · Testdaten · kein Ingame-Nachweis', deployed: false });
    await page.locator('#problems-tab').click();
    fs.mkdirSync(path.resolve(__dirname, '../../build'), { recursive: true });
    fs.mkdirSync(path.resolve(__dirname, '../../docs/previews'), { recursive: true });
    await page.screenshot({ path: path.resolve(__dirname, '../../build/editor-ui-1280.png') });
    await page.screenshot({ path: path.resolve(__dirname, '../../docs/previews/editor.png') });
    await page.setViewportSize({ width: 854, height: 480 });
    await page.screenshot({ path: path.resolve(__dirname, '../../build/editor-ui-854.png') });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.deepEqual(failures, []);
    const late = await browser.newPage();
    await late.goto(url); await late.waitForFunction(() => window.fnIde);
    await late.evaluate(() => { window.sent = []; window.fnSend = message => window.sent.push(message); });
    await late.waitForFunction(() => window.sent.some(message => message.type === 'ready'));
    await late.close();
    console.log('PASS: offline/late bridge startup, live project, folders, middle-click/reopen, stale state, diagnostics, locks, create/rename/delete, shortcuts, deploy pending, rejected save, large paste rollback, safe output, responsive layout.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
