package dev.devpanda.factorynetwork.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.devpanda.factorynetwork.lang.Project;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Validates a browser draft before it reaches either client state or the wire. */
public final class EditorProtocol {
    public static final int MAX_FILE = 64 * 1024;
    public static final int MAX_PROJECT_BYTES = 256 * 1024;
    public static final int MAX_FILES = 128;

    private EditorProtocol() {}

    public static Project readProject(JsonObject files, Project current, Map<String, String> locks) {
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Ein Projekt braucht zwischen 1 und 128 Dateien.");
        }
        Map<String, String> next = new HashMap<>();
        int bytes = 0;
        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
            String name = entry.getKey();
            if (!Project.isValidName(name)) {
                throw new IllegalArgumentException("Ungültiger Dateiname: " + name);
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Dateiinhalt muss Text sein: " + name);
            }
            String source = value.getAsString();
            if (source.length() > MAX_FILE) {
                throw new IllegalArgumentException("Die Datei ist zu lang: " + name);
            }
            bytes += name.getBytes(StandardCharsets.UTF_8).length
                    + source.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_PROJECT_BYTES) {
                throw new IllegalArgumentException("Der Entwurf überschreitet 256 KiB.");
            }
            next.put(name, source);
        }
        for (Map.Entry<String, String> lock : locks.entrySet()) {
            if (!java.util.Objects.equals(current.files().get(lock.getKey()), next.get(lock.getKey()))) {
                throw new IllegalArgumentException(lock.getKey() + " wird von " + lock.getValue() + " bearbeitet.");
            }
        }
        return new Project(next);
    }
}
