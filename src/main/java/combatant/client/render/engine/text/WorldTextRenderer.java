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
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.MsdfTextUniforms;

public enum WorldTextRenderer {
    ;

    private static Renderer3D.BatchBindings cachedBindings;
    private static Renderer3D.BatchBindings cachedEffectBindings;
    private static com.mojang.renderpearl.api.textures.GpuTextureView cachedEffectView;
    private static com.mojang.renderpearl.api.textures.GpuSampler cachedEffectSampler;
    private static com.mojang.renderpearl.api.textures.GpuTextureView cachedView;
    private static com.mojang.renderpearl.api.textures.GpuSampler cachedSampler;
    private static boolean cachedMsdf;
    private static float cachedPxRange;
    private static int cachedAtlasWidth;
    private static int cachedAtlasHeight;

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

        boolean withShadow = resolved.shadow() && resolved.shadowColor().a > 0;
        Renderer3D.BatchBindings bindings = withShadow
                ? effectBindings(texture) : textBindings(font, texture);

        var pipeline = withShadow
                ? (effectiveDepth == Renderer3D.DepthMode.NONE
                    ? WorldRenderPipelines.WORLD_TEXT_EFFECT : WorldRenderPipelines.WORLD_TEXT_EFFECT_DEPTH)
                : font.isMsdf()
                ? (effectiveDepth == Renderer3D.DepthMode.NONE
                ? WorldRenderPipelines.WORLD_TEXT_MSDF
                : WorldRenderPipelines.WORLD_TEXT_MSDF_DEPTH)
                : (effectiveDepth == Renderer3D.DepthMode.NONE
                ? WorldRenderPipelines.WORLD_TEXT
                : WorldRenderPipelines.WORLD_TEXT_DEPTH);

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

        if (withShadow) {
            emitEffectString(mesh, font, text, startX, 0.0, glyphScale,
                    anchor, right, up, offsetX, offsetY, worldScale, resolved.color(),
                    selection.shadowOffset(), -resolved.shadowColor().a / 255.0f);
        } else {
            emitString(mesh, font, text, startX, 0.0, glyphScale, anchor, right, up,
                    offsetX, offsetY, worldScale, resolved.color());
        }
        return width * worldScale;
    }

    /**
     * One world quad per glyph for the same shader coverage used by UI text.
     * Both face and dark outline are composited in the fragment shader.
     */
    public static double drawOutlinedBillboard(Renderer3D renderer, TextRenderer textRenderer,
                                               String text, Vec3 anchor, Options options,
                                               Vec3 right, Vec3 down,
                                               float radius, float opacity) {
        if (renderer == null || text == null || text.isEmpty() || anchor == null) return 0.0;
        Options resolved = options != null ? options : Options.defaults();
        if (resolved.worldScale() <= 0.0 || opacity <= 0.0f) return 0.0;
        CustomTextRenderer custom = resolve(textRenderer);
        if (custom == null) return 0.0;
        CustomTextRenderer.GlyphSelection selection = custom.prepareGlyphFont(resolved.scale(), resolved.big());
        GlyphFont font = selection.font();
        if (font == null || !font.isReady() || font.getTexture() == null) return 0.0;
        AbstractTexture texture = font.getTexture();
        if (texture.getTextureView() == null || texture.getSampler() == null) return 0.0;
        Renderer3D.DepthMode depth = resolved.depthMode() != null
                ? resolved.depthMode() : Renderer3D.DepthMode.MAIN;
        MeshBuilder mesh = renderer.batch(depth == Renderer3D.DepthMode.NONE
                ? WorldRenderPipelines.WORLD_TEXT_EFFECT : WorldRenderPipelines.WORLD_TEXT_EFFECT_DEPTH,
                depth, effectBindings(texture));
        if (mesh == null) return 0.0;
        if (right == null || down == null) {
            Vector3f rv = new Vector3f(1f, 0f, 0f).rotate(RenderState.cameraRotation);
            Vector3f dv = new Vector3f(0f, -1f, 0f).rotate(RenderState.cameraRotation);
            right = new Vec3(rv.x, rv.y, rv.z);
            down = new Vec3(dv.x, dv.y, dv.z);
        }
        double glyphScale = selection.glyphScale();
        double width = font.getWidth(text, text.length()) * glyphScale;
        emitEffectString(mesh, font, text, resolved.centered() ? -width * 0.5 : 0.0, 0.0,
                glyphScale, anchor, right, down, resolved.offsetX(), resolved.offsetY(),
                resolved.worldScale(), resolved.color(), Math.max(0.0f, radius),
                Math.min(1.0f, Math.max(0.0f, opacity)));
        return width * resolved.worldScale();
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
        // Same arithmetic as anchor.add(right.scale(x)).add(up.scale(y)), evaluated on doubles: the old
        // Vec3 chain allocated ~16 objects per glyph.
        final double ax = anchor.x, ay = anchor.y, az = anchor.z;
        final double rx = right.x, ry = right.y, rz = right.z;
        final double ux = up.x, uy = up.y, uz = up.z;
        final int cr = color.r, cg = color.g, cb = color.b, ca = color.a;
        font.emitGlyphs(text, localX, localY, glyphScale, (x0, y0, x1, y1, u0, v0, u1, v1) -> {
            mesh.ensureQuadCapacity();

            double left = offsetX + x0 * worldScale;
            double top = offsetY + y0 * worldScale;
            double right2 = offsetX + x1 * worldScale;
            double bottom = offsetY + y1 * worldScale;

            int i1 = mesh.vec3((ax + rx * left) + ux * top, (ay + ry * left) + uy * top, (az + rz * left) + uz * top)
                    .raw2(u0, v0).color(cr, cg, cb, ca).next();
            int i2 = mesh.vec3((ax + rx * left) + ux * bottom, (ay + ry * left) + uy * bottom, (az + rz * left) + uz * bottom)
                    .raw2(u0, v1).color(cr, cg, cb, ca).next();
            int i3 = mesh.vec3((ax + rx * right2) + ux * bottom, (ay + ry * right2) + uy * bottom, (az + rz * right2) + uz * bottom)
                    .raw2(u1, v1).color(cr, cg, cb, ca).next();
            int i4 = mesh.vec3((ax + rx * right2) + ux * top, (ay + ry * right2) + uy * top, (az + rz * right2) + uz * top)
                    .raw2(u1, v0).color(cr, cg, cb, ca).next();
            mesh.quad(i1, i2, i3, i4);
        });
    }

    /** Only the glyph atlas is necessary: the effect shader gets its MSDF range per vertex. */
    private static Renderer3D.BatchBindings effectBindings(AbstractTexture texture) {
        var view = texture.getTextureView();
        var sampler = texture.getSampler();
        if (cachedEffectBindings != null && cachedEffectView == view && cachedEffectSampler == sampler) {
            return cachedEffectBindings;
        }
        cachedEffectView = view;
        cachedEffectSampler = sampler;
        cachedEffectBindings = Renderer3D.BatchBindings.none().withSampler("u_Texture", view, sampler);
        return cachedEffectBindings;
    }

    private static void emitEffectString(MeshBuilder mesh, GlyphFont font, String text,
                                         double localX, double localY, double glyphScale,
                                         Vec3 anchor, Vec3 right, Vec3 down,
                                         double offsetX, double offsetY, double worldScale,
                                         RenderColor tint, double radius, float signedOpacity) {
        double ax = anchor.x, ay = anchor.y, az = anchor.z;
        double rx = right.x, ry = right.y, rz = right.z;
        double dx = down.x, dy = down.y, dz = down.z;
        int cr = tint.r, cg = tint.g, cb = tint.b, ca = tint.a;
        float mode = font.isMsdf() ? font.getPxRange() : 0.0f;
        font.emitGlyphs(text, localX, localY, glyphScale, (x0, y0, x1, y1, u0, v0, u1, v1) -> {
            if (x1 <= x0 || y1 <= y0) return;
            double pad = radius + 0.75;
            float du = (float) ((u1 - u0) / (x1 - x0));
            float dv = (float) ((v1 - v0) / (y1 - y0));
            float shiftU = (float) (du * radius);
            float shiftV = (float) (dv * radius);
            float minU = Math.min(u0, u1), minV = Math.min(v0, v1);
            float maxU = Math.max(u0, u1), maxV = Math.max(v0, v1);
            mesh.ensureQuadCapacity();
            int a = worldEffectVertex(mesh, ax, ay, az, rx, ry, rz, dx, dy, dz,
                    offsetX + (x0 - pad) * worldScale, offsetY + (y0 - pad) * worldScale,
                    u0 - du * (float) pad, v0 - dv * (float) pad, cr, cg, cb, ca,
                    minU, minV, maxU, maxV, shiftU, shiftV, signedOpacity, mode);
            int b = worldEffectVertex(mesh, ax, ay, az, rx, ry, rz, dx, dy, dz,
                    offsetX + (x0 - pad) * worldScale, offsetY + (y1 + pad) * worldScale,
                    u0 - du * (float) pad, v1 + dv * (float) pad, cr, cg, cb, ca,
                    minU, minV, maxU, maxV, shiftU, shiftV, signedOpacity, mode);
            int c = worldEffectVertex(mesh, ax, ay, az, rx, ry, rz, dx, dy, dz,
                    offsetX + (x1 + pad) * worldScale, offsetY + (y1 + pad) * worldScale,
                    u1 + du * (float) pad, v1 + dv * (float) pad, cr, cg, cb, ca,
                    minU, minV, maxU, maxV, shiftU, shiftV, signedOpacity, mode);
            int d = worldEffectVertex(mesh, ax, ay, az, rx, ry, rz, dx, dy, dz,
                    offsetX + (x1 + pad) * worldScale, offsetY + (y0 - pad) * worldScale,
                    u1 + du * (float) pad, v0 - dv * (float) pad, cr, cg, cb, ca,
                    minU, minV, maxU, maxV, shiftU, shiftV, signedOpacity, mode);
            mesh.quad(a, b, c, d);
        });
    }

    private static int worldEffectVertex(MeshBuilder mesh,
                                         double ax, double ay, double az,
                                         double rx, double ry, double rz,
                                         double dx, double dy, double dz,
                                         double x, double y, float u, float v,
                                         int cr, int cg, int cb, int ca,
                                         float u0, float v0, float u1, float v1,
                                         float shiftU, float shiftV, float opacity, float mode) {
        return mesh.vec3(ax + rx * x + dx * y, ay + ry * x + dy * y, az + rz * x + dz * y)
                .raw2(u, v).color(cr, cg, cb, ca)
                .vec4(u0, v0, u1, v1).vec4(shiftU, shiftV, opacity, mode).next();
    }

    /**
     * Bindings for one glyph atlas are identical for every string drawn with it, and equal bindings
     * merge into a single batch by identity. Keep the last set instead of rebuilding two lists (and an
     * uniform-resolver closure) per string.
     */
    private static Renderer3D.BatchBindings textBindings(GlyphFont font, AbstractTexture texture) {
        var view = texture.getTextureView();
        var sampler = texture.getSampler();
        boolean msdf = font.isMsdf();
        float pxRange = msdf ? font.getPxRange() : 0.0f;
        int atlasWidth = msdf ? font.getAtlasWidth() : 0;
        int atlasHeight = msdf ? font.getAtlasHeight() : 0;
        Renderer3D.BatchBindings cached = cachedBindings;
        if (cached != null && cachedView == view && cachedSampler == sampler && cachedMsdf == msdf
                && cachedPxRange == pxRange && cachedAtlasWidth == atlasWidth && cachedAtlasHeight == atlasHeight) {
            return cached;
        }
        Renderer3D.BatchBindings bindings = Renderer3D.BatchBindings.none().withSampler("u_Texture", view, sampler);
        if (msdf) {
            MsdfUniformKey key = new MsdfUniformKey(pxRange, atlasWidth, atlasHeight);
            bindings = bindings.withUniform("MsdfText", key, () -> {
                MsdfTextUniforms.update(key.pxRange(), key.atlasWidth(), key.atlasHeight());
                return MsdfTextUniforms.get();
            });
        }
        cachedBindings = bindings;
        cachedView = view;
        cachedSampler = sampler;
        cachedMsdf = msdf;
        cachedPxRange = pxRange;
        cachedAtlasWidth = atlasWidth;
        cachedAtlasHeight = atlasHeight;
        return bindings;
    }

    private static CustomTextRenderer resolve(TextRenderer textRenderer) {
        CustomTextRenderer custom = LanguageFallbackTextRenderer.customPrimary(textRenderer);
        if (custom != null) return custom;
        TextRenderer fallback = TextRenderer.get();
        return LanguageFallbackTextRenderer.customPrimary(fallback);
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
