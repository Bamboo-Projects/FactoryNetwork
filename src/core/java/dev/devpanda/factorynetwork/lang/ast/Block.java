package dev.devpanda.factorynetwork.lang.ast;

import dev.devpanda.factorynetwork.lang.Span;

import java.util.List;

/** A sequence of statements in curly braces. */
public record Block(List<Stmt> statements, Span span) {
}
