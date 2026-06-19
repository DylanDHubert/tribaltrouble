package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.gui.ToolTipBox;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.pathfinder.ShipTrajectory;
import com.oddlabs.tt.pathfinder.ShipTrajectoryPoint;
import com.oddlabs.tt.pathfinder.UnitGrid;
import org.jspecify.annotations.NonNull;


public final class ReverseSailBehaviour implements Behaviour {
    private final Ship ship;
    private boolean blocked = false;
    private final ShipTrajectoryPoint curr;

    private final UnitGrid grid;
    private final int grid_size;

    public ReverseSailBehaviour(Ship ship) {
        this.ship = ship;
        grid = ship.getUnitGrid();
        grid_size = grid.getGridSize();

        curr = new ShipTrajectoryPoint(ship);
    }

    public void appendToolTip(ToolTipBox tool_tip_box) {
        tool_tip_box.append("ReverseSailBehaviour: ");
        if (blocked) {
            tool_tip_box.append("BLOCKED");
        } else {
            tool_tip_box.append("MOVING");
        }
    }

    @Override
    public @NonNull State animate(float t) {
        if (ship.isDead()) {
            return State.DONE;
        }

        ship.setLayer(UnitGrid.SEA);

        ShipTrajectoryPoint shipPt = new ShipTrajectoryPoint(ship);

        int rowers = ship.getShipHR().countRowers();
        if (rowers == 0) {
            ship.endTrip();
            return State.DONE;
        }

        float step = rowers * 0.2f * t;

        ShipTrajectory.CollisionState[] state = new ShipTrajectory.CollisionState[1];

        // If it's clear infront of the ship
        if (!ShipTrajectory.checkCollisionOnLine(grid, ship, curr.moved(4), curr.moved(15), 6, state)) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        // If it's blocked behind the ship
        if (ShipTrajectory.checkCollisionOnLine(grid, ship, curr.moved(-4), curr.moved(-step).moved(-8), 6, state)) {
            if (state[0] == ShipTrajectory.CollisionState.LAND) {
                // If blocked by land, it should replan
                ship.reportStuck();
                return State.INTERRUPTIBLE;
            } else {
                // If blocked by a ship, it can wait
                return State.UNINTERRUPTIBLE;
            }
        }

        curr.move(-step);

        ship.free();
        ship.setPosition(curr.positionX, curr.positionY);
        ship.setGridPosition(curr.gridX, curr.gridY);
        ship.occupy();

        return State.UNINTERRUPTIBLE;
    }

    public final boolean isBlocking() {
        return blocked;
    }

    public final void forceInterrupted() {
    }
}
