package com.oddlabs.tt.landscape;

import org.jspecify.annotations.NonNull;

/**
 * Geographic landmark kinds detected for minimap labeling.
 */
public enum KeyPointType {
    PEAK("Peak"),
    VALLEY("Valley"),
    FOREST("Forest"),
    LAKE("Lake"),
    BEACH("Beach");

    private final @NonNull String label;

    KeyPointType(@NonNull String label) {
        this.label = label;
    }

    /** Display name drawn on the minimap. */
    public @NonNull String label() {
        return label;
    }
}
