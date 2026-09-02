package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Names that do not exist in the world.
 *
 * <p>The trigger was a black wall: {@code display test { … }} was
 * grammatically flawless, the compiler said "ready", and the board stayed
 * empty because it had a different name. A mistake that looks like a broken
 * network and is a typo.
 */
class NetworkCheckTest {

    /** A network with exactly these names. */
    private static NetworkView net(List<String> connectors, List<String> displays) {
        return new NetworkView() {
            @Override
            public List<String> connectors() {
                return connectors;
            }

            @Override
            public List<String> displays() {
                return displays;
            }
        };
    }

    private static List<Diagnostic> check(String source, NetworkView view) {
        return new Project(Map.of("main.mf", source)).parse(view).diagnostics();
    }

    @Test
    @DisplayName("A display without a wall is reported")
    void anUnknownDisplayIsReported() {
        List<Diagnostic> problems = check("""
                display test {
                    text "hallo"
                }""", net(List.of(), List.of("halle")));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("test") && !problem.isError()),
                () -> "die Meldung fehlt: " + problems);
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("halle")),
                () -> "der Hinweis auf die vorhandene Wand fehlt: " + problems);
    }

    @Test
    @DisplayName("A store at a device that does not exist is reported")
    void astoreAtAnunknownDeviceIsReported() {
        // Otherwise the terminal shows a stock that misses a chest that
        // never existed — and nobody would think to look in the program.
        List<Diagnostic> problems = check("store kist_1 { }",
                net(List.of("kiste_1", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("kist_1") && !problem.isError()),
                () -> "der Vertipper muss auffallen: " + problems);
        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("kiste_1")),
                () -> "und der Hinweis auf das gemeinte Gerät fehlt: " + problems);
    }

    @Test
    @DisplayName("A store at a device that exists is not reported")
    void astoreAtAknownDeviceIsQuiet() {
        List<Diagnostic> problems = check("store kiste_1 { }",
                net(List.of("kiste_1"), List.of()));

        assertFalse(problems.stream().anyMatch(problem ->
                        problem.message().contains("kiste_1")),
                () -> "das ist in Ordnung: " + problems);
    }

    @Test
    @DisplayName("Power in a recipe is reported instead of silently vanishing")
    void powerInArecipeIsReported() {
        // "in 1000 power" parses — power is a selection like any other.
        // It just does nothing: the order inserts items and tops up
        // fluids, power reaches the machine through the
        // power distribution. Since fluids are actually filled in,
        // this silence is more misleading than before — the line next to it
        // keeps what it promises, this one does not.
        List<Diagnostic> problems = check("""
                recipe pressen at presse {
                    in 1 item:iron_ingot
                    in 1000 power
                    out 1 item:iron_nugget
                }""", net(List.of("presse"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().toLowerCase().contains("strom")
                                && !problem.isError()),
                () -> "die Meldung fehlt: " + problems);
    }

    @Test
    @DisplayName("A recipe without power is not reported")
    void arecipeWithoutPowerIsNotReported() {
        List<Diagnostic> problems = check("""
                recipe pressen at presse {
                    in 1 item:iron_ingot
                    in 1000 fluid:water
                    out 1 item:iron_nugget
                }""", net(List.of("presse"), List.of()));

        assertFalse(problems.stream().anyMatch(problem ->
                        problem.message().toLowerCase().contains("strom")),
                () -> "Wasser wird eingefüllt und ist keine Meldung wert: " + problems);
    }

    @Test
    @DisplayName("An unknown device in a move is reported")
    void anUnknownDeviceInAMoveIsReported() {
        List<Diagnostic> problems = check("""
                fn holen() {
                    move 64 item:iron_ore from kist to ofen
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("kist") && !problem.isError()),
                () -> "der Vertipper muss auffallen: " + problems);
    }

    @Test
    @DisplayName("Local names in a move are not devices")
    void localNamesInAMoveAreNotDevices() {
        List<Diagnostic> problems = check("""
                global lager = 0
                const takt = 5

                group oefen {
                    members ofen
                }

                filter erze {
                    tag:c/ores
                }

                fn holen(ziel: Item) {
                    let quelle = kiste
                    move erze from quelle to ziel
                    move 64 item:iron_ore from kiste to oefen
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.isEmpty(),
                () -> "kein Name hier ist ein unbekanntes Gerät: " + problems);
    }

    @Test
    @DisplayName("A move as an expression is checked too")
    void aMoveExpressionIsCheckedToo() {
        List<Diagnostic> problems = check("""
                fn holen() {
                    let bewegt = move 64 item:iron_ore from kist to ofen
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem -> problem.message().contains("kist")),
                () -> "auch hier: " + problems);
    }

    @Test
    @DisplayName("A template named like a device is reported")
    void aTemplateShadowingADeviceIsReported() {
        List<Diagnostic> problems = check("""
                filter brecher_1 {
                    tag:c/ores
                }""", net(List.of("brecher_1"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("verdeckt") && !problem.isError()),
                () -> "die Warnung fehlt: " + problems);
    }

    @Test
    @DisplayName("Without a device of the same name the template is quiet")
    void aTemplateWithoutACollisionIsQuiet() {
        List<Diagnostic> problems = check("""
                filter erze {
                    tag:c/ores
                }""", net(List.of("brecher_1"), List.of()));

        assertTrue(problems.isEmpty(), () -> "unerwartete Meldung: " + problems);
    }

    @Test
    @DisplayName("A typo gets a suggestion")
    void aTypoGetsASuggestion() {
        List<Diagnostic> problems = check("""
                display halel {
                    text "hallo"
                }""", net(List.of(), List.of("halle")));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.hint() != null && problem.hint().contains("Meintest du")),
                () -> problems.toString());
    }

    @Test
    @DisplayName("A display that exists is not reported")
    void aKnownDisplayIsQuiet() {
        assertTrue(check("""
                display halle {
                    text "hallo"
                }""", net(List.of(), List.of("halle"))).isEmpty());
    }

    @Test
    @DisplayName("An unknown target is reported, a group is not")
    void unknownTargetsAreReportedButGroupsAreNot() {
        NetworkView view = net(List.of("kiste_1", "ofen_1"), List.of());

        assertTrue(check("""
                worker haul {
                    from kiste_2
                    to ofen_1
                }""", view).stream().anyMatch(problem -> problem.message().contains("kiste_2")),
                "kiste_2 gibt es nicht");

        // A group sits in the same place and is not a connector.
        assertTrue(check("""
                group oefen {
                    members ofen_1
                }
                worker haul {
                    from kiste_1
                    to oefen
                }""", view).isEmpty(),
                "eine Gruppe aus demselben Programm ist bekannt");
    }

    @Test
    @DisplayName("Without a known network nothing is checked")
    void nothingIsCheckedWithoutANetwork() {
        // A terminal that has only just opened has no names yet. Reporting
        // each of them would be a line full of warnings that a second
        // later disappear on their own.
        assertTrue(check("""
                display test {
                    text "hallo"
                }""", NetworkView.NONE).isEmpty());
    }

    @Test
    @DisplayName("A warning does not hold up deployment")
    void aWarningDoesNotBlockDeployment() {
        var result = new Project(Map.of("main.mf", """
                display morgen_gebaut {
                    text "hallo"
                }""")).parse(net(List.of(), List.of("halle")));

        assertFalse(result.hasErrors(),
                "eine Wand, die man erst morgen baut, darf man heute schreiben");
    }

    // ---- The side the connector is attached to -----------------------------

    /** A network in which a device has a known profile. */
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
    @DisplayName("A target without an item slot on the connected side is reported")
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
    @DisplayName("A fluid worker at the tank is not reported")
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
    @DisplayName("Nothing is claimed about a device without a profile")
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
    @DisplayName("A worker without a filter triggers no side warning")
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

    @Test
    @DisplayName("A typo inside a list is found too")
    void atypoInsideAlistIsFoundToo() {
        // A list literal is the only expression that contains expressions.
        // Whoever does not descend into it while checking lets a wrong
        // device name slip through right there — and it will soon be there,
        // because a list of targets is the obvious use.
        List<Diagnostic> problems = check("""
                fn holen() {
                    let wege = [move 64 item:iron_ore from kist to ofen]
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("kist")),
                () -> "der Vertipper in der Liste muss auffallen: " + problems);
    }
}
