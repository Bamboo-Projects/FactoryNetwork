package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FnCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactoryNetwork.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + FactoryNetwork.MOD_ID))
                    .icon(() -> new ItemStack(FnItems.CONTROLLER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(FnItems.CONTROLLER.get());
                        output.accept(FnItems.CONTROLLER_EXTENSION.get());
                        output.accept(FnItems.GATEWAY.get());
                        output.accept(FnItems.FABRICATOR.get());
                        // All cable colours, default colour first, and the
                        // router behind it — it picks up colours from them.
                        //
                        // <b>The dense cable has been missing here since 30.08.</b>
                        // It carries the same as the ordinary one and is thus a
                        // variant without difference. The block stays
                        // registered, because NeoForge 21.1 has no way to
                        // rewrite a deleted id in an existing world — there
                        // would otherwise be air.
                        FnItems.CABLES.values()
                                .forEach(cable -> output.accept(cable.get()));
                        output.accept(FnItems.ROUTER.get());
                        output.accept(FnItems.MAST.get());
                        output.accept(FnItems.BRIDGE.get());
                        output.accept(FnItems.CONNECTOR.get());
                        output.accept(FnItems.TERMINAL.get());
                        output.accept(FnItems.WIRELESS_TERMINAL.get());
                        output.accept(FnItems.LAPTOP.get());
                        output.accept(FnItems.ENTANGLEMENT.get());
                        output.accept(FnItems.DISPLAY.get());
                        output.accept(FnItems.WRENCH.get());
                        output.accept(FnItems.LABEL_GUN.get());
                        output.accept(FnItems.ANALYSER.get());
                        // The upgrades: first the module, then the cards from
                        // small to large — the same order as in the recipe
                        // tree.
                        output.accept(FnItems.WIRELESS_MODULE.get());
                        output.accept(FnItems.RANGE_CARD.get());
                        output.accept(FnItems.INFINITY_CARD.get());
                        output.accept(FnItems.ACCELERATION_CARD.get());
                        output.accept(FnItems.BATCH_CARD.get());
                        output.accept(FnItems.DRIVE.get());
                        output.accept(FnItems.RACK.get());
                        output.accept(FnItems.SERVER_CHASSIS.get());
                        // First all compute units, then all memory, then all
                        // drives — sorted by kind and within that by tier, the
                        // way one installs them.
                        FnItems.SERVER_PARTS.values().forEach(tiers ->
                                tiers.forEach(part -> output.accept(part.get())));
                        output.accept(FnItems.CREATIVE_SOURCE.get());
                        output.accept(FnItems.CRYSTAL_ORE.get());
                        output.accept(FnItems.DEEPSLATE_CRYSTAL_ORE.get());
                        output.accept(FnItems.RAW_CRYSTAL.get());
                        output.accept(FnItems.CRYSTAL.get());
                        output.accept(FnItems.PRESS.get());
                        output.accept(FnItems.BURNER.get());
                        output.accept(FnItems.STAMP_PLATE.get());
                        output.accept(FnItems.STAMP_LOGIC.get());
                        output.accept(FnItems.STAMP_MEMORY.get());
                        output.accept(FnItems.STAMP_NETWORK.get());
                        output.accept(FnItems.PLATE.get());
                        output.accept(FnItems.CORE_LOGIC.get());
                        output.accept(FnItems.CORE_MEMORY.get());
                        output.accept(FnItems.CORE_NETWORK.get());
                        FnItems.CELLS.values().forEach(cell -> output.accept(cell.get()));
                        FnItems.FLUID_CELLS.values()
                                .forEach(cell -> output.accept(cell.get()));
                        FnItems.CHEMICAL_CELLS.values()
                                .forEach(cell -> output.accept(cell.get()));
                        FnItems.ENERGY_CELLS.values()
                                .forEach(cell -> output.accept(cell.get()));
                    })
                    .build());

    private FnCreativeTabs() {
    }
}
