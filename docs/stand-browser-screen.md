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

<!-- CLIPBOARD -->

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

<!-- MESSUNG -->

---

## 11. Bekannte Lücken

<!-- LUECKEN -->
