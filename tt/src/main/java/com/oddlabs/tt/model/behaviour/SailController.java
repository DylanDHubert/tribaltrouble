package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.util.Target;

public final class SailController extends Controller {
    private final Ship ship;
    private final Target target;
    private boolean backwards = false;
    private int trials = 0;

    private static final int MAX_TRIALS = 4;

    public SailController(Ship ship, Target t) {
        super(1);
        this.ship = ship;
        this.target = t;
    }

    public final void decide() {
        if (ship.isDead()) {
            return;
        }
        if (shouldGiveUp(0)) {
            if (trials == MAX_TRIALS) {
                ship.popController();
            } else {
                trials++;
                backwards = !backwards;
                setBehaviour();
            }
        } else {
            if (!ship.slid()) {
                ship.setBehaviour(new ShipSlideBehaviour(ship));
            } else {
                setBehaviour();
            }
        }
    }

    private void setBehaviour() {
        if (backwards) {
            ship.setBehaviour(new ReverseSailBehaviour(ship));
        } else {
            ship.setBehaviour(new SailBehaviour(ship, target));
        }
    }
}
