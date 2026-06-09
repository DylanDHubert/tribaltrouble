package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Ship;

public final class ShipSlideController extends Controller {
    private final Ship ship;

    public ShipSlideController(Ship ship) {
        super(1);
        this.ship = ship;
    }

    public final void decide() {
        if (ship.isDead()) {
            return;
        }
        if (shouldGiveUp(0)) {
            ship.popController();
        } else {
            ship.setBehaviour(new ShipSlideBehaviour(ship));
        }
    }
}
