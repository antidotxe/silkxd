package cc.silk.gui.newgui;

import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.gui.newgui.components.CategoryPanel;
import cc.silk.gui.newgui.components.ConfigPanel;
import cc.silk.gui.FriendsScreen;
import cc.silk.gui.effects.SnowEffect;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import cc.silk.utils.render.shader.ShaderRenderer;
import cc.silk.utils.render.GuiGlowHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NewClickGUI extends Screen {
    private final List<CategoryPanel> panels = new ArrayList<>();
    private ConfigPanel configPanel;
    private static final int PANEL_WIDTH = 110;
    private static final int PANEL_SPACING = 5;

    private float animationProgress = 0f;
    private boolean closing = false;
    private long lastFrameTime = System.currentTimeMillis();
    
    private final SnowEffect snowEffect = new SnowEffect();

    private String searchQuery = "";
    private boolean searchFocused = false;
    private float searchExpandProgress = 0f;
    private float searchBarY = 0;
    private static final int SEARCH_BAR_WIDTH = 300;
    private static final int SEARCH_ICON_SIZE = 35;
    private static final java.awt.Color SEARCH_BG = new java.awt.Color(28, 28, 32, 255);
    private static final java.awt.Color SEARCH_TEXT = new java.awt.Color(240, 240, 245, 255);
    private static final java.awt.Color SEARCH_PLACEHOLDER = new java.awt.Color(130, 130, 140, 255);

    private static final int FRIENDS_BUTTON_SIZE = 35;
    private float friendsButtonX = 0;
    private float friendsButtonY = 0;
    private static int contactIconImage = -1;

    public NewClickGUI() {
        super(Text.literal("ClickGUI"));
        initPanels();
        loadContactIcon();
    }

    private void loadContactIcon() {
        if (contactIconImage == -1) {
            contactIconImage = NanoVGRenderer.loadImage("assets/silk/textures/icons/contact.png");
        }
    }

    private void initPanels() {
        NanoVGRenderer.init();

        int x = 20;
        int y = 20;

        for (Category category : Category.values()) {
            if (category == Category.CONFIG)
                continue;

            List<Module> modules = cc.silk.SilkClient.INSTANCE.getModuleManager().getModulesByCategory(category);
            if (!modules.isEmpty()) {
                panels.add(new CategoryPanel(category, x, y, PANEL_WIDTH));
                x += PANEL_WIDTH + PANEL_SPACING;
            }
        }

        configPanel = new ConfigPanel(x, y, PANEL_WIDTH);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastFrameTime) / 1000f;
        lastFrameTime = currentTime;

        if (closing) {
            animationProgress -= deltaTime * 4f;
            if (animationProgress <= 0f) {
                animationProgress = 0f;
                if (client != null) {
                    client.setScreen(null);
                }
                return;
            }
        } else {
            animationProgress += deltaTime * 4f;
            if (animationProgress > 1f) {
                animationProgress = 1f;
            }
        }

        float scale = easeOutCubic(animationProgress);
        float alpha = animationProgress;

        float targetExpand = (searchFocused || !searchQuery.isEmpty()) ? 1f : 0f;
        searchExpandProgress += (targetExpand - searchExpandProgress) * deltaTime * 8f;
        searchExpandProgress = Math.max(0f, Math.min(1f, searchExpandProgress));

        float guiScale = getGuiScaleMultiplier();
        int centerX = width / 2;
        int centerY = height / 2;
        int transformedMouseX = (int) ((mouseX - centerX) / (scale * guiScale) + centerX);
        int transformedMouseY = (int) ((mouseY - centerY) / (scale * guiScale) + centerY);

        for (CategoryPanel panel : panels) {
            panel.update(deltaTime);
            panel.setSearchQuery(searchQuery);
        }

        if (configPanel != null) {
            configPanel.update(deltaTime);
        }

        CategoryPanel draggedPanel = null;
        for (CategoryPanel panel : panels) {
            if (panel.isDragging()) {
                draggedPanel = panel;
                break;
            }
        }

        boolean configDragging = configPanel != null && configPanel.isDragging();

        if (cc.silk.module.modules.client.ClientSettingsModule.isPanelBlurEnabled()) {
            for (CategoryPanel panel : panels) {
                if (panel != draggedPanel) {
                    panel.renderBlur(context.getMatrices(), alpha, scale, centerX, centerY);
                }
            }
            if (configPanel != null && !configDragging) {
                configPanel.renderBlur(context.getMatrices(), alpha, scale, centerX, centerY);
            }
            if (draggedPanel != null) {
                draggedPanel.renderBlur(context.getMatrices(), alpha, scale, centerX, centerY);
            }
            if (configPanel != null && configDragging) {
                configPanel.renderBlur(context.getMatrices(), alpha, scale, centerX, centerY);
            }
        }

        NanoVGRenderer.beginFrame();

        if (cc.silk.module.modules.client.ClientSettingsModule.isSnowEffectEnabled()) {
            snowEffect.update(deltaTime, width, height);
            snowEffect.render(alpha);
        }

        NanoVGRenderer.save();
        NanoVGRenderer.translate(centerX, centerY);
        
        NanoVGRenderer.scale(scale * guiScale, scale * guiScale);
        NanoVGRenderer.translate(-centerX, -centerY);

        renderSearchBar(alpha);
        renderFriendsButton(alpha);

        for (CategoryPanel panel : panels) {
            if (panel != draggedPanel) {
                panel.render(transformedMouseX, transformedMouseY, alpha, scale, centerX, centerY);
            }
        }

        if (configPanel != null && !configDragging) {
            configPanel.render(transformedMouseX, transformedMouseY, alpha, scale, centerX, centerY);
        }

        if (draggedPanel != null) {
            draggedPanel.render(transformedMouseX, transformedMouseY, alpha, scale, centerX, centerY);
        }

        if (configPanel != null && configDragging) {
            configPanel.render(transformedMouseX, transformedMouseY, alpha, scale, centerX, centerY);
        }

        for (CategoryPanel panel : panels) {
            panel.renderSettingsPanel(transformedMouseX, transformedMouseY, alpha, width, height);
        }

        renderTooltips(transformedMouseX, transformedMouseY, alpha);

        NanoVGRenderer.restore();

        NanoVGRenderer.endFrame();

        renderPanelGlows(context, alpha, scale, centerX, centerY, draggedPanel, configDragging);
    }

    private void renderTooltips(int mouseX, int mouseY, float alpha) {
        for (CategoryPanel panel : panels) {
            String tooltipText = panel.getTooltipText();
            if (tooltipText != null && !tooltipText.isEmpty()) {
                float fontSize = 10f;
                float padding = 6f;
                float textWidth = NanoVGRenderer.getTextWidth(tooltipText, fontSize);
                float tooltipWidth = textWidth + padding * 2;
                float tooltipHeight = fontSize + padding * 2;

                float tooltipX = panel.getTooltipX(mouseX);
                float tooltipY = panel.getTooltipY(mouseY);

                if (tooltipX + tooltipWidth > width) {
                    tooltipX = mouseX - tooltipWidth - 10;
                }
                if (tooltipY + tooltipHeight > height) {
                    tooltipY = mouseY - tooltipHeight - 10;
                }

                int bgAlpha = (int) (230 * alpha);
                Color tooltipBg = new Color(28, 28, 32, bgAlpha);
                Color tooltipBorder = new Color(60, 60, 70, bgAlpha);
                Color tooltipTextColor = new Color(240, 240, 245, (int) (255 * alpha));

                MatrixStack matrices = new MatrixStack();
                ShaderRenderer.drawRect(matrices, tooltipX, tooltipY, tooltipWidth, tooltipHeight, 4f, tooltipBg);
                ShaderRenderer.drawOutline(matrices, tooltipX, tooltipY, tooltipWidth, tooltipHeight, 4f, tooltipBorder, 1f);
                NanoVGRenderer.drawText(tooltipText, tooltipX + padding, tooltipY + padding, fontSize,
                        tooltipTextColor);

                break;
            }
        }
    }

    private void renderFriendsButton(float alpha) {
        friendsButtonX = 20;
        friendsButtonY = height - FRIENDS_BUTTON_SIZE - 20;

        int bgAlpha = (int) (255 * alpha);
        Color bgColor = new Color(SEARCH_BG.getRed(), SEARCH_BG.getGreen(), SEARCH_BG.getBlue(), bgAlpha);

        Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
        Color borderColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                (int) (100 * alpha));

        float cornerRadius = 6f;
        MatrixStack matrices = new MatrixStack();
        ShaderRenderer.drawRect(matrices, friendsButtonX, friendsButtonY, FRIENDS_BUTTON_SIZE, FRIENDS_BUTTON_SIZE,
                cornerRadius, bgColor);
        ShaderRenderer.drawOutline(matrices, friendsButtonX, friendsButtonY, FRIENDS_BUTTON_SIZE, FRIENDS_BUTTON_SIZE,
                cornerRadius, borderColor, 1f);

        if (contactIconImage != -1) {
            float iconPadding = 5f;
            float iconSize = FRIENDS_BUTTON_SIZE - (iconPadding * 2);
            Color tint = new Color(255, 255, 255, bgAlpha);
            NanoVGRenderer.drawImage(contactIconImage, friendsButtonX + iconPadding, friendsButtonY + iconPadding,
                    iconSize, iconSize, tint);
        }
    }

    private void renderSearchBar(float alpha) {
        searchBarY = height - SEARCH_ICON_SIZE - 20;

        float currentWidth = SEARCH_ICON_SIZE
                + (SEARCH_BAR_WIDTH - SEARCH_ICON_SIZE) * easeOutCubic(searchExpandProgress);
        float searchBarX = (width - currentWidth) / 2f;

        int bgAlpha = (int) (255 * alpha);
        Color bgColor = new Color(SEARCH_BG.getRed(), SEARCH_BG.getGreen(), SEARCH_BG.getBlue(), bgAlpha);

        Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
        Color borderColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                searchFocused ? bgAlpha : (int) (100 * alpha));

        float cornerRadius = 6f;
        MatrixStack matrices = new MatrixStack();
        ShaderRenderer.drawRect(matrices, searchBarX, searchBarY, currentWidth, SEARCH_ICON_SIZE, cornerRadius, bgColor);

        float borderThickness = searchFocused ? 2f : 1f;
        ShaderRenderer.drawOutline(matrices, searchBarX, searchBarY, currentWidth, SEARCH_ICON_SIZE,
                cornerRadius, borderColor, borderThickness);

        float iconCenterX = searchBarX + SEARCH_ICON_SIZE / 2f;
        float iconCenterY = searchBarY + SEARCH_ICON_SIZE / 2f;
        Color iconColor = new Color(SEARCH_TEXT.getRed(), SEARCH_TEXT.getGreen(), SEARCH_TEXT.getBlue(), bgAlpha);

        float circleRadius = 5f;
        float circleX = iconCenterX - 1;
        float circleY = iconCenterY - 1;

        NanoVGRenderer.drawCircle(circleX, circleY, circleRadius + 0.75f, iconColor);
        Color bgCircle = new Color(SEARCH_BG.getRed(), SEARCH_BG.getGreen(), SEARCH_BG.getBlue(), bgAlpha);
        NanoVGRenderer.drawCircle(circleX, circleY, circleRadius - 0.75f, bgCircle);

        float handleLength = 5f;
        float handleAngle = (float) Math.toRadians(45);
        float handleStartX = circleX + circleRadius * (float) Math.cos(handleAngle);
        float handleStartY = circleY + circleRadius * (float) Math.sin(handleAngle);
        float handleEndX = handleStartX + handleLength * (float) Math.cos(handleAngle);
        float handleEndY = handleStartY + handleLength * (float) Math.sin(handleAngle);

        NanoVGRenderer.drawLine(handleStartX, handleStartY, handleEndX, handleEndY, 1.5f, iconColor);

        if (searchExpandProgress > 0.3f) {
            String displayText = searchQuery.isEmpty() ? "Search modules..." : searchQuery;
            Color textColor = searchQuery.isEmpty()
                    ? new Color(SEARCH_PLACEHOLDER.getRed(), SEARCH_PLACEHOLDER.getGreen(),
                            SEARCH_PLACEHOLDER.getBlue(),
                            (int) (bgAlpha * searchExpandProgress))
                    : new Color(SEARCH_TEXT.getRed(), SEARCH_TEXT.getGreen(), SEARCH_TEXT.getBlue(),
                            (int) (bgAlpha * searchExpandProgress));

            float fontSize = 12f;
            float textX = searchBarX + SEARCH_ICON_SIZE + 5;
            float textY = searchBarY + (SEARCH_ICON_SIZE - fontSize) / 2f;

            if (textX < searchBarX + currentWidth - 10) {
                NanoVGRenderer.drawText(displayText, textX, textY, fontSize, textColor);

                if (searchFocused && System.currentTimeMillis() % 1000 < 500) {
                    float cursorX = textX + NanoVGRenderer.getTextWidth(searchQuery, fontSize);
                    if (cursorX < searchBarX + currentWidth - 5) {
                        NanoVGRenderer.drawRect(cursorX, textY - 1, 1, fontSize + 2, textColor);
                    }
                }
            }
        }
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Override
    public void close() {
        for (CategoryPanel panel : panels) {
            panel.saveState();
        }
        closing = true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float guiScale = getGuiScaleMultiplier();
        float scale = easeOutCubic(animationProgress);
        int centerX = width / 2;
        int centerY = height / 2;
        double transformedMouseX = (mouseX - centerX) / (scale * guiScale) + centerX;
        double transformedMouseY = (mouseY - centerY) / (scale * guiScale) + centerY;

        if (button == 0 && transformedMouseX >= friendsButtonX && transformedMouseX <= friendsButtonX + FRIENDS_BUTTON_SIZE &&
                transformedMouseY >= friendsButtonY && transformedMouseY <= friendsButtonY + FRIENDS_BUTTON_SIZE) {
            if (client != null) {
                client.setScreen(new FriendsScreen());
            }
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.hasActiveSettingsPanel()) {
                if (panel.getSettingsPanel().mouseClicked(transformedMouseX, transformedMouseY, button)) {
                    searchFocused = false;
                    return true;
                }
            }
        }

        if (configPanel != null && configPanel.mouseClicked(transformedMouseX, transformedMouseY, button)) {
            searchFocused = false;
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.mouseClicked(transformedMouseX, transformedMouseY, button)) {
                searchFocused = false;
                return true;
            }
        }

        float currentWidth = SEARCH_ICON_SIZE
                + (SEARCH_BAR_WIDTH - SEARCH_ICON_SIZE) * easeOutCubic(searchExpandProgress);
        float searchBarX = (width - currentWidth) / 2f;
        if (transformedMouseX >= searchBarX && transformedMouseX <= searchBarX + currentWidth &&
                transformedMouseY >= searchBarY && transformedMouseY <= searchBarY + SEARCH_ICON_SIZE) {
            searchFocused = true;
            return true;
        }

        searchFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (configPanel != null && configPanel.charTyped(chr, modifiers)) {
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.hasActiveSettingsPanel()) {
                if (panel.getSettingsPanel().charTyped(chr, modifiers)) {
                    return true;
                }
            }
        }

        if (searchFocused) {
            searchQuery += chr;
            return true;
        }

        if (cc.silk.module.modules.client.ClientSettingsModule.isAutoFocusSearchEnabled()) {
            boolean anySettingsPanelActive = false;
            for (CategoryPanel panel : panels) {
                if (panel.hasActiveSettingsPanel()) {
                    anySettingsPanelActive = true;
                    break;
                }
            }

            if (!anySettingsPanelActive && Character.isLetterOrDigit(chr)) {
                searchFocused = true;
                searchQuery += chr;
                return true;
            }
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (configPanel != null && configPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (searchFocused) {
            if (keyCode == 259) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (keyCode == 257 || keyCode == 335) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float guiScale = getGuiScaleMultiplier();
        float scale = easeOutCubic(animationProgress);
        int centerX = width / 2;
        int centerY = height / 2;
        double transformedMouseX = (mouseX - centerX) / (scale * guiScale) + centerX;
        double transformedMouseY = (mouseY - centerY) / (scale * guiScale) + centerY;

        if (configPanel != null) {
            configPanel.mouseReleased(transformedMouseX, transformedMouseY, button);
        }

        for (CategoryPanel panel : panels) {
            panel.mouseReleased(transformedMouseX, transformedMouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float guiScale = getGuiScaleMultiplier();
        float scale = easeOutCubic(animationProgress);
        int centerX = width / 2;
        int centerY = height / 2;
        double transformedMouseX = (mouseX - centerX) / (scale * guiScale) + centerX;
        double transformedMouseY = (mouseY - centerY) / (scale * guiScale) + centerY;

        if (configPanel != null && configPanel.mouseDragged(transformedMouseX, transformedMouseY, button, deltaX, deltaY)) {
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.mouseDragged(transformedMouseX, transformedMouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float guiScale = getGuiScaleMultiplier();
        float scale = easeOutCubic(animationProgress);
        int centerX = width / 2;
        int centerY = height / 2;
        double transformedMouseX = (mouseX - centerX) / (scale * guiScale) + centerX;
        double transformedMouseY = (mouseY - centerY) / (scale * guiScale) + centerY;

        if (configPanel != null && configPanel.mouseScrolled(transformedMouseX, transformedMouseY, verticalAmount)) {
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.mouseScrolled(transformedMouseX, transformedMouseY, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderPanelGlows(DrawContext context, float alpha, float scale, int centerX, int centerY,
            CategoryPanel draggedPanel, boolean configDragging) {
        if (!cc.silk.module.modules.client.ClientSettingsModule.isGuiGlowEnabled()) {
            return;
        }

        for (CategoryPanel panel : panels) {
            if (panel != draggedPanel) {
                panel.renderGlow(context, alpha, scale, centerX, centerY);
            }
        }

        if (configPanel != null && !configDragging) {
            configPanel.renderGlow(context, alpha, scale, centerX, centerY);
        }

        if (draggedPanel != null) {
            draggedPanel.renderGlow(context, alpha, scale, centerX, centerY);
        }

        if (configPanel != null && configDragging) {
            configPanel.renderGlow(context, alpha, scale, centerX, centerY);
        }

        for (CategoryPanel panel : panels) {
            panel.renderSettingsPanelGlow(context, alpha);
        }

        if (searchFocused) {
            float currentWidth = SEARCH_ICON_SIZE
                    + (SEARCH_BAR_WIDTH - SEARCH_ICON_SIZE) * easeOutCubic(searchExpandProgress);
            float searchBarX = (width - currentWidth) / 2f;
            float transformedX = (searchBarX - centerX) * scale + centerX;
            float transformedY = (searchBarY - centerY) * scale + centerY;
            float transformedWidth = currentWidth * scale;
            float transformedHeight = SEARCH_ICON_SIZE * scale;
            GuiGlowHelper.drawGuiGlow(context, transformedX, transformedY, transformedWidth, transformedHeight,
                    6f * scale);
        }

        float transformedFriendsX = (friendsButtonX - centerX) * scale + centerX;
        float transformedFriendsY = (friendsButtonY - centerY) * scale + centerY;
        float transformedFriendsSize = FRIENDS_BUTTON_SIZE * scale;
        GuiGlowHelper.drawGuiGlow(context, transformedFriendsX, transformedFriendsY, transformedFriendsSize,
                transformedFriendsSize, 6f * scale);
    }

    private float getGuiScaleMultiplier() {
        int scaleValue = cc.silk.module.modules.client.ClientSettingsModule.getGuiScale();
        switch (scaleValue) {
            case 0: return 0.75f;
            case 1: return 1.0f;
            case 2: return 1.25f;
            case 3: return 1.5f;
            default: return 1.0f;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (cc.silk.module.modules.client.ClientSettingsModule.isGuiBlurEnabled()) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }
}
