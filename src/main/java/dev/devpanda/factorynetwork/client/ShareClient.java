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
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        body.add("files", GSON.toJsonTree(files));
        return body.toString();
    }

    public static CompletableFuture<String> upload(String title, Map<String, String> files) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/programs"))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(uploadBody(title, files)))
                .build();
        return send(request).thenApply(json -> json.get("code").getAsString());
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
