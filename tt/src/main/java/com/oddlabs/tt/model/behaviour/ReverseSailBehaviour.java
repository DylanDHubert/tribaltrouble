package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.gui.ToolTipBox;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.pathfinder.ShipTrajectory;
import com.oddlabs.tt.pathfinder.ShipTrajectoryPoint;
import com.oddlabs.tt.pathfinder.UnitGrid;
import org.jspecify.annotations.NonNull;


public final class ReverseSailBehaviour implements Behaviour {
    private static final int NO_COLLISION = 0;
    private static final int RESOLVABLE_COLLISION = 1;
    private static final int UNRESOLVABLE_COLLISION = 2;

    private final Ship ship;
    private boolean blocked = false;
    private final ShipTrajectoryPoint curr;
    private final ShipTrajectoryPoint end;
    private final float total_distance;
    private float curr_distance;

    private final UnitGrid grid;
    private final int grid_size;

    public ReverseSailBehaviour(Ship ship) {
        this.ship = ship;
        grid = ship.getUnitGrid();
        grid_size = grid.getGridSize();

        curr = new ShipTrajectoryPoint(ship);
        end = ShipTrajectory.getNearestGap(grid, curr, curr.moved(-20), 12, 12, 5);

        curr_distance = 0.0f;
        if (end != null) {
            total_distance = curr.distanceTo(end);
        } else {
            total_distance = 0.0f;
        }
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

        if (total_distance == 0.0f || end == null) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        ship.setLayer(UnitGrid.SEA);

        ShipTrajectoryPoint shipPt = new ShipTrajectoryPoint(ship);

        int rowers = ship.getShipHR().countRowers();
        if (rowers == 0) {
            ship.endTrip();
            return State.DONE;
        }

        float step = rowers * 0.2f * t;

        // If it's blocked behind the ship
        if (ShipTrajectory.checkCollisionOnLine(grid, ship, curr, curr.moved(-step), 5)) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        // If it's clear infront of the ship
        if (!ShipTrajectory.checkCollisionOnLine(grid, ship, curr, curr.moved(12), 5)) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        curr.move(-step);
        curr_distance += step;

        ship.free();
        ship.setPosition(curr.positionX, curr.positionY);
        ship.setGridPosition(curr.gridX, curr.gridY);
        ship.occupy();

        if (total_distance <= curr_distance) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        return State.UNINTERRUPTIBLE;
    }

    public final boolean isBlocking() {
        return blocked;
    }

    public final void forceInterrupted() {
    }
}
