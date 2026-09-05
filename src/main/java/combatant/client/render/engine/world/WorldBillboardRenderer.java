/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantWorldMatrices;
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
        // Billboard orientation must never depend on the mutable ambient RenderSystem
        // model-view stack. GuiItemAtlas and other off-screen passes legitimately change
        // that stack while preparing textures. Use the immutable camera view captured at
        // the beginning of the world frame, exactly like ScreenProjection does.
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        if (view != null) {
            Matrix4f inverseView = new Matrix4f(view).invert();
            Vector3f rightVector = inverseView.transformDirection(new Vector3f(1f, 0f, 0f)).normalize();
            Vector3f downVector = inverseView.transformDirection(new Vector3f(0f, -1f, 0f)).normalize();
            if (Float.isFinite(rightVector.x) && Float.isFinite(rightVector.y) && Float.isFinite(rightVector.z)
                    && Float.isFinite(downVector.x) && Float.isFinite(downVector.y) && Float.isFinite(downVector.z)
                    && rightVector.lengthSquared() > 1.0e-8f && downVector.lengthSquared() > 1.0e-8f) {
                return new Basis(
                        new Vec3(rightVector.x, rightVector.y, rightVector.z),
                        new Vec3(downVector.x, downVector.y, downVector.z)
                );
            }
        }

        Vector3f rightVector = new Vector3f(1f, 0f, 0f).rotate(RenderState.cameraRotation).normalize();
        Vector3f upVector = new Vector3f(0f, 1f, 0f).rotate(RenderState.cameraRotation).normalize();
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

    /**
     * Pixel-aligned rectangular frame made only from four-sided quad strips.
     * No SDF/rounded geometry is involved and the three bands touch exactly:
     * outer dark -> color -> inner dark.
     */
    public static void rectangularFrame(Renderer3D renderer,
                                        Basis basis,
                                        Vec3 anchor,
                                        double x,
                                        double y,
                                        double width,
                                        double height,
                                        double worldScale,
                                        int baseColor,
                                        float alpha) {
        float safeAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (renderer == null || basis == null || anchor == null
                || width <= 2.0 || height <= 2.0 || worldScale <= 0.0 || safeAlpha <= 0.001f) {
            return;
        }

        final double outerThickness = 1.0;
        final double colorThickness = 1.45;
        final double innerThickness = 1.0;
        int outer = MatteHudStyle.withAlpha(0x000000, Math.round(170.0f * safeAlpha));
        int color = MatteHudStyle.withAlpha(baseColor, Math.round(235.0f * safeAlpha));

        emitRectOutline(renderer, basis, anchor,
                x - outerThickness, y - outerThickness,
                width + outerThickness * 2.0, height + outerThickness * 2.0,
                outerThickness, worldScale, outer);
        emitRectOutline(renderer, basis, anchor,
                x, y, width, height,
                colorThickness, worldScale, color);

        double innerX = x + colorThickness;
        double innerY = y + colorThickness;
        double innerWidth = width - colorThickness * 2.0;
        double innerHeight = height - colorThickness * 2.0;
        if (innerWidth > innerThickness * 2.0 && innerHeight > innerThickness * 2.0) {
            emitRectOutline(renderer, basis, anchor,
                    innerX, innerY, innerWidth, innerHeight,
                    innerThickness, worldScale, outer);
        }
    }

    private static void emitRectOutline(Renderer3D renderer,
                                        Basis basis,
                                        Vec3 anchor,
                                        double x,
                                        double y,
                                        double width,
                                        double height,
                                        double thickness,
                                        double worldScale,
                                        int argb) {
        if (width <= 0.0 || height <= 0.0 || thickness <= 0.0 || ((argb >>> 24) & 0xFF) <= 0) return;
        double t = Math.min(thickness, Math.min(width, height) * 0.5);
        emitColorQuad(renderer, basis, anchor, x, y, width, t, worldScale, argb);
        emitColorQuad(renderer, basis, anchor, x, y + height - t, width, t, worldScale, argb);
        double middleHeight = Math.max(0.0, height - t * 2.0);
        if (middleHeight > 0.0) {
            emitColorQuad(renderer, basis, anchor, x, y + t, t, middleHeight, worldScale, argb);
            emitColorQuad(renderer, basis, anchor, x + width - t, y + t, t, middleHeight, worldScale, argb);
        }
    }

    /** Raw camera-facing colored quad. No SDF.**/
    public static void quad(Renderer3D renderer,
                            Basis basis,
                            Vec3 anchor,
                            double x,
                            double y,
                            double width,
                            double height,
                            double worldScale,
                            int argb) {
        if (renderer == null || basis == null || anchor == null
                || width <= 0.0 || height <= 0.0 || worldScale <= 0.0
                || ((argb >>> 24) & 0xFF) <= 0) {
            return;
        }
        emitColorQuad(renderer, basis, anchor, x, y, width, height, worldScale, argb);
    }

    private static void emitColorQuad(Renderer3D renderer,
                                      Basis basis,
                                      Vec3 anchor,
                                      double x,
                                      double y,
                                      double width,
                                      double height,
                                      double worldScale,
                                      int argb) {
        if (width <= 0.0 || height <= 0.0) return;
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.NONE);
        if (mesh == null) return;

        Vec3 p0 = point(anchor, basis, x, y, worldScale);
        Vec3 p1 = point(anchor, basis, x, y + height, worldScale);
        Vec3 p2 = point(anchor, basis, x + width, y + height, worldScale);
        Vec3 p3 = point(anchor, basis, x + width, y, worldScale);
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;

        mesh.ensureQuadCapacity();
        int i0 = mesh.vec3(p0.x, p0.y, p0.z).color(r, g, b, a).next();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).color(r, g, b, a).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).color(r, g, b, a).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).color(r, g, b, a).next();
        mesh.quad(i0, i1, i2, i3);
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

    public static void texture(Renderer3D renderer,
                               Basis basis,
                               Vec3 anchor,
                               Identifier textureId,
                               double x,
                               double y,
                               double width,
                               double height,
                               float u0,
                               float v0,
                               float u1,
                               float v1,
                               double worldScale,
                               int argb,
                               float alpha) {
        if (renderer == null || basis == null || anchor == null || textureId == null
                || width <= 0.0 || height <= 0.0 || worldScale <= 0.0 || alpha <= 0.001f) {
            return;
        }

        int sourceAlpha = (argb >>> 24) & 0xFF;
        int finalAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * alpha)));
        if (finalAlpha <= 0) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED,
                textureId,
                Renderer3D.DepthMode.NONE
        );
        if (mesh == null) return;

        Vec3 p0 = point(anchor, basis, x, y, worldScale);
        Vec3 p1 = point(anchor, basis, x, y + height, worldScale);
        Vec3 p2 = point(anchor, basis, x + width, y + height, worldScale);
        Vec3 p3 = point(anchor, basis, x + width, y, worldScale);
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        mesh.ensureQuadCapacity();
        int i0 = mesh.vec3(p0.x, p0.y, p0.z).raw2(u0, v0).color(r, g, b, finalAlpha).next();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).raw2(u0, v1).color(r, g, b, finalAlpha).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).raw2(u1, v1).color(r, g, b, finalAlpha).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).raw2(u1, v0).color(r, g, b, finalAlpha).next();
        mesh.quad(i0, i1, i2, i3);
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
                CombatantRenderPipelines.WORLD_TEXTURED_PREMULTIPLIED_ALPHA,
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
        int i0 = mesh.vec3(p0.x, p0.y, p0.z).raw2(sprite.u0(), sprite.v0()).color(a, a, a, a).next();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).raw2(sprite.u0(), sprite.v1()).color(a, a, a, a).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).raw2(sprite.u1(), sprite.v1()).color(a, a, a, a).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).raw2(sprite.u1(), sprite.v0()).color(a, a, a, a).next();
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
