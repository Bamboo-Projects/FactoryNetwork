package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.lang.Span;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import dev.devpanda.factorynetwork.network.NetworkFluids;
import dev.devpanda.factorynetwork.network.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
    private final NetworkFluids fluidStorage;
    private final List<String> logs = new ArrayList<>();

    /**
     * Die globalen Werte des Netzes, oder {@code null}.
     *
     * <p>Sie leben im Controller und nicht hier: Ein Host wird für einen
     * Durchlauf gebaut, die Werte überdauern ihn. Was hier steht, ist die
     * Karte selbst und keine Kopie — wer schreibt, schreibt im Controller.
     *
     * <p>{@code null} bei Aufrufen ohne Controller. Dann gibt es keine
     * globalen Werte, und das ist etwas anderes als „keine erklärt": Der
     * Interpreter meldet einen unbekannten Namen, was richtig ist.
     */
    private final java.util.Map<String, Value> globals;

    /** Was zu tun ist, wenn ein globaler Wert sich geändert hat. */
    private final Runnable onGlobalChanged;

    public WorldHost(Level level, FactoryGraph graph, NetworkStorage storage,
            NetworkFluids fluidStorage, java.util.Map<String, Value> globals,
            Runnable onGlobalChanged) {
        this.level = level;
        this.graph = graph;
        this.storage = storage;
        this.fluidStorage = fluidStorage == null ? new NetworkFluids() : fluidStorage;
        this.globals = globals;
        this.onGlobalChanged = onGlobalChanged;
    }

    public WorldHost(Level level, FactoryGraph graph, NetworkStorage storage,
            NetworkFluids fluidStorage) {
        this(level, graph, storage, fluidStorage, null, null);
    }

    /** Ohne Flüssigkeitsspeicher — für Aufrufe, die keine brauchen. */
    public WorldHost(Level level, FactoryGraph graph, NetworkStorage storage) {
        this(level, graph, storage, null, null, null);
    }

    @Override
    public Value global(String name) {
        return globals == null ? null : globals.get(name);
    }

    @Override
    public void setGlobal(String name, Value value) {
        if (globals == null) {
            return;
        }
        globals.put(name, value);
        // Ohne das ginge der Wert beim nächsten Speichern verloren: Die
        // BlockEntity weiß nichts davon, dass in ihrer Karte etwas passiert
        // ist.
        if (onGlobalChanged != null) {
            onGlobalChanged.run();
        }
    }

    public List<String> logs() {
        return List.copyOf(logs);
    }

    /**
     * {@code crusher_1.insert(64 item:iron_ore)}
     *
     * <p><b>Derselbe Weg wie {@code move … from storage to gerät}</b>, nur
     * kürzer geschrieben. Das ist kein Zufall, sondern der Grund, warum es
     * hier nur eine Zeile braucht: Die Auswahl, die Mengenrechnung, die
     * Unterscheidung zwischen Gegenständen und Flüssigkeiten — alles steht
     * schon in {@link #move}. Eine zweite Fassung daneben liefe auseinander.
     */
    @Override
    public long insertInto(String device, Value selection) {
        return move(selection, new Value.Builtin("storage"), new Value.Device(device));
    }

    /**
     * {@code storage.items()} — was im Netz lagert.
     *
     * <p>Die Reihenfolge ist die des Speichers und keine sortierte: Wer
     * ordnen will, braucht {@code sort}, und das gibt es noch nicht. Eine
     * stillschweigende Sortierung hier wäre eine Zusage, die niemand
     * eingefordert hat und die später im Weg stünde.
     */
    @Override
    public List<Value> storedItems() {
        List<Value> found = new ArrayList<>();
        storage.contents().forEach((item, count) ->
                found.add(new Value.Selection(List.of(item), count)));
        return found;
    }

    /**
     * {@code crusher_1.items()}
     *
     * <p>Was im Gerät liegt, als Liste von Mengen. Leere Fächer fallen weg —
     * eine Kiste mit siebenundzwanzig Fächern und drei Barren darin soll drei
     * Einträge liefern und nicht siebenundzwanzig.
     *
     * <p>Ein Gerät ohne Inventar liefert eine leere Liste und keinen Fehler:
     * Ob eines da ist, sagt das Profil im Editor, und ein Programm, das über
     * ein leeres Gerät läuft, tut einfach nichts.
     */
    @Override
    public List<Value> itemsIn(String device) {
        IItemHandler handler = handlerOf(new Value.Device(device));
        if (handler == null) {
            return List.of();
        }
        List<Value> found = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                found.add(new Value.Selection(List.of(stack.getItem()), stack.getCount()));
            }
        }
        return found;
    }

    @Override
    public long move(Value amount, Value from, Value to) {
        // Zuerst die Art: Wasser und Steine gehen verschiedene Wege, und ein
        // Fluid-Selektor, der in der Gegenstandsauflösung landet, trifft
        // nichts — was dort ununterscheidbar von "kein Filter" wäre.
        if (isFluidRequest(amount)) {
            return moveFluid(amount, from, to);
        }
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

    /** Meint diese Auswahl Flüssigkeiten? */
    private static boolean isFluidRequest(Value value) {
        Value inner = value instanceof Value.Request request ? request : value;
        if (inner instanceof Value.FluidSelection) {
            return true;
        }
        return inner instanceof Value.Request request
                && request.kind() == Value.Request.Kind.FLUID;
    }

    // ---- Flüssigkeiten ----------------------------------------------------

    /**
     * Bewegt Flüssigkeit, in Millibucket.
     *
     * <p>Die Gestalt spiegelt den Weg der Gegenstände: erst prüfen, dann
     * einfüllen, dann wirklich abziehen. Anders ginge es nicht — ein Tank, der
     * die Hälfte nimmt, darf nicht dazu führen, dass die andere Hälfte
     * verschwindet.
     */
    private long moveFluid(Value amount, Value from, Value to) {
        List<Fluid> fluids = fluidsOf(amount);
        long limit = amountOf(amount);
        boolean fromStorage = isStorage(from);
        boolean toStorage = isStorage(to);
        if (fromStorage && toStorage) {
            return 0;
        }
        if (fromStorage) {
            return fillFromNetwork(fluids, limit, tankOf(to));
        }
        if (toStorage) {
            return drainIntoNetwork(tankOf(from), fluids, limit);
        }
        return transferFluid(tankOf(from), tankOf(to), fluids, limit);
    }

    private long fillFromNetwork(List<Fluid> fluids, long limit, IFluidHandler target) {
        long moved = 0;
        for (Fluid fluid : fluids) {
            if (moved >= limit) {
                break;
            }
            long available = Math.min(limit - moved, fluidStorage.count(fluid));
            if (available <= 0) {
                continue;
            }
            FluidStack offered = new FluidStack(fluid, (int) Math.min(available, Integer.MAX_VALUE));
            int accepted = target.fill(offered, IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) {
                continue;
            }
            fluidStorage.extract(fluid, accepted);
            moved += accepted;
        }
        return moved;
    }

    private long drainIntoNetwork(IFluidHandler source, List<Fluid> fluids, long limit) {
        long moved = 0;
        for (int tank = 0; tank < source.getTanks() && moved < limit; tank++) {
            FluidStack inside = source.getFluidInTank(tank);
            if (inside.isEmpty() || !fluids.contains(inside.getFluid())) {
                continue;
            }
            // Erst fragen, wie viel der Speicher nimmt, dann erst ziehen —
            // eine gezogene Flüssigkeit lässt sich nicht zurücklegen.
            long asked = Math.min(limit - moved, inside.getAmount());
            long fits = fluidStorage.room(inside.getFluid(), asked);
            if (fits <= 0) {
                continue;
            }
            FluidStack wanted = new FluidStack(inside.getFluid(), (int) fits);
            FluidStack taken = source.drain(wanted, IFluidHandler.FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                continue;
            }
            fluidStorage.insert(taken.getFluid(), taken.getAmount());
            moved += taken.getAmount();
        }
        return moved;
    }

    private long transferFluid(IFluidHandler source, IFluidHandler target,
                               List<Fluid> fluids, long limit) {
        long moved = 0;
        for (int tank = 0; tank < source.getTanks() && moved < limit; tank++) {
            FluidStack inside = source.getFluidInTank(tank);
            if (inside.isEmpty() || !fluids.contains(inside.getFluid())) {
                continue;
            }
            FluidStack wanted = new FluidStack(inside.getFluid(),
                    (int) Math.min(limit - moved, inside.getAmount()));
            FluidStack simulated = source.drain(wanted, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                continue;
            }
            int accepted = target.fill(simulated, IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) {
                continue;
            }
            source.drain(new FluidStack(simulated.getFluid(), accepted),
                    IFluidHandler.FluidAction.EXECUTE);
            moved += accepted;
        }
        return moved;
    }

    private IFluidHandler tankOf(Value value) {
        if (!(value instanceof Value.Device device)) {
            throw new ScriptError("Bei move fehlt der Tank.",
                    "Zum Beispiel: move 1000 fluid:water from bottich to kessel");
        }
        BlockPos position = connectorPosition(device.name());
        if (!level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof ConnectorBlockEntity connector)) {
            throw new ScriptError("Der Connector " + device.name() + " ist nicht erreichbar.",
                    "Vielleicht ist sein Chunk gerade nicht geladen.");
        }
        IFluidHandler tank = connector.machineTank();
        if (tank == null) {
            throw new ScriptError("An " + device.name() + " hängt nichts, das Flüssigkeit hält.");
        }
        return tank;
    }

    /** Die Sorten einer Flüssigkeits-Auswahl. */
    private List<Fluid> fluidsOf(Value value) {
        if (value instanceof Value.FluidSelection selection) {
            return selection.fluids();
        }
        String written = value instanceof Value.Request request ? request.selector() : null;
        if (written == null) {
            return List.of();
        }
        Expr parsed = selectorCache.get(written);
        if (parsed == null) {
            parsed = parseSelector(written);
            selectorCache.put(written, parsed);
        }
        List<Fluid> fluids = FluidSelection.resolve(parsed);
        if (fluids.isEmpty()) {
            throw new ScriptError("Die Auswahl " + written + " trifft keine Flüssigkeit.",
                    "Gibt es sie in diesem Pack? Fließendes Wasser zählt nicht mit.");
        }
        return fluids;
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
            // Derselbe Fall wie beim Worker: Der Speicher kann voll sein, seit
            // er an Zellen hängt. Was er nicht nimmt, geht zurück.
            long rest = storage.insert(taken.getItem(), taken.getCount());
            if (rest > 0) {
                ItemStack zurueck = insert(source,
                        new ItemStack(taken.getItem(), (int) rest));
                if (!zurueck.isEmpty()) {
                    throw new ScriptError("Der Speicher ist voll.",
                            "Ein Laufwerk mit freier Zelle schafft Platz.");
                }
            }
            moved += taken.getCount() - rest;
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
        if (isFluidRequest(what)) {
            return fluidsOf(what).stream().mapToLong(fluidStorage::count).sum();
        }
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

    @Override
    public java.util.Collection<String> deviceNames() {
        return graph.connectors().keySet();
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
        if (kind == Expr.Selector.Kind.CHEMICAL) {
            throw new ScriptError("Chemikalien kann diese Fassung noch nicht.",
                    "Die Schreibweise steht, die Anbindung an Mekanism kommt später.");
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
