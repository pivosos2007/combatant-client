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
import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.io.ModelImporters;
import combatant.client.render.engine.asset.model.material.ModelMaterialBridge;
import combatant.client.util.resources.asset.AssetLoad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer.ReplacementSkinCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
                        ReplacementSkinCache handSkin) {
        public Ready(List<PlayerModelCompiler.Surface> surfaces, Identifier texture) {
            this(surfaces, texture, new ReplacementSkinCache());
        }
    }
    public record Snapshot(Ready asset, PlayerRigInstance rig, Matrix4f fitting, ReplacementSkinCache skin) {}
    private record Loaded(List<PlayerModelCompiler.Surface> surfaces, List<PlayerModelTexture.Level> texture) {}
    private record Key(String model, PlayerModelTexture.Quality quality, long generation) {}
    private record GeometryKey(String model, String profile, long generation) {}
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
    private static Key key;
    private static CompletableFuture<Loaded> pending;
    private static Ready ready;
    private static String status = "";
    private static final PlayerRigInstance HAND_BIND_RIG = createHandRig();

    private static PlayerRigInstance createHandRig() {
        PlayerRigInstance rig = new PlayerRigInstance();
        rig.solve();
        return rig;
    }

    private PlayerModels() {}

    public static boolean applies(AbstractClientPlayer player) {
        PlayerModel module = Modules.get(PlayerModel.class);
        return module != null && module.isEnabled() && module.replacement.get()
                && Minecraft.getInstance().player == player;
    }

    public static String status() { return status; }
    public static Ready ready() { return ready; }
    public static void reportFailure(Exception failure) {
        status = "Vanilla fallback: " + failure.getMessage();
        combatant.client.util.logging.DebugLog.warnOnChange("player-model-load", status, "[PlayerModel] %s", status);
    }

    public static Ready prepare(PlayerModel module) {
        try {
            TextureOwner retired;
            while ((retired = (TextureOwner) RELEASED.poll()) != null) {
                if (TEXTURES.remove(retired)) retired.release();
            }
            var catalog = ModelCatalog.discover(Minecraft.getInstance().getResourceManager());
            List<ModelCatalog.Entry> entries = catalog.entries();
            module.model.addOptions(entries.stream().map(ModelCatalog.Entry::id).toList());
            String selected = module.model.get();
            if (selected.equals("none") && !entries.isEmpty()) module.model.set(entries.getFirst().id());
            else if (!selected.contains(":")) {
                var legacy = entries.stream().filter(e -> ModelCatalog.id(e.id()).getPath().equals(selected)).toList();
                if (legacy.size() == 1) module.model.set(legacy.getFirst().id());
            }
            if (entries.isEmpty()) {
                ready = null;
                status = catalog.errors().isEmpty() ? "" : String.join("; ", catalog.errors());
                return null;
            }
            Key requested = new Key(module.model.get(), new PlayerModelTexture.Quality(module.textureScale(),
                    module.textureFiltering.get().equals("Linear")), GENERATION.get());
            if (!requested.equals(key)) {
                key = requested;
                ready = null;
                status = "Loading " + requested.model();
                if (pending != null) pending.cancel(false);
                ready = CACHE.get(requested);
                if (ready != null) {
                    pending = null;
                    status = requested.model;
                    return ready;
                }
                var resources = Minecraft.getInstance().getResourceManager();
                pending = CompletableFuture.supplyAsync(() -> {
                    try { return load(requested, entries, new combatant.client.render.engine.asset.model.io.MinecraftModelResolver(resources)); }
                    catch (IOException | RuntimeException failure) { throw new CompletionException(failure); }
                }, LOADER);
            }
            if (pending != null && pending.isDone()) {
                CompletableFuture<Loaded> completed = pending;
                pending = null;
                Loaded loaded = completed.join();
                Identifier texture = upload(loaded.texture, requested.quality);
                ready = new Ready(loaded.surfaces, texture);
                TEXTURES.add(new TextureOwner(ready));
                CACHE.put(requested, ready);
                while (CACHE.size() > 4) CACHE.remove(CACHE.keySet().iterator().next());
                status = requested.model();
            }
            return ready;
        } catch (Exception failure) {
            ready = null;
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            status = "Vanilla fallback: " + cause.getMessage();
            combatant.client.util.logging.DebugLog.warnOnChange("player-model-load", status, "[PlayerModel] %s", status);
            return null;
        }
    }

    private static Loaded load(Key key, List<ModelCatalog.Entry> entries, combatant.client.render.engine.asset.model.io.ModelResourceResolver source) throws IOException {
        ModelCatalog.Entry entry = entries.stream().filter(e -> e.id().equals(key.model)).findFirst()
                .orElseThrow(() -> new IOException("Complete model not found: " + key.model));
        GeometryKey geometryKey = new GeometryKey(entry.model(), entry.profile(), key.generation);
        List<PlayerModelCompiler.Surface> surfaces = GEOMETRY.get(geometryKey);
        if (surfaces == null) {
            var imported = ModelImporters.load(ModelCatalog.id(entry.model()), id -> source.open(
                    id.equals(ModelCatalog.id(entry.atlas())) ? ModelCatalog.id(entry.texture()) : id));
            var runtime = new ModelRuntimeAsset(imported, ModelMaterialBridge.build(imported));
            surfaces = PlayerModelCompiler.compile(runtime, PlayerModelProfile.load(source, entry.profile()));
            GEOMETRY.put(geometryKey, surfaces);
            while (GEOMETRY.size() > 4) GEOMETRY.remove(GEOMETRY.keySet().iterator().next());
        }
        return new Loaded(surfaces, PlayerModelTexture.decode(ModelCatalog.read(source, entry.texture()), key.quality));
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
        if (asset == null || pose == null) return null;
        Snapshot snapshot = new Snapshot(asset, freezePose(pose), module.fitting(), new ReplacementSkinCache());
        status = module.model.get();
        return snapshot;
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
        matrices.pushPose();
        try {
            matrices.mulPose(snapshot.fitting);
            if (PlayerModelGpuRenderer.trySubmit(snapshot, matrices, originalType, light, overlay, tint, outlineColor))
                return true;
            for (var surface : snapshot.asset.surfaces) {
                RenderType type = originalType.isOutline() ? RenderTypes.outline(snapshot.asset.texture)
                        : surface.translucent() || originalType.hasBlending() || (tint >>> 24) < 255
                        ? RenderTypes.entityTranslucent(snapshot.asset.texture, true)
                        : RenderTypes.entityCutout(snapshot.asset.texture);
                PlayerRigCpuRenderer.submitReplacement(collector, surface.mesh(), snapshot.rig, matrices,
                        type, light, overlay, tint, outlineColor, snapshot.asset, snapshot.skin, part -> true);
            }
            return true;
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
        boolean any = false;
        ReplacementSkinCache cache = selected.handSkin();
        for (var surface : selected.surfaces()) {
            boolean hasArm = surface.mesh().parts().stream().anyMatch(p -> isArmGroup(p.name(), group));
            if (!hasArm) continue;
            RenderType type = surface.translucent()
                    ? RenderTypes.entityTranslucent(selected.texture(), true)
                    : RenderTypes.entityCutout(selected.texture());
            PlayerRigCpuRenderer.submitReplacement(collector, surface.mesh(), HAND_BIND_RIG, matrices,
                    type, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    -1, 0, selected, cache, name -> isArmGroup(name, group));
            any = true;
        }
        return any;
    }

    private static boolean isArmGroup(String name, String group) {
        return name.equals(group) || name.startsWith(group + "/");
    }

    public static boolean active(Object state) {
        return state instanceof PlayerModelRenderState replacement && replacement.combatant$getReplacement() != null;
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
        PlayerModelGpuRenderer.reset();
        combatant.client.render.engine.rig.gpu.RigGpuMeshCache.global().invalidate();
        ModelCatalog.invalidate();
        if (pending != null) pending.cancel(false);
        pending = null;
        key = null;
        ready = null;
        CACHE.clear();
        LOADER.execute(GEOMETRY::clear);
    }

    @AssetLoad(order = 510)
    public static void reloadResources() {
        invalidate();
        if (pending != null) pending.cancel(false);
        pending = null;
        key = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) for (TextureOwner owner : TEXTURES) owner.release();
        TEXTURES.clear();
    }
}
