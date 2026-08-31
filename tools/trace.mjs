/**
 * Nimmt einen Chromium-Trace auf, waehrend getippt wird.
 *
 * Aufruf:  node tools/trace.mjs [sekunden]
 *
 * Voraussetzung: Der Client laeuft mit -Pdevtools, und die Oberflaeche ist
 * offen. Der Zugang ist 127.0.0.1:9222.
 *
 * Warum ueber das Protokoll getippt wird und nicht ueber unseren Java-Weg:
 * Der Trace soll zeigen, was Chromium mit einem Tastendruck macht — nicht,
 * wie er dorthin gekommen ist. Ueber Input.dispatchKeyEvent geht er denselben
 * Weg durch Chromiums Eingabeverarbeitung, aber ohne unseren Anteil davor.
 */

const PORT = 9222;
const SEKUNDEN = Number(process.argv[2] || 20);

/** Die Kategorien, die Chromium fuer das Zeichnen mitschreibt. */
const KATEGORIEN = [
  'devtools.timeline',
  'blink',
  'blink.user_timing',
  'cc',
  'gpu',
  'viz',
  'disabled-by-default-devtools.timeline',
  'disabled-by-default-devtools.timeline.frame',
  'benchmark',
  'toplevel',
].join(',');

async function seiten() {
  const antwort = await fetch(`http://127.0.0.1:${PORT}/json`);
  return antwort.json();
}

function verbinde(url) {
  return new Promise((fertig, kaputt) => {
    const draht = new WebSocket(url);
    draht.onopen = () => fertig(draht);
    draht.onerror = (fehler) => kaputt(fehler);
  });
}

/** Ein Absender, der auf Antworten wartet und Ereignisse durchreicht. */
function anschluss(draht, aufEreignis) {
  let naechste = 1;
  const offen = new Map();
  draht.onmessage = (nachricht) => {
    const daten = JSON.parse(nachricht.data);
    if (daten.id && offen.has(daten.id)) {
      // Fehlerantworten nicht verschlucken: Ein abgelehntes Tracing.start
      // sieht sonst aus wie ein erfolgreiches, das keine Daten liefert.
      if (daten.error) {
        console.error(`FEHLER auf ${offen.get(daten.id).verfahren}: `
          + JSON.stringify(daten.error));
      }
      offen.get(daten.id).fertig(daten.result);
      offen.delete(daten.id);
    } else if (daten.method) {
      aufEreignis(daten.method, daten.params);
    }
  };
  return (verfahren, parameter = {}) =>
    new Promise((fertig) => {
      const id = naechste++;
      offen.set(id, { fertig, verfahren });
      draht.send(JSON.stringify({ id, method: verfahren, params: parameter }));
    });
}

async function schlafe(ms) {
  return new Promise((fertig) => setTimeout(fertig, ms));
}

async function main() {
  const liste = await seiten();
  const seite = liste.find((eintrag) => eintrag.type === 'page');
  if (!seite) {
    console.error('Keine Seite offen. Laeuft die Oberflaeche?');
    console.error(JSON.stringify(liste, null, 2));
    process.exit(1);
  }
  console.error(`Seite: ${seite.url}`);

  // <b>Zwei Verbindungen, und das ist keine Umstaendlichkeit.</b> Tracing
  // gehoert dem Browser, nicht der Seite: Ueber die Seitenverbindung
  // angefordert, nimmt Chromium den Aufruf entgegen und schickt nie ein
  // Ereignis. Genau so ist der erste Versuch ausgegangen — null Ereignisse,
  // keine Fehlermeldung.
  const version = await (await fetch(`http://127.0.0.1:${PORT}/json/version`)).json();

  const ereignisse = [];
  let fertigGemeldet = null;
  const browserDraht = await verbinde(version.webSocketDebuggerUrl);
  const sendeBrowser = anschluss(browserDraht, (verfahren, parameter) => {
    if (verfahren === 'Tracing.dataCollected' && parameter.value) {
      ereignisse.push(...parameter.value);
    } else if (verfahren === 'Tracing.tracingComplete') {
      if (fertigGemeldet) { fertigGemeldet(); }
    }
  });

  const draht = await verbinde(seite.webSocketDebuggerUrl);
  const sende = anschluss(draht, () => {});

  await sende('Page.enable');
  await sende('Runtime.enable');

  // Den Editor anklicken, damit die Eingabe irgendwo landet.
  await sende('Runtime.evaluate', {
    expression:
      "(window.fnEditor && fnEditor.focus()) || " +
      "(document.querySelector('textarea') && " +
      "document.querySelector('textarea').focus()), " +
      "document.activeElement.tagName",
    returnByValue: true,
  }).then((ergebnis) => console.error(`Fokus: ${ergebnis?.result?.value}`));

  console.error(`Trace laeuft, ${SEKUNDEN} s ...`);
  await sendeBrowser('Tracing.start', {
    categories: KATEGORIEN,
    transferMode: 'ReportEvents',
    options: 'sampling-frequency=10000',
  });

  // Acht Zeichen je Sekunde, wie in allen Messungen davor.
  const TEXT = '    let vorrat = storage.count(x)';
  const bis = Date.now() + SEKUNDEN * 1000;
  let i = 0;
  while (Date.now() < bis) {
    const zeichen = TEXT[i++ % TEXT.length];
    await sende('Input.dispatchKeyEvent', {
      type: 'keyDown', text: zeichen, unmodifiedText: zeichen,
      key: zeichen, windowsVirtualKeyCode: zeichen.toUpperCase().charCodeAt(0),
    });
    await sende('Input.dispatchKeyEvent', {
      type: 'keyUp', key: zeichen,
      windowsVirtualKeyCode: zeichen.toUpperCase().charCodeAt(0),
    });
    await schlafe(125);
  }

  // <b>Auf das Abschlusssignal warten, nicht auf die Uhr.</b> Chromium
  // schickt die Daten nach dem Ende in Schueben; wie lange das dauert, haengt
  // an ihrer Menge. Bei sechs Sekunden Aufzeichnung reichten drei Sekunden
  // Wartezeit, bei fuenfzehn nicht mehr — und die Auswertung stand vor einer
  // leeren Liste, ohne dass irgendetwas einen Fehler gemeldet haette.
  const abgeschlossen = new Promise((fertig) => { fertigGemeldet = fertig; });
  await sendeBrowser('Tracing.end');
  await Promise.race([abgeschlossen, schlafe(60000)]);
  await schlafe(500);
  draht.close();
  browserDraht.close();

  console.error(`${ereignisse.length} Ereignisse gesammelt.`);
  auswerten(ereignisse);
  // Der Takt aus Chromiums eigener Sicht — an dem Ereignis, das ein fertiges
  // Bild nach draussen gibt, und an dem, mit dem ein neues beginnt.
  taktAbstaende(ereignisse, 'Display::DrawAndSwap');
  taktAbstaende(ereignisse, 'ProxyMain::BeginMainFrame');
}

/**
 * Fasst zusammen, wo die Zeit hingeht.
 *
 * Gezaehlt werden nur vollstaendige Ereignisse mit Dauer ("X"). Chromium
 * schachtelt sie; die Summe kann daher ueber der Laufzeit liegen. Was zaehlt,
 * ist das Verhaeltnis der Posten zueinander.
 */
function auswerten(ereignisse) {
  const nachName = new Map();
  for (const e of ereignisse) {
    if (e.ph !== 'X' || typeof e.dur !== 'number') { continue; }
    const eintrag = nachName.get(e.name) || { name: e.name, summe: 0, anzahl: 0,
                                              laengste: 0, kat: e.cat };
    eintrag.summe += e.dur;
    eintrag.anzahl++;
    eintrag.laengste = Math.max(eintrag.laengste, e.dur);
    nachName.set(e.name, eintrag);
  }
  const sortiert = [...nachName.values()].sort((a, b) => b.summe - a.summe);

  console.log('\n=== Die dreißig teuersten Posten ===');
  console.log('Summe(ms)  Anzahl  Mittel(ms)  Laengste(ms)  Name');
  for (const e of sortiert.slice(0, 400)) {
    console.log(
      String((e.summe / 1000).toFixed(1)).padStart(9) +
      String(e.anzahl).padStart(8) +
      String((e.summe / e.anzahl / 1000).toFixed(2)).padStart(12) +
      String((e.laengste / 1000).toFixed(2)).padStart(14) +
      '  ' + e.name);
  }
}

/**
 * Wie weit Chromiums Bilder auseinanderliegen.
 *
 * <b>Warum das die eigentliche Frage beantwortet.</b> Die Summen oben sagen,
 * wie lange Chromium arbeitet. Sie sagen nicht, wie lange es wartet — und wenn
 * die gemessene Strecke von aussen doppelt so lang ist wie alle Arbeit
 * zusammen, ist genau das die offene Groesse. Der Abstand zwischen zwei
 * Bildern zeigt sie unmittelbar: Ein fensterloser Browser malt nicht, wann er
 * fertig ist, sondern wann der naechste Takt kommt.
 */
function taktAbstaende(ereignisse, name) {
  const zeiten = ereignisse
    .filter((e) => e.name === name && typeof e.ts === 'number')
    .map((e) => e.ts)
    .sort((a, b) => a - b);
  if (zeiten.length < 3) {
    console.log(`\n${name}: zu wenige Ereignisse (${zeiten.length})`);
    return;
  }
  const abstaende = [];
  for (let i = 1; i < zeiten.length; i++) {
    const ms = (zeiten[i] - zeiten[i - 1]) / 1000;
    // Null-Abstaende sind derselbe Takt, doppelt gemeldet — kein Warten.
    if (ms > 0.5) { abstaende.push(ms); }
  }
  abstaende.sort((a, b) => a - b);
  const p = (q) => abstaende[Math.min(abstaende.length - 1,
    Math.floor(q / 100 * abstaende.length))];
  console.log(`\n=== Abstand zwischen zwei ${name} ===`);
  console.log(`n=${abstaende.length}  p50 ${p(50).toFixed(1)} ms  `
    + `p90 ${p(90).toFixed(1)} ms  Mittel `
    + `${(abstaende.reduce((a, b) => a + b, 0) / abstaende.length).toFixed(1)} ms`);
  const faecher = new Map();
  for (const ms of abstaende) {
    const f = Math.floor(ms / 4) * 4;
    faecher.set(f, (faecher.get(f) || 0) + 1);
  }
  const hoechste = Math.max(...faecher.values());
  for (const f of [...faecher.keys()].sort((a, b) => a - b)) {
    const n = faecher.get(f);
    if (n / abstaende.length < 0.01) { continue; }
    console.log(`${String(f).padStart(4)}–${String(f + 4).padEnd(4)} ms | `
      + '#'.repeat(Math.round(n * 40 / hoechste)).padEnd(40)
      + ` ${String(n).padStart(4)} (${(n * 100 / abstaende.length).toFixed(1)} %)`);
  }
}

main().catch((fehler) => {
  console.error(fehler);
  process.exit(1);
});
