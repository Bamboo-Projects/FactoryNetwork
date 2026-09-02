package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.storage.CellTier;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import dev.devpanda.factorynetwork.item.NetworkAnalyserItem;
import dev.devpanda.factorynetwork.block.CableColour;
import dev.devpanda.factorynetwork.item.ColouredCableItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** The items of the mod, including the block items. */
public final class FnItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FactoryNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> CONTROLLER = ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER);

    /** Builds what the network orders. */
    public static final DeferredItem<BlockItem> FABRICATOR =
            ITEMS.registerSimpleBlockItem(FnBlocks.FABRICATOR);

    /** The extension: sides for cables, nothing else. */
    public static final DeferredItem<BlockItem> CONTROLLER_EXTENSION =
            ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER_EXTENSION);

    /** One end of a line without cable in between. */
    public static final DeferredItem<BlockItem> BRIDGE =
            ITEMS.registerSimpleBlockItem(FnBlocks.BRIDGE);

    /** The mast that one places. */
    public static final DeferredItem<BlockItem> MAST =
            ITEMS.registerSimpleBlockItem(FnBlocks.MAST);

    public static final DeferredItem<BlockItem> GATEWAY =
            ITEMS.registerSimpleBlockItem(FnBlocks.GATEWAY);
    /**
     * One cable item per colour.
     *
     * <p>Only one block, but seventeen items: the colour lives in the block
     * state, and the item decides which one it is placed with. Seventeen
     * separate blocks would be the same thing with seventeen times the
     * registration effort.
     */
    public static final Map<CableColour, DeferredItem<BlockItem>> CABLES = registerCables();

    /** The same again for the dense cable. */
    public static final Map<CableColour, DeferredItem<BlockItem>> DENSE_CABLES =
            registerCables(FnBlocks.DENSE_CABLE, "dense_cable");

    /** The default colour — connects to everything. */
    public static final DeferredItem<BlockItem> CABLE = CABLES.get(CableColour.NONE);

    public static final DeferredItem<BlockItem> DENSE_CABLE = DENSE_CABLES.get(CableColour.NONE);

    private static Map<CableColour, DeferredItem<BlockItem>> registerCables() {
        return registerCables(FnBlocks.CABLE, "cable");
    }

    /**
     * All seventeen colours of a cable kind.
     *
     * <p>The name of the neutral version is the kind name itself, the others
     * carry their colour in front — {@code cable} and {@code red_cable},
     * {@code dense_cable} and {@code red_dense_cable}.
     */
    private static Map<CableColour, DeferredItem<BlockItem>> registerCables(
            net.neoforged.neoforge.registries.DeferredBlock<
                    net.minecraft.world.level.block.Block> block, String sort) {
        Map<CableColour, DeferredItem<BlockItem>> cables = new LinkedHashMap<>();
        for (CableColour colour : CableColour.values()) {
            String name = colour == CableColour.NONE
                    ? sort : colour.getSerializedName() + "_" + sort;
            cables.put(colour, ITEMS.register(name,
                    () -> new ColouredCableItem(block.get(), colour, new Item.Properties())));
        }
        return Map.copyOf(cables);
    }
    /** Junction for thick cables. */
    public static final DeferredItem<BlockItem> ROUTER = ITEMS.registerSimpleBlockItem(FnBlocks.ROUTER);

    /**
     * The connector — an item without its own block.
     *
     * <p>It is placed on a face of a cable and not next to it; what results is
     * a part in the cable's BlockEntity. Until 26.08. there was a separate
     * block for this that could do the same and needed one more slot.
     */
    public static final DeferredItem<Item> CONNECTOR = ITEMS.register("connector",
            () -> new dev.devpanda.factorynetwork.item.ConnectorItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(FnBlocks.TERMINAL);
    public static final DeferredItem<BlockItem> DISPLAY = ITEMS.registerSimpleBlockItem(FnBlocks.DISPLAY);

    /**
     * The upgrades: one module and two cards.
     *
     * <p>They stack, because two identical cards do the same thing — and
     * because identical cards add up, one rarely has just one.
     */
    public static final DeferredItem<Item> WIRELESS_MODULE = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Ability.WIRELESS.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Ability.WIRELESS));

    public static final DeferredItem<Item> RANGE_CARD = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Card.RANGE.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Card.RANGE));

    /**
     * The cards for machines.
     *
     * <p>They sit in the same slots as the range cards and yet compute
     * differently: their step is zero, because they add nothing. What they do
     * is in {@link dev.devpanda.factorynetwork.upgrade.Tuning}.
     */
    public static final DeferredItem<Item> ACCELERATION_CARD = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Card.ACCELERATION.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Card.ACCELERATION));

    public static final DeferredItem<Item> BATCH_CARD = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Card.BATCH.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Card.BATCH));

    public static final DeferredItem<Item> INFINITY_CARD = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Card.INFINITY.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Card.INFINITY));

    /**
     * Removes a connector without touching the cable.
     *
     * <p>It is in the tag {@code c:tools/wrench} and is thus not the only one
     * that can do this — whoever has a wrench from Mekanism or Thermal on hand
     * does not need this one. It is for everyone who plays without a foreign
     * mod.
     */
    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** Assigns a connector its name. */
    public static final DeferredItem<Item> LABEL_GUN = ITEMS.register("label_gun",
            () -> new LabelGunItem(new Item.Properties().stacksTo(1)));

    /**
     * The stamps of the press.
     *
     * <p>A tool, not an ingredient: whoever has one presses with it any number
     * of times. That separates the one-off effort from the ongoing one — and
     * exactly this separation makes a chain interesting rather than merely
     * long.
     */
    public static final DeferredItem<Item> STAMP_PLATE = ITEMS.register("stamp_plate",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_LOGIC = ITEMS.register("stamp_logic",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_MEMORY = ITEMS.register("stamp_memory",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_NETWORK = ITEMS.register("stamp_network",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** The semi-finished good: pressed metal. */
    public static final DeferredItem<Item> PLATE = ITEMS.register("plate",
            () -> new Item(new Item.Properties()));

    /** The cut crystal — pressed from the raw one. */
    public static final DeferredItem<Item> CRYSTAL = ITEMS.register("crystal",
            () -> new Item(new Item.Properties()));

    /**
     * The three cores.
     *
     * <p>They split the expansion into directions: whoever expands storage
     * needs different cores than whoever expands the network. Without this
     * separation the chain would just be a longer path to the same goal.
     */
    public static final DeferredItem<Item> CORE_LOGIC = ITEMS.register("core_logic",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CORE_MEMORY = ITEMS.register("core_memory",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CORE_NETWORK = ITEMS.register("core_network",
            () -> new Item(new Item.Properties()));

    /** The press itself. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PRESS =
            ITEMS.registerSimpleBlockItem(FnBlocks.PRESS);

    /** The ore and its yield. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRYSTAL_ORE =
            ITEMS.registerSimpleBlockItem(FnBlocks.CRYSTAL_ORE);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> DEEPSLATE_CRYSTAL_ORE =
            ITEMS.registerSimpleBlockItem(FnBlocks.DEEPSLATE_CRYSTAL_ORE);

    /**
     * The raw crystal, as it comes out of the stone.
     *
     * <p>Useless on its own — only the press makes something of it. It is the
     * first stage of the chain and the reason to go digging at all.
     */
    public static final DeferredItem<Item> RAW_CRYSTAL = ITEMS.register("raw_crystal",
            () -> new Item(new Item.Properties()));

    /** The storage room: a block that takes cells. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRIVE =
            ITEMS.registerSimpleBlockItem(FnBlocks.DRIVE);

    /**
     * The four cell sizes.
     *
     * <p>Each has two limits: how many kinds and how many items. The kinds are
     * the scarce one — whoever throws everything into one cell fills it long
     * before the quantity is reached.
     */
    public static final Map<CellTier, DeferredItem<Item>> CELLS = registerCells();

    private static Map<CellTier, DeferredItem<Item>> registerCells() {
        Map<CellTier, DeferredItem<Item>> cells = new LinkedHashMap<>();
        for (CellTier tier : CellTier.values()) {
            cells.put(tier, ITEMS.register("cell_" + tier.getSerializedName(),
                    () -> new StorageCellItem(tier, new Item.Properties())));
        }
        return Map.copyOf(cells);
    }

    /**
     * The four sizes of the fluid cell.
     *
     * <p>Fewer kinds and more capacity than the item cells: fluids come in
     * fewer kinds and larger amounts. The number in the name is buckets.
     */
    public static final Map<dev.devpanda.factorynetwork.storage.FluidCellTier,
            DeferredItem<Item>> FLUID_CELLS = registerFluidCells();

    private static Map<dev.devpanda.factorynetwork.storage.FluidCellTier,
            DeferredItem<Item>> registerFluidCells() {
        Map<dev.devpanda.factorynetwork.storage.FluidCellTier, DeferredItem<Item>> cells =
                new LinkedHashMap<>();
        for (var tier : dev.devpanda.factorynetwork.storage.FluidCellTier.values()) {
            cells.put(tier, ITEMS.register("fluid_cell_" + tier.getSerializedName(),
                    () -> new dev.devpanda.factorynetwork.storage.FluidCellItem(
                            tier, new Item.Properties())));
        }
        return Map.copyOf(cells);
    }

    /**
     * The four sizes of the chemical cell.
     *
     * <p><b>Always registered, even without Mekanism.</b> To register
     * conditionally would mean: whoever removes the mod loses their cells from
     * the world — contents and all, because an unknown item disappears on
     * load. An item that always exists and whose tooltip says what it is
     * missing is the friendlier answer.
     */
    public static final Map<dev.devpanda.factorynetwork.storage.ChemicalCellTier,
            DeferredItem<Item>> CHEMICAL_CELLS = registerChemicalCells();

    private static Map<dev.devpanda.factorynetwork.storage.ChemicalCellTier,
            DeferredItem<Item>> registerChemicalCells() {
        Map<dev.devpanda.factorynetwork.storage.ChemicalCellTier, DeferredItem<Item>> cells =
                new LinkedHashMap<>();
        for (var tier : dev.devpanda.factorynetwork.storage.ChemicalCellTier.values()) {
            cells.put(tier, ITEMS.register("chemical_cell_" + tier.getSerializedName(),
                    () -> new dev.devpanda.factorynetwork.storage.ChemicalCellItem(
                            tier, new Item.Properties())));
        }
        return Map.copyOf(cells);
    }

    /**
     * The four sizes of the energy cell.
     *
     * <p>Only one number per tier, because power has no kinds. The smallest
     * already holds more than the buffer in the controller — whoever inserts
     * one sees the difference.
     */
    public static final Map<dev.devpanda.factorynetwork.storage.EnergyCellTier,
            DeferredItem<Item>> ENERGY_CELLS = registerEnergyCells();

    private static Map<dev.devpanda.factorynetwork.storage.EnergyCellTier,
            DeferredItem<Item>> registerEnergyCells() {
        Map<dev.devpanda.factorynetwork.storage.EnergyCellTier, DeferredItem<Item>> cells =
                new LinkedHashMap<>();
        for (var tier : dev.devpanda.factorynetwork.storage.EnergyCellTier.values()) {
            cells.put(tier, ITEMS.register("energy_cell_" + tier.getSerializedName(),
                    () -> new dev.devpanda.factorynetwork.storage.EnergyCellItem(
                            tier, new Item.Properties())));
        }
        return Map.copyOf(cells);
    }

    /** Takes servers — without it the network does not compute. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RACK =
            ITEMS.registerSimpleBlockItem(FnBlocks.RACK);

    /**
     * The server chassis: a sheet of metal with three slots.
     *
     * <p>On its own it can do nothing, and that is the point. It makes three
     * components into a server that one can pull out and plug in elsewhere.
     */
    public static final DeferredItem<Item> SERVER_CHASSIS = ITEMS.register("server_chassis",
            () -> new dev.devpanda.factorynetwork.item.ServerChassisItem(new Item.Properties()));

    /**
     * The server components, by kind and tier.
     *
     * <p>Twelve items from two loops. Written by hand it would be twelve
     * nearly identical lines, and the thirteenth would eventually sit below
     * with a transposed number.
     */
    public static final Map<dev.devpanda.factorynetwork.item.ServerPart,
            List<DeferredItem<Item>>> SERVER_PARTS = registerServerParts();

    private static Map<dev.devpanda.factorynetwork.item.ServerPart,
            List<DeferredItem<Item>>> registerServerParts() {
        var all = new java.util.EnumMap<dev.devpanda.factorynetwork.item.ServerPart,
                List<DeferredItem<Item>>>(dev.devpanda.factorynetwork.item.ServerPart.class);
        for (var part : dev.devpanda.factorynetwork.item.ServerPart.values()) {
            List<DeferredItem<Item>> tiers = new java.util.ArrayList<>();
            for (int value : part.tiers()) {
                tiers.add(ITEMS.register(part.prefix() + "_" + value,
                        () -> new dev.devpanda.factorynetwork.item.ServerPartItem(
                                part, value, new Item.Properties())));
            }
            all.put(part, List.copyOf(tiers));
        }
        return java.util.Collections.unmodifiableMap(all);
    }

    /** Power from furnace fuel — deliberately mediocre. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER =
            ITEMS.registerSimpleBlockItem(FnBlocks.BURNER);

    /** Power without fuel — just for trying out. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CREATIVE_SOURCE =
            ITEMS.registerSimpleBlockItem(FnBlocks.CREATIVE_SOURCE);

    /** Shows the network as a scaffold in the world — even through walls. */
    /**
     * The wireless terminal: the storage from afar, but without code.
     *
     * <p>The battery is smaller than the laptop's — it has less to do, and the
     * two should differ in that too.
     */
    public static final DeferredItem<Item> WIRELESS_TERMINAL =
            ITEMS.register("wireless_terminal",
                    () -> new dev.devpanda.factorynetwork.item.RemoteDeviceItem(
                            new Item.Properties(),
                            dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL,
                            200_000));

    /** The laptop: the same plus the code. */
    public static final DeferredItem<Item> LAPTOP =
            ITEMS.register("laptop",
                    () -> new dev.devpanda.factorynetwork.item.RemoteDeviceItem(
                            new Item.Properties(),
                            dev.devpanda.factorynetwork.upgrade.RemoteDevice.LAPTOP,
                            600_000));

    /** One half of an entanglement — it belongs in a quantum bridge. */
    public static final DeferredItem<Item> ENTANGLEMENT =
            ITEMS.register("entanglement",
                    () -> new dev.devpanda.factorynetwork.item.EntanglementItem(
                            new Item.Properties()));

    public static final DeferredItem<Item> ANALYSER = ITEMS.register("network_analyser",
            () -> new NetworkAnalyserItem(new Item.Properties().stacksTo(1)));

    private FnItems() {
    }
}
