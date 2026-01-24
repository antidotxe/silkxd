package cc.silk.module.modules.render;

import cc.silk.event.impl.render.Render2DEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.*;
import cc.silk.utils.render.DraggableComponent;
import cc.silk.utils.render.nanovg.NanoVGRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Watermark extends Module {
    private static final float PADDING = 6f;
    private static final float TEXT_SIZE = 11f;
    private static final float ICON_SIZE = 12f;
    private static final float BOX_GAP = 6f;
    private static final float ICON_TEXT_GAP = 4f;
    private static final float ROW_GAP = 6f;
    private static final float CORNER_RADIUS = 4f;
    private static final float SNAP_DISTANCE = 5f;
    private static final Color TEXT_COLOR = new Color(200, 200, 200);
    private static final Color ICON_COLOR = new Color(255, 255, 255, 255);
    private static final Color BACKGROUND_BASE = new Color(20, 20, 25);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private final StringSetting text;
    private final NumberSetting transparency;
    private final ModeSetting colorMode;
    private final ColorSetting customColor;
    
    private final BooleanSetting showTitle;
    private final BooleanSetting showUsername;
    private final BooleanSetting showFps;
    private final BooleanSetting showTime;
    private final BooleanSetting showCoords;
    private final BooleanSetting showPing;

    private DraggableComponent draggable;
    private boolean needsInitialCenter = true;
    
    private int fpsIcon = -1;
    private int userIcon = -1;
    private int clockIcon = -1;
    private int pingIcon = -1;

    public Watermark() {
        super("Watermark", "Displays client watermark", -1, Category.RENDER);
        
        this.text = new StringSetting("Text", "Silk");
        this.transparency = new NumberSetting("Transparency", 0, 255, 200, 1);
        this.colorMode = new ModeSetting("Color Mode", "Theme", "Theme", "Custom");
        this.customColor = new ColorSetting("Custom Color", new Color(255, 255, 255));
        
        this.showTitle = new BooleanSetting("Show Title", true);
        this.showUsername = new BooleanSetting("Show Username", true);
        this.showFps = new BooleanSetting("Show FPS", true);
        this.showTime = new BooleanSetting("Show Time", true);
        this.showCoords = new BooleanSetting("Show Coords", true);
        this.showPing = new BooleanSetting("Show Ping", true);
        
        addSettings(text, transparency, colorMode, customColor, 
                   showTitle, showUsername, showFps, showTime, showCoords, showPing);
    }

    @Override
    public void onEnable() {
        loadIcons();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!shouldRender()) {
            return;
        }

        loadIcons();
        initializeDraggable();

        boolean isInChat = mc.currentScreen instanceof ChatScreen;
        if (mc.currentScreen != null && !isInChat) {
            return;
        }

        render(isInChat);
    }

    private boolean shouldRender() {
        return mc.player != null && mc.world != null;
    }

    private void initializeDraggable() {
        if (draggable == null) {
            int screenWidth = mc.getWindow().getScaledWidth();
            draggable = new DraggableComponent(screenWidth / 2f, 10, 200, 20);
            needsInitialCenter = true;
        }
    }

    private void loadIcons() {
        if (fpsIcon == -1) {
            fpsIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/fps.png");
        }
        if (userIcon == -1) {
            userIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/user.png");
        }
        if (clockIcon == -1) {
            clockIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/clock.png");
        }
        if (pingIcon == -1) {
            pingIcon = NanoVGRenderer.loadImage("assets/silk/textures/icons/ping.png");
        }
    }

    private void render(boolean isInChat) {
        WatermarkData data = new WatermarkData();
        List<Box> boxes = createBoxes(data);
        LayoutMetrics metrics = new LayoutMetrics(boxes);
        
        updateDraggableSize(metrics);
        handleDragging(isInChat, metrics.totalWidth);
        
        renderBoxes(boxes);
    }

    private List<Box> createBoxes(WatermarkData data) {
        List<Box> boxes = new ArrayList<>();
        Color accentColor = getAccentColor();
        
        if (showTitle.getValue()) {
            boxes.add(new Box(data.title, -1, accentColor, true));
        }
        if (showUsername.getValue()) {
            boxes.add(new Box(data.username, userIcon, TEXT_COLOR, false));
        }
        if (showFps.getValue()) {
            boxes.add(new Box(data.fpsText, fpsIcon, TEXT_COLOR, false));
        }
        if (showTime.getValue()) {
            boxes.add(new Box(data.time, clockIcon, TEXT_COLOR, false));
        }
        if (showCoords.getValue()) {
            boxes.add(new Box(data.coords, -1, TEXT_COLOR, false));
        }
        if (showPing.getValue()) {
            boxes.add(new Box(data.pingText, pingIcon, TEXT_COLOR, false));
        }
        
        return boxes;
    }

    private void updateDraggableSize(LayoutMetrics metrics) {
        draggable.setWidth(metrics.totalWidth);
        draggable.setHeight(metrics.totalHeight);

        if (needsInitialCenter) {
            int screenWidth = mc.getWindow().getScaledWidth();
            draggable.setX(screenWidth / 2f - metrics.totalWidth / 2f);
            needsInitialCenter = false;
        }
    }

    private void handleDragging(boolean isInChat, float width) {
        if (isInChat) {
            draggable.update();
            snapToCenter(width);
        }
    }

    private void renderBoxes(List<Box> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        
        float x = draggable.getX();
        float y = draggable.getY();
        float textHeight = NanoVGRenderer.getTextHeight(TEXT_SIZE);
        
        int topRowCount = countTopRowBoxes();
        
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            renderBox(box, x, y, textHeight);
            
            x += box.width + BOX_GAP;
            
            if (i == topRowCount - 1 && i < boxes.size() - 1) {
                x = draggable.getX();
                y += textHeight + PADDING * 2 + ROW_GAP;
            }
        }
    }
    
    private int countTopRowBoxes() {
        int count = 0;
        if (showTitle.getValue()) count++;
        if (showUsername.getValue()) count++;
        if (showFps.getValue()) count++;
        if (showTime.getValue()) count++;
        return count;
    }

    private void renderBox(Box box, float x, float y, float textHeight) {
        int alpha = (int) transparency.getValue();
        Color bgColor = new Color(BACKGROUND_BASE.getRed(), BACKGROUND_BASE.getGreen(), 
                                  BACKGROUND_BASE.getBlue(), alpha);
        
        float boxHeight = textHeight + PADDING * 2;
        NanoVGRenderer.drawRoundedRect(x, y, box.width, boxHeight, CORNER_RADIUS, bgColor);
        
        float contentX = x + PADDING;
        float textY = y + PADDING;
        float iconY = y + PADDING + (textHeight - ICON_SIZE) / 2f;
        
        if (box.iconId != -1) {
            NanoVGRenderer.drawImage(box.iconId, contentX, iconY, ICON_SIZE, ICON_SIZE, ICON_COLOR);
            contentX += ICON_SIZE + ICON_TEXT_GAP;
        }
        
        NanoVGRenderer.drawText(box.text, contentX, textY, TEXT_SIZE, box.color);
    }

    private void snapToCenter(float width) {
        int screenWidth = mc.getWindow().getScaledWidth();
        float centerX = screenWidth / 2f;
        float componentCenterX = draggable.getX() + width / 2f;

        if (Math.abs(componentCenterX - centerX) < SNAP_DISTANCE) {
            draggable.setX(centerX - width / 2f);
        }
    }

    private Color getAccentColor() {
        return switch (colorMode.getMode()) {
            case "Custom" -> customColor.getValue();
            default -> cc.silk.module.modules.client.NewClickGUIModule.getAccentColor();
        };
    }

    private class WatermarkData {
        final String title;
        final String username;
        final String time;
        final String fpsText;
        final String coords;
        final String pingText;

        WatermarkData() {
            this.title = text.getValue();
            this.username = mc.player.getName().getString();
            this.time = LocalTime.now().format(TIME_FORMAT);
            
            int fps = mc.getCurrentFps();
            this.fpsText = fps + " Fps";
            
            this.coords = String.format("%d %d %d", 
                (int) mc.player.getX(), 
                (int) mc.player.getY(), 
                (int) mc.player.getZ());
            
            int ping = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()) != null
                ? mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()).getLatency()
                : 0;
            this.pingText = ping + " Ping";
        }
    }

    private class Box {
        final String text;
        final int iconId;
        final Color color;
        final float width;

        Box(String text, int iconId, Color color, boolean isTitle) {
            this.text = text;
            this.iconId = iconId;
            this.color = color;
            
            float textWidth = NanoVGRenderer.getTextWidth(text, TEXT_SIZE);
            float contentWidth = textWidth;
            
            if (iconId != -1) {
                contentWidth += ICON_SIZE + ICON_TEXT_GAP;
            }
            
            this.width = contentWidth + PADDING * 2;
        }
    }

    private class LayoutMetrics {
        final float totalWidth;
        final float totalHeight;

        LayoutMetrics(List<Box> boxes) {
            if (boxes.isEmpty()) {
                this.totalWidth = 100;
                this.totalHeight = 20;
                return;
            }
            
            float textHeight = NanoVGRenderer.getTextHeight(TEXT_SIZE);
            float boxHeight = textHeight + PADDING * 2;
            
            int topRowCount = countTopRowBoxes();
            
            float row1Width = 0;
            for (int i = 0; i < Math.min(topRowCount, boxes.size()); i++) {
                row1Width += boxes.get(i).width;
                if (i < topRowCount - 1) row1Width += BOX_GAP;
            }
            
            float row2Width = 0;
            for (int i = topRowCount; i < boxes.size(); i++) {
                row2Width += boxes.get(i).width;
                if (i < boxes.size() - 1) row2Width += BOX_GAP;
            }
            
            this.totalWidth = Math.max(row1Width, row2Width);
            
            boolean hasBottomRow = boxes.size() > topRowCount;
            this.totalHeight = hasBottomRow ? (boxHeight * 2 + ROW_GAP) : boxHeight;
        }
    }
}
