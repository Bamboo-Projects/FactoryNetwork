package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;

import java.util.List;
import java.util.Map;

/**
 * Der Chemikalienspeicher des Netzes, in Texten statt in Chemikalien.
 *
 * <p><b>Warum eine Schnittstelle und keine Klasse:</b> Die Rechnung ist
 * dieselbe wie bei Gegenständen und Flüssigkeiten — Zellen in Laufwerken, ein
 * Index darüber —, aber sie fasst Mekanism-Typen an. Eine Klasse, die das im
 * Kern täte, ließe sich in einem Pack ohne Mekanism nicht laden, und mit ihr
 * fiele der ganze Controller.
 *
 * <p>Deshalb steht hier nur die Frage, und die Antwort steht in
 * {@code compat/mekanism}. Eine Chemikalie heißt hier {@code "mekanism:hydrogen"}
 * — ein Text, den jede JVM kennt.
 *
 * <p>Ohne Mekanism gibt es {@link #NONE}: Es nimmt nichts an, gibt nichts her
 * und hat keine Laufwerke. Das ist keine Notlösung, sondern die Wahrheit über
 * ein Pack ohne die Mod.
 */
public interface ChemicalStore {

    /** Wie viel von dieser Chemikalie im Netz liegt, in Millibucket. */
    long count(String id);

    /** Lagert ein und meldet, was nicht hineinpasste. */
    long insert(String id, long amount);

    /** Entnimmt und meldet, wie viel wirklich kam. */
    long extract(String id, long amount);

    /** Der ganze Bestand: Kennung auf Menge. */
    Map<String, Long> contents();

    /** Welche Laufwerke im Netz hängen. Setzt der Controller beim Neuaufbau. */
    void setDrives(List<DriveBlockEntity> drives);

    boolean hasDrives();

    /** Wird gerufen, wenn sich etwas geändert hat. */
    void setChangeListener(Runnable listener);

    /** Ein Speicher ohne Mekanism: Er kann nichts, und das ist richtig so. */
    ChemicalStore NONE = new ChemicalStore() {

        @Override
        public long count(String id) {
            return 0;
        }

        @Override
        public long insert(String id, long amount) {
            return amount;
        }

        @Override
        public long extract(String id, long amount) {
            return 0;
        }

        @Override
        public Map<String, Long> contents() {
            return Map.of();
        }

        @Override
        public void setDrives(List<DriveBlockEntity> drives) {
        }

        @Override
        public boolean hasDrives() {
            return false;
        }

        @Override
        public void setChangeListener(Runnable listener) {
        }
    };
}
