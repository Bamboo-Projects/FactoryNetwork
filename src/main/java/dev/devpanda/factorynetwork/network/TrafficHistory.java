package dev.devpanda.factorynetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Was das Netz über die Zeit bewegt hat.
 *
 * <p><b>Auf dem Server gemessen, nicht im Fenster.</b> Ein Verlauf, den der
 * Client selbst mitschreibt, hat Lücken, sobald jemand das Fenster schließt —
 * und genau dann interessiert er sich später dafür, warum nachts nichts lief.
 *
 * <p><b>Je Sekunde ein Punkt, nicht je Tick.</b> Zwanzig Punkte je Sekunde
 * wären in einer Minute zwölfhundert: für ein Diagramm von zweihundert Pixeln
 * sinnlos und über die Leitung teuer.
 *
 * <p><b>Und der Verlauf hat ein Ende.</b> Einer, der ewig wächst, ist ein
 * Speicherleck mit Diagramm.
 */
public class TrafficHistory {

    /** Fünf Minuten Verlauf — so weit reicht die Frage „was war eben". */
    public static final int SAMPLES = 300;

    /** Ein Gerät und was es bewegt hat. */
    public record Consumer(String name, long bytes) { }

    private final Deque<Integer> samples = new ArrayDeque<>();
    private final Map<String, Long> byDevice = new LinkedHashMap<>();

    private int currentSecond;
    private int ticks;
    private long total;

    /**
     * Bucht, was ein Gerät gerade bewegt hat.
     *
     * <p>Der Name und nicht die Stelle: Ein Diagramm, das Koordinaten nennt,
     * beantwortet die Frage „was frisst am meisten" nicht — man müsste erst
     * hingehen und nachsehen.
     */
    public void record(String device, int bytes) {
        if (bytes <= 0) {
            return;
        }
        currentSecond += bytes;
        total += bytes;
        byDevice.merge(device, (long) bytes, Long::sum);
    }

    /**
     * Ein Tick vergeht.
     *
     * <p>Nach zwanzig davon wird ein Punkt geschrieben und die laufende
     * Sekunde zurückgesetzt.
     */
    public void tick() {
        ticks++;
        if (ticks < Bandwidth.TICKS_PER_SECOND) {
            return;
        }
        ticks = 0;
        samples.addLast(currentSecond);
        currentSecond = 0;
        while (samples.size() > SAMPLES) {
            samples.removeFirst();
        }
    }

    /** Der Verlauf, ältester Punkt zuerst. */
    public List<Integer> perSecond() {
        return List.copyOf(samples);
    }

    /** Was seit dem Start des Netzes insgesamt bewegt wurde. */
    public long total() {
        return total;
    }

    /** Was je Gerät bewegt wurde. */
    public Map<String, Long> byDevice() {
        return Map.copyOf(byDevice);
    }

    /**
     * Die größten Verbraucher, absteigend.
     *
     * <p>Die Frage lautet „was frisst am meisten", nicht „was gibt es".
     */
    public List<Consumer> top(int count) {
        List<Consumer> found = new ArrayList<>();
        byDevice.forEach((name, bytes) -> found.add(new Consumer(name, bytes)));
        found.sort(Comparator.comparingLong(Consumer::bytes).reversed());
        return List.copyOf(found.subList(0, Math.min(count, found.size())));
    }
}
