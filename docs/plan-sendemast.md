# Sendemast — Umsetzungsplan

> **Für ausführende Agenten:** Aufgabe für Aufgabe. Erst der Test, dann der
> Code, dann der Lauf, dann der Commit.

**Ziel:** Ein Block am Kabel, der funkt — mit Steckplätzen, einer Reichweite
und einem Fenster, in dem man ihn bestückt. Noch ohne Gegenstelle: Wer ihn
empfängt, kommt in Teil 2b.

**Vorgehen:** Die Reichweitenrechnung ist reines Java und lässt sich gegen die
Zahlen aus `fernzugriff.md` prüfen. Der Block selbst folgt dem Muster des
Serverschranks — Behälter in der BlockEntity, Fenster mit Plätzen, Aufnahme in
den `FactoryGraph`.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5.

**Entwurf:** `docs/fernzugriff.md`, Abschnitt 3.

**Voraussetzung:** `docs/plan-ausbausystem.md` ist umgesetzt — `Loadout`,
`Card`, `Stat` und `UpgradeSlots` stehen.

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.**
- **Echte Umlaute**, keine Unicode-Escapes.
- Modelle und Texturen entstehen in `tools/`, nicht von Hand.
- Nach jeder Aufgabe committen, Meldungen deutsch, ohne Präfixe.
- Blöcke, die kein voller Würfel sind, brauchen `noOcclusion` und einen
  Eintrag in `MachineLayoutTest`.

## Eine Klärung, die der Entwurf offenließ

`fernzugriff.md` §3 sagt: am Mast +16 je Karte, am Gerät +8. **Es ist
dieselbe Karte.** Eine Karte, die an zwei Orten verschieden viel hebt,
widerspricht der Regel aus Teil 1 — dort hebt eine Karte einen Wert, und
zwar ihren.

**Auflösung: Der Mast verdoppelt, was in ihm steckt.** Das Verstärken ist
eine Eigenschaft des Orts, nicht der Karte. `Card.RANGE` bleibt bei acht,
und `Range.MAST_FACTOR` ist zwei. Die Zahlen des Entwurfs stimmen damit
weiter: 16 + 4 × 8 × 2 = 80 am Mast, 4 × 8 = 32 am Laptop.

Der Entwurf wird in Aufgabe 1 entsprechend nachgezogen.

## Verifizierter Bestand

Alles hier wurde vor dem Schreiben im Code nachgesehen:

| Was | Wo |
|---|---|
| `ControllerRegistry.owning(level, pos)` findet den Controller eines Blocks | `network/ControllerRegistry.java:56` |
| Der `FactoryGraph` sammelt Positionen je Blockart beim Aufbau | `network/FactoryGraph.java:224-226` |
| Kanalkosten stehen als Konstanten beisammen | `network/Channels.java:19-41` |
| Stromkosten ebenso, der Controller summiert sie | `network/Power.java`, `ControllerBlockEntity.java:415` |
| Der Serverschrank hält seine Plätze in einer `ShelfBlockEntity` | `block/entity/RackBlockEntity.java:35,49` |
| Er speichert sie über `ContainerHelper.saveAllItems` | `block/entity/ShelfBlockEntity.java:197-213` |
| Sein Fenster heißt `ShelfScreen` | `client/screen/ShelfScreen.java` |
| Gegenstände werden über `ITEMS.register(name, () -> ...)` angemeldet | `registry/FnItems.java:90,96` |
| Modelle mit Kästen entstehen über `machine_elements(boxes)` | `tools/assets.py` |
| `UpgradeSlots` nimmt nur Ausbauten und liefert ein `Loadout` | `upgrade/UpgradeSlots.java` |

---

## Aufgabe 1: Die Reichweitenrechnung

Reines Java, und die Stelle, an der die Zahlen des Entwurfs stehen.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Range.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/upgrade/RangeTest.java`
- Ändern: `docs/fernzugriff.md` (die Klärung von oben)

**Schnittstellen:**
- Verbraucht: `Loadout`, `Stat`, `Card` aus Teil 1.
- Liefert: `Range.MAST_BASE`, `Range.MAST_FACTOR`, `Range.reach(Loadout, Loadout)`,
  `Range.covers(Loadout, Loadout, double)`, `Range.UNLIMITED`.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Zahlen aus fernzugriff.md §3, an einer Stelle nachgerechnet.
 */
class RangeTest {

    private static final Loadout LEER = Loadout.of(List.of());

    private static Loadout karten(int wie_viele) {
        return Loadout.ofCounts(Map.of(Card.RANGE, wie_viele));
    }

    @Test
    @DisplayName("Ein Mast ohne Karten reicht sechzehn Blöcke weit")
    void aBareMastReachesSixteen() {
        assertEquals(16, Range.reach(LEER, LEER));
    }

    @Test
    @DisplayName("Im Mast zählt eine Karte doppelt")
    void theMastAmplifies() {
        // Der Mast ist ein Verstärker: Das Verdoppeln ist eine Eigenschaft
        // des Orts, nicht der Karte. Sonst hätte dieselbe Karte an zwei
        // Orten zwei Werte, und die Regel aus Teil 1 wäre hin.
        assertEquals(16 + 16, Range.reach(karten(1), LEER));
        assertEquals(16 + 64, Range.reach(karten(4), LEER));
    }

    @Test
    @DisplayName("Im Gerät zählt sie einfach")
    void theDeviceCountsPlain() {
        assertEquals(16 + 8, Range.reach(LEER, karten(1)));
        assertEquals(16 + 32, Range.reach(LEER, karten(4)));
    }

    @Test
    @DisplayName("Voll ausgebaut sind es die 112 aus dem Entwurf")
    void fullyBuiltMatchesTheSpec() {
        assertEquals(112, Range.reach(karten(4), karten(4)));
        // Das Wireless Terminal hat nur zwei Plätze.
        assertEquals(96, Range.reach(karten(4), karten(2)));
        // Die Anzeigetafel einen, weil der andere das Modul trägt.
        assertEquals(88, Range.reach(karten(4), karten(1)));
    }

    @Test
    @DisplayName("Die Grenzenlos-Karte im Mast hebt alles auf")
    void infinityInTheMastLiftsEverything() {
        Loadout endgame = Loadout.of(List.of(Card.INFINITY));
        assertEquals(Range.UNLIMITED, Range.reach(endgame, LEER));
        assertTrue(Range.covers(endgame, LEER, 1_000_000.0));
    }

    @Test
    @DisplayName("Im Gerät hebt sie nichts auf")
    void infinityInTheDeviceDoesNothing() {
        // Sie ist Infrastruktur und gehört in den Mast. Steckte sie im
        // Gerät, bräuchte sie auf einem Server jeder Spieler einzeln — so
        // steht es in fernzugriff.md §3.
        Loadout imGeraet = Loadout.of(List.of(Card.INFINITY));
        assertEquals(16, Range.reach(LEER, imGeraet));
        assertFalse(Range.covers(LEER, imGeraet, 1000.0));
    }

    @Test
    @DisplayName("Die Grenze ist die Grenze")
    void theEdgeIsTheEdge() {
        assertTrue(Range.covers(LEER, LEER, 16.0));
        assertFalse(Range.covers(LEER, LEER, 16.01));
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen und den Fehlschlag sehen**

Aufruf: `./gradlew test --tests "*RangeTest*"`
Erwartet: Übersetzungsfehler — `Range` gibt es nicht.

- [ ] **Schritt 3: Die Rechnung schreiben**

```java
package dev.devpanda.factorynetwork.upgrade;

/**
 * Wie weit ein Funksignal trägt.
 *
 * <p>Die Zahlen stehen in {@code docs/fernzugriff.md} §3 und hier — sonst
 * nirgends. Wer sie ändert, ändert sie an beiden Stellen, und der Prüflauf
 * rechnet sie nach.
 */
public final class Range {

    /** Ein Mast ohne Karten trägt so weit. */
    public static final int MAST_BASE = 16;

    /**
     * Um so viel zählt eine Karte im Mast mehr als im Gerät.
     *
     * <p><b>Warum am Ort und nicht an der Karte:</b> Es ist dieselbe Karte.
     * Eine, die an zwei Orten verschieden viel hebt, widerspräche der Regel
     * aus dem Ausbausystem — dort hebt eine Karte ihren Wert, und der ist
     * einer. Das Verstärken ist eine Eigenschaft des Masts.
     */
    public static final int MAST_FACTOR = 2;

    /** Was {@link #reach} liefert, wenn es keine Grenze mehr gibt. */
    public static final int UNLIMITED = -1;

    /**
     * Wie weit dieser Mast dieses Gerät erreicht, in Blöcken.
     *
     * @return {@link #UNLIMITED}, wenn der Mast eine Grenzenlos-Karte trägt
     */
    public static int reach(Loadout mast, Loadout device) {
        if (mast.unlimited(Stat.RANGE)) {
            return UNLIMITED;
        }
        return MAST_BASE
                + mast.value(Stat.RANGE) * MAST_FACTOR
                + device.value(Stat.RANGE);
    }

    /**
     * Reicht es über diese Entfernung?
     *
     * <p>Die Grenze zählt noch dazu: Wer genau auf ihr steht, ist drin.
     */
    public static boolean covers(Loadout mast, Loadout device, double distance) {
        int found = reach(mast, device);
        return found == UNLIMITED || distance <= found;
    }

    private Range() {
    }
}
```

- [ ] **Schritt 4: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*RangeTest*"`
Erwartet: sieben Fälle, keine Fehler.

- [ ] **Schritt 5: Die Gegenprobe**

Setze `MAST_FACTOR` auf `1`. Erwartet: `theMastAmplifies` und
`fullyBuiltMatchesTheSpec` schlagen fehl. Danach zurücksetzen.

- [ ] **Schritt 6: Den Entwurf nachziehen**

In `docs/fernzugriff.md` §3 hinter der Tabelle einfügen:

```markdown
**Dieselbe Karte, zwei Wirkungen — und warum das keine ist.** Eine
Reichweitenkarte hebt ihren Wert um acht, überall. Dass sie im Mast
sechzehn bringt, liegt am Mast: Er verdoppelt, was in ihm steckt. Das
Verstärken ist eine Eigenschaft des Orts und nicht der Karte — sonst hätte
dieselbe Karte zwei Werte, und die Regel aus §2 wäre hin.
```

- [ ] **Schritt 7: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/upgrade/Range.java \
        src/test/java/dev/devpanda/factorynetwork/upgrade/RangeTest.java \
        docs/fernzugriff.md
git commit -m "Wie weit ein Funksignal trägt"
```

---

## Aufgabe 2: Der Block

Ein Mast, den man hinstellen kann. Noch ohne Steckplätze und ohne Netz —
erst einmal steht er da.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/block/MastLayout.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/block/MastBlock.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/registry/FnBlocks.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/registry/FnItems.java`
- Ändern: `tools/textures.py` (eine Blocktextur)
- Ändern: `tools/assets.py` (Modell, Blockstate, Loot-Table, Rezept)
- Ändern: `src/main/resources/assets/factorynetwork/lang/de_de.json`, `en_us.json`
- Ändern: `src/test/java/dev/devpanda/factorynetwork/block/MachineLayoutTest.java`

**Schnittstellen:**
- Verbraucht: nichts aus Aufgabe 1.
- Liefert: `FnBlocks.MAST`, `MastLayout.boxes()`.

- [ ] **Schritt 1: Die Form festlegen und den Test erweitern**

Der Mast ist ein Sockel mit einem Schaft darauf, und oben ein Kranz aus
vier Auslegern — die Antenne. Er ist kein Würfel: Wer ihn sieht, soll ihn
von einem Gehäuse unterscheiden können.

```java
package dev.devpanda.factorynetwork.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Maße des Sendemasts — als reine Zahlen.
 *
 * <p>Ein Sockel, ein Schaft darauf, oben vier Ausleger. <b>Er sieht
 * absichtlich nicht aus wie ein Gehäuse:</b> Wer eine Basis abgeht, soll den
 * Block finden, der funkt, ohne jeden anzuklicken.
 *
 * <p>Dieselben Zahlen stehen im Modellskript {@code tools/assets.py};
 * {@code MachineLayoutTest} wacht darüber, dass beide dasselbe sagen.
 */
public final class MastLayout {

    /** Höhe des Sockels. */
    public static final int BASE = 3;

    /** Halbe Breite des Schafts, von der Blockmitte aus. */
    public static final int SHAFT = 3;

    /** Ab hier sitzen die Ausleger. */
    public static final int ARMS = 11;

    /** Und so weit stehen sie ab. */
    public static final int ARM_OUT = 2;

    public static List<int[]> boxes() {
        List<int[]> boxes = new ArrayList<>();
        int near = 8 - SHAFT;
        int far = 8 + SHAFT;

        // Der Sockel, über die volle Grundfläche: Er trägt, und er zeigt,
        // dass der Block am Boden steht.
        boxes.add(new int[] {0, 0, 0, 16, BASE, 16});

        // Der Schaft bis unter die Ausleger.
        boxes.add(new int[] {near, BASE, near, far, ARMS, far});

        // Vier Ausleger, je einer nach Norden, Süden, Westen, Osten.
        boxes.add(new int[] {near, ARMS, near - ARM_OUT, far, ARMS + 2, near});
        boxes.add(new int[] {near, ARMS, far, far, ARMS + 2, far + ARM_OUT});
        boxes.add(new int[] {near - ARM_OUT, ARMS, near, near, ARMS + 2, far});
        boxes.add(new int[] {far, ARMS, near, far + ARM_OUT, ARMS + 2, far});

        // Die Spitze.
        boxes.add(new int[] {7, ARMS + 2, 7, 9, 16, 9});

        return boxes;
    }

    private MastLayout() {
    }
}
```

In `MachineLayoutTest` die Liste erweitern:

```java
            new Machine("mast", MastLayout::boxes, "cube_all"));
```

(Das `)` der vorherigen Zeile wird dabei zum Komma.)

- [ ] **Schritt 2: Den Test laufen lassen und den Fehlschlag sehen**

Aufruf: `./gradlew test --tests "*MachineLayoutTest*"`
Erwartet: FEHLER mit „mast.json fehlt — tools/assets.py laufen lassen".

- [ ] **Schritt 3: Die Textur malen**

In `tools/textures.py` vor `def main()`:

```python
def mast_side():
    """Der Mast: Blech mit einer senkrechten Naht und Nieten.

    Die Naht läuft längs, weil der Block hoch ist und nicht breit — dieselbe
    Textur liegt auf Sockel, Schaft und Auslegern, und was dort quer liefe,
    sähe an jedem der drei anders aus.
    """
    img = surface(seed=81)
    d = ImageDraw.Draw(img)
    raised(img, (1, 1, N - 2, N - 2), hoehe=3)

    # Die Naht in der Mitte, längs.
    d.rectangle([28, 6, 35, 57], fill=blend(BODY_MID, EDGE, 0.35) + (255,))
    recess(img, (28, 6, 35, 57), tiefe=2)
    for y in range(10, 56, 9):
        rivet(img, 31, y, r=2)

    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        rivet(img, x, y, r=2)
    scratches(img, seed=82)
    return img
```

In `main()` bei den Blocktexturen:

```python
    save(mast_side(), "block", "mast_side")
```

Lauf: `python tools/textures.py`

- [ ] **Schritt 4: Modell, Blockstate, Loot und Rezept**

In `tools/assets.py` neben den anderen Blockformen:

```python
def mast_boxes():
    """Die Kästen des Sendemasts — dieselben Zahlen wie in MastLayout."""
    base, shaft, arms, out = MAST_BASE, MAST_SHAFT, MAST_ARMS, MAST_ARM_OUT
    near, far = 8 - shaft, 8 + shaft
    side = {"*": "side"}
    return [
        ([0, 0, 0], [16, base, 16], side),
        ([near, base, near], [far, arms, far], side),
        ([near, arms, near - out], [far, arms + 2, near], side),
        ([near, arms, far], [far, arms + 2, far + out], side),
        ([near - out, arms, near], [near, arms + 2, far], side),
        ([far, arms, near], [far + out, arms + 2, far], side),
        ([7, arms + 2, 7], [9, 16, 9], side),
    ]


def mast_model():
    """Der Sendemast."""
    write(A + "/models/block/mast.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": texture("mast_side"),
            "side": texture("mast_side"),
        },
        "elements": machine_elements(mast_boxes()),
    })
    write(A + "/blockstates/mast.json", {"variants": {"": {"model": block("mast")}}})
    write(A + "/models/item/mast.json", {"parent": block("mast")})
```

Die Konstanten daneben, mit denselben Werten wie `MastLayout`:

```python
# Der Sendemast in Blockpixeln. Dieselben Zahlen stehen in MastLayout.java;
# MachineLayoutTest hält beide zusammen.
MAST_BASE = 3      # Höhe des Sockels
MAST_SHAFT = 3     # halbe Breite des Schafts
MAST_ARMS = 11     # ab hier die Ausleger
MAST_ARM_OUT = 2   # und so weit stehen sie ab
```

Aufruf in `models()` ergänzen: `mast_model()`

Loot-Table und Rezept bei den anderen. Es gibt keine Hilfsfunktion — die
Tabellen stehen in einer Schleife über die Blocknamen (`assets.py:1608`).
Trage `"mast"` dort in die Liste ein, und schreibe daneben:

```python
    # Der Sendemast: ein Netzkern auf einem Gerüst aus Platten und Eisen.
    write(D + "/recipe/mast.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" I ", "PNP", "PPP"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "P": {"item": MOD + ":plate"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":mast", "count": 1},
    })
```

Prüfe den Namen der Loot-Hilfsfunktion an den vorhandenen Einträgen; heißt
sie anders als `block_drop`, nimm die vorhandene.

Lauf: `python tools/assets.py`

- [ ] **Schritt 5: Block und Gegenstand anmelden**

`MastBlock.java`:

```java
package dev.devpanda.factorynetwork.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Der Sendemast: von hier aus funkt das Netz.
 *
 * <p>Er hat keine Vorderseite — ein Mast steht, und wohin seine Ausleger
 * zeigen, ändert nichts an dem, was er tut.
 */
public class MastBlock extends Block {

    private static final VoxelShape SHAPE = FacingShapes.whole(MastLayout.boxes());

    public MastBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }
}
```

In `FnBlocks` neben den anderen Maschinen:

```java
    /**
     * Von hier aus funkt das Netz.
     *
     * <p>noOcclusion, weil zwischen Sockel und Auslegern Luft ist.
     */
    public static final DeferredBlock<Block> MAST = BLOCKS.register("mast",
            () -> new dev.devpanda.factorynetwork.block.MastBlock(
                    machineProperties().noOcclusion()));
```

In `FnItems`:

```java
    public static final DeferredItem<BlockItem> MAST =
            ITEMS.registerSimpleBlockItem(FnBlocks.MAST);
```

- [ ] **Schritt 6: Die Namen eintragen**

`de_de.json`: `"block.factorynetwork.mast": "Sendemast",`
`en_us.json`: `"block.factorynetwork.mast": "Transmitter Mast",`

- [ ] **Schritt 7: Ansehen und prüfen**

```
python tools/modellblick.py block/mast --out build/modellblick/mast.png
./gradlew test
```

Erwartet: keine Fehler. Sieh dir das Bild an — der Mast soll sich auf einen
Blick von einem Gehäuse unterscheiden. Tut er das nicht, ändere die Zahlen
in `MastLayout` und `assets.py` gemeinsam; der Prüflauf meldet es, wenn nur
eine Seite wandert.

- [ ] **Schritt 8: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/block/Mast*.java \
        src/main/java/dev/devpanda/factorynetwork/registry/ \
        src/test/java/dev/devpanda/factorynetwork/block/MachineLayoutTest.java \
        tools/ src/main/resources/
git commit -m "Ein Mast, der noch nichts funkt"
```

---

## Aufgabe 3: Steckplätze und Fenster

Der Mast bekommt vier Plätze und ein Fenster, in dem man sie bestückt.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/block/entity/MastBlockEntity.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/client/menu/MastMenu.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/client/screen/MastScreen.java`
- Ändern: `MastBlock.java` (Rechtsklick öffnet, `EntityBlock`)
- Ändern: `registry/FnBlockEntities.java`, `registry/FnMenus.java`
- Ändern: `client/FnClient.java` (Fenster anmelden)
- Ändern: `src/main/resources/assets/factorynetwork/lang/*.json`

**Schnittstellen:**
- Verbraucht: `UpgradeSlots` aus Teil 1.
- Liefert: `MastBlockEntity.slots()`, `MastBlockEntity.loadout()`,
  `FnBlockEntities.MAST`, `FnMenus.MAST`.

- [ ] **Schritt 1: Die BlockEntity**

Vier Plätze, gespeichert und zum Client synchronisiert — sonst zeigt das
Fenster beim Öffnen leere Plätze und füllt sie erst einen Tick später.

```java
package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import dev.devpanda.factorynetwork.upgrade.Loadout;
import dev.devpanda.factorynetwork.upgrade.UpgradeSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Was im Sendemast steckt.
 *
 * <p>Vier Plätze, und mehr Zustand hat er nicht: Wie weit er trägt, rechnet
 * {@code Range} aus dem, was darin liegt.
 */
public class MastBlockEntity extends BlockEntity {

    /** So viele Plätze hat ein Mast — siehe fernzugriff.md §3. */
    public static final int SLOTS = 4;

    private final UpgradeSlots slots = new UpgradeSlots(SLOTS);

    public MastBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.MAST.get(), pos, state);
    }

    public UpgradeSlots slots() {
        return slots;
    }

    public Loadout loadout() {
        return slots.loadout();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        slots.save(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        slots.save(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
```

- [ ] **Schritt 2: Menü und Fenster**

**Hier gibt es keinen Code im Plan, und das ist Absicht.** Menü und Fenster
folgen dem Serverschrank Zeile für Zeile: `ShelfScreen`, sein Menü und seine
Registrierung sind die Vorlage. Lies sie und baue dieselbe Form mit vier
Plätzen statt achtundvierzig.

**Was dabei anders ist:** Jeder Platz nimmt nur, was `UpgradeSlots.accepts`
durchlässt. Das gehört in die `Slot`-Unterklasse des Menüs
(`mayPlace(ItemStack)`), nicht in die BlockEntity — sonst kann der Spieler
den Stapel zwar hineinziehen und er verschwindet wieder.

- [ ] **Schritt 3: Der Rechtsklick**

`MastBlock` wird `EntityBlock` und öffnet das Menü in
`useWithoutItem` — dasselbe Muster wie `GatewayBlock:56` mit
`SimpleMenuProvider`.

Und: `onRemove` muss den Inhalt fallen lassen. Wer einen bestückten Mast
abbaut, verliert sonst vier Karten. Der Serverschrank tut das schon; sieh
dort nach.

- [ ] **Schritt 4: Prüfen**

Ein GameTest: Mast setzen, Karte hineinlegen, Block abbauen, prüfen dass die
Karte am Boden liegt. Die vorhandenen GameTests stehen in
`test/FactoryNetworkGameTests.java`.

Aufruf: `./gradlew runGameTestServer`

- [ ] **Schritt 5: Committen**

```bash
git commit -m "Vier Plätze im Mast, und ein Fenster dafür"
```

---

## Aufgabe 4: Der Mast am Netz

Bis hierhin steht ein Block mit Plätzen, den das Netz nicht kennt.

**Dateien:**
- Ändern: `network/Channels.java` (eine Konstante)
- Ändern: `network/Power.java` (eine Konstante)
- Ändern: `network/FactoryGraph.java` (Masten einsammeln)
- Ändern: `block/entity/ControllerBlockEntity.java` (Strom summieren)
- Ändern: `src/main/java/dev/devpanda/factorynetwork/upgrade/Loadout.java`
  (`count` dazu)
- Ändern: `src/test/java/dev/devpanda/factorynetwork/upgrade/LoadoutTest.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/upgrade/MastCostTest.java`

**Schnittstellen:**
- Verbraucht: `Range`, `MastBlockEntity`.
- Liefert: `FactoryGraph.masts()`, `Channels.MAST`, `Power.mast(Loadout)`.

- [ ] **Schritt 1: Der fehlschlagende Test**

```java
package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.network.Power;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was ein Mast kostet — und dass Reichweite eine Entscheidung bleibt.
 */
class MastCostTest {

    @Test
    @DisplayName("Ein Mast ohne Karten zieht wenig")
    void aBareMastIsCheap() {
        assertEquals(Power.MAST_BASE, Power.mast(Loadout.of(List.of())));
    }

    @Test
    @DisplayName("Jede Karte kostet Strom")
    void everyCardCosts() {
        // Ohne das wäre Reichweite ein Häkchen und keine Entscheidung: Wer
        // vier Karten hat, steckt vier hinein, und niemand überlegt.
        int leer = Power.mast(Loadout.of(List.of()));
        int voll = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        assertTrue(voll > leer, "vier Karten kosten nicht mehr als keine");
        assertEquals(leer + 4 * Power.MAST_PER_CARD, voll);
    }

    @Test
    @DisplayName("Die Grenzenlos-Karte kostet am meisten")
    void infinityCostsMost() {
        int voll = Power.mast(Loadout.ofCounts(Map.of(Card.RANGE, 4)));
        int endgame = Power.mast(Loadout.of(List.of(Card.INFINITY)));
        assertTrue(endgame > voll,
                "unbegrenzt kostet weniger als vier Karten — dann nimmt sie jeder");
    }
}
```

- [ ] **Schritt 2: Die Kosten**

In `network/Power.java`:

```java
    /** Was ein Mast ohne Karten zieht, je Tick. */
    public static final int MAST_BASE = 4;

    /** Und was jede Reichweitenkarte darin dazu kostet. */
    public static final int MAST_PER_CARD = 6;

    /**
     * Was die Grenzenlos-Karte kostet.
     *
     * <p>Mehr als vier gewöhnliche zusammen: Sonst nimmt sie jeder, sobald
     * er sie hat, und die Reichweite ist keine Entscheidung mehr.
     */
    public static final int MAST_UNLIMITED = 40;

    /** Was dieser Mast zieht, je Tick. */
    public static int mast(dev.devpanda.factorynetwork.upgrade.Loadout loadout) {
        if (loadout.unlimited(dev.devpanda.factorynetwork.upgrade.Stat.RANGE)) {
            return MAST_BASE + MAST_UNLIMITED;
        }
        return MAST_BASE + loadout.count(
                dev.devpanda.factorynetwork.upgrade.Card.RANGE) * MAST_PER_CARD;
    }
```

**`Loadout.count` gibt es noch nicht.** Der erste Entwurf dieses Plans hat
den Wert durch den Kartenschritt geteilt, um auf die Stückzahl zu kommen —
ein Umweg, der davon lebt, dass eine Karte genau acht hebt, und der bei
einer zweiten Karte auf denselben Wert falsch würde. Lege die Methode in
`Loadout` an und prüfe sie im `LoadoutTest`:

```java
    /** Wie oft dieser Ausbau steckt. */
    public int count(Upgrade upgrade) {
        int found = 0;
        for (Upgrade installed : this.installed) {
            if (installed == upgrade) {
                found++;
            }
        }
        return found;
    }
```

```java
    @Test
    @DisplayName("Und man kann zählen, wie oft einer steckt")
    void countingIsPossible() {
        // Wer Stückzahlen braucht — die Stromrechnung tut das —, soll sie
        // nicht aus einem Wert zurückrechnen müssen. Das ginge nur, solange
        // es je Wert eine Kartenart gibt.
        Loadout mixed = Loadout.of(List.of(Card.RANGE, Card.RANGE, Ability.WIRELESS));
        assertEquals(2, mixed.count(Card.RANGE));
        assertEquals(1, mixed.count(Ability.WIRELESS));
        assertEquals(0, mixed.count(Card.INFINITY));
    }
```

In `network/Channels.java`:

```java
    /** Ein Mast kostet einen Kanal — er ist ein Gerät wie jedes andere. */
    public static final int MAST = 1;
```

- [ ] **Schritt 3: In den Graphen aufnehmen**

`FactoryGraph` sammelt beim Aufbau die Positionen je Blockart (Zeile 224
folgende). Trage die Masten daneben ein, gib dem Record ein Feld `masts`
und einen Zugriff `masts()`, und ziehe die drei Stellen nach, die den
Konstruktor rufen.

Im `ControllerBlockEntity` bei der Stromsumme (Zeile 415) dazu:

```java
        for (BlockPos pos : graph.masts()) {
            if (level.getBlockEntity(pos) instanceof MastBlockEntity mast) {
                total += dev.devpanda.factorynetwork.network.Power.mast(mast.loadout());
            }
        }
```

- [ ] **Schritt 4: Prüfen und committen**

```
./gradlew test
./gradlew runGameTestServer
git commit -m "Der Mast kostet einen Kanal und Strom nach Ausbau"
```

---

## Was am Ende steht

Ein Block, der am Netz hängt, Kanal und Strom kostet, vier Steckplätze hat
und ausrechnen kann, wie weit er trägt. **Empfangen kann ihn noch niemand.**

**Was danach kommt:** Teil 2b — Wireless Terminal und Laptop, die Kopplung
per Rechtsklick, der Akku als `IEnergyStorage` am ItemStack. Der bekommt
einen eigenen Plan, und `Range.covers` ist die Naht, an der er ansetzt.

**Was beim Bauen aufgefallen sein wird und in den Entwurf gehört:** Ob vier
Plätze am Mast sich richtig anfühlen, und ob 16 Blöcke Grundreichweite
reichen, um den Mast überhaupt sinnvoll zu machen. Beides sind Zahlen an
einer Stelle.
