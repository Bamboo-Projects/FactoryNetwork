package dev.devpanda.factorynetwork.lang.ast;

import java.util.List;

/**
 * Ein übersetztes Programm.
 *
 * <p>Alle {@code .mf}-Dateien eines Projekts bilden einen Namensraum, deshalb
 * gibt es hier keine Dateigrenzen mehr — der Übersetzer legt die
 * Deklarationen aller Dateien zusammen.
 */
public record Program(List<Decl> declarations) {

    public List<Decl.Worker> workers() {
        return declarations.stream().filter(Decl.Worker.class::isInstance)
                .map(Decl.Worker.class::cast).toList();
    }

    public List<Decl.Fn> functions() {
        return declarations.stream().filter(Decl.Fn.class::isInstance)
                .map(Decl.Fn.class::cast).toList();
    }

    public List<Decl.On> handlers() {
        return declarations.stream().filter(Decl.On.class::isInstance)
                .map(Decl.On.class::cast).toList();
    }
}
