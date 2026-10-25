/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class SdfDistanceEncodingTest {
    @Test void dynamicSdfDecodesOneAtlasPixelAsOnePixelOfDistance() throws Exception {
        byte[] bytes;
        try (var source = getClass().getResourceAsStream("/assets/combatant/font/inter_regular.ttf")) {
            assertNotNull(source);
            bytes = source.readAllBytes();
        }
        var data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try (STBTTFontinfo info = STBTTFontinfo.malloc(); MemoryStack stack = MemoryStack.stackPush()) {
            assertTrue(STBTruetype.stbtt_InitFont(info, data));
            for (float range : new float[]{4, 6}) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var sdf = STBTruetype.stbtt_GetGlyphSDF(info,
                        STBTruetype.stbtt_ScaleForMappingEmToPixels(info, 32),
                        STBTruetype.stbtt_FindGlyphIndex(info, 'H'), 8, (byte) 128,
                        MsdfFont.sdfPixelDistanceScale(range), width, height, stack.mallocInt(1), stack.mallocInt(1));
                assertNotNull(sdf);
                try {
                    int largestStep = 0;
                    for (int y = 0; y < height.get(0); y++) {
                        for (int x = 1; x < width.get(0); x++) {
                            int left = sdf.get(y * width.get(0) + x - 1) & 255;
                            int right = sdf.get(y * width.get(0) + x) & 255;
                            if (left > 0 && left < 255 && right > 0 && right < 255)
                                largestStep = Math.max(largestStep, Math.abs(right - left));
                        }
                    }
                    assertEquals(1.0, range * largestStep / 255.0, .03,
                            "The MSDF decoder must preserve the dynamic SDF distance slope");
                } finally { STBTruetype.stbtt_FreeSDF(sdf); }
            }
        } finally { MemoryUtil.memFree(data); }
    }
}
