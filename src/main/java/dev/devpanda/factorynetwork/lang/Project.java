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
 * Das Programm eines Controllers, aufgeteilt auf mehrere Dateien.
 *
 * <p>Ein Programm von dreihundert Zeilen in einem Stück ist keine Übersicht.
 * <b>Also ein Projekt:</b> {@code worker.mf}, {@code anzeigen.mf},
 * {@code ereignisse.mf} — wie man es aufteilt, entscheidet der Spieler.
 *
 * <p><b>Alle Dateien teilen einen Namensraum.</b> Das stand seit dem ersten
 * Tag so im Übersetzer: Ein {@code fn} in der einen Datei ruft ein {@code fn}
 * in der anderen, ohne dass irgendwo {@code import} stehen muss. Dateien sind
 * hier Ordnung für den Menschen, keine Grenze für die Sprache — echte Module
 * mit eigenen Namensräumen sind etwas anderes, und dafür ist das Schlüsselwort
 * {@code import} reserviert.
 *
 * <p>Die Reihenfolge ist <b>alphabetisch nach Dateiname</b> und nicht die des
 * Anlegens. Sonst hinge die Reihenfolge der Deklarationen daran, in welcher
 * Reihenfolge das Speicherformat sie zurückgibt — und ein wartender Ablauf
 * verglichen sich nach einem Serverneustart mit einem anders sortierten
 * Programm.
 */
public record Project(Map<String, String> files) {

    /** Die Datei, die es immer gibt. */
    public static final String MAIN = "main.mf";

    /**
     * Was als Dateiname durchgeht.
     *
     * <p><b>Streng, weil der Name in drei Welten landet:</b> ins
     * Speicherformat, ins Dateisystem neben der Welt und über die Leitung.
     * Ein {@code ../} darin wäre ein Weg, aus dem Weltordner
     * herauszuschreiben. Kleinbuchstaben, damit zwei Dateien nicht auf einem
     * System verschieden und auf dem nächsten gleich heißen.
     */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}\\.mf");

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

    /** Ein Projekt aus einer einzelnen Datei — der bisherige Zustand. */
    public static Project of(String source) {
        return new Project(Map.of(MAIN, source == null ? "" : source));
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Die Dateinamen, alphabetisch. */
    public List<String> names() {
        return new ArrayList<>(new TreeMap<>(files).keySet());
    }

    public String source(String name) {
        return files.getOrDefault(name, "");
    }

    /** Dasselbe Projekt mit einer geänderten oder neuen Datei. */
    public Project with(String name, String source) {
        Map<String, String> next = new HashMap<>(files);
        next.put(name, source);
        return new Project(next);
    }

    /**
     * Dasselbe Projekt ohne diese Datei.
     *
     * <p>Die letzte lässt sich nicht löschen — ein Projekt ohne Datei wäre
     * kein Projekt, und der Konstruktor legte sofort wieder eine an.
     */
    public Project without(String name) {
        if (files.size() <= 1) {
            return this;
        }
        Map<String, String> next = new HashMap<>(files);
        next.remove(name);
        return new Project(next);
    }

    /** Der ganze Quelltext am Stück — für alles, was nur lesen will. */
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
     * Übersetzt alle Dateien und legt sie zu einem Programm zusammen.
     *
     * <p>Jede Datei für sich, damit eine Zeilennummer die Zeile <b>in ihrer
     * Datei</b> meint. Würde der Übersetzer den zusammengehängten Text
     * bekommen, zeigte jeder Fehler ab der zweiten Datei auf eine Zeile, die
     * es dort nicht gibt.
     */
    public Parser.ParseResult parse() {
        List<Decl> declarations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> owners = new HashMap<>();
        for (String name : names()) {
            Parser.ParseResult result = Parser.parse(source(name));
            result.diagnostics().forEach(
                    diagnostic -> diagnostics.add(diagnostic.withFile(name)));
            for (Decl declaration : result.program().declarations()) {
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
     * Merkt sich, wem ein Name gehört, und meldet den zweiten Anwärter.
     *
     * <p>Nicht für {@code on}: Zwei Blöcke für dasselbe Ereignis sind
     * erlaubt und laufen beide. Und nicht für kaputte Deklarationen — die
     * haben schon eine Meldung.
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
