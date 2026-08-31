# Tipp-Latenz bei voller Auflösung: Stand nach Schritt G

**31.08.** Gemessen bei drei echten Fenstergrößen, nicht hochgerechnet. Die
Verzögerung ist in drei Abschnitte zerlegt, und die Zerlegung geht auf.

**Die Entscheidung steht in Abschnitt 8.** Sie lautet: **kein PBO**, und der
Grund ist nicht, dass der Upload billig wäre — sondern dass er nicht das
Problem ist.

---

## 1. Wie gemessen wurde

Fenstergröße über Minecrafts eigene Startargumente `--width`/`--height`, also
ein echtes Fenster mit echtem Bildpuffer. Der Browser bekommt genau so viele
Bildpunkte.

| angefordert | Fenster | Browser | Bildpunkte |
|---|---|---|---|
| 854 × 480 | 854 × 480 | 854 × 480 | 0,41 Mio |
| 1280 × 720 | 1280 × 720 | 1280 × 720 | 0,92 Mio |
| **1920 × 1080** | 1920 × **1061** | 1920 × **1062** | **2,04 Mio** |

Die 1080er sind 1061 geworden — die Titelleiste nimmt sich neunzehn Bildpunkte.
Das sind 2,04 statt 2,07 Millionen, ein Unterschied von 1,7 %. Für die Frage
belanglos, aber es soll dastehen.

Jeder Tippabschnitt läuft **45 Sekunden** und liefert etwa 500 Messungen. In
Schritt F waren es vier Sekunden und vierzig — ein p95 daraus war eine
Behauptung.

Hintergrund wie festgelegt: Standbild, halbe Kantenlänge, rohes Format.

---

## 2. Das Hauptergebnis

Normales Tippen, acht Zeichen je Sekunde, Glas an:

| Auflösung | **A** Chromium | **B** Upload | **C** Warten | Summe | **gemessen** | p95 | max |
|---|---:|---:|---:|---:|---:|---:|---:|
| 854 × 480 | 41,8 ms | 2,9 ms | 5,1 ms | 49,8 | **49,7 ms** | 91,6 | 424 |
| 1280 × 720 | 43,6 ms | 6,5 ms | 5,4 ms | 55,5 | **56,0 ms** | 133,4 | 1518 |
| 1920 × 1062 | 47,6 ms | 14,9 ms | 6,0 ms | 68,5 | **67,8 ms** | 110,3 | 421 |

**Die Zerlegung stimmt.** Summe und gemessene Gesamtstrecke weichen um weniger
als eine Millisekunde voneinander ab — bei allen drei Auflösungen. Die Prüfung
darauf läuft mit und hätte sich sonst gemeldet.

*Zur Streuung:* Der 1080p-Wert wurde zweimal gemessen, in zwei Läufen: 63,8 und
67,8 ms. Die Tabelle nennt den zweiten, weil dort jede Phase mit einem frischen
Dokument begann (siehe Abschnitt 5). Zwischen zwei Läufen liegen also etwa
sechs Prozent — das ist die Genauigkeit, mit der diese Zahlen zu lesen sind.

### Und hier steht die Antwort auf die ganze Frage

**A ist konstant. Nur B skaliert.**

```
A (Chromium und Monaco)   41,8 → 43,6 → 47,6 ms      +14 %
B (unser Upload)           2,9 →  6,5 → 14,9 ms      +414 %
C (Warten auf Minecraft)   5,1 →  5,4 →  6,0 ms      +18 %
```

Die Fläche wächst dabei um **397 %**. B folgt ihr genau; A und C tun es nicht
einmal ansatzweise.

B gegen die Bildpunktzahl:

| | Bildpunkte | erwartet | gemessen |
|---|---:|---:|---:|
| 854 × 480 | 1,00× | 1,00× | 1,00× |
| 1280 × 720 | 2,25× | 2,25× | **2,24×** |
| 1920 × 1062 | 4,97× | 4,97× | **5,14×** |

**Der Upload wächst linear mit der Fläche, ohne Überraschung.** Kein
Treiberknick, keine Schwelle. Was in Schritt F hochgerechnet wurde (2,9 × 5,06
≈ 15 ms), ist gemessen 14,8 ms.

**Chromiums Zeit hängt dagegen fast nicht an der Auflösung.** Sie hängt daran,
wie viel Monaco neu zeichnen lässt — und das ist bei jeder Größe fast die ganze
Seite.

---

## 3. Dirty-Fläche und Datenmenge

| Auflösung | KB je Bild | Anteil eines Vollbilds | MB/s | Bilder/s |
|---|---:|---:|---:|---:|
| 854 × 480 | 1468,5 | 91,7 % | 17,7 | 12,3 |
| 1280 × 720 | 3280,6 | 91,1 % | 35,6 | 11,1 |
| 1920 × 1062 | 6828,2 | 85,7 % | **76,5** | 11,5 |

**Der Anteil bleibt bei ~90 %, gleich wie groß der Schirm ist.** Der Befund aus
Schritt F bestätigt sich bei jeder Auflösung: Monaco macht beim Tippen fast
alles ungültig.

Bei 1080p sind das **76 MB je Sekunde** in die Richtung Browser → Minecraft.

---

## 4. Was Minecraft davon merkt

| Auflösung | Bildzeit p50 | p95 | max |
|---|---:|---:|---:|
| 854 × 480 | 8,5 ms | 17,2 ms | 56 ms |
| 1280 × 720 | 8,6 ms | 17,2 ms | 55 ms |
| 1920 × 1062 | 8,7 ms | **24,9 ms** | **103 ms** |

**Der Median bleibt unberührt** — 8,5 bis 8,7 ms bei allen Größen, also 115 bis
118 Bilder je Sekunde.

**Das obere Ende wächst.** Bei 1080p steigt p95 von 17 auf 25 ms, und der
schlechteste Fall auf 103 ms. Das ist der Upload, der im Zeichenfaden sitzt:
14,8 ms je Bild, gut zwölfmal in der Sekunde.

**Das ist der einzige Punkt, an dem ein Pixel Buffer Object wirklich helfen
würde** — nicht bei der Tippverzögerung, sondern hier.

---

## 5. Glas: was es wirklich kostet — und ein Fehler, der es fast verdeckt hätte

**Der erste Anlauf war wertlos, und der Grund ist lehrreich.** Jede Tippphase
läuft fünfundvierzig Sekunden und schreibt in dieselbe Datei. Nach drei Phasen
standen darin über zwei Minuten Tipptext — die dritte Phase maß also ein
Dokument, das doppelt so groß war wie das der ersten. Der Wert für A wanderte
entsprechend mit (41,8 → 49,9 → 58,1 ms bei 854 × 480), und weil „Glas an"
immer zuerst lief, sah es aus, als sei Glas die schnellste Variante.

Es war die Reihenfolge, nicht das Glas.

**Seitdem bekommt jede Phase ein frisches Dokument.** Damit sind die drei
Varianten vergleichbar, und das Ergebnis dreht sich um — bei 1920 × 1062:

| | A | gesamt p50 | p95 | max |
|---|---:|---:|---:|---:|
| **Glas an** | 47,6 ms | **67,8 ms** | **110,3 ms** | 421 ms |
| Glas aus | 42,5 ms | 63,1 ms | 86,3 ms | 125 ms |
| Glas aus, Editor deckend | 41,9 ms | 61,9 ms | 86,0 ms | 114 ms |

**Das Glas kostet etwa sechs Millisekunden im Median und vierundzwanzig im
p95.** Es kostet keine Übertragung — B bleibt bei 14,5 bis 14,9 ms — sondern
Rechenzeit in Chromium, genau wie Schritt F es vermutet hatte.

Der Unterschied zwischen „Glas aus" und „deckender Editor" ist mit gut einer
Millisekunde vernachlässigbar. Die Durchsichtigkeit des Editors selbst kostet
also nichts; die Weichzeichner auf den Panels kosten.

**Was daraus folgt, ist eine Abwägung und keine Rechnung:** Für ein
Viertel schlechtere Ausreißer bekommt man das, wofür Schritt E gebaut wurde.
Ob das ein guter Tausch ist, hängt davon ab, wie sehr die Ausreißer stören —
und das ist eine Produktfrage.

## 6. Rollen und schnelles Tippen

| Auflösung | Rollen p50 | Rollen p95 | Bilder/s | Minecrafts Bildzeit | schnelles Tippen p50 |
|---|---:|---:|---:|---:|---:|
| 854 × 480 | 32,7 ms | 51,3 ms | 26,9 | 8,6 ms | 49,0 ms |
| 1280 × 720 | 40,1 ms | 66,2 ms | 23,7 | 8,8 ms | 59,8 ms |
| 1920 × 1062 | 33,6 ms | 49,1 ms | ~27 | **15,4 ms** | 63,8 ms |

**Rollen bleibt der beste Fall für den Editor** — bei 1080p sogar der beste
Wert überhaupt: p50 33,6 ms, p95 49,1 ms, und A fällt auf 16,7 ms, weil beim
Rollen nur verschoben und nicht neu gesetzt wird.

**Aber es ist der schlechteste Fall für Minecraft.** Die Bildzeit springt auf
15,4 ms — **65 Bilder je Sekunde statt 115**. Der Grund steht in derselben
Zeile: siebenundzwanzig Bilder je Sekunde mal 11,5 ms Upload sind gut drei
Zehntel jeder Sekunde, die der Zeichenfaden mit unserem Upload verbringt.

**Das ist die einzige Stelle in der ganzen Messung, an der ein Pixel Buffer
Object einen sichtbaren Unterschied machen würde** — nicht für das Tippen,
sondern für das Spiel während des Rollens.

**Ein Fehler im Messaufbau, der drei Abschnitte gekostet hat:** „Schnelles
Tippen" schickte anfangs bei *jedem Bild* ein Zeichen, also gegen
hundertfünfzehn je Sekunde. Bei 1080p ist Chromium daran erstickt: zehn Bilder
in fünfundvierzig Sekunden, ein Rückstau von **fünfzehn Sekunden**, und die
beiden folgenden Abschnitte maßen nur noch dessen Abarbeitung — beide mit null
Bildern.

Seitdem sind es zwanzig Zeichen je Sekunde, was schnelles menschliches Tippen
ist. Damit läuft es sauber (705 Bilder bei 720p).

**Warum die ersten 1080p-Tippzahlen trotzdem gültig waren:** Die drei
Tippabschnitte liefen in der Reihenfolge <b>vor</b> dem erstickenden schnellen
Tippen. Betroffen waren nur die beiden Abschnitte danach. Inzwischen ist der
1080p-Lauf mit dem berichtigten Aufbau wiederholt, und die Zahlen in diesem
Bericht stammen aus dem Nachlauf.

---

## 7. Speicher und Startzeit

| | |
|---|---:|
| Chromium mit Monaco (854 × 480, 8 Modelle) | **667 MB** in 12 Prozessen |
| Minecraft daneben, mit allen Mods des Prüfstands | 2376 MB |
| Erstes Öffnen, mit Auspacken | ~4,4 s |
| **Zweites Öffnen in derselben Sitzung** | **1,02 s** (854×480) / **1,15 s** (1080p) |

**Die Sekunde beim zweiten Öffnen ist die Zahl, die für das Produktgefühl
zählt.** Chromium läuft dann, die Dateien liegen ausgepackt, und Monaco baut
sich neu auf. Das ist schnell genug, dass ein Spieler die Oberfläche schließen
und wieder öffnen kann, ohne es zu bereuen.

**Nicht geliefert: die Aufschlüsselung des Speichers nach Stufen** (Punkt 10
des Auftrags). Die Seite kann Monaco inzwischen stufenweise laden — `?huelle`
lädt nur die Hülle, `?einmodell` nur eine Datei —, aber der Ablauf, der die
fünf Stufen durchfährt und dabei Marker setzt, ist nicht gebaut. Ich habe die
Zeit stattdessen in die Auflösungsmessung gesteckt, weil das die Frage war, an
der die Entscheidung hängt. Der Unterbau steht; nachzuholen ist ein Ablauf und
ein Messlauf.

---

## 8. Die Entscheidung: kein PBO

Die Leitplanken aus dem Auftrag, angelegt an die Messung:

| Auflösung | p50 | Ampel | p95 | Ampel |
|---|---:|---|---:|---|
| 854 × 480 | 49,7 ms | **gelb** | 91,6 ms | **gelb** |
| 1280 × 720 | 56,0 ms | **gelb** | 133,4 ms | **rot** |
| 1920 × 1062 | 67,8 ms | **rot** | 110,3 ms | **gelb** |

**Gelb bis rot, bei jeder Auflösung — auch bei der kleinsten.**

Und jetzt die Rechnung, die alles entscheidet. Ein Pixel Buffer Object kann
höchstens **B** beseitigen. Angenommen, er brächte B auf null:

| Auflösung | heute | mit perfektem PBO | Ampel danach |
|---|---:|---:|---|
| 854 × 480 | 49,7 ms | 46,8 ms | **immer noch gelb** |
| 1280 × 720 | 56,0 ms | 49,5 ms | **immer noch gelb** |
| 1920 × 1062 | 67,8 ms | 52,9 ms | **immer noch gelb** |

**Ein PBO bringt keine einzige Auflösung ins Grüne.** Selbst bei 854 × 480, wo
der Upload nur 2,9 ms von 49,7 ausmacht, bliebe die Verzögerung bei 47 ms —
weil **A mit 42 ms konstant dasteht und uns nicht gehört**.

Das ist genau der Fall, den der Auftrag unter Punkt 9 beschreibt: *Upload
klein, Chromium groß → nicht weiter an OpenGL optimieren.* Bei 1080p ist der
Upload mit 23 % nicht mehr winzig, aber er ist nicht der Grund, warum die
Verzögerung über der Grenze liegt.

**Wofür ein PBO trotzdem etwas brächte:** Minecrafts Bildzeit im oberen Ende
(Abschnitt 4) — p95 von 25 auf etwa 17 ms zurück, weil 14,8 ms aus dem
Zeichenfaden verschwänden. Das ist eine Verbesserung für das Spiel, nicht für
den Editor. Ob sie den Aufwand wert ist, ist eine andere Entscheidung als die
hier gestellte.

---

## 9. Wie es sich anfühlt

Ehrlich vorweg, wie schon in E und F: Beurteilt anhand der Verzögerungszahlen
und der Bildschirmfotos, nicht anhand einer langen Sitzung am Gerät.

**Fünfzig bis achtundsechzig Millisekunden vom Anschlag bis zum Zeichen sind
spürbar.** Zum Vergleich: VS Code liegt bei fünfzehn bis fünfundzwanzig. Es ist
nicht das Gefühl eines hakenden Editors — es ist das Gefühl eines Editors über
eine Fernverbindung. Man tippt weiter, und der Text folgt mit erkennbarem
Abstand.

Bei 1080p mit p95 von 110 ms kommt dazu, dass jeder zwanzigste Anschlag
deutlich länger braucht. Das ist der Teil, der stört: nicht die mittlere
Verzögerung, sondern ihre Ungleichmäßigkeit.

**Rollen, Suchen, Vervollständigen und Reiterwechsel fühlen sich unverändert
gut an** — daran hat die Auflösung nichts geändert.

---

## 10. Die Antwort

> **Ist der aktuelle CEF/JCEF-CPU-Pfad bei 1080p gut genug für unsere primäre
> Monaco-IDE?**

**Für den Bildweg: ja. Für das Tippgefühl: noch nicht — und der Bildweg ist
nicht der Grund.**

Genauer:

1. **Unser Pfad ist nicht das Problem.** B beträgt 2,9 bis 14,9 ms und skaliert
   sauber linear. Selbst vollständig beseitigt bliebe die Verzögerung bei
   47–53 ms.
2. **Das Problem ist A** — die Zeit, die Chromium und Monaco brauchen, bis ein
   Bild fertig ist. Sie liegt bei 42 ms und ist von der Auflösung unabhängig.
   Sie liegt außerhalb des **Renderpfads**, aber nicht außerhalb jedes
   Zugriffs: Der Glas-Vergleich zeigt, dass die Zusammensetzung der Seite A um
   sechs Millisekunden bewegt. Die Hebel liegen in Monacos Einstellungen und in
   unserem CSS — genau das, was Abschnitt 9 des Auftrags als nächsten
   Untersuchungsgegenstand nennt.
3. **Minecraft trägt den Workload.** Der Median der Bildzeit bleibt bei 8,5 ms.
   Nur das obere Ende leidet bei 1080p, und dagegen hülfe ein PBO.

**Was das für den nächsten Schritt heißt:** Weiter am Renderpfad zu
optimieren, hätte den falschen Adressaten. Wenn die Tippverzögerung besser
werden soll, führt der Weg über Abschnitt 9 des Auftrags — welche
Monaco-Einstellungen die großen Ungültigkeiten erzeugen und ob sich das
eindämmen lässt. Zwei Hinweise liegen schon vor: **Das Glas kostet sechs
Millisekunden**, und der Anteil der ungültigen Fläche bleibt bei neunzig
Prozent, gleich wie groß der Schirm ist.

**Und wenn die Antwort lauten soll, dass fünfzig Millisekunden reichen** — das
ist eine Produktentscheidung, keine technische. Sie liegt nicht bei mir.

---

## 11. Zum Nachstellen

```
./gradlew runClient -Pide -Ptyping -Pw=1920 -Ph=1080
./gradlew runClient -Pide -Ptyping -Pw=1280 -Ph=720
./gradlew runClient -Pide -Ptyping -Pw=854  -Ph=480
```

Jeder Lauf dauert etwa sechs Minuten. Die Bilder liegen unter
`run/screenshots/fntyp-*.png`, benannt nach Auflösung und Abschnitt.

**Eine Anmerkung zur Umgebung:** Zwischen 12:50 und 13:35 war die
Windows-Sitzung getrennt, und GLFW fand keinen Bildschirm
(`Failed to find a primary monitor`). Kein Client startet in diesem Zustand.
Wer die Läufe wiederholt und diesen Fehler sieht, sucht nicht im Code.
