package com.oddlabs.tt.player;

import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.DeployType;
import com.oddlabs.tt.model.Race;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.IronSpearWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RockSpearWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberSpearWeapon;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class AdvancedAI extends AI {
    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_NORMAL = 1;
    public static final int DIFFICULTY_HARD = 2;
    public static final int DIFFICULTY_EXTREME = 3;
    public static final int DIFFICULTY_GRUELLING = 4;
    public static final int DIFFICULTY_HERCULEAN = 5;

    private static final int SCORE_PEON = 1;
    private static final int SCORE_WARRIOR_ROCK = 4;
    private static final int SCORE_WARRIOR_IRON = 5;
    private static final int SCORE_WARRIOR_RUBBER = 10;
    private static final int SCORE_CHIEFTAIN = 25;

    // INDEXED BY DIFFICULTY: EASY, NORMAL, HARD, EXTREME, GRUELLING, HERCULEAN
    private static final float[] DEFENSE_FACTOR = new float[]{1f, 1.5f, 2f, 2.2f, 2.4f, 2.6f};

    private static final int[] MIN_UNITS_BUILDING_WEAPONS = new int[]{0, 3, 8, 8, 8, 8};
    private static final int[] MIN_WEAPONS_IN_STOCK = new int[]{10, 5, 0, 0, 0, 0}; // RUSHING PENALTY
    private static final int[] MIN_UNITS_REPRODUCING = new int[]{0, 5, 20, 20, 20, 20};
    private static final int[] MAX_UNITS_GATHERING_TREE = new int[]{2, 5, 15, 28, 40, 50};
    private static final int[] MAX_UNITS_GATHERING_ROCK = new int[]{1, 3, 9, 16, 24, 30};
    private static final int[] MAX_UNITS_GATHERING_IRON = new int[]{1, 3, 10, 18, 26, 32};
    private static final int[] MAX_UNITS_GATHERING_RUBBER = new int[]{0, 0, 3, 6, 9, 12};

    // QUARTERS+ARMORY PAIRS: HARD=1, EXTREME=2, GRUELLING=3, HERCULEAN=4
    private static final int[] MAX_BASES = new int[]{1, 1, 1, 2, 3, 4};
    // TOWERS SCALE WITH BASES (2 PER BASE FROM HARD UP)
    private static final int[] MAX_TOWERS = new int[]{0, 0, 2, 4, 6, 8};

    // PER-ARMORY ATTACK WAVE CAP; TOTAL WAVE GROWS WITH ACTIVE ARMORIES
    private final int[] NUM_WARRIORS = new int[]{3, 7, 10, 12, 14, 16};
    private final int[] NUM_WARRIORS_INCREASE = new int[]{1, 3, 5, 5, 5, 6};
    private final int[] NUM_WARRIORS_MAX = new int[]{10, 19, 40, 50, 55, 60};
    private final int[] NUM_WARRIORS_FOR_CHIEFTAIN = new int[]{1000, 1000, 20, 20, 20, 20};

    private final int difficulty;

    private @Nullable LandscapeTarget defense_target = null;
    /**
     * Enemy position found by the most recent {@link #scanForEnemies} call, kept separate from
     * {@link #defense_target} so scans of undefended buildings don't clobber the best target found so far.
     */
    private @Nullable LandscapeTarget last_scan_target = null;

    public AdvancedAI(@NonNull Player owner, UnitInfo unit_info, int difficulty) {
        super(owner, unit_info);
        this.difficulty = difficulty;
    }

    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void animate(float t) {
        if (!shouldDoAction(t))
            return;
        reclassify();
        nodeDefendBase();
        reclassify();
        nodeExpandBases();
        reclassify();
        int desired_towers = desiredTowers();
        if (desired_towers > 0)
            nodeGuardTowers(desired_towers);

        reclassify();
        nodeAttackWithWarriorsAndChieftain(NUM_WARRIORS[difficulty],
                NUM_WARRIORS[difficulty] >= NUM_WARRIORS_FOR_CHIEFTAIN[difficulty]);
        nodeAssignIdlePeons();
        if (getOwner().hasActiveChieftain()) {
            getOwner().getRace().getChieftainAI().decide(getOwner().getChieftain());
        }
    }

    private int buildingCount(Selectable<?> @Nullable [] buildings) {
        return buildings == null ? 0 : buildings.length;
    }

    private int attackWaveMax() {
        return NUM_WARRIORS_MAX[difficulty] * Math.max(1, buildingCount(getArmory()));
    }

    private int desiredTowers() {
        int max = MAX_TOWERS[difficulty];
        if (max <= 0)
            return 0;
        int units = getOwner().getUnitCountContainer().getNumSupplies();
        int unlocked = 0;
        for (int i = 0; i < max; i++) {
            if (units > 90 + i * 30)
                unlocked++;
        }
        int by_bases = Math.max(1, buildingCount(getArmory())) * 2;
        return Math.min(max, Math.min(unlocked, by_bases));
    }

    private void nodeExpandBases() {
        int max = MAX_BASES[difficulty];
        int quarters = buildingCount(getQuarters());
        int armories = buildingCount(getArmory());
        if (quarters < max)
            nodeBuildQuarters();
        if (armories < max && armories < buildingCount(getQuarters()))
            nodeBuildArmory();
    }

    private void nodeDefendBase() {
        int enemy_score = 0;
        defense_target = null;
        if (getQuarters() != null) {
            for (Selectable<?> quarter : getQuarters()) {
                int score = scanForEnemies(quarter);
                if (score > enemy_score) {
                    enemy_score = score;
                    defense_target = last_scan_target;
                }
            }
        }
        if (getArmory() != null && enemy_score == 0) {
            for (Selectable<?> armory : getArmory()) {
                int score = scanForEnemies(armory);
                if (score > enemy_score) {
                    enemy_score = score;
                    defense_target = last_scan_target;
                }
            }
        }
        enemy_score = (int) (DEFENSE_FACTOR[difficulty] * enemy_score);
        if (getDefendingUnits() != null) {
            for (Selectable<?> defendingUnit : getDefendingUnits()) {
                enemy_score -= getUnitScore((Unit) defendingUnit);
            }
        }
        if (enemy_score > 0) {
            nodeDeployArmy();
            nodeDefend(enemy_score);
        }
    }

    private void nodeDeployArmy() {
        if (getArmory() == null)
            return;
        for (Selectable<?> selectable : getArmory()) {
            Building armory = (Building) selectable;
            if (armory.isDead())
                continue;
            int num_units = armory.getUnitContainer().getNumSupplies() - MIN_UNITS_BUILDING_WEAPONS[difficulty];
            int num_weapons = numWeapons(armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            if (num_units <= 0 || num_weapons <= 0)
                continue;

            int num_warriors = Math.min(num_units, num_weapons);
            deployWarriors(armory, num_warriors);
            num_units = armory.getUnitContainer().getNumSupplies();
            if (num_units > 0) {
                getOwner().deployUnits(armory, DeployType.PEON, num_units);
            }
        }
    }

    private void deployWarriors(@NonNull Building armory, int num_warriors) {
        int num_rubber_units = Math.min(num_warriors, armory.getSupplyContainer(
                RubberAxeWeapon.class).getNumSupplies());
        int num_iron_units = Math.min(num_warriors - num_rubber_units, armory.getSupplyContainer(
                IronAxeWeapon.class).getNumSupplies());
        int num_rock_units = Math.min(num_warriors - num_rubber_units - num_iron_units, armory.getSupplyContainer(
                RockAxeWeapon.class).getNumSupplies());
        if (num_rubber_units > 0)
            getOwner().deployUnits(armory, DeployType.RUBBER_WARRIOR, num_rubber_units);
        if (num_iron_units > 0)
            getOwner().deployUnits(armory, DeployType.IRON_WARRIOR, num_iron_units);
        if (num_rock_units > 0)
            getOwner().deployUnits(armory, DeployType.ROCK_WARRIOR, num_rock_units);
    }

    private void nodeDefend(int score) {
        if (defense_target == null) {
            // Defensive guard: enemy_score should only be positive when a target was recorded,
            // but avoid crashing the AI/game if that invariant is ever violated.
            return;
        }
        List<Unit> unit_list = new ArrayList<>();

        int result = 0;
        if (getIdleWarriors() != null && result < score) {
            result = addFromList(getIdleWarriors(), unit_list, result, score);
        }
        if (getIdlePeons() != null && result < score) {
            result = addFromList(getIdlePeons(), unit_list, result, score);
        }
        if (getGatherTreePeons() != null && result < score) {
            result = addFromList(getGatherTreePeons(), unit_list, result, score);
        }
        if (getGatherRockPeons() != null && result < score) {
            result = addFromList(getGatherRockPeons(), unit_list, result, score);
        }
        if (getGatherIronPeons() != null && result < score) {
            result = addFromList(getGatherIronPeons(), unit_list, result, score);
        }
        if (getGatherRubberPeons() != null && result < score) {
            result = addFromList(getGatherRubberPeons(), unit_list, result, score);
        }

        if (result > 0) {
            Unit[] units = new Unit[unit_list.size()];
            unit_list.toArray(units);
            getOwner().setLandscapeTarget(units, defense_target.getGridX(), defense_target.getGridY(), Action.DEFEND,
                    true);
        }
    }

    private int addFromList(Selectable<?> @NonNull [] list, @NonNull List<Unit> new_list, int progress, int score) {
        int result = progress;
        for (Selectable<?> list1 : list) {
            Unit unit = (Unit) list1;
            new_list.add(unit);
            result += getUnitScore(unit);
            if (result > score)
                break;
        }
        return result;
    }

    private int scanForEnemies(@NonNull Selectable<?> src) {
        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(src.getPositionX(), src.getPositionY(), 30f, src,
                Unit.class);
        getUnitGrid().scan(filter, src.getGridX(), src.getGridY());
        int score = 0;
        last_scan_target = null;
        for (Unit unit : filter.getResult()) {
            if (!unit.isDead() && getOwner().isEnemy(unit.getOwner())) {
                score += getUnitScore(unit);
                if (last_scan_target == null)
                    last_scan_target = new LandscapeTarget(unit.getGridX(), unit.getGridY());
            }
        }
        return score;
    }

    private int getUnitScore(@NonNull Unit unit) {
        if (unit.getAbilities().hasAbilities(Abilities.HARVEST)) {
            return SCORE_PEON;
        } else if (unit.getAbilities().hasAbilities(Abilities.MAGIC)) {
            return SCORE_CHIEFTAIN;
        } else if (unit.getWeaponFactory().getType() == RockAxeWeapon.class
                || unit.getWeaponFactory().getType() == RockSpearWeapon.class) {
                    return SCORE_WARRIOR_ROCK;
                } else if (unit.getWeaponFactory().getType() == IronAxeWeapon.class
                        || unit.getWeaponFactory().getType() == IronSpearWeapon.class) {
                            return SCORE_WARRIOR_IRON;
                        } else if (unit.getWeaponFactory().getType() == RubberAxeWeapon.class
                                || unit.getWeaponFactory().getType() == RubberSpearWeapon.class) {
                                    return SCORE_WARRIOR_RUBBER;
                                }
        throw new RuntimeException();
    }

    private void nodeGuardTowers(int num_towers) {
        if ((getTowers() == null && num_towers > 0) || (getTowers() != null && num_towers > getTowers().length)) {
            nodeBuildTower(num_towers);
        } else if (num_towers > 0) {
            for (int i = 0; i < getTowers().length; i++) {
                if (!((Building) getTowers()[i]).getUnitContainer().isSupplyFull() && getIdleWarriors() != null
                        && getIdleWarriors().length > i) {
                    getOwner().setTarget(Selectable.newArray(getIdleWarriors()[i]), getTowers()[i], Action.DEFAULT,
                            false);
                    nodeDeployUnitsInArmory(1);
                }
            }
        }
    }

    private void nodeBuildTower(int number) {
        if (towerUnderConstruction() || getQuarters() == null || getArmory() == null)
            return;
        int have = buildingCount(getTowers());
        if (have >= number)
            return;
        Selectable<?>[] builders = getPeons(10);
        if (builders.length == 0)
            return;

        // ROTATE TOWER ORIGINS ACROSS BASES SO THEY SPREAD WITH EXPANSION
        int base_index = have / 2 % getQuarters().length;
        Building origin = have % 2 == 0 ? (Building) getQuarters()[base_index] : (Building) getArmory()[Math.min(
                base_index, getArmory().length - 1)];
        int ox = origin.getGridX();
        int oy = origin.getGridY();
        int center = getOwner().getWorld().getHeightMap().getGridUnitsPerWorld() / 2;
        int dx = center - ox;
        int dy = center - oy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float inv_dist = dist > 0.1f ? 1f / dist : 0f;
        float along = 10f + (have % 2) * 6f;
        float side = ((have % 4) < 2 ? 1f : -1f) * (4f + have);
        float px = -dy * inv_dist;
        float py = dx * inv_dist;
        int tx = (int) (ox + dx * inv_dist * along + px * side);
        int ty = (int) (oy + dy * inv_dist * along + py * side);
        setTowerUnderConstruction(buildBuilding(Race.BUILDING_TOWER, builders, tx, ty));
    }

    private void nodeAssignIdlePeons() {
        if (getIdlePeons() != null) {
            if (quartersUnderConstruction() && getConstructionSites() != null) {
                getOwner().setTarget(getIdlePeons(), getConstructionSites()[0], Action.DEFAULT, false);
            } else if (armoryUnderConstruction() && getConstructionSites() != null) {
                getOwner().setTarget(getIdlePeons(), getConstructionSites()[0], Action.DEFAULT, false);
            } else if (towerUnderConstruction() && getConstructionSites() != null) {
                getOwner().setTarget(getIdlePeons(), getConstructionSites()[0], Action.DEFAULT, false);
            } else if (getQuarters() != null && !getQuarters()[0].isDead()) {
                getOwner().setTarget(getIdlePeons(), getQuarters()[0], Action.DEFAULT, false);
            }
        }
    }

    private void nodeAttackWithWarriorsAndChieftain(int num_warriors, boolean use_chieftain) {
        if (getIdleWarriors() != null && getIdleWarriors().length >= num_warriors
                && (!use_chieftain || getOwner().hasActiveChieftain())) {
            boolean idle_chieftain = getIdleChieftains() != null && getIdleChieftains().length >= 1;
            Selectable<?>[] warriors;
            if (idle_chieftain && use_chieftain) {
                warriors = Selectable.newArray(num_warriors + 1);
                warriors[num_warriors] = getIdleChieftains()[0];
            } else {
                warriors = Selectable.newArray(num_warriors);
            }

            System.arraycopy(getIdleWarriors(), 0, warriors, 0, num_warriors);
            Target target = findTarget(warriors[0].getGridX(), warriors[0].getGridY());
            if (target != null) {
                getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true);
                if (NUM_WARRIORS[difficulty] < attackWaveMax())
                    NUM_WARRIORS[difficulty] += NUM_WARRIORS_INCREASE[difficulty];
            }
        } else {
            if (getIdleWarriors() != null) {
                nodeDeployUnitsInArmory(num_warriors - getIdleWarriors().length);
            } else {
                nodeDeployUnitsInArmory(num_warriors);
            }
            if (use_chieftain)
                nodeTrainChieftain();
        }
    }

    private void nodeTrainChieftain() {
        if (!getOwner().hasActiveChieftain() && !getOwner().isTrainingChieftain()) {
            if (getQuarters() != null) {
                getOwner().trainChieftain((Building) getQuarters()[0], true);
            }
        }
    }

    private void nodeDeployUnitsInArmory(int num_warriors) {
        if (getArmory() == null || getArmory().length == 0) {
            nodeBuildArmory();
            return;
        }
        int remaining = num_warriors;
        for (Selectable<?> selectable : getArmory()) {
            if (remaining <= 0)
                break;
            Building armory = (Building) selectable;
            if (armory.isDead())
                continue;
            int num_units = armory.getUnitContainer().getNumSupplies() - MIN_UNITS_BUILDING_WEAPONS[difficulty];
            int num_weapons = numWeapons(armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            int can_deploy = Math.min(remaining, Math.min(Math.max(num_units, 0), Math.max(num_weapons, 0)));
            if (can_deploy > 0) {
                deployWarriors(armory, can_deploy);
                remaining -= can_deploy;
            } else {
                if (num_units < remaining) {
                    nodeTransferUnits(remaining - Math.max(num_units, 0), armory);
                }
                if (num_weapons < remaining) {
                    nodeGather(armory, Math.max(num_units, 0));
                }
            }
        }
        if (buildingCount(getArmory()) < MAX_BASES[difficulty])
            nodeExpandBases();
    }

    private void nodeGather(@NonNull Building armory, int num_units) {
        int tree = 0;
        int rock = 0;
        int iron = 0;
        int rubber = 0;

        if (getGatherTreePeons() != null)
            tree = getGatherTreePeons().length;
        if (getGatherRockPeons() != null)
            rock = getGatherRockPeons().length;
        if (getGatherIronPeons() != null)
            iron = getGatherIronPeons().length;
        if (getGatherRubberPeons() != null)
            rubber = getGatherRubberPeons().length;

        if (tree >= MAX_UNITS_GATHERING_TREE[difficulty])
            tree = Integer.MAX_VALUE;
        if (rock >= MAX_UNITS_GATHERING_ROCK[difficulty])
            rock = Integer.MAX_VALUE;
        if (iron >= MAX_UNITS_GATHERING_IRON[difficulty])
            iron = Integer.MAX_VALUE;
        if (rubber >= MAX_UNITS_GATHERING_RUBBER[difficulty])
            rubber = Integer.MAX_VALUE;

        boolean deployed;
        do {
            deployed = false;
            if (num_units > 0 && tree < MAX_UNITS_GATHERING_TREE[difficulty] && tree <= rock && tree <= iron
                    && tree <= rubber) {
                getOwner().deployUnits(armory, DeployType.PEON_HARVEST_TREE, 1);
                deployed = true;
                tree++;
            } else if (num_units > 0 && rock < MAX_UNITS_GATHERING_ROCK[difficulty] && rock <= tree && rock <= iron
                    && rock <= rubber) {
                        getOwner().deployUnits(armory, DeployType.PEON_HARVEST_ROCK, 1);
                        deployed = true;
                        rock++;
                    } else if (num_units > 0 && iron < MAX_UNITS_GATHERING_IRON[difficulty] && iron <= tree
                            && iron <= rock && iron <= rubber) {
                                getOwner().deployUnits(armory, DeployType.PEON_HARVEST_IRON, 1);
                                deployed = true;
                                iron++;
                            } else if (num_units > 0 && rubber < MAX_UNITS_GATHERING_RUBBER[difficulty]
                                    && rubber <= tree && rubber <= rock && rubber <= iron) {
                                        getOwner().deployUnits(armory, DeployType.PEON_HARVEST_RUBBER, 1);
                                        deployed = true;
                                        rubber++;
                                    }
            num_units--;
        } while (deployed);
    }

    private void nodeTransferUnits(int num_units, @NonNull Building armory) {
        Building quarters = findNearestQuarters(armory);
        if (quarters != null) {
            if (!quarters.isDead()) {
                quarters.setRallyPoint(armory);
                if (quarters.getUnitContainer().getNumSupplies() > MIN_UNITS_REPRODUCING[difficulty]) {
                    int units = Math.min(num_units,
                            quarters.getUnitContainer().getNumSupplies() - MIN_UNITS_REPRODUCING[difficulty]);
                    getOwner().deployUnits(quarters, DeployType.PEON, units);
                }
            }
        } else {
            nodeBuildQuarters();
        }
    }

    private @Nullable Building findNearestQuarters(@NonNull Building armory) {
        if (getQuarters() == null || getQuarters().length == 0)
            return null;
        Building best = null;
        int best_dist = Integer.MAX_VALUE;
        for (Selectable<?> selectable : getQuarters()) {
            Building quarters = (Building) selectable;
            if (quarters.isDead())
                continue;
            int dx = quarters.getGridX() - armory.getGridX();
            int dy = quarters.getGridY() - armory.getGridY();
            int dist = dx * dx + dy * dy;
            if (dist < best_dist) {
                best_dist = dist;
                best = quarters;
            }
        }
        return best;
    }

    private void nodeBuildArmory() {
        if (buildingCount(getQuarters()) == 0) {
            nodeBuildQuarters();
            return;
        }
        int max = MAX_BASES[difficulty];
        int armories = buildingCount(getArmory());
        int quarters_count = buildingCount(getQuarters());
        if (armoryUnderConstruction() || armories >= max || armories >= quarters_count)
            return;

        // NEXT ARMORY PAIRS WITH THE UNMATCHED QUARTERS
        Building quarters = (Building) getQuarters()[armories];
        if (!quarters.getAbilities().hasAbilities(Abilities.REPRODUCE))
            return;

        Selectable<?>[] builders = getPeons(20);
        if (builders.length < 20) {
            if (!quarters.isDead() && quarters.getUnitContainer().getNumSupplies() >= 20)
                getOwner().deployUnits(quarters, DeployType.PEON, 20);
        }
        if (builders.length == 0)
            return;

        setArmoryUnderConstruction(buildBuilding(Race.BUILDING_ARMORY, builders, quarters.getGridX(),
                quarters.getGridY()));
        reclassify();
    }

    private void nodeBuildQuarters() {
        int max = MAX_BASES[difficulty];
        int have = buildingCount(getQuarters());
        if (quartersUnderConstruction() || have >= max)
            return;

        Selectable<?>[] builders = getPeons(MIN_UNITS_REPRODUCING[difficulty]);
        if (builders.length == 0)
            return;

        int grid_x;
        int grid_y;
        if (have == 0) {
            grid_x = builders[0].getGridX();
            grid_y = builders[0].getGridY();
        } else {
            int[] site = expansionSite(have);
            grid_x = site[0];
            grid_y = site[1];
        }

        setQuartersUnderConstruction(buildBuilding(Race.BUILDING_QUARTERS, builders, grid_x, grid_y));
        reclassify();
    }

    // PLACE EXTRA BASES OUTWARD FROM THE PRIMARY TOWARD MAP CENTER, ALTERNATING SIDES
    private int @NonNull [] expansionSite(int base_index) {
        Building primary = (Building) getQuarters()[0];
        int ox = primary.getGridX();
        int oy = primary.getGridY();
        int center = getOwner().getWorld().getHeightMap().getGridUnitsPerWorld() / 2;
        int dx = center - ox;
        int dy = center - oy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float inv = dist > 0.1f ? 1f / dist : 0f;
        float px = -dy * inv;
        float py = dx * inv;
        float along = 25f + base_index * 18f;
        float side = ((base_index % 2 == 0) ? 1f : -1f) * (15f + base_index * 8f);
        return new int[]{(int) (ox + dx * inv * along + px * side), (int) (oy + dy * inv * along + py * side)
        };
    }

    private @NonNull Selectable<?> @NonNull [] getPeons(int min_num_peons) {
        var idle = getIdlePeons();
        int idleCount = null != idle ? idle.length : 0;
        var active = Stream.of((Supplier<Selectable<?>[]>) this::getGatherIronPeons, this::getGatherRockPeons,
                this::getGatherTreePeons, this::getGatherRubberPeons).map(Supplier::get).filter(
                        Objects::nonNull).flatMap(Arrays::stream).limit(Math.max(min_num_peons - idleCount, 0));
        return (null != idle ? Stream.concat(Arrays.stream(idle), active) : active).toArray(Selectable[]::new);
    }

    private int numWeapons(@NonNull Building armory) {
        return armory.getSupplyContainer(RockAxeWeapon.class).getNumSupplies() + armory.getSupplyContainer(
                IronAxeWeapon.class).getNumSupplies() + armory.getSupplyContainer(
                        RubberAxeWeapon.class).getNumSupplies();
    }

    private @Nullable Target findTarget(int start_x, int start_y) {
        Target best_building = getOwner().findNearestEnemyBuilding(start_x, start_y);
        Target best_target = getOwner().findNearestEnemy(start_x, start_y);
        if (best_building == null) {
            return best_target;
        }
        if (best_target == null) {
            return null;
        }

        int squared_dist_building = (best_building.getGridX() - start_x) * (best_building.getGridX() - start_x) + (best_building.getGridY() - start_y) * (best_building.getGridY() - start_y);
        int squared_dist_target = (best_target.getGridX() - start_x) * (best_target.getGridX() - start_x) + (best_target.getGridY() - start_y) * (best_target.getGridY() - start_y);

        return squared_dist_target < squared_dist_building / 2 ? best_target : best_building;
    }

    private boolean buildBuilding(int building_type, Selectable<?> @NonNull [] selection, int grid_x, int grid_y) {
        BuildingSiteScanFilter filter = new BuildingSiteScanFilter(getUnitGrid(),
                getOwner().getRace().getBuildingTemplate(building_type), 40, true);
        getUnitGrid().scan(filter, grid_x, grid_y);
        List<? extends Target> target_list = filter.getResult();
        if (!target_list.isEmpty()) {
            Target target = target_list.getFirst();
            getOwner().placeBuilding(selection, building_type, target.getGridX(), target.getGridY());
            return true;
        } else {
            return false;
        }
    }
}
