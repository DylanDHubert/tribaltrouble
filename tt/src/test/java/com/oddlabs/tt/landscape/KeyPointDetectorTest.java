package com.oddlabs.tt.landscape;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyPointDetector")
class KeyPointDetectorTest {

    private static final int GRID = 64;
    private static final float SEA = 5f;
    private static final int METERS = 512; // medium → budget 5

    @Nested
    @DisplayName("KeyPointQuantifier")
    class QuantifierTests {

        @Test
        void mediumMapGetsSeven() {
            assertEquals(7, KeyPointQuantifier.forWorldMeters(512));
            assertEquals(KeyPointQuantifier.KEY_POINT_QUANTIFIER, KeyPointQuantifier.forWorldMeters(512));
        }

        @Test
        void largeStaysFixedWhileMediumScales() {
            assertEquals(4, KeyPointQuantifier.forWorldMeters(256));
            assertEquals(KeyPointQuantifier.LARGE_KEY_POINT_COUNT, KeyPointQuantifier.forWorldMeters(1024));
            assertEquals(20, KeyPointQuantifier.forWorldMeters(2048));
        }
    }

    @Test
    @DisplayName("detects peak at local height maximum")
    void detectsPeak() {
        float[][] heights = flatLand(10f);
        // Tall, steep cone — must clear prominence / flank / curvature gates
        bump(heights, 32, 32, 32f, 8);

        List<KeyPoint> points = KeyPointDetector.detect(heights, SEA, List.of(), METERS);

        assertTrue(points.stream().anyMatch(p -> p.type() == KeyPointType.PEAK),
                "expected a PEAK among " + summarize(points));
        assertTrue(points.stream().allMatch(p -> p.type().label().equals(p.name())));
    }

    @Test
    @DisplayName("prefers one of each type before doubling")
    void prefersTypeDiversity() {
        float[][] heights = islandWithFeatures();
        List<int[]> trees = denseForest(18, 40, 8);

        List<KeyPoint> points = KeyPointDetector.detect(heights, SEA, trees, METERS);

        long distinct = points.stream().map(KeyPoint::type).distinct().count();
        assertTrue(distinct >= Math.min(points.size(), 3),
                "expected diverse types among " + summarize(points));
        // With budget 5 and several kinds present, no type should claim more than leftover after coverage
        for (KeyPointType type : KeyPointType.values()) {
            long count = points.stream().filter(p -> p.type() == type).count();
            assertTrue(count <= 2, type + " over-represented in " + summarize(points));
        }
    }

    @Test
    @DisplayName("detects inland lake and ignores ocean")
    void detectsInlandLake() {
        float[][] heights = flatLand(10f);
        // Ocean ring on border
        for (int i = 0; i < GRID; i++) {
            heights[0][i] = 0f;
            heights[GRID - 1][i] = 0f;
            heights[i][0] = 0f;
            heights[i][GRID - 1] = 0f;
        }
        // Inland lake
        for (int y = 28; y <= 36; y++) {
            for (int x = 28; x <= 36; x++) {
                heights[y][x] = 0f;
            }
        }

        List<KeyPoint> points = KeyPointDetector.detect(heights, SEA, List.of(), METERS);

        assertTrue(points.stream().anyMatch(p -> p.type() == KeyPointType.LAKE),
                "expected a LAKE among " + summarize(points));
    }

    @Test
    @DisplayName("detects forest from dense wood cluster")
    void detectsForest() {
        float[][] heights = flatLand(10f);
        List<int[]> trees = new ArrayList<>();
        for (int y = 20; y < 30; y++) {
            for (int x = 20; x < 30; x++) {
                trees.add(new int[]{x, y});
                trees.add(new int[]{x, y}); // denser
            }
        }

        List<KeyPoint> points = KeyPointDetector.detect(heights, SEA, trees, METERS);

        assertTrue(points.stream().anyMatch(p -> p.type() == KeyPointType.FOREST),
                "expected a FOREST among " + summarize(points));
    }

    @Test
    @DisplayName("same map data yields identical key points (lobby determinism)")
    void deterministicAcrossClients() {
        float[][] heights = islandWithFeatures();
        List<int[]> trees = denseForest(18, 40, 8);

        List<KeyPoint> a = KeyPointDetector.detect(heights, SEA, trees, METERS);
        List<KeyPoint> b = KeyPointDetector.detect(copy(heights), SEA, new ArrayList<>(trees), METERS);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).type(), b.get(i).type());
            assertEquals(a.get(i).worldX(), b.get(i).worldX(), 1e-5f);
            assertEquals(a.get(i).worldY(), b.get(i).worldY(), 1e-5f);
            assertEquals(a.get(i).name(), b.get(i).name());
        }
    }

    @Test
    @DisplayName("respects KeyPointQuantifier budget")
    void respectsBudget() {
        float[][] heights = islandWithFeatures();
        List<int[]> trees = denseForest(18, 40, 8);
        trees.addAll(denseForest(40, 18, 8));

        List<KeyPoint> points = KeyPointDetector.detect(heights, SEA, trees, METERS);

        assertTrue(points.size() <= KeyPointQuantifier.forWorldMeters(METERS));
        assertFalse(points.isEmpty());
    }

    private static float[][] flatLand(float height) {
        float[][] h = new float[GRID][GRID];
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                h[y][x] = height;
            }
        }
        return h;
    }

    private static void bump(float[][] h, int cx, int cy, float peak, int radius) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || y < 0 || x >= GRID || y >= GRID) {
                    continue;
                }
                float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d <= radius) {
                    float t = 1f - d / radius;
                    h[y][x] = Math.max(h[y][x], SEA + 1f + (peak - SEA - 1f) * t * t);
                }
            }
        }
    }

    private static float[][] islandWithFeatures() {
        float[][] h = flatLand(0f); // ocean default
        for (int y = 4; y < GRID - 4; y++) {
            for (int x = 4; x < GRID - 4; x++) {
                h[y][x] = 10f;
            }
        }
        bump(h, 16, 16, 36f, 9);
        bump(h, 48, 48, 34f, 9);
        // inland lake
        for (int y = 30; y <= 38; y++) {
            for (int x = 30; x <= 38; x++) {
                h[y][x] = 0f;
            }
        }
        // Deep bowl valley (steep flanks + clear floor)
        for (int y = 10; y <= 22; y++) {
            for (int x = 40; x <= 52; x++) {
                float dx = x - 46;
                float dy = y - 16;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 6f) {
                    float t = d / 6f;
                    h[y][x] = 6.2f + (10f - 6.2f) * t * t;
                }
            }
        }
        return h;
    }

    private static List<int[]> denseForest(int cx, int cy, int radius) {
        List<int[]> trees = new ArrayList<>();
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x > 0 && y > 0 && x < GRID - 1 && y < GRID - 1) {
                    trees.add(new int[]{x, y});
                    trees.add(new int[]{x, y});
                }
            }
        }
        return trees;
    }

    private static float[][] copy(float[][] src) {
        float[][] dst = new float[src.length][src.length];
        for (int y = 0; y < src.length; y++) {
            System.arraycopy(src[y], 0, dst[y], 0, src.length);
        }
        return dst;
    }

    private static String summarize(List<KeyPoint> points) {
        StringBuilder sb = new StringBuilder("[");
        for (KeyPoint p : points) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(p.type()).append('@').append(p.worldX()).append(',').append(p.worldY());
        }
        return sb.append(']').toString();
    }
}
