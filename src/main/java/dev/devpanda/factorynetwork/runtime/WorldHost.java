package dev.devpanda.factorynetwork.runtime;

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
 * Connects the interpreter to the world.
 *
 * <p>Everything the interpreter knows about Minecraft goes through this
 * class. That is why the language can be checked in ordinary tests: there, a
 * different implementation stands in this place.
 */
public final class WorldHost implements Interpreter.Host {

    private final Level level;
    private final FactoryGraph graph;
    private final NetworkStorage storage;
    private final NetworkFluids fluidStorage;

    /**
     * All stocks of the network, by kind.
     *
     * <p>The three named fields above point into it. It is consulted where
     * the kind is only known at runtime — with {@code count}, say, which is
     * the same question for items, fluids and chemicals.
     */
    private final dev.devpanda.factorynetwork.network.NetworkStores stores;

    private final dev.devpanda.factorynetwork.network.ResourceStore chemicals;

    /**
     * The network's power reserve; set by the controller.
     *
     * <p>Supplied later, like the chemical store. If it is missing, that is
     * not an empty network but no network at all — and then {@code
     * network.power} speaks up instead of inventing a zero.
     */
    private dev.devpanda.factorynetwork.network.NetworkPower power;

    public void setPower(dev.devpanda.factorynetwork.network.NetworkPower store) {
        this.power = store;
    }

    /**
     * The name under which the network shows up in other mods' logs.
     *
     * <p>Fixed, not random: claim mods recognise an actor by its id, and a
     * network that had a different name after every restart could never be
     * whitelisted in any protected zone.
     */
    private static final com.mojang.authlib.GameProfile CLICKER =
            new com.mojang.authlib.GameProfile(
                    java.util.UUID.fromString("f4c704e7-0000-4000-8000-000000000001"),
                    "[factorynetwork]");

    /**
     * {@code altar.click()} — a right-click on the machine.
     *
     * <p><b>Via the full path, not the shortcut.</b> Calling
     * {@code useWithoutItem} directly would be shorter, but it would not
     * fire {@code PlayerInteractEvent.RightClickBlock} — and that is exactly
     * what the protection mods hook into. This mod does not protect the
     * world itself ("that is what protection mods are for", says the label
     * gun); then it must at least leave the way open for those that do.
     *
     * <p><b>No window opens.</b> Clicking a block that would open a menu
     * yields {@code true} and shows nothing: for a player that does not
     * exist, {@code openMenu} has no effect. What an inventory holds is
     * fetched with {@code move}, not with a click.
     */
    @Override
    public boolean clickAt(String device) {
        var connector = connectorFor(device);
        if (connector == null
                || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            throw new ScriptError("Nichts im Netz heißt „" + device + "“.",
                    "Ein Klick geht an ein Gerät, das im Netz hängt.");
        }
        var position = connector.pos();
        var facing = connector.facing();
        var target = position.relative(facing);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory
                .get(server, CLICKER);
        // Reach is checked since 1.21: a player at the origin clicks into
        // thin air, and the call would return without effect.
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(target)
                        .relative(facing.getOpposite(), 0.5),
                facing.getOpposite(), target, false);
        var result = player.gameMode.useItemOn(player, level,
                net.minecraft.world.item.ItemStack.EMPTY,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return result.consumesAction();
    }

    @Override
    public long networkPower() {
        if (power == null) {
            return Interpreter.Host.super.networkPower();
        }
        return power.stored();
    }

    @Override
    public long networkCapacity() {
        if (power == null) {
            return Interpreter.Host.super.networkCapacity();
        }
        return power.capacity();
    }
    private final List<LogEntry> logs = new ArrayList<>();

    /**
     * Who is currently writing.
     *
     * <p>Set by the caller before executing: the flow engine per flow, the
     * worker runtime per worker, the terminal per call.
     */
    private String logSource = "";

    /**
     * Where the lines go, or {@code null}.
     *
     * <p><b>Pass on immediately instead of collecting.</b> There are several
     * hosts — one for the workers, one for the flow engine, one per call from
     * the terminal —, and whoever wants to collect from them has to remember
     * every single place. Exactly one of them was forgotten, and the flows'
     * messages never arrived.
     */
    private java.util.function.Consumer<LogEntry> logSink;

    /**
     * The network's global values, or {@code null}.
     *
     * <p>They live in the controller and not here: a host is built for one
     * run, the values outlive it. What is held here is the map itself, not a
     * copy — whoever writes, writes into the controller.
     *
     * <p>{@code null} for calls without a controller. Then there are no
     * global values, which is something other than "none declared": the
     * interpreter reports an unknown name, which is correct.
     */
    private final java.util.Map<String, Value> globals;

    /** What to do when a global value has changed. */
    private final Runnable onGlobalChanged;

    /**
     * The network's device groups, resolved.
     *
     * <p>The same ones the workers use — including their pointer for
     * {@code round_robin}. Two resolutions side by side would mean two
     * orderings: a {@code move to crushers} from a function and a worker on
     * the same group would then each rotate on their own, and both would
     * always hit the same device first.
     */
    private Map<String, DeviceGroup> groups = Map.of();

    /**
     * Whom to notify that the network has written into a device.
     *
     * <p>The controller then updates the baseline for
     * {@code device_output}. Without a receiver — in calls without a
     * controller — the inventories remain unchanged.
     */
    private java.util.function.Consumer<String> onDeviceFilled;

    /**
     * Who accepts a crafting request, or {@code null}.
     *
     * <p>The controller: the jobs live there. Without it — in calls without a
     * network — there is no crafting, and {@code craft} returns zero instead
     * of an id that belongs to nothing.
     */
    private java.util.function.ToLongBiFunction<Item, Integer> crafting;

    /** Set by the controller. */
    public void setCrafting(java.util.function.ToLongBiFunction<Item, Integer> accept) {
        this.crafting = accept;
    }

    /**
     * Where whatever found no home goes. Set by the controller.
     *
     * <p><b>Without this receiver the goods die with the error.</b> Taking
     * from a machine that does not take back leaves the remainder in hand —
     * and a thrown {@link ScriptError} takes it along.
     */
    public void setHoldBack(java.util.function.Consumer<ItemStack> sink) {
        this.holdBack = sink;
    }

    private java.util.function.Consumer<ItemStack> holdBack;

    public WorldHost(Level level, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores,
            java.util.Map<String, Value> globals, Runnable onGlobalChanged) {
        this.level = level;
        this.graph = graph;
        this.stores = stores == null
                ? new dev.devpanda.factorynetwork.network.NetworkStores() : stores;
        this.storage = this.stores.items();
        this.fluidStorage = this.stores.fluids();
        this.chemicals = this.stores.chemicals();
        this.globals = globals;
        this.onGlobalChanged = onGlobalChanged;
    }

    public WorldHost(Level level, FactoryGraph graph,
            dev.devpanda.factorynetwork.network.NetworkStores stores) {
        this(level, graph, stores, null, null);
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
        // Without this the value would be lost on the next save: the
        // BlockEntity knows nothing about something having happened in its
        // map.
        if (onGlobalChanged != null) {
            onGlobalChanged.run();
        }
    }

    public List<LogEntry> logs() {
        return List.copyOf(logs);
    }

    /**
     * {@code crusher_1.insert(64 item:iron_ore)}
     *
     * <p><b>The same path as {@code move … from storage to gerät}</b>, just
     * written more briefly. That is no accident but the reason a single line
     * suffices here: the selection, the amount arithmetic, the distinction
     * between items and fluids — all of it is already in {@link #move}. A
     * second version alongside would drift apart.
     */
    @Override
    public long insertInto(String device, Value selection) {
        return move(selection, new Value.Builtin("storage"), new Value.Device(device));
    }

    /**
     * {@code storage.items()} — what is stored in the network.
     *
     * <p>The order is the storage's own, not a sorted one: anyone who wants
     * ordering needs {@code sort}, which does not exist yet. A silent sort
     * here would be a promise nobody asked for and one that would get in the
     * way later.
     */
    @Override
    public List<Value> storedItems() {
        List<Value> found = new ArrayList<>();
        storage.byItem().forEach((item, count) ->
                found.add(Value.Selection.ofItems(List.of(item), count)));
        return found;
    }

    /**
     * {@code crusher_1.items()}
     *
     * <p>What lies in the device, as a list of amounts. Empty slots are left
     * out — a chest with twenty-seven slots and three ingots in it should
     * yield three entries, not twenty-seven.
     *
     * <p>A device without an inventory yields an empty list and no error:
     * whether one exists is stated by the profile in the editor, and a
     * program that iterates over an empty device simply does nothing.
     */
    /**
     * {@code brecher_1.slots(1..5)} — what lies in particular slots.
     *
     * <p>Via the undivided inventory, not via the side: anyone who writes a
     * slot number knows the machine, and one connector per machine should
     * suffice.
     *
     * <p>Empty slots are left out, as with {@link #itemsIn} — a chest with
     * twenty-seven slots and three ingots should yield three entries. A
     * number that does not exist is skipped: the number of slots is up to
     * the foreign machine, and a range reaching beyond it is not a program
     * error.
     */
    @Override
    public List<Value> itemsInSlots(String device, List<Integer> slots) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorFor(device);
        if (connector == null) {
            return List.of();
        }
        IItemHandler handler = connector.machineInventoryAll();
        if (handler == null) {
            return List.of();
        }
        List<Value> found = new ArrayList<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= handler.getSlots()) {
                continue;
            }
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                found.add(Value.Selection.ofItems(
                        List.of(stack.getItem()), stack.getCount()));
            }
        }
        return List.copyOf(found);
    }

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
                found.add(Value.Selection.ofItems(
                        List.of(stack.getItem()), stack.getCount()));
            }
        }
        return found;
    }

    @Override
    public long move(Value amount, Value from, Value to) {
        // The kind first: water and stone take different paths, and a fluid
        // selector that ends up in item resolution matches nothing — which
        // there would be indistinguishable from "no filter".
        ResourceKind kind = ResourceKind.of(amount);
        if (kind == ResourceKinds.FLUID) {
            return moveFluid(amount, from, to);
        }
        if (kind == ResourceKinds.CHEMICAL) {
            return moveChemical(amount, from, to);
        }
        // A kind registered by a foreign mod: it goes via the second axis,
        // which the mod itself provides. Without these lines it would run
        // into item resolution — and what matches nobody there would mean
        // "no filter".
        if (kind != null && kind != ResourceKinds.ITEM) {
            return moveForeign(kind, amount, from, to);
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
                // "all" is the one answer to this question: whoever writes
                // it has said what is to be moved — namely everything.
                // Without this branch the message below would confront a
                // program in which the selection is spelled out explicitly.
                if (isEverything(amount)) {
                    items = List.copyOf(storage.byItem().keySet());
                } else {
                    throw new ScriptError("Aus dem Speicher muss stehen, was bewegt wird.",
                            "Zum Beispiel: move 64 item:iron_ore from storage to crusher_1 "
                                    + "— oder all, wenn wirklich alles gemeint ist.");
                }
            }
            long moved = 0;
            // Over the entries and not over the kinds: what is chosen here
            // goes into the machine as a stack. If only the id were used, a
            // named hoe would arrive as a bare one.
            for (var key : List.copyOf(storage.contents().keySet())) {
                if (moved >= limit) {
                    break;
                }
                if (!items.isEmpty() && !items.contains(key.item())) {
                    continue;
                }
                long available = Math.min(
                        Math.min(limit - moved, key.maxStackSize()), storage.count(key));
                if (available <= 0) {
                    continue;
                }
                ItemStack rest = Handoffs.insertInto(target, key.toStack((int) available));
                long accepted = available - rest.getCount();
                // The same case as with the worker: the stock shows what a
                // storage bus sees, and a foreign inventory may keep its
                // contents. What did not actually come out is already in the
                // target and has to come back out of there.
                long taken = storage.extract(key, accepted);
                if (taken < accepted) {
                    Handoffs.pullBack(target, key, accepted - taken);
                }
                moved += taken;
            }
            return moved;
        }
        if (toStorage) {
            return drainInto(source, items, limit);
        }
        return transfer(source, target, items, limit);
    }

    /**
     * Moves a kind that a foreign mod brought along.
     *
     * <p><b>Via the network storage, even from device to device.</b> The
     * same restraint as with chemicals: without the intermediate stop there
     * would need to be a third path for the same operation, and whatever got
     * lost on the way would have gone uncounted. Whether that remains the
     * right answer is a product question and is discussed in
     * {@code maschinenzugriff.md}.
     */
    private long moveForeign(ResourceKind kind, Value amount, Value from, Value to) {
        var machine = kind.machine();
        if (machine == dev.devpanda.factorynetwork.network.MachineAccess.NONE) {
            throw new ScriptError(kind.prefix() + " lässt sich an keiner Maschine bewegen.",
                    "Die Mod, die diese Art mitbringt, hat dafür keinen Zugriff "
                            + "angemeldet. Im Netz lagern kann sie trotzdem.");
        }
        List<?> keys = keysOf(kind, amount);
        long limit = amountOf(amount);
        var store = stores.of(kind);
        boolean fromStorage = isStorage(from);
        boolean toStorage = isStorage(to);
        if (fromStorage && toStorage) {
            return 0;
        }
        if (fromStorage) {
            var target = machineOf(to);
            return machine.fill(store, level, target.pos(), target.side(), keys, limit);
        }
        if (toStorage) {
            var source = machineOf(from);
            return machine.drain(level, source.pos(), source.side(), keys, store, limit);
        }
        var source = machineOf(from);
        var target = machineOf(to);
        long pulled = machine.drain(level, source.pos(), source.side(), keys, store, limit);
        if (pulled <= 0) {
            return 0;
        }
        return machine.fill(store, level, target.pos(), target.side(), keys, pulled);
    }

    /**
     * Does it explicitly say {@code all}?
     *
     * <p>Remains a question of its own: {@code all} is not a resource kind
     * but the declaration that there is no filter.
     */
    private static boolean isEverything(Value value) {
        return value instanceof Value.Request request
                && request.kind() == Value.Request.Kind.ALL;
    }

    // ---- Chemicals --------------------------------------------------------

    /**
     * Moves chemicals, in millibuckets.
     *
     * <p>The same structure as for fluids, and the same reason for every
     * line: what the storage will not take must not leave the container in
     * the first place — a gas that is out and fits nowhere would be gone.
     *
     * <p>Whatever touches Mekanism lives in {@code compat/mekanism}; here
     * there are only ids as text.
     */
    private long moveChemical(Value amount, Value from, Value to) {
        List<String> ids = chemicalsOf(amount);
        long limit = amountOf(amount);
        boolean fromStorage = isStorage(from);
        boolean toStorage = isStorage(to);
        if (fromStorage && toStorage) {
            return 0;
        }
        if (fromStorage) {
            var target = machineOf(to);
            return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.fillFrom(
                    chemicals, level, target.pos(), target.side(), ids, limit);
        }
        if (toStorage) {
            var source = machineOf(from);
            return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.drainInto(
                    level, source.pos(), source.side(), ids, chemicals, limit);
        }
        // Device to device goes via the network storage as an intermediate
        // stop. Without it there would need to be a third path for the same
        // operation, and the amount lost on the way would have gone
        // uncounted.
        var source = machineOf(from);
        var target = machineOf(to);
        long pulled = dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.drainInto(
                level, source.pos(), source.side(), ids, chemicals, limit);
        if (pulled <= 0) {
            return 0;
        }
        return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.fillFrom(
                chemicals, level, target.pos(), target.side(), ids, pulled);
    }

    /**
     * The ids behind a chemical selection.
     *
     * <p><b>No match is an error, not an empty list</b> — the same rule as
     * for items and fluids. It was missing here, and that was costly: empty
     * means <i>everything</i> further down ({@code MekTanks.matches} lets
     * every sort through when none is given). A typo in {@code chemical:…}
     * thus filled some random gas from the network into the machine without
     * saying a word.
     *
     * <p>If Mekanism is missing, the message says so instead of pretending
     * the pack is to blame.
     */
    private List<String> chemicalsOf(Value value) {
        // An already resolved selection — that is how every chemical arrives
        // from a loop and from every it. Without these two lines the list
        // would stay empty, and empty means "no filter" further down.
        if (value instanceof Value.Selection selection
                && selection.kind() == ResourceKinds.CHEMICAL) {
            return selection.chemicals();
        }
        if (value instanceof Value.Resource resource
                && resource.kind() == ResourceKinds.CHEMICAL) {
            return List.of(resource.chemical());
        }
        if (value instanceof Value.Request request) {
            List<String> ids = dev.devpanda.factorynetwork.compat.mekanism.Chemicals.resolve(
                    dev.devpanda.factorynetwork.lang.Selectors.parse(request.selector()));
            if (ids.isEmpty()) {
                throw dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.installed()
                        ? new ScriptError("Die Auswahl " + request.selector()
                                + " trifft keine Chemikalie.",
                                "Gibt es sie in diesem Pack? Ohne Namensraum ist "
                                        + "mekanism gemeint.")
                        : new ScriptError(
                                dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.reason(),
                                dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.hint());
            }
            return ids;
        }
        return List.of();
    }

    /** Where a machine stands and from which side the connector faces it. */
    private record Machine(BlockPos pos, net.minecraft.core.Direction side) {
    }

    private Machine machineOf(Value value) {
        if (value instanceof Value.Group group) {
            return machineOf(memberFor(group));
        }
        if (!(value instanceof Value.Device device)) {
            throw new ScriptError("Bei move fehlt die Maschine.",
                    "Zum Beispiel: move 1000 chemical:mekanism/hydrogen "
                            + "from elektrolyseur to storage");
        }
        var connector = connectorFor(device.name());
        if (connector == null) {
            throw new ScriptError("Der Connector " + device.name() + " ist nicht erreichbar.",
                    "Vielleicht ist sein Chunk gerade nicht geladen.");
        }
        // The facing comes from the part and no longer from the BlockState:
        // up to six sit on one cable block, and each points somewhere else.
        net.minecraft.core.Direction facing = connector.facing();
        return new Machine(connector.pos().relative(facing), facing.getOpposite());
    }

    // ---- Fluids -----------------------------------------------------------

    /**
     * Moves fluid, in millibuckets.
     *
     * <p>The shape mirrors the item path: check first, then fill, then
     * actually extract. It could not work any other way — a tank that takes
     * half must not cause the other half to vanish.
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
            // First ask how much the storage will take, only then drain — a
            // drained fluid cannot be put back.
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
            moved += Handoffs.fluid(source, target, wanted).moved();
        }
        return moved;
    }

    private IFluidHandler tankOf(Value value) {
        if (value instanceof Value.Group group) {
            return tankOf(memberFor(group));
        }
        if (!(value instanceof Value.Device device)) {
            throw new ScriptError("Bei move fehlt der Tank.",
                    "Zum Beispiel: move 1000 fluid:water from bottich to kessel");
        }
        var connector = connectorFor(device.name());
        if (connector == null) {
            throw new ScriptError("Der Connector " + device.name() + " ist nicht erreichbar.",
                    "Vielleicht ist sein Chunk gerade nicht geladen.");
        }
        IFluidHandler tank = connector.machineTank();
        if (tank == null) {
            throw new ScriptError("An " + device.name() + " hängt nichts, das Flüssigkeit hält.");
        }
        return NotifyingHandlers.fluids(tank, noticeFor(device.name()));
    }

    /** The fluid types of a fluid selection. */
    private List<Fluid> fluidsOf(Value value) {
        if (value instanceof Value.Selection selection
                && selection.kind() == ResourceKinds.FLUID) {
            return selection.fluids();
        }
        if (value instanceof Value.Resource resource
                && resource.kind() == ResourceKinds.FLUID) {
            return List.of(resource.fluid());
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
            // The same case as with the worker, and the same order: first
            // ask how much fits in, then take out that much. What a machine
            // gives up and does not take back would otherwise be gone even
            // before the error below is thrown.
            long fits = storage.room(
                    dev.devpanda.factorynetwork.storage.ItemKey.of(stack), wanted);
            if (fits <= 0) {
                throw new ScriptError("Der Speicher ist voll.",
                        "Ein Laufwerk mit freier Zelle schafft Platz.");
            }
            ItemStack taken = source.extractItem(slot, (int) Math.min(wanted, fits), false);
            long rest = storage.insert(taken);
            if (rest > 0) {
                ItemStack zurueck = Handoffs.insertInto(source, taken.copyWithCount((int) rest));
                if (!zurueck.isEmpty()) {
                    // Hand over to hold-back first, then throw. The other way
                    // round the error would take the goods with it — exactly
                    // the silent loss the hold-back is built against.
                    if (holdBack != null) {
                        holdBack.accept(zurueck);
                    }
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
        return Handoffs.items(source, target, items, limit).moved();
    }

    /**
     * {@code brecher.count(item:iron_ore)} — what lies in the device.
     *
     * <p>Inventory and tank, as everywhere on a device: which one is meant is
     * stated by the selection. Without a selection it adds everything up —
     * the question "how full is the machine", for which there is otherwise
     * no form.
     *
     * <p>A device without an inventory yields zero and no error. That none
     * exists is stated by the profile in the editor; a program that computes
     * over an empty device has an empty device, not a program error.
     */
    @Override
    public long countIn(String device, Value what) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorFor(device);
        if (connector == null) {
            return 0;
        }
        ResourceKind kind = ResourceKind.of(what);
        if (kind != null && kind != ResourceKinds.ITEM && kind != ResourceKinds.FLUID
                && kind != ResourceKinds.CHEMICAL) {
            net.minecraft.core.Direction facing = connector.facing();
            return kind.machine().count(level,
                    connector.pos().relative(facing), facing.getOpposite(),
                    keysOf(kind, what));
        }
        if (kind == ResourceKinds.FLUID) {
            return countFluids(connector, fluidsOf(what));
        }
        if (kind == ResourceKinds.CHEMICAL) {
            net.minecraft.core.Direction facing = connector.facing();
            return dev.devpanda.factorynetwork.compat.mekanism.ChemicalStores.amountAt(
                    level, connector.pos().relative(facing), facing.getOpposite(),
                    chemicalsOf(what));
        }
        List<Item> items = what instanceof Value.Nothing ? List.of() : itemsOf(what);
        return countItems(connector, items);
    }

    /**
     * Orders a crafting job.
     *
     * <p>The selection must mean <b>one</b> kind: a job for "some ore or
     * other" would have no answer to the question of what to build. A tag
     * with exactly one match goes through — then the question is answered.
     */
    @Override
    public long craft(Value what) {
        if (crafting == null) {
            return 0;
        }
        List<Item> items = itemsOf(what);
        if (items.size() != 1) {
            throw new ScriptError("craft braucht genau eine Art.",
                    "Zum Beispiel: craft(64 item:chest). Ein Tag, der mehreres "
                            + "trifft, sagt nicht, was gebaut werden soll.");
        }
        long amount = amountOf(what);
        return crafting.applyAsLong(items.get(0),
                (int) Math.max(1, Math.min(amount, 100_000)));
    }

    /**
     * A machine's energy level.
     *
     * <p>Read undivided, not per side: anyone asking for a machine's energy
     * means the machine and not the side its connector is attached to.
     */
    @Override
    public long energyIn(String device) {
        dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorFor(device);
        if (connector == null) {
            return 0;
        }
        var storage = connector.machineEnergy();
        return storage == null ? 0 : storage.getEnergyStored();
    }

    /** Without a selection everything counts. */
    private static long countItems(dev.devpanda.factorynetwork.block.entity.ConnectorPart connector, List<Item> items) {
        IItemHandler handler = connector.machineInventory();
        if (handler == null) {
            return 0;
        }
        long found = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && (items.isEmpty() || items.contains(stack.getItem()))) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static long countFluids(dev.devpanda.factorynetwork.block.entity.ConnectorPart connector, List<Fluid> fluids) {
        IFluidHandler tank = connector.machineTank();
        if (tank == null) {
            return 0;
        }
        long found = 0;
        for (int index = 0; index < tank.getTanks(); index++) {
            FluidStack stack = tank.getFluidInTank(index);
            if (!stack.isEmpty() && (fluids.isEmpty() || fluids.contains(stack.getFluid()))) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    /** The BlockEntity of a connector, or {@code null}. */
    private dev.devpanda.factorynetwork.block.entity.ConnectorPart connectorFor(
            String device) {
        // connectorPosition speaks up itself if the name is unknown or
        // assigned twice. What remains here is the loaded chunk.
        var where = connectorPosition(device);
        if (where.side() == null || !level.isLoaded(where.pos())) {
            return null;
        }
        // By position and side: a connector either sits alone in a connector
        // block or on one face of a cable — either way it looks in exactly
        // one direction.
        return dev.devpanda.factorynetwork.block.entity.Connectors.at(level, where.pos(), where.side());
    }

    @Override
    public long count(Value what) {
        // The same question for all three kinds, now that they stand behind
        // one interface: the only thing still different per kind is the
        // resolution of the selection.
        ResourceKind found = ResourceKind.of(what);
        ResourceKind kind = found == null ? ResourceKinds.ITEM : found;
        var store = stores.of(kind);
        return keysOf(kind, what).stream().mapToLong(store::count).sum();
    }

    @Override
    public int redstone(String device) {
        // <b>Read at the block, not at the face.</b> Whoever places a lever
        // next to a connector means that connector — and the face it looks
        // into is occupied by the machine. If six connectors sit on one
        // cable block, all six read the same: what reaches the block reaches
        // every one of them.
        return level.getBestNeighborSignal(connectorPosition(device).pos());
    }

    @Override
    public void setRedstone(String device, int strength) {
        var connector = connectorFor(device);
        if (connector == null) {
            throw new ScriptError("Der Connector " + device + " ist nicht erreichbar.");
        }
        if (strength < 0 || strength > 15) {
            throw new ScriptError("Redstone geht von 0 bis 15, nicht " + strength + ".");
        }
        connector.setEmittedRedstone(strength);
    }

    @Override
    public void log(String message) {
        log(LogLevel.INFO, message);
    }

    @Override
    public void log(LogLevel level, String message) {
        // The time is fixed right here and not only on collection: between
        // writing and picking up lies a whole tick with all the workers in
        // it.
        LogEntry entry = new LogEntry(level, System.currentTimeMillis(), logSource, message);
        if (logSink != null) {
            logSink.accept(entry);
            return;
        }
        logs.add(entry);
        if (logs.size() > 100) {
            logs.remove(0);
        }
    }

    /**
     * Notifies as soon as the network has written into a device.
     *
     * <p>Set by the controller. What depends on it is described at
     * {@link dev.devpanda.factorynetwork.runtime.NotifyingHandlers}.
     */
    public void setDeviceFilled(java.util.function.Consumer<String> listener) {
        this.onDeviceFilled = listener;
    }

    /** The notice for one device, or {@code null} without a receiver. */
    private Runnable noticeFor(String device) {
        return onDeviceFilled == null ? null : () -> onDeviceFilled.accept(device);
    }

    /** The network's resolved groups; set by the controller. */
    public void setGroups(Map<String, DeviceGroup> resolved) {
        this.groups = resolved == null ? Map.of() : resolved;
    }

    @Override
    public List<String> membersOf(String group) {
        DeviceGroup found = groups.get(group);
        return found == null ? List.of() : found.members();
    }

    /**
     * The device a group is currently distributing to.
     *
     * <p>The order comes from the group itself — round robin, the emptiest,
     * the first that can. The first member with something actually attached
     * is taken: a device whose chunk is not loaded right now must not stall
     * the distribution.
     */
    private Value.Device memberFor(Value.Group group) {
        DeviceGroup resolved = groups.get(group.name());
        if (resolved == null || resolved.isEmpty()) {
            throw new ScriptError("Die Gruppe " + group.name() + " hat kein Mitglied im Netz.",
                    "Steht sie im Programm, und hängen ihre Geräte am Netz?");
        }
        for (String candidate : resolved.order(this::fillLevelOf, new java.util.Random())) {
            if (connectorFor(candidate) != null) {
                return new Value.Device(candidate);
            }
        }
        throw new ScriptError("Kein Gerät der Gruppe " + group.name() + " ist erreichbar.",
                "Vielleicht ist gerade kein Chunk davon geladen.");
    }

    /** How full a device is — for distribution to the emptiest. */
    private long fillLevelOf(String device) {
        var connector = connectorFor(device);
        if (connector == null) {
            return Long.MAX_VALUE;
        }
        IItemHandler handler = connector.machineInventory();
        if (handler == null) {
            return Long.MAX_VALUE;
        }
        long found = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            found += handler.getStackInSlot(slot).getCount();
        }
        return found;
    }

    /** Sends every line there immediately instead of collecting it. */
    public void setLogSink(java.util.function.Consumer<LogEntry> sink) {
        this.logSink = sink;
    }

    @Override
    public void setLogSource(String source) {
        this.logSource = source == null ? "" : source;
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

    // ---- Resolving --------------------------------------------------------

    private dev.devpanda.factorynetwork.network.DevicePos connectorPosition(String name) {
        Optional<dev.devpanda.factorynetwork.network.DevicePos> position =
                graph.connector(name);
        if (position.isEmpty()) {
            if (graph.isAmbiguous(name)) {
                throw new ScriptError("Der Name " + name + " ist "
                        + graph.positionsOf(name).size() + "-mal vergeben.",
                        "Solange zwei Connectoren gleich heißen, lässt sich keiner "
                                + "von beiden ansprechen. Benenne einen um.");
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
        if (value instanceof Value.Group group) {
            return handlerOf(memberFor(group));
        }
        // Particular slots are a device with fewer slots — via the undivided
        // inventory, that is. That is exactly the handle with which a move
        // empties only the output.
        if (value instanceof Value.DeviceSlots view) {
            dev.devpanda.factorynetwork.block.entity.ConnectorPart connector = connectorFor(view.device());
            if (connector == null) {
                throw new ScriptError("Der Connector " + view.device()
                        + " ist nicht erreichbar.",
                        "Vielleicht ist sein Chunk gerade nicht geladen.");
            }
            IItemHandler all = connector.machineInventoryAll();
            if (all == null) {
                throw new ScriptError("An " + view.device()
                        + " hängt keine Maschine mit Inventar.");
            }
            return NotifyingHandlers.items(SlotView.of(all, view.slots()),
                    noticeFor(view.device()));
        }
        if (!(value instanceof Value.Device device)) {
            throw new ScriptError("Hier wird ein Gerät erwartet, gefunden wurde "
                    + value.describe() + ".");
        }
        var connector = connectorFor(device.name());
        if (connector == null) {
            throw new ScriptError("Der Connector " + device.name() + " ist nicht erreichbar.",
                    "Vielleicht ist sein Chunk gerade nicht geladen.");
        }
        IItemHandler handler = connector.machineInventory();
        if (handler == null) {
            throw new ScriptError("An " + device.name() + " hängt keine Maschine mit Inventar.");
        }
        return NotifyingHandlers.items(handler, noticeFor(device.name()));
    }

    /**
     * The resources behind a selection, in the form of their kind.
     *
     * <p>The three resolvers stay separate, and for a reason: they say
     * different things when nothing matches. An item is missing from the
     * pack, flowing water does not count as a fluid, and a chemical may be
     * missing only because Mekanism is. All that comes together here is the
     * question of which of them a call site needs.
     */
    private List<?> keysOf(ResourceKind kind, Value value) {
        if (kind == ResourceKinds.FLUID) {
            return fluidsOf(value);
        }
        if (kind == ResourceKinds.CHEMICAL) {
            return chemicalsOf(value);
        }
        // A foreign kind resolves its selection itself — it knows its
        // registry, and the core does not. An already resolved selection, by
        // contrast, already carries its keys: that is how it arrives from a
        // loop and from every it.
        if (kind != null && kind != ResourceKinds.ITEM) {
            if (value instanceof Value.Selection selection && selection.kind() == kind) {
                return selection.keys();
            }
            if (value instanceof Value.Resource resource && resource.kind() == kind) {
                return List.of(resource.key());
            }
            List<?> found = kind.resolve(selectorFor(value));
            if (found.isEmpty()) {
                throw new ScriptError("Die Auswahl trifft kein " + kind.prefix() + ".",
                        "Gibt es das in diesem Pack?");
            }
            return found;
        }
        return itemsOf(value);
    }

    /**
     * The selector expression behind a value, for a foreign kind.
     *
     * <p>An already resolved selection already carries its keys; only written
     * text has to go through the parser once more. The same parsing as for
     * the built-in kinds, via {@code Selectors}.
     */
    private Expr selectorFor(Value value) {
        if (value instanceof Value.Request request) {
            Expr parsed = selectorCache.get(request.selector());
            if (parsed == null) {
                parsed = dev.devpanda.factorynetwork.lang.Selectors.parse(request.selector());
                if (parsed == null) {
                    throw new ScriptError("Das ist keine Auswahl: " + request.selector() + ".");
                }
                selectorCache.put(request.selector(), parsed);
            }
            return parsed;
        }
        return null;
    }

    private static boolean isStorage(Value value) {
        return value instanceof Value.Builtin builtin && "storage".equals(builtin.name());
    }

    /**
     * Resolves a selector expression to item types.
     *
     * <p>Goes through {@link ItemSelection} so that tags, patterns and
     * {@code except} mean the same here as with the worker. Previously this
     * place understood only single items and let everything else fall
     * through silently — {@code move 64 tag:c/ores} would thereby have moved
     * everything instead of only ores. A wrong result without a message is
     * the worst case.
     */
    private List<Item> itemsOf(Value value) {
        if (value instanceof Value.Resource resource
                && resource.kind() == ResourceKinds.ITEM) {
            return List.of(resource.item());
        }
        // An already resolved selection — that is how every entry from
        // storage.items() and crusher_1.items() arrives. Without this line
        // every loop over a stock failed with "from storage it must say what
        // is to be moved", even though it did say so.
        //
        // The kind is still checked: the path here is fixed in move and
        // count, but reading a fluid selection as an item list would be an
        // error that only a crash reveals.
        if (value instanceof Value.Selection selection
                && selection.kind() == ResourceKinds.ITEM) {
            return selection.items();
        }
        // "all" selects nothing: an empty list means "no filter" here, and
        // that is exactly what is meant. Further down the resolution would
        // otherwise look for a type after a colon that does not exist.
        if (isEverything(value)) {
            return List.of();
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

    /** Rebuilds a selector expression from its written form. */
    private static Expr parseSelector(String written) {
        // The parsing lives in Selectors — the editor needs the same one to
        // show what a pattern resolves to. What remains here are the
        // messages: here a program is running, there a cursor is hovering.
        if (written.indexOf(':') < 0) {
            throw new ScriptError("Das ist keine Auswahl: " + written + ".");
        }
        Expr.Selector parsed = dev.devpanda.factorynetwork.lang.Selectors.parse(written);
        if (parsed == null) {
            throw new ScriptError("Unbekannte Art in " + written + ".");
        }
        if (parsed.kind() == Expr.Selector.Kind.CHEMICAL) {
            throw new ScriptError(
                    dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.reason(),
                    dev.devpanda.factorynetwork.compat.mekanism.FnMekanism.hint());
        }
        return parsed;
    }

    /** Without a leading amount, everything available is meant. */
    /**
     * How much is to be moved — everything if unspecified.
     *
     * <p><b>Also for already resolved selections.</b> With only
     * {@link Value.Request} here, {@code move 8 sorte} in a loop yielded
     * {@code Long.MAX_VALUE} — that is, everything. That looked like a
     * program doing what it says, and emptied the warehouse.
     */
    private static long amountOf(Value value) {
        return switch (value) {
            case Value.Request request when request.hasAmount() -> request.amount();
            case Value.Selection selection when selection.amount() > 0 -> selection.amount();
            default -> Long.MAX_VALUE;
        };
    }
}
