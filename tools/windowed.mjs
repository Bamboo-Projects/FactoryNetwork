/**
 * Misst dieselbe Seite in einem echten Chrome-Fenster statt im fensterlosen
 * Betrieb.
 *
 * Aufruf:  node tools/windowed.mjs [sekunden] [variante]
 *
 * Voraussetzung: Ein Chrome laeuft mit --remote-debugging-port=9223 und hat
 * probe.html offen. Der Aufruf dazu steht in docs/stand-innerhalb-chromium.md.
 *
 * <b>Wozu der Vergleich.</b> Alles, was wir bisher gemessen haben, lief
 * fensterlos: Chromium malt in einen Puffer im Hauptspeicher, den wir abholen.
 * Ein gewoehnliches Fenster geht einen voellig anderen Weg — es haengt am
 * Bildtakt des Bildschirms und gibt seine Bilder direkt an das
 * Fenstersystem. Dieselbe Seite auf beiden Wegen zu messen trennt, was die
 * Seite kostet, von dem, was der fensterlose Betrieb kostet.
 *
 * <b>Was der Vergleich nicht ist.</b> Ein installiertes Chrome ist neuer als
 * unser Chromium 116. Der Vergleich zeigt eine Richtung und eine
 * Groessenordnung, keine auf die Millisekunde belastbare Differenz.
 */

const PORT = Number(process.env.FN_PORT || 9223);
const SEKUNDEN = Number(process.argv[2] || 30);
const VARIANTE = process.argv[3] || 'monaco-full';

function verbinde(url) {
  return new Promise((fertig, kaputt) => {
    const draht = new WebSocket(url);
    draht.onopen = () => fertig(draht);
    draht.onerror = (fehler) => kaputt(fehler);
  });
}

function anschluss(draht) {
  let naechste = 1;
  const offen = new Map();
  draht.onmessage = (nachricht) => {
    const daten = JSON.parse(nachricht.data);
    if (daten.id && offen.has(daten.id)) {
      if (daten.error) {
        console.error(`FEHLER auf ${offen.get(daten.id).verfahren}: `
          + JSON.stringify(daten.error));
      }
      offen.get(daten.id).fertig(daten.result);
      offen.delete(daten.id);
    }
  };
  return (verfahren, parameter = {}) =>
    new Promise((fertig) => {
      const id = naechste++;
      offen.set(id, { fertig, verfahren });
      draht.send(JSON.stringify({ id, method: verfahren, params: parameter }));
    });
}

const schlafe = (ms) => new Promise((f) => setTimeout(f, ms));

async function main() {
  const liste = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
  const seite = liste.find((e) => e.type === 'page' && e.url.includes('probe.html'))
    || liste.find((e) => e.type === 'page');
  if (!seite) {
    console.error('Keine Seite gefunden. Laeuft Chrome mit dem Debug-Port?');
    process.exit(1);
  }
  const version = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json();
  console.error(`Browser: ${version.Browser}`);
  console.error(`Seite:   ${seite.url}`);

  const draht = await verbinde(seite.webSocketDebuggerUrl);
  const sende = anschluss(draht);
  await sende('Page.enable');
  await sende('Runtime.enable');

  // Auf die gewuenschte Variante gehen, falls noch nicht offen.
  if (!seite.url.includes('v=' + VARIANTE)) {
    const ziel = seite.url.split('?')[0] + '?v=' + VARIANTE;
    await sende('Page.navigate', { url: ziel });
    await schlafe(4000);
  }
  // Die Fenstergroesse gehoert ins Protokoll: Sie entscheidet mit, wie viel
  // Flaeche jeder Anschlag ungueltig macht.
  const groesse = await sende('Runtime.evaluate', {
    expression: 'innerWidth + "x" + innerHeight + " dpr=" + devicePixelRatio',
    returnByValue: true,
  });
  console.error(`Flaeche: ${groesse?.result?.value}`);

  await sende('Runtime.evaluate', {
    expression: 'window.fnProbe && (fnProbe.fokus(), fnProbe.leeren()), 1',
  });
  await schlafe(500);

  const TEXT = '    let vorrat = storage.count(x)';
  const bis = Date.now() + SEKUNDEN * 1000;
  let i = 0;
  while (Date.now() < bis) {
    const zeichen = TEXT[i++ % TEXT.length];
    const code = zeichen.toUpperCase().charCodeAt(0);
    await sende('Input.dispatchKeyEvent', {
      type: 'keyDown', text: zeichen, unmodifiedText: zeichen,
      key: zeichen, windowsVirtualKeyCode: code,
    });
    await sende('Input.dispatchKeyEvent', {
      type: 'keyUp', key: zeichen, windowsVirtualKeyCode: code,
    });
    await schlafe(125);
  }

  const antwort = await sende('Runtime.evaluate', {
    expression: 'window.fnProbe ? fnProbe.zahlen() : null',
    returnByValue: true,
  });
  const z = antwort?.result?.value;
  if (!z) {
    console.error('Die Seite meldet keine Zahlen — ist es probe.html?');
    process.exit(1);
  }
  console.log(`\n=== Fenstermodus, ${VARIANTE}, ${z.n} Anschlaege ===`);
  console.log(`keydown→Inhalt   p50 ${z.inhalt[0].toFixed(1)} ms  p95 ${z.inhalt[1].toFixed(1)} ms`);
  console.log(`keydown→rAF1     p50 ${z.raf1[0].toFixed(1)} ms  p95 ${z.raf1[1].toFixed(1)} ms`);
  console.log(`keydown→rAF2     p50 ${z.raf2[0].toFixed(1)} ms  p95 ${z.raf2[1].toFixed(1)} ms  `
    + `max ${z.raf2[2].toFixed(1)} ms`);

  // Und die Form: Ein Bildtakt zeichnet Haeufungen, verteilte Rechenzeit
  // einen Huegel. Dieselbe Frage wie auf der Java-Seite, dieselbe Antwortform.
  const faecher = new Map();
  for (const ms of z.alle) {
    const f = Math.floor(ms / 4) * 4;
    faecher.set(f, (faecher.get(f) || 0) + 1);
  }
  const hoechste = Math.max(...faecher.values());
  console.log('\nVerteilung keydown→rAF2 (Faecher zu 4 ms):');
  for (const f of [...faecher.keys()].sort((a, b) => a - b)) {
    const n = faecher.get(f);
    console.log(`${String(f).padStart(4)}–${String(f + 4).padEnd(4)} ms | `
      + '#'.repeat(Math.round(n * 40 / hoechste)).padEnd(40)
      + ` ${String(n).padStart(4)} (${(n * 100 / z.alle.length).toFixed(1)} %)`);
  }
  draht.close();
}

main().catch((fehler) => { console.error(fehler); process.exit(1); });
