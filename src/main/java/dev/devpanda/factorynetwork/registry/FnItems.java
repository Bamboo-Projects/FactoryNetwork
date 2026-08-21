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

import java.util.LinkedHashMap;
import java.util.Map;

/** Die Gegenstände der Mod, samt der Blockgegenstände. */
public final class FnItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FactoryNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> CONTROLLER = ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER);
    /**
     * Ein Kabelgegenstand je Farbe.
     *
     * <p>Nur ein Block, aber siebzehn Gegenstände: Die Farbe steht im
     * Blockzustand, und der Gegenstand entscheidet, mit welcher gesetzt wird.
     * Siebzehn eigene Blöcke wären dieselbe Sache mit siebzehnfachem
     * Registrierungsaufwand.
     */
    public static final Map<CableColour, DeferredItem<BlockItem>> CABLES = registerCables();

    /** Dasselbe noch einmal für das dichte Kabel. */
    public static final Map<CableColour, DeferredItem<BlockItem>> DENSE_CABLES =
            registerCables(FnBlocks.DENSE_CABLE, "dense_cable");

    /** Die Standardfarbe — verbindet sich mit allem. */
    public static final DeferredItem<BlockItem> CABLE = CABLES.get(CableColour.NONE);

    public static final DeferredItem<BlockItem> DENSE_CABLE = DENSE_CABLES.get(CableColour.NONE);

    private static Map<CableColour, DeferredItem<BlockItem>> registerCables() {
        return registerCables(FnBlocks.CABLE, "cable");
    }

    /**
     * Alle siebzehn Farben einer Kabelsorte.
     *
     * <p>Der Name der neutralen Fassung ist der Sortenname selbst, die
     * anderen tragen ihre Farbe davor — {@code cable} und {@code red_cable},
     * {@code dense_cable} und {@code red_dense_cable}.
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
    public static final DeferredItem<BlockItem> CONNECTOR = ITEMS.registerSimpleBlockItem(FnBlocks.CONNECTOR);
    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(FnBlocks.TERMINAL);
    public static final DeferredItem<BlockItem> DISPLAY = ITEMS.registerSimpleBlockItem(FnBlocks.DISPLAY);

    /** Vergibt einem Connector seinen Namen. */
    public static final DeferredItem<Item> LABEL_GUN = ITEMS.register("label_gun",
            () -> new LabelGunItem(new Item.Properties().stacksTo(1)));

    /**
     * Die Stempel der Presse.
     *
     * <p>Werkzeug, keine Zutat: Wer einen hat, presst damit beliebig oft. Das
     * trennt den einmaligen Aufwand vom laufenden — und genau diese Trennung
     * macht eine Kette interessant statt nur lang.
     */
    public static final DeferredItem<Item> STAMP_PLATE = ITEMS.register("stamp_plate",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_LOGIC = ITEMS.register("stamp_logic",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_MEMORY = ITEMS.register("stamp_memory",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STAMP_NETWORK = ITEMS.register("stamp_network",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** Das Halbzeug: gepresstes Metall. */
    public static final DeferredItem<Item> PLATE = ITEMS.register("plate",
            () -> new Item(new Item.Properties()));

    /** Der geschliffene Kristall — aus dem rohen gepresst. */
    public static final DeferredItem<Item> CRYSTAL = ITEMS.register("crystal",
            () -> new Item(new Item.Properties()));

    /**
     * Die drei Kerne.
     *
     * <p>Sie trennen den Ausbau in Richtungen: Wer Speicher ausbaut, braucht
     * andere Kerne als wer das Netz ausbaut. Ohne diese Trennung wäre die
     * Kette nur ein längerer Weg zum selben Ziel.
     */
    public static final DeferredItem<Item> CORE_LOGIC = ITEMS.register("core_logic",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CORE_MEMORY = ITEMS.register("core_memory",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CORE_NETWORK = ITEMS.register("core_network",
            () -> new Item(new Item.Properties()));

    /** Die Presse selbst. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PRESS =
            ITEMS.registerSimpleBlockItem(FnBlocks.PRESS);

    /** Das Erz und sein Ertrag. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRYSTAL_ORE =
            ITEMS.registerSimpleBlockItem(FnBlocks.CRYSTAL_ORE);

    public static final DeferredItem<net.minecraft.world.item.BlockItem> DEEPSLATE_CRYSTAL_ORE =
            ITEMS.registerSimpleBlockItem(FnBlocks.DEEPSLATE_CRYSTAL_ORE);

    /**
     * Der rohe Kristall, wie er aus dem Stein kommt.
     *
     * <p>Für sich genommen nutzlos — erst die Presse macht daraus etwas. Das
     * ist die erste Stufe der Kette und der Grund, überhaupt graben zu gehen.
     */
    public static final DeferredItem<Item> RAW_CRYSTAL = ITEMS.register("raw_crystal",
            () -> new Item(new Item.Properties()));

    /** Der Lagerraum: ein Block, der Zellen aufnimmt. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DRIVE =
            ITEMS.registerSimpleBlockItem(FnBlocks.DRIVE);

    /**
     * Die vier Zellengrößen.
     *
     * <p>Jede hat zwei Grenzen: wie viele Arten und wie viele Gegenstände.
     * Die Arten sind das Knappe — wer alles in eine Zelle wirft, hat sie voll,
     * lange bevor die Menge erreicht ist.
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

    /** Zeigt das Netz als Gerüst in der Welt — auch durch Wände. */
    public static final DeferredItem<Item> ANALYSER = ITEMS.register("network_analyser",
            () -> new NetworkAnalyserItem(new Item.Properties().stacksTo(1)));

    private FnItems() {
    }
}
