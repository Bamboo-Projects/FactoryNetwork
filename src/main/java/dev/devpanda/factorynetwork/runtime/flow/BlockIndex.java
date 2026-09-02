package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.lang.ast.Decl;
import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.lang.ast.Program;
import dev.devpanda.factorynetwork.lang.ast.Stmt;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives every block of a program a number.
 *
 * <p>A waiting flow has to record which block it is in. A block is an object
 * in memory and cannot be persisted — its number can. On loading, the number
 * turns back into the same block, as long as the program is the same.
 *
 * <p>Exactly this "as long as" is what the source hash guards: if the program
 * changes, the numbers shift, and the flow is not silently resumed in the
 * wrong place but reported as {@code STALE}.
 *
 * <p>The walk through the tree is fixed: declarations in program order,
 * within them statements top to bottom, and for each statement first its own
 * block, then the ones nested inside. As long as this walk stays the same, so
 * do the numbers.
 */
public final class BlockIndex {

    private final Map<Block, Integer> numbers = new IdentityHashMap<>();
    private final List<Block> blocks = new ArrayList<>();

    private BlockIndex() {
    }

    public static BlockIndex of(Program program) {
        BlockIndex index = new BlockIndex();
        for (Decl declaration : program.declarations()) {
            switch (declaration) {
                case Decl.Fn function -> index.walk(function.body());
                case Decl.On handler -> index.walk(handler.body());
                // The functions of a template count too. Without them, a flow
                // inside one would get no number — and would vanish silently
                // when persisted instead of speaking up.
                case Decl.Multiblock template -> template.functions()
                        .forEach(function -> index.walk(function.body()));
                default -> {
                    // Workers, groups, displays have no statement blocks in
                    // which a flow could be standing.
                }
            }
        }
        return index;
    }

    /** The number of a block, or -1 if it does not belong to this program. */
    public int id(Block block) {
        Integer number = numbers.get(block);
        return number == null ? -1 : number;
    }

    /** The block for a number, or {@code null}. */
    public Block block(int id) {
        return id >= 0 && id < blocks.size() ? blocks.get(id) : null;
    }

    public int size() {
        return blocks.size();
    }

    /**
     * A number that changes exactly when the block numbers change.
     *
     * <p>A waiting flow points at blocks with numbers and at a statement
     * inside one with a counter. Both stay valid as long as the count and
     * kind of statements stay the same — comments, indentation and changed
     * numbers in a body shift nothing. An inserted line, by contrast, shifts
     * everything that follows it.
     *
     * <p>That is why this number guards resumption rather than a hash of the
     * source text: otherwise an added comment would force every waiting flow
     * into a confirmation prompt, even though it could carry on.
     *
     * <p>The name of an awaited event counts too. If {@code await Fertig}
     * becomes {@code await Abgebrochen}, the structure is the same, but the
     * flow was waiting for something other than what the program now means.
     */
    public int structureHash() {
        int hash = 17;
        for (Block block : blocks) {
            hash = hash * 31 + block.statements().size();
            for (Stmt statement : block.statements()) {
                hash = hash * 31 + statement.getClass().getSimpleName().hashCode();
                String event = awaitedEventOf(statement);
                if (event != null) {
                    hash = hash * 31 + event.hashCode();
                }
            }
        }
        return hash;
    }

    private static String awaitedEventOf(Stmt statement) {
        Expr expr = switch (statement) {
            case Stmt.Let let -> let.value();
            case Stmt.ExprStmt wrapper -> wrapper.expr();
            default -> null;
        };
        return expr instanceof Expr.Await await ? await.eventName() : null;
    }

    private void walk(Block block) {
        if (block == null || numbers.containsKey(block)) {
            return;
        }
        numbers.put(block, blocks.size());
        blocks.add(block);
        for (Stmt statement : block.statements()) {
            walk(statement);
        }
    }

    private void walk(Stmt statement) {
        switch (statement) {
            case Stmt.If branch -> {
                walk(branch.thenBody());
                walk(branch.elseBlock());
                if (branch.elseIf() != null) {
                    walk(branch.elseIf());
                }
            }
            case Stmt.For loop -> walk(loop.body());
            case Stmt.While loop -> walk(loop.body());
            case Stmt.Let let -> walkAwait(let.value());
            case Stmt.ExprStmt expr -> walkAwait(expr.expr());
            default -> {
                // Nothing else opens a block.
            }
        }
    }

    /** The else branch of an {@code await} is a block as well. */
    private void walkAwait(Expr expr) {
        if (expr instanceof Expr.Await await) {
            walk(await.elseBody());
        }
    }
}
