package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;

import java.util.List;
import java.util.Map;

/**
 * Wo Source im Netz liegt, während es unterwegs ist.
 *
 * <p><b>Ein Zwischenhalt und kein Lager.</b> {@code move} führt auch von
 * Gerät zu Gerät über den Netzspeicher — ohne ihn bräuchte es einen dritten
 * Weg für denselben Vorgang, und was unterwegs verlorenginge, hätte niemand
 * gezählt. Source braucht deshalb einen Speicher, damit es sich überhaupt
 * bewegen lässt.
 *
 * <p><b>Er überlebt keinen Neustart</b>, und das ist Absicht: Gegenstände,
 * Flüssigkeiten und Chemikalien liegen in Zellen in einem Laufwerk, und wie
 * viel hineinpasst, entscheidet die Zelle. Für Source gibt es keine Zelle.
 * Ein Netzspeicher, der ohne Zelle beliebig viel hält, wäre Lagerraum
 * geschenkt — das Lager sind die Quellgläser von Ars Nouveau.
 *
 * <p><b>Die Obergrenze ist eine Annahme.</b> Zehntausend ist reichlich für
 * jeden Transport und zu wenig, um als Lager zu taugen; welche Zahl richtig
 * ist, sagt eine Runde Spielen. Sie steht als offene Frage in
 * {@code ressourcenarten.md}.
 */
public final class SourceBuffer implements ResourceStore {

    /** So viel hält der Zwischenhalt. Siehe Klassenkommentar. */
    public static final long CAPACITY = 10_000;

    private long held;
    private Runnable listener = () -> { };

    @Override
    public long count(Object key) {
        return SourceAccess.KEY.equals(key) ? held : 0;
    }

    @Override
    public long room(Object key, long wanted) {
        if (!SourceAccess.KEY.equals(key) || wanted <= 0) {
            return 0;
        }
        return Math.min(wanted, CAPACITY - held);
    }

    @Override
    public long insert(Object key, long amount) {
        if (!SourceAccess.KEY.equals(key) || amount <= 0) {
            return amount;
        }
        long fits = Math.min(amount, CAPACITY - held);
        if (fits <= 0) {
            return amount;
        }
        held += fits;
        listener.run();
        return amount - fits;
    }

    @Override
    public long extract(Object key, long amount) {
        if (!SourceAccess.KEY.equals(key) || amount <= 0) {
            return 0;
        }
        long taken = Math.min(amount, held);
        if (taken <= 0) {
            return 0;
        }
        held -= taken;
        listener.run();
        return taken;
    }

    @Override
    public Map<?, Long> contents() {
        return held > 0 ? Map.of(SourceAccess.KEY, held) : Map.of();
    }

    /**
     * Laufwerke spielen hier keine Rolle.
     *
     * <p>Es gibt keine Source-Zelle, und der Zwischenhalt hängt an keinem
     * Laufwerk — er ist der Weg und nicht der Ort.
     */
    @Override
    public void setDrives(List<DriveBlockEntity> drives) {
    }

    @Override
    public boolean hasDrives() {
        return true;
    }

    @Override
    public void setChangeListener(Runnable changed) {
        this.listener = changed == null ? () -> { } : changed;
    }
}
