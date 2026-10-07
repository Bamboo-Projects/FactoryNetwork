package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.parse.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a name is declared in the project.
 *
 * <p>All files share one namespace — an {@code fn} in one is called in another
 * without any {@code import} standing anywhere. That is exactly why this is
 * needed: <b>the place of a declaration cannot be seen from the place it is
 * used.</b> With three files you can still search for it, with eight no longer.
 *
 * <p>In the language package and not in the editor, like {@link Signatures}: it
 * is a question to the language, and a language server for VS Code asks the same
 * one.
 */
public final class Definitions {

    /**
     * A found location.
     *
     * @param file   the file
     * @param line   the line in it, from one
     * @param column the column, from one
     */
    public record Location(String file, int line, int column) {
    }

    private Definitions() {
    }

    /**
     * Searches for the declaration of a name.
     *
     * <p>The first one that matches. Two identical names are an error the
     * compiler already reports — to address it again here would mean refusing
     * the jump because of an error one is just about to fix.
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
     * All places where a name is declared — for "where is this used" there is
     * {@link #references}.
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
     * Where a name occurs in the project.
     *
     * <p><b>Over the text and not over the tree.</b> A name stands in
     * expressions, arguments, patterns, and strings; finding it everywhere in
     * the tree would mean handling each kind of expression separately — and the
     * next one that gets added would be forgotten. The text search, in return,
     * occasionally finds a hit in a comment. That is the better error: you see
     * it at once and miss no place.
     *
     * <p>Only a whole word is found: {@code ofen_1} is not a location in
     * {@code ofen_10}.
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

    /** Declarations together with the functions that sit inside a multiblock. */
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
