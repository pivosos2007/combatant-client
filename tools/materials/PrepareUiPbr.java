/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.io.*;
import java.nio.*;
import java.util.*;

public class PrepareUiPbr {
    static final String[][] SETS = {{"Metal027", "graphite"}, {"Plastic012B", "soft_touch"},
            {"Metal032", "satin_titanium"}, {"Metal061B", "brushed_metal"}, {"Porcelain001", "ceramic"}, {"ice_0002", "ice"}};
    static double sample(BufferedImage image, int x, int y, int c) {
        if (image.getColorModel() instanceof java.awt.image.IndexColorModel) {
            return ((image.getRGB(x, y) >>> (16 - 8 * Math.min(c, 2))) & 255) / 255.0;
        }
        c = Math.min(c, image.getRaster().getNumBands() - 1);
        return image.getRaster().getSampleDouble(x, y, c)
                / ((1L << image.getSampleModel().getSampleSize(c)) - 1);
    }
    static double linear(double v) { return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4); }
    static double srgb(double v) { return v <= .0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - .055; }
    static int byteValue(double v) { return (int)Math.round(Math.max(0, Math.min(1, v)) * 255); }
    static int rgba(double r, double g, double b, double a) {
        return byteValue(a) << 24 | byteValue(r) << 16 | byteValue(g) << 8 | byteValue(b);
    }
    static BufferedImage read(Path root, String set, String map) throws Exception {
        if (set.equals("ice_0002")) {
            String suffix = switch (map) {
                case "Color" -> "color_2k.jpg";
                case "NormalGL" -> "normal_opengl_2k.png";
                case "Roughness" -> "roughness_2k.jpg";
                case "Displacement" -> "height_2k.png";
                default -> throw new IllegalArgumentException(map);
            };
            return ImageIO.read(root.resolve("ice/ice_0002_" + suffix).toFile());
        }
        return ImageIO.read(root.resolve(set + "_2K-PNG_" + map + ".png").toFile());
    }
    static ByteArrayOutputStream ktx(boolean data16, int size) throws IOException {
        var out = new ByteArrayOutputStream();
        byte[] orientation = "KTXorientation\0S=r,T=d\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int padded = (orientation.length + 3) & ~3;
        ByteBuffer header = ByteBuffer.allocate(64 + 4 + padded).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{(byte)0xab, 0x4b, 0x54, 0x58, 0x20, 0x31, 0x31, (byte)0xbb, 13, 10, 26, 10});
        for (int v : new int[]{0x04030201, data16 ? 0x1403 : 0x1401, data16 ? 2 : 1, 0x1908,
                data16 ? 0x805b : 0x8058, 0x1908, size, size, 0, 0, 1,
                Integer.numberOfTrailingZeros(size) + 1, 4 + padded}) header.putInt(v);
        header.putInt(orientation.length).put(orientation);
        out.write(header.array());
        return out;
    }
    static void writeMip(ByteArrayOutputStream out, BufferedImage image) throws IOException {
        int size = image.getWidth();
        ByteBuffer bytes = ByteBuffer.allocate(4 + size * size * 4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(size * size * 4);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int pixel = image.getRGB(x, y);
            bytes.put((byte)(pixel >> 16)).put((byte)(pixel >> 8)).put((byte)pixel).put((byte)(pixel >>> 24));
        }
        out.write(bytes.array());
    }
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), output = Path.of(args[1]), report = Path.of(args[2]);
        Files.createDirectories(output);
        Files.createDirectories(report);
        StringBuilder audit = new StringBuilder("# PBR source audit\n\nRaw channel statistics (no sRGB conversion); seam/interior = mean adjacent absolute difference.\n\n| File | Size / bits | Min / mean / max (per channel) | Seam X,Y / interior X,Y | SHA-256 |\n|---|---|---|---|---|\n");
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(p -> p.toString().matches(".*\\.(png|jpg)")).sorted().toList()) {
                BufferedImage im = ImageIO.read(file.toFile());
                int w = im.getWidth(), h = im.getHeight(), channels = im.getColorModel() instanceof java.awt.image.IndexColorModel
                        ? 3 : im.getRaster().getNumBands();
                StringBuilder stats = new StringBuilder();
                double sx = 0, sy = 0, ix = 0, iy = 0;
                for (int c = 0; c < channels; c++) {
                    double lo = 1, hi = 0, sum = 0;
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        double v = sample(im, x, y, c);
                        lo = Math.min(lo, v); hi = Math.max(hi, v); sum += v;
                        if (x > 0) ix += Math.abs(v - sample(im, x - 1, y, c));
                        if (y > 0) iy += Math.abs(v - sample(im, x, y - 1, c));
                    }
                    for (int y = 0; y < h; y++) sx += Math.abs(sample(im, 0, y, c) - sample(im, w - 1, y, c));
                    for (int x = 0; x < w; x++) sy += Math.abs(sample(im, x, 0, c) - sample(im, x, h - 1, c));
                    stats.append(String.format(Locale.ROOT, "%.4f/%.4f/%.4f; ", lo, sum / (w * h), hi));
                }
                audit.append(String.format(Locale.ROOT, "| %s | %dx%d / %s | %s | %.5f,%.5f / %.5f,%.5f | %s |%n",
                        file.getFileName(), w, h, Arrays.toString(im.getSampleModel().getSampleSize()), stats,
                        sx / (h * channels), sy / (w * channels), ix / ((w - 1L) * h * channels), iy / (w * (h - 1L) * channels),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))));
            }
        }
        Files.writeString(report.resolve("SOURCE_AUDIT.md"), audit);
        BufferedImage preview = new BufferedImage(SETS.length * 256, 4 * 256, BufferedImage.TYPE_INT_RGB);
        for (int s = 0; s < SETS.length; s++) {
            if (args.length > 3 && !SETS[s][1].equals(args[3])) continue;
            String set = SETS[s][0];
            BufferedImage color = read(source, set, "Color"), normal = read(source, set, "NormalGL"),
                    rough = read(source, set, "Roughness"), height = read(source, set, "Displacement");
            Path metalPath = source.resolve(set + "_2K-PNG_Metalness.png");
            BufferedImage metal = Files.exists(metalPath) ? ImageIO.read(metalPath.toFile()) : null;
            if (set.equals("ice_0002")) metal = ImageIO.read(source.resolve("ice/ice_0002_ao_2k.jpg").toFile());
            int size = color.getWidth();
            if (size != 2048 || color.getHeight() != size || normal.getWidth() != size || normal.getHeight() != size
                    || rough.getWidth() != size || rough.getHeight() != size || height.getWidth() != size || height.getHeight() != size)
                throw new IllegalArgumentException("Expected matching 2048-square maps for " + set);
            // Linear color, metallic, normal XYZ, squared roughness, height; floats retain source precision through all mips.
            float[][] data = new float[9][size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int i = y * size + x;
                for (int c = 0; c < 3; c++) {
                    data[c][i] = (float)linear(sample(color, x, y, c));
                    data[4 + c][i] = (float)(sample(normal, x, y, c) * 2 - 1);
                }
                double normalLength = Math.sqrt(data[4][i] * data[4][i] + data[5][i] * data[5][i] + data[6][i] * data[6][i]);
                for (int c = 4; c < 7; c++) data[c][i] /= (float)Math.max(normalLength, 1e-8);
                data[3][i] = metal == null ? 0 : (float)sample(metal, x, y, 0);
                data[7][i] = (float)Math.pow(sample(rough, x, y, 0), 2);
                data[8][i] = (float)sample(height, x, y, 0);
            }
            int runtimeSize = set.equals("ice_0002") ? 512 : 256;
            var colorKtx = ktx(false, runtimeSize);
            var microKtx = ktx(true, runtimeSize);
            while (true) {
                if (size <= runtimeSize) {
                    BufferedImage cm = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    BufferedImage nh = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    ByteBuffer microBytes = ByteBuffer.allocate(4 + size * size * 8).order(ByteOrder.LITTLE_ENDIAN);
                    microBytes.putInt(size * size * 8);
                    for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                        int i = y * size + x;
                        double nx = data[4][i], ny = data[5][i], nz = data[6][i];
                        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        // Lost normal variance broadens the roughness lobe instead of producing specular shimmer.
                        double r = Math.sqrt(Math.min(1, data[7][i] + Math.max(0, 1 - len)));
                        double encodedX = nx / Math.max(len, 1e-8) * .5 + .5;
                        double encodedY = ny / Math.max(len, 1e-8) * .5 + .5;
                        for (double value : new double[]{encodedX, encodedY, r, data[8][i]})
                            microBytes.putShort((short)Math.round(Math.max(0, Math.min(1, value)) * 65535));
                        nh.setRGB(x, y, rgba(encodedX, encodedY, r, data[8][i]));
                        cm.setRGB(x, y, rgba(srgb(data[0][i]), srgb(data[1][i]), srgb(data[2][i]), data[3][i]));
                    }
                    writeMip(colorKtx, cm);
                    microKtx.write(microBytes.array());
                    if (size == 256) {
                        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                            int p = nh.getRGB(x, y);
                            preview.setRGB(s * 256 + x, y, cm.getRGB(x, y));
                            double nx = ((p >>> 16) & 255) / 255.0 * 2 - 1;
                            double ny = ((p >>> 8) & 255) / 255.0 * 2 - 1;
                            preview.setRGB(s * 256 + x, y + 256, rgba(nx * .5 + .5, ny * .5 + .5,
                                    Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny)) * .5 + .5, 1));
                            preview.setRGB(s * 256 + x, y + 512, (p & 255) * 0x010101);
                            int h = p >>> 24; preview.setRGB(s * 256 + x, y + 768, h * 0x010101);
                        }
                    }
                }
                if (size == 1) break;
                int nextSize = size / 2;
                float[][] next = new float[9][nextSize * nextSize];
                for (int c = 0; c < 9; c++) for (int y = 0; y < nextSize; y++) for (int x = 0; x < nextSize; x++) {
                    int i = y * 2 * size + x * 2;
                    next[c][y * nextSize + x] = (data[c][i] + data[c][i + 1] + data[c][i + size] + data[c][i + size + 1]) * .25f;
                }
                data = next; size = nextSize;
            }
            Path dir = output.resolve(SETS[s][1]); Files.createDirectories(dir);
            Files.write(dir.resolve(set.equals("ice_0002") ? "color_ao.ktx" : "color_metal.ktx"), colorKtx.toByteArray());
            Files.write(dir.resolve("normal_rough_height.ktx"), microKtx.toByteArray());
            System.out.println(set + " -> " + SETS[s][1] + ": " + runtimeSize + "..1, RGBA16 data + RGBA8 color/" + (set.equals("ice_0002") ? "AO" : "metal"));
        }
        ImageIO.write(preview, "png", report.resolve("packed-preview.png").toFile());
    }
}
