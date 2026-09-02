package dev.devpanda.factorynetwork.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which slot of an entry is currently up.
 *
 * <p>The arithmetic behind it is small and easily off by one — and then the
 * hint line in the editor points to the wrong slot and the completion offers
 * the wrong thing. That is why every position is listed here individually.
 */
class SignaturesTest {

    private static Signatures.Kind kindAt(String line) {
        Signatures.Where where = Signatures.at("display", line);
        assertNotNull(where, () -> "keine Angabe erkannt in: " + line);
        Signatures.Slot slot = where.slot();
        return slot == null ? null : slot.kind();
    }

    @Test
    @DisplayName("row: first a text, then an expression, then nothing more")
    void rowWalksItsSlots() {
        assertEquals(Signatures.Kind.STRING, kindAt("    row "));
        assertEquals(Signatures.Kind.STRING, kindAt("    row \"Bes"));
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Bestand\" "));
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Bestand\" stor"));
        assertNull(kindAt("    row \"Bestand\" storage.count "),
                "hinter der letzten Stelle ist die Angabe voll");
    }

    @Test
    @DisplayName("A text with spaces counts as one word")
    void aQuotedStringIsOneWord() {
        // Without this rule „row \"Freier Platz\"" slipped one word further,
        // and the hint line pointed past the end of the entry.
        assertEquals(Signatures.Kind.EXPR, kindAt("    row \"Freier Platz\" "));
    }

    @Test
    @DisplayName("rate counts across a fixed word")
    void literalSlotsCount() {
        assertEquals(Signatures.Kind.INT, Signatures.at("worker", "    rate ").slot().kind());
        assertEquals(Signatures.Kind.LITERAL,
                Signatures.at("worker", "    rate 64 ").slot().kind());
        assertEquals(Signatures.Kind.DURATION,
                Signatures.at("worker", "    rate 64 per ").slot().kind());
    }

    @Test
    @DisplayName("A keyword still being typed is no entry")
    void anUnfinishedKeywordIsNotASignature() {
        assertNull(Signatures.at("display", "    ro"));
        assertNull(Signatures.at("display", "    "));
        assertNull(Signatures.at("display", "    unbekannt "));
    }

    @Test
    @DisplayName("An entry of the wrong block kind does not count")
    void signaturesBelongToTheirBlock() {
        assertNull(Signatures.at("display", "    from "),
                "from gehört in einen Worker");
        assertNull(Signatures.at("worker", "    title \"x\" "),
                "title gehört in eine Anzeige");
    }

    @Test
    @DisplayName("The shape reads like in the grammar")
    void shapeReadsLikeTheGrammar() {
        Signatures.Signature row = Signatures.find("display", "row");
        assertEquals("row string expr", row.shape());

        Signatures.Signature rate = Signatures.find("worker", "rate");
        assertEquals("rate int per duration", rate.shape());
    }

    @Test
    @DisplayName("Every entry has a sentence of explanation")
    void everySignatureExplainsItself() {
        for (String block : new String[] {"display", "worker", "group"}) {
            for (Signatures.Signature signature : Signatures.forBlock(block)) {
                assertTrue(signature.help() != null && !signature.help().isBlank(),
                        () -> block + "." + signature.keyword() + " erklärt sich nicht");
            }
        }
    }

    // ---- Statements --------------------------------------------------------

    private static Signatures.Kind inFn(String line) {
        Signatures.Where where = Signatures.at("fn", line);
        assertNotNull(where, () -> "keine Anweisung erkannt in: " + line);
        Signatures.Slot slot = where.slot();
        return slot == null ? null : slot.kind();
    }

    @Test
    @DisplayName("move without from skips the source along with its word")
    void moveWithoutFromSkipsTheSourcePair() {
        // Without this rule the „to" landed on the slot of the source, and
        // from there on the shape line pointed at everything wrong.
        assertEquals(Signatures.Kind.SELECTION, inFn("    move "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 to "));
        assertNull(inFn("    move 64 to kiste_1 "), "danach ist die Anweisung voll");
    }

    @Test
    @DisplayName("move with from counts every slot individually")
    void moveWithFromWalksEverySlot() {
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 from "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    move 64 from ofen_1 "));
        assertEquals(Signatures.Kind.TARGET, inFn("    move 64 from ofen_1 to "));
        assertNull(inFn("    move 64 from ofen_1 to kiste_1 "));
    }

    @Test
    @DisplayName("The shape of move shows the brackets of the grammar")
    void moveShapeShowsItsBrackets() {
        assertEquals("move menge [from quelle] to ziel",
                Signatures.find("fn", "move").shape());
    }

    @Test
    @DisplayName("let and for introduce a new name")
    void bindingStatementsIntroduceANewName() {
        assertEquals(Signatures.Kind.NEW_NAME, inFn("    let "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    let zahl "));
        assertEquals(Signatures.Kind.EXPR, inFn("    let zahl = "));

        assertEquals(Signatures.Kind.NEW_NAME, inFn("    for "));
        assertEquals(Signatures.Kind.LITERAL, inFn("    for stapel "));
        assertEquals(Signatures.Kind.EXPR, inFn("    for stapel in "));
    }

    @Test
    @DisplayName("Statements apply in fn, on and multiblock")
    void statementsApplyToEveryCodeBlock() {
        for (String block : new String[] {"fn", "on", "multiblock"}) {
            assertNotNull(Signatures.at(block, "    sleep "),
                    () -> "sleep fehlt in " + block);
        }
        assertNull(Signatures.at("display", "    sleep "),
                "eine Anzeige kennt keine Anweisungen");
    }

    @Test
    // No number in the name: there were once four, then seven, now eight —
    // and the number in the title was each time the one from the week before
    // last.
    @DisplayName("After the dot comes what a device has")
    void afterTheDotTheDeviceMembersAreOffered() {
        java.util.List<String> names = Signatures.MEMBERS.stream()
                .map(Signatures.Member::name).toList();

        assertEquals(java.util.List.of("online", "name", "redstone", "count",
                "insert", "items", "slots", "energy", "click"), names);
    }

    @Test
    @DisplayName("Every member carries its shape")
    void everyMemberCarriesItsShape() {
        for (Signatures.Member member : Signatures.MEMBERS) {
            assertTrue(!member.shape().isBlank(),
                    () -> member.name() + " hat keine Form");
            assertTrue(!member.help().isBlank(),
                    () -> member.name() + " hat keine Erklärung");
        }
    }
}
