package com.oddlabs.tt.landscape;

import com.oddlabs.tt.procedural.GeneratedTerrain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection over real generator terrain (see {@link GeneratedTerrain}). Guards the failure mode
 * where thresholds tuned on synthetic cones left only lakes on an actual island.
 */
@DisplayName("KeyPointDetector on generated islands")
class KeyPointRealTerrainTest {

    /** Kinds that exist on every island; inland lakes genuinely do not always occur. */
    private static final List<KeyPointType> ALWAYS_PRESENT = List.of(
            KeyPointType.PEAK, KeyPointType.VALLEY, KeyPointType.FOREST, KeyPointType.BEACH);

    @ParameterizedTest(name = "{0}m hills {1}")
    @CsvSource({
            "512, 0.2",
            "512, 0.5",
            "512, 0.8",
            "1024, 0.2",
            "1024, 0.5",
            "1024, 0.8",
    })
    @DisplayName("medium and large maps name a peak, valley, forest and beach")
    void namesEveryLandformKind(int metersPerWorld, float hills) {
        for (int seed = 1; seed <= 6; seed++) {
            GeneratedTerrain terrain = GeneratedTerrain.island(metersPerWorld, hills, seed * seed);

            List<KeyPoint> points = KeyPointDetector.detect(
                    terrain.heights(), terrain.seaLevelMeters(), terrain.trees(), metersPerWorld);

            Map<KeyPointType, Integer> counts = counts(points);
            int currentSeed = seed * seed;
            for (KeyPointType required : ALWAYS_PRESENT) {
                assertTrue(counts.containsKey(required),
                        () -> "seed " + currentSeed + " missing " + required + "; got " + describe(points));
            }
        }
    }

    @ParameterizedTest(name = "{0}m hills {1} seed {2}")
    @CsvSource({
            "512, 0.5, 7",
            "1024, 0.5, 11",
    })
    @DisplayName("landmarks stay within the quantifier budget and are spread out")
    void staysWithinBudgetAndSpreadsOut(int metersPerWorld, float hills, int seed) {
        GeneratedTerrain terrain = GeneratedTerrain.island(metersPerWorld, hills, seed);

        List<KeyPoint> points = KeyPointDetector.detect(
                terrain.heights(), terrain.seaLevelMeters(), terrain.trees(), metersPerWorld);

        int budget = Math.max(KeyPointQuantifier.forWorldMeters(metersPerWorld), KeyPointType.values().length);
        assertTrue(points.size() <= budget, "too many landmarks: " + describe(points));

        float minSeparation = metersPerWorld / 24f;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                float dx = points.get(i).worldX() - points.get(j).worldX();
                float dy = points.get(i).worldY() - points.get(j).worldY();
                assertTrue(Math.sqrt(dx * dx + dy * dy) >= minSeparation,
                        () -> "labels too close together: " + describe(points));
            }
        }
    }

    private static Map<KeyPointType, Integer> counts(List<KeyPoint> points) {
        Map<KeyPointType, Integer> counts = new EnumMap<>(KeyPointType.class);
        for (KeyPoint point : points) {
            counts.merge(point.type(), 1, Integer::sum);
        }
        return counts;
    }

    private static String describe(List<KeyPoint> points) {
        if (points.isEmpty()) {
            return "no landmarks";
        }
        StringBuilder sb = new StringBuilder();
        for (KeyPoint p : points) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(p.type()).append('@')
                    .append((int) p.worldX()).append(',').append((int) p.worldY())
                    .append(String.format(" (%.2f)", p.score()));
        }
        return sb.toString();
    }
}
