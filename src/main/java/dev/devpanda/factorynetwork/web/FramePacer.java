package dev.devpanda.factorynetwork.web;

/**
 * Entscheidet, wann ein Browser wieder ein Bild malen darf.
 *
 * <p><b>Reine Zeitrechnung, ohne Minecraft und ohne Chromium</b> — und
 * deshalb prüfbar. Die Uhr kommt von außen, damit ein Prüflauf sie stellen
 * kann; im Spiel ist es {@code System.nanoTime}.
 *
 * <p>Der Takt läuft nicht mit: Wer zehn Bilder lang nicht gefragt wurde,
 * bekommt danach nicht zehn auf einmal. Ein Browser, der aus dem Blick
 * gerät und zurückkommt, soll ein Bild malen und nicht die versäumte
 * Vergangenheit nachholen.
 */
public final class FramePacer {

    private BrowserVisibility visibility;
    private long lastFrameAt = Long.MIN_VALUE;

    public FramePacer(BrowserVisibility visibility) {
        this.visibility = visibility == null ? BrowserVisibility.HIDDEN : visibility;
    }

    public BrowserVisibility visibility() {
        return visibility;
    }

    /**
     * Ändert die Sichtbarkeit.
     *
     * <p>Ein Browser, der sichtbar wird, darf sofort malen — sonst sieht der
     * Spieler beim Aufklappen bis zu eine Fünftelsekunde lang das alte Bild.
     */
    public void setVisibility(BrowserVisibility next) {
        BrowserVisibility wanted = next == null ? BrowserVisibility.HIDDEN : next;
        boolean wakingUp = !visibility.drawing() && wanted.drawing();
        visibility = wanted;
        if (wakingUp) {
            lastFrameAt = Long.MIN_VALUE;
        }
    }

    /**
     * Ist ein Bild fällig?
     *
     * <p>Sagt <b>nicht</b>, dass eines gemalt wurde — das entscheidet
     * Chromium. Wer hier ein Ja bekommt und dann doch nichts tut, meldet das
     * mit {@link #skipped()}, damit der Takt nicht davonläuft.
     *
     * @param now die aktuelle Zeit in Nanosekunden
     */
    public boolean due(long now) {
        long interval = visibility.frameIntervalNanos();
        if (interval < 0) {
            return false;
        }
        if (lastFrameAt == Long.MIN_VALUE) {
            return true;
        }
        return now - lastFrameAt >= interval;
    }

    /** Ein Bild ist entstanden. */
    public void drawn(long now) {
        lastFrameAt = now;
    }

    /** Es wurde doch keines — der Takt bleibt stehen, statt aufzuholen. */
    public void skipped() {
        lastFrameAt = Long.MIN_VALUE;
    }
}
