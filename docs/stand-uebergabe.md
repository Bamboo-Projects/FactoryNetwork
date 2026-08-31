# Übergabe — wo wir stehen und was als Nächstes kommt

Stand: 31. August 2026. Diese Datei ist der Einstiegspunkt für die nächste
Sitzung. Sie fasst zusammen, was entschieden ist, was gemessen wurde, in
welchem Zustand die Maschine ist und womit weiterzumachen ist.

---

> **Überholt seit dem 1. September 2026.** Block A ist gebaut und vermessen.
> Der aktuelle Einstieg ist **`stand-runtime-blockA.md`**; dort stehen die
> Zahlen, die vier Abweichungen vom Plan und der nächste Schritt (B3b).
> Was unten steht, gilt weiterhin für den Proof-of-Concept und seine Messungen.

## In einem Satz

Der Proof-of-Concept steht und ist vermessen; als Nächstes wird die eigene
Laufzeitumgebung gebaut, und der erste Schritt dafür ist **A1: upstream
java-cef auf CEF 146 bauen, ohne Minecraft**.

---

## Was entschieden ist

```text
CEF + Offscreen Rendering        fest
Monaco als Editor                fest
60 Hz OSR                        fest, gemessen, gepatcht
statischer Hintergrund,
  vorgeblurt statt backdrop-filter   fest
upstream java-cef + kleiner Patch-Satz   fest
CEF 146 für Version 1            fest (upstreams eigener Pin)
MCEF wird ersetzt                fest
```

Nicht in Version 1: Accelerated Paint, External Begin Frame, IME-Ereignisquelle,
CEF 151, öffentliche Schnittstelle für andere Mods, Sprachdienst-Brücke,
dynamische Bildrate.

---

## Die Zahlen, die als Abnahme gelten

Version 1 ist gelungen, wenn dieselben Läufe dieselben Zahlen liefern. Alle
lokal gemessen, 1920×1080, Monaco vollständig mit vorgeblurtem Hintergrund,
acht Anschläge je Sekunde.

| Größe | Sollwert | woher |
|---|---|---|
| onPaint-Takt p50 | 16,85 ms (59,4/s) | `ProbeBenchmark`, Stufe A |
| Eingabe→onPaint p50 | 30,2 ms | `ProbeBenchmark`, Stufe B |
| Eingabe→onPaint p95 | 42,2 ms | dito |
| bis sichtbar p50 | 43,7 ms | dito |
| Upload p50 | 9,9 ms | dito |
| Minecrafts Bildzeit p50 | 8,6 ms | dito |
| Lebenslauf, 3 Zyklen | 186 → 190 → 191 MB | `LifecycleBenchmark` |
| Waisenprozesse nach hartem Abbruch | heute **8**, Ziel **0** | `procwatch.ps1` |

Die letzte Zeile ist die einzige, die besser werden muss.

---

## Der Zustand der Maschine

**Wichtig, weil er nicht im Repo steht und still verlorengeht.**

```text
C:\jcef                                   gepatchte java-cef-Quellen (CEF 116)
C:\jcef\jcef_build\native\Release\jcef.dll  die gebaute Bibliothek
```

Eingespielt ist sie hier:

```text
build\mcef-libraries\windows_amd64\jcef.dll            ← gepatcht, 950 KB
build\mcef-libraries\windows_amd64\jcef.dll.original   ← Original, 917 KB
```

**`gradlew clean` löscht `build/` und damit die gepatchte Bibliothek.** MCEF
lädt dann still das Original nach, und jede Messung zeigt kommentarlos wieder
dreißig statt sechzig Bilder je Sekunde — ohne Fehler, ohne Warnung. Nach
jedem `clean` muss die Datei aus `C:\jcef` erneut kopiert werden.

Der 60-Hz-Patch dort ist **ein Behelf für den A/B-Test**, keine Dauerlösung:
Er liest die Bildrate aus der Umgebungsvariablen `JCEF_WINDOWLESS_FRAME_RATE`.
In Version 1 verschwindet er ersatzlos — upstream hat `CefBrowserSettings`.

Der Bau lief unter `C:\jcef` und nicht im Zwischenordner, weil dort der
längste Pfad **264 Zeichen** erreichte, vier über Windows' Grenze von 260.

**Die Patches selbst sind gesichert** und hängen nicht mehr an `C:\jcef`:

```text
tools/runtime/patches/0000-poc-60hz-cef116.patch
tools/runtime/patches/0000-poc-clang-format-nicht-fatal.patch
tools/runtime/patches/LIESMICH.md
```

`C:\jcef` ist damit nur noch Bequemlichkeit — dort liegt die fertig gebaute
Bibliothek. Verschwindet der Ordner, kostet es einen Bau, keine Arbeit.

## Zustand des Repositorys

Alles aus dieser Sitzung ist committet, zuletzt `af4e42c`. Sechs Commits:
Messgrundlagen, Messabläufe, Werkzeuge, Berichte, Pläne, Patches.

**Offen im Baum sind nur `logs/debug.log` und `logs/latest.log`** — das
Rohmaterial der Messungen. Bewusst weder committet noch verworfen: Die Zahlen
stehen in den Berichten, die Protokolle sind ihre Herkunft.

Ein `gradlew compileJava` lief zuletzt grün, und die gepatchte Bibliothek hat
ihn überstanden (950 KB gepatcht, 917 KB Original liegen beide).

---

## Womit weitermachen

**A1 aus `plan-v1-blockA-und-input.md`.** Konkret:

1. `tools/runtime/` anlegen mit `pin.properties` (Commit-SHA von java-cef,
   CEF-Fassung), `patches/`, `build-jcef.ps1`.
2. Upstream klonen, auf den Pin setzen, bauen — **ohne** Patches, erst einmal
   nackt. Das prüft die Pipeline.
3. Die beiden bekannten Fallen einbauen: Prüfung der Pfadlänge, Python 3.12
   festnageln, `clang-format` nicht fatal.

Danach A2 (Patch-Satz), A3 (Paketpipeline), A4a (Takt-Probe), B3a
(`sendKeyEventRaw`), A4b (Tastatur-Prüfstand) — die Reihenfolge steht am Ende
von `plan-input-umsetzung.md` samt Begründung, warum A4b vor B3b kommt.

---

## Welches Dokument was enthält

### Berichte über Gemessenes

| Datei | Inhalt |
|---|---|
| `stand-texturupload.md` | Schritt A–C: eigener Renderpfad, Direktupload |
| `stand-browser-screen.md` | Schritt D: Bildschirm, Eingabe, Fokus |
| `stand-hintergrund.md` | Schritt E: `mc://frame`, Glas |
| `stand-monaco.md` | Schritt F: Monaco als Arbeitslast |
| `stand-1080p.md` | Schritt G: Tipplatenz bei 1080p |
| `stand-was-ist-a.md` | Schritt H: was in A steckt — Monaco sind 4 % |
| `stand-innerhalb-chromium.md` | Schritt I: **der Takt ist die Ursache**, CopyOutput 0,22 ms |
| `stand-60hz.md` | lokaler Kontrolllauf + der 60-Hz-Patch, A/B-Vergleich |
| `stand-lifecycle.md` | kein Leck beim Schließen; Navigation war die Ursache |

### Pläne

| Datei | Inhalt |
|---|---|
| `plan-eigene-runtime.md` | Migrationsplan, Versionsstrategie, Delta zu MCEF |
| `plan-runtime-v1.md` | Version 1: neun Schritte, Paketstruktur, Verteilung, Risiken |
| `plan-v1-blockA-und-input.md` | Block A ausgearbeitet: Bau, Patches, Pipeline, Proben |
| `plan-input-umsetzung.md` | Eingabe-Adapter: Scancode-Messung, Patch, Tabellen, Testmatrix |

### Werkzeuge

| Datei | Zweck |
|---|---|
| `tools/trace.mjs` | Chromium-Trace über CDP aufnehmen und auswerten |
| `tools/raf.mjs` | Bildtakt einer Seite messen (Leerlauf und unter Last) |
| `tools/windowed.mjs` | dieselbe Seite in einem echten Chrome-Fenster messen |
| `tools/procwatch.ps1` | Chromium-Prozesse je Sekunde mitschreiben |
| `tools/runtime/probe/ScanProbe.java` | GLFW-Scancodes ausgeben — Grundlage von `GlfwScancodes` |

### Messabläufe im Spiel

```text
./gradlew runClient -Pide                      Oberfläche öffnen (F6 öffnet erneut)
./gradlew runClient -Pide -Pprobe              Takt + Monaco messen
./gradlew runClient -Pide -Plifecycle          öffnen/schließen, drei Zyklen
./gradlew runClient -Pide -Pdevtools           Debug-Port auf 127.0.0.1:9222
./gradlew runClient -Pide -Ptyping             Tippstrecke
  jeweils mit -Pw=1920 -Ph=1080
```

---

## Die drei Präzisierungen, die den Umfang bestimmen

Alle drei kamen beim Nachsehen im Upstream-Quelltext und korrigieren
Annahmen, die vorher plausibel schienen.

**1. `CefBrowserOsr` ist upstream paketprivat.** Der CinemaMod-Fork hat sie
öffentlich gemacht; unser `FnBrowser` erbt davon. Erben ist unumgänglich, weil
`sendKeyEvent` und die Mausmethoden `protected final` sind. → Sichtbarkeits-
Patch, zwei Zeilen.

**2. Upstream spricht bei der Eingabe AWT**, der Fork eigene Typen. Der
Renderpfad ist dagegen signaturgleich und zieht unverändert um.

**3. Auf Windows hängt die Tastenerkennung an einem privaten Feld.** Der
native Code liest `KeyEvent.scancode` — ein `private transient long`, das nur
der native AWT-Code füllt. Ein selbst gebautes Ereignis trägt dort null, und
Chromium erkennt **keine einzige Taste**. Reflection darauf bräuchte
`--add-opens` und ist bei Spielern nicht ausrollbar. → Der Patch bekommt eine
zweite native Methode mit einfachen Parametern; der Satz wächst auf rund 64
Zeilen.

---

## Der wichtigste Messwert für den nächsten Schritt

`tools/runtime/probe/ScanProbe.java`, gelaufen am 31. August 2026:

```text
UP       0x0148 = 0x100 | 0x48      KP_8     0x0048
DOWN     0x0150 = 0x100 | 0x50      KP_2     0x0050
INSERT   0x0152 = 0x100 | 0x52      KP_0     0x0052
RCTRL    0x011D = 0x100 | 0x1D      LCTRL    0x001D
RALT     0x0138 = 0x100 | 0x38      LALT     0x0038
KP_ENTER 0x011C = 0x100 | 0x1C      ENTER    0x001C
```

**GLFW kodiert „erweiterte Taste" als Bit 8, Windows als Präfix `0xE0` und
lParam-Bit 24.** Pfeiltasten und Ziffernblock teilen sich die unteren acht
Bit. Wer das Bit verliert, bekommt für Pfeil-hoch `VK_NUMPAD8` — der Editor
schriebe eine Acht, statt den Cursor zu bewegen.

Deshalb darf `(scanCode << 16) | 1` aus upstream nicht blind übernommen
werden, und deshalb steht der Tastatur-Prüfstand (A4b) **vor** den
Übersetzungstabellen (B3b).

---

## Was offen ist

- **Das subjektive Tippurteil.** Der 60-Hz-Lauf ist gemessen, aber wie sich
  das Tippen anfühlt, hat noch niemand beurteilt. Ein Lauf mit `-Pide` und
  gesetzter Umgebungsvariable genügt dafür.
- **Warum zwei GPU-Prozesse laufen** statt einem. Nicht verfolgt, ohne
  erkennbare Folgen.
- **Der Fenstervergleich aus `stand-innerhalb-chromium.md` §6** stammt aus der
  Zeit der Fernsitzung und wurde bewusst nicht wiederholt.
