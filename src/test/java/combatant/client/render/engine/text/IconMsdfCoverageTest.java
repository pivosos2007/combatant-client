/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IconMsdfCoverageTest {
    @Test
    void iconAtlasesUseUnicodeMappingsExpectedByMsdfFont() throws Exception {
        assertAtlas("icons", 'B', 'C', 'E');
        assertAtlas("iconsnur", 'A', 'B', 'V');
        assertAtlas("mediaplayer", 0xEA02, 0xEA03, 0xEA06);
        assertAtlas("weather_icons", 0xEA01, 0xEA0E, 0xEA1A);
        assertAtlas("vanilla_symbols", 0x2192, 0x25B6, 0x2718);
    }

    private void assertAtlas(String name, int... expectedCodePoints) throws Exception {
        JsonObject root = readAtlas(name);
        Set<Integer> unicode = new HashSet<>();
        int glyphCount = 0;
        for (JsonElement element : root.getAsJsonArray("glyphs")) {
            JsonObject glyph = element.getAsJsonObject();
            glyphCount++;
            assertTrue(glyph.has("unicode"), name + " contains an index-only glyph");
            unicode.add(glyph.get("unicode").getAsInt());
        }

        assertFalse(unicode.isEmpty(), name + " has no Unicode-mapped glyphs");
        assertTrue(unicode.size() == glyphCount, name + " contains duplicate Unicode mappings");
        for (int codePoint : expectedCodePoints) {
            assertTrue(
                    unicode.contains(codePoint),
                    () -> name + " is missing U+" + Integer.toHexString(codePoint).toUpperCase()
            );
        }
    }

    private JsonObject readAtlas(String name) throws Exception {
        String resource = "/assets/combatant/font/msdf/" + name + ".json";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing MSDF atlas " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
