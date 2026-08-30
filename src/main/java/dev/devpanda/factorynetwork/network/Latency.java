package dev.devpanda.factorynetwork.network;

import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Wie lange es dauert, bis der erste Gegenstand ankommt.
 *
 * <p><b>Nicht je Block, sondern je Gerät auf dem Weg.</b> Licht braucht für
 * zwanzig Blöcke sechzig Nanosekunden; ein Minecraft-Tick ist fünfzig
 * Millisekunden — eine Million mal länger. Latenz aus Entfernung wäre in
 * dieser Mod frei erfunden.
 *
 * <p><b>Was in einem echten Netz wirklich Zeit kostet, ist die
 * Verarbeitung.</b> Jeder Switch auf dem Weg packt das Paket aus, sieht nach,
 * packt es wieder ein. Bei Glasfaser über Kontinente ist das der größere Teil
 * der Latenz, nicht die Strecke.
 *
 * <p><b>Und sie verzögert den Anfang, nicht den Takt.</b> Ein Worker hinter
 * drei Routern fängt drei Ticks später an — danach läuft er so schnell wie
 * jeder andere. Alles andere wäre eine heimliche Bandbreitenstrafe: Wer
 * sauber trennt, bekäme weniger Durchsatz, und genau das soll die Latenz
 * nicht sein.
 */
public final class Latency {

    /**
     * Was ein Gerät auf dem Weg kostet: einen Tick.
     *
     * <p>Ein Brückenpaar kostet damit zwei — eine Hälfte packt ein, die
     * andere aus. Der Plan vom 30.08. nannte einen; zwei ist die ehrlichere
     * Zahl und braucht keine Sonderregel.
     */
    public static final int PER_HOP = 1;

    /** Wie viele Ticks dieser Weg braucht, bis der erste Griff ankommt. */
    public static int of(Level level, List<FactoryGraph.Node> path) {
        int ticks = 0;
        for (FactoryGraph.Node node : path) {
            var block = level.getBlockState(node.pos()).getBlock();
            // Kabel und Controller kosten nichts: Das Kabel ist Glasfaser,
            // und der Controller ist der Anfang des Weges, nicht ein Halt
            // darauf.
            if (block instanceof dev.devpanda.factorynetwork.block.RouterBlock
                    || block instanceof dev.devpanda.factorynetwork.block.BridgeBlock) {
                ticks += PER_HOP;
            }
        }
        return ticks;
    }

    private Latency() {
    }
}
