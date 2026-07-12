/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import java.awt.geom.Path2D;

final class SvgPathParser {
    private static final double EPS = 1e-7;

    private final String data;
    private int index;
    private char command;

    private double cx;
    private double cy;
    private double sx;
    private double sy;
    private double lastCubicX;
    private double lastCubicY;
    private double lastQuadX;
    private double lastQuadY;
    private char prevCmd;

    private SvgPathParser(String data) {
        this.data = data;
    }

    static Path2D.Double parse(String data) {
        return new SvgPathParser(data).parseInternal();
    }

    private static void arcTo(Path2D.Double path,
                              double x1, double y1,
                              double x2, double y2,
                              double rx, double ry,
                              double rotDeg,
                              boolean largeArc,
                              boolean sweep) {
        if (Math.abs(x1 - x2) < EPS && Math.abs(y1 - y2) < EPS) return;
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx < EPS || ry < EPS) {
            path.lineTo(x2, y2);
            return;
        }

        double phi = Math.toRadians(rotDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x1 - x2) * 0.5;
        double dy2 = (y1 - y2) * 0.5;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double rxSq = rx * rx;
        double rySq = ry * ry;
        double x1pSq = x1p * x1p;
        double y1pSq = y1p * y1p;
        double lambda = x1pSq / rxSq + y1pSq / rySq;
        if (lambda > 1.0) {
            double s = Math.sqrt(lambda);
            rx *= s;
            ry *= s;
            rxSq = rx * rx;
            rySq = ry * ry;
        }

        double sign = (largeArc == sweep) ? -1.0 : 1.0;
        double num = rxSq * rySq - rxSq * y1pSq - rySq * x1pSq;
        double den = rxSq * y1pSq + rySq * x1pSq;
        double coef = den <= EPS ? 0.0 : sign * Math.sqrt(Math.max(0.0, num / den));

        double cxp = coef * (rx * y1p / ry);
        double cyp = coef * (-ry * x1p / rx);
        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) * 0.5;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) * 0.5;

        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;
        double theta1 = angleBetween(1.0, 0.0, ux, uy);
        double dTheta = angleBetween(ux, uy, vx, vy);
        if (!sweep && dTheta > 0.0) dTheta -= Math.PI * 2.0;
        else if (sweep && dTheta < 0.0) dTheta += Math.PI * 2.0;

        int segments = (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 2.0));
        double seg = dTheta / segments;
        double t = theta1;
        for (int i = 0; i < segments; i++) {
            double t2 = t + seg;
            appendArcCubic(path, cx, cy, rx, ry, cosPhi, sinPhi, t, t2);
            t = t2;
        }
    }

    private static void appendArcCubic(Path2D.Double path,
                                       double cx, double cy,
                                       double rx, double ry,
                                       double cosPhi, double sinPhi,
                                       double t1, double t2) {
        double dt = t2 - t1;
        double alpha = Math.sin(dt) * (Math.sqrt(4.0 + 3.0 * Math.pow(Math.tan(dt * 0.5), 2.0)) - 1.0) / 3.0;
        double cosT1 = Math.cos(t1);
        double sinT1 = Math.sin(t1);
        double cosT2 = Math.cos(t2);
        double sinT2 = Math.sin(t2);
        double c1x = cosT1 - alpha * sinT1;
        double c1y = sinT1 + alpha * cosT1;
        double c2x = cosT2 + alpha * sinT2;
        double c2y = sinT2 - alpha * cosT2;
        double[] p1 = map(cx, cy, rx, ry, cosPhi, sinPhi, c1x, c1y);
        double[] p2 = map(cx, cy, rx, ry, cosPhi, sinPhi, c2x, c2y);
        double[] p = map(cx, cy, rx, ry, cosPhi, sinPhi, cosT2, sinT2);
        path.curveTo(p1[0], p1[1], p2[0], p2[1], p[0], p[1]);
    }

    private static double[] map(double cx, double cy, double rx, double ry, double cosPhi, double sinPhi, double x, double y) {
        double px = cx + rx * cosPhi * x - ry * sinPhi * y;
        double py = cy + rx * sinPhi * x + ry * cosPhi * y;
        return new double[]{px, py};
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (len <= EPS) return 0.0;
        double cos = Math.max(-1.0, Math.min(1.0, dot / len));
        double angle = Math.acos(cos);
        double cross = ux * vy - uy * vx;
        return cross < 0 ? -angle : angle;
    }

    private static boolean isCommand(char c) {
        return switch (c) {
            case 'M', 'm', 'Z', 'z', 'L', 'l', 'H', 'h', 'V', 'v',
                 'C', 'c', 'S', 's', 'Q', 'q', 'T', 't', 'A', 'a' -> true;
            default -> false;
        };
    }

    private Path2D.Double parseInternal() {
        Path2D.Double out = new Path2D.Double(Path2D.WIND_NON_ZERO);

        while (true) {
            skipDelimiters();
            if (eof()) break;

            char c = data.charAt(index);
            if (isCommand(c)) {
                command = c;
                index++;
            } else if (command == 0) {
                break;
            }

            boolean rel = Character.isLowerCase(command);
            switch (Character.toLowerCase(command)) {
                case 'm' -> parseMove(out, rel);
                case 'z' -> {
                    out.closePath();
                    cx = sx;
                    cy = sy;
                    prevCmd = 'z';
                }
                case 'l' -> parseLine(out, rel);
                case 'h' -> parseHorizontal(out, rel);
                case 'v' -> parseVertical(out, rel);
                case 'c' -> parseCubic(out, rel);
                case 's' -> parseSmoothCubic(out, rel);
                case 'q' -> parseQuadratic(out, rel);
                case 't' -> parseSmoothQuadratic(out, rel);
                case 'a' -> parseArc(out, rel);
                default -> {
                    return out;
                }
            }
        }

        return out;
    }

    private void parseMove(Path2D.Double out, boolean rel) {
        double x = readNumber();
        double y = readNumber();
        if (Double.isNaN(x) || Double.isNaN(y)) return;
        if (rel) {
            x += cx;
            y += cy;
        }
        out.moveTo(x, y);
        cx = sx = x;
        cy = sy = y;
        prevCmd = 'm';

        while (hasNumber()) {
            double lx = readNumber();
            double ly = readNumber();
            if (Double.isNaN(lx) || Double.isNaN(ly)) return;
            if (rel) {
                lx += cx;
                ly += cy;
            }
            out.lineTo(lx, ly);
            cx = lx;
            cy = ly;
            prevCmd = 'l';
        }
    }

    private void parseLine(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(x) || Double.isNaN(y)) return;
            if (rel) {
                x += cx;
                y += cy;
            }
            out.lineTo(x, y);
            cx = x;
            cy = y;
            prevCmd = 'l';
        }
    }

    private void parseHorizontal(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x = readNumber();
            if (Double.isNaN(x)) return;
            if (rel) x += cx;
            out.lineTo(x, cy);
            cx = x;
            prevCmd = 'h';
        }
    }

    private void parseVertical(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double y = readNumber();
            if (Double.isNaN(y)) return;
            if (rel) y += cy;
            out.lineTo(cx, y);
            cy = y;
            prevCmd = 'v';
        }
    }

    private void parseCubic(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x1 = readNumber();
            double y1 = readNumber();
            double x2 = readNumber();
            double y2 = readNumber();
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(x1) || Double.isNaN(y1) || Double.isNaN(x2) || Double.isNaN(y2) || Double.isNaN(x) || Double.isNaN(y))
                return;
            if (rel) {
                x1 += cx;
                y1 += cy;
                x2 += cx;
                y2 += cy;
                x += cx;
                y += cy;
            }
            out.curveTo(x1, y1, x2, y2, x, y);
            lastCubicX = x2;
            lastCubicY = y2;
            cx = x;
            cy = y;
            prevCmd = 'c';
        }
    }

    private void parseSmoothCubic(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x2 = readNumber();
            double y2 = readNumber();
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(x2) || Double.isNaN(y2) || Double.isNaN(x) || Double.isNaN(y)) return;
            if (rel) {
                x2 += cx;
                y2 += cy;
                x += cx;
                y += cy;
            }
            double x1 = cx;
            double y1 = cy;
            if (prevCmd == 'c' || prevCmd == 's') {
                x1 = 2.0 * cx - lastCubicX;
                y1 = 2.0 * cy - lastCubicY;
            }
            out.curveTo(x1, y1, x2, y2, x, y);
            lastCubicX = x2;
            lastCubicY = y2;
            cx = x;
            cy = y;
            prevCmd = 's';
        }
    }

    private void parseQuadratic(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x1 = readNumber();
            double y1 = readNumber();
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(x1) || Double.isNaN(y1) || Double.isNaN(x) || Double.isNaN(y)) return;
            if (rel) {
                x1 += cx;
                y1 += cy;
                x += cx;
                y += cy;
            }
            out.quadTo(x1, y1, x, y);
            lastQuadX = x1;
            lastQuadY = y1;
            cx = x;
            cy = y;
            prevCmd = 'q';
        }
    }

    private void parseSmoothQuadratic(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(x) || Double.isNaN(y)) return;
            if (rel) {
                x += cx;
                y += cy;
            }
            double x1 = cx;
            double y1 = cy;
            if (prevCmd == 'q' || prevCmd == 't') {
                x1 = 2.0 * cx - lastQuadX;
                y1 = 2.0 * cy - lastQuadY;
            }
            out.quadTo(x1, y1, x, y);
            lastQuadX = x1;
            lastQuadY = y1;
            cx = x;
            cy = y;
            prevCmd = 't';
        }
    }

    private void parseArc(Path2D.Double out, boolean rel) {
        while (hasNumber()) {
            double rx = readNumber();
            double ry = readNumber();
            double angle = readNumber();
            double largeArc = readNumber();
            double sweep = readNumber();
            double x = readNumber();
            double y = readNumber();
            if (Double.isNaN(rx) || Double.isNaN(ry) || Double.isNaN(angle) || Double.isNaN(largeArc)
                    || Double.isNaN(sweep) || Double.isNaN(x) || Double.isNaN(y)) return;
            if (rel) {
                x += cx;
                y += cy;
            }
            arcTo(out, cx, cy, x, y, rx, ry, angle, largeArc != 0.0, sweep != 0.0);
            cx = x;
            cy = y;
            prevCmd = 'a';
        }
    }

    private boolean hasNumber() {
        skipDelimiters();
        if (eof()) return false;
        char c = data.charAt(index);
        return c == '+' || c == '-' || c == '.' || Character.isDigit(c);
    }

    private double readNumber() {
        skipDelimiters();
        if (eof()) return Double.NaN;

        int start = index;
        if (data.charAt(index) == '+' || data.charAt(index) == '-') index++;
        boolean hasDot = false;
        while (!eof()) {
            char c = data.charAt(index);
            if (Character.isDigit(c)) {
                index++;
                continue;
            }
            if (c == '.' && !hasDot) {
                hasDot = true;
                index++;
                continue;
            }
            break;
        }
        if (!eof() && (data.charAt(index) == 'e' || data.charAt(index) == 'E')) {
            int e = index + 1;
            if (e < data.length() && (data.charAt(e) == '+' || data.charAt(e) == '-')) e++;
            boolean hasExpDigits = false;
            while (e < data.length() && Character.isDigit(data.charAt(e))) {
                e++;
                hasExpDigits = true;
            }
            if (hasExpDigits) index = e;
        }
        if (start == index) return Double.NaN;
        try {
            return Double.parseDouble(data.substring(start, index));
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private void skipDelimiters() {
        while (!eof()) {
            char c = data.charAt(index);
            if (Character.isWhitespace(c) || c == ',') index++;
            else break;
        }
    }

    private boolean eof() {
        return index >= data.length();
    }
}
