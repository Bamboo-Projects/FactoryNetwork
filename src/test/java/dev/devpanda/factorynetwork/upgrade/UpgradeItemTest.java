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
 * Every upgrade is also an item — with model, texture and name.
 *
 * <p>An upgrade that exists in the enumeration and the recipe but not in the
 * inventory only shows up in the game, and there only to whoever builds it.
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
    @DisplayName("For every upgrade there is texture, model and two names")
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
    @DisplayName("And a recipe, otherwise no one gets to it")
    void everyUpgradeCanBeBuilt() {
        for (Upgrade upgrade : upgrades()) {
            assertTrue(Files.exists(DATA.resolve("recipe/" + upgrade.id() + ".json")),
                    "Rezept fehlt: " + upgrade.id());
        }
    }
}
