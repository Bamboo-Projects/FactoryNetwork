# Handprüfung Monaco

**Status: gefahren am 1. September 2026 auf der eigenen Laufzeitumgebung.
Drei Fehlschläge, alle drei behoben und alle drei von einem Menschen
nachgeprüft. Die Liste ist damit bestanden.**

Diese Liste kann kein Programm abhaken. Alles darin hängt an einem Menschen an
einer echten Tastatur — an einem deutschen Layout, an dem Gefühl, ob Scrollen
richtig herum läuft, und daran, ob ein Zeichen erscheint oder ein Menü
aufgeht.

Die maschinelle Testmatrix war grün (A4b: 40 von 40; im Spiel laufen alle
Stufen von `-Pidebench` durch). **Sie hat trotzdem drei Dinge nicht gesehen,
die ein Mensch in vier Minuten findet.** Genau deshalb gibt es diese Liste.

---

## So wird geprüft

```text
pwsh -File tools/runtime/build-jcef.ps1     falls die Laufzeitumgebung fehlt
./gradlew runClient -Pfnruntime -Pide       Editor öffnet sich, F6 öffnet erneut
```

Zum Vergleich derselbe Lauf ohne `-Pfnruntime` — dann läuft alles auf MCEF wie
bisher. **Wo sich beide unterscheiden, ist der interessante Fall.**

---

## Das Ergebnis

| Nr. | Prüfung | Erwartung | eigene Laufzeit |
|---|---|---|---|
| 1 | Fließtext tippen | zeichengenau, keine Dopplungen | **ja** |
| 1a | **Eingabetaste** | erzeugt einen Zeilenumbruch | **ja**, nach Befund 1 |
| 2 | Deutsches Layout: `ä ö ü ß` | erscheinen korrekt | **ja** |
| 3–5 | AltGr: `@ € \ ~ \|` | erscheinen, kein Kürzel geht auf | **ja** |
| 6 | Strg+C und Strg+V | Zwischenablage in beide Richtungen | **ja** |
| 7 | Strg+F | Suchfeld öffnet, Eingabe landet darin | **ja** |
| 8 | Strg+S | erreicht Monaco, nicht Minecraft | **ja** |
| 9 | Mehrfachcursor (`Alt+Klick`) | zweiter Cursor entsteht | **ja** |
| 10 | IntelliSense | Liste öffnet, Pfeile navigieren, Eingabe übernimmt | **ja** |
| 11 | Schweben über einem Bezeichner | Hinweisfenster erscheint | **ja**, Inhalt noch Platzhalter |
| 12–13 | `Esc` | erst die Liste, dann der Bildschirm | **ja**, nach Befund 2 |
| 14 | Umschalt+Pfeil | erweitert die Auswahl | **ja** |
| 15 | Pos1 / Ende | springen an Zeilenanfang und -ende | **ja** |
| 16 | Bild auf / Bild ab | blättern seitenweise | **ja** |
| 17 | Scrollrichtung | hoch scrollt hoch | **ja** |
| 18 | Doppelklick auf ein Wort | wählt das Wort | **ja** |
| 19 | Ziehen über Text | Auswahl entsteht | **ja** |
| 20 | Rechtsklick | greift oder tut nichts, aber nichts Kaputtes | **ja** |
| 21 | **Mauszeiger über der Dateiliste** | Hand | **ja**, nach Befund 3 |

---

## Die drei Befunde

### 1. Die Eingabetaste erzeugt keinen Zeilenumbruch

```text
Eingabe:     Eingabetaste im Editor
Erwartet:    neue Zeile
Tatsächlich: nichts
```

**Ursache.** Chromium braucht für die Eingabetaste ein Zeichenereignis mit
Wagenrücklauf, nicht nur den Tastendruck. Upstreams eigener Quelltext sagt das
im Linux-Zweig ausdrücklich:

> We need to treat the enter key as a key press of character `\r`. This is
> apparently just how webkit handles it and what it expects.

Unter Windows liefert das Betriebssystem nach `WM_KEYDOWN` genau dafür ein
`WM_CHAR`. Unser Weg liefert es nicht: `KEY_TYPED` entsteht ausschließlich aus
Minecrafts `charTyped`, und das wird für die Eingabetaste nicht gerufen — sie
ist kein druckbares Zeichen. Der Tastendruck kommt an (im Prüfstand als
`keyCode 13` belegt), das Zeichen fehlt.

**Warum die Automatik es nicht fand:** A4b prüfte für die Eingabetaste den
`keyCode` und nicht, ob im Textfeld eine Zeile dazukam.

**Kleinster Fix.** In `FnBrowser.sendKey`: Beim Druck auf Eingabe oder
Ziffernblock-Eingabe zusätzlich ein `KEY_TYPED` mit Wagenrücklauf schicken.
Drei Zeilen, an der Stelle, die ohnehin weiß, welche Taste es ist. Danach
gehört ein Fall in den Prüfstand, der den *Text* prüft und nicht das Ereignis.

### 2. Escape schließt den Bildschirm nicht

```text
Eingabe:     Escape, auch mehrfach
Erwartet:    erst die Vorschlagsliste, dann der Bildschirm
Tatsächlich: die Seite bekommt jedes Escape, der Bildschirm bleibt offen
```

**Die Ursache steht im Quelltext**, `BrowserScreen.keyPressed`:

```java
if (session != null && focus.routesKeyboard()) {
    session.keyPressed(keyCode, scanCode, modifiers);
    return true;                       // hier endet jedes Escape
}
if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
    onClose();                         // nur erreichbar ohne Browserfokus
}
```

Solange der Browser den Fokus hat, kommt der Zweig darunter nie dran. Das
zweite Escape war nie gebaut — die Zeile stand als Erwartung in der Testmatrix
und ist beim Umbau nicht umgesetzt worden.

**Kleinster Fix.** Die zuletzt gedrückte Taste merken: Kommt Escape zweimal
hintereinander ohne andere Taste dazwischen, schließt das zweite den
Bildschirm. Rund zehn Zeilen, und Java muss nicht wissen, was Monaco gerade
offen hat.

### 3. Falscher Mauszeiger über der Dateiliste

```text
Eingabe:     Mauszeiger über die Dateien in der Seitenleiste
Erwartet:    Hand — die Regel dort ist cursor:pointer
Tatsächlich: Größenänderungszeiger nach Südwest
```

**Die Ursache liegt in unserem Code, nicht in der Seite.** Upstream java-cef
meldet keinen CEF-Zeiger. Sein nativer Teil übersetzt vorher:

```cpp
// display_handler.cpp, GetCursorId — upstream java-cef
case CT_HAND:   return JNI_STATIC(HAND_CURSOR);   // aus 2 wird 12
case CT_IBEAM:  return JNI_STATIC(TEXT_CURSOR);   // aus 3 wird 2
default:        return JNI_STATIC(DEFAULT_CURSOR);
```

Unsere Tabelle liest jedoch die Ordnungszahl von `cef_cursor_type_t` — sie
stammt aus dem CinemaMod-Fork, und der reichte sie roh durch. Auf diesem Weg
liest sie damit den falschen Wertevorrat.

Die Zahlen liegen so ungünstig, dass nichts auffällt außer dem Zeiger selbst:

| gemeldet | wir lasen | gemeint war | Stelle in der Seite |
|---|---|---|---|
| 0 | POINTER | DEFAULT — Pfeil | `.ordner`, ohne eigene Regel |
| 2 | HAND | TEXT — Schreibmarke | über Text |
| 12 | SOUTH_WEST_RESIZE | HAND | `.datei { cursor:pointer }` |

**Was der Verdacht auf die Seite widerlegt hat:** Im Protokoll dieses Laufs
steht die 12 **3,8 Sekunden am Stück** — das ist ein Verweilen über einer
Datei, kein Streifen einer Kante. Eine 3 kommt nirgends vor, obwohl über
Monacos Text eine Schreibmarke fällig wäre. Und unser Seitenstil setzt an
keiner Stelle einen Größenänderungszeiger.

**Behoben** durch `AwtCursors` auf dem Weg der eigenen Laufzeitumgebung: Die
AWT-Kennung wird auf die Ordnungszahl von Chromium zurückgerechnet, bevor sie
weitergereicht wird. Alles dahinter bleibt für beide Wege gleich, und die
gemeinsame Tabelle wird nicht angefasst — auf dem MCEF-Weg stimmt sie ja.

**Nachgeprüft im Spiel** am selben Tag, und das Protokoll zeigt genau die drei
erwarteten Zeiger:

```text
IBEAM (3)    über dem Text im Editor
HAND (2)     über den Dateien in der Seitenleiste
POINTER (0)  über den Ordnern
```

**Die 3 ist der eigentliche Beleg.** Sie kam vor dem Fix in keinem einzigen
Lauf vor, obwohl über Monacos Text eine Schreibmarke fällig war — und ein
Größenänderungszeiger kommt jetzt nirgends mehr.

**Was dabei verlorengeht.** Der `switch` im nativen Teil kennt dreizehn Fälle
und macht aus allem übrigen `DEFAULT_CURSOR`. Verbotsschild, Greifhand und
Lupe kommen deshalb als Pfeil an. Für Greifhand und Lupe hat GLFW ohnehin
keinen Zeiger; das Verbotsschild wäre nur mit einem weiteren Patch am nativen
Teil zurückzuholen.

**Und der zweite Fund bleibt bestehen:** Unsere Zeigertabelle stammt aus der
CEF-116-Zeit und stimmt mit CEF 146 nur bis Kennung 42 überein — nachgesehen
in `cef_types.h` der Distribution. CEF 146 hat `CT_MIDDLE_PANNING_VERTICAL`
und `CT_MIDDLE_PANNING_HORIZONTAL` **vor** `CT_CUSTOM` eingefügt; ab 43 zeigt
sie auf die falschen Namen. Für den Fall hier ohne Bedeutung, weil der native
Teil so hohe Werte nie durchlässt.

---

## Ein Wunsch, kein Fehler

Registerkarten oben mit der mittleren Maustaste schließen. Das gehört in die
Oberfläche des Editors, nicht in die Laufzeitumgebung, und ist kein Teil
dieser Prüfung.

---

## Bevor MCEF entfernt wird

Alle drei Befunde sind behoben und **von Hand nachgeprüft**: Die Eingabetaste
macht eine Zeile, das zweite Escape schließt den Bildschirm, und über den
Dateien steht die Hand.

Die geplante Vergleichsmessung auf dem MCEF-Weg entfällt — die Ursache stand
im Quelltext von java-cef, und sie lag auf unserer Seite.

**Was diese Liste geleistet hat und die Automatik nicht konnte:** Alle drei
Befunde lagen in unserem Code, alle drei waren an einer Maschine unsichtbar.
Die Testmatrix prüfte für die Eingabetaste den Tastencode statt den Text; das
zweite Escape stand als Erwartung da und war nie gebaut; und ein Mauszeiger
hat kein Ereignis, das sich abfragen ließe. Vier Minuten an einer echten
Tastatur haben gefunden, was vierzig grüne Prüfungen nicht sahen.
