package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.parse.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A controller's program, split across several files.
 *
 * <p>A program of three hundred lines in one piece is not an overview.
 * <b>So a project:</b> {@code worker.mf}, {@code anzeigen.mf},
 * {@code ereignisse.mf} — how to split it, the player decides.
 *
 * <p><b>All files share one namespace.</b> That has been so in the compiler
 * since day one: an {@code fn} in one file calls an {@code fn} in another
 * without any {@code import} having to stand anywhere. Files here are order for
 * the human, not a boundary for the language — real modules with their own
 * namespaces are something else, and the keyword {@code import} is reserved for
 * that.
 *
 * <p>The order is <b>alphabetical by file name</b> and not that of creation.
 * Otherwise the order of the declarations would hang on the order in which the
 * storage format returns them — and a waiting sequence would compare itself,
 * after a server restart, against a differently sorted program.
 */
public record Project(Map<String, String> files) {

    /** The file that always exists. */
    public static final String MAIN = "main.mf";

    /**
     * What passes as a file name.
     *
     * <p><b>Strict, because the name ends up in three worlds:</b> in the storage
     * format, in the file system next to the world, and over the wire.
     * Lowercase, so that two files are not called different on one system and
     * the same on the next.
     *
     * <p><b>Folders are allowed</b>, as sections with a slash in front:
     * {@code erz/brecher.mf}. They are pure structure for the human — all files
     * still share one namespace, and an {@code fn} in {@code erz/brecher.mf}
     * calls one in {@code main.mf} without a detour.
     *
     * <p><b>The dot is not in the alphabet of a section.</b> That makes
     * {@code ../} not forbidden but impossible, and the same holds for the
     * Windows backslash and a drive's colon. A blocklist could have been gotten
     * around; an alphabet cannot.
     */
    private static final Pattern NAME =
            Pattern.compile("([a-z0-9_]{1,32}/)*[a-z0-9_]{1,32}\\.mf");

    /**
     * And how long the whole path is at most.
     *
     * <p>Previously the limit lay at thirty-five characters, because a name
     * consisted of exactly one section. Without a new one, the storage format,
     * the package over the wire, and the path in the file system would grow with
     * every level — and that path has a limit of its own on Windows.
     */
    private static final int MAX_NAME = 96;

    public Project {
        Map<String, String> sorted = new TreeMap<>();
        files.forEach((name, source) -> {
            if (isValidName(name)) {
                sorted.put(name, source == null ? "" : source);
            }
        });
        if (sorted.isEmpty()) {
            sorted.put(MAIN, "");
        }
        files = Map.copyOf(sorted);
    }

    /** A project from a single file — the previous state. */
    public static Project of(String source) {
        return new Project(Map.of(MAIN, source == null ? "" : source));
    }

    public static boolean isValidName(String name) {
        return name != null && name.length() <= MAX_NAME && NAME.matcher(name).matches();
    }

    /** The file names, alphabetically. */
    public List<String> names() {
        return new ArrayList<>(new TreeMap<>(files).keySet());
    }

    public String source(String name) {
        return files.getOrDefault(name, "");
    }

    /** The same project with a changed or new file. */
    public Project with(String name, String source) {
        Map<String, String> next = new HashMap<>(files);
        next.put(name, source);
        return new Project(next);
    }

    /**
     * The same project under a different file name.
     *
     * <p>The content moves along, the order results anew from the alphabet. If
     * the new name is invalid or already taken, everything stays as it was — the
     * rename is then an input the screen does not accept in the first place, and
     * here stands only the second safeguard.
     */
    public Project renamed(String from, String to) {
        if (!files.containsKey(from) || !isValidName(to) || files.containsKey(to)) {
            return this;
        }
        Map<String, String> next = new HashMap<>(files);
        next.put(to, next.remove(from));
        return new Project(next);
    }

    /**
     * A free name in the manner of the given one.
     *
     * <p>For duplicating and for new files: {@code worker.mf} becomes
     * {@code worker2.mf}, and if that already exists, {@code worker3.mf}. A digit
     * at the end of the name is counted on and not appended — otherwise the copy
     * of {@code worker2.mf} would be called {@code worker222.mf} after the third
     * time.
     *
     * <p><b>Counting happens in the last section.</b> The copy of
     * {@code erz/brecher.mf} is called {@code erz/brecher2.mf} and thus stays
     * next to its original. A digit on the folder would pull it into a folder
     * that does not exist.
     */
    public String freeNameLike(String name) {
        String base = name.endsWith(".mf") ? name.substring(0, name.length() - 3) : name;
        int cut = base.lastIndexOf('/') + 1;
        String folder = base.substring(0, cut);
        String file = base.substring(cut);
        int end = file.length();
        while (end > 1 && Character.isDigit(file.charAt(end - 1))) {
            end--;
        }
        String stem = folder + file.substring(0, end);
        for (int number = 2; number < 1000; number++) {
            String candidate = stem + number + ".mf";
            if (isValidName(candidate) && !files.containsKey(candidate)) {
                return candidate;
            }
        }
        return MAIN;
    }

    /**
     * The same project without this file.
     *
     * <p>The last one cannot be deleted — a project without a file would be no
     * project, and the constructor would immediately create one again.
     */
    public Project without(String name) {
        if (files.size() <= 1) {
            return this;
        }
        Map<String, String> next = new HashMap<>(files);
        next.remove(name);
        return new Project(next);
    }

    /** The whole source text in one piece — for anything that only wants to read. */
    public String joined() {
        StringBuilder out = new StringBuilder();
        for (String name : names()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(source(name));
        }
        return out.toString();
    }

    /**
     * Compiles all files and merges them into one program.
     *
     * <p>Each file on its own, so that a line number means the line <b>in its
     * file</b>. If the compiler got the concatenated text, every error from the
     * second file on would point at a line that does not exist there.
     */
    public Parser.ParseResult parse() {
        return parse(NetworkView.NONE);
    }

    /**
     * The same, but with a look at what really stands in the network.
     *
     * <p>This catches what the grammar does not see: a display that does not
     * exist in the world, or a target nobody named that way. Both are
     * <b>warnings</b> — a wall you only build tomorrow, you may already write
     * into the program today.
     *
     * <p>The names the program assigns itself — groups, multiblocks — are
     * collected over all files beforehand. Otherwise a group in
     * {@code gruppen.mf} would report as unknown as soon as a worker in
     * {@code worker.mf} uses it.
     */
    public Parser.ParseResult parse(NetworkView view) {
        List<Decl> declarations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> owners = new HashMap<>();
        Map<String, Parser.ParseResult> parsed = new java.util.LinkedHashMap<>();
        for (String name : names()) {
            parsed.put(name, Parser.parse(source(name)));
        }
        java.util.Set<String> local = new java.util.HashSet<>();
        parsed.values().forEach(
                result -> local.addAll(NetworkCheck.localNames(result.program())));
        // The same for the global values: they apply across all files, and an
        // assignment in one means the declaration in another.
        Map<String, String> globals = new HashMap<>();
        parsed.values().forEach(
                result -> globals.putAll(GlobalCheck.declaredKinds(result.program())));
        Map<String, String> constants = new HashMap<>();
        parsed.values().forEach(
                result -> constants.putAll(GlobalCheck.declaredConstants(result.program())));
        // And for the events: an on in one file means the event in another.
        Map<String, Integer> events = new HashMap<>();
        parsed.values().forEach(
                result -> events.putAll(EventCheck.declaredEvents(result.program())));
        // And for the filter templates: a template in one file must not be
        // called like a group in another — both stand in expressions.
        Map<String, String> takenNames = new HashMap<>();
        parsed.values().forEach(
                result -> takenNames.putAll(FilterCheck.declaredNames(result.program())));

        for (String name : names()) {
            Parser.ParseResult result = parsed.get(name);
            result.diagnostics().forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            NetworkCheck.run(result.program(), view, local).forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            GlobalCheck.run(result.program(), globals, constants).forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            EventCheck.run(result.program(), events).forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            FilterCheck.run(result.program(), takenNames).forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            for (Decl declaration : result.program().declarations()) {
                // A custom function called like a built-in one would never be
                // reachable: the interpreter checks the built-in ones first.
                // To accept that silently would mean an fn stands there and
                // never runs — the same error as an on with a mistyped event
                // name.
                if (declaration instanceof Decl.Fn
                        && Signatures.FREE_FUNCTIONS.stream()
                                .anyMatch(free -> free.name().equals(declaration.name()))) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                            declaration.span(),
                            "„" + declaration.name() + "“ ist eingebaut.",
                            "Es schreibt ins Protokoll. Nenn deine Funktion anders — "
                                    + "sonst stünde sie da und liefe nie.",
                            name));
                    continue;
                }
                String taken = duplicateOf(declaration, owners, name);
                if (taken != null) {
                    diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR,
                            declaration.span(),
                            "„" + declaration.name() + "“ steht schon in " + taken + ".",
                            "Alle Dateien eines Projekts teilen einen Namensraum. "
                                    + "Zwei gleiche Namen sind einer zu viel.",
                            name));
                    continue;
                }
                declarations.add(declaration);
            }
        }
        return new Parser.ParseResult(new Program(List.copyOf(declarations)),
                List.copyOf(diagnostics));
    }

    /**
     * Remembers whom a name belongs to, and reports the second claimant.
     *
     * <p>Not for {@code on}: two blocks for the same event are allowed and both
     * run. And not for broken declarations — those already have a message.
     */
    private static String duplicateOf(Decl declaration, Map<String, String> owners,
                                      String file) {
        if (declaration instanceof Decl.On || declaration instanceof Decl.Invalid) {
            return null;
        }
        String key = declaration.getClass().getSimpleName() + " " + declaration.name();
        String previous = owners.putIfAbsent(key, file);
        return previous;
    }
}
