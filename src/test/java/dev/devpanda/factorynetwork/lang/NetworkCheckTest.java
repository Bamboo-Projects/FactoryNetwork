package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Namen, die es in der Welt nicht gibt.
 *
 * <p>Der Anlass war eine schwarze Wand: {@code display test { … }} war
 * grammatisch tadellos, der Übersetzer sagte „bereit", und die Tafel blieb
 * leer, weil sie anders hieß. Ein Fehler, der aussieht wie ein kaputtes Netz
 * und ein Tippfehler ist.
 */
class NetworkCheckTest {

    /** Ein Netz mit genau diesen Namen. */
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
    @DisplayName("Eine Anzeige ohne Wand wird gemeldet")
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
    @DisplayName("Ein unbekanntes Gerät in einem move wird gemeldet")
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
    @DisplayName("Örtliche Namen in einem move sind keine Geräte")
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
    @DisplayName("Auch ein move als Ausdruck wird geprüft")
    void aMoveExpressionIsCheckedToo() {
        List<Diagnostic> problems = check("""
                fn holen() {
                    let bewegt = move 64 item:iron_ore from kist to ofen
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem -> problem.message().contains("kist")),
                () -> "auch hier: " + problems);
    }

    @Test
    @DisplayName("Eine Vorlage, die wie ein Gerät heißt, wird gemeldet")
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
    @DisplayName("Ohne Gerät gleichen Namens ist die Vorlage still")
    void aTemplateWithoutACollisionIsQuiet() {
        List<Diagnostic> problems = check("""
                filter erze {
                    tag:c/ores
                }""", net(List.of("brecher_1"), List.of()));

        assertTrue(problems.isEmpty(), () -> "unerwartete Meldung: " + problems);
    }

    @Test
    @DisplayName("Ein Vertipper bekommt einen Vorschlag")
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
    @DisplayName("Eine Anzeige, die es gibt, wird nicht gemeldet")
    void aKnownDisplayIsQuiet() {
        assertTrue(check("""
                display halle {
                    text "hallo"
                }""", net(List.of(), List.of("halle"))).isEmpty());
    }

    @Test
    @DisplayName("Ein unbekanntes Ziel wird gemeldet, eine Gruppe nicht")
    void unknownTargetsAreReportedButGroupsAreNot() {
        NetworkView view = net(List.of("kiste_1", "ofen_1"), List.of());

        assertTrue(check("""
                worker haul {
                    from kiste_2
                    to ofen_1
                }""", view).stream().anyMatch(problem -> problem.message().contains("kiste_2")),
                "kiste_2 gibt es nicht");

        // Eine Gruppe steht an derselben Stelle und ist kein Connector.
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
    @DisplayName("Ohne bekanntes Netz wird nichts geprüft")
    void nothingIsCheckedWithoutANetwork() {
        // Ein Terminal, das gerade erst aufgeht, hat noch keine Namen. Jeden
        // davon zu melden wäre eine Zeile voller Warnungen, die eine Sekunde
        // später von selbst verschwinden.
        assertTrue(check("""
                display test {
                    text "hallo"
                }""", NetworkView.NONE).isEmpty());
    }

    @Test
    @DisplayName("Eine Warnung hält das Übernehmen nicht auf")
    void aWarningDoesNotBlockDeployment() {
        var result = new Project(Map.of("main.mf", """
                display morgen_gebaut {
                    text "hallo"
                }""")).parse(net(List.of(), List.of("halle")));

        assertFalse(result.hasErrors(),
                "eine Wand, die man erst morgen baut, darf man heute schreiben");
    }

    // ---- Die Seite, an der der Connector hängt -----------------------------

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

    @Test
    @DisplayName("Auch in einer Liste wird ein Vertipper gefunden")
    void atypoInsideAlistIsFoundToo() {
        // Ein Listenliteral ist der einzige Ausdruck, der Ausdrücke enthält.
        // Wer beim Prüfen nicht hineingeht, lässt genau dort einen falschen
        // Gerätenamen durch — und dort steht er bald, denn eine Liste von
        // Zielen ist der offensichtliche Gebrauch.
        List<Diagnostic> problems = check("""
                fn holen() {
                    let wege = [move 64 item:iron_ore from kist to ofen]
                }""", net(List.of("kiste", "ofen"), List.of()));

        assertTrue(problems.stream().anyMatch(problem ->
                        problem.message().contains("kist")),
                () -> "der Vertipper in der Liste muss auffallen: " + problems);
    }
}
