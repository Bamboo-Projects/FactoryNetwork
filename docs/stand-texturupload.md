# Texturupload: Stand nach A bis C

**31.08., morgens.** Der Weg von Chromium in eine OpenGL-Textur steht, ist
nachgewiesen und gemessen. Was hier steht, ist aus dem Protokoll abgelesen und
nicht geschätzt.

---

## A — Das Bild kommt richtig an

`WebSelfTest` lädt eine Seite mit vier bekannten Farbfeldern, liest die fertige
Textur mit `glGetTexImage` von der Grafikkarte zurück und vergleicht die
Bildpunkte. Kein Auge entscheidet.

```
Selbsttest: oben links stimmt — rgb(255, 0, 0)
Selbsttest: oben rechts stimmt — rgb(0, 255, 0)
Selbsttest: unten links stimmt — rgb(0, 0, 255)
Selbsttest: unten rechts (halbdurchsichtig) rgba(128, 128, 128, 128)
Selbsttest: 1 Bilder, erstes nach 258 ms, 1 Uploads, 256 KB gesamt
```

Drei Fragen sind damit beantwortet, die „sieht richtig aus" nicht beantwortet
hätte:

- **Farbkanäle.** Rot liegt oben links, nicht Blau. `GL_BGRA` mit
  `GL_UNSIGNED_INT_8_8_8_8_REV` übernimmt Chromiums Anordnung, ohne
  umzusortieren.
- **Zeilenordnung.** Blau liegt unten, wie in der Vorlage. Der Puffer geht
  ungespiegelt in die Textur.
- **Alpha.** Es kommt an.

### Ein Befund, der Folgen hat: vormultipliziertes Alpha

Das vierte Feld ist als `rgba(255,255,255,0.5)` beschrieben — halbdurchsichtiges
**Weiß**. Angekommen ist `rgb(128,128,128)` mit `a=128`.

**Chromium liefert vormultipliziertes Alpha.** Wer das mit dem üblichen
`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` blendet, multipliziert ein zweites Mal;
halbdurchsichtiges Weiß käme dann als Grau heraus. Richtig ist:

```java
GL_ONE, GL_ONE_MINUS_SRC_ALPHA
```

Das gilt, sobald Schritt D die Textur zeichnet. Bis dahin ist es notiert und
nicht vergessen.

### Was A noch nicht beweist

Der Selbsttest belegt den Weg **Puffer → Textur**. Wie das Bild **auf dem
Schirm** steht, hängt an den Texturkoordinaten beim Zeichnen — OpenGL zählt
Texturen von unten, Chromium malt von oben. Gespiegelt wird bewusst nicht beim
Hochladen (das kostete eine Kopie je Bild), sondern beim Zeichnen. Das ist eine
Frage für Schritt D.

---

## Drei Fallen auf dem Weg dahin

Alle drei sahen aus wie ein kaputter Renderpfad und waren es nicht:

1. **`resize()` vor `createImmediately()`.** Die Größenmeldung ging an einen
   Browser, den es noch nicht gab. Sie verpuffte — und weil das Rechteck danach
   schon stimmte, verhinderte die Kurzschlussprüfung den zweiten Versuch.
   Ergebnis: ein Browser, der nie malt. Die richtige Reihenfolge ist
   `setCloseAllowed()` → `createImmediately()` → `resize()`.
2. **`data:`-URL.** Chromium verweigert sie als Hauptdokument seit Version 60,
   als Schutz gegen Phishing. Der Browser lud gar nichts. Eine Datei-URL tut
   es genauso.
3. **Warten auf ein zweites Bild.** Eine Seite, die sich nicht ändert, malt
   genau einmal. Der Prüflauf wartete auf zwei und wartete deshalb für immer.
   Der Fehler lag im Prüflauf, nicht im Weg.

Punkt 3 ist zugleich die beste Nachricht dieses Spikes — siehe die Messung.

---

## Ein Befund vor jeder Messung: die Bildrate ist nicht einstellbar

Nachgesehen im Jar, das MCEF mitbringt, nicht im aktuellen Quelltext von JCEF:

```
org.cef.CefSettings         kein windowless_frame_rate
org/cef/browser/            keine CefBrowserSettings
CefBrowser_N.createBrowser(handler, parent, url, transparent, osr, ctx)
```

`createBrowser` nimmt **gar keine Browser-Einstellungen** entgegen. Was CEF an
Bildern liefert, ist seine Voreinstellung. Das hat zwei Folgen:

- `FramePacer` und `BrowserVisibility` können nur **drosseln**, nie
  beschleunigen. `FOREGROUND(60)` ist keine erreichbare Zahl, sondern eine
  offene Tür für den Fall, dass eine spätere Fassung die Sperre löst.
- Was die Messung an Bildern je Sekunde findet, ist ein **Befund** und keine
  Konfiguration, die wir gewählt hätten.

---

## B und C — Die Messung

`WebBenchmark`, angeworfen mit `./gradlew runClient -Pbenchmark`. Drei
Szenarien, alle bei **1920×1080**, je sechs Sekunden, jeweils nach dem ersten
Bild neu angesetzt — das erste Bild ist immer ein Vollbild und gehört zum
Aufbau, nicht zur Messung.

| Szenario | Bilder/s | Uploads | davon Vollbild | Ausschnitte | KB je Bild | Anteil eines Vollbilds | MB/s | p50 | p95 | max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Ruhe** — statische Seite | 0,0 | 0 | 0 | 0 | 0,0 | 0 % | 0,0 | — | — | — |
| **Kleine Änderung** — 24×24 blinkt | 10,0 | 60 | **0** | 60 | **2,3** | **0,03 %** | 0,0 | 266 µs | 511 µs | 2169 µs |
| **Vollflächig** — jedes Bild neu | 30,1 | 181 | 181 | 0 | 8100,0 | 100 % | **238,1** | 12,7 ms | 14,5 ms | 19,8 ms |

Ein zweiter Lauf in einer geladenen Welt (siehe unten) bestätigt jede Zeile:
Ruhe 0 Bilder, kleine Änderung 60 Ausschnitte zu 2,3 KB bei p50 231 µs,
vollflächig 30,0 Bilder/s zu 237,1 MB/s bei p50 14,7 ms. Die Uploadzeit liegt
dort höher, weil die Grafikkarte nebenher eine Welt zeichnet — dieselbe Arbeit
unter Last.

### Was die drei Zeilen sagen

**Ein ruhender Editor kostet exakt nichts.** Kein Bild, kein Upload, kein Byte
über den Bus — sechs Sekunden lang, bei voller Auflösung. Das ist der Zustand,
in dem ein Editor die allermeiste Zeit verbringt, und er ist gratis. Diese
Zeile beantwortet die Kostenfrage besser als jede andere.

**Ausschnitte funktionieren, und zwar pixelgenau.** Das blinkende Kästchen ist
24×24 Bildpunkte, also 576 × 4 Byte = **2304 Byte = 2,25 KB**. Gemessen wurden
2,3 KB. Chromium rundet die Dirty-Rects nicht auf und fasst sie nicht zusammen;
`GL_UNPACK_ROW_LENGTH` überträgt genau den Ausschnitt aus dem Vollbildpuffer,
ohne Zwischenkopie. **Null von sechzig Uploads gingen als Vollbild.** Der
Unterschied zum Vollbild ist der Faktor **3522**.

Belegt ist das für diesen einen Fall — ein einzelnes Rechteck auf sonst
ruhigem Grund. Ob Chromium mehrere gleichzeitige Änderungen zusammenfasst
oder einzeln meldet, sagt diese Messung nicht.

**Die Obergrenze ist 30,1 Bilder je Sekunde und 238 MB/s.** Das ist CEFs
Voreinstellung, gemessen und nicht gewählt. Bei 1080p sind das 8,1 MB je Bild.

### Die Uploadzeit ist die eigentliche Rechnung

12,7 ms im Median für ein Vollbild bei 1080p sind **637 MB/s effektiv** — für
einen PCIe-Bus wenig. Der Grund steht im Aufruf: `glTexImage2D` schiebt
synchron aus dem Hauptspeicher, und der Treiber wartet. Ein Pixel Buffer Object
oder dauerhaft eingeblendeter Speicher würde das entkoppeln.

Für den Vergleich: derselbe Weg mit einem 2,3-KB-Ausschnitt kostet **266 µs**
im Median — knapp das Fünfzigfache weniger Zeit bei dem Dreitausendfachen
weniger Bytes. Die beiden Faktoren klaffen um siebzig auseinander, und was
dazwischen liegt, ist fester Aufwand je Aufruf. Genau deshalb wären viele
kleine Ausschnitte je Bild teurer als ein einziger.

---

## Was der Spieler davon merkt

Gemessen als Abstand zweier Bilder in `RenderFrameEvent.Post`, Baseline
unmittelbar davor in derselben Sitzung und derselben Szene.

**Der erste Lauf war unbrauchbar, und das ist selbst ein Befund.** Er lief im
Hauptmenü, und dort deckelt Minecraft die Bildrate hart auf sechzig —
unabhängig von VSync (aus) und der eingestellten Sperre (120). Die Bildzeit
klebte bei 16,64 ms, also exakt 60,1 Bildern je Sekunde. Ein Browser, der
weniger als das kostet, hätte dort gratis ausgesehen.

Die Messung wartet deshalb jetzt auf eine geladene Welt und sagt es, solange
sie wartet.

**Der zweite Lauf startet direkt in eine Welt.** Gemessen wird die Bildzeit
während des vollflächigen Szenarios — dem teuersten, den es gibt.

| | ohne Browser | mit Browser (vollflächig 1080p) | Unterschied |
|---|---:|---:|---:|
| Bildzeit Median | 17,9 ms | 31,4 ms | **+13,6 ms** |
| Bildzeit p95 | 32,0 ms | 40,0 ms | +8,1 ms |
| entspricht | **56,0 Bilder/s** | **31,8 Bilder/s** | −24,2 |

Dass der p95-Unterschied **kleiner** ausfällt als der im Median, liegt nicht am
Browser, sondern an der Baseline: Sie wurde kurz nach dem Weltbeitritt
gemessen, und dort lädt Minecraft noch Chunks — ein einzelner Ausreißer von
125 ms steht im Protokoll. Die Baseline ist am oberen Ende also zu schlecht,
und der Vergleich dort entsprechend zu freundlich. Der Median ist die
belastbarere der beiden Zeilen.

**Die entscheidende Beobachtung steht in der Gegenüberstellung zweier Zahlen:**
Der Upload dauert im Median 14,7 ms, und die Bildzeit steigt um 13,6 ms. Das
ist praktisch dasselbe. Der Bildweg kostet also **nicht Chromium** — das läuft
in einem eigenen Prozess und nebenher —, sondern **den blockierenden Upload im
Render-Thread**. Minecraft steht still, solange `glTexImage2D` schiebt.

Genau das ist die Stelle, an der ein Pixel Buffer Object hilft: Er entkoppelt
das Schieben vom Warten. Solange kein Fall auftritt, der wirklich vollflächig
animiert, ist es das aber nicht wert.

### Und was das für den wirklichen Fall heißt

Der Editor ist nicht das vollflächige Szenario, sondern das mit dem blinkenden
Cursor. Dort kostet ein Upload **230 µs**, und es fallen zehn je Sekunde an:

```
10 × 230 µs = 2,3 ms je Sekunde = 0,23 % der Rechenzeit
```

Bei 56 Bildern je Sekunde und 17,9 ms Bildzeit ist das **ein Achtzigstel eines
einzigen Bildes**. Ein tippender Spieler merkt davon nichts. Ein ruhender
Editor kostet gar nichts.

Zwischen diesen beiden Zahlen liegt der ganze Spike: **0,23 % im Alltag,
43 % im schlimmsten Fall.** Und der schlimmste Fall ist eine Seite, die
absichtlich jedes Bild vollständig neu malt — kein Editor tut das.

---

## Das Postfach wird im heutigen Weg umgangen — mit Absicht

`FrameSlot` liegt gebaut und geprüft daneben und steht in keinem der Aufrufe.
Der Grund steht im Javadoc von `BrowserSession` und gehört hierher:

MCEF pumpt Chromiums Nachrichtenschleife per Mixin in `GameRenderer.render`.
`onPaint` kommt damit **im Render-Thread** an, der Zeichenkontext gilt, und der
geliehene Puffer ist in genau diesem Moment gültig. Es gibt kein
Erzeuger-Verbraucher-Paar über Threadgrenzen — also auch nichts, was ein
Postfach lösen müsste. Eine Kopie in ein eigenes Bild kostete bei 1080p
achteinhalb Megabyte je Bild und löste ein Problem, das es hier nicht gibt.

Das Postfach bleibt, weil es gebraucht wird, sobald Chromium einen eigenen
Takt bekommt oder der Aufruf woanders ankommt als die Textur. Was es kostet,
messen wir dann gegen diese Zahlen: Der heutige Weg ist die untere Grenze.

---

## Die Leihfrist steht im Typmodell

`BorrowedFrame` ist **kein** `BrowserFrame` und mit ihm nicht verwandt. Das ist
keine Nachlässigkeit, sondern die Absicht:

```java
slot.offer(borrowed);   // übersetzt nicht
```

Ein geliehener Puffer kann damit nirgends landen, wo er den Aufruf überleben
müsste. Der Weg zum Besitz führt über `toOwned()`, und der kopiert. Der
wichtigste Prüflauf dieser Klasse ist der, den man nicht schreiben kann.

---

## Was offen bleibt

1. **Die Bildorientierung auf dem Schirm** ist ungeprüft (siehe A). Gehört zu
   Schritt D.
2. **Das Blenden mit vormultipliziertem Alpha** ist notiert, aber noch nirgends
   angewandt — es gibt noch nichts, was zeichnet.
3. **Aufklappende Auswahlfelder** (`popup == true` in `onPaint`) werden
   verworfen. Sie brauchen eine zweite Textur und eine Stelle, die sie
   darüberlegt.
4. **Kein Pixel Buffer Object.** Bei 12,7 ms je Vollbild ist das die nächste
   Stelle, an der es etwas zu holen gibt — aber erst, wenn ein Fall auftritt,
   der wirklich vollflächig animiert.
5. **`upload(BrowserFrame)` wirft.** Der besitzende Pfad ist gebaut, aber
   ungemessen; bis er gemessen ist, soll niemand versehentlich darüber laufen.

---

## Wie man es nachstellt

```
./gradlew runClient -Pbenchmark
```

Startet direkt in eine Welt, führt erst den Selbsttest aus, dann die drei
Szenarien, und schreibt alles nach `run/logs/latest.log`. Eine andere Welt:
`-Pworld="Name der Welt"`.
