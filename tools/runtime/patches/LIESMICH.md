# Patches

## Was hier liegt

`0000-poc-*` sind die Patches des **Proof-of-Concept** gegen den
CinemaMod-Fork von java-cef mit CEF 116. Sie gehören nicht in Version 1 und
sind hier gesichert, damit der gemessene Zustand reproduzierbar bleibt.

| Datei | Zweck |
|---|---|
| `0000-poc-60hz-cef116.patch` | `setWindowlessFrameRate` nachgerüstet und die Bildrate über die Umgebungsvariable `JCEF_WINDOWLESS_FRAME_RATE` einstellbar gemacht. Damit wurde der A/B-Vergleich 30 gegen 60 Hz gefahren. |
| `0000-poc-clang-format-nicht-fatal.patch` | Der Bau lädt `clang-format` über ein beigelegtes `gsutil`, das unter Python 3.13 und neuer nicht mehr startet. Das Werkzeug ist für den Bau ohne Bedeutung; der Abbruch wird zur Warnung. |

## Wie sie angewendet wurden

```text
git clone https://github.com/CinemaMod/java-cef.git
git apply <patch>
cmake -G "Visual Studio 17 2022" -A x64 -DPROJECT_ARCH=x86_64 \
      -DCMAKE_POLICY_VERSION_MINIMUM=3.5 ..
cmake --build . --config Release --target jcef
```

**Unter einem kurzen Pfad bauen.** Im Zwischenordner erreichte der längste
Pfad der CEF-Distribution 264 Zeichen, vier über Windows' Grenze von 260 — das
Entpacken brach unbemerkt ab, und der Bau scheiterte später an fehlenden
Kopfdateien.

## Was in Version 1 daraus wird

Nichts davon wird übernommen. Version 1 baut gegen **upstream java-cef** auf
dessen eigenem Pin (CEF 146), und dort gibt es `CefBrowserSettings` mit
`windowless_frame_rate` bereits. Der Umgebungsvariablen-Behelf verschwindet
ersatzlos.

Die Patches von Version 1 heißen `0001-cefbrowserosr-public.patch`,
`0002-send-key-event-raw.patch` und `0003-clang-format-nicht-fatal.patch`;
entworfen sind sie in `docs/plan-v1-blockA-und-input.md` und
`docs/plan-input-umsetzung.md`.
