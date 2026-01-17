package cc.silk.module.setting;

import cc.silk.SilkClient;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class KeybindSetting extends Setting {

    private final boolean moduleKey;
    private final int originalKey;
    private int keyCode;
    private boolean listening;
    private boolean holdMode;

    public KeybindSetting(String name, int key, boolean moduleKey) {
        super(name);
        this.keyCode = key;
        this.originalKey = key;
        this.moduleKey = moduleKey;
        this.holdMode = false;
    }

    public void toggleListening() {
        this.listening = !listening;
    }

    public void toggleHoldMode() {
        this.holdMode = !holdMode;
        triggerAutoSave();
    }
    
    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
        triggerAutoSave();
    }
    
    private void triggerAutoSave() {
        cc.silk.utils.AutoSaveManager.getInstance().scheduleSave();
    }
}