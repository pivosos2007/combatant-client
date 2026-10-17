/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.schematic.view3d.FaceList;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.player.schematic.SchematicBuilder;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.schematic.LayerStats;
import combatant.client.util.schematic.LitematicReader;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicAttention;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicHeader;
import combatant.client.util.schematic.SchematicFiles;
import combatant.client.util.schematic.SchematicLibrary;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.screen.ClientScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The schematic menu: file browser on the left, the build as an orbitable 3D model (or the selected layer
 * top-down) in the middle with a layer strip that doubles as the slider, and placement, builder and material
 * controls on the right.
 */
public final class SchematicScreen extends Screen {

    private static final float HEADER_H = 64f;
    private static final float PAD = 18f;
    private static final float GAP = 8f;
    private static final float BUTTON_H = 36f;
    private static final float OVERLAY_H = 28f;
    private static final float ROW_H = 54f;
    private static final float MATERIAL_ROW_H = 36f;
    private static final float STRIP_W = 72f;
    private static final float OPEN_SLIDE = 26f;
    private static final int[] PLAY_SPEEDS = {1, 2, 4, 8};
    private static final long HAVE_REFRESH_NANOS = 500_000_000L;
    private static final long BUBBLE_HOLD_NANOS = 900_000_000L;

    private enum Btn {
        CLOSE, REFRESH, LOAD, OPEN_FOLDER,
        ORIGIN_HERE, ORIGIN_LOOK, ROTATE, MIRROR, BUILDER, PROJECTION, LAYER_MODE, UNLOAD,
        LAYER_DOWN10, LAYER_DOWN, PLAY, SPEED, LAYER_UP, LAYER_UP10,
        TAB_LAYER, TAB_TOTAL, TAB_ISSUES,
        TAB_3D, TAB_2D, VIEW_MODE, AUTO_ROTATE, RESET_VIEW
    }

    private static final Btn[] BUTTONS = Btn.values();

    private final Screen parent;
    private final SchematicUi ui = new SchematicUi();
    private final SchematicLayerGrid grid = new SchematicLayerGrid();
    private final SchematicViewport viewport = new SchematicViewport();
    private final SchematicState state = SchematicState.INSTANCE;
    private final SchematicLibrary library = SchematicLibrary.INSTANCE;
    private final float[] bx = new float[BUTTONS.length];
    private final float[] by = new float[BUTTONS.length];
    private final float[] bw = new float[BUTTONS.length];
    private final float[] bh = new float[BUTTONS.length];
    private final boolean[] bShown = new boolean[BUTTONS.length];
    private final float[] hover = new float[BUTTONS.length];
    private final float[] press = new float[BUTTONS.length];
    private final float[] rowHover = new float[64];

    private float fixedWidth;
    private float fixedHeight;
    private float panelX, panelY, panelW, panelH;
    private float leftX, leftW, centreX, centreW, rightX, rightW;
    private float contentY, contentBottom;
    private float searchX, searchY, searchW, searchH;
    private float listY, listH;
    private float gridX, gridY, gridW, gridH;
    private float insetX, insetY, insetS;
    private float gapX, gapY, gapW;
    private float stripX, stripY, stripH;
    private float progressY;
    private float infoY;
    private float materialsY, materialsH;
    private float bodyH, smallH, titleH, tinyH;
    private float mouseX, mouseY;
    private float openAnim;
    private float frameDt;

    private List<SchematicHeader> filtered = List.of();
    private int filteredGeneration = -1;
    private String filteredQuery = "";
    private String[] rowFormat = new String[0];
    private String[] rowDims = new String[0];
    private String[] rowBlocks = new String[0];
    private int listFirst;
    private Path selectedPath;
    private String search = "";
    private boolean searchFocused;

    private boolean materialsTotal;
    private boolean issuesShown;
    private int materialsFirst;
    private List<SchematicAttention.Entry> issues = List.of();
    private List<ItemStack> issueStacks = List.of();
    private Object issuesKey;
    private boolean[] materialRedstone = new boolean[0];
    private List<SchematicMaterials.Entry> materials = List.of();
    private List<ItemStack> materialStacks = List.of();
    private int[] materialHave = new int[0];
    private Object materialsKey;
    private long materialsHaveNanos;

    private boolean playing;
    private int speedIndex = 1;
    private float playAccumulator;
    private boolean draggingStrip;
    private boolean draggingGap;
    private long lastFrameNanos;

    private boolean view3d = true;
    private float viewFade = 1f;
    private float thumbLayer;
    private float bubble;
    private int bubbleLayer = -1;
    private long bubbleNanos;

    public SchematicScreen(Screen parent) {
        super(Component.translatable("screen.combatant.schematic.title"));
        this.parent = parent instanceof ChatScreen ? null : parent;
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    protected void init() {
        ui.init();
        ui.clearCaches();
        bodyH = ui.height(SchematicUi.BODY);
        smallH = ui.height(SchematicUi.SMALL);
        titleH = ui.height(SchematicUi.TITLE);
        tinyH = ui.height(SchematicUi.TINY);
        library.refresh();
        if (selectedPath == null && state.source() != null) {
            selectedPath = state.source();
        } else if (selectedPath == null && state.schematic() != null) {
            library.resolve(state.schematic().fileName()).thenAccept(found -> minecraft.execute(() -> {
                if (selectedPath == null) selectedPath = found.path();
            }));
        }
        thumbLayer = state.layer();
    }

    @Override
    public void tick() {
        state.tickDiff(minecraft, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mx, int my, float delta) {
        updateUiMetrics();
        SchematicUi.syncTheme();
        if (minecraft != null) MenuBackgroundRenderer.renderConfigured(minecraft);
        mouseX = toFixed(mx);
        mouseY = toFixed(my);
        layout();
        animate();

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            float alpha = AnimationUtility.easeOutCubic(openAnim);
            renderDimmer(alpha);
            ui.panel(panelX, panelY, panelW, panelH, 16f, alpha);
            renderHeader(alpha);
            renderLeft(alpha);
            renderCentre(alpha);
            renderRight(alpha);
            renderTooltip(alpha);
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        panelW = Math.min(fixedWidth - 60f, 1760f);
        panelH = Math.min(fixedHeight - 60f, 1000f);
        panelX = (fixedWidth - panelW) * 0.5f;
        panelY = (fixedHeight - panelH) * 0.5f + (1f - AnimationUtility.easeOutCubic(openAnim)) * OPEN_SLIDE;
        contentY = panelY + HEADER_H;
        contentBottom = panelY + panelH - PAD;
        leftW = clamp(panelW * 0.21f, 280f, 380f);
        rightW = clamp(panelW * 0.25f, 330f, 440f);
        leftX = panelX + PAD;
        rightX = panelX + panelW - PAD - rightW;
        centreX = leftX + leftW + PAD;
        centreW = rightX - PAD - centreX;
        java.util.Arrays.fill(bShown, false);

        place(Btn.CLOSE, panelX + panelW - PAD - 44f, panelY + 14f, 44f, BUTTON_H);
        layoutLeft();
        layoutCentre();
        layoutRight();
    }

    private void layoutLeft() {
        searchX = leftX;
        searchY = contentY;
        searchH = 38f;
        searchW = leftW - 98f;
        place(Btn.REFRESH, leftX + leftW - 90f, contentY, 90f, searchH);
        float buttonsTop = contentBottom - BUTTON_H * 2f - GAP;
        listY = searchY + searchH + 10f;
        listH = Math.max(ROW_H, buttonsTop - 10f - listY);
        place(Btn.LOAD, leftX, buttonsTop, leftW, BUTTON_H);
        place(Btn.OPEN_FOLDER, leftX, buttonsTop + BUTTON_H + GAP, leftW, BUTTON_H);
    }

    private void layoutCentre() {
        float ctrlY = contentBottom - BUTTON_H;
        float[] widths = {64f, 52f, 96f, 72f, 52f, 64f};
        Btn[] ids = {Btn.LAYER_DOWN10, Btn.LAYER_DOWN, Btn.PLAY, Btn.SPEED, Btn.LAYER_UP, Btn.LAYER_UP10};
        float total = GAP * (ids.length - 1);
        for (float w : widths) total += w;
        float x = centreX + (centreW - total) * 0.5f;
        for (int i = 0; i < ids.length; i++) {
            place(ids[i], x, ctrlY, widths[i], BUTTON_H);
            x += widths[i] + GAP;
        }
        progressY = ctrlY - 46f;
        gridX = centreX;
        gridY = contentY + titleH + smallH + 22f;
        gridW = centreW - STRIP_W - 16f;
        gridH = Math.max(40f, progressY - 14f - gridY);
        stripX = centreX + centreW - STRIP_W;
        stripY = gridY;
        stripH = gridH;
        if (state.hasSchematic()) {
            place(Btn.TAB_3D, centreX + centreW - 112f - 4f, contentY, 56f, 30f);
            place(Btn.TAB_2D, centreX + centreW - 56f, contentY, 56f, 30f);
        }
        insetS = clamp(Math.min(gridW, gridH) * 0.28f, 90f, 220f);
        insetX = gridX + 10f;
        insetY = gridY + gridH - 10f - insetS;
        gapW = 170f;
        gapX = gridX + gridW - 12f - gapW;
        gapY = gridY + 10f + tinyH + 8f;
        if (view3dActive()) {
            place(Btn.VIEW_MODE, gridX + 10f, gridY + 10f, 132f, OVERLAY_H);
            place(Btn.AUTO_ROTATE, gridX + 10f + 132f + 6f, gridY + 10f, 108f, OVERLAY_H);
            place(Btn.RESET_VIEW, gridX + 10f + 246f + 6f, gridY + 10f, 92f, OVERLAY_H);
        }
        Schematic schematic = state.schematic();
        if (schematic == null) return;
        if (view3d) {
            grid.layout(schematic, insetX + 6f, insetY + 6f, insetS - 12f, insetS - 12f);
        } else {
            grid.layout(schematic, gridX + 8f, gridY + 8f, gridW - 16f, gridH - 16f);
        }
    }

    private void layoutRight() {
        infoY = contentY;
        float buttonsY = infoY + 11f * (smallH + 7f) + 10f;
        float half = (rightW - GAP) * 0.5f;
        Btn[] ids = {Btn.ORIGIN_HERE, Btn.ORIGIN_LOOK, Btn.ROTATE, Btn.MIRROR, Btn.BUILDER, Btn.PROJECTION, Btn.LAYER_MODE, Btn.UNLOAD};
        for (int i = 0; i < ids.length; i++) {
            place(ids[i], rightX + (i % 2) * (half + GAP), buttonsY + (i / 2) * (BUTTON_H + GAP), half, BUTTON_H);
        }
        float tabsY = buttonsY + 4f * (BUTTON_H + GAP) + 10f;
        float third = (rightW - GAP * 2f) / 3f;
        place(Btn.TAB_LAYER, rightX, tabsY, third, 32f);
        place(Btn.TAB_TOTAL, rightX + third + GAP, tabsY, third, 32f);
        place(Btn.TAB_ISSUES, rightX + (third + GAP) * 2f, tabsY, third, 32f);
        materialsY = tabsY + 32f + GAP;
        materialsH = Math.max(MATERIAL_ROW_H, contentBottom - materialsY);
    }

    private void place(Btn id, float x, float y, float w, float h) {
        int i = id.ordinal();
        bx[i] = x;
        by[i] = y;
        bw[i] = w;
        bh[i] = h;
        bShown[i] = true;
    }

    private boolean over(Btn id) {
        int i = id.ordinal();
        return bShown[i] && SchematicUi.inside(mouseX, mouseY, bx[i], by[i], bw[i], bh[i]);
    }

    private boolean view3dActive() {
        return view3d && state.hasSchematic() && SchematicActions.loadingPath() == null;
    }

    private boolean overViewportChrome(float mx, float my) {
        if (over(Btn.VIEW_MODE) || over(Btn.AUTO_ROTATE) || over(Btn.RESET_VIEW)) return true;
        if (SchematicUi.inside(mx, my, insetX, insetY, insetS, insetS)) return true;
        return viewport.mode().exploded() && SchematicUi.inside(mx, my, gapX - 6f, gridY + 6f, gapW + 12f, gapY - gridY + 14f);
    }

    private boolean insideViewport(float mx, float my) {
        return SchematicUi.inside(mx, my, gridX, gridY, gridW, gridH);
    }

    private void animate() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        frameDt = dt;
        openAnim = AnimationUtility.snap(AnimationUtility.approach(openAnim, 1f, dt, 7f), 1f, 0.001f);
        viewFade = AnimationUtility.snap(AnimationUtility.approach(viewFade, 1f, dt, 9f), 1f, 0.001f);
        for (Btn id : BUTTONS) {
            int i = id.ordinal();
            hover[i] = AnimationUtility.snap(AnimationUtility.approach(hover[i], over(id) ? 1f : 0f, dt, 12f), over(id) ? 1f : 0f, 0.001f);
            press[i] = AnimationUtility.snap(AnimationUtility.approach(press[i], 0f, dt, 8f), 0f, 0.001f);
        }
        animateStrip(dt, now);
        advancePlayback(dt);
    }

    private void animateStrip(float dt, long now) {
        int layer = state.layer();
        thumbLayer = AnimationUtility.snap(AnimationUtility.approach(thumbLayer, layer, dt, 14f), layer, 0.01f);
        if (layer != bubbleLayer) {
            bubbleLayer = layer;
            bubbleNanos = now;
        }
        boolean show = draggingStrip || SchematicUi.inside(mouseX, mouseY, stripX, stripY, STRIP_W, stripH)
                || now - bubbleNanos < BUBBLE_HOLD_NANOS;
        bubble = AnimationUtility.snap(AnimationUtility.approach(bubble, show ? 1f : 0f, dt, 10f), show ? 1f : 0f, 0.001f);
    }

    private void advancePlayback(float dt) {
        if (!playing || !state.hasSchematic()) return;
        playAccumulator += dt * PLAY_SPEEDS[speedIndex];
        while (playAccumulator >= 1f) {
            playAccumulator -= 1f;
            int next = state.layer() + 1;
            state.setLayer(next >= state.layerCount() ? 0 : next);
        }
    }

    // ---------------------------------------------------------------- header + left

    private void renderDimmer(float alpha) {
        Themes.Theme t = Theme.theme();
        int soft = SchematicUi.withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.46f), Math.round(150f * alpha));
        int deep = SchematicUi.withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.70f), Math.round(190f * alpha));
        Renderer2D.COLOR.quad(0, 0, fixedWidth, fixedHeight, soft, soft, deep, deep);
    }

    private void renderHeader(float alpha) {
        float y = panelY + (HEADER_H - titleH) * 0.5f;
        Renderer2D.COLOR.roundedRect(panelX + PAD, panelY + (HEADER_H - 22f) * 0.5f, 4f, 22f, 2f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        ui.text(tr("screen.combatant.schematic.title"), panelX + PAD + 14f, y, SchematicUi.TITLE, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
        Schematic schematic = state.schematic();
        String sub = schematic == null ? tr("screen.combatant.schematic.none_loaded")
                : schematic.name() + "  ·  " + schematic.sizeX() + "x" + schematic.sizeY() + "x" + schematic.sizeZ();
        float titleW = ui.width(tr("screen.combatant.schematic.title"), SchematicUi.TITLE, true);
        ui.text(ui.ellipsize(sub, SchematicUi.BODY, panelW * 0.4f), panelX + PAD + 14f + titleW + 20f, panelY + (HEADER_H - bodyH) * 0.5f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        String keys = ui.ellipsize(tr("screen.combatant.schematic.keys_hint"), SchematicUi.TINY, panelW * 0.3f);
        ui.textRight(keys, panelX + panelW - PAD - 44f - 16f, panelY + (HEADER_H - tinyH) * 0.5f, SchematicUi.TINY,
                SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
        drawButton(Btn.CLOSE, "X", false, true, alpha);
        float lineY = contentY - 10f;
        int edge = SchematicUi.withAlpha(SchematicUi.STROKE, 0);
        int mid = SchematicUi.withAlpha(SchematicUi.mix(SchematicUi.STROKE, SchematicUi.ACCENT, 0.3f), Math.round(alpha * 200f));
        float half = (panelW - PAD * 2f) * 0.5f;
        Renderer2D.COLOR.quad(panelX + PAD, lineY, half, 1f, edge, mid, mid, edge);
        Renderer2D.COLOR.quad(panelX + PAD + half, lineY, half, 1f, mid, edge, edge, mid);
    }

    private void renderLeft(float alpha) {
        renderSearch(alpha);
        drawButton(Btn.REFRESH, library.isScanning() ? "..." : tr("screen.combatant.schematic.refresh"), false, true, alpha);
        ui.section(leftX, listY, leftW, listH, alpha);
        refreshFiltered();
        int visible = Math.max(1, (int) ((listH - 8f) / ROW_H));
        listFirst = clampInt(listFirst, 0, Math.max(0, filtered.size() - visible));
        if (filtered.isEmpty()) renderEmptyList(alpha);
        for (int i = 0; i < visible && listFirst + i < filtered.size(); i++) {
            renderRow(filtered.get(listFirst + i), listY + 4f + i * ROW_H, i, alpha);
        }
        if (filtered.size() > visible) renderScrollbar(leftX + leftW - 5f, listY + 6f, listH - 12f, listFirst, visible, filtered.size(), alpha);
        boolean canLoad = selectedPath != null && SchematicActions.loadingPath() == null;
        drawButton(Btn.LOAD, tr("screen.combatant.schematic.load"), false, canLoad, alpha);
        drawButton(Btn.OPEN_FOLDER, tr("screen.combatant.schematic.open_folder"), false, true, alpha);
    }

    private void renderEmptyList(float alpha) {
        float cx = leftX + leftW * 0.5f;
        float cy = listY + Math.min(listH * 0.35f, 120f);
        if (library.isScanning()) {
            float spin = (System.nanoTime() / 1_000_000L % 1200L) / 1200f * 360f;
            Renderer2D.COLOR.arcStroke(cx, cy, 16f, 3f, spin, spin + 260f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        } else {
            SchematicViewport.drawCubeIcon(cx, cy, 18f, SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.35f), alpha);
        }
        boolean noMatch = !library.isScanning() && !library.headers().isEmpty();
        String empty = library.isScanning() ? tr("screen.combatant.schematic.scanning")
                : tr(noMatch ? "screen.combatant.schematic.no_match" : "screen.combatant.schematic.empty");
        ui.textCentered(ui.ellipsize(empty, SchematicUi.SMALL, leftW - 24f), cx, cy + 32f, SchematicUi.SMALL,
                SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        if (!noMatch) {
            ui.textCentered(ui.ellipsize(tr("screen.combatant.schematic.empty_hint"), SchematicUi.TINY, leftW - 24f), cx, cy + 38f + smallH,
                    SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
        }
    }

    private void renderSearch(float alpha) {
        int stroke = searchFocused ? SchematicUi.ACCENT : SchematicUi.STROKE;
        Renderer2D.COLOR.roundedRect(searchX, searchY, searchW, searchH, 8f, SchematicUi.scaleAlpha(SchematicUi.WELL, alpha * 0.9f));
        Renderer2D.COLOR.roundedRectStroke(searchX, searchY, searchW, searchH, 8f, 1f, SchematicUi.scaleAlpha(stroke, alpha));
        String shown = search.isEmpty() && !searchFocused ? tr("screen.combatant.schematic.search") : search;
        if (searchFocused && AnimationUtility.blink(500L)) shown += "|";
        int color = search.isEmpty() && !searchFocused ? SchematicUi.FAINT : SchematicUi.TEXT;
        ui.text(ui.ellipsize(shown, SchematicUi.BODY, searchW - 20f), searchX + 10f, searchY + (searchH - bodyH) * 0.5f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(color, alpha), false);
    }

    private void renderRow(SchematicHeader header, float y, int slot, float alpha) {
        float x = leftX + 4f;
        float w = leftW - 14f;
        float rh = ROW_H - 4f;
        boolean selected = header.path().equals(selectedPath);
        boolean hovered = SchematicUi.inside(mouseX, mouseY, x, y, w, rh);
        boolean active = state.source() != null ? state.source().equals(header.path())
                : state.schematic() != null && SchematicFiles.baseName(header.path()).equals(state.schematic().fileName());
        float h = 0f;
        if (slot < rowHover.length) {
            rowHover[slot] = AnimationUtility.approach(rowHover[slot], hovered ? 1f : 0f, frameDt, 14f);
            h = rowHover[slot];
        }
        int fill = selected ? SchematicUi.mix(SchematicUi.SURFACE, SchematicUi.ACCENT, 0.16f + h * 0.05f) : SchematicUi.SURFACE_HOVER;
        float fillAlpha = selected ? 1f : h;
        if (fillAlpha > 0.01f) Renderer2D.COLOR.roundedRect(x, y, w, rh, 8f, SchematicUi.scaleAlpha(fill, alpha * fillAlpha));
        if (selected) Renderer2D.COLOR.roundedRect(x + 1f, y + 9f, 3f, rh - 18f, 1.5f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        int nameColor = header.failed() ? SchematicUi.BAD : selected ? SchematicUi.mix(SchematicUi.TEXT, SchematicUi.ACCENT, 0.4f) : SchematicUi.TEXT;
        ui.text(ui.ellipsize(header.name(), SchematicUi.BODY, w - 36f), x + 12f + h * 2f, y + 6f, SchematicUi.BODY, SchematicUi.scaleAlpha(nameColor, alpha), false);
        if (active) {
            Renderer2D.COLOR.circle(x + w - 12f, y + 6f + bodyH * 0.5f, 6f, SchematicUi.withAlpha(SchematicUi.GOOD, Math.round(alpha * 50f)));
            Renderer2D.COLOR.circle(x + w - 12f, y + 6f + bodyH * 0.5f, 3f, SchematicUi.scaleAlpha(SchematicUi.GOOD, alpha));
        }
        float chipY = y + 11f + bodyH;
        if (header.failed()) {
            float cw = ui.chip(x + 12f, chipY, tr("screen.combatant.schematic.row_failed"), SchematicUi.BAD, alpha);
            ui.text(ui.ellipsize(header.error(), SchematicUi.TINY, w - cw - 28f), x + 18f + cw, chipY + 2f, SchematicUi.TINY,
                    SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
            return;
        }
        int index = listFirst + slot;
        float cx = x + 12f;
        cx += ui.chip(cx, chipY, rowDims[index], SchematicUi.ACCENT, alpha) + 6f;
        String blocksLabel = rowBlocks[index];
        if (!blocksLabel.isEmpty()) cx += ui.chip(cx, chipY, blocksLabel, SchematicUi.GOOD, alpha) + 6f;
        String format = rowFormat[index];
        if (!format.isEmpty()) {
            float fw = ui.width(format, SchematicUi.TINY, false) + 12f;
            if (cx + fw <= x + w - 4f) ui.chip(x + w - 4f - fw, chipY, format, SchematicUi.MUTED, alpha);
        }
    }

    private void renderScrollbar(float x, float y, float h, int first, int visible, int total, float alpha) {
        float thumbH = Math.max(18f, h * visible / total);
        float thumbY = y + (h - thumbH) * first / Math.max(1, total - visible);
        Renderer2D.COLOR.roundedRect(x, y, 3f, h, 1.5f, SchematicUi.withAlpha(0xFFFFFF, Math.round(alpha * 12f)));
        Renderer2D.COLOR.roundedRect(x, thumbY, 3f, thumbH, 1.5f, SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 160f)));
    }

    private void refreshFiltered() {
        String query = search.trim().toLowerCase(Locale.ROOT);
        if (filteredGeneration == library.scanGeneration() && query.equals(filteredQuery)) return;
        filteredGeneration = library.scanGeneration();
        filteredQuery = query;
        List<SchematicHeader> next = new ArrayList<>();
        for (SchematicHeader header : library.headers()) {
            if (query.isEmpty() || header.name().toLowerCase(Locale.ROOT).contains(query)
                    || header.fileName().toLowerCase(Locale.ROOT).contains(query)) next.add(header);
        }
        filtered = next;
        rowFormat = new String[next.size()];
        rowDims = new String[next.size()];
        rowBlocks = new String[next.size()];
        for (int i = 0; i < next.size(); i++) {
            SchematicHeader header = next.get(i);
            rowFormat[i] = SchematicFiles.formatLabel(header.fileName());
            if (header.failed()) continue;
            rowDims[i] = header.dimensions();
            rowBlocks[i] = header.totalBlocks() >= 0 ? tr("screen.combatant.schematic.blocks_count", header.totalBlocks()) : "";
        }
    }

    // ---------------------------------------------------------------- centre

    private void renderCentre(float alpha) {
        Schematic schematic = state.schematic();
        Path loading = SchematicActions.loadingPath();
        if (loading != null) {
            renderLoading(loading, alpha);
            return;
        }
        if (schematic == null) {
            renderNothingLoaded(alpha);
            return;
        }
        renderLayerTitle(schematic, alpha);
        drawButton(Btn.TAB_3D, tr("screen.combatant.schematic.view.tab_3d"), view3d, true, alpha);
        drawButton(Btn.TAB_2D, tr("screen.combatant.schematic.view.tab_2d"), !view3d, true, alpha);
        SchematicDiff diff = state.currentDiff();
        if (diff != null && diff.schematic() != schematic) diff = null;
        float viewAlpha = alpha * (0.35f + 0.65f * AnimationUtility.easeOutCubic(viewFade));
        if (view3d) {
            render3d(schematic, diff, viewAlpha);
        } else {
            ui.well(gridX, gridY, gridW, gridH, 10f, alpha);
            grid.render(schematic, state.layer(), diff, viewAlpha);
        }
        renderStrip(schematic, diff, alpha);
        renderProgress(diff, alpha);
        renderControls(alpha);
    }

    private void renderNothingLoaded(float alpha) {
        ui.well(centreX, gridY, centreW, gridH, 10f, alpha);
        float cx = centreX + centreW * 0.5f;
        float cy = gridY + gridH * 0.5f - 30f;
        String error = SchematicActions.lastError();
        int iconColor = error.isEmpty() ? SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.45f)
                : SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.BAD, 0.55f);
        float bob = (float) Math.sin(System.nanoTime() / 1.0e9 * 1.6) * 3f;
        SchematicViewport.drawCubeIcon(cx, cy + bob, 26f, iconColor, alpha);
        if (error.isEmpty()) {
            ui.textCentered(tr("screen.combatant.schematic.pick_file"), cx, cy + 48f, SchematicUi.BODY,
                    SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
            return;
        }
        ui.textCentered(tr("screen.combatant.schematic.load_error"), cx, cy + 46f, SchematicUi.BODY,
                SchematicUi.scaleAlpha(SchematicUi.BAD, alpha), true);
        ui.textCentered(ui.ellipsize(error, SchematicUi.SMALL, centreW - 40f), cx, cy + 54f + bodyH,
                SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
    }

    private void renderLoading(Path loading, float alpha) {
        ui.well(centreX, gridY, centreW, gridH, 10f, alpha);
        float cx = centreX + centreW * 0.5f;
        float cy = gridY + gridH * 0.5f - 20f;
        float spin = (System.nanoTime() / 1_000_000L % 1200L) / 1200f * 360f;
        Renderer2D.COLOR.circleStroke(cx, cy, 28f, 4f, SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 40f)));
        Renderer2D.COLOR.arcStroke(cx, cy, 28f, 4f, spin, spin + 260f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        SchematicViewport.drawCubeIcon(cx, cy, 11f, SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.5f), alpha);
        ui.textCentered(tr("screen.combatant.schematic.loading", loading.getFileName().toString()), cx, cy + 46f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
    }

    private void render3d(Schematic schematic, SchematicDiff diff, float alpha) {
        ui.well(gridX, gridY, gridW, gridH, 10f, alpha);
        boolean inside = insideViewport(mouseX, mouseY) && !overViewportChrome(mouseX, mouseY) && !draggingStrip && !draggingGap;
        viewport.render(schematic, grid.paletteColors(schematic), state.layer(), diff, gridX, gridY, gridW, gridH,
                frameDt, mouseX, mouseY, inside, alpha);
        if (viewport.faces() == null && viewport.meshFailed()) {
            ui.textCentered(tr("screen.combatant.schematic.view.failed"), gridX + gridW * 0.5f, gridY + gridH * 0.5f,
                    SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.BAD, alpha), false);
        } else if (viewport.faces() == null) {
            float spin = (System.nanoTime() / 1_000_000L % 1200L) / 1200f * 360f;
            float cx = gridX + gridW * 0.5f, cy = gridY + gridH * 0.5f;
            Renderer2D.COLOR.arcStroke(cx, cy, 20f, 3f, spin, spin + 260f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
            ui.textCentered(tr("screen.combatant.schematic.view.building"), cx, cy + 32f, SchematicUi.SMALL,
                    SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        }
        renderViewChrome(alpha);
        renderInset(schematic, diff, alpha);
    }

    private void renderViewChrome(float alpha) {
        String mode = tr("screen.combatant.schematic.view.mode." + viewport.mode().name().toLowerCase(Locale.ROOT));
        drawButton(Btn.VIEW_MODE, mode, false, true, alpha);
        drawButton(Btn.AUTO_ROTATE, tr("screen.combatant.schematic.view.auto_rotate"), viewport.autoRotate(), true, alpha);
        drawButton(Btn.RESET_VIEW, tr("screen.combatant.schematic.view.reset"), false, true, alpha);
        if (viewport.mode().exploded()) renderGapSlider(alpha);
        FaceList faces = viewport.faces();
        float right = gridX + gridW - 12f;
        float bottom = gridY + gridH - 10f - tinyH;
        ui.textRight(ui.ellipsize(tr("screen.combatant.schematic.view.hint"), SchematicUi.TINY, gridW - insetS - 40f), right, bottom,
                SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
        if (faces == null) return;
        String stats = tr("screen.combatant.schematic.view.faces", formatCount(faces.count()));
        if (faces.lodStep() > 1) stats += "  ·  " + tr("screen.combatant.schematic.view.lod", faces.lodStep());
        if (faces.truncated()) stats += "  ·  " + tr("screen.combatant.schematic.view.truncated");
        if (viewport.meshing()) stats = tr("screen.combatant.schematic.view.building") + "  ·  " + stats;
        if (viewport.meshFailed()) stats = tr("screen.combatant.schematic.view.failed") + "  ·  " + stats;
        int color = faces.truncated() ? SchematicUi.WARN : SchematicUi.MUTED;
        ui.textRight(stats, right, bottom - tinyH - 4f, SchematicUi.TINY, SchematicUi.scaleAlpha(color, alpha), false);
    }

    private void renderGapSlider(float alpha) {
        String label = tr("screen.combatant.schematic.view.gap", String.format(Locale.ROOT, "%.2f", viewport.gap()));
        ui.text(label, gapX, gridY + 12f, SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        float t = viewport.gap() / SchematicViewport.MAX_GAP;
        Renderer2D.COLOR.roundedRect(gapX, gapY, gapW, 4f, 2f, SchematicUi.scaleAlpha(SchematicUi.SURFACE_HOVER, alpha));
        Renderer2D.COLOR.roundedRect(gapX, gapY, Math.max(4f, gapW * t), 4f, 2f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        boolean hot = draggingGap || SchematicUi.inside(mouseX, mouseY, gapX - 6f, gapY - 8f, gapW + 12f, 20f);
        float r = hot ? 7f : 6f;
        Renderer2D.COLOR.circle(gapX + gapW * t, gapY + 2f, r + 3f, SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * (hot ? 60f : 30f))));
        Renderer2D.COLOR.circle(gapX + gapW * t, gapY + 2f, r, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha));
    }

    private void renderInset(Schematic schematic, SchematicDiff diff, float alpha) {
        boolean hot = SchematicUi.inside(mouseX, mouseY, insetX, insetY, insetS, insetS);
        ui.section(insetX, insetY, insetS, insetS, alpha * 0.95f);
        if (hot) Renderer2D.COLOR.roundedRectStroke(insetX, insetY, insetS, insetS, 10f, 1f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        grid.render(schematic, state.layer(), diff, alpha);
        ui.text(tr("screen.combatant.schematic.view.tab_2d"), insetX + 8f, insetY + 6f, SchematicUi.TINY,
                SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), true);
    }

    private static String formatCount(int count) {
        return count >= 10_000 ? String.format(Locale.ROOT, "%.1fk", count / 1000f) : String.valueOf(count);
    }

    private void renderLayerTitle(Schematic schematic, float alpha) {
        String layer = tr("screen.combatant.schematic.layer", state.layer() + 1, schematic.sizeY());
        ui.text(layer, centreX, contentY, SchematicUi.TITLE, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
        LayerStats stats = state.stats();
        String blocks = stats == null ? "" : tr("screen.combatant.schematic.blocks_count", stats.blocksInLayer(state.layer()));
        String mode = tr("screen.combatant.schematic.layer_mode." + state.layerMode().name().toLowerCase(Locale.ROOT));
        String worldY = state.placement() == null ? "" : "  ·  Y " + state.placement().worldY(state.layer());
        ui.text(blocks + "  ·  " + mode + worldY, centreX, contentY + titleH + 6f, SchematicUi.SMALL,
                SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        if (playing) {
            ui.textRight(tr("screen.combatant.schematic.playing", PLAY_SPEEDS[speedIndex]), centreX + centreW - 128f, contentY + 6f,
                    SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha), false);
        }
    }

    private void renderStrip(Schematic schematic, SchematicDiff diff, float alpha) {
        ui.section(stripX, stripY, STRIP_W, stripH, alpha);
        LayerStats stats = state.stats();
        int layers = schematic.sizeY();
        int rows = Math.max(1, Math.min(layers, (int) (stripH - 8f)));
        float rowH = (stripH - 8f) / rows;
        float maxW = STRIP_W - 12f;
        int max = stats == null ? 1 : Math.max(1, stats.maxLayerBlocks());
        int low = state.visibleLow(), high = state.visibleHigh();
        for (int r = 0; r < rows; r++) {
            int from = r * layers / rows;
            int to = Math.max(from, (r + 1) * layers / rows - 1);
            int blocks = 0;
            for (int y = from; y <= to; y++) blocks = Math.max(blocks, stats == null ? 0 : stats.blocksInLayer(y));
            if (blocks == 0) continue;
            float w = Math.max(2f, maxW * blocks / max);
            float y = stripY + stripH - 4f - (r + 1) * rowH;
            int color = stripColor(diff, from, to, from <= high && to >= low);
            Renderer2D.COLOR.quad(stripX + 6f, y + Math.min(0.5f, rowH * 0.1f), w, Math.max(1f, rowH - Math.min(1f, rowH * 0.2f)),
                    SchematicUi.scaleAlpha(color, alpha));
        }
        renderStripThumb(layers, alpha);
    }

    private void renderStripThumb(int layers, float alpha) {
        float markerY = stripY + stripH - 4f - (thumbLayer + 0.5f) * (stripH - 8f) / layers;
        boolean hot = draggingStrip || SchematicUi.inside(mouseX, mouseY, stripX, stripY, STRIP_W, stripH);
        float th = hot ? 6f : 4f;
        Renderer2D.COLOR.roundedRectSoftShadow(stripX + 2f, markerY - th * 0.5f, STRIP_W - 4f, th, th * 0.5f, 10f, 0.03f * alpha,
                SchematicUi.withAlpha(SchematicUi.ACCENT, Math.round(alpha * 90f)));
        Renderer2D.COLOR.roundedRect(stripX + 2f, markerY - th * 0.5f, STRIP_W - 4f, th, th * 0.5f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        if (bubble <= 0.01f) return;
        float a = alpha * bubble;
        String label = String.valueOf(state.layer() + 1);
        float bw = Math.max(34f, ui.width(label, SchematicUi.SMALL, true) + 16f);
        float bh = smallH + 10f;
        float bxPos = stripX - 8f - bw + (1f - AnimationUtility.easeOutBack(bubble)) * 10f;
        float byPos = clamp(markerY - bh * 0.5f, stripY, stripY + stripH - bh);
        Renderer2D.COLOR.roundedRectSoftShadow(bxPos, byPos, bw, bh, bh * 0.5f, 12f, 0.03f * a, SchematicUi.withAlpha(0x000000, Math.round(a * 110f)));
        Renderer2D.COLOR.roundedRect(bxPos, byPos, bw, bh, bh * 0.5f, SchematicUi.scaleAlpha(SchematicUi.mix(SchematicUi.SURFACE, SchematicUi.ACCENT, 0.3f), a));
        Renderer2D.COLOR.roundedRectStroke(bxPos, byPos, bw, bh, bh * 0.5f, 1f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, a));
        ui.textCentered(label, bxPos + bw * 0.5f, byPos + 5f, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.TEXT, a), true);
    }

    private static int stripColor(SchematicDiff diff, int from, int to, boolean inWindow) {
        int base = inWindow ? SchematicUi.mix(SchematicUi.MUTED, SchematicUi.ACCENT, 0.35f) : SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.MUTED, 0.3f);
        if (diff == null) return base;
        int total = 0, correct = 0;
        for (int y = from; y <= to; y++) {
            total += diff.total(y);
            correct += diff.correct(y);
        }
        if (total == 0) return base;
        return SchematicUi.mix(base, SchematicUi.GOOD, (float) correct / total);
    }

    private void renderProgress(SchematicDiff diff, float alpha) {
        float barY = progressY;
        float barH = 12f;
        float w = centreW;
        ui.well(centreX, barY, w, barH, 6f, alpha);
        if (diff == null) {
            ui.text(tr("screen.combatant.schematic.progress_unplaced"), centreX, barY + barH + 6f, SchematicUi.SMALL,
                    SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
            return;
        }
        int layer = state.layer();
        int total = Math.max(1, diff.total(layer));
        float x = centreX;
        x = segment(x, barY, w * diff.correct(layer) / total, barH, SchematicUi.GOOD, alpha);
        x = segment(x, barY, w * diff.wrong(layer) / total, barH, SchematicUi.WARN, alpha);
        segment(x, barY, w * diff.missing(layer) / total, barH, SchematicUi.BAD, alpha);
        int buildTotal = 0, buildCorrect = 0;
        for (int y = 0; y < state.layerCount(); y++) {
            buildTotal += diff.total(y);
            buildCorrect += diff.correct(y);
        }
        String text = tr("screen.combatant.schematic.progress", diff.correct(layer), diff.total(layer), diff.missing(layer),
                diff.wrong(layer), buildTotal == 0 ? 100 : buildCorrect * 100 / buildTotal);
        ui.text(text, centreX, barY + barH + 6f, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
    }

    private static float segment(float x, float y, float w, float h, int color, float alpha) {
        if (w <= 0.5f) return x;
        Renderer2D.COLOR.roundedRect(x, y, w, h, Math.min(6f, w * 0.5f), SchematicUi.scaleAlpha(color, alpha));
        return x + w;
    }

    private void renderControls(float alpha) {
        drawButton(Btn.LAYER_DOWN10, "-10", false, true, alpha);
        drawButton(Btn.LAYER_DOWN, "-1", false, true, alpha);
        drawButton(Btn.PLAY, tr(playing ? "screen.combatant.schematic.pause" : "screen.combatant.schematic.play"), playing, true, alpha);
        drawButton(Btn.SPEED, PLAY_SPEEDS[speedIndex] + "/s", false, true, alpha);
        drawButton(Btn.LAYER_UP, "+1", false, true, alpha);
        drawButton(Btn.LAYER_UP10, "+10", false, true, alpha);
    }

    // ---------------------------------------------------------------- right

    private void renderRight(float alpha) {
        renderInfo(alpha);
        boolean loaded = state.hasSchematic();
        boolean placed = state.isPlaced();
        drawButton(Btn.ORIGIN_HERE, tr("screen.combatant.schematic.origin_here"), false, loaded, alpha);
        drawButton(Btn.ORIGIN_LOOK, tr("screen.combatant.schematic.origin_look"), false, loaded, alpha);
        drawButton(Btn.ROTATE, tr("screen.combatant.schematic.rotate"), false, placed, alpha);
        drawButton(Btn.MIRROR, tr("screen.combatant.schematic.mirror"), false, placed, alpha);
        boolean building = SchematicActions.builderEnabled();
        drawButton(Btn.BUILDER, tr(building ? "screen.combatant.schematic.builder_stop" : "screen.combatant.schematic.builder_start"),
                building, placed, alpha);
        drawButton(Btn.PROJECTION, tr("screen.combatant.schematic.projection"), SchematicActions.projectionEnabled(), true, alpha);
        drawButton(Btn.LAYER_MODE, tr("screen.combatant.schematic.layer_mode." + state.layerMode().name().toLowerCase(Locale.ROOT)),
                false, true, alpha);
        drawButton(Btn.UNLOAD, tr("screen.combatant.schematic.unload"), false, loaded, alpha);
        drawButton(Btn.TAB_LAYER, tr("screen.combatant.schematic.materials_layer"), !materialsTotal && !issuesShown, loaded, alpha);
        drawButton(Btn.TAB_TOTAL, tr("screen.combatant.schematic.materials_total"), materialsTotal && !issuesShown, loaded, alpha);
        refreshIssues();
        drawButton(Btn.TAB_ISSUES, tr("screen.combatant.schematic.attention_tab", issues.size()), issuesShown, loaded, alpha);
        if (issuesShown) {
            renderIssues(alpha);
        } else {
            renderMaterials(alpha);
        }
    }

    private void renderInfo(float alpha) {
        Schematic schematic = state.schematic();
        float y = infoY;
        float step = smallH + 7f;
        if (schematic == null) {
            infoLine(tr("screen.combatant.schematic.info.folder"), library.directory().getFileName().toString(), y, alpha);
            return;
        }
        infoLine(tr("screen.combatant.schematic.info.name"), schematic.name(), y, alpha);
        infoLine(tr("screen.combatant.schematic.info.author"), schematic.author().isEmpty() ? "-" : schematic.author(), y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.size"), schematic.sizeX() + " x " + schematic.sizeY() + " x " + schematic.sizeZ(), y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.blocks"), String.valueOf(schematic.totalBlocks()), y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.palette"), String.valueOf(schematic.paletteSize() - 1), y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.regions"), String.valueOf(schematic.regionCount()), y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.warnings"), String.valueOf(schematic.warnings().size()), y += step, alpha);
        renderPlacementInfo(y + step, step, alpha);
    }

    private void renderPlacementInfo(float y, float step, float alpha) {
        SchematicPlacement placement = state.placement();
        if (placement == null) {
            infoLine(tr("screen.combatant.schematic.info.origin"), tr("screen.combatant.schematic.info.not_placed"), y, alpha);
            return;
        }
        BlockPos o = placement.origin();
        infoLine(tr("screen.combatant.schematic.info.origin"), o.getX() + ", " + o.getY() + ", " + o.getZ(), y, alpha);
        String rotation = switch (placement.rotation()) {
            case NONE -> "0°";
            case CLOCKWISE_90 -> "90°";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°";
        };
        infoLine(tr("screen.combatant.schematic.info.rotation"), rotation, y += step, alpha);
        infoLine(tr("screen.combatant.schematic.info.mirror"), mirrorName(placement.mirror()), y += step, alpha);
        SchematicBuilder builder = ModuleManager.get(SchematicBuilder.class);
        if (builder != null) {
            String status = tr("screen.combatant.schematic.builder." + builder.status().name().toLowerCase(Locale.ROOT));
            if (builder.placedCount() > 0) status += " · " + builder.placedCount();
            infoLine(tr("screen.combatant.schematic.info.builder"), status, y + step, alpha);
        }
    }

    private static String mirrorName(Mirror mirror) {
        return tr("screen.combatant.schematic.mirror." + mirror.name().toLowerCase(Locale.ROOT));
    }

    private void infoLine(String label, String value, float y, float alpha) {
        ui.text(label, rightX, y, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        float labelW = Math.max(110f, ui.width(label, SchematicUi.SMALL, false) + 12f);
        ui.text(ui.ellipsize(value, SchematicUi.SMALL, rightW - labelW), rightX + labelW, y, SchematicUi.SMALL,
                SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), false);
    }

    private void renderMaterials(float alpha) {
        ui.section(rightX, materialsY, rightW, materialsH, alpha);
        refreshMaterials();
        if (materials.isEmpty()) {
            String text = tr(state.hasSchematic() ? "screen.combatant.schematic.materials_none" : "screen.combatant.schematic.none_loaded");
            ui.textCentered(text, rightX + rightW * 0.5f, materialsY + Math.min(materialsH * 0.4f, 60f), SchematicUi.SMALL,
                    SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
            return;
        }
        int visible = Math.max(1, (int) ((materialsH - 8f) / MATERIAL_ROW_H));
        materialsFirst = clampInt(materialsFirst, 0, Math.max(0, materials.size() - visible));
        List<SchematicMaterials.Entry> missing = state.missingMaterials();
        for (int i = 0; i < visible && materialsFirst + i < materials.size(); i++) {
            int index = materialsFirst + i;
            renderMaterialRow(index, materialsY + 4f + i * MATERIAL_ROW_H, isMissing(missing, materials.get(index)), alpha);
        }
        if (materials.size() > visible) {
            renderScrollbar(rightX + rightW - 5f, materialsY + 6f, materialsH - 12f, materialsFirst, visible, materials.size(), alpha);
        }
    }

    /** What the builder cannot make exactly: the schematic's own impossibilities, then what the builder met. */
    private void renderIssues(float alpha) {
        ui.section(rightX, materialsY, rightW, materialsH, alpha);
        if (issues.isEmpty()) {
            String text = tr(state.hasSchematic() ? "screen.combatant.schematic.attention_none" : "screen.combatant.schematic.none_loaded");
            ui.textCentered(text, rightX + rightW * 0.5f, materialsY + Math.min(materialsH * 0.4f, 60f), SchematicUi.SMALL,
                    SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
            return;
        }
        int visible = Math.max(1, (int) ((materialsH - 8f) / MATERIAL_ROW_H));
        materialsFirst = clampInt(materialsFirst, 0, Math.max(0, issues.size() - visible));
        for (int i = 0; i < visible && materialsFirst + i < issues.size(); i++) {
            renderIssueRow(materialsFirst + i, materialsY + 4f + i * MATERIAL_ROW_H, alpha);
        }
        if (issues.size() > visible) {
            renderScrollbar(rightX + rightW - 5f, materialsY + 6f, materialsH - 12f, materialsFirst, visible, issues.size(), alpha);
        }
    }

    private void renderIssueRow(int index, float y, float alpha) {
        SchematicAttention.Entry entry = issues.get(index);
        float x = rightX + 6f;
        float w = rightW - 16f;
        float rh = MATERIAL_ROW_H - 2f;
        Renderer2D.COLOR.roundedRect(x, y, w, rh, 6f, SchematicUi.scaleAlpha(SchematicUi.mix(SchematicUi.SURFACE, SchematicUi.WARN, 0.14f), alpha));
        Renderer2D.COLOR.item(issueStacks.get(index), x + 4f, y + (rh - 20f) * 0.5f, 1.25f, index);
        String where = entry.pos() == null
                ? "x" + entry.count()
                : entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
        float whereW = ui.width(where, SchematicUi.TINY, false);
        ui.text(ui.ellipsize(entry.state().getBlock().getName().getString(), SchematicUi.SMALL, w - whereW - 44f),
                x + 32f, y + 3f, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), false);
        ui.textRight(where, x + w - 4f, y + 4f, SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        String reason = tr("screen.combatant.schematic.attention." + entry.reason().name().toLowerCase(Locale.ROOT));
        ui.text(ui.ellipsize(reason, SchematicUi.TINY, w - 36f), x + 32f, y + rh - tinyH - 3f, SchematicUi.TINY,
                SchematicUi.scaleAlpha(SchematicUi.WARN, alpha), false);
    }

    private void refreshIssues() {
        Schematic schematic = state.schematic();
        LayerStats stats = state.stats();
        if (schematic == null || stats == null) {
            issues = List.of();
            issueStacks = List.of();
            issuesKey = null;
            return;
        }
        List<Object> key = List.of(schematic, state.attentionRevision(), state.placement() == null ? 0 : state.placement());
        if (key.equals(issuesKey)) return;
        issuesKey = key;
        List<SchematicAttention.Entry> list = new ArrayList<>(SchematicAttention.staticIssues(schematic, stats.paletteTotals()));
        list.addAll(state.attention());
        issues = list;
        List<ItemStack> stacks = new ArrayList<>(list.size());
        for (SchematicAttention.Entry entry : list) {
            Item item = SchematicMaterials.itemFor(entry.state());
            stacks.add(new ItemStack(item == Items.AIR ? Items.BARRIER : item));
        }
        issueStacks = stacks;
    }

    private static boolean isMissing(List<SchematicMaterials.Entry> missing, SchematicMaterials.Entry entry) {
        for (SchematicMaterials.Entry m : missing) {
            if (m.item() == entry.item()) return true;
        }
        return false;
    }

    private void renderMaterialRow(int index, float y, boolean builderMissing, float alpha) {
        SchematicMaterials.Entry entry = materials.get(index);
        int have = materialHave[index];
        float x = rightX + 6f;
        float w = rightW - 16f;
        float rh = MATERIAL_ROW_H - 2f;
        if (builderMissing) {
            Renderer2D.COLOR.roundedRect(x, y, w, rh, 6f, SchematicUi.scaleAlpha(SchematicUi.mix(SchematicUi.SURFACE, SchematicUi.BAD, 0.22f), alpha));
        } else if (SchematicUi.inside(mouseX, mouseY, x, y, w, rh)) {
            Renderer2D.COLOR.roundedRect(x, y, w, rh, 6f, SchematicUi.scaleAlpha(SchematicUi.SURFACE_HOVER, alpha));
        }
        Renderer2D.COLOR.item(materialStacks.get(index), x + 4f, y + (rh - 20f) * 0.5f, 1.25f, index);
        if (index < materialRedstone.length && materialRedstone[index]) {
            // Redstone parts carry a small dot so a machine's components stand out from its casing.
            Renderer2D.COLOR.roundedRect(x + 2f, y + 3f, 6f, 6f, 3f, SchematicUi.scaleAlpha(SchematicUi.REDSTONE, alpha));
        }
        String counts = have + " / " + entry.count();
        float countsW = ui.width(counts, SchematicUi.SMALL, false);
        float textY = y + 4f;
        ui.text(ui.ellipsize(materialStacks.get(index).getHoverName().getString(), SchematicUi.SMALL, w - countsW - 44f),
                x + 32f, textY, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), false);
        boolean done = have >= entry.count();
        int color = done ? SchematicUi.GOOD : SchematicUi.BAD;
        ui.textRight(counts, x + w - 4f, textY, SchematicUi.SMALL, SchematicUi.scaleAlpha(color, alpha), false);
        float fraction = entry.count() <= 0 ? 1f : Math.min(1f, (float) have / entry.count());
        float barX = x + 32f, barW = w - 36f, barY = y + rh - 8f;
        Renderer2D.COLOR.roundedRect(barX, barY, barW, 3f, 1.5f, SchematicUi.scaleAlpha(SchematicUi.WELL, alpha));
        int fill = done ? SchematicUi.GOOD : SchematicUi.mix(SchematicUi.BAD, SchematicUi.WARN, fraction);
        if (fraction > 0f) Renderer2D.COLOR.roundedRect(barX, barY, Math.max(3f, barW * fraction), 3f, 1.5f, SchematicUi.scaleAlpha(fill, alpha));
    }

    private void refreshMaterials() {
        Schematic schematic = state.schematic();
        LayerStats stats = state.stats();
        if (schematic == null || stats == null) {
            materials = List.of();
            materialsKey = null;
            return;
        }
        List<Object> key = List.of(schematic, materialsTotal ? -1 : state.layer());
        if (!key.equals(materialsKey)) {
            materialsKey = key;
            int[] histogram = materialsTotal ? stats.paletteTotals() : stats.layerHistogram(state.layer());
            materials = SchematicMaterials.materials(schematic, histogram);
            List<ItemStack> stacks = new ArrayList<>(materials.size());
            boolean[] redstone = new boolean[materials.size()];
            for (int i = 0; i < materials.size(); i++) {
                Item item = materials.get(i).item();
                stacks.add(new ItemStack(item));
                redstone[i] = item instanceof BlockItem blockItem
                        && RedstoneRules.isComponent(blockItem.getBlock().defaultBlockState());
            }
            materialStacks = stacks;
            materialRedstone = redstone;
            materialHave = new int[materials.size()];
            materialsHaveNanos = 0L;
        }
        long now = System.nanoTime();
        if (now - materialsHaveNanos < HAVE_REFRESH_NANOS) return;
        materialsHaveNanos = now;
        for (int i = 0; i < materials.size(); i++) {
            materialHave[i] = SchematicMaterials.countInInventory(minecraft == null ? null : minecraft.player, materials.get(i).item());
        }
    }

    // ---------------------------------------------------------------- tooltip

    private void renderTooltip(float alpha) {
        Schematic schematic = state.schematic();
        if (schematic == null || SchematicActions.loadingPath() != null || draggingStrip || draggingGap || viewport.dragging()) return;
        if (view3d && viewport.hovered() >= 0) {
            int index = viewport.hovered();
            int x = schematic.xOf(index), y = schematic.yOf(index), z = schematic.zOf(index);
            drawTooltip(schematic, x, y, z, y != state.layer(), alpha);
            return;
        }
        boolean gridShown = !view3d || SchematicUi.inside(mouseX, mouseY, insetX, insetY, insetS, insetS);
        int[] cell = gridShown ? grid.cellAt(schematic, mouseX, mouseY) : null;
        if (cell == null) return;
        drawTooltip(schematic, cell[0], state.layer(), cell[1], false, alpha);
    }

    private void drawTooltip(Schematic schematic, int cx, int cy, int cz, boolean clickHint, float alpha) {
        int palette = schematic.paletteIndex(cx, cy, cz);
        if (palette == 0) return;
        BlockState block = schematic.paletteState(palette);
        String name = block.getBlock().getName().getString();
        String id = schematic.paletteId(palette);
        String coords = tr("screen.combatant.schematic.tooltip.local", cx, cy, cz);
        SchematicPlacement placement = state.placement();
        if (placement != null) {
            BlockPos world = placement.toWorld(cx, cy, cz);
            coords += "  ·  " + tr("screen.combatant.schematic.tooltip.world", world.getX(), world.getY(), world.getZ());
        }
        String hint = clickHint ? tr("screen.combatant.schematic.tooltip.click_layer", cy + 1) : "";
        float w = Math.max(ui.width(name, SchematicUi.BODY, true), Math.max(ui.width(id, SchematicUi.TINY, false),
                Math.max(ui.width(coords, SchematicUi.TINY, false), ui.width(hint, SchematicUi.TINY, false)))) + 24f;
        float h = bodyH + smallH * 2f + 24f + (hint.isEmpty() ? 0f : smallH + 2f);
        float x = Math.min(mouseX + 16f, fixedWidth - w - 8f);
        float y = Math.min(mouseY + 16f, fixedHeight - h - 8f);
        int swatch = grid.paletteColor(schematic, palette);
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, 8f, 14f, 0.04f, SchematicUi.withAlpha(0x000000, 120));
        Renderer2D.COLOR.roundedRect(x, y, w, h, 8f, SchematicUi.withAlpha(SchematicUi.PANEL_BOTTOM, Math.round(alpha * 245f)));
        Renderer2D.COLOR.roundedRectStroke(x, y, w, h, 8f, 1f, SchematicUi.withAlpha(swatch, Math.round(alpha * 220f)));
        Renderer2D.COLOR.roundedRect(x + 1f, y + 10f, 3f, h - 20f, 1.5f, SchematicUi.withAlpha(swatch, Math.round(alpha * 255f)));
        ui.text(name, x + 12f, y + 8f, SchematicUi.BODY, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
        ui.text(id, x + 12f, y + 12f + bodyH, SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        ui.text(coords, x + 12f, y + 16f + bodyH + smallH, SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        if (!hint.isEmpty()) {
            ui.text(hint, x + 12f, y + 18f + bodyH + smallH * 2f, SchematicUi.TINY, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha), false);
        }
    }

    private void drawButton(Btn id, String label, boolean active, boolean enabled, float alpha) {
        int i = id.ordinal();
        if (!bShown[i]) return;
        ui.button(bx[i], by[i], bw[i], bh[i], label, enabled ? hover[i] : 0f, press[i], active, enabled, alpha);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        float mx = toFixed((float) click.x());
        float my = toFixed((float) click.y());
        mouseX = mx;
        mouseY = my;
        if (click.button() != 0) {
            boolean pan = click.button() == 1 || click.button() == 2;
            if (pan && view3dActive() && insideViewport(mx, my) && !overViewportChrome(mx, my)) viewport.beginDrag(click.button(), mx, my);
            return true;
        }
        searchFocused = SchematicUi.inside(mx, my, searchX, searchY, searchW, searchH);
        for (Btn id : BUTTONS) {
            if (over(id)) {
                press[id.ordinal()] = 1f;
                onButton(id);
                return true;
            }
        }
        if (clickList(mx, my, doubled)) return true;
        if (state.hasSchematic() && SchematicUi.inside(mx, my, stripX, stripY, STRIP_W, stripH)) {
            draggingStrip = true;
            setLayerFromStrip(my);
            return true;
        }
        if (view3dActive()) clickViewport(mx, my, doubled);
        return true;
    }

    private void clickViewport(float mx, float my, boolean doubled) {
        if (viewport.mode().exploded() && SchematicUi.inside(mx, my, gapX - 6f, gapY - 8f, gapW + 12f, 20f)) {
            draggingGap = true;
            setGapFrom(mx);
            return;
        }
        if (SchematicUi.inside(mx, my, insetX, insetY, insetS, insetS)) {
            setView3d(false);
            return;
        }
        if (!insideViewport(mx, my)) return;
        if (doubled) viewport.resetCamera();
        viewport.beginDrag(0, mx, my);
    }

    private boolean clickList(float mx, float my, boolean doubled) {
        if (!SchematicUi.inside(mx, my, leftX, listY, leftW, listH)) return false;
        int index = listFirst + (int) ((my - listY - 4f) / ROW_H);
        if (index < 0 || index >= filtered.size()) return true;
        SchematicHeader header = filtered.get(index);
        selectedPath = header.path();
        if (doubled && !header.failed()) SchematicActions.load(selectedPath);
        return true;
    }

    private void onButton(Btn id) {
        switch (id) {
            case CLOSE -> closeToParent();
            case REFRESH -> library.refresh();
            case LOAD -> SchematicActions.load(selectedPath);
            case OPEN_FOLDER -> Util.getPlatform().openPath(library.directory());
            case ORIGIN_HERE -> SchematicActions.setOriginHere();
            case ORIGIN_LOOK -> SchematicActions.setOriginLooked();
            case ROTATE -> state.rotateClockwise();
            case MIRROR -> state.cycleMirror();
            case BUILDER -> {
                if (state.isPlaced()) SchematicActions.toggleBuilder();
            }
            case PROJECTION -> SchematicActions.toggleProjection();
            case LAYER_MODE -> SchematicActions.cycleLayerMode();
            case UNLOAD -> SchematicActions.unload();
            case LAYER_DOWN10 -> state.stepLayer(-10);
            case LAYER_DOWN -> state.stepLayer(-1);
            case PLAY -> togglePlay();
            case SPEED -> speedIndex = (speedIndex + 1) % PLAY_SPEEDS.length;
            case LAYER_UP -> state.stepLayer(1);
            case LAYER_UP10 -> state.stepLayer(10);
            case TAB_LAYER -> {
                materialsTotal = false;
                issuesShown = false;
            }
            case TAB_TOTAL -> {
                materialsTotal = true;
                issuesShown = false;
            }
            case TAB_ISSUES -> {
                issuesShown = true;
                materialsFirst = 0;
            }
            case TAB_3D -> setView3d(true);
            case TAB_2D -> setView3d(false);
            case VIEW_MODE -> viewport.cycleMode();
            case AUTO_ROTATE -> viewport.toggleAutoRotate();
            case RESET_VIEW -> viewport.resetCamera();
        }
    }

    private void setView3d(boolean next) {
        if (view3d == next) return;
        view3d = next;
        viewFade = 0f;
    }

    private void togglePlay() {
        playing = !playing && state.hasSchematic();
        playAccumulator = 0f;
    }

    private void setLayerFromStrip(float my) {
        int layers = state.layerCount();
        if (layers <= 0) return;
        float t = (stripY + stripH - 4f - my) / Math.max(1f, stripH - 8f);
        state.setLayer((int) Math.floor(t * layers));
    }

    private void setGapFrom(float mx) {
        viewport.setGap((mx - gapX) / gapW * SchematicViewport.MAX_GAP);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        float mx = toFixed((float) click.x());
        float my = toFixed((float) click.y());
        if (draggingStrip) {
            setLayerFromStrip(my);
            return true;
        }
        if (draggingGap) {
            setGapFrom(mx);
            return true;
        }
        if (viewport.dragging()) {
            viewport.drag(mx, my);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingStrip = false;
        draggingGap = false;
        if (viewport.dragging()) {
            int layer = viewport.endDrag(toFixed((float) click.x()), toFixed((float) click.y()));
            if (layer >= 0) state.setLayer(layer);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        float mx = toFixed((float) x);
        float my = toFixed((float) y);
        int direction = vertical > 0 ? 1 : vertical < 0 ? -1 : 0;
        if (direction == 0) return true;
        if (SchematicUi.inside(mx, my, leftX, listY, leftW, listH)) {
            listFirst -= direction;
        } else if (SchematicUi.inside(mx, my, rightX, materialsY, rightW, materialsH)) {
            materialsFirst -= direction;
        } else if (view3dActive() && insideViewport(mx, my) && !ctrlDown()) {
            viewport.zoom(direction);
        } else if (SchematicUi.inside(mx, my, centreX, contentY, centreW, contentBottom - contentY)) {
            state.stepLayer(direction * (shiftDown() ? 10 : 1));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> closeToParent();
            case GLFW.GLFW_KEY_UP -> state.stepLayer(1);
            case GLFW.GLFW_KEY_DOWN -> state.stepLayer(-1);
            case GLFW.GLFW_KEY_PAGE_UP -> state.stepLayer(10);
            case GLFW.GLFW_KEY_PAGE_DOWN -> state.stepLayer(-10);
            case GLFW.GLFW_KEY_HOME -> state.setLayer(0);
            case GLFW.GLFW_KEY_END -> state.setLayer(state.layerCount() - 1);
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> SchematicActions.load(selectedPath);
            case GLFW.GLFW_KEY_R -> viewport.resetCamera();
            case GLFW.GLFW_KEY_T -> setView3d(!view3d);
            case GLFW.GLFW_KEY_E -> viewport.cycleMode();
            default -> {
                return super.keyPressed(input);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!searchFocused) return super.charTyped(input);
        int cp = input.codepoint();
        if (cp >= 32 && cp != 127 && search.length() < 64) search += Character.toString(cp);
        listFirst = 0;
        return true;
    }

    @Override
    public void removed() {
        playing = false;
        super.removed();
    }

    private void closeToParent() {
        if (minecraft != null) ClientScreen.show(minecraft, parent);
    }

    private boolean shiftDown() {
        return keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private boolean ctrlDown() {
        return keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean keyDown(int key) {
        return minecraft != null && InputConstants.isKeyDown(minecraft.getWindow(), key);
    }

    // ---------------------------------------------------------------- metrics

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = width;
            fixedHeight = height;
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float nextW = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        float nextH = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
        if (nextW != fixedWidth || nextH != fixedHeight) ui.clearCaches();
        fixedWidth = nextW;
        fixedHeight = nextH;
    }

    private float toFixed(float screen) {
        Minecraft mc = minecraft;
        if (mc == null) return screen;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screen;
        return screen * mc.getWindow().getGuiScale() / hudScale;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
