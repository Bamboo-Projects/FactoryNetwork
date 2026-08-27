# Wireless Terminal und Laptop — Umsetzungsplan

> **Für ausführende Agenten:** Aufgabe für Aufgabe. Erst der Test, dann der
> Code, dann der Lauf, dann der Commit.

**Ziel:** Zwei Gegenstände, die das Terminal aus der Ferne öffnen — das eine
ohne Code, das andere mit.

**Vorgehen:** Das Terminalfenster braucht nur eine Position im Netz, um seinen
Controller zu finden. Beim Fernzugriff ist das die des Sendemasts. Daran hängt
alles Weitere: Was der Spieler sieht, ist dasselbe Fenster, nur mit einem
Reiter weniger und einer anderen Frage, ob es offen bleiben darf.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5.

**Entwurf:** `docs/fernzugriff.md`, Abschnitt 4.

**Voraussetzung:** `plan-ausbausystem.md` und `plan-sendemast.md` sind
umgesetzt — `Loadout`, `UpgradeSlots`, `Range` und der Mast stehen.

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.**
- **Echte Umlaute**, keine Unicode-Escapes.
- Modelle und Texturen entstehen in `tools/`, nicht von Hand.
- Jeder neue Gegenstand gehört in den Kreativ-Reiter — ein GameTest wacht
  darüber und meldet es sonst beim nächsten Lauf.
- Nach jeder Aufgabe committen, Meldungen deutsch, ohne Präfixe.

## Zwei Präzisierungen zum Entwurf

**Das Wireless Terminal bekommt fünf Reiter, nicht vier.** `fernzugriff.md`
zählt Storage, Crafting, Network und Dashboards auf und übergeht das
Protokoll — es stammt aus `konzept.md` §29, das den Reiter noch nicht kannte.
Das Protokoll ist Diagnose wie die Netzübersicht; es künstlich wegzulassen
wäre eine Regel mehr, die niemand erklären kann. **Die Regel lautet: alles
außer Code.**

**Der Laptop bekommt alle sechs.** Er kann, was das Terminal kann, und dazu
den Code.

## Verifizierter Bestand

| Was | Wo |
|---|---|
| Das Terminalfenster findet seinen Controller über eine Blockposition | `client/menu/TerminalMenu.java:48-52` |
| Es öffnet über `serverPlayer.openMenu(terminal, pos)` | `block/TerminalBlock.java:66` |
| Die Reiter stehen als Aufzählung mit `isReady()` | `client/screen/TerminalTab.java:13-20` (zieht in Aufgabe 1 nach `terminal/`) |
| `Range.covers(mast, device, distance)` beantwortet die Reichweitenfrage | `upgrade/Range.java` |
| `UpgradeSlots` nimmt nur Ausbauten und liefert ein `Loadout` | `upgrade/UpgradeSlots.java` |
| Der Mast hält vier Plätze und kennt sein `loadout()` | `block/entity/MastBlockEntity.java` |
| Gegenstände tragen Daten über Datenkomponenten | `item/LabelGunItem.java`, `CustomData` |

---

## Aufgabe 1: Die beiden Geräte — erledigt am 28.08.

Zwei Gegenstände mit Steckplätzen, Akku und einem gemerkten Netz. Noch ohne
Fenster — sie liegen erst einmal nur im Inventar herum und wissen, wohin sie
gehören.

**Dateien:**
- Anlegen: `upgrade/RemoteDevice.java` (Aufzählung: Terminal, Laptop)
- Anlegen: `item/RemoteDeviceItem.java`
- Anlegen: `registry/FnComponents.java` (falls es sie noch nicht gibt)
- Ändern: `registry/FnItems.java`, `registry/FnCreativeTabs.java`
- Ändern: `tools/textures.py`, `tools/assets.py`, beide `lang/*.json`
- Test: `test/.../RemoteDeviceTest.java` (rein) und ein GameTest

**Schnittstellen:**
- Liefert: `RemoteDevice.TERMINAL`, `RemoteDevice.LAPTOP`,
  `RemoteDevice.slots()`, `RemoteDevice.allows(TerminalTab)`,
  `RemoteDeviceItem.deviceOf(ItemStack)`, `RemoteDeviceItem.mastOf(ItemStack)`,
  `RemoteDeviceItem.bind(ItemStack, BlockPos)`.

- [x] **Schritt 1: Der fehlschlagende Test**

```java
package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was die beiden Geräte dürfen — und was den Laptop teurer macht.
 */
class RemoteDeviceTest {

    @Test
    @DisplayName("Das Terminal kann alles außer Code")
    void theTerminalHasEverythingButCode() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertEquals(tab != TerminalTab.CODE,
                    RemoteDevice.TERMINAL.allows(tab),
                    tab + " am Wireless Terminal");
        }
    }

    @Test
    @DisplayName("Der Laptop kann alles")
    void theLaptopHasEverything() {
        for (TerminalTab tab : TerminalTab.values()) {
            assertTrue(RemoteDevice.LAPTOP.allows(tab), tab + " am Laptop");
        }
    }

    @Test
    @DisplayName("Und er hat mehr Plätze — das ist der zweite Grund für ihn")
    void theLaptopHasMoreSlots() {
        assertEquals(2, RemoteDevice.TERMINAL.slots());
        assertEquals(4, RemoteDevice.LAPTOP.slots());
        assertTrue(RemoteDevice.LAPTOP.slots() > RemoteDevice.TERMINAL.slots());
    }

    @Test
    @DisplayName("Genau ein Gerät kann den Code")
    void codeIsTheDividingLine() {
        // Die Trennung ist der Sinn der ganzen Sache: Unterwegs kommt man
        // ans Lager, aber nicht an den Code.
        long withCode = java.util.Arrays.stream(RemoteDevice.values())
                .filter(device -> device.allows(TerminalTab.CODE))
                .count();
        assertEquals(1, withCode);
        assertFalse(RemoteDevice.TERMINAL.allows(TerminalTab.CODE));
    }
}
```

**Vorher: `TerminalTab` umziehen.** Die Aufzählung liegt heute unter
`client.screen`. Ab Aufgabe 2 entscheidet der **Server**, welche Reiter erlaubt
sind — und Servercode, der aus `client.*` importiert, fällt auf einem
Dedicated Server auf, nicht hier. Verschiebe sie nach
`dev.devpanda.factorynetwork.terminal`, **bevor** `RemoteDevice` den Import
festschreibt. Der Bildschirm importiert sie dann von dort.

Es ist eine reine Aufzählung ohne clientseitige Bezüge; der Umzug ist ein
Suchen-und-Ersetzen über die Importe.

- [x] **Schritt 2: Test laufen lassen, Fehlschlag sehen**

`./gradlew test --tests "*RemoteDeviceTest*"` — `RemoteDevice` gibt es nicht.

- [x] **Schritt 3: Die Aufzählung**

```java
package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.terminal.TerminalTab;

/**
 * Die beiden Geräte für den Fernzugriff.
 *
 * <p><b>Die Trennung ist der Sinn der Sache:</b> Unterwegs kommt man ans
 * Lager, aber nicht an den Code — dafür braucht man den Laptop. Er kann
 * alles, was das Terminal kann, und kostet mehr.
 *
 * <p>Der zweite Unterschied sind die Steckplätze: vier gegen zwei. Der
 * Laptop reicht damit auch weiter, siehe {@code fernzugriff.md} §3.
 */
public enum RemoteDevice {

    /** Der frühe Zugang: alles außer Code. */
    TERMINAL("wireless_terminal", 2, false),

    /** Und das Ziel: alles. */
    LAPTOP("laptop", 4, true);

    private final String id;
    private final int slots;
    private final boolean code;

    RemoteDevice(String id, int slots, boolean code) {
        this.id = id;
        this.slots = slots;
        this.code = code;
    }

    public String id() {
        return id;
    }

    public int slots() {
        return slots;
    }

    /** Darf dieses Gerät diesen Reiter zeigen? */
    public boolean allows(TerminalTab tab) {
        return tab != TerminalTab.CODE || code;
    }
}
```

- [x] **Schritt 4: Der Gegenstand**

`RemoteDeviceItem` hält drei Dinge am ItemStack, alle über Datenkomponenten:

1. **Das gekoppelte Netz** — die Position des Masts. Ohne sie sagt das Gerät
   beim Öffnen, dass es kein Netz kennt.
2. **Die Steckplätze** — als Inventar in der Komponente. Der Bestand hat dafür
   `UpgradeSlots`; sie speichert über `ContainerHelper`, also passt sie in
   einen `CompoundTag`.
3. **Den Akku** — über `Capabilities.EnergyStorage.ITEM`.

Sieh dir `LabelGunItem` an: Er trägt seinen Namen über `CustomData` am Stack
und ist das nächstliegende Muster im Projekt.

Der Tooltip nennt das gekoppelte Netz und den Ladestand. Ein Gerät, das
aussieht wie jedes andere, ist im Inventar nicht zu unterscheiden.

- [x] **Schritt 5: Der Akku als Capability**

```java
// In FnCapabilities, bei den anderen Anmeldungen:
event.registerItem(Capabilities.EnergyStorage.ITEM,
        (stack, context) -> new ComponentEnergyStorage(stack, ...),
        FnItems.WIRELESS_TERMINAL.get(), FnItems.LAPTOP.get());
```

**Das ist der Punkt, der Fremdmods anschließt.** Powah, Flux Networks und
alles andere, was Gegenstände im Inventar lädt, sprechen `IEnergyStorage` —
und mehr braucht es nicht. NeoForge bringt `ComponentEnergyStorage` mit; wenn
die Klasse in 21.1 anders heißt, such im NeoForge-Jar nach
`EnergyStorage`-Implementierungen für ItemStacks.

**Prüfen lässt sich das nur mit einer Fremdmod im Testaufbau.** Ohne die ist
es behauptet, nicht gezeigt — so steht es auch in `fernzugriff.md` §8.

- [x] **Schritt 6: Kopplung per Rechtsklick auf den Mast**

In `MastBlock.useItemOn`: Hält der Spieler ein `RemoteDeviceItem`, merkt sich
der Stack die Position des Masts, und der Spieler bekommt eine Meldung mit dem
Netznamen. Kein Fenster, kein Menü — ein Klick, eine Zeile Text.

- [x] **Schritt 7: Rezept, Namen, Reiter, Prüfläufe — und ein Platzhalter**

Rezepte: Das Terminal aus einer Platte, einem Netzkern und Glas; der Laptop
zusätzlich mit einem Rechenkern.

**Modell und Textur bleiben vorerst flach.** Ein Sprite genügt, kein Eintrag
in `ITEM_BODIES`, kein eigener Körper. Der Grund ist keine Bequemlichkeit: Wie
die beiden Geräte aussehen, ist eine Ansichtssache, und die entscheidet der
User. Sobald sie einen Körper haben, steht er in `assets.py` und im Git — und
etwas zurückzunehmen, das schon dasteht, kostet mehr als es zu schreiben.

Registriert werden müssen sie trotzdem, sonst meldet der Kreativ-Reiter-Wächter
sie als fehlend und `ItemModelTest` findet kein Modell. Ein flaches Sprite
befriedigt beide und schreibt nichts fest.

Der Körper kommt in Aufgabe 4 — nach dem Blick des Users.

- [x] **Schritt 8: Committen**

---

## Aufgabe 2: Das Fenster aus der Ferne — erledigt am 28.08.

**Dateien:**
- Ändern: `client/menu/TerminalMenu.java`
- Ändern: `client/screen/TerminalScreen.java`
- Ändern: `item/RemoteDeviceItem.java` (das Öffnen)

**Der Kern in drei Sätzen.** Das Menü nimmt heute eine Blockposition und
findet darüber seinen Controller. Beim Fernzugriff ist das die Position des
Masts — sonst ändert sich nichts. Was sich ändert, sind zwei Fragen: welche
Reiter gezeigt werden und ob das Fenster offen bleiben darf.

- [x] **Schritt 1: Das Menü lernt, wer es geöffnet hat**

Ein zweites Feld: das `RemoteDevice` oder `null` für den Block. Es geht über
die Leitung mit, damit der Client dieselben Reiter zeigt wie der Server
erlaubt. Ein Client, der den Code-Reiter zeichnet, den der Server ablehnt,
ist schlimmer als einer, der ihn gar nicht hat.

- [x] **Schritt 2: `stillValid` fragt die Reichweite statt die Nähe**

Am Block gilt weiter der Abstand zum Block. Aus der Ferne gilt
`Range.covers(mastLoadout, deviceLoadout, distance)` — und wer aus der
Reichweite läuft, dem geht das Fenster zu, mit einer Meldung.

**Das ist die Stelle, an der die Reichweite überhaupt etwas tut.** Bis hierhin
war sie eine Zahl ohne Wirkung.

**Woher kommt das Geräte-Loadout?** `Range.covers` braucht beide Seiten, und
die Geräteseite steckt im ItemStack. Merke dir beim Öffnen den Platz im
Inventar und prüfe in `stillValid`, dass dort **immer noch dasselbe Gerät
liegt**. Wer es weglegt, in eine Kiste tut oder fallen lässt, hat kein Gerät
mehr in der Hand — und das Fenster geht zu wie beim Wegzug aus der Reichweite.

Ohne diese Prüfung bleibt das Fenster offen, wenn der Gegenstand längst in
einer Kiste liegt.

**Für den GameTest:** Ein Mock-Spieler tickt sein `containerMenu` womöglich
nicht wie ein echter. Ruf `stillValid` dann direkt auf, statt darauf zu warten,
dass sich das Fenster von selbst schließt — sonst prüfst du den Ticker und
nicht die Regel.

- [x] **Schritt 3: Der Bildschirm zeigt nur erlaubte Reiter**

`TerminalScreen` fragt `device.allows(tab)`, bevor es einen Reiter zeichnet.
Beim Block ist `device` null und alles ist erlaubt.

- [x] **Schritt 4: GameTest**

Mast setzen, Gerät koppeln, Fenster öffnen, prüfen: Das Terminal hat keinen
Code-Reiter, der Laptop hat einen. Dann den Spieler aus der Reichweite
setzen und prüfen, dass das Fenster zugeht.

- [x] **Schritt 5: Committen**

### Was beim Bauen dazukam

**Drei Pakete autorisieren jetzt über das offene Fenster statt über
Koordinaten.** `DeployProgramPacket` verlangte einen Terminal-Block an der
gemeldeten Position und höchstens acht Blöcke Abstand; `SaveDraftPacket`
verlangte einen Controller und 64. Beides trug, solange man vor einem Block
stehen musste — **mit dem Laptop steht man vor keinem mehr**, und der Code
wäre für ihn unerreichbar geblieben.

Sie fragen jetzt `player.containerMenu`, wie es `StorageActionPacket`,
`CraftingActionPacket` und `RequestEditPacket` schon vorher taten. Das ist
zugleich strenger: Eine Koordinate im Paket ist eine Behauptung des Clients,
das offene Fenster dagegen tickt der Server selbst.

`RequestEditPacket` kam als drittes dazu. Es ging schon über das Menü, prüfte
aber den Code-Reiter nicht — ein veränderter Client hätte sich mit einem
Wireless Terminal fremden Quelltext geben lassen können.

**Der Besitzschutz aus `FnProtection` ist unberührt.** Er beantwortet eine
andere Frage als „darf dieses Fenster das", und beide gelten weiter.

---

## Aufgabe 3: Der Verbrauch

**Wenig je Tick, solange ein Fenster offen ist, mehr je Handlung** — ein
Stapel bewegt, ein Programm gespeichert. Leerer Akku heißt: Das Fenster geht
nicht auf, mit einer Meldung statt eines schwarzen Bildschirms.

Die Zahlen gehören zu den anderen in `network/Power.java`, nicht an den
Gegenstand.

- [ ] Test zuerst: Ein Gerät ohne Ladung öffnet nicht. Eines mit Ladung
      öffnet und verliert sie über die Zeit.
- [ ] Committen

---

## Was am Ende steht

Zwei Geräte, mit denen man von unterwegs an sein Lager kommt — und mit einem
davon auch an den Code. Damit ist der Fernzugriff aus `fernzugriff.md`
vollständig, bis auf Teil 3: das Funk-Modul für die Anzeigetafel.

**Was diese Fassung nicht kann:** Benachrichtigungen. `konzept.md` §30 sieht
vor, dass Code sie sendet und das Wireless Terminal sie zeigt. Das bleibt
liegen — es ist ein eigenes Thema und kein Anhängsel.
