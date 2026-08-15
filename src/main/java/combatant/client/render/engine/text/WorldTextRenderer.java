/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.MsdfTextUniforms;

public enum WorldTextRenderer {
    ;

    public static double drawBillboard(Renderer3D renderer,
                                       TextRenderer textRenderer,
                                       String text,
                                       Vec3 anchor) {
        return drawBillboard(renderer, textRenderer, text, anchor, Options.defaults());
    }

    public static double drawBillboard(Renderer3D renderer,
                                       TextRenderer textRenderer,
                                       String text,
                                       Vec3 anchor,
                                       Options options) {
        return drawBillboard(renderer, textRenderer, text, anchor, options, null, null);
    }

    public static double drawBillboard(Renderer3D renderer,
                                       TextRenderer textRenderer,
                                       String text,
                                       Vec3 anchor,
                                       Options options,
                                       Vec3 resolvedRight,
                                       Vec3 resolvedDown) {
        if (renderer == null || text == null || text.isEmpty() || anchor == null) return 0.0;
        Options resolved = options != null ? options : Options.defaults();
        if (resolved.worldScale() <= 0.0) return 0.0;

        CustomTextRenderer custom = resolve(textRenderer);
        if (custom == null) return 0.0;

        CustomTextRenderer.GlyphSelection selection = custom.prepareGlyphFont(resolved.scale(), resolved.big());
        GlyphFont font = selection.font();
        if (font == null) return 0.0;

        AbstractTexture texture = font.getTexture();
        if (texture == null || !font.isReady() || texture.getTextureView() == null || texture.getSampler() == null) {
            return 0.0;
        }

        double glyphScale = selection.glyphScale();
        double width = font.getWidth(text, text.length()) * glyphScale;
        double startX = resolved.centered() ? -(width * 0.5) : 0.0;

        Renderer3D.DepthMode requestedDepth = resolved.depthMode() != null
                ? resolved.depthMode()
                : Renderer3D.DepthMode.MAIN;
        Renderer3D.DepthMode effectiveDepth = requestedDepth;

        Renderer3D.BatchBindings bindings = Renderer3D.BatchBindings.none()
                .withSampler("u_Texture", texture.getTextureView(), texture.getSampler());

        var pipeline = font.isMsdf()
                ? (effectiveDepth == Renderer3D.DepthMode.NONE
                ? CombatantRenderPipelines.WORLD_TEXT_MSDF
                : CombatantRenderPipelines.WORLD_TEXT_MSDF_DEPTH)
                : (effectiveDepth == Renderer3D.DepthMode.NONE
                ? CombatantRenderPipelines.WORLD_TEXT
                : CombatantRenderPipelines.WORLD_TEXT_DEPTH);

        if (font.isMsdf()) {
            MsdfUniformKey key = new MsdfUniformKey(font.getPxRange(), font.getAtlasWidth(), font.getAtlasHeight());
            bindings = bindings.withUniform("MsdfText", key, () -> {
                MsdfTextUniforms.update(key.pxRange(), key.atlasWidth(), key.atlasHeight());
                return MsdfTextUniforms.get();
            });
        }

        MeshBuilder mesh = renderer.batch(pipeline, effectiveDepth, bindings);
        if (mesh == null) return 0.0;

        Vec3 right = resolvedRight;
        Vec3 up = resolvedDown;
        if (right == null || up == null) {
            Vector3f rightVector = new Vector3f(1f, 0f, 0f).rotate(RenderState.cameraRotation);
            Vector3f upVector = new Vector3f(0f, 1f, 0f).rotate(RenderState.cameraRotation);
            right = new Vec3(rightVector.x, rightVector.y, rightVector.z);
            up = new Vec3(-upVector.x, -upVector.y, -upVector.z);
        }

        double offsetX = resolved.offsetX();
        double offsetY = resolved.offsetY();
        double worldScale = resolved.worldScale();

        if (resolved.shadow() && resolved.shadowColor().a > 0) {
            double shadowShift = selection.shadowOffset() * worldScale;
            emitString(mesh, font, text, startX + shadowShift, shadowShift, glyphScale, anchor, right, up,
                    offsetX, offsetY, worldScale, resolved.shadowColor());
        }

        emitString(mesh, font, text, startX, 0.0, glyphScale, anchor, right, up,
                offsetX, offsetY, worldScale, resolved.color());
        return width * worldScale;
    }

    /** Exact logical metrics used by the billboard backend for a requested font scale. */
    public static Metrics measure(TextRenderer textRenderer, String text, double scale, boolean big) {
        CustomTextRenderer custom = resolve(textRenderer);
        if (custom == null) return new Metrics(0.0, 0.0);
        CustomTextRenderer.GlyphSelection selection = custom.prepareGlyphFont(scale, big);
        GlyphFont font = selection.font();
        if (font == null) return new Metrics(0.0, 0.0);
        String safeText = text == null ? "" : text;
        double glyphScale = selection.glyphScale();
        return new Metrics(
                font.getWidth(safeText, safeText.length()) * glyphScale,
                (font.height() + 1.0) * glyphScale
        );
    }

    private static void emitString(MeshBuilder mesh,
                                   GlyphFont font,
                                   String text,
                                   double localX,
                                   double localY,
                                   double glyphScale,
                                   Vec3 anchor,
                                   Vec3 right,
                                   Vec3 up,
                                   double offsetX,
                                   double offsetY,
                                   double worldScale,
                                   RenderColor color) {
        font.emitGlyphs(text, localX, localY, glyphScale, (x0, y0, x1, y1, u0, v0, u1, v1) -> {
            mesh.ensureQuadCapacity();

            Vec3 p1 = billboard(anchor, right, up, offsetX + x0 * worldScale, offsetY + y0 * worldScale);
            Vec3 p2 = billboard(anchor, right, up, offsetX + x0 * worldScale, offsetY + y1 * worldScale);
            Vec3 p3 = billboard(anchor, right, up, offsetX + x1 * worldScale, offsetY + y1 * worldScale);
            Vec3 p4 = billboard(anchor, right, up, offsetX + x1 * worldScale, offsetY + y0 * worldScale);

            int i1 = mesh.vec3(p1.x, p1.y, p1.z).raw2(u0, v0).color(color.r, color.g, color.b, color.a).next();
            int i2 = mesh.vec3(p2.x, p2.y, p2.z).raw2(u0, v1).color(color.r, color.g, color.b, color.a).next();
            int i3 = mesh.vec3(p3.x, p3.y, p3.z).raw2(u1, v1).color(color.r, color.g, color.b, color.a).next();
            int i4 = mesh.vec3(p4.x, p4.y, p4.z).raw2(u1, v0).color(color.r, color.g, color.b, color.a).next();
            mesh.quad(i1, i2, i3, i4);
        });
    }

    private static Vec3 billboard(Vec3 anchor, Vec3 right, Vec3 up, double x, double y) {
        return anchor.add(right.scale(x)).add(up.scale(y));
    }

    private static CustomTextRenderer resolve(TextRenderer textRenderer) {
        if (textRenderer instanceof CustomTextRenderer custom) {
            return custom;
        }
        TextRenderer fallback = TextRenderer.get();
        if (fallback instanceof CustomTextRenderer custom) {
            return custom;
        }
        return null;
    }

    public record Options(RenderColor color,
                          RenderColor shadowColor,
                          double scale,
                          double worldScale,
                          double offsetX,
                          double offsetY,
                          boolean centered,
                          boolean shadow,
                          boolean big,
                          Renderer3D.DepthMode depthMode) {
        public static Options defaults() {
            return new Options(
                    new RenderColor(255, 255, 255, 255),
                    new RenderColor(60, 60, 60, 180),
                    1.0,
                    0.025,
                    0.0,
                    0.0,
                    true,
                    false,
                    false,
                    Renderer3D.DepthMode.NONE
            );
        }

        public Options withColor(RenderColor value) {
            return new Options(value, shadowColor, scale, worldScale, offsetX, offsetY, centered, shadow, big, depthMode);
        }

        public Options withShadowColor(RenderColor value) {
            return new Options(color, value, scale, worldScale, offsetX, offsetY, centered, shadow, big, depthMode);
        }

        public Options withScale(double value) {
            return new Options(color, shadowColor, value, worldScale, offsetX, offsetY, centered, shadow, big, depthMode);
        }

        public Options withWorldScale(double value) {
            return new Options(color, shadowColor, scale, value, offsetX, offsetY, centered, shadow, big, depthMode);
        }

        public Options withOffset(double x, double y) {
            return new Options(color, shadowColor, scale, worldScale, x, y, centered, shadow, big, depthMode);
        }

        public Options withCentered(boolean value) {
            return new Options(color, shadowColor, scale, worldScale, offsetX, offsetY, value, shadow, big, depthMode);
        }

        public Options withShadow(boolean value) {
            return new Options(color, shadowColor, scale, worldScale, offsetX, offsetY, centered, value, big, depthMode);
        }

        public Options withBig(boolean value) {
            return new Options(color, shadowColor, scale, worldScale, offsetX, offsetY, centered, shadow, value, depthMode);
        }

        public Options withDepthMode(Renderer3D.DepthMode value) {
            return new Options(color, shadowColor, scale, worldScale, offsetX, offsetY, centered, shadow, big, value);
        }
    }

    private record MsdfUniformKey(float pxRange, int atlasWidth, int atlasHeight) {
    }

    public record Metrics(double width, double height) {
    }
}
