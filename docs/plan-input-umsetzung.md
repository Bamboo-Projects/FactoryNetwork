# Eingabe-Adapter: A4b, B3a–B3d, B4

Stand: 31. August 2026. Umsetzungsskizze, kein Code im Mod. Die
Grundentscheidungen sind gefallen und werden nicht berührt.

---

## Der Messwert, auf dem alles ruht

Vor jeder Zeile Entwurf stand eine Messung. `tools/runtime/probe/ScanProbe.java`
startet GLFW ohne Fenster und fragt `glfwGetKeyScancode` für 33 Tasten ab.
Ergebnis auf Windows, 31. August 2026:

```text
Taste              GLFW   Scancode      hex
A                    65         30   0x001E
1                    49          2   0x0002
ENTER               257         28   0x001C
BACKSPACE           259         14   0x000E
ESCAPE              256          1   0x0001
TAB                 258         15   0x000F
LEFT                263        331   0x014B     ← erweitert
RIGHT               262        333   0x014D     ← erweitert
UP                  265        328   0x0148     ← erweitert
DOWN                264        336   0x0150     ← erweitert
INSERT              260        338   0x0152     ← erweitert
DELETE              261        339   0x0153     ← erweitert
HOME                268        327   0x0147     ← erweitert
END                 269        335   0x014F     ← erweitert
PAGE_UP             266        329   0x0149     ← erweitert
PAGE_DOWN           267        337   0x0151     ← erweitert
LEFT_CONTROL        341         29   0x001D
RIGHT_CONTROL       345        285   0x011D     ← erweitert
LEFT_ALT            342         56   0x0038
RIGHT_ALT           346        312   0x0138     ← erweitert (AltGr)
LEFT_SHIFT          340         42   0x002A
RIGHT_SHIFT         344         54   0x0036
KP_ENTER            335        284   0x011C     ← erweitert
KP_DIVIDE           331        309   0x0135     ← erweitert
KP_MULTIPLY         332         55   0x0037
KP_8                328         72   0x0048
KP_2                322         80   0x0050
KP_0                320         82   0x0052
PRINT_SCREEN        283        311   0x0137     ← erweitert
PAUSE               284         69   0x0045
F1                  290         59   0x003B
F12                 301         88   0x0058
SPACE                32         57   0x0039
```

### Was daraus folgt

**GLFW kodiert „erweiterte Taste" als Bit 8 (0x100).** Windows kodiert sie als
Präfix `0xE0` und im lParam als Bit 24. Das sind zwei verschiedene
Darstellungen desselben Sachverhalts, und niemand rechnet sie ineinander um,
wenn wir es nicht tun.

Die Paare beweisen, warum das nicht optional ist:

```text
UP       0x0148        KP_8   0x0048     gleiche unteren acht Bit
DOWN     0x0150        KP_2   0x0050
INSERT   0x0152        KP_0   0x0052
RCTRL    0x011D        LCTRL  0x001D
KP_ENTER 0x011C        ENTER  0x001C
```

Wer das Bit verliert, bekommt für die Pfeiltaste nach oben den Ziffernblock —
`MapVirtualKey(0x48, …)` liefert `VK_NUMPAD8`, nicht `VK_UP`. Der Editor
schriebe eine Acht, statt den Cursor zu bewegen.

**Und `(scanCode << 16) | 1` aus upstream darf deshalb nicht blind übernommen
werden.** Diese Zeile ist für Tasten ohne Erweiterung richtig und für die
zehn Tasten oben falsch.

`PAUSE` mit 0x45 und ohne Bit 8 ist korrekt gemessen und trotzdem ein
bekanntes Biest: Windows meldet die Taste über ein `0xE1`-Präfix, und der
Scancode kollidiert mit der Feststelltaste für Ziffern. Niedrige Priorität,
über A4b prüfen, notfalls eine Ausnahme — nicht jetzt lösen.

---

# A4b — Der Tastatur-Prüfstand

## Warum er vor dem Adapter steht

Die Tabelle oben sagt, was GLFW liefert. Sie sagt **nicht**, was Chromium am
Ende empfängt — dazwischen liegen unser Patch, `MapVirtualKeyEx` und Chromiums
eigene Umsetzung. Der Prüfstand schließt genau diese Lücke, und zwar dort, wo
ein Fehler eine einzige Ursache haben kann.

## Wie die Events hineinkommen

Über die gepatchte Methode, direkt:

```java
browser.sendKeyEventRaw(KEY_PRESSED, mods, CHAR_UNDEFINED, 0x4B, true, VK_LEFT);
```

Kein Umweg über Minecraft, kein Umweg über CDPs `Input.dispatchKeyEvent` —
letzteres würde Chromiums eigene Eingabeverarbeitung testen und genau den Weg
überspringen, den wir prüfen wollen.

## Wie das Protokoll herauskommt

**Empfehlung für Version 1: der Debug-Port über CDP.** Nicht wegen der
Eleganz, sondern weil er in dieser Codebasis bereits dreimal funktioniert hat
(`tools/trace.mjs`, `tools/raf.mjs`, `tools/windowed.mjs`).

| | CDP über Debug-Port | eigene JS-Brücke |
|---|---|---|
| Aufwand | Port aufmachen, den es schon gibt | Nachrichtenweg, Serialisierung, Rückkanal — neuer Code |
| Rückgabe | `Runtime.evaluate` mit `returnByValue` liefert fertiges JSON | selbst bauen |
| Bewiesen | dreimal in dieser Sitzung | nein |
| Risiko | keins für einen Prüfstand | neuer ungeprüfter Code, der geprüften Code absichern soll |

Eine eigene Brücke brauchen wir später für das Produkt — aber ein Prüfstand
soll nichts absichern müssen, was er selbst mitbringt.

```java
// nach der Referenzfolge, über die Browser-Verbindung:
// Runtime.evaluate { expression: "JSON.stringify(window.protokoll)",
//                    returnByValue: true }
```

## Die Testseite

```html
<!-- tools/runtime/probe/keys.html -->
<script>
window.protokoll = [];
addEventListener('keydown', e => window.protokoll.push({
  art:'down', key:e.key, code:e.code, keyCode:e.keyCode,
  ctrl:e.ctrlKey, shift:e.shiftKey, alt:e.altKey, meta:e.metaKey}));
addEventListener('keypress', e => window.protokoll.push({
  art:'press', key:e.key, charCode:e.charCode}));
addEventListener('keyup', e => window.protokoll.push({
  art:'up', key:e.key, code:e.code, keyCode:e.keyCode}));
window.leeren = () => { window.protokoll = []; };
</script>
<textarea id="feld" autofocus style="position:fixed;inset:0"></textarea>
```

Das `<textarea>` mit Fokus ist wichtig: Ohne fokussiertes Element landen die
Ereignisse am `body`, und `keypress` bleibt aus.

## Skelett

```java
public final class KeyProbe {

    record Fall(String name, int glfwKey, Character zeichen, int mods) {}

    public static void main(String[] args) throws Exception {
        CefApp.startup(new String[] {});
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        CefApp app = CefApp.getInstance(settings);
        CefClient client = app.createClient();

        CefBrowserSettings browserSettings = new CefBrowserSettings();
        browserSettings.windowless_frame_rate = 60;

        CefBrowserOsr browser = new CefBrowserOsr(
                client, seiteAlsDateiUrl(), true, null, browserSettings) {
            @Override public void onPaint(CefBrowser b, boolean popup,
                    Rectangle[] dirty, ByteBuffer puffer, int w, int h) {
                // Der Prüfstand malt nicht — er tippt nur.
            }
        };
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(800, 600);
        browser.setFocus(true);

        // Alles Weitere im Pumpthread, wie in Minecraft.
        pumpeBis(app, () -> geladen);

        for (Fall fall : REFERENZFAELLE) {
            int glfwScancode = GLFW.glfwGetKeyScancode(fall.glfwKey());
            int low  = glfwScancode & 0xFF;
            boolean extended = (glfwScancode & 0x100) != 0;
            int vk = GlfwKeys.toAwt(fall.glfwKey());

            browser.sendKeyEventRaw(KeyEvent.KEY_PRESSED, fall.mods(),
                    KeyEvent.CHAR_UNDEFINED, low, extended, vk);
            if (fall.zeichen() != null) {
                browser.sendKeyEventRaw(KeyEvent.KEY_TYPED, fall.mods(),
                        fall.zeichen(), 0, false, 0);
            }
            browser.sendKeyEventRaw(KeyEvent.KEY_RELEASED, fall.mods(),
                    KeyEvent.CHAR_UNDEFINED, low, extended, vk);
            pumpe(app, 100);          // Chromium Zeit zum Verarbeiten geben
        }

        String json = leseProtokollUeberCdp();
        Files.writeString(Path.of("keyprobe-ergebnis.json"), json);
        vergleicheMitErwartung(json);   // gibt eine Tabelle aus, Zeile je Fall

        browser.close(true);
        pumpe(app, 1000);
        app.dispose();
    }
}
```

## Maschinenlesbare Ausgabe

```json
{
  "faelle": [
    { "name": "LEFT", "erwartet": { "code": "ArrowLeft", "keyCode": 37 },
      "empfangen": { "code": "ArrowLeft", "keyCode": 37 }, "ok": true },
    { "name": "KP_8", "erwartet": { "code": "Numpad8", "keyCode": 104 },
      "empfangen": { "code": "Numpad8", "keyCode": 104 }, "ok": true }
  ],
  "bestanden": 31, "gescheitert": 2
}
```

**`e.code` ist die wichtigste Spalte.** Sie unterscheidet `ArrowUp` von
`Numpad8`, obwohl beide dieselben unteren acht Scancode-Bit haben — genau die
Verwechslung, gegen die der ganze Aufwand geht.

---

# B3a — Der Patch `sendKeyEventRaw`

## Java-Seite

`java/org/cef/browser/CefBrowser_N.java`

```java
/**
 * Sends a key event built from primitive values instead of an AWT KeyEvent.
 *
 * The Windows implementation of sendKeyEvent(KeyEvent) derives the virtual
 * key code from KeyEvent's private "scancode" field, which is only populated
 * by the native AWT code. Embedders that synthesise events from another input
 * source (GLFW, SDL, a game engine) cannot fill it, and every key would arrive
 * with windows_key_code 0.
 *
 * @param type      KeyEvent.KEY_PRESSED, KEY_RELEASED or KEY_TYPED
 * @param modifiers AWT InputEvent *_DOWN_MASK values, not the platform's
 * @param keyChar   the character for KEY_TYPED, CHAR_UNDEFINED otherwise
 * @param scancode  the platform scan code, low byte only on Windows
 * @param extended  Windows extended-key flag; ignored elsewhere
 * @param keyCode   KeyEvent.VK_* — used on Linux and macOS
 */
public void sendKeyEventRaw(int type, int modifiers, char keyChar,
                            int scancode, boolean extended, int keyCode) {
    try {
        N_SendKeyEventRaw(type, modifiers, keyChar, scancode, extended, keyCode);
    } catch (UnsatisfiedLinkError ule) {
        ule.printStackTrace();
    }
}

private final native void N_SendKeyEventRaw(int type, int modifiers,
        char keyChar, int scancode, boolean extended, int keyCode);
```

### Die vier Festlegungen

**Sichtbarkeit: `public`.** Nicht `protected` wie die AWT-Geschwister. Der
Grund ist der Zweck: Die Methode existiert für Einbetter, die keine
AWT-Ereignisse haben. Sie hinter `protected` zu verstecken würde erzwingen,
was sie gerade vermeiden soll. `CefBrowserOsr` erbt sie damit ohne Zutun, und
`FnBrowser` kann sie direkt rufen.

**`type`: die AWT-Konstanten.** `KeyEvent.KEY_PRESSED` (401),
`KEY_RELEASED` (402), `KEY_TYPED` (400). Keine eigenen Zahlen — der native
Code vergleicht bereits gegen diese Werte, und zwei Nummernkreise für dieselbe
Sache sind eine Fehlerquelle ohne Gegenwert.

**`modifiers`: AWT-`_DOWN_MASK`.** Der native Code reicht sie an
`GetCefModifiers()` weiter, und die Funktion erwartet genau diese Zahlen.
Gegen die Verwechslung mit GLFW-Masken helfen drei Dinge: der Name des
Parameters, die Dokumentation oben — und vor allem, dass auf der Aufruferseite
**nur** `AwtModifiers` diese Zahlen erzeugt und `FnBrowser` nie eine rohe
GLFW-Maske durchreicht. Die Trennung ist im Typsystem nicht abbildbar (beides
sind `int`), also muss sie an genau einer Stelle im Code sichtbar sein.

**`extended` als eigener Parameter, nicht als Bit im Scancode.** Damit bleibt
der Patch upstream-nah: Er kennt Windows' Konvention, aber nichts von GLFW.
Die Umrechnung von Bit 8 nach `extended` gehört auf unsere Seite, in
`GlfwScancodes` — sonst wandert GLFW-Wissen in einen Patch, der so klein und
so allgemein wie möglich bleiben soll.

## Native Seite

`native/CefBrowser_N.cpp`, neben `Java_..._N_1SendKeyEvent`.

```cpp
JNIEXPORT void JNICALL
Java_org_cef_browser_CefBrowser_1N_N_1SendKeyEventRaw(
    JNIEnv* env, jobject obj, jint type, jint modifiers, jchar key_char,
    jint scancode, jboolean extended, jint key_code) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);

  CefKeyEvent cef_event;
  cef_event.modifiers = GetCefModifiers(env, ..., modifiers);

#if defined(OS_WIN)
  if (type == KEY_TYPED) {
    cef_event.type = KEYEVENT_CHAR;
    cef_event.windows_key_code = key_char;
    // Kein Scancode: der Windows-Zweig von upstream setzt hier auch keinen.
  } else {
    cef_event.type = (type == KEY_PRESSED) ? KEYEVENT_RAWKEYDOWN : KEYEVENT_KEYUP;
    cef_event.windows_key_code = VkFromScancode(scancode, extended);
    // lParam-Form: Wiederholungszähler 1, Scancode ab Bit 16,
    // Erweiterungsbit auf 24. Upstream setzt Bit 24 nicht — es hat dort
    // keinen Absender, der es kennen könnte.
    cef_event.native_key_code =
        1 | (scancode << 16) | (extended ? (1 << 24) : 0);
  }
  cef_event.character = key_char;
  cef_event.unmodified_character = key_char;
#else
  // Linux und macOS: vorerst den vorhandenen Weg über key_code nehmen,
  // wie sendKeyEvent(KeyEvent) es dort tut. Nicht Teil von Version 1.
#endif

  browser->GetHost()->SendKeyEvent(cef_event);
}
```

### `VkFromScancode` — die Stelle, die nicht blind kopiert werden darf

```cpp
static BYTE VkFromScancode(int scancode, bool extended) {
  // 1. Die Tasten, bei denen der Scancode allein mehrdeutig ist.
  //    0x48 ist Pfeil-hoch UND Ziffernblock-8; nur das Erweiterungsbit
  //    trennt sie, und MapVirtualKey kennt es nicht.
  if (extended) {
    switch (scancode) {
      case 0x48: return VK_UP;      case 0x50: return VK_DOWN;
      case 0x4B: return VK_LEFT;    case 0x4D: return VK_RIGHT;
      case 0x47: return VK_HOME;    case 0x4F: return VK_END;
      case 0x49: return VK_PRIOR;   case 0x51: return VK_NEXT;
      case 0x52: return VK_INSERT;  case 0x53: return VK_DELETE;
      case 0x1C: return VK_RETURN;  // Ziffernblock-Eingabe
      case 0x35: return VK_DIVIDE;
      case 0x1D: return VK_CONTROL; // unspezifisch, siehe unten
      case 0x38: return VK_MENU;
    }
  }
  // 2. Alles Übrige über die Tabelle des Systems.
  return LOBYTE(MapVirtualKeyEx(scancode, MAPVK_VSC_TO_VK_EX, nullptr));
}
```

**Warum die Tabelle und nicht nur `MapVirtualKeyEx`:** Die Funktion versteht
zwar ein `0xE0`-Präfix im oberen Byte, aber ihr Verhalten bei erweiterten
Tasten unterscheidet sich zwischen Windows-Fassungen, und für die Numpad-
Kollision hängt das Ergebnis am Layout. Vierzehn ausgeschriebene Zeilen
kosten nichts und machen das Verhalten unabhängig von der Systemfassung.
**A4b prüft die Tabelle** — dafür existiert der Prüfstand.

**Warum `VK_CONTROL` statt `VK_RCONTROL`:** Chromium erwartet in
`windows_key_code` den unspezifischen virtuellen Code; die Unterscheidung
zwischen links und rechts trifft es über das Erweiterungsbit im
`native_key_code` und über die Modifikatorflaggen. Wer hier `VK_RCONTROL`
einträgt, bekommt eine Taste, die Chromium nicht als Steuerung erkennt.

**Was aus upstream übernommen werden kann:** `GetCefModifiers`, die
Typzuordnung, der Aufruf von `SendKeyEvent`. **Was nicht:**
`(scanCode << 16) | 1` ohne Erweiterungsbit, und die Annahme, dass
`MapVirtualKey` allein reicht.

---

# B3b — `GlfwKeys` und `GlfwScancodes`

## `GlfwScancodes`

Die kleinere und wichtigere der beiden.

```java
/**
 * Rechnet GLFWs Scancode in die Form um, die Windows erwartet.
 *
 * <p>Gemessen am 31. August 2026 mit {@code tools/runtime/probe/ScanProbe}:
 * GLFW setzt für erweiterte Tasten Bit 8; Windows kennt stattdessen ein
 * Präfix und ein eigenes Bit im lParam. Die Umrechnung steht hier und nicht
 * im java-cef-Patch, damit der Patch nichts von GLFW wissen muss.
 */
public final class GlfwScancodes {

    /** Der untere Teil — das, was Windows als Scancode versteht. */
    public static int base(int glfwScancode) {
        return glfwScancode & 0xFF;
    }

    /** Ob Windows die Taste als erweitert führt. */
    public static boolean extended(int glfwScancode) {
        return (glfwScancode & 0x100) != 0;
    }
}
```

**Woher der Scancode kommt: aus `glfwGetKeyScancode(glfwKey)`, nicht aus dem
Ereignis.** Minecraft reicht in `keyPressed(key, scancode, mods)` zwar einen
Scancode durch, aber er stammt aus demselben GLFW und ist derselbe Wert. Die
Abfrage über den Tastencode ist die verlässlichere Quelle, weil sie auch dort
funktioniert, wo wir kein Ereignis haben — im Prüfstand etwa.

**Eine Ausnahmetabelle brauchen wir auf der Java-Seite nicht.** Die Messung
zeigt eine durchgängige Regel (Bit 8), keine Ausreißer. Die einzige
Unregelmäßigkeit ist `PAUSE`, und die betrifft die VK-Zuordnung im nativen
Teil, nicht die Umrechnung hier.

## `GlfwKeys`

```java
public static int toAwt(int glfwKey) { ... }
```

**Direkt gerechnet werden darf genau zweierlei**, weil beide Zählungen dort
den ASCII-Werten folgen:

```java
if (glfwKey >= GLFW_KEY_A && glfwKey <= GLFW_KEY_Z) {
    return KeyEvent.VK_A + (glfwKey - GLFW_KEY_A);
}
if (glfwKey >= GLFW_KEY_0 && glfwKey <= GLFW_KEY_9) {
    return KeyEvent.VK_0 + (glfwKey - GLFW_KEY_0);
}
```

**Alles andere in eine ausgeschriebene Tabelle.** Auch die Funktionstasten,
die verlockend gleichmäßig aussehen: GLFW kennt F1 bis F25, AWT nur VK_F1 bis
VK_F24. Eine Rechnung, die für vierundzwanzig Fälle stimmt und beim
fünfundzwanzigsten einen unsinnigen Wert liefert, ist schlechter als eine
Tabelle.

```text
ENTER, ESCAPE, TAB, BACKSPACE, DELETE, INSERT
LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN
F1..F24                    (F25 → VK_UNDEFINED)
LEFT/RIGHT_SHIFT           → VK_SHIFT
LEFT/RIGHT_CONTROL         → VK_CONTROL
LEFT/RIGHT_ALT             → VK_ALT
LEFT/RIGHT_SUPER           → VK_WINDOWS
KP_0..KP_9                 → VK_NUMPAD0..VK_NUMPAD9
KP_DECIMAL/DIVIDE/MULTIPLY/SUBTRACT/ADD/ENTER/EQUAL
CAPS_LOCK, NUM_LOCK, SCROLL_LOCK, PRINT_SCREEN, PAUSE, MENU
```

**Die OEM-Tasten bleiben absichtlich draußen.** Punkt, Komma, Bindestrich,
Anführungszeichen, eckige Klammern und die beiden Tasten, die es nur auf
manchen Layouts gibt — bei ihnen hängt die Bedeutung am Tastaturlayout, und
GLFW meldet sie nach US-Belegung. Ein deutsches `Ö` käme als
`GLFW_KEY_SEMICOLON` an.

Das ist folgenlos, solange man es weiß: **Für Text zählt der Weg über
`charTyped`**, der das fertige Zeichen vom Betriebssystem bekommt. Der
Tastencode wird auf Windows ohnehin nicht gelesen — dort kommt er aus dem
Scancode, und der ist layoutunabhängig. Die Tabelle dient also den späteren
Linux- und macOS-Zweigen und der Lesbarkeit, nicht der Texteingabe.

**Unbekannte Tasten:** `VK_UNDEFINED` zurückgeben und das Ereignis trotzdem
schicken. Der Scancode trägt die Information; ein fehlender Tastencode ist auf
Windows folgenlos. Kein Wegwerfen — eine Taste, die nichts tut, ist schwerer zu
finden als eine, die falsch abgebildet ist.

---

# B3c — `AwtModifiers`

```java
public static int fromGlfw(int glfwMods) {
    int awt = 0;
    if ((glfwMods & GLFW_MOD_SHIFT)   != 0) awt |= InputEvent.SHIFT_DOWN_MASK;
    if ((glfwMods & GLFW_MOD_CONTROL) != 0) awt |= InputEvent.CTRL_DOWN_MASK;
    if ((glfwMods & GLFW_MOD_ALT)     != 0) awt |= InputEvent.ALT_DOWN_MASK;
    if ((glfwMods & GLFW_MOD_SUPER)   != 0) awt |= InputEvent.META_DOWN_MASK;
    return awt;
}
```

**Die `_DOWN_MASK`-Familie, nicht die alten Konstanten.** `SHIFT_MASK` und
`SHIFT_DOWN_MASK` haben verschiedene Werte, sehen im Code fast gleich aus, und
`getModifiersEx` liefert die zweiten. Eine Verwechslung fällt nicht als Fehler
auf, sondern als Tastenkürzel, das nicht auslöst.

**Feststelltaste und Ziffernblock-Feststellung** (`GLFW_MOD_CAPS_LOCK`,
`GLFW_MOD_NUM_LOCK`) werden **nicht** übertragen. AWT kennt dafür keine
`_DOWN_MASK`, und Chromium leitet die Wirkung ohnehin aus dem gelieferten
Zeichen ab. Ein Großbuchstabe kommt als Großbuchstabe über `charTyped`.

## Der AltGr-Fall

**Das Problem, genau benannt:** GLFW meldet AltGr unter Windows als
`GLFW_MOD_CONTROL | GLFW_MOD_ALT`. Die Messung stützt das von der anderen
Seite: `RIGHT_ALT` hat Scancode `0x0138` — Windows schickt für AltGr sowohl
ein linkes Steuerungs- als auch ein rechtes Alt-Ereignis.

Für den Text ist das folgenlos: Das fertige Zeichen kommt über `charTyped`
und geht als KEY_TYPED durch, wo nur `keyChar` zählt. **Deshalb funktionieren
`@`, `€`, `\`, `~`, `|` heute.**

Das Risiko liegt beim begleitenden KEY_PRESSED: Es trägt Strg und Alt, und
Monaco könnte es als Tastenkürzel deuten und die Eingabe abfangen.

### Strategie

```java
/**
 * Ob diese Kombination wahrscheinlich AltGr ist und kein Tastenkürzel.
 *
 * <p>Windows kann beides nicht unterscheiden — es schickt für AltGr
 * dieselben Flaggen wie für Strg+Alt. Unterscheidbar ist nur die Wirkung:
 * AltGr erzeugt ein Zeichen, Strg+Alt nicht.
 */
static boolean vermutlichAltGr(int glfwMods, boolean folgtEinZeichen) {
    boolean beide = (glfwMods & GLFW_MOD_CONTROL) != 0
                 && (glfwMods & GLFW_MOD_ALT) != 0;
    return beide && folgtEinZeichen;
}
```

**Die Umsetzung ist die Reihenfolge.** Minecraft ruft erst `keyPressed`, dann
`charTyped` — wenn überhaupt eins kommt. Zum Zeitpunkt des KEY_PRESSED wissen
wir also noch nicht, ob ein Zeichen folgt.

Zwei Wege, und der zweite ist der bessere:

1. **KEY_PRESSED verzögern**, bis feststeht, ob `charTyped` folgt. Kostet
   Latenz auf jedem Tastendruck — für einen Editor der falsche Tausch.
2. **KEY_PRESSED sofort schicken, aber bei Strg+Alt ohne die beiden
   Modifikatoren.** Ein echtes Strg+Alt-Kürzel verliert dadurch seine Wirkung;
   dafür funktioniert jedes AltGr-Zeichen.

**Empfehlung: Weg 2, mit einer Einschränkung.** Strg+Alt-Kürzel sind in Monaco
selten (die Standardbelegung nutzt Strg, Umschalt und deren Kombinationen);
AltGr-Zeichen sind auf einem deutschen Layout in jedem zweiten Programmtext.
Der Tausch ist eindeutig.

Die Einschränkung: Nur wenn **rechtes** Alt anliegt. Linkes Alt mit Strg ist
nie AltGr, und die Messung liefert die Unterscheidung — `RIGHT_ALT` hat einen
eigenen Scancode (`0x0138` gegen `0x0038`). Damit bleiben Strg+linkes Alt
Kürzel unangetastet.

**Das ist eine Vermutung mit Messweg**, kein Beschluss: `keyPressed` bekommt
den Scancode mit, also lässt sich rechts von links unterscheiden. Ob GLFW bei
AltGr tatsächlich das rechte Alt meldet und nicht ein synthetisches linkes
Strg zusätzlich, ist ein Testfall in A4b und in der Handmatrix.

---

# B3d — Maus und Rad über AWT

## Tastenzuordnung

```java
static int toAwtButton(int glfwButton) {
    return switch (glfwButton) {
        case GLFW_MOUSE_BUTTON_LEFT   -> MouseEvent.BUTTON1;   // 0 → 1
        case GLFW_MOUSE_BUTTON_RIGHT  -> MouseEvent.BUTTON3;   // 1 → 3
        case GLFW_MOUSE_BUTTON_MIDDLE -> MouseEvent.BUTTON2;   // 2 → 2
        default -> MouseEvent.NOBUTTON;
    };
}
```

**Die Vertauschung ist der Kern.** GLFW zählt nach Reihenfolge (links, rechts,
Mitte), AWT nach Bedeutung (links, Mitte, rechts). Ein direkter Durchgriff
vertauscht rechte und mittlere Taste — und ein Rechtsklick, der als
Mittelklick ankommt, öffnet kein Kontextmenü, sondern fügt in manchen
Anwendungen die Auswahl ein.

## Die Ereignisse

| Anlass | AWT |
|---|---|
| Taste herunter | `MOUSE_PRESSED` |
| Taste herauf | `MOUSE_RELEASED` |
| Bewegung, keine Taste gehalten | `MOUSE_MOVED` |
| Bewegung, Taste gehalten | `MOUSE_DRAGGED` |
| Zeiger verlässt die Fläche | `MOUSE_EXITED` |

**Kein `MOUSE_CLICKED`.** Chromium bildet den Klick aus Herunter und Herauf;
ein zusätzliches Ereignis wäre ein zweiter Klick. In Monaco hieße das: Jeder
Klick setzt den Cursor zweimal, und ein Doppelklick zur Wortauswahl würde als
Dreifachklick zur Zeilenauswahl ankommen.

**`MOUSE_DRAGGED` statt `MOUSE_MOVED`, sobald eine Taste gehalten wird** — das
ist die Grundlage der Textauswahl. Unser `MouseButtons` weiß bereits, welche
Tasten unten sind, und liefert die Unterscheidung.

**Klickzähler:** Über den Konstruktorparameter, aus unserem vorhandenen
`ClickCounter`. Chromium erkennt einen Doppelklick an nichts anderem.

**Koordinaten** bleiben Browser-Pixel; `BrowserView` rechnet sie bereits so
aus, und an dieser Stelle ändert sich nichts.

## Das Rad

```java
new MouseWheelEvent(AwtEventSource.QUELLE, MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(), modifikatoren, x, y,
        0,                                   // clickCount
        false,                               // popupTrigger
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        einheiten,                           // scrollAmount
        drehung);                            // wheelRotation
```

Der native Code liest `getScrollType`, `getWheelRotation` und bei
`WHEEL_UNIT_SCROLL` zusätzlich `getUnitsToScroll`, das das Delta überschreibt.
`getUnitsToScroll` ist `scrollAmount * wheelRotation` — beide Werte müssen
also stimmen, nicht nur einer.

**Das Vorzeichen wird gemessen, nicht angenommen.** GLFW meldet beim Scrollen
nach oben ein positives Delta; AWT führt eine Bewegung nach oben als
**negatives** `wheelRotation`. Die Wahrscheinlichkeit, dass es zunächst
verkehrt herum läuft, ist hoch — und es ist der am schnellsten bemerkte
Fehler.

**Messweg:** eine Seite mit einem hohen, scrollbaren Bereich, ein Radereignis
in eine Richtung, `window.scrollY` über CDP auslesen. Das Vorzeichen steht
danach fest und kommt als Kommentar an die Umrechnung.

## `AwtEventSource`

```java
final class AwtEventSource {
    /**
     * Ein Absender für AWT-Mausereignisse, der nie gezeigt wird.
     *
     * <p><b>Warum kein Canvas und kein Frame.</b> Beide ziehen bei ihrer
     * Erzeugung das Toolkit heran; auf einem headless gesetzten Stapel wirft
     * das. Eine unmittelbare Unterklasse von Component tut es nicht — sie
     * bekommt keinen Peer und braucht keinen, weil niemand sie zeichnet.
     *
     * <p><b>Warum überhaupt eine.</b> Die AWT-Konstruktoren werfen bei
     * {@code null} als Quelle eine IllegalArgumentException. Gelesen wird sie
     * vom nativen Teil nicht — sie muss nur existieren.
     */
    static final Component QUELLE = new Component() {};
}
```

**Zu `java.awt.headless`:** Wir setzen es **nicht**. Eine Mod, die eine globale
Systemeigenschaft verändert, kann anderen Mods den Boden wegziehen. Unter
Windows — der einzigen Plattform von Version 1 — setzt Minecraft es nicht, und
eine peerlose Komponente ist ohnehin unabhängig davon. Auf macOS setzt
Minecraft es auf `true`; ob die peerlose Komponente auch dort trägt, ist zu
prüfen, wenn macOS drankommt, und macOS ist nicht Version 1.

**Fallback, falls AWT bei der Maus doch klemmt:** `sendMouseEventRaw` und
`sendMouseWheelEventRaw` nach demselben Muster wie bei den Tasten. Rund 60
weitere Zeilen im Patch, und AWT wäre vollständig aus dem Bild.
**Nicht vorsorglich bauen** — aber die Tür steht offen, und der Weg ist
erprobt, sobald `sendKeyEventRaw` steht.

---

# B4 — `FnBrowser` umstellen

## Die Änderungen

```text
Klasse
- extends CefBrowserOsr (CinemaMod-Fork)
+ extends CefBrowserOsr (upstream, Patch 1)

Konstruktor
- super(client, url, transparent, null)
+ CefBrowserSettings s = new CefBrowserSettings();
+ s.windowless_frame_rate = 60;
+ super(client, url, transparent, null, s);

Tasten
- sendKeyEvent(new CefKeyEvent(KEY_PRESS, glfwKey, (char) glfwKey, mods))
+ sendKeyEventRaw(KEY_PRESSED, AwtModifiers.fromGlfw(mods),
+                 CHAR_UNDEFINED, GlfwScancodes.base(sc),
+                 GlfwScancodes.extended(sc), GlfwKeys.toAwt(glfwKey))

Zeichen
- sendKeyEvent(new CefKeyEvent(KEY_TYPE, typed, typed, mods))
+ sendKeyEventRaw(KEY_TYPED, AwtModifiers.fromGlfw(mods), typed, 0, false, 0)

Maus
- sendMouseEvent(new CefMouseEvent(...))
+ sendMouseEvent(AwtMouseEvents.press(x, y, button, clicks, mods))

Rad
- sendMouseWheelEvent(new CefMouseWheelEvent(...))
+ sendMouseWheelEvent(AwtMouseEvents.wheel(x, y, delta, mods))

unverändert
  onPaint, onPopupShow, onPopupSize, onCursorChange
```

## Was an Kommentaren verschwindet

**Der ganze Absatz über den Sonderfall des Forks** in `sendKey`:

> „Das Zeichen ist hier keins. Der native Teil dieser JCEF-Fassung liest bei
> Druck und Loslassen aus `keyChar` den GLFW-Tastencode … Es sieht falsch aus
> und ist richtig."

Er war richtig für den Fork und wird nach dem Umzug irreführend. **An seine
Stelle gehört der neue Sachverhalt**, der genauso wenig selbsterklärend ist:
dass der Scancode getrennt in Basis und Erweiterungsbit geht, und warum.

Ebenso verschwinden: die Erwähnung von `JCEF_WINDOWLESS_FRAME_RATE` samt der
Umgebungsvariablen-Prüfung im nativen Patch, und der Kommentar in
`clickMouse`, der GLFW_PRESS/RELEASE als erste Konstruktorstellung erklärt —
AWT nummeriert anders.

## Wie `onPaint` weiter im Renderthread ankommt

**Das hängt nicht an `FnBrowser`, sondern an der Nachrichtenschleife.** Solange
`doMessageLoopWork` aus `GameRenderer.render` gerufen wird — dem Mixin, das
Version 1 als einziges übernimmt —, kommt `onPaint` dort an. Die Klasse ändert
daran nichts, und der Umbau darf daran nichts ändern.

**Absicherung im Prüfstand:** A4a bildet genau dieses Modell nach. Käme
`onPaint` bei upstream aus einem anderen Thread, fiele es dort auf — vor dem
Mod, wo der Cleanup-Vertrag daran hängt.

## Wie die Texturfreigabe abgesichert bleibt

Unverändert, und die Begründung gilt weiter:

```java
closed = true;                 // zuerst
browser.close(true);           // asynchron
texture.close();               // sofort danach
```

Sicher ist das, weil `frame()` als Erstes `closed` prüft und `onPaint` im
selben Thread ankommt wie `close()`. **Beide Bedingungen sind vom Umbau
betroffen** — die erste nicht, die zweite über die Schleife. Deshalb steht
sie in der Abnahme von A4a und nicht nur im Cleanup-Vertrag.

---

# Testmatrix

## Automatisch

| Test | Erwartung |
|---|---|
| A4b `abcXYZ012` | `keypress` je Zeichen, `charCode` passend, keine Dopplung |
| A4b `äöüß@€\|~\\` | dieselben Zeichen; `@ € \| ~ \\` ohne ausgelöstes Kürzel |
| A4b Pfeile | `code` = `ArrowUp/Down/Left/Right`, **nicht** `Numpad*` |
| A4b Ziffernblock 8/2/0 | `code` = `Numpad8/2/0`, **nicht** `Arrow*` |
| A4b Pos1/Ende/Bild auf/ab | `code` = `Home/End/PageUp/PageDown` |
| A4b Einfg/Entf | `code` = `Insert/Delete` |
| A4b Eingabe / Ziffernblock-Eingabe | `NumpadEnter` unterscheidbar von `Enter` |
| A4b rechtes/linkes Strg | beide `ctrlKey=true`, `code` unterscheidet sie |
| A4b Strg+A/C/V/S/F | `keydown` mit `ctrlKey`, **kein** `keypress` |
| A4b Umschalt+Pfeil | `shiftKey=true` am Pfeilereignis |
| A4b Rücktaste/Entf/Esc | `keydown`, kein `keypress` |
| Rad hoch / runter | `window.scrollY` bewegt sich in die erwartete Richtung |
| Ziehen über Text | Auswahl entsteht, `clickCount` bleibt 1 |
| Doppelklick | `clickCount = 2`, Wort ausgewählt |
| `TypingBenchmark` A p50 / p95 | 30,2 ms ± 3 / 42,2 ms ± 5 |
| `ProbeBenchmark` Takt p50 | 16,85 ms ± 1 |
| `LifecycleBenchmark` | 186 → 190 → 191 MB ± 15 |
| harter Abbruch | **0** `jcef_helper` |

## Von Hand in Monaco

| Prüfung | Erwartung |
|---|---|
| Fließtext tippen | zeichengenau, keine Dopplungen, keine Verluste |
| Deutsches Layout `äöüß` | erscheinen korrekt |
| AltGr `@ € \\ ~ \|` | erscheinen; **kein** Kürzel löst aus |
| Strg+C / Strg+V | Zwischenablage in beide Richtungen |
| Strg+F | Suchfeld öffnet, Eingabe landet darin |
| Strg+S | erreicht **Monaco**, nicht Minecraft |
| Mehrfachcursor (Alt+Klick) | zweiter Cursor entsteht |
| IntelliSense | Liste öffnet, Pfeile navigieren, Eingabe übernimmt |
| Schweben | Hinweisfenster erscheint |
| Esc | schließt die Liste; **zweites** Esc schließt die Oberfläche |
| Umschalt+Pfeil | erweitert die Auswahl |
| Pos1 / Ende | Zeilenanfang und -ende |
| Bild auf / ab | seitenweise |
| Scrollrichtung | Rad nach oben bewegt den Text nach oben |
| Doppelklick | Wortauswahl, nicht Zeilenauswahl |

---

# Risiken und Rückfallebenen

| Risiko | Stand | Rückfall |
|---|---|---|
| **Erweiterungsbit** | **gemessen und gelöst** — Bit 8 bei GLFW, Bit 24 im lParam | A4b prüft jede Taste einzeln |
| **VK-Zuordnung erweiterter Tasten** | mittel — `MapVirtualKeyEx` verhält sich je nach Systemfassung anders | explizite Tabelle für die zehn Fälle, von A4b geprüft |
| **AltGr als Strg+Alt** | mittel | Modifikatoren am KEY_PRESSED weglassen, wenn **rechtes** Alt mit Strg anliegt |
| **Rad-Vorzeichen** | hoch, aber sofort sichtbar | messen, festschreiben, Test in der Matrix |
| **AWT-Maus unter NeoForge** | niedrig — peerlose Komponente | `sendMouseEventRaw` nach demselben Muster |
| **`headless` auf macOS** | offen | nicht Version 1 |
| **Kürzelkonflikt mit Minecraft** | mittel — besonders Esc | unser Bildschirm hat den Fokus; Esc nur weiterreichen, wenn Monaco es erwartet |
| **`org.cef` doppelt** | bekannt | Unverträglichkeit erklären, klare Meldung statt Absturz |
| **IME fehlt** | bekannt | nicht Version 1; Text ohne IME wie heute |
| **Linux/macOS-Verhalten von `sendKeyEventRaw`** | offen | Zweige vorerst wie `sendKeyEvent(KeyEvent)`; erst prüfen, wenn die Plattform drankommt |
| **PAUSE** | Sonderfall | niedrige Priorität, A4b prüft, notfalls Ausnahme |

---

# Aufgabenliste

### A4b — Tastatur-Prüfstand

**Ziel:** Vor jeder Zeile Adaptercode belegen, was Chromium tatsächlich
empfängt.
**Dateien:** `tools/runtime/probe/KeyProbe.java`,
`tools/runtime/probe/keys.html`, `tools/runtime/probe/ScanProbe.java`
(vorhanden)
**Abnahme:** JSON mit einer Zeile je Referenzfall; Pfeiltasten liefern
`ArrowUp` und nicht `Numpad8`; Ziffernblock umgekehrt; `bestanden` deckt alle
Fälle der Matrix.
**Risiken:** CDP-Verbindung zum Prüfstand; Fokus im `<textarea>`, sonst
bleibt `keypress` aus.

### B3a — Patch `sendKeyEventRaw`

**Ziel:** Tastenereignisse ohne AWT-Objekt, mit Scancode und Erweiterungsbit.
**Dateien:** `patches/0002-send-key-event-raw.patch` über
`java/org/cef/browser/CefBrowser_N.java` und `native/CefBrowser_N.cpp`
**Abnahme:** Methode aufrufbar; `VkFromScancode` liefert für die zehn
erweiterten Tasten die Tabellenwerte; `native_key_code` trägt Bit 24.
**Risiken:** `MapVirtualKeyEx` je nach Systemfassung; nicht blind
`(scanCode << 16) | 1` übernehmen.

### B3b — `GlfwKeys` und `GlfwScancodes`

**Ziel:** GLFW-Werte in die Form bringen, die der Patch erwartet.
**Dateien:** `web/input/GlfwKeys.java`, `web/input/GlfwScancodes.java`
**Abnahme:** `base`/`extended` stimmen für alle 33 gemessenen Tasten;
`toAwt` deckt die Tabelle ab, unbekannte Tasten liefern `VK_UNDEFINED` und
werden trotzdem geschickt.
**Risiken:** OEM-Tasten sind layoutabhängig — bewusst ausgelassen, dokumentiert.

### B3c — `AwtModifiers`

**Ziel:** GLFW-Masken in AWT-`_DOWN_MASK`, plus AltGr-Behandlung.
**Dateien:** `web/input/AwtModifiers.java`
**Abnahme:** Strg-, Umschalt-, Alt-Kombinationen kommen an; AltGr-Zeichen
erscheinen, ohne ein Kürzel auszulösen; Strg+**linkes** Alt bleibt ein Kürzel.
**Risiken:** `SHIFT_MASK` gegen `SHIFT_DOWN_MASK` — die stille Verwechslung.

### B3d — `AwtMouseEvents` und `AwtEventSource`

**Ziel:** Maus und Rad über AWT, mit gemessenem Vorzeichen.
**Dateien:** `web/input/AwtMouseEvents.java`, `web/input/AwtEventSource.java`
**Abnahme:** Rechtsklick kommt als BUTTON3; Doppelklick wählt ein Wort, keine
Zeile; Rad bewegt in die erwartete Richtung; Ziehen erzeugt eine Auswahl.
**Risiken:** Rechts/Mitte vertauscht; Rad-Vorzeichen; peerlose Komponente.

### B4 — `FnBrowser` umstellen

**Ziel:** Neue Basisklasse, neue Eingabewege, alte Sonderfälle entfernen.
**Dateien:** `web/mcef/FnBrowser.java` → `web/runtime/FnBrowser.java`
**Abnahme:** Takt 16,85 ms ± 1 **ohne** gesetzte Umgebungsvariable; Tippen wie
heute; `onPaint` weiterhin im Renderthread (über A4a belegt).
**Risiken:** Der alte Kommentar zum Fork-Sonderfall muss weg, sonst führt er
den nächsten Leser in die Irre.

### B3e — Testmatrix fahren

**Ziel:** Beleg, dass Version 1 die heutigen Zahlen reproduziert.
**Dateien:** keine — Läufe und Protokoll
**Abnahme:** Jede Zeile beider Matrizen erfüllt.
**Risiken:** Der Handteil braucht ein deutsches Layout am Gerät.

---

## Die Reihenfolge

```text
A1 → A2 → A3 → A4a → B3a → A4b → B3b → B3c → B3d → B4 → B3e
```

**Warum A4b vor B3b:** `GlfwKeys` und `GlfwScancodes` sind
Übersetzungstabellen — ihr einziger Zweck ist, Werte zu liefern, die Chromium
richtig versteht. Ob eine Tabelle das tut, weiß man erst, wenn man Chromium
gefragt hat.

Schriebe man sie zuerst, hätte man beim ersten Fehler drei Verdächtige
gleichzeitig: die Tabelle, den nativen Patch und die Verkabelung im Mod — und
dazu Minecraft, dessen Fokus, dessen Tastenabfang und Monacos eigene
Belegungen. Ein falsch abgebildeter Pfeil sähe genauso aus wie ein
abgefangenes Ereignis.

Der Prüfstand hat nur einen Verdächtigen. Er tippt über dieselbe Methode, die
später der Mod benutzt, und liest aus der Seite ab, was ankam. **Damit wird
die Tabelle abgeschrieben, statt hergeleitet** — und die zehn erweiterten
Tasten, die wahrscheinlichste Fehlerquelle des ganzen Adapters, sind geklärt,
bevor die erste Zeile davon im Mod steht.
