package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Wo ein Gerät im Netz sitzt: eine Stelle und, bei einem Anschluss, eine
 * Fläche.
 *
 * <p><b>Ein Ort allein reichte, solange ein Block einen Anschluss trug.</b>
 * Seit ein Kabelblock bis zu sechs trägt, ist „welches Gerät steht an dieser
 * Stelle" keine beantwortbare Frage mehr — es können sechs sein, jedes mit
 * eigenem Namen, eigenem Kanalbedarf und eigener Maschine dahinter. Der Graph
 * hat sich deshalb bis hierher genau ein Gerät je Kabelblock gemerkt und die
 * anderen fünf verschluckt.
 *
 * <p>Die Seite ist {@code null}, wo es keine gibt: Ein Laufwerk, ein
 * Serverschrank, eine Anzeige sind ganze Blöcke. Nur ein Anschluss sieht in
 * eine Richtung — und er hat immer eine, auch im eigenen Connectorblock, wo
 * sie im {@code FACING} steht.
 */
public record DevicePos(BlockPos pos, @Nullable Direction side) {

    public DevicePos {
        // Ein BlockPos aus der Suche ist oft ein wandernder MutableBlockPos.
        // Als Schlüssel einer Map wäre das ein Fehler, den man erst drei
        // Schritte später sieht.
        pos = pos.immutable();
    }

    /** Ein ganzer Block — Laufwerk, Schrank, Anzeige. */
    public static DevicePos of(BlockPos pos) {
        return new DevicePos(pos, null);
    }

    /** Ein Anschluss an einer Fläche. */
    public static DevicePos of(BlockPos pos, Direction side) {
        return new DevicePos(pos, side);
    }

    /** Die Stelle, an der die Maschine steht — bei einem ganzen Block er selbst. */
    public BlockPos machine() {
        return side == null ? pos : pos.relative(side);
    }

    @Override
    public String toString() {
        return side == null
                ? pos.getX() + "," + pos.getY() + "," + pos.getZ()
                : pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + side.getName();
    }
}
