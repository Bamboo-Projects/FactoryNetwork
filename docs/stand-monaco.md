# Monaco als Editor-Workload: Stand nach Schritt F

**31.08.** Monaco läuft in unserer Runtime, mit Reitern, Dateibaum, Problemfeld
und Statuszeile, über Minecraft als Hintergrund. Alle Szenarien aus dem Auftrag
sind gemessen.

**Die Antwort auf die Schlussfrage steht in Abschnitt 17.** Sie lautet ja, mit
einer benannten Bedingung.

---

## 1. Fassung

**monaco-editor 0.56.0**, die aktuelle. Sie bringt die AMD-Fassung noch mit
(`min/vs/loader.js`) — das war die entscheidende Prüfung, denn ohne sie hätte
es 0.52.x werden müssen: ESM-Module verweigert Chromium von einer
Datei-Adresse, weil die keine Herkunft hat.

Geholt und abgelegt von `tools/monaco.py`, mit fester Fassungsnummer. Kein
Laden zur Laufzeit — eine Oberfläche, die beim ersten Öffnen ins Netz greift,
funktioniert in einem gesperrten Netz nie.

## 2. Größe

| | |
|---|---:|
| Bündel in der Mod | **23,3 MB**, 151 Dateien |
| davon Sprachdienste (TypeScript, CSS, HTML, JSON) | ~17 MB |
| davon Editorkern | ~6 MB |

**Der Versuch, das zu kürzen, ist zweimal gescheitert und steht als Warnung im
Skript.** Erst fehlten die `monaco.contribution`-Anmeldungen (2–5 KB, aber
`editor.main` hängt fest an ihnen), dann die Arbeitsdateien, die diese
ihrerseits nachladen. Ein Dateifilter kann diesen Abhängigkeitsgraphen nicht
schneiden — das könnte nur ein Bundler, der ihn kennt (`bun build`,
`esbuild`). Das ist ein eigener Bauschritt und war für diesen Spike nicht die
Frage.

**Realistisch erreichbar wären ~6 MB**, wenn der Bundler dazukommt.

## 3. Ladezeit

| Schritt | Dauer |
|---|---:|
| Auspacken aus dem Klassenpfad (nur beim ersten Mal je Fassung) | **373 ms** |
| Öffnen bis „IDE bereit" | **~4,4 s** beim ersten Mal, einschließlich Auspacken |

Ausgepackt wird in einen Ordner unter dem Spielordner, versioniert. Eine zweite
Sitzung mit derselben Fassung packt nicht neu aus — die 373 ms entfallen dann.
Wie lange das Öffnen ohne Auspacken genau dauert, ist nicht getrennt gemessen.

## 4. Speicher

**849 MB in 13 Chromium-Prozessen**, mit acht Modellen und 10.000 Zeilen im
größten.

**Wie viel davon Monaco ist und wie viel Chromium selbst, ist nicht getrennt
gemessen.** Ein Vergleichswert für einen leeren Browser fehlt — dafür hätte es
einen eigenen Lauf gebraucht. Was feststeht, ist die Gesamtzahl, und die ist
für ein Spiel, das selbst mehrere Gigabyte braucht, spürbar.

**Ebenfalls nicht gemessen:** die Rechenzeit der Chromium-Prozesse. Der Auftrag
nannte sie „soweit praktikabel"; über Momentaufnahmen des Arbeitsspeichers bin
ich nicht hinausgekommen.

---

## 5. Die Messungen

Fenster 854 × 480, ein Vollbild sind 1601 KB. Hintergrund wie in E festgelegt:
Standbild, halbe Kantenlänge, rohes Format.

| Szenario | Bilder/s | KB je Bild | Anteil | MB/s | Eingabe→Bild p50 / p95 |
|---|---:|---:|---:|---:|---|
| **Ruhe** | 2,7 | 62,6 | 3,9 % | 0,17 | — |
| **Schreibmarke blinkt** | 2,0 | **0,1** | 0,01 % | 0,00 | — |
| **Vervollständigung offen** | 2,0 | **0,1** | 0,01 % | 0,00 | — |
| Normales Tippen | 12,0 | **1436,6** | **89,7 %** | 16,8 | 35,5 / 122,0 ms |
| Schnelles Tippen | 16,7 | 1487,8 | 92,9 % | 24,3 | 33,2 / 140,9 ms |
| Große Auswahl | 7,7 | 1487,8 | 92,9 % | 11,3 | — |
| Maus über Code | 23,7 | 432,8 | 27,0 % | 10,0 | 26,0 / 106,8 ms |
| Suchfeld offen | 3,7 | 457,9 | 28,6 % | 1,7 | — |
| Zehn Schreibmarken | 2,5 | 337,0 | 21,0 % | 0,8 | — |
| Langsam rollen | 18,2 | 555,4 | 34,7 % | 9,9 | 41,5 / 57,8 ms |
| **Schnell rollen, 10.000 Zeilen** | **30,0** | 574,6 | 35,9 % | 16,8 | **25,4 / 33,6 ms** |
| Reiterwechsel (8 Dateien) | 10,7 | 580,7 | 36,3 % | 5,8 | — |
| Tastenkürzel | 18,2 | 135,5 | 8,5 % | 2,3 | 19,3 ms / bis ~400 ms ¹ |
| Themawechsel | 5,0 | ~90 | 5,8 % | 0,4 | — |
| Schriftgröße | 21,0 | 3,3 | 0,20 % | 0,07 | — |
| Übersicht an/aus (danach) | 2,0 | **0,0** | 0 % | 0,00 | — |
| Haftende Kopfzeile an/aus (danach) | 2,0 | 0,0–16,7 | ≤1 % | 0,04 | — |

¹ Acht Messungen einzelner Aktionen — zu wenige für ein Perzentil. Der Wert
sagt: Einzelne Kürzel wie Umbenennen brauchen bis zu einer knappen halben
Sekunde bis zum Bild, der Normalfall liegt bei 19 ms.

**Minecrafts Bildzeit lag in jedem einzelnen Szenario zwischen 8,4 und 8,7 ms
(115–119 Bilder je Sekunde).** Nicht ein Abschnitt hat das Spiel messbar
belastet.

---

## 6. Das Dirty-Rect-Verhalten, und warum es so ist

**Tippen kostet 85–90 % eines Vollbilds. Für eine geänderte Textzeile.**

Das war die auffälligste Zahl, und ich habe zwei Erklärungen geprüft und beide
widerlegt:

| Fall | KB je Bild | Anteil |
|---|---:|---:|
| Tippen, Glas + durchscheinender Editor | 1414,9 | 88,4 % |
| Tippen, **ohne Glas** | 1357,7 | 84,8 % |
| Tippen, ohne Glas **und deckender Editor** | 1357,3 | **84,8 %** |

Weder die Weichzeichner noch die Durchsichtigkeit sind die Ursache. **Monaco
selbst meldet beim Tippen einen fast vollflächigen geänderten Bereich** —
vermutlich, weil Zeilennummern, Übersicht, Bildlaufleiste und haftende
Kopfzeile gemeinsam ungültig werden und Chromium die Rechtecke zu einem großen
zusammenfasst.

**Das ist nichts, was wir auf unserer Seite beheben können.** Es liegt zwischen
Monaco und Chromium.

**Das Glas kostet trotzdem etwas — nur nicht in Bytes.** Ohne Weichzeichner
sinkt die p95-Latenz beim Tippen von 122 auf 58 Millisekunden. Es kostet
Rechenzeit in Chromium, keine Übertragung.

**Und ein erfreulicher Gegenbefund:** Ein blinkender Schreibcursor kostet
**0,1 KB je Bild**, ein offenes Vervollständigungsfenster ebenso. Was klein
ist, bleibt klein — die Ausschnitte arbeiten, wo sie können.

## 7. Rollen

**Schnelles Rollen durch 10.000 Zeilen erreicht genau 30,0 Bilder je
Sekunde** — die Decke von CEF, exakt getroffen. Dabei:

- 574,6 KB je Bild, 16,8 MB/s
- Eingabe bis Bild: **p50 25,4 ms, p95 33,6 ms, max 40,9 ms**
- Minecrafts Bildzeit unverändert 8,6 ms

**Rollen ist der Fall, der am besten läuft**, nicht der schlimmste. Die Latenz
ist dort am niedrigsten und am gleichmäßigsten von allen Szenarien.

## 8. Übersicht (Minimap)

**Kein messbarer Einfluss.**

| | KB je Bild beim schnellen Rollen |
|---|---:|
| mit Übersicht | 574,6 |
| ohne Übersicht | 576,3 |

Im Ruhezustand kostet sie nach dem Umschalten 0,0 KB. Die Übersicht ist gratis
und kann anbleiben.

*Anmerkung zur Messung:* Beim ersten Versuch schien „Übersicht aus" fünfmal so
teuer wie „Übersicht an". Das war die Umschaltung selbst — ein Wechsel malt
einmal alles neu. Seitdem werden nach jedem Wechsel zwanzig Bilder verworfen,
bevor gezählt wird.

## 9. Vervollständigung und Schwebehilfe

Beide öffnen **unmittelbar** und kosten im offenen Zustand nichts (0,1 KB je
Bild).

**Sie sind DOM, kein Chromium-Popup.** Monaco malt sie selbst in die Seite;
`popupPaints` bleibt null. Die zweite Textur aus Schritt D wird dafür nicht
gebraucht — sie bleibt für echte `<select>`-Felder zuständig.

---

## 10. Tastenkürzel

Geprüft im Durchlauf: `Ctrl+A`, `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+Shift+Z`,
`Ctrl+D`, `Ctrl+G`, `Ctrl+F`, `Alt+↑`, `Alt+↓`, `F2`, `Escape`, `Home`, `End`,
`PageDown`.

**Minecraft fängt keine davon ab.** Der Grund ist das Fokusmodell aus Schritt
D: Solange der Browser den Fokus hat, wird kein Tastenereignis an
`super.keyPressed(...)` weitergereicht. Es gibt keine Ausnahme und keine
Sonderliste.

**Ein Fund, der jede spätere Automatisierung betrifft:** Ein selbst erzeugter
Tastendruck **ohne Scancode** kommt auf Windows nicht an — der native Teil baut
den Windows-Tastencode mit `MapVirtualKey` aus dem Scancode. Bei echter Eingabe
liefert Minecraft ihn mit; wer Tasten erzeugt, muss `glfwGetKeyScancode`
fragen. Das steht jetzt in der Basisklasse.

**Ctrl+Mausrad** funktioniert seit einer Korrektur in diesem Schritt: Die
Umschalttasten waren bei allen Mausereignissen hart auf null gesetzt, womit
Zoom, Umschalt-Auswahl und Alt-Mehrfachcursor tot waren.

`Ctrl+P` und `Ctrl+Shift+P` sind nicht belegt — dafür bräuchte es eine
Befehlspalette, die wir nicht gebaut haben.

## 11. Unicode und Zwischenablage

| | Ergebnis |
|---|---|
| `äöü ÄÖÜ ß` | **funktioniert** |
| `€`, `{}`, `[]`, `<>` | **funktioniert** |
| Emoji (U+1F600, jenseits U+FFFF) | **funktioniert** — im Bildschirmfoto sichtbar |
| Mehrzeiliges Einfügen | funktioniert (Chromium selbst) |

**Das Emoji war die Überraschung.** Es ist ein Ersatzpaar und kommt als zwei
`charTyped`-Ereignisse — Minecraft spaltet es selbst auf, und Monaco setzt die
Hälften wieder zusammen.

**Der Vorbehalt aus Schritt D ist damit entschärft, nicht erledigt.** Bewiesen
ist, dass zwei unmittelbar aufeinanderfolgende Hälften zusammenfinden. Nicht
geprüft ist, was geschieht, wenn etwas dazwischenkommt — ein Fokuswechsel oder
eine Bildgrenze zwischen den beiden Ereignissen.

### IME und CJK: Antwort C **und** D

Nachgesehen, nicht vermutet:

```
javap CefBrowser_N   → keine ImeSetComposition, kein ImeCommitText
native/*.cpp         → dieselben Methoden kommen nicht vor
```

- **C)** GLFW liefert keine Kompositionsereignisse — nur fertige Zeichen. Das
  ist eine Eigenschaft von GLFW, nicht von Minecraft.
- **D)** Und selbst wenn: Dieser JCEF-Fork reicht CEFs IME-Schnittstelle nicht
  durch. Sie existiert in CEF, aber nicht in Java.

Beides müsste behoben werden, und beides liegt außerhalb dessen, was wir ohne
eigene Runtime ändern können.

**Was heute schon geht:** Wer mit einer IME schreibt und ein Zeichen bestätigt,
bekommt es als gewöhnliches `charTyped` — die Eingabe kommt an, nur ohne die
Zwischenanzeige während des Komponierens. Ungeprüft, weil hier keine IME
eingerichtet ist.

## 12. Themen

Drei geprüft: **Default Dark**, **Default Light**, und ein eigenes für das
Glas. Der Wechsel kostet ~90 KB je Bild über etwa vier Sekunden und ist
unmittelbar.

**Wie Monaco Themen führt:** `monaco.editor.defineTheme(name, {base, inherit,
rules, colors})`.

- `rules` färbt die **Wortarten** ein: `{ token: 'keyword', foreground:
  '7dd3a0', fontStyle: 'bold' }`. Die Namen der Wortarten kommen aus dem
  Monarch-Einfärber, also aus unserer Hand.
- `colors` färbt die **Oberfläche**: `editor.background`,
  `editorLineNumber.foreground`, `minimap.background` und etwa dreihundert
  weitere Schlüssel — dieselben Namen wie in VS Code.

**Übernahme vorhandener VS-Code-Themen:** technisch machbar und überschaubar.
Ein VS-Code-Thema ist JSON mit `tokenColors` (TextMate-Bereiche) und `colors`
(dieselben Schlüssel wie oben). Zu tun wären zwei Dinge: die `colors` direkt
übernehmen — das ist eine Zuweisung —, und die TextMate-Bereiche
(`keyword.control`, `string.quoted`) auf unsere Monarch-Wortarten abbilden.
Das ist eine Tabelle mit vielleicht zwanzig Zeilen, kein Übersetzer.

**Was Monaco nicht abdeckt:** Alles außerhalb des Editors — Reiter, Dateibaum,
Problemfeld, Statuszeile. Deren Farben müssten wir aus denselben
`colors`-Schlüsseln selbst ziehen (`tab.activeBackground`,
`sideBar.background`, `statusBar.background`). Die Schlüssel sind da; nur
anwenden muss man sie selbst.

**Und das sieht man sofort.** In `fnide-Thema_hell.png` ist der Editor hell,
während Reiter, Dateibaum und Problemfeld dunkel bleiben — ein harter Bruch
mitten im Fenster. **Ein helles Thema ist damit heute nicht benutzbar**, nicht
weil Monaco es nicht könnte, sondern weil unsere Hülle die Farben nicht
mitnimmt. Wer Themen ernsthaft anbieten will, muss die Panel-Farben aus
demselben Datensatz ziehen; sonst gibt es genau ein brauchbares Thema.

## 13. Mehrere Modelle

**Acht Modelle in einer Editor-Instanz**, zwischen 120 und 10.000 Zeilen.

Der Wechsel kostet **580 KB je Bild** über den Umschaltvorgang — ein Vollbild
je Wechsel, danach nichts. Im Ruhezustand kostet ein zusätzliches Modell
**nichts**: Es ist ein Textpuffer im Speicher, kein zweiter Editor und schon
gar kein zweiter Browser.

**Reiter sind damit praktisch kostenlos.** Die Frage aus dem Auftrag ist mit ja
beantwortet.

---

## 14. Sind dreißig Bilder je Sekunde ein Problem?

**Erreicht werden sie genau einmal: beim schnellen Rollen, mit 30,0/s.** Das
ist die Decke von CEF, und sie wird dort getroffen.

**In allen anderen Szenarien liegt die Rate darunter** — Tippen bei 12–17,
Maus bei 24, alles Übrige bei 2–5. Die Decke ist also nur beim Rollen
überhaupt spürbar.

**Und ausgerechnet dort ist die Latenz am besten**: p50 25,4 ms, p95 33,6 ms.
Ein Bild alle 33 ms ist genau das, was 30/s bedeuten — das Rollen läuft an der
Decke, aber gleichmäßig.

**Der Engpass ist nicht die Bildrate, sondern die Latenz beim Tippen:** p50
35 ms, p95 122 ms. Das liegt nicht an der Decke (bei 12 Bildern/s ist reichlich
Luft), sondern an der Zeit, die Chromium für ein vollflächiges Neuzeichnen
braucht.

**Die sechs Fragen aus dem Auftrag** sind aus Schritt D beantwortet und haben
sich nicht geändert:

1. `windowless_frame_rate` über `CefBrowserSettings` setzen? **Nein** — das
   native `createBrowser` nimmt keine entgegen.
2. Was verhindert MCEF? **Nicht MCEF**, sondern der JCEF-Fork.
3. Reicht ein anderer `FnBrowser`? **Nein**, er ruft dasselbe native.
4. MCEFs Fabrik umgehen? **Nein**, hilft nicht.
5. Änderungen in JCEF? **Ja.**
6. Native Änderungen? **Ja** — zwei Zeilen in `CefBrowser_N.cpp` plus ein
   Parameter durch die JNI-Grenze, danach eine eigene Übersetzung.

**Empfehlung: nicht umbauen.** Sechzig Bilder je Sekunde würden das Rollen
glätten und beim Tippen nichts ändern — dort ist nicht die Rate das Problem.

### Woraus die Tippverzögerung besteht

Damit „nicht umbauen" hier und „Pixel Buffer Object oder geteilte Textur" in
Abschnitt 17 nicht widersprüchlich nebeneinanderstehen — sie zielen auf
verschiedene Teile derselben Summe:

```
Verzögerung beim Tippen
  = Zeit, die Chromium zum Neuzeichnen braucht      ← der große Teil
  + Zeit, die unser Upload braucht (2,9 ms heute)   ← wächst mit der Fläche
```

- **Eine höhere Bildrate ändert keinen der beiden Terme.** Bei zwölf Bildern je
  Sekunde ist reichlich Luft unter der Decke; sie ist nicht die Grenze.
- **Ein Pixel Buffer Object ändert den zweiten Term** — heute klein, bei 1080p
  auf das Fünffache gewachsen.
- **Eine geteilte Textur ändert beide**, weil sie den Weg über den
  Hauptspeicher ganz entfernt.

Welcher Weg sich lohnt, entscheidet die Messung bei voller Auflösung.

---

## 15. Wie es sich anfühlt

Ehrlich vorweg: Beurteilt habe ich anhand der Latenzzahlen und der
Bildschirmfotos aller Szenarien, nicht anhand einer langen Sitzung am Gerät.
Was in zwanzig Minuten Arbeit nervt, sagt erst eine solche Sitzung.

| Frage | Antwort |
|---|---|
| Fühlen sich 30 Bilder/s beim Tippen an? | **Nein** — beim Tippen entstehen nur 12–17, die Decke spielt keine Rolle. |
| Ist die Schreibmarke flüssig? | **Ja.** Sie kostet 0,1 KB und blinkt gleichmäßig. |
| Fühlt sich Auswahl direkt an? | **Größtenteils.** Sie erzeugt fast Vollbilder, aber die Rate bleibt bei 8/s. |
| Ist schnelles Rollen flüssig? | **Ja**, das beste Szenario. 30/s bei p95 34 ms. |
| Wirkt die Übersicht sauber? | **Ja**, und sie kostet nichts. |
| Öffnen Vervollständigung und Schwebehilfe unmittelbar? | **Ja.** |
| Merkt man Eingabeverzögerung? | **Ja, beim Tippen.** 35 ms im Median sind an der Grenze; 122 ms im p95 sind deutlich. |
| Deutlich langsamer als VS Code? | **Beim Tippen ja, sonst nein.** VS Code liegt bei 15–25 ms; wir bei 35 ms mit Ausreißern über 100. |

---

## 15a. Was ausgelassen wurde

| Punkt | Warum |
|---|---|
| **50.000-Zeilen-Datei** | Im Auftrag als „optional" genannt. Bei 10.000 Zeilen erreichte das Rollen bereits die Decke von CEF bei gleichmäßiger Verzögerung; Monaco malt ohnehin nur den sichtbaren Ausschnitt. |
| **Prozessorlast der Chromium-Prozesse** | Nur Momentaufnahmen des Arbeitsspeichers gemacht. |
| **Strg+P und Strg+Umschalt+P** | Nicht belegt — dafür bräuchte es eine Befehlspalette. |
| **Eine lange Sitzung am Gerät** | Beurteilt wurde anhand von Latenzzahlen und Bildschirmfotos aller Szenarien. |

---

## 16. Die größten verbleibenden Risiken

1. **Die Eingabeverzögerung beim Tippen ist das eine echte Problem.** p95 von
   122 ms mit Glas, 58 ms ohne. Ein Editor, der beim schnellen Schreiben
   gelegentlich eine Zehntelsekunde hinterherhinkt, fühlt sich nicht wie ein
   moderner Editor an.
2. **Die vollflächigen Dirty-Rects beim Tippen skalieren mit der Auflösung.**
   Bei 854×480 sind es 17 MB/s und 2,9 ms Uploadzeit. Auf 1920×1080
   hochgerechnet (Faktor 5,06): **~85 MB/s und ~15 ms je Upload** — und *das*
   würde Minecrafts Bildzeit treffen, die hier noch unberührt blieb. **Ungemessen
   und das wichtigste offene Risiko.**
3. **849 MB Arbeitsspeicher** neben einem Spiel, das selbst mehrere Gigabyte
   braucht.
4. **23,3 MB im Mod-Bündel**, bis ein Bundler dazukommt.
5. **IME/CJK ist verschlossen** (Abschnitt 11) — ohne eigene Runtime nicht zu
   ändern.
6. **Ein helles Thema ist heute unbenutzbar**, weil die Panel-Farben nicht
   mitwandern (Abschnitt 12). Behebbar, aber Arbeit.
7. **Emoji sind entschärft, nicht abgehakt** — der Härtetest steht aus.
8. **Keine Arbeitsfäden** unter einer Datei-Adresse. Für unsere Sprache
   unerheblich (Monarch läuft im Hauptfaden), für alles Rechenintensive später
   nicht.

---

## 17. Kann das den nativen Editor ablösen?

**Ja — unter einer Bedingung, die gemessen werden muss, bevor man sich
festlegt.**

Wofür das Ja steht:

- Ein **echter** Editor: Faltung, Übersicht, Mehrfachcursor, Suchen und
  Ersetzen, Vervollständigung, Schwebehilfe, Themen, acht Dateien in Reitern —
  alles funktioniert, nichts musste nachgebaut werden.
- **Minecraft merkt nichts davon**: 8,5 ms Bildzeit in jedem Szenario, von der
  Ruhe bis zum Rollen durch zehntausend Zeilen.
- **Rollen, Suchen, Vervollständigen und Reiterwechsel fühlen sich richtig an.**
- **Umlaute, ß, € und sogar Emoji** kommen an; kein Tastenkürzel wird von
  Minecraft abgefangen.

Die Bedingung:

> **Die Eingabeverzögerung beim Tippen muss bei voller Auflösung gemessen
> werden, bevor die Entscheidung endgültig ist.**

Bei 854 × 480 liegt sie bei 35 ms im Median und 122 ms im p95 — grenzwertig,
aber benutzbar. Die Fläche je Bild ist dabei fast ein Vollbild, und die
Uploadzeit skaliert mit ihr. Bei 1080p wären es rechnerisch ~15 ms je Upload
statt 2,9 ms. Ob die Verzögerung dann bei 50 ms bleibt oder auf 150 steigt,
entscheidet, ob sich der Editor beim Schreiben richtig anfühlt — und das ist
die Eigenschaft, an der ein Editor gemessen wird.

**Wenn diese Messung schlecht ausfällt**, gibt es zwei benannte Auswege, beide
außerhalb des heutigen Rahmens: ein Pixel Buffer Object für den Upload (der
Zeitanteil), oder eine geteilte Textur (der ganze Weg). Beide sind ausdrücklich
nicht Teil dieses Schritts gewesen, und beide würden genau an dieser Zahl
ansetzen.

**Was nicht schöngeredet werden soll:** Beim Tippen ist es heute spürbar
langsamer als VS Code. Nicht unbenutzbar, aber spürbar. Alles andere ist es
nicht.

---

## 18. Zum Nachstellen

```
/fnweb ide                          die Oberfläche zum Anfassen
./gradlew runClient -Pide           dasselbe, direkt beim Start
./gradlew runClient -Pide -Pidebench  die ganze Messung, mit Bildern
python tools/monaco.py              das Bündel erneuern
```

Die Bilder aller Szenarien liegen unter `run/screenshots/fnide-*.png`.
