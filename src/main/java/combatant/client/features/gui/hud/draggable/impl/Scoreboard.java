/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import combatant.client.features.module.Modules;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextGlyphFallback;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.input.KeyManager;
import combatant.client.util.logging.ServerDumpUtil;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import combatant.client.util.text.TextRenderUtil.Part;

import java.util.*;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 110)
public final class Scoreboard extends DraggableHudElement {

    {
        defaultLayout(1721.69f, 158.0f);
    }


    private static final Comparator<PlayerScoreEntry> ENTRY_COMPARATOR = Comparator.comparingInt(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static final float BASE_PAD_X = 8.0f;
    private static final float BASE_PAD_Y = 8.0f;
    private static final float BASE_ROW_GAP = 2.0f;
    private static final float BASE_HEADER_GAP = 8.0f;
    private static final float BASE_SCORE_GAP = 8.0f;
    private static final float BASE_FOOTER_GAP = 8.0f;
    private static final float BASE_RADIUS = 4.0f;
    private static final float BASE_SOFTNESS = 1.0f;
    private static final float BASE_STROKE = 0.45f;
    private static final float BASE_TITLE_SCALE = 0.78f;
    private static final float BASE_TEXT_SCALE = 0.68f;
    private static final float BASE_TITLE_LINE_GAP = 1.0f;
    private static final int MAX_ENTRIES = 15;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final KeyBindValue toggleBind =
            new KeyBindValue("toggle_bind", "Э");
    private final NumberValue<Double> scale =
            new NumberValue<>("scoreboard_scale", 1.93, 0.5, 5.0);
    private final ModeValue colorMode =
            new ModeValue("scoreboard_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("scoreboard_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT);
    private final ModeValue bgEffect =
            new ModeValue("scoreboard_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("scoreboard_blur_alpha", 255, 0, 255);
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("scoreboard_bg_alpha", 182, 0, 255);
    private final RGBAColorValue bg =
            new RGBAColorValue("scoreboard_bg", "#820A0A0A");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("scoreboard_bg_secondary", "#B8141414");
    private final BooleanValue strokeEnabled =
            new BooleanValue("scoreboard_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("scoreboard_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("scoreboard_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("scoreboard_shadow_enabled", false);
    private final ModeValue shadowMode =
            new ModeValue("scoreboard_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("scoreboard_shadow_alpha", 72, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("scoreboard_stroke", "#4A4A4A");
    private final RGBColorValue titleColor =
            new RGBColorValue("scoreboard_title_color", "#FFCC00");
    private final RGBColorValue labelColor =
            new RGBColorValue("scoreboard_label_color", "#FFFFFF");
    private final RGBColorValue valueColor =
            new RGBColorValue("scoreboard_value_color", "#FF4A4A");
    private final RGBColorValue footerColor =
            new RGBColorValue("scoreboard_footer_color", "#FFD44A");
    private final EnumValue<HudTextEffects.Effect> textEffect =
            new EnumValue<>("scoreboard_text_effect", HudTextEffects.Effect.STRIPE,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> textEffectSpeed =
            new NumberValue<>("scoreboard_text_effect_speed", 18, 1, 60);
    private final BooleanMapValue textEffectSections =
            new BooleanMapValue("scoreboard_text_effect_sections", defaultTextEffectSections());
    private final List<SidebarLine> previewLinesSample = List.of(
            new SidebarLine(Component.literal("Entry One"), Component.literal("12"), false),
            new SidebarLine(Component.literal("Entry Two"), Component.literal("34"), false),
            new SidebarLine(Component.literal("Entry Three"), Component.literal("56"), false),
            new SidebarLine(Component.literal("Entry Four"), Component.literal("78"), false),
            new SidebarLine(Component.literal("Entry Five"), Component.literal("90"), false),
            new SidebarLine(Component.literal("Entry Six"), Component.literal("12%"), false),
            new SidebarLine(Component.literal("Entry Seven"), Component.literal("3"), false),
            new SidebarLine(Component.literal("Footer Text"), Component.empty(), true)
    );

    private final List<SidebarLine> lines = new ArrayList<>();

    private boolean toggleArmed = true;
    private boolean toggleInit = false;
    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiHeaderBg;
    private int uiStroke;
    private int uiTitleColor;
    private int uiLabelColor;
    private int uiValueColor;
    private int uiFooterColor;
    private float displayLabelWidth = -1.0f;
    private float displayValueWidth = -1.0f;
    private float displayFooterWidth = -1.0f;

    public Scoreboard() {
        super("scoreboard", "Scoreboard", false);
    }

    public static boolean shouldReplaceVanilla() {
        return DraggableHudElementRegistry.isEnabled(Scoreboard.class) && !isHiddenByNoRender();
    }

    private static float smoothWidth(float current, float target) {
        if (current < 0.0f) return target;
        if (target >= current) return target;
        float next = AnimationUtility.approach(current, target, AnimationUtility.deltaTime(), 12.0f);
        return AnimationUtility.snap(next, target, 0.25f);
    }

    private static boolean isHiddenByNoRender() {
        NoRender noRender = Modules.get(NoRender.class);
        return noRender != null && noRender.hideScoreboard();
    }

    private static Map<String, Boolean> defaultTextEffectSections() {
        Map<String, Boolean> sections = new LinkedHashMap<>();
        sections.put("header", true);
        sections.put("labels", false);
        sections.put("values", false);
        sections.put("footer", false);
        return sections;
    }

    private static float measureStyledText(TextRenderer fallback, Component text, int defaultColor, float scale) {
        if (fallback == null || text == null) return 0.0f;
        float width = 0.0f;
        for (Part part : TextRenderUtil.flattenStyled(text, defaultColor)) {
            width += partWidth(fallback, part, scale, 0.0f, 0);
        }
        return width;
    }

    private static List<List<Part>> splitStyledLines(Component text, int defaultColor) {
        List<List<Part>> lines = new ArrayList<>();
        List<Part> current = new ArrayList<>();
        lines.add(current);
        for (Part part : TextRenderUtil.flattenStyled(text, defaultColor)) {
            String value = part.text();
            int start = 0;
            while (true) {
                int newline = value.indexOf('\n', start);
                String segment = newline >= 0 ? value.substring(start, newline) : value.substring(start);
                if (!segment.isEmpty()) {
                    current.add(new Part(
                            segment,
                            part.color(),
                            part.bold(),
                            part.italic(),
                            part.underline(),
                            part.strikethrough(),
                            part.obfuscated()
                    ));
                }
                if (newline < 0) {
                    break;
                }
                current = new ArrayList<>();
                lines.add(current);
                start = newline + 1;
            }
        }
        return lines;
    }

    private static float measureStyledLineWidth(TextRenderer fallback, List<List<Part>> lines, float scale) {
        if (fallback == null || lines == null || lines.isEmpty()) return 0.0f;
        float widest = 0.0f;
        for (List<Part> line : lines) {
            float width = 0.0f;
            int partIndex = 0;
            for (Part part : line) {
                width += partWidth(fallback, part, scale, 0.0f, partIndex++);
            }
            widest = Math.max(widest, width);
        }
        return widest;
    }

    private static void renderStyledText(Renderer2D shapeRenderer,
                                         TextRenderer fallback,
                                         float scale,
                                         float x,
                                         float y,
                                         Component text,
                                         int defaultColor,
                                         boolean animated,
                                         HudTextEffects.Effect effect,
                                         int effectSpeed,
                                         float timeSec,
                                         float phase) {
        if (fallback == null || text == null) return;
        float cursorX = x;
        int partIndex = 0;
        float lineHeight = rendererHeight(fallback, scale);
        for (Part part : TextRenderUtil.flattenStyled(text, defaultColor)) {
            TextRenderer renderer = styledRenderer(fallback, part);
            String renderText = part.obfuscated() ? obfuscate(part.text(), timeSec + phase, partIndex) : part.text();
            float partW = renderTextRuns(shapeRenderer, renderer, scale, cursorX, y, renderText, part.color(), animated,
                    effect, effectSpeed, timeSec + phase + partIndex * 0.12f);
            renderDecorations(shapeRenderer, cursorX, y, partW, lineHeight, part);
            cursorX += partW;
            partIndex++;
        }
    }

    private static void renderStyledLinesCentered(Renderer2D shapeRenderer,
                                                  TextRenderer fallback,
                                                  float scale,
                                                  float boxX,
                                                  float boxWidth,
                                                  float y,
                                                  List<List<Part>> lines,
                                                  float lineGap,
                                                  boolean animated,
                                                  HudTextEffects.Effect effect,
                                                  int effectSpeed,
                                                  float timeSec) {
        if (fallback == null || lines == null || lines.isEmpty()) return;
        float lineHeight = rendererHeight(fallback, scale);
        float cursorY = y;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            List<Part> line = lines.get(lineIndex);
            float lineWidth = 0.0f;
            int measurePart = 0;
            for (Part part : line) {
                lineWidth += partWidth(fallback, part, scale, timeSec + lineIndex * 0.18f, measurePart++);
            }
            float cursorX = boxX + Math.max(0.0f, (boxWidth - lineWidth) * 0.5f);
            int partIndex = 0;
            for (Part part : line) {
                TextRenderer renderer = styledRenderer(fallback, part);
                String renderText = part.obfuscated() ? obfuscate(part.text(), timeSec + lineIndex * 0.18f, partIndex) : part.text();
                float partW = renderTextRuns(shapeRenderer, renderer, scale, cursorX, cursorY, renderText, part.color(), animated,
                        effect, effectSpeed, timeSec + lineIndex * 0.18f + partIndex * 0.12f);
                renderDecorations(shapeRenderer, cursorX, cursorY, partW, lineHeight, part);
                cursorX += partW;
                partIndex++;
            }
            cursorY += lineHeight + lineGap;
        }
    }

    private static TextRenderer styledRenderer(TextRenderer fallback, Part part) {
        if (part == null) return fallback;
        FontInfo.Type type;
        if (part.bold() && part.italic()) {
            type = FontInfo.Type.BoldItalic;
        } else if (part.bold()) {
            type = FontInfo.Type.Bold;
        } else if (part.italic()) {
            type = FontInfo.Type.Italic;
        } else {
            type = FontInfo.Type.Regular;
        }
        TextRenderer renderer = Fonts.renderer("Iosevka", type, fallback);
        return renderer != null ? renderer : fallback;
    }

    private static TextRenderer rendererForGlyph(TextRenderer preferred, int codePoint) {
        return TextGlyphFallback.rendererForGlyph(preferred, codePoint);
    }

    private static float rendererHeight(TextRenderer renderer, float scale) {
        if (renderer == null) return 0.0f;
        renderer.begin(scale, false, false);
        float height = (float) renderer.getHeight(false);
        renderer.end();
        return height;
    }

    private static float partWidth(TextRenderer fallback, Part part, float scale, float timeSec, int salt) {
        if (fallback == null || part == null || part.text().isEmpty()) return 0.0f;
        TextRenderer renderer = styledRenderer(fallback, part);
        String text = part.obfuscated() ? obfuscate(part.text(), timeSec, salt) : part.text();
        return measureTextRuns(renderer, scale, text);
    }

    private static float measureTextRuns(TextRenderer preferred, float scale, String text) {
        if (preferred == null || text == null || text.isEmpty()) return 0.0f;
        TextRenderer current = null;
        float width = 0.0f;
        float svgAdvance = svgGlyphSize(preferred, scale);
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(preferred, cp)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    width += svgAdvance;
                    i += Character.charCount(cp);
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                TextRenderer next = rendererForGlyph(preferred, cp);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, false, false);
                }
                width += (float) current.getWidth(glyph, false);
                i += Character.charCount(cp);
            }
            return width;
        } finally {
            if (current != null) current.end();
        }
    }

    private static float renderTextRuns(Renderer2D shapeRenderer,
                                        TextRenderer preferred,
                                        float scale,
                                        float x,
                                        float y,
                                        String text,
                                        int color,
                                        boolean animated,
                                        HudTextEffects.Effect effect,
                                        int effectSpeed,
                                        float timeSec) {
        if (preferred == null || text == null || text.isEmpty()) return 0.0f;
        TextRenderer current = null;
        StringBuilder run = new StringBuilder();
        float cursorX = x;
        float runX = x;
        int glyphIndex = 0;
        float svgSize = svgGlyphSize(preferred, scale);
        float svgY = y + (rendererHeight(preferred, scale) - svgSize) * 0.5f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (TextGlyphFallback.shouldUseVanillaSvg(preferred, cp)) {
                if (current != null && !run.isEmpty()) {
                    cursorX = flushTextRun(current, run.toString(), scale, runX, y, color, animated,
                            effect, effectSpeed, timeSec + glyphIndex * 0.12f);
                    run.setLength(0);
                }
                current = null;
                String svgName = TextGlyphFallback.vanillaSvgName(cp);
                if (shapeRenderer != null && svgName != null) {
                    shapeRenderer.svg(svgName, cursorX, svgY, svgSize, svgSize,
                            SvgRenderOptions.fromFile().withAlpha(((color >>> 24) & 0xFF) / 255.0f));
                }
                cursorX += svgSize;
                runX = cursorX;
                i += Character.charCount(cp);
                glyphIndex++;
                continue;
            }
            TextRenderer next = rendererForGlyph(preferred, cp);
            if (current != null && next != current) {
                cursorX = flushTextRun(current, run.toString(), scale, runX, y, color, animated,
                        effect, effectSpeed, timeSec + glyphIndex * 0.12f);
                run.setLength(0);
                runX = cursorX;
            }
            if (next != current) {
                current = next;
            }
            run.appendCodePoint(cp);
            i += Character.charCount(cp);
            glyphIndex++;
        }
        if (current != null && !run.isEmpty()) {
            cursorX = flushTextRun(current, run.toString(), scale, runX, y, color, animated,
                    effect, effectSpeed, timeSec + glyphIndex * 0.12f);
        }
        return cursorX - x;
    }

    private static float svgGlyphSize(TextRenderer renderer, float scale) {
        return Math.max(1.0f, rendererHeight(renderer, scale)) * 0.92f;
    }

    private static float flushTextRun(TextRenderer renderer,
                                      String text,
                                      float scale,
                                      float x,
                                      float y,
                                      int color,
                                      boolean animated,
                                      HudTextEffects.Effect effect,
                                      int effectSpeed,
                                      float timeSec) {
        if (renderer == null || text == null || text.isEmpty()) return x;
        renderer.begin(scale, false, false);
        boolean rendered = animated && HudTextEffects.render(
                renderer,
                text,
                x,
                y,
                color,
                effect,
                effectSpeed,
                timeSec,
                false
        );
        if (!rendered) {
            renderer.render(text, x, y, new RenderColor(color), false);
        }
        float width = (float) renderer.getWidth(text, false);
        renderer.end();
        return x + width;
    }

    private static void renderDecorations(Renderer2D renderer,
                                          float x,
                                          float y,
                                          float w,
                                          float lineHeight,
                                          Part part) {
        if (renderer == null || part == null || w <= 0.0f) return;
        if (!part.underline() && !part.strikethrough()) return;
        int color = part.color();
        float h = Math.max(0.65f, lineHeight * 0.055f);
        if (part.underline()) {
            renderer.quad(x, y + lineHeight * 0.88f, w, h, color);
        }
        if (part.strikethrough()) {
            renderer.quad(x, y + lineHeight * 0.54f, w, h, color);
        }
    }

    private static String obfuscate(String text, float timeSec, int salt) {
        if (text == null || text.isEmpty()) return "";
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        int seed = Math.abs((int) (timeSec * 18.0f) * 31 + salt * 131);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                out.append(ch);
            } else {
                out.append(alphabet.charAt(Math.floorMod(seed + i * 17 + ch, alphabet.length())));
            }
        }
        return out.toString();
    }

    private static boolean isBlankText(Component text) {
        return text == null || text.getString().isBlank();
    }

    private static Component normalizeHeaderTitle(Component text) {
        if (text == null) {
            return Component.empty();
        }
        MutableComponent out = Component.empty();
        text.visit((style, value) -> {
            if (value != null && !value.isEmpty()) {
                out.append(Component.literal(normalizeHeaderGlyphs(value)).setStyle(style));
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    private static String normalizeHeaderGlyphs(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            out.append(switch (ch) {
                case 'ᴀ' -> 'a';
                case 'ʙ' -> 'b';
                case 'ᴄ' -> 'c';
                case 'ᴅ' -> 'd';
                case 'ᴇ' -> 'e';
                case 'ꜰ' -> 'f';
                case 'ɢ' -> 'g';
                case 'ʜ' -> 'h';
                case 'ɪ' -> 'i';
                case 'ᴊ' -> 'j';
                case 'ᴋ' -> 'k';
                case 'ʟ' -> 'l';
                case 'ᴍ' -> 'm';
                case 'ɴ' -> 'n';
                case 'ᴏ' -> 'o';
                case 'ᴘ' -> 'p';
                case 'ǫ' -> 'q';
                case 'ʀ' -> 'r';
                case 'ꜱ' -> 's';
                case 'ᴛ' -> 't';
                case 'ᴜ' -> 'u';
                case 'ᴠ' -> 'v';
                case 'ᴡ' -> 'w';
                case 'x' -> 'x';
                case 'ʏ' -> 'y';
                case 'ᴢ' -> 'z';
                default -> ch;
            });
        }
        return out.toString();
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bind(toggleBind, BindMode.PRESS));
        defs.add(SettingDef.number(scale));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.color(bg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bg2).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.colorNoAlpha(titleColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(labelColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(valueColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(footerColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(textEffect));
        defs.add(SettingDef.number(textEffectSpeed).visibleWhen(() -> textEffect.get() != HudTextEffects.Effect.NONE));
        defs.add(SettingDef.group(textEffectSections).visibleWhen(() -> textEffect.get() != HudTextEffects.Effect.NONE));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16.0f;
        this.y = screenH * 0.5f - 96.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.BEFORE_CHAT;
    }

    @Override
    public void onTick() {
        if (!toggleInit) {
            if (mc == null || mc.getWindow() == null) {
                return;
            }
            initToggleState();
            toggleInit = true;
        }

        String combo = toggleBind.get();
        if (combo == null || combo.isBlank() || "NONE".equalsIgnoreCase(combo)) {
            return;
        }
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        boolean pressed = ClientScreen.current() == null && KeyManager.isComboHeldAllowScreen(combo);
        if (!pressed) {
            toggleArmed = true;
            return;
        }
        if (toggleArmed) {
            toggleNoRenderHidden();
            toggleArmed = false;
        }
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (mc == null || (mc.level == null && !preview)) {
            width = 0.0f;
            height = 0.0f;
            return;
        }
        if (!preview && isHiddenByNoRender()) {
            width = 0.0f;
            height = 0.0f;
            return;
        }
        if (!preview && !isEnabled()) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        Objective objective = resolveObjective();
        if (objective == null && !preview) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();
        Component title = resolveTitle(objective, preview);
        lines.clear();
        lines.addAll(resolveLines(objective, preview));
        if (lines.isEmpty()) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float baseScale = HudScale.scale(screenW, screenH) * scale.get().floatValue();
        float padX = BASE_PAD_X * baseScale;
        float padY = BASE_PAD_Y * baseScale;
        float rowGap = BASE_ROW_GAP * baseScale;
        float scoreGap = BASE_SCORE_GAP * baseScale;
        float footerGap = BASE_FOOTER_GAP * baseScale;
        float radius = BASE_RADIUS * baseScale;
        float titleScale = BASE_TITLE_SCALE * baseScale;
        float textScale = BASE_TEXT_SCALE * baseScale;
        float time = (float) (net.minecraft.util.Util.getMillis() / 1000.0);
        HudTextEffects.Effect effect = textEffect.get();
        int effectSpeed = textEffectSpeed.get();

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer titleRenderer = Fonts.renderer("Iosevka", FontInfo.Type.BoldItalic, fallback);
        TextRenderer bodyRenderer = Fonts.renderer("Iosevka", FontInfo.Type.Regular, titleRenderer);

        Component normalizedTitle = normalizeHeaderTitle(title);

        List<List<Part>> titleLines = splitStyledLines(normalizedTitle, uiTitleColor);
        float titleW = measureStyledLineWidth(titleRenderer, titleLines, titleScale);
        float titleH = rendererHeight(titleRenderer, titleScale);

        float textH = rendererHeight(bodyRenderer, textScale);
        float maxLabelW = 0.0f;
        float maxValueW = 0.0f;
        float maxFooterW = 0.0f;
        for (SidebarLine line : lines) {
            if (line.footer()) {
                maxFooterW = Math.max(maxFooterW, measureStyledText(bodyRenderer, line.label(), uiFooterColor, textScale));
                continue;
            }
            maxLabelW = Math.max(maxLabelW, measureStyledText(bodyRenderer, line.label(), uiLabelColor, textScale));
            if (!isBlankText(line.value())) {
                maxValueW = Math.max(maxValueW, measureStyledText(bodyRenderer, line.value(), uiValueColor, textScale));
            }
        }

        displayLabelWidth = smoothWidth(displayLabelWidth, maxLabelW);
        displayValueWidth = smoothWidth(displayValueWidth, maxValueW);
        displayFooterWidth = smoothWidth(displayFooterWidth, maxFooterW);

        float widestRow = displayLabelWidth + (displayValueWidth > 0.0f ? scoreGap + displayValueWidth : 0.0f);
        float widest = Math.max(titleW, Math.max(widestRow, displayFooterWidth));
        width = padX * 2.0f + widest;
        float titleLineGap = BASE_TITLE_LINE_GAP * baseScale;
        float titleBlockHeight = titleLines.size() * titleH + Math.max(0, titleLines.size() - 1) * titleLineGap;
        float headerHeight = titleBlockHeight + padY * 2.0f;

        int footerCount = 0;
        int bodyCount = 0;
        for (SidebarLine line : lines) {
            if (line.footer()) {
                footerCount++;
            } else {
                bodyCount++;
            }
        }

        float rowsH = bodyCount > 0 ? bodyCount * textH + Math.max(0, bodyCount - 1) * rowGap : 0.0f;
        float footersH = footerCount > 0 ? footerCount * textH + Math.max(0, footerCount - 1) * rowGap : 0.0f;
        float footerSectionGap = footerCount > 0 && bodyCount > 0 ? footerGap : 0.0f;
        height = headerHeight + padY + rowsH + footerSectionGap + footersH + padY;

        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, x, y, width, height, radius, baseScale,
                    HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get()), shadowAlpha.get(), 1.0f
            );
        }

        boolean blurEnabled = isBlurEffect();
        if (blurEnabled) {
            drawBlur(x, y, width, height, radius, uiBgPrimary);
        }
        drawBackground(renderer, x, y, width, height, radius, baseScale);
        drawHeader(renderer, x, y, width, headerHeight, radius);

        float cursorY = y + headerHeight + padY;
        float titleY = y + (headerHeight - titleBlockHeight) * 0.5f;

        renderStyledLinesCentered(renderer, titleRenderer, titleScale, x, width, titleY, titleLines, titleLineGap,
                textEffectSections.get("header"), effect, effectSpeed, time);

        boolean footerStarted = false;
        for (SidebarLine line : lines) {
            if (line.footer()) {
                if (!footerStarted && bodyCount > 0) {
                    cursorY += footerGap;
                    footerStarted = true;
                }
                renderStyledText(renderer, bodyRenderer, textScale, x + padX, cursorY, line.label(), uiFooterColor,
                        textEffectSections.get("footer"), effect, effectSpeed, time, 0.0f);
                cursorY += textH + rowGap;
                continue;
            }

            renderStyledText(renderer, bodyRenderer, textScale, x + padX, cursorY, line.label(), uiLabelColor,
                    textEffectSections.get("labels"), effect, effectSpeed, time, 0.0f);
            if (!isBlankText(line.value())) {
                float valueW = measureStyledText(bodyRenderer, line.value(), uiValueColor, textScale);
                float valueX = x + width - padX - valueW;
                renderStyledText(renderer, bodyRenderer, textScale, valueX, cursorY, line.value(), uiValueColor,
                        textEffectSections.get("values"), effect, effectSpeed, time, 0.2f);
            }
            cursorY += textH + rowGap;
        }
    }

    @Override
    public boolean supportsWidgetAnchoring() {
        return true;
    }

    private Objective resolveObjective() {
        if (mc == null || mc.level == null || mc.player == null) {
            return null;
        }

        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            DisplaySlot slot = team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null);
            if (slot != null) {
                objective = scoreboard.getDisplayObjective(slot);
            }
        }
        return objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    private Component resolveTitle(Objective objective, boolean preview) {
        if (objective == null) {
            return preview ? Component.literal("Scoreboard") : Component.empty();
        }
        Component title = LegacyTextUtil.convertLegacyCodes(objective.getDisplayName());
        return isBlankText(title) ? Component.literal("Scoreboard") : title;
    }

    private List<SidebarLine> resolveLines(Objective objective, boolean preview) {
        if (objective == null) {
            return preview ? previewLinesSample : List.of();
        }

        net.minecraft.world.scores.Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<SidebarLine> resolved = new ArrayList<>();
        List<PlayerScoreEntry> visibleEntries = scoreboard.listPlayerScores(objective)
                .stream()
                .filter(entry -> !entry.isHidden())
                .sorted(ENTRY_COMPARATOR)
                .limit(MAX_ENTRIES)
                .toList();

        visibleEntries.forEach(entry -> {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component label = LegacyTextUtil.convertLegacyCodes(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
            Component value = LegacyTextUtil.convertLegacyCodes(entry.formatValue(numberFormat));
            if (isBlankText(label) && isBlankText(value)) {
                return;
            }
            resolved.add(new SidebarLine(label, value, isBlankText(value)));
        });

        ServerDumpUtil.dumpScoreboardSidebar(
                objective,
                visibleEntries,
                resolved.stream().map(line -> line.label().getString()).toList(),
                resolved.stream().map(line -> line.value().getString()).toList()
        );

        return resolved.isEmpty() && preview ? previewLinesSample : resolved;
    }

    private void toggleNoRenderHidden() {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender == null) return;
        noRender.setHideScoreboardRaw(!noRender.hideScoreboardRaw());
    }

    private void initToggleState() {
        toggleArmed = true;
        String combo = toggleBind.get();
        if (combo != null && !combo.isBlank() && !"NONE".equalsIgnoreCase(combo)
                && mc != null && ClientScreen.current() == null
                && KeyManager.isComboHeldAllowScreen(combo)) {
            toggleArmed = false;
        }
    }

    private void drawBlur(float x, float y, float w, float h, float radius, int tintRgb) {
        if (!hasEffect()) return;
        float quality = hud.getBlurRadius();
        float brightness = 1.0f;
        float alpha = blurAlpha.get() / 255f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private void updatePalette() {
        if (isThemeMode()) {
            int panelAlpha = bgAlpha.get();
            uiBgPrimary = HudRenderUtil.setAlpha(theme().windowBg(), panelAlpha);
            uiBgSecondary = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowHeader(), 0.35f),
                    panelAlpha
            );
            uiHeaderBg = HudRenderUtil.setAlpha(theme().windowHeader(), panelAlpha);
            if (isAccentPanelStyle()) {
                uiBgPrimary = HudRenderUtil.accentSurface(uiBgPrimary, 0.20f);
                uiBgSecondary = HudRenderUtil.accentSurface(uiBgSecondary, 0.28f);
                uiHeaderBg = HudRenderUtil.accentSurface(uiHeaderBg, 0.32f);
            }
            uiStroke = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.4f),
                    Math.min(panelAlpha, 190)
            );
            uiTitleColor = theme().textPrimary();
            uiLabelColor = theme().textMuted();
            uiValueColor = theme().textPrimary();
            uiFooterColor = HudRenderUtil.mixColor(theme().accent(), theme().textPrimary(), 0.35f);
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiHeaderBg = HudRenderUtil.mixColor(bg2.getArgb(), bg.getArgb(), 0.2f);
        uiStroke = stroke.getArgb();
        uiTitleColor = titleColor.getArgb();
        uiLabelColor = labelColor.getArgb();
        uiValueColor = valueColor.getArgb();
        uiFooterColor = footerColor.getArgb();
    }

    private void drawBackground(Renderer2D renderer,
                                float x,
                                float y,
                                float width,
                                float height,
                                float radius,
                                float drawScale) {
        if (isThemeMode()) {
            HudRenderUtil.drawHudBackground(renderer, x, y, width, height, radius, BASE_SOFTNESS, uiBgPrimary, true);
        } else {
            renderer.roundedRectGradientQuad(x, y, width, height, radius, BASE_SOFTNESS,
                    uiBgPrimary, uiBgSecondary, uiBgPrimary, uiBgSecondary);
        }

        if (strokeEnabled.get()) {
            HudRenderUtil.drawHudStroke(
                    renderer, x, y, width, height, radius, BASE_SOFTNESS,
                    Math.max(0.5f, BASE_STROKE * drawScale),
                    uiStroke, isThemeMode() && strokeGradient.get(),
                    strokeAlpha.get(), 1.0f
            );
        }
    }

    private void drawHeader(Renderer2D renderer,
                            float x,
                            float y,
                            float width,
                            float headerHeight,
                            float radius) {
        renderer.roundedRectCorners(x, y, width, headerHeight, radius, radius, 0.0f, 0.0f, BASE_SOFTNESS, uiHeaderBg);
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isBlurEffect() {
        return EFFECT_BLUR.equals(bgEffect.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private record SidebarLine(Component label, Component value, boolean footer) {
    }
}
