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
                        // Alle Kabelfarben, Standardfarbe zuerst
                        FnItems.CABLES.values()
                                .forEach(cable -> output.accept(cable.get()));
                        output.accept(FnItems.CONNECTOR.get());
                        output.accept(FnItems.TERMINAL.get());
                        output.accept(FnItems.DISPLAY.get());
                        output.accept(FnItems.LABEL_GUN.get());
                        output.accept(FnItems.ANALYSER.get());
                        output.accept(FnItems.DRIVE.get());
                        output.accept(FnItems.CRYSTAL_ORE.get());
                        output.accept(FnItems.DEEPSLATE_CRYSTAL_ORE.get());
                        output.accept(FnItems.RAW_CRYSTAL.get());
                        output.accept(FnItems.CRYSTAL.get());
                        output.accept(FnItems.PRESS.get());
                        output.accept(FnItems.STAMP_PLATE.get());
                        output.accept(FnItems.STAMP_LOGIC.get());
                        output.accept(FnItems.STAMP_MEMORY.get());
                        output.accept(FnItems.STAMP_NETWORK.get());
                        output.accept(FnItems.PLATE.get());
                        output.accept(FnItems.CORE_LOGIC.get());
                        output.accept(FnItems.CORE_MEMORY.get());
                        output.accept(FnItems.CORE_NETWORK.get());
                        FnItems.CELLS.values().forEach(cell -> output.accept(cell.get()));
                    })
                    .build());

    private FnCreativeTabs() {
    }
}
