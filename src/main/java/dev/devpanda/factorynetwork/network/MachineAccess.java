package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * Wie eine Ressourcenart an einer fremden Maschine gelesen und geschrieben
 * wird.
 *
 * <p><b>Die zweite Achse.</b> {@link ResourceStore} sagt, wo eine Art im Netz
 * liegt — das gehört dieser Mod, und deshalb ließ es sich hinter eine
 * Schnittstelle bringen. Die Maschine gehört jemand anderem: Dort stehen
 * {@code IItemHandler} und {@code IFluidHandler} aus NeoForge und
 * {@code IChemicalHandler} aus Mekanism nebeneinander, heißen an jeder
 * Methode anders und rechnen in verschiedenen Einheiten. Ein gemeinsamer
 * Obertyp existiert nicht.
 *
 * <p><b>Gemeinsam ist nicht der Typ, sondern die Handlung.</b> Drei davon
 * gibt es, und sie sind nicht erfunden, sondern abgelesen: {@code
 * ChemicalStores} hatte genau diese drei, weil die Chemikalien als letzte
 * gebaut wurden und dabei die Naht zum Kompatibilitätsmodul brauchten.
 *
 * <p><b>Wofür das da ist:</b> Eine fremde Mod meldet eine Ressourcenart an
 * (siehe {@code ResourceKinds}) und liefert hier dazu, wie ihre Maschinen sie
 * hergeben und annehmen. Ohne das kann ihre Art im Netz liegen und sich in
 * einem Programm nennen lassen — bewegen ließe sie sich nicht.
 *
 * <p><b>Die eingebauten drei gehen noch ihren eigenen Weg.</b> Sie hierher zu
 * ziehen ist ein eigener Schnitt, und er wartet auf eine Runde Spielen:
 * {@code move} ist die Stelle, an der diese Mod Gegenstände in der Hand hält,
 * und ein Fehler dort kostet einen Bestand statt einer Meldung. Siehe
 * {@code maschinenzugriff.md}, Abschnitt 5.
 */
public interface MachineAccess {

    /**
     * Wie viel davon in der Maschine liegt.
     *
     * <p>{@code keys} trägt Schlüssel in der Form, die
     * {@code ResourceKind.type()} für diese Art nennt. <b>Leer heißt nicht
     * „alles".</b> Eine leere Auswahl hat am 26.08. schon einmal ein Gas
     * verwechselt; wer alles meint, sagt es mit einer vollen Liste.
     */
    long count(Level level, BlockPos pos, Direction side, Collection<?> keys);

    /**
     * Aus dem Netzspeicher in die Maschine.
     *
     * @return wie viel angekommen ist — weniger als gewünscht ist normal
     */
    long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
            Collection<?> keys, long limit);

    /**
     * Aus der Maschine in den Netzspeicher.
     *
     * <p><b>Erst fragen, dann ziehen.</b> Was der Speicher nicht nimmt, darf
     * gar nicht erst aus der Maschine kommen — bei Gegenständen ließe sich
     * der Rest zurücklegen, bei einem Gas nicht. Diese Regel steht hier und
     * nicht beim Aufrufer, weil sie sonst beim vierten Eintrag vergessen
     * wird.
     *
     * @return wie viel im Netz angekommen ist
     */
    long drain(Level level, BlockPos pos, Direction side,
            Collection<?> keys, ResourceStore into, long limit);

    /**
     * Eine Art, die an keiner Maschine ankommt.
     *
     * <p>Die Vorgabe, und keine Notlösung: Eine Art darf im Netz liegen, ohne
     * dass eine Maschine sie kennt — so wie sie sich bewegen darf, ohne
     * lagerbar zu sein. Wer sie trotzdem bewegen will, bekommt eine Meldung
     * und keine stille Null.
     */
    MachineAccess NONE = new MachineAccess() {

        @Override
        public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
            return 0;
        }

        @Override
        public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                Collection<?> keys, long limit) {
            return 0;
        }

        @Override
        public long drain(Level level, BlockPos pos, Direction side,
                Collection<?> keys, ResourceStore into, long limit) {
            return 0;
        }
    };
}
