package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.ModeSetting;
import cc.silk.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class XbowCart extends Module {

    private final ModeSetting firstAction = new ModeSetting("First", "Fire", "Fire", "Rail", "None");
    private final ModeSetting secondAction = new ModeSetting("Second", "Rail", "Fire", "Rail", "None");
    private final ModeSetting thirdAction = new ModeSetting("Third", "None", "Fire", "Rail", "None");
    private final NumberSetting delay = new NumberSetting("Delay", 0, 10, 2, 1);

    private int tickCounter = 0;
    private int actionIndex = 0;
    private boolean active = false;
    private final List<String> sequence = new ArrayList<>();

    public XbowCart() {
        super("Xbow cart", "Customizable cart placement module", -1, Category.COMBAT);
        this.addSettings(firstAction, secondAction, thirdAction, delay);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }
        
        sequence.clear();
        if (!firstAction.isMode("None")) sequence.add(firstAction.getMode());
        if (!secondAction.isMode("None")) sequence.add(secondAction.getMode());
        if (!thirdAction.isMode("None")) sequence.add(thirdAction.getMode());
        
        active = true;
        tickCounter = 0;
        actionIndex = 0;
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (!active || isNull()) return;

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
            setEnabled(false);
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
