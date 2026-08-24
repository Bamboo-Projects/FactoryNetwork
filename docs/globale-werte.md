# Globale Werte — Entwurf

Ein Wert, den alle Dateien sehen, und eine Änderung, von der der Rest des
Programms erfährt.

Stand: 2026-08-24

---

## 1. Wofür

Heute hat ein Programm keinen Zustand, der einen Aufruf überdauert. `let`
lebt in seinem Block, ein Worker ist eine Zusage ohne Gedächtnis, und was
zwischen zwei Ereignissen erinnert werden soll, muss in eine Kiste gelegt
werden.

Was fehlt, sind die Fälle, in denen eine Fabrik einen **Modus** hat:
Nachtschicht, Wartung, Notlauf. Oder einen Zähler, oder eine Einstellung, die
an einem Ort steht und an zwanzig gelesen wird.

```
global modus = "tag"

worker erz {
    from grube
    to storage
    when modus == "tag"
}

display halle {
    row "Modus" modus
}

fn nachtschicht() {
    modus = "nacht"
}
```

Ein Aufruf von `nachtschicht()` legt den Worker schlafen und ändert die
Anzeige. Ohne dass irgendwo steht, dass er das tun soll.

---

## 2. Warum das fast nichts kostet

**Die Reaktivität ist schon da**, sie hat nur nichts, worauf sie sich beziehen
könnte:

- **Anzeigen** werden ohnehin laufend neu ausgewertet. Eine Zeile, die einen
  globalen Wert nennt, zeigt beim nächsten Bild den neuen — ohne Zutun.
- **`when` am Worker** wird bei jedem Durchgang geprüft. Ein Worker, dessen
  Bedingung kippt, schläft ein oder wacht auf, genau wie bei jeder anderen
  Bedingung.
- **Wartende Abläufe** brauchen nichts: `await` wartet auf **Ereignisse**
  (`FlowEngine.wake(String event, …)`), nicht auf Bedingungen. Ein Ablauf, der
  auf einen Wert warten will, wartet auf ein Ereignis, das jemand auslöst.

Damit ist „alles rechnet neu" keine neue Maschinerie, sondern die Folge davon,
dass die Sprache ihre Ausdrücke ohnehin laufend auswertet. **Es braucht keinen
Beobachter, keine Abhängigkeitsverfolgung und keine Benachrichtigung.**

Was gebaut werden muss, ist der Wert selbst: wo er steht, wer ihn ändern darf,
und was mit ihm beim Neustart und beim Programmwechsel passiert.

---

## 3. Eine eigene Deklaration

```
global modus = "tag"
global vorrat = 0
```

**Und kein `let` auf oberster Ebene.** Ein Programm besteht nur aus
Deklarationen; es gibt kein Hauptprogramm, das beim Laden losläuft. Ein `let`
draußen sähe aus wie eine Anweisung, die niemand ausführt.

`global` sagt außerdem, was es ist: etwas, das alle sehen. Bei `let` wäre die
Frage berechtigt, warum ein Name in einer anderen Datei sichtbar ist.

**Der Typ kommt aus dem Anfangswert**, wie überall in der Sprache. `modus` ist
ein Text, `vorrat` eine Zahl, und wer `modus = 3` schreibt, bekommt einen
Fehler beim Übersetzen.

**Der Anfangswert ist ein Literal**, kein Ausdruck. `global x = storage.count(…)`
wäre eine Rechnung ohne festen Zeitpunkt: Wann liefe sie? Beim Übernehmen,
beim Serverstart, bei jedem Laden des Chunks? Ein Literal hat diese Frage
nicht.

---

## 4. Wer schreiben darf

**Funktionen und Ereignisblöcke.** Also überall dort, wo heute schon
Anweisungen stehen.

**Worker nicht.** Ein Worker ist eine Zusage über einen Dauerzustand und hat
keinen Ort, an dem eine Anweisung stünde. Wer bei einer Bewegung etwas merken
will, hängt ein Ereignis daran.

**Anzeigen nicht.** Sie zeigen an. Eine Anzeige, die beim Zeichnen etwas
ändert, wäre ein Bild, das sein eigenes Motiv verschiebt.

### Schreiben ist sofort sichtbar

Innerhalb eines Ticks gibt es keine Zwischenstände: Wer schreibt, hat
geschrieben, und wer danach liest, liest den neuen Wert. Das ist die einfachste
Regel, und sie hält, weil alles auf einem Thread läuft.

**Zwei Abläufe, die im selben Tick schreiben,** überschreiben einander in der
Reihenfolge, in der sie laufen. Der letzte gewinnt. Das ist dieselbe Regel wie
beim Entwurf im Editor und bei den Dateien neben der Welt — wer zuletzt
schreibt, gewinnt — und sie ist hier ehrlicher als eine Sperre, weil ein Tick
keine Dauer hat, in der jemand warten könnte.

---

## 5. Wo der Wert lebt

**Im Controller**, neben dem Programm und dem Entwurf. Er gehört zum Netz, wie
der Stromvorrat und der Speicherinhalt.

**Er überlebt den Serverneustart.** `ValueCodec` serialisiert Werte bereits —
das ist derselbe Weg, über den wartende Abläufe ihre Variablen mitnehmen. Ein
globaler Wert ist dafür der einfachere Fall: eine Karte von Namen auf Werte,
ohne Aufrufstapel.

Damit stimmt für globale Werte, was für Abläufe schon gilt: Ein Serverneustart
ist kein Ereignis, das ein Programm bemerkt.

### Beim Programmwechsel

Wer ein neues Programm übernimmt, hat womöglich andere globale Werte erklärt.
Die Regel:

- **Gleicher Name, gleicher Typ:** Der Wert bleibt. Wer `modus` von `"tag"` auf
  `"nacht"` gestellt hat und dann einen Worker ändert, will nicht wieder bei
  `"tag"` anfangen.
- **Neuer Name:** Der Anfangswert aus der Deklaration.
- **Name weg:** Der Wert wird vergessen.
- **Gleicher Name, anderer Typ:** Der Anfangswert aus der neuen Deklaration.
  Ein Text, der plötzlich als Zahl gelesen wird, ist kein erhaltenswerter
  Zustand.

Das ist dieselbe Haltung wie bei den Worker-Zuständen: Was noch passt, bleibt;
was nicht mehr passt, fängt neu an.

---

## 6. Was man sieht

Ein eigener Abschnitt im Netz-Reiter wäre naheliegend: alle globalen Werte mit
ihrem aktuellen Stand. **Das ist mehr als Bequemlichkeit** — ein Wert, den man
nicht sehen kann, ist beim Fehlersuchen wertlos, und `log()` in eine Schleife
zu schreiben ist der Umweg, den man sonst nimmt.

Ob man ihn dort auch **ändern** können soll, ist offen. Dafür spricht das
Ausprobieren; dagegen, dass ein Programm dann einen Zustand hat, den niemand
im Code findet.

---

## 7. Bewusst nicht dabei

**Abgeleitete Werte.** `global doppelt = vorrat * 2` gibt es nicht — das wäre
eine Rechnung, die bei jedem Lesen laufen müsste, und damit die
Abhängigkeitsverfolgung, die dieser Entwurf gerade vermeidet. Wer eine
Rechnung will, schreibt eine Funktion.

**`await` auf eine Bedingung.** `await modus == "nacht"` ist verlockend,
braucht aber, dass jede Zuweisung prüft, wer darauf wartet — genau der
Beobachter-Mechanismus, den Abschnitt 2 einspart. Wer auf eine Änderung warten
will, löst beim Ändern ein Ereignis aus und wartet darauf.

**Globale Werte über Netze hinweg.** Ein Wert gehört einem Controller. Zwei
Netze teilen nichts.

---

## 8. Offene Punkte

- **Ob es Konstanten braucht.** `global` ist veränderlich; ein fester Wert
  (`const rate = 64`) wäre etwas anderes und vielleicht der häufigere Fall.
- **Ob der Netz-Reiter Schreibrechte bekommt** (Abschnitt 6).
- **Ob Listen und Karten als globale Werte erlaubt sind.** Die Sprache kennt
  Listen (`storage.items()`), aber eine veränderliche globale Liste wirft
  Fragen auf, die ein Text oder eine Zahl nicht hat: Wer darf anhängen, was
  passiert beim Programmwechsel, wie groß darf sie werden.
