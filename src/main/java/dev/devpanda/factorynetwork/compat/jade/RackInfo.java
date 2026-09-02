package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.RackBlockEntity;
import dev.devpanda.factorynetwork.network.ServerBay;
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
 * What Jade says about a server rack: how many bays are running and what they
 * carry together.
 *
 * <p>The most important line is the one about the incomplete bays. A rack
 * holding eleven components while nothing runs is indistinguishable from a
 * full one from the outside — and that is exactly the mistake you search for a
 * long time without a hint.
 */
public enum RackInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_RUNNING = "FnRackRunning";
    private static final String KEY_INCOMPLETE = "FnRackIncomplete";
    private static final String KEY_CPU = "FnRackCpu";
    private static final String KEY_RAM = "FnRackRam";
    private static final String KEY_DISK = "FnRackDisk";

    /**
     * The numbers live in the lower half.
     *
     * <p>At eye level you look at the upper one, and there is no block entity
     * there. Without this detour the tooltip would stay empty precisely when
     * you look straight ahead.
     */
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        var base = dev.devpanda.factorynetwork.block.RackBlock.baseOf(
                accessor.getBlockState(), accessor.getPosition());
        if (!(accessor.getLevel().getBlockEntity(base) instanceof RackBlockEntity rack)) {
            return;
        }
        ServerBay capacity = rack.capacity();
        data.putInt(KEY_RUNNING, rack.runningBays());
        data.putInt(KEY_INCOMPLETE, rack.incompleteBays());
        data.putInt(KEY_CPU, capacity.cpu());
        data.putInt(KEY_RAM, capacity.ram());
        data.putInt(KEY_DISK, capacity.disk());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_RUNNING)) {
            return;
        }
        int running = data.getInt(KEY_RUNNING);
        int incomplete = data.getInt(KEY_INCOMPLETE);
        if (running == 0 && incomplete == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.rack.empty")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(Component.translatable("jade.factorynetwork.rack.bays",
                running, RackBlockEntity.BAYS).withStyle(ChatFormatting.GRAY));
        if (incomplete > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.rack.incomplete",
                    incomplete).withStyle(ChatFormatting.YELLOW));
        }
        if (running > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.rack.capacity",
                            data.getInt(KEY_CPU), data.getInt(KEY_RAM), data.getInt(KEY_DISK))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.RACK;
    }
}
