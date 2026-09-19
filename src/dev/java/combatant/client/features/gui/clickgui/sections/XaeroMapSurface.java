/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.relations.CategoryService;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.UiBlurResources;
import combatant.client.render.engine.renderer.ui.UiDeferredScheduler;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.map.MapGridSpec;
import combatant.client.render.map.MapBearingRay;
import combatant.client.render.map.MapPlayerMarker;
import combatant.client.render.map.MapPlayerMarkerRenderer;
import combatant.client.render.map.MapUncertainty;
import combatant.client.render.map.MapOverlaySnapshot;
import combatant.client.render.map.MapPoint;
import combatant.client.render.map.MapRect;
import combatant.client.render.map.MapSceneDataSnapshot;
import combatant.client.render.map.MapSceneRenderer;
import combatant.client.render.map.MapScreenPoint;
import combatant.client.render.map.MapTerrainTile;
import combatant.client.render.map.MapTileCoordinate;
import combatant.client.render.map.MapTileGpuCopy;
import combatant.client.render.map.MapTileMaterial;
import combatant.client.render.map.MapTileRenderer;
import combatant.client.render.map.MapTileResidencyKey;
import combatant.client.render.map.MapTileUvRect;
import combatant.client.render.map.MapViewport;
import combatant.client.render.map.MapVisibleTileSelector;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.LegacyTextUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.graphics.GpuTextureAndView;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.single.SingleConfigManager;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.config.util.WorldMapClientConfigUtils;
import xaero.map.controls.ControlsRegister;
import xaero.map.mods.SupportMods;
import xaero.map.mods.gui.Waypoint;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.region.BranchLeveledRegion;
import xaero.map.region.LeveledRegion;
import xaero.map.region.MapRegion;
import xaero.map.region.texture.RegionTexture;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class XaeroMapSurface {
    private static final int TILE_RESOLUTION = 64;
    private static final int TILES_PER_REGION_SIDE = 8;
    private static final int REGION_BLOCKS = 512;
    private static final int MAX_LOD = 3;
    private static final int MAX_BRANCH_REQUESTS_PER_FRAME = 2;
    private static final int MAX_LEAF_REQUESTS_PER_FRAME = 1;
    private static final double MIN_SCALE = 1.0 / 16.0;
    private static final double MAX_SCALE = 50.0;
    private static final long ZOOM_ANIMATION_NS = 100_000_000L;
    private static double destinationScale = 3.0;

    private final MapVisibleTileSelector visibleTileSelector = new MapVisibleTileSelector(TILE_RESOLUTION, 16384);
    private final MapSceneRenderer sceneRenderer = new MapSceneRenderer(
            new MapTileRenderer(TILE_RESOLUTION, 8, 32, 32, 8192, 128L * 1024L * 1024L),
            128,
            2L * 1024L * 1024L
    );
    private final ArrayList<BranchLeveledRegion> branchRequests = new ArrayList<>();
    private final Set<MapRegion> leafRequests = new LinkedHashSet<>();
    private final Set<LeveledRegion<?>> updatedRegions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, LoadingRegion> loadingRegions = new LinkedHashMap<>();
    private final boolean[] waitingForBranchCache = new boolean[1];
    private final XaeroMapElements elements = new XaeroMapElements();
    private final XaeroMapUiRenderer.ChromeMotion chromeMotion = new XaeroMapUiRenderer.ChromeMotion();
    private XaeroMapSettingsPanel settings;
    private long settingsRetryAfterNanos;
    private final ArrayList<UiButton> uiButtons = new ArrayList<>();
    private final ArrayList<MenuEntry> contextEntries = new ArrayList<>();
    private final ArrayList<ElementHit> drawerHits = new ArrayList<>();
    private final ArrayList<TargetHit> targetHits = new ArrayList<>();
    private List<LocationMarkerEntry> locationPlayerMarkers = List.of();
    private LocationMarkerEntry hoveredLocationMarker;
    private LocationMarkerEntry rightClickLocationMarker;
    private String transientStatus = "";
    private long transientStatusUntilMs;

    private double centerX;
    private double centerZ;
    private double userScale = destinationScale;
    private double zoomFromScale = destinationScale;
    private long zoomAnimationStart;
    private float zoomAnchorX;
    private float zoomAnchorY;
    private boolean zoomAroundPointer;
    private boolean centered;
    private boolean dragging;
    private float dragX;
    private float dragY;
    private float areaX;
    private float areaY;
    private float areaWidth;
    private float areaHeight;
    private long frameId;
    private long loadingAnimationStart = System.currentTimeMillis();
    private String viewedDimension;
    private boolean prevWaitingForBranchCache;
    private boolean prevLoadingLeaves = true;
    private boolean lastFrameRenderedRootTextures;
    private boolean loadingLeaves;
    private MapViewport viewport;
    private MapProcessor activeProcessor;
    private MapDimension activeDimension;
    private XaeroMapElements.Snapshot elementSnapshot = XaeroMapElements.Snapshot.empty();
    private XaeroMapElements.Element hoveredElement;
    private int pointerBlockX;
    private int pointerBlockY = Short.MAX_VALUE;
    private int pointerBlockZ;
    private boolean rightSelecting;
    private XaeroMapElements.Element rightClickElement;
    private int selectionStartX;
    private int selectionStartZ;
    private int selectionEndX;
    private int selectionEndZ;
    private int contextBlockX;
    private int contextBlockY = Short.MAX_VALUE;
    private int contextBlockZ;
    private float contextX;
    private float contextY;
    private double contextWorldX = Double.NaN;
    private double contextWorldZ = Double.NaN;
    private XaeroMapUiRenderer.ContextBounds contextBounds;
    private boolean contextOpen;
    private boolean deleteArmed;
    private boolean helpOpen;
    private XaeroMapUiRenderer.HelpBounds helpBounds;
    private Drawer drawer = Drawer.NONE;
    private XaeroMapWaypointEditor waypointEditor;
    private double waypointEditorWorldX = Double.NaN;
    private double waypointEditorWorldZ = Double.NaN;
    private Action pressedUiAction;

    Frame render(float x, float y, float width, float height, float mouseX, float mouseY) {
        areaX = x;
        areaY = y;
        areaWidth = width;
        areaHeight = height;
        hoveredElement = null;
        hoveredLocationMarker = null;
        // The map itself is not a permanent "move" affordance. Keep the native cursor neutral
        // until the user is actually panning/selecting or points at an interactive overlay.
        // Otherwise the whole surface looked draggable even while simply inspecting the map.
        if (contains(mouseX, mouseY)) {
            if (rightSelecting) SystemCursor.set(SystemCursor.CursorType.CROSSHAIR);
            else if (dragging) SystemCursor.set(SystemCursor.CursorType.MOVE);
        }

        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."));
        MapProcessor processor = session.getMapProcessor();
        if (processor == null || !processor.isMapWorldUsable()) return Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."));
        MapWorld world = processor.getMapWorld();
        MapDimension dimension = world == null ? null : world.getCurrentDimension();
        if (dimension == null) return Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."));
        activeProcessor = processor;
        activeDimension = dimension;

        // Map chrome uses the rendered map as its optical scene. Register the capture before any
        // atlas/direct-textured map draw so immediate and deferred paths can both mirror into it.
        UiBlurResources.requestUiUnderlay(UiDeferredScheduler.layerForCurrentPhase(false));

        String dimensionId = dimension.getDimId().identifier().toString() + '/' + dimension.getCurrentMultiworld();
        if (!dimensionId.equals(viewedDimension)) {
            viewedDimension = dimensionId;
            centered = false;
            dragging = false;
        }
        if (!centered) centerOnPlayer(processor, dimension);
        updateZoom();
        updateDrag(mouseX, mouseY);

        double pixelsPerBlock = logicalPixelsPerBlock(userScale);
        viewport = new MapViewport(new MapRect(x, y, width, height), centerX, centerZ, pixelsPerBlock);
        updatePointer(viewport, mouseX, mouseY);
        if (processor.getCurrentWorldId() == null
                || processor.isWaitingForWorldUpdate()
                || !processor.getMapSaveLoad().isRegionDetectionComplete()) {
            drawMapUi(processor, dimension, mouseX, mouseY);
            return Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."));
        }

        int lod = selectXaeroLod();
        List<MapTerrainTile> terrain;
        synchronized (processor.renderThreadPauseSync) {
            if (processor.isRenderingPaused()) {
                drawMapUi(processor, dimension, mouseX, mouseY);
                return Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."));
            }
            processor.updateCaveStart();
            processor.getMapSaveLoad().mainTextureLevel = lod;
            terrain = collectTerrain(processor, dimension, viewport, lod);
            pointerBlockY = sampleHeight(processor, pointerBlockX, pointerBlockZ, lod);
        }

        MapSceneDataSnapshot snapshot = new MapSceneDataSnapshot(
                ++frameId,
                terrain,
                collectOverlays(dimension),
                MapGridSpec.DISABLED
        );
        MapSceneRenderer.FrameResult result = sceneRenderer.render(
                frameId,
                viewport,
                snapshot,
                ClickGuiRenderer.getOnestMedium()
        );
        drawLoading(viewport);
        drawSelection(viewport);
        drawFootprints(processor, dimension, viewport);
        Minecraft minecraft = Minecraft.getInstance();
        boolean tabDown = minecraft != null && minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_TAB);
        elements.setTabDown(tabDown);
        elementSnapshot = elements.collect(processor, dimension, userScale);
        boolean elementPointerActive = (settings == null || !settings.isVisible())
                && !contextOpen && !helpOpen && waypointEditor == null && drawer == Drawer.NONE
                && contains(mouseX, mouseY);
        hoveredElement = elements.render(elementSnapshot, viewport,
                elementPointerActive ? mouseX : Float.NaN,
                elementPointerActive ? mouseY : Float.NaN);
        if (hoveredElement != null) SystemCursor.set(SystemCursor.CursorType.HAND);
        renderLocationPlayerMarkers(viewport, mouseX, mouseY, elementPointerActive);
        if (hoveredLocationMarker != null) {
            elements.clearHover();
            hoveredElement = null;
            SystemCursor.set(SystemCursor.CursorType.HAND);
        }
        drawPlayerArrow(processor, dimension, viewport);
        if (hoveredLocationMarker == null) elements.renderHover(viewport);
        drawMapUi(processor, dimension, mouseX, mouseY);
        return result.terrainTilesDrawn() == 0
                ? Frame.waiting(tr("gui.combatant.map.loading", "Preparing World Map..."))
                : new Frame(true, "", result.terrainTilesDrawn());
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (waypointEditor != null) {
            boolean consumed = waypointEditor.mousePressed(mouseX, mouseY, button);
            if (waypointEditor.isClosed()) waypointEditor = null;
            if (consumed) return true;
        }
        if (settings != null && settings.mousePressed(mouseX, mouseY, button)) return true;
        if (helpOpen && helpBounds != null && helpBounds.contains(mouseX, mouseY)) return true;
        if (contextOpen && clickContext(mouseX, mouseY, button)) return true;
        if (clickUiButton(mouseX, mouseY, button)) return true;
        if (clickDrawer(mouseX, mouseY, button)) return true;
        if (!contains(mouseX, mouseY)) return false;

        helpOpen = false;
        contextOpen = false;
        deleteArmed = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightSelecting = true;
            rightClickLocationMarker = hoveredLocationMarker;
            rightClickElement = rightClickLocationMarker == null ? hoveredElement : null;
            contextBlockX = pointerBlockX;
            contextBlockY = pointerBlockY;
            contextBlockZ = pointerBlockZ;
            if (rightClickLocationMarker != null) {
                PlayerLocationSnapshot location = rightClickLocationMarker.location();
                contextWorldX = location.x();
                contextWorldZ = location.z();
                contextBlockX = floorBlock(location.x());
                contextBlockZ = floorBlock(location.z());
                contextBlockY = Double.isFinite(location.y()) ? floorBlock(location.y()) : Short.MAX_VALUE;
            } else if (rightClickElement != null) {
                contextWorldX = rightClickElement.worldX();
                contextWorldZ = rightClickElement.worldZ();
                contextBlockX = floorBlock(contextWorldX);
                contextBlockY = Short.MAX_VALUE;
                contextBlockZ = floorBlock(contextWorldZ);
            } else if (viewport != null) {
                MapPoint anchor = viewport.unproject(mouseX, mouseY);
                contextWorldX = anchor.x();
                contextWorldZ = anchor.z();
            } else {
                contextWorldX = contextBlockX;
                contextWorldZ = contextBlockZ;
            }
            selectionStartX = selectionEndX = pointerBlockX >> 4;
            selectionStartZ = selectionEndZ = pointerBlockZ >> 4;
            if (rightClickLocationMarker != null || rightClickElement != null) {
                selectionStartX = selectionEndX = contextBlockX >> 4;
                selectionStartZ = selectionEndZ = contextBlockZ >> 4;
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (hoveredLocationMarker != null) {
            centerX = hoveredLocationMarker.location().x();
            centerZ = hoveredLocationMarker.location().z();
            centered = true;
            return true;
        }
        if (hoveredElement != null) {
            centerX = hoveredElement.worldX();
            centerZ = hoveredElement.worldZ();
            centered = true;
            return true;
        }
        dragging = true;
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
        if (settings != null) settings.mouseReleased(mouseX, mouseY, button);
        if (waypointEditor != null) {
            waypointEditor.mouseReleased(mouseX, mouseY, button);
            if (waypointEditor.isClosed()) waypointEditor = null;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            pressedUiAction = null;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && rightSelecting) {
            rightSelecting = false;
            updateContextScreenAnchor();
            contextOpen = true;
            contextBounds = null;
            rebuildContextEntries();
        }
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (settings != null && settings.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (!contains(mouseX, mouseY) || amount == 0.0) return false;
        changeZoom(amount, mouseX, mouseY, amount > 0.0);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waypointEditor != null) {
            boolean consumed = waypointEditor.keyPressed(keyCode, modifiers);
            if (waypointEditor.isClosed()) waypointEditor = null;
            if (consumed) return true;
        }
        if (settings != null && settings.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (helpOpen) {
                helpOpen = false;
                return true;
            }
            if (contextOpen || drawer != Drawer.NONE) {
                contextOpen = false;
                contextBounds = null;
                drawer = Drawer.NONE;
                return true;
            }
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            changeZoom(1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            changeZoom(-1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        if (keyCode == ControlsRegister.keyZoomIn.getDefaultKey().getValue()) {
            changeZoom(1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        if (keyCode == ControlsRegister.keyZoomOut.getDefaultKey().getValue()) {
            changeZoom(-1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        if (keyCode == ControlsRegister.keyOpenSettings.getDefaultKey().getValue()) {
            toggleSettings();
            return true;
        }
        if (keyCode == ControlsRegister.keyToggleDimension.getDefaultKey().getValue()) {
            toggleDimension();
            return true;
        }
        if (SupportMods.minimap()) {
            if (keyCode == SupportMods.xaeroMinimap.getWaypointKeyBinding().getDefaultKey().getValue()) {
                openWaypointDraft(null);
                return true;
            }
            if (keyCode == SupportMods.xaeroMinimap.getTempWaypointKeyBinding().getDefaultKey().getValue()) {
                createTemporaryWaypoint();
                return true;
            }
            if (keyCode == SupportMods.xaeroMinimap.getTempWaypointsMenuKeyBinding().getDefaultKey().getValue()) {
                drawer = drawer == Drawer.WAYPOINTS ? Drawer.NONE : Drawer.WAYPOINTS;
                return true;
            }
        }
        if (hoveredElement != null && keyCode == GLFW.GLFW_KEY_T) {
            runElementTeleport(hoveredElement);
            return true;
        }
        if (hoveredElement != null && keyCode == GLFW.GLFW_KEY_E
                && hoveredElement.handle() instanceof Waypoint waypoint) {
            openWaypointDraft(waypoint);
            return true;
        }
        return false;
    }

    boolean charTyped(char chr, int modifiers) {
        if (waypointEditor != null) {
            boolean consumed = waypointEditor.charTyped(chr);
            if (waypointEditor.isClosed()) waypointEditor = null;
            if (consumed) return true;
        }
        return settings != null && settings.charTyped(chr, modifiers);
    }

    void recenter() {
        centered = false;
    }

    void resume() {
        dragging = false;
        rightSelecting = false;
    }

    void suspend() {
        dragging = false;
        rightSelecting = false;
        contextOpen = false;
        helpOpen = false;
        hoveredElement = null;
        elements.clearHover();
    }

    private List<MapTerrainTile> collectTerrain(MapProcessor processor,
                                                MapDimension dimension,
                                                MapViewport viewport,
                                                int requestedLod) {
        branchRequests.clear();
        leafRequests.clear();
        updatedRegions.clear();
        loadingRegions.clear();
        waitingForBranchCache[0] = false;
        loadingLeaves = false;
        boolean renderedRoot = false;
        int brightness = Math.max(0, Math.min(255, Math.round(processor.getBrightness() * 255.0f)));
        int tint = brightness << 24 | 0x00FFFFFF;
        String sourceId = sourceId(processor, dimension);
        List<MapTerrainTile> terrain = new ArrayList<>();
        int leveledRegionShift = 9 + requestedLod;
        LeveledRegion.setComparison(
                pointerBlockX >> leveledRegionShift,
                pointerBlockZ >> leveledRegionShift,
                requestedLod,
                pointerBlockX >> 9,
                pointerBlockZ >> 9
        );
        VisibleLeaves visibleLeaves = prepareVisibleLeaves(processor, viewport, requestedLod);

        for (MapTileCoordinate requested : visibleTileSelector.select(viewport, requestedLod, 0)) {
            ResolvedTile tile = resolveTile(processor, requested, visibleLeaves);
            if (tile == null) {
                continue;
            }
            dimension.getLayeredMapRegions().bumpLoadedRegion(tile.region());
            if (tile.sourceRegion() != tile.region()) {
                dimension.getLayeredMapRegions().bumpLoadedRegion(tile.sourceRegion());
                renderedRoot = true;
            }
            GpuTextureAndView gpu = tile.texture().getGlColorTexture();
            MapTileResidencyKey key = new MapTileResidencyKey(sourceId, tile.sourceCoordinate());
            MapTileMaterial material = tile.texture().getTextureHasLight()
                    ? MapTileMaterial.LIGHT_IN_ALPHA
                    : MapTileMaterial.OPAQUE_RGB;
            terrain.add(MapTerrainTile.gpuCopy(
                    key,
                    tile.texture().getTextureVersion(),
                    requested.worldBounds(TILE_RESOLUTION),
                    new MapTileGpuCopy(gpu.view, 0, 0, TILE_RESOLUTION, TILE_RESOLUTION),
                    material,
                    tile.textureUv(),
                    tint
            ));
        }

        requestLoading(processor);
        prevWaitingForBranchCache = waitingForBranchCache[0];
        prevLoadingLeaves = loadingLeaves;
        lastFrameRenderedRootTextures = renderedRoot;
        return List.copyOf(terrain);
    }

    /**
     * Mirrors GuiMap's leaf-region walk. A leaf is considered once per frame, independently of
     * how many 64x64 textures from its leveled region are visible.
     */
    private VisibleLeaves prepareVisibleLeaves(MapProcessor processor, MapViewport viewport, int lod) {
        MapRect world = viewport.visibleWorldBounds();
        int textureShift = 6 + lod;
        int minTextureX = (int) Math.floor(world.x()) >> textureShift;
        int maxTextureX = (int) Math.floor(world.maxX()) >> textureShift;
        int minTextureZ = (int) Math.floor(world.y()) >> textureShift;
        int maxTextureZ = (int) Math.floor(world.maxY()) >> textureShift;
        int minLeafX = minTextureX << textureShift >> 9;
        int maxLeafX = ((maxTextureX + 1 << textureShift) - 1) >> 9;
        int minLeafZ = minTextureZ << textureShift >> 9;
        int maxLeafZ = ((maxTextureZ + 1 << textureShift) - 1) >> 9;
        int leveledSide = 1 << lod;
        int caveLayer = processor.getCurrentCaveLayer();
        Set<Long> regions = new LinkedHashSet<>();

        for (int leafX = minLeafX; leafX <= maxLeafX; leafX++) {
            for (int leafZ = minLeafZ; leafZ <= maxLeafZ; leafZ++) {
                MapRegion leaf = processor.getLeafMapRegion(caveLayer, leafX, leafZ, false);
                if (leaf == null && processor.regionExists(caveLayer, leafX, leafZ)) {
                    leaf = processor.getLeafMapRegion(caveLayer, leafX, leafZ, true);
                }
                if (leaf == null) continue;
                queueLeafIfNeeded(processor, leaf, lod);
                regions.add(regionKey(Math.floorDiv(leafX, leveledSide), Math.floorDiv(leafZ, leveledSide)));
            }
        }
        return new VisibleLeaves(regions, minLeafX, minLeafZ, maxLeafX, maxLeafZ);
    }

    private ResolvedTile resolveTile(MapProcessor processor,
                                     MapTileCoordinate requested,
                                     VisibleLeaves visibleLeaves) {
        int lod = requested.lod();
        int regionX = Math.floorDiv(requested.x(), TILES_PER_REGION_SIDE);
        int regionZ = Math.floorDiv(requested.z(), TILES_PER_REGION_SIDE);
        if (!visibleLeaves.regions().contains(regionKey(regionX, regionZ))) return null;

        LeveledRegion<?> region = processor.getLeveledRegion(
                processor.getCurrentCaveLayer(), regionX, regionZ, lod);
        if (region == null) return null;
        if (region.loadingAnimation()) addLoadingRegion(requested);

        LeveledRegion<?> root = region.getRootRegion();
        if (root == region) root = null;
        if (root != null && !root.isLoaded()) {
            if (root instanceof BranchLeveledRegion rootBranch
                    && !rootBranch.recacheHasBeenRequested()
                    && !rootBranch.reloadHasBeenRequested()) {
                queueBranch(rootBranch);
            }
            waitingForBranchCache[0] = true;
            root = null;
        }

        updateBranch(processor, region, root, lod, visibleLeaves);
        int localX = Math.floorMod(requested.x(), TILES_PER_REGION_SIDE);
        int localZ = Math.floorMod(requested.z(), TILES_PER_REGION_SIDE);
        RegionTexture<?> exact = region.hasTextures() ? region.getTexture(localX, localZ) : null;
        if (usable(exact)) {
            return new ResolvedTile(requested, region, region, exact, MapTileUvRect.FULL);
        }

        // GuiMap's exact root fallback (lines 971-1004 in Xaero 26.2): one root texel
        // rectangle covers the missing texture at the requested level.
        if (root == null || !root.hasTextures()) {
            return null;
        }
        int levelDiff = MAX_LOD - lod;
        int rootSize = 1 << levelDiff;
        int maxInsideCoord = rootSize - 1;
        int firstTextureX = regionX << 3;
        int firstTextureZ = regionZ << 3;
        int insideX = (firstTextureX & maxInsideCoord) + localX;
        int insideZ = (firstTextureZ & maxInsideCoord) + localZ;
        int rootTextureX = (firstTextureX >> levelDiff & 7) + (insideX >> levelDiff);
        int rootTextureZ = (firstTextureZ >> levelDiff & 7) + (insideZ >> levelDiff);
        RegionTexture<?> fallback = root.getTexture(rootTextureX, rootTextureZ);
        if (!usable(fallback)) {
            return null;
        }
        int insideTextureX = insideX & maxInsideCoord;
        int insideTextureZ = insideZ & maxInsideCoord;
        MapTileUvRect uv = new MapTileUvRect(
                insideTextureX / (double) rootSize,
                insideTextureZ / (double) rootSize,
                (insideTextureX + 1.0) / rootSize,
                (insideTextureZ + 1.0) / rootSize
        );
        MapTileCoordinate source = new MapTileCoordinate(
                root.getRegionX() * TILES_PER_REGION_SIDE + rootTextureX,
                root.getRegionZ() * TILES_PER_REGION_SIDE + rootTextureZ,
                MAX_LOD
        );
        return new ResolvedTile(source, region, root, fallback, uv);
    }

    private void updateBranch(MapProcessor processor,
                              LeveledRegion<?> region,
                              LeveledRegion<?> root,
                              int lod,
                              VisibleLeaves visibleLeaves) {
        if (processor.isUploadingPaused() || WorldMap.pauseRequests) return;
        if (region instanceof BranchLeveledRegion branch && updatedRegions.add(region)) {
            branch.checkForUpdates(
                    processor,
                    prevWaitingForBranchCache,
                    waitingForBranchCache,
                    branchRequests,
                    lod,
                    visibleLeaves.minX(),
                    visibleLeaves.minZ(),
                    visibleLeaves.maxX(),
                    visibleLeaves.maxZ()
            );
        }

        if ((lod != 0 && !prevWaitingForBranchCache || lod == 0 && !prevLoadingLeaves)
                && lastFrameRenderedRootTextures) {
            if (root instanceof BranchLeveledRegion rootBranch && rootBranch != region && updatedRegions.add(root)) {
                rootBranch.checkForUpdates(
                        processor,
                        prevWaitingForBranchCache,
                        waitingForBranchCache,
                        branchRequests,
                        lod,
                        visibleLeaves.minX(),
                        visibleLeaves.minZ(),
                        visibleLeaves.maxX(),
                        visibleLeaves.maxZ()
                );
            }
        }
    }

    private static long regionKey(int x, int z) {
        return (long) x << 32 ^ z & 0xFFFFFFFFL;
    }

    @SuppressWarnings("unchecked")
    private void queueLeafIfNeeded(MapProcessor processor, MapRegion leaf, int lod) {
        ClientConfigManager configManager = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        SingleConfigManager<?> primary = configManager.getPrimaryConfigManager();
        boolean cacheOnly = processor.getMapWorld().isCacheOnlyMode();
        boolean reloadEverything = (Boolean) primary.getEffective(
                (ConfigOption<Boolean>) WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED);
        int reloadVersion = (Integer) primary.getEffective(WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED_VERSION);
        int globalVersion = (Integer) primary.getEffective(WorldMapPrimaryClientConfigOptions.GLOBAL_VERSION);
        int cacheHash = WorldMap.settings.getRegionCacheHashCode();
        int caveStart = processor.getMapWorld().getCurrentDimension().getLayeredMapRegions()
                .getLayer(processor.getCurrentCaveLayer()).getCaveStart();
        int caveDepth = (Integer) configManager.getEffective(
                (ConfigOption<Integer>) WorldMapProfiledConfigOptions.CAVE_MODE_DEPTH);

        synchronized (leaf) {
            if (lod != 0 && leaf.getLoadState() == 0
                    && leaf.loadingNeededForBranchLevel != 0
                    && leaf.loadingNeededForBranchLevel != lod) {
                leaf.loadingNeededForBranchLevel = 0;
                leaf.getParent().setShouldCheckForUpdatesRecursive(true);
            }
            boolean stale = !cacheOnly && (reloadEverything && leaf.getReloadVersion() != reloadVersion
                    || leaf.getCacheHashCode() != cacheHash
                    || leaf.caveStartOutdated(caveStart, caveDepth)
                    || leaf.getVersion() != globalVersion
                    || leaf.getLoadState() != 2 && leaf.shouldCache());
            boolean requiredForLevel = leaf.getLoadState() == 0
                    && (!leaf.isMetaLoaded() || lod == 0 || leaf.loadingNeededForBranchLevel == lod);
            boolean highlightsChanged = (leaf.isMetaLoaded() || leaf.getLoadState() != 0 || !leaf.hasHadTerrain())
                    && leaf.getHighlightsHash() != leaf.getDim().getHighlightHandler()
                    .getRegionHash(leaf.getRegionX(), leaf.getRegionZ());
            if (leaf.canRequestReload_unsynced() && (stale || requiredForLevel || highlightsChanged)) {
                loadingLeaves = true;
                leaf.calculateSortingDistance();
                leafRequests.add(leaf);
            }
        }
    }

    private void queueBranch(BranchLeveledRegion branch) {
        if (branch == null || branchRequests.contains(branch)) return;
        branch.calculateSortingDistance();
        branchRequests.add(branch);
    }

    private void requestLoading(MapProcessor processor) {
        if (WorldMap.pauseRequests) return;
        LeveledRegion<?> next = processor.getMapSaveLoad().getNextToLoadByViewing();
        if ((next != null && !next.shouldAllowAnotherRegionToLoad())
                || processor.getAffectingLoadingFrequencyCount() >= 16) return;

        int[] branchCounter = {0};
        branchRequests.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .filter(branch -> !branch.reloadHasBeenRequested()
                        && !branch.recacheHasBeenRequested()
                        && !branch.isLoaded())
                .limit(MAX_BRANCH_REQUESTS_PER_FRAME)
                .forEach(branch -> {
                    branch.setReloadHasBeenRequested(true, "Gui");
                    processor.getMapSaveLoad().requestBranchCache(branch, "Gui");
                    if (branchCounter[0]++ == 0) {
                        processor.getMapSaveLoad().setNextToLoadByViewing(branch);
                    }
                });

        if (prevWaitingForBranchCache) return;
        int requested = 0;
        List<MapRegion> sortedLeaves = leafRequests.stream().sorted().toList();
        for (MapRegion leaf : sortedLeaves) {
            if (requested >= MAX_LEAF_REQUESTS_PER_FRAME) break;
            if (leaf == next && sortedLeaves.size() > 1) continue;
            synchronized (leaf) {
                if (!leaf.canRequestReload_unsynced()) continue;
                leaf.calculateSortingDistance();
                if (leaf.getLoadState() == 2) {
                    leaf.requestRefresh(processor);
                } else {
                    processor.getMapSaveLoad().requestLoad(leaf, "Gui");
                }
                if (requested == 0) {
                    processor.getMapSaveLoad().setNextToLoadByViewing(leaf);
                }
                requested++;
                if (leaf.getLoadState() == 4) break;
            }
        }
    }

    private void addLoadingRegion(MapTileCoordinate requested) {
        int regionX = Math.floorDiv(requested.x(), TILES_PER_REGION_SIDE);
        int regionZ = Math.floorDiv(requested.z(), TILES_PER_REGION_SIDE);
        double span = REGION_BLOCKS * (1 << requested.lod());
        loadingRegions.putIfAbsent(
                requested.lod() + ":" + regionX + ":" + regionZ,
                new LoadingRegion((regionX + 0.5) * span, (regionZ + 0.5) * span, requested.lod())
        );
    }

    private MapOverlaySnapshot collectOverlays(MapDimension dimension) {
        if (dimension == null) return MapOverlaySnapshot.EMPTY;
        String dimensionKey = dimension.getDimId().identifier().toString();
        var view = PlayerLocationService.get().snapshot();
        List<LocationMarkerEntry> players = new ArrayList<>();
        List<MapUncertainty> uncertainty = new ArrayList<>();
        List<MapBearingRay> bearings = new ArrayList<>();
        Set<String> bearingKeys = new LinkedHashSet<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, PlayerLocationSnapshot> entry : view.bestByPlayer().entrySet()) {
            UUID id = entry.getKey();
            PlayerLocationSnapshot location = entry.getValue();
            if (location == null || !dimensionKey.equals(location.worldIdentity().dimensionKey())) continue;

            String name = MapPlayerMarkerRenderer.resolveDisplayName(id, location.playerName());
            if (name == null || name.isBlank()) name = MapTriangulationConfig.get().nameForTarget(id);
            int accent = CategoryService.getColor(name);

            if (!location.hasPosition()) {
                // Bearing samples are solver input, not a debug history overlay. In the normal map
                // only the current constraint for an explicitly targeted player is useful. Older
                // observations stay inside HeuristicRuntime and never remain painted at previous
                // observer positions.
                if (location.source() == PlayerLocationSource.LOCATOR_BEARING
                        && location.hasBearing()
                        && MapTriangulationConfig.get().isTargeted(id, name)) {
                    double observerX = metadataDouble(location, "observerX");
                    double observerZ = metadataDouble(location, "observerZ");
                    if (Double.isFinite(observerX) && Double.isFinite(observerZ)) {
                        String key = bearingKey(id, location.sourceRevision());
                        if (bearingKeys.add(key)) {
                            bearings.add(new MapBearingRay(
                                    key, observerX, observerZ, location.bearingRadians(), name,
                                    withAlpha(accent, 0.66f * freshnessAlpha(location, now)), 1.35,
                                    150 + location.source().priority()));
                        }
                    }
                }
                continue;
            }

            if (location.source() == PlayerLocationSource.LOCAL_ENTITY_EXACT
                    && !MapUiConfig.get().advancedPlayerMarkers()) continue;

            float alpha = location.source() == PlayerLocationSource.HISTORICAL ? 0.42f : freshnessAlpha(location, now);
            String statusLabel = location.source() == PlayerLocationSource.HISTORICAL
                    ? staleAgeLabel(location, now) : "";
            MapPlayerMarker marker = new MapPlayerMarker(
                    "location-player:" + id, location.x(), location.z(), id, name, statusLabel, accent,
                    sourceGlyph(location.source()), location.exact() ? 27.0f : 25.5f, alpha,
                    300 + location.source().priority());
            players.add(new LocationMarkerEntry(marker, location));

            double major = location.uncertaintyMajor();
            double minor = location.uncertaintyMinor();
            if (!location.exact() && (major > 0.0 || minor > 0.0)) {
                if (major <= 0.0) major = minor;
                if (minor <= 0.0) minor = major;
                int fill = withAlpha(accent, location.source() == PlayerLocationSource.LOCATOR_APPROXIMATE ? 0.12f : 0.095f);
                int stroke = withAlpha(accent, location.source() == PlayerLocationSource.LOCATOR_APPROXIMATE ? 0.68f : 0.58f);
                uncertainty.add(new MapUncertainty(
                        "location-uncertainty:" + id, location.x(), location.z(), major, minor,
                        location.uncertaintyAngleRadians(), fill, stroke, 180 + location.source().priority()));
            }
        }

        // Historical/accepted solver observations stay out of the production
        // map overlay. They are useful for diagnostics and solving, but rendering them here creates
        // stale fans anchored at every previous observer position.

        locationPlayerMarkers = List.copyOf(players);
        return new MapOverlaySnapshot(List.of(), List.of(), List.of(), uncertainty, bearings);
    }

    private void renderLocationPlayerMarkers(MapViewport viewport,
                                             float mouseX,
                                             float mouseY,
                                             boolean interactive) {
        if (viewport == null || locationPlayerMarkers.isEmpty()) return;
        MapRect clip = viewport.screenBounds();
        hoveredLocationMarker = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        if (interactive) {
            for (LocationMarkerEntry entry : locationPlayerMarkers) {
                MapPlayerMarker marker = entry.marker();
                MapScreenPoint point = viewport.project(marker.worldX(), marker.worldZ());
                if (!clip.contains(point.x(), point.y())) continue;
                float hitRadius = Math.max(14.0f, marker.sizePixels() * 0.62f + 2.0f);
                double dx = mouseX - point.x();
                double dy = mouseY - point.y();
                double distance = dx * dx + dy * dy;
                if (distance <= hitRadius * hitRadius && distance < bestDistance) {
                    hoveredLocationMarker = entry;
                    bestDistance = distance;
                }
            }
        }
        try (ClipFunction.Scope ignored = ClipFunction.rectScope(clip.x(), clip.y(), clip.width(), clip.height())) {
            for (LocationMarkerEntry entry : locationPlayerMarkers) {
                MapPlayerMarker marker = entry.marker();
                MapScreenPoint point = viewport.project(marker.worldX(), marker.worldZ());
                float radius = marker.sizePixels() * 0.8f + 24.0f;
                if (!clip.intersects(new MapRect(point.x() - radius, point.y() - radius, radius * 2.0f, radius * 2.0f))) continue;
                MapPlayerMarkerRenderer.drawMarker((float) point.x(), (float) point.y(), marker.playerUuid(), marker.playerName(),
                        marker.accentArgb(), marker.sourceGlyph(), marker.sizePixels(), marker.alpha(),
                        entry == hoveredLocationMarker);
            }
            Renderer2D.flushBatch();
            for (LocationMarkerEntry entry : locationPlayerMarkers) {
                MapPlayerMarker marker = entry.marker();
                MapScreenPoint point = viewport.project(marker.worldX(), marker.worldZ());
                float radius = marker.sizePixels() * 0.8f + 24.0f;
                if (!clip.intersects(new MapRect(point.x() - radius, point.y() - radius, radius * 2.0f, radius * 2.0f))) continue;
                MapPlayerMarkerRenderer.drawLabel((float) point.x(), (float) point.y(), marker.playerUuid(), marker.playerName(),
                        marker.statusLabel(), marker.accentArgb(), marker.sizePixels(), marker.alpha(),
                        entry == hoveredLocationMarker);
            }
        }
    }

    private static String sourceGlyph(PlayerLocationSource source) {
        if (source == null) return "";
        return switch (source) {
            case LOCAL_ENTITY_EXACT -> "";
            case MAPLINK_EXACT -> "map-pinned";
            case LOCATOR_EXACT, LOCATOR_APPROXIMATE -> "locate-fixed";
            case TRIANGULATED, DUPLEX_TRIANGULATED -> "crosshair";
            case LOCATOR_BEARING -> "navigation";
            case HISTORICAL -> "map-pin";
        };
    }

    private static String staleAgeLabel(PlayerLocationSnapshot location, long now) {
        if (location == null) return tr("gui.combatant.map.location.stale", "stale");
        long lostAt = metadataLong(location, "lastLocatorSeenAt");
        long age = lostAt > 0L ? Math.max(0L, now - lostAt) : location.ageMs(now);
        if (age < 1_000L) return tr("gui.combatant.map.location.stale_now", "stale now");
        long seconds = Math.max(1L, age / 1_000L);
        return tr("gui.combatant.map.location.stale", "stale") + " " + seconds + "s";
    }

    private static long metadataLong(PlayerLocationSnapshot location, String key) {
        if (location == null || location.metadata() == null || key == null) return 0L;
        String value = location.metadata().get(key);
        if (value == null || value.isBlank()) return 0L;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
    }

    private static float freshnessAlpha(PlayerLocationSnapshot location, long now) {
        long age = location == null ? 0L : location.ageMs(now);
        if (age <= 2500L) return 1.0f;
        if (age >= 30000L) return 0.58f;
        return 1.0f - (age - 2500L) / 27500.0f * 0.42f;
    }

    private static String bearingKey(UUID id, long sourceRevision) {
        return "bearing:" + id + ':' + sourceRevision;
    }

    private static double metadataDouble(PlayerLocationSnapshot location, String key) {
        if (location == null || location.metadata() == null || key == null) return Double.NaN;
        String value = location.metadata().get(key);
        if (value == null || value.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.max(0, Math.min(255, Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f))) << 24)
                | (argb & 0x00FFFFFF);
    }

    private void drawLoading(MapViewport viewport) {
        long elapsed = System.currentTimeMillis() - loadingAnimationStart;
        double rotation = elapsed % 2000L / 2000.0 * Math.PI * 2.0;
        int actors = 1 + (int) (elapsed % 6000L / 2000L);
        int drawn = 0;
        for (LoadingRegion loading : loadingRegions.values()) {
            if (drawn++ >= 64) break;
            MapScreenPoint point = viewport.project(loading.centerX(), loading.centerZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            double scale = Math.max(0.7, Math.min(1.4,
                    viewport.pixelsPerBlock() * (1 << loading.lod())));
            for (int i = 0; i < actors; i++) {
                double angle = rotation + (i + 1) * Math.PI * 2.0 / 3.0;
                rotatedRect(point.x(), point.y(), 16.0 * scale, 4.0 * scale, angle, 0xFFFFFFFF);
            }
        }
    }

    private void drawPlayerArrow(MapProcessor processor, MapDimension dimension, MapViewport viewport) {
        if (!effective(WorldMapProfiledConfigOptions.ARROW)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        double divisor = playerDimensionDivisor(processor, dimension);
        MapScreenPoint point = viewport.project(
                minecraft.player.getX() / divisor,
                minecraft.player.getZ() / divisor
        );
        float uiScale = 1.0f;
        double edgeX = 27.0 * uiScale;
        double edgeY = 14.0 * uiScale;
        boolean left = point.x() < areaX + edgeX;
        boolean right = point.x() > areaX + areaWidth - edgeX;
        boolean up = point.y() < areaY + edgeY;
        boolean down = point.y() > areaY + areaHeight - edgeY;
        double px = clamp(point.x(), areaX + edgeX, areaX + areaWidth - edgeX);
        double py = clamp(point.y(), areaY + edgeY, areaY + areaHeight - edgeY);
        int color = arrowColor();
        AbstractTexture texture = minecraft.getTextureManager().getTexture(WorldMap.guiTextures);
        if (texture == null || texture.getTextureView() == null) return;
        if (left || right || up || down) {
            float direction = left
                    ? (up ? 1.5f : down ? 0.5f : 1.0f)
                    : right ? (up ? 2.5f : down ? 3.5f : 3.0f)
                    : up ? 2.0f : 0.0f;
            drawXaeroMapObject(texture, px, py + 1.5 * uiScale,
                    54.0 * uiScale, 13.0 * uiScale, 27.0 * uiScale, 13.0 * uiScale,
                    direction * 90.0f, 26.0, 0.0, 54.0, 13.0, 0xD9000000);
            drawXaeroMapObject(texture, px, py,
                    54.0 * uiScale, 13.0 * uiScale, 27.0 * uiScale, 13.0 * uiScale,
                    direction * 90.0f, 26.0, 0.0, 54.0, 13.0, color);
        } else {
            float yaw = minecraft.player.getYRot();
            drawXaeroMapObject(texture, px, py + 1.5 * uiScale,
                    26.0 * uiScale, 28.0 * uiScale, 13.0 * uiScale, 5.0 * uiScale,
                    yaw, 0.0, 0.0, 26.0, 28.0, 0xD9000000);
            drawXaeroMapObject(texture, px, py,
                    26.0 * uiScale, 28.0 * uiScale, 13.0 * uiScale, 5.0 * uiScale,
                    yaw, 0.0, 0.0, 26.0, 28.0, color);
        }
    }

    private void drawFootprints(MapProcessor processor, MapDimension dimension, MapViewport viewport) {
        if (!effective(WorldMapProfiledConfigOptions.FOOTSTEPS)) return;
        ArrayList<Double[]> footprints = processor.getFootprints();
        if (footprints == null || footprints.isEmpty()) return;
        double divisor = playerDimensionDivisor(processor, dimension);
        float uiScale = 1.0f;
        synchronized (footprints) {
            int start = Math.max(0, footprints.size() - 1024);
            for (int i = start; i < footprints.size(); i++) {
                Double[] coordinates = footprints.get(i);
                if (coordinates == null || coordinates.length < 2) continue;
                MapScreenPoint point = viewport.project(coordinates[0] / divisor, coordinates[1] / divisor);
                if (viewport.screenBounds().contains(point.x(), point.y())) {
                    Renderer2D.COLOR.circle(point.x(), point.y(), 2.4 * uiScale, 0xC9000000);
                    Renderer2D.COLOR.circle(point.x(), point.y() - 0.5 * uiScale, 1.65 * uiScale, 0xFFFF3B45);
                }
            }
        }
    }

    private static double playerDimensionDivisor(MapProcessor processor, MapDimension dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || processor.getWorldDimensionTypeRegistry() == null) {
            return 1.0;
        }
        double divisor = dimension.calculateDimDiv(
                processor.getWorldDimensionTypeRegistry(), minecraft.player.level().dimensionType());
        return !Double.isFinite(divisor) || divisor == 0.0 ? 1.0 : divisor;
    }

    private static int arrowColor() {
        MapUiConfig config = MapUiConfig.get();
        return config.isCustomArrowColor() ? config.customArrowColorArgb() : Theme.theme().accent();
    }

    private XaeroMapSettingsPanel ensureSettings() {
        if (settings == null) {
            long now = System.nanoTime();
            if (now < settingsRetryAfterNanos) return null;
            try {
                settings = new XaeroMapSettingsPanel(() -> activeProcessor, () -> activeDimension);
            } catch (RuntimeException error) {
                settingsRetryAfterNanos = now + 1_000_000_000L;
                DebugLog.warnOnce(
                        "clickgui-map-settings-panel",
                        "Xaero World Map settings panel is temporarily unavailable; the map surface will remain active",
                        error
                );
                return null;
            } catch (LinkageError error) {
                settingsRetryAfterNanos = Long.MAX_VALUE;
                DebugLog.warnOnce(
                        "clickgui-map-settings-panel-linkage",
                        "Xaero World Map settings panel is incompatible; the map surface will remain active",
                        error
                );
                return null;
            }
        }
        return settings;
    }

    private void toggleSettings() {
        XaeroMapSettingsPanel panel = ensureSettings();
        if (panel != null) panel.toggle();
    }

    private void openCaveSettings() {
        XaeroMapSettingsPanel panel = ensureSettings();
        if (panel != null) panel.openCave();
    }

    private void drawMapUi(MapProcessor processor, MapDimension dimension, float mouseX, float mouseY) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        boolean radarAvailable = SupportMods.minimap();
        XaeroMapUiRenderer.layoutButtons(uiButtons, areaX, areaY, areaWidth, areaHeight,
                new XaeroMapUiRenderer.ChromeState(
                        settings != null && settings.isOpen(),
                        dimension.getCaveModeType() != 0,
                        processor.getMapWorld().isUsingCustomDimension(),
                        SupportMods.minimap() && effective(WorldMapProfiledConfigOptions.WAYPOINTS),
                        radarAvailable,
                        SupportMods.minimap() && effective(WorldMapProfiledConfigOptions.MINIMAP_RADAR),
                        SupportMods.pac(),
                        SupportMods.pac() && effective(WorldMapProfiledConfigOptions.OPAC_CLAIMS),
                        effective(WorldMapProfiledConfigOptions.ZOOM_BUTTONS),
                        drawer,
                        helpOpen,
                        tr("gui.xaero_box_open_settings", "Settings"),
                        "Recenter",
                        tr("gui.xaero_box_cave_mode", "Cave mode"),
                        tr("gui.xaero_dimension_toggle_button", "Switch dimension"),
                        tr("gui.xaero_box_open_waypoints", "Waypoints"),
                        tr("gui.combatant.map.chrome.targets", "Targeted players"),
                        radarAvailable
                                ? tr("gui.combatant.map.chrome.radar", "Entity radar")
                                : tr("gui.combatant.map.chrome.radar_unavailable", "Entity radar · Xaero Minimap required"),
                        tr("gui.xaero_box_pac_displaying_claims", "Claims"),
                        tr("gui.combatant.map.chrome.radar_list", "Radar list"),
                        tr("gui.combatant.map.chrome.controls", "Controls"),
                        tr("gui.xaero_box_zoom_out", "Zoom out"),
                        tr("gui.xaero_box_zoom_in", "Zoom in")
                ));
        withMapGlassSource(() -> XaeroMapUiRenderer.drawControlChrome(
                uiButtons, mouseX, mouseY, pressedUiAction, palette, chromeMotion));
        XaeroMapUiRenderer.drawCompass(areaX, areaY, areaWidth, areaHeight,
                tr("gui.xaero_compass_north", "N"),
                tr("gui.xaero_compass_east", "E"),
                tr("gui.xaero_compass_south", "S"),
                tr("gui.xaero_compass_west", "W"), palette);
        drawCoordinates(palette, dimension);
        XaeroMapUiRenderer.drawZoom(areaX, areaY, areaWidth, areaHeight, destinationScale, palette);
        withMapGlassSource(() -> XaeroMapUiRenderer.drawDrawer(areaX, areaY, areaWidth, mouseX, mouseY,
                drawer, elementSnapshot, targetedRows(), drawerHits, targetHits,
                effective(WorldMapProfiledConfigOptions.MAP_TELEPORT_ALLOWED), palette, chromeMotion));
        final XaeroMapUiRenderer.HelpBounds[] helpHolder = new XaeroMapUiRenderer.HelpBounds[1];
        withMapGlassSource(() -> helpHolder[0] = XaeroMapUiRenderer.drawHelp(
                areaX, areaY, areaWidth, areaHeight, uiButtons, helpOpen, palette, chromeMotion));
        helpBounds = helpHolder[0];
        if (contextOpen) {
            updateContextScreenAnchor();
            rebuildContextEntries();
            final XaeroMapUiRenderer.ContextBounds[] holder = new XaeroMapUiRenderer.ContextBounds[1];
            withMapGlassSource(() -> holder[0] = XaeroMapUiRenderer.drawContext(
                    areaX, areaY, areaWidth, areaHeight,
                    contextX, contextY, mouseX, mouseY, contextEntries, palette, chromeMotion));
            contextBounds = holder[0];
        } else {
            XaeroMapUiRenderer.updateContextClosed(chromeMotion);
            contextBounds = null;
        }
        if (waypointEditor != null) {
            if (viewport != null && Double.isFinite(waypointEditorWorldX) && Double.isFinite(waypointEditorWorldZ)) {
                MapScreenPoint projected = viewport.project(waypointEditorWorldX, waypointEditorWorldZ);
                waypointEditor.setAnchor((float) projected.x(), (float) projected.y());
            }
            withMapGlassSource(() -> waypointEditor.render(
                    areaX, areaY, areaWidth, areaHeight, mouseX, mouseY, palette));
            if (waypointEditor.isClosed()) {
                waypointEditor = null;
                waypointEditorWorldX = Double.NaN;
                waypointEditorWorldZ = Double.NaN;
            }
        }
        withMapGlassSource(() -> XaeroMapUiRenderer.drawTooltip(areaX, areaY, areaWidth, areaHeight,
                mouseX, mouseY, uiButtons,
                (settings != null && settings.isVisible()) || contextOpen || helpOpen || waypointEditor != null,
                palette, chromeMotion));
        if (settings != null) {
            withMapGlassSource(() -> settings.render(
                    areaX, areaY, areaWidth, areaHeight, mouseX, mouseY));
        }
        drawTransientStatus();
    }

    private static void withMapGlassSource(Runnable draw) {
        Renderer2D.COLOR.withLiquidGlassSceneSource(
                UiBackdropRequest.SceneSource.UI_UNDERLAY, draw);
    }

    private void updateContextScreenAnchor() {
        if (viewport == null || !Double.isFinite(contextWorldX) || !Double.isFinite(contextWorldZ)) return;
        MapScreenPoint projected = viewport.project(contextWorldX, contextWorldZ);
        contextX = clamp((float) projected.x(), areaX + 4.0f, areaX + areaWidth - 4.0f);
        contextY = clamp((float) projected.y(), areaY + 4.0f, areaY + areaHeight - 4.0f);
    }

    private void drawCoordinates(SettingsGuiPalette palette, MapDimension dimension) {
        if (!effective(WorldMapProfiledConfigOptions.COORDINATES)) return;
        if (SupportMods.minimap() && SupportMods.xaeroMinimap.hidingWaypointCoordinates()) return;
        String coordinates = pointerBlockY == Short.MAX_VALUE
                ? "X: " + pointerBlockX + "  Z: " + pointerBlockZ
                : "X: " + pointerBlockX + "  Y: " + pointerBlockY + "  Z: " + pointerBlockZ;
        String dimensionName = activeProcessor == null ? "" : activeProcessor.getDimensionName(dimension.getDimId());
        if (dimensionName != null && !dimensionName.isBlank()) coordinates += "  ·  " + dimensionName;

        XaeroMapUiRenderer.drawCoordinates(areaX, areaY, areaWidth,
                coordinates, clickGuiChromeBottom(), palette);
    }

    private float clickGuiChromeBottom() {
        ClickGuiRenderer.ClickGuiIslandState state = ClickGuiRenderer.islandState();
        if (state.tabCount() <= 0 || state.tabBarH() <= 0.0f) return areaY;
        float lifecycle = clamp((float) state.lifecycle(), 0.0f, 1.0f);
        float inv = 1.0f - lifecycle;
        float eased = 1.0f - inv * inv * inv;
        float shellY = state.tabBarY() - (1.0f - eased) * 18.0f;
        return shellY + state.tabBarH();
    }

    private void rebuildContextEntries() {
        contextEntries.clear();
        XaeroMapElements.Element element = rightClickElement;
        LocationMarkerEntry locationEntry = rightClickLocationMarker;
        if (locationEntry != null) {
            MapPlayerMarker marker = locationEntry.marker();
            PlayerLocationSnapshot location = locationEntry.location();
            String name = marker.playerName().isBlank()
                    ? targetName(location.playerName(), location.playerUuid()) : marker.playerName();
            contextEntries.add(new MenuEntry(name, "users-round", false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(locationSourceLabel(location),
                    locationContextIcon(location.source()), false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(coordinateLabel(location), "clipboard", true,
                    ContextAction.COPY_COORDINATES));
            contextEntries.add(new MenuEntry(chunkLabel(contextBlockX >> 4, contextBlockZ >> 4), "clipboard", true,
                    ContextAction.COPY_CHUNK));
            contextEntries.add(new MenuEntry(tr("gui.combatant.map.action.center_player", "Center on player"),
                    "navigation", true, ContextAction.CENTER_LOCATION));
            boolean targeted = MapTriangulationConfig.get().isTargeted(location.playerUuid(), name);
            contextEntries.add(new MenuEntry(targeted
                    ? tr("gui.combatant.map.action.remove_target", "Remove target")
                    : tr("gui.combatant.map.action.add_target", "Add target"),
                    "crosshair", true, ContextAction.TOGGLE_TARGET));
        } else if (element != null && element.handle() instanceof Waypoint waypoint) {
            contextEntries.add(new MenuEntry(element.plainName(), "map-pin", false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_edit", "Edit"),
                    "pencil", true, ContextAction.EDIT));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_teleport", "Teleport"),
                    "navigation", true, ContextAction.TELEPORT_ELEMENT));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_share", "Share"),
                    "share-2", true, ContextAction.SHARE_ELEMENT));
            contextEntries.add(new MenuEntry(waypoint.isDisabled()
                    ? tr("gui.combatant.map.action.enable", "Enable")
                    : tr("gui.combatant.map.action.disable", "Disable"),
                    "eye", true, ContextAction.TOGGLE_DISABLED));
            contextEntries.add(new MenuEntry(waypoint.isTemporary()
                    ? tr("gui.combatant.map.action.make_permanent", "Make permanent")
                    : tr("gui.combatant.map.action.make_temporary", "Make temporary"),
                    "clock-3", true, ContextAction.TOGGLE_TEMPORARY));
            contextEntries.add(new MenuEntry(deleteArmed
                    ? tr("gui.combatant.map.action.confirm_delete", "Click again to delete")
                    : tr("gui.combatant.map.action.delete", "Delete"),
                    "trash-2", true, ContextAction.DELETE));
        } else if (element != null && element.handle() instanceof PlayerTrackerMapElement<?>) {
            contextEntries.add(new MenuEntry(element.plainName(), "users-round", false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_player_teleport", "Teleport to player"),
                    "navigation", true, ContextAction.TELEPORT_ELEMENT));
        } else if (element != null) {
            contextEntries.add(new MenuEntry(element.plainName(),
                    element.kind() == XaeroMapElements.Kind.WAYPOINT ? "map-pin" : "crosshair",
                    false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(
                    "X " + contextBlockX + "  Z " + contextBlockZ,
                    "clipboard", true, ContextAction.COPY_COORDINATES));
            contextEntries.add(new MenuEntry(
                    chunkLabel(contextBlockX >> 4, contextBlockZ >> 4),
                    "clipboard", true, ContextAction.COPY_CHUNK));
            contextEntries.add(new MenuEntry(
                    tr("gui.combatant.map.action.center_marker", "Center on marker"),
                    "navigation", true, ContextAction.CENTER_ELEMENT));
        } else {
            boolean coordinates = effective(WorldMapProfiledConfigOptions.COORDINATES);
            String chunk = selectionStartX == selectionEndX && selectionStartZ == selectionEndZ
                    ? tr("gui.combatant.map.selection.chunk", "Chunk") + " " + selectionStartX + ", " + selectionStartZ
                    : tr("gui.combatant.map.selection.chunks", "Chunks") + " " + Math.min(selectionStartX, selectionEndX) + ", "
                    + Math.min(selectionStartZ, selectionEndZ) + " → "
                    + Math.max(selectionStartX, selectionEndX) + ", " + Math.max(selectionStartZ, selectionEndZ);
            contextEntries.add(new MenuEntry(chunk, "clipboard", true, ContextAction.COPY_CHUNK));
            if (coordinates) {
                String xyz = contextBlockY == Short.MAX_VALUE
                        ? "X " + contextBlockX + "  Z " + contextBlockZ
                        : "X " + contextBlockX + "  Y " + contextBlockY + "  Z " + contextBlockZ;
                contextEntries.add(new MenuEntry(xyz, "clipboard", true, ContextAction.COPY_COORDINATES));
            }
            if (XaeroMapActions.available() && effective(WorldMapProfiledConfigOptions.WAYPOINTS)) {
                contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_map_create_waypoint", "Create waypoint"),
                        "map-plus", true, ContextAction.CREATE));
                contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_map_create_temporary_waypoint", "Temporary waypoint"),
                        "clock-3", true, ContextAction.CREATE_TEMPORARY));
            }
            boolean teleport = effective(WorldMapProfiledConfigOptions.MAP_TELEPORT_ALLOWED);
            contextEntries.add(new MenuEntry(teleport
                    ? tr("gui.xaero_right_click_map_teleport", "Teleport")
                    : tr("gui.xaero_wm_right_click_map_teleport_not_allowed", "Teleport is disabled"),
                    "navigation", teleport && canTeleportAtPointer(), ContextAction.TELEPORT_MAP));
            if (XaeroMapActions.available()) {
                contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_map_share_location", "Share location"),
                        "share-2", true, ContextAction.SHARE_LOCATION));
                contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_map_waypoints_menu", "Waypoints"),
                        "map-pinned", true, ContextAction.WAYPOINTS));
            }
        }
        contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_box_map_settings", "Settings"),
                "settings-2", true, ContextAction.SETTINGS));
    }

    private boolean clickUiButton(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (UiButton uiButton : uiButtons) {
            if (!uiButton.contains(mouseX, mouseY)) continue;
            if (!uiButton.enabled()) return true;
            pressedUiAction = uiButton.action();
            runUiAction(uiButton.action());
            return true;
        }
        return false;
    }

    private void runUiAction(Action action) {
        if (action != Action.CONTROLS) helpOpen = false;
        switch (action) {
            case SETTINGS -> toggleSettings();
            case RECENTER -> recenter();
            case CAVE -> openCaveSettings();
            case DIMENSION -> toggleDimension();
            case WAYPOINTS -> drawer = drawer == Drawer.WAYPOINTS ? Drawer.NONE : Drawer.WAYPOINTS;
            case PLAYERS -> drawer = drawer == Drawer.PLAYERS ? Drawer.NONE : Drawer.PLAYERS;
            case RADAR_LIST -> drawer = drawer == Drawer.RADAR ? Drawer.NONE : Drawer.RADAR;
            case RADAR -> toggle(WorldMapProfiledConfigOptions.MINIMAP_RADAR);
            case CLAIMS -> toggle(WorldMapProfiledConfigOptions.OPAC_CLAIMS);
            case CONTROLS -> {
                helpOpen = !helpOpen;
                if (helpOpen) {
                    contextOpen = false;
                    contextBounds = null;
                    drawer = Drawer.NONE;
                }
            }
            case ZOOM_IN -> changeZoom(1.0, areaX + areaWidth * 0.5f,
                    areaY + areaHeight * 0.5f, false);
            case ZOOM_OUT -> changeZoom(-1.0, areaX + areaWidth * 0.5f,
                    areaY + areaHeight * 0.5f, false);
        }
    }

    private boolean clickDrawer(float mouseX, float mouseY, int button) {
        for (TargetHit hit : targetHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            PlayerLocationSnapshot location = hit.target().location();
            if (location == null || !location.hasPosition()) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit.teleportable()) {
                if (teleportTarget(location)) return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                centerX = location.x();
                centerZ = location.z();
                centered = true;
            }
            return true;
        }
        for (ElementHit hit : drawerHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightClickLocationMarker = null;
                rightClickElement = hit.element();
                contextWorldX = hit.element().worldX();
                contextWorldZ = hit.element().worldZ();
                updateContextScreenAnchor();
                contextOpen = true;
                contextBounds = null;
                deleteArmed = false;
                rebuildContextEntries();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                centerX = hit.element().worldX();
                centerZ = hit.element().worldZ();
                centered = true;
            }
            return true;
        }
        return false;
    }

    private boolean clickContext(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        XaeroMapUiRenderer.ContextBounds bounds = contextBounds != null
                ? contextBounds
                : XaeroMapUiRenderer.contextBounds(
                        areaX, areaY, areaWidth, areaHeight,
                        contextX, contextY, contextEntries);
        if (bounds == null || !inside(mouseX, mouseY,
                bounds.x(), bounds.y(), bounds.width(), bounds.height())) {
            contextOpen = false;
            contextBounds = null;
            deleteArmed = false;
            rightClickLocationMarker = null;
            return false;
        }
        int row = XaeroMapUiRenderer.contextEntryAt(bounds, mouseX, mouseY, contextEntries);
        if (row >= 0 && row < contextEntries.size()) {
            MenuEntry entry = contextEntries.get(row);
            if (entry.enabled()) runContextAction(entry.action());
        }
        return true;
    }

    private void runContextAction(ContextAction action) {
        XaeroMapElements.Element element = rightClickElement;
        LocationMarkerEntry locationEntry = rightClickLocationMarker;
        Waypoint waypoint = element != null && element.handle() instanceof Waypoint value ? value : null;
        switch (action) {
            case EDIT -> openWaypointDraft(waypoint);
            case TELEPORT_ELEMENT -> runElementTeleport(element);
            case SHARE_ELEMENT -> XaeroMapActions.shareWaypoint(elementSnapshot.waypointWorld(), waypoint);
            case TOGGLE_DISABLED -> XaeroMapActions.toggleDisabled(elementSnapshot.waypointWorld(), waypoint);
            case TOGGLE_TEMPORARY -> XaeroMapActions.toggleTemporary(elementSnapshot.waypointWorld(), waypoint);
            case DELETE -> {
                if (!deleteArmed) {
                    deleteArmed = true;
                    return;
                }
                XaeroMapActions.deleteWaypoint(elementSnapshot.waypointWorld(), waypoint);
            }
            case CREATE -> openWaypointDraft(null);
            case CREATE_TEMPORARY -> createTemporaryWaypoint();
            case TELEPORT_MAP -> teleportMap();
            case SHARE_LOCATION -> XaeroMapActions.shareLocation(elementSnapshot.waypointWorld(),
                    contextBlockX, contextBlockY == Short.MAX_VALUE ? Short.MAX_VALUE : contextBlockY + 1,
                    contextBlockZ);
            case COPY_COORDINATES -> copyContextCoordinates();
            case COPY_CHUNK -> copyContextChunk();
            case CENTER_LOCATION -> {
                if (locationEntry != null && locationEntry.location().hasPosition()) {
                    centerX = locationEntry.location().x();
                    centerZ = locationEntry.location().z();
                    centered = true;
                }
            }
            case CENTER_ELEMENT -> {
                if (element != null) {
                    centerX = element.worldX();
                    centerZ = element.worldZ();
                    centered = true;
                }
            }
            case TOGGLE_TARGET -> {
                if (locationEntry != null) {
                    PlayerLocationSnapshot location = locationEntry.location();
                    String name = locationEntry.marker().playerName();
                    MapTriangulationConfig config = MapTriangulationConfig.get();
                    if (config.isTargeted(location.playerUuid(), name)) {
                        config.removeTargetedPlayer(location.playerUuid(), name);
                    } else {
                        config.addTargetedPlayer(location.playerUuid(), name);
                    }
                }
            }
            case WAYPOINTS -> drawer = Drawer.WAYPOINTS;
            case SETTINGS -> toggleSettings();
            case NONE -> {
            }
        }
        contextOpen = false;
        contextBounds = null;
        deleteArmed = false;
        rightClickLocationMarker = null;
    }

    private void runElementTeleport(XaeroMapElements.Element element) {
        if (element == null) return;
        if (element.handle() instanceof Waypoint waypoint) {
            XaeroMapActions.teleportWaypoint(elementSnapshot.waypointWorld(), waypoint);
        } else if (element.handle() instanceof PlayerTrackerMapElement<?> player) {
            XaeroMapActions.teleportPlayer(activeProcessor, player);
        }
    }

    private void copyContextCoordinates() {
        String value = contextBlockY == Short.MAX_VALUE
                ? contextBlockX + " " + contextBlockZ
                : contextBlockX + " " + contextBlockY + " " + contextBlockZ;
        ClipboardUtil.copy(value);
        showTransientStatus(tr("gui.combatant.map.copied.coordinates", "Coordinates copied") + " · " + value);
    }

    private void copyContextChunk() {
        int minX = Math.min(selectionStartX, selectionEndX);
        int minZ = Math.min(selectionStartZ, selectionEndZ);
        int maxX = Math.max(selectionStartX, selectionEndX);
        int maxZ = Math.max(selectionStartZ, selectionEndZ);
        String value = minX == maxX && minZ == maxZ
                ? minX + " " + minZ
                : minX + " " + minZ + " -> " + maxX + " " + maxZ;
        ClipboardUtil.copy(value);
        showTransientStatus(tr("gui.combatant.map.copied.chunk", "Chunk copied") + " · " + value);
    }

    private void showTransientStatus(String value) {
        transientStatus = value == null ? "" : value.trim();
        transientStatusUntilMs = System.currentTimeMillis() + 1_700L;
    }

    private void drawTransientStatus() {
        if (transientStatus.isBlank()) return;
        long now = System.currentTimeMillis();
        long remaining = transientStatusUntilMs - now;
        if (remaining <= 0L) {
            transientStatus = "";
            return;
        }
        float alpha = remaining < 220L ? Math.max(0.0f, remaining / 220.0f) : 1.0f;
        float textSize = 10.8f;
        float textW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), transientStatus, textSize);
        float width = textW + 18.0f;
        float height = 20.0f;
        float x = areaX + (areaWidth - width) * 0.5f;
        float y = areaY + areaHeight - height - 18.0f;
        Renderer2D.COLOR.roundedRect(x, y, width, height, 7.0f,
                withAlpha(0xFF0B1016, 0.88f * alpha));
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), transientStatus,
                x + 9.0f, y + 4.0f, textSize, withAlpha(0xFFF4F7FA, alpha), false);
    }

    private static int floorBlock(double value) {
        return (int) Math.floor(value);
    }

    private static String coordinateLabel(PlayerLocationSnapshot location) {
        int x = floorBlock(location.x());
        int z = floorBlock(location.z());
        return Double.isFinite(location.y())
                ? "X " + x + "  Y " + floorBlock(location.y()) + "  Z " + z
                : "X " + x + "  Z " + z;
    }

    private static String chunkLabel(int chunkX, int chunkZ) {
        return tr("gui.combatant.map.selection.chunk", "Chunk") + " " + chunkX + ", " + chunkZ;
    }

    private static String locationSourceLabel(PlayerLocationSnapshot location) {
        if (location == null || location.source() == null) {
            return tr("gui.combatant.map.source.unknown", "unknown");
        }
        String source = switch (location.source()) {
            case LOCAL_ENTITY_EXACT -> tr("gui.combatant.map.source.local", "local");
            case MAPLINK_EXACT -> tr("gui.combatant.map.source.maplink", "MapLink");
            case LOCATOR_EXACT -> tr("gui.combatant.map.source.locator_exact", "locator exact");
            case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.source.locator_approx", "locator approximate");
            case LOCATOR_BEARING -> tr("gui.combatant.map.source.bearing", "locator bearing");
            case TRIANGULATED -> tr("gui.combatant.map.source.triangulated", "triangulated");
            case DUPLEX_TRIANGULATED -> tr("gui.combatant.map.source.duplex", "duplex");
            case HISTORICAL -> tr("gui.combatant.map.source.history", "history");
        };
        if (location.source() == PlayerLocationSource.HISTORICAL) {
            String stale = staleAgeLabel(location, System.currentTimeMillis());
            if (!stale.isBlank()) source += " · " + stale;
        }
        return source;
    }

    private static String locationContextIcon(PlayerLocationSource source) {
        if (source == null) return "crosshair";
        return switch (source) {
            case MAPLINK_EXACT -> "map-pinned";
            case LOCATOR_EXACT, LOCATOR_APPROXIMATE, LOCATOR_BEARING -> "locate-fixed";
            case LOCAL_ENTITY_EXACT -> "users-round";
            default -> "crosshair";
        };
    }

    private void createTemporaryWaypoint() {
        if (activeDimension == null || activeProcessor == null) return;
        int x = contextOpen ? contextBlockX : pointerBlockX;
        int y = contextOpen ? contextBlockY : pointerBlockY;
        int z = contextOpen ? contextBlockZ : pointerBlockZ;
        double coordinateScale = activeDimension.calculateDimScale(activeProcessor.getWorldDimensionTypeRegistry());
        XaeroMapActions.createTemporary(elementSnapshot.waypointWorld(), x,
                y == Short.MAX_VALUE ? Short.MAX_VALUE : y + 1, z, coordinateScale);
    }

    private void openWaypointDraft(Waypoint waypoint) {
        if (!XaeroMapActions.available() || elementSnapshot.waypointWorld() == null) return;
        if (waypoint == null && !contextOpen) {
            contextBlockX = pointerBlockX;
            contextBlockY = pointerBlockY;
            contextBlockZ = pointerBlockZ;
        }
        double anchorWorldX = contextBlockX;
        double anchorWorldZ = contextBlockZ;
        if (waypoint != null && rightClickElement != null
                && rightClickElement.handle() == waypoint) {
            anchorWorldX = rightClickElement.worldX();
            anchorWorldZ = rightClickElement.worldZ();
        } else if (waypoint != null && hoveredElement != null
                && hoveredElement.handle() == waypoint) {
            anchorWorldX = hoveredElement.worldX();
            anchorWorldZ = hoveredElement.worldZ();
        }
        waypointEditorWorldX = anchorWorldX;
        waypointEditorWorldZ = anchorWorldZ;

        float editorAnchorX = areaX + areaWidth * 0.5f;
        float editorAnchorY = areaY + areaHeight * 0.5f;
        if (viewport != null) {
            MapScreenPoint projected = viewport.project(waypointEditorWorldX, waypointEditorWorldZ);
            editorAnchorX = (float) projected.x();
            editorAnchorY = (float) projected.y();
        }
        waypointEditor = new XaeroMapWaypointEditor(
                waypoint, editorAnchorX, editorAnchorY,
                (edited, name, symbol, colorIndex) -> {
                    if (edited == null) {
                        XaeroMapActions.createWaypoint(elementSnapshot.waypointWorld(), contextBlockX,
                                contextBlockY == Short.MAX_VALUE ? Short.MAX_VALUE : contextBlockY + 1,
                                contextBlockZ, name, symbol, colorIndex);
                    } else {
                        XaeroMapActions.editWaypoint(elementSnapshot.waypointWorld(), edited,
                                name, symbol, colorIndex);
                    }
                });
        contextOpen = false;
    }

    private void teleportMap() {
        if (!canTeleportAtPointer() || activeDimension == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> target =
                minecraft != null && minecraft.level != null
                        && activeDimension.getDimId().equals(minecraft.level.dimension())
                        ? null : activeDimension.getDimId();
        XaeroMapActions.teleportMap(activeProcessor, contextBlockX, contextBlockY, contextBlockZ, target);
    }

    private boolean canTeleportAtPointer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gameMode != null
                && (!minecraft.gameMode.canHurtPlayer() || contextBlockY != Short.MAX_VALUE);
    }

    private List<TargetRow> targetedRows() {
        MapTriangulationConfig config = MapTriangulationConfig.get();
        Map<UUID, PlayerLocationSnapshot> locations = PlayerLocationService.get().snapshot().bestByPlayer();
        LinkedHashMap<UUID, TargetRow> rows = new LinkedHashMap<>();

        locations.forEach((id, location) -> {
            if (config.isTargeted(id, location.playerName())) {
                String name = location.playerName().isBlank() ? config.nameForTarget(id) : location.playerName();
                rows.put(id, new TargetRow(id, targetName(name, id), location));
            }
        });
        Set<String> ids = config.targetedPlayersValue().get();
        if (ids != null) {
            for (String raw : ids) {
                try {
                    UUID id = UUID.fromString(raw == null ? "" : raw.trim());
                    PlayerLocationSnapshot location = locations.get(id);
                    rows.putIfAbsent(id, new TargetRow(id,
                            targetName(config.nameForTarget(id), id), location));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Set<String> names = config.targetedPlayerNamesValue().get();
        if (names != null) {
            for (String raw : names) {
                if (raw == null || raw.isBlank()) continue;
                String name = raw.trim();
                TargetRow resolved = rows.values().stream()
                        .filter(row -> row.name().equalsIgnoreCase(name))
                        .findFirst().orElse(null);
                if (resolved == null) {
                    UUID id = MapTriangulationConfig.offlineTargetUuid(name);
                    rows.putIfAbsent(id, new TargetRow(id, name, locations.get(id)));
                }
            }
        }
        return rows.values().stream()
                .sorted(Comparator
                        .comparing((TargetRow row) -> row.location() == null)
                        .thenComparing(row -> row.location() == null || !row.location().exact())
                        .thenComparing(TargetRow::name, String.CASE_INSENSITIVE_ORDER))
                .limit(32)
                .toList();
    }

    private static String targetName(String name, UUID id) {
        if (name != null && !name.isBlank()) return name.trim();
        String value = id == null ? "?" : id.toString();
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private boolean targetTeleportable(PlayerLocationSnapshot location) {
        if (location == null || !location.exact() || activeDimension == null
                || !effective(WorldMapProfiledConfigOptions.MAP_TELEPORT_ALLOWED)) return false;
        String targetDimension = location.worldIdentity().dimensionKey();
        return targetDimension.isBlank()
                || targetDimension.equals(activeDimension.getDimId().identifier().toString());
    }

    private boolean teleportTarget(PlayerLocationSnapshot location) {
        if (!targetTeleportable(location)) return false;
        int y = Double.isFinite(location.y()) ? (int) Math.floor(location.y()) : Short.MAX_VALUE;
        XaeroMapActions.teleportMap(activeProcessor,
                (int) Math.floor(location.x()), y, (int) Math.floor(location.z()), null);
        return true;
    }
    private void toggleDimension() {
        if (activeProcessor == null || activeProcessor.getMapWorld() == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean shift = minecraft != null && minecraft.getWindow() != null
                && (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
        activeProcessor.getMapWorld().toggleDimension(!shift);
        activeProcessor.updateCaveStart();
        centered = false;
        dragging = false;
        contextOpen = false;
    }

    @SuppressWarnings("unchecked")
    private static boolean effective(ConfigOption<Boolean> option) {
        return (Boolean) WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(option);
    }

    private static <T> T effectiveValue(ConfigOption<T> option) {
        return WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(option);
    }

    private static void toggle(ConfigOption<Boolean> option) {
        WorldMapClientConfigUtils.tryTogglingCurrentProfileOption(option);
    }

    private static String tr(String key, String fallback) {
        String translated;
        try {
            translated = I18n.get(key, "");
        } catch (RuntimeException ignored) {
            translated = null;
        }
        if (translated == null || translated.equals(key) || translated.startsWith("Format error:")) {
            translated = fallback;
        }
        return LegacyTextUtil.stripLegacy(translated).replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void drawXaeroMapObject(AbstractTexture texture,
                                           double centerX, double centerY,
                                           double width, double height,
                                           double pivotX, double pivotY,
                                           float angle,
                                           double textureX, double textureY,
                                           double textureWidth, double textureHeight,
                                           int color) {
        Renderer2D.COLOR.textureQuadRotated(
                texture.getTextureView(), combatant.client.render.engine.postprocess.PostProcessManager.getSampler(),
                centerX, centerY, width, height, pivotX, pivotY, angle,
                textureX / 256.0, textureY / 256.0,
                (textureX + textureWidth) / 256.0, (textureY + textureHeight) / 256.0,
                color
        );
    }


    private static void rotatedRect(double centerX,
                                    double centerY,
                                    double width,
                                    double height,
                                    double angle,
                                    int color) {
        double halfW = width * 0.5;
        double halfH = height * 0.5;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double offset = 12.0;
        double cx = centerX + Math.cos(angle) * offset;
        double cy = centerY + Math.sin(angle) * offset;
        double[] points = new double[8];
        double[] corners = {-halfW, -halfH, -halfW, halfH, halfW, halfH, halfW, -halfH};
        for (int i = 0; i < corners.length; i += 2) {
            points[i] = cx + corners[i] * cos - corners[i + 1] * sin;
            points[i + 1] = cy + corners[i] * sin + corners[i + 1] * cos;
        }
        Renderer2D.COLOR.polygon(points, 4, color);
    }

    private void changeZoom(double factor, float anchorX, float anchorY, boolean aroundPointer) {
        destinationScale = clamp(destinationScale * Math.pow(1.2, factor), MIN_SCALE, MAX_SCALE);
        zoomFromScale = userScale;
        zoomAnimationStart = System.nanoTime();
        zoomAnchorX = anchorX;
        zoomAnchorY = anchorY;
        zoomAroundPointer = aroundPointer;
    }

    private void updateZoom() {
        if (userScale == destinationScale) return;
        double oldPixelsPerBlock = logicalPixelsPerBlock(userScale);
        MapPoint anchor = new MapViewport(
                new MapRect(areaX, areaY, areaWidth, areaHeight), centerX, centerZ, oldPixelsPerBlock
        ).unproject(zoomAnchorX, zoomAnchorY);
        double progress = clamp((System.nanoTime() - zoomAnimationStart) / (double) ZOOM_ANIMATION_NS, 0.0, 1.0);
        double eased = 0.5 - Math.cos(progress * Math.PI) * 0.5;
        double next = zoomFromScale + (destinationScale - zoomFromScale) * eased;
        double nextPixelsPerBlock = logicalPixelsPerBlock(next);
        if (zoomAroundPointer && next > userScale) {
            centerX = anchor.x() - (zoomAnchorX - (areaX + areaWidth * 0.5)) / nextPixelsPerBlock;
            centerZ = anchor.z() - (zoomAnchorY - (areaY + areaHeight * 0.5)) / nextPixelsPerBlock;
        }
        userScale = progress >= 1.0 ? destinationScale : next;
    }

    private void updateDrag(float mouseX, float mouseY) {
        if (!dragging) return;
        double pixelsPerBlock = logicalPixelsPerBlock(userScale);
        centerX -= (mouseX - dragX) / pixelsPerBlock;
        centerZ -= (mouseY - dragY) / pixelsPerBlock;
        dragX = mouseX;
        dragY = mouseY;
    }

    private void updatePointer(MapViewport currentViewport, float mouseX, float mouseY) {
        MapPoint point = currentViewport.unproject(mouseX, mouseY);
        pointerBlockX = (int) Math.floor(point.x());
        pointerBlockZ = (int) Math.floor(point.z());
        if (rightSelecting && rightClickElement == null && rightClickLocationMarker == null) {
            selectionEndX = pointerBlockX >> 4;
            selectionEndZ = pointerBlockZ >> 4;
        }
    }

    private int sampleHeight(MapProcessor processor, int blockX, int blockZ, int lod) {
        int shift = 9 + lod;
        LeveledRegion<?> region = processor.getLeveledRegion(
                processor.getCurrentCaveLayer(), blockX >> shift, blockZ >> shift, lod);
        if (region == null || !region.hasTextures()) return Short.MAX_VALUE;
        int textureX = blockX >> (6 + lod) & 7;
        int textureZ = blockZ >> (6 + lod) & 7;
        RegionTexture<?> texture = region.getTexture(textureX, textureZ);
        if (texture == null) return Short.MAX_VALUE;
        int pixelX = blockX >> lod & 63;
        int pixelZ = blockZ >> lod & 63;
        int bottom = texture.getHeight(pixelX, pixelZ);
        int top = texture.getTopHeight(pixelX, pixelZ);
        if (bottom != top && top != Short.MAX_VALUE) return top;
        if (bottom != top && effective(WorldMapProfiledConfigOptions.DETECT_AMBIGUOUS_Y)) {
            return Short.MAX_VALUE;
        }
        return bottom;
    }

    private void drawSelection(MapViewport currentViewport) {
        if ((!rightSelecting && !contextOpen) || rightClickElement != null || rightClickLocationMarker != null) return;
        int left = Math.min(selectionStartX, selectionEndX) << 4;
        int right = (Math.max(selectionStartX, selectionEndX) + 1) << 4;
        int top = Math.min(selectionStartZ, selectionEndZ) << 4;
        int bottom = (Math.max(selectionStartZ, selectionEndZ) + 1) << 4;
        MapScreenPoint a = currentViewport.project(left, top);
        MapScreenPoint b = currentViewport.project(right, bottom);
        float x = (float) Math.min(a.x(), b.x());
        float y = (float) Math.min(a.y(), b.y());
        float width = (float) Math.abs(b.x() - a.x());
        float height = (float) Math.abs(b.y() - a.y());
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.quad(x, y, width, height, 0x284FD6BE);
        float stroke = 1.5f;
        renderer.quad(x, y, width, stroke, 0xC855D6BE);
        renderer.quad(x, y + height - stroke, width, stroke, 0xC855D6BE);
        renderer.quad(x, y, stroke, height, 0xC855D6BE);
        renderer.quad(x + width - stroke, y, stroke, height, 0xC855D6BE);
    }

    private void centerOnPlayer(MapProcessor processor, MapDimension dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        double divisor = 1.0;
        if (processor.getWorldDimensionTypeRegistry() != null) {
            divisor = dimension.calculateDimDiv(
                    processor.getWorldDimensionTypeRegistry(),
                    minecraft.player.level().dimensionType()
            );
        }
        if (!Double.isFinite(divisor) || divisor == 0.0) divisor = 1.0;
        centerX = minecraft.player.getX() / divisor;
        centerZ = minecraft.player.getZ() / divisor;
        centered = true;
    }

    private int selectXaeroLod() {
        if (userScale >= 1.0) return 0;
        double reversedScale = 1.0 / userScale;
        return Math.min((int) Math.floor(Math.log(reversedScale) / Math.log(2.0)), MAX_LOD);
    }

    private double logicalPixelsPerBlock(double scale) {
        return scale;
    }

    private String sourceId(MapProcessor processor, MapDimension dimension) {
        return "xaero/" + safe(processor.getCurrentWorldId())
                + '/' + safe(dimension.getDimId().identifier().toString())
                + '/' + safe(dimension.getCurrentMultiworld())
                + "/cave-" + processor.getCurrentCaveLayer();
    }

    private boolean contains(float mouseX, float mouseY) {
        return inside(mouseX, mouseY, areaX, areaY, areaWidth, areaHeight);
    }

    private static boolean inside(double mouseX,
                                  double mouseY,
                                  double x,
                                  double y,
                                  double width,
                                  double height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static boolean usable(RegionTexture<?> texture) {
        if (texture == null) return false;
        GpuTextureAndView gpu = texture.getGlColorTexture();
        return gpu != null && gpu.texture != null && gpu.view != null;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "default" : value.replace(':', '_').replace('/', '_');
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    enum Action {
        SETTINGS,
        RECENTER,
        CAVE,
        DIMENSION,
        WAYPOINTS,
        PLAYERS,
        RADAR_LIST,
        RADAR,
        CLAIMS,
        CONTROLS,
        ZOOM_IN,
        ZOOM_OUT
    }

    enum ContextAction {
        NONE,
        EDIT,
        TELEPORT_ELEMENT,
        SHARE_ELEMENT,
        TOGGLE_DISABLED,
        TOGGLE_TEMPORARY,
        DELETE,
        CREATE,
        CREATE_TEMPORARY,
        TELEPORT_MAP,
        SHARE_LOCATION,
        COPY_COORDINATES,
        COPY_CHUNK,
        CENTER_LOCATION,
        CENTER_ELEMENT,
        TOGGLE_TARGET,
        WAYPOINTS,
        SETTINGS
    }

    enum Drawer {
        NONE,
        WAYPOINTS,
        PLAYERS,
        RADAR
    }

    record UiButton(Action action, String icon, float x, float y, float size,
                    String tooltip, boolean active, boolean enabled) {
        boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, size, size);
        }
    }

    record MenuEntry(String label, String icon, boolean enabled, ContextAction action) {
    }

    record ElementHit(XaeroMapElements.Element element, float x, float y,
                              float width, float height) {
        boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    record TargetRow(UUID playerUuid, String name, PlayerLocationSnapshot location) {
    }

    record TargetHit(TargetRow target, float x, float y, float width, float height,
                     boolean teleportable) {
        boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }

    record LocationMarkerEntry(MapPlayerMarker marker, PlayerLocationSnapshot location) {
    }

    record Frame(boolean ready, String status, int texturesRendered) {
        static Frame waiting(String status) {
            return new Frame(false, status, 0);
        }
    }

    private record ResolvedTile(MapTileCoordinate sourceCoordinate,
                                LeveledRegion<?> region,
                                LeveledRegion<?> sourceRegion,
                                RegionTexture<?> texture,
                                MapTileUvRect textureUv) {
    }

    private record LoadingRegion(double centerX, double centerZ, int lod) {
    }

    private record VisibleLeaves(Set<Long> regions, int minX, int minZ, int maxX, int maxZ) {
    }
}
