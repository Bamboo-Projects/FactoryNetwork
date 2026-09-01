package dev.devpanda.factorynetwork.web.mcef;

import dev.devpanda.factorynetwork.web.capture.FrameStore;

/**
 * Der Bearbeiter für {@code mc://frame} — Fassung für MCEF.
 *
 * <p>Hier steht nichts, und das ist die ganze Aussage: Die Schnittstelle
 * {@code CefResourceHandler} des CinemaMod-Forks verlangt nur, was in
 * {@link FrameScheme} schon steht. Upstream verlangt eine Methode mehr, und
 * deren Parametertypen gibt es im Fork nicht — deshalb endet die gemeinsame
 * Fassung eine Ableitung früher und diese Klasse gibt es zweimal.
 */
public final class FrameHandler extends FrameScheme {

    public FrameHandler(FrameStore store) {
        super(store);
    }
}
