/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import combatant.client.render.engine.Texture;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.logging.DebugLog;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public enum SvgMeshBackend {
    ;
    private static final int MAX_CACHE = 96;
    private static final int MAX_TEXTURE_CACHE = 256;
    private static final int MAX_GEOMETRY_CACHE = 256;
    private static final int MAX_TEXTURE_SIDE = 4096;
    private static final int MAX_TEXTURE_PIXELS = 4 * 1024 * 1024;
    private static final int SMALL_TEXTURE_QUANTUM = 8;
    private static final int MEDIUM_TEXTURE_QUANTUM = 16;
    private static final int LARGE_TEXTURE_QUANTUM = 32;
    private static final double EPS = 1e-7;
    private static final int MAX_TRACE_KEYS = 512;
    private static final Set<String> TRACE_ONCE_KEYS = new HashSet<>();
    private static final Map<String, SvgDoc> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SvgDoc> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private static final Map<GeometryKey, CompiledGeometry> GEOMETRY_CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GeometryKey, CompiledGeometry> eldest) {
            if (size() > MAX_GEOMETRY_CACHE) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };
    private static final Map<TextureKey, CompiledTexture> TEXTURE_CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextureKey, CompiledTexture> eldest) {
            if (size() > MAX_TEXTURE_CACHE) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (TEXTURE_CACHE) {
            for (CompiledTexture texture : TEXTURE_CACHE.values()) {
                texture.close();
            }
            TEXTURE_CACHE.clear();
        }
        synchronized (GEOMETRY_CACHE) {
            for (CompiledGeometry geometry : GEOMETRY_CACHE.values()) {
                geometry.close();
            }
            GEOMETRY_CACHE.clear();
        }
        synchronized (TRACE_ONCE_KEYS) {
            TRACE_ONCE_KEYS.clear();
        }
    }

    public static void draw(Renderer2D renderer, Identifier svg, double x, double y, double w, double h) {
        draw(renderer, svg, x, y, w, h, SvgRenderOptions.DEFAULT);
    }

    public static void draw(Renderer2D renderer, Identifier svg, double x, double y, double w, double h, SvgRenderOptions options) {
        if (renderer == null || svg == null || w == 0.0 || h == 0.0) return;
        SvgRenderOptions resolved = options == null ? SvgRenderOptions.DEFAULT : options;
        if (SvgMsdfRegistry.draw(renderer, svg, x, y, w, h, resolved)) return;
        SvgDoc doc = loadFromResource(svg);
        if (doc == null) return;
        render(renderer, doc, x, y, w, h, resolved);
    }

    public static void draw(Renderer2D renderer, Path svgPath, double x, double y, double w, double h) {
        draw(renderer, svgPath, x, y, w, h, SvgRenderOptions.DEFAULT);
    }

    public static void draw(Renderer2D renderer, Path svgPath, double x, double y, double w, double h, SvgRenderOptions options) {
        if (renderer == null || svgPath == null || w == 0.0 || h == 0.0) return;
        SvgDoc doc = loadFromFile(svgPath);
        if (doc == null) return;
        render(renderer, doc, x, y, w, h, options == null ? SvgRenderOptions.DEFAULT : options);
    }

    private static @Nullable SvgDoc loadFromResource(Identifier id) {
        String key = "res:" + id;
        synchronized (CACHE) {
            SvgDoc cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        var opt = mc.getResourceManager().getResource(id);
        if (opt.isEmpty()) return null;
        try (InputStream in = opt.get().open()) {
            SvgDoc parsed = parseSvg(in, key);
            if (parsed == null) return null;
            synchronized (CACHE) {
                CACHE.put(key, parsed);
            }
            return parsed;
        } catch (Exception e) {
            DebugLog.warn("[Combatant][SVG] Resource load failed %s : %s", id, e.getMessage());
            return null;
        }
    }

    private static @Nullable SvgDoc loadFromFile(Path path) {
        String key = "file:" + path.toAbsolutePath();
        synchronized (CACHE) {
            SvgDoc cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        try (InputStream in = Files.newInputStream(path)) {
            SvgDoc parsed = parseSvg(in, key);
            if (parsed == null) return null;
            synchronized (CACHE) {
                CACHE.put(key, parsed);
            }
            return parsed;
        } catch (IOException e) {
            DebugLog.warn("[Combatant][SVG] File load failed %s : %s", path, e.getMessage());
            return null;
        }
    }

    private static @Nullable SvgDoc parseSvg(InputStream in, String cacheKey) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            safeSet(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
            safeSet(f, "http://xml.org/sax/features/external-general-entities", false);
            safeSet(f, "http://xml.org/sax/features/external-parameter-entities", false);
            safeSet(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(in);
            Element root = doc.getDocumentElement();
            if (root == null) return null;

            double[] vb = parseViewBox(root);
            SvgStyle rootStyle = SvgStyle.root();
            ArrayList<SvgShape> shapes = new ArrayList<>();
            walk(root, new AffineTransform(), rootStyle, shapes);
            return new SvgDoc(cacheKey, vb[0], vb[1], vb[2], vb[3], shapes);
        } catch (Exception e) {
            DebugLog.warn("[Combatant][SVG] Parse failed: %s", e.getMessage());
            return null;
        }
    }

    private static void safeSet(DocumentBuilderFactory f, String name, boolean value) {
        try {
            f.setFeature(name, value);
        } catch (Exception ignored) {
        }
    }

    private static double[] parseViewBox(Element root) {
        String viewBox = root.getAttribute("viewBox");
        if (viewBox != null && !viewBox.isBlank()) {
            double[] vals = SvgStyleUtil.parseNumberList(viewBox);
            if (vals.length == 4 && vals[2] > 0.0 && vals[3] > 0.0) {
                return vals;
            }
        }
        double w = SvgStyleUtil.parseLength(root.getAttribute("width"), 24.0);
        double h = SvgStyleUtil.parseLength(root.getAttribute("height"), 24.0);
        if (w <= 0.0) w = 24.0;
        if (h <= 0.0) h = 24.0;
        return new double[]{0.0, 0.0, w, h};
    }

    private static void walk(Element node,
                             AffineTransform parentTransform,
                             SvgStyle parentStyle,
                             List<SvgShape> out) {
        String tag = node.getTagName();
        if (tag == null || tag.isBlank()) return;
        tag = tag.toLowerCase(Locale.ROOT);

        SvgStyle style = parentStyle.copy();
        SvgStyleUtil.applyElementStyle(style, node);
        if (!style.visible) return;

        AffineTransform transform = new AffineTransform(parentTransform);
        transform.concatenate(SvgStyleUtil.parseTransform(node.getAttribute("transform")));

        switch (tag) {
            case "svg", "g", "a", "symbol" -> {
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child instanceof Element childElement) {
                        walk(childElement, transform, style, out);
                    }
                }
            }
            case "defs" -> {
            }
            default -> {
                Path2D.Double path = buildPath(node, tag);
                if (path == null) return;
                int winding = "evenodd".equalsIgnoreCase(style.fillRule) ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
                path.setWindingRule(winding);
                out.add(new SvgShape(path, transform, style.copy(), winding));
            }
        }
    }

    private static @Nullable Path2D.Double buildPath(Element element, String tag) {
        return switch (tag) {
            case "path" -> {
                String d = element.getAttribute("d");
                if (d == null || d.isBlank()) yield null;
                yield SvgPathParser.parse(d);
            }
            case "rect" -> buildRect(element);
            case "circle" -> buildCircle(element);
            case "ellipse" -> buildEllipse(element);
            case "line" -> buildLine(element);
            case "polyline" -> buildPoly(element, false);
            case "polygon" -> buildPoly(element, true);
            default -> null;
        };
    }

    private static @Nullable Path2D.Double buildRect(Element e) {
        double x = SvgStyleUtil.parseLength(e.getAttribute("x"), 0.0);
        double y = SvgStyleUtil.parseLength(e.getAttribute("y"), 0.0);
        double w = SvgStyleUtil.parseLength(e.getAttribute("width"), 0.0);
        double h = SvgStyleUtil.parseLength(e.getAttribute("height"), 0.0);
        if (w <= 0.0 || h <= 0.0) return null;
        double rx = SvgStyleUtil.parseLength(e.getAttribute("rx"), -1.0);
        double ry = SvgStyleUtil.parseLength(e.getAttribute("ry"), -1.0);
        if (rx < 0.0 && ry >= 0.0) rx = ry;
        if (ry < 0.0 && rx >= 0.0) ry = rx;
        if (rx < 0.0) rx = 0.0;
        if (ry < 0.0) ry = 0.0;
        rx = Math.min(rx, w * 0.5);
        ry = Math.min(ry, h * 0.5);
        Path2D.Double out = new Path2D.Double();
        if (rx > 0.0 || ry > 0.0) out.append(new RoundRectangle2D.Double(x, y, w, h, rx * 2.0, ry * 2.0), false);
        else {
            out.moveTo(x, y);
            out.lineTo(x + w, y);
            out.lineTo(x + w, y + h);
            out.lineTo(x, y + h);
            out.closePath();
        }
        return out;
    }

    private static @Nullable Path2D.Double buildCircle(Element e) {
        double cx = SvgStyleUtil.parseLength(e.getAttribute("cx"), 0.0);
        double cy = SvgStyleUtil.parseLength(e.getAttribute("cy"), 0.0);
        double r = SvgStyleUtil.parseLength(e.getAttribute("r"), 0.0);
        if (r <= 0.0) return null;
        Path2D.Double out = new Path2D.Double();
        out.append(new Ellipse2D.Double(cx - r, cy - r, 2.0 * r, 2.0 * r), false);
        return out;
    }

    private static @Nullable Path2D.Double buildEllipse(Element e) {
        double cx = SvgStyleUtil.parseLength(e.getAttribute("cx"), 0.0);
        double cy = SvgStyleUtil.parseLength(e.getAttribute("cy"), 0.0);
        double rx = SvgStyleUtil.parseLength(e.getAttribute("rx"), 0.0);
        double ry = SvgStyleUtil.parseLength(e.getAttribute("ry"), 0.0);
        if (rx <= 0.0 || ry <= 0.0) return null;
        Path2D.Double out = new Path2D.Double();
        out.append(new Ellipse2D.Double(cx - rx, cy - ry, 2.0 * rx, 2.0 * ry), false);
        return out;
    }

    private static Path2D.Double buildLine(Element e) {
        double x1 = SvgStyleUtil.parseLength(e.getAttribute("x1"), 0.0);
        double y1 = SvgStyleUtil.parseLength(e.getAttribute("y1"), 0.0);
        double x2 = SvgStyleUtil.parseLength(e.getAttribute("x2"), 0.0);
        double y2 = SvgStyleUtil.parseLength(e.getAttribute("y2"), 0.0);
        Path2D.Double out = new Path2D.Double();
        out.moveTo(x1, y1);
        out.lineTo(x2, y2);
        return out;
    }

    private static @Nullable Path2D.Double buildPoly(Element e, boolean closed) {
        String points = e.getAttribute("points");
        if (points == null || points.isBlank()) return null;
        double[] vals = SvgStyleUtil.parseNumberList(points);
        if (vals.length < 4) return null;
        Path2D.Double out = new Path2D.Double();
        out.moveTo(vals[0], vals[1]);
        for (int i = 2; i + 1 < vals.length; i += 2) {
            out.lineTo(vals[i], vals[i + 1]);
        }
        if (closed) out.closePath();
        return out;
    }

    private static void render(Renderer2D renderer,
                               SvgDoc doc,
                               double x,
                               double y,
                               double width,
                               double height,
                               SvgRenderOptions options) {
        if (doc.vw <= EPS || doc.vh <= EPS) return;

        if (options.textureCache()) {
            CompiledTexture texture = getOrRasterize(doc, width, height, options);
            if (texture != null && texture.isReady()) {
                traceOnce("svg-texture-draw:" + doc.cacheKey + ':' + texture.widthPx + 'x' + texture.heightPx + ':' + texture.scale,
                        "[Combatant][SVG] GPU texture path active: doc=%s tex=%dx%d logical=%.2fx%.2f scale=%d",
                        doc.cacheKey, texture.widthPx, texture.heightPx, Math.abs(width), Math.abs(height), texture.scale);
                renderer.textureQuad(texture.texture.getTextureView(), texture.texture.getSampler(), x, y, width, height, textureTint(options));
                return;
            }

            traceOnce("svg-texture-fallback:" + doc.cacheKey + ':' + quantize(width, 16.0) + 'x' + quantize(height, 16.0),
                    "[Combatant][SVG] GPU texture path unavailable, falling back to mesh: doc=%s logical=%.2fx%.2f",
                    doc.cacheKey, Math.abs(width), Math.abs(height));
        }

        CompiledGeometry geometry = getOrCompile(doc, width, height, options);
        renderer.quadBatch(batch -> {
            for (QuadCmd quad : geometry.quads) {
                batch.quad(x + quad.x, y + quad.y, quad.w, quad.h, quad.argb);
            }
        });
    }

    private static @Nullable CompiledTexture getOrRasterize(SvgDoc doc,
                                                            double width,
                                                            double height,
                                                            SvgRenderOptions options) {
        double logicalW = Math.abs(width);
        double logicalH = Math.abs(height);
        if (logicalW <= EPS || logicalH <= EPS) return null;

        PhysicalSize physical = physicalDrawSize(logicalW, logicalH);
        int scale = rasterScaleFor(physical.width, physical.height, options.rasterScale());
        int texW = quantizeTextureDimension(Math.max(1, (int) Math.ceil(physical.width * scale)));
        int texH = quantizeTextureDimension(Math.max(1, (int) Math.ceil(physical.height * scale)));
        if (texW <= 0 || texH <= 0) return null;

        int gradientAngle1024 = options.colorMode() == SvgColorMode.GRADIENT_LINEAR
                ? quantize(options.gradientAngleDegrees(), 1024.0)
                : 0;
        int alpha1024 = quantize(options.alpha(), 1024.0);
        TextureKey key = new TextureKey(
                doc.cacheKey,
                texW,
                texH,
                options.colorMode(),
                scale,
                options.colorMode() == SvgColorMode.GRADIENT_LINEAR ? options.gradientStartArgb() : 0,
                options.colorMode() == SvgColorMode.GRADIENT_LINEAR ? options.gradientEndArgb() : 0,
                gradientAngle1024,
                options.colorMode() == SvgColorMode.GRADIENT_LINEAR ? alpha1024 : 0
        );

        synchronized (TEXTURE_CACHE) {
            CompiledTexture cached = TEXTURE_CACHE.get(key);
            if (cached != null) {
                traceOnce("svg-texture-cache-hit:" + key,
                        "[Combatant][SVG] Texture cache hit: doc=%s tex=%dx%d scale=%d",
                        doc.cacheKey, texW, texH, scale);
                return cached;
            }
        }

        traceOnce("svg-texture-rasterize:" + key,
                "[Combatant][SVG] Rasterizing AA texture: doc=%s tex=%dx%d logical=%.2fx%.2f physical=%.2fx%.2f scale=%d",
                doc.cacheKey, texW, texH, logicalW, logicalH, physical.width, physical.height, scale);
        CompiledTexture compiled = rasterizeTexture(doc, texW, texH, options, scale);
        if (compiled == null) return null;

        synchronized (TEXTURE_CACHE) {
            CompiledTexture raced = TEXTURE_CACHE.get(key);
            if (raced != null) {
                compiled.close();
                return raced;
            }
            TEXTURE_CACHE.put(key, compiled);
        }
        return compiled;
    }

    private static PhysicalSize physicalDrawSize(double logicalW, double logicalH) {
        ViewportContext viewport = ViewportContext.current();
        if (viewport == null || viewport.width() <= EPS || viewport.height() <= EPS) {
            return new PhysicalSize(logicalW, logicalH);
        }

        double scaleX = viewport.framebufferWidth() / Math.max(EPS, Math.abs(viewport.width()));
        double scaleY = viewport.framebufferHeight() / Math.max(EPS, Math.abs(viewport.height()));
        if (!Double.isFinite(scaleX) || scaleX <= EPS) scaleX = 1.0;
        if (!Double.isFinite(scaleY) || scaleY <= EPS) scaleY = 1.0;
        return new PhysicalSize(logicalW * scaleX, logicalH * scaleY);
    }

    private static int rasterScaleFor(double width, double height, int requestedScale) {
        int scale = Math.max(1, Math.min(4, requestedScale));
        while (scale > 1) {
            int texW = quantizeTextureDimension(Math.max(1, (int) Math.ceil(width * scale)));
            int texH = quantizeTextureDimension(Math.max(1, (int) Math.ceil(height * scale)));
            long pixels = (long) texW * (long) texH;
            if (texW <= MAX_TEXTURE_SIDE && texH <= MAX_TEXTURE_SIDE && pixels <= MAX_TEXTURE_PIXELS) {
                return scale;
            }
            scale--;
        }
        return 1;
    }

    private static int quantizeTextureDimension(int pixels) {
        if (pixels <= 1) return 1;
        int quantum = pixels <= 128
                ? SMALL_TEXTURE_QUANTUM
                : pixels <= 512 ? MEDIUM_TEXTURE_QUANTUM : LARGE_TEXTURE_QUANTUM;
        return ((pixels + quantum - 1) / quantum) * quantum;
    }

    private static @Nullable CompiledTexture rasterizeTexture(SvgDoc doc,
                                                              int texW,
                                                              int texH,
                                                              SvgRenderOptions options,
                                                              int scale) {
        try {
            BufferedImage image = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                AffineTransform view = new AffineTransform();
                view.scale(texW / doc.vw, texH / doc.vh);
                view.translate(-doc.vx, -doc.vy);

                SvgRenderOptions rasterOptions = options.withAlpha(1.0f);
                boolean overrideMask = options.colorMode() == SvgColorMode.OVERRIDE;
                for (SvgShape shape : doc.shapes) {
                    AffineTransform tx = new AffineTransform(view);
                    tx.concatenate(shape.transform);
                    paintShape(g, shape, tx, rasterOptions, overrideMask, texW, texH);
                }
            } finally {
                g.dispose();
            }

            Texture texture = uploadTexture(image);
            if (texture != null) {
                traceOnce("svg-texture-upload:" + doc.cacheKey + ':' + texW + 'x' + texH + ':' + scale,
                        "[Combatant][SVG] Uploaded GPU texture: doc=%s tex=%dx%d scale=%d",
                        doc.cacheKey, texW, texH, scale);
            }
            return texture == null ? null : new CompiledTexture(texture, texW, texH, scale);
        } catch (Throwable t) {
            DebugLog.warn("[Combatant][SVG] Texture rasterization failed: %s", t.getMessage());
            return null;
        }
    }

    private static void paintShape(Graphics2D g,
                                   SvgShape shape,
                                   AffineTransform tx,
                                   SvgRenderOptions options,
                                   boolean overrideMask,
                                   int viewportW,
                                   int viewportH) {
        Shape transformed = tx.createTransformedShape(shape.path);
        boolean gradient = options.colorMode() == SvgColorMode.GRADIENT_LINEAR;
        int fill = overrideMask || gradient
                ? resolveMaskPaint(shape.style.fill, shape.style.fillOpacity, shape.style.groupOpacity, options.alpha())
                : SvgStyleUtil.resolvePaint(shape.style.fill, shape.style.fillOpacity, shape.style.groupOpacity, options);
        if (((fill >>> 24) & 0xFF) > 0) {
            if (gradient) g.setPaint(linearGradientPaint(options, viewportW, viewportH, fill));
            else g.setColor(new Color(fill, true));
            g.fill(transformed);
        }

        int stroke = overrideMask || gradient
                ? resolveMaskPaint(shape.style.stroke, shape.style.strokeOpacity, shape.style.groupOpacity, options.alpha())
                : SvgStyleUtil.resolvePaint(shape.style.stroke, shape.style.strokeOpacity, shape.style.groupOpacity, options);
        if (((stroke >>> 24) & 0xFF) <= 0) return;

        double scale = estimateScale(tx);
        float strokeWidth = Math.max(0.0f, (float) (shape.style.strokeWidth * scale));
        if (strokeWidth <= 0.0f) return;

        BasicStroke bs = new BasicStroke(strokeWidth, shape.style.lineCap, shape.style.lineJoin, shape.style.miterLimit);
        Shape stroked = bs.createStrokedShape(transformed);
        if (gradient) g.setPaint(linearGradientPaint(options, viewportW, viewportH, stroke));
        else g.setColor(new Color(stroke, true));
        g.fill(stroked);
    }

    private static int resolveMaskPaint(Integer paint, float channelOpacity, float groupOpacity, float alpha) {
        if (paint == null) return 0;
        int baseA = (paint >>> 24) & 0xFF;
        int outAlpha = clamp255(Math.round(baseA * clamp01(channelOpacity) * clamp01(groupOpacity) * clamp01(alpha)));
        return (outAlpha << 24) | 0x00FFFFFF;
    }

    private static Paint linearGradientPaint(SvgRenderOptions options, int viewportW, int viewportH, int alphaMask) {
        float w = Math.max(1f, viewportW);
        float h = Math.max(1f, viewportH);
        double angle = Math.toRadians(options.gradientAngleDegrees());
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        float extent = Math.max(1f, Math.abs(dx) * w + Math.abs(dy) * h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        Color start = gradientColor(options.gradientStartArgb(), alphaMask);
        Color end = gradientColor(options.gradientEndArgb(), alphaMask);
        return new LinearGradientPaint(
                cx - dx * extent * 0.5f,
                cy - dy * extent * 0.5f,
                cx + dx * extent * 0.5f,
                cy + dy * extent * 0.5f,
                new float[]{0f, 1f},
                new Color[]{start, end}
        );
    }

    private static Color gradientColor(int argb, int alphaMask) {
        int maskA = (alphaMask >>> 24) & 0xFF;
        int colorA = (argb >>> 24) & 0xFF;
        int a = clamp255(Math.round(maskA * (colorA / 255.0f)));
        return new Color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, a);
    }

    private static int textureTint(SvgRenderOptions options) {
        if (options.colorMode() == SvgColorMode.OVERRIDE) {
            int override = options.overrideArgb();
            int alpha = clamp255(Math.round(((override >>> 24) & 0xFF) * clamp01(options.alpha())));
            return (alpha << 24) | (override & 0x00FFFFFF);
        }

        int alpha = clamp255(Math.round(255.0f * clamp01(options.alpha())));
        return (alpha << 24) | 0x00FFFFFF;
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) return 0.0f;
        if (value >= 1.0f) return 1.0f;
        return value;
    }

    private static int clamp255(int value) {
        if (value <= 0) return 0;
        if (value >= 255) return 255;
        return value;
    }

    private static @Nullable Texture uploadTexture(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        bleedTransparentRgb(pixels, w, h);

        ByteBuffer buffer = MemoryUtil.memAlloc(w * h * 4);
        Texture texture = null;
        try {
            for (int argb : pixels) {
                buffer.put((byte) ((argb >>> 16) & 0xFF));
                buffer.put((byte) ((argb >>> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >>> 24) & 0xFF));
            }
            buffer.flip();

            texture = new Texture(w, h, GpuFormat.RGBA8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR, AddressMode.CLAMP_TO_EDGE);
            texture.upload(buffer);
            return texture;
        } catch (Throwable t) {
            if (texture != null) {
                try {
                    texture.close();
                } catch (Throwable ignored) {
                }
            }
            DebugLog.warn("[Combatant][SVG] Texture upload failed: %s", t.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void bleedTransparentRgb(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0) return;

        for (int pass = 0; pass < 3; pass++) {
            int[] source = pixels.clone();
            boolean changed = false;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (((source[index] >>> 24) & 0xFF) != 0) continue;

                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int count = 0;

                    for (int oy = -1; oy <= 1; oy++) {
                        int ny = y + oy;
                        if (ny < 0 || ny >= height) continue;

                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = x + ox;
                            if (nx < 0 || nx >= width) continue;

                            int neighbor = source[ny * width + nx];
                            int a = (neighbor >>> 24) & 0xFF;
                            if (a == 0) continue;

                            r += (neighbor >>> 16) & 0xFF;
                            g += (neighbor >>> 8) & 0xFF;
                            b += neighbor & 0xFF;
                            count++;
                        }
                    }

                    if (count > 0) {
                        pixels[index] = ((r / count) << 16) | ((g / count) << 8) | (b / count);
                        changed = true;
                    }
                }
            }

            if (!changed) return;
        }
    }

    private static CompiledGeometry getOrCompile(SvgDoc doc,
                                                 double width,
                                                 double height,
                                                 SvgRenderOptions options) {
        int w16 = quantize(width, 16.0);
        int h16 = quantize(height, 16.0);
        int alpha1024 = quantize(options.alpha(), 1024.0);
        int flatness1024 = quantize(options.curveFlatness(), 1024.0);
        int gradientAngle1024 = quantize(options.gradientAngleDegrees(), 1024.0);
        GeometryKey key = new GeometryKey(
                doc.cacheKey,
                w16,
                h16,
                options.colorMode(),
                options.overrideArgb(),
                options.gradientStartArgb(),
                options.gradientEndArgb(),
                gradientAngle1024,
                alpha1024,
                flatness1024
        );

        synchronized (GEOMETRY_CACHE) {
            CompiledGeometry cached = GEOMETRY_CACHE.get(key);
            if (cached != null) {
                cached.hits++;
                return cached;
            }
        }

        CompiledGeometry compiled = compileGeometry(doc, width, height, options);
        synchronized (GEOMETRY_CACHE) {
            CompiledGeometry raced = GEOMETRY_CACHE.get(key);
            if (raced != null) {
                raced.hits++;
                return raced;
            }
            compiled.hits++;
            GEOMETRY_CACHE.put(key, compiled);
        }
        return compiled;
    }

    private static int quantize(double value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static CompiledGeometry compileGeometry(SvgDoc doc,
                                                    double width,
                                                    double height,
                                                    SvgRenderOptions options) {
        ArrayList<QuadCmd> quads = new ArrayList<>(512);
        QuadSink sink = (x, y, w, h, argb) -> quads.add(new QuadCmd(x, y, w, h, argb));

        AffineTransform view = new AffineTransform();
        view.scale(width / doc.vw, height / doc.vh);
        view.translate(-doc.vx, -doc.vy);

        for (SvgShape shape : doc.shapes) {
            AffineTransform tx = new AffineTransform(view);
            tx.concatenate(shape.transform);
            drawShape(shape, tx, width, height, options, sink);
        }

        return new CompiledGeometry(quads);
    }

    private static void drawShape(SvgShape shape, AffineTransform tx, double viewportW, double viewportH, SvgRenderOptions options, QuadSink sink) {
        Shape transformed = tx.createTransformedShape(shape.path);
        int fill = SvgStyleUtil.resolvePaint(shape.style.fill, shape.style.fillOpacity, shape.style.groupOpacity, options);
        if (((fill >>> 24) & 0xFF) > 0) {
            if (options.colorMode() == SvgColorMode.GRADIENT_LINEAR) {
                fillShapeGradient(transformed, shape.windingRule, fill, options.curveFlatness(), viewportW, viewportH, options, sink);
            } else {
                fillShape(transformed, shape.windingRule, fill, options.curveFlatness(), sink);
            }
        }

        int stroke = SvgStyleUtil.resolvePaint(shape.style.stroke, shape.style.strokeOpacity, shape.style.groupOpacity, options);
        if (((stroke >>> 24) & 0xFF) <= 0) return;

        double scale = estimateScale(tx);
        float strokeWidth = Math.max(0.0f, (float) (shape.style.strokeWidth * scale));
        if (strokeWidth <= 0.0f) return;

        BasicStroke bs = new BasicStroke(strokeWidth, shape.style.lineCap, shape.style.lineJoin, shape.style.miterLimit);
        Shape stroked = bs.createStrokedShape(transformed);
        if (options.colorMode() == SvgColorMode.GRADIENT_LINEAR) {
            fillShapeGradient(stroked, Path2D.WIND_NON_ZERO, stroke, options.curveFlatness(), viewportW, viewportH, options, sink);
        } else {
            fillShape(stroked, Path2D.WIND_NON_ZERO, stroke, options.curveFlatness(), sink);
        }
    }

    private static double estimateScale(AffineTransform tx) {
        double sx = Math.hypot(tx.getScaleX(), tx.getShearX());
        double sy = Math.hypot(tx.getScaleY(), tx.getShearY());
        if (sx <= EPS && sy <= EPS) return 1.0;
        if (sx <= EPS) return sy;
        if (sy <= EPS) return sx;
        return 0.5 * (sx + sy);
    }

    private static void fillShape(Shape shape, int winding, int argb, float flatness, QuadSink sink) {
        fillShapeInternal(shape, winding, argb, flatness, 1.0, 1.0, null, sink);
    }

    private static void fillShapeGradient(Shape shape, int winding, int alphaMask, float flatness, double viewportW, double viewportH, SvgRenderOptions options, QuadSink sink) {
        fillShapeInternal(shape, winding, alphaMask, flatness, viewportW, viewportH, options, sink);
    }

    private static void fillShapeInternal(Shape shape, int winding, int argb, float flatness, double viewportW, double viewportH, SvgRenderOptions gradientOptions, QuadSink sink) {
        ArrayList<Edge> edges = new ArrayList<>(128);
        Bounds b = collectEdges(shape.getPathIterator(null, Math.max(0.05, flatness)), edges);
        if (edges.isEmpty()) return;

        int yMin = (int) Math.floor(b.minY);
        int yMax = (int) Math.ceil(b.maxY);
        if (yMax <= yMin) return;

        ArrayList<Crossing> xs = new ArrayList<>(64);
        boolean evenOdd = winding == Path2D.WIND_EVEN_ODD;

        for (int y = yMin; y < yMax; y++) {
            double scanY = y + 0.5;
            xs.clear();
            for (Edge e : edges) {
                if (Math.abs(e.y2 - e.y1) <= EPS) continue;
                boolean up = e.y1 <= scanY && e.y2 > scanY;
                boolean down = e.y2 <= scanY && e.y1 > scanY;
                if (!up && !down) continue;
                double x = e.x1 + (scanY - e.y1) * (e.x2 - e.x1) / (e.y2 - e.y1);
                xs.add(new Crossing(x, e.wind));
            }
            if (xs.isEmpty()) continue;
            xs.sort((a, c) -> Double.compare(a.x, c.x));

            if (evenOdd) {
                for (int i = 0; i + 1 < xs.size(); i += 2) {
                    double x0 = xs.get(i).x;
                    double x1 = xs.get(i + 1).x;
                    if (x1 - x0 > EPS) sink.quad(x0, y, x1 - x0, 1.0, gradientOptions == null ? argb : gradientColor(gradientOptions, argb, x0 + (x1 - x0) * 0.5, scanY, viewportW, viewportH));
                }
                continue;
            }

            int wind = 0;
            double start = 0.0;
            for (Crossing c : xs) {
                int prev = wind;
                wind += c.wind;
                if (prev == 0 && wind != 0) start = c.x;
                else if (prev != 0 && wind == 0) {
                    if (c.x - start > EPS) sink.quad(start, y, c.x - start, 1.0, gradientOptions == null ? argb : gradientColor(gradientOptions, argb, start + (c.x - start) * 0.5, scanY, viewportW, viewportH));
                }
            }
        }
    }

    private static int gradientColor(SvgRenderOptions options, int alphaMask, double x, double y, double viewportW, double viewportH) {
        double angle = Math.toRadians(options.gradientAngleDegrees());
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double w = Math.max(1.0, Math.abs(viewportW));
        double h = Math.max(1.0, Math.abs(viewportH));
        double extent = Math.abs(dx) * w + Math.abs(dy) * h;
        double centered = (x - w * 0.5) * dx + (y - h * 0.5) * dy;
        float t = clamp01((float) (centered / Math.max(1.0, extent) + 0.5));
        int mixed = mixArgb(options.gradientStartArgb(), options.gradientEndArgb(), t);
        int maskA = (alphaMask >>> 24) & 0xFF;
        int mixedA = (mixed >>> 24) & 0xFF;
        int a = clamp255(Math.round(maskA * (mixedA / 255.0f)));
        return (a << 24) | (mixed & 0x00FFFFFF);
    }

    private static int mixArgb(int a, int b, float t) {
        float k = clamp01(t);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * k);
        int or = Math.round(ar + (br - ar) * k);
        int og = Math.round(ag + (bg - ag) * k);
        int ob = Math.round(ab + (bb - ab) * k);
        return (clamp255(oa) << 24) | (clamp255(or) << 16) | (clamp255(og) << 8) | clamp255(ob);
    }

    private static Bounds collectEdges(PathIterator it, List<Edge> out) {
        double[] seg = new double[6];
        double sx = 0.0;
        double sy = 0.0;
        double px = 0.0;
        double py = 0.0;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        while (!it.isDone()) {
            int type = it.currentSegment(seg);
            switch (type) {
                case PathIterator.SEG_MOVETO -> {
                    sx = seg[0];
                    sy = seg[1];
                    px = sx;
                    py = sy;
                    minY = Math.min(minY, sy);
                    maxY = Math.max(maxY, sy);
                }
                case PathIterator.SEG_LINETO -> {
                    addEdge(out, px, py, seg[0], seg[1]);
                    px = seg[0];
                    py = seg[1];
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                }
                case PathIterator.SEG_CLOSE -> {
                    addEdge(out, px, py, sx, sy);
                    px = sx;
                    py = sy;
                }
                default -> {
                }
            }
            it.next();
        }

        if (!Double.isFinite(minY) || !Double.isFinite(maxY)) return new Bounds(0.0, 0.0);
        return new Bounds(minY, maxY);
    }

    private static void addEdge(List<Edge> out, double x1, double y1, double x2, double y2) {
        if (Math.abs(x1 - x2) <= EPS && Math.abs(y1 - y2) <= EPS) return;
        out.add(new Edge(x1, y1, x2, y2, y1 < y2 ? 1 : -1));
    }

    private static void traceOnce(String key, String message, Object... args) {
        if (!DebugLog.isEnabled()) return;

        synchronized (TRACE_ONCE_KEYS) {
            if (TRACE_ONCE_KEYS.size() >= MAX_TRACE_KEYS) {
                TRACE_ONCE_KEYS.clear();
            }
            if (!TRACE_ONCE_KEYS.add(key)) {
                return;
            }
        }

        DebugLog.renderThread(message, args);
    }

    @FunctionalInterface
    private interface QuadSink {
        void quad(double x, double y, double w, double h, int argb);
    }

    private record SvgDoc(String cacheKey, double vx, double vy, double vw, double vh, List<SvgShape> shapes) {
    }

    private record SvgShape(Path2D.Double path, AffineTransform transform, SvgStyle style, int windingRule) {
    }

    private record Edge(double x1, double y1, double x2, double y2, int wind) {
    }

    private record Crossing(double x, int wind) {
    }

    private record Bounds(double minY, double maxY) {
    }

    private record PhysicalSize(double width, double height) {
    }

    private record TextureKey(String cacheKey,
                              int widthPx,
                              int heightPx,
                              SvgColorMode colorMode,
                              int rasterScale,
                              int gradientStartArgb,
                              int gradientEndArgb,
                              int gradientAngleQ1024,
                              int alphaQ1024) {
    }

    private record GeometryKey(String cacheKey,
                               int widthQ16,
                               int heightQ16,
                               SvgColorMode colorMode,
                               int overrideArgb,
                               int gradientStartArgb,
                               int gradientEndArgb,
                               int gradientAngleQ1024,
                               int alphaQ1024,
                               int flatnessQ1024) {
    }

    private record QuadCmd(double x, double y, double w, double h, int argb) {
    }

    private record CompiledTexture(Texture texture, int widthPx, int heightPx, int scale) {

        private boolean isReady() {
            return texture != null && texture.isReady();
        }

        private void close() {
            if (texture != null) {
                try {
                    texture.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static final class CompiledGeometry {
        private final List<QuadCmd> quads;
        private int hits;

        private CompiledGeometry(List<QuadCmd> quads) {
            this.quads = quads;
        }

        private void close() {
        }
    }
}
