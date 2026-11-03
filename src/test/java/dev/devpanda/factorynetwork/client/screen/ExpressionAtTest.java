package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which expression the hover asks about. */
class ExpressionAtTest {

    @Test
    @DisplayName("The whole chain with its arguments, from any part of it")
    void chain() {
        String line = "    if kiste.count(item:iron_ingot) > 64 {";
        assertEquals("kiste.count(item:iron_ingot)", CodeEditor.expressionIn(line, line.indexOf("kiste") + 1));
        assertEquals("kiste.count(item:iron_ingot)", CodeEditor.expressionIn(line, line.indexOf("count") + 2));
        assertEquals("zaehler", CodeEditor.expressionIn("    zaehler = zaehler + 1", 6));
        assertEquals("network.power", CodeEditor.expressionIn("let p = network.power", 18));
        assertEquals("", CodeEditor.expressionIn("let p = 1", 6));
    }
}
