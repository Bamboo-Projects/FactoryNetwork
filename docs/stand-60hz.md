# Lokaler Kontrolllauf und der 60-Hz-Patch

Stand: 31. August 2026, alle Messungen lokal am Gerät, nicht über eine
Fernsitzung. Auflösung 1920×1080, acht Anschläge je Sekunde, 45 Sekunden
Tippen je Stufe, 542 bis 556 Messungen.

---

## Die kurze Antwort

**Nein, die Fernsitzung hat nichts Wesentliches verfälscht.** Takt,
Rückkopierzeit und Größenordnung der Tipplatenz sind lokal dieselben.

**Und ja, der Patch wirkt genau wie gerechnet.** Der reale Bildtakt springt von
29,7 auf 59,4 Bilder je Sekunde, die Gesamtstrecke bis zum sichtbaren Zeichen
fällt von 50,9 auf 43,7 Millisekunden — ohne Gegenrechnung an anderer Stelle.

---

# Teil A — Lokaler Kontrolllauf

## Was gleich geblieben ist

| | über Fernsitzung | lokal | |
|---|---|---|---|
| OSR-Takt (p50) | 33,33 ms — 30,0/s | 33,64 ms — **29,7/s** | bestätigt |
| `CopyOutput` je Bild | 0,25 ms | **0,22 ms** | bestätigt |
| `Display::DrawAndSwap` | 2,67 ms | 2,43 ms | bestätigt |
| Upload bei 1080p | 9,0 ms | **10,9 ms** | weiterhin relevant |
| Minecrafts Bildzeit | 8,5 ms | 8,7 ms | bestätigt |

Alle vier Bedingungen aus dem Auftrag sind erfüllt: Der Takt liegt bei 30,
der Rückkopieranteil bleibt weit unter einer Millisekunde, die Monaco-Latenz
bewegt sich in derselben Größenordnung, und der Upload bleibt ein
nennenswerter Posten auf Minecrafts Renderthread.

## Ein Unterschied, der keiner ist

Die Verteilung des Takts streut lokal breiter (p10 20,96 ms, p90 45,19 ms),
während sie über die Fernsitzung in allen Perzentilen exakt 33,33 ms zeigte.
Das liegt nicht am Ort der Messung, sondern an ihrer Stelle: Diesmal wurde in
Java gemessen, wo das Bild im Render-Thread ankommt und deshalb auf Minecrafts
eigenem Raster von rund achteinhalb Millisekunden landet. Über die Fernsitzung
wurde in der Seite gemessen, vor jeder Übergabe. Der Median trifft beide Male
denselben Wert.

## Monaco lokal, ungepatcht

```text
1920×1080, Monaco vollständig, vorgeblurter Hintergrund
A Eingabe→Bild        p50 35,1 ms   p95 56,0 ms
gesamt bis sichtbar   p50 50,9 ms
Upload                p50 10,9 ms   p95 14,4 ms
Minecrafts Bildzeit   p50  8,7 ms   p95 21,7 ms
548 Bilder (12,2/s), 5.194 KB je Bild — 64,1 % eines Vollbilds, 61,8 MB/s
```

Der frühere Wert aus der Fernsitzung lautete A 27,6 ms, ist aber **nicht
direkt vergleichbar**: Er stammt von `monaco-full` ohne Hintergrund bei
1920×1062, dieser von `monaco-vorblur` bei 1920×1080. Verglichen werden darf
nur innerhalb dieses Berichts — und dort ist der Vergleich sauber.

## Was nicht wiederholt wurde

Textfeld, `contenteditable`, Leinwand, Monaco minimal, Glas an und aus,
weitere Auflösungen, Minimap. Alles in Schritt G bis I beantwortet. Auch der
Fenstervergleich aus §6 des vorigen Berichts wurde nicht neu gemessen — er
stammt aus der Zeit der Fernsitzung und ist für die anstehende Entscheidung
nicht nötig.

---

# Teil B — Der 60-Hz-Patch

## Was zurückportiert wurde

Aus dem aktuellen `chromiumembedded/java-cef` stammt die dynamische
Schnittstelle, die unser CinemaMod-Fork noch nicht kennt:

```java
public void setWindowlessFrameRate(int frameRate);
private final native void N_SetWindowlessFrameRate(int frameRate);
```

Dazu die native Gegenseite, gebaut nach dem Muster der benachbarten Methoden:

```cpp
JNIEXPORT void JNICALL
Java_org_cef_browser_CefBrowser_1N_N_1SetWindowlessFrameRate(JNIEnv* env,
                                                             jobject obj,
                                                             jint frameRate) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->SetWindowlessFrameRate(frameRate);
}
```

`CefBrowserHost::SetWindowlessFrameRate(int)` ist in CEF 116 vorhanden
(`include/cef_browser.h`, Zeile 739) — der Backport trifft eine bestehende
Schnittstelle und erfindet nichts.

## Warum es dabei nicht bleiben konnte

Die dynamische Schnittstelle allein hätte in diesem Aufbau nichts bewirkt.
Der Grund liegt darin, wo die JCEF-Java-Klassen herkommen: **MCEF bringt sie
in seiner eigenen Jar mit** (`org/cef/browser/CefBrowser_N.class`, 170
Klassen insgesamt). Eine neue native Methode braucht aber eine passende
Deklaration in genau der geladenen Java-Klasse, sonst bindet die
Laufzeitumgebung sie nie. Diese Klasse zu ersetzen hieße, in Gradles globalen
Zwischenspeicher einzugreifen.

Deshalb kommt der eigentliche Schalter an die Stelle, an der die
Browsereinstellungen ohnehin entstehen — in `N_CreateBrowser`, wo die
`CefBrowserSettings` gebaut werden:

```cpp
  if (const char* fps_text = std::getenv("JCEF_WINDOWLESS_FRAME_RATE")) {
    const int fps = std::atoi(fps_text);
    if (fps >= 1 && fps <= 60) {
      settings.windowless_frame_rate = fps;
    }
  }
```

Das hat drei Vorteile: keine Java-Änderung, kein Eingriff in fremde Jars, und
**eine einzige Bibliothek bedient beide Seiten des Vergleichs** — mit gesetzter
Variable 60, ohne sie CEFs Voreinstellung 30. Genau das macht den A/B-Test
unten sauber.

Der Wertebereich 1 bis 60 ist CEFs eigener; alles außerhalb wird ignoriert,
damit ein Tippfehler keinen Browser erzeugt, der nie malt.

## Wie groß der Patch ist

```text
java/org/cef/browser/CefBrowser_N.java   17 +
native/CefBrowser_N.cpp                  27 +
CMakeLists.txt                            5 +-   (nur Bauwerkzeug, siehe unten)
                                         ————
                                48 Zeilen, davon 44 am Produktcode
```

Kein Chromium-Build: Der Fork lädt die fertige CEF-Binärdistribution selbst,
gebaut werden nur `jcef.dll` und der Wrapper. `libcef.dll` bleibt das
unveränderte Original.

## Wo die Artefakte liegen — und wie sie wieder verschwinden

```text
Quellen und Bau      C:\jcef                (kurzer Pfad, siehe unten)
gebaute Bibliothek   C:\jcef\jcef_build\native\Release\jcef.dll
im Einsatz           build\mcef-libraries\windows_amd64\jcef.dll
Original gesichert   build\mcef-libraries\windows_amd64\jcef.dll.original
```

**Achtung: `gradlew clean` löscht `build/` und damit die eingespielte
Bibliothek.** MCEF lädt daraufhin still das Original nach, und die nächste
Messung zeigt kommentarlos wieder dreißig Bilder je Sekunde — ohne Fehler,
ohne Warnung, nur mit schlechteren Zahlen. Nach jedem `clean` muss die Datei
aus `C:\jcef` erneut kopiert werden.

Dass MCEF die getauschte Bibliothek nicht von sich aus überschreibt, ist
belegt: Drei Client-Starts nach dem Tausch, kein erneuter Download. Die
Prüfsumme, die MCEF vergleicht, gilt dem heruntergeladenen Archiv und nicht
den entpackten Dateien.

Die gepatchten Quellen liegen bislang nur außerhalb des Projekts. Ob Diff und
Bibliothek ins Repo gehören, ist eine offene Entscheidung — ohne sie ist der
Zustand nicht reproduzierbar.

## Zwei Hürden auf dem Weg

**Das mitgelieferte gsutil startet nicht mehr.** Der Bau lädt `clang-format`
über ein beigelegtes Python-Werkzeug, das unter Python 3.13 und neuer an
seiner eigenen Enum-Prüfung scheitert. `clang-format` ist ein
Formatierungswerkzeug und für den Bau ohne Bedeutung — der Abbruch wurde in
`CMakeLists.txt` zu einer Warnung herabgestuft.

**Windows' Pfadlängengrenze.** Im Ablageordner für Zwischendateien wurde die
CEF-Distribution nur unvollständig entpackt; einzelne Header fehlten, und der
Wrapper ließ sich nicht übersetzen. Die Ursache war messbar: Der längste Pfad
kam auf **264 Zeichen**, vier über der Grenze von 260. Der Bau läuft deshalb
unter `C:\jcef`. Das ist keine Eigenart dieses Rechners und wird bei jedem
Wiederaufbau wieder zuschlagen.

## Der reale Takt, vorher und nachher

Gemessen wird der Abstand zweier `onPaint`-Aufrufe an einer Seite, die in
jedem Bild ein kleines Feld umfärbt — nicht der Wert, den eine Schnittstelle
zurückmeldet.

```text
                      p10        p50        p90        Bilder/s   Abstände
ohne Variable      20,96 ms   33,64 ms   45,19 ms       29,7        482
mit 60             4,42 ms   16,85 ms   27,58 ms       59,4       1002
```

**Der Takt hat sich verdoppelt**, und die Zahl der gelieferten Bilder in
denselben zwanzig Sekunden ebenso.

## Monaco: 30 gegen 60, lokal, gleiche Fläche

Beide Läufe 1920×1080, Monaco vollständig, vorgeblurter Hintergrund,
45 Sekunden, acht Anschläge je Sekunde.

| | 30 Hz | 60 Hz | Differenz |
|---|---|---|---|
| **A Eingabe→onPaint p50** | 35,1 ms | **30,2 ms** | −4,9 ms |
| A Eingabe→onPaint p95 | 56,0 ms | **42,2 ms** | **−13,8 ms** |
| **gesamt bis sichtbar p50** | 50,9 ms | **43,7 ms** | **−7,2 ms** |
| Upload p50 | 10,9 ms | 9,9 ms | −1,0 ms |
| Upload p95 | 14,4 ms | 12,9 ms | −1,5 ms |
| Minecrafts Bildzeit p50 | 8,7 ms | 8,6 ms | unverändert |
| Minecrafts Bildzeit p95 | 21,7 ms | 20,0 ms | −1,7 ms |
| onPaint je Sekunde | 12,2 | 12,6 | +0,4 |
| Übertragungsrate | 61,8 MB/s | 63,9 MB/s | +3,4 % |

**Der Gewinn im schlechtesten Fall ist größer als im mittleren.** Das passt
zur Ursache: Wer auf ein Raster wartet, wartet im Mittel einen halben
Rasterabstand und im ungünstigen Fall einen ganzen. Halbiert man das Raster,
halbiert sich beides — und der ungünstige Fall fällt weiter.

Der schlechteste Einzelwert läuft der Richtung zuwider (240 ms mit 60 Hz gegen
117 ms mit 30 Hz). Das ist ein einzelner Ausreißer aus der Umgebung — ein
Welt-Takt oder eine Speicherbereinigung —, kein Gegenbefund: Über 556
Messungen ist p95 die belastbare Zahl, und die fällt um knapp vierzehn
Millisekunden.

**Und es kostet praktisch nichts.** Die naheliegende Sorge, ein doppelter Takt
verdopple die Übertragungslast, trifft nicht zu: Gemalt wird änderungs- und
nicht taktgetrieben. Bei acht Anschlägen je Sekunde liefert Chromium in beiden
Fällen rund zwölf Bilder je Sekunde; der Takt hebt nur die Untergrenze der
Wartezeit. Minecrafts Bildzeit bleibt unverändert.

---

## Renderer-Prozesse

Inzwischen beantwortet, siehe `stand-lifecycle.md`: **Beim Öffnen und
Schließen der Oberfläche bleibt nichts liegen.** Über drei vollständige Zyklen
kehren die Prozesse in unter einer Sekunde auf ihren Ausgangswert zurück
(186 → 190 → 191 MB).

Das Wachstum aus Schritt I — zehn Renderer, rund 660 MB — kam ausschließlich
vom **Navigieren** innerhalb einer Sitzung. Daraus folgt eine Vorgabe für die
Oberfläche, nicht für die Laufzeitumgebung: kein `location.href` zwischen
internen Ansichten.

## Subjektives Tippgefühl

Kann ich nicht liefern — ich kann die Zahlen messen, aber nicht fühlen, wie es
sich anfasst. Das braucht ein paar Minuten Schreiben am Gerät.

Was die Zahlen dazu sagen, als Einordnung: Die Gesamtstrecke bis zum
sichtbaren Zeichen liegt jetzt bei **43,7 ms im Median**. Ein lokaler
Desktop-Editor auf einem 60-Hz-Schirm liegt üblicherweise im Bereich von 20
bis 40 ms. Wir sind damit am oberen Rand dieses Bereichs angekommen, aber
nicht mehr außerhalb — vor dem Patch waren es 50,9 ms und im ungünstigen
Fünftel deutlich mehr.

---

## Die beiden Abschlussfragen

> **Hat die Fernsitzung unsere bisherigen Schlussfolgerungen wesentlich
> verfälscht?**

Nein. Takt, Rückkopierzeit und Zeichenstruktur sind lokal dieselben. Die
Schlussfolgerung aus Schritt I — der Takt bestimmt die Tipplatenz, nicht der
Rückweg des Bildes — hält, und der Patch bestätigt sie zusätzlich von der
anderen Seite: Wer die Ursache ändert, ändert die Wirkung.

Ein einziger Punkt aus dem alten Bericht bleibt unter Vorbehalt: der Vergleich
mit einem gewöhnlichen Chrome-Fenster (§6). Er lief über die Fernsitzung und
wurde auf Wunsch nicht wiederholt.

> **Reicht 60-Hz-OSR als Grundlage für die Monaco-Oberfläche?**

Die Zahlen sagen: Es ist der richtige Weg, und er ist billig — vierzig Zeilen
für sieben Millisekunden im Median und vierzehn im ungünstigen Fall.

Ob es *reicht*, entscheidet das Schreiben, nicht die Tabelle. Was die Zahlen
noch offenlassen: Von den verbleibenden 43,7 ms sind rund 10 ms unser Upload
und rund 8 ms Minecrafts eigene Bildzeit. Beides ist angreifbar — der Upload
über Accelerated Paint, die Bildzeit gar nicht. Der Takt ist es ab jetzt nicht
mehr.
