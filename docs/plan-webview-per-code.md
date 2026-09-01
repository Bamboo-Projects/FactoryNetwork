# Web-Flächen aus dem Programm

Gewünscht am 1. September 2026: **Eine Web-Fläche entsteht im Programm, nicht
an einem Gegenstand.** Sie hat einen Namen, steht frei im Raum und lebt,
solange das Programm läuft.

```text
webview lager {
  url  "https://…"
  at   112.5 68 -340.5
  face south
  size 4 x 3
}
```

Dieses Dokument hält die Kette fest und die Fragen, die vorher zu beantworten
sind. Es ist kein Entwurf — der gehört in den Block selbst.

---

## Warum der Block nicht die Antwort ist

Die Tafel aus Stufe 1 funktioniert und hat den Renderpfad bewiesen. Sie ist
trotzdem der falsche Weg für das, was gemeint war:

```text
ein Block je Fläche        Größe und Lage sind, was der Block hergibt
benannt am Gegenstand      abbauen und neu setzen verliert den Bezug
gesetzt von Hand           ein Programm kann keine Tafel platzieren
```

Ein Display an der Wand hat dieselbe Bauform und ist trotzdem etwas anderes:
Es zeigt Zeilen, die der Server ausrechnet. Eine Web-Fläche zeigt eine Seite,
die jeder Client selbst lädt. Der Unterschied entscheidet mehrere Fragen weiter
unten.

---

## Die Kette, Glied für Glied

Das Display ist in jedem Glied die Vorlage — dieselben Dateien, dieselbe
Reihenfolge.

| Glied | Vorlage | was neu ist |
|---|---|---|
| `Decl.WebView` im Syntaxbaum | `Decl.Display` | Adresse, Ort, Ausrichtung, Größe statt Zeilen |
| Parser | `parseDisplay` | vier Angaben statt acht Bausteinen |
| Prüfung | Semantik der Displays | Adresse plausibel, Größe begrenzt |
| Editor | `Signatures` | Vervollständigung und Prüflauf `editorCheck` |
| Auswertung | `ControllerBlockEntity` | keine Rechnung, nur Weitergabe |
| Zustandspaket | `DisplayStatePacket` | Ort, Größe, Ausrichtung je Fläche |
| Verwalter im Client | `WebPanels` | Schlüssel ist der Name, nicht die Blockposition |
| Renderer | `WebPanelRenderer` | **kein Blockrenderer** — siehe unten |

**Der Renderer ist das einzige Glied ohne Vorlage.** Ohne Block gibt es keinen
`BlockEntityRenderer` und damit auch nicht dessen Sichtprüfung. Gezeichnet
wird stattdessen aus `RenderLevelStageEvent`, und die Drosselung nach
Entfernung und Sichtbarkeit muss dort selbst stehen — der Zeitstempel aus
`WebPanels` trägt nicht, weil niemand mehr aufhört zu fragen.

**Die Vierecksrechnung gehört einmal an eine Stelle.** Sie stand heute dreimal
falsch im Blockrenderer — zwei Vorzeichen und ein Betrag, und zwischen den
Fehlern lagen zwei Pixel. Ein zweiter Renderer, der sie neu errät, ist der
teuerste Weg, denselben Fehler noch einmal zu machen.

---

## Entschieden am 1. September 2026

**1. Der Block bleibt, bis der Code-Weg grün ist.** Dieselbe Regel wie bei
MCEF: nicht entfernen, bevor das Neue trägt. Solange ist die Tafel der einzige
lauffähige Beweis des Renderpfads — zeigt der freie Renderer nichts, sagt sie,
ob es an ihm liegt oder am Rest der Kette. Danach fällt sie.

**2. Jeder in Reichweite sieht sie.** Wie ein Display: Wer hinschaut, sieht
etwas. Jeder Client fährt dafür eigenes Chromium; fünf Spieler mit zehn
Flächen sind fünfzig Browser, aber verteilt auf fünf Rechner. Die Obergrenze
aus `FnClientConfig` greift damit pro Spieler und nicht je Welt — sie steht
dort schon richtig.

**3. Die Auflösung folgt der Größe, gedeckelt.** 512 Pixel je Block
Kantenlänge, höchstens 2048×2048. Eine Fläche von 4×3 Blöcken bekäme
2048×1536 — doppelt so viel Upload wie der Editor in Vollbild, und damit die
Stelle, an der es aufhören muss.

```text
1x1 Block   →   512 x  512      wie heute die Tafel
2x2         →  1024 x 1024
4x3         →  2048 x 1536      gedeckelt
8x6         →  2048 x 1536      dieselbe Zahl, nur gröber
```

**4. Wann sie verschwindet.** „Solange das Programm läuft" heißt: Wird es neu
eingespielt oder der Controller abgebaut, geht die Fläche zu. Am billigsten
über einen vollständigen Abgleich — das Paket trägt die ganze Liste, und was
darin fehlt, wird geschlossen. Dasselbe Verfahren wie bei den Anzeigen.

---

## Was schon trägt

Aus Stufe 1 bleibt alles außer dem Blockrenderer:

```text
SessionTexture      Chromiums Kennung als Minecraft-Textur
BrowserSession      Sitzung mit Namen, Größe, Sichtbarkeitsstufe
FnClientConfig      wie viele Flächen dieser Rechner trägt
WebDevTools         /fnweb devtools findet jede Instanz über ihren Namen
```

Der Name, den `BrowserSession` seit heute trägt, ist genau der aus
`webview NAME` — er war für diesen Zweck gedacht und wartet darauf.
