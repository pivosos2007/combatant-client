/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.compat.xaero.XaeroIntegration;
import combatant.client.compat.xaero.XaeroWaypointSnapshot;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.map.MapGridSpec;
import combatant.client.render.map.MapLabel;
import combatant.client.render.map.MapMarker;
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
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.graphics.GpuTextureAndView;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
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
    private boolean lastFrameRenderedRootTextures;

    static boolean available() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return false;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null || !processor.isMapWorldUsable()) return false;
        MapWorld world = processor.getMapWorld();
        return world != null && world.getCurrentDimension() != null;
    }

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
        MapDimension dimension = world == null ? null : world.getCurrentDimension();
        if (dimension == null) return Frame.waiting("Preparing World Map...");

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
        MapViewport viewport = new MapViewport(new MapRect(x, y, width, height), centerX, centerZ, pixelsPerBlock);
        if (processor.getCurrentWorldId() == null
                || processor.isWaitingForWorldUpdate()
                || !processor.getMapSaveLoad().isRegionDetectionComplete()) {
            drawChrome();
            return Frame.waiting("Preparing World Map...");
        }

        int lod = selectXaeroLod();
        List<MapTerrainTile> terrain;
        synchronized (processor.renderThreadPauseSync) {
            if (processor.isRenderingPaused()) {
                drawChrome();
                return Frame.waiting("Preparing World Map...");
            }
            processor.updateCaveStart();
            processor.getMapSaveLoad().mainTextureLevel = lod;
            terrain = collectTerrain(processor, dimension, viewport, lod);
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
                ClickGuiRenderer.getInterRegular()
        );
        drawLoading(viewport);
        drawPlayerArrow(processor, dimension, viewport);
        drawChrome();
        drawZoom();
        return result.terrainTilesDrawn() == 0
                ? Frame.waiting("Preparing World Map...")
                : new Frame(true, "", result.terrainTilesDrawn());
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !contains(mouseX, mouseY)) return false;
        double chromeScale = chromeScale();
        double right = areaX + areaWidth - 20.0 * chromeScale;
        if (inside(mouseX, mouseY, right, areaY + areaHeight - 160.0 * chromeScale, 20.0 * chromeScale, 20.0 * chromeScale)) {
            changeZoom(1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        if (inside(mouseX, mouseY, right, areaY + areaHeight - 140.0 * chromeScale, 20.0 * chromeScale, 20.0 * chromeScale)) {
            changeZoom(-1.0, areaX + areaWidth * 0.5f, areaY + areaHeight * 0.5f, false);
            return true;
        }
        dragging = true;
        dragX = mouseX;
        dragY = mouseY;
        return true;
    }

    void mouseReleased(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!contains(mouseX, mouseY) || amount == 0.0) return false;
        changeZoom(amount, mouseX, mouseY, amount > 0.0);
        return true;
    }

    void recenter() {
        centered = false;
    }

    void resume() {
        dragging = false;
    }

    void suspend() {
        dragging = false;
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
        boolean renderedRoot = false;
        int brightness = Math.max(0, Math.min(255, Math.round(processor.getBrightness() * 255.0f)));
        int tint = brightness << 24 | 0x00FFFFFF;
        String sourceId = sourceId(processor, dimension);
        List<MapTerrainTile> terrain = new ArrayList<>();

        for (MapTileCoordinate requested : visibleTileSelector.select(viewport, requestedLod, 0)) {
            ResolvedTile tile = resolveTile(processor, requested);
            if (tile == null) {
                addLoadingRegion(requested);
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
                    new MapTileGpuCopy(gpu.texture, 0, 0, TILE_RESOLUTION, TILE_RESOLUTION),
                    material,
                    tile.textureUv(),
                    tint
            ));
        }

        requestLoading(processor);
        prevWaitingForBranchCache = waitingForBranchCache[0];
        lastFrameRenderedRootTextures = renderedRoot;
        return List.copyOf(terrain);
    }

    private ResolvedTile resolveTile(MapProcessor processor, MapTileCoordinate requested) {
        int lod = requested.lod();
        int regionX = Math.floorDiv(requested.x(), TILES_PER_REGION_SIDE);
        int regionZ = Math.floorDiv(requested.z(), TILES_PER_REGION_SIDE);
        LeveledRegion<?> region = processor.getLeveledRegion(
                processor.getCurrentCaveLayer(), regionX, regionZ, lod);
        if (region == null) {
            queueLeaf(processor, requested);
            region = processor.getLeveledRegion(processor.getCurrentCaveLayer(), regionX, regionZ, lod);
        }
        if (region == null) return null;

        updateBranch(processor, region, lod, regionX, regionZ);
        int localX = Math.floorMod(requested.x(), TILES_PER_REGION_SIDE);
        int localZ = Math.floorMod(requested.z(), TILES_PER_REGION_SIDE);
        RegionTexture<?> exact = region.hasTextures() ? region.getTexture(localX, localZ) : null;
        if (usable(exact)) {
            return new ResolvedTile(requested, region, region, exact, MapTileUvRect.FULL);
        }

        queueLeaf(processor, requested);
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

        if (lastFrameRenderedRootTextures) {
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

    private void queueLeaf(MapProcessor processor, MapTileCoordinate requested) {
        long worldX = (long) requested.x() * requested.blockSpan(TILE_RESOLUTION);
        long worldZ = (long) requested.z() * requested.blockSpan(TILE_RESOLUTION);
        int leafRegionX = (int) Math.floorDiv(worldX, REGION_BLOCKS);
        int leafRegionZ = (int) Math.floorDiv(worldZ, REGION_BLOCKS);
        int caveLayer = processor.getCurrentCaveLayer();
        MapRegion leaf = processor.getLeafMapRegion(caveLayer, leafRegionX, leafRegionZ, false);
        if (leaf == null && processor.regionExists(caveLayer, leafRegionX, leafRegionZ)) {
            leaf = processor.getLeafMapRegion(caveLayer, leafRegionX, leafRegionZ, true);
        }
        if (leaf != null) leafRequests.add(leaf);
    }

    private void queueBranch(BranchLeveledRegion branch) {
        if (branch == null || branchRequests.contains(branch)) return;
        branch.calculateSortingDistance();
        branchRequests.add(branch);
    }

    private void requestLoading(MapProcessor processor) {
        LeveledRegion<?> next = processor.getMapSaveLoad().getNextToLoadByViewing();
        if ((next != null && !next.shouldAllowAnotherRegionToLoad())
                || processor.getAffectingLoadingFrequencyCount() >= 16) return;

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
                    if (processor.getMapSaveLoad().getNextToLoadByViewing() == null) {
                        processor.getMapSaveLoad().setNextToLoadByViewing(branch);
                    }
                });

        if (prevWaitingForBranchCache) return;
        int requested = 0;
        for (MapRegion leaf : leafRequests) {
            if (requested >= MAX_LEAF_REQUESTS_PER_FRAME) break;
            synchronized (leaf) {
                if (!leaf.canRequestReload_unsynced()) continue;
                leaf.calculateSortingDistance();
                processor.getMapSaveLoad().requestLoad(leaf, "Gui");
                if (processor.getMapSaveLoad().getNextToLoadByViewing() == null) {
                    processor.getMapSaveLoad().setNextToLoadByViewing(leaf);
                }
                requested++;
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
        List<MapMarker> markers = new ArrayList<>();
        List<MapLabel> labels = new ArrayList<>();
        for (XaeroWaypointSnapshot waypoint : XaeroIntegration.snapshots(XaeroIntegration.RenderTarget.WORLD_MAP)) {
            double dimensionScale = waypoint.coordinateSpace() == XaeroWaypointSnapshot.CoordinateSpace.OVERWORLD
                    && dimension.getDimId().equals(Level.NETHER) ? 0.125 : 1.0;
            int color = waypoint.color() == XaeroWaypointSnapshot.Color.GOLD ? 0xFFFFB82E : 0xFFFF4D57;
            double worldX = waypoint.x() * dimensionScale;
            double worldZ = waypoint.z() * dimensionScale;
            markers.add(new MapMarker(waypoint.id(), worldX, worldZ, 4.0, color, 0xFF111318, 100));
            labels.add(new MapLabel(waypoint.id() + ":label", worldX, worldZ, waypoint.name(), 0xFFF4F6FA, 100));
        }
        return new MapOverlaySnapshot(markers, labels, List.of());
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
        MapScreenPoint point = viewport.project(
                minecraft.player.getX() / divisor,
                minecraft.player.getZ() / divisor
        );
        double size = 8.0 * screenScaleMultiplier() / viewportUiScale();
        drawArrow(point.x(), point.y() + 2.0 * screenScaleMultiplier() / viewportUiScale(), size,
                minecraft.player.getYRot(), 0xE6000000);
        drawArrow(point.x(), point.y(), size, minecraft.player.getYRot(), 0xFFFF2C2C);
    }

    private void drawChrome() {
        double scale = chromeScale();
        double right = areaX + areaWidth;
        double bottom = areaY + areaHeight;
        Renderer2D.TEXTURE.begin();
        icon(areaX + 5.0 * scale, areaY + 5.0 * scale, 20.0 * scale, 113, 0, 20);
        buttonIcon(areaX, bottom - 40.0 * scale, scale, 229, 64);
        buttonIcon(areaX, bottom - 60.0 * scale, scale, 197, 80);
        buttonIcon(right - 20.0 * scale, bottom - 20.0 * scale, scale, 213, 0);
        buttonIcon(right - 20.0 * scale, bottom - 40.0 * scale, scale, 197, 32);
        buttonIcon(right - 20.0 * scale, bottom - 60.0 * scale, scale, 213, 32);
        buttonIcon(right - 20.0 * scale, bottom - 80.0 * scale, scale, 197, 64);
        buttonIcon(right - 20.0 * scale, bottom - 100.0 * scale, scale, 133, 0);
        buttonIcon(right - 20.0 * scale, bottom - 120.0 * scale, scale, 197, 0);
        buttonIcon(right - 20.0 * scale, bottom - 140.0 * scale, scale, 181, 0);
        buttonIcon(right - 20.0 * scale, bottom - 160.0 * scale, scale, 165, 0);
        icon(right - 34.0 * scale, areaY + 2.0 * scale, 32.0 * scale, 0, 37, 32);
        Renderer2D.TEXTURE.render(WorldMap.guiTextures);
    }

    private void drawZoom() {
        String zoom = Math.round(destinationScale * 1000.0) / 1000.0 + "x";
        float fontSize = (float) (10.0 * chromeScale());
        float textWidth = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), zoom, fontSize);
        float textX = areaX + (areaWidth - textWidth) * 0.5f;
        float textY = (float) (areaY + areaHeight - 12.0 * chromeScale());
        Renderer2D.COLOR.quad(textX - 2.0f, textY - 1.0f, textWidth + 4.0f, fontSize + 2.0f, 0x66000000);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(), zoom, textX, textY, fontSize, 0xFFFFFFFF, false);
    }

    private static void buttonIcon(double x, double y, double scale, int u, int v) {
        icon(x + 2.0 * scale, y + 2.0 * scale, 16.0 * scale, u, v, 16);
    }

    private static void icon(double x, double y, double size, int u, int v, int sourceSize) {
        Renderer2D.TEXTURE.texQuad(
                x, y, size, size,
                u / 256.0, v / 256.0,
                (u + sourceSize) / 256.0, (v + sourceSize) / 256.0,
                0xFFFFFFFF
        );
    }

    private static void drawArrow(double centerX, double centerY, double size, float yaw, int color) {
        double[] local = {0.0, 1.0, 0.72, -0.72, 0.0, -0.38, -0.72, -0.72};
        double[] points = new double[local.length];
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        for (int i = 0; i < local.length; i += 2) {
            double px = local[i] * size;
            double py = local[i + 1] * size;
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
        return scale * screenScaleMultiplier() / viewportUiScale();
    }

    private static double screenScaleMultiplier() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return 1.0;
        int shortSide = Math.min(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        return shortSide <= 1080 ? 1.0 : shortSide / 1080.0;
    }

    private static double viewportUiScale() {
        ViewportContext context = ViewportContext.current();
        return context == null ? 1.0 : Math.max(0.0001, context.uiScale());
    }

    private static double chromeScale() {
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft == null || minecraft.getWindow() == null
                ? 1.0
                : minecraft.getWindow().getGuiScale();
        return guiScale / viewportUiScale();
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
