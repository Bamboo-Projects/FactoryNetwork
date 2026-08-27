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

/** Die Gegenstände der Mod, samt der Blockgegenstände. */
public final class FnItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FactoryNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> CONTROLLER = ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER);

    /** Baut, was das Netz bestellt. */
    public static final DeferredItem<BlockItem> FABRICATOR =
            ITEMS.registerSimpleBlockItem(FnBlocks.FABRICATOR);

    /** Der Anbau: Seiten für Kabel, sonst nichts. */
    public static final DeferredItem<BlockItem> CONTROLLER_EXTENSION =
            ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER_EXTENSION);

    /** Der Sendemast, den man hinstellt. */
    public static final DeferredItem<BlockItem> MAST =
            ITEMS.registerSimpleBlockItem(FnBlocks.MAST);

    public static final DeferredItem<BlockItem> GATEWAY =
            ITEMS.registerSimpleBlockItem(FnBlocks.GATEWAY);
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
    /** Kreuzung für dicke Kabel. */
    public static final DeferredItem<BlockItem> ROUTER = ITEMS.registerSimpleBlockItem(FnBlocks.ROUTER);

    /**
     * Der Anschluss — ein Gegenstand ohne eigenen Block.
     *
     * <p>Er wird an eine Fläche eines Kabels gesetzt und nicht daneben; was
     * dabei entsteht, ist ein Teil in der BlockEntity des Kabels. Bis zum
     * 26.08. gab es dazu einen eigenen Block, der dasselbe konnte und einen
     * Platz mehr brauchte.
     */
    public static final DeferredItem<Item> CONNECTOR = ITEMS.register("connector",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(FnBlocks.TERMINAL);
    public static final DeferredItem<BlockItem> DISPLAY = ITEMS.registerSimpleBlockItem(FnBlocks.DISPLAY);

    /**
     * Die Ausbauten: ein Modul und zwei Karten.
     *
     * <p>Sie stapeln sich, weil zwei gleiche Karten dasselbe tun — und weil
     * gleiche Karten sich addieren, hat man selten nur eine.
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

    public static final DeferredItem<Item> INFINITY_CARD = ITEMS.register(
            dev.devpanda.factorynetwork.upgrade.Card.INFINITY.id(),
            () -> new dev.devpanda.factorynetwork.item.UpgradeItem(
                    new Item.Properties(),
                    dev.devpanda.factorynetwork.upgrade.Card.INFINITY));

    /**
     * Nimmt einen Anschluss ab, ohne das Kabel anzufassen.
     *
     * <p>Er steht im Tag {@code c:tools/wrench} und ist damit nicht der
     * einzige, der das kann — wer einen Schlüssel von Mekanism oder Thermal
     * dabeihat, braucht diesen hier nicht. Er ist für alle, die ohne
     * Fremdmod spielen.
     */
    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench",
            () -> new Item(new Item.Properties().stacksTo(1)));

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

    /**
     * Die vier Größen der Flüssigkeitszelle.
     *
     * <p>Weniger Sorten und mehr Menge als bei den Gegenstandszellen:
     * Flüssigkeiten gibt es in weniger Sorten und größeren Mengen. Die Zahl
     * im Namen sind Eimer.
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
     * Die vier Größen der Chemikalienzelle.
     *
     * <p><b>Immer registriert, auch ohne Mekanism.</b> Bedingt zu
     * registrieren hieße: Wer die Mod entfernt, verliert seine Zellen aus der
     * Welt — samt Inhalt, denn ein unbekannter Gegenstand verschwindet beim
     * Laden. Ein Gegenstand, den es immer gibt und dessen Tooltip sagt, was
     * ihm fehlt, ist die freundlichere Antwort.
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
     * Die vier Größen der Energiezelle.
     *
     * <p>Nur eine Zahl je Stufe, denn Strom hat keine Sorten. Die kleinste
     * trägt schon mehr als der Puffer im Controller — wer eine einsetzt, sieht
     * den Unterschied.
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

    /** Nimmt Server auf — ohne ihn rechnet das Netz nicht. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RACK =
            ITEMS.registerSimpleBlockItem(FnBlocks.RACK);

    /**
     * Das Servergehäuse: ein Blech mit drei Steckplätzen.
     *
     * <p>Für sich genommen kann es nichts, und das ist der Punkt. Es macht
     * aus drei Bauteilen einen Server, den man herausziehen und woanders
     * hineinstecken kann.
     */
    public static final DeferredItem<Item> SERVER_CHASSIS = ITEMS.register("server_chassis",
            () -> new dev.devpanda.factorynetwork.item.ServerChassisItem(new Item.Properties()));

    /**
     * Die Serverbauteile, nach Art und Stufe.
     *
     * <p>Zwölf Gegenstände aus zwei Schleifen. Von Hand geschrieben wären es
     * zwölf fast gleiche Zeilen, und die dreizehnte stünde irgendwann mit
     * einem Zahlendreher darunter.
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

    /** Strom aus Ofenbrennstoff — absichtlich mittelmäßig. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BURNER =
            ITEMS.registerSimpleBlockItem(FnBlocks.BURNER);

    /** Strom ohne Brennstoff — nur zum Ausprobieren. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CREATIVE_SOURCE =
            ITEMS.registerSimpleBlockItem(FnBlocks.CREATIVE_SOURCE);

    /** Zeigt das Netz als Gerüst in der Welt — auch durch Wände. */
    /**
     * Das Wireless Terminal: das Lager aus der Ferne, aber ohne Code.
     *
     * <p>Der Akku ist kleiner als der des Laptops — er hat weniger zu tun,
     * und die beiden sollen sich auch darin unterscheiden.
     */
    public static final DeferredItem<Item> WIRELESS_TERMINAL =
            ITEMS.register("wireless_terminal",
                    () -> new dev.devpanda.factorynetwork.item.RemoteDeviceItem(
                            new Item.Properties(),
                            dev.devpanda.factorynetwork.upgrade.RemoteDevice.TERMINAL,
                            200_000));

    /** Der Laptop: dasselbe und dazu der Code. */
    public static final DeferredItem<Item> LAPTOP =
            ITEMS.register("laptop",
                    () -> new dev.devpanda.factorynetwork.item.RemoteDeviceItem(
                            new Item.Properties(),
                            dev.devpanda.factorynetwork.upgrade.RemoteDevice.LAPTOP,
                            600_000));

    public static final DeferredItem<Item> ANALYSER = ITEMS.register("network_analyser",
            () -> new NetworkAnalyserItem(new Item.Properties().stacksTo(1)));

    private FnItems() {
    }
}
