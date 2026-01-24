package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.ItemUseEvent;
import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class XbowCart extends Module {

    private final BooleanSetting manualMode = new BooleanSetting("Manual", false);
    private final NumberSetting manualDelay = new NumberSetting("Manual Delay", 0, 10, 1, 1);
    private final ModeSetting firstAction = new ModeSetting("First", "Fire", "Fire", "Rail", "None");
    private final ModeSetting secondAction = new ModeSetting("Second", "Rail", "Fire", "Rail", "None");
    private final ModeSetting thirdAction = new ModeSetting("Third", "None", "Fire", "Rail", "None");
    private final NumberSetting delay = new NumberSetting("Delay", 0, 10, 2, 1);

    private int tickCounter = 0;
    private int actionIndex = 0;
    private boolean active = false;
    private final List<String> sequence = new ArrayList<>();
    
    private int manualStep = 0;
    private boolean shouldExecute = false;
    private boolean shouldSwitch = false;
    private int executeDelay = 0;

    public XbowCart() {
        super("Xbow cart", "Customizable cart placement module", -1, Category.COMBAT);
        this.addSettings(manualMode, manualDelay, firstAction, secondAction, thirdAction, delay);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }
        
        if (manualMode.getValue()) {
            manualStep = 0;
            shouldExecute = false;
            shouldSwitch = false;
            executeDelay = 0;
            active = true;
        } else {
            sequence.clear();
            if (!firstAction.isMode("None")) sequence.add(firstAction.getMode());
            if (!secondAction.isMode("None")) sequence.add(secondAction.getMode());
            if (!thirdAction.isMode("None")) sequence.add(thirdAction.getMode());
            
            active = true;
            tickCounter = 0;
            actionIndex = 0;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!active || isNull()) return;

        if (manualMode.getValue()) {
            handleManualModeTick();
        } else {
            handleAutoMode();
        }
    }

    @EventHandler
    private void onItemUse(ItemUseEvent event) {
        if (!active || isNull() || !manualMode.getValue()) return;
        
        ItemStack heldStack = mc.player.getMainHandStack();
        net.minecraft.item.Item currentItem = heldStack.isEmpty() ? null : heldStack.getItem();

        switch (manualStep) {
            case 0:
                if (isRailItem(currentItem)) {
                    manualStep = 1;
                    shouldSwitch = true;
                }
                break;

            case 1:
                if (currentItem == Items.TNT_MINECART) {
                    manualStep = 2;
                    shouldSwitch = true;
                } else if (isRailItem(currentItem)) {
                    manualStep = 1;
                    shouldSwitch = true;
                }
                break;

            case 2:
                if (currentItem == Items.FLINT_AND_STEEL) {
                    manualStep = 3;
                    shouldSwitch = true;
                } else if (isRailItem(currentItem)) {
                    manualStep = 1;
                    shouldSwitch = true;
                }
                break;

            case 3:
                if (isRailItem(currentItem)) {
                    manualStep = 1;
                    shouldSwitch = true;
                }
                break;
        }
    }

    private void handleManualModeTick() {
        if (executeDelay > 0) {
            executeDelay--;
            return;
        }
        
        if (shouldSwitch) {
            shouldSwitch = false;
            executeDelay = manualDelay.getValueInt();
            
            switch (manualStep) {
                case 1:
                    if (switchToItem(Items.TNT_MINECART)) {
                        shouldExecute = true;
                    } else {
                        manualStep = 0;
                    }
                    break;
                case 2:
                    if (switchToItem(Items.FLINT_AND_STEEL)) {
                        shouldExecute = true;
                    } else {
                        manualStep = 0;
                    }
                    break;
                case 3:
                    switchToItem(Items.CROSSBOW);
                    manualStep = 0;
                    break;
            }
            return;
        }
        
        if (shouldExecute) {
            shouldExecute = false;
            
            switch (manualStep) {
                case 1:
                    ((MinecraftClientAccessor) mc).invokeDoItemUse();
                    break;
                case 2:
                    break;
            }
        }
        
        if (manualStep == 2 && mc.player.getVehicle() == null) {
            ItemStack heldStack = mc.player.getMainHandStack();
            if (!heldStack.isEmpty() && heldStack.getItem() == Items.FLINT_AND_STEEL) {
                net.minecraft.util.hit.HitResult hit = mc.crosshairTarget;
                if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    net.minecraft.util.hit.BlockHitResult blockHit = (net.minecraft.util.hit.BlockHitResult) hit;
                    net.minecraft.block.BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
                    
                    if (!isRailBlock(state.getBlock())) {
                        ((MinecraftClientAccessor) mc).invokeDoItemUse();
                        manualStep = 3;
                        shouldSwitch = true;
                    }
                }
            }
        }
    }

    private boolean isRailBlock(net.minecraft.block.Block block) {
        return block == net.minecraft.block.Blocks.RAIL || 
               block == net.minecraft.block.Blocks.POWERED_RAIL || 
               block == net.minecraft.block.Blocks.DETECTOR_RAIL || 
               block == net.minecraft.block.Blocks.ACTIVATOR_RAIL;
    }

    private void handleAutoMode() {
        if (actionIndex < sequence.size()) {
            if (tickCounter == 0) {
                String currentAction = sequence.get(actionIndex);
                executeAction(currentAction);
            }
            
            tickCounter++;
            
            if (tickCounter > delay.getValueInt()) {
                tickCounter = 0;
                actionIndex++;
            }
        } else {
            switchToItem(Items.CROSSBOW);
            active = false;
            actionIndex = 0;
            tickCounter = 0;
        }
    }

    private void executeAction(String action) {
        if (action.equals("Fire")) {
            if (switchToItem(Items.FLINT_AND_STEEL)) {
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
            }
        } else if (action.equals("Rail")) {
            if (switchToItem(Items.RAIL) || switchToItem(Items.POWERED_RAIL) || 
                switchToItem(Items.DETECTOR_RAIL) || switchToItem(Items.ACTIVATOR_RAIL)) {
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
            }
            if (switchToItem(Items.TNT_MINECART)) {
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
            }
        }
    }

    private boolean isRailItem(net.minecraft.item.Item item) {
        if (item == null) return false;
        return item == Items.RAIL || item == Items.POWERED_RAIL || 
               item == Items.DETECTOR_RAIL || item == Items.ACTIVATOR_RAIL;
    }

    private boolean switchToItem(net.minecraft.item.Item item) {
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
