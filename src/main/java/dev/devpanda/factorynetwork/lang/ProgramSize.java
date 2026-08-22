package dev.devpanda.factorynetwork.lang;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;

/**
 * Wie groß ein Programm ist, gemessen in Anweisungen.
 *
 * <p>Der Datenträger im Serverschrank begrenzt diese Zahl. <b>Gezählt werden
 * Anweisungen und nicht Zeichen</b>: Kommentare, Einrückung und lange Namen
 * kosten nichts. Eine Sprache, in der Erklären teuer ist, wird nicht
 * erklärt — und ein Programm, das an der Grenze steht, soll man kommentieren
 * dürfen, nicht kürzen müssen.
 *
 * <p>Eine Deklaration zählt selbst mit: Ein leerer Worker ist nicht nichts,
 * er läuft. Und ein verschachtelter Block zählt seine Anweisungen dazu, denn
 * eine Schleife mit zehn Zeilen ist zehnmal so viel Programm wie eine mit
 * einer.
 */
public final class ProgramSize {

    private ProgramSize() {
    }

    /** Wie viele Anweisungen das Programm hat. */
    public static int of(Program program) {
        int total = 0;
        for (Decl declaration : program.declarations()) {
            total += of(declaration);
        }
        return total;
    }

    private static int of(Decl declaration) {
        // Jede Deklaration zählt selbst, dazu ihr Inhalt.
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
