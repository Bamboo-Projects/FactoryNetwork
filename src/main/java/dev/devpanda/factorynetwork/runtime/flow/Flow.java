package dev.devpanda.factorynetwork.runtime.flow;

import dev.devpanda.factorynetwork.lang.ast.Block;
import dev.devpanda.factorynetwork.runtime.Value;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein Ablauf, der warten kann.
 *
 * <p><b>Das ist die Zusage, um derentwillen die Mod gebaut wird:</b> Ein
 * Ablauf, der auf ein Ereignis wartet, macht nach einem Serverneustart genau
 * dort weiter. Möglich ist das, weil sein Zustand nicht im Aufrufstapel von
 * Java steht, sondern in Rahmen, die sich aufschreiben lassen.
 *
 * <p>Nicht jede Funktion braucht das. Wer nur rechnet und Gegenstände bewegt,
 * läuft durch den gewöhnlichen Interpreter — schneller und ohne Buchführung.
 * Erst {@code await} und {@code sleep} machen einen Ablauf daraus.
 */
public final class Flow {

    public enum Status {
        /** Läuft und will Schritte machen. */
        RUNNING,
        /**
         * Angestellt: Es fehlt ein freies Rechenwerk.
         *
         * <p>Nicht abgelehnt, sondern verschoben. <b>Verzögerung ist
         * wiederherstellbar, Verlust nicht</b> — ein abgelehntes
         * {@code device_changed} ist für immer weg, und die Gegenstände
         * stehen bis zum nächsten Neustart in einer Maschine, die niemand
         * mehr anfasst.
         */
        QUEUED,
        /** Wartet auf eine bestimmte Spielzeit. */
        SLEEPING,
        /** Wartet auf ein Ereignis. */
        AWAITING,
        /** Zu Ende. */
        DONE,
        /** Abgebrochen, mit Grund. */
        FAILED,
        /**
         * Das Programm hat sich geändert, während dieser Ablauf wartete.
         *
         * <p>Er wird nicht heimlich fortgesetzt und nicht heimlich verworfen,
         * sondern erscheint im Terminal mit der Wahl: abbrechen oder
         * weiterlaufen lassen. So wurde es festgelegt.
         */
        STALE
    }

    private final long id;
    private final String entryPoint;
    private final Deque<Frame> stack = new ArrayDeque<>();

    private Status status = Status.RUNNING;
    private String detail = "";
    private Value result = Value.Nothing.get();

    /** Worauf gewartet wird. */
    private long wakeAt = -1;
    private String awaitedEvent;
    private long awaitDeadline = -1;
    private String awaitResultName;

    public Flow(long id, String entryPoint) {
        this.id = id;
        this.entryPoint = entryPoint;
    }

    public long id() {
        return id;
    }

    /** Die Funktion oder der Ereignisblock, mit dem er begann. */
    public String entryPoint() {
        return entryPoint;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public Value result() {
        return result;
    }

    public Deque<Frame> stack() {
        return stack;
    }

    public Frame top() {
        return stack.peek();
    }

    public void push(Frame frame) {
        stack.push(frame);
    }

    public Frame pop() {
        return stack.pop();
    }

    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    // ---- Zustandswechsel --------------------------------------------------

    public void sleepUntil(long gameTime) {
        status = Status.SLEEPING;
        wakeAt = gameTime;
        detail = "schläft";
    }

    public void awaitEvent(String event, long deadline, String resultName) {
        status = Status.AWAITING;
        awaitedEvent = event;
        awaitDeadline = deadline;
        awaitResultName = resultName;
        detail = "wartet auf " + event;
    }

    public void finish(Value value) {
        status = Status.DONE;
        result = value;
        detail = "fertig";
    }

    public void fail(String reason) {
        status = Status.FAILED;
        detail = reason;
    }

    /** Der Ablauf wartete, als das Programm gewechselt wurde. */
    /** Stellt den Ablauf an: Es fehlt ein Platz. */
    public void queue(String reason) {
        status = Status.QUEUED;
        detail = reason;
    }

    /** Holt ihn wieder aus der Warteschlange. */
    public void dequeue() {
        status = Status.RUNNING;
        detail = "";
    }

    public void markStale() {
        status = Status.STALE;
        detail = "Programm geändert, während dieser Ablauf wartete";
    }

    /**
     * Nimmt einen {@code STALE}-Ablauf wieder auf.
     *
     * <p>Die Wahl des Spielers: weiterlaufen lassen statt abbrechen. Wohin er
     * zurückkehrt, steht in seinen eigenen Feldern — worauf er wartete, ist
     * beim Anhalten nicht verloren gegangen.
     */
    public void unstale(int structureHash) {
        this.structureHash = structureHash;
        if (awaitedEvent != null) {
            status = Status.AWAITING;
            detail = "wartet auf " + awaitedEvent;
        } else if (wakeAt >= 0) {
            status = Status.SLEEPING;
            detail = "schläft";
        } else {
            status = Status.RUNNING;
            detail = "";
        }
    }

    public void resume() {
        status = Status.RUNNING;
        awaitedEvent = null;
        awaitDeadline = -1;
        wakeAt = -1;
        detail = "";
    }

    /**
     * Die Gestalt des Programms, mit dem dieser Ablauf begann.
     *
     * <p>Zeigt der Ablauf nach einem Neustart oder einem neuen Programm noch
     * auf dieselben Stellen? Diese Zahl beantwortet das. Stimmt sie nicht
     * mehr, wird der Ablauf {@code STALE} — nicht heimlich fortgesetzt und
     * nicht heimlich verworfen.
     */
    private int structureHash;

    public int structureHash() {
        return structureHash;
    }

    public void setStructureHash(int structureHash) {
        this.structureHash = structureHash;
    }

    /** Setzt den gespeicherten Wartezustand zurück in den Ablauf. */
    public void restore(Status status, String detail, Value result, long wakeAt,
            String awaitedEvent, long awaitDeadline, String awaitResultName) {
        this.status = status;
        this.detail = detail;
        this.result = result;
        this.wakeAt = wakeAt;
        this.awaitedEvent = awaitedEvent;
        this.awaitDeadline = awaitDeadline;
        this.awaitResultName = awaitResultName;
    }

    // ---- Warten -----------------------------------------------------------

    /** Ist die Zeit gekommen, auf die gewartet wurde? */
    public boolean isDue(long gameTime) {
        return status == Status.SLEEPING && gameTime >= wakeAt;
    }

    /** Wartet dieser Ablauf auf dieses Ereignis? */
    public boolean waitsFor(String event) {
        return status == Status.AWAITING && event.equals(awaitedEvent);
    }

    /** Ist die Frist abgelaufen? */
    public boolean hasTimedOut(long gameTime) {
        return status == Status.AWAITING && awaitDeadline >= 0 && gameTime >= awaitDeadline;
    }

    public String awaitedEvent() {
        return awaitedEvent;
    }

    public long awaitDeadline() {
        return awaitDeadline;
    }

    public long wakeAt() {
        return wakeAt;
    }

    /** Unter welchem Namen das Ergebnis des Wartens abgelegt wird. */
    public String awaitResultName() {
        return awaitResultName;
    }

    /** Zu welcher Anlage der Ablauf gerade gehört, oder leer. */
    public String devicePrefix() {
        Frame frame = top();
        return frame == null ? "" : frame.devicePrefix();
    }

    /** Legt einen Wert in den obersten Rahmen — das Ergebnis eines await. */
    public void bind(String name, Value value) {
        if (name != null && top() != null) {
            top().locals().put(name, value);
        }
    }

    /**
     * Sucht einen Namen von innen nach außen.
     *
     * <p>Dieselbe Regel wie im gewöhnlichen Interpreter: Der innerste Rahmen
     * gewinnt. Nur steht der Stapel hier als Liste da, statt in Javas
     * Aufrufen zu stecken.
     */
    public Value find(String name) {
        for (Frame frame : stack) {
            Value value = frame.locals().get(name);
            if (value != null) {
                return value;
            }
            if (frame.isCall()) {
                // Weiter unten liegt der Rufende. Seine Namen gehen die
                // gerufene Funktion nichts an — sonst hinge das Verhalten
                // davon ab, wer sie gerade aufruft.
                return null;
            }
        }
        return null;
    }

    public boolean assign(String name, Value value) {
        for (Frame frame : stack) {
            if (frame.locals().containsKey(name)) {
                frame.locals().put(name, value);
                return true;
            }
            if (frame.isCall()) {
                return false;
            }
        }
        return false;
    }

    /** Alle sichtbaren Namen — für das Aufschreiben und die Anzeige. */
    public Map<String, Value> visibleLocals() {
        Map<String, Value> all = new LinkedHashMap<>();
        stack.descendingIterator().forEachRemaining(frame -> all.putAll(frame.locals()));
        return all;
    }

    @Override
    public String toString() {
        return entryPoint + "#" + id + " " + status + (detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
