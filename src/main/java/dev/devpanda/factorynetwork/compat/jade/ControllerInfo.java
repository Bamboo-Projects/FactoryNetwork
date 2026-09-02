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
 * What Jade says about the controller: the state of the whole network at a
 * glance — devices, cables, how many were left without a channel, and how many
 * workers are currently halted.
 */
public enum ControllerInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_DEVICES = "FnDevices";
    private static final String KEY_CONNECTORS = "FnConnectors";
    private static final String KEY_UNNAMED = "FnUnnamed";
    private static final String KEY_STARVED = "FnStarved";
    private static final String KEY_CABLES = "FnCables";
    private static final String KEY_WORKERS = "FnWorkers";
    private static final String KEY_POWER_STATE = "FnPowerState";
    private static final String KEY_POWER_DRAW = "FnPowerDraw";
    private static final String KEY_POWER_STORED = "FnPowerStored";
    private static final String KEY_THREADS = "FnThreads";
    private static final String KEY_HALTED = "FnHalted";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ControllerBlockEntity controller)) {
            return;
        }
        var graph = controller.graph();
        data.putInt(KEY_DEVICES, graph.deviceCount());
        data.putInt(KEY_CONNECTORS, graph.connectorCount());
        data.putInt(KEY_UNNAMED, graph.unnamedConnectors().size());
        // Since 29 Aug there are no more devices without a channel. The key
        // stays so that an old client doesn't look for it and find nothing.
        data.putInt(KEY_STARVED, 0);
        data.putInt(KEY_CABLES, graph.cableCount());
        data.putInt(KEY_WORKERS, controller.program().workers().size());
        data.putInt(KEY_POWER_STATE, controller.power().state().ordinal());
        data.putInt(KEY_POWER_DRAW, controller.powerDraw());
        data.putInt(KEY_POWER_STORED, controller.power().stored());
        data.putInt(KEY_THREADS, controller.threads());
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
        // Power first: a network that is stalled because its reserve is empty
        // looks like one with a bug in the program.
        var zustand = dev.devpanda.factorynetwork.network.NetworkPower.State
                .values()[data.getInt(KEY_POWER_STATE)];
        String schluessel = switch (zustand) {
            case RUNNING -> "jade.factorynetwork.controller.power";
            case BOOTING -> "jade.factorynetwork.controller.power_booting";
            case OFF -> "jade.factorynetwork.controller.power_off";
        };
        net.minecraft.ChatFormatting farbe = switch (zustand) {
            case RUNNING -> net.minecraft.ChatFormatting.GRAY;
            case BOOTING -> net.minecraft.ChatFormatting.YELLOW;
            case OFF -> net.minecraft.ChatFormatting.RED;
        };
        tooltip.add(Component.translatable(schluessel, data.getInt(KEY_POWER_DRAW),
                        String.format(java.util.Locale.GERMANY, "%,d",
                                data.getInt(KEY_POWER_STORED)))
                .withStyle(farbe));
        if (data.getInt(KEY_THREADS) == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.controller.no_server")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("jade.factorynetwork.controller.network",
                data.getInt(KEY_DEVICES), data.getInt(KEY_CABLES))
                .withStyle(ChatFormatting.GRAY));
        // And below it, how many of them the code can address. The two numbers
        // diverge widely: racks, drives, routers and displays are devices, but
        // not connectors.
        tooltip.add(Component.translatable("jade.factorynetwork.controller.connectors",
                data.getInt(KEY_CONNECTORS))
                .withStyle(ChatFormatting.DARK_GRAY));

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
