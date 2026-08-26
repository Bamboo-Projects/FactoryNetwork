package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.lang.ast.Expr;
import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.runtime.ResourceKind;
import dev.devpanda.factorynetwork.runtime.ResourceKinds;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Source aus Ars Nouveau als Ressourcenart.
 *
 * <p><b>Der Beweis, für den die offene Registry gebaut wurde.</b> Diese Art
 * kommt von einer fremden Mod, und der Kern kennt sie nicht: keine Zeile in
 * {@code ResourceKinds}, keine im Parser, keine im Wertemodell. Was sie
 * kostet, steht vollständig in diesem Ordner — vier Klassen und ein Aufruf
 * beim Laden.
 *
 * <p><b>Geschrieben wird {@code source:source}.</b> Das liest sich doppelt
 * und ist trotzdem richtig: Die Präfixform ist die einzige, die eine fremde
 * Mod bekommen kann. Das nackte Wort — wie bei {@code power} — verlangt ein
 * Schlüsselwort im Lexer, und Schlüsselwörter gehören der Sprache: Sie stehen
 * in der Grammatik für VS Code, in der EBNF und im Handbuch, und keine dieser
 * Stellen kann eine Mod beim Laden ergänzen. Dazu kollidierte ein nacktes
 * {@code source} mit einer Filtervorlage dieses Namens, die es heute geben
 * darf.
 *
 * <p><b>Eine Sorte, ein Schlüssel.</b> Source ist eine ungeteilte Menge wie
 * Strom, kein Sortiment wie Gegenstände. Der Schlüssel ist deshalb die
 * Zeichenkette {@code "source"} und nicht ein Typ von Ars Nouveau: Eine
 * Signatur mit einer fremden Klasse würde diese beim Laden auflösen, auch in
 * einem Pack ohne die Mod. Dieselbe Vorsicht wie bei den Chemikalien.
 */
public final class ArsSource implements ResourceKind {

    public static final ArsSource INSTANCE = new ArsSource();

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(FnArs.MOD_ID, "source");

    private ArsSource() {
    }

    /**
     * Meldet die Art an — einmal, beim Laden.
     *
     * <p>Auch ohne Ars Nouveau: Sonst hieße {@code source:source} in einem
     * Pack ohne die Mod „keine Ressourcenart" statt „diese Mod fehlt", und der
     * Spieler suchte den Tippfehler. Was ohne die Mod fehlt, ist der Zugriff
     * auf die Maschinen — und der meldet sich von selbst.
     */
    public static void register() {
        ResourceKinds.register(INSTANCE);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public String prefix() {
        return "source";
    }

    @Override
    public String plural() {
        return "Source";
    }

    @Override
    public Class<?> type() {
        return String.class;
    }

    @Override
    public String tag() {
        return "src";
    }

    @Override
    public String selectionTag() {
        return "srcsel";
    }

    @Override
    public String idOf(Object key) {
        return (String) key;
    }

    @Override
    public Object fromId(String id) {
        return SourceAccess.KEY;
    }

    @Override
    public String nameOf(Object key) {
        return "Source";
    }

    /**
     * Es gibt genau eine Sorte, und sie heißt wie ihre Art.
     *
     * <p>{@code source:source} trifft sie. Alles andere hinter dem
     * Doppelpunkt trifft nichts — und eine leere Liste ist die ehrliche
     * Antwort darauf, nicht eine stille Umdeutung auf die eine Sorte.
     */
    @Override
    public List<?> resolve(Expr selector) {
        return switch (selector) {
            case Expr.Selector one -> SourceAccess.KEY.equals(one.path())
                    ? List.of(SourceAccess.KEY) : List.of();
            case Expr.Amount amount -> resolve(amount.selection());
            case Expr.Except except -> resolve(except.base());
            case null, default -> List.of();
        };
    }

    @Override
    public ResourceStore newStore() {
        return new SourceBuffer();
    }

    @Override
    public MachineAccess machine() {
        return SourceAccess.INSTANCE;
    }
}
