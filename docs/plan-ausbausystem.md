# Ausbausystem — Umsetzungsplan

> **Für ausführende Agenten:** Aufgabe für Aufgabe. Erst der Test, dann der
> Code, dann der Lauf, dann der Commit.

**Ziel:** Steckplätze, in die Module und Karten passen, und die Rechnung
darauf — welche Fähigkeiten ein Gerät hat und wie hoch seine Werte sind.

**Vorgehen:** Die Rechnung ist reines Java ohne Minecraft-Typen und lässt
sich damit in gewöhnlichen Tests prüfen, wie das Paket `lang`. Darum herum
liegen die Gegenstände und ein Behälter, der beides speichert. Funk und Geräte
kommen in Teil 2 und 3; hier entsteht nur, worauf sie stehen.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5.

**Entwurf:** `docs/fernzugriff.md`, Abschnitte 2 und 7.

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.**
- **Echte Umlaute**, keine Unicode-Escapes.
- Das Paket `upgrade` bleibt ohne Minecraft-Typen in den Klassen, die Tests
  laden.
- Nach jeder Aufgabe committen, Meldungen deutsch, ohne Präfixe.
- `./gradlew test` für die schnellen Tests.
- Modelle und Texturen entstehen in `tools/`, nicht von Hand — sonst
  überschreibt der nächste Lauf sie.

## Verifizierter Bestand

Alles hier wurde vor dem Schreiben im Code nachgesehen:

| Was | Wo |
|---|---|
| `ServerPart` ist ein Enum mit Registrierungspräfix | `item/ServerPart.java:14` |
| Der Serverschrank hält seine Plätze über feste Indexrechnung | `block/entity/RackBlockEntity.java:37-75` |
| Er erbt den Behälter von `ShelfBlockEntity` | `block/entity/RackBlockEntity.java:35,49` |
| Gegenstände werden über `DeferredRegister.Items` angemeldet | `registry/FnItems.java:22` |
| Gegenstandsmodelle erzeugt `item_model(name)` | `tools/assets.py`, Funktion `item_model` |
| Ein Gegenstand mit Körper braucht einen Eintrag in `ITEM_BODIES` | `tools/assets.py`, Karte `ITEM_BODIES` |
| Gegenstandstexturen malt `tools/textures.py` in `main()` | `tools/textures.py`, Aufrufe `save(..., "item", ...)` |
| Reine Tests liegen ohne Minecraft-Bezug in `src/test/java` | `lang/DeviceProfileTest.java` |
| Es gibt kein Ausbausystem und keine Karten | keine Treffer in `src/` und `docs/konzept.md` |

---

## Aufgabe 1: Die Arten und die Rechnung

Reines Java. Hier entsteht die Regel, die der ganze Rest voraussetzt: Ein
Modul gibt eine Fähigkeit, eine Karte hebt einen Wert.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Upgrade.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Ability.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Card.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Stat.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/Loadout.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/upgrade/LoadoutTest.java`

**Schnittstellen:**
- Verbraucht: nichts.
- Liefert: `Loadout.of(List<? extends Upgrade>)`, `Loadout.has(Ability)`,
  `Loadout.value(Stat)`, `Loadout.unlimited(Stat)`, `Upgrade.id()`.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Regel, die das ganze Ausbausystem trägt: Ein Modul gibt eine
 * Fähigkeit, eine Karte hebt einen Wert.
 */
class LoadoutTest {

    @Test
    @DisplayName("Ohne Bestückung kann ein Gerät nichts und hat keinen Wert")
    void emptyMeansNothing() {
        Loadout empty = Loadout.of(List.of());
        assertFalse(empty.has(Ability.WIRELESS));
        assertEquals(0, empty.value(Stat.RANGE));
        assertFalse(empty.unlimited(Stat.RANGE));
    }

    @Test
    @DisplayName("Ein Modul schaltet seine Fähigkeit frei und sonst keine")
    void aModuleUnlocksItsOwnAbility() {
        Loadout one = Loadout.of(List.of(Ability.WIRELESS));
        assertTrue(one.has(Ability.WIRELESS));
        // Und es hebt keinen Wert: Dafür sind Karten da.
        assertEquals(0, one.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Gleiche Karten addieren sich")
    void equalCardsAddUp() {
        assertEquals(8, Loadout.of(List.of(Card.RANGE)).value(Stat.RANGE));
        assertEquals(24, Loadout.of(
                List.of(Card.RANGE, Card.RANGE, Card.RANGE)).value(Stat.RANGE));
    }

    @Test
    @DisplayName("Die Infinity-Karte hebt die Grenze auf, statt sie zu heben")
    void infinityLiftsTheLimit() {
        Loadout endgame = Loadout.of(List.of(Card.RANGE, Card.INFINITY));
        assertTrue(endgame.unlimited(Stat.RANGE));
        // Der Zahlenwert bleibt daneben stehen und wird nicht gebraucht —
        // wer unlimited fragt, fragt value nicht mehr.
        assertEquals(8, endgame.value(Stat.RANGE));
    }

    @Test
    @DisplayName("Jede Karte wirkt in genau einem Punkt")
    void everyCardHitsExactlyOneStat() {
        // Ohne diese Probe wächst hier über die Zeit ein zweites Regelwerk:
        // eine Karte, die drei Dinge zugleich tut, und niemand weiß mehr,
        // was ein Steckplatz kostet.
        for (Card card : Card.values()) {
            assertTrue(card.stat() != null, card + " hebt keinen Wert");
        }
    }

    @Test
    @DisplayName("Module und Karten teilen sich die Steckplätze")
    void bothKindsShareTheSlots() {
        Loadout mixed = Loadout.of(List.of(Ability.WIRELESS, Card.RANGE, Card.RANGE));
        assertTrue(mixed.has(Ability.WIRELESS));
        assertEquals(16, mixed.value(Stat.RANGE));
        assertEquals(3, mixed.installed().size());
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen und den Fehlschlag sehen**

Aufruf: `./gradlew test --tests "*LoadoutTest*"`
Erwartet: Übersetzungsfehler — `Upgrade`, `Ability`, `Card`, `Stat` und
`Loadout` gibt es nicht.

- [ ] **Schritt 3: Die vier kleinen Typen anlegen**

`Upgrade.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

/**
 * Was in einen Steckplatz passt.
 *
 * <p><b>Zwei Arten, und der Unterschied ist scharf:</b> Ein Modul (siehe {@link Ability})
 * gibt eine Fähigkeit, die vorher nicht da war — eine Anzeigetafel kann ohne
 * Funk-Modul keinen Funk. Eine {@link Card} hebt einen Wert an einer
 * Fähigkeit, die schon da ist — der Laptop funkt auch ohne Karte, nur nicht
 * weit.
 *
 * <p>Beide belegen denselben Platz. Wer alles will, muss entscheiden, was er
 * weglässt; das ist der Sinn der festen Platzzahl.
 *
 * <p>Ohne Minecraft-Bezug, damit die Rechnung darauf in gewöhnlichen Tests
 * prüfbar bleibt — dasselbe Vorgehen wie im Paket {@code lang}.
 */
public sealed interface Upgrade permits Ability, Card {

    /** Der Name im Registrierungspfad, etwa {@code wireless_module}. */
    String id();
}
```

`Ability.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

/**
 * Die Fähigkeiten, die ein Modul freischalten kann.
 *
 * <p><b>Sie heißt nach der Fähigkeit und nicht nach dem Modul</b>, weil
 * {@code Module} in Java seit Version 9 vergeben ist: {@code java.lang.Module}
 * steht in jeder Datei ohne Import zur Verfügung. Ein eigener Typ desselben
 * Namens funktioniert zwar — der Import gewinnt —, aber er stellt jedem Leser
 * und jedem Werkzeug ein Bein. Der Gegenstand heißt weiter Funk-Modul.
 */
public enum Ability implements Upgrade {

    /** Ohne Kabel am Netz — für die Anzeigetafel. */
    WIRELESS("wireless_module");

    private final String id;

    Ability(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
```

`Stat.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

/** Die Werte, die Karten heben können. */
public enum Stat {

    /** Wie weit ein Funksignal trägt, in Blöcken. */
    RANGE
}
```

`Card.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

/**
 * Die Karten und was sie heben.
 *
 * <p><b>Gleiche Karten addieren sich, statt in Stufen aufzurüsten.</b> Ein
 * Stufensystem — Reichweite I, II, III — macht die alte Karte wertlos, sobald
 * die neue da ist. Vier gleiche Karten in vier Plätzen halten den Wert an der
 * Zahl der Plätze fest, und die ist die eigentliche Entscheidung.
 */
public enum Card implements Upgrade {

    /** Acht Blöcke mehr Reichweite, je Stück. */
    RANGE("range_card", Stat.RANGE, 8, false),

    /**
     * Hebt die Reichweitengrenze ganz auf.
     *
     * <p>Eine Karte und kein Modul: Sie schafft nichts Neues, sie hebt eine
     * Grenze auf. Ihr Zahlenwert ist null, weil ihn niemand liest — wer
     * {@code unlimited} fragt, fragt {@code value} nicht mehr.
     */
    INFINITY("infinity_card", Stat.RANGE, 0, true);

    private final String id;
    private final Stat stat;
    private final int step;
    private final boolean unlimited;

    Card(String id, Stat stat, int step, boolean unlimited) {
        this.id = id;
        this.stat = stat;
        this.step = step;
        this.unlimited = unlimited;
    }

    @Override
    public String id() {
        return id;
    }

    /** Worauf sie wirkt — genau ein Punkt, nie zwei. */
    public Stat stat() {
        return stat;
    }

    /** Um wie viel sie ihn hebt, je Stück. */
    public int step() {
        return step;
    }

    /** Ob sie die Grenze ganz aufhebt. */
    public boolean unlimited() {
        return unlimited;
    }
}
```

- [ ] **Schritt 4: Die Rechnung anlegen**

`Loadout.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

import java.util.List;

/**
 * Was eine Bestückung kann, und wie hoch ihre Werte sind.
 *
 * <p>Zwei Fragen, mehr nicht: <i>Habe ich diese Fähigkeit?</i> und <i>wie
 * hoch ist dieser Wert?</i> Alles, was Steckplätze hat, stellt sie — der
 * Sendemast, die Geräte, die Anzeigetafel.
 */
public record Loadout(List<Upgrade> installed) {

    public Loadout {
        installed = List.copyOf(installed);
    }

    public static Loadout of(List<? extends Upgrade> installed) {
        // Der Umweg über ArrayList ist nötig: List.copyOf einer Liste von
        // Untertypen bleibt eine Liste von Untertypen, und der Record will
        // eine von Upgrade.
        return new Loadout(new java.util.ArrayList<Upgrade>(installed));
    }

    /** Steckt ein Modul dieser Art darin? */
    public boolean has(Ability ability) {
        return installed.contains(ability);
    }

    /**
     * Die Summe aller Karten auf diesen Wert.
     *
     * <p>Ohne die Infinity-Karte: Deren Schritt ist null, und wer sie steckt,
     * fragt {@link #unlimited} statt dieser Zahl.
     */
    public int value(Stat stat) {
        int sum = 0;
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat) {
                sum += card.step();
            }
        }
        return sum;
    }

    /** Hebt eine der Karten die Grenze dieses Werts auf? */
    public boolean unlimited(Stat stat) {
        for (Upgrade upgrade : installed) {
            if (upgrade instanceof Card card && card.stat() == stat
                    && card.unlimited()) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Schritt 5: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*LoadoutTest*"`
Erwartet: sechs Fälle, keine Fehler.

- [ ] **Schritt 6: Die Gegenprobe**

Setze in `Card.RANGE` den Schritt von `8` auf `4` und lasse den Test erneut
laufen. Erwartet: `equalCardsAddUp` schlägt fehl. Danach zurücksetzen.

Ohne diese Probe steht nicht fest, dass der Test die Zahl wirklich liest.

- [ ] **Schritt 7: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/upgrade/ \
        src/test/java/dev/devpanda/factorynetwork/upgrade/
git commit -m "Module geben Fähigkeiten, Karten heben Werte"
```

---

## Aufgabe 2: Die Gegenstände

Drei Gegenstände, die es zu greifen gibt. Sie tun noch nichts — es gibt
weder Funk noch ein Gerät mit Steckplätzen. Sie entstehen trotzdem zuerst,
weil Aufgabe 3 sie braucht, um einen Behälter zu füllen.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/item/UpgradeItem.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/registry/FnItems.java`
- Ändern: `tools/textures.py` (drei neue Gegenstandstexturen)
- Ändern: `tools/assets.py` (drei Einträge in `ITEM_BODIES`, drei Aufrufe)
- Ändern: `src/main/resources/assets/factorynetwork/lang/de_de.json` und
  `en_us.json`
- Ändern: `tools/assets.py` (drei Rezepte)
- Test: `src/test/java/dev/devpanda/factorynetwork/upgrade/UpgradeItemTest.java`

**Schnittstellen:**
- Verbraucht: `Upgrade`, `Ability`, `Card` aus Aufgabe 1.
- Liefert: `UpgradeItem.upgrade()`, `FnItems.WIRELESS_MODULE`,
  `FnItems.RANGE_CARD`, `FnItems.INFINITY_CARD`,
  `UpgradeItem.upgradeOf(ItemStack)`.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jeder Ausbau ist auch ein Gegenstand — mit Modell, Textur und Namen.
 *
 * <p>Ein Ausbau, den es in Aufzählung und Rezept gibt, aber nicht im
 * Inventar, fällt erst im Spiel auf, und dort auch nur dem, der ihn baut.
 */
class UpgradeItemTest {

    private static final Path ASSETS =
            Path.of("src/main/resources/assets/factorynetwork");

    private static String read(Path file) throws IOException {
        assertTrue(Files.exists(file), file + " fehlt");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Zu jedem Ausbau gibt es Textur, Modell und zwei Namen")
    void everyUpgradeIsAnItem() throws IOException {
        String german = read(ASSETS.resolve("lang/de_de.json"));
        String english = read(ASSETS.resolve("lang/en_us.json"));
        for (Upgrade upgrade : upgrades()) {
            String id = upgrade.id();
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + id + ".png")),
                    "Textur fehlt: " + id);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")),
                    "Modell fehlt: " + id);
            String key = "\"item.factorynetwork." + id + "\"";
            assertTrue(german.contains(key), "deutscher Name fehlt: " + id);
            assertTrue(english.contains(key), "englischer Name fehlt: " + id);
        }
    }

    private static java.util.List<Upgrade> upgrades() {
        java.util.List<Upgrade> all = new java.util.ArrayList<>();
        all.addAll(java.util.List.of(Ability.values()));
        all.addAll(java.util.List.of(Card.values()));
        return all;
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen und den Fehlschlag sehen**

Aufruf: `./gradlew test --tests "*UpgradeItemTest*"`
Erwartet: FEHLER mit „Textur fehlt: wireless_module".

- [ ] **Schritt 3: Die Gegenstandsklasse anlegen**

`UpgradeItem.java`:

```java
package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.upgrade.Upgrade;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Modul oder eine Karte als Gegenstand.
 *
 * <p>Er trägt keine Daten: Was er tut, steht in der {@link Upgrade}, und die
 * hängt an der Gegenstandsart. Zwei Reichweitenkarten sind dasselbe und
 * stapeln sich deshalb.
 */
public class UpgradeItem extends Item {

    private final Upgrade upgrade;

    public UpgradeItem(Properties properties, Upgrade upgrade) {
        super(properties);
        this.upgrade = upgrade;
    }

    public Upgrade upgrade() {
        return upgrade;
    }

    /** Welcher Ausbau in diesem Stapel steckt, oder {@code null}. */
    public static Upgrade upgradeOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem item ? item.upgrade() : null;
    }
}
```

- [ ] **Schritt 4: Die drei Gegenstände anmelden**

In `registry/FnItems.java` hinter den Kabelgegenständen einfügen:

```java
    /**
     * Die Ausbauten: ein Modul und zwei Karten.
     *
     * <p>Sie stapeln sich, weil zwei gleiche Karten dasselbe tun — und weil
     * gleiche Karten sich addieren, hat man selten nur eine.
     */
    public static final DeferredItem<Item> WIRELESS_MODULE = ITEMS.registerItem(
            dev.devpanda.factorynetwork.upgrade.Ability.WIRELESS.id(),
            properties -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    properties, dev.devpanda.factorynetwork.upgrade.Ability.WIRELESS));

    public static final DeferredItem<Item> RANGE_CARD = ITEMS.registerItem(
            dev.devpanda.factorynetwork.upgrade.Card.RANGE.id(),
            properties -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    properties, dev.devpanda.factorynetwork.upgrade.Card.RANGE));

    public static final DeferredItem<Item> INFINITY_CARD = ITEMS.registerItem(
            dev.devpanda.factorynetwork.upgrade.Card.INFINITY.id(),
            properties -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    properties, dev.devpanda.factorynetwork.upgrade.Card.INFINITY));
```

Die Form ist dieselbe wie bei `LABEL_GUN` und `CONNECTOR` in derselben Datei
(`FnItems.java:90,96`): `ITEMS.register(name, () -> new Item(...))`, nicht
`registerItem`.

- [ ] **Schritt 5: Die Texturen malen**

In `tools/textures.py` vor `def main()` einfügen. Die Farben und Hilfen sind
dieselben, die alle anderen Gegenstände benutzen:

```python
def upgrade_card(ton, zeichen):
    """Eine Karte: Platine mit Kontaktleiste und einem Zeichen darauf.

    Alle Karten teilen sich die Form — was eine tut, sagt allein das Zeichen.
    Wer drei verschiedene Formen malt, macht aus einem Ausbausystem drei
    Einzelstücke.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([12, 16, 51, 47], fill=255)
    img.alpha_composite(masked_surface(mask, blend(ton, LIGHT, 0.25),
                                       blend(ton, EDGE, 0.45), seed=700))
    d = ImageDraw.Draw(img)
    d.rectangle([12, 16, 51, 47], outline=EDGE + (255,))
    raised(img, (12, 16, 51, 47), hoehe=2)

    # Die Kontaktleiste unten: daran erkennt man auf einen Blick, dass es
    # etwas zum Hineinstecken ist.
    for x in range(16, 48, 6):
        d.rectangle([x, 44, x + 3, 47], fill=BRASS + (255,))
        d.point((x, 44), fill=BRASS_HI + (255,))

    d.rectangle([18, 21, 45, 40], fill=blend(BODY_MID, EDGE, 0.4) + (255,))
    recess(img, (18, 21, 45, 40), tiefe=2)
    zeichen(d)
    scratches(img, seed=701)
    return img


def range_card():
    """Drei Bögen: ein Signal, das nach außen läuft."""
    def zeichen(d):
        for i, breite in enumerate((6, 11, 16)):
            farbe = blend(ACCENT, (12, 18, 14), 0.2 + i * 0.2) + (255,)
            d.arc([31 - breite, 30 - breite, 31 + breite, 30 + breite],
                  start=225, end=315, fill=farbe, width=2)
        d.rectangle([30, 29, 32, 31], fill=ACCENT + (255,))
    return upgrade_card(BODY_TOP, zeichen)


def infinity_card():
    """Die liegende Acht — die Grenze, die es nicht mehr gibt."""
    def zeichen(d):
        farbe = blend(ACCENT, LIGHT, 0.35) + (255,)
        d.ellipse([21, 25, 31, 36], outline=farbe, width=2)
        d.ellipse([32, 25, 42, 36], outline=farbe, width=2)
    return upgrade_card(blend(BODY_TOP, ACCENT, 0.12), zeichen)


def wireless_module():
    """Ein Modul: kürzer als eine Karte, mit Antenne statt Zeichen.

    Es sieht absichtlich anders aus als eine Karte. Wer im Inventar steht,
    soll ohne Tooltip sehen, ob er eine Fähigkeit oder einen Wert in der Hand
    hat.
    """
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rectangle([14, 28, 49, 47], fill=255)
    img.alpha_composite(masked_surface(mask, blend(BODY_TOP, LIGHT, 0.25),
                                       blend(BODY_MID, EDGE, 0.45), seed=702))
    d = ImageDraw.Draw(img)
    d.rectangle([14, 28, 49, 47], outline=EDGE + (255,))
    raised(img, (14, 28, 49, 47), hoehe=2)
    for x in range(18, 48, 6):
        d.rectangle([x, 44, x + 3, 47], fill=BRASS + (255,))

    # Die Antenne steht über dem Korpus: die Fähigkeit, die das Modul gibt.
    d.rectangle([30, 12, 33, 30], fill=blend(BODY_TOP, LIGHT, 0.4) + (255,))
    farbe = blend(ACCENT, LIGHT, 0.3) + (255,)
    for i, breite in enumerate((5, 9)):
        d.arc([31 - breite, 13 - breite, 31 + breite, 13 + breite],
              start=200, end=340, fill=farbe, width=2)
    scratches(img, seed=703)
    return img
```

In `main()` bei den Gegenstandstexturen ergänzen:

```python
    save(range_card(), "item", "range_card")
    save(infinity_card(), "item", "infinity_card")
    save(wireless_module(), "item", "wireless_module")
```

Lauf: `python tools/textures.py`

- [ ] **Schritt 6: Modelle und Rezepte erzeugen**

In `tools/assets.py` in `ITEM_BODIES` ergänzen — die Umrisse sind die
Rechtecke aus Schritt 5, auf ganze Blockpixel nach außen gerundet:

```python
    "range_card": (3, 4, 13, 12, 1),
    "infinity_card": (3, 4, 13, 12, 1),
    "wireless_module": (3, 3, 13, 12, 2),
```

Eine Karte ist einen Blockpixel dick, ein Modul zwei: Es ist ein Gerät, sie
ist eine Platine.

Bei den Gegenstandsmodellen ergänzen:

```python
    for name in ("range_card", "infinity_card", "wireless_module"):
        item_model(name)
```

Und bei den Rezepten:

Es gibt keine Hilfsfunktion für Rezepte — sie stehen als Wörterbuch da,
genau wie `controller` und `cable` bei `assets.py:1639,1651`. Dieselbe Form:

```python
    # Die Reichweitenkarte: Kupfer außen, ein Kristall in der Mitte, Platten
    # als Boden. Zwei je Handgriff — man braucht selten nur eine.
    write(D + "/recipe/range_card.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["CCC", "CKC", "PPP"],
        "key": {
            "C": {"item": "minecraft:copper_ingot"},
            "K": {"item": MOD + ":crystal"},
            "P": {"item": MOD + ":plate"},
        },
        "result": {"id": MOD + ":range_card", "count": 2},
    })

    # Die Grenzenlos-Karte: vier Reichweitenkarten um einen Netzkern.
    write(D + "/recipe/infinity_card.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["RKR", "KNK", "RKR"],
        "key": {
            "R": {"item": MOD + ":range_card"},
            "K": {"item": MOD + ":crystal"},
            "N": {"item": MOD + ":core_network"},
        },
        "result": {"id": MOD + ":infinity_card", "count": 1},
    })

    # Das Funk-Modul: eine Reichweitenkarte in einem Gehäuse aus Platten.
    write(D + "/recipe/wireless_module.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" P ", "PRP", " P "],
        "key": {
            "P": {"item": MOD + ":plate"},
            "R": {"item": MOD + ":range_card"},
        },
        "result": {"id": MOD + ":wireless_module", "count": 1},
    })
```

Lauf: `python tools/assets.py`

- [ ] **Schritt 7: Die Namen eintragen**

In `assets/factorynetwork/lang/de_de.json`:

```json
  "item.factorynetwork.range_card": "Reichweitenkarte",
  "item.factorynetwork.infinity_card": "Grenzenlos-Karte",
  "item.factorynetwork.wireless_module": "Funk-Modul",
```

In `en_us.json`:

```json
  "item.factorynetwork.range_card": "Range Card",
  "item.factorynetwork.infinity_card": "Infinity Card",
  "item.factorynetwork.wireless_module": "Wireless Module",
```

- [ ] **Schritt 8: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*UpgradeItemTest*"`
Erwartet: ein Fall, kein Fehler.

Dann der ganze Lauf: `./gradlew test`
Erwartet: keine Fehler. `ItemModelTest` prüft die drei neuen Modelle mit —
sie haben eigene Kästen und dürfen deshalb nicht von `item/generated` erben.

- [ ] **Schritt 9: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/item/UpgradeItem.java \
        src/main/java/dev/devpanda/factorynetwork/registry/FnItems.java \
        src/test/java/dev/devpanda/factorynetwork/upgrade/UpgradeItemTest.java \
        tools/textures.py tools/assets.py \
        src/main/resources/assets/factorynetwork/
git commit -m "Ein Funk-Modul und zwei Karten, zum Anfassen"
```

---

## Aufgabe 3: Der Behälter

Ein kleiner Behälter mit fester Platzzahl, der nur Ausbauten annimmt und die
Rechnung aus Aufgabe 1 daraus baut. Blöcke und Gegenstände benutzen ihn in
Teil 2; hier entsteht er allein und wird allein geprüft.

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/upgrade/UpgradeSlots.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/upgrade/UpgradeSlotsTest.java`

**Schnittstellen:**
- Verbraucht: `Loadout`, `Upgrade`, `UpgradeItem.upgradeOf(ItemStack)`.
- Liefert: `new UpgradeSlots(int)`, `UpgradeSlots.size()`,
  `UpgradeSlots.accepts(ItemStack)`, `UpgradeSlots.set(int, ItemStack)`,
  `UpgradeSlots.get(int)`, `UpgradeSlots.loadout()`,
  `UpgradeSlots.save(CompoundTag, HolderLookup.Provider)`,
  `UpgradeSlots.load(CompoundTag, HolderLookup.Provider)`.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

```java
package dev.devpanda.factorynetwork.upgrade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Behälter nimmt nur Ausbauten, und die Zahl der Plätze ist die
 * eigentliche Entscheidung eines Geräts.
 */
class UpgradeSlotsTest {

    @Test
    @DisplayName("Ein neuer Behälter ist leer und kann nichts")
    void freshSlotsAreEmpty() {
        UpgradeSlots slots = new UpgradeSlots(4);
        assertEquals(4, slots.size());
        assertFalse(slots.loadout().has(Ability.WIRELESS));
        assertEquals(0, slots.loadout().value(Stat.RANGE));
    }

    @Test
    @DisplayName("Was kein Ausbau ist, kommt nicht hinein")
    void onlyUpgradesFit() {
        UpgradeSlots slots = new UpgradeSlots(2);
        assertFalse(slots.accepts(new ItemStack(Items.DIRT)));
        assertTrue(slots.accepts(ItemStack.EMPTY));
    }

    @Test
    @DisplayName("Mehr als die Plätze hergeben geht nicht")
    void thereAreNoExtraSlots() {
        UpgradeSlots slots = new UpgradeSlots(2);
        assertEquals(2, slots.size());
        assertThrowsIndex(() -> slots.get(2));
        assertThrowsIndex(() -> slots.set(2, ItemStack.EMPTY));
    }

    private static void assertThrowsIndex(Runnable action) {
        org.junit.jupiter.api.Assertions.assertThrows(
                IndexOutOfBoundsException.class, action::run);
    }
}
```

> **Beim Ausführen entschieden (27.08.):** Der Behälter ist in einem
> gewöhnlichen Test überhaupt nicht anzufassen. Er hält `ItemStack`, und
> deren Klasseninitialisierung verlangt die Registrierungen —
> `Bootstrap.bootStrap()` in einer `@BeforeAll` half auch nicht, es scheitert
> davor. Kein anderer Test im Projekt benutzt `ItemStack`; die Linie ist also
> alt und nicht zufällig.
>
> Statt die Probe in einen GameTest zu schieben, ist die eine Regel, die es zu
> schützen gilt, aus dem Behälter herausgezogen: `Loadout.ofCounts` nimmt
> Stückzahlen und zählt Stück für Stück. Sie wird im `LoadoutTest` geprüft,
> der Behälter sammelt nur noch, was in den Plätzen liegt. `UpgradeSlotsTest`
> gibt es nicht.
>
> Was damit ungeprüft bleibt: `accepts`, `get`, `set` und das Speichern. Das
> ist Verwaltung von je einer Zeile, und der erste Block mit Steckplätzen in
> Teil 2 fasst sie im GameTest ohnehin an.

**Hinweis für den Ausführenden:** Dieser Test lädt Minecraft-Klassen
(`ItemStack`, `Items`). Läuft er in dieser Umgebung nicht an, weil die
Registrierungen fehlen, dann nimm die beiden Fälle mit `ItemStack` heraus und
prüfe sie stattdessen im GameTest — die Fälle ohne Minecraft-Bezug
(`freshSlotsAreEmpty`, `thereAreNoExtraSlots`) bleiben hier. Lauf das zuerst
aus, bevor du den Behälter schreibst: Das entscheidet, wo die Probe steht.

- [ ] **Schritt 2: Den Test laufen lassen und den Fehlschlag sehen**

Aufruf: `./gradlew test --tests "*UpgradeSlotsTest*"`
Erwartet: Übersetzungsfehler — `UpgradeSlots` gibt es nicht.

- [ ] **Schritt 3: Den Behälter schreiben**

`UpgradeSlots.java`:

```java
package dev.devpanda.factorynetwork.upgrade;

import dev.devpanda.factorynetwork.item.UpgradeItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Steckplätze eines Geräts oder Blocks.
 *
 * <p><b>Die Zahl der Plätze ist fest und die eigentliche Entscheidung.</b>
 * Wer alles will, muss wählen, was er weglässt — ein Behälter, der wächst,
 * nähme dem Ausbau seinen Preis.
 *
 * <p>Er nimmt nur Ausbauten an. Ein Platz, in den alles passt, ist ein
 * Rucksack und kein Steckplatz.
 */
public class UpgradeSlots {

    private final NonNullList<ItemStack> contents;

    public UpgradeSlots(int size) {
        this.contents = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public int size() {
        return contents.size();
    }

    /** Passt dieser Stapel in einen Steckplatz? Leer passt immer. */
    public boolean accepts(ItemStack stack) {
        return stack.isEmpty() || UpgradeItem.upgradeOf(stack) != null;
    }

    public ItemStack get(int slot) {
        return contents.get(slot);
    }

    /**
     * Legt einen Stapel in einen Platz.
     *
     * @throws IllegalArgumentException wenn es kein Ausbau ist — wer das
     *         aufruft, hat {@link #accepts} nicht gefragt.
     */
    public void set(int slot, ItemStack stack) {
        if (!accepts(stack)) {
            throw new IllegalArgumentException(
                    "kein Ausbau: " + stack.getItem());
        }
        contents.set(slot, stack);
    }

    /** Was diese Bestückung kann und wie hoch ihre Werte sind. */
    public Loadout loadout() {
        List<Upgrade> found = new ArrayList<>();
        for (ItemStack stack : contents) {
            for (int i = 0; i < stack.getCount(); i++) {
                Upgrade upgrade = UpgradeItem.upgradeOf(stack);
                if (upgrade != null) {
                    found.add(upgrade);
                }
            }
        }
        return Loadout.of(found);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, contents, registries);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
    }
}
```

**Zur Zählung:** Ein Platz kann einen Stapel halten, und jedes Stück darin
zählt. Drei Reichweitenkarten auf einem Platz wirken damit wie drei Karten —
das ist die Folge daraus, dass gleiche Karten sich addieren, und es macht die
Platzzahl zur Entscheidung über Vielfalt statt über Menge. Wenn das im Spiel
falsch wirkt, ist die Antwort eine Stapelgrenze von eins am Gegenstand und
nicht eine zweite Rechnung hier.

- [ ] **Schritt 4: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*UpgradeSlotsTest*"`
Erwartet: keine Fehler.

- [ ] **Schritt 5: Die Gegenprobe**

Nimm in `accepts` die Prüfung heraus, sodass es immer `true` liefert. Lauf:
`onlyUpgradesFit` muss fehlschlagen. Danach zurücksetzen.

- [ ] **Schritt 6: Der ganze Lauf**

Aufruf: `./gradlew test`
Erwartet: keine Fehler.

- [ ] **Schritt 7: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/upgrade/UpgradeSlots.java \
        src/test/java/dev/devpanda/factorynetwork/upgrade/UpgradeSlotsTest.java
git commit -m "Steckplätze, die nur Ausbauten annehmen"
```

---

## Was am Ende steht

Drei Gegenstände, eine Rechnung und ein Behälter — und nichts davon tut im
Spiel etwas. Das ist beabsichtigt: Teil 2 hängt den Sendemast und die Geräte
daran, Teil 3 die Anzeigetafel.

**Was ausdrücklich nicht dazugehört:** Kein Funk, keine Reichweite, keine
Bedienoberfläche für die Plätze. `Stat.RANGE` steht schon da, weil die Karte
irgendwo hinwirken muss, aber niemand liest sie.

**Was danach zu tun ist:** `docs/fernzugriff.md` §7 nennt die Reihenfolge.
Teil 2 bekommt einen eigenen Plan.
