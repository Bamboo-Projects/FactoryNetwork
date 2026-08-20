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
 * Gibt jedem Block eines Programms eine Nummer.
 *
 * <p>Ein wartender Ablauf muss aufschreiben, in welchem Block er steht. Ein
 * Block ist ein Objekt im Speicher und lässt sich nicht aufschreiben — seine
 * Nummer schon. Beim Laden wird aus der Nummer wieder derselbe Block, solange
 * das Programm dasselbe ist.
 *
 * <p>Genau dieses „solange" bewacht der Quelltext-Hash: Ändert sich das
 * Programm, verschieben sich die Nummern, und der Ablauf wird nicht heimlich
 * an falscher Stelle fortgesetzt, sondern als {@code STALE} gemeldet.
 *
 * <p>Der Weg durch den Baum liegt fest: Deklarationen in Programmreihenfolge,
 * darin Anweisungen von oben nach unten, bei jeder Anweisung erst der eigene
 * Block, dann die darin enthaltenen. Solange dieser Weg gleich bleibt,
 * bleiben es auch die Nummern.
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
                default -> {
                    // Worker, Gruppen, Anzeigen haben keine Anweisungsblöcke,
                    // in denen ein Ablauf stehen könnte.
                }
            }
        }
        return index;
    }

    /** Die Nummer eines Blocks, oder -1, wenn er nicht zu diesem Programm gehört. */
    public int id(Block block) {
        Integer number = numbers.get(block);
        return number == null ? -1 : number;
    }

    /** Der Block zu einer Nummer, oder {@code null}. */
    public Block block(int id) {
        return id >= 0 && id < blocks.size() ? blocks.get(id) : null;
    }

    public int size() {
        return blocks.size();
    }

    /**
     * Eine Zahl, die sich genau dann ändert, wenn sich die Nummern ändern.
     *
     * <p>Ein wartender Ablauf zeigt mit Nummern auf Blöcke und mit einem
     * Zähler auf eine Anweisung darin. Beides bleibt gültig, solange Anzahl
     * und Art der Anweisungen gleich bleiben — Kommentare, Einrückung und
     * geänderte Zahlen im Rumpf verschieben nichts. Eine eingefügte Zeile
     * verschiebt dagegen alles, was dahinter steht.
     *
     * <p>Deshalb bewacht diese Zahl die Fortsetzung und nicht ein Hash des
     * Quelltextes: Sonst würde ein hinzugefügter Kommentar jeden wartenden
     * Ablauf zur Nachfrage zwingen, obwohl er weiterlaufen könnte.
     *
     * <p>Der Name eines erwarteten Ereignisses zählt mit. Wird aus
     * {@code await Fertig} ein {@code await Abgebrochen}, ist die Struktur
     * gleich, der Ablauf wartete aber auf etwas anderes als das Programm
     * jetzt meint.
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
                // Alles andere öffnet keinen Block.
            }
        }
    }

    /** Der else-Zweig eines {@code await} ist ebenfalls ein Block. */
    private void walkAwait(Expr expr) {
        if (expr instanceof Expr.Await await) {
            walk(await.elseBody());
        }
    }
}
