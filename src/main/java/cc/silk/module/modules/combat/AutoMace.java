package cc.silk.module.modules.combat;

import cc.silk.event.impl.input.HandleInputEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.modules.misc.Teams;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import cc.silk.utils.math.TimerUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class AutoMace extends Module {

    private final NumberSetting minFallDistance = new NumberSetting("Min Fall Distance", 1.0, 10.0, 3.0, 0.5);
    private final NumberSetting attackDelay = new NumberSetting("Attack Delay", 0, 500, 100, 10);
    private final NumberSetting densityThreshold = new NumberSetting("Density Threshold", 1.0, 20.0, 7.0, 0.5);
    private final BooleanSetting targetPlayers = new BooleanSetting("Target Players", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", false);
    private final BooleanSetting stunSlam = new BooleanSetting("Stun Slam", false);
    private final BooleanSetting onlyAxe = new BooleanSetting("Only Axe", false);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch Mace", true);
    private final BooleanSetting stayOnMace = new BooleanSetting("Stay On Mace", false);

    private final TimerUtil attackTimer = new TimerUtil();
    private int savedSlot = -1;
    private double fallStartY = -1;
    private boolean isFalling = false;
    private boolean slamExecuted = false;
    private boolean maceHit = false;
    private int slamTick = 0;

    public AutoMace() {
        super("Auto Mace", "Automatically attacks with mace", -1, Category.COMBAT);
        this.addSettings(minFallDistance, attackDelay, densityThreshold, targetPlayers, targetMobs, stunSlam,
                onlyAxe, autoSwitch, stayOnMace);
    }

    @EventHandler
    private void preMotion(TickEvent event) {
        if (isNull())
            return;

        updateFall();
        attack();
    }

    private void updateFall() {
        boolean onGround = mc.player.isOnGround();
        boolean falling = mc.player.getVelocity().y < -0.1;
        boolean rising = mc.player.getVelocity().y > 0.1;
        double currentY = mc.player.getY();

        if (onGround) {
            if (isFalling) {
                resetFall();
            }
            if (savedSlot != -1 && !stayOnMace.getValue()) {
                switchToSlot(savedSlot);
                savedSlot = -1;
            }
            return;
        }

        if (rising && maceHit) {
            maceHit = false;
            fallStartY = currentY;
        }

        if (!isFalling) {
            isFalling = true;
            fallStartY = currentY;
            slamExecuted = false;
            maceHit = false;
            slamTick = 0;
        } else if (falling && fallStartY != -1 && currentY > fallStartY) {
            fallStartY = currentY;
        }
    }

    private void attack() {
        if (!isFalling || mc.player.getVelocity().y >= -0.1)
            return;

        double fallDist = fallStartY == -1 ? 0 : Math.max(0, fallStartY - mc.player.getY());
        if (fallDist < minFallDistance.getValueFloat())
            return;

        Entity target = mc.targetedEntity;
        if (!isValidTarget(target) || FriendManager.isFriend(target.getUuid()))
            return;

        if (stunSlam.getValue()) {
            handleSlam(target, fallDist);
        }

        if (!stunSlam.getValue() || slamExecuted || slamTick == 0) {
            handleMaceAttack(target);
        }
    }

    private void handleSlam(Entity target, double fallDist) {
        boolean targetBlocking = target instanceof PlayerEntity player &&
                player.isHolding(Items.SHIELD) &&
                player.isBlocking();

        if (onlyAxe.getValue() && !isAxe(mc.player.getMainHandStack())) {
            return;
        }

        if (targetBlocking && fallDist > minFallDistance.getValueFloat() && !slamExecuted && slamTick == 0) {
            if (savedSlot == -1)
                savedSlot = mc.player.getInventory().selectedSlot;
            slamTick = 1;
        }

        if (slamTick == 1) {
            int axeSlot = onlyAxe.getValue() ? mc.player.getInventory().selectedSlot : getAxeSlotId();
            if (axeSlot != -1) {
                mc.player.getInventory().selectedSlot = axeSlot;
                ((MinecraftClientAccessor) mc).invokeDoAttack();
            }
            slamTick = 2;
        } else if (slamTick == 2) {
            switchToMace();
            slamExecuted = true;
            slamTick = 0;
        }
    }

    private void handleMaceAttack(Entity target) {
        if (maceHit) return;
        
        double fallDist = fallStartY == -1 ? 0 : Math.max(0, fallStartY - mc.player.getY());

        if (!hasMace()) {
            if (savedSlot == -1)
                savedSlot = mc.player.getInventory().selectedSlot;
            if (autoSwitch.getValue()) {
                switchToAppropriateMace(fallDist);
            } else {
                switchToMace();
            }
        } else if (autoSwitch.getValue()) {
            switchToAppropriateMace(fallDist);
        }

        if (hasMace() && attackTimer.hasElapsedTime((long) attackDelay.getValue(), true)) {
            ((MinecraftClientAccessor) mc).invokeDoAttack();
            maceHit = true;
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.cameraEntity)
            return false;
        if (!(entity instanceof LivingEntity livingEntity))
            return false;
        if (!livingEntity.isAlive() || livingEntity.isDead())
            return false;
        if (Teams.isTeammate(entity))
            return false;

        if (entity instanceof PlayerEntity) {
            return targetPlayers.getValue();
        } else {
            if (!targetMobs.getValue())
                return false;
            return !(entity instanceof PassiveEntity) && !(entity instanceof Tameable);
        }
    }

    private int getAxeSlotId() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAxe(stack))
                return i;
        }
        return -1;
    }

    private boolean isAxe(ItemStack stack) {
        return stack.getItem() instanceof AxeItem;
    }

    private boolean hasMace() {
        return mc.player.getMainHandStack().getItem() == Items.MACE;
    }

    private void switchToMace() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE) {
                mc.player.getInventory().selectedSlot = i;
                return;
            }
        }
    }

    private void switchToAppropriateMace(double fallDistance) {
        boolean useDensity = fallDistance >= densityThreshold.getValue();

        int targetSlot = useDensity ? findDensityMaceSlot() : findBreachMaceSlot();

        if (targetSlot == -1) {
            targetSlot = findAnyMaceSlot();
        }

        if (targetSlot != -1) {
            mc.player.getInventory().selectedSlot = targetSlot;
        }
    }

    private int findDensityMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE && hasDensityEnchantment(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findBreachMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE && hasBreachEnchantment(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findAnyMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasDensityEnchantment(ItemStack stack) {
        return stack.getEnchantments().getEnchantments().stream()
                .anyMatch(enchantment -> enchantment.getIdAsString().contains("density"));
    }

    private boolean hasBreachEnchantment(ItemStack stack) {
        return stack.getEnchantments().getEnchantments().stream()
                .anyMatch(enchantment -> enchantment.getIdAsString().contains("breach"));
    }

    private void switchToSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    private void resetFall() {
        isFalling = false;
        fallStartY = -1;
        slamExecuted = false;
        maceHit = false;
        slamTick = 0;
    }

    @Override
    public void onEnable() {
        savedSlot = -1;
        fallStartY = -1;
        isFalling = false;
        slamExecuted = false;
        maceHit = false;
        slamTick = 0;
        attackTimer.reset();
    }

    @Override
    public void onDisable() {
        if (savedSlot != -1) {
            switchToSlot(savedSlot);
        }
        resetAll();
    }

    private void resetAll() {
        savedSlot = -1;
        fallStartY = -1;
        isFalling = false;
        slamExecuted = false;
        maceHit = false;
        slamTick = 0;
        attackTimer.reset();
    }
}