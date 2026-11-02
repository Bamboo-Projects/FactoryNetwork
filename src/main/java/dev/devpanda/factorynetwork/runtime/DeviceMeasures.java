package dev.devpanda.factorynetwork.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.TreeMap;

/**
 * Values other mods measure at a place, for {@code device.measure("name")}.
 *
 * <p>Registered at load time by the adapters under {@code compat}, one name
 * per value. The device's place is the block behind its connector.
 */
public final class DeviceMeasures {

    @FunctionalInterface
    public interface Measure {
        double at(Level level, BlockPos pos);
    }

    private static final Map<String, Measure> MEASURES = new TreeMap<>();

    private DeviceMeasures() {
    }

    public static void register(String name, Measure measure) {
        MEASURES.put(name, measure);
    }

    public static Measure get(String name) {
        return MEASURES.get(name);
    }

    /** The registered names, sorted, for the error message. */
    public static java.util.Set<String> names() {
        return MEASURES.keySet();
    }
}
