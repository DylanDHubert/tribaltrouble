package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.tt.resource.WorldGenerator;

public interface ConfigurationListener extends ErrorListener {
    void connected(Client client, Game game, WorldGenerator generator, int player_slot, int player_count);

    void setPlayers(PlayerSlot[] players);

    void gameStarted();

    /**
     * Called whenever a player's color arrives over the network (see {@link PlayerColors}), so the lobby UI can
     * refresh immediately instead of waiting for the next full {@link #setPlayers} broadcast.
     */
    default void playerColorChanged() {
    }
}
