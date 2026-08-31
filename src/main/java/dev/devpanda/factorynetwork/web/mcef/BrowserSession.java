package dev.devpanda.factorynetwork.web.mcef;

import com.cinemamod.mcef.MCEF;
import dev.devpanda.factorynetwork.web.BrowserVisibility;
import dev.devpanda.factorynetwork.web.FramePacer;
import dev.devpanda.factorynetwork.web.frame.BorrowedFrame;
import dev.devpanda.factorynetwork.web.texture.GlTextureBackend;

/**
 * Ein Browser samt seiner Textur.
 *
 * <p>Der ganze Weg an einer Stelle: Chromium malt, wir laden hoch, Minecraft
 * zeichnet. Nichts dazwischen — <b>kein Postfach, keine Kopie</b>.
 *
 * <p><b>Warum das hier richtig ist und nicht faul.</b> MCEF pumpt Chromiums
 * Nachrichtenschleife per Mixin in {@code GameRenderer.render}. Damit kommt
 * {@code onPaint} im Render-Thread an, der Zeichenkontext gilt, und der
 * geliehene Puffer ist genau in diesem Moment gültig. Eine Kopie in ein
 * eigenes Bild und ein Postfach dazwischen kosteten bei 1080p achteinhalb
 * Megabyte je Bild und lösten ein Problem, das es hier nicht gibt.
 *
 * <p>Der zweite Weg — Kopie, Postfach, entkoppelter Upload — bleibt gebaut
 * und geprüft. Er wird gebraucht, sobald Chromium einen eigenen Takt bekommt
 * oder der Aufruf woanders ankommt als die Textur. Was er kostet, messen wir
 * gegen diesen hier: Das ist die untere Grenze, unter die niemand kommt.
 */
public final class BrowserSession implements AutoCloseable {

    private final FnBrowser browser;
    private final GlTextureBackend texture = new GlTextureBackend();
    private final FramePacer pacer;

    private long paints;
    private long firstPaintNanos;
    private final long createdNanos = System.nanoTime();
    private boolean closed;

    private BrowserSession(String url, boolean transparent, int width, int height,
                           BrowserVisibility visibility) {
        this.pacer = new FramePacer(visibility);
        this.browser = new FnBrowser(MCEF.getClient().getHandle(), url, transparent,
                this::onFrame);
        // <b>Die Reihenfolge ist nicht beliebig.</b> Erst darf der Browser
        // geschlossen werden, dann entsteht er, und erst danach bekommt er
        // seine Größe. Andersherum geht die Größenmeldung an einen Browser,
        // den es noch nicht gibt — sie verpufft, und weil das Rechteck danach
        // schon stimmt, versucht es niemand ein zweites Mal. Ergebnis: ein
        // Browser, der nie malt. Genau so ist es hier zuerst passiert.
        browser.setCloseAllowed();
        browser.createImmediately();
        browser.resize(width, height);
    }

    /**
     * Öffnet einen Browser.
     *
     * <p>Nur zu rufen, wenn die Runtime bereit ist — sonst gibt es keinen
     * {@code CefClient}, an den man ihn hängen könnte.
     */
    public static BrowserSession open(String url, boolean transparent,
                                      int width, int height,
                                      BrowserVisibility visibility) {
        return new BrowserSession(url, transparent, width, height, visibility);
    }

    /**
     * Ein Bild ist da.
     *
     * <p>Läuft im Render-Thread. Der Puffer im Bild ist nach der Rückkehr
     * ungültig; deshalb geht er direkt weiter und wird nirgends gemerkt.
     */
    private void onFrame(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        if (paints == 0) {
            firstPaintNanos = System.nanoTime();
        }
        paints++;
        texture.upload(frame);
        pacer.drawn(System.nanoTime());
    }

    public void resize(int width, int height) {
        if (!closed) {
            browser.resize(width, height);
        }
    }

    public void setVisibility(BrowserVisibility visibility) {
        pacer.setVisibility(visibility);
        // Chromium selbst mitteilen, dass niemand hinsieht. In dieser
        // JCEF-Fassung heißt das setWindowVisibility und nicht wasHidden —
        // nachgesehen im Jar, nicht im aktuellen Quelltext von JCEF, der
        // neuer ist als das, was MCEF mitbringt.
        browser.setWindowVisibility(visibility.drawing());
    }

    public int textureId() {
        return texture.textureId();
    }

    public int width() {
        return browser.browserWidth();
    }

    public int height() {
        return browser.browserHeight();
    }

    public GlTextureBackend texture() {
        return texture;
    }

    public long paints() {
        return paints;
    }

    /** Was der Browser gerade geladen hat — für die Fehlersuche. */
    public String currentUrl() {
        try {
            return browser.getURL();
        } catch (Throwable notReady) {
            return "(noch keine)";
        }
    }

    /** Wie lange es vom Öffnen bis zum ersten Bild dauerte, in Millisekunden. */
    public double millisToFirstPaint() {
        return firstPaintNanos == 0 ? -1.0
                : (firstPaintNanos - createdNanos) / 1_000_000.0;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            browser.close(true);
        } catch (Throwable broken) {
            // Ein Browser, der beim Schließen zickt, darf den Rest nicht
            // aufhalten — die Textur muss trotzdem weg.
        }
        texture.close();
    }
}
