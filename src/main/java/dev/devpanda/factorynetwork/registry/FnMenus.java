package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.client.menu.PressMenu;
import dev.devpanda.factorynetwork.client.menu.TerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FnMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, FactoryNetwork.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<PressMenu>> PRESS =
            MENUS.register("press", () -> net.neoforged.neoforge.common.extensions
                    .IMenuTypeExtension.create(PressMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL =
            MENUS.register("terminal",
                    () -> IMenuTypeExtension.create(TerminalMenu::new));

    private FnMenus() {
    }
}
