package dev.devpanda.factorynetwork.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The share's JSON, without the network: tests never go online. */
class ShareClientTest {

    @Test
    @DisplayName("An upload carries title and files and nothing about the player")
    void uploadCarriesOnlyTitleAndFiles() {
        var body = JsonParser.parseString(ShareClient.uploadBody("Erz", Map.of("main.mf", "fn a() {}")))
                .getAsJsonObject();
        assertEquals("Erz", body.get("title").getAsString());
        assertEquals("fn a() {}", body.getAsJsonObject("files").get("main.mf").getAsString());
        assertEquals(2, body.size(), "nur title und files");
    }

    @Test
    @DisplayName("An update carries the key; a bound upload the proof, and only then a name")
    void updateCarriesKeyAndProofOnlyWhenAsked() {
        var update = JsonParser.parseString(ShareClient.body("Erz", Map.of("main.mf", "x"), "abc", null, false))
                .getAsJsonObject();
        assertEquals("abc", update.get("key").getAsString());
        assertFalse(update.has("auth"), "ohne Bindung kein Spielername");
        var bound = JsonParser.parseString(ShareClient.body("Erz", Map.of("main.mf", "x"), null,
                new ShareClient.Proof("Dev", "00ff"), true)).getAsJsonObject();
        assertEquals("Dev", bound.getAsJsonObject("auth").get("username").getAsString());
        assertEquals(true, bound.get("bind").getAsBoolean());
        assertFalse(bound.has("key"));
    }

    @Test
    @DisplayName("A list answer becomes entries")
    void listBecomesEntries() {
        var json = JsonParser.parseString("""
                {"page":0,"programs":[{"code":"FN-ABC234","title":"Erz","file_count":2,
                "lines":40,"downloads":7,"created":1}]}""").getAsJsonObject();
        var list = ShareClient.parseList(json);
        assertEquals(1, list.size());
        assertEquals("FN-ABC234", list.get(0).code());
        assertEquals(7, list.get(0).downloads());
    }

    @Test
    @DisplayName("A fetched program keeps its files")
    void fetchedProgramKeepsFiles() {
        var json = JsonParser.parseString("""
                {"code":"FN-ABC234","title":"Erz","files":{"main.mf":"x","a/b.mf":"y"}}""").getAsJsonObject();
        var shared = ShareClient.parseShared(json);
        assertEquals(Map.of("main.mf", "x", "a/b.mf", "y"), shared.files());
        assertFalse(shared.title().isEmpty());
    }
}
