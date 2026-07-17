package wtf.opal.client.feature.module.impl.utility.inventory.manager;

import wtf.opal.client.feature.module.property.impl.GroupProperty;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;

public final class InventoryManagerSettings {

    private final BoundedNumberProperty delay;
    private final BoundedNumberProperty maxBlocks;

    private final MultipleBooleanProperty slots;
    private final NumberProperty swordSlot, pickaxeSlot, axeSlot, blockSlot, gappleSlot, goldenGappleSlot, bowSlot, potionSlot, enderPearlSlot;

    public InventoryManagerSettings(final InventoryManagerModule module) {
        this.delay = new BoundedNumberProperty("Delay", 50, 100, 0, 400, 5);
        this.maxBlocks = new BoundedNumberProperty("Max Blocks", 64, 64, 1, 512, 1);

        this.slots = new MultipleBooleanProperty("Slots",
                new BooleanProperty("Sword", true),
                new BooleanProperty("Pickaxe", true),
                new BooleanProperty("Axe", true),
                new BooleanProperty("Blocks", true),
                new BooleanProperty("Gapple", true),
                new BooleanProperty("Golden Gapple", true),
                new BooleanProperty("Bow", true),
                new BooleanProperty("Potions", true),
                new BooleanProperty("Ender Pearls", true)
        );

        this.swordSlot = new NumberProperty("Sword Slot", 1, 1, 9, 1).hideIf(() -> !slots.getProperty("Sword").getValue());
        this.pickaxeSlot = new NumberProperty("Pickaxe Slot", 2, 1, 9, 1).hideIf(() -> !slots.getProperty("Pickaxe").getValue());
        this.axeSlot = new NumberProperty("Axe Slot", 3, 1, 9, 1).hideIf(() -> !slots.getProperty("Axe").getValue());
        this.blockSlot = new NumberProperty("Block Slot", 4, 1, 9, 1).hideIf(() -> !slots.getProperty("Blocks").getValue());
        this.gappleSlot = new NumberProperty("Gapple Slot", 5, 1, 9, 1).hideIf(() -> !slots.getProperty("Gapple").getValue());
        this.goldenGappleSlot = new NumberProperty("Golden Gapple Slot", 6, 1, 9, 1).hideIf(() -> !slots.getProperty("Golden Gapple").getValue());
        this.bowSlot = new NumberProperty("Bow Slot", 7, 1, 9, 1).hideIf(() -> !slots.getProperty("Bow").getValue());
        this.potionSlot = new NumberProperty("Potion Slot", 8, 1, 9, 1).hideIf(() -> !slots.getProperty("Potions").getValue());
        this.enderPearlSlot = new NumberProperty("Ender Pearl Slot", 9, 1, 9, 1).hideIf(() -> !slots.getProperty("Ender Pearls").getValue());

        module.addProperties(delay, maxBlocks, new GroupProperty("Slots", slots, swordSlot, pickaxeSlot, axeSlot, blockSlot, gappleSlot, goldenGappleSlot, bowSlot, potionSlot, enderPearlSlot));
    }

    public Double getDelay() {
        return delay.getRandomValue();
    }

    public int getMaxBlocks() {
        return maxBlocks.getValue().first.intValue();
    }

    public MultipleBooleanProperty getSlots() {
        return slots;
    }

    public int getSwordSlot() {
        return swordSlot.getValue().intValue();
    }

    public int getPickaxeSlot() {
        return pickaxeSlot.getValue().intValue();
    }

    public int getAxeSlot() {
        return axeSlot.getValue().intValue();
    }

    public int getBlockSlot() {
        return blockSlot.getValue().intValue();
    }

    public int getGappleSlot() {
        return gappleSlot.getValue().intValue();
    }

    public int getGoldenGappleSlot() {
        return goldenGappleSlot.getValue().intValue();
    }

    public int getBowSlot() {
        return bowSlot.getValue().intValue();
    }

    public int getPotionSlot() {
        return potionSlot.getValue().intValue();
    }

    public int getEnderPearlSlot() {
        return enderPearlSlot.getValue().intValue();
    }
}