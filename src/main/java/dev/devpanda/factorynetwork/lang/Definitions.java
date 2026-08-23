package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.parse.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wo ein Name im Projekt erklärt wird.
 *
 * <p>Alle Dateien teilen einen Namensraum — ein {@code fn} in der einen wird
 * in der anderen aufgerufen, ohne dass irgendwo {@code import} steht. Genau
 * deshalb braucht es das hier: <b>Der Ort einer Erklärung ist von der Stelle
 * ihres Gebrauchs aus nicht zu sehen.</b> Bei drei Dateien sucht man sie noch,
 * bei acht nicht mehr.
 *
 * <p>Im Sprachpaket und nicht im Editor, wie {@link Signatures}: Es ist eine
 * Frage an die Sprache, und ein Sprachserver für VS Code stellt dieselbe.
 */
public final class Definitions {

    /**
     * Eine Fundstelle.
     *
     * @param file   die Datei
     * @param line   die Zeile darin, von eins an
     * @param column die Spalte, von eins an
     */
    public record Location(String file, int line, int column) {
    }

    private Definitions() {
    }

    /**
     * Sucht die Deklaration zu einem Namen.
     *
     * <p>Die erste, die passt. Zwei gleiche Namen sind ein Fehler, den der
     * Übersetzer schon meldet — hier noch einmal darauf einzugehen hieße, den
     * Sprung wegen eines Fehlers zu verweigern, den man gerade beheben will.
     */
    public static Optional<Location> find(Project project, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (String file : project.names()) {
            Parser.ParseResult result = Parser.parse(project.source(file));
            for (Decl declaration : flatten(result.program().declarations())) {
                if (name.equals(declaration.name())) {
                    return Optional.of(new Location(file, declaration.span().line(),
                            declaration.span().column()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Alle Stellen, an denen ein Name erklärt wird — für „wo wird das
     * benutzt" gibt es {@link #references}.
     */
    public static List<Location> findAll(Project project, String name) {
        List<Location> found = new ArrayList<>();
        for (String file : project.names()) {
            Parser.ParseResult result = Parser.parse(project.source(file));
            for (Decl declaration : flatten(result.program().declarations())) {
                if (name.equals(declaration.name())) {
                    found.add(new Location(file, declaration.span().line(),
                            declaration.span().column()));
                }
            }
        }
        return found;
    }

    /**
     * Wo ein Name im Projekt vorkommt.
     *
     * <p><b>Über den Text und nicht über den Baum.</b> Ein Name steht in
     * Ausdrücken, Argumenten, Mustern und Zeichenketten; ihn im Baum überall
     * zu finden hieße, jede Ausdrucksart einzeln zu behandeln — und die
     * nächste, die dazukommt, würde vergessen. Die Textsuche findet dafür
     * gelegentlich einen Treffer in einem Kommentar. Das ist der bessere
     * Fehler: Man sieht ihn sofort und übersieht keine Stelle.
     *
     * <p>Gefunden wird nur ein ganzes Wort: {@code ofen_1} ist keine
     * Fundstelle in {@code ofen_10}.
     */
    public static List<Location> references(Project project, String name) {
        List<Location> found = new ArrayList<>();
        if (name == null || name.isBlank()) {
            return found;
        }
        for (String file : project.names()) {
            String[] lines = project.source(file).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                int from = 0;
                while (true) {
                    int at = lines[i].indexOf(name, from);
                    if (at < 0) {
                        break;
                    }
                    if (isWholeWord(lines[i], at, name.length())) {
                        found.add(new Location(file, i + 1, at + 1));
                    }
                    from = at + name.length();
                }
            }
        }
        return found;
    }

    private static boolean isWholeWord(String line, int at, int length) {
        boolean leftFree = at == 0 || !isNameChar(line.charAt(at - 1));
        int after = at + length;
        boolean rightFree = after >= line.length() || !isNameChar(line.charAt(after));
        return leftFree && rightFree;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Deklarationen samt der Funktionen, die in einem Multiblock stecken. */
    private static List<Decl> flatten(List<Decl> declarations) {
        List<Decl> all = new ArrayList<>(declarations);
        for (Decl declaration : declarations) {
            if (declaration instanceof Decl.Multiblock multiblock) {
                all.addAll(multiblock.functions());
            }
        }
        return all;
    }
}
