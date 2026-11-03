package dev.devpanda.factorynetwork.acceptance;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Remote control for a dev client, so tests in the game can run without
 * someone at the keyboard. Only in the acceptance companion, only with
 * {@code -Dfn.control=true}, and only on 127.0.0.1.
 *
 * <p>Every request runs on the render thread and answers plain text.
 * Input goes through the same handlers a real keyboard and mouse reach, so
 * GUI events and their modifiers behave as in play. One exception: key
 * mappings with a modifier read the real key state, which cannot be faked.
 *
 * <pre>
 *   GET /state                         player, screen, slots
 *   GET /cmd?c=fill ...                a chat command, without the slash
 *   GET /use?x=&amp;y=&amp;z=&amp;face=up         right-click a block
 *   GET /move?gx=&amp;gy=                   mouse to a GUI position
 *   GET /click?gx=&amp;gy=&amp;button=0&amp;mods=0  mouse click at a GUI position
 *   GET /slot?n=&amp;button=0&amp;mods=0       click a menu slot by index
 *   GET /key?k=73&amp;mods=2               press and release a key (GLFW codes)
 *   GET /scroll?gx=&amp;gy=&amp;dy=-3          mouse wheel at a GUI position
 *   GET /sneak?on=1                    hold the sneak key (0 lets go)
 *   GET /type?text=x                   set the focused text field
 *   GET /close                         close the screen
 *   GET /shot?name=x                   screenshot into screenshots/x.png
 *   GET /clipboard                     what is on the clipboard
 * </pre>
 */
final class ControlBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT = Integer.getInteger("fn.control.port", 25599);

    private ControlBridge() {
    }

    static void start() {
        if (!Boolean.getBoolean("fn.control")) {
            return;
        }
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            route(server, "/state", q -> state());
            route(server, "/cmd", q -> {
                Minecraft.getInstance().player.connection.sendCommand(q.get("c"));
                return "ok";
            });
            route(server, "/use", ControlBridge::use);
            route(server, "/move", q -> {
                move(dbl(q, "gx"), dbl(q, "gy"));
                return "ok";
            });
            route(server, "/click", q -> click(dbl(q, "gx"), dbl(q, "gy"), num(q, "button", 0), num(q, "mods", 0)));
            route(server, "/slot", ControlBridge::slot);
            route(server, "/key", q -> key(num(q, "k", 0), num(q, "mods", 0)));
            // Scrolls the mouse wheel at a GUI position: dy > 0 up, < 0 down.
            route(server, "/scroll", q -> {
                Minecraft client = Minecraft.getInstance();
                move(dbl(q, "gx"), dbl(q, "gy"));
                invoke(client.mouseHandler, "onScroll", new Class<?>[]{long.class, double.class, double.class},
                        client.getWindow().handle(), 0.0, dbl(q, "dy"));
                return "ok";
            });
            // The manual page for a word of the language, as Ctrl+click in the editor opens it.
            route(server, "/manual", q -> String.valueOf(
                    dev.devpanda.factorynetwork.client.ManualTopics.open(q.getOrDefault("word", ""))));
            // Holds the sneak key down (on=1) or lets go (on=0).
            route(server, "/sneak", q -> {
                Minecraft.getInstance().options.keyShift.setDown(num(q, "on", 1) != 0);
                return "ok";
            });
            // Types into the focused text field of the open screen.
            route(server, "/type", q -> {
                var screen = Minecraft.getInstance().screen;
                if (screen != null && screen.getFocused()
                        instanceof net.minecraft.client.gui.components.EditBox box) {
                    box.setValue(q.getOrDefault("text", ""));
                    return "ok";
                }
                return "no text field";
            });
            route(server, "/close", q -> {
                if (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.onClose();
                }
                return "ok";
            });
            route(server, "/shot", q -> {
                String name = q.getOrDefault("name", "control") + ".png";
                Minecraft client = Minecraft.getInstance();
                Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> { });
                return client.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath().toString();
            });
            route(server, "/clipboard", q -> Minecraft.getInstance().keyboardHandler.getClipboard());
            server.start();
            LOGGER.info("FN_CONTROL listening on 127.0.0.1:{}", PORT);
        } catch (IOException e) {
            LOGGER.error("FN_CONTROL could not start", e);
        }
    }

    private static void route(HttpServer server, String path, Function<Map<String, String>, String> action) {
        server.createContext(path, exchange -> {
            String answer;
            int status = 200;
            try {
                Map<String, String> query = query(exchange);
                CompletableFuture<String> result = Minecraft.getInstance().submit(() -> action.apply(query));
                answer = result.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                status = 500;
                answer = "error: " + e;
            }
            reply(exchange, status, answer == null ? "" : answer);
        });
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> out = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static int num(Map<String, String> q, String key, int fallback) {
        String value = q.get(key);
        return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
    }

    private static double dbl(Map<String, String> q, String key) {
        return Double.parseDouble(q.get(key));
    }

    private static String state() {
        Minecraft client = Minecraft.getInstance();
        StringBuilder out = new StringBuilder();
        if (client.player == null) {
            return "no player";
        }
        out.append("dimension=").append(client.level.dimension().identifier())
                .append(" pos=").append(client.player.blockPosition().toShortString())
                .append(" facing=").append(client.player.getDirection())
                .append(" gamemode=").append(client.gameMode.getPlayerMode())
                .append('\n');
        out.append("screen=").append(client.screen == null ? "none" : client.screen.getClass().getName());
        if (client.screen instanceof AbstractContainerScreen<?> screen) {
            out.append(" left=").append(screen.getLeftPos()).append(" top=").append(screen.getTopPos())
                    .append(" width=").append(screen.getImageWidth()).append(" height=").append(screen.getImageHeight())
                    .append('\n');
            for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                Slot slot = screen.getMenu().slots.get(i);
                ItemStack stack = slot.getItem();
                out.append("slot ").append(i).append(" at ").append(slot.x).append(',').append(slot.y)
                        .append(" container=").append(slot.container.getClass().getSimpleName())
                        .append(" item=").append(stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem())
                                + " x" + stack.getCount())
                        .append('\n');
            }
        } else {
            out.append('\n');
        }
        return out.toString();
    }

    private static String use(Map<String, String> q) {
        Minecraft client = Minecraft.getInstance();
        BlockPos pos = new BlockPos(num(q, "x", 0), num(q, "y", 0), num(q, "z", 0));
        Direction face = Direction.byName(q.getOrDefault("face", "up"));
        dev.devpanda.factorynetwork.client.SlotOverlay.remember(pos);
        var hit = new BlockHitResult(pos.getCenter().relative(face, 0.5), face, pos, false);
        return client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).toString();
    }

    /** Mouse to a GUI position: the handler wants window pixels. */
    private static void move(double guiX, double guiY) {
        Minecraft client = Minecraft.getInstance();
        double scale = client.getWindow().getGuiScale();
        invoke(client.mouseHandler, "onMove", new Class<?>[]{long.class, double.class, double.class},
                client.getWindow().handle(), guiX * scale, guiY * scale);
    }

    private static String click(double guiX, double guiY, int button, int mods) {
        Minecraft client = Minecraft.getInstance();
        move(guiX, guiY);
        // The screen finds the slot under the mouse while drawing; one frame first.
        client.screen.mouseMoved(guiX, guiY);
        Class<?>[] types = {long.class, MouseButtonInfo.class, int.class};
        invoke(client.mouseHandler, "onButton", types, client.getWindow().handle(), new MouseButtonInfo(button, mods), 1);
        invoke(client.mouseHandler, "onButton", types, client.getWindow().handle(), new MouseButtonInfo(button, mods), 0);
        return "ok";
    }

    private static String slot(Map<String, String> q) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            return "no container screen";
        }
        Slot slot = screen.getMenu().slots.get(num(q, "n", 0));
        return click(screen.getLeftPos() + slot.x + 8, screen.getTopPos() + slot.y + 8,
                num(q, "button", 0), num(q, "mods", 0));
    }

    private static String key(int key, int mods) {
        Minecraft client = Minecraft.getInstance();
        Class<?>[] types = {long.class, int.class, KeyEvent.class};
        invoke(client.keyboardHandler, "keyPress", types, client.getWindow().handle(), 1, new KeyEvent(key, 0, mods));
        invoke(client.keyboardHandler, "keyPress", types, client.getWindow().handle(), 0, new KeyEvent(key, 0, mods));
        return "ok";
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(name + ": " + e, e);
        }
    }
}
