package dev.devpanda.factorynetwork.client.screen;

import net.minecraft.network.chat.Component;

/**
 * Die Reiter des Terminals.
 *
 * <p>Die Fertigung steht mit in der Leiste, ist aber ausgegraut: Autocrafting
 * gibt es noch nicht. Sie zu zeigen ist ehrlicher, als sie zu verstecken — der
 * Spieler sieht, wohin es geht, und stößt nicht auf einen Reiter, der eines
 * Tages unerklärt auftaucht.
 */
public enum TerminalTab {

    STORAGE("storage", true),
    CRAFTING("crafting", false),
    CODE("code", true),
    NETWORK("network", true),
    DASHBOARDS("dashboards", true),
    LOG("log", true);

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
