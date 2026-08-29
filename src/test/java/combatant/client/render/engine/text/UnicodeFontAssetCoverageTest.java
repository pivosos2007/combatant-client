/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UnicodeFontAssetCoverageTest {
    @Test
    void fallbackDefinitionUsesValidMinecraftProviderSchema() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/combatant/font/unicode_fallback.json")) {
            assertNotNull(stream, "Missing unicode_fallback font definition");
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
            JsonArray providers = root.getAsJsonArray("providers");
            assertNotNull(providers);
            assertEquals(21, providers.size());
            providers.forEach(provider -> GlyphProviderDefinition.MAP_CODEC.codec()
                    .parse(JsonOps.INSTANCE, provider)
                    .getOrThrow());
        }
    }

    @Test
    void bundledFontsCoverEuropeanCjkAndMajorWorldScripts() throws Exception {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("noto_sans.ttf", "Deutsch ÄÖÜ ß · Latviešu āčēģīķļņšūž · Ελληνικά · Кириллица");
        samples.put("noto_sans_cjk_sc.ttf", "简体中文 繁體中文 日本語 かな カナ 한국어");
        samples.put("noto_sans_arabic.ttf", "العربية");
        samples.put("noto_sans_hebrew.ttf", "עברית");
        samples.put("noto_sans_devanagari.ttf", "हिन्दी");
        samples.put("noto_sans_bengali.ttf", "বাংলা");
        samples.put("noto_sans_gurmukhi.ttf", "ਪੰਜਾਬੀ");
        samples.put("noto_sans_gujarati.ttf", "ગુજરાતી");
        samples.put("noto_sans_tamil.ttf", "தமிழ்");
        samples.put("noto_sans_telugu.ttf", "తెలుగు");
        samples.put("noto_sans_kannada.ttf", "ಕನ್ನಡ");
        samples.put("noto_sans_malayalam.ttf", "മലയാളം");
        samples.put("noto_sans_thai.ttf", "ภาษาไทย");
        samples.put("noto_sans_khmer.ttf", "ភាសាខ្មែរ");
        samples.put("noto_sans_lao.ttf", "ພາສາລາວ");
        samples.put("noto_sans_myanmar.ttf", "မြန်မာ");
        samples.put("noto_sans_georgian.ttf", "ქართული");
        samples.put("noto_sans_armenian.ttf", "Հայերեն");
        samples.put("noto_sans_ethiopic.ttf", "አማርኛ");
        samples.put("noto_sans_sinhala.ttf", "සිංහල");

        for (Map.Entry<String, String> entry : samples.entrySet()) {
            String resource = "/assets/combatant/font/unicode/" + entry.getKey();
            try (InputStream stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, "Missing bundled font " + resource);
                Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
                assertEquals(-1, font.canDisplayUpTo(entry.getValue()),
                        () -> entry.getKey() + " does not cover sample: " + entry.getValue());
            }
        }
    }

    @Test
    void detectsRtlRunsIndependentlyOfClientLocale() {
        assertFalse(VanillaTextRenderer.containsRightToLeftCodePoint("Deutsch Latviešu 日本語"));
        assertTrue(VanillaTextRenderer.containsRightToLeftCodePoint("العربية"));
        assertTrue(VanillaTextRenderer.containsRightToLeftCodePoint("עברית"));
    }
}
