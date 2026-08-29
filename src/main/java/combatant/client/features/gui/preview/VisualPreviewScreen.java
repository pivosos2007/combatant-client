/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.SettingsPanelComponent;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.render.engine.math.HudScale;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class VisualPreviewScreen extends Screen {
    private final ClickGuiScreen parent;
    private final VisualPreviewProvider provider;
    private final SettingsPanelComponent settingsPanel = new SettingsPanelComponent(SettingRenderSurface.MODULES);

    private float mouseX;
    private float mouseY;
    private float yaw;
    private float pitch;
    private float targetYaw;
    private float targetPitch;
    private float cameraYaw;
    private float cameraPitch;
    private float targetCameraYaw;
    private float targetCameraPitch;
    private float cameraDolly;
    private float targetCameraDolly;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private float panX;
    private float panY;
    private float targetPanX;
    private float targetPanY;
    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float targetCameraX;
    private float targetCameraY;
    private float targetCameraZ;
    private float flySpeed = 1.0f;
    private long lastFrameNanos;
    private boolean dragging;
    private int dragButton = -1;
    private float dragMouseX;
    private float dragMouseY;
    private boolean closing;
    private boolean settingsVisible = true;

    public VisualPreviewScreen(ClickGuiScreen parent, VisualPreviewProvider provider) {
        super(Component.literal(provider == null ? "Visual Preview" : provider.title()));
        if (provider == null) throw new IllegalArgumentException("provider");
        this.parent = parent;
        this.provider = provider;
        settingsPanel.setHintsVisible(false);
        resetTransform(true);
        if (!provider.settings().isEmpty()) {
            settingsPanel.open(provider.id(), provider.title(), provider.settings(), false);
        }
    }

    public static void open(VisualPreviewProvider provider) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || provider == null) return;
        ClickGuiScreen parent;
        if (ClientScreen.current() instanceof ClickGuiScreen clickGuiScreen) {
            ClickGuiRenderer.captureMainScreen();
            parent = clickGuiScreen;
        } else {
            parent = ClickGuiRenderer.ensureMainScreen();
        }
        ClientScreen.show(mc, new VisualPreviewScreen(parent, provider));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // The complete scene is rendered by VisualPreviewRenderer in the SCREEN_TOP phase.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No vanilla world blur/dim pass. The preview owns the full framebuffer.
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        float vx = toVirtualX(mouseX);
        float vy = toVirtualY(mouseY);
        this.mouseX = vx;
        this.mouseY = vy;
        if (!dragging) return;

        float dx = vx - dragMouseX;
        float dy = vy - dragMouseY;
        dragMouseX = vx;
        dragMouseY = vy;
        VisualPreviewInteractionProfile profile = provider.interactionProfile();
        float sensitivity = mouseSensitivityScale();
        boolean subjectDrag = profile.subjectMode() == VisualPreviewSubjectMode.ROTATE
                && ((profile.middleSubjectDrag() && dragButton == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                || (!profile.middleSubjectDrag() && dragButton == GLFW.GLFW_MOUSE_BUTTON_LEFT));
        if (subjectDrag) {
            targetYaw += dx * profile.subjectSensitivity() * sensitivity;
            targetPitch = Mth.clamp(targetPitch + dy * profile.subjectSensitivity() * sensitivity, -89.0f, 89.0f);
            return;
        }

        boolean cameraDrag = (profile.primaryCameraDrag() && dragButton == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                || (profile.secondaryCameraDrag() && dragButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (!cameraDrag) return;
        switch (profile.cameraMode()) {
            case ORBIT, FREE_FLY -> {
                targetCameraYaw += dx * profile.cameraSensitivity() * sensitivity;
                targetCameraPitch = Mth.clamp(
                        targetCameraPitch + dy * profile.cameraSensitivity() * sensitivity,
                        -89.0f,
                        89.0f
                );
            }
            case PAN -> {
                targetPanX += dx;
                targetPanY += dy;
            }
            case FIXED -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        float vx = toVirtualX(click.x());
        float vy = toVirtualY(click.y());
        mouseX = vx;
        mouseY = vy;
        float scale = sceneUiScale();
        if (settingsVisible && settingsPanel.isActive()
                && settingsPanel.mouseClicked(vx, vy, click.button(), scale)) return true;

        VisualPreviewInteractionProfile profile = provider.interactionProfile();
        boolean subjectDrag = profile.subjectMode() == VisualPreviewSubjectMode.ROTATE
                && ((profile.middleSubjectDrag() && click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                || (!profile.middleSubjectDrag() && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT));
        boolean cameraDrag = (profile.primaryCameraDrag() && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                || (profile.secondaryCameraDrag() && click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (insideSubject(vx, vy) && (subjectDrag || cameraDrag)) {
            dragging = true;
            dragButton = click.button();
            dragMouseX = vx;
            dragMouseY = vy;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        float vx = toVirtualX(click.x());
        float vy = toVirtualY(click.y());
        mouseX = vx;
        mouseY = vy;
        if (click.button() == dragButton) {
            dragging = false;
            dragButton = -1;
        }
        if (settingsVisible) settingsPanel.mouseReleased(vx, vy, click.button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float vx = toVirtualX(mouseX);
        float vy = toVirtualY(mouseY);
        this.mouseX = vx;
        this.mouseY = vy;
        if (settingsVisible && settingsPanel.isActive()
                && settingsPanel.mouseScrolled(vx, vy, verticalAmount)) return true;
        switch (provider.interactionProfile().wheelMode()) {
            case SUBJECT_SCALE -> targetZoom = Mth.clamp(
                    targetZoom * (float) Math.pow(1.10, verticalAmount), 0.35f, 3.5f);
            case CAMERA_DOLLY -> targetCameraDolly = Mth.clamp(
                    targetCameraDolly + (float) verticalAmount * 0.16f, -1.8f, 0.62f);
            case FLY_SPEED -> flySpeed = Mth.clamp(
                    flySpeed * (float) Math.pow(1.12, verticalAmount), 0.15f, 8.0f);
            case NONE -> {
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_S && (input.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
            settingsVisible = !settingsVisible;
            return true;
        }
        if (settingsVisible && settingsPanel.isActive()
                && settingsPanel.keyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_R
                && !provider.interactionProfile().equals(VisualPreviewInteractionProfile.FIXED)) {
            resetTransform(false);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (settingsVisible && settingsPanel.isActive()) {
            int codepoint = input.codepoint();
            if (Character.isBmpCodePoint(codepoint)
                    && settingsPanel.charTyped((char) codepoint, 0)) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        dragging = false;
        dragButton = -1;
        Minecraft mc = Minecraft.getInstance();
        ClientScreen.show(mc, parent != null ? parent : ClickGuiRenderer.ensureMainScreen());
        ClickGuiRenderer.ensureCursorShown();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public VisualPreviewProvider provider() {
        return provider;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    public float cameraDolly() {
        return cameraDolly;
    }

    public float cameraX() {
        return cameraX;
    }

    public float cameraY() {
        return cameraY;
    }

    public float cameraZ() {
        return cameraZ;
    }

    float mouseXVirtual() {
        return mouseX;
    }

    float mouseYVirtual() {
        return mouseY;
    }

    void renderSettings(float width, float height) {
        if (!settingsVisible || !settingsPanel.isActive()) return;
        float inputScale = sceneUiScale();
        float panelScale = Mth.clamp(inputScale * 1.5f, 2.55f, 3.0f);
        float expectedPanelW = 115.0f * panelScale;
        float anchorX = width - expectedPanelW - 24.0f * panelScale - 12.0f;
        settingsPanel.render(anchorX, 12.0f, 0.0f, height - 24.0f, mouseX, mouseY, inputScale);
    }

    private boolean insideSubject(float x, float y) {
        float width = virtualWidth();
        float height = virtualHeight();
        float rightReserve = provider.settings().isEmpty() || !settingsVisible ? 24.0f : 420.0f;
        return x >= 24.0f && x <= width - rightReserve && y >= 48.0f && y <= height - 48.0f;
    }

    void updateFrameState() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 1.0f / 60.0f : (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0f, 0.05f);

        updateFreeFlightTargets(dt);
        float rotationBlend = 1.0f - (float) Math.exp(-14.0f * dt);
        float motionBlend = 1.0f - (float) Math.exp(-11.0f * dt);
        yaw = Mth.lerp(rotationBlend, yaw, targetYaw);
        pitch = Mth.lerp(rotationBlend, pitch, targetPitch);
        cameraYaw = Mth.lerp(rotationBlend, cameraYaw, targetCameraYaw);
        cameraPitch = Mth.lerp(rotationBlend, cameraPitch, targetCameraPitch);
        cameraDolly = Mth.lerp(motionBlend, cameraDolly, targetCameraDolly);
        zoom = Mth.lerp(motionBlend, zoom, targetZoom);
        panX = Mth.lerp(motionBlend, panX, targetPanX);
        panY = Mth.lerp(motionBlend, panY, targetPanY);
        cameraX = Mth.lerp(motionBlend, cameraX, targetCameraX);
        cameraY = Mth.lerp(motionBlend, cameraY, targetCameraY);
        cameraZ = Mth.lerp(motionBlend, cameraZ, targetCameraZ);
    }

    private void updateFreeFlightTargets(float dt) {
        if (provider.interactionProfile().cameraMode() != VisualPreviewCameraMode.FREE_FLY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().handle();
        float forward = keyAxis(window, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S);
        float side = keyAxis(window, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_A);
        float vertical = keyAxis(window, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT);
        if (forward == 0.0f && side == 0.0f && vertical == 0.0f) return;
        float distance = flySpeed * dt * 2.6f;
        float yawRadians = (float) Math.toRadians(targetCameraYaw);
        float sin = (float) Math.sin(yawRadians);
        float cos = (float) Math.cos(yawRadians);
        targetCameraX += (side * cos - forward * sin) * distance;
        targetCameraZ += (forward * cos + side * sin) * distance;
        targetCameraY += vertical * distance;
    }

    private static float keyAxis(long window, int positive, int negative) {
        float value = GLFW.glfwGetKey(window, positive) == GLFW.GLFW_PRESS ? 1.0f : 0.0f;
        if (GLFW.glfwGetKey(window, negative) == GLFW.GLFW_PRESS) value -= 1.0f;
        return value;
    }

    private float mouseSensitivityScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return 1.0f;
        double sensitivity = mc.options.sensitivity().get();
        double curve = sensitivity * 0.6 + 0.2;
        return (float) Math.max(0.05, curve * curve * curve * 8.0);
    }

    private void resetTransform(boolean immediate) {
        VisualPreviewInteractionProfile profile = provider.interactionProfile();
        boolean objectOnly = profile.cameraMode() == VisualPreviewCameraMode.FIXED
                && profile.subjectMode() == VisualPreviewSubjectMode.ROTATE
                && !profile.middleSubjectDrag();
        float initialYaw = objectOnly ? -28.0f : 0.0f;
        float initialPitch = objectOnly ? 18.0f : 0.0f;
        targetYaw = initialYaw;
        targetPitch = initialPitch;
        targetCameraYaw = 0.0f;
        targetCameraPitch = 0.0f;
        targetCameraDolly = 0.0f;
        targetZoom = 1.0f;
        targetPanX = targetPanY = 0.0f;
        targetCameraX = targetCameraY = targetCameraZ = 0.0f;
        if (!immediate) return;
        yaw = targetYaw;
        pitch = targetPitch;
        cameraYaw = targetCameraYaw;
        cameraPitch = targetCameraPitch;
        cameraDolly = targetCameraDolly;
        zoom = targetZoom;
        panX = targetPanX;
        panY = targetPanY;
        cameraX = targetCameraX;
        cameraY = targetCameraY;
        cameraZ = targetCameraZ;
    }

    boolean settingsVisible() {
        return settingsVisible && !provider.settings().isEmpty();
    }

    private float sceneUiScale() {
        // Same input scale as the ClickGUI settings screen. MODULES surface applies its normal
        // popup scale and compact setting metrics to both panel chrome and child controls.
        return 2.0f;
    }

    private float toVirtualX(double scaledX) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return (float) scaledX;
        double scaledWidth = mc.getWindow().getGuiScaledWidth();
        double framebufferWidth = mc.getWindow().getWidth();
        double framebufferX = scaledWidth > 0.0 ? scaledX * framebufferWidth / scaledWidth : scaledX;
        return (float) (framebufferX / HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
    }

    private float toVirtualY(double scaledY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return (float) scaledY;
        double scaledHeight = mc.getWindow().getGuiScaledHeight();
        double framebufferHeight = mc.getWindow().getHeight();
        double framebufferY = scaledHeight > 0.0 ? scaledY * framebufferHeight / scaledHeight : scaledY;
        return (float) (framebufferY / HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
    }

    private float virtualWidth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return width;
        return HudScale.virtualWidth(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private float virtualHeight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return height;
        return HudScale.virtualHeight(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }
}
