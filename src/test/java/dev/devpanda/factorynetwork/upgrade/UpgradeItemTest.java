package dev.devpanda.factorynetwork.upgrade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    private static final Path DATA =
            Path.of("src/main/resources/data/factorynetwork");

    private static String read(Path file) throws IOException {
        assertTrue(Files.exists(file), file + " fehlt");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static List<Upgrade> upgrades() {
        List<Upgrade> all = new ArrayList<>();
        all.addAll(List.of(Ability.values()));
        all.addAll(List.of(Card.values()));
        return all;
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

    @Test
    @DisplayName("Und ein Rezept, sonst kommt niemand daran")
    void everyUpgradeCanBeBuilt() {
        for (Upgrade upgrade : upgrades()) {
            assertTrue(Files.exists(DATA.resolve("recipe/" + upgrade.id() + ".json")),
                    "Rezept fehlt: " + upgrade.id());
        }
    }
}
