/* Test the real CEF page -> Java -> dedicated server path, in the isolated acceptance world. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const evidence = path.resolve(__dirname, '../../build/acceptance-evidence');
const valid = '// acceptance: native CEF roundtrip\nworker transfer {\n    from source\n    to target\n}\n';
const broken = '// acceptance: native CEF draft survives restart\nworker unfinished {\n    from source\n';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

(async () => {
  const reopen = process.argv.includes('--verify-reopen');
  assert(reopen !== process.argv.includes('--exercise'), 'Choose --exercise (edits test project) or --verify-reopen (reads saved draft).');
  const pages = await (await fetch('http://127.0.0.1:9222/json')).json();
  const targets = pages.filter(page => page.type === 'page' &&
    /\/acceptance-client\/factorynetwork-web\/ide\/index\.html$/.test(new URL(page.url).pathname));
  assert.equal(targets.length, 1, 'Exactly one editor in the isolated acceptance-client directory is required.');
  const socket = new WebSocket(targets[0].webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  const pending = new Map();
  let sequence = 0;
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const {resolve, reject, timer} = pending.get(message.id);
      clearTimeout(timer); pending.delete(message.id);
      if (message.error) reject(new Error(JSON.stringify(message.error)));
      else resolve(message.result);
    }
  };
  const call = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 10000);
    pending.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method, params}));
  });
  const evaluate = async expression => {
    const response = await call('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
    return response.result.value;
  };
  const waitFor = async (expression, label) => {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (await evaluate(expression)) return;
      await delay(100);
    }
    throw new Error('Timed out: ' + label + '; state=' + JSON.stringify(await evaluate('window.__nativeTestState')));
  };
  try {
    await waitFor("typeof monaco === 'object' && typeof fnReceive === 'function' && typeof fnSend === 'function'", 'native bridge ready');
    await evaluate(`if (!window.__nativeTestReceiver) {
      window.__nativeTestReceiver = window.fnReceive;
      window.fnReceive = function(message) {
        if (message.type === 'state') window.__nativeTestState = message;
        return window.__nativeTestReceiver(message);
      };
    }
    window.fnSend({type:'ready'});`);
    await waitFor('window.__nativeTestState?.connected', 'server connection');
    const initial = await evaluate('window.__nativeTestState');
    assert(initial.connectors.includes('source') && initial.connectors.includes('target'), 'Expected acceptance fixture');
    fs.mkdirSync(evidence, {recursive:true});
    if (reopen) {
      const expected = JSON.parse(fs.readFileSync(path.join(evidence, 'native-after.json'), 'utf8'));
      assert.deepEqual(initial.files, expected.files, 'Client reopened the exact saved draft after server restart.');
      assert.equal(initial.dirty, false);
      assert.equal(initial.awaitingSave, false);
      assert(initial.diagnostics.some(d => d.severity === 'error'), 'Saved broken draft still has compiler diagnostics.');
      assert.equal(await evaluate("monaco.editor.getModels().find(model => model.uri.path === '/main.mf').getValue()"), expected.files['main.mf']);
      fs.writeFileSync(path.join(evidence, 'native-reopened.json'), JSON.stringify(initial, null, 2));
      console.log('NATIVE_CEF_REOPEN_OK: real client reconnected and displays the exact draft saved before server restart.');
      return;
    }
    fs.writeFileSync(path.join(evidence, 'native-before.json'), JSON.stringify(initial, null, 2));
    await evaluate(`monaco.editor.getModels().find(model => model.uri.path === '/main.mf').setValue(${JSON.stringify(valid)});`);
    await evaluate("document.getElementById('save').click(); document.getElementById('check').click();");
    await waitFor(`window.__nativeTestState.files['main.mf'] === ${JSON.stringify(valid)} &&
      !window.__nativeTestState.dirty && !window.__nativeTestState.awaitingSave &&
      !window.__nativeTestState.diagnostics.some(d => d.severity === 'error')`, 'valid draft saved and compiled');
    await evaluate("document.getElementById('deploy').click()");
    await waitFor('window.__nativeTestState.deployed && window.__nativeTestState.deploy.accepted && !window.__nativeTestState.deployPending', 'server accepted deployment');
    fs.writeFileSync(path.join(evidence, 'native-deployed.json'), JSON.stringify(await evaluate('window.__nativeTestState'), null, 2));
    await evaluate(`monaco.editor.getModels().find(model => model.uri.path === '/main.mf').setValue(${JSON.stringify(broken)});`);
    await waitFor(`window.__nativeTestState.files['main.mf'] === ${JSON.stringify(broken)} &&
      !window.__nativeTestState.dirty && !window.__nativeTestState.awaitingSave &&
      window.__nativeTestState.diagnostics.some(d => d.severity === 'error')`, 'broken draft autosaved with compiler diagnostics');
    assert.equal(await evaluate("document.getElementById('deploy').disabled"), true, 'UI refuses deploying compiler errors');
    assert.equal(await evaluate('window.__nativeTestState.deployed'), false);
    const capture = await call('Page.captureScreenshot', {format:'png'});
    fs.writeFileSync(path.join(evidence, 'native-editor.png'), Buffer.from(capture.data, 'base64'));
    fs.writeFileSync(path.join(evidence, 'native-after.json'), JSON.stringify(await evaluate('window.__nativeTestState'), null, 2));
    console.log('NATIVE_CEF_ACCEPTANCE_OK: real save acknowledgement, compile, deploy, autosave and diagnostics.');
  } finally { socket.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
