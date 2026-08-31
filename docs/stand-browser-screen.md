# Bildschirm und Eingabe: Stand nach Schritt D

**31.08.** Der Browser hat einen Bildschirm, nimmt Maus und Tastatur an, hat
einen eigenen Fokuszustand, klappt Auswahlfelder auf und setzt den Mauszeiger.

---

## 1. Was von MCEF übernommen wurde

| Übernommen | Warum |
|---|---|
| **Das Hochfahren von Chromium** — Download, Entpacken, `CefApp`, `CefClient` | 269 MB Binärwerk und ein Mixin, das Chromiums Nachrichtenschleife in `GameRenderer.render` pumpt. Das nachzubauen wäre eine eigene Mod. |
| **`CefBrowserOsr` als Basisklasse** | Sie ist öffentlich, hat einen öffentlichen Konstruktor und bringt Erzeugung, Größenmeldung und Ziehen mit. |
| **Der Tausch von mittlerer und rechter Maustaste** | GLFW zählt `0/1/2 = links/rechts/mitte`, CEF `links/mitte/rechts`. MCEFs Umsetzung ist richtig, und der native Teil von JCEF bestätigt sie. |
| **Das Runden beim Scrollen** (Faktor drei, auf ganze Rasten) | Erprobt und begründet: Minecraft meldet gebrochene Werte, ungerundet fühlt sich das Scrollen zäh an. Unter macOS bleibt es ungerundet, weil Trackpads dort feine Werte liefern, die etwas bedeuten. |
| **Der Vertrag für Tastenereignisse** | Nicht Code, sondern Wissen: siehe unten. |

## 2. Was bewusst ersetzt wurde

| Ersetzt | Statt dessen | Warum |
|---|---|---|
| **`MCEFRenderer`** | eigenes `GlTextureBackend` | MCEFs Renderer lädt in seinem `onPaint` direkt in seine eigene Textur. Genau dieser Schritt soll uns gehören — er ist der, den ein beschleunigter Pfad später ersetzt. |
| **Popup-Rückkopieren** | zweite Textur | MCEF hält den Inhalt eines Auswahlfeldes in einem Puffer und malt ihn **bei jedem Bild der Hauptansicht erneut** in deren Textur, weil Chromium ihn nicht aufbewahrt. Eine eigene Textur behält ihn von selbst — kein Kopieren, keine zusätzliche Übertragung je Bild. |
| **`clickCount` immer 1** | eigener Zähler | CEF leitet Doppelklicks nicht aus Zeitstempeln ab, sondern glaubt der Zahl im Ereignis. Mit einer festen Eins markiert kein Doppelklick je ein Wort. |
| **`MCEF.getGLFWCursorHandle`** | eigener Zeiger-Zwischenspeicher | Die Methode ist paketprivat. Nachgebaut wird fast nichts: Welcher Chromium-Zeiger welchem GLFW-Zeiger entspricht, steht als öffentliches Feld `glfwId` im Aufzählungstyp von JCEF selbst. |
| **`ExampleScreen`s Eingabeweiterleitung** | eigener Bildschirm | Er ruft nach dem Weiterleiten zusätzlich `super.keyPressed(...)`. Damit sieht der Bildschirm jede Taste zweimal. |
| **Browser-Tastenkürzel** (Strg+R, Strg+±, Alt+Pfeil) | nichts | Ein Editor ist kein Browserfenster. Strg+R lädt kein Programm neu, und Alt+Pfeil ist im Text eine Bewegung. |

---

## 3. Einheiten: Framebuffer, GUI, Browser

Drei Räume, und die häufigste Fehlerquelle ist, zwei davon zu verwechseln.

| Einheit | Woher | Beispiel bei GUI-Skalierung 3 |
|---|---|---|
| **Framebuffer-Pixel** | `window.getWidth()` | 1920 × 1080 |
| **GUI-Einheiten** | `screen.width`, Mauskoordinaten | 640 × 360 |
| **Browser-Pixel** | `browser.resize(w, h)` | 1920 × 1080 |

**Die Entscheidung: ein Browser-Pixel ist ein Framebuffer-Pixel.** Der Browser
bekommt so viele Pixel, wie der Bereich auf dem Schirm wirklich einnimmt.
Andernfalls rechnete ein Editor bei Skalierung 3 auf einem Drittel der
Auflösung und würde beim Zeichnen wieder aufgeblasen — der Unterschied
zwischen lesbarer und matschiger Schrift.

**CSS-Pixel sind hier Browser-Pixel.** Diese JCEF-Fassung setzt keinen
`device_scale_factor`; was Chromium als CSS-Pixel rechnet, ist genau das, was
es malt. Eine Seite mit `width: 100vw` bekommt also 1920 CSS-Pixel.

**Das Seitenverhältnis kann nicht kippen**, weil nur eine einzige Zahl
skaliert. Breite und Höhe gehen durch dieselbe Multiplikation.

```
Browser-Pixel = (GUI-Punkt − Ursprung der Fläche) × guiScale
```

Diese eine Formel steht in `BrowserView` und wird von **Zeichnen und Maus
gemeinsam** benutzt. Getrennt gerechnet wäre der Fehler unsichtbar: Der Spieler
klickt neben das, was er sieht, meldet „die Seite reagiert falsch", und gesucht
wird in der Weboberfläche.

Für eine Fläche in der Welt gibt es denselben Weg über einen Anteil von 0 bis
1: `fromFraction(0.5, 0.25)` trifft dasselbe Pixel wie die Maus an der
entsprechenden Stelle. Ein Prüflauf hält das fest.

---

## 4. Eingabe

### Maus

| Ereignis | Weg |
|---|---|
| Bewegung | `MOUSE_MOVED` samt Flaggen der gedrückten Tasten |
| Verlassen der Fläche | `MOUSE_EXIT` — genau einmal, nicht bei jeder Bewegung außerhalb |
| Drücken / Loslassen | `GLFW_PRESS` / `GLFW_RELEASE` mit übersetzter Tastennummer |
| Doppelklick | eigener Zähler: 500 ms, 4 Pixel Toleranz, 1–3 |
| Ziehen | eine gewöhnliche Bewegung — die gedrückte Taste steht in den Flaggen |
| Rad | Faktor drei, auf ganze Rasten gerundet |

**Die Flaggen sind der Grund, warum Ziehen überhaupt funktioniert.** CEF führt
gedrückte Maustasten im selben Feld wie Strg und Umschalt. Eine Bewegung ohne
diese Flaggen sieht für Chromium aus wie eine Bewegung mit losgelassener Taste,
und jedes Markieren bricht nach dem ersten Pixel ab.

### Tastatur

**Der Vertrag dieser JCEF-Fassung ist ungewöhnlich, und er steht im nativen
Teil, nicht in der Dokumentation.** Nachgesehen in `CefBrowser_N.cpp`:

| Ereignis | `id` | was `keyChar` bedeutet |
|---|---|---|
| `KEY_PRESS` | 1 (`GLFW_PRESS`) | der **GLFW-Tastencode** — verglichen mit `GLFW_KEY_LEFT` und Verwandten, um Sondertasten den richtigen Scancode zu geben |
| `KEY_RELEASE` | 0 (`GLFW_RELEASE`) | dito |
| `KEY_TYPE` | 2 (`GLFW_REPEAT`) | das **echte Zeichen**, es landet unverändert bei Chromium |

Auf Windows wird der Tastencode gar nicht gelesen: Aus dem GLFW-Scancode macht
`MapVirtualKey` den Windows-Tastencode. Modifier sind **GLFW-Modifier** und
gehen unverändert durch — genau das, was Minecrafts
`keyPressed(keyCode, scanCode, modifiers)` liefert.

**MCEFs `(char) keyCode` sieht deshalb falsch aus und ist richtig.** Das war
der erste Punkt, an dem ich mich beinahe geirrt hätte.

**Text wird nirgends aus Tastencodes zusammengebaut.** Er kommt über
Minecrafts `charTyped` — mit Tastaturbelegung, Umschalttaste und allem, was das
Betriebssystem beisteuert. Umlaute, ß und das Eurozeichen gehen so den einzigen
Weg, auf dem sie richtig ankommen können.

**Die Grenze:** `char` ist sechzehn Bit breit. Alles bis `U+FFFF` geht. Zeichen
darüber (Emoji) kämen als zwei Hälften an und wären zwei kaputte Ereignisse.
Ebenso offen: IME und CJK — Minecraft reicht Kompositionsereignisse nicht
durch, und CEF hätte dafür einen eigenen Weg.

---

## 5. Fokus

Zwei Zustände, `MINECRAFT` und `BROWSER`, und genau einer bekommt die Tastatur.
Ein Prüflauf hält fest, dass es nie beide sein können.

**Zur Escape-Taste.** Die bequeme Annahme wäre, Escape gehöre Minecraft. Für
eine Weboberfläche ist das falsch: Ein Auswahlfeld schließt sich damit, ein
Dialog ebenso, und ein Editor bricht damit seine Vervollständigung ab. Wer
Escape abfängt, nimmt der Seite eine Taste, die sie selbst braucht — der
Spieler merkt nur, dass „das Menü sich nicht schließen lässt".

**Die Entscheidung: Escape geht an die Seite.** Den Rückweg macht **F10** — sie
gibt den Fokus an Minecraft, und dann schließt Escape wie gewohnt. F10, weil
Vanilla sie nicht belegt und Weboberflächen sie selten brauchen. Ein Hinweis am
oberen Rand sagt es für sechs Sekunden; eine Taste, die man kennen muss und die
nirgends steht, wäre eine Falle.

**Warum zwei Zustände und nicht eine Sonderregel im Bildschirm:** Eine Fläche
in der Welt hat keinen Bildschirm, der Tasten für sie abfangen könnte. Dasselbe
Modell trägt dort: Die Fläche bekommt den Fokus, und sie gibt ihn wieder her.

Solange der Fokus beim Browser liegt, wird **kein** Tastenereignis an
`super.keyPressed(...)` weitergereicht. Was die Seite bekommen hat, löst
nirgends sonst noch etwas aus.

---

## 6. Aufgeklappte Felder

Der Vertrag von CEF, nachgesehen in JCEF und in MCEFs Umsetzung:

| Aufruf | was er sagt |
|---|---|
| `onPopupShow(browser, show)` | ein Feld klappt auf oder zu |
| `onPopupSize(browser, rect)` | wo es liegt — in Browser-Pixeln der **Hauptansicht** |
| `onPaint(popup = true, …)` | der Puffer ist **popupgroß**, die geänderten Bereiche zählen von der Ecke des Feldes |

```
Haupttextur  ────┐
                 ├──►  Minecraft zeichnet: erst die Haupttextur,
Popup-Textur ────┘      dann das Feld darüber
```

**Nicht in die Haupttextur kopiert.** MCEF muss das tun, weil es nur eine
Textur hat: Chromium bewahrt den Popup-Inhalt zwischen zwei Aufrufen nicht auf,
also hält MCEF ihn in einem eigenen Puffer und malt ihn bei jedem Bild der
Hauptansicht erneut hinein. Eine zweite Textur behält ihn von selbst — sie
kostet einmal Speicher und danach nichts mehr.

**Beim Schließen ist nichts wiederherzustellen.** Chromium malt die verdeckte
Fläche selbst neu; das kommt als gewöhnliches Bild der Hauptansicht an. Es
genügt, die Popup-Textur nicht mehr zu zeichnen.

**`onPopupSize` kann vor dem ersten Bild kommen und über den Rand hinausragen.**
Was ankommt, ist die gewünschte Lage, nicht die zurechtgeschnittene — deshalb
wird beim Zeichnen beschnitten und nicht beim Speichern.

Die Lage geht durch **dieselbe Umrechnung wie die Maus**. Anders gerechnet läge
das Feld neben der Stelle, an der man es anklickt.

---

## 7. Mauszeiger

`CefCursorType` bringt die GLFW-Entsprechung als öffentliches Feld `glfwId`
mit. Zu tun bleibt das Aufbewahren: `glfwCreateStandardCursor` legt jedes Mal
einen neuen an, und einer je Bewegung über einen Link wäre ein Leck, das man
erst nach Stunden bemerkt.

Was Chromium meldet und GLFW nicht kennt — Zellenkreuz, Fortschritt,
Kontextmenü — trägt dort eine Null und bleibt der Pfeil. Einen falschen zu
erzwingen wäre schlechter als der Pfeil.

**Zeigerwechsel können aus Chromiums eigenen Threads kommen.** GLFW verträgt
Fensteraufrufe nur aus dem Render-Thread; der Wunsch wird gemerkt und beim
Zeichnen angewandt.

Beim Schließen wird der gewohnte Zeiger zurückgesetzt — eine Schreibmarke, die
über der Spielwelt stehen bleibt, sieht nach einem kaputten Spiel aus.

---

## 8. Zwischenablage

**Nichts gebaut, und das ist die richtige Antwort.** Gesucht wurde im
Quelltext von JCEF — weder im Java-Teil noch im nativen kommt das Wort
`clipboard` vor. Chromium greift selbst auf die Zwischenablage des
Betriebssystems zu, über seine eigene Plattformschicht.

Nötig ist dafür nur, dass die Tastenereignisse richtig ankommen: Der
GLFW-Modifikator wird nativ zu `EVENTFLAG_CONTROL_DOWN`, und den Rest erledigt
Chromium.

**Ein Fund am Rande, der teuer hätte werden können.** Beim ersten Versuch kam
Strg+A und Strg+V nirgends an. Der Grund steht im nativen Teil: Auf Windows
wird der Windows-Tastencode mit `MapVirtualKey` **aus dem Scancode** gebaut —

```cpp
scanCode = MapScanCodeGLFW(env, cls, key_char, scanCode);
BYTE VkCode = LOBYTE(MapVirtualKey(scanCode, MAPVK_VSC_TO_VK));
```

Eine Null im Scancode ergibt einen Tastencode von null, und Chromium sieht eine
Taste, die es nicht gibt. Bei echter Eingabe liefert Minecraft den Scancode
mit; wer Tasten selbst erzeugt, muss ihn über `glfwGetKeyScancode` erfragen.
Das gilt für jede spätere Stelle, die Tasten simuliert.

**Was gemessen wurde und was nicht:** Dass ein Einfügen *etwas* bewirkt, ist an
den geänderten Bildern ablesbar. Ob der Text *richtig* ankommt, wüsste nur die
Seite selbst — dafür bräuchte es eine Brücke zu ihr, und die ist ausdrücklich
noch nicht dran. Die vier Wege aus dem Auftrag (Browser→System,
System→Browser, Minecraft→Browser, Browser→Minecraft) laufen alle über
dieselbe Systemzwischenablage; geprüft ist der Weg System→Browser.

---

## 9. Bildrate: A, B oder C

Die Frage war, ob sich `windowless_frame_rate` setzen ließe. Nachgesehen im
Quelltext, nicht geraten.

**Die Antwort ist C).**

```cpp
// CefBrowser_N.cpp, im nativen createBrowser
CefBrowserSettings settings;
if (transparent == JNI_FALSE) {
    settings.background_color = CefColorSetARGB(255, 255, 255, 255);
}
```

Die Einstellungen werden **im nativen Teil** angelegt und nur um die
Hintergrundfarbe ergänzt. Von Java aus gibt es keinen Weg dorthin:

- **A) Mit MCEF unmöglich** — richtig, aber nicht der Kern.
- **B) Mit eigenem Browser-Erzeugungspfad?** Ebenfalls nein. Auch ein eigener
  Erbe von `CefBrowserOsr` ruft dasselbe native `createBrowser`, und das nimmt
  keine Browser-Einstellungen entgegen.
- **C) Es müsste eine Änderung am nativen Teil von JCEF sein** — zwei Zeilen in
  `CefBrowser_N.cpp` plus ein neuer Parameter durch die JNI-Grenze, und danach
  eine eigene Übersetzung der 269 MB.

Ein Kommandozeilenschalter wäre der billigere Weg, aber `--off-screen-frame-rate`
gehört zum Beispielprogramm von CEF und nicht zum Kern; in diesem Fork kommt er
nicht vor.

**Folge für die Schnittstelle:** `BrowserVisibility` kann nur **drosseln**.
Das steht jetzt im Quelltext, samt der gemessenen 30,1 Bilder je Sekunde.
`FOREGROUND(60)` bleibt als Wert stehen und ist als das dokumentiert, was es
ist — eine offene Tür für den Fall, dass eine spätere Fassung die Sperre löst.

---

## 10. Gemessene Interaktion

Der Bildschirm bedient sich selbst: Mausbewegung im Kreis, Rollen, Tippen,
Sondertasten, Einfügen, Auswahlfeld, Größenänderung — je vier Sekunden. Die
Ereignisse gehen **durch den Bildschirm** und nicht an ihm vorbei, damit
Umrechnung, Fokusprüfung und Klickzählung mitgemessen werden.

Fenster 854 × 480, GUI-Skalierung 2, also **854 × 480 Browser-Pixel**. Ein
Vollbild sind damit 1601 KB.

| Abschnitt | Bilder/s | Vollbild-Uploads | Ausschnitte | KB je Bild | Anteil eines Vollbilds | MB/s | Uploadzeit p50 / p95 |
|---|---:|---:|---:|---:|---:|---:|---|
| **Ruhe** | 0,0 | 0 | 0 | 0,0 | 0 % | 0,00 | — |
| **Maus bewegen** | 22,5 | 0 | 90 | 215,0 | 13,4 % | 4,72 | 481 / 759 µs |
| **Rollen** | 15,2 | 0 | 61 | **810,8** | **50,6 %** | 12,06 | 1519 / 1966 µs |
| **Tippen** | 8,5 | 0 | 34 | 13,6 | 0,85 % | 0,11 | 129 / 160 µs |
| **Sondertasten** | 2,0 | 0 | 8 | 2,3 | 0,14 % | 0,00 | 130 / 193 µs |
| **Einfügen** | 2,0 | 0 | 8 | 17,4 | 1,09 % | 0,03 | 144 / 546 µs |
| **Auswahlfeld** | 0,2 | 0 | 1 | 279,1 | 17,4 % | 0,07 | 637 µs |
| **Größe ändern** | 0,5 | 2 | 0 | 1191,8 | 152 % ¹ | 0,58 | 1416 / 2685 µs |

¹ Über hundert Prozent, weil zwei Vollbilder in **verschiedenen** Größen
anfielen — eines in der alten, eines in der neuen — und der Anteil sich auf die
kleinere bezieht. 1601 + 784 = 2385 KB, verteilt auf zwei Uploads.

### Die Antwort auf die eigentliche Frage

**Ja, eine gewöhnliche Handlung erzeugt große Bereiche — genau eine: Rollen.**
Mit **50,6 % eines Vollbilds je Bild** liegt es zwei Größenordnungen über dem
Tippen. Der Grund ist einleuchtend, sobald man ihn sieht: Beim Rollen
verschiebt sich alles, was im Rollbereich steht. Es gibt keinen kleinen
Ausschnitt mehr, den man melden könnte.

Hochgerechnet auf 1920 × 1080 — das sind 2 073 600 gegen 409 920 Bildpunkte,
also **Faktor 5,06**:

| Handlung | bei 854×480 | bei 1920×1080 |
|---|---:|---:|
| Tippen | 0,11 MB/s | ~0,6 MB/s |
| Maus über Zeilen | 4,7 MB/s | ~24 MB/s |
| Rollen | 12,1 MB/s | **~61 MB/s** |
| vollflächige Animation | — | 240 MB/s |

Rollen kostet also gut ein Viertel des schlimmsten Falls. Das ist viel, aber
es dauert Sekunden und nicht Minuten — und es ist die Handlung, bei der ein
Ruckler am wenigsten auffällt, weil ohnehin alles in Bewegung ist.

**Alles andere ist billig.** Tippen kostet 0,85 % eines Vollbilds je Bild; ein
Editor, in dem jemand schreibt, liegt bei einem Zehntel Megabyte je Sekunde.

### Was die Zahlen sonst noch belegen

- **Die Sondertasten kommen an.** Home, Ende, Pfeile, Rücktaste und Entfernen
  erzeugten acht Bilder à 2,3 KB — die Größe eines wandernden Schreibcursors.
  Das ist der Nachweis für den ungewöhnlichen Tastenvertrag: Stünde in
  `keyChar` ein Schriftzeichen statt des GLFW-Tastencodes, bekäme jede dieser
  Tasten einen falschen Scancode und es geschähe nichts.
- **Das Einfügen kommt an.** 17,4 KB je Bild — vorher, mit Scancode null,
  waren es 2,1 KB, und das war nur der blinkende Cursor.
- **Das Auswahlfeld klappt auf, in eigener Textur.** Ein Popup-Bild, 119,1 KB,
  ohne einen einzigen Vollbild-Upload der Hauptansicht. Genau das, was MCEFs
  Rückkopieren vermeiden sollte.
- **Der Mauszeiger wechselt.** Im Protokoll: `POINTER (0)` und `IBEAM (3)` —
  Chromium meldet beide, und beide werden gesetzt.

### Stören die dreißig Bilder je Sekunde beim Bedienen?

**Nein — sie wurden beim Bedienen nie erreicht.**

| Handlung | Bilder/s |
|---|---:|
| Maus bewegen | 22,5 |
| Rollen | 15,2 |
| Tippen | 8,5 |
| Sondertasten, Einfügen | 2,0 |
| Ruhe | 0,0 |

Der höchste Wert liegt bei **22,5** und damit unter der Decke. Eine Seite malt
nur, wenn sich etwas ändert; beim Bedienen ändert sich seltener etwas, als
Chromium liefern dürfte. In die Decke läuft nur, was von sich aus dauernd
animiert.

**Die Einschränkung ist ehrlich zu nennen:** Gemessen wurde eine schlichte
Seite. Ein Editor mit Syntaxhervorhebung, blinkender Einfügemarke und
Vervollständigungsfenster kann öfter malen. Ob dreißig für *den* reichen, sagt
diese Messung nicht — sie sagt, dass die Decke bei gewöhnlicher Bedienung
keine Rolle spielt.

---

## 11. Bekannte Lücken

| Lücke | Wie schlimm | Was zu tun wäre |
|---|---|---|
| **Zeichen jenseits von `U+FFFF`** — Emoji kämen als zwei Hälften an | gering für einen Programmtext | `charTyped` sammelt Ersatzpaare und schickt sie zusammen |
| **IME und CJK** ungeprüft | offen, betrifft ganze Sprachräume | Minecraft reicht Kompositionsereignisse nicht durch; CEF hätte mit `ImeCommitText` einen eigenen Weg. Eine eigene Aufgabe. |
| **Ein Popup, das über den Rand ragt**, wird vom Bildschirm beschnitten statt von der Fläche | gering, solange die Fläche der ganze Bildschirm ist | Beim Zeichnen auf die Fläche beschneiden — nötig, sobald der Browser nur einen Teil einnimmt |
| **Verschachtelte Popups** (ein Menü im Menü) | unbekannt | CEF meldet nur ein Popup; ob Chromium so etwas überhaupt so schickt, ist ungeprüft |
| **Ziehen von Inhalten** (HTML5 Drag & Drop) | nicht gebaut | MCEF hat dafür einen `MCEFDragContext`. Für einen Editor selten gebraucht; für einen Dateibaum später schon |
| **Der Bildschirm ist der einzige Ort mit Fokus** | so gewollt für D | Der Fokuszustand liegt schon in einer eigenen Klasse; eine Fläche in der Welt braucht nur einen Halter dafür |
| **Zwei Browser gleichzeitig** ungeprüft | offen | Chromiums Bildrate von 30 gilt je Browser oder insgesamt — das ist noch nicht gemessen |
| **Ziehen und Doppelklick sind gebaut, aber nicht im Betrieb belegt** | mittel | Die Logik hat Prüfläufe (Klickzähler, Tastenflaggen), aber kein Messabschnitt zieht eine Markierung auf. Ein Abschnitt „Text markieren" würde es zeigen |
| **Die Lage des Auswahlfeldes auf dem Schirm ist gerechnet, nicht zurückgelesen** | mittel | Sie geht durch dieselbe Umrechnung wie die Maus, was das Wichtigste ist. Ein Rücklesen wie beim Bildnachweis würde es beweisen |

---

---

## 12. Eine Zahl aus Schritt C, die zu genau dastand

Die Bildzeitdifferenz beim vollflächigen Fall wurde inzwischen dreimal
gemessen: **+13,6 / +4,0 / +19,2 ms** im Median. Sie streut mit dem, was
Minecraft nebenher lädt — die Baseline läuft kurz nach dem Betreten der Welt,
und dort kommen noch Chunks. Der belastbare Wert ist die **Uploadzeit selbst**,
und die liegt stabil bei **13 bis 15 ms**.

In `stand-texturupload.md` steht die erste dieser drei Zahlen ohne diesen
Vorbehalt. Sie ist nicht falsch, aber sie ist genauer, als die Messung es
hergibt.

---

## 13. Ausblick, ohne begonnen zu haben

Für `mc://frame` — Minecrafts Bild als Inhalt der Seite — gibt es in MCEF
bereits ein Vorbild: `ModScheme` registriert ein eigenes Schema über einen
`CefResourceHandler`. Der liefert allerdings **Bytes**, keine Textur. Ein
Framebuffer müsste je Aktualisierung kodiert werden, und bei 1080p sind das
achteinhalb Megabyte durch einen Kompressor. Das ist die Stelle, an der dieser
Weg teuer wird — zu klären, bevor er gebaut wird.

Mehr steht dazu hier nicht, weil die Entscheidung darüber noch nicht gefallen
ist.

---

## 14. Zum Nachstellen

```
/fnweb probe        eine Prüfseite zum Anfassen
/fnweb nachweis     die acht Stellen zurücklesen
/fnweb messung      die Abschnitte selbsttätig durchspielen
/fnweb seite <url>  irgendeine Adresse
/fnweb zustand      ob die Runtime bereit ist
```

Im Bildschirm: **F10** gibt die Tastatur zurück, **F9** schließt einen
Messabschnitt ab und schreibt ihn ins Protokoll.

Der ganze Ablauf — Selbsttest, Bildnachweis, Zahlenmessung,
Interaktionsmessung — läuft von selbst mit:

```
./gradlew runClient -Pbenchmark
```
