package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.CableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Was ein Kabel je Tick trägt.
 *
 * <p><b>Die Grenze am Kabel ist nicht, wie viele Geräte daran hängen, sondern
 * wie viel hindurchgeht.</b> Bis zum 29.08. war es umgekehrt — Kanäle, nach
 * dem Vorbild von Applied Energistics.
 *
 * <p>Der Grund für den Wechsel steht in
 * {@code docs/plan-durchsatz-statt-kanaele.md} und in einem Satz hier: <b>Bei
 * AE2 ist die Form des Netzes das Spiel, hier ist es der Code.</b> Ein
 * Programm sieht nie, welchen Weg ein Kanal nimmt; es sieht nur, ob
 * {@code rate 64 per 1t} durchkommt. Eine Grenze, die das Programm nicht
 * spürt, ist eine Grenze am falschen Ort.
 *
 * <p><b>Und sie schaltet nichts ab.</b> Kein Kanal hieß: aus. Zu wenig
 * Durchsatz heißt: langsamer. Ein Netz an der Grenze arbeitet weiter, nur
 * zäher — das sieht man an den Zahlen, statt an einem Gerät, das plötzlich
 * nichts mehr tut.
 */
public final class Throughput {

    /**
     * Ein gewöhnliches Kabel: ein Stapel je Tick.
     *
     * <p>Genug für jede einzelne Leitung, zu wenig für eine Hauptader, an der
     * zehn Worker ziehen.
     */
    public static final int THIN = 64;

    /**
     * Ein dichtes: acht Stapel.
     *
     * <p>Der Unterschied, für den man es baut. Wäre er klein, wäre das dichte
     * Kabel ein teureres Kabel, das dicker aussieht.
     */
    public static final int DENSE = 512;

    /**
     * Was nicht leitet, begrenzt auch nichts.
     *
     * <p>Der Controller, ein Laufwerk, ein Schrank: Sie sind Ziel und nicht
     * Strecke. Eine Grenze dort wäre eine zweite Grenze am selben Weg.
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * Was an dieser Stelle je Tick hindurchgeht.
     *
     * <p>Router, Gateway und Quantum-Brücke gehören zum dichten Kabel und
     * tragen so viel wie eines — dieselbe Regel, die vorher für die Kanäle
     * galt: Sie sind Leitung und kein Vermehrer.
     */
    public static int at(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof dev.devpanda.factorynetwork.block.RouterBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.GatewayBlock
                || state.getBlock() instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
            return DENSE;
        }
        int channels = CableBlock.channelsAt(state);
        if (channels <= 0) {
            return UNLIMITED;
        }
        // Die Kabelart steht in derselben Zahl, die vorher die Kanäle nannte.
        return channels >= CableBlock.CHANNELS_DENSE ? DENSE : THIN;
    }

    private Throughput() {
    }
}
