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
     * Kleinbuchstaben, damit zwei Dateien nicht auf einem System verschieden
     * und auf dem nächsten gleich heißen.
     *
     * <p><b>Ordner sind erlaubt</b>, als Abschnitte mit Schrägstrich davor:
     * {@code erz/brecher.mf}. Sie sind reine Gliederung für den Menschen —
     * alle Dateien teilen weiterhin einen Namensraum, und ein {@code fn} in
     * {@code erz/brecher.mf} ruft eines in {@code main.mf} ohne Umweg.
     *
     * <p><b>Der Punkt steht nicht im Alphabet eines Abschnitts.</b> Damit ist
     * {@code ../} nicht verboten, sondern unmöglich, und dasselbe gilt für
     * den Rückstrich von Windows und den Doppelpunkt eines Laufwerks. Eine
     * Verbotsliste hätte man umgehen können; ein Alphabet nicht.
     */
    private static final Pattern NAME =
            Pattern.compile("([a-z0-9_]{1,32}/)*[a-z0-9_]{1,32}\\.mf");

    /**
     * Und wie lang der ganze Pfad höchstens ist.
     *
     * <p>Vorher lag die Grenze bei fünfunddreißig Zeichen, weil ein Name aus
     * genau einem Abschnitt bestand. Ohne eine neue wüchse mit jeder Ebene
     * das Speicherformat, das Paket über die Leitung und der Pfad im
     * Dateisystem — und der hat auf Windows selbst eine Grenze.
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

    /** Ein Projekt aus einer einzelnen Datei — der bisherige Zustand. */
    public static Project of(String source) {
        return new Project(Map.of(MAIN, source == null ? "" : source));
    }

    public static boolean isValidName(String name) {
        return name != null && name.length() <= MAX_NAME && NAME.matcher(name).matches();
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
     * Dasselbe Projekt unter einem anderen Dateinamen.
     *
     * <p>Der Inhalt zieht mit, die Reihenfolge ergibt sich neu aus dem
     * Alphabet. Ist der neue Name ungültig oder schon vergeben, bleibt alles
     * wie es war — das Umbenennen ist dann eine Eingabe, die der Bildschirm
     * gar nicht erst annimmt, und hier steht nur die zweite Sicherung.
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
     * Ein freier Name in der Art des gegebenen.
     *
     * <p>Für das Verdoppeln und für neue Dateien: {@code worker.mf} wird zu
     * {@code worker2.mf}, und wenn es die schon gibt, zu {@code worker3.mf}.
     * Eine Ziffer am Ende des Namens wird dabei weitergezählt und nicht
     * angehängt — sonst hieße die Kopie von {@code worker2.mf} nach dem
     * dritten Mal {@code worker222.mf}.
     *
     * <p><b>Gezählt wird im letzten Abschnitt.</b> Die Kopie von
     * {@code erz/brecher.mf} heißt {@code erz/brecher2.mf} und bleibt damit
     * neben ihrer Vorlage. Eine Ziffer am Ordner zöge sie in einen Ordner,
     * den es nicht gibt.
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
        return parse(NetworkView.NONE);
    }

    /**
     * Dasselbe, aber mit einem Blick auf das, was wirklich im Netz steht.
     *
     * <p>Damit fällt auf, was die Grammatik nicht sieht: eine Anzeige, die es
     * in der Welt nicht gibt, oder ein Ziel, das niemand so genannt hat.
     * Beides sind <b>Warnungen</b> — eine Wand, die man erst morgen baut,
     * darf man heute schon ins Programm schreiben.
     *
     * <p>Die Namen, die das Programm selbst vergibt — Gruppen, Multiblocks —
     * werden vorher über alle Dateien gesammelt. Sonst meldete eine Gruppe in
     * {@code gruppen.mf} als unbekannt, sobald ein Worker in
     * {@code worker.mf} sie benutzt.
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
        // Dasselbe für die globalen Werte: Sie gelten über alle Dateien, und
        // eine Zuweisung in der einen meint die Erklärung in der anderen.
        Map<String, String> globals = new HashMap<>();
        parsed.values().forEach(
                result -> globals.putAll(GlobalCheck.declaredKinds(result.program())));
        Map<String, String> constants = new HashMap<>();
        parsed.values().forEach(
                result -> constants.putAll(GlobalCheck.declaredConstants(result.program())));
        // Und für die Ereignisse: Ein on in der einen Datei meint das event in
        // der anderen.
        Map<String, Integer> events = new HashMap<>();
        parsed.values().forEach(
                result -> events.putAll(EventCheck.declaredEvents(result.program())));
        // Und für die Filter-Vorlagen: Eine Vorlage in der einen Datei darf
        // nicht heißen wie eine Gruppe in der anderen — beide stehen in
        // Ausdrücken.
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
                // Eine eigene Funktion, die wie eine eingebaute heißt, wäre
                // nie erreichbar: Der Interpreter prüft die eingebauten
                // zuerst. Das still hinzunehmen hieße, dass ein fn dasteht
                // und nie läuft — derselbe Fehler wie ein on mit vertipptem
                // Ereignisnamen.
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
