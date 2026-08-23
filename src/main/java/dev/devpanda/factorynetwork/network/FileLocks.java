package dev.devpanda.factorynetwork.network;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wer welche Datei eines Projekts gerade bearbeitet.
 *
 * <p><b>Ohne das überschreiben sich zwei Spieler wortlos.</b> Beide schicken
 * den ganzen Entwurf, und wer zuletzt tippt, gewinnt — auch über eine Datei,
 * die er gar nicht offen hatte. Der andere merkt es, wenn seine Arbeit weg
 * ist.
 *
 * <p><b>Je Datei und nicht je Projekt.</b> Zwei Leute an einer Fabrik
 * arbeiten fast immer an verschiedenen Stücken; das ganze Projekt zu sperren
 * hieße, dass einer wartet, obwohl nichts kollidiert.
 *
 * <p><b>Genommen wird eine Sperre durch Schreiben, nicht durch Öffnen.</b>
 * Wer eine Datei nur ansieht, soll sie nicht blockieren — und niemand soll
 * daran denken müssen, sie wieder freizugeben. Sie verfällt von selbst,
 * wenn eine Weile nichts mehr kam.
 *
 * <p><b>Ohne jeden Minecraft-Bezug.</b> Gebraucht werden eine Kennung und
 * ein Name; wer die mitbringt, ist der Sache gleich. So lässt sich die Regel
 * in gewöhnlichen Tests prüfen — ein {@code ServerPlayer} bräuchte eine
 * Welt, einen Server und ein halbes Spiel.
 */
public final class FileLocks {

    /** So lange nach dem letzten Schreiben gilt eine Sperre weiter. */
    private static final long TIMEOUT_TICKS = 20L * 60;

    private record Holder(UUID player, String name, long touched) {
    }

    private final Map<String, Holder> holders = new HashMap<>();

    /**
     * Darf dieser Spieler in diese Datei schreiben?
     *
     * <p>Ja, wenn sie frei ist, ihm gehört oder die Sperre abgelaufen ist.
     * Im ersten und letzten Fall nimmt er sie damit.
     */
    public boolean claim(String file, UUID player, String name, long now) {
        Holder holder = holders.get(file);
        if (holder != null && !holder.player().equals(player)
                && now - holder.touched() < TIMEOUT_TICKS) {
            return false;
        }
        holders.put(file, new Holder(player, name, now));
        return true;
    }

    /**
     * Gibt alles frei, was diesem Spieler gehört.
     *
     * <p>Beim Schließen des Terminals. Auf den Zeitablauf zu warten hieße,
     * dass ein anderer eine Minute vor einer Datei steht, die niemand mehr
     * offen hat.
     */
    public void release(UUID player) {
        holders.values().removeIf(holder -> holder.player().equals(player));
    }

    /**
     * Wer welche Datei hält, aus Sicht eines bestimmten Spielers.
     *
     * <p>Die eigenen Sperren stehen nicht darin: Dass man selbst schreibt,
     * ist keine Nachricht.
     */
    public Map<String, String> othersFor(UUID player, long now) {
        Map<String, String> shown = new LinkedHashMap<>();
        holders.forEach((file, holder) -> {
            if (!holder.player().equals(player) && now - holder.touched() < TIMEOUT_TICKS) {
                shown.put(file, holder.name());
            }
        });
        return shown;
    }
}
