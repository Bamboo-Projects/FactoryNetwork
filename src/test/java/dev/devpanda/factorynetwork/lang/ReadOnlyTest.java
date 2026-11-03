package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the editor's hover may evaluate, and what it must not. */
class ReadOnlyTest {

    private static boolean reads(String written) {
        var expr = ReadOnly.parse(written);
        assertNotNull(expr, () -> "nicht lesbar: " + written);
        return ReadOnly.onlyReads(expr);
    }

    @Test
    @DisplayName("Questions are allowed")
    void questions() {
        assertTrue(reads("kiste.count(item:iron_ingot)"));
        assertTrue(reads("storage.count(tag:c/ores) / 64"));
        assertTrue(reads("zaehler + 1"));
        assertTrue(reads("network.power"));
        assertTrue(reads("ofen.redstone()"));
        assertTrue(reads("max(kiste.count(), 3)"));
        assertTrue(reads("storage.items().where(it.amount > 10).count()"));
    }

    @Test
    @DisplayName("Anything that changes something is refused")
    void changes() {
        assertFalse(reads("ofen.redstone(15)"), "redstone mit Zahl setzt");
        assertFalse(reads("kiste.insert(item:coal)"), "insert legt hinein");
        assertFalse(reads("ofen.click()"), "click fasst an");
        assertFalse(reads("craft(item:chest)"), "craft bestellt");
        assertFalse(reads("nachschub()"), "eine Funktion des Programms kann alles");
    }

    @Test
    @DisplayName("Not an expression is no expression")
    void notAnExpression() {
        assertNull(ReadOnly.parse(""));
        assertNull(ReadOnly.parse("worker w {"));
    }
}
