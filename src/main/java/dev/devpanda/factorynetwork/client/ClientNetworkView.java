package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.NetworkView;

import java.util.List;

/**
 * What the client knows about the network, for the compiler in the editor.
 *
 * <p>This way it shows already while typing that no board is called
 * {@code test} — instead of only at a blank wall three rooms further on.
 *
 * <p><b>As long as nothing has arrived, nothing is checked.</b> A terminal
 * that has only just opened has no names yet; reporting each of them as
 * unknown would be a line full of warnings that vanish by themselves a second
 * later.
 */
public final class ClientNetworkView implements NetworkView {

    public static final ClientNetworkView INSTANCE = new ClientNetworkView();

    private ClientNetworkView() {
    }

    @Override
    public boolean knowsNetwork() {
        return !ClientNetworkState.connectors().isEmpty()
                || !ClientNetworkState.displays().isEmpty();
    }

    @Override
    public List<String> connectors() {
        return ClientNetworkState.connectors();
    }

    @Override
    public List<String> displays() {
        return ClientNetworkState.displays();
    }

    @Override
    public dev.devpanda.factorynetwork.lang.DeviceProfile profile(String connector) {
        return ClientNetworkState.profile(connector);
    }
}
