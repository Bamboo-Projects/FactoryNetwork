package dev.devpanda.factorynetwork.compat.jade;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellInventory;
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
 * Was Jade über ein Laufwerk sagt: wie viele Zellen stecken, wie voll sie
 * sind, und ob die Arten oder die Menge zuerst ausgehen.
 *
 * <p>Der letzte Punkt ist der, den man ohne Hilfe nicht sieht: Eine Zelle mit
 * allen Artenplätzen belegt nimmt nichts Neues mehr an, obwohl sie nach Menge
 * fast leer ist.
 */
public enum DriveInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_CELLS = "FnCells";
    private static final String KEY_TYPES = "FnTypes";
    private static final String KEY_TYPE_ROOM = "FnTypeRoom";
    private static final String KEY_AMOUNT = "FnAmount";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof DriveBlockEntity drive)) {
            return;
        }
        int zellen = 0;
        int arten = 0;
        int freieArten = 0;
        long menge = 0;
        for (CellInventory cell : drive.inventories()) {
            zellen++;
            arten += cell.usedTypes();
            freieArten += cell.freeTypes();
            menge += cell.usedAmount();
        }
        data.putInt(KEY_CELLS, zellen);
        data.putInt(KEY_TYPES, arten);
        data.putInt(KEY_TYPE_ROOM, freieArten);
        data.putLong(KEY_AMOUNT, menge);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_CELLS)) {
            return;
        }
        int zellen = data.getInt(KEY_CELLS);
        if (zellen == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.empty")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(Component.translatable("jade.factorynetwork.drive.cells",
                zellen, DriveBlockEntity.SLOTS).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("jade.factorynetwork.drive.contents",
                        String.format(java.util.Locale.GERMANY, "%,d", data.getLong(KEY_AMOUNT)),
                        data.getInt(KEY_TYPES))
                .withStyle(ChatFormatting.GRAY));

        if (data.getInt(KEY_TYPE_ROOM) == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.no_types")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.DRIVE;
    }
}
