# Handprüfung Monaco

**Status: erstellt, nicht gefahren.**

Diese Liste kann kein Programm abhaken. Alles darin hängt an einem Menschen an
einer echten Tastatur — an einem deutschen Layout, an dem Gefühl, ob Scrollen
richtig herum läuft, und daran, ob ein Zeichen erscheint oder ein Menü
aufgeht.

Die maschinelle Testmatrix ist grün (A4b: 40 von 40; im Spiel laufen alle
Stufen von `-Pidebench` durch). **Das ersetzt diese Liste nicht.** Was eine
Maschine prüfen kann, ist, ob ein Ereignis ankommt; ob das Tippen sich richtig
anfühlt, kann sie nicht.

---

## So wird geprüft

```text
pwsh -File tools/runtime/build-jcef.ps1     falls die Laufzeitumgebung fehlt
./gradlew runClient -Pfnruntime -Pide       Editor öffnet sich, F6 öffnet erneut
```

Zum Vergleich derselbe Lauf ohne `-Pfnruntime` — dann läuft alles auf MCEF wie
bisher. **Wo sich beide unterscheiden, ist der interessante Fall.**

---

## Die Liste

Jede Zeile mit `ja`, `nein` oder einer Bemerkung ausfüllen. Ein `nein` ist
kein Beinbruch, sondern das Ergebnis — es gehört in den Bericht, nicht
weggeredet.

| Nr. | Prüfung | Erwartung | eigene Laufzeit | MCEF |
|---|---|---|---|---|
| 1 | Fließtext tippen | erscheint zeichengenau, keine Dopplungen, keine Verluste | | |
| 2 | Deutsches Layout: `ä ö ü ß` | erscheinen korrekt | | |
| 3 | AltGr: `@` | erscheint, **kein** Tastenkürzel geht auf | | |
| 4 | AltGr: `€` | erscheint | | |
| 5 | AltGr: `\` `~` `\|` | erscheinen | | |
| 6 | Strg+C und Strg+V | Zwischenablage arbeitet in beide Richtungen | | |
| 7 | Strg+F | Suchfeld öffnet, Eingabe landet darin | | |
| 8 | Strg+S | erreicht **Monaco**, nicht Minecraft | | |
| 9 | Mehrfachcursor (`Alt+Klick`) | zweiter Cursor entsteht | | |
| 10 | IntelliSense | Vorschlagsliste öffnet, Pfeile navigieren, Eingabe übernimmt | | |
| 11 | Schweben über einem Bezeichner | Hinweisfenster erscheint | | |
| 12 | `Esc` bei offener Vorschlagsliste | schließt die Liste, **nicht** den Bildschirm | | |
| 13 | `Esc` danach | schließt den Bildschirm | | |
| 14 | Umschalt+Pfeil | erweitert die Auswahl | | |
| 15 | Pos1 / Ende | springen an Zeilenanfang und -ende | | |
| 16 | Bild auf / Bild ab | blättern seitenweise | | |
| 17 | Scrollrichtung | fühlt sich richtig an — hoch scrollt hoch | | |
| 18 | Doppelklick auf ein Wort | wählt **das Wort**, nicht die Zeile | | |
| 19 | Ziehen über Text | Auswahl entsteht und bricht nicht ab | | |
| 20 | Rechtsklick | falls ein Kontextverhalten vorgesehen ist: greift es | | |

---

## Worauf besonders zu achten ist

**Zeile 3 bis 5 sind der Kern.** Windows meldet AltGr als Strg + rechtes Alt.
Gemessen ist: Ein Zeichen mit Strg am Ereignis kommt bei Chromium gar nicht
an — deshalb entfernt `AwtModifiers.forCharacter` Strg und Alt am getippten
Zeichen. Das ist geprüft; **ungeprüft ist, ob Monaco die begleitende
Tastenmeldung als Kürzel deutet.** Wenn bei `@` etwas aufgeht statt eines
Zeichens zu erscheinen, ist das der Fall, für den die Gegenmaßnahme
weitergehen müsste: Strg und rechtes Alt auch am Tastendruck weglassen.

**Zeile 12 und 13 sind der Fokuskonflikt in Reinform.** Ein Editor braucht
Esc, ein Minecraft-Bildschirm schließt damit. Beides gleichzeitig geht nicht;
die Reihenfolge muss stimmen.

**Zeile 17 ist die, die man nicht messen kann.** Das Vorzeichen ist geprüft
(`wheelRotation +1` erzeugt `deltaY -2,0`), aber ob sich die Geschwindigkeit
richtig anfühlt, sagt nur eine Hand am Rad.

---

## Wenn etwas nicht stimmt

```text
Zeile notieren
beide Wege vergleichen — läuft es auf MCEF anders?
Protokoll mitnehmen:  ./gradlew runClient -Pfnruntime -Pide -Ptrace
```

`-Ptrace` schreibt jede Taste mit Scancode, Erweiterungs-Bit und
Modifikatoren mit. Damit lässt sich eine Beobachtung an der Tastatur mit dem
vergleichen, was der Prüfstand gemessen hat.

---

## Bevor MCEF entfernt wird

Diese Liste muss **gefahren oder bewusst als Risiko angenommen** sein. Solange
sie offen ist, steht der Nachweis für den neuen Weg auf Zahlen allein — und
Zahlen sagen nichts darüber, ob sich das Tippen richtig anfühlt.
