/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.map.MapGridSpec;
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
import combatant.client.util.logging.DebugLog;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.graphics.GpuTextureAndView;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.single.SingleConfigManager;
import xaero.hud.minimap.common.config.MinimapConfigConstants;
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
    private XaeroMapSettingsPanel settings;
    private long settingsRetryAfterNanos;
    private final ArrayList<UiButton> uiButtons = new ArrayList<>();
    private final ArrayList<MenuEntry> contextEntries = new ArrayList<>();
    private final ArrayList<ElementHit> drawerHits = new ArrayList<>();

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
    private boolean prevLoadingLeaves;
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
    private boolean contextOpen;
    private boolean deleteArmed;
    private Drawer drawer = Drawer.NONE;
    private boolean controlsOpen;
    private WaypointDraft waypointDraft;

    Frame render(float x, float y, float width, float height, float mouseX, float mouseY) {
        areaX = x;
        areaY = y;
        areaWidth = width;
        areaHeight = height;

        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return Frame.waiting("Preparing World Map...");
        MapProcessor processor = session.getMapProcessor();
        if (processor == null || !processor.isMapWorldUsable()) return Frame.waiting("Preparing World Map...");
        MapWorld world = processor.getMapWorld();
        MapDimension dimension = world == null ? null : world.getFutureDimension();
        if (dimension == null && world != null) dimension = world.getCurrentDimension();
        if (dimension == null) return Frame.waiting("Preparing World Map...");
        activeProcessor = processor;
        activeDimension = dimension;

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
            return Frame.waiting("Preparing World Map...");
        }

        int lod = selectXaeroLod();
        List<MapTerrainTile> terrain;
        synchronized (processor.renderThreadPauseSync) {
            if (processor.isRenderingPaused()) {
                drawMapUi(processor, dimension, mouseX, mouseY);
                return Frame.waiting("Preparing World Map...");
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
        elementSnapshot = elements.collect(processor, dimension, userScale);
        hoveredElement = elements.render(elementSnapshot, viewport, mouseX, mouseY);
        drawPlayerArrow(processor, dimension, viewport);
        drawMapUi(processor, dimension, mouseX, mouseY);
        return result.terrainTilesDrawn() == 0
                ? Frame.waiting("Preparing World Map...")
                : new Frame(true, "", result.terrainTilesDrawn());
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (waypointDraft != null && waypointDraft.mousePressed(mouseX, mouseY, button)) return true;
        if (settings != null && settings.mousePressed(mouseX, mouseY, button)) return true;
        if (contextOpen && clickContext(mouseX, mouseY, button)) return true;
        if (clickUiButton(mouseX, mouseY, button)) return true;
        if (clickDrawer(mouseX, mouseY, button)) return true;
        if (!contains(mouseX, mouseY)) return false;

        contextOpen = false;
        deleteArmed = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightSelecting = true;
            rightClickElement = hoveredElement;
            contextBlockX = pointerBlockX;
            contextBlockY = pointerBlockY;
            contextBlockZ = pointerBlockZ;
            selectionStartX = selectionEndX = pointerBlockX >> 4;
            selectionStartZ = selectionEndZ = pointerBlockZ >> 4;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        dragging = true;
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
        if (settings != null) settings.mouseReleased(mouseX, mouseY, button);
        if (waypointDraft != null) waypointDraft.mouseReleased(mouseX, mouseY, button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && rightSelecting) {
            rightSelecting = false;
            contextX = clamp(mouseX, areaX + 8.0f, areaX + areaWidth - 246.0f);
            contextY = clamp(mouseY, areaY + 8.0f, areaY + areaHeight - 280.0f);
            contextOpen = true;
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
        if (waypointDraft != null && waypointDraft.keyPressed(keyCode, modifiers)) return true;
        if (settings != null && settings.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contextOpen || drawer != Drawer.NONE || controlsOpen) {
                contextOpen = false;
                drawer = Drawer.NONE;
                controlsOpen = false;
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
        if (waypointDraft != null && waypointDraft.charTyped(chr)) return true;
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

        for (MapTileCoordinate requested : visibleTileSelector.select(viewport, requestedLod, 0)) {
            ResolvedTile tile = resolveTile(processor, requested);
            if (tile == null) continue;
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
                    new MapTileGpuCopy(gpu.texture, 0, 0, TILE_RESOLUTION, TILE_RESOLUTION),
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

    private ResolvedTile resolveTile(MapProcessor processor, MapTileCoordinate requested) {
        int lod = requested.lod();
        int regionX = Math.floorDiv(requested.x(), TILES_PER_REGION_SIDE);
        int regionZ = Math.floorDiv(requested.z(), TILES_PER_REGION_SIDE);
        MapRegion leaf = findExistingLeaf(processor, requested);
        if (leaf == null) return null;
        queueLeafIfNeeded(processor, leaf, lod);

        LeveledRegion<?> region = processor.getLeveledRegion(
                processor.getCurrentCaveLayer(), regionX, regionZ, lod);
        if (region == null) return null;
        if (region.loadingAnimation()) addLoadingRegion(requested);

        updateBranch(processor, region, lod, regionX, regionZ);
        int localX = Math.floorMod(requested.x(), TILES_PER_REGION_SIDE);
        int localZ = Math.floorMod(requested.z(), TILES_PER_REGION_SIDE);
        RegionTexture<?> exact = region.hasTextures() ? region.getTexture(localX, localZ) : null;
        if (usable(exact)) {
            return new ResolvedTile(requested, region, region, exact, MapTileUvRect.FULL);
        }

        LeveledRegion<?> root = region.getRootRegion();
        if (root == null || root == region) return null;
        if (root instanceof BranchLeveledRegion branch && !branch.isLoaded()) {
            queueBranch(branch);
            waitingForBranchCache[0] = true;
            return null;
        }
        if (!root.hasTextures()) return null;

        int subdivisions = 1 << (MAX_LOD - lod);
        int rootTileX = Math.floorDiv(requested.x(), subdivisions);
        int rootTileZ = Math.floorDiv(requested.z(), subdivisions);
        RegionTexture<?> fallback = root.getTexture(
                Math.floorMod(rootTileX, TILES_PER_REGION_SIDE),
                Math.floorMod(rootTileZ, TILES_PER_REGION_SIDE)
        );
        if (!usable(fallback)) return null;
        int insideX = Math.floorMod(requested.x(), subdivisions);
        int insideZ = Math.floorMod(requested.z(), subdivisions);
        double unit = 1.0 / subdivisions;
        return new ResolvedTile(
                new MapTileCoordinate(rootTileX, rootTileZ, MAX_LOD),
                region,
                root,
                fallback,
                new MapTileUvRect(
                        insideX * unit,
                        insideZ * unit,
                        (insideX + 1) * unit,
                        (insideZ + 1) * unit
                )
        );
    }

    private void updateBranch(MapProcessor processor,
                              LeveledRegion<?> region,
                              int lod,
                              int regionX,
                              int regionZ) {
        if (!(region instanceof BranchLeveledRegion branch)
                || processor.isUploadingPaused()
                || !updatedRegions.add(region)) return;
        int side = 1 << lod;
        int minLeafX = regionX * side;
        int minLeafZ = regionZ * side;
        branch.checkForUpdates(
                processor,
                prevWaitingForBranchCache,
                waitingForBranchCache,
                branchRequests,
                lod,
                minLeafX,
                minLeafZ,
                minLeafX + side - 1,
                minLeafZ + side - 1
        );

        if ((lod != 0 && !prevWaitingForBranchCache || lod == 0 && !prevLoadingLeaves)
                && lastFrameRenderedRootTextures) {
            LeveledRegion<?> root = region.getRootRegion();
            if (root instanceof BranchLeveledRegion rootBranch && rootBranch != branch && updatedRegions.add(root)) {
                rootBranch.checkForUpdates(
                        processor,
                        prevWaitingForBranchCache,
                        waitingForBranchCache,
                        branchRequests,
                        lod,
                        minLeafX,
                        minLeafZ,
                        minLeafX + side - 1,
                        minLeafZ + side - 1
                );
            }
        }
    }

    private MapRegion findExistingLeaf(MapProcessor processor, MapTileCoordinate requested) {
        long worldX = (long) requested.x() * requested.blockSpan(TILE_RESOLUTION);
        long worldZ = (long) requested.z() * requested.blockSpan(TILE_RESOLUTION);
        int leafRegionX = (int) Math.floorDiv(worldX, REGION_BLOCKS);
        int leafRegionZ = (int) Math.floorDiv(worldZ, REGION_BLOCKS);
        int caveLayer = processor.getCurrentCaveLayer();
        MapRegion leaf = processor.getLeafMapRegion(caveLayer, leafRegionX, leafRegionZ, false);
        if (leaf == null && processor.regionExists(caveLayer, leafRegionX, leafRegionZ)) {
            leaf = processor.getLeafMapRegion(caveLayer, leafRegionX, leafRegionZ, true);
        }
        return leaf;
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
        return new MapOverlaySnapshot(List.of(), List.of(), List.of());
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
        double px = clamp(point.x(), areaX + 18.0, areaX + areaWidth - 18.0);
        double py = clamp(point.y(), areaY + 18.0, areaY + areaHeight - 18.0);
        float yaw = minecraft.player.getYRot();
        if (px != point.x() || py != point.y()) {
            yaw = (float) Math.toDegrees(Math.atan2(point.y() - py, point.x() - px)) - 90.0f;
        }
        int color = arrowColor();
        drawPlayerMarker(px, py + 1.2, 14.0, 6.0, yaw, 0xD9000000);
        drawPlayerMarker(px, py, 13.0, 5.0, yaw, color);
    }

    private void drawFootprints(MapProcessor processor, MapDimension dimension, MapViewport viewport) {
        if (!effective(WorldMapProfiledConfigOptions.FOOTSTEPS)) return;
        ArrayList<Double[]> footprints = processor.getFootprints();
        if (footprints == null || footprints.isEmpty()) return;
        double divisor = playerDimensionDivisor(processor, dimension);
        synchronized (footprints) {
            int start = Math.max(0, footprints.size() - 1024);
            for (int i = start; i < footprints.size(); i++) {
                Double[] coordinates = footprints.get(i);
                if (coordinates == null || coordinates.length < 2) continue;
                MapScreenPoint point = viewport.project(coordinates[0] / divisor, coordinates[1] / divisor);
                if (viewport.screenBounds().contains(point.x(), point.y())) {
                    Renderer2D.COLOR.circle(point.x(), point.y(), 1.25, 0xFFFF1A1A);
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
        int option = effectiveValue(WorldMapProfiledConfigOptions.ARROW_COLOR);
        if (option == -2 && SupportMods.minimap()) {
            float[] color = SupportMods.xaeroMinimap.getArrowColor();
            if (color != null && color.length >= 3) {
                return 0xFF000000
                        | Math.round(color[0] * 255.0f) << 16
                        | Math.round(color[1] * 255.0f) << 8
                        | Math.round(color[2] * 255.0f);
            }
        }
        if (option >= 0 && option < MinimapConfigConstants.ARROW_COLORS.length) {
            float[] color = MinimapConfigConstants.ARROW_COLORS[option];
            return 0xFF000000
                    | Math.round(color[0] * 255.0f) << 16
                    | Math.round(color[1] * 255.0f) << 8
                    | Math.round(color[2] * 255.0f);
        }
        return 0xFFF5F8FC;
    }

    private void toggleSettings() {
        if (settings == null) {
            long now = System.nanoTime();
            if (now < settingsRetryAfterNanos) return;
            try {
                settings = new XaeroMapSettingsPanel(() -> activeProcessor, () -> activeDimension);
            } catch (RuntimeException error) {
                settingsRetryAfterNanos = now + 1_000_000_000L;
                DebugLog.warnOnce(
                        "clickgui-map-settings-panel",
                        "Xaero World Map settings panel is temporarily unavailable; the map surface will remain active",
                        error
                );
                return;
            } catch (LinkageError error) {
                settingsRetryAfterNanos = Long.MAX_VALUE;
                DebugLog.warnOnce(
                        "clickgui-map-settings-panel-linkage",
                        "Xaero World Map settings panel is incompatible; the map surface will remain active",
                        error
                );
                return;
            }
        }
        settings.toggle();
    }

    private void drawMapUi(MapProcessor processor, MapDimension dimension, float mouseX, float mouseY) {
        uiButtons.clear();
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float scale = (float) Math.max(0.9, chromeScale());
        float size = 30.0f * scale;
        float gap = 7.0f * scale;
        float inset = 14.0f * scale;

        addUiButton(Action.SETTINGS, "settings-2", areaX + inset, areaY + inset, size,
                tr("gui.xaero_box_open_settings", "Settings"), settings != null && settings.isOpen());
        addUiButton(Action.RECENTER, "locate-fixed", areaX + inset + size + gap, areaY + inset, size,
                "Recenter", false);

        float leftBottom = areaY + areaHeight - inset - size;
        addUiButton(Action.CAVE, "layers", areaX + inset, leftBottom, size,
                tr("gui.xaero_box_cave_mode", "Cave mode"), dimension.getCaveModeType() != 0);
        addUiButton(Action.DIMENSION, "route", areaX + inset, leftBottom - size - gap, size,
                tr("gui.xaero_dimension_toggle_button", "Switch dimension"),
                processor.getMapWorld().isUsingCustomDimension());

        float right = areaX + areaWidth - inset - size;
        float cursor = areaY + areaHeight - inset - size;
        if (SupportMods.minimap() && effective(WorldMapProfiledConfigOptions.WAYPOINTS)) {
            addUiButton(Action.WAYPOINTS, "map-pinned", right, cursor, size,
                    tr("gui.xaero_box_open_waypoints", "Waypoints"), drawer == Drawer.WAYPOINTS);
            cursor -= size + gap;
        }
        addUiButton(Action.PLAYERS, "users-round", right, cursor, size,
                tr("gui.xaero_box_open_players", "Players"), drawer == Drawer.PLAYERS);
        cursor -= size + gap;
        if (SupportMods.minimap()) {
            addUiButton(Action.RADAR, "radar", right, cursor, size,
                    tr("gui.xaero_box_minimap_radar", "Minimap radar"),
                    effective(WorldMapProfiledConfigOptions.MINIMAP_RADAR));
            cursor -= size + gap;
        }
        if (SupportMods.pac()) {
            addUiButton(Action.CLAIMS, "land-plot", right, cursor, size,
                    tr("gui.xaero_box_pac_displaying_claims", "Claims"),
                    effective(WorldMapProfiledConfigOptions.OPAC_CLAIMS));
            cursor -= size + gap;
        }
        addUiButton(Action.EXPORT, "map", right, cursor, size,
                tr("gui.xaero_box_export", "Export"), false);
        cursor -= size + gap;
        addUiButton(Action.CONTROLS, "circle-question-mark", right, cursor, size,
                tr("gui.xaero_box_controls", "Controls"), controlsOpen);
        cursor -= size + gap;
        if (effective(WorldMapProfiledConfigOptions.ZOOM_BUTTONS)) {
            addUiButton(Action.ZOOM_OUT, "zoom-out", right, cursor, size,
                    tr("gui.xaero_box_zoom_out", "Zoom out"), false);
            cursor -= size + gap;
            addUiButton(Action.ZOOM_IN, "zoom-in", right, cursor, size,
                    tr("gui.xaero_box_zoom_in", "Zoom in"), false);
        }

        for (UiButton button : uiButtons) drawUiButton(button, mouseX, mouseY, palette);
        drawCompass(palette, scale);
        drawCoordinates(palette, dimension, scale);
        drawZoom(palette, scale);
        drawDrawer(mouseX, mouseY, palette, scale);
        if (controlsOpen) drawControls(palette, scale);
        if (contextOpen) drawContext(mouseX, mouseY, palette, scale);
        if (waypointDraft != null) waypointDraft.render(mouseX, mouseY, palette, scale);
        drawTooltip(mouseX, mouseY, palette, scale);
        if (settings != null) settings.render(areaX, areaY, areaWidth, areaHeight, mouseX, mouseY);
    }

    private void drawZoom(SettingsGuiPalette palette, float scale) {
        String zoom = Math.round(destinationScale * 1000.0) / 1000.0 + "x";
        float fontSize = 10.0f * scale;
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), zoom, fontSize);
        float textX = areaX + (areaWidth - textWidth) * 0.5f;
        float textY = areaY + areaHeight - 20.0f * scale;
        Renderer2D.COLOR.roundedRect(textX - 6.0f * scale, textY - 3.0f * scale,
                textWidth + 12.0f * scale, fontSize + 6.0f * scale, 5.0f * scale, palette.panelBgLeft());
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getOnestMedium(), zoom, textX, textY, fontSize, palette.panelText(), false);
    }

    private void addUiButton(Action action, String icon, float x, float y, float size,
                             String tooltip, boolean active) {
        uiButtons.add(new UiButton(action, icon, x, y, size, tooltip, active));
    }

    private static void drawUiButton(UiButton button, float mouseX, float mouseY,
                                     SettingsGuiPalette palette) {
        boolean hover = button.contains(mouseX, mouseY);
        int background = button.active()
                ? palette.panelPillActive()
                : hover ? palette.controlSurfaceHover() : palette.controlSurface();
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(button.x(), button.y(), button.size(), button.size(),
                button.size() * 0.25f, 7.0f, 0.05f, palette.panelShadow());
        renderer.roundedRect(button.x(), button.y(), button.size(), button.size(),
                button.size() * 0.25f, background);
        renderer.roundedRectStroke(button.x(), button.y(), button.size(), button.size(),
                button.size() * 0.25f, 0.7f, palette.panelStroke());
        float iconSize = button.size() * 0.52f;
        renderer.svg(button.icon(), button.x() + (button.size() - iconSize) * 0.5f,
                button.y() + (button.size() - iconSize) * 0.5f, iconSize, iconSize,
                SvgRenderOptions.overrideColor(button.active() || hover
                        ? palette.panelText() : palette.panelMuted()));
    }

    private void drawTooltip(float mouseX, float mouseY, SettingsGuiPalette palette, float scale) {
        if ((settings != null && settings.isOpen()) || contextOpen || waypointDraft != null) return;
        UiButton hovered = null;
        for (UiButton button : uiButtons) {
            if (button.contains(mouseX, mouseY)) {
                hovered = button;
                break;
            }
        }
        if (hovered == null) return;
        float fontSize = 10.0f * scale;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), hovered.tooltip(), fontSize);
        float x = clamp(mouseX + 10.0f * scale, areaX + 6.0f, areaX + areaWidth - width - 16.0f * scale);
        float y = clamp(mouseY + 12.0f * scale, areaY + 6.0f, areaY + areaHeight - 26.0f * scale);
        Renderer2D.COLOR.roundedRect(x, y, width + 12.0f * scale, 20.0f * scale,
                5.0f * scale, palette.panelBgRight());
        Renderer2D.COLOR.roundedRectStroke(x, y, width + 12.0f * scale, 20.0f * scale,
                5.0f * scale, 0.6f, palette.panelStroke());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), hovered.tooltip(),
                x + 6.0f * scale, y + 5.0f * scale, fontSize, palette.panelText(), false);
    }

    private void drawCompass(SettingsGuiPalette palette, float scale) {
        String north = tr("gui.xaero_compass_north", "N");
        String east = tr("gui.xaero_compass_east", "E");
        String south = tr("gui.xaero_compass_south", "S");
        String west = tr("gui.xaero_compass_west", "W");
        float centerX = areaX + areaWidth * 0.5f;
        float centerY = areaY + areaHeight * 0.5f;
        drawCardinal(north, centerX, areaY + 52.0f * scale, palette, scale);
        drawCardinal(south, centerX, areaY + areaHeight - 40.0f * scale, palette, scale);
        drawCardinal(west, areaX + 44.0f * scale, centerY, palette, scale);
        drawCardinal(east, areaX + areaWidth - 44.0f * scale, centerY, palette, scale);
    }

    private static void drawCardinal(String text, float centerX, float centerY,
                                     SettingsGuiPalette palette, float scale) {
        float size = 10.0f * scale;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestBold(), text, size);
        Renderer2D.COLOR.roundedRect(centerX - width * 0.5f - 5.0f * scale,
                centerY - 3.0f * scale, width + 10.0f * scale, size + 6.0f * scale,
                5.0f * scale, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), text,
                centerX - width * 0.5f, centerY, size, palette.panelText(), false);
    }

    private void drawCoordinates(SettingsGuiPalette palette, MapDimension dimension, float scale) {
        if (!effective(WorldMapProfiledConfigOptions.COORDINATES)) return;
        if (SupportMods.minimap() && SupportMods.xaeroMinimap.hidingWaypointCoordinates()) return;
        String coordinates = pointerBlockY == Short.MAX_VALUE
                ? "X: " + pointerBlockX + "  Z: " + pointerBlockZ
                : "X: " + pointerBlockX + "  Y: " + pointerBlockY + "  Z: " + pointerBlockZ;
        String dimensionName = activeProcessor == null ? "" : activeProcessor.getDimensionName(dimension.getDimId());
        if (dimensionName != null && !dimensionName.isBlank()) coordinates += "  ·  " + dimensionName;
        float size = 10.0f * scale;
        float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), coordinates, size);
        float x = areaX + (areaWidth - width) * 0.5f;
        float y = areaY + 15.0f * scale;
        Renderer2D.COLOR.roundedRect(x - 7.0f * scale, y - 4.0f * scale,
                width + 14.0f * scale, size + 8.0f * scale, 6.0f * scale, palette.panelBgLeft());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), coordinates,
                x, y, size, palette.panelText(), false);
    }

    private void drawDrawer(float mouseX, float mouseY, SettingsGuiPalette palette, float scale) {
        drawerHits.clear();
        if (drawer == Drawer.NONE) return;
        float width = 250.0f * scale;
        float x = areaX + areaWidth - width - 58.0f * scale;
        float y = areaY + 54.0f * scale;
        float rowHeight = 34.0f * scale;
        List<XaeroMapElements.Element> rows = elementSnapshot.elements().stream()
                .filter(element -> drawer == Drawer.WAYPOINTS
                        ? element.kind() == XaeroMapElements.Kind.WAYPOINT
                        : element.kind() != XaeroMapElements.Kind.WAYPOINT)
                .limit(14)
                .toList();
        float height = 42.0f * scale + Math.max(1, rows.size()) * rowHeight + 8.0f * scale;
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 9.0f * scale, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 9.0f * scale,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 9.0f * scale, 0.7f, palette.panelStroke());
        String title = drawer == Drawer.WAYPOINTS
                ? tr("gui.xaero_box_open_waypoints", "Waypoints")
                : "Players & Radar";
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                x + 12.0f * scale, y + 13.0f * scale, 12.0f * scale, palette.panelText(), false);
        renderer.quad(x + 10.0f * scale, y + 38.0f * scale, width - 20.0f * scale,
                1.0f, palette.panelDivider());
        float rowY = y + 43.0f * scale;
        if (rows.isEmpty()) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Nothing to display",
                    x + 12.0f * scale, rowY + 7.0f * scale, 10.0f * scale, palette.panelMuted(), false);
        }
        for (XaeroMapElements.Element element : rows) {
            boolean hover = inside(mouseX, mouseY, x + 6.0f * scale, rowY,
                    width - 12.0f * scale, rowHeight - 3.0f * scale);
            if (hover) renderer.roundedRect(x + 6.0f * scale, rowY,
                    width - 12.0f * scale, rowHeight - 3.0f * scale,
                    5.0f * scale, palette.controlSurfaceHover());
            String icon = switch (element.kind()) {
                case WAYPOINT -> "map-pin";
                case PLAYER -> "users-round";
                case ENTITY -> "radar";
            };
            renderer.svg(icon, x + 12.0f * scale, rowY + 8.0f * scale,
                    15.0f * scale, 15.0f * scale, SvgRenderOptions.overrideColor(element.color()));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), element.plainName(),
                    x + 36.0f * scale, rowY + 7.0f * scale, 10.0f * scale,
                    element.disabled() ? palette.panelMuted() : palette.panelText(), false);
            String location = (int) element.worldX() + ", " + (int) element.worldZ();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), location,
                    x + 36.0f * scale, rowY + 19.0f * scale, 8.0f * scale,
                    palette.panelMuted(), false);
            drawerHits.add(new ElementHit(element, x + 6.0f * scale, rowY,
                    width - 12.0f * scale, rowHeight - 3.0f * scale));
            rowY += rowHeight;
        }
    }

    private void drawControls(SettingsGuiPalette palette, float scale) {
        String[] lines = {
                "Drag — pan", "Wheel — zoom", "Right click — select/actions",
                "R — recenter", "E — edit hovered waypoint", "T — teleport to hovered element",
                "Esc — close active map surface"
        };
        float width = 276.0f * scale;
        float height = (44.0f + lines.length * 20.0f) * scale;
        float x = areaX + areaWidth - width - 58.0f * scale;
        float y = Math.max(areaY + 54.0f * scale, areaY + areaHeight - height - 54.0f * scale);
        Renderer2D.COLOR.roundedRectGradient(x, y, width, height, 9.0f * scale,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        Renderer2D.COLOR.roundedRectStroke(x, y, width, height, 9.0f * scale,
                0.7f, palette.panelStroke());
        ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(),
                tr("gui.xaero_box_controls", "Controls"), x + 12.0f * scale,
                y + 13.0f * scale, 12.0f * scale, palette.panelText(), false);
        float lineY = y + 40.0f * scale;
        for (String line : lines) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), line,
                    x + 12.0f * scale, lineY, 9.5f * scale, palette.panelMuted(), false);
            lineY += 20.0f * scale;
        }
    }

    private void drawContext(float mouseX, float mouseY, SettingsGuiPalette palette, float scale) {
        rebuildContextEntries();
        float width = 238.0f * scale;
        float rowHeight = 28.0f * scale;
        float height = 12.0f * scale + contextEntries.size() * rowHeight;
        float x = clamp(contextX, areaX + 8.0f, areaX + areaWidth - width - 8.0f);
        float y = clamp(contextY, areaY + 8.0f, areaY + areaHeight - height - 8.0f);
        contextX = x;
        contextY = y;
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.roundedRectSoftShadow(x, y, width, height, 8.0f * scale, 10.0f,
                0.05f, palette.panelShadow());
        renderer.roundedRectGradient(x, y, width, height, 8.0f * scale,
                palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
        renderer.roundedRectStroke(x, y, width, height, 8.0f * scale, 0.7f, palette.panelStroke());
        float rowY = y + 6.0f * scale;
        for (MenuEntry entry : contextEntries) {
            boolean hover = entry.enabled() && inside(mouseX, mouseY, x + 5.0f * scale, rowY,
                    width - 10.0f * scale, rowHeight - 2.0f * scale);
            if (hover) renderer.roundedRect(x + 5.0f * scale, rowY,
                    width - 10.0f * scale, rowHeight - 2.0f * scale,
                    5.0f * scale, palette.controlSurfaceHover());
            if (entry.icon() != null) {
                renderer.svg(entry.icon(), x + 11.0f * scale, rowY + 6.0f * scale,
                        14.0f * scale, 14.0f * scale,
                        SvgRenderOptions.overrideColor(entry.enabled() ? palette.panelText() : palette.panelMuted()));
            }
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), entry.label(),
                    x + 34.0f * scale, rowY + 7.0f * scale, 9.5f * scale,
                    entry.enabled() ? palette.panelText() : palette.panelMuted(), false);
            rowY += rowHeight;
        }
    }

    private void rebuildContextEntries() {
        contextEntries.clear();
        XaeroMapElements.Element element = rightClickElement;
        if (element != null && element.handle() instanceof Waypoint waypoint) {
            contextEntries.add(new MenuEntry(element.plainName(), "map-pin", false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_edit", "Edit"),
                    "pencil", true, ContextAction.EDIT));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_teleport", "Teleport"),
                    "navigation", true, ContextAction.TELEPORT_ELEMENT));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_waypoint_share", "Share"),
                    "share-2", true, ContextAction.SHARE_ELEMENT));
            contextEntries.add(new MenuEntry(waypoint.isDisabled() ? "Enable" : "Disable",
                    "eye", true, ContextAction.TOGGLE_DISABLED));
            contextEntries.add(new MenuEntry(waypoint.isTemporary() ? "Make permanent" : "Make temporary",
                    "clock-3", true, ContextAction.TOGGLE_TEMPORARY));
            contextEntries.add(new MenuEntry(deleteArmed ? "Click again to delete" : "Delete",
                    "trash-2", true, ContextAction.DELETE));
        } else if (element != null && element.handle() instanceof PlayerTrackerMapElement<?>) {
            contextEntries.add(new MenuEntry(element.plainName(), "users-round", false, ContextAction.NONE));
            contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_player_teleport", "Teleport to player"),
                    "navigation", true, ContextAction.TELEPORT_ELEMENT));
        } else {
            boolean coordinates = effective(WorldMapProfiledConfigOptions.COORDINATES);
            String chunk = selectionStartX == selectionEndX && selectionStartZ == selectionEndZ
                    ? "Chunk " + selectionStartX + ", " + selectionStartZ
                    : "Chunks " + Math.min(selectionStartX, selectionEndX) + ", "
                    + Math.min(selectionStartZ, selectionEndZ) + " → "
                    + Math.max(selectionStartX, selectionEndX) + ", " + Math.max(selectionStartZ, selectionEndZ);
            contextEntries.add(new MenuEntry(chunk, "land-plot", false, ContextAction.NONE));
            if (coordinates) {
                String xyz = contextBlockY == Short.MAX_VALUE
                        ? "X " + contextBlockX + "  Z " + contextBlockZ
                        : "X " + contextBlockX + "  Y " + contextBlockY + "  Z " + contextBlockZ;
                contextEntries.add(new MenuEntry(xyz, "crosshair", false, ContextAction.NONE));
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
        contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_box_map_export", "Export selection"),
                "map", true, ContextAction.EXPORT));
        contextEntries.add(new MenuEntry(tr("gui.xaero_right_click_box_map_settings", "Settings"),
                "settings-2", true, ContextAction.SETTINGS));
    }

    private boolean clickUiButton(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (UiButton uiButton : uiButtons) {
            if (!uiButton.contains(mouseX, mouseY)) continue;
            runUiAction(uiButton.action());
            return true;
        }
        return false;
    }

    private void runUiAction(Action action) {
        switch (action) {
            case SETTINGS -> toggleSettings();
            case RECENTER -> recenter();
            case CAVE -> cycleCaveMode();
            case DIMENSION -> toggleDimension();
            case WAYPOINTS -> drawer = drawer == Drawer.WAYPOINTS ? Drawer.NONE : Drawer.WAYPOINTS;
            case PLAYERS -> drawer = drawer == Drawer.PLAYERS ? Drawer.NONE : Drawer.PLAYERS;
            case RADAR -> toggle(WorldMapProfiledConfigOptions.MINIMAP_RADAR);
            case CLAIMS -> toggle(WorldMapProfiledConfigOptions.OPAC_CLAIMS);
            case EXPORT -> exportSelection();
            case CONTROLS -> controlsOpen = !controlsOpen;
            case ZOOM_IN -> changeZoom(1.0, areaX + areaWidth * 0.5f,
                    areaY + areaHeight * 0.5f, false);
            case ZOOM_OUT -> changeZoom(-1.0, areaX + areaWidth * 0.5f,
                    areaY + areaHeight * 0.5f, false);
        }
    }

    private boolean clickDrawer(float mouseX, float mouseY, int button) {
        for (ElementHit hit : drawerHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightClickElement = hit.element();
                contextX = mouseX;
                contextY = mouseY;
                contextOpen = true;
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
        float scale = (float) Math.max(0.9, chromeScale());
        float width = 238.0f * scale;
        float rowHeight = 28.0f * scale;
        float height = 12.0f * scale + contextEntries.size() * rowHeight;
        if (!inside(mouseX, mouseY, contextX, contextY, width, height)) {
            contextOpen = false;
            deleteArmed = false;
            return false;
        }
        int row = (int) ((mouseY - contextY - 6.0f * scale) / rowHeight);
        if (row >= 0 && row < contextEntries.size()) {
            MenuEntry entry = contextEntries.get(row);
            if (entry.enabled()) runContextAction(entry.action());
        }
        return true;
    }

    private void runContextAction(ContextAction action) {
        XaeroMapElements.Element element = rightClickElement;
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
            case WAYPOINTS -> drawer = Drawer.WAYPOINTS;
            case EXPORT -> exportSelection();
            case SETTINGS -> toggleSettings();
            case NONE -> {
            }
        }
        contextOpen = false;
        deleteArmed = false;
    }

    private void runElementTeleport(XaeroMapElements.Element element) {
        if (element == null) return;
        if (element.handle() instanceof Waypoint waypoint) {
            XaeroMapActions.teleportWaypoint(elementSnapshot.waypointWorld(), waypoint);
        } else if (element.handle() instanceof PlayerTrackerMapElement<?> player) {
            XaeroMapActions.teleportPlayer(activeProcessor, player);
        }
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
        waypointDraft = new WaypointDraft(waypoint);
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

    private void exportSelection() {
        int startX = rightSelecting || contextOpen ? selectionStartX : pointerBlockX >> 4;
        int startZ = rightSelecting || contextOpen ? selectionStartZ : pointerBlockZ >> 4;
        int endX = rightSelecting || contextOpen ? selectionEndX : startX;
        int endZ = rightSelecting || contextOpen ? selectionEndZ : startZ;
        XaeroMapActions.export(activeProcessor, startX, startZ, endX, endZ);
    }

    private void cycleCaveMode() {
        if (activeDimension == null || !WorldMapClientConfigUtils.getEffectiveCaveModeAllowed()) return;
        activeDimension.toggleCaveModeType(true);
        if (activeProcessor != null) {
            synchronized (activeProcessor.uiSync) {
                activeDimension.saveConfigUnsynced();
            }
            activeProcessor.updateCaveStart();
        }
        centered = false;
        contextOpen = false;
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
        String translated = I18n.get(key);
        return translated.equals(key) ? fallback : translated;
    }

    private final class WaypointDraft {
        private final Waypoint edited;
        private String name;
        private String symbol;
        private int colorIndex;
        private int focusedField;
        private float x;
        private float y;
        private float width;
        private float height;

        private WaypointDraft(Waypoint edited) {
            this.edited = edited;
            this.name = edited == null ? "Waypoint" : edited.getName();
            this.symbol = edited == null ? "W" : edited.getSymbol();
            if (edited != null && edited.getOriginal() instanceof xaero.common.minimap.waypoints.Waypoint source) {
                this.colorIndex = source.getColor();
            } else {
                this.colorIndex = 6;
            }
        }

        private void render(float mouseX, float mouseY, SettingsGuiPalette palette, float scale) {
            width = 304.0f * scale;
            height = 224.0f * scale;
            x = areaX + (areaWidth - width) * 0.5f;
            y = areaY + (areaHeight - height) * 0.5f;
            Renderer2D renderer = Renderer2D.COLOR;
            renderer.roundedRectSoftShadow(x, y, width, height, 11.0f * scale, 14.0f,
                    0.06f, palette.panelShadow());
            renderer.roundedRectGradient(x, y, width, height, 11.0f * scale,
                    palette.panelBgLeft(), palette.panelBgRight(), 0.0f);
            renderer.roundedRectStroke(x, y, width, height, 11.0f * scale,
                    0.8f, palette.panelStroke());
            String title = edited == null ? "Create waypoint" : "Edit waypoint";
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestBold(), title,
                    x + 16.0f * scale, y + 15.0f * scale, 13.0f * scale, palette.panelText(), false);
            renderer.svg("map-pin", x + width - 32.0f * scale, y + 13.0f * scale,
                    16.0f * scale, 16.0f * scale,
                    SvgRenderOptions.overrideColor(xaero.hud.minimap.waypoint.WaypointColor
                            .fromIndex(Math.floorMod(colorIndex, 16)).getHex() | 0xFF000000));
            drawDraftField("Name", name, 0, y + 49.0f * scale, palette, scale);
            drawDraftField("Symbol", symbol, 1, y + 96.0f * scale, palette, scale);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), "Color",
                    x + 16.0f * scale, y + 139.0f * scale, 9.0f * scale, palette.panelMuted(), false);
            float chipX = x + 16.0f * scale;
            float chipY = y + 155.0f * scale;
            for (int i = 0; i < 16; i++) {
                int color = xaero.hud.minimap.waypoint.WaypointColor.fromIndex(i).getHex() | 0xFF000000;
                float chip = 13.0f * scale;
                renderer.roundedRect(chipX, chipY, chip, chip, chip * 0.38f, color);
                if (i == colorIndex) renderer.roundedRectStroke(chipX - 2.0f * scale,
                        chipY - 2.0f * scale, chip + 4.0f * scale, chip + 4.0f * scale,
                        chip * 0.48f, 1.2f, palette.panelText());
                chipX += 17.0f * scale;
            }
            drawDraftButton("Cancel", x + width - 144.0f * scale, y + height - 38.0f * scale,
                    58.0f * scale, mouseX, mouseY, palette, scale, false);
            drawDraftButton(edited == null ? "Create" : "Save", x + width - 78.0f * scale,
                    y + height - 38.0f * scale, 62.0f * scale, mouseX, mouseY, palette, scale, true);
        }

        private void drawDraftField(String label, String value, int index, float fieldY,
                                    SettingsGuiPalette palette, float scale) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), label,
                    x + 16.0f * scale, fieldY, 9.0f * scale, palette.panelMuted(), false);
            float y0 = fieldY + 14.0f * scale;
            Renderer2D.COLOR.roundedRect(x + 16.0f * scale, y0, width - 32.0f * scale,
                    27.0f * scale, 6.0f * scale,
                    focusedField == index + 1 ? palette.controlSurfaceHover() : palette.controlSurface());
            Renderer2D.COLOR.roundedRectStroke(x + 16.0f * scale, y0, width - 32.0f * scale,
                    27.0f * scale, 6.0f * scale, 0.6f, palette.panelStroke());
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), value,
                    x + 24.0f * scale, y0 + 7.0f * scale, 10.0f * scale, palette.panelText(), false);
        }

        private void drawDraftButton(String label, float bx, float by, float bw,
                                     float mouseX, float mouseY, SettingsGuiPalette palette,
                                     float scale, boolean primary) {
            boolean hover = inside(mouseX, mouseY, bx, by, bw, 25.0f * scale);
            Renderer2D.COLOR.roundedRect(bx, by, bw, 25.0f * scale, 6.0f * scale,
                    primary ? palette.panelPillActive()
                            : hover ? palette.controlSurfaceHover() : palette.controlSurface());
            float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), label, 9.5f * scale);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), label,
                    bx + (bw - textWidth) * 0.5f, by + 7.0f * scale,
                    9.5f * scale, palette.panelText(), false);
        }

        private boolean mousePressed(float mouseX, float mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            float scale = (float) Math.max(0.9, chromeScale());
            if (!inside(mouseX, mouseY, x, y, width, height)) {
                waypointDraft = null;
                return true;
            }
            if (inside(mouseX, mouseY, x + 16.0f * scale, y + 63.0f * scale,
                    width - 32.0f * scale, 27.0f * scale)) {
                focusedField = 1;
                return true;
            }
            if (inside(mouseX, mouseY, x + 16.0f * scale, y + 110.0f * scale,
                    width - 32.0f * scale, 27.0f * scale)) {
                focusedField = 2;
                return true;
            }
            float chipX = x + 16.0f * scale;
            for (int i = 0; i < 16; i++) {
                if (inside(mouseX, mouseY, chipX, y + 155.0f * scale,
                        13.0f * scale, 13.0f * scale)) {
                    colorIndex = i;
                    return true;
                }
                chipX += 17.0f * scale;
            }
            if (inside(mouseX, mouseY, x + width - 144.0f * scale,
                    y + height - 38.0f * scale, 58.0f * scale, 25.0f * scale)) {
                waypointDraft = null;
                return true;
            }
            if (inside(mouseX, mouseY, x + width - 78.0f * scale,
                    y + height - 38.0f * scale, 62.0f * scale, 25.0f * scale)) {
                save();
                return true;
            }
            return true;
        }

        private void mouseReleased(float mouseX, float mouseY, int button) {
        }

        private boolean keyPressed(int keyCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waypointDraft = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                focusedField = focusedField == 1 ? 2 : 1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusedField != 0) {
                if (focusedField == 1 && !name.isEmpty()) name = name.substring(0, name.length() - 1);
                if (focusedField == 2 && !symbol.isEmpty()) symbol = symbol.substring(0, symbol.length() - 1);
                return true;
            }
            return focusedField != 0;
        }

        private boolean charTyped(char chr) {
            if (focusedField == 1 && !Character.isISOControl(chr) && name.length() < 48) {
                name += chr;
                return true;
            }
            if (focusedField == 2 && !Character.isISOControl(chr) && symbol.length() < 2) {
                symbol += chr;
                return true;
            }
            return focusedField != 0;
        }

        private void save() {
            if (edited == null) {
                XaeroMapActions.createWaypoint(elementSnapshot.waypointWorld(), contextBlockX,
                        contextBlockY == Short.MAX_VALUE ? Short.MAX_VALUE : contextBlockY + 1,
                        contextBlockZ, name, symbol, colorIndex);
            } else {
                XaeroMapActions.editWaypoint(elementSnapshot.waypointWorld(), edited,
                        name, symbol, colorIndex);
            }
            waypointDraft = null;
        }
    }

    private static void drawPlayerMarker(double centerX, double centerY, double width, double height,
                                         float yaw, int color) {
        double halfW = width * 0.5;
        double halfH = height * 0.5;
        double[] local = {
                0.0, -halfH,
                halfW, halfH * 0.82,
                0.0, halfH * 0.28,
                -halfW, halfH * 0.82
        };
        double[] points = new double[local.length];
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        for (int i = 0; i < local.length; i += 2) {
            double px = local[i];
            double py = local[i + 1];
            points[i] = centerX + px * cos - py * sin;
            points[i + 1] = centerY + px * sin + py * cos;
        }
        Renderer2D.COLOR.polygon(points, points.length / 2, color);
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
        if (rightSelecting && rightClickElement == null) {
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
        if ((!rightSelecting && !contextOpen) || rightClickElement != null) return;
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
        float stroke = Math.max(1.0f, (float) chromeScale());
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

    private static double chromeScale() {
        return 1.0;
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

    private enum Action {
        SETTINGS,
        RECENTER,
        CAVE,
        DIMENSION,
        WAYPOINTS,
        PLAYERS,
        RADAR,
        CLAIMS,
        EXPORT,
        CONTROLS,
        ZOOM_IN,
        ZOOM_OUT
    }

    private enum ContextAction {
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
        WAYPOINTS,
        EXPORT,
        SETTINGS
    }

    private enum Drawer {
        NONE,
        WAYPOINTS,
        PLAYERS
    }

    private record UiButton(Action action, String icon, float x, float y, float size,
                            String tooltip, boolean active) {
        private boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, size, size);
        }
    }

    private record MenuEntry(String label, String icon, boolean enabled, ContextAction action) {
    }

    private record ElementHit(XaeroMapElements.Element element, float x, float y,
                              float width, float height) {
        private boolean contains(float mouseX, float mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
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
}
