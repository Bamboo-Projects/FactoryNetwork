package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConnectorBlockEntity>> CONNECTOR =
            BLOCK_ENTITIES.register("connector", () -> BlockEntityType.Builder
                    .of(ConnectorBlockEntity::new, FnBlocks.CONNECTOR.get())
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
