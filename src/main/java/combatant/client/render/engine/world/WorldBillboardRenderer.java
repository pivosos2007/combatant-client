/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.MatteHudStyle;

/** Flat, camera-facing world UI primitives. Local coordinates are logical pixels. */
public enum WorldBillboardRenderer {
    ;

    public static Basis currentBasis() {
        Vector3f rightVector = new Vector3f(1f, 0f, 0f).rotate(RenderState.cameraRotation);
        Vector3f upVector = new Vector3f(0f, 1f, 0f).rotate(RenderState.cameraRotation);
        return new Basis(
                new Vec3(rightVector.x, rightVector.y, rightVector.z),
                new Vec3(-upVector.x, -upVector.y, -upVector.z)
        );
    }

    public static void softShadow(Renderer3D renderer,
                                  Basis basis,
                                  Vec3 anchor,
                                  double x,
                                  double y,
                                  double width,
                                  double height,
                                  double radius,
                                  double blur,
                                  double worldScale,
                                  int argb) {
        int sourceAlpha = (argb >>> 24) & 0xFF;
        if (sourceAlpha <= 0 || blur <= 0.0) return;
        emitSdfQuad(renderer, basis, anchor, x, y, width, height, radius, blur,
                worldScale, argb, 1.0f, 0.08f);
    }

    public static void roundedRect(Renderer3D renderer,
                                   Basis basis,
                                   Vec3 anchor,
                                   double x,
                                   double y,
                                   double width,
                                   double height,
                                   double radius,
                                   double worldScale,
                                   int argb) {
        if (renderer == null || basis == null || anchor == null || width <= 0.0 || height <= 0.0
                || worldScale <= 0.0 || ((argb >>> 24) & 0xFF) <= 0) {
            return;
        }

        emitSdfQuad(renderer, basis, anchor, x, y, width, height, radius, 0.0,
                worldScale, argb, 0.0f, 0.0f);
    }

    public static void roundedRectStroke(Renderer3D renderer,
                                         Basis basis,
                                         Vec3 anchor,
                                         double x,
                                         double y,
                                         double width,
                                         double height,
                                         double radius,
                                         double thickness,
                                         double worldScale,
                                         int argb) {
        if (thickness <= 0.0) return;
        emitSdfQuad(renderer, basis, anchor, x, y, width, height, radius, 0.0,
                worldScale, argb, 2.0f, (float) thickness);
    }

    public static void mattePlate(Renderer3D renderer,
                                  Basis basis,
                                  Vec3 anchor,
                                  double x,
                                  double y,
                                  double width,
                                  double height,
                                  double radius,
                                  double shadowBlur,
                                  double worldScale,
                                  float alpha) {
        float safeAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (safeAlpha <= 0.001f) return;
        softShadow(renderer, basis, anchor, x, y, width, height, radius, shadowBlur, worldScale,
                MatteHudStyle.withAlpha(0x000000, Math.round(112.0f * safeAlpha)));
        roundedRect(renderer, basis, anchor, x, y, width, height, radius, worldScale,
                MatteHudStyle.surfaceColor(safeAlpha));
        roundedRectStroke(renderer, basis, anchor, x, y, width, height, radius, 0.6, worldScale,
                MatteHudStyle.strokeColor(safeAlpha));
    }

    public static void text(Renderer3D renderer,
                            Basis basis,
                            TextRenderer textRenderer,
                            String text,
                            Vec3 anchor,
                            double pixelX,
                            double pixelY,
                            double textScale,
                            double worldScale,
                            int argb,
                            float alpha,
                            boolean shadow) {
        if (text == null || text.isEmpty() || alpha <= 0.001f) return;
        WorldTextRenderer.drawBillboard(renderer, textRenderer, text, anchor,
                WorldTextRenderer.Options.defaults()
                        .withColor(new RenderColor(MatteHudStyle.scaleAlpha(argb, alpha)))
                        .withShadowColor(new RenderColor(MatteHudStyle.scaleAlpha(0xA0000000, alpha)))
                        .withScale(textScale)
                        .withWorldScale(worldScale)
                        .withOffset(pixelX * worldScale, pixelY * worldScale)
                        .withCentered(false)
                        .withShadow(shadow)
                        .withDepthMode(Renderer3D.DepthMode.NONE),
                basis != null ? basis.right() : null,
                basis != null ? basis.down() : null);
    }

    public static void item(Renderer3D renderer,
                            Basis basis,
                            Vec3 anchor,
                            ItemBatchRenderer.WorldItemSprite sprite,
                            double x,
                            double y,
                            double size,
                            double worldScale,
                            float alpha) {
        if (renderer == null || basis == null || anchor == null || sprite == null
                || sprite.textureView() == null || sprite.sampler() == null
                || size <= 0.0 || worldScale <= 0.0 || alpha <= 0.001f) {
            return;
        }

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED,
                sprite.textureView(),
                sprite.sampler(),
                Renderer3D.DepthMode.NONE
        );
        if (mesh == null) return;

        Vec3 p0 = point(anchor, basis, x, y, worldScale);
        Vec3 p1 = point(anchor, basis, x, y + size, worldScale);
        Vec3 p2 = point(anchor, basis, x + size, y + size, worldScale);
        Vec3 p3 = point(anchor, basis, x + size, y, worldScale);
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));

        mesh.ensureQuadCapacity();
        int i0 = mesh.vec3(p0.x, p0.y, p0.z).raw2(sprite.u0(), sprite.v0()).color(255, 255, 255, a).next();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).raw2(sprite.u0(), sprite.v1()).color(255, 255, 255, a).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).raw2(sprite.u1(), sprite.v1()).color(255, 255, 255, a).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).raw2(sprite.u1(), sprite.v0()).color(255, 255, 255, a).next();
        mesh.quad(i0, i1, i2, i3);
    }

    private static void emitSdfQuad(Renderer3D renderer,
                                    Basis basis,
                                    Vec3 anchor,
                                    double x,
                                    double y,
                                    double width,
                                    double height,
                                    double radius,
                                    double blur,
                                    double worldScale,
                                    int argb,
                                    float mode,
                                    float secondaryParam) {
        if (renderer == null || basis == null || anchor == null || width <= 0.0 || height <= 0.0
                || worldScale <= 0.0 || ((argb >>> 24) & 0xFF) <= 0) {
            return;
        }
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_BILLBOARD_SDF, Renderer3D.DepthMode.NONE);
        if (mesh == null) return;

        double safeRadius = Math.max(0.0, Math.min(radius, Math.min(width, height) * 0.5));
        double safeBlur = Math.max(0.0, blur);
        double padding = safeBlur * 2.0;
        Vec3 p0 = point(anchor, basis, x - padding, y - padding, worldScale);
        Vec3 p1 = point(anchor, basis, x - padding, y + height + padding, worldScale);
        Vec3 p2 = point(anchor, basis, x + width + padding, y + height + padding, worldScale);
        Vec3 p3 = point(anchor, basis, x + width + padding, y - padding, worldScale);
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;
        mesh.ensureQuadCapacity();
        int i0 = mesh.vec3(p0.x, p0.y, p0.z).raw2(0.0f, 0.0f).color(r, g, b, a)
                .vec4((float) width, (float) height, (float) safeRadius, (float) safeBlur)
                .vec4(mode, secondaryParam, 0.0f, 0.0f).next();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).raw2(0.0f, 1.0f).color(r, g, b, a)
                .vec4((float) width, (float) height, (float) safeRadius, (float) safeBlur)
                .vec4(mode, secondaryParam, 0.0f, 0.0f).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).raw2(1.0f, 1.0f).color(r, g, b, a)
                .vec4((float) width, (float) height, (float) safeRadius, (float) safeBlur)
                .vec4(mode, secondaryParam, 0.0f, 0.0f).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).raw2(1.0f, 0.0f).color(r, g, b, a)
                .vec4((float) width, (float) height, (float) safeRadius, (float) safeBlur)
                .vec4(mode, secondaryParam, 0.0f, 0.0f).next();
        mesh.quad(i0, i1, i2, i3);
    }

    private static Vec3 point(Vec3 anchor, Basis basis, double x, double y, double worldScale) {
        return anchor
                .add(basis.right().scale(x * worldScale))
                .add(basis.down().scale(y * worldScale));
    }

    public record Basis(Vec3 right, Vec3 down) {
    }
}
