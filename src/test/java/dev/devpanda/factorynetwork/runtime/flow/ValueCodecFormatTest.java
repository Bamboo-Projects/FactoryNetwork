package dev.devpanda.factorynetwork.runtime.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Namen auf der Platte.
 *
 * <p>Ein wartender Ablauf liegt in der Welt, und seine Variablen liegen darin
 * als NBT. Wer das Wertemodell umbaut, ändert dabei leicht auch diese Namen —
 * und dann liest eine alte Welt ihre Abläufe nicht mehr. Sie stehen hier
 * fest.
 *
 * <p><b>Von Hand gebaut und nicht über einen Rundlauf durch den Schreiber.</b>
 * Ein Rundlauf ist mit sich selbst immer einig, auch wenn beide Seiten
 * gemeinsam abgedriftet sind. Geprüft wird deshalb andersherum: ein Tag, wie
 * es heute in einer Welt liegt, hinein — und dasselbe Tag wieder heraus.
 *
 * <p>Gegenstände und Flüssigkeiten fehlen hier, weil ihre Kennungen gegen die
 * Registry aufgelöst werden und die ohne laufendes Spiel keine ist. Für sie
 * steht dieselbe Prüfung im Prüflauf.
 */
class ValueCodecFormatTest {

    @Test
    @DisplayName("Eine gespeicherte Chemikalie heißt weiterhin chem")
    void asavedChemicalIsStillCalledChem() {
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chem");
        stored.putString("v", "mekanism:hydrogen");

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }

    @Test
    @DisplayName("Eine gespeicherte Chemikalienauswahl heißt weiterhin chemsel")
    void asavedChemicalSelectionIsStillCalledChemsel() {
        ListTag ids = new ListTag();
        ids.add(StringTag.valueOf("mekanism:hydrogen"));
        ids.add(StringTag.valueOf("mekanism:oxygen"));
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chemsel");
        stored.put("i", ids);
        stored.putLong("a", 500);

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }

    @Test
    @DisplayName("Eine Auswahl ohne Menge behält die -1")
    void aselectionWithoutAnamountKeepsTheMinusOne() {
        // -1 heißt „alles, was da ist". Ginge die Zahl beim Umbau auf 0, wäre
        // aus „alles" still „nichts" geworden — und der Ablauf bewegte nach
        // dem Neustart keinen Tropfen mehr.
        ListTag ids = new ListTag();
        ids.add(StringTag.valueOf("mekanism:hydrogen"));
        CompoundTag stored = new CompoundTag();
        stored.putString("t", "chemsel");
        stored.put("i", ids);
        stored.putLong("a", -1);

        assertEquals(stored, ValueCodec.write(ValueCodec.read(stored)));
    }
}
