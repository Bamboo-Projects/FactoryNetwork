package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.lang.TokenType;
import dev.devpanda.factorynetwork.network.FactoryGraph;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Wie ein Connector zu seinem Namen kommt.
 *
 * <p>Getrennt vom Gegenstand, damit sich die Regeln ohne Server prüfen lassen
 * — dieselbe Trennung wie bei {@code NameDistance}.
 */
public final class ConnectorNaming {

    /** Weiter als bis hierhin wird nicht durchnummeriert. */
    private static final int MAX_SUFFIX = 999;

    /**
     * Schlägt einen Namen für die Maschine hinter dem Connector vor.
     *
     * <p>Aus {@code minecraft:blast_furnace} wird {@code blast_furnace_1}, und
     * beim nächsten Ofen {@code blast_furnace_2}. Die Nummer kommt aus dem
     * Netzwerk, nicht aus der Gun: Sonst vergäben zwei Spieler mit zwei Guns
     * denselben Namen, und wer die Gun neu herstellt, finge wieder bei eins an.
     */
    public static String suggestFor(BlockState machine, FactoryGraph graph) {
        String base = baseName(machine);
        return nextFree(base, graph);
    }

    /** Der Blockname ohne Namensraum, als Grundlage für den Vorschlag. */
    public static String baseName(BlockState machine) {
        if (machine == null || machine.isAir()) {
            return "device";
        }
        String path = BuiltInRegistries.BLOCK.getKey(machine.getBlock()).getPath();
        return path.isBlank() ? "device" : path;
    }

    /** Die kleinste freie Nummer zu einem Grundnamen. */
    public static String nextFree(String base, FactoryGraph graph) {
        for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
            String candidate = base + "_" + suffix;
            if (!graph.isTaken(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    /**
     * Was an einem eingegebenen Namen auffällt.
     *
     * <p>Die Gun ist der Ort, an dem Namen entstehen — was hier durchgeht,
     * steht später im Code. Deshalb prüft sie dieselben Regeln wie der
     * Übersetzer, aber sie <b>blockiert nicht</b>: Ein Name, der ein
     * Schlüsselwort ist, bleibt erlaubt, er braucht im Code nur Rückstriche.
     * Das ist die Entscheidung aus {@code sprache.md}, Abschnitt 6 — Namen
     * sind Spielstand, Schlüsselwörter sind es nicht.
     */
    public static Warning check(String name, FactoryGraph graph) {
        String trimmed = normalize(name);
        if (trimmed.isBlank()) {
            return new Warning(Kind.EMPTY, null);
        }
        if (!isValidIdentifier(trimmed)) {
            return new Warning(Kind.NOT_AN_IDENTIFIER, null);
        }
        if (TokenType.isKeyword(trimmed)) {
            return new Warning(Kind.KEYWORD, null);
        }
        if (graph != null && graph.isTaken(trimmed)) {
            return new Warning(Kind.TAKEN, nextFree(stripSuffix(trimmed), graph));
        }
        return new Warning(Kind.NONE, null);
    }

    public record Warning(Kind kind, String suggestion) {
        public boolean isFine() {
            return kind == Kind.NONE;
        }
    }

    public enum Kind {
        /** Alles in Ordnung. */
        NONE,
        /** Leer — der Connector bleibt unbenannt und damit unsichtbar. */
        EMPTY,
        /** Enthält Zeichen, die kein Name sein können. */
        NOT_AN_IDENTIFIER,
        /** Ist ein Schlüsselwort — erlaubt, braucht im Code aber Rückstriche. */
        KEYWORD,
        /** Schon vergeben — zwei gleiche Namen machen beide unbrauchbar. */
        TAKEN
    }

    /**
     * Normalform NFC, wie im Übersetzer.
     *
     * <p>Ohne das entstünden hier Namen, die im Code nicht wiederzufinden
     * sind: Die Texteingabe im Spiel liefert {@code ü} je nach Herkunft als
     * ein Zeichen oder als {@code u} mit angehängten Punkten.
     */
    public static String normalize(String name) {
        return name == null ? "" : Normalizer.normalize(name.trim(), Normalizer.Form.NFC);
    }

    /** Taugt der Name als Bezeichner in Manifold? */
    public static boolean isValidIdentifier(String name) {
        if (name.isEmpty()) {
            return false;
        }
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Entfernt eine angehängte Nummer, damit weitergezählt werden kann. */
    public static String stripSuffix(String name) {
        int underscore = name.lastIndexOf('_');
        if (underscore <= 0 || underscore == name.length() - 1) {
            return name;
        }
        String tail = name.substring(underscore + 1);
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return name;
            }
        }
        return name.substring(0, underscore);
    }

    private ConnectorNaming() {
    }
}
