package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Where foreign mods dock in.
 *
 * <p>A buffer alone is not enough: without this registration no cable and no
 * generator finds it, and the block sits there with an energy store that
 * nobody can put anything into. <b>That is exactly what happened with the
 * press</b> — it needed FE and could get none.
 */
@EventBusSubscriber(modid = FactoryNetwork.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FnCapabilities {

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // On all sides: which side accepts power is a question nobody wants to
        // ask when laying a cable.
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
        // <b>The press's inventory.</b> Without this line it accepts power and
        // nothing else: no connector finds an inventory, no worker can feed
        // it, and a machine that cannot be fed is, in this mod, no machine at
        // all.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                FnBlockEntities.PRESS.get(),
                (PressBlockEntity press, net.minecraft.core.Direction side)
                        -> press.inventory());

        // The battery of the remote devices.
        //
        // This is the one line that connects Powah, Flux Networks and every
        // other mod that charges items in the inventory: they all look for
        // IEnergyStorage on the ItemStack and find it here. Without it the
        // charge level would be a number nobody can fill.
        for (net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> held
                : java.util.List.of(FnItems.WIRELESS_TERMINAL, FnItems.LAPTOP)) {
            event.registerItem(Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new net.neoforged.neoforge.energy.ComponentEnergyStorage(
                            stack, FnComponents.ENERGY.get(),
                            ((dev.devpanda.factorynetwork.item.RemoteDeviceItem) stack.getItem())
                                    .capacity()),
                    held.get());
        }

        // Without a BlockEntity: the creative source is a plain block, and its
        // storage is a number without state.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side)
                        -> dev.devpanda.factorynetwork.block.CreativeSourceBlock.TAP,
                dev.devpanda.factorynetwork.registry.FnBlocks.CREATIVE_SOURCE.get());
    }

    private FnCapabilities() {
    }
}
