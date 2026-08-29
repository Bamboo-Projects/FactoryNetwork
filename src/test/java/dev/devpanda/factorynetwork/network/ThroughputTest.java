package dev.devpanda.factorynetwork.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was ein Kabel je Tick trägt.
 *
 * <p><b>Die Grenze am Kabel ist nicht mehr, wie viele Geräte daran hängen,
 * sondern wie viel hindurchgeht.</b> Der Grund steht in
 * {@code plan-durchsatz-statt-kanaele.md}: Ein Programm sieht nie, welchen
 * Weg ein Kanal nimmt — es sieht nur, ob {@code rate 64 per 1t} durchkommt.
 */
class ThroughputTest {

    @Test
    @DisplayName("Ein gewöhnliches Kabel trägt einen Stapel je Tick")
    void aPlainCableCarriesOneStack() {
        // Genug für jede einzelne Leitung, zu wenig für eine Hauptader.
        assertEquals(64, Throughput.THIN);
    }

    @Test
    @DisplayName("Ein dichtes trägt deutlich mehr — dafür baut man es")
    void aDenseCableIsWorthIt() {
        assertTrue(Throughput.DENSE >= Throughput.THIN * 4,
                "das dichte Kabel lohnt sich kaum: " + Throughput.DENSE
                        + " gegen " + Throughput.THIN);
    }

    @Test
    @DisplayName("Was nicht leitet, begrenzt auch nichts")
    void whatDoesNotCarryDoesNotLimit() {
        // Der Controller, ein Laufwerk, ein Schrank: Sie sind Ziel und nicht
        // Strecke. Eine Grenze dort wäre eine zweite Grenze am selben Weg.
        assertEquals(Integer.MAX_VALUE, Throughput.UNLIMITED);
    }
}
