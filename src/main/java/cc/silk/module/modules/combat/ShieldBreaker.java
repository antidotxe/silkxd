package cc.silk.module.modules.combat;

import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.event.impl.player.DoAttackEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.math.TimerUtil;
import cc.silk.utils.mc.CombatUtil;
import cc.silk.utils.mc.InventoryUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;

// unga_
public final class ShieldBreaker extends Module {

    public static boolean breakingShield = false;

    private final NumberSetting cps = new NumberSetting("CPS", 1, 20, 20, 1);
    private final NumberSetting reactionDelay = new NumberSetting("Reaction Delay", 0, 250, 0, 5);
    private final NumberSetting swapDelay = new NumberSetting("Swap Delay", 0, 500, 50, 10);
    private final NumberSetting attackDelay = new NumberSetting("Attack Delay", 0, 500, 50, 10);
    private final NumberSetting swapBackDelay = new NumberSetting("Swap Back Delay", 0, 500, 100, 10);
    private final BooleanSetting revertSlot = new BooleanSetting("Revert Slot", true);
    private final BooleanSetting rayTraceCheck = new BooleanSetting("Check Facing", true);
    private final BooleanSetting autoStun = new BooleanSetting("Auto Stun", true);
    private final BooleanSetting disableIfUsingItem = new BooleanSetting("Disable if using item", true);
    private final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", false);

    private final TimerUtil cpsTimer = new TimerUtil();
    private final TimerUtil reactionTimer = new TimerUtil();
    private final TimerUtil swapTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil swapBackTimer = new TimerUtil();

    private int savedSlot = -1;

    public ShieldBreaker() {
        super("Shield Breaker", "Automatically breaks the opponent's shield", -1, Category.COMBAT);
        addSettings(
                cps, reactionDelay, swapDelay, attackDelay, swapBackDelay,
                revertSlot, rayTraceCheck, disableIfUsingItem, ignoreFriends, autoStun
        );
    }

    private boolean canRunAuto() {
        if (isNull() || mc.currentScreen != null)
            return false;
        if (!InventoryUtil.hasWeapon(AxeItem.class))
            return false;
        if (mc.player.isUsingItem() && disableIfUsingItem.getValue())
            return false;
        return cpsTimer.hasElapsedTime((long) (1000.0 / cps.getValue()));
    }

    private PlayerEntity getTargetPlayer() {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit))
            return null;
        if (!(hit.getEntity() instanceof PlayerEntity target))
            return null;
        if (FriendManager.isFriend(target.getUuid()) && ignoreFriends.getValue())
            return null;
        return target;
    }

    @EventHandler
    private void onTickEvent(HandleInputEvent event) {
        if (isNull())
            return;

        PlayerEntity target = getTargetPlayer();

        // Handle swapping back to original slot when not attacking a shielding player
        if (savedSlot != -1 && swapBackTimer.hasElapsedTime(swapBackDelay.getValueInt())) {
            boolean shouldSwapBack = false;

            if (target == null) {
                shouldSwapBack = true;
            } else {
                boolean isBlocking = target.isBlocking() && target.isHolding(Items.SHIELD);
                boolean canBreak = !rayTraceCheck.getValue() || !CombatUtil.isShieldFacingAway(target);

                if (!isBlocking || !canBreak) {
                    shouldSwapBack = true;
                }
            }

            if (shouldSwapBack) {
                if (revertSlot.getValue())
                    mc.player.getInventory().selectedSlot = savedSlot;
                savedSlot = -1;
                breakingShield = false;
                return;
            }
        }

        if (!canRunAuto())
            return;

        if (target == null)
            return;

        boolean isBlocking = target.isBlocking() && target.isHolding(Items.SHIELD);
        boolean canBreak = !rayTraceCheck.getValue() || !CombatUtil.isShieldFacingAway(target);

        if (!isBlocking || !canBreak) {
            if (!reactionTimer.hasElapsedTime(reactionDelay.getValueInt() / 2))
                reactionTimer.reset();
            return;
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
            if (reactionTimer.hasElapsedTime(reactionDelay.getValueInt())
                    && swapTimer.hasElapsedTime(swapDelay.getValueInt())) {

                breakingShield = true;

                if (savedSlot == -1)
                    savedSlot = mc.player.getInventory().selectedSlot;

                InventoryUtil.swapToWeapon(AxeItem.class);
                attackTimer.reset();
                swapTimer.reset();
            }
            return;
        }

        if (attackTimer.hasElapsedTime(attackDelay.getValueInt()) || savedSlot == -1) {
            ((MinecraftClientAccessor) mc).invokeDoAttack();

            if (autoStun.getValue()) {
                ((MinecraftClientAccessor) mc).invokeDoAttack();
            }

            cpsTimer.reset();
            attackTimer.reset();
            swapBackTimer.reset();
            breakingShield = false;
        }
    }

    @Override
    public void onDisable() {
        if (savedSlot != -1 && revertSlot.getValue()) {
            mc.player.getInventory().selectedSlot = savedSlot;
        }
        savedSlot = -1;
        breakingShield = false;
        super.onDisable();
    }
}
