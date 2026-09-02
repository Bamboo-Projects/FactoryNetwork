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
 * Keeps the extension's table identical to the one in the code.
 *
 * <p><b>Why it exists twice:</b> The extension for VS Code is copied, not
 * built — no {@code npm install}, no compiler. So it cannot look into the
 * Java code and needs the shapes as a file next to itself.
 *
 * <p><b>And why it still does not drift apart:</b> This check generates the
 * file from {@link Signatures} and compares it with the checked-in one.
 * Whoever adds an entry and forgets the file gets a red test — and the new
 * version is already there and only needs to be checked in.
 */
class SignaturesExportTest {

    private static final Path TARGET = Path.of("editor", "vscode", "data", "signatures.json");

    @Test
    @DisplayName("The extension's table is the one from the code")
    void theExportMatchesTheTable() throws IOException {
        String expected = build();
        // Line endings aligned: with core.autocrlf=true Git checks the file
        // out as CRLF on Windows, while it is written with LF. Without this
        // line the test fails after every branch switch even though nothing
        // differs in content — and whoever has seen that three times no
        // longer believes it the fourth time.
        String actual = Files.exists(TARGET)
                ? Files.readString(TARGET, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";

        if (!expected.equals(actual)) {
            // Rewritten instead of merely reported: whoever changes the table
            // should not have to update the file by hand. The test fails
            // anyway — the file wants to be checked in.
            Files.createDirectories(TARGET.getParent());
            Files.writeString(TARGET, expected, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual,
                "signatures.json war nicht auf dem Stand und wurde neu geschrieben — "
                        + "bitte einchecken.");
    }

    /** The table as JSON, the way the extension reads it. */
    private static String build() {
        JsonObject root = new JsonObject();

        JsonObject blocks = new JsonObject();
        for (String block : Signatures.BLOCKS_WITH_ENTRIES) {
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

        // What a device has, for the completion after the dot. Without this
        // block the extension would know nothing of it — and the claim that
        // it follows the same rules would be only half true.
        JsonArray members = new JsonArray();
        for (Signatures.Member member : Signatures.MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            members.add(entry);
        }
        root.add("members", members);

        // And the same for a list. The in-game editor has known them for a
        // long time; without this block the extension stood there after
        // „storage.items()." without a single suggestion.
        JsonArray listMembers = new JsonArray();
        for (Signatures.Member member : Signatures.LIST_MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            listMembers.add(entry);
        }
        root.add("listMembers", listMembers);

        // And the network. Without this block the extension offered the
        // device members after „network." — that is, redstone() on a network.
        JsonArray networkMembers = new JsonArray();
        for (Signatures.Member member : Signatures.NETWORK_MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            networkMembers.add(entry);
        }
        root.add("networkMembers", networkMembers);

        JsonArray entryMembers = new JsonArray();
        for (Signatures.Member member : Signatures.ENTRY_MEMBERS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", member.name());
            entry.addProperty("shape", member.shape());
            entry.addProperty("help", member.help());
            entryMembers.add(entry);
        }
        root.add("entryMembers", entryMembers);

        // The events the network itself fires. They are in none of the
        // player's files — without this block the extension cannot suggest
        // them, and a mistyped name is noticed only when the block never
        // runs.
        // The functions without a receiver. Without them the extension did
        // not even suggest log(), which has existed since day one.
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

        // The same list as in the in-game editor. It once stood here on its
        // own, and a new word therefore landed in only one of the two
        // editors.
        JsonArray declarations = new JsonArray();
        Signatures.DECLARATIONS.forEach(declarations::add);
        root.add("declarations", declarations);

        // The top-level shapes — today only global, but the extension cannot
        // offer them without this block.
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
