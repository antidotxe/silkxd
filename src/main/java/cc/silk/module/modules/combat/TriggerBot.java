package cc.silk.module.modules.combat;

import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.event.impl.world.WorldChangeEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.modules.misc.Teams;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.RangeSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.math.MathUtils;
import cc.silk.utils.math.TimerUtil;
import cc.silk.utils.mc.CombatUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

// Koi touch this and im going to fucking rape you

// sorry graph :3

public final class TriggerBot extends Module {
    public static final RangeSetting swordThreshold = new RangeSetting("Sword Threshold", 0.1, 1, 0.90, 0.95, 0.01);
    public static final RangeSetting axeThreshold = new RangeSetting("Axe Threshold", 0.1, 1, 0.90, 0.95, 0.01);
    public static final RangeSetting axePostDelay = new RangeSetting("Axe Post Delay", 1, 500, 120, 120, 0.5);
    public static final RangeSetting reactionTime = new RangeSetting("Reaction Time", 1, 350, 20, 95, 0.5);
    public static final RangeSetting missChance = new RangeSetting("Miss Chance", 0, 100, 0, 0, 1);
    public static final ModeSetting cooldownMode = new ModeSetting("Cooldown Mode", "Smart", "Smart", "Strict", "None");
    public static final ModeSetting critMode = new ModeSetting("Criticals", "Strict", "None", "Strict");
    public static final BooleanSetting ignorePassiveMobs = new BooleanSetting("No Passive", true);
    public static final BooleanSetting ignoreInvisible = new BooleanSetting("No Invisible", true);
    public static final BooleanSetting ignoreCrystals = new BooleanSetting("No Crystals", true);
    public static final BooleanSetting respectShields = new BooleanSetting("Ignore Shields", false);
    public static final BooleanSetting useOnlySwordOrAxe = new BooleanSetting("Only Sword or Axe", true);
    public static final BooleanSetting onlyWhenMouseDown = new BooleanSetting("Only Mouse Hold", false);
    public static final BooleanSetting disableOnWorldChange = new BooleanSetting("Disable on Load", false);
    public static final BooleanSetting samePlayer = new BooleanSetting("Same Player", false);

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil samePlayerTimer = new TimerUtil();
    private final TimerUtil timerReactionTime = new TimerUtil();

    public boolean waitingForDelay = false;

    private boolean waitingForReaction = false;
    private long currentReactionDelay = 0;
    private float randomizedPostDelay = 0;
    private float randomizedThreshold = 0;
    private Entity target;
    private String lastTargetUUID = null;

    public TriggerBot() {
        super("Trigger Bot", "Makes you automatically attack once aimed at a target", -1, Category.COMBAT);
        addSettings(
                swordThreshold, axeThreshold,
                axePostDelay, reactionTime, missChance,
                cooldownMode, critMode, ignorePassiveMobs, ignoreCrystals, respectShields,
                ignoreInvisible, onlyWhenMouseDown, useOnlySwordOrAxe,
                disableOnWorldChange, samePlayer);
    }

    @EventHandler
    private void onWorldChangeEvent(WorldChangeEvent event) {
        if (disableOnWorldChange.getValue() && this.isEnabled()) {
            this.toggle();
        }
    }

    @EventHandler
    private void handleInputEvent(HandleInputEvent event) {
        if (isNull())
            return;
        assert mc.player != null;
        if (mc.player.isUsingItem())
            return;
        if (mc.currentScreen != null)
            return;

        target = mc.targetedEntity;
        if (target == null)
            return;
        if (!isHoldingSwordOrAxe())
            return;

        if (onlyWhenMouseDown.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            return;
        }

        if (!hasTarget(target))
            return;

        if (respectShields.getValue()) {
            Item item = mc.player.getMainHandStack().getItem();
            if (target instanceof PlayerEntity playerTarget &&
                    CombatUtil.isShieldFacingAway(playerTarget) &&
                    item instanceof SwordItem) {
                return;
            }
        }

        if (target != null && (!target.getUuidAsString().equals(lastTargetUUID))) {
            lastTargetUUID = target.getUuidAsString();
        }

        if (!waitingForReaction) {
            waitingForReaction = true;
            timerReactionTime.reset();

            long delay;
            switch (cooldownMode.getMode()) {
                case "Smart" -> {
                    double distance = mc.player.distanceTo(target);
                    double maxDistance = 3.0;
                    double multiplier = distance < maxDistance / 2 ? 0.66 : 1.0;
                    delay = (long) MathUtils.randomDoubleBetween(reactionTime.getMinValue(),
                            reactionTime.getMaxValue());
                    delay *= (long) multiplier;
                }
                case "None" -> delay = 0;
                default -> delay = (long) MathUtils.randomDoubleBetween(reactionTime.getMinValue(),
                        reactionTime.getMaxValue());
            }

            currentReactionDelay = delay;
        }

        if (waitingForReaction && timerReactionTime.hasElapsedTime(currentReactionDelay, true)) {
            if (critMode.getMode().equals("Strict")) {
                if (!mc.player.isOnGround() && !mc.player.isClimbing()) {
                    if (canCrit() && mc.player.getAttackCooldownProgress(0.0f) >= swordThreshold.getMinValue()) {
                        if (hasTarget(target) && samePlayerCheck(target)) {
                            attack();
                            waitingForReaction = false;
                        }
                    }
                } else {
                    if (hasElapsedDelay() && hasTarget(target) && samePlayerCheck(target)) {
                        attack();
                        waitingForReaction = false;
                    }
                }
            } else {
                if (hasElapsedDelay() && hasTarget(target) && samePlayerCheck(target)) {
                    attack();
                    waitingForReaction = false;
                }
            }
        }
    }

    private boolean samePlayerCheck(Entity entity) {
        if (!samePlayer.getValue())
            return true;
        if (entity == null)
            return false;

        if (lastTargetUUID == null || samePlayerTimer.hasElapsedTime(3000, false)) {
            lastTargetUUID = entity.getUuidAsString();
            samePlayerTimer.reset();
            return true;
        }
        return entity.getUuidAsString().equals(lastTargetUUID);
    }

    private boolean canCrit() {
        if (mc.player == null)
            return false;

        return !mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isInLava()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && mc.player.fallDistance > 0.065f
                && mc.player.getVehicle() == null;
    }

    private boolean setPreferCrits() {
        if (mc.player == null || mc.world == null)
            return false;

        String mode = critMode.getMode();
        if (mode.equals("None"))
            return false;

        if (mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return false;
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult hitResult))
            return false;
        Entity targetEntity = hitResult.getEntity();
        if (targetEntity != target || !hasTarget(targetEntity))
            return false;

        if (mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.isSubmergedInWater()
                || mc.player.isClimbing()) {
            return false;
        }

        BlockState state = mc.world.getBlockState(mc.player.getBlockPos());
        if (state.isOf(Blocks.COBWEB)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SCAFFOLDING)
                || state.isOf(Blocks.SLIME_BLOCK)
                || state.isOf(Blocks.HONEY_BLOCK)
                || state.isOf(Blocks.POWDER_SNOW)) {
            return false;
        }

        boolean cooldownReady = mc.player.getAttackCooldownProgress(0.0f) >= swordThreshold.getMinValue();
        return mode.equals("Strict") && cooldownReady && canCrit();
    }

    private boolean hasElapsedDelay() {
        if (setPreferCrits())
            return false;

        assert mc.player != null;
        Item heldItem = mc.player.getMainHandStack().getItem();
        float cooldown = mc.player.getAttackCooldownProgress(0.0f);

        if (heldItem instanceof AxeItem) {
            if (!waitingForDelay) {
                randomizedThreshold = (float) MathUtils.randomDoubleBetween(axeThreshold.getMinValue(),
                        axeThreshold.getMaxValue());
                randomizedPostDelay = (float) MathUtils.randomDoubleBetween(axePostDelay.getMinValue(),
                        axePostDelay.getMaxValue());
                waitingForDelay = true;
            }
            if (cooldown >= randomizedThreshold) {
                if (timer.hasElapsedTime((long) randomizedPostDelay, true)) {
                    waitingForDelay = false;
                    return true;
                }
            } else {
                timer.reset();
            }
            return false;
        } else {
            float swordDelay = (float) MathUtils.randomDoubleBetween(swordThreshold.getMinValue(),
                    swordThreshold.getMaxValue());
            return cooldown >= swordDelay;
        }
    }

    private boolean isHoldingSwordOrAxe() {
        if (!useOnlySwordOrAxe.getValue())
            return true;
        assert mc.player != null;
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof AxeItem || item instanceof SwordItem;
    }

    public void attack() {
        if (missChance.getMinValue() > 0 && Math.random() * 100 < missChance.getMinValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.resetLastAttackedTicks();
        } else {
            ((MinecraftClientAccessor) mc).invokeDoAttack();
        }
        if (samePlayer.getValue() && target != null) {
            lastTargetUUID = target.getUuidAsString();
            samePlayerTimer.reset();
        }
        waitingForDelay = false;
    }

    public boolean hasTarget(Entity en) {
        if (en == mc.player || en == mc.cameraEntity || !en.isAlive())
            return false;
        if (en instanceof PlayerEntity player && FriendManager.isFriend(player.getUuid()))
            return false;
        if (Teams.isTeammate(en))
            return false;
        if (en instanceof WindChargeEntity)
            return false;

        return switch (en) {
            case EndCrystalEntity ignored when ignoreCrystals.getValue() -> false;
            case Tameable ignored -> false;
            case PassiveEntity ignored when ignorePassiveMobs.getValue() -> false;
            default -> !ignoreInvisible.getValue() || !en.isInvisible();
        };
    }

    @Override
    public void onEnable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
        super.onDisable();
    }
}