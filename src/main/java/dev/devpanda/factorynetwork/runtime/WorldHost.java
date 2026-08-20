package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verbindet den Interpreter mit der Welt.
 *
 * <p>Alles, was der Interpreter über Minecraft weiß, geht durch diese Klasse.
 * Das ist der Grund, warum sich die Sprache in gewöhnlichen Tests prüfen
 * lässt: Dort steht eine andere Fassung an dieser Stelle.
 */
public final class WorldHost implements Interpreter.Host {

    private final Level level;
    private final FactoryGraph graph;
    private final NetworkStorage storage;
    private final List<String> logs = new ArrayList<>();

    public WorldHost(Level level, FactoryGraph graph, NetworkStorage storage) {
        this.level = level;
        this.graph = graph;
        this.storage = storage;
    }

    public List<String> logs() {
        return List.copyOf(logs);
    }

    @Override
    public long move(Value amount, Value from, Value to) {
        List<Item> items = itemsOf(amount);
        long limit = amountOf(amount);

        IItemHandler source = from == null ? null : handlerOf(from);
        IItemHandler target = handlerOf(to);
        boolean fromStorage = isStorage(from);
        boolean toStorage = isStorage(to);

        if (fromStorage && toStorage) {
            return 0;
        }
        if (fromStorage) {
            if (items.isEmpty()) {
                throw new ScriptError("Aus dem Speicher muss stehen, was bewegt wird.",
                        "Zum Beispiel: move 64 item:iron_ore from storage to crusher_1");
            }
            long moved = 0;
            for (Item item : items) {
                if (moved >= limit) {
                    break;
                }
                long available = Math.min(limit - moved, storage.count(item));
                if (available <= 0) {
                    continue;
                }
                ItemStack rest = insert(target, new ItemStack(item, (int) available));
                long accepted = available - rest.getCount();
                storage.extract(item, accepted);
                moved += accepted;
            }
            return moved;
        }
        if (toStorage) {
            return drainInto(source, items, limit);
        }
        return transfer(source, target, items, limit);
    }

    private long drainInto(IItemHandler source, List<Item> items, long limit) {
        if (source == null) {
            throw new ScriptError("Bei move fehlt die Quelle.",
                    "Zum Beispiel: move item:iron_ore from chest to storage");
        }
        long moved = 0;
        for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || (!items.isEmpty() && !items.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(limit - moved, stack.getCount());
            ItemStack taken = source.extractItem(slot, wanted, false);
            storage.insert(taken);
            moved += taken.getCount();
        }
        return moved;
    }

    private long transfer(IItemHandler source, IItemHandler target,
                          List<Item> items, long limit) {
        if (source == null) {
            throw new ScriptError("Bei move fehlt die Quelle.");
        }
        long moved = 0;
        for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || (!items.isEmpty() && !items.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(limit - moved, stack.getCount());
            ItemStack simulated = source.extractItem(slot, wanted, true);
            ItemStack rest = insert(target, simulated);
            int accepted = simulated.getCount() - rest.getCount();
            if (accepted <= 0) {
                break;
            }
            source.extractItem(slot, accepted, false);
            moved += accepted;
        }
        return moved;
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        return rest;
    }

    @Override
    public long count(Value what) {
        return itemsOf(what).stream().mapToLong(storage::count).sum();
    }

    @Override
    public int redstone(String device) {
        BlockPos position = connectorPosition(device);
        // Gelesen wird an der Maschine, auf die der Connector zeigt.
        return level.getBestNeighborSignal(position);
    }

    @Override
    public void setRedstone(String device, int strength) {
        BlockPos position = connectorPosition(device);
        if (!(level.getBlockEntity(position) instanceof ConnectorBlockEntity connector)) {
            throw new ScriptError("Der Connector " + device + " ist nicht erreichbar.");
        }
        if (strength < 0 || strength > 15) {
            throw new ScriptError("Redstone geht von 0 bis 15, nicht " + strength + ".");
        }
        connector.setEmittedRedstone(strength);
    }

    @Override
    public void log(String message) {
        logs.add(message);
        if (logs.size() > 100) {
            logs.remove(0);
        }
    }

    @Override
    public boolean hasDevice(String name) {
        return graph.connector(name).isPresent();
    }

    @Override
    public String suggestDevice(String name) {
        return graph.closestName(name).orElse(null);
    }

    // ---- Auflösen ---------------------------------------------------------

    private BlockPos connectorPosition(String name) {
        Optional<BlockPos> position = graph.connector(name);
        if (position.isEmpty()) {
            if (graph.isAmbiguous(name)) {
                throw new ScriptError("Der Name " + name + " ist "
                        + graph.positionsOf(name).size() + "-mal vergeben.",
                        "Solange zwei Connectoren gleich heißen, lässt sich keiner "
                                + "von beiden ansprechen. Benenne einen um.");
            }
            if (!graph.starvedConnectors().isEmpty()) {
                throw new ScriptError("Unbekannter Connector " + name + ".",
                        graph.starvedConnectors().size() + " Geräte im Netz haben keinen "
                                + "freien Kanal bekommen — vielleicht ist eines davon gemeint. "
                                + "Ein Kabelstrang trägt acht.");
            }
            String suggestion = suggestDevice(name);
            throw new ScriptError("Unbekannter Connector " + name + ".",
                    suggestion == null ? null : "Meintest du " + suggestion + "?");
        }
        return position.get();
    }

    private IItemHandler handlerOf(Value value) {
        if (isStorage(value)) {
            return null;
        }
        if (!(value instanceof Value.Device device)) {
            throw new ScriptError("Hier wird ein Gerät erwartet, gefunden wurde "
                    + value.describe() + ".");
        }
        BlockPos position = connectorPosition(device.name());
        if (!level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof ConnectorBlockEntity connector)) {
            throw new ScriptError("Der Connector " + device.name() + " ist nicht erreichbar.",
                    "Vielleicht ist sein Chunk gerade nicht geladen.");
        }
        IItemHandler handler = connector.machineInventory();
        if (handler == null) {
            throw new ScriptError("An " + device.name() + " hängt keine Maschine mit Inventar.");
        }
        return handler;
    }

    private static boolean isStorage(Value value) {
        return value instanceof Value.Builtin builtin && "storage".equals(builtin.name());
    }

    /**
     * Löst einen Auswahlausdruck zu Gegenstandsarten auf.
     *
     * <p>Geht über {@link ItemSelection}, damit Tags, Muster und
     * {@code except} hier dasselbe bedeuten wie beim Worker. Vorher verstand
     * diese Stelle nur einzelne Gegenstände und ließ alles andere still
     * durchfallen — {@code move 64 tag:c/ores} hätte damit alles bewegt statt
     * nur Erze. Ein falsches Ergebnis ohne Meldung ist der schlimmste Fall.
     */
    private List<Item> itemsOf(Value value) {
        if (value instanceof Value.ItemValue item) {
            return List.of(item.item());
        }
        String written = switch (value) {
            case Value.Request request -> request.selector();
            case Value.Text text -> text.value();
            default -> null;
        };
        if (written == null) {
            return List.of();
        }
        Expr parsed = selectorCache.get(written);
        if (parsed == null) {
            parsed = parseSelector(written);
            selectorCache.put(written, parsed);
        }
        List<Item> items = ItemSelection.resolve(parsed);
        if (items.isEmpty()) {
            throw new ScriptError("Die Auswahl " + written + " trifft nichts.",
                    "Gibt es den Gegenstand oder den Tag in diesem Pack?");
        }
        return items;
    }

    private final Map<String, Expr> selectorCache = new HashMap<>();

    /** Baut aus der geschriebenen Form wieder einen Auswahlausdruck. */
    private static Expr parseSelector(String written) {
        int colon = written.indexOf(':');
        if (colon < 0) {
            throw new ScriptError("Das ist keine Auswahl: " + written + ".");
        }
        Expr.Selector.Kind kind = switch (written.substring(0, colon)) {
            case "item" -> Expr.Selector.Kind.ITEM;
            case "fluid" -> Expr.Selector.Kind.FLUID;
            case "chemical" -> Expr.Selector.Kind.CHEMICAL;
            case "tag" -> Expr.Selector.Kind.TAG;
            default -> throw new ScriptError("Unbekannte Art in " + written + ".");
        };
        if (kind == Expr.Selector.Kind.FLUID || kind == Expr.Selector.Kind.CHEMICAL) {
            throw new ScriptError("Flüssigkeiten und Chemikalien kann diese Fassung noch nicht.",
                    "Die Schreibweise steht, die Anbindung kommt später.");
        }
        String rest = written.substring(colon + 1);
        int slash = rest.indexOf('/');
        String namespace = null;
        String path = rest;
        if (slash >= 0 && (kind == Expr.Selector.Kind.TAG || !rest.startsWith("*"))) {
            namespace = rest.substring(0, slash);
            path = rest.substring(slash + 1);
        }
        return new Expr.Selector(kind, namespace, path, new Span(0, 0, 1, 1));
    }

    /** Ohne vorangestellte Menge ist alles gemeint, was verfügbar ist. */
    private static long amountOf(Value value) {
        if (value instanceof Value.Request request && request.hasAmount()) {
            return request.amount();
        }
        return Long.MAX_VALUE;
    }
}
