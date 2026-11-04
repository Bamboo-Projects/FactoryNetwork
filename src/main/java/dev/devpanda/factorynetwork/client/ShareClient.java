package dev.devpanda.factorynetwork.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the program share: upload, fetch by code, list, report.
 *
 * <p>Sends nothing but the program and its title — no player name, no
 * server address. Every call runs off the game thread; the caller gets a
 * future and hands the answer back to the game itself.
 */
public final class ShareClient {

    /** One entry of the public list. */
    public record Listed(String code, String title, int files, int lines, int downloads) {
    }

    /** What an upload hands back: the code, the key for later updates, whether it is bound. */
    public record Uploaded(String code, String key, boolean bound) {
    }

    /** Proof of a Minecraft account: the session was joined with this id just now. */
    public record Proof(String username, String serverId) {
    }

    /**
     * Joins Mojang's session with a random id, the way the game does when it
     * logs into a server. The share then asks Mojang whether this player
     * did; the access token never leaves for anywhere but Mojang. Blocks.
     */
    public static Proof prove() throws Exception {
        var client = net.minecraft.client.Minecraft.getInstance();
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder serverId = new StringBuilder();
        for (byte value : bytes) {
            serverId.append(String.format("%02x", value));
        }
        client.services().sessionService().joinServer(client.getUser().getProfileId(),
                client.getUser().getAccessToken(), serverId.toString());
        return new Proof(client.getUser().getName(), serverId.toString());
    }

    /** A fetched program. */
    public record Shared(String code, String title, Map<String, String> files) {
    }

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private ShareClient() {
    }

    /** Where the share lives: the system property for tests, else the client config. */
    public static String base() {
        String override = System.getProperty("fn.shareUrl");
        String base = override != null && !override.isBlank() ? override : FnClientConfig.shareUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** The JSON an upload sends. */
    public static String uploadBody(String title, Map<String, String> files) {
        return body(title, files, null, null, false);
    }

    /** The JSON of an upload or update: never the player's name unless proof was asked for. */
    public static String body(String title, Map<String, String> files, String key, Proof proof,
                              boolean bind) {
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        body.add("files", GSON.toJsonTree(files));
        if (key != null && !key.isEmpty()) {
            body.addProperty("key", key);
        }
        if (proof != null) {
            JsonObject auth = new JsonObject();
            auth.addProperty("username", proof.username());
            auth.addProperty("serverId", proof.serverId());
            body.add("auth", auth);
        }
        if (bind) {
            body.addProperty("bind", true);
        }
        return body.toString();
    }

    public static CompletableFuture<Uploaded> upload(String title, Map<String, String> files,
                                                     Proof proof, boolean bind) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/programs"))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(title, files, null, proof, bind)))
                .build();
        return send(request).thenApply(json -> new Uploaded(json.get("code").getAsString(),
                json.get("key").getAsString(), json.get("bound").getAsBoolean()));
    }

    /** A new version under an existing code, with the disk's key or the bound account. */
    public static CompletableFuture<Uploaded> update(String code, String title, Map<String, String> files,
                                                     String key, Proof proof, boolean bind) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/programs/"
                        + URLEncoder.encode(code, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body(title, files, key, proof, bind)))
                .build();
        return send(request).thenApply(json -> new Uploaded(json.get("code").getAsString(),
                key == null ? "" : key, json.get("bound").getAsBoolean()));
    }

    public static CompletableFuture<Shared> fetch(String code) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/programs/"
                        + URLEncoder.encode(code.trim().toUpperCase(java.util.Locale.ROOT), StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return send(request).thenApply(ShareClient::parseShared);
    }

    public static CompletableFuture<List<Listed>> list(boolean popular, String query, int page) {
        String url = base() + "/v1/programs?sort=" + (popular ? "popular" : "new")
                + "&page=" + page
                + (query == null || query.isBlank() ? ""
                        : "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return send(request).thenApply(ShareClient::parseList);
    }

    public static CompletableFuture<Void> report(String code) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/programs/"
                        + URLEncoder.encode(code.trim().toUpperCase(java.util.Locale.ROOT), StandardCharsets.UTF_8)
                        + "/report"))
                .timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.noBody()).build();
        return send(request).thenApply(json -> null);
    }

    public static Shared parseShared(JsonObject json) {
        Map<String, String> files = new LinkedHashMap<>();
        json.getAsJsonObject("files").entrySet()
                .forEach(entry -> files.put(entry.getKey(), entry.getValue().getAsString()));
        return new Shared(json.get("code").getAsString(), json.get("title").getAsString(), files);
    }

    public static List<Listed> parseList(JsonObject json) {
        List<Listed> found = new ArrayList<>();
        JsonArray programs = json.getAsJsonArray("programs");
        for (var element : programs) {
            JsonObject entry = element.getAsJsonObject();
            found.add(new Listed(entry.get("code").getAsString(), entry.get("title").getAsString(),
                    entry.get("file_count").getAsInt(), entry.get("lines").getAsInt(),
                    entry.get("downloads").getAsInt()));
        }
        return found;
    }

    private static CompletableFuture<JsonObject> send(HttpRequest request) {
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException(json.has("error")
                                ? json.get("error").getAsString() : "HTTP " + response.statusCode());
                    }
                    return json;
                });
    }
}
