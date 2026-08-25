package dev.devpanda.factorynetwork.client.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was der Editor an welcher Stelle vorschlägt.
 *
 * <p>Geprüft werden nur die Stellen, die ohne Registry auskommen —
 * Gegenstände und Tags brauchen ein geladenes Spiel und stehen deshalb in
 * den GameTests.
 *
 * <p>Die Frage dahinter ist immer dieselbe: <b>Passt der Vorschlag zu dem,
 * was die Grammatik an dieser Stelle erlaubt?</b> Ein Vorschlag, der überall
 * dasselbe zeigt, hilft nirgends — und einer, der Verbotenes anbietet, ist
 * schlechter als keiner.
 */
class CompletionsTest {

    private static List<String> texts(List<Completions.Entry> entries) {
        return entries.stream().map(Completions.Entry::text).toList();
    }

    /** Vorschläge am Ende der letzten Zeile. */
    private static List<String> at(String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return texts(Completions.at(code, last, code.get(last).length()));
    }

    @Test
    @DisplayName("In einer Anzeige stehen ihre Angaben, keine Anweisungen")
    void insideDisplayOnlyDisplayEntries() {
        List<String> shown = at("display halle {", "    ");

        assertTrue(shown.contains("title"), () -> "title fehlt: " + shown);
        assertTrue(shown.contains("row"), () -> "row fehlt: " + shown);
        assertTrue(shown.contains("progress"), () -> "progress fehlt: " + shown);
        assertFalse(shown.contains("if"),
                () -> "eine Anzeige kennt keine Anweisungen: " + shown);
        assertFalse(shown.contains("let"), () -> shown.toString());
        assertFalse(shown.contains("from"),
                () -> "from gehört in einen Worker, nicht in eine Anzeige: " + shown);
    }

    @Test
    @DisplayName("Auf oberster Ebene steht filter neben den anderen Deklarationen")
    void filterIsADeclaration() {
        List<String> shown = at("");

        assertTrue(shown.contains("filter"), () -> "filter fehlt: " + shown);
        assertTrue(shown.contains("group"), () -> "group fehlt: " + shown);
    }

    @Test
    @DisplayName("In einer Vorlage steht except und keine Deklaration")
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
    @DisplayName("An einer Auswahlstelle stehen die Vorlagen des Projekts")
    void templatesAreOfferedWhereASelectionFits() {
        List<String> shown = at("filter erze {", "    tag:c/ores", "}", "",
                "worker holt {", "    from grube", "    to storage", "    filter ");

        assertTrue(shown.contains("erze"), () -> "die Vorlage fehlt: " + shown);
    }

    @Test
    @DisplayName("Nach it. stehen die Angaben eines Postens")
    void afterItTheEntryMembers() {
        List<String> shown = at("fn test() {", "    log(storage.items().where(it.");

        assertTrue(shown.contains("amount"), () -> "amount fehlt: " + shown);
        assertTrue(shown.contains("item"), () -> "item fehlt: " + shown);
        assertFalse(shown.contains("online"),
                () -> "ein Posten ist kein Gerät: " + shown);
        assertFalse(shown.contains("insert"), () -> shown.toString());
    }

    @Test
    @DisplayName("Vorgeschlagen wird nur, was die Laufzeit auch kann")
    void onlyWhatTheRuntimeCanDo() {
        List<String> shown = at("worker haul {", "    to ");

        assertTrue(shown.contains("storage"), () -> "storage fehlt: " + shown);
        // crafting, world, network, workers und multiblocks parst die Sprache,
        // aber der Interpreter kennt keines davon: Wer sie hinschreibt,
        // bekommt „Als Ziel taugt nur ein Name" — eine Meldung, die dem
        // Vorschlag widerspricht, der sie ausgelöst hat.
        assertFalse(shown.contains("crafting"),
                () -> "crafting wird nicht ausgewertet: " + shown);
        assertFalse(shown.contains("network"), () -> shown.toString());
        assertFalse(shown.contains("multiblocks"), () -> shown.toString());
    }

    @Test
    @DisplayName("In der on-Kopfzeile stehen die Ereignisse und keine Deklarationen")
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
    @DisplayName("Nach on stehen die Ereignisse")
    void afterOnTheEventsAreOffered() {
        List<String> shown = at("on ");

        // Die vier des Netzes stehen in keiner Datei. Wer sie nicht auswendig
        // weiß, tippt irgendetwas — und ein on mit vertipptem Namen wird
        // übernommen und läuft nie.
        assertTrue(shown.contains("device_online"), () -> "device_online fehlt: " + shown);
        assertTrue(shown.contains("device_offline"), () -> "device_offline fehlt: " + shown);
        assertTrue(shown.contains("device_changed"), () -> "device_changed fehlt: " + shown);
        assertTrue(shown.contains("redstone_changed"), () -> "redstone_changed fehlt: " + shown);
        assertFalse(shown.contains("worker"),
                () -> "hier steht ein Ereignisname, keine Deklaration: " + shown);
    }

    @Test
    @DisplayName("Nach on gehören auch die selbst erklärten Ereignisse dazu")
    void afterOnOwnEventsCountToo() {
        List<String> shown = at("event Fertig(nummer: Int)", "", "on ");

        assertTrue(shown.contains("Fertig"), () -> "das eigene Ereignis fehlt: " + shown);
    }

    @Test
    @DisplayName("In einem Worker stehen seine Angaben")
    void insideWorkerOnlyWorkerEntries() {
        List<String> shown = at("worker haul {", "    ");

        assertTrue(shown.contains("from"), () -> "from fehlt: " + shown);
        assertFalse(shown.contains("title"),
                () -> "title gehört in eine Anzeige: " + shown);
    }

    @Test
    @DisplayName("In einer Gruppe stehen members und strategy")
    void insideGroupOnlyGroupEntries() {
        List<String> shown = at("group oefen {", "    ");

        assertEquals(List.of("members", "strategy"), shown);
    }

    @Test
    @DisplayName("Hinter einem fertigen Deklarationsnamen kommt die Klammer")
    void nothingAfterADeclarationName() {
        assertTrue(at("display halle ").isEmpty(),
                "hinter dem Namen gibt es nichts vorzuschlagen");
        assertTrue(at("worker haul ").isEmpty(), "und im Worker genauso");
    }

    @Test
    @DisplayName("Was schon vollständig dasteht, ist kein Vorschlag")
    void noSuggestionForAnExactMatch() {
        // „worker" ist selbst eine Deklaration; wer es zu Ende getippt hat,
        // braucht es nicht angeboten zu bekommen.
        assertFalse(at("worker").contains("worker"),
                "ein Eintrag, der nichts ändert, verdeckt nur die Zeile darunter");
    }

    @Test
    @DisplayName("Auf oberster Ebene stehen die Deklarationen")
    void topLevelOffersDeclarations() {
        List<String> shown = at("disp");

        assertTrue(shown.contains("display"), () -> shown.toString());
    }

    /** Die Formangaben der Vorschläge. */
    private static List<String> details(String... lines) {
        List<String> code = List.of(lines);
        int last = code.size() - 1;
        return Completions.at(code, last, code.get(last).length()).stream()
                .map(Completions.Entry::detail).toList();
    }

    @Test
    @DisplayName("Ein Vorschlag bringt seine Form mit")
    void everyBlockEntryCarriesItsShape() {
        List<String> shown = at("display halle {", "    ");
        List<String> shapes = details("display halle {", "    ");

        int row = shown.indexOf("row");
        assertEquals("string expr", shapes.get(row),
                "ohne die Form ist ein Vorschlag ein Wort, das man nachschlagen muss");
        assertEquals("string", shapes.get(shown.indexOf("title")));
    }

    @Test
    @DisplayName("Hinter row kommt kein row mehr")
    void insideAnEntryTheKeywordsAreGone() {
        // Das war die eigentliche Beschwerde: Nach „row" bot der Editor
        // wieder „title, row, text …" an — also alles ausser dem, was dort
        // hingehört.
        assertFalse(at("display halle {", "    row ").contains("row"),
                "an dieser Stelle steht ein Text, kein Schlüsselwort");
        assertFalse(at("display halle {", "    row \"Bestand\" ").contains("title"),
                "und hier ein Ausdruck");
    }

    @Test
    @DisplayName("An einer Textstelle gibt es nichts vorzuschlagen")
    void nothingToOfferForAString() {
        assertTrue(at("display halle {", "    title ").isEmpty(),
                "einen freien Text kann niemand vorschlagen — die Formzeile sagt es");
    }

    @Test
    @DisplayName("An einer Ausdrucksstelle stehen die Bestände")
    void expressionSlotsOfferTheBuiltins() {
        List<String> shown = at("display halle {", "    text ");

        assertTrue(shown.contains("storage"), () -> shown.toString());
        // network stand hier früher mit: Die Sprache parst es, ausgewertet
        // wird es nirgends — auch nicht in einer Anzeige. Ein Vorschlag, der
        // in eine Fehlermeldung führt, ist schlechter als keiner.
        assertFalse(shown.contains("network"),
                () -> "network wird nicht ausgewertet: " + shown);
        assertFalse(shown.contains("if"), () -> "kein Ausdruck ist eine Anweisung: " + shown);
    }

    @Test
    @DisplayName("Hinter strategy stehen die Verteilungen")
    void strategySlotOffersStrategies() {
        assertTrue(at("worker haul {", "    strategy ").contains("round_robin"));
    }

    @Test
    @DisplayName("Hinter button stehen die Funktionen des Projekts")
    void buttonSlotOffersFunctions() {
        List<String> shown = at("fn leeren() {", "}", "display halle {",
                "    button \"Leeren\" ");

        assertTrue(shown.contains("leeren"), () -> shown.toString());
    }

    @Test
    @DisplayName("Hinter rate steht das feste Wort per")
    void literalSlotOffersItsWord() {
        assertEquals(List.of("per"), at("worker haul {", "    rate 64 "));
    }

    @Test
    @DisplayName("Eine volle Angabe schlägt nichts mehr vor")
    void aCompleteEntryOffersNothing() {
        assertTrue(at("worker haul {", "    rate 64 per 5s ").isEmpty());
    }

    @Test
    @DisplayName("Nach move 64 stehen from und to zur Wahl")
    void anOptionalWordOffersBothPaths() {
        List<String> shown = at("fn test() {", "    move 64 ");

        assertTrue(shown.contains("from"), () -> shown.toString());
        assertTrue(shown.contains("to"),
                () -> "die Quelle darf auch fehlen: " + shown);
    }

    @Test
    @DisplayName("In einer Funktion stehen Anweisungen und Ausdrücke nebeneinander")
    void codeBlocksOfferBoth() {
        assertTrue(at("fn test() {", "    ").contains("move"),
                "die Anweisungen stehen zuerst");
        // Mit einem angefangenen Wort kommt der Ausdruck durch. Ohne eines
        // ist die Liste schon von den Anweisungen voll — das ist richtig so,
        // denn eine Zeile fängt weit öfter mit einer Anweisung an.
        assertTrue(at("fn test() {", "    sto").contains("storage"),
                "ein Ausdruck ist auch eine Anweisung");
    }

    @Test
    @DisplayName("Eine Anweisung bringt ihre Form mit")
    void statementsCarryTheirShape() {
        List<String> shown = at("fn test() {", "    ");
        List<String> shapes = details("fn test() {", "    ");

        assertEquals("menge [from quelle] to ziel", shapes.get(shown.indexOf("move")));
        assertEquals("duration", shapes.get(shown.indexOf("sleep")));
    }

    @Test
    @DisplayName("Hinter emit stehen die Ereignisse des Projekts")
    void emitOffersProjectEvents() {
        List<String> shown = at("event ofen_fertig(x: Int)", "fn test() {", "    emit ");

        assertTrue(shown.contains("ofen_fertig"), () -> shown.toString());
    }

    @Test
    @DisplayName("Für einen neuen Namen gibt es keinen Vorschlag")
    void newNamesAreNotSuggested() {
        assertTrue(at("fn test() {", "    let ").isEmpty(),
                "wie hiesse er auch — er entsteht ja gerade");
    }

    // ---- Nach dem Punkt ----------------------------------------------------

    /**
     * Ein Netz mit genau diesem Connector, für die Dauer eines Tests.
     *
     * <p>{@link dev.devpanda.factorynetwork.client.ClientNetworkState} ist
     * statisch — es gibt einen Client und ein Netz. Für den Test wird es
     * gefüllt und danach geleert, sonst sieht der nächste Test ein Netz, das
     * es nicht gibt.
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
    @DisplayName("Nach dem Punkt steht, was ein Gerät hat")
    void afterTheDotTheDeviceMembersAreOffered() {
        withNetwork("crusher_1", () -> assertEquals(
                List.of("online", "name", "redstone", "count", "insert", "items", "slots", "energy"),
                at("fn test() {", "    if crusher_1.")));
    }

    /**
     * <b>Hier weicht der Editor im Spiel von der Erweiterung ab.</b> Er kennt
     * das laufende Netz und schlägt nur hinter einem wirklichen Connector
     * etwas vor. VS Code kennt es nicht und bietet hinter jedem Namen an —
     * dort ist ein zu großzügiger Vorschlag besser als gar keiner.
     */
    @Test
    @DisplayName("Hinter einem unbekannten Namen gibt es nichts")
    void afterAnUnknownNameNothingIsOffered() {
        withNetwork("crusher_1", () -> assertTrue(
                at("fn test() {", "    if gibt_es_nicht.").isEmpty(),
                () -> "was kein Connector ist, hat auch keine Mitglieder, war: "
                        + at("fn test() {", "    if gibt_es_nicht.")));
    }

    // ---- Namen aus dem ganzen Projekt --------------------------------------

    /**
     * Ein Projekt aus mehreren Dateien, für die Dauer eines Tests.
     *
     * <p>{@code ClientProjectState} ist statisch — es gibt einen Client und
     * einen Entwurf. Danach wird geleert, sonst sieht der nächste Test
     * Dateien, die es nicht gibt.
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
    @DisplayName("Eine Funktion aus einer anderen Datei wird vorgeschlagen")
    void aFunctionFromAnotherFileIsOffered() {
        withProject(java.util.Map.of(
                "werte.mf", "fn heizen() {\n}",
                "main.mf", "display halle {\n    button \"An\" \n}"), () ->
                assertTrue(at("display halle {", "    button \"An\" ").contains("heizen"),
                        "alle Dateien teilen einen Namensraum"));
    }

    @Test
    @DisplayName("Ein Ereignis aus einer anderen Datei auch")
    void anEventFromAnotherFileToo() {
        withProject(java.util.Map.of(
                "ereignisse.mf", "event nachschub(menge)",
                "main.mf", "fn test() {\n    emit \n}"), () ->
                assertTrue(at("fn test() {", "    emit ").contains("nachschub"),
                        () -> at("fn test() {", "    emit ").toString()));
    }

    @Test
    @DisplayName("Ohne Projekt bleibt die offene Datei die Quelle")
    void withoutAProjectTheOpenFileIsTheSource() {
        assertTrue(at("fn heizen() {", "}", "display halle {", "    button \"An\" ")
                        .contains("heizen"),
                "was im Text steht, gilt immer — auch ohne Entwurf auf dem Client");
    }

    @Test
    @DisplayName("Hinter items() steht eine Liste, kein Gerät")
    void afterItemsCallTheListMembersAreOffered() {
        withNetwork("crusher_1", () -> assertEquals(
                List.of("count", "first", "sum", "where", "sort"),
                at("fn test() {", "    log(crusher_1.items().")));
    }

    @Test
    @DisplayName("Eine Zahl mit Punkt ist kein Zugriff")
    void aNumberWithADotIsNotAMemberAccess() {
        withNetwork("crusher_1", () -> assertFalse(
                at("fn test() {", "    let x = 3.").contains("online"),
                "3.5 ist eine Zahl und kein Gerät"));
    }

}
