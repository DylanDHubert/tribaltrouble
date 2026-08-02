package com.oddlabs.tt.net;

import com.oddlabs.util.Color;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Wire format for syncing a player's personal lobby color over the existing chat channel, so lobbies with
 * mismatched client versions degrade gracefully instead of disconnecting (no new ARMI methods, no PlayerSlot
 * serialization changes). Any parse failure is treated as "not a color message" rather than an error.
 */
public final class PlayerColorMessage {
    private static final String PREFIX = "\u0001PCOLOR ";

    private PlayerColorMessage() {
    }

    public static @NonNull String format(int slot, @NonNull Vector4f color) {
        return String.format(Locale.ROOT, "%s%d %.3f %.3f %.3f", PREFIX, slot, color.x(), color.y(), color.z());
    }

    public static boolean isColorMessage(@Nullable String chat) {
        return chat != null && chat.startsWith(PREFIX);
    }

    /**
     * Parses a color message. Returns null for anything that isn't a well-formed color message (wrong prefix,
     * malformed slot/color fields, out-of-range values) so callers can silently ignore it instead of crashing.
     */
    public static @Nullable Parsed tryParse(@Nullable String chat) {
        if (!isColorMessage(chat)) {
            return null;
        }
        String[] parts = chat.substring(PREFIX.length()).trim().split(" ");
        if (parts.length != 4) {
            return null;
        }
        try {
            int slot = Integer.parseInt(parts[0]);
            float r = Float.parseFloat(parts[1]);
            float g = Float.parseFloat(parts[2]);
            float b = Float.parseFloat(parts[3]);
            if (slot < 0 || !isNormalized(r) || !isNormalized(g) || !isNormalized(b)) {
                return null;
            }
            return new Parsed(slot, Color.argb4v((0xFF << 24) | (asByte(r) << 16) | (asByte(g) << 8) | asByte(b)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNormalized(float value) {
        return value >= 0f && value <= 1f;
    }

    private static int asByte(float value) {
        return Math.round(value * 255f) & 0xFF;
    }

    public record Parsed(int slot, @NonNull Vector4f color) {
    }
}
