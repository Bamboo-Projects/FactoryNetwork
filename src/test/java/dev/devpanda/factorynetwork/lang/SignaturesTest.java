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

    // ---- Anweisungen -------------------------------------------------------

    private static Signatures.Kind inFn(String line) {
        Signatures.Where where = Signatures.at("fn", line);
        assertNotNull(where, () -> "keine Anweisung erkannt in: " + line);
        Signatures.Slot slot = where.slot();
        return slot == null ? null : slot.kind();
    }

    @Test
    @DisplayName("move ohne from überspringt die Quelle mitsamt ihrem Wort")
    void moveWithoutFromSkipsTheSourcePair() {
        // Ohne diese Regel landete das „to" auf der Stelle der Quelle, und
        // ab da zeigte die Formzeile auf alles Falsche.
        assertEquals(Signatures.Kind.SELECTION, inFn("    move "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 to "));
        assertNull(inFn("    move 64 to kiste_1 "), "danach ist die Anweisung voll");
    }

    @Test
    @DisplayName("move mit from zählt jede Stelle einzeln")
    void moveWithFromWalksEverySlot() {
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 from "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 from ofen_1 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 from ofen_1 to "));
        assertNull(inFn("    move 64 from ofen_1 to kiste_1 "));
    }

    @Test
    @DisplayName("Die Form von move zeigt die Klammern der Grammatik")
    void moveShapeShowsItsBrackets() {
        assertEquals("move menge [from quelle] to ziel",
                Signatures.find("fn", "move").shape());
    }

    @Test
    @DisplayName("let und for führen einen neuen Namen ein")
    void bindingStatementsIntroduceANewName() {
        assertEquals(Signatures.Kind.NEW_NAME, inFn("    let "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    let zahl "));
        assertEquals(Signatures.Kind.EXPR, inFn("    let zahl = "));

        assertEquals(Signatures.Kind.NEW_NAME, inFn("    for "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    for stapel "));
        assertEquals(Signatures.Kind.EXPR, inFn("    for stapel in "));
    }

    @Test
    @DisplayName("Anweisungen gelten in fn, on und multiblock")
    void statementsApplyToEveryCodeBlock() {
        for (String block : new String[] {"fn", "on", "multiblock"}) {
            assertNotNull(Signatures.at(block, "    sleep "),
                    () -> "sleep fehlt in " + block);
        }
        assertNull(Signatures.at("display", "    sleep "),
                "eine Anzeige kennt keine Anweisungen");
    }

    @Test
    @DisplayName("Nach dem Punkt stehen die vier Dinge, die ein Gerät hat")
    void afterTheDotTheFourDeviceMembersAreOffered() {
        java.util.List<String> names = Signatures.MEMBERS.stream()
                .map(Signatures.Member::name).toList();

        assertEquals(java.util.List.of("online", "name", "redstone", "count"), names);
    }

    @Test
    @DisplayName("Jedes Mitglied trägt seine Form")
    void everyMemberCarriesItsShape() {
        for (Signatures.Member member : Signatures.MEMBERS) {
            assertTrue(!member.shape().isBlank(),
                    () -> member.name() + " hat keine Form");
            assertTrue(!member.help().isBlank(),
                    () -> member.name() + " hat keine Erklärung");
        }
    }
}
