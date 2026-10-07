package dev.devpanda.factorynetwork.lang.ast;

import java.util.List;

/**
 * A compiled program.
 *
 * <p>All {@code .mf} files of a project form one namespace, so there are no
 * file boundaries here anymore — the compiler merges the declarations of all
 * files.
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

    public List<Decl.Event> events() {
        return declarations.stream().filter(Decl.Event.class::isInstance)
                .map(Decl.Event.class::cast).toList();
    }

    /** The declaration of an event, or {@code null}. */
    public Decl.Event event(String name) {
        return events().stream().filter(candidate -> candidate.name().equals(name))
                .findFirst().orElse(null);
    }
}
