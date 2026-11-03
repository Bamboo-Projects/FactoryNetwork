package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;
import dev.devpanda.factorynetwork.lang.parse.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which statements of the running program a breakpoint on a line of a file
 * means.
 *
 * <p>A line number alone says nothing: every file has a line 5. The file is
 * parsed on its own to find the function or {@code on} block the line lies
 * in; that one is looked up by name in the running program, and in it the
 * statements that begin on that line. Line numbers count per file, so they
 * match.
 */
public final class Breakpoints {

    private Breakpoints() {
    }

    /** The statements of {@code running} on these lines of this file's source. */
    public static List<Object> resolve(Program running, String source, Set<Integer> lines) {
        List<Object> found = new ArrayList<>();
        if (source == null || lines.isEmpty()) {
            return found;
        }
        Program alone = Parser.parse(source).program();
        for (int line : lines) {
            for (Decl declaration : alone.declarations()) {
                Block body = bodyOf(declaration);
                if (body == null || !covers(declaration, body, line)) {
                    continue;
                }
                for (Decl candidate : running.declarations()) {
                    if (sameDeclaration(declaration, candidate)) {
                        collect(bodyOf(candidate), line, found);
                    }
                }
            }
        }
        return found;
    }

    private static Block bodyOf(Decl declaration) {
        return switch (declaration) {
            case Decl.Fn fn -> fn.body();
            case Decl.On on -> on.body();
            default -> null;
        };
    }

    private static boolean sameDeclaration(Decl written, Decl running) {
        return written.getClass() == running.getClass() && written.name().equals(running.name());
    }

    /** Whether the line lies between the declaration's first line and its block's last. */
    private static boolean covers(Decl declaration, Block body, int line) {
        int first = declaration.span().line();
        int last = lastLine(body, first);
        return line >= first && line <= last;
    }

    private static int lastLine(Block block, int fallback) {
        int last = fallback;
        for (Stmt statement : block.statements()) {
            last = Math.max(last, statement.span().line());
            for (Block nested : nestedBlocks(statement)) {
                last = Math.max(last, lastLine(nested, last));
            }
        }
        return last + 1;
    }

    private static void collect(Block block, int line, List<Object> found) {
        if (block == null) {
            return;
        }
        for (Stmt statement : block.statements()) {
            if (statement.span().line() == line) {
                found.add(statement);
            }
            for (Block nested : nestedBlocks(statement)) {
                collect(nested, line, found);
            }
        }
    }

    private static List<Block> nestedBlocks(Stmt statement) {
        List<Block> blocks = new ArrayList<>();
        switch (statement) {
            case Stmt.If branch -> {
                blocks.add(branch.thenBody());
                if (branch.elseBlock() != null) {
                    blocks.add(branch.elseBlock());
                }
                if (branch.elseIf() != null) {
                    blocks.addAll(nestedBlocks(branch.elseIf()));
                    blocks.add(new Block(List.of(branch.elseIf()), branch.elseIf().span()));
                }
            }
            case Stmt.For loop -> blocks.add(loop.body());
            case Stmt.While loop -> blocks.add(loop.body());
            default -> {
            }
        }
        return blocks;
    }
}
