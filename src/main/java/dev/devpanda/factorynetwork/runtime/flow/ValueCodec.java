package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Schreibt Werte auf und liest sie zurück.
 *
 * <p>Gebraucht für die Variablen eines wartenden Ablaufs. Anders als beim
 * Netzwerkspeicher wird hier <b>nichts stillschweigend übergangen</b>: Fehlt
 * der Gegenstand einer Variablen, weil eine Mod aus dem Pack genommen wurde,
 * scheitert der Ablauf mit klarer Meldung. Ein Lagerbestand darf einen Posten
 * verlieren; eine Variable, mit der weitergerechnet wird, darf sich nicht
 * heimlich in etwas anderes verwandeln.
 */
public final class ValueCodec {

    private static final String KEY_TYPE = "t";
    private static final String KEY_VALUE = "v";
    private static final String KEY_AMOUNT = "a";
    private static final String KEY_ITEMS = "i";

    private ValueCodec() {
    }

    public static CompoundTag write(Value value) {
        CompoundTag tag = new CompoundTag();
        switch (value) {
            case Value.Int number -> {
                tag.putString(KEY_TYPE, "int");
                tag.putLong(KEY_VALUE, number.value());
            }
            case Value.Decimal number -> {
                tag.putString(KEY_TYPE, "dec");
                tag.putDouble(KEY_VALUE, number.value());
            }
            case Value.Bool flag -> {
                tag.putString(KEY_TYPE, "bool");
                tag.putBoolean(KEY_VALUE, flag.value());
            }
            case Value.Text text -> {
                tag.putString(KEY_TYPE, "text");
                tag.putString(KEY_VALUE, text.value());
            }
            case Value.Duration duration -> {
                tag.putString(KEY_TYPE, "dur");
                tag.putLong(KEY_VALUE, duration.ticks());
            }
            case Value.ItemValue item -> {
                tag.putString(KEY_TYPE, "item");
                tag.putString(KEY_VALUE, BuiltInRegistries.ITEM.getKey(item.item()).toString());
            }
            case Value.Request request -> {
                tag.putString(KEY_TYPE, "req");
                tag.putString(KEY_VALUE, request.selector());
                tag.putLong(KEY_AMOUNT, request.amount());
            }
            case Value.Selection selection -> {
                tag.putString(KEY_TYPE, "sel");
                ListTag items = new ListTag();
                selection.items().forEach(item -> items.add(
                        net.minecraft.nbt.StringTag.valueOf(
                                BuiltInRegistries.ITEM.getKey(item).toString())));
                tag.put(KEY_ITEMS, items);
                tag.putLong(KEY_AMOUNT, selection.amount());
            }
            case Value.FluidValue sort -> {
                tag.putString(KEY_TYPE, "fluid");
                tag.putString(KEY_VALUE, BuiltInRegistries.FLUID.getKey(sort.fluid()).toString());
            }
            case Value.FluidSelection selection -> {
                tag.putString(KEY_TYPE, "fluidsel");
                ListTag fluids = new ListTag();
                selection.fluids().forEach(fluid -> fluids.add(
                        net.minecraft.nbt.StringTag.valueOf(
                                BuiltInRegistries.FLUID.getKey(fluid).toString())));
                tag.put(KEY_ITEMS, fluids);
                tag.putLong(KEY_AMOUNT, selection.amount());
            }
            case Value.Device device -> {
                tag.putString(KEY_TYPE, "dev");
                tag.putString(KEY_VALUE, device.name());
            }
            // Eine Gruppe trägt nur ihren Namen, und genau deshalb übersteht
            // sie den Neustart unbeschadet: Wer heute darin steht, entscheidet
            // beim nächsten Blick wieder das Netz.
            case Value.Group group -> {
                tag.putString(KEY_TYPE, "grp");
                tag.putString(KEY_VALUE, group.name());
            }
            case Value.Builtin builtin -> {
                tag.putString(KEY_TYPE, "builtin");
                tag.putString(KEY_VALUE, builtin.name());
            }
            case Value.ValueList list -> {
                tag.putString(KEY_TYPE, "list");
                ListTag entries = new ListTag();
                list.entries().forEach(entry -> entries.add(write(entry)));
                tag.put(KEY_ITEMS, entries);
            }
            case Value.Nothing ignored -> tag.putString(KEY_TYPE, "nothing");
        }
        return tag;
    }

    public static Value read(CompoundTag tag) {
        String type = tag.getString(KEY_TYPE);
        return switch (type) {
            case "int" -> new Value.Int(tag.getLong(KEY_VALUE));
            case "dec" -> new Value.Decimal(tag.getDouble(KEY_VALUE));
            case "bool" -> new Value.Bool(tag.getBoolean(KEY_VALUE));
            case "text" -> new Value.Text(tag.getString(KEY_VALUE));
            case "dur" -> new Value.Duration(tag.getLong(KEY_VALUE));
            case "item" -> new Value.ItemValue(item(tag.getString(KEY_VALUE)));
            case "req" -> new Value.Request(tag.getString(KEY_VALUE), tag.getLong(KEY_AMOUNT));
            case "sel" -> readSelection(tag);
            case "fluid" -> new Value.FluidValue(fluid(tag.getString(KEY_VALUE)));
            case "fluidsel" -> readFluidSelection(tag);
            case "dev" -> new Value.Device(tag.getString(KEY_VALUE));
            case "grp" -> new Value.Group(tag.getString(KEY_VALUE));
            case "builtin" -> new Value.Builtin(tag.getString(KEY_VALUE));
            case "list" -> readList(tag);
            case "nothing" -> Value.Nothing.get();
            default -> throw new ScriptError("Unbekannte Art von Wert: " + type + ".");
        };
    }

    private static Value readSelection(CompoundTag tag) {
        ListTag items = tag.getList(KEY_ITEMS, Tag.TAG_STRING);
        List<Item> resolved = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            resolved.add(item(items.getString(i)));
        }
        return new Value.Selection(List.copyOf(resolved), tag.getLong(KEY_AMOUNT));
    }

    private static Value readFluidSelection(CompoundTag tag) {
        ListTag entries = tag.getList(KEY_ITEMS, Tag.TAG_STRING);
        List<net.minecraft.world.level.material.Fluid> resolved = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            resolved.add(fluid(entries.getString(i)));
        }
        return new Value.FluidSelection(List.copyOf(resolved), tag.getLong(KEY_AMOUNT));
    }

    private static net.minecraft.world.level.material.Fluid fluid(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
            throw new ScriptError("Die Flüssigkeit " + key + " gibt es nicht mehr.",
                    "Der Ablauf hielt sie in einer Variablen fest. Wurde eine Mod "
                            + "aus dem Pack genommen?");
        }
        return BuiltInRegistries.FLUID.get(id);
    }

    private static Value readList(CompoundTag tag) {
        ListTag entries = tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        List<Value> values = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            values.add(read(entries.getCompound(i)));
        }
        return new Value.ValueList(List.copyOf(values));
    }

    private static Item item(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new ScriptError("Den Gegenstand " + key + " gibt es nicht mehr.",
                    "Der Ablauf hielt ihn in einer Variablen fest. Wurde eine Mod "
                            + "aus dem Pack genommen?");
        }
        return BuiltInRegistries.ITEM.get(id);
    }
}
