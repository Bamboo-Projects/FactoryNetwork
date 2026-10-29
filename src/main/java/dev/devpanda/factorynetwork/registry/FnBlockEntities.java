package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.block.entity.RouterBlockEntity;
import dev.devpanda.factorynetwork.block.entity.TerminalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public final class FnBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryNetwork.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> CONTROLLER =
            BLOCK_ENTITIES.register("controller", () -> new BlockEntityType<>(ControllerBlockEntity::new, FnBlocks.CONTROLLER.get()));

    /** The mast holds four slots and nothing else. */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.MastBlockEntity>>
            MAST = BLOCK_ENTITIES.register("mast", () -> new BlockEntityType<>(dev.devpanda.factorynetwork.block.entity.MastBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.MAST.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity>>
            BRIDGE = BLOCK_ENTITIES.register("bridge", () -> new BlockEntityType<>(dev.devpanda.factorynetwork.block.entity.BridgeBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.BRIDGE.get()));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity>>
            GATEWAY = BLOCK_ENTITIES.register("gateway", () -> new BlockEntityType<>(dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.GATEWAY.get()));

    /**
     * The cable block that carries connectors.
     *
     * <p>Both cable kinds share it: a dense cable carries the same parts as an
     * ordinary one, just more channels.
     */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity>>
            CABLE_BUS = BLOCK_ENTITIES.register("cable_bus", () -> new BlockEntityType<>(dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.CABLE.get(),
                            dev.devpanda.factorynetwork.registry.FnBlocks.DENSE_CABLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressBlockEntity>> PRESS =
            BLOCK_ENTITIES.register("press", () -> new BlockEntityType<>(PressBlockEntity::new, FnBlocks.PRESS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveBlockEntity>> DRIVE =
            BLOCK_ENTITIES.register("drive", () -> new BlockEntityType<>(DriveBlockEntity::new, FnBlocks.DRIVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER =
            BLOCK_ENTITIES.register("router", () -> new BlockEntityType<>(RouterBlockEntity::new, FnBlocks.ROUTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerBlockEntity>>
            BURNER = BLOCK_ENTITIES.register("burner", () -> new BlockEntityType<>(BurnerBlockEntity::new, FnBlocks.BURNER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RackBlockEntity>> RACK =
            BLOCK_ENTITIES.register("server_rack", () -> new BlockEntityType<>(RackBlockEntity::new, FnBlocks.RACK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayBlockEntity>> DISPLAY =
            BLOCK_ENTITIES.register("display", () -> new BlockEntityType<>(DisplayBlockEntity::new, FnBlocks.DISPLAY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITIES.register("terminal", () -> new BlockEntityType<>(TerminalBlockEntity::new, FnBlocks.TERMINAL.get()));

    private FnBlockEntities() {
    }
}
