package com.oddlabs.tt.gamemode.twintotems;

import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.model.SceneryModel;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.net.PeerHub;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.trigger.GameOverDelayTrigger;
import com.oddlabs.tt.trigger.GameOverTrigger;
import com.oddlabs.tt.util.StateChecksum;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

/**
 * Tracks uncontested ownership of both Twin Totems objectives. Holding both for
 * {@link #HOLD_BOTH_SECONDS} wins; Standard wipe still ends the match via {@link GameOverTrigger}.
 */
public final class TwinTotemsController implements Animated {
    // -------------------------------------------------------------------------
    // HOLD BOTH TOTEMS UNCONTESTED THIS MANY SECONDS STRAIGHT TO WIN. TUNE HERE.
    // PROGRESS RESETS IF EITHER TOTEM IS LOST OR CONTESTED.
    // -------------------------------------------------------------------------
    public static final float HOLD_BOTH_SECONDS = 60f;

    // CAPTURE RADIUS IN GRID CELLS AROUND EACH OBJECTIVE. TUNE HERE.
    public static final int CAPTURE_RADIUS_GRID = 8;

    private static final int UNOWNED = Integer.MIN_VALUE;
    private static final int CONTESTED = Integer.MIN_VALUE + 1;

    private final @NonNull WorldViewer viewer;
    private final @NonNull SceneryModel totem;
    private final @NonNull SceneryModel treasure;
    private final int radius_sq = CAPTURE_RADIUS_GRID * CAPTURE_RADIUS_GRID;

    private int totem_owner = UNOWNED;
    private int treasure_owner = UNOWNED;
    private int holding_team = UNOWNED;
    private float hold_seconds;
    private boolean finished;
    private int last_announced_ten = -1;

    TwinTotemsController(@NonNull WorldViewer viewer, @NonNull SceneryModel totem, @NonNull SceneryModel treasure) {
        this.viewer = viewer;
        this.totem = totem;
        this.treasure = treasure;
        viewer.getWorld().getAnimationManagerGameTime().registerAnimation(this);
        chat(TwinTotemsModeRules.i18n("mode_start"));
    }

    @Override
    public void animate(float t) {
        if (finished) {
            return;
        }

        int new_totem = ownerOf(totem.getGridX(), totem.getGridY());
        int new_treasure = ownerOf(treasure.getGridX(), treasure.getGridY());

        if (new_totem != totem_owner) {
            announceOwnerChange("totem_name", totem_owner, new_totem);
            totem_owner = new_totem;
        }
        if (new_treasure != treasure_owner) {
            announceOwnerChange("treasure_name", treasure_owner, new_treasure);
            treasure_owner = new_treasure;
        }

        boolean both_held = totem_owner != UNOWNED
                && totem_owner != CONTESTED
                && totem_owner == treasure_owner;

        if (both_held) {
            if (holding_team != totem_owner) {
                holding_team = totem_owner;
                hold_seconds = 0f;
                last_announced_ten = -1;
                chat(TwinTotemsModeRules.i18n("hold_started", teamLabel(holding_team), (int) HOLD_BOTH_SECONDS));
            }
            hold_seconds += t;
            int tens = (int) (hold_seconds / 10f);
            if (tens > last_announced_ten && hold_seconds < HOLD_BOTH_SECONDS) {
                last_announced_ten = tens;
                int remaining = Math.max(0, Math.round(HOLD_BOTH_SECONDS - hold_seconds));
                chat(TwinTotemsModeRules.i18n("hold_progress", teamLabel(holding_team), remaining));
            }
            if (hold_seconds >= HOLD_BOTH_SECONDS) {
                finishWin(holding_team);
            }
        } else if (holding_team != UNOWNED) {
            chat(TwinTotemsModeRules.i18n("hold_broken", teamLabel(holding_team)));
            holding_team = UNOWNED;
            hold_seconds = 0f;
            last_announced_ten = -1;
        }
    }

    private int ownerOf(int grid_x, int grid_y) {
        int found_team = UNOWNED;
        for (Player player : viewer.getWorld().getPlayers()) {
            if (player.getPlayerInfo().getTeam() == PlayerInfo.TEAM_NEUTRAL) {
                continue;
            }
            for (Selectable<?> s : player.getUnits().getSet()) {
                if (!(s instanceof Unit) || s.isDead()) {
                    continue;
                }
                int dx = s.getGridX() - grid_x;
                int dy = s.getGridY() - grid_y;
                if (dx * dx + dy * dy > radius_sq) {
                    continue;
                }
                int team = player.getPlayerInfo().getTeam();
                if (found_team == UNOWNED) {
                    found_team = team;
                } else if (found_team != team) {
                    return CONTESTED;
                }
            }
        }
        return found_team;
    }

    private void announceOwnerChange(@NonNull String name_key, int old_owner, int new_owner) {
        String name = TwinTotemsModeRules.i18n(name_key);
        if (new_owner == CONTESTED) {
            chat(TwinTotemsModeRules.i18n("objective_contested", name));
        } else if (new_owner == UNOWNED) {
            chat(TwinTotemsModeRules.i18n("objective_lost", name));
        } else if (old_owner == UNOWNED || old_owner == CONTESTED) {
            chat(TwinTotemsModeRules.i18n("objective_captured", name, teamLabel(new_owner)));
        } else {
            chat(TwinTotemsModeRules.i18n("objective_stolen", name, teamLabel(new_owner)));
        }
    }

    private void finishWin(int winning_team) {
        finished = true;
        viewer.getWorld().getAnimationManagerGameTime().removeAnimation(this);

        GameOverTrigger wipe_trigger = viewer.getGameOverTrigger();
        if (wipe_trigger != null) {
            wipe_trigger.disable();
        }

        Player local = viewer.getLocalPlayer();
        boolean local_won = local.getPlayerInfo().getTeam() == winning_team;
        chat(TwinTotemsModeRules.i18n("hold_complete", teamLabel(winning_team)));
        if (local_won) {
            viewer.getPeerHub().gameWon();
            new GameOverDelayTrigger(viewer, viewer.getGUIRoot().getDelegate().getCamera(),
                    TwinTotemsModeRules.i18n("you_victorious_totems"));
        } else {
            viewer.getPeerHub().leaveGame();
            new GameOverDelayTrigger(viewer, viewer.getGUIRoot().getDelegate().getCamera(),
                    TwinTotemsModeRules.i18n("you_defeated_totems"));
        }
    }

    private void chat(@NonNull String message) {
        viewer.getPeerHub().receiveChat(PeerHub.SYSTEM_NAME, message, false);
    }

    private static @NonNull String teamLabel(int team) {
        return TwinTotemsModeRules.i18n("team_label", team + 1);
    }

    @Override
    public void updateChecksum(@NonNull StateChecksum checksum) {
        checksum.update(totem_owner);
        checksum.update(treasure_owner);
        checksum.update(holding_team);
        checksum.update(Float.floatToIntBits(hold_seconds));
    }
}
