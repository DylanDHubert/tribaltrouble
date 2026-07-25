package com.oddlabs.tt.landscape;

/**
 * How many key points to label on a map. Medium (512m) uses {@link #KEY_POINT_QUANTIFIER};
 * large stays fixed so bumping medium does not inflate it.
 */
public final class KeyPointQuantifier {

    /** Target landmark count on a medium (512m) map. */
    public static final int KEY_POINT_QUANTIFIER = 7;

    /** Landmark count on a large (1024m) map — held fixed when medium changes. */
    public static final int LARGE_KEY_POINT_COUNT = 10;

    /** World size in meters that {@link #KEY_POINT_QUANTIFIER} refers to. */
    public static final int REFERENCE_METERS_PER_WORLD = 512;

    private static final int LARGE_METERS_PER_WORLD = 1024;

    private KeyPointQuantifier() {
    }

    /**
     * Landmark budget for a world of the given side length in meters.
     * Small≈4, medium=7, large=10, enormous=20.
     */
    public static int forWorldMeters(int metersPerWorld) {
        if (metersPerWorld <= 0) {
            return 1;
        }
        if (metersPerWorld == LARGE_METERS_PER_WORLD) {
            return LARGE_KEY_POINT_COUNT;
        }
        // Enormous and other sizes: scale from medium, but large is capped above
        if (metersPerWorld > LARGE_METERS_PER_WORLD) {
            return Math.round(LARGE_KEY_POINT_COUNT * (metersPerWorld / (float) LARGE_METERS_PER_WORLD));
        }
        return Math.max(1, Math.round(KEY_POINT_QUANTIFIER * (metersPerWorld / (float) REFERENCE_METERS_PER_WORLD)));
    }
}
