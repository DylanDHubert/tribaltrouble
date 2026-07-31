package com.oddlabs.tt.gamemode.twintotems;

import com.oddlabs.matchmaking.GameModeOption;
import com.oddlabs.tt.gamemode.GameModeRules;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.model.SceneryModel;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.render.SpriteKey;
import com.oddlabs.tt.util.Utils;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Twin Totems: hold both objectives uncontested for {@link TwinTotemsController#HOLD_BOTH_SECONDS}, or win by
 * Standard wipe.
 */
public final class TwinTotemsModeRules implements GameModeRules {
    public static final @NonNull String OPTION_RATED = "rated";

    private static final @NonNull List<@NonNull GameModeOption> OPTIONS = List.of(
            new GameModeOption(OPTION_RATED, GameModeOption.Type.BOOL, false, "rated_game"));

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(TwinTotemsModeRules.class.getName());

    @Override
    public @NonNull List<@NonNull GameModeOption> getOptions() {
        return OPTIONS;
    }

    @Override
    public boolean isPlayerAlive(@NonNull Player player) {
        int units = player.getUnitCountContainer().getNumSupplies();
        return units > 0 || player.hasActiveChieftain() || player.getQuarters() != null;
    }

    @Override
    public void onGameStart(@NonNull WorldViewer viewer) {
        HeightMap height_map = viewer.getWorld().getHeightMap();
        boolean[][] access = height_map.getAccessGrid();
        int mid = height_map.getGridUnitsPerWorld() / 2;

        // DISTANCE FROM MAP CENTER AS A FRACTION OF HALF-MAP WIDTH. TUNE HERE.
        // 0.35 KEEPS TOTEMS OPPOSITE SIDES OF MIDDLE — NOT ON THE EDGE, NOT NEXT TO EACH OTHER.
        final float CENTER_OFFSET_FRACTION = 0.35f;
        int offset = Math.max(4, (int) (mid * CENTER_OFFSET_FRACTION));

        int[] totem_grid = findAccessibleNear(access, mid - offset, mid);
        int[] treasure_grid = findAccessibleNear(access, mid + offset, mid);

        SpriteKey[] treasures = viewer.getWorld().getRacesResources().getTreasures();
        float shadow = 2.6f;
        float cell = HeightMap.METERS_PER_UNIT_GRID;
        float half = cell / 2f;

        // TOTEM OBJECTIVE — LARGE ICON STATUE MESH
        SceneryModel totem = new SceneryModel(viewer.getWorld(),
                totem_grid[0] * cell + half, totem_grid[1] * cell + half,
                0f, -1f, treasures[0], shadow, true, i18n("totem_name"));
        // TREASURE OBJECTIVE — TREASURE CHEST MESH
        SceneryModel treasure = new SceneryModel(viewer.getWorld(),
                treasure_grid[0] * cell + half, treasure_grid[1] * cell + half,
                0f, -1f, treasures[4], shadow, true, i18n("treasure_name"));

        new TwinTotemsController(viewer, totem, treasure);
    }

    private static int @NonNull [] findAccessibleNear(boolean @NonNull [] @NonNull [] access, int goal_x, int goal_y) {
        int size = access.length;
        int gx = Math.clamp(goal_x, 0, size - 1);
        int gy = Math.clamp(goal_y, 0, size - 1);
        if (access[gy][gx]) {
            return new int[]{gx, gy};
        }
        for (int r = 1; r < size; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = gx + dx;
                    int y = gy + dy;
                    if (x >= 0 && y >= 0 && x < size && y < size && access[y][x]) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return new int[]{gx, gy};
    }

    static @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(BUNDLE, key, args);
    }
}
