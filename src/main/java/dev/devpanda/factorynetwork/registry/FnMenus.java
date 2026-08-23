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

    public static final DeferredHolder<MenuType<?>, MenuType<
            dev.devpanda.factorynetwork.client.menu.BurnerMenu>> BURNER =
            MENUS.register("burner", () -> IMenuTypeExtension.create(
                    dev.devpanda.factorynetwork.client.menu.BurnerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<
            dev.devpanda.factorynetwork.client.menu.RouterMenu>> ROUTER =
            MENUS.register("router", () -> IMenuTypeExtension.create(
                    dev.devpanda.factorynetwork.client.menu.RouterMenu::new));

    /** Ein Fenster für Laufwerk und Serverschrank — beide sind ein Regal. */
    public static final DeferredHolder<MenuType<?>, MenuType<
            dev.devpanda.factorynetwork.client.menu.ShelfMenu>> SHELF =
            MENUS.register("shelf", () -> IMenuTypeExtension.create(
                    dev.devpanda.factorynetwork.client.menu.ShelfMenu::new));

    /**
     * Ein Fenster für Connector und Anzeige: beide bekommen einen Namen.
     *
     * <p>Bisher ging das nur mit der Beschriftungspistole. Ohne gebaute
     * Pistole ließ sich kein Gerät ansprechen — und der Rechtsklick sagte
     * einem nur den Namen, den man ohnehin sah.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<
            dev.devpanda.factorynetwork.client.menu.NameMenu>> NAME =
            MENUS.register("name", () -> IMenuTypeExtension.create(
                    dev.devpanda.factorynetwork.client.menu.NameMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL =
            MENUS.register("terminal",
                    () -> IMenuTypeExtension.create(TerminalMenu::new));

    private FnMenus() {
    }
}
