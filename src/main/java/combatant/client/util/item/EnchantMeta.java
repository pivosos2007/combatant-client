/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record EnchantMeta(
        String key,      // path: "lunge", "sharpness"
        String category  // "armor", "melee", "other"
) {
    public String translationKey() {
        return "enchantment.minecraft." + key;
    }

    public String localizedName() {
        String translationKey = translationKey();
        String translated = I18n.get(translationKey);
        if (translated != null && !translated.isBlank() && !translationKey.equals(translated)) {
            return translated.trim();
        }
        return fallbackName();
    }

    public String shortName() {
        return abbreviate(localizedName());
    }

    static String abbreviate(String name) {
        if (name == null || name.isBlank()) return "";

        String[] rawWords = name.trim().split("[^\\p{L}\\p{N}]+");
        List<String> words = new ArrayList<>(rawWords.length);
        for (String word : rawWords) {
            if (!word.isBlank()) words.add(word);
        }
        if (words.isEmpty()) return "";
        if (words.size() == 1) return codePointPrefix(words.getFirst(), 3);

        // Connector words do not carry meaning in a compact label:
        // "Bane of Arthropods" -> "BA", "Защита от снарядов" -> "ЗС".
        List<String> meaningful = words.stream()
                .filter(word -> word.codePointCount(0, word.length()) > 2)
                .toList();
        if (meaningful.isEmpty()) meaningful = words;

        StringBuilder result = new StringBuilder(Math.min(meaningful.size(), 3));
        for (String word : meaningful) {
            if (result.codePointCount(0, result.length()) >= 3) break;
            result.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
        }
        return result.toString();
    }

    private String fallbackName() {
        String[] words = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder(key.length() + 4);
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            int first = word.codePointAt(0);
            result.appendCodePoint(Character.toUpperCase(first));
            result.append(word.substring(Character.charCount(first)));
        }
        return result.toString();
    }

    private static String codePointPrefix(String value, int length) {
        int count = Math.min(length, value.codePointCount(0, value.length()));
        return value.substring(0, value.offsetByCodePoints(0, count));
    }
}
