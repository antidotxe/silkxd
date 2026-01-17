package cc.silk.utils;

import cc.silk.SilkClient;

import java.util.Timer;
import java.util.TimerTask;

public class AutoSaveManager {
    private static final AutoSaveManager INSTANCE = new AutoSaveManager();
    private static final long DEBOUNCE_DELAY = 1000; // 1 second delay
    
    private Timer timer;
    private TimerTask pendingTask;
    
    private AutoSaveManager() {
        this.timer = new Timer("AutoSave-Timer", true);
    }
    
    public static AutoSaveManager getInstance() {
        return INSTANCE;
    }
    
    public void scheduleSave() {
        try {
            if (!cc.silk.module.modules.client.ClientSettingsModule.isAutoSaveEnabled()) {
                return;
            }
            
            if (SilkClient.INSTANCE == null || SilkClient.INSTANCE.getProfileManager() == null) {
                return;
            }
            
            if (pendingTask != null) {
                pendingTask.cancel();
            }
            
            pendingTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        SilkClient.INSTANCE.getProfileManager().saveProfile("default", true);
                    } catch (Exception e) {
                    
                    }
                }
            };
            
            timer.schedule(pendingTask, DEBOUNCE_DELAY);
        } catch (Exception e) {
       
        }

    }
    
    public void shutdown() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
