package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.block.ConnectorBlock;
import dev.devpanda.factorynetwork.lang.DeviceProfile;
import dev.devpanda.factorynetwork.lang.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Probt, was die Maschine hinter einem Connector kann.
 *
 * <p>Alle sechs Richtungen und dazu den seitenlosen Zugang: Manche Maschine
 * bietet ihre Fähigkeit ausschließlich ohne Seite an, und wer nur die sechs
 * Richtungen probt, meldet für genau die „nimmt nichts an" — die schlechteste
 * Sorte Fehler, weil sie plausibel aussieht.
 *
 * <p>Der Aufwand fällt beim Öffnen des Terminals an, nicht laufend. Sieben
 * Zugänge mal drei Fähigkeiten je Connector; die Abfragen sind in NeoForge
 * zwischengespeichert und kosten in dieser Größenordnung nichts.
 */
public final class DeviceScan {

    private DeviceScan() {
    }

    /** Das Profil der Maschine hinter diesem Connector. */
    public static DeviceProfile of(ConnectorBlockEntity connector) {
        Level level = connector.getLevel();
        if (level == null) {
            return DeviceProfile.unreachable();
        }
        BlockState state = connector.getBlockState();
        Direction facing = ConnectorBlock.machineSide(state);
        BlockPos target = connector.getBlockPos().relative(facing);
        if (!level.isLoaded(target)) {
            return DeviceProfile.unreachable();
        }
        // Luft ist keine fehlende Auskunft, sondern eine: Da steht nichts.
        // Beides zusammenzuwerfen hieße, einem Spieler „nicht geladen" zu
        // sagen, während er davorsteht — und die Meldung über die falsche
        // Seite bliebe aus, weil sie ein Profil braucht.
        BlockState machine = level.getBlockState(target);

        Map<Side, DeviceProfile.Access> access = new EnumMap<>(Side.class);
        for (Direction direction : Direction.values()) {
            // Aus Sicht der Maschine kommt der Zugriff von der Gegenseite.
            DeviceProfile.Access at = probe(level, target, direction.getOpposite());
            if (at != null) {
                access.put(side(direction), at);
            }
        }
        DeviceProfile.Access anySide = probe(level, target, null);
        if (anySide != null) {
            access.put(Side.ANY, anySide);
        }

        return new DeviceProfile(machine.getBlock().getDescriptionId(),
                BuiltInRegistries.BLOCK.getKey(machine.getBlock()).getNamespace(),
                side(facing), access);
    }

    /**
     * Was an einer Seite geht, oder {@code null}, wenn dort nichts geht.
     *
     * <p>{@code null} und kein leerer Eintrag: Eine Seite, die nichts kann,
     * steht gar nicht erst in der Karte, und das Profil bleibt so klein wie
     * die Maschine schlicht ist.
     */
    private static DeviceProfile.Access probe(Level level, BlockPos pos, Direction from) {
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, from);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, from);
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, from);

        int slots = items == null ? 0 : items.getSlots();
        int tanks = fluids == null ? 0 : fluids.getTanks();
        boolean power = energy != null;
        if (slots == 0 && tanks == 0 && !power) {
            return null;
        }
        return new DeviceProfile.Access(slots, tanks, power);
    }

    /** Minecrafts Richtung als Seite der Sprache. */
    public static Side side(Direction direction) {
        return switch (direction) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            case EAST -> Side.EAST;
        };
    }
}
