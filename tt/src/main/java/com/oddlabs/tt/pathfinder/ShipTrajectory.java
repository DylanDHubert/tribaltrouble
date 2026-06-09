package com.oddlabs.tt.pathfinder;

import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.util.DebugRender;
import com.oddlabs.tt.util.Target;

import java.util.ArrayList;
import java.util.List;

public final class ShipTrajectory {
    private final Ship ship;
    private final UnitGrid grid;

    private final List<ShipTrajectorySegment> trajectory;

    private boolean isComplete = true;

    private int currentSegmentIndex = 0;
    private float segmentProgress = 0.0f;
    private float totalProgress = 0.0f;

    public ShipTrajectory(Ship ship, Target t) {
        this.ship = ship;

        grid = ship.getUnitGrid();

        ShipTrajectoryPoint p0 = new ShipTrajectoryPoint(ship);
        ShipTrajectoryPoint p3 = pickTargetPosition(grid, ship, t);
        ShipTrajectoryPoint p2 = null;
        if (p3 != null) {
            p2 = p3.clone();
            p2.setDirectionTo(new ShipTrajectoryPoint(t));
            p2.move(-5);
        }
        ShipTrajectoryPoint p1 = null;
        p1 = p0.moved(10);

        List<ShipTrajectoryPoint> brokenDownPath = null;
        if (p1 != null && p2 != null) {
            brokenDownPath = breakDownPath(p1, p2, 0);
        }

        if (brokenDownPath != null) {
            optimizePath(brokenDownPath);
            brokenDownPath.add(p3);
            brokenDownPath.add(0, p0);
            trajectory = createTrajectory(brokenDownPath);
        } else {
            trajectory = null;
        }
    }

    public void debugRender(HeightMap heightmap) {
        if (trajectory == null) {
            return;
        }
        final float OFFSET = 2.0f;
        float z = heightmap.getSeaLevelMeters() + OFFSET;
        for (ShipTrajectorySegment segment : trajectory) {
            if (segment.isStraight) {
                DebugRender.drawLine(
                        segment.p0.positionX, segment.p0.positionY, z,
                        segment.p1.positionX, segment.p1.positionY, z,
                        0.0f, 1.0f, 0.0f);
            } else {
                drawArc(segment, z);
            }
        }
    }

    private void drawArc(ShipTrajectorySegment segment, float z) {
        float prevX = segment.p0.positionX;
        float prevY = segment.p0.positionY;
        for (int i = 1; i <= 16; i++) {
            float percent = (float) i / 16;
            ShipTrajectoryPoint pt = segment.center.clone();
            pt.setDirectionTo(segment.p0);
            float deltaAngle = (float) StrictMath.toDegrees(segment.length * percent / segment.radius);
            pt.rotate(deltaAngle * segment.angle_sign);
            pt.move(segment.radius);
            DebugRender.drawLine(prevX, prevY, z, pt.positionX, pt.positionY, z, 0.0f, 1.0f, 0.0f);
            prevX = pt.positionX;
            prevY = pt.positionY;
        }
    }

    public boolean exists() {
        return trajectory != null && trajectory.size() > 0;
    }

    public boolean isComplete() {
        return isComplete;
    }

    private ShipTrajectorySegment get(int index) {
        return trajectory.get(index);
    }

    public ShipTrajectoryPoint advance(float distance) {
        if (currentSegmentIndex >= trajectory.size()) {
            return null;
        }

        ShipTrajectoryPoint pt = new ShipTrajectoryPoint();
        while (distance > 0.0f && currentSegmentIndex < trajectory.size()) {
            distance = trajectory.get(currentSegmentIndex).advance(distance, pt);
            if (distance > 0.0f) {
                currentSegmentIndex++;
            }
        }
        return pt;
    }

    public boolean reachedGoal() {
        if (currentSegmentIndex >= trajectory.size()) {
            return true;
        }
        return false;
    }

    private final List<ShipTrajectoryPoint> breakDownPath(ShipTrajectoryPoint p0, ShipTrajectoryPoint p1, int depth) {
        if (depth > 50) {
            return null;
        }

        if (StrictMath.abs(p1.gridX - p0.gridX) <= 1 && StrictMath.abs(p1.gridY - p0.gridY) <= 1) {
            List<ShipTrajectoryPoint> result = new ArrayList<ShipTrajectoryPoint>();
            result.add(p0);
            result.add(p1);
            return result;
        }

        if (!checkCollisionOnLine(grid, ship, p0, p1, 6)) {
            List<ShipTrajectoryPoint> result = new ArrayList<ShipTrajectoryPoint>();
            result.add(p0);
            result.add(p1);
            return result;
        } else {
            ShipTrajectoryPoint pmid = new ShipTrajectoryPoint(
                    (int) StrictMath.round((p0.gridX + p1.gridX) * 0.5f),
                    (int) StrictMath.round((p0.gridY + p1.gridY) * 0.5f));

            float dir_x = p1.gridX - p0.gridX;
            float dir_y = p1.gridY - p0.gridY;
            float len = (float) StrictMath.sqrt(dir_x * dir_x + dir_y * dir_y);
            if (len < 0.001f) {
                List<ShipTrajectoryPoint> result = new ArrayList<ShipTrajectoryPoint>();
                result.add(p0);
                result.add(p1);
                return result;
            }

            dir_x /= len;
            dir_y /= len;
            float perp_x = -dir_y;
            float perp_y = dir_x;
            int grid_size = ship.getUnitGrid().getGridSize();
            int reach = grid_size;
            int pleftx = (int) StrictMath.max(
                    0,
                    StrictMath.min(
                            grid_size - 1,
                            StrictMath.round(pmid.gridX + perp_x * reach)));
            int plefty = (int) StrictMath.max(
                    0,
                    StrictMath.min(
                            grid_size - 1,
                            StrictMath.round(pmid.gridY + perp_y * reach)));
            int prightx = (int) StrictMath.max(
                    0,
                    StrictMath.min(
                            grid_size - 1,
                            StrictMath.round(pmid.gridX - perp_x * reach)));
            int prighty = (int) StrictMath.max(
                    0,
                    StrictMath.min(
                            grid_size - 1,
                            StrictMath.round(pmid.gridY - perp_y * reach)));

            ShipTrajectoryPoint pleft = new ShipTrajectoryPoint(pleftx, plefty);
            ShipTrajectoryPoint pright = new ShipTrajectoryPoint(prightx, prighty);
            ShipTrajectoryPoint gap1 = getNearestGap(grid, pmid, pleft, 20, 50, 6);
            ShipTrajectoryPoint gap2 = getNearestGap(grid, pmid, pright, 20, 50, 6);

            ShipTrajectoryPoint closest;
            if (gap1 != null && gap2 != null) {
                float gap1dist2 = gap1.gridDistanceTo(pmid);
                float gap2dist2 = gap2.gridDistanceTo(pmid);
                if (gap1dist2 <= gap2dist2) {
                    closest = gap1;
                } else {
                    closest = gap2;
                }
            } else if (gap1 != null) {
                closest = gap1;
            } else if (gap2 != null) {
                closest = gap2;
            } else {
                return null;
            }

            if ((closest.gridX == p0.gridX && closest.gridX == p0.gridY)
                    || (closest.gridX == p1.gridX && closest.gridY == p1.gridY)) {
                return null;
            }

            List<ShipTrajectoryPoint> firstHalf = breakDownPath(p0, closest, depth + 1);
            List<ShipTrajectoryPoint> secondHalf = breakDownPath(closest, p1, depth + 1);
            if (secondHalf != null && firstHalf != null) {
                List<ShipTrajectoryPoint> combined = new ArrayList<ShipTrajectoryPoint>();
                combined.addAll(firstHalf);
                combined.addAll(secondHalf);
                return combined;
            } else if (firstHalf != null) {
                isComplete = false;
                return firstHalf;
            } else {
                return null;
            }
        }
    }

    private final List<ShipTrajectorySegment> createTrajectory(List<ShipTrajectoryPoint> path) {
        List result = new ArrayList<ShipTrajectorySegment>();
        if (path == null || path.size() < 2) {
            return result;
        }

        int n = path.size();

        ShipTrajectoryPoint prev = path.get(0);

        for (int i = 1; i < n - 1; i++) {
            ShipTrajectoryPoint a = path.get(i - 1);
            ShipTrajectoryPoint b = path.get(i);
            ShipTrajectoryPoint c = path.get(i + 1);

            float clip0 = a.distanceTo(b) * 0.5f;
            float clip1 = b.distanceTo(c) * 0.5f;
            float clip = (float) StrictMath.min(clip0, clip1);
            clip = (float) StrictMath.min(clip, 20.0f);

            ShipTrajectoryPoint b_a = b.clone();
            b_a.setDirectionTo(a);
            b_a.move(clip);

            ShipTrajectoryPoint b_c = b.clone();
            b_c.setDirectionTo(c);
            b_c.move(clip);

            ShipTrajectoryPoint p0 = prev.clone();
            p0.setDirectionTo(b);
            ShipTrajectoryPoint p1 = b_a.clone();
            p1.copyDirection(p0);
            result.add(makeStraightSegment(p0, p1));

            ShipTrajectoryPoint center = b_a.rotated(90).intersection(b_c.rotated(90));
            if (center != null) {
                float radius = center.distanceTo(b_a);
                center.setDirectionTo(b_c);
                center.rotate(-90.0f);
                b_c.copyDirection(center);
                b_a.setDirectionTo(b);
                result.add(makeArcSegment(b_a, b_c, radius, center));
                prev = b_c;
            } else {
                // If there's no intersection, that's not a realistic turn the ship
                // could make. So we'll assume the path is incomplete and stop here.
                isComplete = false;
                return result;
            }
        }

        ShipTrajectoryPoint p1 = path.get(n - 1);
        prev.setDirectionTo(p1);
        p1.copyDirection(prev);
        result.add(makeStraightSegment(prev, p1));

        return result;
    }

    private ShipTrajectorySegment makeStraightSegment(ShipTrajectoryPoint p0, ShipTrajectoryPoint p1) {
        return new ShipTrajectorySegment(p0, p1);
    }

    private ShipTrajectorySegment makeArcSegment(
            ShipTrajectoryPoint p0,
            ShipTrajectoryPoint p1,
            float radius,
            ShipTrajectoryPoint center) {
        return new ShipTrajectorySegment(p0, p1, radius, center);
    }

    private final void optimizePath(List<ShipTrajectoryPoint> path) {
        if (path == null || path.size() < 3) {
            return;
        }

        boolean changed = true;
        while (changed && path.size() >= 3) {
            changed = false;
            int i = 1;
            while (i < path.size() - 1) {
                ShipTrajectoryPoint prev = path.get(i - 1);
                ShipTrajectoryPoint next = path.get(i + 1);

                if (!checkCollisionOnLine(grid, ship, prev, next, 6)) {
                    path.remove(i);
                    changed = true;
                } else {
                    i++;
                }
            }
        }
    }

    public static List<ShipTrajectoryPoint> pickTargetArray(UnitGrid grid, Target target, int numTargets) {
        List<ShipTrajectoryPoint> targets = new ArrayList<ShipTrajectoryPoint>();
        if (numTargets <= 0) {
            return targets;
        }

        ShipTrajectoryPoint midPt = pickTargetPosition(grid, null, target);
        if (midPt == null) {
            return targets;
        }
        targets.add(midPt);

        int numLeft = (numTargets - 1) / 2;
        int numRight = numTargets - numLeft - 1;
        for (int i = 1; i <= numLeft; i++) {
            ShipTrajectoryPoint pt = midPt.moved(10 * i);
            targets.add(pt);
        }
        for (int i = 1; i <= numRight; i++) {
            ShipTrajectoryPoint pt = midPt.moved(10 * i);
            targets.add(pt);
        }
        return targets;
    }

    public static ShipTrajectoryPoint pickTargetPosition(UnitGrid grid, Occupant self, Target target) {
        ShipTrajectoryPoint pt = new ShipTrajectoryPoint(target);
        float bestDist = 100.0f;
        ShipTrajectoryPoint bestGap = null;
        for (int i = 0; i < 48; i++) {
            float angle = i * 7.5f;
            ShipTrajectoryPoint gap = getNearestGap(grid, pt, pt.rotated(angle).moved(20), 12, 12, 6);
            if (gap != null) {
                float dist = gap.distanceTo(pt);
                if (dist < bestDist) {
                    bestGap = gap;
                    bestDist = dist;
                }
            }
        }
        return bestGap;
    }

    public static ShipTrajectoryPoint getNearestGap(
            UnitGrid grid, ShipTrajectoryPoint p0, ShipTrajectoryPoint p1, int minSize, int maxSize, int thickness) {
        int grid_size = grid.getGridSize();

        int dx = p1.gridX - p0.gridX;
        int dy = p1.gridY - p0.gridY;
        float line_len = (float) StrictMath.sqrt(dx * dx + dy * dy);

        float perp_x;
        float perp_y;
        if (line_len < 0.001f) {
            perp_x = 1.0f;
            perp_y = 0.0f;
        } else {
            float dir_x = dx / line_len;
            float dir_y = dy / line_len;
            perp_x = -dir_y;
            perp_y = dir_x;
        }

        int current_x = p0.gridX;
        int current_y = p0.gridY;
        int step_x = p0.gridX < p1.gridX ? 1 : -1;
        int step_y = p0.gridY < p1.gridY ? 1 : -1;
        int abs_dx = StrictMath.abs(dx);
        int abs_dy = StrictMath.abs(dy);
        int err = abs_dx - abs_dy;

        int run_length = 0;
        int run_start_x = p0.gridX;
        int run_start_y = p0.gridY;

        ShipTrajectoryPoint result = null;

        while (true) {
            boolean is_water_strip = true;
            float half_span = (thickness - 1) * 0.5f;
            for (int i = 0; i < thickness; i++) {
                float offset = i - half_span;
                int check_x = (int) StrictMath.round(current_x + perp_x * offset);
                int check_y = (int) StrictMath.round(current_y + perp_y * offset);
                if (check_x < 0
                        || check_x >= grid_size
                        || check_y < 0
                        || check_y >= grid_size
                        || !grid.isWater(check_x, check_y)
                        || grid.isDockable(check_x, check_y)) {
                    is_water_strip = false;
                    break;
                }
            }

            if (is_water_strip) {
                if (run_length == 0) {
                    run_start_x = current_x;
                    run_start_y = current_y;
                }
                run_length++;
                if (run_length >= minSize) {
                    result = new ShipTrajectoryPoint(
                            (int) StrictMath.round((run_start_x + current_x) * 0.5f),
                            (int) StrictMath.round((run_start_y + current_y) * 0.5f));
                    if (run_length >= maxSize) {
                        return result;
                    }
                }
            } else {
                run_length = 0;
            }

            if (current_x == p1.gridX && current_y == p1.gridY) {
                break;
            }

            int e2 = err * 2;
            if (e2 > -abs_dy) {
                err -= abs_dy;
                current_x += step_x;
            }
            if (e2 < abs_dx) {
                err += abs_dx;
                current_y += step_y;
            }
        }

        return result;
    }

    private static boolean collisionOnCell(UnitGrid grid, Occupant self, int x, int y) {
        int grid_size = grid.getGridSize();
        if (x < 0 || x >= grid_size || y < 0 || y >= grid_size) {
            return true;
        }
        if (!grid.isWater(x, y)) {
            return true;
        }
        Occupant occ = grid.getOccupant(x, y, UnitGrid.SEA);
        if (self != null && occ != null && occ != self) {
            return true;
        }
        return false;
    }

    public final boolean checkCollisionOnLine(
            ShipTrajectoryPoint p0, ShipTrajectoryPoint p1, int thickness) {
        return checkCollisionOnLine(grid, ship, p0, p1, thickness);
    }

    public static boolean checkCollisionOnLine(
            UnitGrid grid, Occupant self, ShipTrajectoryPoint p0, ShipTrajectoryPoint p1, int thickness) {
        final int half_thickness = thickness / 2;

        float dir_x = p1.gridX - p0.gridX;
        float dir_y = p1.gridY - p0.gridY;
        float length = (float) StrictMath.sqrt(dir_x * dir_x + dir_y * dir_y);

        if (length < 0.001f) {
            for (int ox = -half_thickness; ox <= half_thickness; ox++) {
                for (int oy = -half_thickness; oy <= half_thickness; oy++) {
                    if (ox * ox + oy * oy > half_thickness * half_thickness) {
                        continue;
                    }
                    if (collisionOnCell(grid, self, p0.gridX + ox, p0.gridY + oy)) {
                        return true;
                    }
                }
            }
            return false;
        }

        dir_x /= length;
        dir_y /= length;
        float perp_x = -dir_y;
        float perp_y = dir_x;

        for (float distance = 0.0f; distance <= length; distance += 0.5f) {
            float center_x = p0.gridX + dir_x * distance;
            float center_y = p0.gridY + dir_y * distance;
            for (int offset = -half_thickness; offset <= half_thickness; offset++) {
                int check_x = (int) StrictMath.round(center_x + perp_x * offset);
                int check_y = (int) StrictMath.round(center_y + perp_y * offset);
                if (collisionOnCell(grid, self, check_x, check_y)) {
                    return true;
                }
            }
        }

        for (int offset = -half_thickness; offset <= half_thickness; offset++) {
            int check_x = (int) StrictMath.round(p1.gridX + perp_x * offset);
            int check_y = (int) StrictMath.round(p1.gridY + perp_y * offset);
            if (collisionOnCell(grid, self, check_x, check_y)) {
                return true;
            }
        }

        return false;
    }
}
