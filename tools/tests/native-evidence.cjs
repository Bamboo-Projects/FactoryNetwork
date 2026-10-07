const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const evidence = path.join(root, 'build/acceptance-evidence');
const read = name => JSON.parse(fs.readFileSync(path.join(evidence, name), 'utf8'));
const snapshot = name => {
  const lines = fs.readFileSync(path.join(evidence, name), 'utf8').split(/\r?\n/);
  return JSON.parse(lines.filter(line => line.includes('FN_ACCEPTANCE_SNAPSHOT ')).at(-1).split('FN_ACCEPTANCE_SNAPSHOT ')[1]);
};
const deployed = read('native-deployed.json');
const saved = read('native-after.json');
const reopened = read('native-reopened.json');
const before = snapshot('native-server-before-restart.log');
const after = snapshot('native-server-after-restart.log');
assert.equal(deployed.deploy.accepted, true);
assert.equal(deployed.deployed, true);
assert.equal(saved.dirty, false);
assert.equal(saved.awaitingSave, false);
assert(saved.diagnostics.some(d => d.severity === 'error'));
assert.notDeepEqual(deployed.files, saved.files);
for (const state of [before, after]) {
  assert.deepEqual(state.project, deployed.files);
  assert.deepEqual(state.draft, saved.files);
  assert.equal(state.bamboocefLoaded, false);
  assert.equal(state.online, true);
  assert.equal(state.source, 0);
  assert.equal(state.target, 128);
}
assert.equal(reopened.connected, true);
assert.deepEqual(reopened.files, saved.files);
const report = {
  checkedAt: new Date().toISOString(), factorynetwork: '0.1.2', bamboocef: '0.1.1',
  transport: 'Actual native CEF/Monaco page, Java bridge, logged-in development Minecraft client, dedicated NeoForge server',
  automation: 'CEF DevTools drives real editor controls; acceptance companion opens the real TerminalMenu and EditorApp',
  manualHardwareInputTested: false, bamboocefOnServer: false,
  verified: ['save acknowledgement', 'compile diagnostics', 'valid deployment', 'invalid draft autosave',
    'UI blocks invalid deployment', 'separate running project and draft survive full server restart',
    'reconnected native client displays exact saved draft'],
  beforeRestart: before, afterRestart: after, reopenedDraft: reopened.files,
  screenshot: '../previews/editor-native.png'
};
const target = path.join(root, 'docs/validation/native-cef-2026-09-06.json');
fs.mkdirSync(path.dirname(target), {recursive:true});
fs.writeFileSync(target, JSON.stringify(report, null, 2) + '\n');
console.log('NATIVE_RESTART_EVIDENCE_OK: native editor, save, deploy, diagnostics, full restart and reconnect.');
