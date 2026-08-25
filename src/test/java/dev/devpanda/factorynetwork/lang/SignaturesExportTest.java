package dev.devpanda.factorynetwork.lang;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hält die Tabelle der Erweiterung mit der im Code gleich.
 *
 * <p><b>Warum sie doppelt vorliegt:</b> Die Erweiterung für VS Code wird
 * kopiert, nicht gebaut — kein {@code npm install}, kein Übersetzer. Sie kann
 * also nicht in den Java-Code sehen und braucht die Formen als Datei neben
 * sich.
 *
 * <p><b>Und warum sie trotzdem nicht auseinanderläuft:</b> Diese Prüfung
 * erzeugt die Datei aus {@link Signatures} und vergleicht sie mit der
 * eingecheckten. Wer eine Angabe hinzufügt und die Datei vergisst, bekommt
 * einen roten Test — und die neue Fassung liegt dann schon da und muss nur
 * eingecheckt werden.
 */
class SignaturesExportTest {

    private static final Path TARGET = Path.of("editor", "vscode", "data", "signatures.json");

    @Test
    @DisplayName("Die Tabelle der Erweiterung ist die aus dem Code")
    void theExportMatchesTheTable() throws IOException {
        String expected = build();
        // Zeilenenden angeglichen: Mit core.autocrlf=true checkt Git die
        // Datei unter Windows als CRLF aus, geschrieben wird sie mit LF.
        // Ohne diese Zeile schlägt der Test nach jedem Zweigwechsel fehl,
        // obwohl inhaltlich nichts abweicht — und wer das dreimal erlebt hat,
        // glaubt ihm beim vierten Mal nicht mehr.
        String actual = Files.exists(TARGET)
                ? Files.readString(TARGET, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";

        if (!expected.equals(actual)) {
            // Neu geschrieben statt nur gemeldet: Wer die Tabelle ändert,
            // soll die Datei nicht von Hand nachziehen müssen. Der Test
            // scheitert trotzdem — sie will eingecheckt werden.
            Files.createDirectories(TARGET.getParent());
            Files.writeString(TARGET, expected, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual,
                "signatures.json war nicht auf dem Stand und wurde neu geschrieben — "
                        + "bitte einchecken.");
    }

    /** Die Tabelle als JSON, so wie die Erweiterung sie liest. */
    private static String build() {
        JsonObject root = new JsonObject();

        JsonObject blocks = new JsonObject();
        for (String block : new String[] {"display", "worker", "group", "filter", "fn"}) {
            JsonArray entries = new JsonArray();
            for (Signatures.Signature signature : Signatures.forBlock(block)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("keyword", signature.keyword());
                entry.addProperty("shape", signature.shape());
                entry.addProperty("help", signature.help());
                JsonArray slots = new JsonArray();
                for (Signatures.Slot slot : signature.slots()) {
                    JsonObject one = new JsonObject();
                    one.addProperty("kind", slot.kind().name());
                    one.addProperty("label", slot.label());
                    one.addProperty("optional", slot.optional());
                    slots.add(one);
                }
                entry.add("slots", slots);
                entries.add(entry);
            }
            blocks.add(block, entries);
        }
        root.add("blocks", blocks);

        JsonArray strategies = new JsonArray();
        Signatures.STRATEGIES.forEach(strategies::add);
        root.add("strategies", strategies);

        // Was an einem Gerät steht, für die Vervollständigung nach dem Punkt.
        // Ohne diesen Block wüsste die Erweiterung nichts davon — und die
        // Behauptung, sie folge denselben Regeln, wäre nur halb wahr.
        JsonArray members = new JsonArray();
        for (Signatures.Member member : Signatures.MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            members.add(entry);
        }
        root.add("members", members);

        // Und dasselbe für eine Liste. Der Editor im Spiel kennt sie seit
        // langem; ohne diesen Block stand die Erweiterung nach
        // „storage.items()." ohne einen einzigen Vorschlag da.
        JsonArray listMembers = new JsonArray();
        for (Signatures.Member member : Signatures.LIST_MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            listMembers.add(entry);
        }
        root.add("listMembers", listMembers);

        // Die Ereignisse, die das Netz selbst auslöst. Sie stehen in keiner
        // Datei des Spielers — ohne diesen Block kann die Erweiterung sie
        // nicht vorschlagen, und ein vertippter Name fällt erst auf, wenn
        // der Block nie läuft.
        // Die Funktionen ohne Empfänger. Ohne sie schlug die Erweiterung
        // nicht einmal log() vor, das es seit dem ersten Tag gibt.
        JsonArray freeFunctions = new JsonArray();
        for (Signatures.Member function : Signatures.FREE_FUNCTIONS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", function.name());
            entry.addProperty("shape", function.shape());
            entry.addProperty("help", function.help());
            freeFunctions.add(entry);
        }
        root.add("freeFunctions", freeFunctions);

        JsonArray builtinEvents = new JsonArray();
        new java.util.TreeSet<>(BuiltinEvents.ARITY.keySet()).forEach(builtinEvents::add);
        root.add("builtinEvents", builtinEvents);

        JsonArray declarations = new JsonArray();
        for (String word : new String[] {"worker", "group", "filter", "multiblock", "event",
                                         "display", "fn", "on", "global"}) {
            declarations.add(word);
        }
        root.add("declarations", declarations);

        // Die Formen der obersten Ebene — heute nur global, aber die
        // Erweiterung kann sie ohne diesen Block nicht anbieten.
        JsonArray topLevel = new JsonArray();
        for (Signatures.Signature signature : Signatures.TOP_LEVEL) {
            JsonObject entry = new JsonObject();
            entry.addProperty("keyword", signature.keyword());
            entry.addProperty("shape", signature.shape());
            entry.addProperty("help", signature.help());
            JsonArray slots = new JsonArray();
            for (Signatures.Slot slot : signature.slots()) {
                JsonObject one = new JsonObject();
                one.addProperty("kind", slot.kind().name());
                one.addProperty("label", slot.label());
                one.addProperty("optional", slot.optional());
                slots.add(one);
            }
            entry.add("slots", slots);
            topLevel.add(entry);
        }
        root.add("topLevel", topLevel);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }
}
