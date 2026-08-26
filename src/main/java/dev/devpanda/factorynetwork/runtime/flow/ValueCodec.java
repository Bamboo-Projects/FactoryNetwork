package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.runtime.ScriptError;
import dev.devpanda.factorynetwork.runtime.Value;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
            // Alle drei Arten auf einem Weg — und trotzdem stehen auf der
            // Platte dieselben Namen wie vorher: Ein wartender Ablauf aus
            // einer alten Welt muss seine Variablen wiederfinden. Welcher
            // Name zu welcher Art gehört, sagt ResourceKind.
            case Value.Resource resource -> {
                tag.putString(KEY_TYPE, resource.kind().tag());
                tag.putString(KEY_VALUE, resource.kind().idOf(resource.key()));
            }
            case Value.Selection selection -> {
                tag.putString(KEY_TYPE, selection.kind().selectionTag());
                ListTag keys = new ListTag();
                selection.keys().forEach(key -> keys.add(
                        net.minecraft.nbt.StringTag.valueOf(selection.kind().idOf(key))));
                tag.put(KEY_ITEMS, keys);
                tag.putLong(KEY_AMOUNT, selection.amount());
            }
            case Value.Request request -> {
                tag.putString(KEY_TYPE, "req");
                tag.putString(KEY_VALUE, request.selector());
                tag.putLong(KEY_AMOUNT, request.amount());
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
            case Value.DeviceSlots view -> {
                tag.putString(KEY_TYPE, "slots");
                tag.putString(KEY_VALUE, view.device());
                tag.putIntArray(KEY_ITEMS,
                        view.slots().stream().mapToInt(Integer::intValue).toArray());
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
        // Erst die Ressourcen: Ihre Namen gehören der Art, und welche Art zu
        // einem Namen gehört, weiß die Registry. Ohne diesen Umweg stünde
        // für jede neue Art eine Zeile mehr in diesem Codec.
        var kind = dev.devpanda.factorynetwork.runtime.ResourceKinds.byTag(type);
        if (kind != null) {
            return kind.tag().equals(type)
                    ? new Value.Resource(kind, kind.fromId(tag.getString(KEY_VALUE)))
                    : readSelection(kind, tag);
        }
        return switch (type) {
            case "int" -> new Value.Int(tag.getLong(KEY_VALUE));
            case "dec" -> new Value.Decimal(tag.getDouble(KEY_VALUE));
            case "bool" -> new Value.Bool(tag.getBoolean(KEY_VALUE));
            case "text" -> new Value.Text(tag.getString(KEY_VALUE));
            case "dur" -> new Value.Duration(tag.getLong(KEY_VALUE));
            case "req" -> new Value.Request(tag.getString(KEY_VALUE), tag.getLong(KEY_AMOUNT));
            case "dev" -> new Value.Device(tag.getString(KEY_VALUE));
            case "grp" -> new Value.Group(tag.getString(KEY_VALUE));
            case "slots" -> new Value.DeviceSlots(tag.getString(KEY_VALUE),
                    java.util.Arrays.stream(tag.getIntArray(KEY_ITEMS)).boxed().toList());
            case "builtin" -> new Value.Builtin(tag.getString(KEY_VALUE));
            case "list" -> readList(tag);
            case "nothing" -> Value.Nothing.get();
            // Eine Art, die niemand mehr kennt. Seit die Registry offen ist,
            // ist das der wahrscheinlichere Fall: Nicht der Codec ist kaputt,
            // sondern die Mod fehlt, die diese Art mitgebracht hat.
            default -> throw new ScriptError(
                    "Diese Art von Wert kennt hier niemand: " + type + ".",
                    "Ein wartender Ablauf hielt sie fest. Wurde eine Mod aus dem "
                            + "Pack genommen?");
        };
    }

    /**
     * Eine Auswahl, Eintrag für Eintrag.
     *
     * <p>Ob eine Kennung gegen eine Registry geprüft wird, entscheidet die
     * Art — und das ist der einzige Unterschied, der von den drei Lesern
     * übrig ist. Er steht in {@code ResourceKind.fromId}.
     */
    private static Value readSelection(
            dev.devpanda.factorynetwork.runtime.ResourceKind kind, CompoundTag tag) {
        ListTag entries = tag.getList(KEY_ITEMS, Tag.TAG_STRING);
        List<Object> resolved = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            resolved.add(kind.fromId(entries.getString(i)));
        }
        return new Value.Selection(kind, List.copyOf(resolved), tag.getLong(KEY_AMOUNT));
    }

    private static Value readList(CompoundTag tag) {
        ListTag entries = tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
        List<Value> values = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            values.add(read(entries.getCompound(i)));
        }
        return new Value.ValueList(List.copyOf(values));
    }

}
