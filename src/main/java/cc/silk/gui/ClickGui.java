package cc.silk.gui;

import cc.silk.SilkClient;
import cc.silk.gui.animation.AnimationManager;
import cc.silk.gui.components.SettingsRenderer;
import cc.silk.gui.components.UIRenderer;
import cc.silk.gui.events.GuiEventHandler;
import cc.silk.gui.utils.SearchUtils;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.modules.client.ClickGUIModule;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.render.font.FontManager;
import cc.silk.utils.render.font.fonts.FontRenderer;
import cc.silk.gui.theme.Theme;
import cc.silk.gui.theme.ThemeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClickGui extends Screen {
    private static final Map<Module, Boolean> lastModuleExpanded = new HashMap<>();
    private static final int SIDEBAR_WIDTH = 180;
    private static final int CONTAINER_WIDTH = SIDEBAR_WIDTH + 600;
    private static final int COLOR_PICKER_PANEL_WIDTH = 250;
    private static final int HEADER_HEIGHT = 60;
    private static final int MODULE_HEIGHT = 35;
    private static final int PADDING = 18;
    private static final int SETTING_HEIGHT = 28;
    private static final int SEARCH_BAR_WIDTH = 200;
    private static final int SEARCH_BAR_HEIGHT = 25;
    private static Category lastSelectedCategory = Category.COMBAT;
    private static int lastScrollOffset = 0;
    private final Map<Module, Boolean> moduleExpanded = new HashMap<>();
    private final Map<NumberSetting, Boolean> sliderDragging = new HashMap<>();
    private final ColorPickerManager colorPickerManager;
    private final AnimationManager animationManager;
    private final GuiEventHandler eventHandler;
    private final FontRenderer titleFont;
    private final FontRenderer regularFont;
    private final FontRenderer smallFont;
    private final float typedTitleElapsed = 0f;
    private final long lastCursorBlink = 0;
    private final List<String> configs = new ArrayList<>();
    private String configName = "";
    private boolean configNameFocused = false;
    private String selectedConfig = "";

    public ClickGui() {
        super(Text.empty());

        FontManager fontManager = SilkClient.INSTANCE.getFontManager();
        this.titleFont = fontManager.getSize(24, FontManager.Type.Poppins);
        this.regularFont = fontManager.getSize(14, FontManager.Type.Inter);
        this.smallFont = fontManager.getSize(12, FontManager.Type.Inter);

        this.animationManager = new AnimationManager();
        this.eventHandler = new GuiEventHandler(moduleExpanded, sliderDragging, lastSelectedCategory);
        this.colorPickerManager = new ColorPickerManager(eventHandler);

        animationManager.initializeGuiAnimation();

        for (Module module : SilkClient.INSTANCE.getModuleManager().getModules()) {
            moduleExpanded.put(module, lastModuleExpanded.getOrDefault(module, false));
            animationManager.initializeModuleAnimations(module);
        }
        eventHandler.setScrollOffset(lastScrollOffset);

        loadConfigs();
    }

    @Override
    public void init() {
        super.init();
        animationManager.initializeGuiAnimation();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
        animationManager.updateAnimations(delta);
        animationManager.updateGuiAnimations(delta);

        if (animationManager.shouldCloseGui()) {
            lastSelectedCategory = eventHandler.getSelectedCategory();
            lastScrollOffset = eventHandler.getScrollOffset();
            lastModuleExpanded.clear();
            lastModuleExpanded.putAll(moduleExpanded);
            SilkClient.INSTANCE.getModuleManager().getModule(ClickGUIModule.class).get().setEnabled(false);
            super.close();
            return;
        }

        int screenWidth = width;
        int screenHeight = height;

        renderBackground(context, mouseX, mouseY, delta);

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        matrices.translate(centerX, centerY, 0);
        
        float guiScale = getGuiScaleMultiplier();
        float finalScale = animationManager.getScaleAnimation() * guiScale;
        
        int transformedMouseX = (int) ((mouseX - centerX) / finalScale + centerX);
        int transformedMouseY = (int) ((mouseY - centerY) / finalScale + centerY);
        
        matrices.scale(finalScale, finalScale, 1f);
        matrices.translate(-centerX, -centerY, 0);

        int containerX = (screenWidth - CONTAINER_WIDTH) / 2;
        int containerY = (screenHeight - 500) / 2;
        int containerWidth = CONTAINER_WIDTH;
        int containerHeight = 500;

    int alpha = (int) (animationManager.getGuiAnimation() * 240);
    Color containerColor = applyAlpha(theme.containerBg(), alpha);
    context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, containerColor.getRGB());

    renderSidebar(context, containerX, containerY, containerHeight, transformedMouseX, transformedMouseY);

        renderContent(context, containerX + SIDEBAR_WIDTH, containerY, CONTAINER_WIDTH - SIDEBAR_WIDTH, containerHeight, transformedMouseX, transformedMouseY);

        if (eventHandler.isAnyColorPickerExpanded()) {
            int colorPickerPanelX = containerX + containerWidth + 10;
            colorPickerManager.renderColorPickerPanel(context, colorPickerPanelX, containerY, COLOR_PICKER_PANEL_WIDTH, containerHeight, transformedMouseX, transformedMouseY);
        }

        renderHeader(context, containerX, containerY, containerWidth, transformedMouseX, transformedMouseY);

        matrices.pop();


    }


    private void renderHeader(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
        int headerAlpha = (int) (animationManager.getGuiAnimation() * 255);
        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
        context.fill(x, y, x + width, y + HEADER_HEIGHT, applyAlpha(theme.headerBg(), headerAlpha).getRGB());
        MatrixStack matrices = context.getMatrices();


        String fullTitle = "Silk";
        String title = fullTitle;
        int titleX = x + PADDING;
        int titleY = y + 15;
        int textAlpha = (int) (animationManager.getGuiAnimation() * 255);
        titleFont.drawString(matrices, title, titleX, titleY, new Color(255, 255, 255, textAlpha));
        int verX = titleX + (int) titleFont.getStringWidth(fullTitle) + 8;
        int verY = titleY + 6;
        smallFont.drawString(matrices, SilkClient.CLIENT_VERSION, verX, verY, new Color(180, 180, 200, textAlpha));

        renderSearchBar(context, x, y, width);
    }

    private void renderSearchBar(DrawContext context, int x, int y, int width) {
        MatrixStack matrices = context.getMatrices();

        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());

        int searchX = x + width - SEARCH_BAR_WIDTH - PADDING;
        int searchY = y + (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2;

        String displayText = eventHandler.getSearchQuery().isEmpty() ? "Search modules..." : eventHandler.getSearchQuery();
    Color textColor = eventHandler.getSearchQuery().isEmpty() ? applyAlpha(theme.muted(), 180) : theme.text();
        int textX = searchX + 8;
        int textY = searchY + (SEARCH_BAR_HEIGHT - 12) / 2 + 2;

        String clippedText = displayText;
        float maxTextWidth = SEARCH_BAR_WIDTH - 16;
        while (smallFont.getStringWidth(clippedText) > maxTextWidth && clippedText.length() > 0) {
            clippedText = clippedText.substring(0, clippedText.length() - 1);
        }
    context.fill(searchX, searchY, searchX + SEARCH_BAR_WIDTH, (searchY + SEARCH_BAR_HEIGHT) - 2,
        applyAlpha(theme.panelBg(), 220).getRGB());
    smallFont.drawString(matrices, clippedText, textX, textY - 4, textColor);

        if (eventHandler.isSearchFocused() && !eventHandler.getSearchQuery().isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastCursorBlink) % 1000 < 500) {
                int cursorX = textX + (int) smallFont.getStringWidth(clippedText);
                context.fill(cursorX, textY - 1, cursorX + 1, textY + 11, theme.text().getRGB());
            }
        }
    }

    private void renderSidebar(DrawContext context, int x, int y, int height, int mouseX, int mouseY) {
        int sidebarAlpha = (int) (animationManager.getGuiAnimation() * 255);
    Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
    Color sb = applyAlpha(theme.sidebarBg(), sidebarAlpha);
    context.fill(x, y + HEADER_HEIGHT, x + SIDEBAR_WIDTH, y + height - 12, sb.getRGB());
    context.fill(x + 12, y + height - 12, x + SIDEBAR_WIDTH, y + height, sb.getRGB());

        int sidebarX = x;
        int sidebarY = y + HEADER_HEIGHT;

        MatrixStack matrices = context.getMatrices();
        int categoryY = sidebarY + PADDING;

        for (Category category : Category.values()) {
            boolean isSelected = category == eventHandler.getSelectedCategory();
            boolean isHovered = mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH &&
                    mouseY >= categoryY && mouseY <= categoryY + 35;

            float targetAnimation = isSelected ? 1f : (isHovered ? 0.3f : 0f);
            float currentAnimation = animationManager.getCategoryAnimation(category);
            float newAnimation = MathHelper.lerp(0.15f, currentAnimation, targetAnimation);
            animationManager.setCategoryAnimation(category, newAnimation);
            Color textColor = isSelected ? Color.WHITE : new Color(190, 190, 190);
            regularFont.drawString(matrices, category.getName(), sidebarX + 20, categoryY + 13, textColor);

            categoryY += 45;
        }
    }

    private void renderContent(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
    Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
    int contentAlpha = (int) (animationManager.getGuiAnimation() * animationManager.getCategorySwitch() * 255);
    Color panel = applyAlpha(theme.panelBg(), contentAlpha);
    context.fill(x, y + HEADER_HEIGHT, x + width, y + height - 12, panel.getRGB());
    context.fill(x, y + height - 12, x + width - 12, y + height, panel.getRGB());

        context.enableScissor(x, y + HEADER_HEIGHT, x + width, y + height);

        if (eventHandler.getSelectedCategory() == Category.CONFIG) {
            renderConfigContent(context, x, y, width, height, mouseX, mouseY);
        } else {
            renderModuleContent(context, x, y, width, height, mouseX, mouseY);
        }

        context.disableScissor();
    }

    private void renderModuleContent(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());

        List<Module> allModules;
        if (eventHandler.getSearchQuery().isEmpty()) {
            allModules = SilkClient.INSTANCE.getModuleManager().getModulesInCategory(eventHandler.getSelectedCategory());
        } else {
            allModules = SilkClient.INSTANCE.getModuleManager().getModules();
        }
        List<Module> modules = filterModulesBySearch(allModules);

        int moduleY = y + HEADER_HEIGHT + PADDING - eventHandler.getScrollOffset();
        int totalContentHeight = PADDING;

        for (Module module : modules) {
            int moduleHeight = MODULE_HEIGHT + 5;
            if (moduleExpanded.get(module)) {
                moduleHeight += (int) (animationManager.getDropdownAnimation(module) * SettingsRenderer.getModuleSettingsHeight(module, SETTING_HEIGHT, eventHandler));
            }

            totalContentHeight += moduleHeight;

            if (moduleY + moduleHeight < y + HEADER_HEIGHT) {
                moduleY += moduleHeight;
                continue;
            }

            if (moduleY > y + height) break;

            boolean isHovered = mouseX >= x + PADDING && mouseX <= x + width - PADDING &&
                    mouseY >= moduleY && mouseY <= moduleY + MODULE_HEIGHT;
            boolean isEnabled = module.isEnabled();
            boolean hasSettings = module.getSettings() != null && !module.getSettings().isEmpty();

            float targetAnimation = isEnabled ? 1f : (isHovered ? 0.2f : 0f);
            float currentAnimation = animationManager.getModuleAnimation(module);
            float newAnimation = MathHelper.lerp(0.12f, currentAnimation, targetAnimation);
            animationManager.setModuleAnimation(module, newAnimation);

            float targetDropdown = moduleExpanded.get(module) ? 1f : 0f;
            float currentDropdown = animationManager.getDropdownAnimation(module);
            float newDropdown = MathHelper.lerp(0.15f, currentDropdown, targetDropdown);
            animationManager.setDropdownAnimation(module, newDropdown);

            Color bgColor = isHovered ? applyAlpha(theme.panelAltBg(), 220) : applyAlpha(theme.panelBg(), 140);
        context.fill(x + PADDING, moduleY, x + width - PADDING, moduleY + MODULE_HEIGHT, bgColor.getRGB());
        context.drawBorder(x + PADDING, moduleY, width - PADDING * 2, MODULE_HEIGHT, applyAlpha(theme.muted(), 120).getRGB());
            int indicatorAlpha = isEnabled ? (int) (newAnimation * 255) : 0;
            if (indicatorAlpha > 0) {
                Color indicator = applyAlpha(theme.accent(), indicatorAlpha);
                context.fill(x + PADDING, moduleY, x + PADDING + 4, moduleY + MODULE_HEIGHT, indicator.getRGB());
            }
            Color textColor = isEnabled ? theme.text() : theme.muted();
            regularFont.drawString(matrices, module.getName(), x + PADDING + 10, moduleY + 8, textColor);
            if (hasSettings) {
                UIRenderer.renderDropdownArrow(context, x + width - PADDING - 30, moduleY + MODULE_HEIGHT / 2,
                        moduleExpanded.get(module), theme.muted());
            }

            if (module.getDescription() != null && !module.getDescription().isEmpty()) {
                float descWidth = smallFont.getStringWidth(module.getDescription());
                float maxDescWidth = width - PADDING - 30 - 100;
                if (descWidth > maxDescWidth) {
                    String truncated = module.getDescription();
                    while (smallFont.getStringWidth(truncated + "...") > maxDescWidth && truncated.length() > 1) {
                        truncated = truncated.substring(0, truncated.length() - 1);
                    }
                    truncated += "...";
                    descWidth = smallFont.getStringWidth(truncated);
            smallFont.drawString(matrices, truncated,
                x + width - PADDING - descWidth - (hasSettings ? 40 : 10), moduleY + 10, theme.muted());
                } else {
            smallFont.drawString(matrices, module.getDescription(),
                x + width - PADDING - descWidth - (hasSettings ? 40 : 10), moduleY + 10, theme.muted());
                }
            }

            moduleY += MODULE_HEIGHT + 5;

            if (hasSettings && newDropdown > 0.05f) {
                int settingsHeight = renderModuleSettings(context, module, x, moduleY, width, newDropdown);
                moduleY += (int) (newDropdown * settingsHeight);
            }
        }

        totalContentHeight += PADDING;
        int visibleHeight = height - HEADER_HEIGHT;
        eventHandler.updateMaxScrollOffset(totalContentHeight, visibleHeight);
    }

    private void renderConfigContent(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        int startY = y + HEADER_HEIGHT + PADDING - eventHandler.getScrollOffset();
        int currentY = startY;

        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
    titleFont.drawString(matrices, "Config Manager", x + PADDING, currentY, theme.text());
        currentY += 50;

        context.fill(x + PADDING, currentY, x + width - PADDING, currentY + 30, applyAlpha(theme.panelBg(), 200).getRGB());
        String displayText = configName.isEmpty() ? "Enter config name..." : configName;
        Color textColor = configName.isEmpty() ? applyAlpha(theme.muted(), 180) : theme.text();
        regularFont.drawString(matrices, displayText, x + PADDING + 8, currentY + 8, textColor);

        if (configNameFocused && !configName.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastCursorBlink) % 1000 < 500) {
                int cursorX = x + PADDING + 8 + (int) regularFont.getStringWidth(configName);
                context.fill(cursorX, currentY + 6, cursorX + 1, currentY + 24, Color.WHITE.getRGB());
            }
        }
        currentY += 40;

    renderConfigButton(context, x + PADDING, currentY, 80, 25, "Save", theme.accent(), mouseX, mouseY);
    renderConfigButton(context, x + PADDING + 90, currentY, 80, 25, "Load", applyAlpha(theme.panelAltBg(), 200), mouseX, mouseY);
    renderConfigButton(context, x + PADDING + 180, currentY, 80, 25, "Delete", new Color(200, 80, 80), mouseX, mouseY);
        currentY += 40;

        regularFont.drawString(matrices, "Available Configs:", x + PADDING, currentY, new Color(180, 180, 180));
        currentY += 25;

        for (String config : configs) {
            boolean isHovered = mouseX >= x + PADDING && mouseX <= x + width - PADDING &&
                    mouseY >= currentY && mouseY <= currentY + 25;
            boolean isSelected = config.equals(selectedConfig);

        Color bgColor = isSelected ? applyAlpha(theme.accent(), 170) : (isHovered ? applyAlpha(theme.panelAltBg(), 160) : applyAlpha(theme.panelBg(), 120));
        context.fill(x + PADDING, currentY, x + width - PADDING, currentY + 25, bgColor.getRGB());

        Color itemTextColor = isSelected ? theme.text() : theme.muted();
        regularFont.drawString(matrices, config, x + PADDING + 8, currentY + 6, itemTextColor);

            currentY += 30;
        }

        int totalContentHeight = currentY - startY + PADDING;
        int visibleHeight = height - HEADER_HEIGHT;
        eventHandler.updateMaxScrollOffset(totalContentHeight, visibleHeight);
    }

    private void renderConfigButton(DrawContext context, int x, int y, int width, int height, String text, Color baseColor, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        Color buttonColor;
        if (isHovered) {
            int r = Math.max(0, Math.min(255, baseColor.getRed() + 20));
            int g = Math.max(0, Math.min(255, baseColor.getGreen() + 20));
            int b = Math.max(0, Math.min(255, baseColor.getBlue() + 20));
            int a = baseColor.getAlpha();
            buttonColor = new Color(r, g, b, a);
        } else {
            buttonColor = baseColor;
        }
        context.fill(x, y, x + width, y + height, buttonColor.getRGB());

        int textX = x + (width - (int) smallFont.getStringWidth(text)) / 2;
        int textY = y + (height - 12) / 2;
        Theme theme = ThemeManager.getTheme(ClickGUIModule.theme.getMode());
        smallFont.drawString(context.getMatrices(), text, textX, textY, theme.text());
    }

    private List<Module> filterModulesBySearch(List<Module> modules) {
        return SearchUtils.filterModulesBySearch(modules, eventHandler.getSearchQuery());
    }

    private int renderModuleSettings(DrawContext context, Module module, int x, int moduleY, int width, float animation) {
        return SettingsRenderer.renderModuleSettings(context, module, x, moduleY, width, animation, smallFont, eventHandler.getDropdownExpanded(), eventHandler);
    }

    private boolean handleColorPickerClicks(double mouseX, double mouseY, int button) {
        return colorPickerManager.handleColorPickerClicks(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button < 0 || button > 8) return false;

        float guiScale = getGuiScaleMultiplier();
        float finalScale = animationManager.getScaleAnimation() * guiScale;
        float centerX = width / 2f;
        float centerY = height / 2f;
        double transformedMouseX = (mouseX - centerX) / finalScale + centerX;
        double transformedMouseY = (mouseY - centerY) / finalScale + centerY;

        if (eventHandler.getSelectedCategory() == Category.CONFIG && handleConfigClick(transformedMouseX, transformedMouseY, button)) {
            return true;
        }

        if (handleColorPickerClicks(transformedMouseX, transformedMouseY, button)) {
            return true;
        }

        List<Module> allModules;
        if (eventHandler.getSearchQuery().isEmpty()) {
            allModules = SilkClient.INSTANCE.getModuleManager().getModulesInCategory(eventHandler.getSelectedCategory());
        } else {
            allModules = SilkClient.INSTANCE.getModuleManager().getModules();
        }
        List<Module> modules = filterModulesBySearch(allModules);

        return eventHandler.handleMouseClick(transformedMouseX, transformedMouseY, button, width, height, modules) ||
                super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float guiScale = getGuiScaleMultiplier();
        float finalScale = animationManager.getScaleAnimation() * guiScale;
        float centerX = width / 2f;
        float centerY = height / 2f;
        double transformedMouseX = (mouseX - centerX) / finalScale + centerX;
        double transformedMouseY = (mouseY - centerY) / finalScale + centerY;

        if (colorPickerManager.handleColorPickerDrag(transformedMouseX, transformedMouseY, button, deltaX, deltaY)) {
            return true;
        }

        return eventHandler.handleMouseDrag(transformedMouseX, transformedMouseY, button, deltaX, deltaY, width, height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float guiScale = getGuiScaleMultiplier();
        float finalScale = animationManager.getScaleAnimation() * guiScale;
        float centerX = width / 2f;
        float centerY = height / 2f;
        double transformedMouseX = (mouseX - centerX) / finalScale + centerX;
        double transformedMouseY = (mouseY - centerY) / finalScale + centerY;

        if (colorPickerManager.handleColorPickerRelease(transformedMouseX, transformedMouseY, button)) {
            return true;
        }

        return eventHandler.handleMouseRelease(transformedMouseX, transformedMouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float guiScale = getGuiScaleMultiplier();
        float finalScale = animationManager.getScaleAnimation() * guiScale;
        float centerX = width / 2f;
        float centerY = height / 2f;
        double transformedMouseX = (mouseX - centerX) / finalScale + centerX;
        double transformedMouseY = (mouseY - centerY) / finalScale + centerY;

        return eventHandler.handleMouseScroll(transformedMouseX, transformedMouseY, horizontalAmount, verticalAmount, height) ||
                super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (configNameFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                saveConfig();
                configNameFocused = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!configName.isEmpty()) {
                    configName = configName.substring(0, configName.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                configNameFocused = false;
                return true;
            }
        }

        if (colorPickerManager.handleColorPickerKeyPress(keyCode)) {
            return true;
        }
        if (eventHandler.handleKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            lastSelectedCategory = eventHandler.getSelectedCategory();
            lastScrollOffset = eventHandler.getScrollOffset();
            lastModuleExpanded.clear();
            lastModuleExpanded.putAll(moduleExpanded);
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (configNameFocused && chr >= 32 && chr < 127) {
            if (configName.length() < 20) {
                configName += chr;
            }
            return true;
        }
        if (colorPickerManager.handleColorPickerCharTyped(chr)) return true;
        return eventHandler.handleCharTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        animationManager.startClosingAnimation();
    }

    private void loadConfigs() {
        configs.clear();
        File profileDir = SilkClient.INSTANCE.getProfileManager().getProfileDir();
        if (profileDir.exists() && profileDir.isDirectory()) {
            File[] files = profileDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    configs.add(file.getName().replace(".json", ""));
                }
            }
        }
    }

    private static Color applyAlpha(Color base, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }

    private boolean handleConfigClick(double mouseX, double mouseY, int button) {
        int screenWidth = width;
        int screenHeight = height;
        int containerX = (screenWidth - CONTAINER_WIDTH) / 2;
        int containerY = (screenHeight - 500) / 2;
        int contentX = containerX + SIDEBAR_WIDTH;
        int contentY = containerY + HEADER_HEIGHT + PADDING - eventHandler.getScrollOffset();

        int inputY = contentY + 50;
        if (mouseY >= inputY && mouseY <= inputY + 30) {
            configNameFocused = true;
            return true;
        }

        int buttonY = inputY + 40;
        if (mouseY >= buttonY && mouseY <= buttonY + 25) {
            if (mouseX >= contentX + PADDING && mouseX <= contentX + PADDING + 80) {
                saveConfig();
                return true;
            } else if (mouseX >= contentX + PADDING + 90 && mouseX <= contentX + PADDING + 170) {
                loadConfig();
                return true;
            } else if (mouseX >= contentX + PADDING + 180 && mouseX <= contentX + PADDING + 260) {
                deleteConfig();
                return true;
            }
        }

        int listY = buttonY + 65;
        for (String config : configs) {
            if (mouseY >= listY && mouseY <= listY + 25) {
                selectedConfig = config;
                configName = config;
                return true;
            }
            listY += 30;
        }

        configNameFocused = false;
        return false;
    }

    private void saveConfig() {
        if (!configName.trim().isEmpty()) {
            SilkClient.INSTANCE.getProfileManager().saveProfile(configName.trim(), true);
            loadConfigs();
            selectedConfig = configName.trim();
        }
    }

    private void loadConfig() {
        if (!selectedConfig.isEmpty()) {
            SilkClient.INSTANCE.getProfileManager().loadProfile(selectedConfig);
        }
    }

    private void deleteConfig() {
        if (!selectedConfig.isEmpty()) {
            File configFile = new File(SilkClient.INSTANCE.getProfileManager().getProfileDir(), selectedConfig + ".json");
            if (configFile.exists() && configFile.delete()) {
                loadConfigs();
                selectedConfig = "";
                configName = "";
            }
        }
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
}