package dev.devpanda.factorynetwork.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt, welche Seiten laufen und wo ihre Werkzeuge stehen.
 *
 * <p><b>Chromiums eigene Werkzeuge, nicht nachgebaute.</b> Läuft die
 * Fernwartung, führt Chromium unter {@code /json/list} jede offene Seite mit
 * Titel, Adresse und einer eigenen Werkzeugadresse. Das hier ist nur die
 * Übersetzung dieser Liste in den Chat — mit der Zuordnung, die dort fehlt:
 * welche Seite zu welcher Tafel gehört.
 *
 * <p><b>Die Zuordnung steckt im Fragment.</b> Jede Fläche hängt ihren Namen
 * als {@code #fn-panel=…} an ihre Adresse. Ohne das heißen in der Liste alle
 * gleich — dieselbe Seite, derselbe Titel.
 *
 * <p><b>Der Abruf läuft nicht im Renderthread.</b> Er geht zwar an die eigene
 * Maschine, aber ein hängender Port hinge sonst das Spiel auf. Ein kurzer
 * Thread holt, der Renderthread schreibt.
 */
public final class WebDevTools {

    /** Woher die Liste kommt. */
    private static final String LIST = "http://127.0.0.1:"
            + dev.devpanda.factorynetwork.web.runtime.WebDebug.PORT + "/json/list";

    /** Was man im Browser öffnet, um alles zu sehen. */
    private static final String FRONT = "http://127.0.0.1:"
            + dev.devpanda.factorynetwork.web.runtime.WebDebug.PORT;

    private WebDevTools() {
    }

    /**
     * Schreibt die Liste in den Chat.
     *
     * <p>Aus dem Spiel zu rufen; die Antwort kommt ein paar Millisekunden
     * später und läuft über {@code Minecraft.execute} zurück.
     */
    public static void show() {
        if (!dev.devpanda.factorynetwork.web.runtime.WebDebug.isRequested()) {
            say(Component.literal(
                            "Chromiums Fernwartung ist zu. Der Client muss mit "
                                    + "-Dfn.devtools=true starten — im Entwicklungslauf über "
                                    + "./gradlew runClient -Pdevtools.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                List<Component> lines = fetch();
                Minecraft.getInstance().execute(() -> lines.forEach(WebDevTools::say));
            } catch (Throwable broken) {
                Minecraft.getInstance().execute(() -> say(Component.literal(
                                "Die Liste ließ sich nicht holen: " + broken)
                        .withStyle(ChatFormatting.RED)));
            }
        }, "FactoryNetwork DevTools");
        worker.setDaemon(true);
        worker.start();
    }

    private static List<Component> fetch() throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(LIST).toURL().openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(5_000);
        JsonElement parsed;
        try (InputStreamReader reader = new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        } finally {
            connection.disconnect();
        }

        List<Component> lines = new ArrayList<>();
        JsonArray pages = parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        if (pages.isEmpty()) {
            lines.add(Component.literal("Es läuft gerade keine Seite.")
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.literal("Offene Seiten (" + pages.size() + "):")
                .withStyle(ChatFormatting.WHITE));
        for (JsonElement element : pages) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject page = element.getAsJsonObject();
            String url = text(page, "url");
            String title = text(page, "title");
            String label = panelName(url);
            lines.add(Component.literal("  • ")
                    .append(Component.literal(label.isEmpty() ? title : label)
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  " + shorten(url))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        lines.add(Component.literal("Werkzeuge öffnen: " + FRONT)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.BLUE)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, FRONT))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Im Browser öffnen und die Seite auswählen")))));
        return lines;
    }

    /**
     * Der Name aus dem Fragment, oder leer.
     *
     * <p>Das Fragment schreibt {@code WebPanels} beim Öffnen einer Fläche.
     * Eine Seite ohne Fragment ist keine Fläche — der Editor etwa.
     */
    private static String panelName(String url) {
        int at = url.indexOf("#fn-panel=");
        if (at < 0) {
            return "";
        }
        String raw = url.substring(at + "#fn-panel=".length());
        return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** Lange Dateiadressen sind im Chat nur Rauschen. */
    private static String shorten(String url) {
        int cut = url.indexOf('#');
        String plain = cut < 0 ? url : url.substring(0, cut);
        int slash = plain.lastIndexOf('/');
        return slash < 0 ? plain : plain.substring(slash + 1);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static void say(Component line) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(line, false);
        }
    }
}
