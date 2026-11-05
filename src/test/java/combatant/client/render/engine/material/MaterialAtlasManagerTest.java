package combatant.client.render.engine.material;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialAtlasManagerTest {
    @Test
    void blitsSpriteIntoAtlasAtNonzeroOffsetWithoutChangingSource() throws Exception {
        try (NativeImage source = patterned(16, 16);
             NativeImage atlas = new NativeImage(2048, 512, true)) {
            method("blit", NativeImage.class, NativeImage.class, int.class, int.class)
                    .invoke(null, source, atlas, 1984, 432);

            assertPattern(atlas, 1984, 432, 16, 16);
            assertPattern(source, 0, 0, 16, 16);
            assertEquals(0, atlas.getPixel(1983, 432));
            assertEquals(0, atlas.getPixel(2000, 432));
            assertEquals(0, atlas.getPixel(1984, 431));
            assertEquals(0, atlas.getPixel(1984, 448));
        }
    }

    @Test
    void copiesImagePixelsWithoutChangingSource() throws Exception {
        try (NativeImage source = patterned(16, 32);
             NativeImage copy = (NativeImage) method("copyImage", NativeImage.class).invoke(null, source)) {
            assertEquals(16, copy.getWidth());
            assertEquals(32, copy.getHeight());
            assertPattern(copy, 0, 0, 16, 32);
            assertPattern(source, 0, 0, 16, 32);
            copy.setPixel(0, 0, 0);
            assertEquals(pixel(0, 0), source.getPixel(0, 0));
        }
    }

    @Test
    void extractsStaticFrameWithoutChangingSheet() throws Exception {
        Class<?> timeline = Class.forName(MaterialAtlasManager.class.getName() + "$Timeline");
        Method frame = method("frame", NativeImage.class, timeline,
                int.class, int.class, int.class, int.class, int.class, int.class);
        try (NativeImage sheet = patterned(16, 32);
             NativeImage output = (NativeImage) frame.invoke(null, sheet, null, 0, 16, 16, 16, 32, 0)) {
            assertEquals(16, output.getWidth());
            assertEquals(16, output.getHeight());
            assertPattern(output, 0, 0, 16, 16);
            assertPattern(sheet, 0, 0, 16, 32);
        }
    }

    private static Method method(String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = MaterialAtlasManager.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static NativeImage patterned(int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, pixel(x, y));
            }
        }
        return image;
    }

    private static void assertPattern(NativeImage image, int offsetX, int offsetY, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(pixel(x, y), image.getPixel(offsetX + x, offsetY + y),
                        "pixel at " + x + ", " + y);
            }
        }
    }

    private static int pixel(int x, int y) {
        return 0xFF000080 | (x << 16) | (y << 8);
    }
}
