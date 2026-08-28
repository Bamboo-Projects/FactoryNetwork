package dev.devpanda.factorynetwork.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Ein fremdes Inventar, das zum Netzspeicher zählt.
 *
 * <p>Der Speicherbus, wie AE2 ihn hat — nur erklärt das Programm ihn und
 * nicht ein Block: {@code store kiste_1 { … }}. Die Begründung steht in
 * {@code speicherbus.md}.
 *
 * <p><b>Durchgereicht und nicht gespiegelt.</b> Eine Kopie ist falsch, sobald
 * jemand die Kiste anfasst — ein Spieler räumt sie aus, ein Trichter füllt
 * sie, eine fremde Maschine leert sie im selben Tick. Ein Auftrag, der auf
 * eine veraltete Kopie rechnet, hinterließe genau den halben Stapel
 * Zwischenzeug, den die Fertigung ausdrücklich vermeidet.
 *
 * <p><b>Einmal je Tick gelesen.</b> Durchreichen heißt nicht, bei jeder Frage
 * das ganze Inventar zu zählen: Ein Worker fragt je Tick, eine Anzeige auch,
 * und ein Auftrag mehrmals. Der Bus liest deshalb, wenn ihn jemand dazu
 * auffordert — der Controller tut das je Tick —, und behält die Antwort
 * dazwischen. Sie ist damit nie älter als ein Tick.
 *
 * <p>Der Zugriff steht hinter einem {@link Supplier}: Ein Connector kann
 * abgebaut, die Kiste zerschlagen, der Klotz entladen werden. Ein festes
 * {@code IItemHandler} hier wäre ein Zeiger auf etwas, das es nicht mehr gibt.
 */
public final class StorageBus {

    private final String device;
    private final long priority;
    private final Supplier<IItemHandler> access;

    /**
     * Was hinein darf, oder leer für alles.
     *
     * <p>Schon aufgelöst: Der Bus fragt je Ablage, und eine Auswahl je Ablage
     * gegen die Registry aufzulösen wäre der teuerste Weg, dieselbe Antwort
     * zu bekommen. Der Controller löst sie beim Neuaufbau auf — dann, wenn
     * sich das Programm ohnehin geändert haben kann.
     */
    private final java.util.Set<ItemKey> allowed;

    /** Was beim letzten Lesen darin lag. */
    private final Map<ItemKey, Long> contents = new LinkedHashMap<>();

    public StorageBus(String device, long priority, java.util.Collection<ItemKey> allowed,
                      Supplier<IItemHandler> access) {
        this.device = device;
        this.priority = priority;
        this.allowed = allowed == null ? java.util.Set.of() : java.util.Set.copyOf(allowed);
        this.access = access;
    }

    /**
     * Ob diese Art hinein darf.
     *
     * <p>Ohne Filter darf alles. <b>Für das Herausholen gilt er nicht:</b>
     * Was schon drinliegt, gehört zum Bestand und ist erreichbar — es zu
     * verschweigen, weil es nicht zum Filter passt, wäre eine Lüge über
     * etwas, das jeder sehen kann, und ein Bestand, aus dem man nichts holen
     * kann, wäre die schlimmere Hälfte davon.
     */
    public boolean accepts(ItemKey item) {
        return allowed.isEmpty() || allowed.contains(item);
    }

    public String device() {
        return device;
    }

    /** Wohin zuerst eingelagert wird; die Zellen stehen auf null. */
    public long priority() {
        return priority;
    }

    /** Der Zugriff, oder {@code null}, wenn gerade keiner dasteht. */
    public IItemHandler handler() {
        return access.get();
    }

    /** Was beim letzten Lesen darin lag. */
    public Map<ItemKey, Long> contents() {
        return contents;
    }

    /**
     * Legt ab und liefert, was nicht hineinpasste.
     *
     * <p>Über die Fächer der Maschine und nicht in ein eigenes Fach: Wohin
     * etwas gehört, weiß sie selbst — dieselbe Auskunft, auf die sich auch
     * {@code move} verlässt.
     *
     * <p>Der gemerkte Inhalt wird mitgeführt und nicht neu gelesen. Ein Lesen
     * je Ablage wäre genau das, was das Lesen je Tick einspart.
     */
    public long insert(ItemKey item, long count) {
        IItemHandler handler = access.get();
        if (handler == null || count <= 0 || !accepts(item)) {
            return count;
        }
        // Der Stapel wird aus dem Schlüssel gebaut und trägt damit, was der
        // Gegenstand ausmacht. Vorher entstand hier ein nackter — ein
        // verzaubertes Buch kam in der Kiste ohne Verzauberung an.
        ItemStack rest = item.toStack(
                (int) Math.min(count, item.maxStackSize()));
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        long placed = count - rest.getCount();
        if (placed > 0) {
            contents.merge(item, placed, Long::sum);
        }
        return count - placed;
    }

    /**
     * Holt heraus und liefert, wie viel es wurde.
     *
     * <p>Nur aus Fächern, die diese Art führen — und nur so viel, wie die
     * Maschine hergibt. Ein Eingangsfach, das nichts herausrückt, ist keine
     * Fehlermeldung, sondern eine Maschine, die ihre Regeln behält.
     */
    public long extract(ItemKey item, long count) {
        IItemHandler handler = access.get();
        if (handler == null || count <= 0) {
            return 0;
        }
        long taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < count; slot++) {
            // Ganze Gegenstände vergleichen, nicht nur die Kennung: Sonst
            // holte eine Anforderung nach einer nackten Spitzhacke die
            // verzauberte aus der Kiste.
            if (!item.equals(ItemKey.of(handler.getStackInSlot(slot)))) {
                continue;
            }
            taken += handler.extractItem(slot, (int) (count - taken), false).getCount();
        }
        if (taken > 0) {
            long left = contents.getOrDefault(item, 0L) - taken;
            if (left > 0) {
                contents.put(item, left);
            } else {
                contents.remove(item);
            }
        }
        return taken;
    }

    /**
     * Liest das Inventar neu.
     *
     * @return ob sich etwas geändert hat
     */
    public boolean refresh() {
        IItemHandler handler = access.get();
        Map<ItemKey, Long> found = new LinkedHashMap<>();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    found.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
                }
            }
        }
        if (found.equals(contents)) {
            return false;
        }
        contents.clear();
        contents.putAll(found);
        return true;
    }
}
