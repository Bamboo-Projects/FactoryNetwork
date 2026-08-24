package dev.devpanda.factorynetwork.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Was hinter einem Connector steht.
 *
 * <p>Reine Auskunft, keine Rechnung: Der Server probt die Fähigkeiten des
 * Nachbarblocks, und was dabei herauskommt, steht hier. Der Editor liest es
 * für das Zeigen, für die Vorschläge und für die Warnung, wenn der Connector
 * an einer Seite hängt, an der die Maschine nichts annimmt.
 *
 * <p><b>Der Übersetzungsschlüssel und nicht der fertige Text.</b> „Crusher"
 * heißt auf einem englischen Server anders als im deutschen Client, und
 * übersetzt wird dort, wo jemand hinsieht.
 *
 * @param descriptionId Übersetzungsschlüssel des Blocks, etwa
 *                      {@code block.mekanism.crusher}
 * @param namespace     die Mod, aus der er stammt
 * @param connectedSide die Seite, an der der Connector tatsächlich hängt
 * @param access        was an welcher Seite geht; Seiten ohne Eintrag können
 *                      nichts
 */
public record DeviceProfile(String descriptionId, String namespace,
                            Side connectedSide, Map<Side, Access> access) {

    /**
     * Was an einer Seite geht.
     *
     * @param slots  Fächer des Gegenstandsspeichers, null wenn keiner
     * @param tanks  Behälter des Flüssigkeitsspeichers, null wenn keiner
     * @param energy ob dort Strom hineingeht oder herauskommt
     */
    public record Access(int slots, int tanks, boolean energy) {

        /** Wonach sich fragen lässt. */
        public enum Ability { ITEMS, FLUIDS, ENERGY }

        public boolean has(Ability ability) {
            return switch (ability) {
                case ITEMS -> slots > 0;
                case FLUIDS -> tanks > 0;
                case ENERGY -> energy;
            };
        }
    }

    /** Mehrere Seiten, die dasselbe können. */
    public record Group(List<Side> sides, Access access) {
    }

    /**
     * Ein Gerät, über das nichts bekannt ist.
     *
     * <p>Der Chunk ist nicht geladen, oder da steht gar nichts. <b>Das ist
     * etwas anderes als ein Gerät, das nichts kann</b> — dieselbe
     * Unterscheidung wie bei {@link NetworkView#knowsNetwork()}. Wer sie
     * einebnet, warnt vor Maschinen, die tadellos funktionieren.
     */
    public static DeviceProfile unreachable() {
        return new DeviceProfile("", "", Side.ANY, Map.of());
    }

    /** Ist überhaupt etwas bekannt? */
    public boolean reachable() {
        return !descriptionId.isEmpty();
    }

    /** Was an dieser Seite geht — oder nichts. */
    public Access accessAt(Side side) {
        Access direct = access.get(side);
        if (direct != null) {
            return direct;
        }
        // Was ohne Seite angeboten wird, gilt für jede.
        return access.get(Side.ANY);
    }

    public boolean hasItems(Side side) {
        return can(side, Access.Ability.ITEMS);
    }

    public boolean hasFluids(Side side) {
        return can(side, Access.Ability.FLUIDS);
    }

    public boolean hasEnergy(Side side) {
        return can(side, Access.Ability.ENERGY);
    }

    public boolean can(Side side, Access.Ability ability) {
        Access at = accessAt(side);
        return at != null && at.has(ability);
    }

    /**
     * Die Seiten, an denen das geht — für „Norden hätte einen".
     *
     * <p>Ohne die angeschlossene: Wer sie nennt, sagt dem Spieler, er solle
     * den Connector dorthin hängen, wo er schon hängt.
     */
    public List<Side> sidesWith(Access.Ability ability) {
        List<Side> found = new ArrayList<>();
        for (Map.Entry<Side, Access> entry : access.entrySet()) {
            if (entry.getKey() != connectedSide && entry.getValue().has(ability)) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    /**
     * Seiten mit gleichem Zugang zusammengefasst.
     *
     * <p>Eine Maschine bietet an vier Seiten dasselbe an, und ohne das hier
     * stünde es im Tooltip viermal. Zusammengefasst wird nach dem Inhalt des
     * Zugangs und nicht nach der Handler-Instanz: Für die Anzeige ist
     * „Norden, Süden: 3 Fächer" richtig, gleichgültig ob es dieselbe Instanz
     * ist — und die Instanz überlebt den Weg zum Client ohnehin nicht.
     *
     * <p>Die Reihenfolge der Seiten ist die der Aufzählung, damit dasselbe
     * Gerät immer gleich dasteht.
     */
    public List<Group> grouped() {
        Map<Access, List<Side>> byAccess = new LinkedHashMap<>();
        for (Side side : Side.values()) {
            Access at = access.get(side);
            if (at != null) {
                byAccess.computeIfAbsent(at, key -> new ArrayList<>()).add(side);
            }
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Access, List<Side>> entry : byAccess.entrySet()) {
            groups.add(new Group(entry.getValue(), entry.getKey()));
        }
        return groups;
    }
}
