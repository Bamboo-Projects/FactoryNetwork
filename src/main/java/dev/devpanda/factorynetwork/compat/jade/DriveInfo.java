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
 *
 * <p>Gegenstände und Flüssigkeiten bekommen je eine Zeile, aber nur, wenn es
 * sie gibt. Ein Laufwerk mit drei Gegenstandszellen soll nicht „0 mB" melden.
 */
public enum DriveInfo implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    INSTANCE;

    private static final String KEY_CELLS = "FnCells";
    private static final String KEY_TYPES = "FnTypes";
    private static final String KEY_TYPE_ROOM = "FnTypeRoom";
    private static final String KEY_AMOUNT = "FnAmount";
    private static final String KEY_ITEM_CELLS = "FnItemCells";
    private static final String KEY_FLUID_CELLS = "FnFluidCells";
    private static final String KEY_FLUID_TYPES = "FnFluidTypes";
    private static final String KEY_FLUID_AMOUNT = "FnFluidAmount";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof DriveBlockEntity drive)) {
            return;
        }
        int itemCells = 0;
        int types = 0;
        int freeTypes = 0;
        long amount = 0;
        for (CellInventory<dev.devpanda.factorynetwork.storage.ItemKey> cell
                : drive.inventories()) {
            itemCells++;
            types += cell.usedTypes();
            freeTypes += cell.freeTypes();
            amount += cell.usedAmount();
        }
        int fluidCells = 0;
        int fluidTypes = 0;
        long fluidAmount = 0;
        for (CellInventory<net.minecraft.world.level.material.Fluid> cell
                : drive.fluidInventories()) {
            fluidCells++;
            fluidTypes += cell.usedTypes();
            freeTypes += cell.freeTypes();
            fluidAmount += cell.usedAmount();
        }
        data.putInt(KEY_CELLS, drive.usedSlots());
        data.putInt(KEY_ITEM_CELLS, itemCells);
        data.putInt(KEY_TYPES, types);
        data.putInt(KEY_TYPE_ROOM, freeTypes);
        data.putLong(KEY_AMOUNT, amount);
        data.putInt(KEY_FLUID_CELLS, fluidCells);
        data.putInt(KEY_FLUID_TYPES, fluidTypes);
        data.putLong(KEY_FLUID_AMOUNT, fluidAmount);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_CELLS)) {
            return;
        }
        int cells = data.getInt(KEY_CELLS);
        if (cells == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.empty")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(Component.translatable("jade.factorynetwork.drive.cells",
                cells, DriveBlockEntity.SLOTS).withStyle(ChatFormatting.GRAY));
        if (data.getInt(KEY_ITEM_CELLS) > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.contents",
                            grouped(data.getLong(KEY_AMOUNT)), data.getInt(KEY_TYPES))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.getInt(KEY_FLUID_CELLS) > 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.fluids",
                            grouped(data.getLong(KEY_FLUID_AMOUNT)),
                            data.getInt(KEY_FLUID_TYPES))
                    .withStyle(ChatFormatting.GRAY));
        }

        if (data.getInt(KEY_TYPE_ROOM) == 0) {
            tooltip.add(Component.translatable("jade.factorynetwork.drive.no_types")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String grouped(long value) {
        return String.format(java.util.Locale.GERMANY, "%,d", value);
    }

    @Override
    public ResourceLocation getUid() {
        return FactoryNetworkJadePlugin.DRIVE;
    }
}
