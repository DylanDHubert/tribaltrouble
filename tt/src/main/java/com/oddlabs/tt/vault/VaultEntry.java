package com.oddlabs.tt.vault;

import org.jspecify.annotations.NonNull;

/**
 * ONE SPRITE FROM geometry.xml — GROUP + NAME MAP TO A .binsprite PATH.
 */
public record VaultEntry(
        @NonNull String group,
        @NonNull String name,
        float scale,
        boolean modulateColor
) {
    public @NonNull String binspritePath() {
        return "/geometry/" + group + "/" + name + ".binsprite";
    }

    public @NonNull String displayName() {
        return group + " / " + name;
    }
}
