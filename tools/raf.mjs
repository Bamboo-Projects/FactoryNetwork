/**
 * Misst, wie schnell eine Seite ueberhaupt Bilder bekommt.
 *
 * Aufruf:  node tools/raf.mjs [port]
 *          9222 = unser fensterloser Browser, 9223 = Chrome im Fenster
 *
 * <b>Warum diese Zahl vor jeder anderen kommt.</b> Alle unsere
 * Seitenmessungen enden am zweiten Bildaufruf nach einem Tastendruck. Wie
 * lange das dauert, haengt zur Haelfte davon ab, wie oft eine Seite
 * ueberhaupt drankommt: Bei sechzig Bildern je Sekunde sind zwei Aufrufe
 * 33 ms, bei dreissig sind es 67 ms — ohne dass irgendetwas langsamer
 * geworden waere. Wer den Takt nicht kennt, haelt ihn fuer Rechenzeit.
 */

const PORT = Number(process.argv[2] || 9222);

const liste = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
const seite = liste.find((e) => e.type === 'page' && e.url.includes('probe.html'))
  || liste.find((e) => e.type === 'page');
if (!seite) {
  console.error(`Keine Seite auf Port ${PORT}.`);
  process.exit(1);
}
const version = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json();
console.log(`Browser: ${version.Browser}`);
console.log(`Seite:   ${seite.url}`);

const draht = new WebSocket(seite.webSocketDebuggerUrl);
await new Promise((fertig) => { draht.onopen = fertig; });
let naechste = 1;
const offen = new Map();
draht.onmessage = (nachricht) => {
  const daten = JSON.parse(nachricht.data);
  if (daten.id && offen.has(daten.id)) {
    if (daten.error) { console.error('FEHLER: ' + JSON.stringify(daten.error)); }
    offen.get(daten.id)(daten.result);
    offen.delete(daten.id);
  }
};
const sende = (verfahren, parameter = {}) => new Promise((fertig) => {
  offen.set(naechste, fertig);
  draht.send(JSON.stringify({ id: naechste++, method: verfahren, params: parameter }));
});

// <b>Waehrend nichts passiert, und waehrend etwas passiert.</b> Ein
// fensterloser Browser darf schlafen, wenn sich nichts aendert — eine
// Leerlaufmessung wuerde dann einen Takt zeigen, den es unter Last nicht
// gibt. Deshalb zweimal: einmal ruhig, einmal mit einer Flaeche, die sich
// bei jedem Bild aendert.
async function miss(beschreibung, mitLast) {
  const ergebnis = await sende('Runtime.evaluate', {
    expression: `new Promise(function (fertig) {
      var last = ${mitLast};
      var kasten = null;
      if (last) {
        kasten = document.createElement('div');
        kasten.style.cssText = 'position:fixed;left:0;top:0;width:200px;'
          + 'height:200px;z-index:9999;background:#f0f';
        document.body.appendChild(kasten);
      }
      var zeiten = [], letzte = null, i = 0;
      function schritt(t) {
        if (letzte !== null) { zeiten.push(t - letzte); }
        letzte = t;
        if (kasten) { kasten.style.background = (i++ % 2) ? '#f0f' : '#0ff'; }
        if (zeiten.length < 150) { requestAnimationFrame(schritt); }
        else {
          if (kasten) { kasten.remove(); }
          var s = zeiten.slice().sort(function (a, b) { return a - b; });
          fertig(JSON.stringify({
            p10: s[Math.floor(s.length * 0.1)],
            p50: s[Math.floor(s.length * 0.5)],
            p90: s[Math.floor(s.length * 0.9)],
            n: s.length
          }));
        }
      }
      requestAnimationFrame(schritt);
    })`,
    awaitPromise: true, returnByValue: true,
  });
  const z = JSON.parse(ergebnis.result.value);
  console.log(`${beschreibung.padEnd(22)} p10 ${z.p10.toFixed(2)} ms | `
    + `p50 ${z.p50.toFixed(2)} ms | p90 ${z.p90.toFixed(2)} ms  `
    + `= ${(1000 / z.p50).toFixed(1)} Bilder/s`);
}

await miss('im Leerlauf', false);
await miss('unter Last', true);
draht.close();
