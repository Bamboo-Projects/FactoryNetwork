package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Welche Stelle einer Angabe gerade dran ist.
 *
 * <p>Die Rechnung dahinter ist klein und geht leicht um eins daneben — und
 * dann zeigt die Hinweiszeile im Editor auf die falsche Stelle und die
 * Vervollständigung bietet das Falsche an. Deshalb steht hier jede Stellung
 * einzeln.
 */
class SignaturesTest {

    private static Signatures.Kind kindAt(String line) {
        Signatures.Where where = Signatures.at("display", line);
        assertNotNull(where, () -> "keine Angabe erkannt in: " + line);
        Signatures.Slot slot = where.slot();
        return slot == null ? null : slot.kind();
    }

    @Test
    @DisplayName("row: erst ein Text, dann ein Ausdruck, dann nichts mehr")
    void rowWalksItsSlots() {
        assertEquals(Signatures.Kind.STRING, kindAt("    row "));
        assertEquals(Signatures.Kind.STRING, kindAt("    row \"Bes"));
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Bestand\" "));
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Bestand\" stor"));
        assertNull(kindAt("    row \"Bestand\" storage.count "),
                "hinter der letzten Stelle ist die Angabe voll");
    }

    @Test
    @DisplayName("Ein Text mit Leerzeichen zählt als ein Wort")
    void aQuotedStringIsOneWord() {
        // Ohne diese Regel rutschte „row \"Freier Platz\"" um ein Wort weiter,
        // und die Hinweiszeile zeigte hinter das Ende der Angabe.
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Freier Platz\" "));
    }

    @Test
    @DisplayName("rate zählt über ein festes Wort hinweg")
    void literalSlotsCount() {
        assertEquals(Signatures.Kind.INT, Signatures.at("worker", "    rate ").slot().kind());
        assertEquals(Signatures.Kind.LITERAL,
                Signatures.at("worker", "    rate 64 ").slot().kind());
        assertEquals(Signatures.Kind.DURATION,
                Signatures.at("worker", "    rate 64 per ").slot().kind());
    }

    @Test
    @DisplayName("Ein Schlüsselwort, das noch getippt wird, ist keine Angabe")
    void anUnfinishedKeywordIsNotASignature() {
        assertNull(Signatures.at("display", "    ro"));
        assertNull(Signatures.at("display", "    "));
        assertNull(Signatures.at("display", "    unbekannt "));
    }

    @Test
    @DisplayName("Eine Angabe der falschen Blockart gilt nicht")
    void signaturesBelongToTheirBlock() {
        assertNull(Signatures.at("display", "    from "),
                "from gehört in einen Worker");
        assertNull(Signatures.at("worker", "    title \"x\" "),
                "title gehört in eine Anzeige");
    }

    @Test
    @DisplayName("Die Form liest sich wie in der Grammatik")
    void shapeReadsLikeTheGrammar() {
        Signatures.Signature row = Signatures.find("display", "row");
        assertEquals("row string expr", row.shape());

        Signatures.Signature rate = Signatures.find("worker", "rate");
        assertEquals("rate int per duration", rate.shape());
    }

    @Test
    @DisplayName("Jede Angabe hat einen Satz Erklärung")
    void everySignatureExplainsItself() {
        for (String block : new String[] {"display", "worker", "group"}) {
            for (Signatures.Signature signature : Signatures.forBlock(block)) {
                assertTrue(signature.help() != null && !signature.help().isBlank(),
                        () -> block + "." + signature.keyword() + " erklärt sich nicht");
            }
        }
    }
}
