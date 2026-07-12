/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.player.PlayerHealthResolver;

import java.util.List;

import static combatant.client.features.theme.Theme.theme;

/**
 * Player radar widget with a rectangular map and text list.
 */
@HudElementRegister(order = 140)
public final class Radar extends DraggableHudElement {

    {
        defaultLayout(1666.0f, 17.915222f);
    }


    private static final float BASE_RADIUS = 6f;
    private static final float BASE_LINE_W = 1.0f;
    private static final float BASE_PAD = 3f;
    private static final float BLIP_SIZE = 4f;
    private static final float BLIP_MARGIN = 6f;
    private static final float TEXT_SCALE_MULT = 0.6f;
    private static final String FIELD_KEY_PREFIX = "radar_field_";
    private static final Field[] FIELD_OPTIONS = {
            Field.NAME,
            Field.HP,
            Field.DISTANCE,
            Field.PING,
            Field.NONE
    };
    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final EnumValue<Mode> mode =
            new EnumValue<>("radar_mode", Mode.RECT, Mode.RECT, Mode.TEXT);
    private final EnumValue<ColorMode> colorMode =
            new EnumValue<>("radar_color_mode", ColorMode.SYNC, ColorMode.SYNC, ColorMode.CUSTOM);
    private final NumberValue<Double> size =
            new NumberValue<>("radar_size", 217.27, 20.0, 300.0);
    private final RGBAColorValue bgColor =
            new RGBAColorValue("radar_bg_color", "#CC101010");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("radar_bg_alpha", 109, 0, 255);
    private final RGBColorValue textColor =
            new RGBColorValue("radar_text_color", "#FFFFFF");
    private final EnumValue<Field> field1 = fieldSlot(1, Field.NAME);
    private final EnumValue<Field> field2 = fieldSlot(2, Field.HP);
    private final EnumValue<Field> field3 = fieldSlot(3, Field.PING);
    private final EnumValue<Field> field4 = fieldSlot(4, Field.NONE);
    private final EnumValue<Field> field5 = fieldSlot(5, Field.NONE);
    private final BooleanValue blur =
            new BooleanValue("radar_blur", true);
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("radar_blur_alpha", 255, 0, 255);
    private final BooleanValue gradient =
            new BooleanValue("radar_gradient", false);
    public Radar() {
        super("radar", "Radar", false);
    }

    private static EnumValue<Field> fieldSlot(int slot, Field def) {
        return new EnumValue<>(FIELD_KEY_PREFIX + slot, def, FIELD_OPTIONS);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.mode(mode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.number(size));
        defs.add(SettingDef.color(bgColor)
                .visibleWhen(() -> colorMode.get() == ColorMode.CUSTOM));
        defs.add(SettingDef.number(bgAlpha)
                .visibleWhen(() -> colorMode.get() == ColorMode.SYNC));
        defs.add(SettingDef.colorNoAlpha(textColor)
                .visibleWhen(() -> mode.get() == Mode.TEXT
                        && colorMode.get() == ColorMode.CUSTOM));
        defs.add(SettingDef.mode(field1)
                .visibleWhen(() -> mode.get() == Mode.TEXT));
        defs.add(SettingDef.mode(field2)
                .visibleWhen(() -> mode.get() == Mode.TEXT));
        defs.add(SettingDef.mode(field3)
                .visibleWhen(() -> mode.get() == Mode.TEXT));
        defs.add(SettingDef.mode(field4)
                .visibleWhen(() -> mode.get() == Mode.TEXT));
        defs.add(SettingDef.mode(field5)
                .visibleWhen(() -> mode.get() == Mode.TEXT));
        defs.add(SettingDef.bool(blur));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(blur::get));
        defs.add(SettingDef.bool(gradient)
                .visibleWhen(() -> colorMode.get() == ColorMode.SYNC));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 120f;
        this.y = 120f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (mc == null && !preview) {
            width = 0f;
            height = 0f;
            return;
        }
        if (!preview && !isEnabled()) {
            width = 0f;
            height = 0f;
            return;
        }

        if (mode.get() == Mode.TEXT) {
            renderTextMode(textRenderer, screenW, screenH, preview);
        } else {
            renderRectMode(renderer, tickDelta, screenW, screenH);
        }
    }

    private void renderRectMode(Renderer2D renderer, float tickDelta, int screenW, int screenH) {
        float scale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f);
        float size = this.size.get().floatValue() * scale;
        float baseX = x;
        float baseY = y;
        float radius = Math.min(BASE_RADIUS * scale, size * 0.25f);
        float lineW = Math.max(0.5f, BASE_LINE_W * scale);
        float pad = BASE_PAD * scale;

        int bgAlpha = this.bgAlpha.get();
        boolean syncTheme = colorMode.get() == ColorMode.SYNC;
        int bgRgb = syncTheme
                ? (theme().windowBg() & 0x00FFFFFF)
                : (bgColor.getArgb() & 0x00FFFFFF);
        int bg = (bgAlpha << 24) | bgRgb;

        boolean blurEnabled = blur.get();
        if (blurEnabled) {
            drawBlur(baseX, baseY, size, size, radius, bg);
        }
        boolean useGradient = syncTheme && gradient.get();
        HudRenderUtil.drawHudBackground(renderer, baseX, baseY, size, size, radius, 1.0f, bg, useGradient);

        int stroke = HudRenderUtil.setAlpha(theme().accent(), 0x55);
        renderer.roundedRectStroke(baseX, baseY, size, size, radius, 1.0f,
                Math.max(1.0f, 1.25f * scale), stroke);

        int cross = HudRenderUtil.setAlpha(theme().textMuted(), 0x55);
        float cx = baseX + size * 0.5f;
        float cy = baseY + size * 0.5f;
        renderer.quad(cx - lineW * 0.5f, baseY + pad, lineW, size - pad * 2f, cross);
        renderer.quad(baseX + pad, cy - lineW * 0.5f, size - pad * 2f, lineW, cross);

        renderBlips(renderer, tickDelta, cx, cy, size * 0.5f - BLIP_MARGIN * scale, scale);

        width = size;
        height = size;
    }

    private void renderBlips(Renderer2D renderer, float tickDelta, float cx, float cy, float half, float scale) {
        if (mc == null || mc.player == null || mc.level == null) return;

        float blip = Math.max(2.5f, BLIP_SIZE * scale);
        float blipRadius = Math.max(1.0f, blip * 0.5f);
        float yaw = mc.player.getViewYRot(tickDelta);
        float yawRad = (float) Math.toRadians(yaw);
        float cos = Mth.cos(yawRad);
        float sin = Mth.sin(yawRad);

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            double px = player.getX();
            double pz = player.getZ();
            double dx = px - mc.player.getX();
            double dz = pz - mc.player.getZ();

            float rotY = (float) (-(dz * cos - dx * sin)) * 2.0f;
            float rotX = (float) (-(dx * cos + dz * sin)) * 2.0f;

            rotX = Mth.clamp(rotX, -half, half);
            rotY = Mth.clamp(rotY, -half, half);

            int color = CategoryService.getColor(player);
            renderer.roundedRect(cx + rotX - blip * 0.5f, cy + rotY - blip * 0.5f,
                    blip, blip, blipRadius, 1.0f, color);
        }
    }

    private void renderTextMode(TextRenderer textRenderer, int screenW, int screenH, boolean preview) {
        boolean ready = mc != null && mc.player != null && mc.level != null;
        if (!ready && !preview) {
            width = 0f;
            height = 0f;
            return;
        }

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer renderer = Fonts.renderer("Iosevka", FontInfo.Type.Regular, fallback);

        float scale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * TEXT_SCALE_MULT;

        renderer.begin(scale, false, false);
        float lineH = (float) renderer.getHeight(false);
        renderer.end();

        int textColor = colorMode.get() == ColorMode.CUSTOM
                ? 0xFF000000 | (this.textColor.getArgb() & 0x00FFFFFF)
                : theme().accent();

        float maxW = 0f;
        float yOff = 0f;
        if (ready) {
            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                String line = buildLine(player);
                if (line.isEmpty()) continue;

                renderer.begin(scale, false, false);
                float w = (float) renderer.getWidth(line, false);
                renderer.render(line, x, y + yOff, new RenderColor(textColor), false);
                renderer.end();

                maxW = Math.max(maxW, w);
                yOff += lineH;
            }
        }

        if (preview && yOff <= 0f) {
            String line = "Player 20 40ms";
            renderer.begin(scale, false, false);
            float w = (float) renderer.getWidth(line, false);
            renderer.render(line, x, y, new RenderColor(textColor), false);
            renderer.end();
            maxW = w;
            yOff = lineH;
        }

        width = maxW;
        height = Math.max(lineH, yOff);
    }

    private String buildLine(Player player) {
        StringBuilder out = new StringBuilder(64);
        appendField(out, field1.get(), player);
        appendField(out, field2.get(), player);
        appendField(out, field3.get(), player);
        appendField(out, field4.get(), player);
        appendField(out, field5.get(), player);
        return out.toString().trim();
    }

    private void appendField(StringBuilder out, Field field, Player player) {
        if (field == null || field == Field.NONE) return;
        if (out.length() > 0) out.append(' ');
        switch (field) {
            case HP -> {
                int hp = (int) Math.ceil(PlayerHealthResolver.totalHealth(player));
                out.append(hp);
            }
            case DISTANCE -> {
                int dist = (int) Math.ceil(mc.player.distanceTo(player));
                out.append(dist).append('m');
            }
            case PING -> {
                int ping = getPlayerPing(player);
                if (ping >= 0) {
                    out.append(ping).append("ms");
                } else {
                    out.append("--");
                }
            }
            case NAME -> out.append(player.getName().getString());
            default -> {
                // ignore
            }
        }
    }

    private int getPlayerPing(Player player) {
        if (mc == null || mc.getConnection() == null) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        return entry != null ? entry.getLatency() : -1;
    }

    private void drawBlur(float x, float y, float w, float h, float radius, int tintRgb) {
        float quality = hud.getBlurRadius();
        float brightness = 1.0f;
        float alpha = blurAlpha.get() / 255f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private enum Mode implements EnumValue.IdProvider {
        RECT("Rect"),
        TEXT("Text");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum ColorMode implements EnumValue.IdProvider {
        SYNC("Sync"),
        CUSTOM("Custom");

        private final String id;

        ColorMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum Field implements EnumValue.IdProvider {
        NAME("Name"),
        HP("Hp"),
        DISTANCE("Distance"),
        PING("Ping"),
        NONE("None");

        private final String id;

        Field(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}




