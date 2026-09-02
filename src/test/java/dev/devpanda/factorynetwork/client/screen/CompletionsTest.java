package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the editor suggests at which spot.
 *
 * <p>Only the spots that do without the registry are checked — items and
 * tags need a loaded game and therefore stand in the GameTests.
 *
 * <p>The question behind it is always the same: <b>Does the suggestion fit
 * what the grammar allows at this spot?</b> A suggestion that shows the same
 * thing everywhere helps nowhere — and one that offers something forbidden is
 * worse than none.
 */
class CompletionsTest {

    private static List<String> texts(List<Completions.Entry> entries) {
        return entries.stream().map(Completions.Entry::text).toList();
    }

    /** Suggestions at the end of the last line. */
    private static List<String> at(String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return texts(Completions.at(code, last, code.get(last).length()));
    }

    @Test
    @DisplayName("Inside a display stand its entries, no statements")
    void insideDisplayOnlyDisplayEntries() {
        List<String> shown = at("display halle {", "    ");

        assertTrue(shown.contains("title"), () -> "title fehlt: " + shown);
        assertTrue(shown.contains("row"), () -> "row fehlt: " + shown);
        assertTrue(shown.contains("progress"), () -> "progress fehlt: " + shown);
        assertTrue(shown.contains("scale"), () -> "scale fehlt: " + shown);
        assertFalse(shown.contains("if"),
                () -> "eine Anzeige kennt keine Anweisungen: " + shown);
        assertFalse(shown.contains("let"), () -> shown.toString());
        assertFalse(shown.contains("from"),
                () -> "from gehört in einen Worker, nicht in eine Anzeige: " + shown);
    }

    /** What a suggestion really inserts. */
    private static String insertOf(String wanted, String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return Completions.at(code, last, code.get(last).length()).stream()
                .filter(entry -> entry.text().equals(wanted))
                .map(Completions.Entry::insert)
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("What has parentheses is inserted with parentheses")
    void whatHasParenthesesIsInsertedWithThem() {
        // Noticed on first playing: the suggestion set the name and nothing
        // more, and you typed the parentheses in by hand every time. Whether
        // any belong is in the form — it needs no second list that drifts
        // apart.
        assertEquals("log()", insertOf("log", "fn f() {", "    lo"),
                "log ist ein Aufruf");
        assertEquals("count()",
                insertOf("count", "fn f() {", "    storage.items().cou"),
                "count() steht mit Klammern in der Form");
        assertEquals("sum()",
                insertOf("sum", "fn f() {", "    storage.items().su"),
                "sum() auch");
    }

    @Test
    @DisplayName("What has none gets none either")
    void whatHasNoneGetsNone() {
        // network.power lies in the controller and is not a query into the
        // world — that is why it stands without parentheses, and the
        // suggestion must not turn it into a call.
        assertEquals("power", insertOf("power", "fn f() {", "    network.pow"));
        assertEquals("amount",
                insertOf("amount", "fn f() {", "    log(storage.items().where(it.amo"));
    }

    @Test
    @DisplayName("One's own function stands in the body and is called")
    void anownFunctionStandsInTheBodyAndIsCalled() {
        // It was not offered there at all: whoever wanted to call their own
        // function typed the name out in full.
        assertEquals("kuehlen()",
                insertOf("kuehlen", "fn kuehlen() {", "}", "fn main() {", "    kue"));
    }

    @Test
    @DisplayName("The offered prefixes are the ones that really exist")
    void theofferedPrefixesAreTheOnesThatExist() {
        List<String> shown = at("worker w {", "    filter ");

        // This list was once hard-coded in the editor, four entries long, and
        // did not know "chemical:" — although it has existed since 26.08.
        // Now it comes from the registry, and so the same thing stands there
        // that the translator assumes.
        for (String prefix : dev.devpanda.factorynetwork.runtime.ResourceKinds
                .selectorPrefixes()) {
            assertTrue(shown.contains(prefix + ":"),
                    () -> prefix + ": fehlt im Editor: " + shown);
        }
        assertTrue(shown.contains("chemical:"),
                () -> "chemical: hat vier Monate gefehlt: " + shown);
    }

    @Test
    @DisplayName("At the top level filter stands next to the other declarations")
    void filterIsADeclaration() {
        List<String> shown = at("");

        assertTrue(shown.contains("filter"), () -> "filter fehlt: " + shown);
        assertTrue(shown.contains("group"), () -> "group fehlt: " + shown);
    }

    @Test
    @DisplayName("Inside a template stands except and no declaration")
    void insideATemplateOnlyExcept() {
        List<String> shown = at("filter erze {", "    ");

        assertTrue(shown.contains("except"), () -> "except fehlt: " + shown);
        assertFalse(shown.contains("worker"),
                () -> "in einer Vorlage steht keine Deklaration: " + shown);
        assertFalse(shown.contains("from"),
                () -> "from gehört in einen Worker: " + shown);
        assertFalse(shown.contains("if"),
                () -> "eine Vorlage kennt keine Anweisungen: " + shown);
    }

    @Test
    @DisplayName("At a selection spot stand the templates of the project")
    void templatesAreOfferedWhereASelectionFits() {
        List<String> shown = at("filter erze {", "    tag:c/ores", "}", "",
                "worker holt {", "    from grube", "    to storage", "    filter ");

        assertTrue(shown.contains("erze"), () -> "die Vorlage fehlt: " + shown);
    }

    @Test
    @DisplayName("After it. stand the members of an entry")
    void afterItTheEntryMembers() {
        List<String> shown = at("fn test() {", "    log(storage.items().where(it.");

        assertTrue(shown.contains("amount"), () -> "amount fehlt: " + shown);
        assertTrue(shown.contains("item"), () -> "item fehlt: " + shown);
        assertFalse(shown.contains("online"),
                () -> "ein Posten ist kein Gerät: " + shown);
        assertFalse(shown.contains("insert"), () -> shown.toString());
    }

    @Test
    @DisplayName("Only what the runtime can actually do is suggested")
    void onlyWhatTheRuntimeCanDo() {
        List<String> shown = at("worker haul {", "    to ");

        assertTrue(shown.contains("storage"), () -> "storage fehlt: " + shown);
        // world, network, workers and multiblocks are parsed by the language,
        // but the interpreter knows none of them: whoever writes them gets
        // „Als Ziel taugt nur ein Name" — a message that contradicts the
        // suggestion that triggered it.
        assertFalse(shown.contains("network"), () -> shown.toString());
        assertFalse(shown.contains("multiblocks"), () -> shown.toString());
        // And crafting is a source: crafting is done into the storage.
        assertFalse(shown.contains("crafting"),
                () -> "crafting ist kein Ziel: " + shown);
    }

    @Test
    @DisplayName("crafting stands at from, because it is a source")
    void craftingIsOfferedAsAsource() {
        List<String> shown = at("worker nachschub {", "    from ");

        assertTrue(shown.contains("crafting"), () -> "crafting fehlt: " + shown);
        assertTrue(shown.contains("storage"), () -> "storage fehlt: " + shown);
    }

    @Test
    @DisplayName("In the on header stand the events and no declarations")
    void theOnHeaderOffersEvents() {
        List<String> shown = at("on ");

        assertTrue(shown.contains("device_output"), () -> "device_output fehlt: " + shown);
        assertTrue(shown.contains("redstone_changed"),
                () -> "redstone_changed fehlt: " + shown);
        assertFalse(shown.contains("worker"),
                () -> "hinter on steht kein Deklarationswort: " + shown);
        assertFalse(shown.contains("global"), () -> shown.toString());
    }

    @Test
    @DisplayName("After on stand the events")
    void afterOnTheEventsAreOffered() {
        List<String> shown = at("on ");

        // The four of the network stand in no file. Whoever does not know them
        // by heart types something — and an on with a mistyped name is
        // accepted and never runs.
        assertTrue(shown.contains("device_online"), () -> "device_online fehlt: " + shown);
        assertTrue(shown.contains("device_offline"), () -> "device_offline fehlt: " + shown);
        assertTrue(shown.contains("device_changed"), () -> "device_changed fehlt: " + shown);
        assertTrue(shown.contains("redstone_changed"), () -> "redstone_changed fehlt: " + shown);
        assertFalse(shown.contains("worker"),
                () -> "hier steht ein Ereignisname, keine Deklaration: " + shown);
    }

    @Test
    @DisplayName("After on the self-declared events belong too")
    void afterOnOwnEventsCountToo() {
        List<String> shown = at("event Fertig(nummer: Int)", "", "on ");

        assertTrue(shown.contains("Fertig"), () -> "das eigene Ereignis fehlt: " + shown);
    }

    @Test
    @DisplayName("In a recipe stand in and out")
    void insideArecipeOnlyInAndOut() {
        List<String> shown = at("recipe mahlen at brecher {", "    ");

        assertEquals(List.of("in", "out"), shown);
    }

    @Test
    @DisplayName("In a worker stand its entries")
    void insideWorkerOnlyWorkerEntries() {
        List<String> shown = at("worker haul {", "    ");

        assertTrue(shown.contains("from"), () -> "from fehlt: " + shown);
        assertFalse(shown.contains("title"),
                () -> "title gehört in eine Anzeige: " + shown);
    }

    @Test
    @DisplayName("In a group stand members and strategy")
    void insideGroupOnlyGroupEntries() {
        List<String> shown = at("group oefen {", "    ");

        assertEquals(List.of("members", "strategy"), shown);
    }

    @Test
    @DisplayName("After a finished declaration name comes the brace")
    void nothingAfterADeclarationName() {
        assertTrue(at("display halle ").isEmpty(),
                "hinter dem Namen gibt es nichts vorzuschlagen");
        assertTrue(at("worker haul ").isEmpty(), "und im Worker genauso");
    }

    @Test
    @DisplayName("What already stands complete is no suggestion")
    void noSuggestionForAnExactMatch() {
        // "worker" is itself a declaration; whoever has typed it to the end
        // does not need it offered.
        assertFalse(at("worker").contains("worker"),
                "ein Eintrag, der nichts ändert, verdeckt nur die Zeile darunter");
    }

    @Test
    @DisplayName("At the top level stand the declarations")
    void topLevelOffersDeclarations() {
        List<String> shown = at("disp");

        assertTrue(shown.contains("display"), () -> shown.toString());
    }

    /** The form details of the suggestions. */
    private static List<String> details(String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return Completions.at(code, last, code.get(last).length()).stream()
                .map(Completions.Entry::detail).toList();
    }

    @Test
    @DisplayName("A suggestion brings its form with it")
    void everyBlockEntryCarriesItsShape() {
        List<String> shown = at("display halle {", "    ");
        List<String> shapes = details("display halle {", "    ");

        int row = shown.indexOf("row");
        assertEquals("string expr", shapes.get(row),
                "ohne die Form ist ein Vorschlag ein Wort, das man nachschlagen muss");
        assertEquals("string", shapes.get(shown.indexOf("title")));
    }

    @Test
    @DisplayName("After row no row comes any more")
    void insideAnEntryTheKeywordsAreGone() {
        // That was the actual complaint: after "row" the editor offered
        // "title, row, text …" again — that is, everything except what belongs
        // there.
        assertFalse(at("display halle {", "    row ").contains("row"),
                "an dieser Stelle steht ein Text, kein Schlüsselwort");
        assertFalse(at("display halle {", "    row \"Bestand\" ").contains("title"),
                "und hier ein Ausdruck");
    }

    @Test
    @DisplayName("At a text spot there is nothing to suggest")
    void nothingToOfferForAString() {
        assertTrue(at("display halle {", "    title ").isEmpty(),
                "einen freien Text kann niemand vorschlagen — die Formzeile sagt es");
    }

    @Test
    @DisplayName("At an expression spot stand the built-ins")
    void expressionSlotsOfferTheBuiltins() {
        List<String> shown = at("display halle {", "    text ");

        assertTrue(shown.contains("storage"), () -> shown.toString());
        // network used to stand here too: the language parses it, but it is
        // evaluated nowhere — not even in a display. A suggestion that leads
        // into an error message is worse than none.
        assertFalse(shown.contains("network"),
                () -> "network wird nicht ausgewertet: " + shown);
        assertFalse(shown.contains("if"), () -> "kein Ausdruck ist eine Anweisung: " + shown);
    }

    @Test
    @DisplayName("After strategy stand the distributions")
    void strategySlotOffersStrategies() {
        assertTrue(at("worker haul {", "    strategy ").contains("round_robin"));
    }

    @Test
    @DisplayName("After button stand the functions of the project")
    void buttonSlotOffersFunctions() {
        List<String> shown = at("fn leeren() {", "}", "display halle {",
                "    button \"Leeren\" ");

        assertTrue(shown.contains("leeren"), () -> shown.toString());
    }

    @Test
    @DisplayName("After rate stands the fixed word per")
    void literalSlotOffersItsWord() {
        assertEquals(List.of("per"), at("worker haul {", "    rate 64 "));
    }

    @Test
    @DisplayName("A full entry suggests nothing more")
    void aCompleteEntryOffersNothing() {
        assertTrue(at("worker haul {", "    rate 64 per 5s ").isEmpty());
    }

    @Test
    @DisplayName("After move 64 from and to are up for choice")
    void anOptionalWordOffersBothPaths() {
        List<String> shown = at("fn test() {", "    move 64 ");

        assertTrue(shown.contains("from"), () -> shown.toString());
        assertTrue(shown.contains("to"),
                () -> "die Quelle darf auch fehlen: " + shown);
    }

    @Test
    @DisplayName("In a function statements and expressions stand side by side")
    void codeBlocksOfferBoth() {
        assertTrue(at("fn test() {", "    ").contains("move"),
                "die Anweisungen stehen zuerst");
        // With a started word the expression gets through. Without one the
        // list is already full of the statements — that is right, because a
        // line far more often begins with a statement.
        assertTrue(at("fn test() {", "    sto").contains("storage"),
                "ein Ausdruck ist auch eine Anweisung");
    }

    @Test
    @DisplayName("A statement brings its form with it")
    void statementsCarryTheirShape() {
        List<String> shown = at("fn test() {", "    ");
        List<String> shapes = details("fn test() {", "    ");

        assertEquals("menge [from quelle] to ziel", shapes.get(shown.indexOf("move")));
        assertEquals("duration", shapes.get(shown.indexOf("sleep")));
    }

    @Test
    @DisplayName("After emit stand the events of the project")
    void emitOffersProjectEvents() {
        List<String> shown = at("event ofen_fertig(x: Int)", "fn test() {", "    emit ");

        assertTrue(shown.contains("ofen_fertig"), () -> shown.toString());
    }

    @Test
    @DisplayName("For a new name there is no suggestion")
    void newNamesAreNotSuggested() {
        assertTrue(at("fn test() {", "    let ").isEmpty(),
                "wie hiesse er auch — er entsteht ja gerade");
    }

    // ---- After the dot ----------------------------------------------------

    /**
     * A network with exactly this connector, for the duration of a test.
     *
     * <p>{@link dev.devpanda.factorynetwork.client.ClientNetworkState} is
     * static — there is one client and one network. For the test it is filled
     * and afterwards emptied, otherwise the next test sees a network that does
     * not exist.
     */
    private static void withNetwork(String connector, Runnable body) {
        dev.devpanda.factorynetwork.client.ClientNetworkState.accept(
                new dev.devpanda.factorynetwork.network.packet.NetworkStatePacket(
                        List.of(new dev.devpanda.factorynetwork.network.packet.NamedPlace(
                                connector, new net.minecraft.core.BlockPos(1, 2, 3))),
                        List.of(), List.of(), List.of(), List.of(), List.of()));
        try {
            body.run();
        } finally {
            dev.devpanda.factorynetwork.client.ClientNetworkState.accept(
                    new dev.devpanda.factorynetwork.network.packet.NetworkStatePacket(
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        }
    }

    @Test
    @DisplayName("After the dot stands what a device has")
    void afterTheDotTheDeviceMembersAreOffered() {
        withNetwork("crusher_1", () -> assertEquals(
                List.of("online", "name", "redstone", "count", "insert", "items", "slots",
                        "energy", "click"),
                at("fn test() {", "    if crusher_1.")));
    }

    /**
     * <b>Here the editor in the game differs from the extension.</b> It knows
     * the running network and only suggests something behind a real connector.
     * VS Code does not know it and offers behind every name — there a too
     * generous suggestion is better than none at all.
     */
    @Test
    @DisplayName("Behind an unknown name there is nothing")
    void afterAnUnknownNameNothingIsOffered() {
        withNetwork("crusher_1", () -> assertTrue(
                at("fn test() {", "    if gibt_es_nicht.").isEmpty(),
                () -> "was kein Connector ist, hat auch keine Mitglieder, war: "
                        + at("fn test() {", "    if gibt_es_nicht.")));
    }

    // ---- Names from the whole project --------------------------------------

    /**
     * A project of several files, for the duration of a test.
     *
     * <p>{@code ClientProjectState} is static — there is one client and one
     * draft. Afterwards it is emptied, otherwise the next test sees files
     * that do not exist.
     */
    private static void withProject(java.util.Map<String, String> files, Runnable body) {
        dev.devpanda.factorynetwork.client.ClientProjectState.setDraft(
                new dev.devpanda.factorynetwork.lang.Project(files));
        try {
            body.run();
        } finally {
            dev.devpanda.factorynetwork.client.ClientProjectState.setDraft(
                    dev.devpanda.factorynetwork.lang.Project.of(""));
        }
    }

    @Test
    @DisplayName("A function from another file is suggested")
    void aFunctionFromAnotherFileIsOffered() {
        withProject(java.util.Map.of(
                "werte.mf", "fn heizen() {\n}",
                "main.mf", "display halle {\n    button \"An\" \n}"), () ->
                assertTrue(at("display halle {", "    button \"An\" ").contains("heizen"),
                        "alle Dateien teilen einen Namensraum"));
    }

    @Test
    @DisplayName("An event from another file too")
    void anEventFromAnotherFileToo() {
        withProject(java.util.Map.of(
                "ereignisse.mf", "event nachschub(menge)",
                "main.mf", "fn test() {\n    emit \n}"), () ->
                assertTrue(at("fn test() {", "    emit ").contains("nachschub"),
                        () -> at("fn test() {", "    emit ").toString()));
    }

    @Test
    @DisplayName("Without a project the open file stays the source")
    void withoutAProjectTheOpenFileIsTheSource() {
        assertTrue(at("fn heizen() {", "}", "display halle {", "    button \"An\" ")
                        .contains("heizen"),
                "was im Text steht, gilt immer — auch ohne Entwurf auf dem Client");
    }

    @Test
    @DisplayName("After items() stands a list, not a device")
    void afterItemsCallTheListMembersAreOffered() {
        withNetwork("crusher_1", () -> assertEquals(
                List.of("count", "first", "sum", "where", "sort", "plus", "without",
                        "rest"),
                at("fn test() {", "    log(crusher_1.items().")));
    }

    @Test
    @DisplayName("At an entry stands the chemical too")
    void apostAlsoHasAchemical() {
        // The editor knows no types and does not know whether an entry means
        // items, fluids or chemicals. It offers all three and says in the form
        // what it applies to.
        withNetwork("crusher_1", () -> assertTrue(
                at("fn test() {", "    log(it.").contains("chemical"),
                "it.chemical gehört zu it.item und it.fluid"));
    }

    @Test
    @DisplayName("After network. stands what can be read off the network")
    void afterNetworkDotTheNetworkMembersAreOffered() {
        // network is a dot access like on a device and yet not one: without
        // the distinction a network would have redstone().
        withNetwork("crusher_1", () -> assertEquals(
                List.of("power", "capacity"),
                at("fn test() {", "    if network.")));
    }

    @Test
    @DisplayName("At a device there is no power")
    void adeviceHasNopower() {
        withNetwork("crusher_1", () -> assertFalse(
                at("fn test() {", "    if crusher_1.").contains("power"),
                "der Stand einer Maschine heißt energy()"));
    }

    @Test
    @DisplayName("A number with a dot is not an access")
    void aNumberWithADotIsNotAMemberAccess() {
        withNetwork("crusher_1", () -> assertFalse(
                at("fn test() {", "    let x = 3.").contains("online"),
                "3.5 ist eine Zahl und kein Gerät"));
    }

}
