package dev.devpanda.factorynetwork.registry;

import dev.devpanda.factorynetwork.FactoryNetwork;
import dev.devpanda.factorynetwork.block.entity.CableBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ConnectorBlockEntity;
import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import dev.devpanda.factorynetwork.block.entity.DisplayBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            BLOCK_ENTITIES.register("cable", () -> BlockEntityType.Builder
                    .of(CableBlockEntity::new, FnBlocks.CABLE.get())
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
