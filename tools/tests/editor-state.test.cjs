/* SPDX-License-Identifier: MIT */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { Session, treeOf, validName, projectError } = require('../../src/main/resources/assets/factorynetwork/web/ide/editor-state.js');
const state = (revision = 0, extra = {}) => ({ type: 'state', revision, files: { 'main.mf': 'worker source {}', 'erz/ofen.mf': '// Erz' }, locks: {}, ...extra });

test('late bridge snapshot cannot overwrite new text or revive stale diagnostics', () => {
  const s = new Session(); s.accept(state());
  s.change('main.mf', '// first'); s.change('main.mf', '// second');
  assert.equal(s.accept(state(1, { diagnostics: [{ message: 'old error' }] })), false);
  assert.equal(s.files['main.mf'], '// second'); assert.equal(s.pending(), true);
  assert.equal(s.accept(state(2, { files: s.files, diagnostics: [] })), true);
  assert.equal(s.pending(), false);
});
test('close all tabs, receive update, reopen: no file deletion or draft loss', () => {
  const s = new Session(); s.accept(state()); s.open('erz/ofen.mf'); s.change('erz/ofen.mf', '// edit');
  s.close('erz/ofen.mf'); assert.equal(s.active, 'main.mf'); s.close('main.mf');
  s.accept(state(1, { files: s.files })); assert.equal(s.active, null);
  s.open('erz/ofen.mf'); assert.equal(s.files[s.active], '// edit');
});
test('folder paths distinguish same basename and produce real hierarchy', () => {
  const tree = treeOf({ 'erz/ofen.mf': '', 'chemie/ofen.mf': '', 'erz/linie_a/wasser.mf': '', 'main.mf': '' });
  assert.equal(tree.files[0].path, 'main.mf');
  assert.equal(tree.folders.get('erz').files[0].path, 'erz/ofen.mf');
  assert.equal(tree.folders.get('erz').folders.get('linie_a').files[0].path, 'erz/linie_a/wasser.mf');
});
test('held files remain readable, but cannot be changed, renamed or deleted', () => {
  const s = new Session(); s.accept(state(0, { locks: { 'erz/ofen.mf': 'Alex' } }));
  assert.equal(s.open('erz/ofen.mf'), true);
  assert.equal(s.change('erz/ofen.mf', 'wrong'), false);
  assert.equal(s.rename('erz/ofen.mf', 'new.mf'), false);
  assert.equal(s.remove('erz/ofen.mf'), false); assert.equal(s.revision, 0);
});
test('rename updates active tab and whole draft; final file cannot be deleted', () => {
  const s = new Session(); s.accept(state());
  assert.equal(s.rename('main.mf', 'steuerung/main.mf'), true);
  assert.equal(s.active, 'steuerung/main.mf');
  assert.equal(s.message('change').files['steuerung/main.mf'], 'worker source {}');
  assert.equal(s.remove('erz/ofen.mf'), true);
  assert.equal(s.remove('steuerung/main.mf'), false);
});
test('filename validation follows Project instead of OS path rules', () => {
  for (const invalid of ['../main.mf', 'Foo.mf', 'a\\b.mf', 'a/.mf', '__proto__', 'a'.repeat(33) + '.mf', 'main.fn']) assert.equal(validName(invalid), false, invalid);
  assert.equal(validName('erz/linie_1/ofen.mf'), true);
});
test('project limits count UTF-16 file lengths and UTF-8 project bytes including names', () => {
  assert.equal(projectError({ 'main.mf': 'ä'.repeat(65536) }), '');
  assert.match(projectError({ 'main.mf': 'a'.repeat(65537) }), /zu lang/);
  assert.match(projectError({ 'main.mf': 'ä'.repeat(65536), 'second.mf': 'ä'.repeat(65536) }), /256 KiB/);
  assert.match(projectError(Object.fromEntries(Array.from({ length: 129 }, (_, i) => [`f${i}.mf`, '']))), /128/);
});
test('rejected revision can restore authoritative draft without staying pending forever', () => {
  const s = new Session(); s.accept(state()); s.change('main.mf', '// rejected');
  assert.equal(s.accept(state(1)), true); assert.equal(s.pending(), false);
  assert.equal(s.files['main.mf'], 'worker source {}');
});
