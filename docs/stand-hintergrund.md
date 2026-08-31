# Minecraft als Hintergrund, echtes CSS-Glas darüber: Stand nach Schritt E

**31.08.** Minecrafts Bild geht als Element in die Seite, Chromium filtert es
mit `backdrop-filter`, und das fertige Bild kommt zurück auf den Schirm. Beides
ist nachgewiesen, nicht behauptet.

---

## 1. Wo im Renderpfad aufgenommen wird

Minecrafts Ablauf in `GameRenderer.render`, nachgesehen im Quelltext von
1.21.1:

```
renderLevel(deltaTracker)                    ← die Welt
getMainRenderTarget().bindWrite(true)
RenderSystem.clear(256, …)                   ← nur die Tiefe, nicht die Farbe
gui.render(guiGraphics, deltaTracker)        ← Hotbar, Fadenkreuz, Anzeigen
minecraft.getOverlay()?.render(…)
screen.renderWithTooltip(…)                  ← unser Bildschirm
```

**Aufgenommen wird in der ersten Zeile von `BrowserScreen.render()`**, vor
jedem eigenen Strich. Dort steht im Hauptziel genau das, was der Auftrag
verlangt: **Welt und normales Spielbild, ohne unsere Oberfläche.**

| Frage | Antwort |
|---|---|
| Welcher Framebuffer? | `Minecraft.getMainRenderTarget()` — Minecrafts Hauptziel, in das auch alles andere geht |
| Wann? | zwischen `gui.render(…)` und dem Zeichnen des Bildschirms |
| GUI/HUD enthalten? | **ja** — Hotbar und Fadenkreuz sind auf den Bildern zu sehen |
| Nur die Welt? | nein, und das ist so gewollt |
| Rückkopplung? | **ausgeschlossen durch die Reihenfolge** |

**Zur Rückkopplung, weil sie die wichtigste Regel war.** Der Browser wird
*nach* dem Aufnahmepunkt gezeichnet. Was aufgenommen wird, kann ihn deshalb
nie enthalten — auch nicht ein Bild später, denn dann wird wieder vorher
aufgenommen. Es braucht keine Erkennung, kein zweites Ziel und keinen
Sonderzustand. Die Reihenfolge allein genügt, und sie kann nicht versehentlich
umkippen: Ein Bildschirm zeichnet immer nach dem HUD.

`renderBackground()` — Minecrafts Abdunklung — läuft **nach** der Aufnahme.
Sonst wäre der Hintergrund um seinen eigenen Schleier dunkler, und mit jedem
Bild ein bisschen mehr.

---

## 2. Die vollständige Pipeline

```
Minecrafts Bild auf der Grafikkarte  (Hauptziel, RGBA8)
  │
  ├─ glBlitFramebuffer, GL_LINEAR        Grafikkarte → Grafikkarte, verkleinert
  │                                       0,2–2,0 ms
  ▼
kleines TextureTarget
  │
  ├─ glGetTexImage                       Grafikkarte → Hauptspeicher   ← teuer
  │                                       0,8 ms (0,25×) … 11 ms (1,0×)
  ▼
Bildpunkte im Hauptspeicher
  │
  ├─ Kodierung                           Hauptspeicher → Hauptspeicher
  │                                       PNG 6–54 ms │ BMP 0,04–0,24 ms
  ▼
byte[] im Ablageort                      eine atomare Zuweisung, keine Sperre
  │
  ├─ Chromium fragt auf seinem Netzwerk-Thread ab
  ├─ readResponse kopiert in Chromiums Puffer
  ▼
Chromium dekodiert und lädt auf seine Grafikkarte
  │
  ├─ rendert die Seite samt Filtern
  ├─ onPaint mit Vollbild-Ausschnitt
  ▼
unsere Textur (Schritt D)               Hauptspeicher → Grafikkarte
  │
  ▼
Minecraft zeichnet sie
```

**Kopien im Einzelnen, je Aufnahme:**

1. Grafikkarte → Grafikkarte (Blit, verkleinernd)
2. Grafikkarte → Hauptspeicher (`glGetTexImage`) — **die teure**
3. Hauptspeicher → Hauptspeicher (Kodierung; bei BMP nur ein `arraycopy`)
4. Hauptspeicher → Chromium (`readResponse`)
5. Chromium: Dekodieren → eigene Grafikkarte
6. Chromiums Bild → unser Hauptspeicher (`onPaint`, Schritt D)
7. Hauptspeicher → Grafikkarte (`glTexImage2D`, Schritt D)

**Sieben Kopien für einen Rundlauf**, davon vier über die Bus-Grenze. Ein
geteiltes Texturobjekt spart die Schritte 2, 5, 6 und 7 — das ist die Rechnung
für später, ausdrücklich nicht für jetzt.

---

## 3. Schema und Auslieferung

`mc://frame/current?v=<n>`, beantwortet von einem `CefResourceHandler`.

**Die Fragen aus dem Auftrag, der Reihe nach:**

- **Wann registriert MCEF eigene Schemas?** Unmittelbar nach `CefUtil.init()`,
  im selben Aufruf, in dem Chromium hochkommt — `MCEF.initialize()` ruft dort
  `registerSchemeHandlerFactory("mod", "", …)`.
- **Nach der Initialisierung nachregistrierbar?** **Ja**, nachgewiesen. Der
  Handler wird angenommen und beantwortet Anfragen.
- **Muss es vorher in `onRegisterCustomSchemes` stehen?** **Nein**, nicht für
  diesen Zweck. Ein Bild wird geladen, auch wenn das Schema als
  nicht-standardisiert gilt.
- **Ein vorhandenes Schema verwenden?** Nicht nötig. Für den Fall, dass später
  volle Herkunfts-Semantik gebraucht wird — für `fetch`, relative Adressen,
  CORS —, bliebe der Weg über eine synthetische Domain auf `https`
  (`registerSchemeHandlerFactory("https", "mc-frame.internal", …)`). Ungeprüft,
  weil ungebraucht.
- **Herkunft und CORS?** Ein selbst angemeldetes Schema ohne Deklaration hat
  **keine Herkunft**. Für ein `<img>` spielt das keine Rolle; für `fetch` oder
  ein `<canvas>`, aus dem gelesen wird, spielte es eine.

**Für eine eigene CEF-Runtime später** wäre `onRegisterCustomSchemes` der
richtige Ort: Dort ließe sich `mc` als standardisiert, sicher und CORS-fähig
deklarieren. Das geht nur **vor** der Initialisierung, und dort kommen wir bei
MCEF nicht hin, ohne es zu forken. Solange nur Bilder fließen, fehlt nichts.

### Ein Fehler, der teuer war

Der erste Entwurf gab jedem Bildschirm einen eigenen Ablageort. Chromium nimmt
aber **eine** Anmeldung je Schema entgegen, und die erste bleibt stehen. Der
Nachweis meldete seinen an, räumte ihn beim Schließen ab — und die Messung
danach bekam auf ihre Anmeldung ein freundliches `true` und wurde nie gefragt.
Jede Anfrage lief gegen einen leeren Ablageort.

**Das sah aus wie ein Adressproblem.** Mit Nummer lud nichts, ohne Nummer schon
(weil die eine Probe *im Nachweis* lief). Zwei Korrekturen an der Adressform
gingen ins Leere, eine davon machte es schlimmer. Gefunden hat es erst
Chromiums eigene Konsole — die vorher niemand las, weil sie nirgends hinging.

Seitdem: ein Ablageort bei der Anmeldung, und `WebConsole` leitet Chromiums
Meldungen ins Protokoll. Ein Bild, das nicht lädt, meldet sich jetzt selbst.

---

## 4. Zwischenspeicher

**Gemessen, nicht vermutet.** Ein einfarbig magentafarbenes Bild wurde unter
der Adresse **ohne** Nummer nachgeschoben und danach ein freier Bildpunkt
zurückgelesen:

```
Der Zwischenspeicher steht nicht im Weg — unter derselben Adresse kam
das neue Bild an, rgb(255, 0, 255).
```

Der Kopf `Cache-Control: no-store, no-cache, must-revalidate` **genügt**.

Die Nummer in der Adresse bleibt trotzdem: Sie kostet nichts, deckt den Fall
ab, dass eine spätere Chromium-Fassung den Kopf anders behandelt, und macht im
Protokoll sichtbar, welches Bild gemeint war.

**Kein Neuladen der Seite.** Der Austausch ist ein einzelner
JavaScript-Aufruf, der die Adresse des Bildes setzt. Die Seite bleibt stehen,
mit allem, was darauf getippt wurde. Das ist ausdrücklich **keine Brücke**: Es
geht in eine Richtung, kennt einen Befehl, und die Seite kann nichts
zurückgeben.

---

## 5. Der Nachweis für das CSS-Glas

Drei Stufen, aus dem fertigen Bildschirm zurückgelesen.

**Stufe 1 — liegt das Bild da, und pixelgenau?**

```
frei oben links    rgb(255,   0,   0)      frei unten links   rgb(  0,   0, 255)
frei oben rechts   rgb(  0, 255,   0)      frei unten rechts  rgb(255, 255, 255)
```

**Stufe 2 — filtert das Glas den Bildinhalt?**

| Fläche | gemessen | über Schwarz käme |
|---|---|---|
| `blur(18px)` über Rot | **rgb(173, 7, 10)** | rgb(7, 7, 10) |
| `blur(18px) saturate(140%)` über Grün | rgb(7, 173, 10) | rgb(7, 7, 10) |
| `blur(40px)` über Blau | rgb(7, 7, 176) | rgb(7, 7, 10) |
| ohne Filter über Weiß | rgb(173, 173, 176) | rgb(7, 7, 10) |
| **über der Grenze Rot/Grün** | **rgb(90, 94, 10)** | rgb(7, 7, 10) |
| zwei überlappende Flächen | rgb(73, 73, 117) | rgb(7, 7, 10) |

Die Rechnung geht exakt auf: `20·0,35 + 255·0,65 = 173,75`. Und der
Gegenbeweis steht in derselben Zeile — die *übrigen* Kanäle zeigen genau die 7
und 10, die ein Filter über Schwarz ergäbe.

**Der schärfste Fall ist die Kante.** Eine Glasfläche über der Grenze zwischen
Rot und Grün zeigt `rgb(90, 94, 10)`: beide Kanäle deutlich angehoben. Ein
gefiltertes Nichts kann das nicht, gleich wie stark gefiltert wird.

**Stufe 3 — Zwischenspeicher**, siehe oben.

---

## 6. Auflösungen

Fenster 854 × 480. Ein Vollbild sind 1601 KB roh.

| Stufe | Größe | Verkleinern | Lesen | Kodieren | zusammen | KB |
|---|---|---:|---:|---:|---:|---:|
| 1,0× PNG | 854×480 | 169 µs | 11021 µs | **53999 µs** | 65,2 ms | 99,7 |
| 0,5× PNG | 427×240 | 1647 µs | 3053 µs | 22061 µs | 26,8 ms | 72,1 |
| 0,25× PNG | 214×120 | 1655 µs | 793 µs | 6322 µs | 8,8 ms | 26,5 |
| 0,5× **BMP** | 427×240 | 1980 µs | 1030 µs | **93 µs** | **3,1 ms** | 400,4 |
| 1,0× **BMP** | 854×480 | 1970 µs | 2837 µs | **241 µs** | **5,0 ms** | 1601,3 |

### Was man sieht

**0,5× ist vom Vollbild nicht zu unterscheiden.** Fadenkreuz und Hotbar sind
scharf, die Blockkanten stehen.

**0,25× ist hinter dem Glas ebenfalls nicht zu unterscheiden** — der
Weichzeichner frisst die fehlende Schärfe vollständig. **Wo das Bild frei
liegt, sieht man es sofort:** Das Fadenkreuz wird zu einem Strich, die
Hotbar-Symbole werden matschig.

Damit hängt die Wahl daran, wie viel freie Fläche eine Oberfläche lässt. Eine
IDE, die den Schirm füllt, käme mit 0,25× aus. Eine mit Rändern nicht.

Die Bilder liegen unter `run/screenshots/fn-*.png`.

---

## 7. Format

**Die Kodierung ist der Flaschenhals, und zwar mit weitem Abstand.**

| | 0,5× | 1,0× |
|---|---:|---:|
| PNG kodieren | 22,1 ms | 54,0 ms |
| BMP kodieren | 0,09 ms | 0,24 ms |
| **Faktor** | **235×** | **224×** |

PNG spart Bytes im Hauptspeicher — 72 KB gegen 400 KB — und bezahlt dafür mit
Rechenzeit auf dem Render-Thread. Der Tausch lohnt nicht: Die Bytes gehen
nirgendwohin, sie liegen ohnehin im Hauptspeicher.

**Und man sieht den Unterschied in der Bildzeit:**

| Fall | Bildzeit p95 |
|---|---:|
| 10/s, 0,5×, PNG | **31,9 ms** |
| 10/s, 0,5×, BMP | **17,1 ms** |

Die Ruckler kommen von der Kompression, nicht von der Übertragung.

**BMP ist hier richtig**, und es hat einen zweiten Vorzug: Diese Bilder zählen
ihre Zeilen von unten, OpenGL-Texturen auch. Es ist nichts zu spiegeln —
vierundfünfzig Byte Kopf, dann die Bildpunkte, wie sie von der Grafikkarte
kommen.

Nicht geprüft: JPEG (verlustbehaftet, aber Chromium dekodiert es schnell) und
WebP. Für einen Hintergrund hinter Weichzeichnern wäre Verlust unkritisch — das
bleibt offen, weil BMP die Frage vorerst erledigt.

---

## 8. Takte

Alle bei 0,5×.

| Modus | Aufnahmen/s | je Aufnahme | **hinaus** | **zurück** | Bildzeit p50 / p95 |
|---|---:|---:|---:|---:|---:|
| Standbild | 0 nach dem ersten | — | 0 ¹ | 0 ¹ | 8,4 / 16,6 ms |
| 2/s PNG | 2,0 | 19,4 ms | 0,14 MB/s | 6,24 MB/s | 8,5 / 16,4 ms |
| 5/s PNG | 5,0 | 18,9 ms | 0,34 MB/s | 15,61 MB/s | 8,5 / 25,9 ms |
| 10/s PNG | 9,6 | 18,9 ms | 0,65 MB/s | 17,48 MB/s | 8,7 / 31,9 ms |
| 10/s BMP | 9,6 | **1,0 ms** | 3,74 MB/s | 21,19 MB/s | 8,6 / **17,1 ms** |

¹ Im eingeschwungenen Zustand. Beim Öffnen und bei jedem Wechsel des
Messabschnitts fallen zwei bis drei Übertragungen an (im Protokoll 0,62 bis
0,94 MB/s über das Messfenster gemittelt) — das ist der Aufbau, nicht der
Betrieb.

### Ist die Richtung Minecraft → Browser teurer als umgekehrt?

**Nein — umgekehrt, und zwar deutlich.**

Bei 10/s gehen **0,65 MB/s** hinaus und **17,5 MB/s** zurück. Faktor 27.

Der Grund ist die Asymmetrie der Formate: Hinaus geht ein komprimiertes Bild
(69 KB), zurück kommt der **unkomprimierte Vollbild-Upload** der ganzen Seite
(1601 KB). Jeder neue Hintergrund macht das gesamte Seitenbild ungültig — das
Bild deckt die Fläche, und alles Glas darüber muss neu gerechnet werden. Die
Ausschnitte, die in Schritt D alles gerettet haben, greifen hier nicht.

**Die Rechenzeit liegt dagegen auf dem Hinweg**: 19 ms Aufnahme gegen etwa
1,4 ms Upload bei dieser Größe. Wer „teurer" fragt, muss also sagen, worin —
in Bytes ist der Rückweg teurer, in Zeit der Hinweg. Mit BMP verschwindet der
Zeitanteil fast ganz, und übrig bleibt eine Frage der Bytes.

---

## 9. Sichtbare Verzögerung

Bei einem Standbild gibt es keine.

Bei bewegtem Hintergrund liegt zwischen Aufnahme und Anzeige die Aufnahmezeit
selbst (BMP: 1 ms) plus Chromiums Dekodier- und Zeichenzeit. Aus den Zahlen
lässt sich das obere Ende abschätzen: Bei 10/s BMP kommen 68 Uploads in 5
Sekunden zurück, also 13,6/s — mehr als die 9,6 Aufnahmen. Chromium hinkt dem
Takt nicht hinterher.

**Was ich nicht gemessen habe:** die Zeit vom Aufnahmezeitpunkt bis zum
sichtbaren Bild, in Millisekunden. Dafür bräuchte es einen Zeitstempel, der die
ganze Kette mitläuft — machbar, aber ohne Rückkanal aus der Seite umständlich.
Ein bewegter Hintergrund hinkt jedenfalls um mindestens ein Aufnahmeintervall
hinterher: bei 2/s eine halbe Sekunde, bei 10/s ein Zehntel.

---

## 10. Was der Blick sagt

- **Standbild bei Vollbild-Oberfläche: angenehm.** Es sieht nicht nach einem
  Standbild aus, sondern nach einem Fenster. Solange man nicht damit rechnet,
  dass sich etwas bewegt, fehlt nichts.
- **2/s fällt auf**, sobald man sich dreht — der Hintergrund springt.
- **5/s fällt beim Umsehen noch auf**, im Stehen nicht.
- **10/s fällt nicht mehr auf.**
- **0,5× ist von 1,0× nicht zu unterscheiden**, auch außerhalb des Glases.
- **0,25× nur hinter dem Glas**; am freien Rand sofort.
- **Ein Versatz stört nicht**, weil der Hintergrund kein Bedienelement ist. Wer
  in einem Editor schreibt, sieht nicht auf die Welt dahinter.

**Ehrlich dazu:** Die Bewertungen zu 2, 5 und 10 Bildern beruhen auf den
gespeicherten Bildern und den Zahlen, nicht auf minutenlangem Zusehen. Was
davon in einer langen Sitzung nervt, sagt erst eine lange Sitzung.

---

## 11. Bekannte Einschränkungen

| Punkt | Stand |
|---|---|
| **Der Hintergrund friert bei geschlossenem Bildschirm ein** | Der Aufnahmepunkt liegt im Bildschirm. Ohne Bildschirm gibt es keine Aufnahme — für eine Fläche in der Welt bräuchte es einen anderen Punkt. |
| **Keine Erkennung stehender Kamera** | Bei bewegtem Hintergrund wird auch dann aufgenommen, wenn sich nichts geändert hat. Die Stelle dafür ist da (ein Vergleich vor `store.put`), gebaut ist sie nicht. |
| **Kodierung läuft im Render-Thread** | Bei BMP mit 1 ms unkritisch. Bei PNG war es die Ursache der Ruckler. Ein Arbeitsfaden dafür wäre die nächste Verbesserung, wenn PNG je gebraucht wird. |
| **Kein Zeitstempel durch die Kette** | Die tatsächliche Verzögerung in Millisekunden ist nicht gemessen (siehe 9). |
| **GUI-Skalierung nur bei 2 geprüft** | Die Umrechnung ist per Konstruktion skalenunabhängig — Aufnahme und Browser bekommen dieselbe Framebuffergröße —, aber gelaufen ist sie bei einer Einstellung. |
| **Fenstergrößenänderung** | Beim Neuaufbau wird die Aufnahme verworfen und neu gemacht; ein altes Seitenverhältnis kann nicht stehenbleiben. Nicht unter fortlaufendem Ziehen am Fensterrand geprüft. |
| **Kein Alpha im Hintergrund** | Das aufgenommene Bild ist deckend. Wollte man Minecraft *durch* die Seite hindurch sehen, wäre das ein anderer Weg. |
| **Zwei Bildschirme mit Hintergrund gleichzeitig** | Ungeprüft. Sie teilten sich den einen Ablageort, was funktioniert, solange beide dasselbe Bild wollen. |

**Zum Speicher**, weil danach gefragt war: Der Ablageort hält **genau ein
Bild** — bei 0,5× im BMP-Format 400 KB, bei 1080p gut 2 MB. Ein Verlauf wird
nicht aufbewahrt; wer einen Hintergrund anfordert, will den aktuellen. Dazu
kommt das Verkleinerungsziel auf der Grafikkarte, das dieselbe Größe hat und
wiederverwendet wird.

---

## 12. Welcher Modus sollte Standard werden?

**Empfehlung: Standbild bei 0,5× im BMP-Format, mit einer neuen Aufnahme bei
jedem Öffnen und bei jeder Größenänderung.**

Begründung:

1. **Ein Editor sieht nicht nach draußen.** Wer schreibt, schaut auf den Text.
   Ein bewegter Hintergrund kostet dauerhaft und wird dauerhaft nicht bemerkt.
2. **Ein Standbild kostet nach dem ersten Bild exakt nichts** — keine
   Aufnahme, kein Upload, keine Bytes. Das ist dieselbe Eigenschaft, die schon
   den ruhenden Editor gratis gemacht hat.
3. **Die Aufnahme kostet 3,1 ms**, einmal beim Öffnen — bei 8,4 ms Bildzeit
   gut ein Drittel eines Bildes, und das in einem Übergang, in dem ohnehin
   etwas passiert.
4. **0,5× sieht aus wie 1,0×**, kostet aber ein Viertel der Bytes.

**Wo Bewegung gewünscht ist** — eine Tafel an der Wand, ein Überwachungsbild —
ist **5/s bei 0,25× im BMP-Format** der richtige Kompromiss: 100 KB je Bild,
also 0,49 MB/s hinaus, und hinter Glas sieht man den Unterschied ohnehin nicht.

### Hochgerechnet auf 1920 × 1080

Gemessen wurde bei 854 × 480. Ein echter Bildschirm hat **5,06-mal so viele
Bildpunkte**, und Lesen wie Rückweg wachsen mit den Bytes:

| | bei 854×480 | bei 1920×1080 (gerechnet) |
|---|---:|---:|
| Lesen, 1,0× | 11,0 ms | ~56 ms |
| Lesen, 0,5× | 3,1 ms | ~15 ms |
| BMP je Bild, 0,5× | 400 KB | ~2,0 MB |
| hinaus bei 5/s, 0,5× BMP | 2,0 MB/s | ~10 MB/s |
| **zurück bei 5/s** | 15,6 MB/s | **~79 MB/s** |

Der Rückweg ist die Zahl, die aufhorchen lässt: Bei jedem Hintergrundwechsel
kommen gemessen zwei Vollbild-Übertragungen zurück (50 Uploads auf 25
Aufnahmen), und ein Vollbild sind bei 1080p 7,9 MB.

**Das stützt die Empfehlung.** Bei der Größe, um die es wirklich geht, ist ein
bewegter Hintergrund keine Kleinigkeit mehr — ein Standbild dagegen kostet
nach dem ersten Bild weiterhin nichts.

**Nicht empfohlen: 10/s.** Es kostet doppelt so viel wie 5/s und sieht kaum
anders aus, solange niemand die Kamera schwenkt.

---

## 13. Zum Nachstellen

```
/fnweb glas          Hintergrund mit Glasflächen, 5/s bei 0,5× im BMP-Format
/fnweb glasmessung   die ganze Matrix, mit Bildern in run/screenshots
/fnweb hintergrund   der dreistufige Nachweis
```

Der ganze Ablauf läuft von selbst mit `./gradlew runClient -Pbenchmark`.
