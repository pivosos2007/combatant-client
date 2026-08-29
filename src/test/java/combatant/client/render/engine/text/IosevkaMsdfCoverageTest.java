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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IosevkaMsdfCoverageTest {
    private static final String[] STYLES = {
            "iosevka-medium",
            "iosevka-mediumitalic",
            "iosevka-bold",
            "iosevka-bolditalic"
    };

    private static final String LANGUAGE_SAMPLE =
            "Deutsch ÄÖÜß · Latviešu āčēģīķļņšūž · "
                    + "Čeština řů · Polski Łą · Română șț · Türkçe Ğİı · "
                    + "Tiếng Việt Ạệơư · Ελληνικά · Кириллица";

    @Test
    void betterChatIosevkaStylesContainBroadEuropeanCoverage() throws Exception {
        for (String style : STYLES) {
            JsonObject root = readAtlas(style);
            Set<Integer> glyphs = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray("glyphs")) {
                JsonObject glyph = element.getAsJsonObject();
                if (glyph.has("unicode")) glyphs.add(glyph.get("unicode").getAsInt());
            }

            LANGUAGE_SAMPLE.codePoints().forEach(codePoint -> assertTrue(
                    glyphs.contains(codePoint),
                    () -> style + " is missing U+" + Integer.toHexString(codePoint).toUpperCase()
            ));
            assertTrue(glyphs.contains(0x0304), style + " is missing combining macron");
            assertTrue(glyphs.size() >= 5_000, style + " unexpectedly contains a reduced glyph subset");

            JsonObject atlas = root.getAsJsonObject("atlas");
            assertTrue(atlas.get("width").getAsInt() <= 2048, style + " atlas is too wide");
            assertTrue(atlas.get("height").getAsInt() <= 2048, style + " atlas is too tall");
        }
    }

    private JsonObject readAtlas(String style) throws Exception {
        String resource = "/assets/combatant/font/msdf/" + style + ".json";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing MSDF atlas " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
