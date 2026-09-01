package dev.devpanda.factorynetwork.web.mcef;

import dev.devpanda.factorynetwork.web.frame.BorrowedFrame;
import dev.devpanda.factorynetwork.web.frame.DirtyRegion;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein Browser ohne Fenster, dessen Bilder und Ereignisse wir selbst annehmen.
 *
 * <p><b>Der Renderpfad gehört uns, der Rest nicht.</b> JCEFs
 * {@code CefBrowserOsr} bringt alles mit, was ein Browser ohne Fenster
 * braucht — Erzeugung, Größe, Ziehen —, und es ist eine öffentliche Klasse mit
 * öffentlichem Konstruktor. Wir erben davon und überschreiben genau das, was
 * uns gehören soll.
 *
 * <p>MCEFs eigener Browser tut dasselbe, geht aber zwei Schritte weiter: Er
 * lädt in seinem {@code onPaint} sofort in eine Textur, die ihm gehört, und er
 * kopiert aufgeklappte Felder in ebendiese Textur zurück. Beides wollen wir
 * anders — deshalb erben wir eine Ebene tiefer und lassen es weg.
 *
 * <p><b>Die Eingabe muss hier stehen und kann nirgends sonst.</b>
 * {@code sendKeyEvent}, {@code sendMouseEvent} und {@code sendMouseWheelEvent}
 * sind auf {@code CefBrowser_N} {@code protected final}. Nur wer erbt, kommt
 * heran. Die Entscheidungen — welcher Klick der wievielte war, welche Taste an
 * wen geht — stehen deshalb woanders, in Klassen ohne Chromium darin; hier
 * steht nur die Weitergabe.
 *
 * <p><b>Der Puffer wird nur geliehen.</b> Was hinausgeht, ist ein
 * {@link BorrowedFrame} — gültig, solange dieser Aufruf läuft, und mit dem
 * besitzenden Bild absichtlich nicht verwandt.
 */
public class FnBrowser extends CefBrowserOsr {

    /**
     * Was ein Browser meldet.
     *
     * <p>Alles hier kommt im Render-Thread an, solange MCEF Chromiums
     * Nachrichtenschleife dort pumpt — außer {@link #cursorChanged(int)}, das
     * auch von Chromiums eigenen Threads kommen kann.
     */
    public interface Events {

        /** Ein neues Bild der Hauptansicht. Der Puffer gilt nur in diesem Aufruf. */
        void frame(BorrowedFrame frame);

        /**
         * Ein neues Bild eines aufgeklappten Feldes.
         *
         * <p>Der Puffer ist so groß wie das Feld, nicht wie die Seite, und die
         * geänderten Bereiche zählen von der Ecke des Feldes.
         */
        void popupFrame(BorrowedFrame frame);

        /** Ein Feld klappt auf oder zu. */
        void popupShown(boolean shown);

        /** Wo das Feld liegt, in Browser-Pixeln der Hauptansicht. */
        void popupPlaced(int x, int y, int width, int height);

        /** Chromium hätte gern einen anderen Mauszeiger. */
        void cursorChanged(int cefCursorType);
    }

    private final Events events;

    /**
     * Öffnet einen Browser am Client dieser Fassung.
     *
     * <p>Die Fabrik steht hier und nicht beim Aufrufer, weil es diese Klasse
     * zweimal gibt — einmal auf MCEF, einmal auf der eigenen
     * Laufzeitumgebung. Woher der Client kommt, weiß nur die jeweilige
     * Fassung; alles davor bleibt davon unberührt.
     */
    public static FnBrowser open(String url, boolean transparent, Events events) {
        return new FnBrowser(CefHost.client(), url, transparent, events);
    }

    public FnBrowser(CefClient client, String url, boolean transparent, Events events) {
        super(client, url, transparent, null);
        this.events = events;
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

    // ---- Was hereinkommt ---------------------------------------------------

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                        ByteBuffer buffer, int width, int height) {
        if (events == null || buffer == null || width <= 0 || height <= 0) {
            return;
        }
        BorrowedFrame frame = new BorrowedFrame(buffer, width, height, regionsOf(dirtyRects));
        if (popup) {
            events.popupFrame(frame);
        } else {
            events.frame(frame);
        }
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        super.onPopupShow(browser, show);
        if (events != null) {
            events.popupShown(show);
        }
    }

    /**
     * Wo das aufgeklappte Feld hingehört.
     *
     * <p><b>Kann vor dem ersten Bild kommen und über den Rand hinausragen.</b>
     * Chromium meldet die gewünschte Lage, nicht die zurechtgeschnittene.
     * Beschnitten wird deshalb beim Zeichnen und nicht hier — was hier
     * ankommt, ist die Wahrheit über das Feld und soll sie bleiben.
     */
    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        super.onPopupSize(browser, size);
        if (events != null && size != null) {
            events.popupPlaced(size.x, size.y, size.width, size.height);
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (events != null) {
            events.cursorChanged(cursorType);
        }
        // Wahr heißt: Wir haben uns gekümmert. Chromium versucht sonst, selbst
        // einen Zeiger zu setzen — auf ein Fenster, das es nicht gibt.
        return true;
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

    // ---- Was hinausgeht ----------------------------------------------------

    /**
     * Eine Mausbewegung.
     *
     * @param leaving wahr, wenn der Zeiger die Fläche verlässt — Chromium
     *                braucht das, um Schwebezustände zurückzunehmen, sonst
     *                bleibt der zuletzt berührte Knopf für immer hell
     */
    public void moveMouse(int x, int y, int modifiers, boolean leaving) {
        sendMouseEvent(new CefMouseEvent(
                leaving ? CefMouseEvent.MOUSE_EXIT : CefMouseEvent.MOUSE_MOVED,
                x, y, 0, 0, modifiers));
    }

    /**
     * Ein Tastendruck oder ein Loslassen der Maus.
     *
     * @param down       gedrückt oder losgelassen
     * @param cefButton  Chromiums Zählung, nicht Minecrafts
     * @param clickCount 1, 2 oder 3 — daran und nur daran erkennt CEF einen
     *                   Doppelklick
     */
    public void clickMouse(int x, int y, boolean down, int cefButton,
                           int clickCount, int modifiers) {
        if (cefButton < 0) {
            return;
        }
        // Erster Parameter: GLFW_PRESS ist 1, GLFW_RELEASE ist 0 — der native
        // Teil vergleicht gegen genau diese Konstanten.
        sendMouseEvent(new CefMouseEvent(down ? 1 : 0, x, y,
                clickCount, cefButton, modifiers));
    }

    public void scrollMouse(int x, int y, double amount, int modifiers) {
        sendMouseWheelEvent(new CefMouseWheelEvent(
                CefMouseWheelEvent.WHEEL_UNIT_SCROLL, x, y, amount, modifiers));
    }

    /**
     * Eine Taste geht herunter oder herauf.
     *
     * <p><b>Das Zeichen ist hier keins.</b> Der native Teil dieser
     * JCEF-Fassung liest bei Druck und Loslassen aus {@code keyChar} den
     * <b>GLFW-Tastencode</b>, nicht ein Schriftzeichen: Er vergleicht ihn mit
     * {@code GLFW_KEY_LEFT} und Verwandten, um Sondertasten den richtigen
     * Scancode zu geben. Wer dort ein echtes Zeichen hineinschreibt, bekommt
     * für die Pfeiltasten den Scancode irgendeines Buchstabens.
     *
     * <p>Nachgesehen in {@code CefBrowser_N.cpp}, {@code MapScanCodeGLFW}. Es
     * sieht falsch aus und ist richtig.
     */
    public void sendKey(int glfwKeyCode, int scanCode, int modifiers, boolean down) {
        CefKeyEvent event = new CefKeyEvent(
                down ? CefKeyEvent.KEY_PRESS : CefKeyEvent.KEY_RELEASE,
                glfwKeyCode, (char) glfwKeyCode, modifiers);
        event.scancode = scanCode;
        sendKeyEvent(event);
    }

    /**
     * Ein getipptes Zeichen.
     *
     * <p><b>Hier ist das Zeichen wirklich das Zeichen.</b> Bei dieser Art von
     * Ereignis landet {@code keyChar} unverändert als Zeichen bei Chromium.
     * Deshalb geht Text den Weg über Minecrafts {@code charTyped} und wird
     * nirgends aus Tastencodes zusammengebaut: Was auf einer deutschen
     * Tastatur ein Umlaut ist, weiß nur das Betriebssystem.
     *
     * <p>Die Grenze: {@code char} ist sechzehn Bit breit. Alles bis
     * {@code U+FFFF} geht — Umlaute, ß, das Eurozeichen. Zeichen darüber
     * (Emoji) kommen als zwei Hälften an und wären hier zwei kaputte
     * Ereignisse.
     */
    public void sendChar(char typed, int modifiers) {
        sendKeyEvent(new CefKeyEvent(CefKeyEvent.KEY_TYPE, typed, typed, modifiers));
    }
}
