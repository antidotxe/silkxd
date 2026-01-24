package cc.silk.gui.newgui.components;

import cc.silk.gui.newgui.GuiConstants;
import cc.silk.module.Module;
import cc.silk.utils.render.nanovg.NanoVGRenderer;

import java.awt.*;

public class ModuleButton {
    private final Module module;
    private final float relativeX, relativeY;
    private final int width, height;

    private float searchAlpha = 1f;
    private float targetSearchAlpha = 1f;
    private boolean matchesSearch = true;
    private float hoverAlpha = 0f;
    private float targetHoverAlpha = 0f;
    private String searchQuery = "";

    public ModuleButton(Module module, float relativeX, float relativeY, int width, int height) {
        this.module = module;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.width = width;
        this.height = height;
    }

    public void updateSearch(String query, float deltaTime) {
        this.searchQuery = query;

        if (query.isEmpty()) {
            matchesSearch = true;
            targetSearchAlpha = 1f;
        } else {
            matchesSearch = module.getName().toLowerCase().contains(query);
            targetSearchAlpha = matchesSearch ? 1f : 0f;
        }

        float speed = 8f;
        if (searchAlpha < targetSearchAlpha) {
            searchAlpha += deltaTime * speed;
            if (searchAlpha > targetSearchAlpha)
                searchAlpha = targetSearchAlpha;
        } else if (searchAlpha > targetSearchAlpha) {
            searchAlpha -= deltaTime * speed;
            if (searchAlpha < targetSearchAlpha)
                searchAlpha = targetSearchAlpha;
        }

        float hoverSpeed = 12f;
        if (hoverAlpha < targetHoverAlpha) {
            hoverAlpha += deltaTime * hoverSpeed;
            if (hoverAlpha > targetHoverAlpha)
                hoverAlpha = targetHoverAlpha;
        } else if (hoverAlpha > targetHoverAlpha) {
            hoverAlpha -= deltaTime * hoverSpeed;
            if (hoverAlpha < targetHoverAlpha)
                hoverAlpha = targetHoverAlpha;
        }
    }

    public void render(float panelX, float panelY, int mouseX, int mouseY, float alpha) {
        render(panelX, panelY, mouseX, mouseY, alpha, false);
    }

    public void render(float panelX, float panelY, int mouseX, int mouseY, float alpha, boolean isLastVisible) {
        if (searchAlpha <= 0.01f)
            return;

        float combinedAlpha = alpha * searchAlpha;

        float x = panelX + relativeX;
        float y = panelY + relativeY;
        float buttonWidth = width;
        float buttonHeight = height;

        boolean hovered = isMouseOver(x, y, buttonWidth, buttonHeight, mouseX, mouseY);
        targetHoverAlpha = hovered ? 1f : 0f;

        Color accentColor = cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();

        float cornerRadius = isLastVisible ? 4f : 0f;

        if (module.isEnabled()) {
            Color enabledBg = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                    (int) (255 * combinedAlpha));
            if (isLastVisible) {
                NanoVGRenderer.drawRoundedRectVarying(x, y, buttonWidth, buttonHeight, 0f, 0f, cornerRadius, cornerRadius, enabledBg);
            } else {
                NanoVGRenderer.drawRect(x, y, buttonWidth, buttonHeight, enabledBg);
            }
            
            if (hoverAlpha > 0.01f) {
                Color hoverOverlay = new Color(255, 255, 255, (int) (30 * hoverAlpha * combinedAlpha));
                if (isLastVisible) {
                    NanoVGRenderer.drawRoundedRectVarying(x, y, buttonWidth, buttonHeight, 0f, 0f, cornerRadius, cornerRadius, hoverOverlay);
                } else {
                    NanoVGRenderer.drawRect(x, y, buttonWidth, buttonHeight, hoverOverlay);
                }
            }
        } else {
            if (hoverAlpha > 0.01f) {
                float easedHover = easeOutCubic(hoverAlpha);
                
                Color hoverBg = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        (int) (80 * easedHover * combinedAlpha));
                if (isLastVisible) {
                    NanoVGRenderer.drawRoundedRectVarying(x, y, buttonWidth, buttonHeight, 0f, 0f, cornerRadius, cornerRadius, hoverBg);
                } else {
                    NanoVGRenderer.drawRect(x, y, buttonWidth, buttonHeight, hoverBg);
                }
                
                Color glowColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                        (int) (40 * easedHover * combinedAlpha));
                NanoVGRenderer.drawRect(x, y, 2, buttonHeight, glowColor);
            }
        }

        if (matchesSearch && searchAlpha > 0.9f && !searchQuery.isEmpty()) {
            Color highlightBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(),
                    (int) (150 * combinedAlpha));
            NanoVGRenderer.drawRect(x, y, 2, buttonHeight, highlightBorder);
        }

        Color textColor;
        if (module.isEnabled()) {
            textColor = new Color(255, 255, 255, (int) (255 * combinedAlpha));
        } else if (hoverAlpha > 0.01f) {
            float easedHover = easeOutCubic(hoverAlpha);
            int baseAlpha = 130;
            int hoverBoost = (int) (125 * easedHover);
            textColor = new Color(200, 200, 210, (int) ((baseAlpha + hoverBoost) * combinedAlpha));
        } else {
            textColor = new Color(130, 130, 140, (int) (255 * combinedAlpha));
        }

        float fontSize = 9f;
        float textY = Math.round(y + (buttonHeight - fontSize) / 2f);
        float textX = Math.round(x + GuiConstants.PADDING);
        NanoVGRenderer.drawText(module.getName(), textX, textY, fontSize, textColor);

        if (module.getKey() != 0 && module.getKey() != -1) {
            String keyName = getKeyDisplayName(module.getKey());
            if (keyName != null && !keyName.isEmpty()) {
                keyName = "[" + keyName + "]";
                float keybindFontSize = 7f;
                float keybindWidth = NanoVGRenderer.getTextWidth(keyName, keybindFontSize);
                float keybindX = Math.round(x + buttonWidth - keybindWidth - GuiConstants.PADDING);
                float keybindY = Math.round(y + (buttonHeight - keybindFontSize) / 2f);

                Color keybindColor = new Color(
                        textColor.getRed(),
                        textColor.getGreen(),
                        textColor.getBlue(),
                        (int) (textColor.getAlpha() * 0.6f));
                NanoVGRenderer.drawText(keyName, keybindX, keybindY, keybindFontSize, keybindColor);
            }
        }
    }
    
    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    public boolean mouseClicked(float panelX, float panelY, double mouseX, double mouseY, int button) {
        if (searchAlpha <= 0.01f)
            return false;

        float x = panelX + relativeX;
        float y = panelY + relativeY;
        float buttonWidth = width;
        float buttonHeight = height;

        if (isMouseOver(x, y, buttonWidth, buttonHeight, mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
                return true;
            } else return button == 1;
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public float getAbsoluteY(float panelY) {
        return panelY + relativeY;
    }

    public float getSearchAlpha() {
        return searchAlpha;
    }

    private boolean isMouseOver(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String getKeyDisplayName(int key) {
        if (key == 0 || key == -1) {
            return null;
        }

        if (key <= -100) {
            int button = -100 - key;
            return switch (button) {
                case 0 -> "M1";
                case 1 -> "M2";
                case 2 -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                default -> "M" + (button + 1);
            };
        }

        if (key >= 0 && key <= 7) {
            return switch (key) {
                case 0 -> "M1";
                case 1 -> "M2";
                case 2 -> "M3";
                case 3 -> "M4";
                case 4 -> "M5";
                case 5 -> "M6";
                case 6 -> "M7";
                case 7 -> "M8";
                default -> "M" + (key + 1);
            };
        }

        String glfwName = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase();
        }

        return switch (key) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BKSP";
            case 260 -> "INS";
            case 261 -> "DEL";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 280 -> "CAPS";
            case 290 -> "F1";
            case 291 -> "F2";
            case 292 -> "F3";
            case 293 -> "F4";
            case 294 -> "F5";
            case 295 -> "F6";
            case 296 -> "F7";
            case 297 -> "F8";
            case 298 -> "F9";
            case 299 -> "F10";
            case 300 -> "F11";
            case 301 -> "F12";
            case 340 -> "LSHIFT";
            case 341 -> "LCTRL";
            case 342 -> "LALT";
            case 344 -> "RSHIFT";
            case 345 -> "RCTRL";
            case 346 -> "RALT";
            default -> null;
        };
    }
}
