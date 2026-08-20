package dev.devpanda.factorynetwork.block;

import java.util.Map;

/**
 * Wo die Stränge eines Bündels im Block liegen — als reine Zahlen.
 *
 * <p><b>Ohne jeden Minecraft-Bezug</b>, damit sich die Geometrie in
 * gewöhnlichen Tests gegen die erzeugten Modelldateien prüfen lässt. Eine
 * Klasse, die {@code VoxelShape} anfasst, ließe sich nur mit laufendem
 * Server prüfen — und diese Zahlen sind genau die, die auseinanderlaufen
 * können, ohne dass es jemand merkt.
 *
 * <p>Dieselben Werte stehen im Modellskript {@code tools/assets.py}. Dass sie
 * doppelt geführt werden, ist der Preis dafür, dass Minecraft Modelle und
 * Trefferflächen getrennt hält; {@code CableLayoutTest} wacht darüber.
 */
public final class CableLayout {

    /** Lage der Stränge in der Fläche: Paare aus x und y, je nach Anzahl. */
    private static final Map<Integer, int[][]> POSITIONS = Map.of(
            1, new int[][]{{5, 5}},
            2, new int[][]{{3, 6}, {9, 6}},
            3, new int[][]{{2, 6}, {6, 2}, {10, 6}},
            4, new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}});

    /** Ein Strang allein ist dicker — ein einzelnes Kabel sieht aus wie immer. */
    public static int size(int strandCount) {
        return strandCount <= 1 ? 6 : 4;
    }

    /** In der Tiefe sitzt jeder Strang mittig. */
    public static int depth(int strandCount) {
        return (16 - size(strandCount)) / 2;
    }

    public static int[][] positions(int strandCount) {
        return POSITIONS.getOrDefault(clamp(strandCount), POSITIONS.get(1));
    }

    public static int count(int strandCount) {
        return positions(strandCount).length;
    }

    public static int clamp(int strandCount) {
        return Math.max(1, Math.min(4, strandCount));
    }

    private CableLayout() {
    }
}
