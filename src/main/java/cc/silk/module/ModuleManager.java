package cc.silk.module;

import cc.silk.module.modules.client.Client;
import cc.silk.module.modules.client.ClientSettingsModule;
import cc.silk.module.modules.client.Debugger;
import cc.silk.module.modules.client.KeybindsModule;
import cc.silk.module.modules.client.NewClickGUIModule;
import cc.silk.module.modules.client.Secret;
import cc.silk.module.modules.combat.*;
import cc.silk.module.modules.misc.*;
import cc.silk.module.modules.player.*;
import cc.silk.module.modules.render.*;
import cc.silk.module.modules.movement.AutoFirework;
import cc.silk.module.modules.movement.AutoHeadHitter;
import cc.silk.module.modules.movement.KeepSprint;
import cc.silk.module.modules.movement.SnapTap;
import cc.silk.module.modules.movement.Sprint;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public final class ModuleManager {

    private final List<Module> modules = new java.util.ArrayList<>();

    public ModuleManager() {
        addModules();
    }

    public List<Module> getEnabledModules() {
        return modules.stream()
                .filter(Module::isEnabled)
                .toList();
    }

    public List<Module> getModulesInCategory(Category category) {
        return modules.stream()
                .filter(module -> module.getModuleCategory() == category)
                .toList();
    }

    public List<Module> getModulesByCategory(Category category) {
        return getModulesInCategory(category);
    }

    public <T extends Module> Optional<T> getModule(Class<T> moduleClass) {
        return modules.stream()
                .filter(module -> module.getClass().equals(moduleClass))
                .map(moduleClass::cast)
                .findFirst();
    }

    private void addModules() {
        // Combat
        add(
                new AutoMace(), new TotemHit(), new TriggerBot(), new Velocity(),
                new ShieldBreaker(), new ThrowPot(), new ElytraHotSwap(),
                new AntiMiss(), new WTap(), new STap(),
                new AimAssist(), new SwordHotSwap(), new AutoCrystal(), new SwordSwap(), new BreachSwap(),
                new KeyCrystal(), new KeyAnchor(), new KeyLava(), new AutoPot(), //new StunCob(),
                new AutoCart(), new CrystalOptimizer(), new Criticals(), new XbowCart(), new Hitboxes(),
                new SafeAnchor());
        // Movement
        add(new Sprint(), new AutoFirework(), new AutoHeadHitter(), new KeepSprint(), new SnapTap());

        // Player
        add(
                new AutoExtinguish(), new AutoTool(), new AutoWeb(), new AutoRefill(),
                new AutoDrain(), new AutoCrafter(), new FastPlace(), new FastEXP(),
                new TrapSave(), new PingSpoof(), new AutoDoubleHand(),
                new AutoMLG(), new FastMine(), new ReBuffNotifier(), new CoverUp());

        // Render
        add(
                new ContainerSlots(), new FullBright(), new Watermark(), new TargetHUD(),
                new SwingSpeed(),
                new Notifications(), new ArrowESP(), new OutlineESP(), new CustomOutlineESP(), new ESP2D(), new ESP3D(), new TargetESP(),
                new ArrayList(), new Trajectories(), new BlurTest(), new AspectRatio());

        // Misc
        add(
                new CartKey(), new HoverTotem(), new MiddleClickFriend(),
                new PearlKey(), new PearlCatch(), new WindChargeKey(), new Teams(), new FakePlayer(),
                new Friends());

        // Client
        add(new NewClickGUIModule(), new ClientSettingsModule(), new Client(), new Debugger(),
                new Secret(), new KeybindsModule());
    }

    private void add(Module... mods) {
        modules.addAll(Arrays.asList(mods));
    }
}
