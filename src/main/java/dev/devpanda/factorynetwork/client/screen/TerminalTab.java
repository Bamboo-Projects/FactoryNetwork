package dev.devpanda.factorynetwork.client.screen;

import net.minecraft.network.chat.Component;

/**
 * Die Reiter des Terminals.
 *
 * <p>Fertigung und Anzeigen stehen mit in der Leiste, sind aber ausgegraut:
 * Autocrafting und Displays gibt es noch nicht. Sie zu zeigen ist ehrlicher,
 * als sie zu verstecken — der Spieler sieht, wohin es geht, und stößt nicht
 * auf einen Reiter, der eines Tages unerklärt auftaucht.
 */
public enum TerminalTab {

    STORAGE("storage", true),
    CRAFTING("crafting", false),
    CODE("code", true),
    NETWORK("network", true),
    DASHBOARDS("dashboards", false);

    private final String key;
    private final boolean ready;

    TerminalTab(String key, boolean ready) {
        this.key = key;
        this.ready = ready;
    }

    public boolean isReady() {
        return ready;
    }

    public Component title() {
        return Component.translatable("screen.factorynetwork.terminal.tab." + key);
    }

    public Component notReadyHint() {
        return Component.translatable("screen.factorynetwork.terminal.tab." + key + ".soon");
    }
}
