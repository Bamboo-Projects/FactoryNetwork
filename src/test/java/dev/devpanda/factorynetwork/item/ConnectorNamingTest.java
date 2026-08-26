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
        // <b>Die beiden Literale sehen gleich aus und sind es nicht.</b>
        // Genau darin lag der Fehler: Vorher standen hier zwei
        // zusammengesetzte Umlaute — byteweise dasselbe Literal zweimal,
        // also assertEquals(f(x), f(x)) und damit wahr für jede Fassung,
        // auch für eine, die gar nicht normalisiert. Die Längenprüfung
        // darunter ist der Wächter dagegen: Fällt das zerlegte ü beim
        // nächsten Bearbeiten wieder zusammen, wird sie rot.
        String zerlegt = "ofen_süd";
        String zusammen = "ofen_süd";
        assertEquals(zusammen.length() + 1, zerlegt.length(),
                "Die zerlegte Form muss wirklich ein Zeichen länger sein");
        assertEquals(zusammen, ConnectorNaming.normalize(zerlegt),
                "Wer den Namen zerlegt eingibt, meint denselben Connector");
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

    @Test
    @DisplayName("Ein Anlagenname mit Schrägstrich ist ein gültiger Gerätename")
    void aninstanceNameWithAslashIsAvalidDeviceName() {
        // anlagen.md nennt die Beschriftungspistole den Weg, auf dem eine
        // Anlage entsteht: werk_1/eingang. Nur ging das nicht — die Prüfung
        // kannte den Schrägstrich nicht, und Fenster wie Pistole lehnten ab.
        // Ein Multiblock liess sich damit im Spiel gar nicht bauen.
        assertTrue(ConnectorNaming.isValidDeviceName("werk_1/eingang"));
        assertTrue(ConnectorNaming.isValidDeviceName("schmelze/ofen_2"));
        assertEquals(ConnectorNaming.Kind.NONE,
                ConnectorNaming.check("werk_1/eingang", null).kind());
    }

    @Test
    @DisplayName("Zwei Schrägstriche sind keine zwei Ebenen")
    void twoSlashesAreNotTwoLevels() {
        // Eine Anlage und eine Rolle, mehr nicht. Eine zweite Ebene gäbe es
        // im Code nicht wiederzugeben — dort steht anlage.rolle und sonst
        // nichts.
        assertFalse(ConnectorNaming.isValidDeviceName("a/b/c"));
        assertFalse(ConnectorNaming.isValidDeviceName("/eingang"));
        assertFalse(ConnectorNaming.isValidDeviceName("werk_1/"));
        assertFalse(ConnectorNaming.isValidDeviceName("werk 1/eingang"));
    }

    @Test
    @DisplayName("Der Schrägstrich bleibt aus dem Bezeichner heraus")
    void theslashStaysOutOfTheIdentifier() {
        // isValidIdentifier sagt, was in Manifold als Name steht. Dort gibt
        // es keinen Schrägstrich — er steht nur in der Beschriftung.
        assertFalse(ConnectorNaming.isValidIdentifier("werk_1/eingang"));
    }
}
