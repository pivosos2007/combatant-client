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
import combatant.client.util.schematic.SchematicFormats;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicAttention;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicHeader;
import combatant.client.util.schematic.SchematicLibrary;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The schematic menu: file browser on the left, the build as an orbitable 3D model (or the selected layer
 * top-down) in the middle with a layer strip that doubles as the slider, and placement, builder and material
 * controls on the right. Hosted either by {@link SchematicScreen} or embedded in the ClickGUI "Schematics" section.
 */
public final class SchematicPanel {

    private static final float HEADER_H = 48f;
    private static final float TOPBAR_H = 56f;
    private static final float PAD = 14f;
    private static final float GAP = 14f;
    private static final float BUTTON_H = 36f;
    private static final float OVERLAY_H = 30f;
    private static final float ROW_H = 62f;
    private static final float MATERIAL_ROW_H = 42f;
    private static final float PANEL_RADIUS = 14f;
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

    private static final long TICK_NANOS = 50_000_000L;

    /** Null when embedded in a host; otherwise runs when the user closes the standalone menu. */
    private final Runnable onClose;
    private boolean initialized;
    private float embedX, embedY, embedW, embedH;
    private long lastTickNanos;
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
    private float colTop, colBottom;
    private float leftPX, leftPW, centrePX, centrePW, rightPX, rightPW;
    private float detailsH, materialsPY, materialsPH;
    private boolean libraryEmpty;
    private int infoRows;
    private final List<String[]> infoList = new ArrayList<>();
    private List<String> emptyHint = List.of();
    private float emptyY;
    private float segView, segMat;
    private float mouseX, mouseY;
    private float openAnim;
    private float frameDt;

    private List<SchematicHeader> filtered = List.of();
    private int filteredGeneration = -1;
    private String filteredQuery = "";
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

    /** Standalone menu: closing runs {@code onClose} and the panel owns the whole screen (dimmer, background, X button). */
    public SchematicPanel(Runnable onClose) {
        this.onClose = onClose;
    }

    /** Embedded in a host that provides the bounds (the ClickGUI section): no dimmer, no close button. */
    public SchematicPanel() {
        this.onClose = null;
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    public void init() {
        ui.init();
        ui.clearCaches();
        initialized = true;
        bodyH = ui.height(SchematicUi.BODY);
        smallH = ui.height(SchematicUi.SMALL);
        titleH = ui.height(SchematicUi.TITLE);
        tinyH = ui.height(SchematicUi.TINY);
        library.refresh();
        if (selectedPath == null && state.schematic() != null) {
            selectedPath = library.resolve(state.schematic().fileName());
        }
        thumbLayer = state.layer();
    }

    /** Replays the fade-in; used when a host shows an already constructed panel again. */
    public void restartOpenAnimation() {
        openAnim = 0f;
        lastFrameNanos = 0L;
    }

    public void tick() {
        state.tickDiff(Minecraft.getInstance(), 256, 256);
    }

    /** Renders the standalone menu; {@code mx}/{@code my} are vanilla GUI-scaled coordinates. */
    public void renderStandalone(GuiGraphicsExtractor context, int mx, int my) {
        updateUiMetrics();
        SchematicUi.syncTheme();
        MenuBackgroundRenderer.renderConfigured(Minecraft.getInstance());
        mouseX = toFixed(mx);
        mouseY = toFixed(my);
        layout();
        animate();

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            float alpha = AnimationUtility.easeOutCubic(openAnim);
            renderDimmer(alpha);
            renderBody(alpha);
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    /**
     * Renders inside an already running {@code Renderer2D.COLOR} batch, in the host's fixed coordinate space.
     * Drag state is polled here because the host forwards presses and releases only.
     */
    public void renderEmbedded(float x, float y, float w, float h, float mx, float my) {
        if (!initialized) init();
        updateUiMetrics();
        SchematicUi.syncTheme();
        embedX = x;
        embedY = y;
        embedW = w;
        embedH = h;
        mouseX = mx;
        mouseY = my;
        long now = System.nanoTime();
        if (now - lastTickNanos >= TICK_NANOS) {
            lastTickNanos = now;
            tick();
        }
        mouseDragged(mx, my);
        layout();
        animate();
        renderBody(AnimationUtility.easeOutCubic(openAnim));
    }

    private void renderBody(float alpha) {
        if (onClose != null) renderTopBar(alpha);
        ui.panel(leftPX, colTop, leftPW, colBottom - colTop, PANEL_RADIUS, alpha);
        ui.panel(centrePX, colTop, centrePW, colBottom - colTop, PANEL_RADIUS, alpha);
        ui.panel(rightPX, colTop, rightPW, detailsH, PANEL_RADIUS, alpha);
        if (materialsPH > 1f) ui.panel(rightPX, materialsPY, rightPW, materialsPH, PANEL_RADIUS, alpha);
        renderLeft(alpha);
        renderCentre(alpha);
        renderRight(alpha);
        renderTooltip(alpha);
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        if (onClose == null) {
            panelX = embedX;
            panelY = embedY;
            panelW = embedW;
            panelH = embedH;
        } else {
            panelW = Math.min(fixedWidth - 60f, 1760f);
            panelH = Math.min(fixedHeight - 60f, 1000f);
            panelX = (fixedWidth - panelW) * 0.5f;
            panelY = (fixedHeight - panelH) * 0.5f + (1f - AnimationUtility.easeOutCubic(openAnim)) * OPEN_SLIDE;
        }
        colTop = panelY + (onClose == null ? 0f : TOPBAR_H + GAP);
        colBottom = panelY + panelH;
        contentY = colTop + HEADER_H + 12f;
        contentBottom = colBottom - PAD;

        // Three separate glass columns with gaps; the side columns give way before the preview does.
        float total = Math.max(1f, panelW - GAP * 2f);
        float leftO = clamp(total * 0.22f, 200f, 380f);
        float rightO = clamp(total * 0.26f, 240f, 440f);
        float sides = leftO + rightO;
        float maxSides = Math.max(total - 340f, Math.min(sides, total * 0.45f));
        if (sides > maxSides) {
            float factor = maxSides / sides;
            leftO *= factor;
            rightO *= factor;
        }
        float centreO = total - leftO - rightO;
        leftPX = panelX;
        leftPW = leftO;
        centrePX = leftPX + leftO + GAP;
        centrePW = centreO;
        rightPX = centrePX + centreO + GAP;
        rightPW = rightO;
        leftX = leftPX + PAD;
        leftW = leftPW - PAD * 2f;
        centreX = centrePX + PAD;
        centreW = centrePW - PAD * 2f;
        rightX = rightPX + PAD;
        rightW = rightPW - PAD * 2f;
        java.util.Arrays.fill(bShown, false);

        if (onClose != null) place(Btn.CLOSE, panelX + panelW - PAD - 44f, panelY + (TOPBAR_H - BUTTON_H) * 0.5f, 44f, BUTTON_H);
        layoutLeft();
        layoutCentre();
        layoutRight();
    }

    private void layoutLeft() {
        libraryEmpty = library.headers().isEmpty() && !library.isScanning();
        // Refresh lives in the bottom action row (or under the empty-state call to action), not the header.
        if (libraryEmpty) {
            // No files: no search, no Load; the list is the hint and its call to action.
            searchX = -10_000f;
            searchY = 0f;
            searchW = 0f;
            searchH = 0f;
            listY = contentY;
            listH = Math.max(ROW_H, contentBottom - listY);
            emptyHint = wrap(tr("screen.combatant.schematic.empty_hint"), SchematicUi.SMALL, leftW - 40f, 3);
            emptyY = listY + Math.max(30f, listH * 0.5f - 100f);
            float ctaY = emptyY + 10f + 40f + bodyH + 8f + emptyHint.size() * (smallH + 4f) + 16f;
            float ctaW = Math.min(leftW - 32f, 220f);
            place(Btn.OPEN_FOLDER, leftX + (leftW - ctaW) * 0.5f, ctaY, ctaW, 40f);
            place(Btn.REFRESH, leftX + (leftW - ctaW) * 0.5f, ctaY + 50f, ctaW, 40f);
            return;
        }
        searchX = leftX;
        searchY = contentY;
        searchH = 38f;
        searchW = leftW;
        listY = searchY + searchH + 10f;
        float buttonsTop;
        if (leftW >= 340f) {
            buttonsTop = contentBottom - BUTTON_H;
            float side = Math.min(120f, leftW * 0.28f);
            place(Btn.LOAD, leftX, buttonsTop, leftW - side * 2f - 16f, BUTTON_H);
            place(Btn.REFRESH, leftX + leftW - side * 2f - 8f, buttonsTop, side, BUTTON_H);
            place(Btn.OPEN_FOLDER, leftX + leftW - side, buttonsTop, side, BUTTON_H);
        } else {
            buttonsTop = contentBottom - BUTTON_H * 2f - 8f;
            float half = (leftW - 8f) * 0.5f;
            place(Btn.LOAD, leftX, buttonsTop, leftW, BUTTON_H);
            place(Btn.REFRESH, leftX, buttonsTop + BUTTON_H + 8f, half, BUTTON_H);
            place(Btn.OPEN_FOLDER, leftX + half + 8f, buttonsTop + BUTTON_H + 8f, half, BUTTON_H);
        }
        listH = Math.max(ROW_H, buttonsTop - 10f - listY);
    }

    /** Greedy word wrap into at most {@code maxLines} lines, each ellipsized to {@code maxW}. */
    private List<String> wrap(String text, float size, float maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && ui.width(cur + " " + word, size, false) > maxW) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        while (lines.size() > maxLines) {
            String last = lines.remove(lines.size() - 1);
            lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + last);
        }
        for (int i = 0; i < lines.size(); i++) lines.set(i, ui.ellipsize(lines.get(i), size, maxW));
        return lines;
    }

    private void layoutCentre() {
        boolean loaded = state.hasSchematic();
        float ctrlY = contentBottom - BUTTON_H;
        progressY = ctrlY - 14f - 12f - 6f - smallH;
        gridX = centreX;
        stripX = centreX + centreW - STRIP_W;
        if (loaded) {
            float[] widths = {60f, 50f, 90f, 68f, 50f, 60f};
            Btn[] ids = {Btn.LAYER_DOWN10, Btn.LAYER_DOWN, Btn.PLAY, Btn.SPEED, Btn.LAYER_UP, Btn.LAYER_UP10};
            float total = 8f * (ids.length - 1);
            for (float w : widths) total += w;
            float sc = Math.min(1f, centreW / total);
            float x = centreX + (centreW - total * sc) * 0.5f;
            for (int i = 0; i < ids.length; i++) {
                place(ids[i], x, ctrlY, widths[i] * sc, BUTTON_H);
                x += widths[i] * sc + 8f * sc;
            }
            gridY = contentY + titleH + smallH + 22f;
            gridW = Math.max(60f, centreW - STRIP_W - 16f);
            gridH = Math.max(40f, progressY - 14f - gridY);
            float trackW = Math.min(132f, centreW * 0.5f);
            float tabH = 34f;
            float tabY = colTop + (HEADER_H - tabH) * 0.5f;
            place(Btn.TAB_3D, centrePX + centrePW - PAD - trackW, tabY, trackW * 0.5f, tabH);
            place(Btn.TAB_2D, centrePX + centrePW - PAD - trackW * 0.5f, tabY, trackW * 0.5f, tabH);
        } else {
            gridY = contentY;
            gridW = centreW;
            gridH = Math.max(40f, contentBottom - gridY);
        }
        stripY = gridY;
        stripH = gridH;
        insetS = clamp(Math.min(gridW, gridH) * 0.28f, 90f, 220f);
        insetX = gridX + 10f;
        insetY = gridY + gridH - 10f - insetS;
        gapW = Math.min(170f, gridW * 0.4f);
        gapX = gridX + gridW - 12f - gapW;
        gapY = gridY + 10f + tinyH + 8f;
        if (view3dActive()) {
            float avail = gridW - 20f - 12f;
            float sc = Math.min(1f, avail / 372f);
            float x = gridX + 10f;
            place(Btn.VIEW_MODE, x, gridY + 10f, 144f * sc, OVERLAY_H);
            x += 144f * sc + 6f;
            place(Btn.AUTO_ROTATE, x, gridY + 10f, 120f * sc, OVERLAY_H);
            x += 120f * sc + 6f;
            place(Btn.RESET_VIEW, x, gridY + 10f, 96f * sc, OVERLAY_H);
        }
        Schematic schematic = state.schematic();
        if (schematic == null) return;
        if (view3d) {
            grid.layout(schematic, insetX + 6f, insetY + 24f, insetS - 12f, insetS - 30f);
        } else {
            grid.layout(schematic, gridX + 8f, gridY + 8f, gridW - 16f, gridH - 16f);
        }
    }

    private void layoutRight() {
        boolean loaded = state.hasSchematic();
        buildInfoRows();
        float step = smallH + 8f;
        float btnH = 34f;
        float bgap = 8f;
        int rows = infoList.size();
        float avail = colBottom - colTop - GAP;
        float materialsMin = HEADER_H + 110f;
        if (!loaded) {
            detailsH = HEADER_H + 12f + rows * step + PAD;
        } else {
            while (true) {
                detailsH = HEADER_H + 12f + rows * step + 10f + 4f * btnH + 3f * bgap + PAD;
                if (detailsH + materialsMin <= avail || (rows <= 3 && btnH <= 28f)) break;
                if (rows > 3) {
                    rows--;
                } else {
                    btnH = 28f;
                    bgap = 6f;
                }
            }
        }
        infoRows = rows;
        infoY = colTop + HEADER_H + 12f;
        materialsPY = colTop + detailsH + GAP;
        materialsPH = Math.max(0f, colBottom - materialsPY);
        materialsY = materialsPY + HEADER_H + 10f;
        materialsH = Math.max(MATERIAL_ROW_H, colBottom - PAD - materialsY);
        if (!loaded) return;
        float buttonsY = infoY + infoRows * step + 10f;
        float half = (rightW - 8f) * 0.5f;
        Btn[] ids = {Btn.ORIGIN_HERE, Btn.ORIGIN_LOOK, Btn.ROTATE, Btn.MIRROR, Btn.BUILDER, Btn.PROJECTION, Btn.LAYER_MODE, Btn.UNLOAD};
        for (int i = 0; i < ids.length; i++) {
            place(ids[i], rightX + (i % 2) * (half + 8f), buttonsY + (i / 2) * (btnH + bgap), half, btnH);
        }
        float tabH = 34f;
        float tabsY = materialsPY + (HEADER_H - tabH) * 0.5f;
        float third = rightW / 3f;
        place(Btn.TAB_LAYER, rightX, tabsY, third, tabH);
        place(Btn.TAB_TOTAL, rightX + third, tabsY, third, tabH);
        place(Btn.TAB_ISSUES, rightX + third * 2f, tabsY, third, tabH);
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
        segView = AnimationUtility.snap(AnimationUtility.approach(segView, view3d ? 0f : 1f, dt, 16f), view3d ? 0f : 1f, 0.001f);
        float matTarget = issuesShown ? 2f : materialsTotal ? 1f : 0f;
        segMat = AnimationUtility.snap(AnimationUtility.approach(segMat, matTarget, dt, 16f), matTarget, 0.001f);
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

    /** Standalone only: a glass pill with the title, the loaded schematic and the close button. */
    private void renderTopBar(float alpha) {
        ui.panel(panelX, panelY, panelW, TOPBAR_H, PANEL_RADIUS, alpha);
        SchematicViewport.drawCubeIcon(panelX + 36f, panelY + TOPBAR_H * 0.5f + 5f, 7.5f,
                SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.6f), alpha);
        String title = tr("screen.combatant.schematic.title");
        ui.text(title, panelX + 54f, panelY + (TOPBAR_H - titleH) * 0.5f, SchematicUi.TITLE, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
        Schematic schematic = state.schematic();
        String sub = schematic == null ? tr("screen.combatant.schematic.none_loaded")
                : schematic.name() + "  ·  " + schematic.sizeX() + "x" + schematic.sizeY() + "x" + schematic.sizeZ();
        float titleW = ui.width(title, SchematicUi.TITLE, true);
        ui.text(ui.ellipsize(sub, SchematicUi.BODY, panelW * 0.5f), panelX + 54f + titleW + 20f, panelY + (TOPBAR_H - bodyH) * 0.5f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        drawButton(Btn.CLOSE, "X", false, true, alpha);
    }

    private void renderLeft(float alpha) {
        ui.header(tr("screen.combatant.schematic.title"), leftPX, colTop, leftPW, HEADER_H, alpha);
        drawButton(Btn.REFRESH, library.isScanning() ? "..." : tr("screen.combatant.schematic.refresh"), false, true, alpha);
        if (!libraryEmpty) renderSearch(alpha);
        ui.section(leftX, listY, leftW, listH, alpha);
        refreshFiltered();
        int visible = Math.max(1, (int) ((listH - 8f) / ROW_H));
        listFirst = clampInt(listFirst, 0, Math.max(0, filtered.size() - visible));
        if (filtered.isEmpty()) renderEmptyList(alpha);
        for (int i = 0; i < visible && listFirst + i < filtered.size(); i++) {
            renderRow(filtered.get(listFirst + i), listY + 4f + i * ROW_H, i, alpha);
        }
        if (filtered.size() > visible) ui.scrollbar(leftX + leftW - 7f, listY + 8f, listH - 16f, listFirst, visible, filtered.size(), alpha);
        boolean canLoad = selectedPath != null && SchematicActions.loadingPath() == null;
        drawButton(Btn.LOAD, tr("screen.combatant.schematic.load"), false, canLoad, canLoad, alpha);
        drawButton(Btn.OPEN_FOLDER, tr("screen.combatant.schematic.open_folder"), false, true, libraryEmpty, alpha);
    }

    private void renderEmptyList(float alpha) {
        float cx = leftX + leftW * 0.5f;
        boolean scanning = library.isScanning();
        float cy = libraryEmpty ? emptyY + 10f : listY + Math.min(listH * 0.35f, 120f);
        if (scanning) {
            float spin = (System.nanoTime() / 1_000_000L % 1200L) / 1200f * 360f;
            Renderer2D.COLOR.arcStroke(cx, cy, 16f, 3f, spin, spin + 260f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        } else {
            SchematicViewport.drawCubeIcon(cx, cy, 20f, SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.4f), alpha);
        }
        String title = scanning ? tr("screen.combatant.schematic.scanning")
                : tr(libraryEmpty ? "screen.combatant.schematic.empty" : "screen.combatant.schematic.no_matches");
        ui.textCentered(ui.ellipsize(title, SchematicUi.BODY, leftW - 24f), cx, cy + 40f, SchematicUi.BODY,
                SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
        if (!libraryEmpty) return;
        for (int i = 0; i < emptyHint.size(); i++) {
            ui.textCentered(emptyHint.get(i), cx, cy + 40f + bodyH + 8f + i * (smallH + 4f), SchematicUi.SMALL,
                    SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        }
    }

    private void renderSearch(float alpha) {
        float r = searchH * 0.5f;
        int fill = SchematicUi.withAlpha(SchematicUi.mix(SchematicUi.GLASS_RGB, 0xFF000000, searchFocused ? 0.2f : 0.4f),
                Math.round(alpha * (searchFocused ? 150f : 120f)));
        Renderer2D.COLOR.roundedRect(searchX, searchY, searchW, searchH, r, fill);
        if (searchFocused) {
            Renderer2D.COLOR.roundedRectStroke(searchX, searchY, searchW, searchH, r, 1f, SchematicUi.scaleAlpha(SchematicUi.withAlpha(SchematicUi.ACCENT, 150), alpha));
        }
        String shown = search.isEmpty() && !searchFocused ? tr("screen.combatant.schematic.search") : search;
        if (searchFocused && AnimationUtility.blink(500L)) shown += "|";
        int color = search.isEmpty() && !searchFocused ? SchematicUi.FAINT : SchematicUi.TEXT;
        ui.text(ui.ellipsize(shown, SchematicUi.BODY, searchW - 36f), searchX + 18f, searchY + (searchH - bodyH) * 0.5f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(color, alpha), false);
    }

    private void renderRow(SchematicHeader header, float y, int slot, float alpha) {
        float x = leftX + 4f;
        float w = leftW - 16f;
        float rh = ROW_H - 6f;
        boolean selected = header.path().equals(selectedPath);
        boolean hovered = SchematicUi.inside(mouseX, mouseY, x, y, w, rh);
        boolean active = state.schematic() != null && SchematicFormats.baseName(header.path()).equals(state.schematic().fileName());
        float h = 0f;
        if (slot < rowHover.length) {
            rowHover[slot] = AnimationUtility.approach(rowHover[slot], hovered ? 1f : 0f, frameDt, 14f);
            h = rowHover[slot];
        }
        if (selected) {
            int fill = SchematicUi.withAlpha(SchematicUi.mix(SchematicUi.GLASS_RGB, SchematicUi.ACCENT, 0.42f), Math.round(165f + h * 35f));
            Renderer2D.COLOR.roundedRect(x, y, w, rh, 10f, SchematicUi.scaleAlpha(fill, alpha));
            Renderer2D.COLOR.roundedRect(x + 4f, y + 12f, 3f, rh - 24f, 1.5f, SchematicUi.scaleAlpha(SchematicUi.ACCENT, alpha));
        } else if (h > 0.01f) {
            Renderer2D.COLOR.roundedRect(x, y, w, rh, 10f, SchematicUi.withAlpha(SchematicUi.HOVER_RGB, Math.round(alpha * h * 120f)));
        }
        int nameColor = header.failed() ? SchematicUi.BAD : SchematicUi.TEXT;
        ui.text(ui.ellipsize(header.name(), SchematicUi.BODY, w - 52f), x + 18f + h * 2f, y + 8f, SchematicUi.BODY,
                SchematicUi.scaleAlpha(nameColor, alpha), selected);
        if (active) {
            Renderer2D.COLOR.circle(x + w - 16f, y + 8f + bodyH * 0.5f, 6f, SchematicUi.withAlpha(SchematicUi.GOOD, Math.round(alpha * 50f)));
            Renderer2D.COLOR.circle(x + w - 16f, y + 8f + bodyH * 0.5f, 3f, SchematicUi.scaleAlpha(SchematicUi.GOOD, alpha));
        }
        float chipY = y + 12f + bodyH;
        if (header.failed()) {
            float cw = ui.chip(x + 18f, chipY, tr("screen.combatant.schematic.row_failed"), SchematicUi.BAD, alpha);
            ui.text(ui.ellipsize(header.error(), SchematicUi.TINY, w - cw - 40f), x + 26f + cw, chipY + 3f, SchematicUi.TINY,
                    SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
            return;
        }
        float cx = x + 18f;
        cx += ui.chip(cx, chipY, header.dimensions(), SchematicUi.ACCENT, alpha) + 6f;
        if (header.totalBlocks() >= 0 && cx + 60f < x + w) {
            ui.chip(cx, chipY, tr("screen.combatant.schematic.blocks_count", header.totalBlocks()), SchematicUi.GOOD, alpha);
        }
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
    }

    // ---------------------------------------------------------------- centre

    private void renderCentreHeader(float alpha) {
        Schematic schematic = state.schematic();
        boolean loaded = schematic != null;
        float trackW = loaded ? bw[Btn.TAB_3D.ordinal()] + bw[Btn.TAB_2D.ordinal()] : 0f;
        float limit = centrePX + centrePW - PAD - trackW - 12f;
        String title = loaded ? schematic.name() : tr("screen.combatant.schematic.panel.preview");
        String shown = ui.ellipsize(title, SchematicUi.TITLE, Math.max(60f, (limit - centrePX - 40f) * 0.6f));
        float end = ui.header(shown, centrePX, colTop, centrePW, HEADER_H, alpha);
        if (loaded) {
            String dims = schematic.sizeX() + "x" + schematic.sizeY() + "x" + schematic.sizeZ();
            float chipY = colTop + (HEADER_H - (tinyH + 6f)) * 0.5f;
            if (end + 90f < limit) end += 10f + ui.chip(end + 10f, chipY, dims, SchematicUi.ACCENT, alpha);
            float room = limit - end - 16f;
            if (room > 150f) {
                ui.text(ui.ellipsize(tr("screen.combatant.schematic.keys_hint"), SchematicUi.SMALL, room), end + 16f,
                        colTop + (HEADER_H - smallH) * 0.5f, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.FAINT, alpha), false);
            }
            int a = Btn.TAB_3D.ordinal();
            ui.segmentTrack(bx[a], by[a], trackW, bh[a], alpha);
            ui.segmentPill(bx[a] + segView * bw[a], by[a], bw[a], bh[a], alpha);
            drawTab(Btn.TAB_3D, tr("screen.combatant.schematic.view.tab_3d"), view3d, true, alpha);
            drawTab(Btn.TAB_2D, tr("screen.combatant.schematic.view.tab_2d"), !view3d, true, alpha);
        }
    }

    private void renderCentre(float alpha) {
        renderCentreHeader(alpha);
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
        if (viewport.faces() == null) {
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
        ui.well(insetX, insetY, insetS, insetS, 10f, alpha * 0.95f);
        Renderer2D.COLOR.roundedRectStroke(insetX, insetY, insetS, insetS, 10f, 1f,
                SchematicUi.scaleAlpha(hot ? SchematicUi.ACCENT : SchematicUi.PANEL_STROKE, alpha));
        grid.render(schematic, state.layer(), diff, alpha);
        ui.chip(insetX + 6f, insetY + 6f, tr("screen.combatant.schematic.view.tab_2d"), SchematicUi.ACCENT, alpha);
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
            ui.textRight(tr("screen.combatant.schematic.playing", PLAY_SPEEDS[speedIndex]), centreX + centreW, contentY + 6f,
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
        segment(x, barY, w * diff.wrong(layer) / total, barH, SchematicUi.BAD, alpha);
        int buildTotal = 0, buildCorrect = 0;
        for (int y = 0; y < state.layerCount(); y++) {
            buildTotal += diff.total(y);
            buildCorrect += diff.correct(y);
        }
        String text = tr("screen.combatant.schematic.progress", diff.correct(layer), diff.total(layer), diff.missing(layer),
                diff.wrong(layer), buildTotal == 0 ? 100 : buildCorrect * 100 / buildTotal);
        ui.text(text, centreX, barY + barH + 6f, SchematicUi.SMALL, SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha * 0.9f), false);
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
        boolean loaded = state.hasSchematic();
        boolean placed = state.isPlaced();
        ui.header(tr("screen.combatant.schematic.panel.details"), rightPX, colTop, rightPW, HEADER_H, alpha);
        renderInfo(alpha);
        if (!loaded) {
            renderMaterialsPlaceholder(alpha);
            return;
        }
        drawButton(Btn.ORIGIN_HERE, tr("screen.combatant.schematic.origin_here"), false, loaded, alpha);
        drawButton(Btn.ORIGIN_LOOK, tr("screen.combatant.schematic.origin_look"), false, loaded, alpha);
        drawButton(Btn.ROTATE, tr("screen.combatant.schematic.rotate"), false, placed, alpha);
        drawButton(Btn.MIRROR, tr("screen.combatant.schematic.mirror"), false, placed, alpha);
        boolean building = SchematicActions.builderEnabled();
        drawButton(Btn.BUILDER, tr(building ? "screen.combatant.schematic.builder_stop" : "screen.combatant.schematic.builder_start"),
                building, placed, placed && !building, alpha);
        drawButton(Btn.PROJECTION, tr("screen.combatant.schematic.projection"), SchematicActions.projectionEnabled(), true, alpha);
        drawButton(Btn.LAYER_MODE, tr("screen.combatant.schematic.layer_mode." + state.layerMode().name().toLowerCase(Locale.ROOT)),
                false, true, alpha);
        drawButton(Btn.UNLOAD, tr("screen.combatant.schematic.unload"), false, loaded, alpha);
        refreshIssues();
        float tabH = bh[Btn.TAB_LAYER.ordinal()];
        float tabY = by[Btn.TAB_LAYER.ordinal()];
        ui.segmentTrack(rightX, tabY, rightW, tabH, alpha);
        ui.segmentPill(rightX + segMat * (rightW / 3f), tabY, rightW / 3f, tabH, alpha);
        Renderer2D.COLOR.quad(rightPX + 14f, materialsPY + HEADER_H, rightPW - 28f, 1f, SchematicUi.scaleAlpha(SchematicUi.SPLIT, alpha));
        drawTab(Btn.TAB_LAYER, tr("screen.combatant.schematic.materials_layer"), !materialsTotal && !issuesShown, loaded, alpha);
        drawTab(Btn.TAB_TOTAL, tr("screen.combatant.schematic.materials_total"), materialsTotal && !issuesShown, loaded, alpha);
        drawTab(Btn.TAB_ISSUES, tr("screen.combatant.schematic.attention_tab", issues.size()), issuesShown, loaded, alpha);
        if (issuesShown) {
            renderIssues(alpha);
        } else {
            renderMaterials(alpha);
        }
    }

    /** Nothing loaded: the materials card says so instead of showing an empty box. */
    private void renderMaterialsPlaceholder(float alpha) {
        ui.header(tr("screen.combatant.schematic.panel.materials"), rightPX, materialsPY, rightPW, HEADER_H, alpha);
        float cx = rightX + rightW * 0.5f;
        float cy = materialsPY + HEADER_H + Math.max(40f, (materialsPH - HEADER_H) * 0.38f);
        SchematicViewport.drawCubeIcon(cx, cy, 16f, SchematicUi.mix(SchematicUi.SURFACE_HOVER, SchematicUi.ACCENT, 0.3f), alpha);
        ui.textCentered(ui.ellipsize(tr("screen.combatant.schematic.none_loaded"), SchematicUi.BODY, rightW - 16f), cx, cy + 34f,
                SchematicUi.BODY, SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
    }

    private static String[] infoRow(String labelKey, String value) {
        return new String[]{tr(labelKey), value};
    }

    /** Rows of the info card in priority order, so a short column drops the least useful ones first. */
    private void buildInfoRows() {
        infoList.clear();
        Schematic schematic = state.schematic();
        if (schematic == null) {
            infoList.add(infoRow("screen.combatant.schematic.info.folder", library.directory().toAbsolutePath().normalize().toString()));
            infoList.add(infoRow("screen.combatant.schematic.info.files", String.valueOf(library.headers().size())));
            return;
        }
        infoList.add(infoRow("screen.combatant.schematic.info.name", schematic.name()));
        infoList.add(infoRow("screen.combatant.schematic.info.size", schematic.sizeX() + " x " + schematic.sizeY() + " x " + schematic.sizeZ()));
        infoList.add(infoRow("screen.combatant.schematic.info.blocks", String.valueOf(schematic.totalBlocks())));
        SchematicPlacement placement = state.placement();
        if (placement == null) {
            infoList.add(infoRow("screen.combatant.schematic.info.origin", tr("screen.combatant.schematic.info.not_placed")));
        } else {
            BlockPos o = placement.origin();
            infoList.add(infoRow("screen.combatant.schematic.info.origin", o.getX() + ", " + o.getY() + ", " + o.getZ()));
            String rotation = switch (placement.rotation()) {
                case NONE -> "0°";
                case CLOCKWISE_90 -> "90°";
                case CLOCKWISE_180 -> "180°";
                case COUNTERCLOCKWISE_90 -> "270°";
            };
            infoList.add(infoRow("screen.combatant.schematic.info.rotation", rotation));
            infoList.add(infoRow("screen.combatant.schematic.info.mirror", mirrorName(placement.mirror())));
            SchematicBuilder builder = ModuleManager.get(SchematicBuilder.class);
            if (builder != null) {
                String status = tr("screen.combatant.schematic.builder." + builder.status().name().toLowerCase(Locale.ROOT));
                if (builder.placedCount() > 0) status += " · " + builder.placedCount();
                infoList.add(infoRow("screen.combatant.schematic.info.builder", status));
                int chests = builder.chestIndex().indexedKeys(Minecraft.getInstance().level).length;
                if (chests > 0) infoList.add(infoRow("screen.combatant.schematic.info.chests", String.valueOf(chests)));
            }
        }
        infoList.add(infoRow("screen.combatant.schematic.info.author", schematic.author().isEmpty() ? "-" : schematic.author()));
        infoList.add(infoRow("screen.combatant.schematic.info.palette", String.valueOf(schematic.paletteSize() - 1)));
        infoList.add(infoRow("screen.combatant.schematic.info.regions", String.valueOf(schematic.regionCount())));
        infoList.add(infoRow("screen.combatant.schematic.info.warnings", String.valueOf(schematic.warnings().size())));
    }

    private void renderInfo(float alpha) {
        float step = smallH + 8f;
        for (int i = 0; i < infoRows && i < infoList.size(); i++) {
            String[] row = infoList.get(i);
            float y = infoY + i * step;
            if ((i & 1) == 1) {
                Renderer2D.COLOR.roundedRect(rightX - 4f, y - 4f, rightW + 8f, step, 4f,
                        SchematicUi.scaleAlpha(SchematicUi.withAlpha(SchematicUi.SURFACE, 60), alpha));
            }
            infoLine(row[0], row[1], y, alpha);
        }
    }

    private static String mirrorName(Mirror mirror) {
        return tr("screen.combatant.schematic.mirror." + mirror.name().toLowerCase(Locale.ROOT));
    }

    private void infoLine(String label, String value, float y, float alpha) {
        ui.text(ui.ellipsize(label, SchematicUi.SMALL, rightW * 0.38f - 8f), rightX, y, SchematicUi.SMALL,
                SchematicUi.scaleAlpha(SchematicUi.MUTED, alpha), false);
        float valueX = rightX + rightW * 0.38f;
        ui.textRight(ui.ellipsize(value, SchematicUi.SMALL, rightX + rightW - valueX), rightX + rightW, y, SchematicUi.SMALL,
                SchematicUi.scaleAlpha(SchematicUi.TEXT, alpha), true);
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
            ui.scrollbar(rightX + rightW - 7f, materialsY + 8f, materialsH - 16f, materialsFirst, visible, materials.size(), alpha);
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
            ui.scrollbar(rightX + rightW - 7f, materialsY + 8f, materialsH - 16f, materialsFirst, visible, issues.size(), alpha);
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
        boolean rowHot = SchematicUi.inside(mouseX, mouseY, x, y, w, rh);
        int card = builderMissing ? SchematicUi.mix(SchematicUi.SURFACE, SchematicUi.BAD, 0.22f)
                : rowHot ? SchematicUi.SURFACE_HOVER : SchematicUi.SURFACE;
        Renderer2D.COLOR.roundedRect(x, y, w, rh, 6f, SchematicUi.scaleAlpha(SchematicUi.withAlpha(card, builderMissing || rowHot ? 255 : 120), alpha));
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
        Renderer2D.COLOR.roundedRect(barX, barY, barW, 4f, 2f, SchematicUi.scaleAlpha(SchematicUi.WELL, alpha));
        int fill = done ? SchematicUi.GOOD : SchematicUi.mix(SchematicUi.ACCENT, SchematicUi.GOOD, fraction);
        if (fraction > 0f) Renderer2D.COLOR.roundedRect(barX, barY, Math.max(4f, barW * fraction), 4f, 2f, SchematicUi.scaleAlpha(fill, alpha));
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
            materialHave[i] = SchematicMaterials.countInInventory(Minecraft.getInstance().player, materials.get(i).item());
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
        drawButton(id, label, active, enabled, false, alpha);
    }

    private void drawButton(Btn id, String label, boolean active, boolean enabled, boolean primary, float alpha) {
        int i = id.ordinal();
        if (!bShown[i]) return;
        ui.button(bx[i], by[i], bw[i], bh[i], label, enabled ? hover[i] : 0f, press[i], active, enabled, primary, alpha);
    }

    private void drawTab(Btn id, String label, boolean active, boolean enabled, float alpha) {
        int i = id.ordinal();
        if (!bShown[i]) return;
        ui.tab(bx[i], by[i], bw[i], bh[i], label, enabled ? hover[i] : 0f, press[i], active, enabled, alpha);
    }

    // ---------------------------------------------------------------- input

    /** Pointer press in the fixed (virtual) space; {@code doubled} marks a double-click. Always consumed. */
    public boolean mousePressed(float mx, float my, int button, boolean doubled) {
        mouseX = mx;
        mouseY = my;
        if (button != 0) {
            boolean pan = button == 1 || button == 2;
            if (pan && view3dActive() && insideViewport(mx, my) && !overViewportChrome(mx, my)) viewport.beginDrag(button, mx, my);
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
            case CLOSE -> requestClose();
            case REFRESH -> library.refresh();
            case LOAD -> SchematicActions.load(selectedPath);
            case OPEN_FOLDER -> SchematicActions.openFolder();
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

    /** @return whether a drag of this panel is in progress and consumed the movement. */
    public boolean mouseDragged(float mx, float my) {
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
        return false;
    }

    public void mouseReleased(float mx, float my) {
        draggingStrip = false;
        draggingGap = false;
        if (viewport.dragging()) {
            int layer = viewport.endDrag(mx, my);
            if (layer >= 0) state.setLayer(layer);
        }
    }

    public boolean mouseScrolled(float mx, float my, double vertical) {
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

    /** @return whether the key was consumed; Escape is left to the host when the panel has no close action. */
    public boolean keyPressed(int key) {
        if (searchFocused) {
            if (key == InputKeys.BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (key == InputKeys.ESCAPE || key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        switch (key) {
            case InputKeys.ESCAPE -> {
                if (onClose == null) return false;
                onClose.run();
            }
            case InputKeys.UP -> state.stepLayer(1);
            case InputKeys.DOWN -> state.stepLayer(-1);
            case InputKeys.PAGE_UP -> state.stepLayer(10);
            case InputKeys.PAGE_DOWN -> state.stepLayer(-10);
            case InputKeys.HOME -> state.setLayer(0);
            case InputKeys.END -> state.setLayer(state.layerCount() - 1);
            case InputKeys.SPACE -> togglePlay();
            case InputKeys.ENTER, InputKeys.KP_ENTER -> SchematicActions.load(selectedPath);
            case InputKeys.R -> viewport.resetCamera();
            case InputKeys.T -> setView3d(!view3d);
            case InputKeys.E -> viewport.cycleMode();
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean charTyped(int codepoint) {
        if (!searchFocused) return false;
        if (codepoint >= 32 && codepoint != 127 && search.length() < 64) search += Character.toString(codepoint);
        listFirst = 0;
        return true;
    }

    /** The panel is leaving the screen: stop playback. */
    public void removed() {
        playing = false;
    }

    private void requestClose() {
        if (onClose != null) onClose.run();
    }

    private boolean shiftDown() {
        return keyDown(InputKeys.LEFT_SHIFT) || keyDown(InputKeys.RIGHT_SHIFT);
    }

    private boolean ctrlDown() {
        return keyDown(InputKeys.LEFT_CONTROL) || keyDown(InputKeys.RIGHT_CONTROL);
    }

    private boolean keyDown(int key) {
        return CombatantInput.state().isPressed(InputDevice.KEYBOARD, key);
    }

    // ---------------------------------------------------------------- metrics

    private void updateUiMetrics() {
        Minecraft mc = Minecraft.getInstance();
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float nextW = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        float nextH = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
        if (nextW != fixedWidth || nextH != fixedHeight) ui.clearCaches();
        fixedWidth = nextW;
        fixedHeight = nextH;
    }

    /** Converts a vanilla GUI-scaled screen coordinate into the fixed (virtual) space this panel works in. */
    public static float toFixed(float screen) {
        Minecraft mc = Minecraft.getInstance();
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
