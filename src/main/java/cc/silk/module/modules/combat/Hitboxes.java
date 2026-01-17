package cc.silk.module.modules.combat;

import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.RangeSetting;

public final class Hitboxes extends Module {
    private static Hitboxes INSTANCE;
    
    public static final RangeSetting expansion = new RangeSetting("Expansion", 0, 2, 0.3, 0.3, 0.01);

    public Hitboxes() {
        super("Hitboxes", "Expands player hitboxes for easier targeting (BLATANT)", -1, Category.COMBAT);
        addSettings(expansion);
        INSTANCE = this;
    }

    public static Hitboxes getInstance() {
        return INSTANCE;
    }

    public static float getExpansion() {
        if (INSTANCE != null && INSTANCE.isEnabled()) {
            return (float) expansion.getMinValue();
        }
        return 0f;
    }
}
