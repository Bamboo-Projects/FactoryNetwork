# Schritt I — Was innerhalb von Chromium wirklich Zeit kostet

Stand: 31. August 2026. Alle Zahlen bei 1920×1062, acht Anschlägen je Sekunde,
je 45 Sekunden und 570 bis 606 Messungen. Chromium 116.0.5845.190.

---

## Die kurze Antwort

Die Strecke vom Tastendruck bis zum fertigen Bild besteht überwiegend aus
**Warten auf einen festen Takt von dreißig Bildern je Sekunde**. Nicht aus
Layout, nicht aus Zeichnen, nicht aus dem Rückweg des Bildes aus dem
Grafikspeicher.

Der Beleg ist eine einzige Messung:

```text
Abstand zwischen zwei Bildaufrufen im fensterlosen Betrieb
  p10  33,33 ms
  p50  33,33 ms
  p90  33,33 ms      = exakt 30,0 Bilder je Sekunde
```

Drei gleiche Perzentile auf zwei Nachkommastellen, im Leerlauf wie unter Last,
auf jeder Seite. Das ist kein Ergebnis von Arbeit — Arbeit streut. Das ist ein
Zähler. Es ist CEFs Voreinstellung für `windowless_frame_rate`, und unsere
JCEF-Fassung kann sie nicht ändern.

Jeder Anschlag wartet daher im Mittel **16,7 ms**, bevor Chromium überhaupt
anfängt. Die gesamte Zeichenarbeit, die er dann auslöst, beträgt rund 14 ms.

---

## 1. Welche Tracing-Methode funktioniert

**Chrome DevTools Protocol über einen lokalen Debug-Port.** Ohne CEF-Fork,
ohne Patch an MCEF.

MCEF baut Chromiums Einstellungen selbst und setzt keinen Debug-Port; die
Schalterliste ist fest verdrahtet. Änderbar ist das an genau einer Stelle:
`CefAppHandlerAdapter.onBeforeCommandLineProcessing`. Dort hängt jetzt
`web/runtime/WebDebug.java` und ergänzt `remote-debugging-port=9222` sowie
`remote-debugging-address=127.0.0.1`.

Zwei Bedingungen, beide beim ersten Versuch verfehlt:

- **Die Anmeldung muss in den Mod-Konstruktor.** MCEF startet Chromium beim
  allerersten `setScreen`, also beim Übergang ins Hauptmenü. Aus dem
  `FMLClientSetupEvent` heraus kam die Antwort „Zu spät, Chromium läuft schon".
- **Tracing gehört der Browser-Verbindung, nicht der Seite.** Über die
  Seitenverbindung angefordert, nimmt Chromium `Tracing.start` entgegen und
  schickt nie ein Ereignis — keine Fehlermeldung, null Daten. Die
  Browser-Verbindung steht in `/json/version` unter `webSocketDebuggerUrl`.

Der Port geht nur auf, wenn `fn.devtools` gesetzt ist (`-Pdevtools`), und
bindet ausdrücklich an `127.0.0.1`. In einem Release ist er zu.

Werkzeuge: `tools/trace.mjs` (Trace aufnehmen und auswerten),
`tools/raf.mjs` (Bildtakt messen), `tools/windowed.mjs` (Vergleich im
Fenstermodus). Ein Lauf liefert rund 255.000 Ereignisse in zwanzig Sekunden.

---

## 2. Zeit für Layout und Zeichnen

Aus dem Trace, Monaco vollständig, je Bild gemittelt:

```text
Layout                                     0,82 ms
Paint + PrePaint                           1,33 ms
```

Zusammen gut zwei Millisekunden. Das ist der Posten, den die bisherige
Vermutung für den Hauptverdächtigen hielt.

---

## 3. Zeit für Raster und Compositing

```text
ProxyMain::BeginMainFrame                 11,30 ms
WidgetBaseInputHandler::OnHandleInputEvent 9,13 ms
FireAnimationFrame                         6,96 ms
Display::DrawAndSwap                       2,67 ms
RasterTask                                 0,14 ms
```

`BeginMainFrame` und `OnHandleInputEvent` sind Klammern, keine Einzelposten —
sie enthalten das meiste der darunter stehenden Zeilen. Alle Zeichenstufen
zusammen ergeben rund vierzehn Millisekunden je Bild.

Dem stehen die von außen gemessenen 27,6 ms gegenüber. **Die Differenz ist
Wartezeit**, und sie hat oben ihren Namen bekommen.

Der Trace bestätigt den Takt ein drittes Mal, wenn man die Abstände zwischen
den fertigen Bildern ansieht statt die Dauern. Über zwanzig Sekunden bei acht
Anschlägen je Sekunde:

```text
Abstand zwischen zwei Display::DrawAndSwap (n=180)
  32– 36 ms    11,1 %   ← zwei Bilder in Folge: genau ein Takt
 148–168 ms    44,1 %   ← ein Bild je Anschlag: der Tippabstand
  16– 20 ms     1,7 %
```

Die große Gruppe bei 150 ms ist kein Befund über Chromium, sondern über den
Tippstrom — ein fensterloser Browser malt nur, wenn sich etwas ändert, und
zwischen zwei Anschlägen liegen 125 ms. Aussagekräftig ist die andere Gruppe:
Wo tatsächlich zwei Bilder aufeinanderfolgen, liegen 32 bis 36 ms dazwischen.
**Kein einziger Abstand liegt bei 16 bis 17 ms**, dem Wert, den ein Takt von
sechzig Bildern hätte.

---

## 4. Der sichtbare OSR-Readback-Anteil

```text
SkiaOutputSurfaceImplOnGpu::CopyOutput      0,25 ms je Bild
```

Eine Viertelmillisekunde. Das ist der vollständige Rückweg des fertigen Bildes
aus dem Grafikspeicher, den der fensterlose Betrieb erzwingt — die Stufe, für
die Accelerated Paint und Shared Textures gebaut werden.

Sie ist **ein Prozent** der gemessenen Strecke.

---

## 5. Leinwand gegen Textfeld gegen Monaco

Der Kontrollfall aus dem Auftrag: `keydown` → JavaScript zeichnet ein
Rechteck von zwanzig mal zwanzig Bildpunkten → Chromium liefert ein Bild.
Weniger Arbeit ist nicht möglich.

| | ungültige Fläche je Bild | A Eingabe→onPaint | B Upload | bis sichtbar |
|---|---|---|---|---|
| Leinwand (Kontrollfall) | 70,8 KB — **0,9 %** | **21,3 ms** | 0,2 ms | 25,6 ms |
| Textfeld | 4.717,6 KB — 59,2 % | **18,4 ms** | 11,3 ms | 31,5 ms |
| Monaco vollständig | 5.127,0 KB — 64,4 % | **27,6 ms** | 9,0 ms | 40,3 ms |

**Das Textfeld macht fünfundsechzigmal mehr Fläche ungültig als die Leinwand
und ist trotzdem drei Millisekunden schneller.** Wenn die Zeit Arbeit wäre,
wäre das unmöglich.

Die Sicht der Seite auf dieselben Anschläge, mit ihrer eigenen Uhr gemessen:

```text
                  keydown→Inhalt   keydown→rAF1   keydown→rAF2
Leinwand              0,1 ms          1,9 ms        34,2 ms
Textfeld              0,7 ms          1,3 ms        29,1 ms
```

Das JavaScript ist nach einer Zehntelmillisekunde fertig. Der erste
Bildaufruf kommt nach knapp zwei. Der zweite — der erst kommt, wenn das Bild
tatsächlich übergeben ist — **einen vollen Takt später**.

Der Auftrag hatte formuliert: „Wenn selbst ein kleiner Canvas-Paint ungefähr
die 20-ms-Basis zeigt, wird der Fall für den OSR-/Compositor-Unterbau
stärker." Der Canvas-Paint zeigt 21,3 ms. Aber der Trace weist den Verdacht
vom Readback weg: Es ist nicht der Unterbau, der rechnet. Es ist der Takt, der
wartet.

**Was hier nicht noch einmal steht, und warum.** Der Auftrag nennt unter
Punkt 3 vier Vergleichsseiten: `textarea`, `contenteditable`, Monaco minimal
und Monaco vollständig. Dieser Lauf misst drei Stufen und tauscht
`contenteditable` und Monaco minimal gegen den Kontrollfall Leinwand. Der
Grund ist die Beweislage: Beide sind in Schritt H bereits vermessen —
`contenteditable` lag zwischen Textfeld und Monaco, Monaco minimal und
vollständig trennten 0,8 ms. Sie unterscheiden sich in der Menge der Arbeit,
und genau diese Achse ist es, die sich als bedeutungslos herausgestellt hat.
Die Leinwand dagegen setzt die Arbeit auf ein Siebzigstel und trennt damit
zum ersten Mal Arbeit von Wartezeit. Vier Punkte auf einer folgenlosen Achse
hätten weniger gezeigt als ein Punkt außerhalb.

---

## 6. Fenstermodus gegen fensterlosen Betrieb

Dieselbe Seite, derselbe Tippweg über `Input.dispatchKeyEvent`, einmal in
unserem OSR-Browser und einmal in einem gewöhnlichen Chrome-Fenster.

```text
                        fensterlos (116)     Fenster (Chrome 152)
Leinwand   keydown→rAF2      34,2 ms              33,1 ms
Textfeld   keydown→rAF2      29,1 ms              33,1 ms
Monaco     keydown→rAF2         —                 33,0 ms
```

Die fehlende Zelle ist ein Fehler im Messablauf, kein Ausfall: Der Bericht
der Seite geht über die Konsole, und für die letzte Stufe schließt der
Messbildschirm den Browser, bevor die Zeile ankommt. Für Monaco liegt die
Zahl von außen vor (A 27,6 ms), die der Seite nicht. Beim nächsten Lauf
gehört das Schließen hinter den letzten Bericht.

Im Fenstermodus liegen 72 bis 82 Prozent aller Anschläge im selben Fach von
32 bis 36 ms. **Der fensterlose Betrieb ist innerhalb von Chromium nicht
langsamer als ein echtes Fenster.**

Ein Vorbehalt, der beim Messen fast zu einem Fehlschluss geführt hätte: Auch
das Chrome-Fenster lieferte zunächst nur 30,7 Bilder je Sekunde, obwohl der
Bildschirm mit 59 Hz läuft. Erst der Gegentest mit `--disable-gpu-vsync
--disable-frame-rate-limit` zeigte 58,1 Bilder je Sekunde im Leerlauf und
1.666 unter Last. Die 30 Hz im Fenster kommen also von der Bildsynchronisation
und nicht von einer Grenze der Maschine — anders als unsere exakten 33,33 ms,
die aus CEFs Voreinstellung stammen. Zwei verschiedene Ursachen, zufällig
dieselbe Zahl. Ohne die getrennte Taktmessung hätte der Vergleich „beide
gleich schnell" gelautet und wäre aus dem falschen Grund richtig gewesen.

---

## 7. Was Accelerated Paint realistisch am Tippgefühl ändern würde

**Am Tippgefühl: fast nichts. An Minecrafts Bildzeit: einiges.**

Accelerated Paint ersetzt den Weg „Chromium zeichnet ins Grafikgedächtnis →
kopiert zurück in den Hauptspeicher → wir laden es wieder hoch" durch eine
geteilte Textur. Die beiden Posten, die dabei wegfallen, sind gemessen:

```text
CopyOutput in Chromium          0,25 ms   (Latenz)
unser Upload B                  9,0 ms    (Minecrafts Renderthread)
```

Die 0,25 ms sind der Anteil an der Tipplatenz. Die 9 ms liegen auf Minecrafts
Renderthread und kosten dort Bildzeit — sichtbar beim Rollen, nicht beim
Tippen. Gegen die 16,7 ms mittlere Wartezeit auf den Takt richtet Accelerated
Paint nichts aus.

**Der Hebel, der etwas ausrichten würde, ist ein anderer:**

```text
Takt 30 Hz (heute):   33,33 ms Raster,  16,7 ms mittlere Wartezeit
Takt 60 Hz:           16,67 ms Raster,   8,3 ms mittlere Wartezeit
                                        ————————
                                         8,4 ms je Anschlag gespart
```

Für Monaco hieße das rechnerisch A von 27,6 auf rund 19 ms und die
Gesamtstrecke von 40,3 auf rund 32 ms. Das ist mehr, als jede
Monaco-Optimierung in Schritt H hergegeben hätte — und es kostet keine
geteilte Textur, sondern eine Zahl in `CefBrowserSettings`.

---

## 8. Welche Chromium-Prozesse den Speicher belegen

**Zuerst eine Korrektur an Schritt H.** Dort standen rund 800 MB, und die
Schlussfolgerung lautete, das sei Chromium selbst. Die Hälfte davon war ein
Artefakt der Messung.

Frischer Client, Oberfläche einmal geöffnet, keine Seitenwechsel:

```text
gpu-process       152 MB
gpu-process        65 MB
renderer          116 MB   ← die Oberfläche mit Monaco
renderer           29 MB
renderer           29 MB
utility            32 MB
utility            32 MB
utility            21 MB
utility            21 MB
                  ——————
                  499 MB
```

Nach einem Messlauf mit mehreren Seitenwechseln dagegen:

```text
gpu-process       160 MB
10 × renderer   31–143 MB   zusammen rund 660 MB
3 × utility      23–51 MB
                  ——————
                  rund 800 MB
```

Die Kennungen der zehn Renderer verraten die Ursache: viermal
`renderer-client-id=5`, viermal `13`, je einmal `7` und `11`. Das sind
Prozesse abgelöster Seiten, die nach der Navigation liegenbleiben. Jeder
`location.href`-Wechsel in den Messreihen hat einen hinterlassen.

**Für die Oberfläche im Normalbetrieb gilt die erste Tabelle.** Monaco kostet
116 MB in seinem Renderer; die übrigen 383 MB sind Chromiums Unterbau. Zwei
GPU-Prozesse statt einem sind auffällig und nicht weiter verfolgt — sie
tauchten in beiden Messungen auf.

Java (Minecraft selbst) lag bei 5.329 MB und ist von dieser Frage unberührt.

---

## 9. Versions- und API-Delta

### Was wir fahren

```text
CEF            116.0.27+gd8c85ac+chromium-116.0.5845.190   (August 2023)
Chromium       116.0.5845.190
V8             11.6.189.20
JCEF-Fork      CinemaMod, eaeb3d4370aa, 22. Oktober 2024
java-cef       a78e832f9f13c2c688caea3d04d8b84fcd238d94
MCEF           4cecd7b1f009, 10. Juni 2025
```

### Was aktuell ist

```text
stable   CEF 151.3.24+g2384915   Chromium 151.0.7922.174
beta     CEF 152.0.5+gb129680    Chromium 152.0.7977.54
```

**Fünfunddreißig Chromium-Hauptfassungen Abstand.** Das ist zuerst eine Frage
der Sicherheitsaktualisierungen und erst danach eine der Geschwindigkeit.

> Nachtrag vom selben Tag: Hier stand zunächst „CEF 143, Dezember 2025,
> siebenundzwanzig Hauptfassungen". Diese Angabe stammte aus einer
> Enzyklopädie und war acht Monate alt. Die Zahlen oben sind an der Quelle
> abgelesen — `cef-builds.spotifycdn.com/index.json`, für alle vier
> Plattformen identisch verfügbar.

### Welche OSR-Schnittstellen fehlen wo

| | unser Fork | upstream java-cef | upstream CEF |
|---|---|---|---|
| `CefBrowserSettings` | **fehlt ganz** | vorhanden | vorhanden |
| `windowless_frame_rate` | **nicht setzbar** | setzbar (1–60) | setzbar |
| `OnAcceleratedPaint` | fehlt | **nicht gebunden** | vorhanden |
| Shared Textures | fehlt | **nicht gebunden** | vorhanden |
| External Begin Frame | fehlt | nicht gebunden | vorhanden |
| IME | keine Bindung | **keine Bindung** | vorhanden |

Belege: Im Fork gibt es keine Datei mit `BrowserSettings` im Namen, und
`CefClient.createBrowser(String url, boolean isTransparent, …)` nimmt gar
keine Einstellungen entgegen. Weder `windowless_frame_rate` noch
`OnAcceleratedPaint` kommen im Java- oder im nativen Teil vor.

Upstream ist `java/org/cef/CefBrowserSettings.java` vorhanden und enthält
**genau ein Feld**: `windowless_frame_rate`, Wertebereich 1 bis 60,
Voreinstellung 30. Die Klasse existiert also ausschließlich für die eine
Einstellung, die uns fehlt — und sechzig ist zugleich die Obergrenze, mehr
gibt CEF nicht her.

`org/cef/browser/CefBrowser.java` hat upstream **keine** IME-Methoden:
weder `ImeSetComposition` noch `ImeCommitText` oder
`ImeFinishComposingText`. Die Schnittstelle deckt Navigation, Zoom,
Dateioperationen, JavaScript und Entwicklerwerkzeuge ab, aber keine
Eingabemethoden. CEF selbst hat sie; gebunden sind sie in Java nirgends.

**Der wichtigste Punkt für die Entscheidung:** `OnAcceleratedPaint` fehlt
nicht nur uns, sondern auch dem aktuellen upstream java-cef. Issue #506 ist
seit dem 1. Juni 2025 offen und hält fest: „Java CEF lacks bindings for
OnAcceleratedPaint, meaning we cannot make use of this feature." Ein Pull
Request #524 vom 29. Januar 2026 fügt sie hinzu — Windows über
D3D11-Texturhandles in OpenGL, Linux über dmabuf und EGL, macOS über
IOSurface. Er ist **nicht zusammengeführt**; Marshall Greenblatt hat sich
zustimmend geäußert und wartet auf die macOS-Seite.

Wer heute Accelerated OSR unter Java will, muss diesen Pull Request also
selbst tragen und pflegen. Ein Wechsel auf aktuelles upstream JCEF allein
brächte ihn nicht mit.

---

## 10. Die Abschlussfrage

> Ist eine eigene aktuelle CEF/JCEF-Runtime mit Accelerated OSR jetzt
> technisch gerechtfertigt, oder würde sie hauptsächlich Kopien beseitigen,
> ohne unser Tippgefühl wesentlich zu verbessern?

**Von den drei angebotenen Aussagen trifft B zu:** Der Readback dominiert
nicht. Mit 0,25 ms je Bild ist er als Ursache des Tippgefühls ausgeschlossen.
Accelerated OSR würde Kopien beseitigen — davon profitiert Minecrafts
Renderthread mit rund 9 ms je Bild, das Tippgefühl praktisch nicht.

Aber die Frage hat eine zweite Hälfte, und die fällt anders aus als erwartet.
Es ist nicht Blink, nicht Layout und nicht Paint, was die Strecke füllt — es
ist ein Takt von dreißig Bildern je Sekunde, den unsere JCEF-Fassung nicht
verstellen kann, weil ihr `CefBrowserSettings` vollständig fehlt.

**Eine eigene Runtime ist gerechtfertigt — aber nicht wegen Accelerated
Paint.** Die drei Gründe, nach Gewicht:

1. **Der Bildtakt.** `windowless_frame_rate` von 30 auf 60 spart rund 8,4 ms
   je Anschlag. Kein Shared-Texture-Interop, keine Plattformpfade, eine Zahl.
   Das ist der einzige gemessene Hebel, der das Tippgefühl wirklich bewegt.
2. **Chromium 116 aus dem August 2023.** Siebenundzwanzig Hauptfassungen ohne
   Sicherheitsaktualisierungen sind für eine Mod, die fremde Seiten anzeigen
   könnte, das ernstere Problem.
3. **IME.** Ohne Bindung gibt es keine Eingabe für Sprachen, die eine
   brauchen. Für einen Editor ist das keine Randnotiz — und hier hilft auch
   ein Wechsel auf upstream nicht, denn dort fehlt sie ebenso. Das wäre
   Eigenarbeit von Grund auf, nicht Abschreiben.

Accelerated Paint gehört auf denselben Weg, aber ans Ende: als Entlastung von
Minecrafts Renderthread, nicht als Antwort auf die Latenz.

### Wo das auf die vier Wege aus Punkt 10 des Auftrags fällt

Der billigste Weg zum größten gemessenen Gewinn ist **B — MCEF/JCEF patchen**,
und zwar zunächst nur an einer Stelle: `CefBrowserSettings` mit
`windowless_frame_rate` durchreichen. Das ist ein klar begrenzter Eingriff in
den nativen Teil und braucht weder einen eigenen CEF-Build noch eine eigene
native Schicht.

Was danach kommt — eigener JCEF-Build (C) für den aktuellen Chromium-Unterbau,
irgendwann der Accelerated-Paint-Pfad — ist eine Entscheidung, die von der
ersten Messung nach dem Takt-Patch abhängt. Vorher lohnt sie nicht.

---

## Was bewusst nicht getan wurde

Kein Accelerated Paint, kein PBO, kein CEF-Fork als Produkt, kein Language
Service, kein Umbau des Prozessmodells. Der Auftrag war zerlegen, nicht bauen.

## Offen geblieben

- Warum zwei GPU-Prozesse laufen statt einem.
- Ob `windowless_frame_rate = 60` die gerechnete Ersparnis tatsächlich
  liefert. Das lässt sich erst nach dem Patch messen und ist die erste Zahl,
  die danach fällig ist.
