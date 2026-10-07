const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const snapshots = filename => fs.readFileSync(path.join(root, 'build/acceptance-evidence', filename), 'utf8')
  .split(/\r?\n/).filter(line => line.includes('FN_ACCEPTANCE_SNAPSHOT '))
  .map(line => JSON.parse(line.split('FN_ACCEPTANCE_SNAPSHOT ')[1]));
const before = snapshots('before-restart.log').at(-1);
const after = snapshots('after-restart.log');
assert(before && after.length >= 2, 'Need snapshots before restart, after restart, and after refill.');
assert.notDeepEqual(before.project, before.draft, 'Draft must differ from running program.');
assert(before.draft['main.mf'].includes('worker unfinished'), 'Invalid draft was saved.');
for (const state of after) {
  assert.deepEqual(state.project, before.project, 'Running program survives full process restart.');
  assert.deepEqual(state.draft, before.draft, 'Invalid draft survives full process restart.');
  assert.equal(state.bamboocefLoaded, false, 'Server has no BambooCEF.');
  assert.equal(state.online, true);
}
assert.equal(after[0].target, before.target);
assert.equal(after.at(-1).target, before.target + 64, 'Worker resumes after restart and transfers all refilled items.');
assert.equal(after.at(-1).source, 0);
assert.match(fs.readFileSync(path.join(root, 'build/acceptance-evidence/after-restart.log'), 'utf8'), /There are 0 of a max of 2 players online/);
const report = { checkedAt: new Date().toISOString(), server: 'NeoForge 21.1.248 / Minecraft 1.21.1',
  factorynetwork: '0.1.2', bamboocefOnServer: false, onlinePlayersDuringResume: 0,
  transport: 'real server packet handlers with an automated FakePlayer; no logged-in second client',
  beforeRestart: before, afterRestart: after[0], afterRefill: after.at(-1) };
const target = path.join(root, 'docs/validation/acceptance-server-2026-09-06.json');
fs.mkdirSync(path.dirname(target), {recursive:true});
fs.writeFileSync(target, JSON.stringify(report, null, 2) + '\n');
console.log('DEDICATED_SERVER_ACCEPTANCE_OK: separate draft, running project, full restart, 64 resumed transfers, no BambooCEF.');
