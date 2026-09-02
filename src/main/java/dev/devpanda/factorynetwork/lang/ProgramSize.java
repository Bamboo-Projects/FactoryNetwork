package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;

/**
 * How large a program is, measured in statements.
 *
 * <p>The storage medium in the server rack limits this number. <b>Statements
 * are counted, not characters</b>: comments, indentation, and long names cost
 * nothing. A language in which explaining is expensive does not get explained —
 * and a program that sits at the limit should be one you are allowed to
 * comment, not forced to shorten.
 *
 * <p>A declaration counts itself: an empty worker is not nothing, it runs. And
 * a nested block adds its statements, because a loop with ten lines is ten
 * times as much program as one with a single line.
 */
public final class ProgramSize {

    private ProgramSize() {
    }

    /** How many statements the program has. */
    public static int of(Program program) {
        int total = 0;
        for (Decl declaration : program.declarations()) {
            total += of(declaration);
        }
        return total;
    }

    private static int of(Decl declaration) {
        // Each declaration counts itself, plus its content.
        return 1 + switch (declaration) {
            case Decl.Fn fn -> of(fn.body());
            case Decl.On on -> of(on.body());
            case Decl.Worker worker -> worker.entries().size();
            case Decl.Display display -> display.entries().size();
            case Decl.Group group -> group.members().size();
            case Decl.Multiblock multiblock -> {
                int inner = multiblock.devices().size();
                for (Decl.Fn function : multiblock.functions()) {
                    inner += of(function);
                }
                yield inner;
            }
            case Decl.Event event -> event.parameters().size();
            // A recipe costs its lines: one per ingredient and one per result.
            case Decl.Recipe recipe -> recipe.inputs().size() + recipe.outputs().size();
            // A store costs its entries. The one in front counts it itself; a
            // store with no braces content is thus not free, because it costs
            // the network one inventory read per tick.
            case Decl.Store store -> (store.filter() == null ? 0 : 1)
                    + (store.priority() == 0 ? 0 : 1);
            // As with a group: one per line. A template of twenty selectors is
            // not something the server carries along on the side.
            case Decl.FilterTemplate template ->
                    template.includes().size() + template.excludes().size();
            // A global value costs its one line and nothing more: its initial
            // value is a literal, not a statement. Zero would still be wrong —
            // the one in front counts it, and so a thousand global values are
            // not free.
            case Decl.Global ignored -> 0;
            // A constant costs its line like a global value. The one in front
            // counts it; a thousand constants are not free.
            case Decl.Const ignored -> 0;
            case Decl.Invalid ignored -> 0;
        };
    }

    private static int of(Block block) {
        if (block == null) {
            return 0;
        }
        int total = 0;
        for (Stmt statement : block.statements()) {
            total += of(statement);
        }
        return total;
    }

    private static int of(Stmt statement) {
        return 1 + switch (statement) {
            case Stmt.If branch -> of(branch.thenBody())
                    + of(branch.elseBlock())
                    + (branch.elseIf() == null ? 0 : of(branch.elseIf()));
            case Stmt.For loop -> of(loop.body());
            case Stmt.While loop -> of(loop.body());
            default -> 0;
        };
    }
}
