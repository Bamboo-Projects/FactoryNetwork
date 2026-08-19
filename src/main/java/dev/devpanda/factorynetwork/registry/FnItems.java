package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.item.LabelGunItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Die Gegenstände der Mod, samt der Blockgegenstände. */
public final class FnItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FactoryNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> CONTROLLER = ITEMS.registerSimpleBlockItem(FnBlocks.CONTROLLER);
    public static final DeferredItem<BlockItem> CABLE = ITEMS.registerSimpleBlockItem(FnBlocks.CABLE);
    public static final DeferredItem<BlockItem> CONNECTOR = ITEMS.registerSimpleBlockItem(FnBlocks.CONNECTOR);
    public static final DeferredItem<BlockItem> TERMINAL = ITEMS.registerSimpleBlockItem(FnBlocks.TERMINAL);

    /** Vergibt einem Connector seinen Namen. */
    public static final DeferredItem<Item> LABEL_GUN = ITEMS.register("label_gun",
            () -> new LabelGunItem(new Item.Properties().stacksTo(1)));

    private FnItems() {
    }
}
