package dev.devpanda.factorynetwork.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die Regeln der Namensvergabe ohne Server. Was hier durchgeht, steht
 * später im Code des Spielers — deshalb gelten dieselben Regeln wie im
 * Übersetzer.
 */
class ConnectorNamingTest {

    @Test
    @DisplayName("Namen werden nach NFC normalisiert")
    void namesAreNormalized() {
        // Einmal zusammengesetzt, einmal zerlegt — dasselbe Ergebnis.
        assertEquals(ConnectorNaming.normalize("ofen_süd"),
                ConnectorNaming.normalize("ofen_süd"));
    }

    @Test
    void leadingAndTrailingSpaceIsRemoved() {
        assertEquals("crusher_1", ConnectorNaming.normalize("  crusher_1  "));
    }

    @Test
    @DisplayName("Umlaute sind gültige Namen")
    void umlautsAreValid() {
        assertTrue(ConnectorNaming.isValidIdentifier("ofen_süd"));
        assertTrue(ConnectorNaming.isValidIdentifier("_hilfsofen"));
        assertTrue(ConnectorNaming.isValidIdentifier("crusher_1"));
    }

    @Test
    void spacesAndPunctuationAreNot() {
        assertFalse(ConnectorNaming.isValidIdentifier("mein ofen"));
        assertFalse(ConnectorNaming.isValidIdentifier("ofen-1"));
        assertFalse(ConnectorNaming.isValidIdentifier("1ofen"));
        assertFalse(ConnectorNaming.isValidIdentifier(""));
    }

    @Test
    @DisplayName("Die angehängte Nummer wird für das Weiterzählen abgetrennt")
    void suffixIsStripped() {
        assertEquals("furnace", ConnectorNaming.stripSuffix("furnace_12"));
        assertEquals("blast_furnace", ConnectorNaming.stripSuffix("blast_furnace_3"));
    }

    @Test
    @DisplayName("Ein Name ohne Nummer bleibt unverändert")
    void nameWithoutSuffixStaysWhole() {
        assertEquals("furnace", ConnectorNaming.stripSuffix("furnace"));
        assertEquals("ofen_süd", ConnectorNaming.stripSuffix("ofen_süd"));
        assertEquals("furnace_", ConnectorNaming.stripSuffix("furnace_"));
    }
}
