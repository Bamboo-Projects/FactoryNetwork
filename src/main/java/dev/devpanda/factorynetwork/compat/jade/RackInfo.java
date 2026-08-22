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
 * Was Jade über einen Serverschrank sagt: wie viele Einschübe laufen und was
 * sie zusammen tragen.
 *
 * <p>Die wichtigste Zeile ist die über die unvollständigen Einschübe. Ein
 * Schrank, in dem elf Bauteile stecken und trotzdem nichts läuft, ist von
 * außen nicht von einem vollen zu unterscheiden — und das ist genau der
 * Fehler, den man ohne Hinweis lange sucht.
 */
public enum RackInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_RUNNING = "FnRackRunning";
    private static final String KEY_INCOMPLETE = "FnRackIncomplete";
    private static final String KEY_CPU = "FnRackCpu";
    private static final String KEY_RAM = "FnRackRam";
    private static final String KEY_DISK = "FnRackDisk";

    /**
     * Die Zahlen stehen in der unteren Hälfte.
     *
     * <p>Auf Augenhöhe schaut man auf die obere, und dort sitzt keine
     * BlockEntity. Ohne diesen Umweg bliebe der Tooltip genau dann leer,
     * wenn man geradeaus hinsieht.
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
