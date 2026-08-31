# Version 1, ausgearbeitet: Block A und der Eingabe-Adapter

Stand: 31. August 2026. Umsetzungsplan, kein Code im Mod. Die
Grundentscheidungen sind gefallen und werden hier nicht berührt.

---

## Dritte Präzisierung des Patch-Umfangs

Die beiden ersten stehen in `plan-runtime-v1.md`. Diese kommt hinzu und ist
die folgenreichste, weil sie die Reihenfolge der Arbeit ändert.

**Auf Windows hängt die gesamte Tastenerkennung an einem privaten Feld, das
wir nicht füllen können.** Der native Code von upstream:

```c
jlong scanCode = 0;
GetJNIFieldLong(env, cls, key_event, "scancode", &scanCode);
BYTE VkCode = LOBYTE(MapVirtualKey(scanCode, MAPVK_VSC_TO_VK));
...
cef_event.windows_key_code = VkCode;
cef_event.native_key_code  = (scanCode << 16) | 1;
```

Drei Beobachtungen, jede nachgesehen:

1. **`getKeyCode()` wird auf Windows gar nicht gelesen.** Nur im Linux- und
   macOS-Zweig. Der virtuelle Tastencode wird ausschließlich aus dem Scancode
   berechnet.
2. **`scancode` ist ein privates Feld**, kein Konstruktorparameter:
   ```text
   java.awt.event.KeyEvent:
     private transient long rawCode;
     private transient long primaryLevelUnicode;
     private transient long scancode;        ← das hier
     private transient long extendedKeyCode;
   ```
   Gesetzt wird es nur vom nativen AWT-Code, wenn Windows selbst eine Taste
   meldet. Ein Ereignis, das wir bauen, hat dort **null**.
3. **Null bedeutet, dass gar nichts funktioniert.** `MapVirtualKey(0, …)`
   liefert 0, also `windows_key_code = 0` für jede Taste. Nicht „falsche
   Sondertasten" — Chromium erkennt keinen einzigen Tastendruck.

Der CinemaMod-Fork hat genau dieses Loch mit einer eigenen Funktion gestopft
(`MapScanCodeGLFW`, `CefBrowser_N.cpp:1665`), die aus dem missbrauchten
`keyChar` den Scancode ableitet. Das ist der Sonderfall, den unser `FnBrowser`
dokumentiert. **Upstream hat diese Funktion nicht.**

### Was daraus folgt

| Weg | Urteil |
|---|---|
| Reflection auf `scancode` | **verworfen.** Braucht `--add-opens java.desktop/java.awt.event=ALL-UNNAMED`. Spieler setzen keine JVM-Schalter, und eine Mod kann sie nicht erzwingen. Nicht „schwierig", sondern nicht ausrollbar. |
| Eigene `KeyEvent`-Unterklasse | **unmöglich.** Das Feld ist privat, kein Setter, kein Konstruktorweg. |
| **Native Variante mit einfachen Parametern** | **gewählt.** Siehe unten. |

### Die gewählte Form

Statt ein AWT-Objekt zu bauen, dessen wichtigstes Feld wir nicht füllen
können, bekommt der Patch eine zweite native Methode, die die Werte direkt
nimmt:

```java
// in CefBrowser_N.java, neben sendKeyEvent(KeyEvent)
public void sendKeyEventRaw(int type, int modifiers, char keyChar,
                            int scancode, boolean extended, int keyCode) {
    N_SendKeyEventRaw(type, modifiers, keyChar, scancode, extended, keyCode);
}
private final native void N_SendKeyEventRaw(int type, int modifiers,
        char keyChar, int scancode, boolean extended, int keyCode);
```

> Nachtrag: Der Parameter `extended` kam nach einer Messung hinzu. GLFW
> kodiert erweiterte Tasten als Bit 8 des Scancodes, Windows erwartet ein
> eigenes Bit im lParam — und ohne die Unterscheidung liefert die Pfeiltaste
> nach oben denselben Wert wie die Acht auf dem Ziffernblock. Die Messung und
> die Folgen stehen in `plan-input-umsetzung.md`.

Nativ übernimmt sie den Windows-Zweig von upstream **fast wörtlich** — nur die
Herkunft der Werte ändert sich von JNI-Feldzugriffen zu Parametern.
`keyCode` wird mitgeführt, weil die Linux- und macOS-Zweige ihn brauchen.

**Eine Festlegung, die sonst still schiefgeht:** `modifiers` trägt
**AWT-`_DOWN_MASK`-Werte**, nicht GLFW-Masken. Der übernommene native Code
reicht sie an `GetCefModifiers()` weiter, und die Funktion erwartet genau
diese Zahlen. Wer beim Umsetzen GLFW-Masken durchreicht, bekommt keinen
Fehler — nur Tastenkürzel, die nicht auslösen.

**Der Gewinn reicht weiter als die Tastatur:** Ohne AWT-Objekt für Tasten
entfällt die ganze Frage nach einer Dummy-Komponente und nach `headless` für
diesen Pfad. Sie bleibt nur noch für die Maus offen, und dort ist sie harmlos
(siehe Abschnitt B3.5).

### Was den Patch **nicht** braucht

Beides nachgesehen, beides eine Erleichterung:

- **Getippte Zeichen.** Der KEY_TYPED-Zweig lautet
  `cef_event.windows_key_code = key_char;` — **kein Scancode**, kein
  Tastencode. Getippter Text bräuchte den Patch also nicht.
  Wir schicken ihn trotzdem über dieselbe Methode
  (`sendKeyEventRaw(KEY_TYPED, mods, zeichen, 0, 0)`), und zwar aus einem
  Grund, der weiter reicht als Bequemlichkeit: Damit ist der **gesamte**
  Tastenpfad frei von AWT-Objekten, und die Dummy-Komponente aus B3.5 bleibt
  beweisbar auf die Maus beschränkt. Es kostet keine zusätzliche Zeile im
  Patch — der Typ-Parameter ist ohnehin da.
- **Maus und Rad.** Gelesen werden nur öffentliche Methoden: `getID`, `getX`,
  `getY`, `getClickCount`, `getButton`, `getModifiersEx`, beim Rad zusätzlich
  `getScrollType`, `getWheelRotation`, `getUnitsToScroll`. Alles über die
  Konstruktoren setzbar, kein verstecktes Feld.

### Umfang des Patch-Satzes

```text
1. CefBrowserOsr: Klasse und Konstruktor public          2 Zeilen
2. sendKeyEventRaw: Java-Methode + native Deklaration   ~12 Zeilen
3. N_SendKeyEventRaw: native Umsetzung                  ~50 Zeilen
                                                        ——————
                                                        ~64 Zeilen
```

Aus „zwei Zeilen" werden vierundsechzig. Immer noch ein kleiner Patch-Satz,
aber die Zahl gehört ehrlich in die Planung.

---

# Teil 1 — Block A

## A1. java-cef auf CEF 146 bauen

Das Rezept steht nicht in der Theorie — es ist der Lauf, der in dieser Sitzung
für CEF 116 funktioniert hat, mit den beiden Fallen, die dabei zugeschlagen
haben.

### Ordnerstruktur

```text
tools/runtime/
  pin.properties          Fassungen, an einer Stelle
  patches/
    0001-cefbrowserosr-public.patch
    0002-send-key-event-raw.patch
  build-jcef.ps1          Checkout, Patches, CMake, Bau
  package-runtime.ps1     Artefakte einsammeln, packen
  manifest.ps1            SHA-256, Manifest schreiben
  probe/                  Standalone-Prüfprogramm (A4)
  build/                  Arbeitsverzeichnis, nicht eingecheckt
```

### Der Pin, an genau einer Stelle

```properties
# tools/runtime/pin.properties
jcef.repo=https://github.com/chromiumembedded/java-cef.git
jcef.commit=<voller SHA, kein Branch, kein Tag>
cef.version=146.0.10+g8219561+chromium-146.0.7680.179
```

**Ein Commit-SHA, kein Branch.** Ein Branch driftet zwischen zwei Bauten und
macht „reproduzierbar" zu einer Behauptung. Die CEF-Fassung wird zusätzlich
festgehalten, damit ein versehentliches Anheben des Upstream-Pins auffällt,
statt still mitzukommen.

### Die beiden Fallen, beide erlebt

**Windows-Pfadlänge.** Der längste Pfad der CEF-Distribution kam im
Zwischenordner auf **264 Zeichen**, vier über der Grenze von 260. Folge: Das
Entpacken bricht unbemerkt ab, einzelne Kopfdateien fehlen, und der Bau
scheitert später an einer Fehlermeldung, die nichts mit der Ursache zu tun hat.

```powershell
# in build-jcef.ps1, ganz vorn
$arbeitsPfad = $env:FN_JCEF_BUILD ?? "C:\fnjcef"
if ($arbeitsPfad.Length -gt 20) {
  throw "Arbeitspfad zu lang ($($arbeitsPfad.Length)). Die CEF-Distribution " +
        "braucht rund 240 Zeichen für ihre tiefsten Dateien."
}
```

Kein Verlassen auf „lange Pfade sind ja aktiviert" — die Prüfung ist billiger
als die Fehlersuche.

**Python-Fassung.** Der Bau lädt `clang-format` über ein beigelegtes `gsutil`,
das unter Python 3.13 und neuer an seiner eigenen Enum-Prüfung stirbt. Zwei
Maßnahmen:

```powershell
# 1. Fassung festnageln statt die des Rechners zu nehmen
$python = "C:\Python312\python.exe"
if (-not (Test-Path $python)) { throw "Python 3.12 fehlt (3.13+ bricht gsutil)" }

# 2. clang-format ist ein Formatierer und für den Bau ohne Bedeutung
#    → in CMakeLists den FATAL_ERROR zur Warnung herabstufen (Patch 0003)
```

### Ablauf

```text
build-jcef.ps1
  1. Pfadlänge prüfen
  2. Python-Fassung prüfen
  3. git clone --depth 1 + checkout <commit aus pin.properties>
  4. git apply patches/*.patch   (in Reihenfolge, jeder muss sauber gehen)
  5. cmake -G "Visual Studio 17 2022" -A x64 -DPROJECT_ARCH=x86_64
  6. cmake --build . --config Release --target jcef
  7. Java-Teil bauen (jcef.jar)
  8. Artefakte nach build/out/ sammeln
```

### Was entstehen muss

```text
build/out/windows-x86_64/
  jcef.jar                 die Java-Klassen, gepatcht
  jcef.dll                 unsere native Bibliothek
  jcef_helper.exe          Chromiums Hilfsprozess
  libcef.dll               unverändert aus der Distribution
  chrome_elf.dll, d3dcompiler_47.dll, libEGL.dll, libGLESv2.dll,
  vk_swiftshader.dll, vulkan-1.dll
  snapshot_blob.bin, v8_context_snapshot.bin
  icudtl.dat, resources.pak, chrome_100_percent.pak,
  chrome_200_percent.pak
  locales/
```

Die Liste ist nicht geraten — CMake gibt sie beim Konfigurieren aus
(`CEF Binary files:` / `CEF Resource files:`).

### Reproduzierbarkeit prüfen

Nicht behaupten, sondern zeigen:

```powershell
# build-jcef.ps1 -Verify
# baut zweimal aus leerem Verzeichnis und vergleicht die Prüfsummen
```

**Erwartung mit Augenmaß:** `libcef.dll` und die Ressourcen kommen aus der
Distribution und müssen bitgleich sein. `jcef.dll` enthält Zeitstempel und
einen Build-Pfad; MSVC erzeugt sie nicht ohne Weiteres bitgleich. Das Ziel für
Version 1 ist deshalb **gleiches Ergebnis, nicht gleiche Bytes**: gleiche
Fassungen, gleiche Patches, gleiche Dateiliste, und die Probe aus A4 besteht.
Bitgleichheit (`/Brepro`, feste Pfade) ist ein späteres Thema.

---

## A2. Der Patch-Satz

### Datei 1 — Sichtbarkeit

`java/org/cef/browser/CefBrowserOsr.java`

```diff
-class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
+public class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {

-    CefBrowserOsr(CefClient client, String url, boolean transparent,
-                  CefRequestContext context, CefBrowserSettings settings) {
+    public CefBrowserOsr(CefClient client, String url, boolean transparent,
+                         CefRequestContext context, CefBrowserSettings settings) {
```

### Datei 2 und 3 — die Tastenvariante

`java/org/cef/browser/CefBrowser_N.java` und `native/CefBrowser_N.cpp`, wie
oben beschrieben.

### Wie die Patches gepflegt werden

**Als `.patch`-Dateien über einem gepinnten Checkout, nicht als Fork-Klon.**
Der Unterschied ist der Kern der Frage „wie verhindert man, dass unser Anteil
driftet":

| | Fork-Klon | Patch-Dateien |
|---|---|---|
| Unser Anteil sichtbar | nur per Vergleich gegen upstream | **jede Zeile in `patches/`** |
| Upstream anheben | Merge, Konflikte im Klon, Anteil vermischt sich | `git apply` schlägt **fehl** — laut und an der richtigen Stelle |
| Prüfbarkeit | „was haben wir geändert?" ist eine Recherche | `wc -l patches/*.patch` |

Erzeugt werden sie mit `git format-patch` gegen den Pin; angewendet in
`build-jcef.ps1` mit `git apply --check` vorab, damit ein Fehlschlag den Bau
sofort beendet statt halb gepatchten Code zu übersetzen.

**Eine Regel dazu:** Jeder Patch trägt oben eine Begründung, warum er nötig
ist und was ihn überflüssig machen würde. Der Sichtbarkeits-Patch endet, wenn
upstream `CefBrowserOsr` öffnet; die Tastenvariante endet, wenn upstream einen
Weg bietet, den Scancode zu übergeben.

---

## A3. Bau- und Paketpipeline

| Skript | Eingang | Ausgang | Prüfungen |
|---|---|---|---|
| `build-jcef.ps1` | `pin.properties`, `patches/` | `build/out/<plattform>/` | Pfadlänge, Python, `git apply --check`, Artefaktliste vollständig |
| `package-runtime.ps1` | `build/out/<plattform>/` | `build/dist/fn-runtime-<cef>-<plattform>.tar.gz` | jede erwartete Datei vorhanden, Archiv entpackbar |
| `manifest.ps1` | `build/dist/*.tar.gz` | `build/dist/runtime-manifest.json` | SHA-256 je Archiv, Größe, Fassungen aus `pin.properties` |

### SHA-256 und Manifest

```powershell
$hash = (Get-FileHash $archiv -Algorithm SHA256).Hash.ToLower()
```

Das Manifest wird **erzeugt, nicht gepflegt** — jeder Wert stammt aus einer
Datei oder aus `pin.properties`. Von Hand eingetragene Prüfsummen sind der
Weg, auf dem eine falsche Zahl in eine Auslieferung gerät.

```json
{
  "runtime": 1,
  "cef": "146.0.10+g8219561",
  "chromium": "146.0.7680.179",
  "jcef": "<commit>",
  "patches": ["0001-cefbrowserosr-public", "0002-send-key-event-raw"],
  "platforms": {
    "windows-x86_64": { "url": "...", "size": 0, "sha256": "..." }
  }
}
```

Die Liste der Patches gehört hinein: Eine installierte Laufzeitumgebung soll
sagen können, was in ihr steckt.

### Wie Linux und macOS später dazukommen

Die Trennung ist von Anfang an da, auch wenn nur ein Zweig gefüllt ist:

```text
build-jcef.ps1        ruft build-jcef.common.ps1 mit Windows-Belegung
build-jcef.sh         später, ruft dieselbe Logik mit anderer Belegung
package-runtime.*     plattformabhängig nur in der Dateiliste
manifest.*            plattformneutral, nimmt beliebig viele Archive
```

Konkret heißt „ohne Windows umzubauen": Die Dateiliste je Plattform steht in
einer eigenen Datei (`files-windows.txt`), nicht im Skript. Ein neuer Zweig
bringt eine neue Liste mit und ändert nichts Bestehendes.

---

## A4. Standalone-Probe

Zwei Aufgaben in einem Programm. Die erste war schon geplant; die zweite
ergibt sich aus dem Scancode-Fund und ist die wichtigere.

1. **Takt und Lebenszyklus prüfen** — läuft der Unterbau überhaupt?
2. **Das Tastatur-Mapping empirisch klären** — bevor eine Zeile davon im Mod
   landet.

### Warum die Schleife Minecrafts Modell nachbilden muss

Unser gesamter Renderpfad und der Cleanup-Vertrag ruhen darauf, dass `onPaint`
im selben Thread ankommt, aus dem gepumpt wird. Eine Probe, die CEFs eigene
Schleife laufen lässt, prüft ein anderes Modell und gibt eine Zusicherung, die
im Mod nicht gilt.

### Skelett

```java
public final class Probe {

    /** Ein Thread, der pumpt — wie Minecrafts Renderthread. */
    private static final ArrayBlockingQueue<Runnable> AUFTRAEGE =
            new ArrayBlockingQueue<>(64);

    public static void main(String[] args) throws Exception {
        CefApp.startup(new String[] {});

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.cache_path = null;                 // frisch, jedes Mal

        CefApp app = CefApp.getInstance(settings);
        CefClient client = app.createClient();

        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 60;

        Messungen abstaende = new Messungen();
        long[] letzterPaint = { 0 };

        // Eigene Unterklasse, genau wie FnBrowser im Mod.
        CefBrowserOsr browser = new CefBrowserOsr(
                client, args[0], true, null, browserSettings) {
            @Override
            public void onPaint(CefBrowser b, boolean popup, Rectangle[] dirty,
                                ByteBuffer buffer, int w, int h) {
                long jetzt = System.nanoTime();
                if (letzterPaint[0] != 0) {
                    abstaende.dazu(jetzt - letzterPaint[0]);
                }
                letzterPaint[0] = jetzt;
                // Nichts hochladen — hier zählt nur der Takt.
            }
        };
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(1920, 1080);

        // Die Pumpschleife. Alles Weitere passiert in diesem Thread.
        long bis = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < bis) {
            app.doMessageLoopWork(0);
            Runnable auftrag = AUFTRAEGE.poll();
            if (auftrag != null) { auftrag.run(); }
            Thread.sleep(1);
        }

        System.out.println("Takt p50 " + abstaende.perzentil(50) + " ms");
        System.out.println("Takt p95 " + abstaende.perzentil(95) + " ms");

        browser.close(true);
        // Weiterpumpen, damit CEF das Schliessen abarbeiten kann.
        for (int i = 0; i < 200; i++) { app.doMessageLoopWork(0); Thread.sleep(5); }
        app.dispose();
        Thread.sleep(2000);
        System.out.println("Fertig. Jetzt jcef_helper zaehlen.");
    }
}
```

### Der Tastatur-Prüfstand

Der Teil, der das Mapping klärt. Die Probe tippt eine Referenzfolge über die
neue Methode und **fragt die Seite, was angekommen ist**:

```javascript
// probe-keys.html
window.protokoll = [];
addEventListener('keydown', e => window.protokoll.push(
  {art:'down', key:e.key, code:e.code, keyCode:e.keyCode,
   ctrl:e.ctrlKey, shift:e.shiftKey, alt:e.altKey}));
addEventListener('keypress', e => window.protokoll.push(
  {art:'press', key:e.key, charCode:e.charCode}));
addEventListener('keyup', e => window.protokoll.push({art:'up', key:e.key}));
```

Ausgelesen über `executeJavaScript` und einen Rückkanal, oder — einfacher —
über den Debug-Port, den wir in dieser Sitzung schon benutzt haben.

**Damit ist das Mapping keine Behauptung mehr.** Für jede Taste der Testmatrix
steht schwarz auf weiß, was Chromium empfangen hat.

### Abnahme

| | Sollwert | Herkunft |
|---|---|---|
| onPaint-Abstand p50 | 16,7 ms ± 1 | gemessen 16,85 ms |
| daraus Bildrate | 59–60/s | gemessen 59,4/s |
| `jcef_helper` nach `dispose()` | **0** | `tools/procwatch.ps1` |
| Referenzfolge in der Seite | jede Taste wie erwartet | Testmatrix B3.6 |

---

# Teil 2 — B3 und B4: der Eingabe-Adapter

## B3.1 Zielklassen

Der Vorschlag aus dem Auftrag, angepasst an den Scancode-Fund:

```text
web/input/
  GlfwKeys          GLFW-Tastencode → AWT-Tastencode (Tabelle)
  GlfwScancodes     GLFW-Tastencode → Windows-Scancode
  AwtModifiers      GLFW-Modifikatoren → AWT getModifiersEx
  AwtMouseEvents    GLFW → MouseEvent / MouseWheelEvent
  AwtEventSource    die eine Dummy-Komponente, nur für die Maus
```

**Zwei Abweichungen vom Vorschlag, beide begründet:**

`AwtKeyEvents` entfällt. Tasten gehen nicht über AWT-Objekte, sondern über
`sendKeyEventRaw` mit einfachen Parametern — das ist die Folge des
Scancode-Funds. Stattdessen zwei kleine Übersetzer: einer für den Tastencode,
einer für den Scancode.

`AwtEventSource` bleibt, schrumpft aber auf die Maus. Sie steht damit **nicht**
mehr im Weg der Texteingabe.

`GlfwScancodes` ist eine eigene Klasse, weil dort die Herleitung steht — und
weil sie plattformabhängig ist, während `GlfwKeys` es nicht ist.

## B3.2 Das Tastenmapping

### Woher der Scancode kommt

GLFW liefert ihn selbst, und unser Code holt ihn heute schon:

```java
int scancode = GLFW.glfwGetKeyScancode(glfwKey);
```

**Das ist auf Windows genau der Wert, den `MapVirtualKey(…, MAPVK_VSC_TO_VK)`
erwartet** — GLFW gibt unter Windows den Hardware-Scancode zurück. Damit ist
der Weg direkt:

```text
GLFW-Taste → glfwGetKeyScancode → sendKeyEventRaw(scancode) → MapVirtualKey → VkCode
```

**Zu prüfen, nicht anzunehmen:** Für erweiterte Tasten (Pfeile, Einfügen,
Entfernen, Pos1, Ende, Bild auf/ab, rechtes Strg, rechtes Alt) setzt Windows
im Scancode ein Erweiterungs-Bit. Ob GLFW dieses Bit mitliefert und ob
`MapVirtualKey` ohne es die richtige Taste findet, ist genau die Frage, die
der Prüfstand aus A4 beantwortet. Es ist die wahrscheinlichste Fehlerquelle
des ganzen Adapters.

### Der Tastencode

Für Windows braucht ihn niemand (der VkCode kommt aus dem Scancode). Für die
späteren Linux- und macOS-Zweige schon — deshalb wird er mitgeführt und
gefüllt:

```text
GLFW_KEY_A..Z        → KeyEvent.VK_A..VK_Z          (direkt, gleiche Reihenfolge)
GLFW_KEY_0..9        → KeyEvent.VK_0..VK_9          (direkt)
GLFW_KEY_F1..F25     → KeyEvent.VK_F1..VK_F24       (F25 hat kein Gegenstück)
GLFW_KEY_ENTER       → VK_ENTER
GLFW_KEY_BACKSPACE   → VK_BACK_SPACE
GLFW_KEY_DELETE      → VK_DELETE
GLFW_KEY_ESCAPE      → VK_ESCAPE
GLFW_KEY_TAB         → VK_TAB
GLFW_KEY_LEFT/…      → VK_LEFT / VK_RIGHT / VK_UP / VK_DOWN
GLFW_KEY_HOME/END    → VK_HOME / VK_END
GLFW_KEY_PAGE_UP/…   → VK_PAGE_UP / VK_PAGE_DOWN
GLFW_KEY_INSERT      → VK_INSERT
```

Buchstaben und Ziffern gehen direkt, weil beide Zählungen dort den
ASCII-Werten folgen. Alles andere kommt in eine ausgeschriebene Tabelle —
keine Rechnung, die für neunzig Prozent stimmt.

### Die Modifikatoren

```text
GLFW_MOD_SHIFT    → InputEvent.SHIFT_DOWN_MASK
GLFW_MOD_CONTROL  → InputEvent.CTRL_DOWN_MASK
GLFW_MOD_ALT      → InputEvent.ALT_DOWN_MASK
GLFW_MOD_SUPER    → InputEvent.META_DOWN_MASK
```

`getModifiersEx` wird gelesen, also die `_DOWN_MASK`-Familie — **nicht** die
alten `SHIFT_MASK`-Konstanten. Die sehen ähnlich aus und haben andere Werte;
das ist eine klassische stille Verwechslung.

### AltGr und das deutsche Layout — der ehrliche Teil

**AltGr meldet GLFW unter Windows als Strg + Alt.** Das ist keine Eigenart
unseres Codes, sondern wie Windows die Taste liefert.

Für Text ist das folgenlos: Das fertige Zeichen kommt über Minecrafts
`charTyped` und geht als KEY_TYPED durch, wo nur `keyChar` zählt. **Deshalb
funktionieren `@`, `\`, `~`, `|`, `€` und die Umlaute heute** — das Zeichen
bestimmt das Betriebssystem, nicht wir.

Das Problem liegt woanders: Das begleitende KEY_PRESSED trägt dann Strg **und**
Alt als Modifikatoren. Monaco könnte das als Tastenkürzel deuten und die
Eingabe abfangen, bevor das Zeichen ankommt. Ob es das tut, ist ein
**Testfall**, kein Absatz Theorie — er steht in der Matrix.

Fällt der Test negativ aus, ist die Gegenmaßnahme umrissen: Wenn Strg und Alt
gemeinsam anliegen und ein Zeichen folgt, die Modifikatoren am KEY_PRESSED
weglassen. Das ist genau das, was Browser für AltGr tun.

## B3.3 Reihenfolge der Ereignisse

### Ein Buchstabe

```text
KEY_PRESSED   scancode der Taste, keyChar = CHAR_UNDEFINED
KEY_TYPED     keyChar = 'a', kein Scancode nötig
KEY_RELEASED  scancode der Taste
```

Minecraft liefert genau diese drei Anlässe: `keyPressed`, `charTyped`,
`keyReleased`. Wir bauen nichts zusammen und leiten nichts ab.

### Ein Tastenkürzel, Strg+S

```text
KEY_PRESSED   Strg          modifiers = CTRL
KEY_PRESSED   S             modifiers = CTRL
KEY_RELEASED  S             modifiers = CTRL
KEY_RELEASED  Strg          modifiers = 0
```

**Kein KEY_TYPED.** Minecraft ruft `charTyped` bei gedrückter Strg-Taste nicht
auf — und genau das ist richtig, denn ein KEY_TYPED würde ein Steuerzeichen in
den Text schreiben.

### Wann KEY_TYPED gesendet wird — und wann nicht

| | KEY_TYPED? |
|---|---|
| druckbares Zeichen, ohne Strg/Alt | **ja**, von `charTyped` |
| mit Strg | nein |
| Rücktaste, Entf, Eingabe, Esc, Pfeile, Pos1, Ende, Bild auf/ab | **nein** — reine Steuertasten |
| AltGr-Zeichen (`@`, `€`) | **ja** — Windows liefert das Zeichen |
| Funktionstasten | nein |

**Die Regel dahinter ist einfach:** KEY_TYPED entsteht ausschließlich aus
`charTyped`. Wir erzeugen es nie selbst aus einem Tastencode. Damit ist auch
die Frage nach doppelten Eingaben beantwortet: Ein Zeichen hat genau eine
Quelle. Wer zusätzlich aus KEY_PRESSED ein Zeichen ableiten wollte, bekäme
jeden Buchstaben zweimal — der klassische Fehler an dieser Stelle.

## B3.4 Maus und Rad

Hier bleibt alles bei AWT, weil alle nötigen Werte über die Konstruktoren
gehen.

```text
GLFW_MOUSE_BUTTON_LEFT   (0) → MouseEvent.BUTTON1
GLFW_MOUSE_BUTTON_RIGHT  (1) → MouseEvent.BUTTON3     ← nicht BUTTON2
GLFW_MOUSE_BUTTON_MIDDLE (2) → MouseEvent.BUTTON2
```

Die Vertauschung von rechts und Mitte ist die zweite klassische Falle: GLFW
zählt in Reihenfolge der Tasten, AWT nach Bedeutung.

| Anlass | AWT-Ereignis |
|---|---|
| Taste herunter | `MOUSE_PRESSED` |
| Taste herauf | `MOUSE_RELEASED` |
| Bewegung ohne Taste | `MOUSE_MOVED` |
| Bewegung mit Taste | `MOUSE_DRAGGED` |
| Rad | `MOUSE_WHEEL` |

**`MOUSE_CLICKED` schicken wir nicht.** Chromium baut den Klick aus Herunter
und Herauf; ein zusätzliches Ereignis wäre ein zweiter Klick.

**Klickzähler:** Der Wert geht als `clickCount` in den Konstruktor. Unser
`ClickCounter` liefert ihn bereits und bleibt unverändert — daran und nur
daran erkennt Chromium einen Doppelklick.

**Rad:** Der native Code liest `getScrollType`, `getWheelRotation` und bei
`WHEEL_UNIT_SCROLL` zusätzlich `getUnitsToScroll`, das das Delta überschreibt.

```java
new MouseWheelEvent(quelle, MouseEvent.MOUSE_WHEEL, zeit, modifikatoren,
        x, y, 0, false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        scrollAmount,     // Einheiten je Rastung
        wheelRotation);   // Vorzeichen beachten
```

**Das Vorzeichen ist zu messen, nicht zu raten.** GLFW meldet bei einer
Bewegung nach oben ein positives Delta, AWT bei einer Bewegung nach oben ein
**negatives** `wheelRotation`. Die Wahrscheinlichkeit, dass Scrollen zunächst
verkehrt herum läuft, ist hoch — und es ist der am schnellsten bemerkte
Fehler. Prüfstand aus A4, dann festschreiben.

**Koordinaten** bleiben Browser-Pixel. `BrowserView` rechnet sie schon so aus,
und an dieser Stelle ändert sich nichts.

## B3.5 Die Ereignisquelle

**Für Tasten entfällt die Frage** — `sendKeyEventRaw` nimmt keine Komponente.
Das ist der zweite Gewinn des Patches.

Für die Maus braucht AWT eine Quelle; `null` wirft `IllegalArgumentException`.

```java
final class AwtEventSource {
    /**
     * Eine Komponente, die nie gezeigt wird und nur als Absender dient.
     *
     * <p>Kein Canvas und kein Frame: Beide ziehen bei ihrer Erzeugung das
     * Toolkit heran und werfen auf einem headless gesetzten Stapel. Eine
     * unmittelbare Unterklasse von Component tut das nicht — sie hat keinen
     * Peer und braucht keinen, weil niemand sie zeichnet.
     */
    static final Component QUELLE = new Component() {};
}
```

**Zu `java.awt.headless`:**

- **Windows** (Version 1): Minecraft setzt es nicht. Eine peerlose Komponente
  ist ohnehin unabhängig davon.
- **macOS**: Minecraft setzt `-Djava.awt.headless=true`, weil AWT dort mit dem
  Hauptthread kollidiert. Da wir keine Komponente **zeigen**, sondern nur als
  Absender führen, sollte auch das tragen — **zu verifizieren, wenn macOS
  drankommt**, und macOS ist nicht Version 1.
- **Wir setzen `java.awt.headless` nicht.** Eine Mod, die eine globale
  Systemeigenschaft verändert, kann anderen Mods den Boden wegziehen.

## B3.6 Testmatrix

### Automatisch

| Test | Sollwert |
|---|---|
| `TypingBenchmark`, A Eingabe→Bild p50 | 30,2 ms ± 3 |
| `TypingBenchmark`, p95 | 42,2 ms ± 5 |
| `ProbeBenchmark`, Takt p50 | 16,85 ms ± 1 |
| Referenzfolge `abcXYZ012` | Seite meldet dieselben Zeichen, keine Dopplungen |
| Sonderzeichenfolge `äöüß@€\|~\\` | Seite meldet dieselben Zeichen |
| `Strg+A`, `Strg+C`, `Strg+V`, `Strg+S`, `Strg+F` | `keydown` mit `ctrlKey=true`, **kein** `keypress` |
| Pfeile, Pos1, Ende, Bild auf/ab | `keydown` mit korrektem `code`, kein `keypress` |
| Rücktaste, Entf | `keydown` mit `Backspace`/`Delete` |
| Rad hoch / runter | Inhalt bewegt sich in die **erwartete** Richtung |
| Ziehen über Text | Auswahl entsteht, `clickCount` bleibt 1 |
| Doppelklick | `clickCount = 2`, Wort ausgewählt |

### Von Hand in Monaco

| Prüfung | Erwartung |
|---|---|
| Fließtext tippen | erscheint zeichengenau, keine Dopplungen, keine Verluste |
| Deutsches Layout: `äöüß` | erscheinen korrekt |
| AltGr: `@`, `€`, `\`, `~`, `\|` | erscheinen; **kein** Tastenkürzel wird ausgelöst |
| `Strg+C` / `Strg+V` | Zwischenablage arbeitet in beide Richtungen |
| `Strg+F` | Suchfeld öffnet, Eingabe landet darin |
| `Strg+S` | erreicht **Monaco**, nicht Minecraft |
| Mehrfachcursor (`Alt+Klick`) | zweiter Cursor entsteht |
| IntelliSense | Vorschlagsliste öffnet, Pfeile navigieren, Eingabe übernimmt |
| Schweben über einem Bezeichner | Hinweisfenster erscheint |
| `Esc` | schließt Vorschlagsliste; ein **zweites** `Esc` schließt die Oberfläche |
| Pos1 / Ende | springen an Zeilenanfang und -ende |
| Bild auf / Bild ab | blättern seitenweise |
| Pfeile mit Umschalt | erweitern die Auswahl |

**Die Esc-Zeile ist der Fokuskonflikt in Reinform** und gehört ausdrücklich
geprüft: Ein Editor braucht Esc, ein Minecraft-Bildschirm schließt damit.

## B3.7 Risiken und Rückfallebenen

| Risiko | Bewertung | Rückfall |
|---|---|---|
| **Erweiterungs-Bit im Scancode** | **hoch** — Pfeile und Sondertasten könnten falsch ankommen | Prüfstand A4 klärt es vor dem Einbau; notfalls eine kleine Ausnahmetabelle für die betroffenen Tasten |
| **Rad-Vorzeichen** | hoch, aber sofort sichtbar | messen, festschreiben, Test in der Matrix |
| **AltGr wird als Strg+Alt gedeutet** | mittel | Modifikatoren am KEY_PRESSED weglassen, wenn Strg+Alt zusammen anliegen und ein Zeichen folgt |
| **AWT unter NeoForge** | niedrig — nur noch die Maus, peerlose Komponente | wenn es klemmt: `sendMouseEventRaw` nach demselben Muster wie bei den Tasten |
| **`headless` auf macOS** | offen | macOS ist nicht Version 1 |
| **Fokuskonflikt Esc** | mittel | Esc nur weiterreichen, wenn Monaco es erwartet; sonst schließen |
| **Minecraft fängt Tasten ab** | mittel | unser Bildschirm hat den Fokus und liefert `keyPressed` selbst — die vorhandene Lösung trägt bereits |
| **IME fehlt weiterhin** | bekannt | nicht Version 1; Text ohne IME funktioniert wie heute |

**Die generelle Rückfallebene**, falls AWT sich als Ganzes als Sackgasse
erweist: Der Weg, der für die Tasten ohnehin gewählt wurde — eine native
Variante mit einfachen Parametern — lässt sich für Maus und Rad genauso
bauen. Das wären weitere rund 60 Zeilen im Patch und würde AWT vollständig aus
dem Bild nehmen. **Nicht vorsorglich bauen**, aber wissen, dass die Tür offen
ist.

## B4. FnBrowser

Klein, weil die Arbeit im Adapter steckt.

```text
- extends CefBrowserOsr (Fork)      → extends CefBrowserOsr (upstream, gepatcht)
- super(client, url, transparent, null)
+ super(client, url, transparent, null, browserSettings)
                                       mit windowless_frame_rate = 60

- sendKey(glfwKey, scanCode, mods, down)   → sendKeyEventRaw(PRESSED/RELEASED, …)
- sendChar(typed, mods)                    → sendKeyEventRaw(TYPED, mods, typed, 0, 0)
- clickMouse / moveMouse / scrollMouse     → AwtMouseEvents

onPaint, onPopupShow, onPopupSize          unverändert
```

Und ersatzlos gestrichen: der Umgebungsvariablen-Behelf
`JCEF_WINDOWLESS_FRAME_RATE` samt der Kommentare zum Sonderfall des alten
Forks. Beides war richtig für seinen Zweck und ist danach nur noch irreführend.

---

# Aufgabenliste

### Block A

**A1 — java-cef auf CEF 146 bauen**
`tools/runtime/` anlegen, `pin.properties` mit Commit-SHA und CEF-Fassung,
`build-jcef.ps1` mit Prüfungen für Pfadlänge und Python-Fassung.
*Fertig:* Zweimal aus leerem Verzeichnis gebaut, gleiche Artefaktliste,
`libcef.dll` bitgleich.
*Fallen:* 264 Zeichen sind gemessen zu lang (Grenze 260); `gsutil` stirbt unter
Python 3.13+; `clang-format` darf fehlen.

**A2 — Patch-Satz anlegen**
`0001` Sichtbarkeit (2 Zeilen), `0002` `sendKeyEventRaw` (Java + nativ, ~62
Zeilen), `0003` `clang-format` nicht fatal. Jeder mit Begründung im Kopf.
*Fertig:* `git apply --check` läuft auf frischem Checkout durch; Bau danach
erfolgreich.

**A3 — Paketpipeline**
`package-runtime.ps1`, `manifest.ps1`, Dateiliste als eigene Datei je
Plattform.
*Fertig:* Ein Aufruf erzeugt Archiv und Manifest; jede Prüfsumme stammt aus
einer Datei, keine von Hand.

**A4a — Probe: Takt und Lebenszyklus**
Pumpschleife nach Minecrafts Modell, `windowless_frame_rate = 60`.
*Fertig:* p50 16,7 ms ± 1; null `jcef_helper` nach `dispose()`.

**A4b — Probe: Tastatur-Prüfstand** ← klärt das Risiko vor dem Einbau
Referenzfolge über `sendKeyEventRaw`, Protokoll aus der Seite auslesen.
*Fertig:* Für jede Taste der Matrix steht fest, was Chromium empfangen hat;
insbesondere ist das Erweiterungs-Bit der Pfeiltasten geklärt.

### Block B3/B4

**B3a — Patch `sendKeyEventRaw`** (gehört zu A2, hier als eigene Aufgabe
geführt, weil der Prüfstand darauf wartet)
*Fertig:* Methode aufrufbar, Windows-Zweig übernimmt Upstreams Logik.

**B3b — `GlfwKeys` und `GlfwScancodes`**
Ausgeschriebene Tabelle, kein Rechnen außer bei Buchstaben und Ziffern.
*Fertig:* Jede Taste der Matrix bildet auf den erwarteten Wert ab, belegt
durch A4b.

**B3c — `AwtModifiers`**
`_DOWN_MASK`-Familie, AltGr-Sonderfall vorbereitet.
*Fertig:* Strg-, Umschalt- und Alt-Kombinationen kommen korrekt an; AltGr
löst kein Tastenkürzel aus.

**B3d — `AwtMouseEvents` und `AwtEventSource`**
Tastenzuordnung mit vertauschtem Rechts/Mitte, Rad-Vorzeichen gemessen,
peerlose Komponente als Absender.
*Fertig:* Klick, Doppelklick, Ziehen, Rad in beide Richtungen korrekt.

**B3e — Testmatrix fahren**
Automatischer Teil und Handteil in Monaco.
*Fertig:* Jede Zeile der Matrix erfüllt; `TypingBenchmark` innerhalb der
Toleranzen.

**B4 — `FnBrowser` umstellen**
Neue Basisklasse mit `CefBrowserSettings`, Adapter einhängen,
Umgebungsvariablen-Behelf entfernen.
*Fertig:* Takt 16,85 ms ± 1 ohne gesetzte Umgebungsvariable; Tippen wie heute.

### Reihenfolge

```text
A1 → A2 → A3
       ↓
A4a  (Takt läuft)
       ↓
B3a  (Patch für die Tastenvariante)
       ↓
A4b  (Prüfstand — klärt das Mapping, bevor es in den Mod geht)
       ↓
B3b → B3c → B3d
       ↓
B4 → B3e
```

**A4b vor B3b ist der Kern dieser Reihenfolge.** Das Mapping wird gemessen,
bevor es geschrieben wird — sonst wird es im Mod hergeleitet, wo ein Fehler
zwischen zwanzig anderen Ursachen liegt.
