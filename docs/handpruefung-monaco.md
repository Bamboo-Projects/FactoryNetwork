# Handprüfung Monaco

**Status: gefahren am 1. September 2026 auf der eigenen Laufzeitumgebung.
Ergebnis: drei Fehlschläge.**

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
| 1a | **Eingabetaste** | erzeugt einen Zeilenumbruch | **NEIN** — Befund 1 |
| 2 | Deutsches Layout: `ä ö ü ß` | erscheinen korrekt | **ja** |
| 3–5 | AltGr: `@ € \ ~ \|` | erscheinen, kein Kürzel geht auf | **ja** |
| 6 | Strg+C und Strg+V | Zwischenablage in beide Richtungen | **ja** |
| 7 | Strg+F | Suchfeld öffnet, Eingabe landet darin | **ja** |
| 8 | Strg+S | erreicht Monaco, nicht Minecraft | **ja** |
| 9 | Mehrfachcursor (`Alt+Klick`) | zweiter Cursor entsteht | **ja** |
| 10 | IntelliSense | Liste öffnet, Pfeile navigieren, Eingabe übernimmt | **ja** |
| 11 | Schweben über einem Bezeichner | Hinweisfenster erscheint | **ja**, Inhalt noch Platzhalter |
| 12–13 | `Esc` | erst die Liste, dann der Bildschirm | **NEIN** — Befund 2 |
| 14 | Umschalt+Pfeil | erweitert die Auswahl | **ja** |
| 15 | Pos1 / Ende | springen an Zeilenanfang und -ende | **ja** |
| 16 | Bild auf / Bild ab | blättern seitenweise | **ja** |
| 17 | Scrollrichtung | hoch scrollt hoch | **ja** |
| 18 | Doppelklick auf ein Wort | wählt das Wort | **ja** |
| 19 | Ziehen über Text | Auswahl entsteht | **ja** |
| 20 | Rechtsklick | greift oder tut nichts, aber nichts Kaputtes | **ja** |
| 21 | **Mauszeiger über der Navigation** | Pfeil oder Hand | **NEIN** — Befund 3 |

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

### 3. Falscher Mauszeiger über der Navigation

```text
Eingabe:     Mauszeiger über die Dateiliste bewegen
Erwartet:    Pfeil oder Hand
Tatsächlich: ein Größenänderungs-Zeiger
```

**Was gemessen ist:** Chromium hat `SOUTH_WEST_RESIZE (12)` angefordert.

**Die Übersetzung ist damit nicht die Ursache.** Kennung 12 ist in CEF 146
tatsächlich `CT_SOUTHWESTRESIZE` — nachgesehen in `cef_types.h` der
Distribution —, und unsere Tabelle bildet sie auf den richtigen GLFW-Zeiger ab.

Offen bleibt, ob die Seite ihn dort selbst anfordert oder ob das Zurücksetzen
ausbleibt. Unser eigener Seitenstil setzt keinen Größenänderungs-Zeiger;
Monacos mitgelieferter tut es an mehreren Stellen.

**Kleinster nächster Schritt — eine Messung, kein Fix:** dieselbe Stelle auf
dem MCEF-Weg anfahren (`./gradlew runClient -Pide`). Zeigt sich dort derselbe
Zeiger, gehört der Fehler in die Seite und nicht in die Laufzeitumgebung.
Dazu hilft eine Protokollzeile je Zeigerwechsel statt nur je neuer Art — heute
wird jede Art genau einmal geschrieben, und damit ist ein ausbleibendes
Zurücksetzen unsichtbar.

**Unabhängig davon ein echter Fund:** Unsere Zeigertabelle stammt aus der
CEF-116-Zeit und stimmt mit CEF 146 nur bis Kennung 42 überein. CEF 146 hat
`CT_MIDDLE_PANNING_VERTICAL` und `CT_MIDDLE_PANNING_HORIZONTAL` **vor**
`CT_CUSTOM` eingefügt; ab 43 zeigt unsere Tabelle auf die falschen Namen. Für
den Fall hier ohne Bedeutung, für die Vollständigkeit nicht.

---

## Ein Wunsch, kein Fehler

Registerkarten oben mit der mittleren Maustaste schließen. Das gehört in die
Oberfläche des Editors, nicht in die Laufzeitumgebung, und ist kein Teil
dieser Prüfung.

---

## Bevor MCEF entfernt wird

Die drei Befunde gehören behoben und nachgeprüft. Befund 1 und 2 liegen in
unserem Code und sind klein; Befund 3 braucht zuerst die eine Vergleichsmessung
auf dem MCEF-Weg, die sagt, wo er überhaupt hingehört.
