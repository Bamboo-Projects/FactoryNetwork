package dev.devpanda.factorynetwork.client;

import net.minecraft.network.chat.Component;

/**
 * Wonach der Bestand sortiert wird.
 *
 * <p>Dieselben drei wie bei Applied Energistics, weil sie die drei Fragen
 * beantworten, die man an einen Bestand hat: Wie heißt es, wie viel ist da,
 * woher kommt es. Was dort noch dazukommt — „nur Herstellbares" — hat bei uns
 * keinen Sinn, solange es kein Autocrafting gibt.
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

    /** Der Buchstabe auf dem Knopf — für Symbole ist bei zwölf Pixeln kein Platz. */
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
