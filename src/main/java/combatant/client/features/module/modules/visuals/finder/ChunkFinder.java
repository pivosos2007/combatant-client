/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One module for every "this chunk looks inhabited" finder. Each finder has its own enable toggle
 * and its own settings, which are only visible while that toggle is on:
 * <ul>
 *     <li><b>Sus</b>: growth that needs a loaded chunk and hidden amethyst ({@link SusChunkScanner}).</li>
 *     <li><b>OG</b>: blocks worldgen never produces ({@link OgChunkScanner}).</li>
 *     <li><b>Prime</b>: new light or redstone below Y=0 from server updates ({@link PrimeChunkTracker}).</li>
 *     <li><b>Spawner</b>: spawner ESP with labels and the activation ring ({@link SpawnerTracker}).</li>
 * </ul>
 * Scanning and marker drawing are shared through {@link ChunkMarkers}; the render mode (box, glow
 * or shader surface) applies to every finder.
 */
@ModuleInfo(
        id = "chunkfinder",
        displayName = "ChunkFinder",
        aliases = {
                "SusChunkFinder", "GrowthFinder", "OPChunkFinder",
                "OGChunkFinder", "BaseFinder", "OldChunkFinder",
                "PrimeChunkFinder", "DeepslateFinder", "LightChangeFinder",
                "SpawnerFinder", "SpawnerESP", "SpawnerNotifier", "SpawnerNametags"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.chunkfinder.description")
public final class ChunkFinder extends ShaderBoxModule {

    // ---- shared ---------------------------------------------------------------------------------------------

    private final NumberValue<Integer> scanRadius = numCommon("scan_radius", CommonSettingSchemas.FINDER_SCAN_RADIUS,
            ChunkMarkers.MAX_SCAN_RADIUS, 2, ChunkMarkers.MAX_SCAN_RADIUS);
    private final NumberValue<Integer> markerY = numCommon("marker_y", CommonSettingSchemas.FINDER_MARKER_Y, 63, -64, 320);
    private final BooleanValue tracers = boolCommon("tracers", CommonSettingSchemas.FINDER_TRACERS, true);
    // A plate at one height is easy to miss from above or below; a beam is visible anywhere.
    private final BooleanValue beams = boolCommon("beams", CommonSettingSchemas.FINDER_BEAMS, false);
    private final BooleanValue chatAlerts = boolCommon("chat_alerts", CommonSettingSchemas.FINDER_CHAT_ALERTS, true);
    private final BooleanValue soundAlerts = boolCommon("sound_alerts", CommonSettingSchemas.FINDER_SOUND_ALERTS, true);

    // ---- sus ------------------------------------------------------------------------------------------------

    private final BooleanValue susEnabled = bool("sus_enabled", true);
    private final NumberValue<Integer> susSensitivity =
            visibleWhen(num("sus_sensitivity", 5, 1, 10), susEnabled::get);
    private final BooleanMapValue susSignals =
            visibleWhen(group("sus_signals", SusChunkScanner.defaultSignals()), susEnabled::get);
    private final NumberValue<Integer> susMinMarkerDistance =
            visibleWhen(num("sus_min_marker_distance", 4, 0, 48), susEnabled::get);
    private final RGBAColorValue susFillColor =
            visibleWhen(color("sus_fill_color", "#40CB40FF"), susEnabled::get);
    private final RGBAColorValue susLineColor =
            visibleWhen(color("sus_line_color", "#FFCB40FF"), susEnabled::get);

    // ---- og -------------------------------------------------------------------------------------------------

    private final BooleanValue ogEnabled = bool("og_enabled", true);
    private final BooleanMapValue ogSignals =
            visibleWhen(group("og_signals", OgChunkScanner.defaultSignals()), ogEnabled::get);
    private final NumberValue<Integer> ogMinMarkerDistance =
            visibleWhen(num("og_min_marker_distance", 24, 0, 48), ogEnabled::get);
    private final RGBAColorValue ogFillColor =
            visibleWhen(color("og_fill_color", "#4050FF00"), ogEnabled::get);
    private final RGBAColorValue ogLineColor =
            visibleWhen(color("og_line_color", "#FF50FF00"), ogEnabled::get);

    // ---- prime ----------------------------------------------------------------------------------------------

    private final BooleanValue primeEnabled = bool("prime_enabled", true);
    private final BooleanValue primeWatchRedstone =
            visibleWhen(bool("prime_watch_redstone", true), primeEnabled::get);
    private final RGBAColorValue primeFillColor =
            visibleWhen(color("prime_fill_color", "#40D8D8FF"), primeEnabled::get);
    private final RGBAColorValue primeLineColor =
            visibleWhen(color("prime_line_color", "#FFD8D8FF"), primeEnabled::get);

    // ---- spawner --------------------------------------------------------------------------------------------

    private final BooleanValue spawnerEnabled = bool("spawner_enabled", true);
    private final NumberValue<Integer> spawnerMaxDistance =
            visibleWhen(num("spawner_max_distance", 128, 16, 512), spawnerEnabled::get);
    private final BooleanValue spawnerNametags =
            visibleWhen(bool("spawner_nametags", true), spawnerEnabled::get);
    private final BooleanValue spawnerActivationRing =
            visibleWhen(bool("spawner_activation_ring", true), spawnerEnabled::get);
    private final BooleanValue spawnerTrialSpawners =
            visibleWhen(bool("spawner_trial_spawners", true), spawnerEnabled::get);
    private final RGBAColorValue spawnerBoxColor =
            visibleWhen(color("spawner_box_color", "#FFFF5050"), spawnerEnabled::get);
    private final RGBAColorValue spawnerRingColor =
            visibleWhen(color("spawner_ring_color", "#90FF5050"), spawnerEnabled::get);
    // Shows at a glance which spawners are running because you stand in their range.
    private final RGBAColorValue spawnerActiveColor =
            visibleWhen(color("spawner_active_color", "#FF50FF50"), spawnerEnabled::get);

    // ---- runtime --------------------------------------------------------------------------------------------

    private final SusChunkScanner susScanner = new SusChunkScanner(susSensitivity, susSignals);
    private final OgChunkScanner ogScanner = new OgChunkScanner(ogSignals);
    private final ChunkMarkers<SusChunkScanner.Scored> sus = new ChunkMarkers<>(
            markerSettings(susMinMarkerDistance, susFillColor, susLineColor),
            () -> "SusChunkFinder", susScanner::signature, susScanner::analyze, 3, 3000L);
    private final ChunkMarkers<OgChunkScanner.Found> og = new ChunkMarkers<>(
            markerSettings(ogMinMarkerDistance, ogFillColor, ogLineColor),
            () -> "OGChunkFinder", ogScanner::signature, ogScanner::analyze, 4, 3000L);
    private final PrimeChunkTracker prime = new PrimeChunkTracker(
            new PrimeChunkTracker.Settings(markerY, tracers, chatAlerts, soundAlerts, primeWatchRedstone,
                    primeFillColor, primeLineColor),
            () -> "PrimeChunkFinder");
    private final SpawnerTracker spawners = new SpawnerTracker(
            new SpawnerTracker.Settings(spawnerMaxDistance, spawnerNametags, spawnerActivationRing, tracers,
                    spawnerTrialSpawners, chatAlerts, spawnerBoxColor, spawnerRingColor, spawnerActiveColor),
            () -> "SpawnerFinder");

    // Which finders are currently running; follows the toggles so switching one on starts it fresh.
    private boolean susActive;
    private boolean ogActive;
    private boolean primeActive;
    private boolean spawnerActive;

    public ChunkFinder() {
        super(RenderMode.BOX, RenderMode.BOX, RenderMode.GLOW, RenderMode.SHADER);
    }

    private ChunkMarkers.Settings markerSettings(NumberValue<Integer> minMarkerDistance,
                                                 RGBAColorValue fill, RGBAColorValue line) {
        return new ChunkMarkers.Settings(scanRadius, markerY, minMarkerDistance, tracers, beams,
                chatAlerts, soundAlerts, fill, line);
    }

    @Override
    public void onEnable() {
        syncFinders();
    }

    @Override
    public void onDisable() {
        susActive = false;
        ogActive = false;
        primeActive = false;
        spawnerActive = false;
        sus.reset();
        og.reset();
        prime.stop();
        spawners.reset();
    }

    private void syncFinders() {
        if (susEnabled.get() != susActive) {
            susActive = !susActive;
            sus.reset();
        }
        if (ogEnabled.get() != ogActive) {
            ogActive = !ogActive;
            og.reset();
        }
        if (primeEnabled.get() != primeActive) {
            primeActive = !primeActive;
            if (primeActive) prime.start();
            else prime.stop();
        }
        if (spawnerEnabled.get() != spawnerActive) {
            spawnerActive = !spawnerActive;
            spawners.reset();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (susActive) sus.onPacket(event.getPacket());
        if (ogActive) og.onPacket(event.getPacket());
        if (primeActive) prime.onPacket(event.getPacket());
        if (spawnerActive) spawners.onPacket(event.getPacket());
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        syncFinders();
        if (susActive) sus.tick();
        if (ogActive) og.tick();
        if (primeActive) prime.tick();
        if (spawnerActive) spawners.tick();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null) return;
        if (susActive) sus.render(renderer, this);
        if (ogActive) og.render(renderer, this);
        if (primeActive) prime.render(renderer, this);
        if (spawnerActive) spawners.render(renderer, this);
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.BEFORE_MISC_OVERLAYS;
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || !spawnerActive) return;
        spawners.renderHud(renderer, textRenderer, tickDelta);
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!isEnabled() || !spawnerActive) return;
        spawners.renderHudForeground(renderer, textRenderer);
    }
}
