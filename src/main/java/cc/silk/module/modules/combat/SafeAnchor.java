package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.TickEvent;
import meteordevelopment.orbit.EventHandler;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class SafeAnchor extends Module {
    private final NumberSetting placeDelay = new NumberSetting("Place Delay", 0, 20, 2, 1);
    private int tickCounter = 0;
    private int step = 0; // 0 = place anchor, 1 = place glowstone, 2 = charge anchor
    private BlockPos anchorPos = null;

    public SafeAnchor() {
        super("Safe Anchor", "Places anchor and glowstone protection", -1, Category.COMBAT);
        this.addSettings(placeDelay);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }
        tickCounter = 0;
        step = 0;
        anchorPos = null;
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.player == null) {
            setEnabled(false);
            return;
        }

        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        switch (step) {
            case 0 -> { // Step 1: Place anchor
                if (switchToItem(Items.RESPAWN_ANCHOR)) {
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    tickCounter = placeDelay.getValueInt();
                    // Store anchor position
                    if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        anchorPos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
                    }
                    step = 1;
                } else {
                    setEnabled(false);
                }
            }
            case 1 -> { // Step 2: Place glowstone
                if (switchToItem(Items.GLOWSTONE)) {
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    tickCounter = placeDelay.getValueInt();
                    step = 2;
                } else {
                    setEnabled(false);
                }
            }
            case 2 -> { // Step 3: Charge and activate anchor
                if (switchToItem(Items.GLOWSTONE)) {
                    // Charge the anchor
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    tickCounter = placeDelay.getValueInt();
                    
                    // Then activate
                    if (switchToItem(Items.RESPAWN_ANCHOR)) {
                        ((MinecraftClientAccessor) mc).invokeDoItemUse();
                        setEnabled(false);
                    }
                } else {
                    setEnabled(false);
                }
            }
        }
    }
    
    private float getYawToFace(BlockPos pos) {
        if (mc.player == null) return 0;
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
    }
    
    private boolean switchToItem(net.minecraft.item.Item item) {
        if (mc.player == null) return false;

        // Check offhand first
        ItemStack offhand = mc.player.getOffHandStack();
        if (!offhand.isEmpty() && offhand.getItem() == item) {
            return true;
        }

        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                mc.player.getInventory().selectedSlot = i;
                return true;
            }
        }
        return false;
    }
}
