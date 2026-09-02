package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.storage.CellFormat;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import dev.devpanda.factorynetwork.storage.ChemicalCellItem;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chemical cells, with Mekanism types.
 *
 * <p><b>The arithmetic was already there.</b> {@code CellInventory} and
 * {@code CellFormat} have been generic in the type since the fluids: what
 * differs is the registry and the size, not a single line of the arithmetic
 * over types and amounts. So there is hardly more here than the format — and
 * that is the result of a decision from two days ago, not chance.
 *
 * <p>This class is entered only when Mekanism is present. It may therefore be
 * named whatever it likes, and know Mekanism.
 */
final class MekCells {

    /**
     * How the contents of a chemical cell are stored in the item.
     *
     * <p>Dedicated field names, so a cell is never confused with a fluid cell:
     * both count in millibuckets, and a swapped format would read water as
     * hydrogen.
     */
    static final CellFormat<Chemical> CHEMICALS = new CellFormat<>(
            "ChemicalCell", "Amount", new CellFormat.Entry<>() {

                @Override
                public Chemical read(net.minecraft.nbt.CompoundTag tag,
                                     net.minecraft.core.HolderLookup.Provider registries) {
                    ResourceLocation id = ResourceLocation.tryParse(tag.getString("Chemical"));
                    return id == null
                            || !mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(id)
                            ? null : mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(id);
                }

                @Override
                public void write(net.minecraft.nbt.CompoundTag tag, Chemical key,
                                  net.minecraft.core.HolderLookup.Provider registries) {
                    tag.putString("Chemical",
                            mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(key).toString());
                }
            });

    private MekCells() {
    }

    /** An opened cell, or {@code null} if none is inserted. */
    static CellView open(ItemStack cell,
                         net.minecraft.core.HolderLookup.Provider registries) {
        if (ChemicalCellItem.tierOf(cell) == null) {
            return null;
        }
        return CellInventory.of(cell, ChemicalCellItem.tierOf(cell), CHEMICALS, registries);
    }

    /** What is in the cell, as identifier to amount. */
    static Map<String, Long> read(ItemStack cell,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        Map<String, Long> found = new LinkedHashMap<>();
        CHEMICALS.read(cell, registries).forEach((chemical, amount) -> {
            ResourceLocation id = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            if (id != null && amount > 0) {
                found.put(id.toString(), amount);
            }
        });
        return found;
    }

    /** The chemical for an identifier, or {@code null}. */
    static Chemical chemical(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(key)) {
            return null;
        }
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(key);
    }

    /** And back. */
    static String idOf(Chemical chemical) {
        ResourceLocation id = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
        return id == null ? "" : id.toString();
    }
}
