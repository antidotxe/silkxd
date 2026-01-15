package cc.silk.module.modules.render;

import cc.silk.event.impl.player.TickEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.ColorSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.friend.FriendManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;

import java.awt.*;

public class OutlineESP extends Module {

    private static OutlineESP instance;

    private final BooleanSetting showSelf = new BooleanSetting("Show Self", false);
    private final BooleanSetting teamCheck = new BooleanSetting("Team Check", false);
    private final BooleanSetting showPassives = new BooleanSetting("Show Passives", false);
    private final BooleanSetting showHostiles = new BooleanSetting("Show Hostiles", false);
    private final NumberSetting range = new NumberSetting("Range", 10, 200, 100, 5);
    private final ColorSetting playerColor = new ColorSetting("Player Color", new Color(255, 255, 255));
    private final ColorSetting passiveColor = new ColorSetting("Passive Color", new Color(0, 255, 0));
    private final ColorSetting hostileColor = new ColorSetting("Hostile Color", new Color(255, 0, 0));

    private final java.util.Set<Integer> handledEntities = new java.util.HashSet<>();

    public OutlineESP() {
        super("Outline ESP", "Uses Minecraft's glowing effect for entity outlines", Category.RENDER);
        instance = this;
        addSettings(showSelf, teamCheck, showPassives, showHostiles, range, playerColor, passiveColor, hostileColor);
    }

    public static OutlineESP getInstance() {
        return instance;
    }

    public boolean shouldEntityGlow(Entity entity) {
        if (!isEnabled() || isNull())
            return false;
        return shouldRender(entity);
    }

    public boolean wasHandledByModule(Entity entity) {
        return handledEntities.contains(entity.getId());
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.world == null)
            return;

        try {
            for (Entity entity : mc.world.getEntities()) {
                if (shouldRender(entity)) {
                    applyColor(entity);
                } else {
                    removeGlow(entity);
                }
            }
        } catch (Exception e) {
        }
    }

    private void removeGlow(Entity entity) {
        if (!handledEntities.contains(entity.getId())) {
            return;
        }

        if (mc.world == null || mc.world.getScoreboard() == null) {
            return;
        }

        handledEntities.remove(entity.getId());

        try {
            String teamName = "outlineESP_" + entity.getId();
            Team team = mc.world.getScoreboard().getTeam(teamName);

            if (team != null) {
                mc.world.getScoreboard().removeScoreHolderFromTeam(entity.getNameForScoreboard(), team);
                mc.world.getScoreboard().removeTeam(team);
            }

            Team currentTeam = entity.getScoreboardTeam();
            if (currentTeam != null && currentTeam.getName().startsWith("outlineESP_")) {
                mc.world.getScoreboard().removeScoreHolderFromTeam(entity.getNameForScoreboard(), currentTeam);
            }
        } catch (Exception e) {
        }
    }

    private boolean shouldRender(Entity entity) {
        if (mc.player.distanceTo(entity) > range.getValue())
            return false;

        if (entity instanceof PlayerEntity player) {
            if (player == mc.player && !showSelf.getValue())
                return false;
            if (teamCheck.getValue() && isTeammate(player))
                return false;
            return true;
        }

        if (entity instanceof PassiveEntity) {
            return showPassives.getValue();
        }

        if (entity instanceof HostileEntity) {
            return showHostiles.getValue();
        }

        return false;
    }

    private boolean isTeammate(PlayerEntity player) {
        if (mc.player.getScoreboardTeam() == null || player.getScoreboardTeam() == null) {
            return false;
        }
        return mc.player.getScoreboardTeam().equals(player.getScoreboardTeam());
    }

    private void applyColor(Entity entity) {
        if (mc.world == null || mc.world.getScoreboard() == null) {
            return;
        }

        handledEntities.add(entity.getId());

        try {
            String teamName = "outlineESP_" + entity.getId();
            Team existingTeam = entity.getScoreboardTeam();

            if (existingTeam != null && !existingTeam.getName().startsWith("outlineESP_")) {
                mc.world.getScoreboard().removeScoreHolderFromTeam(entity.getNameForScoreboard(), existingTeam);
            }

            Team team = mc.world.getScoreboard().getTeam(teamName);
            if (team == null) {
                team = mc.world.getScoreboard().addTeam(teamName);
            }

            if (entity.getScoreboardTeam() != team) {
                mc.world.getScoreboard().addScoreHolderToTeam(entity.getNameForScoreboard(), team);
            }

            Color color = getColorForEntity(entity);
            team.setColor(getClosestMinecraftColor(color));
        } catch (Exception e) {
        }
    }

    private Color getColorForEntity(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (FriendManager.isFriend(player.getUuid())) {
                return new Color(128, 0, 128);
            }
            return playerColor.getValue();
        }

        if (entity instanceof PassiveEntity) {
            return passiveColor.getValue();
        }

        if (entity instanceof HostileEntity) {
            return hostileColor.getValue();
        }

        return playerColor.getValue();
    }

    private net.minecraft.util.Formatting getClosestMinecraftColor(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        if (r > 200 && g < 100 && b < 100)
            return net.minecraft.util.Formatting.RED;
        if (r < 100 && g > 200 && b < 100)
            return net.minecraft.util.Formatting.GREEN;
        if (r < 100 && g < 100 && b > 200)
            return net.minecraft.util.Formatting.BLUE;
        if (r > 200 && g > 200 && b < 100)
            return net.minecraft.util.Formatting.YELLOW;
        if (r > 200 && g < 100 && b > 200)
            return net.minecraft.util.Formatting.LIGHT_PURPLE;
        if (r < 100 && g > 200 && b > 200)
            return net.minecraft.util.Formatting.AQUA;
        if (r > 200 && g > 200 && b > 200)
            return net.minecraft.util.Formatting.WHITE;
        if (r < 100 && g < 100 && b < 100)
            return net.minecraft.util.Formatting.DARK_GRAY;

        return net.minecraft.util.Formatting.WHITE;
    }

    @Override
    public void onDisable() {
        if (!isNull() && mc.world != null && mc.world.getScoreboard() != null) {
            try {
                java.util.Set<Integer> entitiesToRemove = new java.util.HashSet<>(handledEntities);

                for (Entity entity : mc.world.getEntities()) {
                    if (entitiesToRemove.contains(entity.getId())) {
                        removeGlow(entity);
                    }
                }

                java.util.List<Team> teamsToRemove = new java.util.ArrayList<>();
                for (Team team : mc.world.getScoreboard().getTeams()) {
                    if (team.getName().startsWith("outlineESP_")) {
                        teamsToRemove.add(team);
                    }
                }

                for (Team team : teamsToRemove) {
                    mc.world.getScoreboard().removeTeam(team);
                }

                handledEntities.clear();
            } catch (Exception e) {
                handledEntities.clear();
            }
        }
        super.onDisable();
    }
}
