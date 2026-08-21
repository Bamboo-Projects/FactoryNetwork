package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
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

    /** Die Standardfarbe — verbindet sich mit allem. */
    public static final DeferredItem<BlockItem> CABLE = CABLES.get(CableColour.NONE);

    private static Map<CableColour, DeferredItem<BlockItem>> registerCables() {
        Map<CableColour, DeferredItem<BlockItem>> cables = new LinkedHashMap<>();
        for (CableColour colour : CableColour.values()) {
            String name = colour == CableColour.NONE
                    ? "cable" : colour.getSerializedName() + "_cable";
            cables.put(colour, ITEMS.register(name,
                    () -> new ColouredCableItem(FnBlocks.CABLE.get(), colour,
                            new Item.Properties())));
        }
        return Map.copyOf(cables);
    }
    public static final DeferredItem<BlockItem> CONNECTOR = ITEMS.registerSimpleBlockItem(FnBlocks.CONNECTOR);
    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(FnBlocks.TERMINAL);
    public static final DeferredItem<BlockItem> DISPLAY = ITEMS.registerSimpleBlockItem(FnBlocks.DISPLAY);

    /** Vergibt einem Connector seinen Namen. */
    public static final DeferredItem<Item> LABEL_GUN = ITEMS.register("label_gun",
            () -> new LabelGunItem(new Item.Properties().stacksTo(1)));

    /** Zeigt das Netz als Gerüst in der Welt — auch durch Wände. */
    public static final DeferredItem<Item> ANALYSER = ITEMS.register("network_analyser",
            () -> new NetworkAnalyserItem(new Item.Properties().stacksTo(1)));

    private FnItems() {
    }
}
