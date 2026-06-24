package com.oddlabs.tt.model;

public final class ShipUnitContainer extends UnitContainer {
    private final Ship building;

    public ShipUnitContainer(Ship building) {
        super(building.getOwner().getWorld().getMaxUnitCount());
        this.building = building;
    }

    public final void enter(Unit unit) {
        ShipAllocation allocation = building.getShipHR().tryAllocate(unit);
        unit.mountDeck(building, allocation);
    }

    public final boolean canEnter(Unit unit) {
        return building.getShipHR().canAllocate(unit);
    }

    private final int getTotalSupplies() {
        return getNumSupplies() + getNumPreparing();
    }

    public int getNumSupplies() {
        return building.getShipHR().countUnits();
    }

    public int capAmount(int amount) {
        int supply_count = getNumSupplies();
        return Math.max(supply_count + amount, 0) - supply_count;
    }

    public final Unit exit() {
        return null;
    }

    public int increaseSupply(int amount) {
        return super.increaseSupply(amount);
    }

    public final void animate(float t) {
    }
}
