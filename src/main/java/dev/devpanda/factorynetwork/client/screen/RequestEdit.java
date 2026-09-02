package dev.devpanda.factorynetwork.client.screen;

import dev.devpanda.factorynetwork.client.ClientProjectState;
import dev.devpanda.factorynetwork.network.packet.RequestEditPacket;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Request editing" — for both windows.
 *
 * <p>The lock holds, and whoever stands in front of it could so far only
 * wait. A knock costs nothing and resolves the most common case: the other
 * person has the file open but has stopped typing.
 *
 * <p><b>F4 and no button.</b> A button would need room in a footer that is
 * 288 pixels wide in the tab — and it would sit there even when nobody holds
 * the file. The key is listed in the shortcut list and in the hint beside
 * it; it is found right where the question comes up.
 */
public final class RequestEdit {

    /** F4. */
    public static final int KEY = 293;

    private RequestEdit() {
    }

    /**
     * Asks for the file when someone else is holding it.
     *
     * @return whether the key press was consumed
     */
    public static boolean ask(String file) {
        if (file == null || ClientProjectState.heldBy(file) == null) {
            // Free — then there is nothing to request, and the key should not
            // pretend it did something.
            return false;
        }
        PacketDistributor.sendToServer(new RequestEditPacket(file));
        return true;
    }
}
