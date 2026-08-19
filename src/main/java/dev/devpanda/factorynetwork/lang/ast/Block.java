package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/** Eine Folge von Anweisungen in geschweiften Klammern. */
public record Block(List<Stmt> statements, Span span) {
}
