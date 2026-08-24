# Geräteerkennung — Umsetzungsplan

> **Für ausführende Agenten:** Dieser Plan wird Aufgabe für Aufgabe abgearbeitet.
> Jeder Schritt ist eine Kästchenzeile (`- [ ]`). Erst der Test, dann der Code,
> dann der Lauf, dann der Commit.

**Ziel:** Der Editor weiß, welche Maschine hinter einem Connector steht, was sie
an welcher Seite annimmt, welche Slots sie hat — und liest deren Inhalt auf
Anfrage.

**Vorgehen:** Ein Profil je Connector reist beim Öffnen mit dem Netzzustand zum
Client. Es speist die Vorschlagsliste, das Zeigen und eine Warnung, wenn der
Connector an einer Seite hängt, die zur Art des Workers nicht passt. Slotinhalte
kommen über ein eigenes Anfrage-Antwort-Paar.

**Technik:** Java 21, NeoForge 1.21.1, JUnit 5, NeoForge-GameTests.

**Entwurf:** `docs/geraeteerkennung.md` — dort steht das Warum zu jeder
Entscheidung hier.

## Durchgehende Regeln

- **Bezeichner englisch, Kommentare und Meldungen deutsch.** Wie im ganzen
  Projekt.
- **Echte Umlaute in allen Dateien**, keine Unicode-Escapes.
- **Das Paket `lang` bleibt ohne Minecraft-Typen** in den Klassen, die Tests
  laden — `NetworkView`, `NetworkCheck`, `DeviceProfile`, `Signatures`. Nur
  `ProgramFolder` fällt heute schon aus dieser Regel; sie wird nicht weiter
  aufgeweicht.
- **Warnungen, keine Fehler.** Alles, was aus dem Netz kommt, ist
  `Diagnostic.Severity.WARNING` — eine Maschine, die morgen dasteht, darf heute
  schon im Programm stehen.
- **Tests laufen mit `./gradlew test`**, GameTests mit
  `./gradlew runGameTestServer`.
- Nach jeder Aufgabe committen. Commit-Meldungen deutsch, ohne Präfixe wie
  `feat:`.

---

## Aufgabe 1: Das Profil als Daten

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/lang/Side.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/lang/DeviceProfile.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/DeviceProfileTest.java`

**Schnittstellen:**
- Liefert: `Side` (Aufzählung mit sieben Werten), `DeviceProfile` mit
  `descriptionId()`, `namespace()`, `connectedSide()`, `access()`,
  `accessAt(Side)`, `hasItems(Side)`, `hasFluids(Side)`, `hasEnergy(Side)`,
  `sidesWith(Access.Ability)`, `reachable()`.

**Warum eine eigene Seitenaufzählung:** Minecrafts `Direction` hat sechs Werte
und keinen für den seitenlosen Zugang — den gäbe es nur als `null`, und ein
`null` als Schlüssel in einer Karte ist die Sorte Falle, die erst in drei
Monaten zuschnappt. Dazu kommt, dass `NetworkView` und `NetworkCheck` heute ohne
Minecraft auskommen und ihre Tests deshalb in Millisekunden laufen.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

`src/test/java/dev/devpanda/factorynetwork/lang/DeviceProfileTest.java`:

```java
package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Profil ist reine Auskunft: Es rechnet nicht, es sagt nur, was da ist.
 */
class DeviceProfileTest {

    private static DeviceProfile crusher() {
        return new DeviceProfile("block.mekanism.crusher", "mekanism", Side.UP,
                Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.SOUTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));
    }

    @Test
    @DisplayName("Eine Seite ohne Eintrag kann nichts")
    void anUnlistedSideCanNothing() {
        DeviceProfile profile = crusher();

        assertFalse(profile.hasItems(Side.DOWN));
        assertFalse(profile.hasFluids(Side.DOWN));
        assertFalse(profile.hasEnergy(Side.DOWN));
    }

    @Test
    @DisplayName("Die angeschlossene Seite wird getrennt geführt")
    void theConnectedSideIsKeptApart() {
        DeviceProfile profile = crusher();

        assertEquals(Side.UP, profile.connectedSide());
        assertTrue(profile.hasEnergy(Side.UP));
        assertFalse(profile.hasItems(Side.UP),
                "oben nimmt die Maschine Strom und keine Gegenstände");
    }

    @Test
    @DisplayName("Wer Gegenstände sucht, bekommt die Seiten genannt, die welche haben")
    void sidesWithItemsAreListed() {
        List<Side> sides = crusher().sidesWith(DeviceProfile.Access.Ability.ITEMS);

        assertEquals(2, sides.size(), () -> "erwartet Norden und Süden, war " + sides);
        assertTrue(sides.contains(Side.NORTH));
        assertTrue(sides.contains(Side.SOUTH));
    }

    @Test
    @DisplayName("Ein Gerät im nicht geladenen Bereich sagt nichts über sich")
    void anUnloadedDeviceSaysNothing() {
        DeviceProfile unknown = DeviceProfile.unreachable();

        assertFalse(unknown.reachable());
        assertFalse(unknown.hasItems(Side.NORTH));
        assertTrue(unknown.sidesWith(DeviceProfile.Access.Ability.ITEMS).isEmpty());
    }

    @Test
    @DisplayName("Seiten mit gleichem Zugang stehen zusammen")
    void sidesWithTheSameAccessAreGrouped() {
        DeviceProfile.Access threeSlots = new DeviceProfile.Access(3, 0, false);
        DeviceProfile machine = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.UP, Map.of(
                        Side.NORTH, threeSlots,
                        Side.SOUTH, threeSlots,
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        List<DeviceProfile.Group> groups = machine.grouped();

        assertEquals(2, groups.size(),
                () -> "erwartet zwei Gruppen, war " + groups);
        DeviceProfile.Group items = groups.stream()
                .filter(group -> group.access().slots() == 3)
                .findFirst().orElseThrow();
        assertEquals(List.of(Side.NORTH, Side.SOUTH), items.sides(),
                "die Seiten stehen in der Reihenfolge der Aufzählung");
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*DeviceProfileTest*"`
Erwartet: Übersetzungsfehler — `Side` und `DeviceProfile` gibt es nicht.

- [ ] **Schritt 3: `Side` anlegen**

`src/main/java/dev/devpanda/factorynetwork/lang/Side.java`:

```java
package dev.devpanda.factorynetwork.lang;

/**
 * Eine Seite eines Blocks, aus Sicht der Sprache.
 *
 * <p><b>Warum nicht Minecrafts {@code Direction}:</b> Eine Fähigkeit lässt
 * sich auch ohne Seite anbieten, und manche Maschine bietet sie
 * ausschließlich so an. In {@code Direction} wäre das ein {@code null} —
 * als Schlüssel in einer Karte die Sorte Falle, die erst spät zuschnappt.
 * Hier ist es {@link #ANY}, ein Wert wie jeder andere.
 *
 * <p>Der zweite Grund: {@link NetworkView} und {@link NetworkCheck} kommen
 * ohne Minecraft aus, und ihre Tests laufen deshalb in Millisekunden statt
 * in einer Minute GameTest.
 */
public enum Side {
    DOWN("unten"),
    UP("oben"),
    NORTH("Norden"),
    SOUTH("Süden"),
    WEST("Westen"),
    EAST("Osten"),
    /** Ohne Seite angeboten — gilt für jede Richtung. */
    ANY("überall");

    private final String written;

    Side(String written) {
        this.written = written;
    }

    /** Wie die Seite in einer Meldung dasteht. */
    public String written() {
        return written;
    }
}
```

- [ ] **Schritt 4: `DeviceProfile` anlegen**

`src/main/java/dev/devpanda/factorynetwork/lang/DeviceProfile.java`:

```java
package dev.devpanda.factorynetwork.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Was hinter einem Connector steht.
 *
 * <p>Reine Auskunft, keine Rechnung: Der Server probt die Fähigkeiten des
 * Nachbarblocks, und was dabei herauskommt, steht hier. Der Editor liest es
 * für das Zeigen, für die Vorschläge und für die Warnung, wenn der Connector
 * an einer Seite hängt, an der die Maschine nichts annimmt.
 *
 * <p><b>Der Übersetzungsschlüssel und nicht der fertige Text.</b> „Crusher"
 * heißt auf einem englischen Server anders als im deutschen Client, und
 * übersetzt wird dort, wo jemand hinsieht.
 *
 * @param descriptionId Übersetzungsschlüssel des Blocks, etwa
 *                      {@code block.mekanism.crusher}
 * @param namespace     die Mod, aus der er stammt
 * @param connectedSide die Seite, an der der Connector tatsächlich hängt
 * @param access        was an welcher Seite geht; Seiten ohne Eintrag können
 *                      nichts
 */
public record DeviceProfile(String descriptionId, String namespace,
                            Side connectedSide, Map<Side, Access> access) {

    /**
     * Was an einer Seite geht.
     *
     * @param slots  Fächer des Gegenstandsspeichers, null wenn keiner
     * @param tanks  Behälter des Flüssigkeitsspeichers, null wenn keiner
     * @param energy ob dort Strom hineingeht oder herauskommt
     */
    public record Access(int slots, int tanks, boolean energy) {

        /** Wonach sich fragen lässt. */
        public enum Ability { ITEMS, FLUIDS, ENERGY }

        public boolean has(Ability ability) {
            return switch (ability) {
                case ITEMS -> slots > 0;
                case FLUIDS -> tanks > 0;
                case ENERGY -> energy;
            };
        }
    }

    /**
     * Ein Gerät, über das nichts bekannt ist.
     *
     * <p>Der Chunk ist nicht geladen, oder da steht gar nichts. <b>Das ist
     * etwas anderes als ein Gerät, das nichts kann</b> — dieselbe
     * Unterscheidung wie bei {@link NetworkView#knowsNetwork()}. Wer sie
     * einebnet, warnt vor Maschinen, die tadellos funktionieren.
     */
    public static DeviceProfile unreachable() {
        return new DeviceProfile("", "", Side.ANY, Map.of());
    }

    /** Ist überhaupt etwas bekannt? */
    public boolean reachable() {
        return !descriptionId.isEmpty();
    }

    /** Was an dieser Seite geht — oder nichts. */
    public Access accessAt(Side side) {
        Access direct = access.get(side);
        if (direct != null) {
            return direct;
        }
        // Was ohne Seite angeboten wird, gilt für jede.
        return access.get(Side.ANY);
    }

    public boolean hasItems(Side side) {
        return can(side, Access.Ability.ITEMS);
    }

    public boolean hasFluids(Side side) {
        return can(side, Access.Ability.FLUIDS);
    }

    public boolean hasEnergy(Side side) {
        return can(side, Access.Ability.ENERGY);
    }

    public boolean can(Side side, Access.Ability ability) {
        Access at = accessAt(side);
        return at != null && at.has(ability);
    }

    /**
     * Die Seiten, an denen das geht — für „Norden hätte einen".
     *
     * <p>Ohne die angeschlossene: Wer sie nennt, sagt dem Spieler, er solle
     * den Connector dorthin hängen, wo er schon hängt.
     */
    public List<Side> sidesWith(Access.Ability ability) {
        List<Side> found = new ArrayList<>();
        for (Map.Entry<Side, Access> entry : access.entrySet()) {
            if (entry.getKey() != connectedSide && entry.getValue().has(ability)) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    /**
     * Seiten mit gleichem Zugang zusammengefasst.
     *
     * <p>Eine Maschine bietet an vier Seiten dasselbe an, und ohne das hier
     * stünde es im Tooltip viermal. Zusammengefasst wird nach dem Inhalt des
     * Zugangs und nicht nach der Handler-Instanz: Für die Anzeige ist
     * „Norden, Süden: 3 Fächer" richtig, gleichgültig ob es dieselbe
     * Instanz ist — und die Instanz überlebt den Weg zum Client ohnehin
     * nicht.
     *
     * <p>Die Reihenfolge der Seiten ist die der Aufzählung, damit dasselbe
     * Gerät immer gleich dasteht.
     */
    public List<Group> grouped() {
        Map<Access, List<Side>> byAccess = new LinkedHashMap<>();
        for (Side side : Side.values()) {
            Access at = access.get(side);
            if (at != null) {
                byAccess.computeIfAbsent(at, key -> new ArrayList<>()).add(side);
            }
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Access, List<Side>> entry : byAccess.entrySet()) {
            groups.add(new Group(entry.getValue(), entry.getKey()));
        }
        return groups;
    }

    /** Mehrere Seiten, die dasselbe können. */
    public record Group(List<Side> sides, Access access) {
    }
}
```

Importe der Klasse: `java.util.ArrayList`, `java.util.LinkedHashMap`,
`java.util.List`, `java.util.Map`.

- [ ] **Schritt 5: Den Test laufen lassen, er muss durchgehen**

Aufruf: `./gradlew test --tests "*DeviceProfileTest*"`
Erwartet: 5 Tests, alle grün.

- [ ] **Schritt 6: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/lang/Side.java \
        src/main/java/dev/devpanda/factorynetwork/lang/DeviceProfile.java \
        src/test/java/dev/devpanda/factorynetwork/lang/DeviceProfileTest.java
git commit -m "Ein Profil beschreibt, was hinter einem Connector steht"
```

---

## Aufgabe 2: Die Ressourcenart eines Workers ins Sprachpaket

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/lang/WorkerKind.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/runtime/WorkerRuntime.java:786-798`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/WorkerKindTest.java`

**Schnittstellen:**
- Braucht: nichts aus Aufgabe 1.
- Liefert: `WorkerKind.of(Decl.Worker) -> Expr.Selector.Kind` und
  `WorkerKind.selectorKind(Expr) -> Expr.Selector.Kind`. Aufgabe 3 baut darauf.

**Worum es geht:** `WorkerRuntime.isFluidWorker` und `selectorKind` sind reine
Arbeit am Syntaxbaum und kennen kein Minecraft — sie stehen aber in der
Laufzeit, wo `NetworkCheck` nicht hinlangen kann, ohne Minecraft mitzuziehen.
Zwei Fassungen derselben Regel würden auseinanderlaufen.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

`src/test/java/dev/devpanda/factorynetwork/lang/WorkerKindTest.java`:

```java
package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.parse.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Woran ein Worker seine Ressourcenart trägt.
 *
 * <p>Nicht an einem eigenen Feld, sondern am Auswahlausdruck seines Filters.
 * Die Laufzeit liest sie so, und die Prüfung im Editor muss dieselbe Regel
 * lesen — sonst warnt der eine, wo der andere schweigt.
 */
class WorkerKindTest {

    private static Decl.Worker firstWorker(String source) {
        return (Decl.Worker) Parser.parse(source).program().declarations().get(0);
    }

    @Test
    @DisplayName("Ein Filter auf Gegenstände macht einen Gegenstands-Worker")
    void anItemFilterMakesAnItemWorker() {
        Decl.Worker worker = firstWorker("""
                worker mahlen {
                    from chest
                    to crusher_1
                    filter item:iron_ore
                }""");

        assertEquals(Expr.Selector.Kind.ITEM, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("Ein Filter auf Flüssigkeiten macht einen Flüssigkeits-Worker")
    void aFluidFilterMakesAFluidWorker() {
        Decl.Worker worker = firstWorker("""
                worker pumpen {
                    from tank_1
                    to boiler
                    filter fluid:water
                }""");

        assertEquals(Expr.Selector.Kind.FLUID, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("Eine Menge davor ändert die Art nicht")
    void anAmountInFrontKeepsTheKind() {
        Decl.Worker worker = firstWorker("""
                worker mahlen {
                    from chest
                    to crusher_1
                    filter 64 item:iron_ore
                }""");

        assertEquals(Expr.Selector.Kind.ITEM, WorkerKind.of(worker));
    }

    @Test
    @DisplayName("Ohne Filter ist die Art unbekannt")
    void withoutAFilterTheKindIsUnknown() {
        Decl.Worker worker = firstWorker("""
                worker schieben {
                    from chest
                    to crusher_1
                }""");

        assertNull(WorkerKind.of(worker),
                "ohne Filter darf nichts geraten werden");
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*WorkerKindTest*"`
Erwartet: Übersetzungsfehler — `WorkerKind` gibt es nicht.

- [ ] **Schritt 3: `WorkerKind` anlegen**

`src/main/java/dev/devpanda/factorynetwork/lang/WorkerKind.java`:

```java
package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;

/**
 * Was ein Worker bewegt: Gegenstände, Flüssigkeiten oder Chemikalien.
 *
 * <p>Ein Worker trägt seine Art nicht als eigene Angabe, sondern am
 * Auswahlausdruck seines Filters — {@code filter item:iron_ore} gegen
 * {@code filter fluid:water}. Das steht hier und nicht in der Laufzeit,
 * weil die Prüfung im Editor dieselbe Regel braucht und kein Minecraft
 * mitziehen soll.
 */
public final class WorkerKind {

    private WorkerKind() {
    }

    /**
     * Die Art dieses Workers, oder {@code null}.
     *
     * <p>{@code null} heißt „unbekannt" und nicht „Gegenstände": Ohne Filter
     * lässt sich nichts sagen, und eine geratene Art führt zu einer Warnung,
     * die falsch ist.
     */
    public static Expr.Selector.Kind of(Decl.Worker worker) {
        Decl.Worker.Entry filter = worker.entry(Decl.Worker.Entry.Kind.FILTER);
        return filter == null ? null : selectorKind(filter.value());
    }

    /**
     * Die Art eines Auswahlausdrucks, durch Menge und Ausnahme hindurch.
     *
     * <p>{@code 64 item:iron_ore} und {@code tag:c/ores except item:x} tragen
     * ihre Art nicht an der Wurzel.
     */
    public static Expr.Selector.Kind selectorKind(Expr expr) {
        return switch (expr) {
            case Expr.Selector selector -> selector.kind();
            case Expr.Amount amount -> selectorKind(amount.selection());
            case Expr.Except except -> selectorKind(except.base());
            case null, default -> null;
        };
    }
}
```

- [ ] **Schritt 4: `WorkerRuntime` auf die neue Stelle umstellen**

In `src/main/java/dev/devpanda/factorynetwork/runtime/WorkerRuntime.java` die
beiden privaten Methoden `isFluidWorker` und `selectorKind` (Zeilen 786-798)
löschen und die Aufrufer umstellen. `isFluidWorker` wird zu:

```java
    /** Meint der Filter dieses Workers Flüssigkeiten? */
    private static boolean isFluidWorker(Decl.Worker worker) {
        return WorkerKind.of(worker) == Expr.Selector.Kind.FLUID;
    }
```

Dazu `import dev.devpanda.factorynetwork.lang.WorkerKind;` ergänzen. Die
Aufrufe von `selectorKind` im Rest der Datei (Suche: `selectorKind(`) auf
`WorkerKind.selectorKind(` umstellen.

- [ ] **Schritt 5: Alle Tests laufen lassen**

Aufruf: `./gradlew test`
Erwartet: alles grün, auch die Laufzeittests — die Regel hat sich nicht
geändert, nur ihr Ort.

- [ ] **Schritt 6: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/lang/WorkerKind.java \
        src/main/java/dev/devpanda/factorynetwork/runtime/WorkerRuntime.java \
        src/test/java/dev/devpanda/factorynetwork/lang/WorkerKindTest.java
git commit -m "Die Ressourcenart eines Workers steht jetzt bei der Sprache"
```

---

## Aufgabe 3: Die Warnung bei der falschen Seite

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/NetworkView.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/NetworkCheck.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/NetworkCheckTest.java`

**Schnittstellen:**
- Braucht: `DeviceProfile`, `Side` (Aufgabe 1), `WorkerKind` (Aufgabe 2).
- Liefert: `NetworkView.profile(String) -> DeviceProfile` (Standard:
  `DeviceProfile.unreachable()`). Aufgabe 5 füllt sie auf dem Client.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

Ans Ende von `NetworkCheckTest.java`, vor die schließende Klammer:

```java
    /** Ein Netz, in dem ein Gerät ein bekanntes Profil hat. */
    private static NetworkView netWith(String name, DeviceProfile profile) {
        return new NetworkView() {
            @Override
            public List<String> connectors() {
                return List.of(name);
            }

            @Override
            public List<String> displays() {
                return List.of();
            }

            @Override
            public DeviceProfile profile(String wanted) {
                return name.equals(wanted) ? profile : DeviceProfile.unreachable();
            }
        };
    }

    @Test
    @DisplayName("Ein Ziel ohne Gegenstandsfach an der angeschlossenen Seite wird gemeldet")
    void aTargetWithoutItemsOnTheConnectedSideIsReported() {
        DeviceProfile tank = new DeviceProfile("block.mekanism.tank", "mekanism",
                Side.UP, Map.of(
                        Side.UP, new DeviceProfile.Access(0, 1, false),
                        Side.NORTH, new DeviceProfile.Access(2, 0, false)));

        List<Diagnostic> problems = check("""
                worker mahlen {
                    from storage
                    to tank_1
                    filter item:iron_ore
                }""", netWith("tank_1", tank));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("tank_1") && !problem.isError()),
                () -> "die Meldung fehlt: " + problems);
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("Norden")),
                () -> "der Hinweis auf die brauchbare Seite fehlt: " + problems);
    }

    @Test
    @DisplayName("Ein Flüssigkeits-Worker am Tank wird nicht gemeldet")
    void aFluidWorkerAtATankIsFine() {
        DeviceProfile tank = new DeviceProfile("block.mekanism.tank", "mekanism",
                Side.UP, Map.of(Side.UP, new DeviceProfile.Access(0, 1, false)));

        List<Diagnostic> problems = check("""
                worker pumpen {
                    from storage
                    to tank_1
                    filter fluid:water
                }""", netWith("tank_1", tank));

        assertTrue(problems.isEmpty(),
                () -> "hier ist alles in Ordnung, gemeldet wurde: " + problems);
    }

    @Test
    @DisplayName("Über ein Gerät ohne Profil wird nichts behauptet")
    void nothingIsClaimedAboutAnUnknownDevice() {
        List<Diagnostic> problems = check("""
                worker mahlen {
                    from storage
                    to crusher_1
                    filter item:iron_ore
                }""", netWith("crusher_1", DeviceProfile.unreachable()));

        assertTrue(problems.isEmpty(),
                () -> "ein nicht geladenes Gerät ist kein Fehler: " + problems);
    }

    @Test
    @DisplayName("Ein Worker ohne Filter löst keine Seitenwarnung aus")
    void aWorkerWithoutFilterIsNotChecked() {
        DeviceProfile tank = new DeviceProfile("block.mekanism.tank", "mekanism",
                Side.UP, Map.of(Side.UP, new DeviceProfile.Access(0, 1, false)));

        List<Diagnostic> problems = check("""
                worker schieben {
                    from storage
                    to tank_1
                }""", netWith("tank_1", tank));

        assertTrue(problems.stream().noneMatch(problem ->
                        problem.message().contains("Seite")),
                () -> "ohne Filter darf die Art nicht geraten werden: " + problems);
    }
```

Dazu oben in der Datei ergänzen: `import java.util.Map;` steht schon da.

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*NetworkCheckTest*"`
Erwartet: Übersetzungsfehler — `NetworkView.profile` gibt es nicht.

- [ ] **Schritt 3: `NetworkView` erweitern**

In `NetworkView.java` nach `displays()` einfügen:

```java
    /**
     * Was hinter einem Connector steht.
     *
     * <p>Standardmäßig unbekannt: Ein Test hat kein Netz, und der Server
     * kannte bis hierher nur Namen. Wer nichts weiß, sagt zu keinem Gerät
     * etwas — das ist etwas anderes, als ein Gerät für leer zu erklären.
     */
    default DeviceProfile profile(String connector) {
        return DeviceProfile.unreachable();
    }
```

Und in der Konstanten `NONE` nichts ergänzen — sie erbt die Standardfassung.

- [ ] **Schritt 4: Die Prüfung in `NetworkCheck` einbauen**

In `NetworkCheck.java` die Methode `checkWorker` ersetzen:

```java
    private static void checkWorker(Decl.Worker worker, NetworkView view, Set<String> local,
                                    List<Diagnostic> problems) {
        for (Decl.Worker.Entry entry : worker.entries()) {
            switch (entry.kind()) {
                case FROM, TO, OVERFLOW -> {
                    checkTarget(entry.value(), view, local, problems);
                    checkTarget(entry.second(), view, local, problems);
                    checkSide(worker, entry.value(), view, problems);
                }
                default -> { }
            }
        }
    }

    /**
     * Passt die Seite, an der der Connector hängt, zu dem, was der Worker
     * bewegt?
     *
     * <p>Der Fehler dahinter ist der stillste von allen: Der Worker läuft,
     * bewegt nichts, und meldet nichts — denn „nichts bewegt" ist der
     * Normalfall. Wer die Maschine falsch herum ankabelt, sucht das im
     * Programm.
     *
     * <p><b>Geprüft wird die angeschlossene Seite</b>, nicht ob irgendeine
     * Seite es könnte. Sonst schweigt die Warnung genau in dem Fall, für den
     * sie gebaut ist.
     */
    private static void checkSide(Decl.Worker worker, Expr target, NetworkView view,
                                  List<Diagnostic> problems) {
        if (!(target instanceof Expr.Name name)) {
            return;
        }
        DeviceProfile profile = view.profile(name.value());
        if (!profile.reachable()) {
            return;
        }
        Expr.Selector.Kind kind = WorkerKind.of(worker);
        if (kind == null) {
            return;
        }
        DeviceProfile.Access.Ability needed = switch (kind) {
            case ITEM, TAG -> DeviceProfile.Access.Ability.ITEMS;
            case FLUID -> DeviceProfile.Access.Ability.FLUIDS;
            // Chemikalien sind noch nicht angebunden; über sie wird nichts
            // behauptet, solange der Server sie nicht proben kann.
            case CHEMICAL -> null;
        };
        if (needed == null || profile.can(profile.connectedSide(), needed)) {
            return;
        }
        List<Side> elsewhere = profile.sidesWith(needed);
        String what = needed == DeviceProfile.Access.Ability.FLUIDS
                ? "Flüssigkeiten" : "Gegenstände";
        String hint = elsewhere.isEmpty()
                ? "Diese Maschine nimmt an keiner Seite " + what + " an."
                : "An " + written(elsewhere) + " ginge es — häng den Connector dorthin.";
        problems.add(new Diagnostic(Diagnostic.Severity.WARNING, name.span(),
                "Der Connector „" + name.value() + "“ hängt "
                        + profile.connectedSide().written()
                        + " — dort nimmt die Maschine keine " + what + " an.",
                hint));
    }

    /** „Norden", „Norden und Süden", „Norden, Süden und oben". */
    private static String written(List<Side> sides) {
        List<String> words = sides.stream().map(Side::written).toList();
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1))
                + " und " + words.get(words.size() - 1);
    }
```

- [ ] **Schritt 5: Den Test laufen lassen, er muss durchgehen**

Aufruf: `./gradlew test --tests "*NetworkCheckTest*"`
Erwartet: alle Tests grün, auch die bisherigen vier.

- [ ] **Schritt 6: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/lang/NetworkView.java \
        src/main/java/dev/devpanda/factorynetwork/lang/NetworkCheck.java \
        src/test/java/dev/devpanda/factorynetwork/lang/NetworkCheckTest.java
git commit -m "Ein Connector an der falschen Seite wird jetzt gemeldet"
```

---

## Aufgabe 4: Die Fähigkeiten in der Welt proben

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/block/entity/DeviceScan.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/block/entity/ConnectorBlockEntity.java`
- Test: `src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java`

**Schnittstellen:**
- Braucht: `DeviceProfile`, `Side` (Aufgabe 1).
- Liefert: `DeviceScan.of(ConnectorBlockEntity) -> DeviceProfile` und
  `DeviceScan.side(Direction) -> Side`. Aufgabe 5 ruft das erste, Aufgabe 8 das
  zweite.

**Worum es geht:** Alle sechs Richtungen des Nachbarblocks proben, dazu den
seitenlosen Zugang. `ConnectorBlockEntity` holt heute schon `ItemHandler` und
`FluidHandler` für genau eine Seite — dieselbe Abfrage, siebenmal.

- [ ] **Schritt 1: Den fehlschlagenden GameTest schreiben**

In `FactoryNetworkGameTests.java` ergänzen:

```java
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aChestIsRecognisedFromEverySide(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        BlockPos chest = connector.east();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.EAST));

        ConnectorBlockEntity entity =
                (ConnectorBlockEntity) helper.getBlockEntity(connector);
        DeviceProfile profile = DeviceScan.of(entity);

        helper.assertTrue(profile.reachable(), "die Kiste wurde nicht erkannt");
        helper.assertTrue(profile.descriptionId().contains("chest"),
                "falscher Übersetzungsschlüssel: " + profile.descriptionId());
        helper.assertTrue(profile.connectedSide() == Side.EAST,
                "die angeschlossene Seite stimmt nicht: " + profile.connectedSide());
        helper.assertTrue(profile.hasItems(Side.EAST),
                "eine Kiste nimmt an jeder Seite Gegenstände an");
        helper.assertTrue(profile.accessAt(Side.EAST).slots() == 27,
                "eine Kiste hat 27 Fächer, gezählt wurden "
                        + profile.accessAt(Side.EAST).slots());
        helper.assertFalse(profile.hasFluids(Side.EAST),
                "eine Kiste hat keinen Tank");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aConnectorWithoutNeighbourFindsNothing(GameTestHelper helper) {
        BlockPos connector = new BlockPos(2, 1, 1);
        helper.setBlock(connector, FnBlocks.CONNECTOR.get().defaultBlockState()
                .setValue(ConnectorBlock.FACING, Direction.EAST));

        ConnectorBlockEntity entity =
                (ConnectorBlockEntity) helper.getBlockEntity(connector);
        DeviceProfile profile = DeviceScan.of(entity);

        helper.assertFalse(profile.reachable(),
                "über Luft ist nichts bekannt");
        helper.succeed();
    }
```

Nötige Ergänzungen bei den Importen dieser Datei:

```java
import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.block.entity.DeviceScan;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew runGameTestServer`
Erwartet: Übersetzungsfehler — `DeviceScan` gibt es nicht.

- [ ] **Schritt 3: `DeviceScan` anlegen**

`src/main/java/dev/devpanda/factorynetwork/block/entity/DeviceScan.java`:

```java
package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Probt, was die Maschine hinter einem Connector kann.
 *
 * <p>Alle sechs Richtungen und dazu den seitenlosen Zugang: Manche Maschine
 * bietet ihre Fähigkeit ausschließlich ohne Seite an, und wer nur die sechs
 * Richtungen probt, meldet für genau die „nimmt nichts an" — die schlechteste
 * Sorte Fehler, weil sie plausibel aussieht.
 *
 * <p>Der Aufwand fällt beim Öffnen des Terminals an, nicht laufend. Sieben
 * Zugänge mal drei Fähigkeiten je Connector; die Abfragen sind in NeoForge
 * zwischengespeichert und kosten in dieser Größenordnung nichts.
 */
public final class DeviceScan {

    private DeviceScan() {
    }

    /** Das Profil der Maschine hinter diesem Connector. */
    public static DeviceProfile of(ConnectorBlockEntity connector) {
        Level level = connector.getLevel();
        if (level == null) {
            return DeviceProfile.unreachable();
        }
        BlockState state = connector.getBlockState();
        Direction facing = ConnectorBlock.machineSide(state);
        BlockPos target = connector.getBlockPos().relative(facing);
        if (!level.isLoaded(target)) {
            return DeviceProfile.unreachable();
        }
        BlockState machine = level.getBlockState(target);
        if (machine.isAir()) {
            return DeviceProfile.unreachable();
        }

        Map<Side, DeviceProfile.Access> access = new EnumMap<>(Side.class);
        for (Direction direction : Direction.values()) {
            // Aus Sicht der Maschine kommt der Zugriff von der Gegenseite.
            DeviceProfile.Access at = probe(level, target, direction.getOpposite());
            if (at != null) {
                access.put(side(direction), at);
            }
        }
        DeviceProfile.Access anySide = probe(level, target, null);
        if (anySide != null) {
            access.put(Side.ANY, anySide);
        }

        return new DeviceProfile(machine.getBlock().getDescriptionId(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(machine.getBlock()).getNamespace(),
                side(facing), access);
    }

    /**
     * Was an einer Seite geht, oder {@code null}, wenn dort nichts geht.
     *
     * <p>{@code null} und kein leerer Eintrag: Eine Seite, die nichts kann,
     * steht gar nicht erst in der Karte, und das Profil bleibt so klein wie
     * die Maschine schlicht ist.
     */
    private static DeviceProfile.Access probe(Level level, BlockPos pos, Direction from) {
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, from);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, from);
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, from);

        int slots = items == null ? 0 : items.getSlots();
        int tanks = fluids == null ? 0 : fluids.getTanks();
        boolean power = energy != null;
        if (slots == 0 && tanks == 0 && !power) {
            return null;
        }
        return new DeviceProfile.Access(slots, tanks, power);
    }

    /** Minecrafts Richtung als Seite der Sprache. */
    public static Side side(Direction direction) {
        return switch (direction) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            case EAST -> Side.EAST;
        };
    }
}
```

**Keine Rückrichtung.** Eine Methode `direction(Side)` wäre naheliegend, hat
aber keinen Aufrufer: Wer den Inhalt liest, nimmt `machineInventory()` des
Connectors, und das kennt seine Seite selbst. Sie käme erst dazu, wenn der
Editor eine andere als die angeschlossene Seite auslesen soll — heute wäre sie
toter Code.

- [ ] **Schritt 4: Den GameTest laufen lassen**

Aufruf: `./gradlew runGameTestServer`
Erwartet: beide neuen Tests grün. Dauert etwa eine Minute.

- [ ] **Schritt 5: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/block/entity/DeviceScan.java \
        src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java
git commit -m "Der Server probt, was eine Maschine an welcher Seite kann"
```

---

## Aufgabe 5: Das Profil zum Client bringen

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/network/packet/DeviceProfileCodec.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/network/packet/NetworkStatePacket.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/block/entity/ControllerBlockEntity.java:719-724`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/block/entity/TerminalBlockEntity.java:52-69`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/ClientNetworkState.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/ClientNetworkView.java`
- Test: `src/test/java/dev/devpanda/factorynetwork/network/DeviceProfileCodecTest.java`

**Schnittstellen:**
- Braucht: `DeviceProfile` (Aufgabe 1), `DeviceScan.of` (Aufgabe 4).
- Liefert: `NetworkStatePacket` mit sechstem Feld
  `Map<String, DeviceProfile> profiles`, `ClientNetworkState.profile(String)`,
  `ClientNetworkView.profile(String)`. Aufgaben 6, 7 und 9 lesen davon.

**Achtung:** `StreamCodec.composite` trägt höchstens sechs Felder. Das Paket hat
danach genau sechs — die Grenze ist erreicht, ein siebtes bräuchte eine von Hand
geschriebene Fassung wie in `AnalyserDataPacket.SUMMARY`.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

`src/test/java/dev/devpanda/factorynetwork/network/DeviceProfileCodecTest.java`:

```java
package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ein Profil, das über die Leitung geht, muss drüben dasselbe sein.
 *
 * <p>Geprüft wird die Umrechnung in die flache Form und zurück — ohne
 * Netzwerkpuffer, damit der Test ohne Minecraft läuft.
 */
class DeviceProfileCodecTest {

    @Test
    @DisplayName("Ein Profil übersteht Hin- und Rückweg unverändert")
    void aProfileSurvivesTheRoundTrip() {
        DeviceProfile before = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.UP, Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        DeviceProfile after = DeviceProfileCodec.fromFlat(DeviceProfileCodec.toFlat(before));

        assertEquals(before, after);
    }

    @Test
    @DisplayName("Ein unbekanntes Gerät bleibt unbekannt")
    void anUnreachableProfileStaysUnreachable() {
        DeviceProfile before = DeviceProfile.unreachable();

        DeviceProfile after = DeviceProfileCodec.fromFlat(DeviceProfileCodec.toFlat(before));

        assertEquals(before, after);
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*DeviceProfileCodecTest*"`
Erwartet: Übersetzungsfehler — `DeviceProfileCodec` gibt es nicht.

- [ ] **Schritt 3: `DeviceProfileCodec` anlegen**

`src/main/java/dev/devpanda/factorynetwork/network/packet/DeviceProfileCodec.java`:

```java
package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Bringt ein {@link DeviceProfile} über die Leitung.
 *
 * <p>Der Umweg über eine flache Form hat einen Grund: Eine Karte von
 * Aufzählung auf Aufzählung mit einem Verbundwert lässt sich mit
 * {@code StreamCodec.composite} nicht bauen, und von Hand geschriebene
 * Puffercodes sind schwer zu prüfen. Die flache Form ist eine Liste von
 * Vieren und lässt sich ohne Minecraft testen.
 */
public final class DeviceProfileCodec {

    /** Ein Zugang als flache Vier: Seite, Fächer, Behälter, Strom. */
    public record FlatAccess(int side, int slots, int tanks, boolean energy) {

        public static final StreamCodec<RegistryFriendlyByteBuf, FlatAccess> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FlatAccess::side,
                        ByteBufCodecs.VAR_INT, FlatAccess::slots,
                        ByteBufCodecs.VAR_INT, FlatAccess::tanks,
                        ByteBufCodecs.BOOL, FlatAccess::energy,
                        FlatAccess::new);
    }

    /** Ein ganzes Profil, flach. */
    public record Flat(String name, String descriptionId, String namespace,
                       int connectedSide, List<FlatAccess> access) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Flat> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(256), Flat::name,
                        ByteBufCodecs.stringUtf8(256), Flat::descriptionId,
                        ByteBufCodecs.stringUtf8(128), Flat::namespace,
                        ByteBufCodecs.VAR_INT, Flat::connectedSide,
                        FlatAccess.STREAM_CODEC.apply(ByteBufCodecs.list(7)), Flat::access,
                        Flat::new);
    }

    private DeviceProfileCodec() {
    }

    /** Ohne Namen — für den Test und für die Antwort auf eine Anfrage. */
    public static Flat toFlat(DeviceProfile profile) {
        return toFlat("", profile);
    }

    public static Flat toFlat(String name, DeviceProfile profile) {
        List<FlatAccess> flat = new ArrayList<>();
        for (Map.Entry<Side, DeviceProfile.Access> entry : profile.access().entrySet()) {
            flat.add(new FlatAccess(entry.getKey().ordinal(), entry.getValue().slots(),
                    entry.getValue().tanks(), entry.getValue().energy()));
        }
        return new Flat(name, profile.descriptionId(), profile.namespace(),
                profile.connectedSide().ordinal(), flat);
    }

    public static DeviceProfile fromFlat(Flat flat) {
        Map<Side, DeviceProfile.Access> access = new EnumMap<>(Side.class);
        for (FlatAccess entry : flat.access()) {
            access.put(Side.values()[entry.side()],
                    new DeviceProfile.Access(entry.slots(), entry.tanks(), entry.energy()));
        }
        return new DeviceProfile(flat.descriptionId(), flat.namespace(),
                Side.values()[flat.connectedSide()], access);
    }
}
```

- [ ] **Schritt 4: Den Test laufen lassen, er muss durchgehen**

Aufruf: `./gradlew test --tests "*DeviceProfileCodecTest*"`
Erwartet: beide Tests grün.

- [ ] **Schritt 5: Das Paket erweitern**

In `NetworkStatePacket.java` das Record und den Codec ändern:

```java
public record NetworkStatePacket(List<NamedPlace> connectors, List<NamedPlace> displays,
                                 List<String> workers, List<String> plants,
                                 List<String> fluids,
                                 List<DeviceProfileCodec.Flat> profiles)
        implements CustomPacketPayload {
```

und im `STREAM_CODEC` als sechsten Eintrag vor `NetworkStatePacket::new`:

```java
                    DeviceProfileCodec.Flat.STREAM_CODEC.apply(ByteBufCodecs.list(512)),
                    NetworkStatePacket::profiles,
```

Ins Javadoc der Klasse ergänzen:

```java
 * <p><b>Die Profile sagen, was hinter den Connectoren steht.</b> Sie reisen
 * hier mit und nicht auf Anfrage, weil sie sich nur ändern, wenn jemand die
 * Maschine austauscht. Was gerade in den Fächern liegt, kommt dagegen über
 * {@link DeviceSnapshotPacket} — das ändert sich im Sekundentakt.
 *
 * <p><b>Sechs Felder sind die Grenze.</b> {@code StreamCodec.composite} trägt
 * nicht mehr; ein siebtes bräuchte eine von Hand geschriebene Fassung wie in
 * {@code AnalyserDataPacket}.
```

- [ ] **Schritt 6: Den Controller die Profile bauen lassen**

In `ControllerBlockEntity.java` nach `connectorPlaces()` einfügen:

```java
    /**
     * Die Profile aller Connectoren, für den Editor.
     *
     * <p>Einmal beim Öffnen des Terminals. Wer nicht geladen ist, liefert ein
     * Profil, das nichts über sich sagt — und das ist etwas anderes als eines,
     * das nichts kann.
     */
    public List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat>
            connectorProfiles() {
        List<dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.Flat> profiles =
                new java.util.ArrayList<>();
        if (level == null) {
            return profiles;
        }
        for (Map.Entry<String, BlockPos> entry : graph.connectors().entrySet()) {
            if (!(level.getBlockEntity(entry.getValue()) instanceof ConnectorBlockEntity connector)) {
                continue;
            }
            profiles.add(dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec.toFlat(
                    entry.getKey(), DeviceScan.of(connector)));
        }
        return profiles;
    }
```

In `TerminalBlockEntity.java` beide Aufrufe ergänzen — der leere Fall bekommt
ein sechstes `List.of()`, der gefüllte `entity.connectorProfiles()`.

- [ ] **Schritt 7: Der Client hält die Profile**

In `ClientNetworkState.java` ergänzen:

```java
    private static Map<String, DeviceProfile> profiles = Map.of();
```

in `accept` dazu:

```java
        Map<String, DeviceProfile> received = new java.util.HashMap<>();
        for (DeviceProfileCodec.Flat flat : packet.profiles()) {
            received.put(flat.name(), DeviceProfileCodec.fromFlat(flat));
        }
        profiles = received;
```

und als neue Methode:

```java
    /**
     * Was hinter einem Connector steht.
     *
     * <p>Nie {@code null}: Wer nach einem Namen fragt, den es nicht gibt,
     * bekommt ein Profil, das über sich nichts sagt.
     */
    public static DeviceProfile profile(String connector) {
        return profiles.getOrDefault(connector, DeviceProfile.unreachable());
    }
```

In `ClientNetworkView.java` durchreichen:

```java
    @Override
    public DeviceProfile profile(String connector) {
        return ClientNetworkState.profile(connector);
    }
```

- [ ] **Schritt 8: Alles bauen und laufen lassen**

Aufruf: `./gradlew test && ./gradlew runGameTestServer`
Erwartet: alles grün.

- [ ] **Schritt 9: Committen**

```bash
git add -A
git commit -m "Die Geräteprofile reisen mit dem Netzzustand zum Editor"
```

---

## Aufgabe 6: Das Gerät steht in der Vorschlagsliste

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/screen/Completions.java:296-303`
- Test: `src/test/java/dev/devpanda/factorynetwork/client/CompletionsDetailTest.java`

**Schnittstellen:**
- Braucht: `ClientNetworkState.profile` (Aufgabe 5).
- Liefert: `Completions.describe(DeviceProfile) -> String`, öffentlich, damit
  Aufgabe 9 dieselbe Beschreibung im Zeigen benutzt.

**Worum es geht:** `addConnectors` setzt heute ein leeres `detail`. Dort gehört
hinein, was die Maschine ist und kann.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

`src/test/java/dev/devpanda/factorynetwork/client/CompletionsDetailTest.java`:

```java
package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.client.screen.Completions;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was hinter einem Connector in der Vorschlagsliste steht.
 *
 * <p>Der Übersetzungsschlüssel wird hier nicht aufgelöst — das tut der
 * Client mit seiner Sprachdatei. Geprüft wird der Teil dahinter.
 */
class CompletionsDetailTest {

    @Test
    @DisplayName("Ein Gerät nennt, was es kann")
    void aDeviceNamesWhatItCanDo() {
        DeviceProfile crusher = new DeviceProfile("block.mekanism.crusher", "mekanism",
                Side.NORTH, Map.of(
                        Side.NORTH, new DeviceProfile.Access(3, 0, false),
                        Side.UP, new DeviceProfile.Access(0, 0, true)));

        String detail = Completions.abilities(crusher);

        assertTrue(detail.contains("Gegenstände"), () -> "war: " + detail);
        assertTrue(detail.contains("Strom"), () -> "war: " + detail);
    }

    @Test
    @DisplayName("Ein unbekanntes Gerät behauptet nichts")
    void anUnknownDeviceClaimsNothing() {
        assertEquals("", Completions.abilities(DeviceProfile.unreachable()));
    }

    @Test
    @DisplayName("Ein Gerät, das nichts kann, sagt das auch")
    void adeviceWithoutAbilitiesSaysSo() {
        DeviceProfile stone = new DeviceProfile("block.minecraft.stone", "minecraft",
                Side.NORTH, Map.of());

        assertEquals("nichts anzuschließen", Completions.abilities(stone));
    }
}
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*CompletionsDetailTest*"`
Erwartet: Übersetzungsfehler — `Completions.abilities` gibt es nicht.

- [ ] **Schritt 3: `abilities` schreiben und `addConnectors` umstellen**

In `Completions.java` ergänzen:

```java
    /**
     * Was ein Gerät kann, in einer Zeile.
     *
     * <p>Über alle Seiten zusammengefasst und nicht je Seite: In der
     * Vorschlagsliste ist Platz für ein paar Wörter, und die Frage dort
     * lautet „taugt das überhaupt". Welche Seite es genau ist, sagt das
     * Zeigen.
     */
    public static String abilities(DeviceProfile profile) {
        if (!profile.reachable()) {
            return "";
        }
        List<String> can = new ArrayList<>();
        for (Side side : Side.values()) {
            if (profile.hasItems(side) && !can.contains("Gegenstände")) {
                can.add("Gegenstände");
            }
            if (profile.hasFluids(side) && !can.contains("Flüssigkeiten")) {
                can.add("Flüssigkeiten");
            }
            if (profile.hasEnergy(side) && !can.contains("Strom")) {
                can.add("Strom");
            }
        }
        return can.isEmpty() ? "nichts anzuschließen" : String.join(", ", can);
    }
```

und `addConnectors` ersetzen:

```java
    private static void addConnectors(List<Entry> entries, String prefix) {
        for (String connector : ClientNetworkState.connectors()) {
            if (!matches(connector, prefix)) {
                continue;
            }
            DeviceProfile profile = ClientNetworkState.profile(connector);
            String detail = profile.reachable()
                    ? net.minecraft.network.chat.Component
                            .translatable(profile.descriptionId()).getString()
                            + " · " + abilities(profile)
                    : "";
            entries.add(new Entry(connector, connector, Entry.Kind.CONNECTOR, detail));
        }
    }
```

Importe ergänzen: `dev.devpanda.factorynetwork.lang.DeviceProfile`,
`dev.devpanda.factorynetwork.lang.Side`.

- [ ] **Schritt 4: Den Test laufen lassen**

Aufruf: `./gradlew test --tests "*CompletionsDetailTest*"`
Erwartet: 3 Tests grün.

- [ ] **Schritt 5: Im Spiel ansehen**

Aufruf: `./gradlew runClient`
Eine Kiste neben einen Connector setzen, benennen, Terminal öffnen, in einem
Worker `to ` tippen. Hinter dem Namen muss „Kiste · Gegenstände" stehen.

- [ ] **Schritt 6: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/client/screen/Completions.java \
        src/test/java/dev/devpanda/factorynetwork/client/CompletionsDetailTest.java
git commit -m "Die Vorschlagsliste nennt Maschine und Fähigkeiten"
```

---

## Aufgabe 7: Vorschläge nach dem Punkt

**Dateien:**
- Ändern: `src/main/java/dev/devpanda/factorynetwork/lang/Signatures.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/screen/Completions.java:68-106`
- Test: `src/test/java/dev/devpanda/factorynetwork/lang/SignaturesTest.java`

**Schnittstellen:**
- Braucht: nichts aus früheren Aufgaben.
- Liefert: `Signatures.MEMBERS` als `List<Signatures.Member>` mit
  `name()`, `shape()`, `help()`.

**Ehrlich bleiben:** Die Sprache kennt an einem Gerät vier Dinge — `online`,
`name`, `redstone()`, `count()` (`Interpreter.java:699` und `:736`). Mehr wird
nicht vorgeschlagen, auch wenn `sprache.md` §6 mehr beschreibt. Ein Vorschlag,
der zu einem Laufzeitfehler führt, ist schlimmer als keiner.

- [ ] **Schritt 1: Den fehlschlagenden Test schreiben**

Ans Ende von `SignaturesTest.java`:

```java
    @Test
    @DisplayName("Nach dem Punkt stehen die vier Dinge, die ein Gerät hat")
    void afterTheDotTheFourDeviceMembersAreOffered() {
        List<String> names = Signatures.MEMBERS.stream()
                .map(Signatures.Member::name).toList();

        assertEquals(List.of("online", "name", "redstone", "count"), names);
    }

    @Test
    @DisplayName("Jedes Mitglied trägt seine Form")
    void everyMemberCarriesItsShape() {
        for (Signatures.Member member : Signatures.MEMBERS) {
            assertFalse(member.shape().isBlank(),
                    () -> member.name() + " hat keine Form");
            assertFalse(member.help().isBlank(),
                    () -> member.name() + " hat keine Erklärung");
        }
    }
```

- [ ] **Schritt 2: Den Test laufen lassen, er muss fehlschlagen**

Aufruf: `./gradlew test --tests "*SignaturesTest*"`
Erwartet: Übersetzungsfehler — `Signatures.MEMBERS` gibt es nicht.

- [ ] **Schritt 3: `MEMBERS` in `Signatures` ergänzen**

```java
    /**
     * Was an einem Gerät steht — {@code crusher_1.online}.
     *
     * <p><b>Vier und nicht mehr.</b> Der Interpreter kennt {@code online},
     * {@code name}, {@code redstone()} und {@code count()};
     * {@code sprache.md} §6 beschreibt darüber hinaus {@code insert()},
     * {@code items()} und {@code busy}, die es noch nicht gibt. Was hier
     * steht, muss laufen — ein Vorschlag, der in einen Laufzeitfehler führt,
     * ist schlimmer als gar keiner.
     *
     * <p>Für jedes Gerät dieselben: Gerätespezifisches gibt es nach dem Punkt
     * erst, wenn die Mitglieder aus §6 gebaut sind. Dann ist es ein Eintrag
     * hier und nichts weiter.
     */
    public record Member(String name, String shape, String help) {
    }

    public static final List<Member> MEMBERS = List.of(
            new Member("online", "bool",
                    "Ob das Gerät gerade im Netz hängt."),
            new Member("name", "string",
                    "Der Name, den die Beschriftungspistole vergeben hat."),
            new Member("redstone", "int, oder redstone(int)",
                    "Die Redstone-Stärke, 0 bis 15. Mit Zahl gesetzt, ohne gelesen."),
            new Member("count", "count(selection) int",
                    "Wie viel von einer Art im Netzspeicher liegt."));
```

- [ ] **Schritt 4: Die Punkt-Stelle in `Completions.at` einbauen**

In `Completions.at` direkt nach der Berechnung von `before` einfügen:

```java
        // Nach einem Punkt hinter einem Gerätenamen: was ein Gerät hat.
        //
        // Vor der Prüfung auf die Stelle in der Angabe, weil „to crusher_1."
        // sonst als angefangener Zielname gelesen würde — und dann stünden
        // dort wieder die Connectoren.
        String member = memberPrefix(upToCursor);
        if (member != null) {
            for (Signatures.Member candidate : Signatures.MEMBERS) {
                if (matches(candidate.name(), prefix)) {
                    entries.add(new Entry(candidate.name(), candidate.name(),
                            Entry.Kind.BUILTIN, candidate.shape()));
                }
            }
            return limit(entries);
        }
```

und als neue Methode:

```java
    /**
     * Der Gerätename vor dem Punkt, oder {@code null}.
     *
     * <p>Nur, wenn davor wirklich ein Connector steht: {@code storage.} ist
     * etwas anderes, und {@code 3.5} ist gar kein Punktzugriff.
     */
    private static String memberPrefix(String upToCursor) {
        String prefix = currentWord(upToCursor);
        String before = upToCursor.substring(0, upToCursor.length() - prefix.length());
        if (!before.endsWith(".")) {
            return null;
        }
        String name = currentWord(before.substring(0, before.length() - 1));
        return ClientNetworkState.connectors().contains(name) ? name : null;
    }
```

- [ ] **Schritt 5: Beide Tests laufen lassen**

Aufruf: `./gradlew test`
Erwartet: alles grün.

- [ ] **Schritt 6: Im Spiel ansehen**

Aufruf: `./gradlew runClient`
Im Editor `if crusher_1.` tippen — es müssen vier Vorschläge erscheinen, jeder
mit seiner Form dahinter.

- [ ] **Schritt 7: Committen**

```bash
git add src/main/java/dev/devpanda/factorynetwork/lang/Signatures.java \
        src/main/java/dev/devpanda/factorynetwork/client/screen/Completions.java \
        src/test/java/dev/devpanda/factorynetwork/lang/SignaturesTest.java
git commit -m "Nach dem Punkt schlägt der Editor vor, was ein Gerät hat"
```

**Nachlauf:** `SignaturesExportTest` hält die Tabelle für VS Code gleich. Wenn er
fehlschlägt, muss die erzeugte JSON-Datei unter `editor/vscode/data` neu
geschrieben werden — der Test sagt, mit welchem Aufruf.

---

## Aufgabe 8: Die Slotinhalte auf Anfrage

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/network/packet/DeviceSnapshotRequestPacket.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/network/packet/DeviceSnapshotPacket.java`
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/client/ClientDeviceState.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/network/packet/FnPackets.java:45`
- Test: `src/main/java/dev/devpanda/factorynetwork/test/FactoryNetworkGameTests.java`

**Schnittstellen:**
- Braucht: `DeviceScan` (Aufgabe 4), `DeviceProfileCodec` (Aufgabe 5).
- Liefert: `ClientDeviceState.request(String)`, `ClientDeviceState.snapshot(String)
  -> DeviceSnapshotPacket` (oder `null`, solange nichts da ist). Aufgabe 9 liest
  beides.

**Der Deckel:** höchstens 64 Fächer je Antwort. Wurde gekürzt, steht das in der
Antwort — ein Fassregal mit zweihundert Fächern soll den Tooltip nicht sprengen
und auch nicht heimlich lügen.

- [ ] **Schritt 1: Die Anfrage anlegen**

`DeviceSnapshotRequestPacket.java`:

```java
package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * „Was liegt gerade in diesem Gerät?"
 *
 * <p>Auf Anfrage und nicht laufend: In einer Fabrik mit vierzig Connectoren
 * wäre die laufende Übertragung Dauerverkehr für etwas, das man einmal
 * ansieht. Gefragt wird, wenn der Zeiger auf einem Gerätenamen stehen bleibt.
 */
public record DeviceSnapshotRequestPacket(String connector) implements CustomPacketPayload {

    public static final Type<DeviceSnapshotRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "device_snapshot_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshotRequestPacket>
            STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(256),
                    DeviceSnapshotRequestPacket::connector,
                    DeviceSnapshotRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeviceSnapshotRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TerminalMenu menu)) {
                return;
            }
            // Über das offene Menü und nicht über eine mitgeschickte
            // Koordinate: Wer nach einem Gerät fragt, muss vor dem Terminal
            // stehen, das dazugehört.
            //
            // controller(player) und nicht controller() — das Menü löst den
            // Controller über den Spieler auf, weil es selbst nur die
            // Koordinate des Terminals kennt.
            DeviceSnapshotPacket answer = menu.controller(player)
                    .map(controller -> DeviceSnapshotPacket.of(controller, packet.connector()))
                    .orElse(null);
            if (answer != null) {
                net.neoforged.neoforge.network.PacketDistributor
                        .sendToPlayer(player, answer);
            }
        });
    }
}
```

- [ ] **Schritt 2: Die Antwort anlegen**

`DeviceSnapshotPacket.java`:

```java
package dev.devpanda.factorynetwork.network.packet;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DeviceScan;
import dev.devpanda.factorynetwork.client.ClientDeviceState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Was gerade in einem Gerät liegt.
 *
 * <p>Trägt die Struktur gleich mit: Damit ist der Fall „Maschine wurde
 * ausgetauscht, während das Terminal offen ist" nebenbei erledigt, ohne
 * dafür einen eigenen Mechanismus zu bauen.
 *
 * <p><b>Gedeckelt auf 64 Fächer</b>, und wenn gekürzt wurde, steht das
 * dabei. Ein Fassregal mit zweihundert Fächern soll den Tooltip nicht
 * sprengen — aber auch nicht heimlich lügen.
 */
public record DeviceSnapshotPacket(String connector, DeviceProfileCodec.Flat profile,
                                   List<ItemStack> slots, int slotsOmitted,
                                   int energy, int energyCapacity)
        implements CustomPacketPayload {

    /** Mehr passt in keinen Tooltip. */
    public static final int MAX_SLOTS = 64;

    public static final Type<DeviceSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FactoryNetwork.MOD_ID, "device_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshotPacket>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256), DeviceSnapshotPacket::connector,
                    DeviceProfileCodec.Flat.STREAM_CODEC, DeviceSnapshotPacket::profile,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS)),
                    DeviceSnapshotPacket::slots,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::slotsOmitted,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::energy,
                    ByteBufCodecs.VAR_INT, DeviceSnapshotPacket::energyCapacity,
                    DeviceSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Liest das Gerät aus, oder liefert {@code null}, wenn es das nicht gibt. */
    public static DeviceSnapshotPacket of(ControllerBlockEntity controller, String connector) {
        if (controller.getLevel() == null) {
            return null;
        }
        BlockPos pos = controller.graph().connectors().get(connector);
        if (pos == null
                || !(controller.getLevel().getBlockEntity(pos)
                        instanceof ConnectorBlockEntity entity)) {
            return null;
        }

        List<ItemStack> stacks = new ArrayList<>();
        int omitted = 0;
        IItemHandler items = entity.machineInventory();
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                if (stacks.size() >= MAX_SLOTS) {
                    omitted = items.getSlots() - MAX_SLOTS;
                    break;
                }
                stacks.add(items.getStackInSlot(slot).copy());
            }
        }

        int energy = 0;
        int capacity = 0;
        IEnergyStorage power = entity.machineEnergy();
        if (power != null) {
            energy = power.getEnergyStored();
            capacity = power.getMaxEnergyStored();
        }

        return new DeviceSnapshotPacket(connector,
                DeviceProfileCodec.toFlat(connector, DeviceScan.of(entity)),
                stacks, omitted, energy, capacity);
    }

    public static void handle(DeviceSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeviceState.accept(packet));
    }
}
```

- [ ] **Schritt 3: `machineEnergy` im Connector ergänzen**

In `ConnectorBlockEntity.java` neben `machineTank()`:

```java
    /**
     * Der Stromspeicher der Maschine, auf die der Connector zeigt.
     *
     * <p>Derselbe Nachbar, dieselbe Seite — dritte Fähigkeit. Gelesen wird er
     * heute nur für das Zeigen im Editor; verteilt wird noch kein Strom.
     */
    public @Nullable net.neoforged.neoforge.energy.IEnergyStorage machineEnergy() {
        if (level == null) {
            return null;
        }
        Direction facing = ConnectorBlock.machineSide(getBlockState());
        BlockPos target = worldPosition.relative(facing);
        if (!level.isLoaded(target)) {
            return null;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, target,
                facing.getOpposite());
    }
```

- [ ] **Schritt 4: Den Zustand auf dem Client halten**

`ClientDeviceState.java`:

```java
package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotRequestPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Was der Client über den Inhalt der Geräte weiß.
 *
 * <p><b>Erst nach einer Viertelsekunde fragen.</b> Sonst schickt jede
 * Mausbewegung über eine Zeile mit drei Namen drei Anfragen. Bis die Antwort
 * da ist, steht im Tooltip, was ohnehin bekannt ist — Identität, Seiten,
 * Fächerzahl.
 *
 * <p>Die Antwort bleibt bis zum Schließen des Terminals liegen, wird beim
 * erneuten Zeigen aber neu geholt: Ein zweites Mal hinsehen heißt meistens
 * nachsehen, ob sich etwas geändert hat.
 */
public final class ClientDeviceState {

    /** So lange muss der Zeiger stillhalten, in Millisekunden. */
    private static final long DELAY = 250;

    private static final Map<String, DeviceSnapshotPacket> snapshots = new HashMap<>();

    private static String pending = "";
    private static long since;

    private ClientDeviceState() {
    }

    /**
     * Meldet, dass der Zeiger auf diesem Gerät steht.
     *
     * <p>Wird bei jedem Bild aufgerufen; die Anfrage geht erst, wenn der
     * Zeiger lange genug stillhält.
     */
    public static void hovering(String connector) {
        long now = System.currentTimeMillis();
        if (!connector.equals(pending)) {
            pending = connector;
            since = now;
            return;
        }
        if (since > 0 && now - since >= DELAY) {
            since = 0;
            PacketDistributor.sendToServer(new DeviceSnapshotRequestPacket(connector));
        }
    }

    /** Der Zeiger steht auf keinem Gerät mehr. */
    public static void notHovering() {
        pending = "";
        since = 0;
    }

    /** Was zuletzt über ein Gerät ankam, oder {@code null}. */
    public static DeviceSnapshotPacket snapshot(String connector) {
        return snapshots.get(connector);
    }

    public static void accept(DeviceSnapshotPacket packet) {
        snapshots.put(packet.connector(), packet);
    }

    /** Beim Schließen des Terminals: Der Inhalt von vorhin gilt nicht mehr. */
    public static void clear() {
        snapshots.clear();
        notHovering();
    }
}
```

- [ ] **Schritt 5: Die Pakete anmelden**

In `FnPackets.java` nach der Zeile für `AnalyserDataPacket`:

```java
        registrar.playToServer(DeviceSnapshotRequestPacket.TYPE,
                DeviceSnapshotRequestPacket.STREAM_CODEC,
                DeviceSnapshotRequestPacket::handle);
        registrar.playToClient(DeviceSnapshotPacket.TYPE, DeviceSnapshotPacket.STREAM_CODEC,
                DeviceSnapshotPacket::handle);
```

- [ ] **Schritt 6: Den GameTest schreiben**

In `FactoryNetworkGameTests.java`:

**Der Test muss `of()` wirklich aufrufen.** `buildSetup` stellt bereits einen
Controller mit zwei benannten Connectoren an Kisten hin — `quarry_output` und
`depot`. Genau der Aufbau, den die Antwort braucht.

```java
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSnapshotReportsWhatIsInTheChest(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        // Zwei Barren in die Kiste hinter quarry_output.
        BlockPos connector = entity.graph().connectors().get("quarry_output");
        helper.assertTrue(connector != null, "quarry_output fehlt im Netz");
        ConnectorBlockEntity port =
                (ConnectorBlockEntity) helper.getLevel().getBlockEntity(connector);
        IItemHandler chest = port.machineInventory();
        helper.assertTrue(chest != null, "hinter quarry_output steht keine Kiste");
        chest.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);

        DeviceSnapshotPacket snapshot =
                DeviceSnapshotPacket.of(entity, "quarry_output");

        helper.assertTrue(snapshot != null, "es kam keine Antwort");
        helper.assertTrue(snapshot.slots().size() == 27,
                "eine Kiste hat 27 Fächer, gemeldet wurden " + snapshot.slots().size());
        helper.assertTrue(snapshot.slotsOmitted() == 0,
                "bei 27 Fächern wird nichts gekürzt");
        helper.assertTrue(snapshot.slots().get(0).getCount() == 2,
                "der Inhalt des ersten Fachs stimmt nicht");
        helper.assertTrue(snapshot.profile().descriptionId().contains("chest"),
                "die Antwort trägt kein Profil der Kiste: "
                        + snapshot.profile().descriptionId());
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void aSnapshotOfAnUnknownNameIsRefused(GameTestHelper helper) {
        BlockPos controller = buildSetup(helper);
        ControllerBlockEntity entity = controllerAt(helper, controller);
        entity.rebuildNetwork();

        helper.assertTrue(DeviceSnapshotPacket.of(entity, "gibt_es_nicht") == null,
                "auf einen unbekannten Namen darf es keine Antwort geben");
        helper.succeed();
    }
```

Zusätzliche Importe in dieser Datei:

```java
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
```

- [ ] **Schritt 7: Bauen und laufen lassen**

Aufruf: `./gradlew test && ./gradlew runGameTestServer`
Erwartet: alles grün.

- [ ] **Schritt 8: Committen**

```bash
git add -A
git commit -m "Der Editor kann den Inhalt eines Geräts erfragen"
```

---

## Aufgabe 9: Das Zeigen

**Dateien:**
- Anlegen: `src/main/java/dev/devpanda/factorynetwork/client/screen/EditorTooltip.java`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/screen/CodeScreen.java:449-527`
- Ändern: `src/main/java/dev/devpanda/factorynetwork/client/screen/CodeTabView.java:290-322`
- Test: von Hand im Spiel — was gezeichnet wird, prüft kein Einheitstest sinnvoll

**Schnittstellen:**
- Braucht: `Completions.abilities` (Aufgabe 6), `ClientDeviceState` (Aufgabe 8),
  `ClientNetworkState.profile` (Aufgabe 5), `DeviceProfile.grouped` (Aufgabe 1).
- Liefert: `EditorTooltip.render(GuiGraphics, Font, CodeEditor, Project,
  List<Diagnostic>, int, int)`.

**Die Lage, und warum sie anders ist als gedacht:** Die beiden Zeigen-Pfade sind
**nicht** gleich gebaut. `CodeScreen` hat mit `describeName` (Zeile 449) längst
eine Namensauskunft — Koordinate im Netz, Erklärungsort im Projekt, Fundstellen.
`CodeTabView` hat sie nicht; dort gibt es nur Signatur und Meldung.

Daraus folgt zweierlei. Erstens gehört die Geräteauskunft **in `describeName`
hinein** und nicht als vierter Fall daneben — wer auf `crusher_1` zeigt, stellt
eine Frage, und „wo steht das" und „was ist das" sind zwei Hälften derselben
Antwort. Zweitens ist das der Anlass, die Methode zu teilen: Der Reiter im
Terminal bekommt damit die Namensauskunft, die das eigene Fenster längst hat.

**Reihenfolge der Fälle:** Name vor Signatur vor Meldung. Ein Name ist das
Genaueste, was unter dem Zeiger stehen kann; die Signatur gilt für die ganze
Zeile, die Meldung auch.

- [ ] **Schritt 1: `EditorTooltip` anlegen**

`src/main/java/dev/devpanda/factorynetwork/client/screen/EditorTooltip.java`:

```java
package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientDeviceState;
import dev.devpanda.factorynetwork.client.ClientNetworkState;
import dev.devpanda.factorynetwork.client.FnFonts;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Definitions;
import dev.devpanda.factorynetwork.lang.Diagnostic;
import dev.devpanda.factorynetwork.lang.Project;
import dev.devpanda.factorynetwork.lang.Side;
import dev.devpanda.factorynetwork.network.packet.DeviceSnapshotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Was beim Zeigen im Editor dasteht.
 *
 * <p>An einer Stelle und nicht in jedem Fenster einzeln. Der Anlass war eine
 * Ungleichheit, die niemandem aufgefallen war: Das eigene Fenster erklärte
 * einen Namen unter dem Zeiger — Stelle im Netz, Erklärungsort, Fundstellen —,
 * der Reiter im Terminal nicht. Dieselbe Frage, zwei Antworten, je nachdem wo
 * man tippt.
 *
 * <p>Die Reihenfolge ist Absicht: Ein Name ist das Genaueste, was unter dem
 * Zeiger stehen kann. Die Signatur gilt für die ganze Zeile, die Meldung auch.
 */
public final class EditorTooltip {

    /** Mehr Fundstellen deckten den halben Bildschirm. */
    private static final int MAX_PLACES = 5;

    /** Und mehr belegte Fächer auch. */
    private static final int MAX_SLOTS_SHOWN = 6;

    private EditorTooltip() {
    }

    /**
     * Zeichnet den Tooltip, wenn es einen gibt.
     *
     * <p>Die Prüfungen auf Knöpfe und offene Menüs bleiben beim jeweiligen
     * Fenster — sie unterscheiden sich, und sie kennen ihre eigenen Flächen.
     */
    public static void render(GuiGraphics graphics, Font font, CodeEditor editor,
                              Project project, List<Diagnostic> problems,
                              int mouseX, int mouseY) {
        if (describeName(graphics, font, editor, project, mouseX, mouseY)) {
            return;
        }
        ClientDeviceState.notHovering();

        var signature = editor.signatureAt(mouseX, mouseY);
        if (signature != null) {
            graphics.renderComponentTooltip(font, List.of(
                    FnFonts.mono(signature.shape()),
                    Component.literal("§7" + signature.help())), mouseX, mouseY);
            return;
        }

        Diagnostic problem = editor.diagnosticAt(problems, mouseX, mouseY);
        if (problem == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(problem.message()));
        if (problem.hint() != null) {
            lines.add(Component.literal("§7" + problem.hint()));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Der Name unter dem Zeiger, erklärt.
     *
     * <p>Übernommen aus {@code CodeScreen}, um die Maschine dahinter
     * erweitert. Die Reihenfolge im Kasten folgt der Frage, die man stellt:
     * erst was es ist, dann wo es steht, dann was im Programm damit passiert.
     *
     * @return ob etwas gezeichnet wurde
     */
    private static boolean describeName(GuiGraphics graphics, Font font, CodeEditor editor,
                                        Project project, int mouseX, int mouseY) {
        String word = editor.wordAt(mouseX, mouseY);
        if (word.isEmpty()) {
            return false;
        }
        var declared = Definitions.find(project, word);
        BlockPos inWorld = ClientNetworkState.placeOf(word);
        boolean isConnector = ClientNetworkState.connectors().contains(word);
        if (declared.isEmpty() && inWorld == null) {
            return false;
        }

        // Vor der Prüfung auf ein bekanntes Profil: Ein Gerät, dessen Chunk
        // beim Öffnen nicht geladen war, hat noch keines — und die Antwort
        // auf die Anfrage bringt es mit. Wer hier erst fragt, wenn schon
        // etwas bekannt ist, bekommt für genau diese Geräte nie etwas.
        if (isConnector) {
            ClientDeviceState.hovering(word);
        } else {
            ClientDeviceState.notHovering();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(FnFonts.mono(word));
        if (isConnector) {
            addDevice(lines, word);
        }
        if (inWorld != null) {
            lines.add(Component.translatable("screen.factorynetwork.code.at",
                            inWorld.getX(), inWorld.getY(), inWorld.getZ())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.factorynetwork.code.locate_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (declared.isPresent()) {
            addDeclaration(lines, project, word, declared.get());
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    /** Was die Maschine ist, kann und gerade enthält. */
    private static void addDevice(List<Component> lines, String connector) {
        DeviceProfile profile = profileOf(connector);
        if (!profile.reachable()) {
            lines.add(Component.literal("§8Nicht geladen — über die Maschine ist "
                    + "nichts bekannt."));
            return;
        }
        lines.add(Component.translatable(profile.descriptionId())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("§7Angeschlossen: "
                + profile.connectedSide().written()));

        // Seiten mit gleichem Zugang stehen zusammen — eine Maschine, die an
        // vier Seiten dasselbe kann, soll das nicht viermal sagen.
        for (DeviceProfile.Group group : profile.grouped()) {
            List<String> what = new ArrayList<>();
            if (group.access().slots() > 0) {
                what.add(group.access().slots() + " Fächer");
            }
            if (group.access().tanks() > 0) {
                what.add(group.access().tanks() + " Behälter");
            }
            if (group.access().energy()) {
                what.add("Strom");
            }
            List<String> sides = group.sides().stream().map(Side::written).toList();
            lines.add(Component.literal("§8" + String.join(", ", sides)
                    + ": " + String.join(", ", what)));
        }

        // An der angeschlossenen Seite geht gar nichts — der Fehler, den man
        // sonst nur durch Ausprobieren findet.
        Side connected = profile.connectedSide();
        if (profile.accessAt(connected) == null) {
            List<Side> elsewhere = new ArrayList<>(
                    profile.sidesWith(DeviceProfile.Access.Ability.ITEMS));
            elsewhere.addAll(profile.sidesWith(DeviceProfile.Access.Ability.FLUIDS));
            lines.add(Component.literal(elsewhere.isEmpty()
                    ? "§cDort ist nichts anzuschließen."
                    : "§cDort ist nichts anzuschließen — "
                            + elsewhere.get(0).written() + " ginge."));
        }
        addContents(lines, connector);
    }

    /**
     * Was gerade drin liegt, sobald die Antwort da ist.
     *
     * <p>Vorher steht hier nichts. Der Kasten springt dann um ein paar Zeilen
     * — besser als einer, der eine Viertelsekunde lang gar nicht da ist.
     */
    private static void addContents(List<Component> lines, String connector) {
        DeviceSnapshotPacket snapshot = ClientDeviceState.snapshot(connector);
        if (snapshot == null) {
            return;
        }
        int shown = 0;
        for (int slot = 0; slot < snapshot.slots().size(); slot++) {
            ItemStack stack = snapshot.slots().get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (shown == MAX_SLOTS_SHOWN) {
                lines.add(Component.literal("§8…"));
                break;
            }
            lines.add(Component.literal("§7" + slot + ": ")
                    .append(stack.getHoverName())
                    .append(Component.literal(" §7×" + stack.getCount())));
            shown++;
        }
        if (shown == 0 && !snapshot.slots().isEmpty()) {
            lines.add(Component.literal("§8leer"));
        }
        if (snapshot.slotsOmitted() > 0) {
            lines.add(Component.literal("§8und " + snapshot.slotsOmitted()
                    + " weitere Fächer"));
        }
        if (snapshot.energyCapacity() > 0) {
            lines.add(Component.literal("§7Strom: " + snapshot.energy()
                    + " / " + snapshot.energyCapacity()));
        }
    }

    /** Wo der Name erklärt wird und wo er sonst noch vorkommt. */
    private static void addDeclaration(List<Component> lines, Project project, String word,
                                       Definitions.Location declared) {
        List<Definitions.Location> places = Definitions.references(project, word);
        lines.add(Component.translatable("screen.factorynetwork.code.declared_in",
                        declared.file(), declared.line())
                .withStyle(ChatFormatting.GRAY));
        // Die Erklärung selbst ist eine Fundstelle; gezählt wird, was sonst
        // noch da ist.
        int used = Math.max(0, places.size() - 1);
        lines.add(Component.translatable("screen.factorynetwork.code.used", used)
                .withStyle(ChatFormatting.DARK_GRAY));
        int shown = 0;
        for (Definitions.Location place : places) {
            if (place.line() == declared.line() && place.file().equals(declared.file())) {
                continue;
            }
            if (shown++ >= MAX_PLACES) {
                break;
            }
            lines.add(Component.literal("§8  " + place.file() + ":" + place.line()));
        }
    }

    /**
     * Das Profil, bevorzugt aus der letzten Antwort.
     *
     * <p>Die Antwort auf eine Anfrage trägt die Struktur mit. Wer sie
     * bevorzugt, bekommt eine ausgetauschte Maschine mit — und ein Gerät,
     * dessen Chunk beim Öffnen nicht geladen war, überhaupt erst.
     */
    private static DeviceProfile profileOf(String connector) {
        DeviceSnapshotPacket snapshot = ClientDeviceState.snapshot(connector);
        if (snapshot != null) {
            return dev.devpanda.factorynetwork.network.packet.DeviceProfileCodec
                    .fromFlat(snapshot.profile());
        }
        return ClientNetworkState.profile(connector);
    }
}
```

- [ ] **Schritt 2: `CodeScreen` umstellen**

In `CodeScreen.java` die Methode `describeName` (Zeilen 449-497) **löschen** —
sie lebt jetzt in `EditorTooltip`. In `renderTooltip` alles ab
`if (describeName(…))` bis zum Ende der Methode ersetzen durch:

```java
        EditorTooltip.render(graphics, font, editor, project, openProblems, mouseX, mouseY);
    }
```

Die drei Prüfungen davor bleiben: `panel.hasMenu() || showingHelp` und
`overButton`. Ungenutzt gewordene Importe entfernen — der Übersetzer meldet
sie nicht, `Definitions` und `ChatFormatting` werden aber hier nicht mehr
gebraucht.

- [ ] **Schritt 3: `CodeTabView` umstellen**

In `CodeTabView.renderTooltip` alles ab `var signature = editor.signatureAt(…)`
bis zum Ende der Methode ersetzen durch:

```java
        EditorTooltip.render(graphics, font, editor, project, openProblems, mouseX, mouseY);
    }
```

Die Prüfungen auf `overButton`, `overExpand` und `overPlus` bleiben davor
stehen.

**Damit bekommt der Reiter die Namensauskunft, die er nie hatte** — Stelle im
Netz, Erklärungsort, Fundstellen. Das ist kein Nebeneffekt, sondern der Grund,
warum die Methode wandert.

- [ ] **Schritt 4: Beim Schließen aufräumen**

Der Inhalt von vorhin gilt nicht mehr, wenn das Terminal zugeht. In
`CodeScreen.onClose` und in `TerminalScreen` (dort, wo `removed()` oder
`onClose()` steht) ergänzen:

```java
        ClientDeviceState.clear();
```

- [ ] **Schritt 5: Bauen**

Aufruf: `./gradlew build`
Erwartet: übersetzt ohne Fehler.

- [ ] **Schritt 6: Im Spiel prüfen**

Aufruf: `./gradlew runClient`

Aufbau: Controller, Laufwerk mit Zelle, Kabel, drei Connectoren. Am ersten eine
Kiste mit ein paar Gegenständen, am zweiten einen Stein, am dritten eine Kiste,
wobei der Connector von der Kiste **weg** zeigt. Alle drei beschriften.

Zu prüfen:
1. Auf den ersten Namen zeigen — Name, „Kiste", angeschlossene Seite, „27
   Fächer", nach kurzem Halten der Inhalt.
2. Auf den zweiten zeigen — kein Fächereintrag, kein Inhalt.
3. Auf den dritten zeigen — die rote Zeile „Dort ist nichts anzuschließen".
4. Denselben Namen in einem `worker`-Block mit `filter item:...` als `to`
   verwenden — die Warnung muss in der Zeile stehen.
5. Dasselbe im Reiter des Terminals **und** im eigenen Fenster: Beide müssen
   jetzt gleich viel zeigen.
6. In einem Worker `to ` tippen — Namen mit Beschreibung dahinter.
7. `crusher_1.` tippen — vier Vorschläge.

- [ ] **Schritt 7: Committen**

```bash
git add -A
git commit -m "Zeigen erklärt einen Namen jetzt überall gleich, mit Maschine dahinter"
```
---

## Aufgabe 10: Die Dokumentation nachziehen

**Dateien:**
- Ändern: `docs/umsetzung.md`

- [ ] **Schritt 1: Den Fehlt-Punkt umschreiben**

In `docs/umsetzung.md` den Punkt 1 unter „Fehlt, in dieser Reihenfolge"
(„Der Sprachdienst über die Signaturen hinaus") streichen. Die beiden folgenden
Punkte rücken auf 1 und 2.

Im Abschnitt „Der Editor, eigener Strang" unter „Steht" anfügen:

```markdown
**Die Geräte hinter den Connectoren.** Der Server probt beim Öffnen alle sechs
Seiten der Maschine und den seitenlosen Zugang; daraus weiß der Editor, was
dort steht und was es an welcher Seite annimmt. Das speist drei Stellen: die
Vorschlagsliste nennt hinter jedem Connector Maschine und Fähigkeiten, das
Zeigen nennt dazu die Fächer und auf Anfrage ihren Inhalt, und ein Worker, der
Gegenstände an eine Seite schickt, die keine annimmt, bekommt eine Warnung mit
der Seite, an der es ginge.

Dazu Vorschläge nach dem Punkt: `crusher_1.` bietet `online`, `name`,
`redstone()` und `count()` an — die vier, die der Interpreter wirklich kennt.
```

Unter „Fehlt" als neuen Punkt aufnehmen:

```markdown
1. **Die Gerätemitglieder aus `sprache.md` §6.** `insert()`, `items()`,
   `output()`, `send()` und `busy` sind beschrieben und nicht gebaut; nach dem
   Punkt stehen deshalb für jedes Gerät dieselben vier Einträge. Bei `busy` ist
   vorher zu klären, woher der Wert kommen soll — es gibt keine Capability, über
   die eine fremde Maschine „ich arbeite gerade" meldet.
```

Und unter „Offene Fragen, nicht entschieden" anfügen:

```markdown
- **Die Annahme-Probe.** Ob ein Fach einen bestimmten Gegenstand nimmt, lässt
  sich nur durch einen simulierten Einfügeversuch beantworten, und der braucht
  Kandidaten. Vorgesehen sind die `item:`-Literale des Entwurfs; gebaut ist es
  nicht. Siehe `docs/geraeteerkennung.md`, Abschnitt 3.
```

- [ ] **Schritt 2: Committen**

```bash
git add docs/umsetzung.md
git commit -m "Stand der Geräteerkennung im Umsetzungsdokument nachgezogen"
```

---

## Was dieser Plan nicht enthält

- **Die Annahme-Probe gegen die Literale des Entwurfs** (Entwurf, Abschnitt 3).
  Sie braucht den Entwurf auf dem Server als Kandidatenquelle und ist damit ein
  eigener Schritt. Ohne sie sagt das Zeigen, welche Fächer es gibt und was drin
  liegt, aber nicht, ob `iron_ore` in Fach 0 passt. **Das ist eine bewusste
  Kürzung dieses Plans, keine des Entwurfs** — sie gehört als elfte Aufgabe
  nachgereicht, sobald die zehn stehen.
- Flüssigkeitsstände im Tooltip. Der Rahmen steht (`tanks` im Profil), gefüllt
  wird er mit derselben Arbeit wie die Fächer.
- Alles, was der Entwurf unter „Bewusst nicht dabei" führt.
