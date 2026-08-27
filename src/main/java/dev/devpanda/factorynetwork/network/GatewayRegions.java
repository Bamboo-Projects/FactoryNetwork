package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import dev.devpanda.factorynetwork.block.GatewayBlock;
import dev.devpanda.factorynetwork.block.entity.GatewayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Welches Gerät hinter welchem Gateway hängt.
 *
 * <p>Ein Gateway gibt seiner Umgebung einen Anlagennamen: Was von ihm aus über
 * Kabel erreichbar ist, gehört zu seiner Anlage. Das ist die zweite Antwort auf
 * dieselbe Frage, die {@code werk_1/eingang} beantwortet — und sie geht von
 * dem aus, was eine Anlage im Spiel wirklich ist: <b>etwas
 * Zusammenhängendes</b>.
 *
 * <p><b>Ein anderes Gateway ist die Grenze.</b> Wer zwei Anlagen nebeneinander
 * baut, stellt zwei Gateways hin, und dazwischen hört jede auf. Ohne diese
 * Regel liefe die Suche über das ganze Netz und jede Anlage hieße wie das
 * zuletzt gefundene Gateway.
 *
 * <p><b>Der Controller ist auch eine.</b> Sonst zöge sich eine Anlage über den
 * Controller hinweg in jeden anderen Strang, und aus einem Gateway würde eine
 * Aussage über das ganze Netz.
 *
 * <p><b>Zwei Gateways auf demselben Gerät heben sich auf.</b> Dann steht nicht
 * fest, welche Anlage gemeint ist, und geraten wird nicht — das Gerät gehört
 * dann zu keiner, so als stünde kein Gateway da. Gemeldet wird es im Reiter
 * <i>Netz</i>; still das erste zu nehmen hinge an der Suchreihenfolge.
 */
public final class GatewayRegions {

    /** Nichts gefunden — kein Gateway im Netz. */
    public static final GatewayRegions EMPTY =
            new GatewayRegions(Map.of(), Set.of());

    private final Map<BlockPos, String> byDevice;
    private final Set<BlockPos> contested;

    private GatewayRegions(Map<BlockPos, String> byDevice, Set<BlockPos> contested) {
        this.byDevice = byDevice;
        this.contested = contested;
    }

    /** Die Anlage eines Geräts, oder {@code null}. */
    public String instanceAt(BlockPos device) {
        return byDevice.get(device);
    }

    /** Geräte, die von zwei Gateways beansprucht werden. */
    public Set<BlockPos> contested() {
        return contested;
    }

    public boolean isEmpty() {
        return byDevice.isEmpty() && contested.isEmpty();
    }

    /**
     * Sucht von jedem Gateway aus, was zu ihm gehört.
     *
     * <p>Gelaufen wird über die Kabel, nicht über den Netzgraphen: Der kennt
     * die Wege zum Controller, und hier zählt die Nachbarschaft zum Gateway.
     *
     * @param gateways die Stellen, an denen ein Gateway steht
     */
    public static GatewayRegions of(Level level, Iterable<BlockPos> gateways,
            int maxNodes) {
        Map<BlockPos, String> byDevice = new LinkedHashMap<>();
        Set<BlockPos> contested = new HashSet<>();
        for (BlockPos gateway : gateways) {
            String name = nameAt(level, gateway);
            if (name.isEmpty()) {
                continue;
            }
            for (BlockPos device : reach(level, gateway, maxNodes)) {
                String taken = byDevice.get(device);
                if (taken == null) {
                    byDevice.put(device, name);
                } else if (!taken.equals(name)) {
                    contested.add(device);
                }
            }
        }
        // Ein umstrittenes Gerät gehört zu keiner Anlage: Die Antwort „die
        // erste gefundene" hinge an der Reihenfolge der Suche.
        contested.forEach(byDevice::remove);
        return byDevice.isEmpty() && contested.isEmpty()
                ? EMPTY : new GatewayRegions(Map.copyOf(byDevice), Set.copyOf(contested));
    }

    private static String nameAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GatewayBlockEntity gateway
                ? gateway.instance() : "";
    }

    /**
     * Die Connectoren, die von diesem Gateway aus über Kabel erreichbar sind.
     *
     * <p>Angehalten wird an einem anderen Gateway, am Controller und an allem,
     * was kein Kabel ist. Ein Gerät selbst leitet nicht weiter — wer zwei
     * Connectoren aneinanderstellt, meint zwei Connectoren und keine Leitung.
     */
    private static Set<BlockPos> reach(Level level, BlockPos gateway, int maxNodes) {
        Set<BlockPos> found = new HashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(gateway);
        seen.add(gateway);
        while (!queue.isEmpty() && seen.size() <= maxNodes) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.isLoaded(next) || !seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof CableBlock
                        && dev.devpanda.factorynetwork.block.CableBlock.carries(state)) {
                    queue.add(next);
                    // Ein Kabel leitet weiter und trägt zugleich Anschlüsse an
                    // seinen Flächen. Beides gilt: Der Strang läuft durch, und
                    // was daran hängt, gehört zu dieser Anlage.
                    if (dev.devpanda.factorynetwork.block.entity.Connectors.any(level, next)) {
                        found.add(next);
                    }
                } else if (dev.devpanda.factorynetwork.block.entity.Connectors
                        .any(level, next)) {
                    found.add(next);
                }
                // Alles andere endet hier — auch ein zweites Gateway.
            }
        }
        return found;
    }
}
