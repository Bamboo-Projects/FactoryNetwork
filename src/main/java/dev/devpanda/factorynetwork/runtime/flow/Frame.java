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

    // ---- Aufruf einer eigenen Funktion ------------------------------------

    private boolean call;
    private String resultName;
    private String devicePrefix = "";

    /**
     * Macht diesen Rahmen zum Rumpf eines Aufrufs.
     *
     * <p>Ein {@code return} darin beendet nicht den ganzen Ablauf, sondern nur
     * diesen Rahmen — der Wert landet im rufenden unter {@code resultName}.
     */
    public void beginCall(String resultName, String devicePrefix) {
        this.call = true;
        this.resultName = resultName;
        this.devicePrefix = devicePrefix == null ? "" : devicePrefix;
    }

    public boolean isCall() {
        return call;
    }

    public String resultName() {
        return resultName;
    }

    /**
     * Vor welchen Gerätenamen dieser Rahmen etwas setzt.
     *
     * <p>In einer Vorlage heißt ein Gerät {@code crusher}; in der Welt trägt
     * es den Namen der Anlage davor. Der Rahmen weiß, zu welcher Anlage er
     * gehört — sonst wüsste ein Ablauf nach einem Neustart nicht mehr, welche
     * der drei Erzanlagen er bedient.
     */
    public String devicePrefix() {
        return devicePrefix;
    }

    public void setDevicePrefix(String devicePrefix) {
        this.devicePrefix = devicePrefix == null ? "" : devicePrefix;
    }

    // ---- Lauf über eine Liste ---------------------------------------------

    private String iterationVariable;
    private java.util.List<Value> iterationValues = java.util.List.of();
    private int iterationIndex;

    /**
     * Macht diesen Rahmen zum Rumpf eines {@code for}.
     *
     * <p>Der Stand steht damit im Rahmen und nicht im Programm — anders als
     * bei {@code while}, wo die Bedingung jede Runde neu geprüft wird. Nur so
     * lässt sich ein {@code for}, das mitten in der Liste auf ein Ereignis
     * wartet, aufschreiben und fortsetzen.
     */
    public void beginIteration(String variable, java.util.List<Value> values) {
        this.iterationVariable = variable;
        this.iterationValues = java.util.List.copyOf(values);
        this.iterationIndex = 0;
        bindCurrent();
    }

    /** Nur zum Zurücklesen: Stand mitten in der Liste. */
    public void restoreIteration(String variable, java.util.List<Value> values, int index) {
        this.iterationVariable = variable;
        this.iterationValues = java.util.List.copyOf(values);
        this.iterationIndex = index;
    }

    public boolean hasIteration() {
        return iterationVariable != null;
    }

    public String iterationVariable() {
        return iterationVariable;
    }

    public java.util.List<Value> iterationValues() {
        return iterationValues;
    }

    public int iterationIndex() {
        return iterationIndex;
    }

    /**
     * Rückt auf den nächsten Eintrag vor.
     *
     * @return ob es noch einen gab
     */
    public boolean nextIteration() {
        if (!hasIteration() || iterationIndex + 1 >= iterationValues.size()) {
            return false;
        }
        iterationIndex++;
        bindCurrent();
        return true;
    }

    private void bindCurrent() {
        if (iterationIndex < iterationValues.size()) {
            locals.put(iterationVariable, iterationValues.get(iterationIndex));
        }
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
