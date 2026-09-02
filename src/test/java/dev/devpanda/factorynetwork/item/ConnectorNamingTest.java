package dev.devpanda.factorynetwork.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the rules of name assignment without a server. What passes here
 * stands later in the player's code — that is why the same rules apply as in
 * the translator.
 */
class ConnectorNamingTest {

    @Test
    @DisplayName("Names are normalized to NFC")
    void namesAreNormalized() {
        // <b>The two literals look the same and are not.</b>
        // That is exactly where the error lay: previously two composed umlauts
        // stood here — byte for byte the same literal twice, that is,
        // assertEquals(f(x), f(x)) and thus true for every version, even for
        // one that does not normalize at all. The length check below is the
        // guard against that: if the decomposed ü falls back together on the
        // next edit, it turns red.
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
    @DisplayName("Umlauts are valid names")
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
    @DisplayName("The appended number is split off for the counting on")
    void suffixIsStripped() {
        assertEquals("furnace", ConnectorNaming.stripSuffix("furnace_12"));
        assertEquals("blast_furnace", ConnectorNaming.stripSuffix("blast_furnace_3"));
    }

    @Test
    @DisplayName("A name without a number stays unchanged")
    void nameWithoutSuffixStaysWhole() {
        assertEquals("furnace", ConnectorNaming.stripSuffix("furnace"));
        assertEquals("ofen_süd", ConnectorNaming.stripSuffix("ofen_süd"));
        assertEquals("furnace_", ConnectorNaming.stripSuffix("furnace_"));
    }

    @Test
    @DisplayName("An instance name with a slash is a valid device name")
    void aninstanceNameWithAslashIsAvalidDeviceName() {
        // anlagen.md calls the labeling gun the way in which an instance
        // comes about: werk_1/eingang. Only it did not work — the check did
        // not know the slash, and both the window and the gun refused it. A
        // multiblock could not be built in the game at all with that.
        assertTrue(ConnectorNaming.isValidDeviceName("werk_1/eingang"));
        assertTrue(ConnectorNaming.isValidDeviceName("schmelze/ofen_2"));
        assertEquals(ConnectorNaming.Kind.NONE,
                ConnectorNaming.check("werk_1/eingang", null).kind());
    }

    @Test
    @DisplayName("Two slashes are not two levels")
    void twoSlashesAreNotTwoLevels() {
        // An instance and a role, nothing more. A second level could not be
        // represented in the code — there stands anlage.rolle and nothing
        // else.
        assertFalse(ConnectorNaming.isValidDeviceName("a/b/c"));
        assertFalse(ConnectorNaming.isValidDeviceName("/eingang"));
        assertFalse(ConnectorNaming.isValidDeviceName("werk_1/"));
        assertFalse(ConnectorNaming.isValidDeviceName("werk 1/eingang"));
    }

    @Test
    @DisplayName("The slash stays out of the identifier")
    void theslashStaysOutOfTheIdentifier() {
        // isValidIdentifier says what stands as a name in Manifold. There is
        // no slash there — it stands only in the labeling.
        assertFalse(ConnectorNaming.isValidIdentifier("werk_1/eingang"));
    }
}
