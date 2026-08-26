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
            BLOCK_ENTITIES.register("controller", () -> BlockEntityType.Builder
                    .of(ControllerBlockEntity::new, FnBlocks.CONTROLLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity>>
            GATEWAY = BLOCK_ENTITIES.register("gateway", () -> BlockEntityType.Builder
                    .of(dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.GATEWAY.get())
                    .build(null));

    /**
     * Der Kabelblock, der Anschlüsse trägt.
     *
     * <p>Beide Kabelarten teilen ihn: Ein dichtes Kabel trägt dieselben
     * Teile wie ein gewöhnliches, nur mehr Kanäle.
     */
    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity>>
            CABLE_BUS = BLOCK_ENTITIES.register("cable_bus", () -> BlockEntityType.Builder
                    .of(dev.devpanda.factorynetwork.block.entity.CableBusBlockEntity::new,
                            dev.devpanda.factorynetwork.registry.FnBlocks.CABLE.get(),
                            dev.devpanda.factorynetwork.registry.FnBlocks.DENSE_CABLE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressBlockEntity>> PRESS =
            BLOCK_ENTITIES.register("press", () -> BlockEntityType.Builder
                    .of(PressBlockEntity::new, FnBlocks.PRESS.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveBlockEntity>> DRIVE =
            BLOCK_ENTITIES.register("drive", () -> BlockEntityType.Builder
                    .of(DriveBlockEntity::new, FnBlocks.DRIVE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RouterBlockEntity>> ROUTER =
            BLOCK_ENTITIES.register("router", () -> BlockEntityType.Builder
                    .of(RouterBlockEntity::new, FnBlocks.ROUTER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerBlockEntity>>
            BURNER = BLOCK_ENTITIES.register("burner", () -> BlockEntityType.Builder
                    .of(BurnerBlockEntity::new, FnBlocks.BURNER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RackBlockEntity>> RACK =
            BLOCK_ENTITIES.register("server_rack", () -> BlockEntityType.Builder
                    .of(RackBlockEntity::new, FnBlocks.RACK.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayBlockEntity>> DISPLAY =
            BLOCK_ENTITIES.register("display", () -> BlockEntityType.Builder
                    .of(DisplayBlockEntity::new, FnBlocks.DISPLAY.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITIES.register("terminal", () -> BlockEntityType.Builder
                    .of(TerminalBlockEntity::new, FnBlocks.TERMINAL.get())
                    .build(null));

    private FnBlockEntities() {
    }
}
