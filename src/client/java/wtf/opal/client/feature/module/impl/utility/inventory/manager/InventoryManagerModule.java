package wtf.opal.client.feature.module.impl.utility.inventory.manager;

import net.hypixel.data.type.GameType;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.*;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.helper.impl.LocalDataWatch;
import wtf.opal.client.feature.helper.impl.server.impl.HypixelServer;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.combat.killaura.KillAuraModule;
import wtf.opal.client.feature.module.impl.movement.InventoryMoveModule;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldModule;
import wtf.opal.client.feature.module.repository.ModuleRepository;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.impl.game.packet.ReceivePacketEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.misc.time.Stopwatch;
import wtf.opal.utility.player.InventoryUtility;
import wtf.opal.utility.player.PlayerUtility;

import java.util.Comparator;

import static wtf.opal.client.Constants.mc;

public final class InventoryManagerModule extends Module {

    private final InventoryManagerSettings settings = new InventoryManagerSettings(this);

    public final Stopwatch stopwatch = new Stopwatch();

    public InventoryManagerModule() {
        super("Inventory Manager", "Manages your inventory.", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreGameTickEvent(final PreGameTickEvent event) {
        if (mc.player == null) return;

        final ModuleRepository moduleRepository = OpalClient.getInstance().getModuleRepository();

        if (!(mc.currentScreen instanceof InventoryScreen) && !moduleRepository.getModule(InventoryMoveModule.class).isEnabled())
            return;

        final KillAuraModule killAuraModule = moduleRepository.getModule(KillAuraModule.class);
        final ScaffoldModule scaffoldModule = moduleRepository.getModule(ScaffoldModule.class);
        if ((killAuraModule.isEnabled() && killAuraModule.getTargeting().isTargetSelected())
                || scaffoldModule.isEnabled()) {
            return;
        }

        final boolean blitz;
        if (LocalDataWatch.get().getKnownServerManager().getCurrentServer() instanceof HypixelServer) {
            final HypixelServer.ModAPI.Location currentLocation = HypixelServer.ModAPI.get().getCurrentLocation();
            if (currentLocation == null || currentLocation.isLobby()) {
                return;
            }

            if (currentLocation.serverType() == GameType.SURVIVAL_GAMES) {
                blitz = false;
            } else {
                blitz = false;
                if (currentLocation.serverType() != GameType.SKYWARS) {
                    return;
                }
            }
        } else {
            blitz = false;
        }

        final ScreenHandler screenHandler = mc.player.currentScreenHandler;

        if (!(screenHandler instanceof PlayerScreenHandler playerHandler)) {
            return;
        }

        final Slot bestSword = getBestSword(playerHandler);
        final Slot preferredSwordSlot = screenHandler.getSlot(settings.getSwordSlot() + 35);

        final Slot bestPickaxe = getBestPickaxe(playerHandler);
        final Slot preferredPickaxeSlot = screenHandler.getSlot(settings.getPickaxeSlot() + 35);

        final Slot bestAxe = getBestAxe(playerHandler);
        final Slot preferredAxeSlot = screenHandler.getSlot(settings.getAxeSlot() + 35);

        final Slot mostBlocks = getMostBlocks(playerHandler);
        final Slot preferredBlockSlot = screenHandler.getSlot(settings.getBlockSlot() + 35);

        final Slot bestGapple = getBestGapple(playerHandler);
        final Slot preferredGappleSlot = screenHandler.getSlot(settings.getGappleSlot() + 35);

        final Slot bestGoldenGapple = getBestGoldenGapple(playerHandler);
        final Slot preferredGoldenGappleSlot = screenHandler.getSlot(settings.getGoldenGappleSlot() + 35);

        final Slot bestBow = getBestBow(playerHandler);
        final Slot preferredBowSlot = screenHandler.getSlot(settings.getBowSlot() + 35);

        final Slot mostPotions = getMostPotions(playerHandler);
        final Slot preferredPotionSlot = screenHandler.getSlot(settings.getPotionSlot() + 35);

        final Slot mostEnderPearls = getMostEnderPearls(playerHandler);
        final Slot preferredEnderPearlSlot = screenHandler.getSlot(settings.getEnderPearlSlot() + 35);

        InventoryUtility.filterSlots(playerHandler, slot -> !slot.getStack().isEmpty(), true).forEach(validSlot -> {
            if (!canMove(settings.getDelay().longValue()) || InventoryUtility.isGoodItem(validSlot.getStack())) {
                return;
            }

            if (validSlot.getStack().getComponents().get(DataComponentTypes.EQUIPPABLE) != null) {
                return;
            }

            final ItemStack stack = validSlot.getStack();

            if (stack.isIn(ItemTags.SWORDS)) {
                if (settings.getSlots().getProperty("Sword").getValue()) {
                    if (bestSword != null && ItemStack.areItemsEqual(stack, bestSword.getStack())) {
                        return;
                    }
                }
                return;
            }

            if (stack.isIn(ItemTags.PICKAXES)) {
                if (settings.getSlots().getProperty("Pickaxe").getValue()) {
                    if (bestPickaxe != null && ItemStack.areItemsEqual(stack, bestPickaxe.getStack())) {
                        return;
                    }
                }
                return;
            }

            if (stack.getItem() instanceof AxeItem) {
                if (settings.getSlots().getProperty("Axe").getValue()) {
                    if (bestAxe != null && ItemStack.areItemsEqual(stack, bestAxe.getStack())) {
                        return;
                    }
                }
                return;
            }

            if (stack.getItem() instanceof BlockItem && settings.getSlots().getProperty("Blocks").getValue()) {
                if (mostBlocks != null && ItemStack.areItemsEqual(stack, mostBlocks.getStack())) {
                    return;
                }
            }

            if (stack.getItem() == Items.GOLDEN_APPLE && !stack.hasEnchantments() && settings.getSlots().getProperty("Gapple").getValue()) {
                if (bestGapple != null && ItemStack.areItemsEqual(stack, bestGapple.getStack())) {
                    return;
                }
            }

            if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && settings.getSlots().getProperty("Golden Gapple").getValue()) {
                if (bestGoldenGapple != null && ItemStack.areItemsEqual(stack, bestGoldenGapple.getStack())) {
                    return;
                }
            }

            if (stack.getItem() instanceof BowItem && settings.getSlots().getProperty("Bow").getValue()) {
                if (bestBow != null && ItemStack.areItemsEqual(stack, bestBow.getStack())) {
                    return;
                }
            }

            if (stack.getItem() instanceof PotionItem && settings.getSlots().getProperty("Potions").getValue()) {
                if (mostPotions != null && ItemStack.areItemsEqual(stack, mostPotions.getStack())) {
                    return;
                }
            }

            if (stack.getItem() == Items.ENDER_PEARL && settings.getSlots().getProperty("Ender Pearls").getValue()) {
                if (mostEnderPearls != null && ItemStack.areItemsEqual(stack, mostEnderPearls.getStack())) {
                    return;
                }
            }

            if (settings.getSlots().getProperty("Sword").getValue()) {
                arrangeBestSword(screenHandler, preferredSwordSlot, bestSword);
            }
            if (settings.getSlots().getProperty("Pickaxe").getValue()) {
                arrangeBestPickaxe(screenHandler, preferredPickaxeSlot, bestPickaxe);
            }
            if (settings.getSlots().getProperty("Axe").getValue()) {
                arrangeBestAxe(screenHandler, preferredAxeSlot, bestAxe);
            }
            if (settings.getSlots().getProperty("Blocks").getValue()) {
                arrangeMostBlocks(screenHandler, preferredBlockSlot, mostBlocks);
            }
            if (settings.getSlots().getProperty("Gapple").getValue()) {
                arrangeBestGapple(screenHandler, preferredGappleSlot, bestGapple);
            }
            if (settings.getSlots().getProperty("Golden Gapple").getValue()) {
                arrangeBestGoldenGapple(screenHandler, preferredGoldenGappleSlot, bestGoldenGapple);
            }
            if (settings.getSlots().getProperty("Bow").getValue()) {
                arrangeBestBow(screenHandler, preferredBowSlot, bestBow);
            }
            if (settings.getSlots().getProperty("Potions").getValue()) {
                arrangeMostPotions(screenHandler, preferredPotionSlot, mostPotions);
            }
            if (settings.getSlots().getProperty("Ender Pearls").getValue()) {
                arrangeMostEnderPearls(screenHandler, preferredEnderPearlSlot, mostEnderPearls);
            }

            if (validSlot.getIndex() == preferredSwordSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredPickaxeSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredAxeSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredBlockSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredGappleSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredGoldenGappleSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredBowSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredPotionSlot.getIndex()) {
                return;
            }
            if (validSlot.getIndex() == preferredEnderPearlSlot.getIndex()) {
                return;
            }
            if (validSlot.getStack().getItem() instanceof BucketItem) {
                return;
            }

            if (validSlot.getStack().getName().getStyle().isEmpty()) {
                InventoryUtility.drop(playerHandler, validSlot.id);
                stopwatch.reset();
            }
        });
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        if (event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdate
                && slotUpdate.getStack().getItem() != Items.AIR
                && mc.player != null
                && slotUpdate.getSyncId() == mc.player.playerScreenHandler.syncId) {
            stopwatch.reset();
        }
    }

    private void arrangeBestSword(final ScreenHandler screenHandler, final Slot preferredSwordSlot, final Slot bestSwordSlot) {
        if (bestSwordSlot != null && bestSwordSlot.getIndex() != preferredSwordSlot.getIndex()) {
            double bestSwordValue = InventoryUtility.getSwordValue(bestSwordSlot.getStack());
            double preferredSwordValue = InventoryUtility.getSwordValue(preferredSwordSlot.getStack());

            if (bestSwordValue > preferredSwordValue) {
                InventoryUtility.swap(screenHandler, bestSwordSlot.id, preferredSwordSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestSword(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().isIn(ItemTags.SWORDS), false)
                .stream()
                .max(Comparator.comparing(swordSlot -> InventoryUtility.getSwordValue(swordSlot.getStack())))
                .orElse(null);
    }

    private void arrangeBestPickaxe(final ScreenHandler screenHandler, final Slot preferredPickaxeSlot, final Slot bestPickaxeSlot) {
        if (bestPickaxeSlot != null && bestPickaxeSlot.getIndex() != preferredPickaxeSlot.getIndex()) {
            double bestPickaxeValue = InventoryUtility.getToolValue(bestPickaxeSlot.getStack());
            double preferredPickaxeValue = InventoryUtility.getToolValue(preferredPickaxeSlot.getStack());

            if (bestPickaxeValue > preferredPickaxeValue) {
                InventoryUtility.swap(screenHandler, bestPickaxeSlot.id, preferredPickaxeSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestPickaxe(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().isIn(ItemTags.PICKAXES), false)
                .stream()
                .max(Comparator.comparing(pickaxeSlot -> InventoryUtility.getToolValue(pickaxeSlot.getStack())))
                .orElse(null);
    }

    private void arrangeBestAxe(final ScreenHandler screenHandler, final Slot preferredAxeSlot, final Slot bestAxeSlot) {
        if (bestAxeSlot != null && bestAxeSlot.getIndex() != preferredAxeSlot.getIndex()) {
            double bestAxeValue = InventoryUtility.getToolValue(bestAxeSlot.getStack());
            double preferredAxeValue = InventoryUtility.getToolValue(preferredAxeSlot.getStack());

            if (bestAxeValue > preferredAxeValue) {
                InventoryUtility.swap(screenHandler, bestAxeSlot.id, preferredAxeSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestAxe(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().getItem() instanceof AxeItem, false)
                .stream()
                .max(Comparator.comparing(axeSlot -> InventoryUtility.getToolValue(axeSlot.getStack())))
                .orElse(null);
    }

    private Slot getMostBlocks(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot ->
                                slot.getStack().getItem() instanceof BlockItem blockItem &&
                                        slot.getStack().getCount() <= settings.getMaxBlocks() &&
                                        InventoryUtility.isGoodBlock(blockItem.getBlock())
                        , false)
                .stream()
                .max(Comparator.comparing(blockSlot -> blockSlot.getStack().getCount()))
                .orElse(null);
    }

    private void arrangeMostBlocks(final ScreenHandler screenHandler, final Slot preferredBlockSlot, final Slot mostBlockSlot) {
        if (mostBlockSlot != null && mostBlockSlot.getIndex() != preferredBlockSlot.getIndex()) {
            double mostBlockCount = mostBlockSlot.getStack().getCount();
            double preferredBlockValue = preferredBlockSlot.getStack().getCount();

            if (mostBlockCount > preferredBlockValue) {
                InventoryUtility.swap(screenHandler, mostBlockSlot.id, preferredBlockSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestGapple(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot ->
                                slot.getStack().getItem() == Items.GOLDEN_APPLE && !slot.getStack().hasEnchantments(), false)
                .stream()
                .max(Comparator.comparing(gappleSlot -> gappleSlot.getStack().getCount()))
                .orElse(null);
    }

    private void arrangeBestGapple(final ScreenHandler screenHandler, final Slot preferredGappleSlot, final Slot bestGappleSlot) {
        if (bestGappleSlot != null && bestGappleSlot.getIndex() != preferredGappleSlot.getIndex()) {
            int bestGappleCount = bestGappleSlot.getStack().getCount();
            int preferredGappleCount = preferredGappleSlot.getStack().getCount();

            if (bestGappleCount > preferredGappleCount) {
                InventoryUtility.swap(screenHandler, bestGappleSlot.id, preferredGappleSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestGoldenGapple(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot ->
                                slot.getStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE, false)
                .stream()
                .max(Comparator.comparing(goldenGappleSlot -> goldenGappleSlot.getStack().getCount()))
                .orElse(null);
    }

    private void arrangeBestGoldenGapple(final ScreenHandler screenHandler, final Slot preferredGoldenGappleSlot, final Slot bestGoldenGappleSlot) {
        if (bestGoldenGappleSlot != null && bestGoldenGappleSlot.getIndex() != preferredGoldenGappleSlot.getIndex()) {
            int bestGoldenGappleCount = bestGoldenGappleSlot.getStack().getCount();
            int preferredGoldenGappleCount = preferredGoldenGappleSlot.getStack().getCount();

            if (bestGoldenGappleCount > preferredGoldenGappleCount) {
                InventoryUtility.swap(screenHandler, bestGoldenGappleSlot.id, preferredGoldenGappleSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getBestBow(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().getItem() instanceof BowItem, false)
                .stream()
                .max(Comparator.comparing(bowSlot -> InventoryUtility.getBowAttackBonus(bowSlot.getStack())))
                .orElse(null);
    }

    private void arrangeBestBow(final ScreenHandler screenHandler, final Slot preferredBowSlot, final Slot bestBowSlot) {
        if (bestBowSlot != null && bestBowSlot.getIndex() != preferredBowSlot.getIndex()) {
            double bestBowValue = InventoryUtility.getBowAttackBonus(bestBowSlot.getStack());
            double preferredBowValue = InventoryUtility.getBowAttackBonus(preferredBowSlot.getStack());

            if (bestBowValue > preferredBowValue) {
                InventoryUtility.swap(screenHandler, bestBowSlot.id, preferredBowSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getMostPotions(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().getItem() instanceof PotionItem, false)
                .stream()
                .max(Comparator.comparing(potionSlot -> potionSlot.getStack().getCount()))
                .orElse(null);
    }

    private void arrangeMostPotions(final ScreenHandler screenHandler, final Slot preferredPotionSlot, final Slot mostPotionSlot) {
        if (mostPotionSlot != null && mostPotionSlot.getIndex() != preferredPotionSlot.getIndex()) {
            int mostPotionCount = mostPotionSlot.getStack().getCount();
            int preferredPotionCount = preferredPotionSlot.getStack().getCount();

            if (mostPotionCount > preferredPotionCount) {
                InventoryUtility.swap(screenHandler, mostPotionSlot.id, preferredPotionSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    private Slot getMostEnderPearls(final ScreenHandler screenHandler) {
        return InventoryUtility.filterSlots(screenHandler, slot -> slot.getStack().getItem() == Items.ENDER_PEARL, false)
                .stream()
                .max(Comparator.comparing(enderPearlSlot -> enderPearlSlot.getStack().getCount()))
                .orElse(null);
    }

    private void arrangeMostEnderPearls(final ScreenHandler screenHandler, final Slot preferredEnderPearlSlot, final Slot mostEnderPearlSlot) {
        if (mostEnderPearlSlot != null && mostEnderPearlSlot.getIndex() != preferredEnderPearlSlot.getIndex()) {
            int mostEnderPearlCount = mostEnderPearlSlot.getStack().getCount();
            int preferredEnderPearlCount = preferredEnderPearlSlot.getStack().getCount();

            if (mostEnderPearlCount > preferredEnderPearlCount) {
                InventoryUtility.swap(screenHandler, mostEnderPearlSlot.id, preferredEnderPearlSlot.id - 36);
                stopwatch.reset();
            }
        }
    }

    public boolean canMove(final long delay) {
        if (delay == 0) return true;

        return stopwatch.hasTimeElapsed(delay);
    }

}