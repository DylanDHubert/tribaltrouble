package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.MatchmakingServerInterface;
import com.oddlabs.tt.global.Settings;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Per-lobby-session registry of player colors received over the network, layered on top of the local
 * {@link Settings#team_colours} defaults. Colors are resolved per-slot: a color received from the network takes
 * priority, then the local player's own saved preference, then the default palette. Never throws; unknown slots
 * simply fall back to defaults, so a mismatched or older peer degrades gracefully instead of breaking the lobby.
 */
public final class PlayerColors {
    private final @Nullable Vector4f @NonNull [] network_colors = new Vector4f[MatchmakingServerInterface.MAX_PLAYERS];

    public @NonNull Vector4fc getColor(int slot, int local_player_slot) {
        if (slot < 0 || slot >= network_colors.length) {
            return Settings.DEFAULT_TEAM_COLOURS[0];
        }
        Vector4f network_color = network_colors[slot];
        if (network_color != null) {
            return network_color;
        }
        if (slot == local_player_slot) {
            return Settings.getSettings().team_colours[slot];
        }
        return Settings.DEFAULT_TEAM_COLOURS[slot];
    }

    public void setNetworkColor(int slot, @NonNull Vector4f color) {
        if (slot >= 0 && slot < network_colors.length) {
            network_colors[slot] = new Vector4f(color);
        }
    }
}
