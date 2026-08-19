package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        Item item = itemOf(amount);
        long limit = amountOf(amount);

        IItemHandler source = from == null ? null : handlerOf(from);
        IItemHandler target = handlerOf(to);
        boolean fromStorage = isStorage(from);
        boolean toStorage = isStorage(to);

        if (fromStorage && toStorage) {
            return 0;
        }
        if (fromStorage) {
            if (item == null) {
                throw new ScriptError("Aus dem Speicher muss stehen, was bewegt wird.",
                        "Zum Beispiel: move 64 item:iron_ore from storage to crusher_1");
            }
            long available = Math.min(limit, storage.count(item));
            if (available <= 0) {
                return 0;
            }
            ItemStack rest = insert(target, new ItemStack(item, (int) available));
            long accepted = available - rest.getCount();
            storage.extract(item, accepted);
            return accepted;
        }
        if (toStorage) {
            return drainInto(source, item, limit);
        }
        return transfer(source, target, item, limit);
    }

    private long drainInto(IItemHandler source, Item item, long limit) {
        if (source == null) {
            throw new ScriptError("Bei move fehlt die Quelle.",
                    "Zum Beispiel: move item:iron_ore from chest to storage");
        }
        long moved = 0;
        for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || (item != null && stack.getItem() != item)) {
                continue;
            }
            int wanted = (int) Math.min(limit - moved, stack.getCount());
            ItemStack taken = source.extractItem(slot, wanted, false);
            storage.insert(taken);
            moved += taken.getCount();
        }
        return moved;
    }

    private long transfer(IItemHandler source, IItemHandler target, Item item, long limit) {
        if (source == null) {
            throw new ScriptError("Bei move fehlt die Quelle.");
        }
        long moved = 0;
        for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || (item != null && stack.getItem() != item)) {
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
        Item item = itemOf(what);
        return item == null ? 0 : storage.count(item);
    }

    @Override
    public int redstone(String device) {
        BlockPos position = connectorPosition(device);
        // Gelesen wird an der Maschine, auf die der Connector zeigt.
        return level.getBestNeighborSignal(position);
    }

    @Override
    public void setRedstone(String device, int strength) {
        // Bewusst noch nicht: Dafür braucht der Connector einen eigenen
        // Zustand im Blockmodell, und der gehört in denselben Schritt wie die
        // Anzeige am Block.
        throw new ScriptError("Redstone setzen kann diese Fassung noch nicht.",
                "Lesen geht bereits: sensor.redstone()");
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

    /** Holt die Gegenstandsart aus einem Auswahlausdruck. */
    private static Item itemOf(Value value) {
        String written;
        if (value instanceof Value.Request request) {
            written = request.selector();
        } else if (value instanceof Value.Text text) {
            written = text.value();
        } else if (value instanceof Value.ItemValue item) {
            return item.item();
        } else {
            return null;
        }
        int colon = written.indexOf(':');
        if (colon < 0 || !written.startsWith("item:")) {
            return null;
        }
        String rest = written.substring(colon + 1);
        int slash = rest.indexOf('/');
        String namespace = slash < 0 ? "minecraft" : rest.substring(0, slash);
        String path = slash < 0 ? rest : rest.substring(slash + 1);
        if (path.indexOf('*') >= 0) {
            throw new ScriptError("Muster kann diese Fassung noch nicht auflösen.",
                    "Nenne den Gegenstand einzeln, etwa item:iron_ore.");
        }
        ResourceLocation id = ResourceLocation.tryBuild(namespace.toLowerCase(Locale.ROOT), path);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new ScriptError("Unbekannter Gegenstand " + written + ".");
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    /** Ohne vorangestellte Menge ist alles gemeint, was verfügbar ist. */
    private static long amountOf(Value value) {
        if (value instanceof Value.Request request && request.hasAmount()) {
            return request.amount();
        }
        return Long.MAX_VALUE;
    }
}
