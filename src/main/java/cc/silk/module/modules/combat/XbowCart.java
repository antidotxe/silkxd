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
    private final NumberSetting manualDelay = new NumberSetting("Manual Delay (ms)", 0, 200, 50, 10);
    private final ModeSetting firstAction = new ModeSetting("First", "Fire", "Fire", "Rail", "None");
    private final ModeSetting secondAction = new ModeSetting("Second", "Rail", "Fire", "Rail", "None");
    private final ModeSetting thirdAction = new ModeSetting("Third", "None", "Fire", "Rail", "None");
    private final NumberSetting delay = new NumberSetting("Delay", 0, 10, 2, 1);

    private int tickCounter = 0;
    private int actionIndex = 0;
    private boolean active = false;
    private final List<String> sequence = new ArrayList<>();
    
    private int manualStep = 0;
    private long switchTime = 0;

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
            switchTime = 0;
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
        
        long delayMs = manualDelay.getValueInt();

        switch (manualStep) {
            case 0:
                if (isRailItem(currentItem)) {
                    manualStep = 1;
                    switchTime = System.currentTimeMillis() + delayMs;
                }
                break;

            case 1:
                if (currentItem == Items.TNT_MINECART) {
                    manualStep = 2;
                    switchTime = System.currentTimeMillis() + delayMs;
                } else if (isRailItem(currentItem)) {
                    manualStep = 1;
                    switchTime = System.currentTimeMillis() + delayMs;
                }
                break;

            case 2:
                if (currentItem == Items.FLINT_AND_STEEL) {
                    manualStep = 3;
                    switchTime = System.currentTimeMillis() + delayMs;
                } else if (isRailItem(currentItem)) {
                    manualStep = 1;
                    switchTime = System.currentTimeMillis() + delayMs;
                }
                break;

            case 3:
                if (isRailItem(currentItem)) {
                    manualStep = 1;
                    switchTime = System.currentTimeMillis() + delayMs;
                }
                break;
        }
    }

    private void handleManualModeTick() {
        if (switchTime > 0 && System.currentTimeMillis() >= switchTime) {
            switchTime = 0;
            
            switch (manualStep) {
                case 1:
                    if (!switchToItem(Items.TNT_MINECART)) {
                        manualStep = 0;
                    }
                    break;
                case 2:
                    if (!switchToItem(Items.FLINT_AND_STEEL)) {
                        manualStep = 0;
                    }
                    break;
                case 3:
                    switchToItem(Items.CROSSBOW);
                    manualStep = 0;
                    break;
            }
        }
        
        if (switchTime > 0 && System.currentTimeMillis() > switchTime + 1000) {
            manualStep = 0;
            switchTime = 0;
        }
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
