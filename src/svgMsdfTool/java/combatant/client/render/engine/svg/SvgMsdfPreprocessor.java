package combatant.client.render.engine.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Build-time SVG normalizer for MSDF generation.
 *
 * <p>msdfgen's SVG loader consumes a single vector path. Most project icons are stroke-based,
 * so this tool outlines strokes and emits one compound filled path with the original viewBox.</p>
 */
public final class SvgMsdfPreprocessor {
    private SvgMsdfPreprocessor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: SvgMsdfPreprocessor <input.svg> <output.svg>");
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        safeSet(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        safeSet(factory, "http://xml.org/sax/features/external-general-entities", false);
        safeSet(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        safeSet(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Document document = factory.newDocumentBuilder().parse(input);
        Element root = document.getDocumentElement();
        if (root == null) throw new IllegalArgumentException("SVG root missing");

        double[] viewBox = parseViewBox(root);
        Path2D.Double compound = new Path2D.Double(Path2D.WIND_NON_ZERO);
        walk(root, new AffineTransform(), SvgStyle.root(), compound);
        if (compound.getCurrentPoint() == null) {
            throw new IllegalArgumentException("No drawable SVG paths found");
        }

        String pathData = toPathData(compound);
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""
                + fmt(viewBox[0]) + ' ' + fmt(viewBox[1]) + ' ' + fmt(viewBox[2]) + ' ' + fmt(viewBox[3])
                + "\"><path fill=\"#000\" d=\"" + pathData + "\"/></svg>\n";

        File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.writeString(output.toPath(), svg, StandardCharsets.UTF_8);
    }

    private static void safeSet(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    private static void walk(Element node, AffineTransform parentTransform, SvgStyle parentStyle, Path2D.Double out) {
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
                path.transform(transform);

                if (style.fill != null && (((style.fill >>> 24) & 0xFF) > 0 || style.fillOpacity > 0.0f)) {
                    out.append(path, false);
                }
                if (style.stroke != null && style.strokeWidth > 0.0f
                        && (((style.stroke >>> 24) & 0xFF) > 0 || style.strokeOpacity > 0.0f)) {
                    BasicStroke stroke = new BasicStroke(
                            style.strokeWidth,
                            style.lineCap,
                            style.lineJoin,
                            style.miterLimit
                    );
                    Shape stroked = stroke.createStrokedShape(path);
                    out.append(stroked, false);
                }
            }
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

    private static Path2D.Double buildPath(Element element, String tag) {
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

    private static Path2D.Double buildRect(Element e) {
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
        if (rx > 0.0 || ry > 0.0) {
            out.append(new RoundRectangle2D.Double(x, y, w, h, rx * 2.0, ry * 2.0), false);
        } else {
            out.moveTo(x, y);
            out.lineTo(x + w, y);
            out.lineTo(x + w, y + h);
            out.lineTo(x, y + h);
            out.closePath();
        }
        return out;
    }

    private static Path2D.Double buildCircle(Element e) {
        double cx = SvgStyleUtil.parseLength(e.getAttribute("cx"), 0.0);
        double cy = SvgStyleUtil.parseLength(e.getAttribute("cy"), 0.0);
        double r = SvgStyleUtil.parseLength(e.getAttribute("r"), 0.0);
        if (r <= 0.0) return null;
        Path2D.Double out = new Path2D.Double();
        out.append(new Ellipse2D.Double(cx - r, cy - r, 2.0 * r, 2.0 * r), false);
        return out;
    }

    private static Path2D.Double buildEllipse(Element e) {
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

    private static Path2D.Double buildPoly(Element e, boolean closed) {
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

    private static String toPathData(Shape shape) {
        StringBuilder out = new StringBuilder(4096);
        double[] c = new double[6];
        PathIterator it = shape.getPathIterator(null);
        while (!it.isDone()) {
            int segment = it.currentSegment(c);
            switch (segment) {
                case PathIterator.SEG_MOVETO -> out.append('M').append(fmt(c[0])).append(' ').append(fmt(c[1]));
                case PathIterator.SEG_LINETO -> out.append('L').append(fmt(c[0])).append(' ').append(fmt(c[1]));
                case PathIterator.SEG_QUADTO -> out.append('Q')
                        .append(fmt(c[0])).append(' ').append(fmt(c[1])).append(' ')
                        .append(fmt(c[2])).append(' ').append(fmt(c[3]));
                case PathIterator.SEG_CUBICTO -> out.append('C')
                        .append(fmt(c[0])).append(' ').append(fmt(c[1])).append(' ')
                        .append(fmt(c[2])).append(' ').append(fmt(c[3])).append(' ')
                        .append(fmt(c[4])).append(' ').append(fmt(c[5]));
                case PathIterator.SEG_CLOSE -> out.append('Z');
                default -> {
                }
            }
            it.next();
        }
        return out.toString();
    }

    private static String fmt(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value) < 1e-9) value = 0.0;
        return String.format(Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
