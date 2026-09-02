package dev.devpanda.factorynetwork.terminal;

import net.minecraft.network.chat.Component;

/**
 * The tabs of the terminal.

 * <p><b>Why not with the screens?</b> Because from remote access onward the
 * server decides which tabs a device may show — the Wireless Terminal has no
 * code. Server code that imports from {@code client.*} slips through here and
 * only shows up on a real server.
 *
 * <p>All six now do something. Crafting stood greyed out in the bar for a long
 * time — visible, so that one could see where things were headed, rather than
 * hiding it and letting it turn up unexplained one day. Since 25 Aug it holds
 * what it promised.
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
