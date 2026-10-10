/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import combatant.client.render.engine.uniform.ShaderUniformBindings;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Frame pointer dynamics; individual glass shapes gate the response with their own SDF. */
public enum UiGlassFrameUniforms {
    ;

    private static final ShaderUniformBindings.Block BLOCK = ShaderUniformBindings.block("UIGlassFrame");
    public static final String BLOCK_NAME = BLOCK.name();
    private static final ShaderUniformBindings.Writer DATA = BLOCK.writer();
    private static final float VISCOSITY = 0.65f;
    private static long lastFrameId = Long.MIN_VALUE;
    private static float lastLogicalW = Float.NaN;
    private static float lastLogicalH = Float.NaN;
    private static long lastUpdateNanos;
    private static float rawX;
    private static float rawY;
    private static float easedX;
    private static float easedY;
    private static float velocityX;
    private static float velocityY;
    private static float envelope;
    private static float pressImpulse;
    private static boolean wasPressed;
    private static boolean initialized;
    private static GpuBufferSlice current;

    public static GpuBufferSlice update(Minecraft mc, float framebufferWidth, float framebufferHeight) {
        CombatantUniformAllocator allocator = CombatantRenderSystem.uniforms();
        long frameId = allocator.frameId();

        float safeFramebufferW = Math.max(framebufferWidth, 1.0f);
        float safeFramebufferH = Math.max(framebufferHeight, 1.0f);
        ViewportContext viewport = ViewportContext.current();
        float logicalW = viewport != null ? Math.max(viewport.width(), 1.0f) : safeFramebufferW;
        float logicalH = viewport != null ? Math.max(viewport.height(), 1.0f) : safeFramebufferH;
        // GLFW mouse coordinates are window coordinates, not framebuffer pixels (HiDPI).
        float windowW = mc != null && mc.getWindow() != null
                ? Math.max(1, mc.getWindow().getScreenWidth()) : safeFramebufferW;
        float windowH = mc != null && mc.getWindow() != null
                ? Math.max(1, mc.getWindow().getScreenHeight()) : safeFramebufferH;
        if (current != null && lastFrameId == frameId
                && Float.compare(lastLogicalW, logicalW) == 0
                && Float.compare(lastLogicalH, logicalH) == 0) return current;
        // Keep state in normalized window coordinates: one frame can contain
        // differently scaled logical UI views without jumping the fluid state.
        float nextRawX = mc != null && mc.mouseHandler != null
                ? (float) mc.mouseHandler.xpos() / windowW : rawX;
        float nextRawY = mc != null && mc.mouseHandler != null
                ? (float) mc.mouseHandler.ypos() / windowH : rawY;
        boolean pressed = mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        long now = System.nanoTime();
        if (!initialized) {
            rawX = easedX = nextRawX;
            rawY = easedY = nextRawY;
            lastUpdateNanos = now;
            wasPressed = pressed;
            initialized = true;
        } else if (lastFrameId != frameId) {
            float dtMillis = Math.max(1.0f, Math.min(50.0f, (now - lastUpdateNanos) * 1.0e-6f));
            float dtSeconds = dtMillis * 0.001f;
            float follow = 1.0f - (float) Math.exp(-dtMillis / (25.0f + VISCOSITY * 180.0f));
            float settle = 1.0f - (float) Math.exp(-dtMillis / (80.0f + VISCOSITY * 620.0f));
            // Discard discontinuities after focus changes, instead of creating a giant
            // pseudo-fluid impulse when the cursor jumps back into the window.
            boolean continuous = (now - lastUpdateNanos) <= 180_000_000L;
            float instantVelocityX = continuous ? (nextRawX - rawX) / dtSeconds : 0.0f;
            float instantVelocityY = continuous ? (nextRawY - rawY) / dtSeconds : 0.0f;
            if (!continuous) {
                easedX = nextRawX;
                easedY = nextRawY;
                velocityX = velocityY = envelope = 0.0f;
            }
            velocityX += (instantVelocityX - velocityX) * follow;
            velocityY += (instantVelocityY - velocityY) * follow;
            easedX += (nextRawX - easedX) * follow;
            easedY += (nextRawY - easedY) * follow;

            float moving = Math.abs(instantVelocityX * logicalW) + Math.abs(instantVelocityY * logicalH) > 12.0f ? 1.0f : 0.0f;
            float envelopeRate = moving > envelope ? follow : settle;
            envelope += (moving - envelope) * envelopeRate;
            if (moving == 0.0f) {
                velocityX += (0.0f - velocityX) * settle;
                velocityY += (0.0f - velocityY) * settle;
            }
            // Press is a short optical impulse, not a permanent bright hover state.
            pressImpulse *= (float) Math.exp(-dtMillis / 230.0f);
            if (pressed && !wasPressed) pressImpulse = 1.0f;
            wasPressed = pressed;
            rawX = nextRawX;
            rawY = nextRawY;
            lastUpdateNanos = now;
        }

        DATA.vec4("uGlassPointer", rawX * logicalW, rawY * logicalH,
                        easedX * logicalW, easedY * logicalH)
                .vec4("uGlassMotion", velocityX * logicalW, velocityY * logicalH,
                        envelope, pressImpulse);
        current = DATA.upload(8);
        lastFrameId = frameId;
        lastLogicalW = logicalW;
        lastLogicalH = logicalH;
        return current;
    }
}
