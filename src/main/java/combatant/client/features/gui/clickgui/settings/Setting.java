/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import net.minecraft.client.resources.language.I18n;
import combatant.client.config.ConfigLang;
import combatant.client.config.SettingOwner;
import combatant.client.config.values.ConfigValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class Setting {

    protected static final float LABEL_PAD = 6f;
    private static final ThreadLocal<MissingI18nReporter> MISSING_REPORTER = new ThreadLocal<>();
    private static boolean LANG_READY = false;
    protected final ConfigValue<?> value;
    private final String name;
    private final Map<String, String> cachedOptionDisplayNames = new HashMap<>();
    private SettingOwner parent;
    private Supplier<Boolean> visibility = () -> true;
    private Supplier<String> unavailableReason = () -> null;
    private boolean lastVisible = true;
    private float visibilityAnim = 1f;
    private long lastAnimTime = System.nanoTime();
    private String cachedDisplayName;
    private String cachedTranslationKey;
    private SettingOwner cachedTranslationOwner;
    private final List<String> commonI18nKeys = new ArrayList<>();
    private boolean i18nNameEnabled = true;
    private boolean i18nOptionsEnabled = true;

    public Setting(String name, ConfigValue<?> value) {
        this.name = name;
        this.value = value;
    }

    public Setting(String name) {
        this.name = name;
        this.value = null;
    }

    protected static float labelX(float x) {
        return x + LABEL_PAD;
    }

    private static String resolveFirstTranslation(java.util.List<String> keys, boolean logIfMissing) {
        if (keys == null || keys.isEmpty()) return null;

        if (!ensureLangReady()) {
            return null;
        }

        String firstLoggable = null;

        for (String key : keys) {
            if (key == null || key.isBlank()) continue;

            boolean loggable = key.startsWith("setting.");
            if (loggable && firstLoggable == null) {
                firstLoggable = key;
            }

            String translated = I18n.get(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }

        if (logIfMissing && firstLoggable != null) {
            MissingI18nReporter reporter = MISSING_REPORTER.get();
            if (reporter != null) {
                reporter.missing(keys);
            }
        }

        return null;
    }

    private static boolean ensureLangReady() {
        if (LANG_READY) return true;
        if (!I18n.get("setting.module.bind").equals("setting.module.bind")) {
            LANG_READY = true;
        }
        return LANG_READY;
    }

    public static boolean prepareI18nPreflightPass() {
        LANG_READY = false;
        return ensureLangReady();
    }

    public static AutoCloseable captureMissingI18n(MissingI18nReporter reporter) {
        MissingI18nReporter previous = MISSING_REPORTER.get();
        if (reporter == null) {
            MISSING_REPORTER.remove();
        } else {
            MISSING_REPORTER.set(reporter);
        }
        return () -> {
            if (previous == null) {
                MISSING_REPORTER.remove();
            } else {
                MISSING_REPORTER.set(previous);
            }
        };
    }

    // --- UI ---
    public String getName() {
        return name;
    }

    public String getDisplayName() {
        if (LANG_READY && cachedDisplayName != null) return cachedDisplayName;

        if (i18nNameEnabled) {
            String translated = resolveFirstTranslation(getTranslationKeys(), true);
            if (translated != null) {
                if (LANG_READY) cachedDisplayName = translated;
                return translated;
            }
        }

        if (value != null) {
            String fallback = ConfigLang.getName(value);
            if (fallback != null && !fallback.isBlank() && !fallback.equals(value.getName())) {
                if (LANG_READY) cachedDisplayName = fallback;
                return fallback;
            }
        }

        if (LANG_READY) cachedDisplayName = name;
        return name;
    }

    public String getOptionDisplayName(String optionId) {
        if (optionId == null || optionId.isBlank()) return optionId;

        if (!i18nOptionsEnabled) {
            return optionId;
        }

        if (LANG_READY) {
            String cached = cachedOptionDisplayNames.get(optionId);
            if (cached != null) return cached;
        }

        java.util.List<String> optionKeys = optionTranslationKeys(optionId);

        String translated = resolveFirstTranslation(optionKeys, true);
        String resolved = translated != null ? translated : optionId;

        if (LANG_READY) {
            cachedOptionDisplayNames.put(optionId, resolved);
        }

        return resolved;
    }

    public SettingOwner getParent() {
        return parent;
    }

    public void setParent(SettingOwner owner) {
        if (parent != owner) {
            parent = owner;
            invalidateNameCaches();
            return;
        }
        parent = owner;
    }

    public String getTranslationKey() {
        if (parent == null) return null;
        if (cachedTranslationKey != null && cachedTranslationOwner == parent) {
            return cachedTranslationKey;
        }
        String prefix = parent.getTranslationKeyPrefix();
        String id = getTranslationId();
        if (prefix == null || id == null || prefix.isBlank() || id.isBlank()) return null;
        cachedTranslationOwner = parent;
        cachedTranslationKey = prefix + "." + id;
        return cachedTranslationKey;
    }

    public java.util.List<String> getTranslationKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>(3);

        String local = getTranslationKey();
        if (local != null && !local.isBlank()) {
            keys.add(local);
        }

        for (String commonI18nKey : commonI18nKeys) {
            if (commonI18nKey != null && !commonI18nKey.isBlank()) {
                keys.add("setting.common." + commonI18nKey);
            }
        }

        String id = getTranslationId();
        if (id != null && !id.isBlank()) {
            keys.add("setting.common." + id);
        }

        return keys;
    }

    public void setCommonI18nKey(String key) {
        ArrayList<String> keys = new ArrayList<>();
        addCommonI18nKey(keys, key);
        setCommonI18nKeys(keys);
    }

    public void setCommonI18nKeys(Iterable<String> keys) {
        ArrayList<String> normalized = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                addCommonI18nKey(normalized, key);
            }
        }
        if (!commonI18nKeys.equals(normalized)) {
            commonI18nKeys.clear();
            commonI18nKeys.addAll(normalized);
            invalidateNameCaches();
        }
    }

    private static void addCommonI18nKey(ArrayList<String> keys, String key) {
        if (key == null) return;
        String normalized = key.trim();
        if (!normalized.isBlank() && !keys.contains(normalized)) {
            keys.add(normalized);
        }
    }

    public void setI18nEnabled(boolean name, boolean options) {
        if (i18nNameEnabled != name || i18nOptionsEnabled != options) {
            i18nNameEnabled = name;
            i18nOptionsEnabled = options;
            invalidateNameCaches();
        }
    }

    /**
     * Trigger i18n lookup so missing keys are logged early.
     */
    public void preflightI18n() {
        if (!i18nNameEnabled) return;
        resolveFirstTranslation(getTranslationKeys(), true);
    }

    protected void preflightOptionI18n(String optionId) {
        if (!i18nOptionsEnabled) return;
        if (optionId == null || optionId.isBlank()) return;

        resolveFirstTranslation(optionTranslationKeys(optionId), true);
    }

    private java.util.List<String> optionTranslationKeys(String optionId) {
        ArrayList<String> optionKeys = new ArrayList<>();
        if (optionId == null || optionId.isBlank()) return optionKeys;
        ArrayList<String> variants = optionIdVariants(optionId);
        for (String base : getTranslationKeys()) {
            if (base == null || base.isBlank()) continue;
            for (String variant : variants) {
                addOptionKey(optionKeys, base, variant);
            }
        }
        return optionKeys;
    }

    private static ArrayList<String> optionIdVariants(String optionId) {
        ArrayList<String> variants = new ArrayList<>();
        addVariant(variants, optionId);

        String lower = optionId.toLowerCase(java.util.Locale.ROOT);
        addVariant(variants, lower);
        addNormalizedOptionVariants(variants, lower);
        addVariant(variants, optionId.toUpperCase(java.util.Locale.ROOT));

        if (!lower.isEmpty()) {
            addVariant(variants, Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }

        return variants;
    }

    private static void addNormalizedOptionVariants(ArrayList<String> variants, String lower) {
        if (lower == null || lower.isBlank()) return;
        String normalized = lower.trim()
                .replace('-', '_')
                .replace(' ', '_');
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        normalized = stripEdgeUnderscores(normalized);
        addVariant(variants, normalized);

        String compact = normalized.replace("_", "");
        addVariant(variants, compact);
    }

    private static String stripEdgeUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') start++;
        while (end > start && value.charAt(end - 1) == '_') end--;
        return start == 0 && end == value.length() ? value : value.substring(start, end);
    }

    private static void addVariant(ArrayList<String> variants, String value) {
        if (value != null && !value.isBlank() && !variants.contains(value)) {
            variants.add(value);
        }
    }

    private static void addOptionKey(ArrayList<String> keys, String base, String optionId) {
        String key = base + ".option." + optionId;
        if (!keys.contains(key)) {
            keys.add(key);
        }
    }

    public Setting unavailableReason(Supplier<String> supplier) {
        this.unavailableReason = supplier == null ? () -> null : supplier;
        return this;
    }

    public boolean isAvailable() {
        return getUnavailableReason().isEmpty();
    }

    public String getUnavailableReason() {
        try {
            String reason = unavailableReason.get();
            return reason == null ? "" : reason.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public abstract void render(float x, float y, float width, float mouseX, float mouseY);

    public abstract void mouseClicked(double mx, double my, int button);

    public void mouseReleased(double mx, double my, int button) {
    }

    /**
     * Called by parent panels when a click happens outside this setting hitbox.
     */
    public void mouseClickedOutside(double mx, double my, int button) {
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public abstract float getHeight();

    /**
     * Уникальный ID (для сериализации)
     */
    public String getId() {
        return name.toLowerCase().replace(" ", "_");
    }

    protected String getTranslationId() {
        return getId();
    }

    // ======================================================
    //      Сериализация (через ConfigValue)
    // ======================================================

    public Object save() {
        if (value != null)
            return value.toJson();
        return null;
    }

    public void load(Object o) {
        if (value != null)
            value.fromJson(o);
    }

    /**
     * Underlying config value (used for persistence).
     */
    public ConfigValue<?> getConfigValue() {
        return value;
    }

    // ======================================================
    //      Visibility
    // ======================================================
    public Setting visibleWhen(Supplier<Boolean> supplier) {
        this.visibility = supplier == null ? () -> true : supplier;
        return this;
    }

    public boolean isVisible() {
        try {
            return visibility.get();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Smooth visibility progress 0..1 for animating show/hide.
     */
    public float updateVisibilityAnim() {
        boolean vis = isVisible();
        long now = System.nanoTime();
        float dt = (now - lastAnimTime) / 1_000_000_000f;
        lastAnimTime = now;
        if (dt <= 0f) dt = 0.0001f;
        dt = Math.min(dt, 0.05f); // avoid huge jumps on long pauses

        if (vis && !lastVisible) {
            visibilityAnim = 0f; // start animating from 0 when becoming visible
        }

        float target = vis ? 1f : 0f;
        float speed = 10f; // higher = faster
        float step = Math.min(0.35f, dt * speed); // cap so it never jumps in a single frame
        visibilityAnim += (target - visibilityAnim) * step;

        // clamp
        if (visibilityAnim < 0.001f) visibilityAnim = 0f;
        if (visibilityAnim > 0.999f) visibilityAnim = 1f;

        lastVisible = vis;
        return visibilityAnim;
    }

    public float getVisibilityAnim() {
        return visibilityAnim;
    }

    public boolean isVisibilityTargetVisible() {
        return lastVisible;
    }

    /**
     * Height scaled by visibility animation, used for layout.
     */
    public float getAnimatedHeight() {
        return getHeight() * visibilityAnim;
    }

    private void invalidateNameCaches() {
        cachedDisplayName = null;
        cachedTranslationKey = null;
        cachedTranslationOwner = null;
        cachedOptionDisplayNames.clear();
    }

    @FunctionalInterface
    public interface MissingI18nReporter {
        void missing(java.util.List<String> keyChain);
    }
}
