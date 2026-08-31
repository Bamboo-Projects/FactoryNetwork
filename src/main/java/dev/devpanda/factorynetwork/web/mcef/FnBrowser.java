package dev.devpanda.factorynetwork.web.mcef;

import dev.devpanda.factorynetwork.web.frame.BorrowedFrame;
import dev.devpanda.factorynetwork.web.frame.DirtyRegion;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein Browser ohne Fenster, dessen Bilder wir selbst entgegennehmen.
 *
 * <p><b>Der Renderpfad gehört uns, der Rest nicht.</b> JCEFs
 * {@code CefBrowserOsr} bringt alles mit, was ein Browser ohne Fenster
 * braucht — Erzeugung, Größe, Mauszeiger, Ziehen —, und es ist eine
 * öffentliche Klasse mit öffentlichem Konstruktor. Wir erben davon und
 * überschreiben genau eine Methode: die, in der das Bild ankommt.
 *
 * <p>MCEFs eigener Browser tut dasselbe, geht aber einen Schritt weiter: Er
 * lädt in seinem {@code onPaint} sofort in eine Textur, die ihm gehört. Genau
 * dieser Schritt ist der, den wir später gegen einen beschleunigten tauschen
 * wollen — deshalb erben wir eine Ebene tiefer und lassen ihn weg.
 *
 * <p><b>Es braucht dafür keinen Übergriff in ein fremdes Paket.</b> Beide
 * MCEF-Zweige legen eine Klasse nach {@code org.cef.browser}, weil sie
 * {@code CefBrowser_N} direkt erweitern. Eine Ebene höher ist das nicht
 * nötig, und was nicht nötig ist, sollte auch nicht dastehen.
 *
 * <p><b>Der Puffer wird nur geliehen.</b> Was hinausgeht, ist ein
 * {@link BorrowedFrame} — gültig, solange dieser Aufruf läuft, und mit dem
 * besitzenden Bild absichtlich nicht verwandt.
 */
public class FnBrowser extends CefBrowserOsr {

    /** Wer die Bilder bekommt. */
    public interface FrameSink {
        /**
         * Ein neues Bild liegt an.
         *
         * <p>Läuft im Render-Thread, solange MCEF Chromiums
         * Nachrichtenschleife dort pumpt. Der Puffer ist nach der Rückkehr
         * ungültig.
         */
        void accept(BorrowedFrame frame);
    }

    private final FrameSink sink;

    public FnBrowser(CefClient client, String url, boolean transparent, FrameSink sink) {
        super(client, url, transparent, null);
        this.sink = sink;
    }

    /**
     * Setzt die Größe des unsichtbaren Fensters.
     *
     * <p>Beides ist nötig: das Rechteck, das JCEF bei {@code getViewRect}
     * zurückgibt, und die Meldung an Chromium. Ohne das Rechteck malt es in
     * der alten Größe weiter, ohne die Meldung malt es gar nicht neu.
     */
    public void resize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (browser_rect_.width == safeWidth && browser_rect_.height == safeHeight) {
            return;
        }
        browser_rect_.setBounds(0, 0, safeWidth, safeHeight);
        wasResized(safeWidth, safeHeight);
    }

    public int browserWidth() {
        return browser_rect_.width;
    }

    public int browserHeight() {
        return browser_rect_.height;
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                        ByteBuffer buffer, int width, int height) {
        // Aufklappende Auswahlfelder kommen in einer späteren Runde: Sie
        // brauchen eine zweite Textur und eine Stelle, die sie darüberlegt.
        if (popup || sink == null || buffer == null || width <= 0 || height <= 0) {
            return;
        }
        sink.accept(new BorrowedFrame(buffer, width, height, regionsOf(dirtyRects)));
    }

    private static List<DirtyRegion> regionsOf(Rectangle[] rectangles) {
        if (rectangles == null || rectangles.length == 0) {
            return List.of();
        }
        List<DirtyRegion> regions = new ArrayList<>(rectangles.length);
        for (Rectangle rectangle : rectangles) {
            if (rectangle.width > 0 && rectangle.height > 0) {
                regions.add(new DirtyRegion(Math.max(0, rectangle.x),
                        Math.max(0, rectangle.y), rectangle.width, rectangle.height));
            }
        }
        return regions;
    }
}
