package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.gui.ToolTipBox;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.pathfinder.ShipTrajectory;
import com.oddlabs.tt.pathfinder.ShipTrajectoryPoint;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;

public final class SailBehaviour implements Behaviour {
    private static final float SHIP_SPEED = 0.2f;
    private static final int NO_COLLISION = 0;
    private static final int RESOLVABLE_COLLISION = 1;
    private static final int UNRESOLVABLE_COLLISION = 2;
    private static final float TIMEOUT = 1.0f;

    private final Ship ship;
    private final Target target;
    private float timer = 0.0f;

    private final ShipTrajectory trajectory;

    private boolean blocked = false;

    public SailBehaviour(Ship ship, Target t) {
        this.ship = ship;
        this.target = t;

        this.trajectory = new ShipTrajectory(ship, t);
    }

    public final boolean isBlocking() {
        return blocked;
    }

    public final ShipTrajectory getTrajectory() {
        return trajectory;
    }

    public void appendToolTip(ToolTipBox tool_tip_box) {
        tool_tip_box.append("SailBehaviour: ");
        if (blocked) {
            tool_tip_box.append("BLOCKED");
        } else {
            tool_tip_box.append("MOVING");
        }
    }

    private State endTrip() {
        if (!trajectory.isComplete() || !trajectory.reachedGoal()) {
            if (timer >= TIMEOUT) {
                ship.reportStuck();
                return State.INTERRUPTIBLE;
            } else {
                return State.UNINTERRUPTIBLE;
            }
        } else {
            ship.endTrip();
            return State.DONE;
        }
    }

    @Override
    public @NonNull State animate(float t) {
        if (ship.isDead()) {
            return State.DONE;
        }

        ship.setLayer(UnitGrid.SEA);

        if (!trajectory.exists()) {
            ship.reportStuck();
            return State.INTERRUPTIBLE;
        }

        int rowers = ship.getShipHR().countRowers();
        if (rowers == 0) {
            ship.endTrip();
            return State.DONE;
        }
        float speed = rowers * SHIP_SPEED;
        ShipTrajectoryPoint new_pose = trajectory.advance(speed * t);

        if (trajectory.reachedGoal()) {
            timer += t;
            return endTrip();
        }

        ShipTrajectoryPoint fromPoint = new ShipTrajectoryPoint(ship);

        if (trajectory.checkCollisionOnLine(fromPoint, new_pose.moved(6), 5)) {
            timer += t;
            return endTrip();
        }

        ship.free();
        ship.setPosition(new_pose.positionX, new_pose.positionY);
        ship.setGridPosition(new_pose.gridX, new_pose.gridY);
        ship.setDirection(new_pose.directionX, new_pose.directionY);
        ship.occupy();

        timer = 0.0f;

        return State.UNINTERRUPTIBLE;
    }

    public final void forceInterrupted() {
    }
}
