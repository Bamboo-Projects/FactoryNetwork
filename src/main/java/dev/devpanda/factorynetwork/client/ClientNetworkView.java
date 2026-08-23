package dev.devpanda.factorynetwork.client;

import dev.devpanda.factorynetwork.lang.NetworkView;

import java.util.List;

/**
 * Was der Client über das Netz weiß, für den Übersetzer im Editor.
 *
 * <p>Damit fällt schon beim Tippen auf, dass keine Tafel {@code test} heißt —
 * statt erst an einer schwarzen Wand drei Räume weiter.
 *
 * <p><b>Solange nichts angekommen ist, wird nichts geprüft.</b> Ein Terminal,
 * das gerade erst aufgeht, hat noch keine Namen; jeden davon als unbekannt zu
 * melden wäre eine Zeile voller Warnungen, die eine Sekunde später von selbst
 * verschwinden.
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
}
