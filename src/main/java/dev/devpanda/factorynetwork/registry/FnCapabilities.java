package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Wo Fremdmods andocken.
 *
 * <p>Ein Puffer allein reicht nicht: Ohne diese Anmeldung findet ihn kein
 * Kabel und kein Generator, und der Block steht mit einem Stromspeicher da,
 * in den niemand etwas hineingeben kann. <b>Genau das war bei der Presse der
 * Fall</b> — sie brauchte FE und konnte keines bekommen.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FnCapabilities {

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // Auf allen Seiten: Welche Seite Strom annimmt, ist eine Frage, die
        // niemand stellen will, wenn er ein Kabel anlegt.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                FnBlockEntities.PRESS.get(),
                (PressBlockEntity press, net.minecraft.core.Direction side) -> press.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                FnBlockEntities.BURNER.get(),
                (dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity burner,
                        net.minecraft.core.Direction side) -> burner.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                FnBlockEntities.CONTROLLER.get(),
                (ControllerBlockEntity controller, net.minecraft.core.Direction side)
                        -> controller.power().port());
        // Der Akku der Ferngeräte.
        //
        // Das ist die eine Zeile, die Powah, Flux Networks und jede andere
        // Mod anschließt, die Gegenstände im Inventar lädt: Sie alle suchen
        // IEnergyStorage am ItemStack und finden ihn hier. Ohne sie wäre der
        // Ladestand eine Zahl, die niemand füllen kann.
        for (net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> held
                : java.util.List.of(FnItems.WIRELESS_TERMINAL, FnItems.LAPTOP)) {
            event.registerItem(Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new net.neoforged.neoforge.energy.ComponentEnergyStorage(
                            stack, FnComponents.ENERGY.get(),
                            ((dev.devpanda.factorynetwork.item.RemoteDeviceItem) stack.getItem())
                                    .capacity()),
                    held.get());
        }

        // Ohne BlockEntity: Die Kreativquelle ist ein schlichter Block, und
        // ihr Speicher ist eine Zahl ohne Zustand.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side)
                        -> dev.devpanda.factorynetwork.block.CreativeSourceBlock.TAP,
                dev.devpanda.factorynetwork.registry.FnBlocks.CREATIVE_SOURCE.get());
    }

    private FnCapabilities() {
    }
}
