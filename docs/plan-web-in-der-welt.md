# Web-Flächen in der Welt

Gewünscht am 1. September 2026: **Eine Web-Fläche muss sich in die Welt setzen
lassen — als Block, nicht nur als Vollbild.** Das gehört zur eigenen
CEF-Runtime und ist kein Aufsatz, den man später danebenstellt.

Dieses Dokument hält die Anforderung fest und was dafür schon da ist. Es ist
kein Entwurf; der gehört in den Block selbst.

---

## Was der Wunsch bedeutet

Heute gibt es genau eine Web-Fläche: den Editor als Vollbildschirm. Was fehlt,
ist dieselbe Fläche **an einem Block**, sichtbar im Vorbeigehen, ohne dass
jemand etwas anklickt.

Der vorhandene Anzeigeblock (`DisplayBlockEntity`, `DisplayRenderer`) zeichnet
heute Minecraft-Text auf seine Vorderseite. Er ist die naheliegende Stelle —
aber die Entscheidung, ob eine Web-Fläche derselbe Block mit anderem Inhalt
oder ein eigener ist, gehört in den Block.

---

## Was schon trägt

| Stück | Zustand |
|---|---|
| `BrowserSession.textureId()` | liefert eine rohe GL-Textur — genau das, was ein Blockrenderer braucht |
| `BrowserCompositor` | zeichnet diese Textur heute im Bildschirm; ein Viereck in der Welt ist dieselbe Arbeit an anderer Stelle |
| `BrowserVisibility`, `FramePacer` | **für diesen Fall geschrieben** — im Kopfkommentar steht „bei einem Dutzend Anzeigen in einer Basis" |
| `BrowserManager` (B5) | weiß, wie viele Browser leben; ohne ihn ließe sich keine Obergrenze durchsetzen |
| `ProcessGuard` (B6) | fängt ab, was ein Dutzend Chromium-Prozesse bei einem Absturz hinterließen |

Der Renderpfad ist also da. Was fehlt, ist alles um ihn herum.

---

## Was neu ist — und was davon wirklich Arbeit macht

**Klein.** Die Textur in einem `BlockEntityRenderer` auf eine Blockfläche
zeichnen. Der Editor macht nichts anderes, nur mit einer anderen Matrix.

**Der eigentliche Aufwand: die Eingabe.** Ein Bildschirm hat einen Fokus und
einen Mauszeiger; ein Block in der Welt hat beides nicht. Es braucht

```text
Strahl vom Spieler → Trefferpunkt auf der Blockfläche → Browser-Pixel
eine Regel, wann eine Tafel die Tastatur bekommt und wann sie sie abgibt
einen Weg zurück, der nicht Escape ist — das gehört im Spiel dem Menü
```

**Der eigentliche Preis: jede Fläche ist ein eigener Chromium.** Gemessen für
einen Browser: fünf Hilfsprozesse, und ein Upload von 1920×1080 kostet 9–14 ms
auf dem Renderthread. Zwei Flächen in Vollauflösung fressen das Bildbudget.

Beherrschbar ist das über drei Regeln, und die gehören in den Entwurf:

1. **Auflösung je Tafel klein halten** — der Upload skaliert mit der Fläche.
   Eine Tafel an der Wand braucht keine 1920×1080.
2. **Sichtbarkeit entscheidet den Takt.** Was niemand ansieht, malt zwei Bilder
   je Sekunde und nicht sechzig. `BrowserVisibility` ist dafür da.
3. **Eine harte Obergrenze**, wie viele Flächen gleichzeitig leben. Was darüber
   liegt, zeigt ein Standbild oder gar nichts.

**Lebenslauf.** Chunk entlädt oder Block wird abgebaut → Browser zu. Über den
`BrowserManager` ist das jetzt eine Zeile; vorher wusste niemand, dass es ihn
gab.

---

## Zwei Entscheidungen, bevor irgendetwas gebaut wird

**1. Wie viele Flächen sollen gleichzeitig laufen dürfen?** Die Antwort
bestimmt alles andere. Eine einzige große Tafel je Basis ist ein anderes
Programm als zwanzig kleine.

**2. Was zeigen die Flächen?** Eine Seite, die der Spieler selbst schreibt?
Eine feste Statusansicht aus dem Netz? Beides sind Web-Flächen, aber nur das
zweite braucht keine Adresszeile und keinen Fokus für die Tastatur — und wäre
damit ein Bruchteil der Arbeit.

---

## Ein Nebenbefund, der hier hingehört

Die Kommentare in `BrowserVisibility` und `FramePacer` behaupten noch, die
Bildrate sei gar nicht einstellbar — „gemessen 30,1 Bilder je Sekunde, CEFs
Voreinstellung". Das stammt aus der MCEF-Zeit und stimmt seit CEF 146 nicht
mehr: `CefBrowserSettings.windowless_frame_rate` gibt es, wir setzen 60, und
gemessen kommen 60,3. Die Drosselung nach unten funktioniert wie beschrieben;
nur der Satz „nach oben ist nichts zu holen" ist überholt.

Gehört korrigiert, wenn dieser Block drankommt — dann ist die Drosselung
ohnehin das Thema.

---

## Reihenfolge

Dieser Block hängt an nichts, was noch offen ist, und blockiert nichts. Er
kann vor oder nach B8/B9 kommen.

**Vorschlag:** danach. B8/B9 sind klein und geplant, und sie nehmen MCEF aus
dem Weg — jede neue Fläche, die vorher entsteht, müsste sonst zweimal gebaut
werden, einmal je Weg.
