package dev.devpanda.factorynetwork.client;

import net.minecraft.network.chat.Component;

/**
 * What the stock is sorted by.
 *
 * <p>The same three as in Applied Energistics, because they answer the three
 * questions one has about a stock: what it is called, how much is there, where
 * it comes from. What is added there on top — "craftable only" — makes no
 * sense for us as long as there is no autocrafting.
 */
public enum StorageSort {

    AMOUNT("amount"),
    NAME("name"),
    MOD("mod");

    private final String key;

    StorageSort(String key) {
        this.key = key;
    }

    public Component title() {
        return Component.translatable("screen.factorynetwork.terminal.sort." + key);
    }

    /** The letter on the button — at twelve pixels there is no room for icons. */
    public String badge() {
        return switch (this) {
            case AMOUNT -> "#";
            case NAME -> "A";
            case MOD -> "M";
        };
    }

    public StorageSort next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
