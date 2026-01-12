package cc.silk.module.modules.combat;
import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.modules.misc.Teams;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.RangeSetting;
import cc.silk.utils.math.MathUtils;
import cc.silk.utils.math.TimerUtil;
import cc.silk.utils.mc.CombatUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.*;
import org.lwjgl.glfw.GLFW;

public final class TriggerBot extends Module {
    public static final RangeSetting swordThreshold = new RangeSetting("Sword Threshold", 0.1, 1, 0.9, 0.95, 0.01);
    public static final RangeSetting axeThreshold = new RangeSetting("Axe Threshold", 0.1, 1, 0.9, 0.95, 0.01);
    public static final RangeSetting axePostDelay = new RangeSetting("Axe Post Delay", 1, 500, 120, 120, 0.5);
    public static final RangeSetting reactionTime = new RangeSetting("Reaction Time", 1, 350, 20, 95, 0.5);

    public static final ModeSetting cooldownMode = new ModeSetting("Cooldown Mode", "Smart", "Smart", "Strict", "None");
    public static final ModeSetting critMode = new ModeSetting("Criticals", "Strict", "None", "Strict");

    public static final BooleanSetting ignorePassiveMobs = new BooleanSetting("No Passive", true);
    public static final BooleanSetting ignoreInvisible = new BooleanSetting("No Invisible", true);
    public static final BooleanSetting ignoreTamed = new BooleanSetting("No Tamed", true);
    public static final BooleanSetting ignoreCrystals = new BooleanSetting("No Crystals", true);
    public static final BooleanSetting respectShields = new BooleanSetting("Ignore Shields", false);
    public static final BooleanSetting useOnlySwordOrAxe = new BooleanSetting("Only Melee", false);
    public static final BooleanSetting onlyWhenMouseDown = new BooleanSetting("Only Mouse Hold", false);
    public static final BooleanSetting samePlayer = new BooleanSetting("Same Player", false);

    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil reactionTimer = new TimerUtil();
    private final TimerUtil samePlayerTimer = new TimerUtil();

    private boolean waitingForReaction = false;
    private boolean waitingForPostDelay = false;
    private float randomizedThreshold = 0f;
    private float randomizedPostDelay = 0f;
    private long currentReactionDelay = 0L;
    private Entity target;
    private String lastTargetUUID = null;

    public TriggerBot() {
        super("Trigger Bot", "Automatically attacks targets when aimed", -1, Category.COMBAT);
        addSettings(
                swordThreshold, axeThreshold, axePostDelay, reactionTime,
                cooldownMode, critMode, ignorePassiveMobs, ignoreInvisible, ignoreTamed, ignoreCrystals,
                respectShields, useOnlySwordOrAxe, onlyWhenMouseDown, samePlayer
        );
    }

    @EventHandler
    public void onHandleInput(HandleInputEvent event) {
        if (isNull() || mc.player == null || mc.currentScreen != null || mc.player.isUsingItem()) return;

        target = mc.targetedEntity;
        if (target == null || !hasTarget(target) || !isHoldingWeapon()) return;

        if (onlyWhenMouseDown.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            return;
        }

        if (respectShields.getValue() && target instanceof PlayerEntity playerTarget) {
            Item mainHand = mc.player.getMainHandStack().getItem();
            if (CombatUtil.isShieldFacingAway(playerTarget) && mc.player.getMainHandStack().getItem() instanceof SwordItem) return;
        }

        if (target != null && !target.getUuidAsString().equals(lastTargetUUID)) {
            lastTargetUUID = target.getUuidAsString();
        }

        if (!waitingForReaction) {
            waitingForReaction = true;
            reactionTimer.reset();
            currentReactionDelay = calculateReactionDelay();
        }

        if (waitingForReaction && reactionTimer.hasElapsedTime(currentReactionDelay, true)) {
            attemptAttack();
            waitingForReaction = false;
        }
    }

    private long calculateReactionDelay() {
        switch (cooldownMode.getMode()) {
            case "Smart" -> {
                double distance = mc.player.distanceTo(target);
                double multiplier = (distance < 1.5) ? 0.66 : 1.0;
                return (long) (MathUtils.randomDoubleBetween(reactionTime.getMinValue(), reactionTime.getMaxValue()) * multiplier);
            }
            case "None" -> { return 0; }
            default -> { return (long) MathUtils.randomDoubleBetween(reactionTime.getMinValue(), reactionTime.getMaxValue()); }
        }
    }

    private void attemptAttack() {
        if (critMode.getMode().equals("Strict") && canCrit()) {
            if (mc.player.getAttackCooldownProgress(0f) >= swordThreshold.getMinValue() && samePlayerCheck(target)) {
                attack();
            }
        } else {
            if (hasElapsedDelay() && samePlayerCheck(target)) {
                attack();
            }
        }
    }

    private boolean samePlayerCheck(Entity entity) {
        if (!samePlayer.getValue()) return true;
        if (entity == null) return false;

        if (lastTargetUUID == null || samePlayerTimer.hasElapsedTime(3000, false)) {
            lastTargetUUID = entity.getUuidAsString();
            samePlayerTimer.reset();
            return true;
        }
        return entity.getUuidAsString().equals(lastTargetUUID);
    }

    private boolean canCrit() {
        return !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isInLava()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && mc.player.fallDistance > 0.065f
                && mc.player.getVehicle() == null;
    }

    private boolean hasElapsedDelay() {
        Item mainHand = mc.player.getMainHandStack().getItem();
        float cooldown = mc.player.getAttackCooldownProgress(0f);

        if (mainHand instanceof AxeItem) {
            if (!waitingForPostDelay) {
                randomizedThreshold = (float) MathUtils.randomDoubleBetween(axeThreshold.getMinValue(), axeThreshold.getMaxValue());
                randomizedPostDelay = (float) MathUtils.randomDoubleBetween(axePostDelay.getMinValue(), axePostDelay.getMaxValue());
                waitingForPostDelay = true;
            }

            if (cooldown >= randomizedThreshold) {
                if (attackTimer.hasElapsedTime((long) randomizedPostDelay, true)) {
                    waitingForPostDelay = false;
                    return true;
                }
            } else {
                attackTimer.reset();
            }
            return false;
        }

        // what the fuh
        float threshold = (mainHand instanceof SwordItem || mainHand instanceof TridentItem || mainHand == Items.AIR)
                ? (float) MathUtils.randomDoubleBetween(swordThreshold.getMinValue(), swordThreshold.getMaxValue())
                : 0f;

        return cooldown >= threshold;
    }


    private boolean isHoldingWeapon() {
        if (!useOnlySwordOrAxe.getValue()) return true;

        var item = mc.player.getMainHandStack().getItem();

        return item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem
                || item == Items.AIR;
    }



    public void attack() {
        ((MinecraftClientAccessor) mc).invokeDoAttack();
        if (samePlayer.getValue() && target != null) {
            lastTargetUUID = target.getUuidAsString();
            samePlayerTimer.reset();
        }
        waitingForPostDelay = false;
    }

    private boolean hasTarget(Entity en) {
        if (en == mc.player || en == mc.cameraEntity || !en.isAlive()) return false;
        if (Teams.isTeammate(en)) return false;
        return switch (en) {
            case WindChargeEntity windChargeEntity -> false;
            case EndCrystalEntity endCrystalEntity when ignoreCrystals.getValue() -> false;
            case Tameable tameable when ignoreTamed.getValue() -> false;
            case PassiveEntity passiveEntity when ignorePassiveMobs.getValue() -> false;
            default -> !ignoreInvisible.getValue() || !en.isInvisible();
        };

    }

    @Override
    public void onEnable() {
        resetTimers();
    }

    @Override
    public void onDisable() {
        resetTimers();
    }

    private void resetTimers() {
        attackTimer.reset();
        reactionTimer.reset();
        waitingForReaction = false;
        waitingForPostDelay = false;
    }
}