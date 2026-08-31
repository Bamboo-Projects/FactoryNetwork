# Was steckt in A? Stand nach Schritt H

**31.08.** Gemessen bei 1920 × 1062 mit fünf Vergleichsseiten und einer Uhr
innerhalb der Seite.

**Die Antwort steht in Abschnitt 12.** Sie lautet: **hauptsächlich
Chromium/OSR, nicht Monaco.** Ein nacktes `<textarea>` kostet bereits
einundzwanzig Millisekunden.

---

## Die Messung auf einen Blick

Alle Varianten: derselbe Tippstrom, acht Zeichen je Sekunde, fünfundvierzig
Sekunden, ~550 Messungen je Zeile. Tastendruck, Zeichen und Loslassen einzeln,
wie von einer echten Tastatur.

| Variante | **Java: A** p50 / p95 | KB je Bild | Anteil | **JS: keydown→Inhalt** | keydown→rAF1 |
|---|---:|---:|---:|---:|---:|
| **A** `<textarea>` | **21,4** / 48,3 ms | 4547 | 57,1 % | **0,8 ms** | 1,5 ms |
| **B** `contenteditable` | 21,8 / 48,6 ms | 3919 | 49,2 % | 0,8 ms | 1,4 ms |
| **C** Monaco minimal | 33,6 / 66,8 ms | 4512 | 56,6 % | 2,0 ms | 7,4 ms |
| **D** Monaco vollständig | 34,4 / 56,2 ms | 5126 | 64,4 % | 2,3 ms | 9,0 ms |
| **E** Monaco + Glas | 40,2 / 60,2 ms | 5121 | 64,3 % | 2,4 ms | 9,0 ms |
| **F** Monaco + vorgeblurtes Glas | **35,7** / 56,7 ms | 5090 | 63,9 % | — | — |

---

## 1–5. Die Varianten einzeln

**Ein nacktes Textfeld kostet 21,4 ms.** Kein Framework, kein Glas, keine
Animation, ein einziges DOM-Element. Das ist die **Untergrenze dieses Aufbaus**
für Texteingabe — schneller kann in dieser Runtime nichts sein.

**`contenteditable` liegt gleichauf** (21,8 ms). Chromiums Texteingabe an sich
ist also nicht der Unterschied.

**Monaco minimal kostet 33,6 ms** — mit abgeschalteter Übersicht, ohne
Zeilennummern, ohne Faltung, ohne haftende Kopfzeile, ohne Ränder, ohne Shell.

**Monaco vollständig kostet 34,4 ms.** Also **0,8 ms mehr** als die minimale
Fassung.

**Glas kostet 5,8 ms** (40,2 gegen 34,4).

---

## 6–7. Die Sicht aus der Seite heraus

Die Seite misst mit ihrer eigenen Uhr, ohne Bezug zu Java — es zählen nur
Abstände.

| | keydown → Inhalt geändert | keydown → rAF1 |
|---|---:|---:|
| `<textarea>` | 0,8 ms | 1,5 ms |
| Monaco vollständig | 2,3 ms | 9,0 ms |

**Monacos eigenes JavaScript kostet 1,5 Millisekunden mehr als ein Textfeld.**
Anderthalb. Bei einer Gesamtstrecke von fünfunddreißig.

**Eine Einschränkung, die dazugehört:** Der dritte geplante Wert —
`keydown → rAF2`, also nach dem Übergeben des Bildes — ist unbrauchbar. Er
liegt bei allen Varianten zwischen 28 und 35 ms und misst damit nicht die
Arbeit, sondern **den Takt des Compositors**: Bei rund dreißig Bildern je
Sekunde sind zwei Aufrufe eben etwa dreiunddreißig Millisekunden. Die Zahl
steht im Protokoll, taugt aber nicht als Renderzeit.

Was bleibt, genügt: **keydown → Inhalt** ist reine JavaScript-Zeit, und sie ist
winzig.

---

## 8. Die geänderte Fläche

**Schon ein `<textarea>` macht 57 % der Seite ungültig, wenn ein einziges
Zeichen erscheint.**

Das ist der Befund, der die Frage „ist die große Invalidierung
Monaco-spezifisch?" beantwortet: **Nein.**

| | Anteil |
|---|---:|
| `<textarea>` | 57,1 % |
| `contenteditable` | 49,2 % |
| Monaco minimal | 56,6 % |
| Monaco vollständig | 64,4 % |

Monaco minimal liegt **gleichauf mit dem nackten Textfeld**. Erst die volle
Fassung legt sieben Prozentpunkte drauf — für Übersicht, Ränder und Shell
zusammen.

Woher die 57 % bei einem Textfeld kommen, sagt diese Messung nicht. Naheliegend
ist, dass Chromium im fensterlosen Betrieb gröber zusammenfasst als beim
Zeichnen in ein echtes Fenster; belegt ist das hier nicht.

---

## 9. Welche Monaco-Featuregruppe kostet etwas?

**Keine.**

Der Unterschied zwischen minimal und vollständig beträgt **0,8 ms** — bei einer
Streuung zwischen zwei Läufen von etwa 4 ms. Er ist nicht messbar.

Damit entfällt die geplante Untersuchung nach Gruppen (Ränder, Renderflächen,
Decorations, Shell). Sie einzeln zu vermessen hieße, ein Nichts in vier Teile
zu zerlegen.

**Das ist auch eine Entwarnung für die Gestaltung:** Übersicht, Zeilennummern,
Faltung, haftende Kopfzeile, Dateibaum, Problemfeld und Statuszeile sind
zusammen unter einer Millisekunde wert. Nichts davon muss aus
Geschwindigkeitsgründen wegbleiben.

---

## 10. Vorgeblurtes Glas statt `backdrop-filter`

| | A p50 | A p95 |
|---|---:|---:|
| ohne Glas | 34,4 ms | 56,2 ms |
| **`backdrop-filter`** | 40,2 ms | 60,2 ms |
| **vorgeblurte Kopie** | **35,7 ms** | **56,7 ms** |

**Der Spezialpfad holt die 5,8 ms zurück.** Die vorgeblurte Fassung liegt
innerhalb der Streuung von „ohne Glas".

**Wie es gemacht ist:** Dasselbe Hintergrundbild ein zweites Mal im Dokument,
mit `filter: blur(18px) saturate(140%)` — nicht `backdrop-filter`. Ein `filter`
auf einem unveränderlichen Element wird von Chromium zwischengespeichert; er
läuft einmal statt bei jedem Neuzeichnen. Die Panels zeigen einen Ausschnitt
dieser Kopie statt eines live gefilterten Untergrunds.

**Warum das hier funktioniert:** Der Minecraft-Hintergrund steht während einer
IDE-Sitzung still. Was sich nicht ändert, muss auch nicht neu gefiltert werden.

**Was der Vergleich nicht abdeckt:** Die vorgeblurte Fassung filtert nur den
Hintergrund, nicht die Oberfläche darüber. Panels, die über *anderen Panels*
liegen, sähen anders aus. Für die heutige Anordnung — alles liegt direkt über
dem Hintergrundbild — ist der Unterschied nicht zu sehen; für eine Oberfläche
mit gestapelten Ebenen wäre er es.

---

## 11. Speicher

| Stufe | Chromium gesamt |
|---|---:|
| leerer Browser | 791 MB |
| Hülle ohne Monaco | 798 MB |
| Monaco, ein Modell | 807 MB |
| Monaco, volle Oberfläche | 785 MB |

**Die Stufen sind ununterscheidbar.** Die Schwankung von ±15 MB ist kleiner als
das Rauschen zwischen zwei Messungen derselben Stufe.

**Damit ist die Frage beantwortet, woher die ~800 MB kommen: von Chromium
selbst.** Monaco, die Oberfläche und acht Modelle zusammen sind darin nicht
messbar.

Das ist gleichzeitig die Antwort auf eine Frage, die niemand gestellt hat:
Speicher zu sparen, indem man weniger im Browser anzeigt, funktioniert nicht.
Wer die 800 MB angreifen will, muss an Chromiums Prozessmodell — und das heißt
eigene Runtime.

*Zur Sorgfalt:* Die Stufe „CEF ohne Browser" ist nicht valide. Die Marke wurde
gesetzt, während der Bildschirm seinen Browser schon öffnete; gemessen wurde
also derselbe Zustand wie bei „leerer Browser".

---

## 12. Die Antwort

> **Ist die 42–48-ms-Strecke hauptsächlich Monaco oder hauptsächlich
> Chromium/CEF-OSR?**

**Hauptsächlich Chromium/OSR.**

Die Zerlegung von A bei Monaco vollständig (34,4 ms):

```
  0,8 ms   JavaScript, wie es auch ein Textfeld braucht
  1,5 ms   Monacos eigenes JavaScript obendrauf
 12,2 ms   Chromiums Mehrarbeit für Monacos DOM
           (Layout und Zeichnen eines komplexen Baums)
 19,9 ms   der Aufbau selbst — Layout, Zeichnen, Zusammensetzen,
           und der Rückweg des fertigen Bildes in den Hauptspeicher
```

Die letzten beiden Zeilen sind der Punkt. **Rund zwanzig Millisekunden fallen
an, bevor Monaco überhaupt ins Spiel kommt** — sie stecken schon in einem
`<textarea>` mit einem einzigen Element. Weitere zwölf kostet Monacos DOM, aber
auch die verbraucht Chromium und nicht Monacos JavaScript.

**Monacos eigener Anteil sind 1,5 von 34,4 Millisekunden. Vier Prozent.**

### Was das für die Wegwahl heißt

> **Weg 1 — Monaco/CSS optimieren:** wenig zu holen.

Die Featuregruppen kosten zusammen 0,8 ms (Abschnitt 9). Der einzige lohnende
Einzelposten ist das Glas mit 5,8 ms, und dafür gibt es bereits einen Weg, der
nichts kostet und gleich aussieht (Abschnitt 10). Danach ist Schluss: Was
bliebe, wäre Monaco durch etwas Einfacheres zu ersetzen — und dann wäre man bei
einem Textfeld und immer noch bei einundzwanzig Millisekunden.

> **Weg 2 — den Bitmap-OSR-Pfad angreifen:** dort liegt die Masse.

Zwanzig der vierunddreißig Millisekunden fallen unabhängig vom Inhalt an. Was
davon Layout und Zeichnen ist und was der Rückweg vom Grafikspeicher, trennt
diese Messung **nicht** — dafür bräuchte es Chromiums eigene Ablaufverfolgung
oder einen Vergleich mit einem beschleunigten Pfad.

**Das ist die Grenze dieses Spikes, und sie ist ehrlich zu nennen:** Er zeigt,
*dass* die Masse vor Monaco liegt, nicht *wo genau* darin.

### Eine Zahl, die dabei auffällt

Beim Wechsel von der Auflösung 854 × 480 auf 1920 × 1062 blieb A in Schritt G
konstant. Wäre der GPU→CPU-Rückweg der große Posten in A, müsste er mit der
Fläche wachsen — er tut es nicht. **Das spricht dafür, dass die zwanzig
Millisekunden eher Layout und Zeichnen sind als der Rückweg.**

Wenn das stimmt, brächte auch ein beschleunigter Pfad mit geteilter Textur
weniger, als die Rechnung nahelegt: Er entfernte B (11 ms) und den Rückweg,
aber nicht Chromiums Zeichenarbeit.

**Bevor daraus native Arbeit wird, wäre das der nächste zu klärende Punkt** —
und er ist billiger zu klären, als eine Schicht dafür zu bauen.

---

## 13. Zum Nachstellen

```
./gradlew runClient -Pide -Pprobe -Pw=1920 -Ph=1080
```

Die Messfläche liegt unter `assets/factorynetwork/web/ide/probe.html` und nimmt
`?v=textarea|contenteditable|monaco-min|monaco-full|monaco-glas|monaco-vorblur`.

**Drei Fehler im Messaufbau, die je einen Lauf gekostet haben** und im
Quelltext als Warnung stehen:

1. **Nur `charTyped` zu senden erzeugt kein `keydown`.** Die Seite maß an
   `keydown`, Java tippte mit Zeichen — in jeder Zeile stand `n=0`. Echte
   Eingabe kommt in drei Teilen: Taste herunter, Zeichen, Taste herauf.
2. **Zwei Elemente mit derselben Kennung.** Die Fokussuche fand das falsche,
   und `contenteditable` tippte fünfundvierzig Sekunden ins Leere.
3. **Ein Editor ohne Höhe.** Ohne die Hüllenklasse bekam der Container null
   Bildpunkte; Monaco lud, zeigte nichts und nahm keinen Fokus an. Die Variante
   „minimal" fiel zweimal aus, bevor es auffiel.
