package dev.devpanda.factorynetwork.terminal;

import net.minecraft.network.chat.Component;

/**
 * Die Reiter des Terminals.

 * <p><b>Warum nicht bei den Bildschirmen?</b> Weil ab dem Fernzugriff der
 * Server entscheidet, welche Reiter ein Gerät zeigen darf — das Wireless
 * Terminal hat keinen Code. Servercode, der aus {@code client.*} importiert,
 * läuft hier durch und fällt erst auf einem echten Server auf.
 *
 * <p>Alle sechs tun inzwischen etwas. Die Fertigung stand lange ausgegraut in
 * der Leiste — sichtbar, damit man sah, wohin es geht, statt sie zu
 * verstecken und eines Tages unerklärt auftauchen zu lassen. Seit dem 25.08.
 * steht darin, was sie versprochen hat.
 */
public enum TerminalTab {

    STORAGE("storage", true),
    CRAFTING("crafting", true),
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
