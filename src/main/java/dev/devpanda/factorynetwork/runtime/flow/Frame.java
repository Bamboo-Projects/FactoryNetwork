package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.runtime.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein Rahmen auf dem Stapel eines Ablaufs.
 *
 * <p>Er weiß, in welchem Block er steht und bei welcher Anweisung. Das ist
 * der ganze Unterschied zum gewöhnlichen Aufrufstapel von Java: Diese Angaben
 * sind Daten und lassen sich aufschreiben. Ein Ablauf, der wartet, ist damit
 * nichts weiter als eine Liste solcher Rahmen — und die übersteht einen
 * Serverneustart.
 *
 * <p>Welcher Block das ist, wird beim Aufschreiben zur Nummer aus dem
 * {@link BlockIndex}. Der Rahmen selbst führt keine Nummer mit; sie gilt nur
 * für ein bestimmtes Programm, der Rahmen aber lebt im Speicher.
 */
public final class Frame {

    private final Block block;
    private final Map<String, Value> locals = new LinkedHashMap<>();
    private final boolean loop;
    private int index;
    private boolean exitOnLeave;

    public Frame(Block block, boolean loop) {
        this.block = block;
        this.loop = loop;
    }

    public Block block() {
        return block;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void advance() {
        index++;
    }

    /** Ist dieser Rahmen ein Schleifenrumpf? */
    public boolean isLoop() {
        return loop;
    }

    /**
     * Endet der ganze Ablauf, sobald dieser Rahmen fertig ist?
     *
     * <p>Gesetzt für den {@code else}-Zweig eines {@code await} mit Frist.
     * Die Sprache verlangt, dass dieser Zweig den Ablauf verlässt — steht
     * dort kein {@code return}, sorgt dieses Merkmal dafür, dass der Ablauf
     * trotzdem endet, statt hinter dem {@code await} mit einem Wert
     * weiterzumachen, den es nie gab.
     */
    public boolean exitOnLeave() {
        return exitOnLeave;
    }

    public void setExitOnLeave(boolean exitOnLeave) {
        this.exitOnLeave = exitOnLeave;
    }

    public boolean atEnd() {
        return index >= block.statements().size();
    }

    public Map<String, Value> locals() {
        return locals;
    }

    /** Setzt den Zähler zurück — für die nächste Runde einer Schleife. */
    public void restart() {
        index = 0;
    }
}
