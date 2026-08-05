package com.oddlabs.tt.landscape;

import org.jspecify.annotations.NonNull;

/**
 * A named geographic landmark on the map. Positions are world meters; detection is deterministic
 * from terrain/resources so every player sees the same labels for the same map.
 */
public record KeyPoint(
                       @NonNull KeyPointType type,
                       float worldX,
                       float worldY,
                       float score,
                       @NonNull String name) {
}
