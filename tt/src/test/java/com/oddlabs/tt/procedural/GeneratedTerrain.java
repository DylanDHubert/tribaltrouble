package com.oddlabs.tt.procedural;

import com.oddlabs.procedural.Channel;
import com.oddlabs.procedural.Tools;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.util.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds real generator terrain for tests. {@link Landscape} itself cannot be constructed headlessly
 * because it uploads blend textures to OpenGL, so this mirrors the height pipeline of
 * {@code Landscape.generateTerrainNative()} — the same Mountain, Voronoi, Hill, perturb, erode and
 * beach-shelf steps, at the same height scales — and places woods in clumps the way vegetation
 * generation does.
 *
 * <p>Kept in {@code com.oddlabs.tt.procedural} so the package-private generator primitives are
 * reachable. If the shipped pipeline changes materially, update this alongside it.
 */
public final class GeneratedTerrain {

    private final float[][] heights;
    private final float seaLevelMeters;
    private final List<int[]> trees;

    private GeneratedTerrain(float[][] heights, float seaLevelMeters, List<int[]> trees) {
        this.heights = heights;
        this.seaLevelMeters = seaLevelMeters;
        this.trees = trees;
    }

    public float[][] heights() {
        return heights;
    }

    public float seaLevelMeters() {
        return seaLevelMeters;
    }

    public List<int[]> trees() {
        return trees;
    }

    public static GeneratedTerrain island(int metersPerWorld, float hills, int seed) {
        int gridSize = metersPerWorld / 2;
        int heightScale = heightScale(metersPerWorld);

        Channel height = new Mountain(gridSize, Utils.powerOf2Log2(gridSize) - 6, 0.5f, seed)
                .toChannel().multiply(0.67f);

        Voronoi voronoi = new Voronoi(gridSize, 4, 4, 1, 1f, seed);
        height.channelAdd(voronoi.getDistance(-1f, 1f, 0f).brightness(1.5f).multiply(0.33f));
        if (gridSize > 128) {
            height.channelSubtract(voronoi.getDistance(1f, 0f, 0f).gamma(.5f).flipV().rotate(90));
        } else {
            height.channelSubtract(voronoi.getDistance(-1f, 1f, 0f).gamma(.5f).flipV().rotate(90));
        }

        height.perturb(new Midpoint(gridSize, 2, 0.5f, seed).toChannel(), 0.25f);
        Channel shape = new Hill(gridSize, Hill.OVAL).toChannel();
        height.channelAdd(shape.copy().multiply(0.15f));
        height.channelSubtract(shape.copy().invert().multiply(0.5f));
        height.erode((24f - hills * 12f) / gridSize, gridSize >> 2);
        height.channelMultiply(shape.gamma2());
        height.smooth(1);
        beachShelf(height);
        flattenEdges(height, gridSize);

        float[][] scaled = new float[gridSize][gridSize];
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                scaled[y][x] = heightScale * height.getPixel(x, y);
            }
        }

        float seaLevelMeters = heightScale * Globals.SEA_LEVEL;
        return new GeneratedTerrain(scaled, seaLevelMeters,
                clumpedTrees(scaled, seaLevelMeters, gridSize, metersPerWorld, seed));
    }

    private static int heightScale(int metersPerWorld) {
        return switch (metersPerWorld) {
            case 256 -> 32;
            case 512 -> 48;
            case 1024 -> 64;
            case 2048 -> 76;
            default -> throw new IllegalArgumentException("unsupported world size " + metersPerWorld);
        };
    }

    /** Mirrors {@code Landscape.beaches}: smooth shelves where the land meets the sea. */
    private static void beachShelf(Channel channel) {
        float sealevel = 1.1f * Globals.SEA_LEVEL;
        float threshold = 2f * sealevel;
        for (int y = 0; y < channel.height; y++) {
            for (int x = 0; x < channel.width; x++) {
                float value = channel.getPixel(x, y);
                if (value < sealevel) {
                    channel.putPixel(x, y, Tools.interpolateSmooth(0, sealevel, value / sealevel));
                } else if (value < threshold) {
                    channel.putPixel(x, y, Tools.interpolateSmooth(sealevel, 2f * threshold - sealevel,
                            0.5f * (value - sealevel) / (threshold - sealevel)));
                }
            }
        }
    }

    private static void flattenEdges(Channel height, int gridSize) {
        for (int x = 0; x < gridSize; x++) {
            height.putPixel(x, 0, 0f);
            height.putPixel(x, gridSize - 1, 0f);
        }
        for (int y = 0; y < gridSize; y++) {
            height.putPixel(0, y, 0f);
            height.putPixel(gridSize - 1, y, 0f);
        }
    }

    /**
     * Woods cluster where a noise channel peaks, at the tree count the shipped generator uses
     * ({@code 2^(2*log2(meters) - 9)}), which is sparse: a few hundred trees on a medium map.
     */
    private static List<int[]> clumpedTrees(
            float[][] heights,
            float seaLevelMeters,
            int gridSize,
            int metersPerWorld,
            int seed) {
        int maxTrees = (int) Math.pow(2, 2 * Utils.powerOf2Log2(metersPerWorld) - 9);
        Channel vegetation = new Midpoint(gridSize, 4, 0.6f, seed).toChannel();

        List<float[]> ranked = new ArrayList<>();
        for (int y = 1; y < gridSize - 1; y++) {
            for (int x = 1; x < gridSize - 1; x++) {
                if (heights[y][x] <= seaLevelMeters + 1f) {
                    continue;
                }
                ranked.add(new float[]{vegetation.getPixel(x, y), x, y});
            }
        }
        ranked.sort(Comparator
                .comparingDouble((float[] e) -> -e[0])
                .thenComparingDouble(e -> e[1])
                .thenComparingDouble(e -> e[2]));

        List<int[]> trees = new ArrayList<>(maxTrees);
        for (int i = 0; i < Math.min(maxTrees, ranked.size()); i++) {
            float[] entry = ranked.get(i);
            trees.add(new int[]{(int) entry[1], (int) entry[2]});
        }
        return trees;
    }
}
