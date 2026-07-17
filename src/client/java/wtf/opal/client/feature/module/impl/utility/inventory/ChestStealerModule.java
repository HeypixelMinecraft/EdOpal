package wtf.opal.client.feature.module.impl.utility.inventory;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.world.GameMode;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.number.BoundedNumberProperty;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.player.InventoryUtility;

import java.util.Random;

import static wtf.opal.client.Constants.mc;

public final class ChestStealerModule extends Module {

    private static final TagKey<Item> DIAMOND_ARMOR = TagKey.of(Registries.ITEM.getKey(), Identifier.of("minecraft", "diamond_armor"));
    private static final TagKey<Item> IRON_ARMOR = TagKey.of(Registries.ITEM.getKey(), Identifier.of("minecraft", "iron_armor"));
    private static final TagKey<Item> DIAMOND_SWORDS = TagKey.of(Registries.ITEM.getKey(), Identifier.of("minecraft", "diamond_swords"));
    private static final TagKey<Item> IRON_SWORDS = TagKey.of(Registries.ITEM.getKey(), Identifier.of("minecraft", "iron_swords"));
    private static final TagKey<Item> SWORDS = TagKey.of(Registries.ITEM.getKey(), Identifier.of("minecraft", "swords"));

    private int clickDelay = 0;
    private int oDelay = 0;
    private boolean inChest = false;
    private boolean warnedFull = false;
    private final Random random = new Random();

    public final BoundedNumberProperty minDelay = new BoundedNumberProperty("Min Delay", 1, 20, 0, 20, 1);
    public final BoundedNumberProperty maxDelay = new BoundedNumberProperty("Max Delay", 2, 20, 0, 20, 1);
    public final BoundedNumberProperty openDelay = new BoundedNumberProperty("Open Delay", 1, 20, 0, 20, 1);
    public final BooleanProperty autoClose = new BooleanProperty("Auto Close", false);
    public final BooleanProperty nameCheck = new BooleanProperty("Name Check", true);
    public final BooleanProperty skipTrash = new BooleanProperty("Skip Trash", true);
    public final BooleanProperty moreArmor = new BooleanProperty("More Armor", false);
    public final BooleanProperty moreSword = new BooleanProperty("More Sword", false);
    public final BooleanProperty highlight = new BooleanProperty("Highlight", true);

    public ChestStealerModule() {
        super("Chest Stealer", "Steals items from chests.", ModuleCategory.UTILITY);
        addProperties(minDelay, maxDelay, openDelay, autoClose, nameCheck, skipTrash, moreArmor, moreSword, highlight);
    }

    private boolean isValidGameMode() {
        GameMode gameMode = mc.interactionManager.getCurrentGameMode();
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }

    private boolean isMoreArmor(ItemStack itemStack) {
        if (itemStack == null) return false;
        if (!this.moreArmor.getValue()) return false;
        if (!InventoryUtility.isArmor(itemStack)) return false;

        int diamondCount = 0;
        int ironCount = 0;

        for (int i = 36; i < 40; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && InventoryUtility.isArmor(stack)) {
                if (stack.isIn(DIAMOND_ARMOR)) diamondCount++;
                else if (stack.isIn(IRON_ARMOR)) ironCount++;
            }
        }

        return itemStack.isIn(DIAMOND_ARMOR) || (itemStack.isIn(IRON_ARMOR) && itemStack.hasEnchantments());
    }

    private boolean isMoreSword(ItemStack itemStack) {
        if (itemStack == null) return false;
        if (!this.moreSword.getValue()) return false;
        if (!itemStack.isIn(SWORDS)) return false;

        return itemStack.isIn(DIAMOND_SWORDS)
                || InventoryUtility.calculateEnchantmentLevel(itemStack, Enchantments.FIRE_ASPECT) > 0
                || (itemStack.isIn(IRON_SWORDS) && itemStack.hasEnchantments());
    }

    private void shiftClick(GenericContainerScreenHandler screenHandler, int slotId) {
        mc.interactionManager.clickSlot(screenHandler.syncId, slotId, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
        resetClickDelay();
    }

    @Subscribe
    public void onPreGameTickEvent(final PreGameTickEvent event) {
        if (this.clickDelay > 0) {
            this.clickDelay--;
        }
        if (this.oDelay > 0) {
            this.oDelay--;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen container)) {
            this.inChest = false;
            return;
        }

        final GenericContainerScreenHandler screenHandler = container.getScreenHandler();
        final Inventory chestInventory = screenHandler.getInventory();

        if (!container.getTitle().getString().toLowerCase().contains("chest")) {
            this.inChest = false;
            return;
        }

        if (!this.inChest) {
            this.inChest = true;
            this.warnedFull = false;
            this.oDelay = this.openDelay.getValue().first.intValue() + 1;
        }

        if (this.oDelay <= 0 && this.clickDelay <= 0) {
            if (this.isEnabled() && this.isValidGameMode()) {
                if (this.nameCheck.getValue()) {
                    String inventoryName = container.getTitle().getString();
                    if (!inventoryName.equals("Chest") && !inventoryName.equals("Large Chest")) {
                        return;
                    }
                }

                if (mc.player.getInventory().getEmptySlot() == -1) {
                    if (!this.warnedFull) {
                        ChatUtility.error("Your inventory is full!");
                        this.warnedFull = true;
                    }
                    if (this.autoClose.getValue()) {
                        mc.player.closeHandledScreen();
                    }
                } else {
                    if (this.skipTrash.getValue()) {
                        int bestSword = -1;
                        double bestDamage = 0.0;
                        int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
                        double[] bestArmorProtection = new double[]{0.0, 0.0, 0.0, 0.0};
                        int bestPickaxeSlot = -1;
                        float bestPickaxeEfficiency = 1.0F;
                        int bestShovelSlot = -1;
                        float bestShovelEfficiency = 1.0F;
                        int bestAxeSlot = -1;
                        float bestAxeEfficiency = 1.0F;
                        int bestBow = -1;
                        double bestBowDamage = 0.0;

                        for (int i = 0; i < chestInventory.size(); i++) {
                            final Slot slot = screenHandler.getSlot(i);
                            if (!slot.hasStack()) continue;

                            final ItemStack stack = slot.getStack();
                            final Item item = stack.getItem();

                            if (stack.isIn(SWORDS)) {
                                double damage = InventoryUtility.getAttackBonus(stack);
                                if (bestSword == -1 || damage > bestDamage) {
                                    bestSword = i;
                                    bestDamage = damage;
                                }
                            } else if (InventoryUtility.isArmor(stack)) {
                                final int armorType = getArmorType(stack);
                                double protectionLevel = InventoryUtility.getArmorProtection(stack);
                                if (bestArmorSlots[armorType] == -1 || protectionLevel > bestArmorProtection[armorType]) {
                                    bestArmorSlots[armorType] = i;
                                    bestArmorProtection[armorType] = protectionLevel;
                                }
                            } else if (stack.isIn(ItemTags.PICKAXES)) {
                                float efficiency = InventoryUtility.getToolEfficiency(stack);
                                if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                                    bestPickaxeSlot = i;
                                    bestPickaxeEfficiency = efficiency;
                                }
                            } else if (item instanceof ShovelItem) {
                                float efficiency = InventoryUtility.getToolEfficiency(stack);
                                if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                                    bestShovelSlot = i;
                                    bestShovelEfficiency = efficiency;
                                }
                            } else if (item instanceof AxeItem) {
                                float efficiency = InventoryUtility.getToolEfficiency(stack);
                                if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                                    bestAxeSlot = i;
                                    bestAxeEfficiency = efficiency;
                                }
                            } else if (item instanceof BowItem) {
                                double damage = InventoryUtility.getBowAttackBonus(stack);
                                if (bestBow == -1 || damage > bestBowDamage) {
                                    bestBow = i;
                                    bestBowDamage = damage;
                                }
                            }
                        }

                        int swordInInventorySlot = InventoryUtility.findSwordInInventorySlot(0, true);
                        double damage = swordInInventorySlot != -1 ? InventoryUtility.getAttackBonus(mc.player.getInventory().getStack(swordInInventorySlot)) : 0.0;
                        if (bestDamage > damage) {
                            this.shiftClick(screenHandler, bestSword);
                            return;
                        }

                        for (int i = 0; i < 4; i++) {
                            int slot = InventoryUtility.findArmorInventorySlot(i, true);
                            double protectionLevel = slot != -1
                                    ? InventoryUtility.getArmorProtection(mc.player.getInventory().getStack(slot))
                                    : 0.0;
                            if (bestArmorProtection[i] > protectionLevel) {
                                this.shiftClick(screenHandler, bestArmorSlots[i]);
                                return;
                            }
                        }

                        int pickaxeSlot = InventoryUtility.findInventorySlot("pickaxe", 0, true);
                        float pickaxeEfficiency = pickaxeSlot != -1 ? InventoryUtility.getToolEfficiency(mc.player.getInventory().getStack(pickaxeSlot)) : 1.0F;
                        if (bestPickaxeEfficiency > pickaxeEfficiency) {
                            this.shiftClick(screenHandler, bestPickaxeSlot);
                            return;
                        }

                        int shovelSlot = InventoryUtility.findInventorySlot("shovel", 0, true);
                        float shovelEfficiency = shovelSlot != -1 ? InventoryUtility.getToolEfficiency(mc.player.getInventory().getStack(shovelSlot)) : 1.0F;
                        if (bestShovelEfficiency > shovelEfficiency) {
                            this.shiftClick(screenHandler, bestShovelSlot);
                            return;
                        }

                        int axeSlot = InventoryUtility.findInventorySlot("axe", 0, true);
                        float efficiency = axeSlot != -1 ? InventoryUtility.getToolEfficiency(mc.player.getInventory().getStack(axeSlot)) : 1.0F;
                        if (bestAxeEfficiency > efficiency) {
                            this.shiftClick(screenHandler, bestAxeSlot);
                            return;
                        }

                        int bowSlot = InventoryUtility.findBowInventorySlot(0, true);
                        double bowDamage = bowSlot != -1 ? InventoryUtility.getBowAttackBonus(mc.player.getInventory().getStack(bowSlot)) : 0.0;
                        if (bestBowDamage > bowDamage) {
                            this.shiftClick(screenHandler, bestBow);
                            return;
                        }
                    }

                    for (int i = 0; i < chestInventory.size(); i++) {
                        final Slot slot = screenHandler.getSlot(i);
                        if (!slot.hasStack()) continue;

                        final ItemStack stack = slot.getStack();
                        if (!this.skipTrash.getValue() || !InventoryUtility.isNotSpecialItem(stack) || isMoreArmor(stack) || isMoreSword(stack)) {
                            this.shiftClick(screenHandler, i);
                            return;
                        }
                    }

                    if (this.autoClose.getValue()) {
                        mc.player.closeHandledScreen();
                    }
                }
            }
        }
    }

    private void resetClickDelay() {
        int min = this.minDelay.getValue().first.intValue();
        int max = this.maxDelay.getValue().second.intValue();
        this.clickDelay = random.nextInt(max - min + 1) + min;
    }

    private int getArmorType(ItemStack stack) {
        var equip = stack.getComponents().get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        if (equip == null) return 0;

        return switch (equip.slot()) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> 0;
        };
    }

    public BooleanProperty getHighlight() {
        return highlight;
    }

    public BooleanProperty getSkipTrash() {
        return skipTrash;
    }
}
