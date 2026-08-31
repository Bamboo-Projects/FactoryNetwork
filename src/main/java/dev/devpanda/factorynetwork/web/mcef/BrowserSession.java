package dev.devpanda.factorynetwork.web.mcef;

import com.cinemamod.mcef.MCEF;
import dev.devpanda.factorynetwork.web.BrowserVisibility;
import dev.devpanda.factorynetwork.web.FramePacer;
import dev.devpanda.factorynetwork.web.frame.BorrowedFrame;
import dev.devpanda.factorynetwork.web.input.ClickCounter;
import dev.devpanda.factorynetwork.web.input.MouseButtons;
import dev.devpanda.factorynetwork.web.texture.GlTextureBackend;

import java.util.function.IntConsumer;

/**
 * Ein Browser samt seiner Texturen, seiner Eingabe und seines Zustands.
 *
 * <p>Der ganze Bildweg an einer Stelle: Chromium malt, wir laden hoch,
 * Minecraft zeichnet. Nichts dazwischen — <b>kein Postfach, keine Kopie</b>.
 *
 * <p><b>Warum das hier richtig ist und nicht faul.</b> MCEF pumpt Chromiums
 * Nachrichtenschleife per Mixin in {@code GameRenderer.render}. Damit kommt
 * {@code onPaint} im Render-Thread an, der Zeichenkontext gilt, und der
 * geliehene Puffer ist genau in diesem Moment gültig. Eine Kopie in ein
 * eigenes Bild und ein Postfach dazwischen kosteten bei 1080p achteinhalb
 * Megabyte je Bild und lösten ein Problem, das es hier nicht gibt.
 *
 * <p><b>Alle Koordinaten hier sind Browser-Pixel.</b> Wo die Fläche auf dem
 * Schirm liegt, weiß diese Klasse nicht und soll sie nicht wissen — das
 * rechnet {@code BrowserView} um, und zwar für das Zeichnen und die Maus mit
 * derselben Formel. Nur so kann später eine Fläche in der Welt dieselbe
 * Sitzung mit ihren eigenen Koordinaten füttern.
 */
public final class BrowserSession implements AutoCloseable, FnBrowser.Events {

    private final FnBrowser browser;
    private final GlTextureBackend texture = new GlTextureBackend();

    /**
     * Die zweite Textur, für aufgeklappte Felder.
     *
     * <p><b>Und nicht ein Zurückkopieren in die erste.</b> MCEF hält den
     * Inhalt eines Auswahlfeldes in einem eigenen Puffer und malt ihn bei
     * jedem Bild der Hauptansicht erneut in deren Textur — weil Chromium den
     * Popup-Inhalt zwischen zwei Aufrufen nicht aufbewahrt. Das kostet je Bild
     * eine Kopie und eine zusätzliche Übertragung, solange das Feld offen ist.
     *
     * <p>Eine eigene Textur kostet nichts davon: Sie behält ihren Inhalt, bis
     * ein neues Bild kommt, und wird beim Zeichnen einfach darübergelegt.
     */
    private final GlTextureBackend popupTexture = new GlTextureBackend();

    private final FramePacer pacer;
    private final MouseButtons buttons = new MouseButtons();
    private final ClickCounter clicks = new ClickCounter();

    private boolean popupShown;
    private int popupX;
    private int popupY;
    private int popupWidth;
    private int popupHeight;

    private IntConsumer cursorSink;

    /**
     * Wann zuletzt eine Eingabe hineinging, auf die noch kein Bild kam.
     *
     * <p><b>Die Zahl, die ein Editor entscheidet.</b> Bilder je Sekunde sagen
     * wenig darüber, ob sich das Tippen unmittelbar anfühlt; was zählt, ist
     * die Spanne zwischen einem Tastendruck und dem Bild, das ihn zeigt. Genau
     * die wird hier gemessen — ohne Rückkanal aus der Seite, allein aus dem,
     * was ohnehin durch diese Klasse geht.
     *
     * <p>Null heißt: Es steht keine Eingabe aus, das nächste Bild gehört zu
     * nichts, was jemand getan hat.
     */
    private long pendingInputNanos;

    /**
     * Die Eingabe, deren Bild schon hochgeladen ist und nur noch gezeichnet
     * werden muss.
     */
    private long paintedForInputNanos;

    /** Wann der letzte Upload fertig war. */
    private long uploadEndNanos;

    /** A: von der Eingabe bis Chromium ein Bild liefert. */
    private final dev.devpanda.factorynetwork.web.measure.DurationSamples toPaint =
            new dev.devpanda.factorynetwork.web.measure.DurationSamples();

    /** C: vom Ende des Uploads bis Minecraft das nächste Mal zeichnet. */
    private final dev.devpanda.factorynetwork.web.measure.DurationSamples toScreen =
            new dev.devpanda.factorynetwork.web.measure.DurationSamples();

    /** Die ganze Strecke, von der Eingabe bis zum gezeichneten Bild. */
    private final dev.devpanda.factorynetwork.web.measure.DurationSamples totalLatency =
            new dev.devpanda.factorynetwork.web.measure.DurationSamples();

    private long paints;
    private long popupPaints;
    private long firstPaintNanos;
    private final long createdNanos = System.nanoTime();
    private boolean closed;

    private BrowserSession(String url, boolean transparent, int width, int height,
                           BrowserVisibility visibility) {
        this.pacer = new FramePacer(visibility);
        this.browser = new FnBrowser(MCEF.getClient().getHandle(), url, transparent, this);
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

    // ---- Was der Browser meldet -------------------------------------------

    @Override
    public void frame(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        if (paints == 0) {
            firstPaintNanos = System.nanoTime();
        }
        paints++;
        long pending = pendingInputNanos;
        if (pending != 0) {
            toPaint.record(System.nanoTime() - pending);
            pendingInputNanos = 0;
            paintedForInputNanos = pending;
        }
        texture.upload(frame);
        uploadEndNanos = System.nanoTime();
        pacer.drawn(uploadEndNanos);
    }

    @Override
    public void popupFrame(BorrowedFrame frame) {
        if (closed) {
            return;
        }
        popupPaints++;
        popupTexture.upload(frame);
    }

    @Override
    public void popupShown(boolean shown) {
        popupShown = shown;
        if (!shown) {
            // Die verdeckte Fläche malt Chromium von selbst neu — es kommt als
            // gewöhnliches Bild der Hauptansicht. Hier ist nichts
            // wiederherzustellen, nur etwas zu verstecken.
            popupWidth = 0;
            popupHeight = 0;
        }
    }

    @Override
    public void popupPlaced(int x, int y, int width, int height) {
        popupX = x;
        popupY = y;
        popupWidth = width;
        popupHeight = height;
    }

    @Override
    public void cursorChanged(int cefCursorType) {
        IntConsumer sink = cursorSink;
        if (sink != null) {
            sink.accept(cefCursorType);
        }
    }

    /** Wer die Zeigerwünsche bekommt — der Screen, solange er offen ist. */
    public void onCursor(IntConsumer sink) {
        this.cursorSink = sink;
    }

    // ---- Eingabe -----------------------------------------------------------

    /**
     * Der Zeiger bewegt sich, in Browser-Pixeln.
     *
     * <p>Gedrückte Maustasten gehen mit: Ohne sie sieht Chromium eine Bewegung
     * mit losgelassener Taste, und jedes Markieren bricht nach dem ersten
     * Pixel ab.
     */
    public void mouseMoved(int x, int y, int keyboardModifiers) {
        if (!closed) {
            noteInput();
            browser.moveMouse(x, y, buttons.modifiersWith(keyboardModifiers), false);
        }
    }

    /**
     * Der Zeiger verlässt die Fläche.
     *
     * <p>Ohne diese Meldung bleibt der zuletzt berührte Knopf hell, und ein
     * Schwebehinweis steht, bis jemand die Fläche wieder betritt.
     */
    public void mouseLeft(int x, int y) {
        if (closed) {
            return;
        }
        browser.moveMouse(x, y, buttons.modifiersWith(0), true);
        clicks.forget();
    }

    public void mousePressed(int x, int y, int minecraftButton, int keyboardModifiers,
                             long nowMillis) {
        if (closed) {
            return;
        }
        int cefButton = MouseButtons.toBrowserButton(minecraftButton);
        if (cefButton < 0) {
            return;
        }
        noteInput();
        int count = clicks.pressed(minecraftButton, x, y, nowMillis);
        buttons.press(minecraftButton);
        browser.clickMouse(x, y, true, cefButton, count,
                buttons.modifiersWith(keyboardModifiers));
    }

    public void mouseReleased(int x, int y, int minecraftButton, int keyboardModifiers) {
        if (closed) {
            return;
        }
        int cefButton = MouseButtons.toBrowserButton(minecraftButton);
        if (cefButton < 0) {
            return;
        }
        int count = clicks.released();
        // Erst die Flagge löschen, dann melden: Beim Loslassen ist die Taste
        // schon oben, und Chromium liest den Zustand danach.
        buttons.release(minecraftButton);
        browser.clickMouse(x, y, false, cefButton, count,
                buttons.modifiersWith(keyboardModifiers));
    }

    /**
     * Das Rad dreht sich.
     *
     * <p>Der Faktor drei und das Runden auf ganze Rasten sind von MCEF
     * übernommen und dort erprobt: Minecraft meldet gebrochene Werte, und
     * ungerundet fühlt sich das Scrollen zäh an. Unter macOS bleibt es
     * ungerundet, weil Trackpads dort feine Werte liefern, die etwas bedeuten.
     */
    public void mouseScrolled(int x, int y, double amount, int keyboardModifiers) {
        if (closed) {
            return;
        }
        noteInput();
        double scroll = amount < 0 ? Math.floor(amount) : Math.ceil(amount);
        browser.scrollMouse(x, y, scroll * 3.0,
                buttons.modifiersWith(keyboardModifiers));
    }

    /** Eine Taste geht herunter. Der Tastencode ist GLFWs, nicht Minecrafts. */
    public void keyPressed(int glfwKeyCode, int scanCode, int modifiers) {
        if (!closed) {
            noteInput();
            browser.sendKey(glfwKeyCode, scanCode, modifiers, true);
        }
    }

    public void keyReleased(int glfwKeyCode, int scanCode, int modifiers) {
        if (!closed) {
            browser.sendKey(glfwKeyCode, scanCode, modifiers, false);
        }
    }

    /**
     * Ein Zeichen wurde getippt.
     *
     * <p>Das kommt aus Minecrafts eigenem Texteingabeweg und ist bereits das
     * fertige Zeichen — mit Tastaturbelegung, Umschalttaste und allem, was das
     * Betriebssystem dazu beiträgt. Es aus Tastencodes nachzubauen, hieße, die
     * Tastaturbelegung des Spielers zu erraten.
     */
    public void charTyped(char typed, int modifiers) {
        if (!closed) {
            noteInput();
            browser.sendChar(typed, modifiers);
        }
    }

    /**
     * Ob die Seite noch lädt.
     *
     * <p>Wichtig für alles, was in die Seite hineinruft: Vor dem Ende des
     * Ladens gibt es das Dokument noch nicht, und ein Aufruf verpufft — ohne
     * Fehler, ohne Spur. Genau das ist hier zuerst passiert: Der Hintergrund
     * wurde einmal gesetzt, zu früh, und blieb für immer leer.
     */
    public boolean loading() {
        try {
            return closed || browser.isLoading() || !browser.hasDocument();
        } catch (Throwable notReady) {
            return true;
        }
    }

    /**
     * Führt eine Zeile JavaScript in der Seite aus.
     *
     * <p><b>Das ist keine Brücke.</b> Es geht nur in eine Richtung, die Seite
     * kann nichts zurückgeben, und es gibt keine Schnittstelle, die sie
     * anbieten müsste. Gebraucht wird es für genau eine Sache: die Adresse
     * eines Bildes auszutauschen, ohne die Seite neu zu laden — und damit,
     * ohne alles zu verlieren, was jemand darauf getippt hat.
     */
    public void runScript(String code) {
        if (closed) {
            return;
        }
        // Über den Hauptrahmen und nicht über den Browser: Der Browser reicht
        // den Aufruf an denselben Rahmen weiter, aber nur wenn er einen hat —
        // und er hat keinen, solange nichts geladen ist.
        org.cef.browser.CefFrame frame = browser.getMainFrame();
        if (frame == null) {
            return;
        }
        frame.executeJavaScript(code, "fn://runtime", 0);
    }

    /**
     * Merkt sich, dass gerade etwas hineinging.
     *
     * <p>Nur der <b>erste</b> Anschlag einer Folge zählt: Wer zehnmal tippt,
     * bevor ein Bild kommt, wartet einmal — und diese eine Wartezeit ist, was
     * er merkt.
     */
    private void noteInput() {
        if (pendingInputNanos == 0) {
            pendingInputNanos = System.nanoTime();
        }
    }

    /**
     * Meldet, dass Minecraft jetzt zeichnet.
     *
     * <p>Der letzte fehlende Abschnitt: Zwischen dem fertigen Upload und dem
     * Bild, das der Spieler sieht, liegt die Wartezeit auf den nächsten
     * Durchgang von Minecraft. Ohne diesen Aufruf endete jede Messung an der
     * Grafikkarte statt am Auge.
     *
     * <p><b>Und eine Ehrlichkeit dazu:</b> Gemessen wird bis zum <i>Beginn</i>
     * des Zeichnens, nicht bis das Bild auf dem Schirm steht. Was danach noch
     * kommt — Zeichnen, Puffertausch, Bildwiederholung — ist hier nicht drin.
     */
    public void noteScreenFrame() {
        if (uploadEndNanos == 0) {
            return;
        }
        long now = System.nanoTime();
        toScreen.record(now - uploadEndNanos);
        if (paintedForInputNanos != 0) {
            totalLatency.record(now - paintedForInputNanos);
            paintedForInputNanos = 0;
        }
        uploadEndNanos = 0;
    }

    /** A — von der Eingabe bis Chromium ein Bild liefert. */
    public dev.devpanda.factorynetwork.web.measure.DurationSamples toPaint() {
        return toPaint;
    }

    /** C — vom Ende des Uploads bis Minecraft zeichnet. */
    public dev.devpanda.factorynetwork.web.measure.DurationSamples toScreen() {
        return toScreen;
    }

    /** Die ganze Strecke. Muss ungefähr A + B + C sein. */
    public dev.devpanda.factorynetwork.web.measure.DurationSamples totalLatency() {
        return totalLatency;
    }

    /** Wie lange es von einer Eingabe bis zum Bild dauert (A). */
    public dev.devpanda.factorynetwork.web.measure.DurationSamples inputLatency() {
        return toPaint;
    }

    /** Setzt alle drei Abschnitte zurück. */
    public void resetLatency() {
        toPaint.reset();
        toScreen.reset();
        totalLatency.reset();
        pendingInputNanos = 0;
        paintedForInputNanos = 0;
        uploadEndNanos = 0;
    }

    /** Sagt Chromium, ob es die Tastatur hat. */
    public void setFocused(boolean focused) {
        if (closed) {
            return;
        }
        browser.setFocus(focused);
        if (!focused) {
            // Was beim Fokusverlust gedrückt war, bleibt es sonst für immer.
            buttons.forget();
            clicks.forget();
        }
    }

    // ---- Größe, Sichtbarkeit, Zustand -------------------------------------

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

    public int popupTextureId() {
        return popupTexture.textureId();
    }

    public boolean popupVisible() {
        return popupShown && popupWidth > 0 && popupHeight > 0;
    }

    public int popupX() {
        return popupX;
    }

    public int popupY() {
        return popupY;
    }

    public int popupWidth() {
        return popupWidth;
    }

    public int popupHeight() {
        return popupHeight;
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

    public GlTextureBackend popupTexture() {
        return popupTexture;
    }

    public long paints() {
        return paints;
    }

    public long popupPaints() {
        return popupPaints;
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
        cursorSink = null;
        try {
            browser.close(true);
        } catch (Throwable broken) {
            // Ein Browser, der beim Schließen zickt, darf den Rest nicht
            // aufhalten — die Texturen müssen trotzdem weg.
        }
        texture.close();
        popupTexture.close();
    }
}
