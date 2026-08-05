package com.oddlabs.tt.landscape;

import com.oddlabs.tt.pathfinder.UnitGrid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects a small set of large geographic landmarks from map data alone (height + trees).
 * Results are deterministic for a given map so every lobby client shares the same names
 * regardless of start-position orientation.
 *
 * <p>Detection works at feature scale rather than per grid cell: the height field is smoothed, then
 * landforms are classified from the eigenvalues of the Hessian. A summit is two-dimensional — both
 * curvatures bend down and it must be the highest point of a {@linkplain #featureRadius
 * feature-sized} window. A valley is one-dimensional — it curves up across its axis but is free to
 * run downhill along it, which is what real eroded terrain looks like, so it is found by profiling
 * the cross-section along the direction of strongest upward curvature. Both are scored by relief,
 * which over a fixed window is proportional to mean flank steepness. Forests and beaches are peaks
 * of a density field (not centroids of one sprawling component, which would land in the middle of
 * the island), and lakes are inland water bodies.
 *
 * <p>Quality bars are relative to the strongest landmark of the same kind, so a map always yields
 * at least one of every kind that physically exists on it.
 */
public final class KeyPointDetector {

    /** A duplicate landmark must reach this fraction of the best score of its own kind. */
    private static final float QUALITY_FLOOR_FRAC = 0.25f;
    /** Water body must span at least this many grid cells to be worth naming. */
    private static final int LAKE_MIN_CELLS = 16;
    /** Land within this height of the sea counts as shoreline sand. */
    private static final float BEACH_MAX_ABOVE_SEA = 2.5f;
    private static final float EPS = 1e-5f;

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator.comparingDouble((
            Candidate c) -> c.score).reversed().thenComparingInt(c -> c.gridX).thenComparingInt(
                    c -> c.gridY).thenComparing(c -> c.type);

    private KeyPointDetector() {
    }

    public static @NonNull List<KeyPoint> detect(@NonNull HeightMap heightMap) {
        int gridSize = heightMap.getGridUnitsPerWorld();
        float[][] heights = new float[gridSize][gridSize];
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                heights[y][x] = heightMap.getHeight(x, y);
            }
        }
        return detect(
                heights,
                heightMap.getSeaLevelMeters(),
                heightMap.getTrees(),
                heightMap.getMetersPerWorld());
    }

    /**
     * Pure detection entry point (also used by unit tests). Heights are indexed {@code [y][x]}.
     * Tree entries are {@code {gridX, gridY}}.
     */
    public static @NonNull List<KeyPoint> detect(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            @NonNull List<int[]> trees,
            int metersPerWorld) {
        int gridSize = heights.length;
        if (gridSize < 8) {
            return List.of();
        }

        int featureRadius = featureRadius(gridSize);
        int smoothRadius = Math.max(1, gridSize / 128);
        float[][] smoothed = boxBlur(heights, smoothRadius);

        List<Candidate> candidates = new ArrayList<>();
        collectPeaks(smoothed, seaLevel, featureRadius, candidates);
        collectValleys(smoothed, seaLevel, featureRadius, candidates);
        collectForests(heights, seaLevel, trees, featureRadius, candidates);
        collectLakes(heights, seaLevel, candidates);
        collectBeaches(heights, seaLevel, featureRadius, candidates);

        normalizeScoresByType(candidates);
        dropWeakDuplicates(candidates);
        candidates.sort(CANDIDATE_ORDER);

        int budget = Math.max(KeyPointQuantifier.forWorldMeters(metersPerWorld), distinctTypes(candidates));
        return select(candidates, budget, gridSize);
    }

    /** Half-width of the window a landmark must dominate, in grid cells. */
    private static int featureRadius(int gridSize) {
        return Math.max(4, gridSize / 32);
    }

    /**
     * Bring each landmark kind onto a 0–1 scale so area-based scores (lakes, beaches) can be
     * compared against relief-based ones (peaks, valleys).
     */
    private static void normalizeScoresByType(@NonNull List<Candidate> candidates) {
        float[] max = new float[KeyPointType.values().length];
        for (Candidate c : candidates) {
            int i = c.type.ordinal();
            if (c.score > max[i]) {
                max[i] = c.score;
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            float best = max[c.type.ordinal()];
            float norm = best > EPS ? c.score / best : 0f;
            candidates.set(i, new Candidate(c.type, c.gridX, c.gridY, norm));
        }
    }

    /** Normalized scores make this a per-kind bar; the best of each kind always survives. */
    private static void dropWeakDuplicates(@NonNull List<Candidate> candidates) {
        candidates.removeIf(c -> c.score < QUALITY_FLOOR_FRAC);
    }

    private static int distinctTypes(@NonNull List<Candidate> candidates) {
        boolean[] seen = new boolean[KeyPointType.values().length];
        int count = 0;
        for (Candidate c : candidates) {
            if (!seen[c.type.ordinal()]) {
                seen[c.type.ordinal()] = true;
                count++;
            }
        }
        return count;
    }

    /**
     * Summits: both Hessian curvatures bend downwards and the cell is the high point of a
     * feature-sized window. Score is relief above the lowest ground in that window, which for a
     * fixed window is proportional to mean flank steepness.
     */
    private static void collectPeaks(
            float @NonNull [] @NonNull [] smoothed,
            float seaLevel,
            int featureRadius,
            @NonNull List<Candidate> out) {
        int n = smoothed.length;
        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                float h = smoothed[y][x];
                if (h <= seaLevel || !isLocalMax(smoothed, x, y)) {
                    continue;
                }
                // A dome curves down along both principal axes; saddles and troughs do not
                if (curvature(smoothed, x, y).major >= 0f) {
                    continue;
                }
                Extremes window = windowExtremes(smoothed, x, y, featureRadius);
                if (h < window.max - EPS) {
                    continue;
                }
                float relief = h - window.min;
                if (relief > EPS) {
                    out.add(new Candidate(KeyPointType.PEAK, x, y, relief));
                }
            }
        }
    }

    /**
     * Troughs: ground that curves upwards across one axis, scored by how far it climbs on both
     * sides of that axis. Erosion drains valleys to the sea, so requiring a closed basin (a
     * two-dimensional minimum) finds nothing on real islands — the cross-section is what matters.
     */
    private static void collectValleys(
            float @NonNull [] @NonNull [] smoothed,
            float seaLevel,
            int featureRadius,
            @NonNull List<Candidate> out) {
        int n = smoothed.length;
        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                if (smoothed[y][x] <= seaLevel) {
                    continue;
                }
                Curvature curvature = curvature(smoothed, x, y);
                if (curvature.major <= 0f) {
                    continue;
                }
                float relief = crossSectionRelief(
                        smoothed, x, y, curvature.axisX, curvature.axisY, featureRadius);
                if (relief > EPS) {
                    out.add(new Candidate(KeyPointType.VALLEY, x, y, relief));
                }
            }
        }
    }

    /**
     * How far the ground climbs on both sides of a cell along {@code (axisX, axisY)}. Returns zero
     * unless the cell is flanked by rising ground in both directions, which rules out hillsides.
     */
    private static float crossSectionRelief(
            float @NonNull [] @NonNull [] field,
            int cx,
            int cy,
            float axisX,
            float axisY,
            int radius) {
        float forward = sideRise(field, cx, cy, axisX, axisY, radius);
        if (forward <= 0f) {
            return 0f;
        }
        float backward = sideRise(field, cx, cy, -axisX, -axisY, radius);
        if (backward <= 0f) {
            return 0f;
        }
        // The shallower flank sets the depth: a valley is only as deep as its lower wall
        return Math.min(forward, backward);
    }

    /** Highest rise above the centre along one direction, or zero if the ground falls away first. */
    private static float sideRise(
            float @NonNull [] @NonNull [] field,
            int cx,
            int cy,
            float dirX,
            float dirY,
            int radius) {
        int n = field.length;
        float center = field[cy][cx];
        float rise = 0f;
        for (int step = 1; step <= radius; step++) {
            int x = Math.round(cx + dirX * step);
            int y = Math.round(cy + dirY * step);
            if (x < 0 || y < 0 || x >= n || y >= n) {
                break;
            }
            float v = field[y][x];
            if (v < center - EPS) {
                return 0f;
            }
            rise = Math.max(rise, v - center);
        }
        return rise;
    }

    /** Peaks of the tree-density field: the heart of each wood, not the average of all woods. */
    private static void collectForests(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            @NonNull List<int[]> trees,
            int featureRadius,
            @NonNull List<Candidate> out) {
        int n = heights.length;
        float[][] density = new float[n][n];
        for (int[] tree : trees) {
            if (tree == null || tree.length < 2) {
                continue;
            }
            int gx = tree[0];
            int gy = tree[1];
            if (gx < 0 || gy < 0 || gx >= n || gy >= n || heights[gy][gx] <= seaLevel) {
                continue;
            }
            density[gy][gx] += 1f;
        }

        float[][] blurred = boxBlur(density, Math.max(2, featureRadius / 2));
        collectDensityPeaks(blurred, heights, seaLevel, featureRadius, KeyPointType.FOREST, out);
    }

    /**
     * Peaks of shoreline-sand coverage. Using coverage peaks rather than the centroid of the
     * coastline matters: the coast forms one ring whose centroid is the middle of the island.
     */
    private static void collectBeaches(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            int featureRadius,
            @NonNull List<Candidate> out) {
        int n = heights.length;
        float[][] sand = new float[n][n];
        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                float h = heights[y][x];
                if (h <= seaLevel || h > seaLevel + BEACH_MAX_ABOVE_SEA) {
                    continue;
                }
                if (touchesWater(heights, seaLevel, x, y)) {
                    sand[y][x] = 1f;
                }
            }
        }

        float[][] blurred = boxBlur(sand, Math.max(2, featureRadius / 2));
        collectDensityPeaks(blurred, heights, seaLevel, featureRadius, KeyPointType.BEACH, out);
    }

    private static void collectDensityPeaks(
            float @NonNull [] @NonNull [] blurred,
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            int featureRadius,
            @NonNull KeyPointType type,
            @NonNull List<Candidate> out) {
        int n = blurred.length;
        for (int y = 1; y < n - 1; y++) {
            for (int x = 1; x < n - 1; x++) {
                float d = blurred[y][x];
                if (d <= EPS || heights[y][x] <= seaLevel) {
                    continue;
                }
                if (!isLocalMax(blurred, x, y)) {
                    continue;
                }
                if (d < windowExtremes(blurred, x, y, featureRadius).max - EPS) {
                    continue;
                }
                out.add(new Candidate(type, x, y, d));
            }
        }
    }

    /**
     * Inland water only: connected components of {@code h <= sea} that do not touch the map
     * border. Border-touching water is the surrounding ocean.
     */
    private static void collectLakes(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            @NonNull List<Candidate> out) {
        int n = heights.length;
        boolean[][] visited = new boolean[n][n];
        int[] qx = new int[n * n];
        int[] qy = new int[n * n];

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (visited[y][x] || heights[y][x] > seaLevel) {
                    continue;
                }

                int head = 0;
                int tail = 1;
                qx[0] = x;
                qy[0] = y;
                visited[y][x] = true;

                long sumX = 0;
                long sumY = 0;
                boolean touchesBorder = false;

                while (head < tail) {
                    int cx = qx[head];
                    int cy = qy[head];
                    head++;
                    sumX += cx;
                    sumY += cy;
                    if (cx == 0 || cy == 0 || cx == n - 1 || cy == n - 1) {
                        touchesBorder = true;
                    }
                    tail = enqueueIfWater(heights, seaLevel, visited, qx, qy, tail, n, cx + 1, cy);
                    tail = enqueueIfWater(heights, seaLevel, visited, qx, qy, tail, n, cx - 1, cy);
                    tail = enqueueIfWater(heights, seaLevel, visited, qx, qy, tail, n, cx, cy + 1);
                    tail = enqueueIfWater(heights, seaLevel, visited, qx, qy, tail, n, cx, cy - 1);
                }

                int cells = tail;
                if (touchesBorder || cells < LAKE_MIN_CELLS) {
                    continue;
                }
                // Snap the label onto water: a crescent lake's centroid can fall on land
                int centerX = (int) (sumX / cells);
                int centerY = (int) (sumY / cells);
                int bestIndex = 0;
                long bestDistSq = Long.MAX_VALUE;
                for (int i = 0; i < cells; i++) {
                    long dx = qx[i] - centerX;
                    long dy = qy[i] - centerY;
                    long distSq = dx * dx + dy * dy;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestIndex = i;
                    }
                }
                out.add(new Candidate(KeyPointType.LAKE, qx[bestIndex], qy[bestIndex], cells));
            }
        }
    }

    /**
     * Pick landmarks with spatial spread, covering every available kind once before allowing a
     * second landmark of the same kind, then filling the remaining slots round-robin across kinds.
     * The coverage pass relaxes spacing rather than skipping a kind, so a map never loses a kind
     * just because its best example sits near another landmark.
     */
    private static @NonNull List<KeyPoint> select(
            @NonNull List<Candidate> sorted,
            int budget,
            int gridSize) {
        int spreadDist = Math.round(gridSize / (2f * (float) Math.sqrt(Math.max(1, budget))));
        int labelDist = Math.max(4, gridSize / 24);
        int[] coverageDistances = {spreadDist, spreadDist / 2, labelDist};

        List<Candidate> accepted = new ArrayList<>(budget);
        List<KeyPoint> chosen = new ArrayList<>(budget);

        for (KeyPointType type : coverageOrder(sorted)) {
            if (chosen.size() >= budget) {
                break;
            }
            Candidate pick = null;
            for (int distance : coverageDistances) {
                pick = bestOfType(sorted, type, accepted, distance);
                if (pick != null) {
                    break;
                }
            }
            if (pick == null) {
                pick = bestOfType(sorted, type, List.of(), 0);
            }
            if (pick != null) {
                accept(pick, chosen, accepted);
            }
        }

        // Fill remaining slots round-robin so a coastline full of good beaches cannot crowd out
        // the second-best summit or wood
        List<KeyPointType> order = coverageOrder(sorted);
        boolean placedThisRound = true;
        while (chosen.size() < budget && placedThisRound) {
            placedThisRound = false;
            for (KeyPointType type : order) {
                if (chosen.size() >= budget) {
                    break;
                }
                Candidate pick = bestOfType(sorted, type, accepted, spreadDist);
                if (pick != null) {
                    accept(pick, chosen, accepted);
                    placedThisRound = true;
                }
            }
        }
        return chosen;
    }

    /** Kinds ordered by their strongest example, so the most striking landmark is placed first. */
    private static @NonNull List<KeyPointType> coverageOrder(@NonNull List<Candidate> sorted) {
        List<KeyPointType> order = new ArrayList<>(KeyPointType.values().length);
        for (Candidate c : sorted) {
            if (!order.contains(c.type)) {
                order.add(c.type);
            }
        }
        return order;
    }

    private static @Nullable Candidate bestOfType(
            @NonNull List<Candidate> sorted,
            @NonNull KeyPointType type,
            @NonNull List<Candidate> accepted,
            int minDistance) {
        for (Candidate c : sorted) {
            if (c.type != type || accepted.contains(c)) {
                continue;
            }
            if (isFarEnough(c, accepted, minDistance)) {
                return c;
            }
        }
        return null;
    }

    private static boolean isFarEnough(
            @NonNull Candidate c,
            @NonNull List<Candidate> accepted,
            int minDistance) {
        int minDistSq = minDistance * minDistance;
        for (Candidate prev : accepted) {
            int dx = c.gridX - prev.gridX;
            int dy = c.gridY - prev.gridY;
            if (dx * dx + dy * dy < minDistSq) {
                return false;
            }
        }
        return true;
    }

    private static void accept(
            @NonNull Candidate c,
            @NonNull List<KeyPoint> chosen,
            @NonNull List<Candidate> accepted) {
        accepted.add(c);
        chosen.add(new KeyPoint(
                c.type,
                UnitGrid.coordinateFromGrid(c.gridX),
                UnitGrid.coordinateFromGrid(c.gridY),
                c.score,
                c.type.label()));
    }

    private static int enqueueIfWater(
            float @NonNull [] @NonNull [] heights,
            float seaLevel,
            boolean @NonNull [] @NonNull [] visited,
            int @NonNull [] qx,
            int @NonNull [] qy,
            int tail,
            int n,
            int x,
            int y) {
        if (x < 0 || y < 0 || x >= n || y >= n || visited[y][x] || heights[y][x] > seaLevel) {
            return tail;
        }
        visited[y][x] = true;
        qx[tail] = x;
        qy[tail] = y;
        return tail + 1;
    }

    private static boolean touchesWater(float @NonNull [] @NonNull [] heights, float seaLevel, int x, int y) {
        return heights[y][x + 1] <= seaLevel
                || heights[y][x - 1] <= seaLevel
                || heights[y + 1][x] <= seaLevel
                || heights[y - 1][x] <= seaLevel;
    }

    /**
     * Principal curvatures from the eigenvalues of the discrete Hessian, with the axis along which
     * curvature is strongest. {@code major < 0} means a dome, {@code major > 0} a trough or bowl.
     */
    private static @NonNull Curvature curvature(float @NonNull [] @NonNull [] h, int x, int y) {
        float hxx = h[y][x + 1] + h[y][x - 1] - 2f * h[y][x];
        float hyy = h[y + 1][x] + h[y - 1][x] - 2f * h[y][x];
        float hxy = (h[y + 1][x + 1] - h[y + 1][x - 1] - h[y - 1][x + 1] + h[y - 1][x - 1]) * 0.25f;

        float mean = 0.5f * (hxx + hyy);
        float halfDiff = 0.5f * (hxx - hyy);
        float spread = (float) Math.sqrt(halfDiff * halfDiff + hxy * hxy);
        float major = mean + spread;

        // Eigenvector of the larger eigenvalue; falls back to the x axis when curvature is isotropic
        float axisX = hxy;
        float axisY = major - hxx;
        float length = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (length < EPS) {
            axisX = 1f;
            axisY = 0f;
        } else {
            axisX /= length;
            axisY /= length;
        }
        return new Curvature(major, axisX, axisY);
    }

    /** Plateau tolerant: after smoothing, broad crests rarely have one strictly highest cell. */
    private static boolean isLocalMax(float @NonNull [] @NonNull [] field, int x, int y) {
        float v = field[y][x];
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (field[y + dy][x + dx] > v) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @NonNull Extremes windowExtremes(
            float @NonNull [] @NonNull [] field,
            int cx,
            int cy,
            int radius) {
        int n = field.length;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int fromY = Math.max(0, cy - radius);
        int toY = Math.min(n - 1, cy + radius);
        int fromX = Math.max(0, cx - radius);
        int toX = Math.min(n - 1, cx + radius);
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                float v = field[y][x];
                if (v < min) {
                    min = v;
                }
                if (v > max) {
                    max = v;
                }
            }
        }
        return new Extremes(min, max);
    }

    private static float @NonNull [] @NonNull [] boxBlur(float @NonNull [] @NonNull [] src, int radius) {
        int n = src.length;
        float[][] tmp = new float[n][n];
        float[][] dst = new float[n][n];
        int r = Math.max(0, radius);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float sum = 0f;
                int count = 0;
                for (int dx = -r; dx <= r; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= n) {
                        continue;
                    }
                    sum += src[y][xx];
                    count++;
                }
                tmp[y][x] = sum / count;
            }
        }
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float sum = 0f;
                int count = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= n) {
                        continue;
                    }
                    sum += tmp[yy][x];
                    count++;
                }
                dst[y][x] = sum / count;
            }
        }
        return dst;
    }

    private record Curvature(float major, float axisX, float axisY) {
    }

    private record Extremes(float min, float max) {
    }

    private record Candidate(@NonNull KeyPointType type, int gridX, int gridY, float score) {
    }
}
