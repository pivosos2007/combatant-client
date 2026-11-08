/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.PlayerModel;
import combatant.client.features.playeranimator.PlayerRigInstance;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.io.ModelImporters;
import combatant.client.render.engine.asset.model.material.ModelMaterialBridge;
import combatant.client.util.resources.asset.AssetLoad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import combatant.client.render.ViewObstructionFadeState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer.ReplacementSkinCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;

public final class PlayerModels {
    public record Ready(List<PlayerModelCompiler.Surface> surfaces, Identifier texture,
                        ReplacementSkinCache handSkin, String bodyMode, List<Identifier> clothingSprites,
                        java.util.Set<String> clothingParts, Set<String> skinParts, boolean fullImportedBody,
                        PlayerModelProfile.SkinFit skinFit) {
        public Ready(List<PlayerModelCompiler.Surface> surfaces, Identifier texture) {
            this(surfaces, texture, new ReplacementSkinCache(), "replace", List.of(), Set.of(),
                    Set.of(), true, PlayerModelProfile.SkinFit.DEFAULT);
        }
        public Ready(List<PlayerModelCompiler.Surface> surfaces, Identifier texture,
                     ReplacementSkinCache handSkin) {
            this(surfaces, texture, handSkin, "replace", List.of(), Set.of(),
                    Set.of(), true, PlayerModelProfile.SkinFit.DEFAULT);
        }
        public boolean usesPlayerSkin() { return "player_skin".equals(bodyMode); }
    }
    public record Snapshot(Ready asset, PlayerRigInstance rig, Matrix4f fitting,
                           ReplacementSkinCache skin, float opacity, float hit) {
        public Snapshot(Ready asset, PlayerRigInstance rig, Matrix4f fitting, ReplacementSkinCache skin) {
            this(asset, rig, fitting, skin, 1.0f, 0.0f);
        }
        public boolean faded() { return opacity < 0.995f; }
    }
    private record Loaded(List<PlayerModelCompiler.Surface> surfaces, List<PlayerModelTexture.Level> texture,
                          String bodyMode, List<Identifier> clothingSprites, Set<String> clothingParts,
                          Set<String> skinParts, boolean fullImportedBody,
                          PlayerModelProfile.SkinFit skinFit) {}
    private record Key(String model, PlayerModelTexture.Quality quality, long generation) {}
    private record GeometryKey(String model, String profile, List<String> renderGroups, long generation) {}
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final java.util.concurrent.ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Combatant player models");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<GeometryKey, List<PlayerModelCompiler.Surface>> GEOMETRY = new LinkedHashMap<>();
    private static final Map<Key, Ready> CACHE = new LinkedHashMap<>(8, 0.75f, true);
    private static final ReferenceQueue<Ready> RELEASED = new ReferenceQueue<>();
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
    private static final Set<TextureOwner> TEXTURES = new HashSet<>();
    private static final class TextureOwner extends WeakReference<Ready> {
        private final List<Identifier> ids;
        private TextureOwner(Ready asset) {
            super(asset, RELEASED);
            ids = List.of(asset.texture);
            // Deferred render states retain Ready. Release only after the last state/cache lets go.
            CLEANER.register(asset, () -> Minecraft.getInstance().execute(() -> {
                if (TEXTURES.remove(this)) release();
            }));
        }
        private void release() {
            for (Identifier id : ids) Minecraft.getInstance().getTextureManager().release(id);
        }
    }
    /** Multiple relations may be rendered in the same frame: never cancel an unrelated model load. */
    private static final Map<Key, CompletableFuture<Loaded>> PENDING = new LinkedHashMap<>();
    private static final Map<Key, String> FAILED = new HashMap<>();
    private static Ready ready;
    private static String status = "";
    private static final PlayerRigInstance HAND_BIND_RIG = createHandRig();
    // Cache the arm-only mesh once per imported asset. No per-frame vertex
    // filtering, skinning, or new GPU geometry allocations.
    private static final Map<Ready, Map<HumanoidArm, List<PlayerModelCompiler.Surface>>> HAND_GEOMETRY =
            new WeakHashMap<>();

    private static PlayerRigInstance createHandRig() {
        PlayerRigInstance rig = new PlayerRigInstance();
        rig.solve();
        return rig;
    }

    private PlayerModels() {}

    public static boolean applies(AbstractClientPlayer player) {
        PlayerModel module = Modules.get(PlayerModel.class);
        return applies(player, module, module == null || player == null
                ? PlayerModel.Target.SELF : module.targetFor(player));
    }

    public static boolean applies(AbstractClientPlayer player, PlayerModel module, PlayerModel.Target target) {
        return player != null && module != null && module.isEnabled()
                && module.replacement(target).get();
    }

    public static String status() { return status; }
    /** Last prepared local-player model, retained for existing callers. */
    public static Ready ready() { return ready; }
    public static void reportFailure(Exception failure) {
        status = "Vanilla fallback: " + failure.getMessage();
    }

    public static Ready prepare(PlayerModel module) { return prepare(module, PlayerModel.Target.SELF); }

    public static Ready prepare(PlayerModel module, PlayerModel.Target target) {
        if (module == null || target == null) return null;
        boolean self = target == PlayerModel.Target.SELF;
        Key requested = null;
        try {
            TextureOwner retired;
            while ((retired = (TextureOwner) RELEASED.poll()) != null) {
                if (TEXTURES.remove(retired)) retired.release();
            }
            var catalog = ModelCatalog.discover(Minecraft.getInstance().getResourceManager());
            List<ModelCatalog.Entry> entries = catalog.entries();
            var selection = module.model(target);
            selection.addOptions(entries.stream().map(ModelCatalog.Entry::id).toList());
            String selected = selection.get();
            // Maintain the legacy automatic selection for self only. New relations are opt-in.
            if (self && selected.equals("none") && !entries.isEmpty()) {
                selection.set(entries.getFirst().id());
                selected = selection.get();
            } else if (!selected.equals("none") && !selected.contains(":")) {
                String legacySelection = selected;
                var legacy = entries.stream()
                        .filter(e -> ModelCatalog.id(e.id()).getPath().equals(legacySelection)).toList();
                if (legacy.size() == 1) {
                    selection.set(legacy.getFirst().id());
                    selected = selection.get();
                }
            }
            if (selected.equals("none") || entries.isEmpty()) {
                if (self) {
                    ready = null;
                    status = catalog.errors().isEmpty() ? "" : String.join("; ", catalog.errors());
                }
                return null;
            }
            requested = new Key(selected, new PlayerModelTexture.Quality(module.textureScale(),
                    module.textureFiltering.get().equals("Linear")), GENERATION.get());
            Ready cached = CACHE.get(requested);
            if (cached != null) {
                if (self) { ready = cached; status = selected; }
                return cached;
            }
            String failed = FAILED.get(requested);
            if (failed != null) {
                if (self) { ready = null; status = failed; }
                return null;
            }
            CompletableFuture<Loaded> future = PENDING.get(requested);
            if (future == null) {
                var resources = Minecraft.getInstance().getResourceManager();
                Key loadKey = requested;
                List<ModelCatalog.Entry> loadEntries = List.copyOf(entries);
                future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return load(loadKey, loadEntries,
                                new combatant.client.render.engine.asset.model.io.MinecraftModelResolver(resources));
                    } catch (IOException | RuntimeException failure) {
                        throw new CompletionException(failure);
                    }
                }, LOADER);
                PENDING.put(requested, future);
            }
            if (!future.isDone()) {
                if (self) { ready = null; status = "Loading " + selected; }
                return null;
            }
            PENDING.remove(requested);
            Loaded loaded = future.join();
            Identifier texture = upload(loaded.texture, requested.quality());
            Ready resolved = new Ready(loaded.surfaces, texture, new ReplacementSkinCache(),
                    loaded.bodyMode, loaded.clothingSprites, loaded.clothingParts,
                    loaded.skinParts, loaded.fullImportedBody, loaded.skinFit);
            TEXTURES.add(new TextureOwner(resolved));
            CACHE.put(requested, resolved);
            while (CACHE.size() > 12) CACHE.remove(CACHE.keySet().iterator().next());
            if (self) { ready = resolved; status = selected; }
            return resolved;
        } catch (Exception failure) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            String message = "Vanilla fallback: " + cause.getMessage();
            if (requested != null) {
                PENDING.remove(requested);
                FAILED.put(requested, message);
            }
            if (self) { ready = null; status = message; }
            return null;
        }
    }

    private static Loaded load(Key key, List<ModelCatalog.Entry> entries, combatant.client.render.engine.asset.model.io.ModelResourceResolver source) throws IOException {
        ModelCatalog.Entry entry = entries.stream().filter(e -> e.id().equals(key.model)).findFirst()
                .orElseThrow(() -> new IOException("Complete model not found: " + key.model));
        PlayerModelProfile profile = PlayerModelProfile.load(source, entry.profile());
        GeometryKey geometryKey = new GeometryKey(entry.model(), entry.profile(),
                entry.renderGroups(), key.generation);
        List<PlayerModelCompiler.Surface> surfaces = GEOMETRY.get(geometryKey);
        if (surfaces == null) {
            var imported = ModelImporters.load(ModelCatalog.id(entry.model()), id -> source.open(
                    id.equals(ModelCatalog.id(entry.atlas())) ? ModelCatalog.id(entry.texture()) : id));
            var runtime = new ModelRuntimeAsset(imported, ModelMaterialBridge.build(imported));
            surfaces = PlayerModelCompiler.compile(runtime, profile, entry.renderGroups());
            GEOMETRY.put(geometryKey, surfaces);
            while (GEOMETRY.size() > 4) GEOMETRY.remove(GEOMETRY.keySet().iterator().next());
        }
        // Resource-only clothing sprites must be actual player-UV PNG layers. They are never
        // mixed into or cached as the Minecraft skin; resource reload owns their lifetime.
        for (String sprite : entry.clothingSprites()) {
            byte[] png = ModelCatalog.read(source, sprite);
            validatePng(png);
            int width = ByteBuffer.wrap(png, 16, 4).getInt();
            int height = ByteBuffer.wrap(png, 20, 4).getInt();
            int colorType = png[25] & 0xff;
            if (width != height || width < 64 || width > 2048 || (width & (width - 1)) != 0
                    || (colorType != 6 && colorType != 4))
                throw new IOException("Clothing sprite must use square RGBA player-skin UVs: " + sprite);
        }
        return new Loaded(surfaces, PlayerModelTexture.decode(ModelCatalog.read(source, entry.texture()), key.quality),
                entry.bodyMode(), entry.clothingSprites().stream().map(ModelCatalog::id).toList(),
                Set.copyOf(entry.clothingParts()), Set.copyOf(entry.skinParts()),
                entry.renderGroups().isEmpty(), profile.skinFit());
    }

    static void validatePng(byte[] bytes) throws IOException {
        if (bytes.length < 33 || ByteBuffer.wrap(bytes).getLong() != 0x89504e470d0a1a0aL
                || ByteBuffer.wrap(bytes, 12, 4).getInt() != 0x49484452) throw new IOException("Invalid PNG image");
        int width = ByteBuffer.wrap(bytes, 16, 4).getInt(), height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) throw new IOException("PNG dimensions exceed 4096");
    }

    private static Identifier upload(List<PlayerModelTexture.Level> levels, PlayerModelTexture.Quality quality) {
        Identifier id = Identifier.fromNamespaceAndPath("combatant", "player_models/body_" + UUID.randomUUID());
        var texture = new PlayerModelTexture(id.toString(), levels, quality);
        try { Minecraft.getInstance().getTextureManager().register(id, texture); }
        catch (RuntimeException failure) { texture.close(); throw failure; }
        return id;
    }

    public static Snapshot snapshot(Ready asset, PlayerRigInstance pose, PlayerModel module) {
        return snapshot(asset, pose, module, PlayerModel.Target.SELF);
    }

    public static Snapshot snapshot(Ready asset, PlayerRigInstance pose, PlayerModel module,
                                    PlayerModel.Target target) {
        if (asset == null || pose == null) return null;
        return new Snapshot(asset, freezePose(pose), module.fitting(target), new ReplacementSkinCache());
    }

    /** Capture render-state alpha and damage once, before GPU draw submissions are deferred. */
    public static Snapshot snapshot(Ready asset, PlayerRigInstance pose, PlayerModel module,
                                    PlayerModel.Target target, AbstractClientPlayer player,
                                    ViewObstructionFadeState fade) {
        Snapshot base = snapshot(asset, pose, module, target);
        if (base == null || player == null) return base;
        PlayerModelVisualEffects.Values effects = PlayerModelVisualEffects.sample(player, fade);
        return new Snapshot(base.asset(), base.rig(), base.fitting(), base.skin(),
                effects.opacity(), effects.hit());
    }

    static PlayerRigInstance freezePose(PlayerRigInstance pose) {
        PlayerRigInstance frozen = new PlayerRigInstance();
        for (int bone = 0; bone < pose.pose().boneCount(); bone++) frozen.pose().set(bone, pose.pose().bone(bone));
        frozen.solve();
        return frozen;
    }

    public static boolean submit(Object state, SubmitNodeCollector collector, PoseStack matrices,
                                 RenderType originalType, int light, int overlay, int tint, int outlineColor) {
        if (!(state instanceof PlayerModelRenderState target) || target.combatant$getReplacement() == null) return false;
        Snapshot snapshot = target.combatant$getReplacement();
        target.combatant$setReplacementSubmitted(false);
        matrices.pushPose();
        try {
            matrices.mulPose(snapshot.fitting);
            // Both opaque and translucent surfaces are GPU-skinned. The latter are
            // flushed at the correct world transparency boundary, not via a second
            // per-frame CPU mesh emission into Vanilla's collector.
            if (PlayerModelGpuRenderer.trySubmit(snapshot, matrices, originalType, light, overlay, tint, outlineColor)) {
                target.combatant$setReplacementSubmitted(true);
                return true;
            }
            return false;
        } finally { matrices.popPose(); }
    }

    /**
     * Called from vanilla's renderPlayerHand, after its equip/swing/use/map transforms have
     * already been applied. Only the matching arm nodes are submitted; held items remain vanilla.
     */
    public static boolean submitFirstPersonHand(PoseStack matrices, SubmitNodeCollector collector,
                                                int light, HumanoidArm arm) {
        PlayerModel module = Modules.get(PlayerModel.class);
        Minecraft mc = Minecraft.getInstance();
        if (module == null || !module.isEnabled() || !module.replacement.get()
                || !module.firstPersonHands.get() || mc.player == null || arm == null) return false;
        Ready selected = prepare(module);
        if (selected == null) return false;
        String group = arm == HumanoidArm.LEFT ? "leftArmModel" : "rightArmModel";
        // The source model uses vanilla model-pixel coordinates. Do not apply entity world
        // offsets / animation to the camera-space hand: vanilla has already placed the hand.
        List<PlayerModelCompiler.Surface> armSurfaces = HAND_GEOMETRY
                .computeIfAbsent(selected, ignored -> new EnumMap<>(HumanoidArm.class))
                .computeIfAbsent(arm, ignored -> {
                    List<PlayerModelCompiler.Surface> parts = new ArrayList<>();
                    for (var surface : selected.surfaces()) {
                        var matching = surface.mesh().parts().stream()
                                .filter(part -> isArmGroup(part.name(), group)).toList();
                        if (!matching.isEmpty()) {
                            parts.add(new PlayerModelCompiler.Surface(
                                    new combatant.client.render.engine.rig.mesh.RigMeshData(matching),
                                    surface.translucent()));
                        }
                    }
                    return List.copyOf(parts);
                });
        if (armSurfaces.isEmpty()) return false;
        return PlayerModelGpuRenderer.submitHand(
                new Snapshot(selected, HAND_BIND_RIG, new Matrix4f(), selected.handSkin()),
                armSurfaces, matrices, light);
    }

    private static boolean isArmGroup(String name, String group) {
        return name.equals(group) || name.startsWith(group + "/");
    }

    /** The original Avatar model retains the player's live skin, visibility and head pose.
     *  Replacement armor is submitted separately on the GPU using the same extracted rig. */
    /** The exact immutable pose used for the imported GPU armor is also used for the
     *  player's native skin geometry. This prevents the vanilla pose diverging at the
     *  neck/shoulders during flight, crouching, and custom animator transitions. */
    public static Snapshot skinBacked(Object state) {
        if (state instanceof PlayerModelRenderState replacement) {
            Snapshot snapshot = replacement.combatant$getReplacement();
            if (snapshot != null && snapshot.asset().usesPlayerSkin()) return snapshot;
        }
        return null;
    }

    /** Give native skin and clothing fragments the same smooth damage color as imported armor. */
    public static int nativeSkinTint(int tint, float hit) {
        if (hit <= 0.0f) return tint;
        float amount = Math.max(0.0f, Math.min(1.0f, hit));
        int color = tint == -1 ? 0xFFFFFFFF : tint;
        int green = Math.round(((color >>> 8) & 255) * (1.0f - 0.65f * amount));
        int blue = Math.round((color & 255) * (1.0f - 0.65f * amount));
        return (color & 0xFFFF0000) | (green << 8) | blue;
    }

    /** Only a full imported body replaces native armor, cape, wings and attachments.
     *  A partial skin-backed helmet must not suppress unrelated vanilla layers. */
    public static boolean replacesBody(Object state) {
        if (!(state instanceof PlayerModelRenderState replacement) ||
                !replacement.combatant$isReplacementSubmitted()) return false;
        Snapshot overlay = skinBacked(state);
        return overlay == null || overlay.asset().fullImportedBody();
    }

    public static boolean active(Object state) {
        return state instanceof PlayerModelRenderState replacement && replacement.combatant$getReplacement() != null;
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
        PlayerModelGpuRenderer.reset();
        combatant.client.render.engine.rig.gpu.RigGpuMeshCache.global().invalidate();
        ModelCatalog.invalidate();
        for (CompletableFuture<Loaded> future : PENDING.values()) future.cancel(false);
        PENDING.clear();
        FAILED.clear();
        ready = null;
        CACHE.clear();
        HAND_GEOMETRY.clear();
        PlayerModelVisualEffects.clear();
        LOADER.execute(GEOMETRY::clear);
    }

    @AssetLoad(order = 510)
    public static void reloadResources() {
        invalidate();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) for (TextureOwner owner : TEXTURES) owner.release();
        TEXTURES.clear();
    }
}
