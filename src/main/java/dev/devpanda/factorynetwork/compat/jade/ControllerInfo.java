package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.ControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Was Jade über den Controller sagt: der Zustand des ganzen Netzes auf einen
 * Blick — Geräte, Kabel, wie viele ohne Kanal geblieben sind, und wie viele
 * Worker gerade stehen.
 */
public enum ControllerInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_DEVICES = "FnDevices";
    private static final String KEY_UNNAMED = "FnUnnamed";
    private static final String KEY_STARVED = "FnStarved";
    private static final String KEY_CABLES = "FnCables";
    private static final String KEY_WORKERS = "FnWorkers";
    private static final String KEY_HALTED = "FnHalted";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ControllerBlockEntity controller)) {
            return;
        }
        var graph = controller.graph();
        data.putInt(KEY_DEVICES, graph.connectorCount());
        data.putInt(KEY_UNNAMED, graph.unnamedConnectors().size());
        data.putInt(KEY_STARVED, graph.starvedConnectors().size());
        data.putInt(KEY_CABLES, graph.cableCount());
        data.putInt(KEY_WORKERS, controller.program().workers().size());
        data.putInt(KEY_HALTED, (int) controller.runtime().states().values().stream()
                .filter(state -> state.status == dev.devpanda.factorynetwork.runtime
                        .WorkerRuntime.Status.HALTED)
                .count());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_DEVICES)) {
            return;
        }
        tooltip.add(Component.translatable("jade.factorynetwork.controller.network",
                data.getInt(KEY_DEVICES), data.getInt(KEY_CABLES))
                .withStyle(ChatFormatting.GRAY));

        int unnamed = data.getInt(KEY_UNNAMED);
        if (unnamed > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.controller.unnamed", unnamed)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int starved = data.getInt(KEY_STARVED);
        if (starved > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.controller.starved", starved)
                    .withStyle(ChatFormatting.RED));
        }
        int workers = data.getInt(KEY_WORKERS);
        int halted = data.getInt(KEY_HALTED);
        if (workers > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.controller.workers", workers)
                    .withStyle(halted > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        }
        if (halted > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.controller.halted", halted)
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.CONTROLLER;
    }
}
