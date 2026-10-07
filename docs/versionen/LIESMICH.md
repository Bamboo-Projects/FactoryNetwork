# Die Versionsschicht — Vorlagen für andere Minecraft-Versionen

Unter `src/main/java/dev/devpanda/factorynetwork/platform` liegen die Klassen,
die sich je Minecraft-Version unterscheiden dürfen. Der übrige Code ruft nur
sie und merkt von dem Unterschied nichts.

Hier liegen dieselben Klassen, geschrieben gegen eine spätere Version. **Sie
werden nicht mitgebaut.** Sie sind der Beleg, dass die Schicht trägt, und der
Anfang für den Port.

| Datei | Klasse | Geprüft gegen | Was sich dort ändert |
|---|---|---|---|
| `Nbt-1.21.5.java.txt` | `platform.Nbt` | 1.21.5 und 26.1.2 | Lesen aus `CompoundTag` liefert Optionale; typisiertes `contains` und `getList` entfallen |
| `Names-26.1.java.txt` | `platform.Names` | 26.1.2 | `Item.getDescription()` gibt es nicht mehr |
| `Chat-26.1.java.txt` | `platform.Chat` | 26.1.2 | `displayClientMessage` ist zu zwei Aufrufen geworden |
| `Keys-26.1.java.txt` | `platform.client.Keys` | 26.1.2 | Die Tastenfragen stehen nicht mehr an `Screen` |

## Gemessen am 07.10.2026

Die heutige Quelle, ohne `compat`, gegen die Zielversion übersetzt. Jeweils
Compilerfehler vor der Schicht und mit ihr:

| Ziel | vorher | mit der Schicht |
|---|---|---|
| 1.21.5 | 306 | 205 (nur `Nbt`) |
| 26.1.2 | 802 | 621 (alle vier, dazu `isClientSide()` als Methode) |

Die Zahlen sind Untergrenzen: Ein nicht auflösbarer Import verdeckt
Folgefehler in derselben Datei.

## Was noch fehlt

Nach Häufigkeit auf 26.1.2:

- **`GuiGraphics` gibt es nicht mehr** (108). Die Oberflächen zeichnen dort
  anders; das ist ein Umbau im Client und keine Schicht.
- **`ResourceLocation` heißt `Identifier`** (114). Ein umbenannter Typ lässt
  sich nicht kapseln, ohne ihn überall zu verpacken. Das ist Arbeit für ein
  Werkzeug, das Quelltext je Version umschreibt.
- **Blöcke laden und speichern über `ValueInput`/`ValueOutput`** (ab 1.21.8).
- **Interaktions-Ergebnisse, Energie- und Gegenstands-Capabilities,
  Block-Entity-Typen, Rezepte.**
