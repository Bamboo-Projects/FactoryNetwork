package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.analyser.AnalyserData;
import dev.devpanda.factorynetwork.network.packet.AnalyserDataPacket;

/**
 * The network as the analyser last reported it.
 *
 * <p>For drawing only. What stands here was true at the last packet — for a
 * debugging tool that is exactly right, because it comes in again several
 * times a second.
 */
public final class ClientAnalyserState {

    private static AnalyserData data = AnalyserData.empty();
    private static long lastUpdate;

    private ClientAnalyserState() {
    }

    public static void accept(AnalyserDataPacket packet) {
        data = new AnalyserData(packet.nodes(), packet.links(), packet.summary());
        lastUpdate = System.currentTimeMillis();
    }

    public static AnalyserData data() {
        return data;
    }

    /**
     * Is the picture still fresh?
     *
     * <p>If the supply stops — because the tool was put away or the controller
     * has disappeared —, the display vanishes by itself instead of showing an
     * old network.
     */
    public static boolean isFresh() {
        return System.currentTimeMillis() - lastUpdate < 2000;
    }

    public static void clear() {
        data = AnalyserData.empty();
        lastUpdate = 0;
    }
}
